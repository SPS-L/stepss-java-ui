#!/usr/bin/env python3
"""Find the Mach-O binaries that travel inside a jar, and check them back in.

Apple's notary service opens jar files and inspects every Mach-O it finds
inside them. jpackage signs the .app bundle and the runtime it assembles and
never reaches inside a jar, so a library that ships native code as a jar
resource arrives at Apple unsigned. FlatLaf is one: STEPSS 3.82 was rejected
with "Archive contains critical validation errors" over four dylibs under
com/formdev/flatlaf/natives/, two of them in flatlaf-3.7.2.jar and two more in
the fat stepss.jar that -post-jar merges them into.

This module is the half of the fix that knows about zip files.
tools/sign-jar-natives.sh is the half that knows about codesign, and it is the
entry point; nothing calls this directly except that script and its tests.

Commands:
  find    <jar>                 print the Mach-O entry names, one per line
  extract <jar> <stagedir>      the same, and write each one out under
                                <stagedir>, plus a snapshot of the whole
                                archive for `check` to compare against
  check   <jar> <stagedir>      after the caller has written the staged files
                                back in, assert that they landed and that
                                nothing else in the jar moved or changed

Exit status is 0 on success and 1 on a failed check. `find` and `extract`
print nothing and succeed when a jar holds no Mach-O at all: deciding that
zero is a failure is the caller's job, because the caller is the one that
knows how many jars it looked at.
"""

import json
import os
import struct
import sys
import zipfile

# The four thin Mach-O magics: 32- and 64-bit, each in both byte orders.
# These are the first four bytes of a single-architecture Mach-O file.
THIN_MAGICS = frozenset([
    b"\xfe\xed\xfa\xce",  # MH_MAGIC
    b"\xce\xfa\xed\xfe",  # MH_CIGAM
    b"\xfe\xed\xfa\xcf",  # MH_MAGIC_64
    b"\xcf\xfa\xed\xfe",  # MH_CIGAM_64
])

# The universal ("fat") wrappers, mapped to how their header is laid out:
# (big-endian?, 64-bit offsets?). A fat_arch is 20 bytes, a fat_arch_64 is 32.
FAT_MAGICS = {
    b"\xca\xfe\xba\xbe": (True, False),   # FAT_MAGIC
    b"\xbe\xba\xfe\xca": (False, False),  # FAT_CIGAM
    b"\xca\xfe\xba\xbf": (True, True),    # FAT_MAGIC_64
    b"\xbf\xba\xfe\xca": (False, True),   # FAT_CIGAM_64
}

# No jar entry that is a Mach-O is anywhere near this large, and the cap keeps
# a hostile or corrupt archive from being read into memory whole just because
# its first four bytes happened to match.
MAX_CANDIDATE_BYTES = 256 * 1024 * 1024

# A fat binary has as many slices as the vendor chose to ship; two is the norm
# and Apple has never shipped more than a handful. The bound exists only to
# reject a Java class file whose constant-pool count read as a slice count.
MAX_FAT_ARCH = 32


def looks_mach_o(data):
    """Decide from the bytes alone whether `data` is a Mach-O binary.

    The extension is not consulted, deliberately. FlatLaf calls these .dylib,
    the JNI convention on macOS used to be .jnilib, and a library is free to
    ship one under no extension at all; a check keyed on the name is a check
    that silently stops working the next time a dependency is bumped.

    The thin magics are unambiguous. The fat magic is not: 0xCAFEBABE is also
    the magic of every Java class file, so a jar full of classes would come
    back full of "Mach-O" on a magic test alone. A fat header is therefore
    parsed rather than pattern-matched, and it counts only if the slice table
    it declares is internally consistent: a plausible number of slices, each
    lying inside the file, and each one beginning with a thin Mach-O magic of
    its own. A class file survives none of that, because the bytes that would
    have to be a slice offset are its constant pool.
    """
    if len(data) < 8:
        return False
    magic = data[:4]
    if magic in THIN_MAGICS:
        return True
    if magic not in FAT_MAGICS:
        return False

    big_endian, wide = FAT_MAGICS[magic]
    order = ">" if big_endian else "<"
    (nfat_arch,) = struct.unpack(order + "I", data[4:8])
    if not 1 <= nfat_arch <= MAX_FAT_ARCH:
        return False

    entry_size = 32 if wide else 20
    off_fmt = order + ("QQ" if wide else "II")
    # cputype and cpusubtype are four bytes each in both shapes, so the offset
    # and size pair starts at the same place in a fat_arch and a fat_arch_64.
    off_at = 8
    table_end = 8 + nfat_arch * entry_size
    if table_end > len(data):
        return False

    for i in range(nfat_arch):
        base = 8 + i * entry_size
        # cputype and cpusubtype occupy the first eight bytes of either shape;
        # they are not checked against a list of known CPUs, because that list
        # grows every time Apple ships an architecture. The offset and size
        # that follow are what the check rests on.
        offset, size = struct.unpack(
            off_fmt, data[base + off_at: base + off_at + (16 if wide else 8)])
        if size == 0 or offset + size > len(data):
            return False
        if data[offset:offset + 4] not in THIN_MAGICS:
            return False
    return True


def mach_o_entries(jar):
    """Every Mach-O entry in `jar`, in the order the archive stores them."""
    found = []
    with zipfile.ZipFile(jar) as zf:
        for info in zf.infolist():
            if info.is_dir() or info.file_size < 8:
                continue
            with zf.open(info) as f:
                head = f.read(4)
                if head in THIN_MAGICS:
                    found.append(info.filename)
                    continue
                if head not in FAT_MAGICS:
                    continue
                if info.file_size > MAX_CANDIDATE_BYTES:
                    continue
                if looks_mach_o(head + f.read()):
                    found.append(info.filename)
    return found


def snapshot(jar):
    """What every entry looks like now, for `check` to compare against.

    Name, CRC, uncompressed size, compression method and stored timestamp, in
    archive order. Two archives agreeing on this list agree on their contents:
    the CRC pins the bytes and the order pins the layout, which is what keeps
    META-INF/MANIFEST.MF first where the jar format wants it.
    """
    with zipfile.ZipFile(jar) as zf:
        return [[i.filename, i.CRC, i.file_size, i.compress_type,
                 list(i.date_time)] for i in zf.infolist()]


def staged_path(stage, name):
    path = os.path.join(stage, name)
    # zipfile normalises away leading slashes and ".." on extraction; this is
    # the same guard, because the writes below bypass zipfile.extract.
    root = os.path.abspath(stage)
    if not os.path.abspath(path).startswith(root + os.sep):
        raise SystemExit("jar-natives.py: entry escapes the staging directory: %s" % name)
    return path


def cmd_find(jar):
    for name in mach_o_entries(jar):
        print(name)
    return 0


def cmd_extract(jar, stage):
    names = mach_o_entries(jar)
    os.makedirs(stage, exist_ok=True)
    with open(os.path.join(stage, ".jar-snapshot.json"), "w") as f:
        json.dump({"jar": os.path.abspath(jar),
                   "entries": snapshot(jar),
                   "mach_o": names}, f)
    with zipfile.ZipFile(jar) as zf:
        for name in names:
            info = zf.getinfo(name)
            path = staged_path(stage, name)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with zf.open(info) as src, open(path, "wb") as dst:
                dst.write(src.read())
            # The stored Unix mode, so that zip writes the entry back with the
            # permissions it had. The high half of external_attr is zero for an
            # archive written by a DOS-minded tool, and there is nothing to
            # restore in that case.
            mode = (info.external_attr >> 16) & 0o7777
            if mode:
                os.chmod(path, mode)
            # The modification time is deliberately NOT restored. zip stamps a
            # replaced entry from the file on disk, and `ant bundle` re-runs
            # the jar target, whose -post-jar merge is a <jar update="true">
            # that replaces an entry only when its source is newer. A signed
            # dylib carrying its original 2026 timestamp would lose that
            # comparison and the merge would keep the unsigned copy.
            print(name)
    return 0


def cmd_check(jar, stage):
    snap_file = os.path.join(stage, ".jar-snapshot.json")
    if not os.path.exists(snap_file):
        print("jar-natives.py: no snapshot in %s; extract was never run" % stage,
              file=sys.stderr)
        return 1
    with open(snap_file) as f:
        before = json.load(f)

    expected = {n: i for i, n in enumerate(before["mach_o"])}
    after = snapshot(jar)

    # Everything that was not signed must be exactly as it was, in the same
    # place. Info-ZIP's update mode copies untouched entries across verbatim
    # rather than recompressing them, so this is an assertion about a property
    # the repack already has, not a hope.
    def others(entries):
        return [e for e in entries if e[0] not in expected]

    if others(after) != others(before["entries"]):
        print("jar-natives.py: repacking %s changed entries it should not have."
              % jar, file=sys.stderr)
        was = {e[0]: e for e in others(before["entries"])}
        now = {e[0]: e for e in others(after)}
        for name in sorted(set(was) ^ set(now)):
            print("  %s: %s" % (name, "removed" if name in was else "added"),
                  file=sys.stderr)
        for name in sorted(set(was) & set(now)):
            if was[name] != now[name]:
                print("  %s: %s -> %s" % (name, was[name], now[name]), file=sys.stderr)
        if [e[0] for e in others(after)] != [e[0] for e in others(before["entries"])]:
            print("  the order of the remaining entries changed", file=sys.stderr)
        return 1

    # And every signed file must actually be in there, byte for byte.
    ok = True
    with zipfile.ZipFile(jar) as zf:
        present = set(zf.namelist())
        for name in before["mach_o"]:
            if name not in present:
                print("jar-natives.py: %s lost %s in the repack" % (jar, name),
                      file=sys.stderr)
                ok = False
                continue
            with open(staged_path(stage, name), "rb") as f:
                want = f.read()
            if zf.read(name) != want:
                print("jar-natives.py: %s in %s is not the file that was signed"
                      % (name, jar), file=sys.stderr)
                ok = False
    if not ok:
        return 1
    print("%s: %d signed binaries written back, %d other entries untouched"
          % (jar, len(before["mach_o"]), len(others(after))))
    return 0


def main(argv):
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2
    command = argv[1]
    if command == "find" and len(argv) == 3:
        return cmd_find(argv[2])
    if command == "extract" and len(argv) == 4:
        return cmd_extract(argv[2], argv[3])
    if command == "check" and len(argv) == 4:
        return cmd_check(argv[2], argv[3])
    print("jar-natives.py: unknown command: %s" % " ".join(argv[1:]), file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
