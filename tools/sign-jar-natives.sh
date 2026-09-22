#!/usr/bin/env bash
# Developer ID signing for the Mach-O binaries that travel inside jar files.
#
# Why this exists. Apple's notary service opens jar files and inspects the
# Mach-O binaries inside them; jpackage does not. jpackage signs the .app and
# the runtime it assembles, copies --input verbatim into Contents/app and
# never looks inside a jar, so a library shipping native code as a jar
# resource reaches Apple unsigned. STEPSS 3.82 was rejected for exactly that,
# "Archive contains critical validation errors", over the four FlatLaf dylibs:
#
#   .../app/lib/flatlaf-3.7.2.jar/com/formdev/flatlaf/natives/libflatlaf-macos-{arm64,x86_64}.dylib
#   .../app/stepss.jar/com/formdev/flatlaf/natives/libflatlaf-macos-{arm64,x86_64}.dylib
#
# each with "The binary is not signed" and "The signature does not include a
# secure timestamp". The engines are not affected: they ride along as .tar.gz
# resources, which the notary does not open, and they are signed upstream.
#
# Both architectures are signed, the x86_64 slice included, although the
# bundle ships arm64 only. Apple objected to it, and deleting a file from a
# third-party jar changes what that library ships, which is a larger decision
# than a signing fix.
#
# Why the source lib/ directory is signed and not only dist/. `ant bundle`
# depends on `jar`, so the installer build re-runs the whole jar target and
# NetBeans' copylibs recopies lib/*.jar over dist/lib/*.jar on the way
# through. Signing dist/ alone is undone a step later, silently: it was
# measured, by appending a marker to the dylibs in dist/, and the marker was
# gone after the next `ant jar`. Signing lib/ as well is what survives,
# because copylibs then propagates the signed jar into dist/lib/ and -post-jar
# merges the signed entries into the fat dist/stepss.jar. dist/ is signed too,
# so the guarantee does not rest on that rebuild happening; whichever copy
# jpackage packages is signed either way, and `verify` below is run after the
# build to prove it rather than assume it.
#
# Why sign.sh rather than a codesign call of its own. What Apple asked for is
# a Developer ID signature, a secure timestamp and the hardened runtime, which
# is precisely what `sign.sh sign` applies, along with the entitlements. That
# file is the one place those flags are written down, across five
# repositories, and a second spelling of them here is a second thing to keep
# in step. SIGN_SH points at a checkout of SPS-L/stepss-ci; the composite
# action cannot be used for this, because it opens a keychain of its own and
# the release job already has one open.
#
# Usage:
#   SIGN_SH=<path>/sign-macos/sign.sh tools/sign-jar-natives.sh sign <dir>...
#   tools/sign-jar-natives.sh verify <dir>...
#
# Both commands fail when they find nothing: a step that signs or checks zero
# files while reporting success is how this failure comes back unnoticed.
#
# Written for the macOS runner's stock /bin/bash 3.2, which has no mapfile and
# raises "unbound variable" on "${a[@]}" for an empty array under `set -u`,
# hence the read loops and the count checks before every expansion.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_NATIVES="$HERE/jar-natives.py"

usage() {
    cat >&2 <<'EOF'
usage: sign-jar-natives.sh sign <dir> [dir ...]     (needs SIGN_SH)
       sign-jar-natives.sh verify <dir> [dir ...]
EOF
}

abs() { (cd "$(dirname "$1")" && printf '%s/%s\n' "$PWD" "$(basename "$1")"); }

# Every *.jar at any depth under the given roots, written to $1 one per line.
# A plain redirect rather than a process substitution, so that `set -e` still
# sees a failing find: a command that fails inside <(...) leaves the reading
# loop with no input and no error, which is the same silent success this
# script exists to rule out.
#
# Nested jars, a jar inside a jar, are not descended into: nothing here ships
# one, and the notary's own report addresses entries one level deep. A
# dependency that started shipping natives that way would need this extended,
# and would announce itself the way FlatLaf did.
collect_jars() {
    local out="$1"; shift
    local root
    : > "$out"
    for root in "$@"; do
        if [ ! -d "$root" ]; then
            echo "sign-jar-natives.sh: no such directory: $root" >&2
            exit 1
        fi
        find "$root" -type f -name '*.jar' -print >> "$out"
    done
    sort -o "$out" "$out"
}

no_binaries_found() {
    local jar_list="$1" count="$2"
    echo "sign-jar-natives.sh: found no Mach-O binaries in $count jar(s)." >&2
    echo "FlatLaf alone ships four, so finding none means the scan has stopped" >&2
    echo "working, not that the problem went away. Jars searched:" >&2
    sed 's/^/  /' "$jar_list" >&2
    exit 1
}

cmd_sign() {
    if [ -z "${SIGN_SH:-}" ]; then
        echo "sign-jar-natives.sh: SIGN_SH is empty." >&2
        echo "It must name the sign.sh of a SPS-L/stepss-ci checkout. This step" >&2
        echo "does not skip: a skipped one ships unsigned binaries inside the" >&2
        echo "jars under a green run, which is the failure it exists to stop." >&2
        exit 1
    fi
    if [ ! -f "$SIGN_SH" ]; then
        echo "sign-jar-natives.sh: no such file: $SIGN_SH" >&2
        exit 1
    fi

    local stage="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/jar-natives"
    rm -rf "$stage"
    mkdir -p "$stage"

    local jar_list="$stage/jars.txt"
    collect_jars "$jar_list" "$@"

    # Parallel arrays rather than a map: bash 3.2 has no associative arrays.
    local staged=() hit_jars=() hit_dirs=()
    local jars=0 jar dir name
    while IFS= read -r jar; do
        dir="$stage/$jars"
        jars=$((jars + 1))
        python3 "$JAR_NATIVES" extract "$jar" "$dir" > "$dir.entries"
        if [ ! -s "$dir.entries" ]; then
            echo "$jar: no Mach-O entries"
            rm -rf "$dir"
            continue
        fi
        echo "$jar:"
        while IFS= read -r name; do
            echo "    $name"
            staged+=("$dir/$name")
        done < "$dir.entries"
        hit_jars+=("$jar")
        hit_dirs+=("$dir")
    done < "$jar_list"

    [ "${#staged[@]}" -gt 0 ] || no_binaries_found "$jar_list" "$jars"

    # One call rather than one per file: sign.sh resolves the signing identity
    # out of the keychain once per invocation, and both of its commands take a
    # list and loop inside.
    bash "$SIGN_SH" sign "${staged[@]}"
    bash "$SIGN_SH" verify "${staged[@]}"

    local i=0 entries=()
    while [ "$i" -lt "${#hit_jars[@]}" ]; do
        jar="$(abs "${hit_jars[$i]}")"
        dir="${hit_dirs[$i]}"
        i=$((i + 1))
        entries=()
        while IFS= read -r name; do
            entries+=("$name")
        done < "$dir.entries"
        # Info-ZIP update mode: every entry not named here is copied across
        # verbatim, still compressed, still in its place, so META-INF/MANIFEST.MF
        # stays the first entry the jar format wants it to be. zip stores each
        # argument under the relative path it is named by, which is why the
        # staging tree mirrors the entry names exactly. The `cd` is the whole
        # reason $jar was made absolute first.
        ( cd "$dir" && zip -q "$jar" "${entries[@]}" )
        python3 "$JAR_NATIVES" check "$jar" "$dir"
    done

    echo "Signed ${#staged[@]} Mach-O binaries across $jars jar(s)."
}

cmd_verify() {
    local stage="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/jar-natives-verify"
    rm -rf "$stage"
    mkdir -p "$stage"

    local jar_list="$stage/jars.txt"
    collect_jars "$jar_list" "$@"

    local staged=() jars=0 jar dir name
    while IFS= read -r jar; do
        dir="$stage/$jars"
        jars=$((jars + 1))
        python3 "$JAR_NATIVES" extract "$jar" "$dir" > "$dir.entries"
        while IFS= read -r name; do
            echo "$jar: $name"
            staged+=("$dir/$name")
        done < "$dir.entries"
    done < "$jar_list"

    [ "${#staged[@]}" -gt 0 ] || no_binaries_found "$jar_list" "$jars"

    # A Mach-O signature lives inside the file rather than beside it, so a
    # copy extracted from the jar carries it and this answers for the bytes
    # that are actually in the archive jpackage packaged. Same check as
    # `sign.sh verify`, spelled out here because this command runs after the
    # keychain has served its purpose and needs no credentials at all.
    local f
    for f in "${staged[@]}"; do
        codesign --verify --strict --verbose=2 "$f"
    done
    echo "All ${#staged[@]} Mach-O binaries across $jars jar(s) carry a valid signature."
}

main() {
    local command="${1:-}"
    [ -n "$command" ] || { usage; exit 2; }
    shift
    [ "$#" -gt 0 ] || { usage; exit 2; }
    case "$command" in
        sign)   cmd_sign "$@" ;;
        verify) cmd_verify "$@" ;;
        *) echo "sign-jar-natives.sh: unknown command: $command" >&2; usage; exit 2 ;;
    esac
}

main "$@"
