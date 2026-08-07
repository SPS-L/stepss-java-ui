# Custom-Model Compilation on gfortran — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Codegen tab's Compile button build a custom `dynsim` from CODEGEN-generated Fortran models on Windows x86_64, Linux x86_64 and macOS arm64, by driving upstream's gfortran Makefiles against the pinned uramses kit.

**Architecture:** A new `my.ramses.compile` package owns the pipeline — extract the kit, reset it to pristine, stage the generated `.f90` files, splice them into the five router files at explicit marker comments, probe for a usable gfortran, then run `make -f build/Makefile.<plat> exe`. The kit ships as a build-time-repacked slice of the public `stepss-uramses` source archive, verified by a content manifest rather than by container bytes. On success the built executable becomes the engine subsequent simulations run on.

**Tech Stack:** Java 11 (source/target 11), Swing, Apache Commons Exec, Apache Ant, gfortran/GNU make (on the user's machine), NetBeans form-backed UI.

## Global Constraints

- `javac.source` and `javac.target` are **11**. No `var`, no records, no switch expressions, no text blocks. The existing platform package uses explicit generics (`new HashMap<Platform, Payload>()`) — match that style.
- **No unit-test framework exists in this repo and none is being added.** Verification is behavioural, via headless `main()` harnesses under `tools/` and the acceptance runs in Task 7. This mirrors P1 exactly.
- `src/my/ramses/RamsesUI.form` is the **source of truth** for generated `initComponents()`. Any change to a component's *initial* state must be made in the `.form` file. This plan requires no `.form` change — `Compile` and `savedynsim` both stay `enabled="false"` initially (form lines 2541 and 2551) and are enabled at runtime from Java. Do not edit the `.form`.
- **Never use compound git commands** (`&&`, `||`, `;`). Run each git command separately, and `cd` in its own command first.
- This repo is a **git submodule** of the stepss umbrella; its git dir is outside the sandbox write allowlist, so git write operations need `dangerouslyDisableSandbox: true`.
- `build.xml` contains **two** targets named `-post-compile`, at lines 37 and 92. Ant lets the later definition win, so line 92 (the `PayloadManifestCheck` one) is the effective target and the `<obfuscate>` block at line 37 is dead. **Extend the target at line 92. Never add a third `-post-compile`** — it would silently override the manifest check.
- Marker strings, used verbatim everywhere: `!<<STEPSS-GUI:EXTERNALS>>` and `!<<STEPSS-GUI:CASES>>`.
- The five model kinds, in this exact order and spelling: `exc`, `inj`, `tor`, `twop`, `dctl`.
- Every router names its procedure pointer `<kind>_ptr`. The case body is always `<kind>_ptr=><model>`.
- Platform → Makefile/kit/output mapping, used verbatim:

  | `Platform` | Makefile | Module kit | Output |
  |---|---|---|---|
  | `WINDOWS_X86_64` | `build/Makefile.windows` | `modules_wg/` | `Release_wg/dynsim.exe` |
  | `LINUX_X86_64` | `build/Makefile.linux` | `modules_l/` | `Release_l/dynsim` |
  | `MACOS_ARM64` | `build/Makefile.macos` | `modules_m/` | `Release_m/dynsim` |

- `Platform.key()` already returns `"windows"`, `"linux"`, `"macos"` — exactly the Makefile suffixes. Use it. The module-kit suffixes (`wg`, `l`, `m`) have no existing accessor and are introduced in Task 3.
- The uramses Makefiles are **always invoked from the kit root** with `-f build/Makefile.<plat>`. `-f` does not change make's working directory and every path inside them is root-relative. Never `cd build`.
- Upstream repo for Task 1: `../stepss-uramses` (public, default branch `master`). It is a **separate git repository** — commit there separately, and never commit its changes from this repo.

---

## File Structure

**Create in this repo:**

| File | Responsibility |
|---|---|
| `src/my/ramses/compile/RouterSplicer.java` | Pure text: model-kind parsing and marker-based insertion. No I/O, no platform knowledge. |
| `src/my/ramses/compile/FortranToolchain.java` | Locating `make` and an ABI-compatible `gfortran`; reading GFORTRAN module ABI versions. |
| `src/my/ramses/compile/ModelCompiler.java` | The pipeline: reset, stage, splice, build, adopt. |
| `src/my/ramses/compile/CompileHarness.java` | Headless `main()` for verifying splice and probe without the GUI. |
| `src/my/ramses/platform/UramsesKitPack.java` | Build-time: extract, verify content manifest, repack. |
| `tools/compile-harness.sh` | Wrapper that runs `CompileHarness` against a built classes dir. |

**Modify in this repo:**

| File | Change |
|---|---|
| `versions.properties` | `uramses` block: version, repo, tag, source URL, manifest digest. |
| `build.xml` | `fetch-uramses` target; `stage-payloads` runs `UramsesKitPack`; `-post-compile` (line 92) also checks the kit. |
| `src/my/ramses/platform/ToolSpec.java` | `Kind.ZIP_TREE` and a retained-prefix list. |
| `src/my/ramses/platform/ToolExtractor.java` | Tree extraction honouring retained prefixes. |
| `src/my/ramses/platform/Toolchain.java` | `URAMSES` spec, lazy extraction, `uramsesKit()`, `moduleKitDir()`. |
| `src/my/ramses/RamsesUI.java` | `CompileActionPerformed`, `savedynsimActionPerformed`, enable `Compile` after Run Codegen. |
| `README.md` | The two "not available this release" rows, and the new prerequisites. |

**Modify in `../stepss-uramses` (Task 1 only):** `src/usr_exc_models.f90`, `src/usr_inj_models.f90`, `src/usr_tor_models.f90`, `src/usr_twop_models.f90`, `src/usr_dctl_models.f90`.

---

### Task 1: Upstream marker patch (stepss-uramses)

This task is in a **different repository**: `../stepss-uramses`, public, default branch `master`. Nothing in `stepss-java-ui` changes.

**Files:**
- Modify: `../stepss-uramses/src/usr_exc_models.f90`
- Modify: `../stepss-uramses/src/usr_inj_models.f90`
- Modify: `../stepss-uramses/src/usr_tor_models.f90`
- Modify: `../stepss-uramses/src/usr_twop_models.f90`
- Modify: `../stepss-uramses/src/usr_dctl_models.f90`

**Interfaces:**
- Produces: two marker comments per router file, `!<<STEPSS-GUI:EXTERNALS>>` and `!<<STEPSS-GUI:CASES>>`, which Task 4's `RouterSplicer` locates by exact string match.

- [ ] **Step 1: Create a branch in the uramses repo**

```bash
cd /home/apetros/Code/stepss/stepss-uramses
```
```bash
git checkout -b stepss-gui-markers
```

- [ ] **Step 2: Add the EXTERNALS marker to each router**

In each of the five files, insert the marker as the **last line of the contiguous `external` declaration block**, at the same indentation as the declarations around it. The five blocks currently end at:

| File | Last `external` line |
|---|---|
| `usr_exc_models.f90` | `   external :: exc_ENTSOE_lim` (line 36) |
| `usr_inj_models.f90` | `   external inj_PMU` (line 29) |
| `usr_tor_models.f90` | `   external :: tor_hygov, tor_GAST, tor_TGOV1D` (line 26) |
| `usr_twop_models.f90` | `   external twop_HVDC_LCC,  twop_HVDC_VSC, twop_HVDC_VSC_SC, twop_DCL_WCL` (line 25) |
| `usr_dctl_models.f90` | `   external dctl_line_prot` (line 25) |

For `usr_exc_models.f90` the result reads:

```fortran
   ! Models built from custom_models/ in this repository. Declare your own
   ! here alongside the pre-compiled ones above.
   external :: exc_ENTSOE_lim
   !<<STEPSS-GUI:EXTERNALS>>
```

- [ ] **Step 3: Add the CASES marker to each router**

Insert as the **last entry inside `select case`**, immediately before `end select` (after removing `case default` in Step 4). For `usr_exc_models.f90`:

```fortran
      ! Models compiled from custom_models/ in this repository. Add your own
      ! below; the name is what a .dat file's EXC record refers to, with or
      ! without the "exc_" prefix.
      case('exc_ENTSOE_lim')
         exc_ptr=>exc_ENTSOE_lim

      !<<STEPSS-GUI:CASES>>
   end select
```

- [ ] **Step 4: Remove the empty `case default` from `usr_exc_models.f90` and `usr_dctl_models.f90`**

Only those two have one, and both bodies are empty. Delete the `case default` line. An unmatched `select case` with no default already leaves the pointer untouched, so this changes nothing behaviourally.

- [ ] **Step 5: Delete the dead `tor_sultan` comment**

In `usr_tor_models.f90`, delete these two lines:

```fortran
   !case('tor_sultan')
   !      tor_ptr=>tor_sultan  
```

No `tor_sultan` symbol exists in `modules_l/libramses.a` and it is not declared `external`.

- [ ] **Step 6: Restore `twop_HVDC_VSC`'s dispatch entry**

In `usr_twop_models.f90`, replace these two commented lines:

```fortran
!   case('twop_HVDC_VSC')
!         twop_ptr => twop_HVDC_VSC
```

with the live entry:

```fortran
   case('twop_HVDC_VSC')
         twop_ptr => twop_HVDC_VSC
```

This one is **not** dead code: the model is declared `external` and defined in `libramses.a`, so it ships in RAMSES and only the comment made it unnameable.

- [ ] **Step 7: Verify every declared model is dispatched**

Run:
```bash
cd /home/apetros/Code/stepss/stepss-uramses
```
```bash
for f in src/usr_*_models.f90; do kind=$(basename "$f" | sed 's/usr_//;s/_models.f90//'); grep "^[[:space:]]*external" "$f" | grep -o "${kind}_[A-Za-z0-9_]*" | sort -u | while read sym; do live=$(grep -v "^[[:space:]]*!" "$f" | grep -c "=>[[:space:]]*${sym}[[:space:]]*$"); if [ "$live" = "0" ]; then echo "NOT DISPATCHED: $sym"; fi; done; done
```
Expected: **no output**. Before Step 6 this printed `NOT DISPATCHED: twop_HVDC_VSC`.

Note the test is the live `=>` assignment, not the case label: a label may differ in case from the procedure it dispatches (`case('exc_kundur')` dispatches `exc_KUNDUR`), so matching labels against symbol names over-reports.

- [ ] **Step 8: Verify the markers are present and unique**

```bash
grep -c "STEPSS-GUI:EXTERNALS\|STEPSS-GUI:CASES" src/usr_*_models.f90
```
Expected: each of the five files reports `2`.

- [ ] **Step 9: Confirm the routers still compile**

```bash
make -f build/Makefile.linux clean
```
```bash
make -f build/Makefile.linux exe
```
Expected: BUILD completes and `Release_l/dynsim` exists. This proves the markers are inert comments and that removing `case default` and restoring `twop_HVDC_VSC` both compile.

If gfortran's module ABI does not match `modules_l/` on this machine, `check-deps` fails with an explicit remedy — follow it and re-run. The kit is gfortran 13.3 / ABI 15.

- [ ] **Step 10: Commit**

```bash
cd /home/apetros/Code/stepss/stepss-uramses
```
```bash
git add src/usr_exc_models.f90 src/usr_inj_models.f90 src/usr_tor_models.f90 src/usr_twop_models.f90 src/usr_dctl_models.f90
```
```bash
git commit -m "Add STEPSS GUI marker comments to the model routers

The STEPSS Java GUI splices generated models into these files. Explicit
markers make that a declared contract rather than a guess about source
layout, so a reformat here cannot silently break it.

Also normalises the five routers: the empty case default goes from exc
and dctl (an unmatched select case with no default already leaves the
pointer untouched), the dead tor_sultan comment goes, and twop_HVDC_VSC
is restored to the select-case. That last one is not dead code - the
model is declared external and defined in libramses.a, so it ships but
was unnameable from a .dat file."
```

Do **not** push or tag. Whether this lands upstream and under which tag is the user's call; Task 2 pins a version independently.

---

### Task 2: Fetch, verify and repack the uramses kit

**Files:**
- Create: `src/my/ramses/platform/UramsesKitPack.java`
- Modify: `versions.properties`
- Modify: `build.xml`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `src/my/ramses/payload/uramses-kit-v3.55.zip`, a zip whose entries are **relative paths with the archive's top-level directory stripped** — `build/Makefile.linux`, `src/usr_exc_models.f90`, `modules_l/libramses.a`, and so on. Task 3's `ToolSpec` retained-prefix lists match against exactly these paths.
- Produces: `UramsesKitPack.main(String[] args)` with `args[0]` = source zip, `args[1]` = output zip, `args[2]` = expected manifest SHA-256 (or the literal `COMPUTE` to print the digest and skip verification).

- [ ] **Step 1: Add the uramses block to `versions.properties`**

Append:

```properties
# stepss-uramses is the one PUBLIC component, so this payload is fetched over
# plain HTTPS with no gh and no STEPSS_TOKEN. GitHub's auto-generated source
# archives are not guaranteed byte-stable - a compression change on their side
# in early 2023 altered checksums for already-published tags - so the pin below
# is the SHA-256 of a content manifest over the files we retain, not of the
# downloaded container. Re-compression is invisible to it; a changed byte in
# any retained file is not.
uramses.version=3.55
uramses.repo=SPS-L/stepss-uramses
uramses.tag=v3.55
uramses.source.url=https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v3.55.zip
uramses.manifest.sha256=COMPUTE
```

`COMPUTE` is replaced with the real digest in Step 6. It is a deliberate two-phase bootstrap, not a placeholder left behind.

- [ ] **Step 2: Write `UramsesKitPack`**

```java
package my.ramses.platform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Build-time step that turns the published stepss-uramses source archive into
 * the slice the GUI ships.
 *
 * <p>Runs from build.xml's {@code stage-payloads}, following the pattern set by
 * {@link PayloadManifestCheck}: a small {@code main()} on the build classpath
 * rather than custom Ant tasks.
 *
 * <p>Verification is by content, not container. GitHub's auto-generated source
 * archives carry no byte-stability guarantee, so a digest pinned against the
 * downloaded zip can fail months later on unchanged content. Instead this
 * builds a manifest - one {@code <sha256>  <path>} line per retained file,
 * sorted by path - and digests that. Re-compression by GitHub is invisible;
 * a single changed byte in any retained file is not.
 */
public final class UramsesKitPack {

    /** Path prefixes retained from the source archive, after stripping its top-level directory. */
    private static final List<String> RETAIN = Collections.unmodifiableList(Arrays.asList(
            "build/Makefile.linux",
            "build/Makefile.macos",
            "build/Makefile.windows",
            "src/",
            "custom_models/",
            "tools/",
            "modules_l/",
            "modules_m/",
            "modules_wg/",
            "README.md",
            "LICENSE.rst"));

    private UramsesKitPack() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: UramsesKitPack <source.zip> <output.zip> <manifest-sha256|COMPUTE>");
            System.exit(2);
            return;
        }
        File source = new File(args[0]);
        File output = new File(args[1]);
        String expected = args[2];

        ZipFile zip = new ZipFile(source);
        try {
            List<String> entries = retainedEntries(zip);
            if (entries.isEmpty()) {
                System.err.println("No retained entries found in " + source
                        + ". Is this a stepss-uramses source archive?");
                System.exit(1);
                return;
            }
            String manifest = manifestOf(zip, entries);
            String digest = hex(sha256(manifest.getBytes("UTF-8")));

            if ("COMPUTE".equals(expected)) {
                System.out.println("uramses.manifest.sha256=" + digest);
                System.out.println("Retained " + entries.size() + " files. Set the digest in "
                        + "versions.properties and re-run.");
                return;
            }
            if (!digest.equals(expected)) {
                System.err.println("uramses kit manifest check FAILED");
                System.err.println("  expected " + expected);
                System.err.println("  actual   " + digest);
                System.err.println("The retained contents of " + source.getName()
                        + " differ from what versions.properties pins. This is a content"
                        + " change upstream, not a re-compression: re-compression does not"
                        + " affect this digest.");
                System.exit(1);
                return;
            }
            repack(zip, entries, output);
            System.out.println("uramses kit OK: " + entries.size() + " files -> "
                    + output.getAbsolutePath() + " (" + output.length() / 1024 + " KB)");
        } finally {
            zip.close();
        }
    }

    /**
     * Entry names with the archive's single top-level directory stripped,
     * filtered to {@link #RETAIN} and sorted. Directory entries are dropped:
     * the repacked zip carries files only, and the extractor recreates parents.
     */
    static List<String> retainedEntries(ZipFile zip) {
        List<String> kept = new ArrayList<String>();
        java.util.Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) {
                continue;
            }
            String rel = stripTopLevel(e.getName());
            if (rel != null && isRetained(rel)) {
                kept.add(rel);
            }
        }
        Collections.sort(kept);
        return kept;
    }

    /** {@code stepss-uramses-3.55/build/Makefile.linux} -> {@code build/Makefile.linux}. */
    static String stripTopLevel(String name) {
        int slash = name.indexOf('/');
        if (slash < 0 || slash == name.length() - 1) {
            return null;
        }
        return name.substring(slash + 1);
    }

    static boolean isRetained(String rel) {
        for (String prefix : RETAIN) {
            if (prefix.endsWith("/") ? rel.startsWith(prefix) : rel.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** One {@code <sha256>  <path>} line per retained file, in sorted path order. */
    static String manifestOf(ZipFile zip, List<String> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String rel : entries) {
            ZipEntry e = entryFor(zip, rel);
            InputStream in = zip.getInputStream(e);
            try {
                sb.append(hex(sha256(readAll(in)))).append("  ").append(rel).append('\n');
            } finally {
                in.close();
            }
        }
        return sb.toString();
    }

    private static ZipEntry entryFor(ZipFile zip, String rel) throws IOException {
        java.util.Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (rel.equals(stripTopLevel(e.getName()))) {
                return e;
            }
        }
        throw new IOException("Entry vanished from archive: " + rel);
    }

    static void repack(ZipFile zip, List<String> entries, File output) throws IOException {
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output));
        try {
            for (String rel : entries) {
                ZipEntry src = entryFor(zip, rel);
                ZipEntry dst = new ZipEntry(rel);
                dst.setTime(0L);
                out.putNextEntry(dst);
                InputStream in = zip.getInputStream(src);
                try {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                } finally {
                    in.close();
                }
                out.closeEntry();
            }
        } finally {
            out.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            bytes.write(buf, 0, n);
        }
        return bytes.toByteArray();
    }

    private static byte[] sha256(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable", ex);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(Character.forDigit((b[i] >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b[i] & 0xf, 16));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 3: Add the `fetch-uramses` target to `build.xml`**

Insert immediately before the `stage-payloads` target. This one uses plain `<get>`, not the `fetch-asset` macro, because `stepss-uramses` is public and its source archive is not a release asset:

```xml
 <target name="fetch-uramses"
         description="Download the pinned stepss-uramses source archive (public, no gh needed)">
  <mkdir dir="${payload.cache}"/>
  <available file="${payload.cache}/stepss-uramses-${uramses.version}.zip"
             property="uramses.cached"/>
  <get src="${uramses.source.url}"
       dest="${payload.cache}/stepss-uramses-${uramses.version}.zip"
       verbose="true" unless:set="uramses.cached"/>
 </target>
```

The `unless:set` idiom (not a bare `unless`) is required — a bare `unless` attribute fails on Ant 1.10.14. The project element already declares `xmlns:unless="ant:unless"`.

- [ ] **Step 4: Wire `fetch-uramses` and `UramsesKitPack` into `stage-payloads`**

Change `stage-payloads`'s `depends` to `fetch-payloads,fetch-uramses`, and append this after the existing `<copy>`:

```xml
  <javac srcdir="src" destdir="${build.classes.dir}" includeantruntime="false"
         source="${javac.source}" target="${javac.target}"
         includes="my/ramses/platform/UramsesKitPack.java"/>
  <java classname="my.ramses.platform.UramsesKitPack"
        classpath="${build.classes.dir}" fork="true" failonerror="true">
   <arg value="${payload.cache}/stepss-uramses-${uramses.version}.zip"/>
   <arg value="src/my/ramses/payload/uramses-kit-v${uramses.version}.zip"/>
   <arg value="${uramses.manifest.sha256}"/>
  </java>
```

`UramsesKitPack` must be compiled here rather than relying on the main compile, because `stage-payloads` runs before it. It depends on nothing outside `java.*`, so compiling it alone succeeds.

- [ ] **Step 5: Fetch and compute the digest**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant fetch-uramses
```
Expected: `payload-cache/stepss-uramses-3.55.zip` exists, about 12 MB.

Then run the packer in COMPUTE mode:
```bash
mkdir -p build/classes
```
```bash
javac -d build/classes src/my/ramses/platform/UramsesKitPack.java
```
```bash
java -cp build/classes my.ramses.platform.UramsesKitPack payload-cache/stepss-uramses-3.55.zip /dev/null COMPUTE
```
Expected: one line `uramses.manifest.sha256=<64 hex chars>` and a count of retained files. The count should be **around 190** — 3 Makefiles, 8 src files, 4 custom_models files, 9 tools files, 3 kits of 58-59 files each, and 2 top-level docs. A count near 291 means the retain filter is not being applied; a count under 100 means a prefix is wrong.

- [ ] **Step 6: Replace `COMPUTE` with the real digest**

Edit `versions.properties`, replacing `uramses.manifest.sha256=COMPUTE` with the digest printed in Step 5.

- [ ] **Step 7: Verify the full build stages the kit**

```bash
ant clean jar
```
Expected: BUILD SUCCESSFUL, and the log carries a line like `uramses kit OK: 190 files -> .../uramses-kit-v3.55.zip (5xxx KB)`.

```bash
unzip -l src/my/ramses/payload/uramses-kit-v3.55.zip | head -20
```
Expected: entries begin `build/Makefile.linux`, `custom_models/...` — **no** `stepss-uramses-3.55/` prefix, **no** `modules_wi/`, **no** `tests/`.

```bash
ls -lh dist/stepss.jar
```
Expected: about 31 MB, up from 26 MB.

- [ ] **Step 8: Verify the digest actually gates**

Temporarily corrupt the pin:
```bash
sed -i 's/^uramses.manifest.sha256=.*/uramses.manifest.sha256=0000000000000000000000000000000000000000000000000000000000000000/' versions.properties
```
```bash
ant clean jar
```
Expected: BUILD FAILED, with `uramses kit manifest check FAILED` and both digests printed.

Restore the correct digest before continuing:
```bash
git checkout versions.properties
```
Then re-apply Step 1 and Step 6 edits (the `git checkout` discards them). Confirm `ant clean jar` is green again.

- [ ] **Step 9: Commit**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
git add versions.properties build.xml src/my/ramses/platform/UramsesKitPack.java
```
```bash
git commit -m "Fetch, verify and repack the uramses kit at build time

stepss-uramses is public, so this payload needs no gh and no token. Its
source archive is auto-generated by GitHub, which makes no byte-stability
promise, so verification is by a content manifest over the retained files
rather than by the container's digest: re-compression is invisible to it,
a changed byte is not.

The repack drops modules_wi (Intel, unreachable here), tests, examples
and .github, taking a 12 MB archive to about 5 MB staged."
```

---

### Task 3: Tree extraction and the uramses tool spec

**Files:**
- Modify: `src/my/ramses/platform/ToolSpec.java`
- Modify: `src/my/ramses/platform/ToolExtractor.java`
- Modify: `src/my/ramses/platform/Toolchain.java`

**Interfaces:**
- Consumes: the repacked zip from Task 2, whose entries are top-level-stripped relative paths.
- Produces:
  - `ToolSpec.Kind.ZIP_TREE`
  - `new ToolSpec.Payload(String resource, String extractedName, java.util.List<String> retain)` — the ZIP_TREE constructor.
  - `Toolchain.URAMSES` (the String id `"uramses"`)
  - `Toolchain.extractOnDemand(String id)` returning `File`, throwing `IOException`
  - `Toolchain.uramsesKit()` returning `File` (the extracted kit root, or null if not yet extracted)
  - `Toolchain.moduleKitDir(Platform p)` returning `"modules_l"` / `"modules_m"` / `"modules_wg"`

- [ ] **Step 1: Add `ZIP_TREE` and the retain list to `ToolSpec`**

Change the enum and add the field plus a constructor overload:

```java
    public enum Kind { ZIP, TGZ, RAW, ZIP_TREE }

    public static final class Payload {
        public final String resource;
        public final Kind kind;
        public final String member;
        public final String extractedName;
        public final boolean executable;
        /**
         * For {@link Kind#ZIP_TREE} only: the archive-relative path prefixes
         * to unpack. A prefix ending in '/' matches a subtree; anything else
         * must match an entry exactly. Null for every other kind.
         */
        public final java.util.List<String> retain;

        public Payload(String resource, Kind kind, String member,
                       String extractedName, boolean executable) {
            this.resource = resource;
            this.kind = kind;
            this.member = member;
            this.extractedName = extractedName;
            this.executable = executable;
            this.retain = null;
        }

        /** ZIP_TREE payload: unpacks the retained prefixes into {@code extractedName}/. */
        public Payload(String resource, String extractedName, java.util.List<String> retain) {
            this.resource = resource;
            this.kind = Kind.ZIP_TREE;
            this.member = null;
            this.extractedName = extractedName;
            this.executable = false;
            this.retain = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<String>(retain));
        }
    }
```

- [ ] **Step 2: Handle `ZIP_TREE` in `ToolExtractor.unpack`**

Add a case to the switch in `unpack`:

```java
                case ZIP_TREE:
                    unpackZipTree(in, dir, payload);
                    break;
```

and add the method beside `unpackZip`:

```java
    /**
     * Unpacks the entries matching {@code payload.retain} into
     * {@code dir/payload.extractedName/}, preserving their relative paths.
     * Used for the uramses kit, where only the running platform's module
     * directory is wanted: shipping all three costs ~5 MB in the jar but
     * unpacking all three would cost ~35 MB on disk for no gain.
     */
    private static void unpackZipTree(InputStream raw, File dir, ToolSpec.Payload payload)
            throws IOException {
        File root = new File(dir, payload.extractedName);
        mkdirs(root);
        int written = 0;
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw));
        try {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && retains(payload, name)) {
                    File out = safeChild(root, name);
                    mkdirs(out.getParentFile());
                    writeStream(zin, out);
                    written++;
                }
                zin.closeEntry();
            }
        } finally {
            closeQuietly(zin);
        }
        if (written == 0) {
            throw new IOException("Payload '" + payload.resource
                    + "' produced no files for the retained prefixes " + payload.retain
                    + ". The archive layout and the manifest disagree.");
        }
    }

    private static boolean retains(ToolSpec.Payload payload, String name) {
        for (String prefix : payload.retain) {
            if (prefix.endsWith("/") ? name.startsWith(prefix) : name.equals(prefix)) {
                return true;
            }
        }
        return false;
    }
```

`safeChild` already rejects entries escaping the target directory, and it is applied against `root`, so a malicious entry cannot escape even the kit subdirectory.

- [ ] **Step 3: Confirm the existing stamp and delete logic covers a tree**

No change needed, but verify by reading: `extract()` computes `target = new File(dir, payload.extractedName)` — for the kit that is the directory `<dir>/uramses`, and `target.exists()` is true for a directory. `topLevelOf("uramses")` returns `"uramses"`, so the pre-unpack `deleteRecursively` removes exactly the kit directory. The `payload.executable` branch is skipped because the ZIP_TREE constructor sets it false.

- [ ] **Step 4: Add the uramses spec and lazy extraction to `Toolchain`**

Add the constant beside the others:

```java
    public static final String URAMSES = "uramses";
```

Add the spec inside `buildSpecs()`, after the CODEGEN block. The common prefixes repeat per platform because each platform retains a different module kit:

```java
        s.add(new ToolSpec(URAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/uramses-kit-v3.55.zip", "uramses",
                java.util.Arrays.asList("build/", "src/", "custom_models/", "tools/",
                                        "README.md", "LICENSE.rst", "modules_wg/")))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/uramses-kit-v3.55.zip", "uramses",
                java.util.Arrays.asList("build/", "src/", "custom_models/", "tools/",
                                        "README.md", "LICENSE.rst", "modules_l/")))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/uramses-kit-v3.55.zip", "uramses",
                java.util.Arrays.asList("build/", "src/", "custom_models/", "tools/",
                                        "README.md", "LICENSE.rst", "modules_m/"))));
```

Add the lazy set and accessors:

```java
    /**
     * Tools that {@link #extractAll()} deliberately skips. The uramses kit is
     * ~12 MB unpacked and only the Codegen tab's Compile step needs it, so
     * paying for it on every launch would tax every user for a feature most
     * never open.
     */
    private static final java.util.Set<String> LAZY =
            java.util.Collections.singleton(URAMSES);

    /** Extracts one tool on first use and caches the result. */
    public File extractOnDemand(String id) throws IOException {
        File existing = resolved.get(id);
        if (existing != null) {
            return existing;
        }
        File f = ToolExtractor.extract(byId(id), platform, dir);
        resolved.put(id, f);
        return f;
    }

    /** @return the extracted uramses kit root, or null if it has not been extracted yet. */
    public File uramsesKit() {
        return get(URAMSES);
    }

    /** The module-kit directory name inside the uramses kit, per platform. */
    public static String moduleKitDir(Platform p) {
        switch (p) {
            case WINDOWS_X86_64: return "modules_wg";
            case LINUX_X86_64:   return "modules_l";
            case MACOS_ARM64:    return "modules_m";
            default: throw new IllegalArgumentException("No module kit for " + p);
        }
    }
```

and make `extractAll()` skip the lazy set:

```java
    public void extractAll() throws IOException {
        for (ToolSpec spec : SPECS) {
            if (spec.availableOn(platform) && !LAZY.contains(spec.id())) {
                resolved.put(spec.id(), ToolExtractor.extract(spec, platform, dir));
            }
        }
    }
```

- [ ] **Step 5: Verify the build and the manifest check still pass**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant clean jar
```
Expected: BUILD SUCCESSFUL. `PayloadManifestCheck` now also covers `payload/uramses-kit-v3.55.zip`, since the resource string starts with `payload/` — this is the check that catches a `versions.properties` bump that forgets `Toolchain.java`.

- [ ] **Step 6: Verify lazy extraction and the tree layout**

```bash
tools/dump-toolchain.sh
```
Expected: the fingerprint lists `CODEGEN dyngraph dynsim helios` as before and **not** a `uramses` directory — proving `extractAll()` skips it.

Then force one extraction with a throwaway harness:
```bash
cat > /tmp/claude-1000/KitProbe.java <<'EOF'
import my.ramses.platform.*;
import java.io.File;
public class KitProbe {
    public static void main(String[] a) throws Exception {
        File d = new File(a[0]);
        d.mkdirs();
        Toolchain tc = new Toolchain(Platform.current(), d);
        File kit = tc.extractOnDemand(Toolchain.URAMSES);
        System.out.println("kit=" + kit);
        System.out.println("makefile=" + new File(kit, "build/Makefile.linux").isFile());
        System.out.println("router=" + new File(kit, "src/usr_exc_models.f90").isFile());
        System.out.println("libramses=" + new File(kit, Toolchain.moduleKitDir(Platform.current()) + "/libramses.a").isFile());
        System.out.println("no_wi=" + !new File(kit, "modules_wi").exists());
        System.out.println("no_other_kit=" + !new File(kit, "modules_m").exists());
    }
}
EOF
```
```bash
javac -cp build/classes -d /tmp/claude-1000 /tmp/claude-1000/KitProbe.java
```
```bash
java -cp build/classes:dist/stepss.jar:/tmp/claude-1000 KitProbe /tmp/claude-1000/kit-test
```
Expected, on Linux:
```
kit=/tmp/claude-1000/kit-test/uramses
makefile=true
router=true
libramses=true
no_wi=true
no_other_kit=true
```
`no_other_kit=true` is the one that proves per-platform filtering works — only `modules_l` was unpacked.

- [ ] **Step 7: Commit**

```bash
git add src/my/ramses/platform/ToolSpec.java src/my/ramses/platform/ToolExtractor.java src/my/ramses/platform/Toolchain.java
```
```bash
git commit -m "Extract the uramses kit as a filtered tree, on demand

ToolSpec gained a ZIP_TREE payload carrying retained path prefixes, so
the extractor can unpack a subtree rather than a single named member.
Each platform retains only its own module kit: shipping all three costs
about 5 MB in the jar, unpacking all three would cost 35 MB on disk for
nothing.

The kit is also excluded from extractAll(). Only the Codegen tab's
Compile step needs it, so extracting it on every launch would tax every
user for a feature most never open."
```

---

### Task 4: Router splicing

**Files:**
- Create: `src/my/ramses/compile/RouterSplicer.java`
- Create: `src/my/ramses/compile/CompileHarness.java`
- Create: `tools/compile-harness.sh`

**Interfaces:**
- Consumes: `Toolchain.extractOnDemand`, `Toolchain.URAMSES` (Task 3); the markers from Task 1.
- Produces:
  - `RouterSplicer.kindOf(String fileName)` returning one of `exc`/`inj`/`tor`/`twop`/`dctl`, throwing `IllegalArgumentException`
  - `RouterSplicer.modelNameOf(String fileName)` returning e.g. `exc_ENTSOE_lim`
  - `RouterSplicer.routerFor(String kind)` returning e.g. `src/usr_exc_models.f90`
  - `RouterSplicer.splice(String source, String kind, java.util.List<String> models)` returning the spliced text, throwing `IOException` when a marker is absent

- [ ] **Step 1: Write `RouterSplicer`**

```java
package my.ramses.compile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Inserts CODEGEN-generated models into the uramses router files, at the
 * marker comments those files carry.
 *
 * <p>Pure text in, pure text out: no file or process access, so it can be
 * exercised headlessly without a compiler or a kit on disk.
 *
 * <p>Every router names its procedure pointer {@code <kind>_ptr}, so one rule
 * covers all five. The routers differ in ways this class never has to know
 * about - {@code external ::} versus bare {@code external}, {@code modelname4}
 * versus {@code modelname5} - because the markers, not the surrounding syntax,
 * locate both insertion points.
 */
public final class RouterSplicer {

    public static final String EXTERNALS_MARKER = "!<<STEPSS-GUI:EXTERNALS>>";
    public static final String CASES_MARKER = "!<<STEPSS-GUI:CASES>>";

    public static final List<String> KINDS = Collections.unmodifiableList(
            Arrays.asList("exc", "inj", "tor", "twop", "dctl"));

    private RouterSplicer() {
    }

    /**
     * The model kind, taken as the leading segment up to the first underscore.
     *
     * <p>The pre-P1 implementation used {@code substring(0, 3)}, which
     * silently ignored dctl models and throws on any name shorter than three
     * characters. An unrecognised kind is an error here, not a silent skip:
     * a model the user asked for that never reaches the router would compile
     * clean and then fail at run time with an unresolved model name.
     */
    public static String kindOf(String fileName) {
        String base = stripExtension(fileName);
        int underscore = base.indexOf('_');
        if (underscore <= 0) {
            throw new IllegalArgumentException("Model file '" + fileName
                    + "' has no <kind>_<name> prefix. Expected one of " + KINDS + ".");
        }
        String kind = base.substring(0, underscore);
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("Model file '" + fileName
                    + "' has unknown kind '" + kind + "'. Expected one of " + KINDS + ".");
        }
        return kind;
    }

    /** {@code exc_ENTSOE_lim.f90} -> {@code exc_ENTSOE_lim}. */
    public static String modelNameOf(String fileName) {
        return stripExtension(fileName);
    }

    /** Kit-relative path of the router owning a kind. */
    public static String routerFor(String kind) {
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("Unknown model kind: " + kind);
        }
        return "src/usr_" + kind + "_models.f90";
    }

    /**
     * Returns {@code source} with an {@code external} declaration and a
     * select-case entry added for each model.
     *
     * @throws IOException if either marker is missing, which means the kit
     *         predates the marker contract.
     */
    public static String splice(String source, String kind, List<String> models)
            throws IOException {
        requireMarker(source, EXTERNALS_MARKER, kind);
        requireMarker(source, CASES_MARKER, kind);

        StringBuilder externals = new StringBuilder();
        StringBuilder cases = new StringBuilder();
        for (String model : models) {
            externals.append("   external :: ").append(model).append('\n');
            cases.append("      case('").append(model).append("')\n");
            cases.append("         ").append(kind).append("_ptr=>").append(model).append("\n\n");
        }

        String out = insertBefore(source, EXTERNALS_MARKER, externals.toString());
        out = insertBefore(out, CASES_MARKER, cases.toString());
        return out;
    }

    private static void requireMarker(String source, String marker, String kind)
            throws IOException {
        if (source.indexOf(marker) < 0) {
            throw new IOException("Router for kind '" + kind + "' does not carry the "
                    + marker + " marker. The bundled uramses kit predates the marker "
                    + "contract this build requires.");
        }
    }

    /** Inserts {@code text} on its own lines immediately before the marker's line. */
    private static String insertBefore(String source, String marker, String text) {
        int at = source.indexOf(marker);
        int lineStart = source.lastIndexOf('\n', at);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        return source.substring(0, lineStart) + text + source.substring(lineStart);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
```

- [ ] **Step 2: Write `CompileHarness` with the splice checks**

This is the repo's stand-in for unit tests, following P1's `ToolchainDump` precedent. It prints one `PASS`/`FAIL` line per check and exits non-zero if any failed.

```java
package my.ramses.compile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Headless checks for the parts of the compile pipeline that need neither a
 * Fortran toolchain nor an extracted kit. This repository has no unit-test
 * framework and is not gaining one, so this is where splice behaviour is
 * pinned down; the pipeline as a whole is verified behaviourally in the
 * acceptance runs.
 */
public final class CompileHarness {

    private static int failures = 0;

    public static void main(String[] args) {
        checkKindParsing();
        checkSpliceInsertsBothPoints();
        checkSpliceIsIdempotentOverPristineSource();
        checkMissingMarkerFails();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkKindParsing() {
        expect("kind exc", "exc", RouterSplicer.kindOf("exc_ENTSOE_lim.f90"));
        expect("kind twop", "twop", RouterSplicer.kindOf("twop_MY_MODEL.f90"));
        expect("kind dctl", "dctl", RouterSplicer.kindOf("dctl_thing.f90"));
        expect("model name", "exc_ENTSOE_lim", RouterSplicer.modelNameOf("exc_ENTSOE_lim.f90"));
        expect("router path", "src/usr_twop_models.f90", RouterSplicer.routerFor("twop"));
        expectThrows("unknown kind rejected", "zzz_model.f90");
        expectThrows("no underscore rejected", "model.f90");
        expectThrows("empty prefix rejected", "_model.f90");
    }

    private static void checkSpliceInsertsBothPoints() {
        try {
            String out = RouterSplicer.splice(pristine(), "exc",
                    Arrays.asList("exc_ALPHA", "exc_BETA"));
            expect("external alpha", 1, count(out, "external :: exc_ALPHA"));
            expect("external beta", 1, count(out, "external :: exc_BETA"));
            expect("case alpha", 1, count(out, "case('exc_ALPHA')"));
            expect("pointer alpha", 1, count(out, "exc_ptr=>exc_ALPHA"));
            expect("markers survive", 1, count(out, RouterSplicer.CASES_MARKER));
            expect("external precedes marker", true,
                    out.indexOf("external :: exc_ALPHA")
                            < out.indexOf(RouterSplicer.EXTERNALS_MARKER));
            expect("case precedes marker", true,
                    out.indexOf("case('exc_ALPHA')")
                            < out.indexOf(RouterSplicer.CASES_MARKER));
        } catch (Exception ex) {
            fail("splice threw: " + ex);
        }
    }

    /**
     * The GUI resets the kit to pristine before every compile, so splicing
     * twice from pristine must give exactly one entry per model - never two.
     * This is the specific defect the reset exists to prevent.
     */
    private static void checkSpliceIsIdempotentOverPristineSource() {
        try {
            String first = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_ALPHA"));
            String second = RouterSplicer.splice(pristine(), "exc", Arrays.asList("exc_ALPHA"));
            expect("second splice equals first", true, first.equals(second));
            expect("exactly one case", 1, count(second, "case('exc_ALPHA')"));
        } catch (Exception ex) {
            fail("idempotence check threw: " + ex);
        }
    }

    private static void checkMissingMarkerFails() {
        try {
            RouterSplicer.splice("subroutine x\nend subroutine x\n", "exc",
                    Arrays.asList("exc_ALPHA"));
            fail("missing marker should have thrown");
        } catch (java.io.IOException expected) {
            pass("missing marker rejected");
        } catch (Exception ex) {
            fail("wrong exception for missing marker: " + ex);
        }
    }

    /** A minimal stand-in with the same shape as the real router. */
    private static String pristine() {
        List<String> lines = new ArrayList<String>();
        lines.add("subroutine assoc_exciter_ptr(modelname,exc_ptr)");
        lines.add("   external :: exc_KUNDUR");
        lines.add("   " + RouterSplicer.EXTERNALS_MARKER);
        lines.add("   select case (modelname4)");
        lines.add("      case('exc_kundur')");
        lines.add("         exc_ptr=>exc_KUNDUR");
        lines.add("      " + RouterSplicer.CASES_MARKER);
        lines.add("   end select");
        lines.add("end subroutine assoc_exciter_ptr");
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            n++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return n;
    }

    private static void expect(String what, Object want, Object got) {
        if (want.equals(got)) {
            pass(what);
        } else {
            fail(what + ": wanted <" + want + "> got <" + got + ">");
        }
    }

    private static void expectThrows(String what, String fileName) {
        try {
            RouterSplicer.kindOf(fileName);
            fail(what + ": no exception");
        } catch (IllegalArgumentException expected) {
            pass(what);
        }
    }

    private static void pass(String what) {
        System.out.println("PASS  " + what);
    }

    private static void fail(String what) {
        failures++;
        System.out.println("FAIL  " + what);
    }
}
```

- [ ] **Step 3: Write `tools/compile-harness.sh`**

```bash
#!/usr/bin/env bash
# Runs the headless compile-pipeline checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant jar' first." >&2
    exit 1
fi
exec java -cp build/classes my.ramses.compile.CompileHarness
```

Then make it executable:
```bash
chmod +x tools/compile-harness.sh
```

- [ ] **Step 4: Run the harness and confirm every check passes**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant clean jar
```
```bash
tools/compile-harness.sh
```
Expected: a `PASS` line per check and a final `ALL CHECKS PASSED`, exit 0.

- [ ] **Step 5: Verify the splicer against the real router files**

This proves the markers from Task 1 are where the splicer expects. Extract a kit first if `/tmp/claude-1000/kit-test` is gone (Task 3, Step 6 creates it).

```bash
cat > /tmp/claude-1000/SpliceReal.java <<'EOF'
import my.ramses.compile.RouterSplicer;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
public class SpliceReal {
    public static void main(String[] a) throws Exception {
        File kit = new File(a[0]);
        for (String kind : RouterSplicer.KINDS) {
            File r = new File(kit, RouterSplicer.routerFor(kind));
            String src = new String(Files.readAllBytes(r.toPath()), "UTF-8");
            String out = RouterSplicer.splice(src, kind, Arrays.asList(kind + "_PROBE"));
            boolean ok = out.contains("external :: " + kind + "_PROBE")
                      && out.contains(kind + "_ptr=>" + kind + "_PROBE");
            System.out.println((ok ? "PASS  " : "FAIL  ") + kind + " -> " + r.getName());
        }
    }
}
EOF
```
```bash
javac -cp build/classes -d /tmp/claude-1000 /tmp/claude-1000/SpliceReal.java
```
```bash
java -cp build/classes:/tmp/claude-1000 SpliceReal /tmp/claude-1000/kit-test/uramses
```
Expected: five `PASS` lines, one per kind.

If any line reports `FAIL`, or the run throws the "does not carry the marker" `IOException`, the bundled kit predates Task 1's markers. Until uramses tags a release with them, patch the extracted copy by hand and re-run — the pin moves in Task 7.

- [ ] **Step 6: Commit**

```bash
git add src/my/ramses/compile/RouterSplicer.java src/my/ramses/compile/CompileHarness.java tools/compile-harness.sh
```
```bash
git commit -m "Add marker-based router splicing

One rule covers all five routers, because each names its pointer
<kind>_ptr and the markers locate both insertion points regardless of
the surrounding syntax.

Kind parsing takes the segment before the first underscore and rejects
anything unrecognised. The pre-P1 code used substring(0, 3), which
silently ignored dctl models and threw on short names - and a model that
never reaches the router compiles clean, then fails at run time with an
unresolved model name.

CompileHarness is the stand-in for unit tests this repo has no framework
for, including the idempotence check the pristine reset exists to
guarantee."
```

---

### Task 5: Fortran toolchain probe

**Files:**
- Create: `src/my/ramses/compile/FortranToolchain.java`
- Modify: `src/my/ramses/compile/CompileHarness.java`

**Interfaces:**
- Consumes: `Platform`, `PlatformLauncher.findOnPath` (P1); `Toolchain.moduleKitDir` (Task 3).
- Produces:
  - `FortranToolchain.Probe` with public final fields `File make`, `String fc`, `String problem` (null when usable)
  - `FortranToolchain.probe(File kitDir, Platform p)` returning `Probe`
  - `FortranToolchain.moduleAbi(File modFile)` returning `int` (-1 when unreadable)
  - `FortranToolchain.compilerAbi(String fc)` returning `int` (-1 when unreadable)
  - `FortranToolchain.msysRoot()` returning `File` or null

- [ ] **Step 1: Write `FortranToolchain`**

```java
package my.ramses.compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import my.ramses.platform.Platform;
import my.ramses.platform.PlatformLauncher;
import my.ramses.platform.Toolchain;

/**
 * Locates the tools a custom-model build needs and picks a gfortran whose
 * module ABI matches the bundled kit.
 *
 * <p>A GFORTRAN {@code .mod} file can only be read by the compiler generation
 * that wrote it, and the three kits are deliberately not on a common ABI:
 * modules_l is gfortran 13.3 / ABI 15 because it is built on ubuntu-24.04,
 * while modules_m and modules_wg are gfortran 16.1 / ABI 16 because the macOS
 * and Windows runners track newer toolchains. Each platform's default compiler
 * matches its own kit today, so the versioned-compiler scan below is a fallback
 * for hosts where it does not.
 *
 * <p>The definitive check is upstream's {@code make check-deps}, which the
 * caller runs and whose message it surfaces verbatim. This class exists to pick
 * a good {@code FC=} before that runs, so the common "distro ships
 * gfortran-13 but not gfortran" case resolves without user action.
 */
public final class FortranToolchain {

    /** Versioned compilers to try, newest first, when plain gfortran does not fit. */
    private static final int[] CANDIDATE_VERSIONS = {16, 15, 14, 13, 12, 11, 10, 9};

    public static final class Probe {
        public final File make;
        /** The value to pass as {@code FC=}, or null to leave the Makefile default. */
        public final String fc;
        /** Human-readable reason this toolchain is unusable, or null when it is usable. */
        public final String problem;

        Probe(File make, String fc, String problem) {
            this.make = make;
            this.fc = fc;
            this.problem = problem;
        }
    }

    private FortranToolchain() {
    }

    public static Probe probe(File kitDir, Platform p) {
        File make = findMake(p);
        if (make == null) {
            return new Probe(null, null, missingToolMessage("make", p));
        }
        String fc = pickCompiler(kitDir, p);
        if (fc == null) {
            return new Probe(make, null, missingToolMessage("gfortran", p));
        }
        return new Probe(make, fc, null);
    }

    static File findMake(Platform p) {
        File make = PlatformLauncher.findOnPath(p.isWindows() ? "make.exe" : "make");
        if (make != null) {
            return make;
        }
        if (p.isWindows()) {
            File root = msysRoot();
            if (root != null) {
                File candidate = new File(root, "usr\\bin\\make.exe");
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Returns the compiler to use, preferring plain {@code gfortran} when its
     * module ABI matches the kit, then scanning versioned binaries. Returns the
     * plain name when no ABI could be read from either side, matching
     * check_kit.sh's lenient behaviour - the definitive gate is check-deps.
     */
    static String pickCompiler(File kitDir, Platform p) {
        int kitAbi = kitAbi(kitDir, p);
        String plain = p.isWindows() ? "gfortran.exe" : "gfortran";
        File plainFile = PlatformLauncher.findOnPath(plain);

        if (plainFile != null) {
            int abi = compilerAbi(plainFile.getAbsolutePath());
            if (kitAbi < 0 || abi < 0 || abi == kitAbi) {
                return "gfortran";
            }
        }
        for (int i = 0; i < CANDIDATE_VERSIONS.length; i++) {
            String name = "gfortran-" + CANDIDATE_VERSIONS[i] + (p.isWindows() ? ".exe" : "");
            File candidate = PlatformLauncher.findOnPath(name);
            if (candidate != null && compilerAbi(candidate.getAbsolutePath()) == kitAbi) {
                return "gfortran-" + CANDIDATE_VERSIONS[i];
            }
        }
        return plainFile != null ? "gfortran" : null;
    }

    /** The ABI of the kit, read from one of its own .mod files. */
    static int kitAbi(File kitDir, Platform p) {
        File mods = new File(kitDir, Toolchain.moduleKitDir(p));
        File[] found = mods.listFiles(new java.io.FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".mod");
            }
        });
        if (found == null || found.length == 0) {
            return -1;
        }
        java.util.Arrays.sort(found);
        return moduleAbi(found[0]);
    }

    /**
     * Reads the GFORTRAN module ABI integer out of a .mod file. They are
     * gzipped text whose banner carries {@code module version '15'}.
     */
    public static int moduleAbi(File modFile) {
        InputStream in = null;
        try {
            in = new GZIPInputStream(new java.io.FileInputStream(modFile));
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) {
                return -1;
            }
            return parseAbi(new String(buf, 0, n, "UTF-8"));
        } catch (IOException ex) {
            return -1;
        } finally {
            closeQuietly(in);
        }
    }

    /** Compiles a probe module and reads the ABI the compiler emits. */
    public static int compilerAbi(String fc) {
        File tmp = null;
        try {
            tmp = File.createTempFile("stepss-abi", "");
            if (!tmp.delete() || !tmp.mkdir()) {
                return -1;
            }
            File src = new File(tmp, "probe.f90");
            writeText(src, "module probe_mod\nend module probe_mod\n");
            Process proc = new ProcessBuilder(fc, "-c", src.getAbsolutePath(),
                    "-o", new File(tmp, "probe.o").getAbsolutePath(),
                    "-J" + tmp.getAbsolutePath())
                    .redirectErrorStream(true).start();
            drain(proc.getInputStream());
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return -1;
            }
            File mod = new File(tmp, "probe_mod.mod");
            return mod.isFile() ? moduleAbi(mod) : -1;
        } catch (IOException ex) {
            return -1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        } finally {
            deleteRecursively(tmp);
        }
    }

    static int parseAbi(String banner) {
        String key = "module version '";
        int at = banner.indexOf(key);
        if (at < 0) {
            return -1;
        }
        int start = at + key.length();
        int end = banner.indexOf('\'', start);
        if (end < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(banner.substring(start, end).trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** MSYS2 install root on Windows, or null. */
    public static File msysRoot() {
        String env = System.getenv("MSYS2_ROOT");
        if (env != null) {
            File f = new File(env);
            if (f.isDirectory()) {
                return f;
            }
        }
        File standard = new File("C:\\msys64");
        return standard.isDirectory() ? standard : null;
    }

    /** Directories prepended to PATH for the build, so MSYS2's tools are found. */
    public static List<String> extraPathEntries(Platform p) {
        List<String> out = new ArrayList<String>();
        if (p.isWindows()) {
            File root = msysRoot();
            if (root != null) {
                out.add(new File(root, "mingw64\\bin").getAbsolutePath());
                out.add(new File(root, "usr\\bin").getAbsolutePath());
            }
        }
        return out;
    }

    static String missingToolMessage(String tool, Platform p) {
        switch (p) {
            case WINDOWS_X86_64:
                return tool + " was not found. Compiling custom models on Windows needs "
                        + "MSYS2 with the MinGW-w64 toolchain. Install MSYS2 from "
                        + "https://www.msys2.org/ and then, in an MSYS2 shell, run:\n"
                        + "    pacman -S mingw-w64-x86_64-gcc-fortran "
                        + "mingw-w64-x86_64-openblas make\n"
                        + "STEPSS looks for MSYS2 in C:\\msys64, or wherever MSYS2_ROOT points.";
            case MACOS_ARM64:
                return tool + " was not found. Compiling custom models on macOS needs "
                        + "Homebrew's GCC and OpenBLAS, plus the Command Line Tools:\n"
                        + "    brew install gcc openblas\n"
                        + "    xcode-select --install";
            case LINUX_X86_64:
            default:
                return tool + " was not found. Compiling custom models needs gfortran, "
                        + "make and OpenBLAS:\n"
                        + "    Debian/Ubuntu: sudo apt install gfortran make libopenblas-dev\n"
                        + "    Fedora/RHEL:   sudo dnf install gcc-gfortran make openblas-devel\n"
                        + "    Arch:          sudo pacman -S gcc-fortran make openblas";
        }
    }

    private static void writeText(File f, String text) throws IOException {
        java.io.OutputStream out = new java.io.FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static void drain(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        while (r.readLine() != null) {
            // Discard: only the emitted .mod matters.
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignore) {
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                deleteRecursively(kids[i]);
            }
        }
        f.delete();
    }
}
```

- [ ] **Step 2: Add ABI-parsing checks to `CompileHarness`**

Add the call inside `main`, after `checkMissingMarkerFails();`:

```java
        checkAbiParsing();
```

and the method:

```java
    private static void checkAbiParsing() {
        expect("abi 15", 15, FortranToolchain.parseAbi(
                "GFORTRAN module version '15' created from probe.f90"));
        expect("abi 16", 16, FortranToolchain.parseAbi(
                "GFORTRAN module version '16' created from probe.f90"));
        expect("abi absent", -1, FortranToolchain.parseAbi("not a module banner"));
        expect("abi unterminated", -1, FortranToolchain.parseAbi("module version '15"));
    }
```

- [ ] **Step 3: Run the harness**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant clean jar
```
```bash
tools/compile-harness.sh
```
Expected: `ALL CHECKS PASSED`, including the four new ABI lines.

- [ ] **Step 4: Verify the probe against the real machine**

```bash
cat > /tmp/claude-1000/ProbeReal.java <<'EOF'
import my.ramses.compile.FortranToolchain;
import my.ramses.platform.Platform;
import java.io.File;
public class ProbeReal {
    public static void main(String[] a) throws Exception {
        Platform p = Platform.current();
        File kit = new File(a[0]);
        System.out.println("kit ABI     = " + FortranToolchain.kitAbi(kit, p));
        System.out.println("gfortran ABI= " + FortranToolchain.compilerAbi("gfortran"));
        FortranToolchain.Probe probe = FortranToolchain.probe(kit, p);
        System.out.println("make        = " + probe.make);
        System.out.println("FC          = " + probe.fc);
        System.out.println("problem     = " + probe.problem);
    }
}
EOF
```
```bash
javac -cp build/classes -d /tmp/claude-1000 /tmp/claude-1000/ProbeReal.java
```
```bash
java -cp build/classes:/tmp/claude-1000 ProbeReal /tmp/claude-1000/kit-test/uramses
```
Expected on this Linux box: `kit ABI = 15`, `gfortran ABI = 15`, a real path for `make`, `FC = gfortran`, `problem = null`.

- [ ] **Step 5: Verify the missing-tool path**

```bash
env PATH=/nonexistent java -cp build/classes:/tmp/claude-1000 ProbeReal /tmp/claude-1000/kit-test/uramses
```
Expected: `make = null`, `problem` carries the multi-line Linux message naming `apt install gfortran make libopenblas-dev`.

- [ ] **Step 6: Commit**

```bash
git add src/my/ramses/compile/FortranToolchain.java src/my/ramses/compile/CompileHarness.java
```
```bash
git commit -m "Probe for make and an ABI-compatible gfortran

A GFORTRAN .mod file can only be read by the compiler generation that
wrote it, and the three kits are deliberately not on a common ABI: 15 on
Linux, 16 on macOS and Windows. Each platform's default compiler matches
its own kit today, so the versioned-compiler scan is a fallback for hosts
where it does not - the common case being a distro that ships
gfortran-13 but no plain gfortran.

The definitive gate stays upstream's check-deps, whose message the caller
surfaces verbatim so the advice stays correct when upstream changes
compilers."
```

---

### Task 6: The compile pipeline and UI wiring

**Files:**
- Create: `src/my/ramses/compile/ModelCompiler.java`
- Modify: `src/my/ramses/RamsesUI.java`

**Interfaces:**
- Consumes: `RouterSplicer` (Task 4), `FortranToolchain` (Task 5), `Toolchain.extractOnDemand`/`moduleKitDir` (Task 3).
- Produces:
  - `new ModelCompiler(Platform p, Toolchain tc)`
  - `ModelCompiler.prepare(java.util.List<File> generated)` throwing `IOException`
  - `ModelCompiler.build(ModelCompiler.Listener l)` throwing `IOException`
  - `ModelCompiler.Listener` with `void onOutput(String line)` and `void onFinished(int exitCode, File dynsim, String problem)`
  - `ModelCompiler.builtExecutable()` returning `File`

- [ ] **Step 1: Write `ModelCompiler`**

```java
package my.ramses.compile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.environment.EnvironmentUtils;

import my.ramses.platform.Platform;
import my.ramses.platform.Toolchain;

/**
 * Builds a custom {@code dynsim} from CODEGEN-generated models, by driving
 * upstream's gfortran Makefiles against the bundled uramses kit.
 *
 * <p>The build definition lives upstream and is CI-tested on the same runner
 * images that produce the module kits, so the STEPSS route and the documented
 * PyRAMSES route are the same build. This class only stages inputs and runs
 * {@code make}.
 */
public final class ModelCompiler {

    public interface Listener {
        void onOutput(String line);

        /** @param problem null on success; otherwise a user-facing explanation. */
        void onFinished(int exitCode, File dynsim, String problem);
    }

    private final Platform platform;
    private final Toolchain toolchain;
    private File kitDir;
    private File built;

    public ModelCompiler(Platform platform, Toolchain toolchain) {
        this.platform = platform;
        this.toolchain = toolchain;
    }

    /**
     * Extracts the kit, resets it to pristine, stages the generated models and
     * splices them into their routers.
     *
     * <p>The reset is a full re-extraction rather than a targeted restore.
     * Re-splicing an already-spliced router would emit a duplicate case label
     * and fail the build with an error that reads like a user mistake, and a
     * stale {@code Release_*} tree would let a previous build's objects survive
     * into this one. Re-unpacking ~12 MB takes well under a second and makes
     * every compile start from the same state.
     */
    public void prepare(List<File> generated) throws IOException {
        if (generated == null || generated.isEmpty()) {
            throw new IOException("No generated model files to compile. Run Codegen first.");
        }

        File dir = toolchain.directory();
        File existing = new File(dir, "uramses");
        deleteRecursively(existing);
        kitDir = toolchain.extractOnDemand(Toolchain.URAMSES);
        built = null;

        Map<String, List<String>> byKind = new HashMap<String, List<String>>();
        for (File f : generated) {
            String kind;
            try {
                kind = RouterSplicer.kindOf(f.getName());
            } catch (IllegalArgumentException ex) {
                throw new IOException(ex.getMessage(), ex);
            }
            copy(f, new File(new File(kitDir, "custom_models"), f.getName()));
            List<String> models = byKind.get(kind);
            if (models == null) {
                models = new ArrayList<String>();
                byKind.put(kind, models);
            }
            models.add(RouterSplicer.modelNameOf(f.getName()));
        }

        for (Map.Entry<String, List<String>> e : byKind.entrySet()) {
            File router = new File(kitDir, RouterSplicer.routerFor(e.getKey()));
            String source = new String(Files.readAllBytes(router.toPath()), Charset.forName("UTF-8"));
            String spliced = RouterSplicer.splice(source, e.getKey(), e.getValue());
            Files.write(router.toPath(), spliced.getBytes(Charset.forName("UTF-8")));
        }
    }

    /**
     * Runs {@code check-deps} then {@code exe}, asynchronously, reporting
     * through {@code listener}. {@link #prepare} must have run first.
     */
    public void build(final Listener listener) throws IOException {
        if (kitDir == null) {
            throw new IOException("prepare() must run before build()");
        }
        FortranToolchain.Probe probe = FortranToolchain.probe(kitDir, platform);
        if (probe.problem != null) {
            listener.onFinished(-1, null, probe.problem);
            return;
        }

        final File expected = new File(kitDir, releaseDir() + "/dynsim" + platform.exeSuffix());
        CommandLine cmd = new CommandLine(probe.make.getAbsolutePath());
        cmd.addArgument("-f");
        cmd.addArgument("build/Makefile." + platform.key());
        if (probe.fc != null) {
            cmd.addArgument("FC=" + probe.fc);
        }
        cmd.addArgument("check-deps");
        cmd.addArgument("exe");

        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        executor.setWorkingDirectory(kitDir);
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        OutputStream sink = new LineSink(listener);
        executor.setStreamHandler(new PumpStreamHandler(sink, sink));

        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler() {
            @Override
            public void onProcessComplete(int exitValue) {
                built = expected.isFile() ? expected : null;
                if (built != null) {
                    built.setExecutable(true);
                }
                listener.onFinished(exitValue, built, built == null
                        ? "make reported success but " + expected.getAbsolutePath()
                          + " was not produced."
                        : null);
            }

            @Override
            public void onProcessFailed(org.apache.commons.exec.ExecuteException ex) {
                listener.onFinished(ex.getExitValue(), null,
                        "Compilation failed (exit " + ex.getExitValue()
                        + "). The build log above carries the compiler's own message.");
            }
        };
        executor.execute(cmd, buildEnvironment(), handler);
    }

    /** @return the executable produced by the last successful build, or null. */
    public File builtExecutable() {
        return built;
    }

    /** The kit root, or null before {@link #prepare}. Exposed for diagnostics. */
    public File kitDirectory() {
        return kitDir;
    }

    private String releaseDir() {
        switch (platform) {
            case WINDOWS_X86_64: return "Release_wg";
            case LINUX_X86_64:   return "Release_l";
            case MACOS_ARM64:    return "Release_m";
            default: throw new IllegalStateException("No release dir for " + platform);
        }
    }

    /**
     * Child environment for make. On Windows the MSYS2 directories are
     * prepended so make, gfortran, OpenBLAS and the bash that check-deps needs
     * are all resolvable even when STEPSS was launched outside an MSYS2 shell.
     * Returns null elsewhere, which Commons Exec reads as "inherit".
     */
    private Map buildEnvironment() throws IOException {
        List<String> extra = FortranToolchain.extraPathEntries(platform);
        if (extra.isEmpty()) {
            return null;
        }
        Map env = EnvironmentUtils.getProcEnvironment();
        StringBuilder path = new StringBuilder();
        for (String entry : extra) {
            path.append(entry).append(File.pathSeparator);
        }
        path.append((String) env.get("PATH"));
        EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + path);
        return env;
    }

    private static void copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        Files.copy(from.toPath(), to.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                deleteRecursively(kids[i]);
            }
        }
        f.delete();
    }

    /** Splits the merged stdout/stderr stream into lines for the listener. */
    private static final class LineSink extends OutputStream {
        private final Listener listener;
        private final StringBuilder line = new StringBuilder();

        LineSink(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                listener.onOutput(line.toString());
                line.setLength(0);
            } else if (b != '\r') {
                line.append((char) b);
            }
        }

        @Override
        public void close() {
            if (line.length() > 0) {
                listener.onOutput(line.toString());
                line.setLength(0);
            }
        }
    }
}
```

- [ ] **Step 2: Replace `CompileActionPerformed` in `RamsesUI.java`**

Replace the whole stub method (currently at `src/my/ramses/RamsesUI.java:4449`) with:

```java
    private void CompileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CompileActionPerformed
        if (codeGenFiles == null || codeGenFiles.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "<html>No generated models to compile. Run Codegen first.</html>",
                    "Nothing to compile", JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.List<File> generated = new java.util.ArrayList<File>();
        for (File temp : codeGenFiles) {
            String base = temp.getName().replaceFirst("[.][^.]+$", "");
            File f90 = new File(myTempDir, base + ".f90");
            if (!f90.isFile()) {
                JOptionPane.showMessageDialog(this,
                        "<html>Generated source <B>" + base + ".f90</B> was not found."
                        + "<br>Run Codegen again before compiling.</html>",
                        "Generated source missing", JOptionPane.ERROR_MESSAGE);
                return;
            }
            generated.add(f90);
        }

        codegenPane.setText("Preparing the build kit...\n");
        Compile.setEnabled(false);
        modelCompiler = new ModelCompiler(toolchain.platform(), toolchain);
        try {
            modelCompiler.prepare(generated);
        } catch (IOException ex) {
            Compile.setEnabled(true);
            codegenPane.append(ex.getMessage() + "\n");
            JOptionPane.showMessageDialog(this,
                    "<html>Could not prepare the build:<br>" + ex.getMessage() + "</html>",
                    "Compilation failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        codegenPane.append("Building custom simulator with gfortran...\n\n");
        try {
            modelCompiler.build(new ModelCompiler.Listener() {
                public void onOutput(final String line) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            codegenPane.append(line + "\n");
                        }
                    });
                }

                public void onFinished(final int exitCode, final File dynsim,
                        final String problem) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            reportCompileOutcome(exitCode, dynsim, problem);
                        }
                    });
                }
            });
        } catch (IOException ex) {
            Compile.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not start the build:<br>" + ex.getMessage() + "</html>",
                    "Compilation failed", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_CompileActionPerformed

    /**
     * Adopts a freshly built simulator, or reports why there is not one. Always
     * runs on the EDT. On any failure {@code ramsesExec} is left pointing at the
     * bundled engine, so a failed compile never leaves the app unable to
     * simulate.
     */
    private void reportCompileOutcome(int exitCode, File dynsim, String problem) {
        Compile.setEnabled(true);
        if (problem != null || dynsim == null) {
            codegenPane.append("\n" + (problem == null ? "Compilation failed." : problem) + "\n");
            JOptionPane.showMessageDialog(this,
                    "<html>" + (problem == null ? "Compilation failed." : escapeHtml(problem))
                    + "</html>", "Compilation failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ramsesExec = dynsim;
        savedynsim.setEnabled(true);
        codegenPane.append("\nBuild succeeded: " + dynsim.getAbsolutePath() + "\n");
        codegenPane.append("Simulations will now run on this custom simulator.\n");
        JOptionPane.showMessageDialog(this,
                "<html>Custom simulator built.<br>Simulations will now run on it"
                + " instead of the bundled engine.</html>",
                "Compilation complete", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Renders a multi-line plain-text message safely inside an HTML dialog. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\n", "<br>");
    }
```

- [ ] **Step 3: Replace `savedynsimActionPerformed`**

Replace the stub (currently at `src/my/ramses/RamsesUI.java:4513`) with a real save:

```java
    private void savedynsimActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savedynsimActionPerformed
        if (modelCompiler == null || modelCompiler.builtExecutable() == null) {
            JOptionPane.showMessageDialog(this,
                    "<html>No compiled simulator to save. Compile first.</html>",
                    "Nothing to save", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File source = modelCompiler.builtExecutable();
        fileChooser.setSelectedFile(new File(source.getName()));
        fileChooser.setDialogTitle("Save Compiled Simulator");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = fileChooser.getSelectedFile();
        if (dest.exists() && JOptionPane.showConfirmDialog(this, "Overwrite existing file?",
                "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.CANCEL_OPTION) {
            return;
        }
        try {
            fileOps.copyFiletoFile(source, dest);
            dest.setExecutable(true);
            codegenPane.append("Saved simulator to " + dest.getAbsolutePath() + "\n");
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not save the simulator:<br>" + ex.getMessage() + "</html>",
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_savedynsimActionPerformed
```

- [ ] **Step 4: Enable `Compile` after a successful Codegen run, and declare the field**

In `execCodegenActionPerformed`, next to the existing `saveCGFiles.setEnabled(true);` (currently `src/my/ramses/RamsesUI.java:4411`), add:

```java
    Compile.setEnabled(true);
```

Add the field beside `private File codegenExec = null;` (currently line 4569):

```java
    private ModelCompiler modelCompiler = null;
```

Add the import beside the other `my.ramses.platform` imports:

```java
import my.ramses.compile.ModelCompiler;
```

Do **not** touch `RamsesUI.form`: both buttons' initial `enabled="false"` stays exactly as it is, and enabling at runtime needs no form change.

- [ ] **Step 5: Build and confirm the harness still passes**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant clean jar
```
Expected: BUILD SUCCESSFUL.
```bash
tools/compile-harness.sh
```
Expected: `ALL CHECKS PASSED`.

- [ ] **Step 6: Confirm the generated `initComponents()` was not disturbed**

```bash
git diff --stat src/my/ramses/RamsesUI.form
```
Expected: **no output** — the form is untouched.
```bash
git diff src/my/ramses/RamsesUI.java | grep -c "GEN-BEGIN\|GEN-END"
```
Expected: `0` — no edit landed inside a NetBeans-generated block.

- [ ] **Step 7: Commit**

```bash
git add src/my/ramses/compile/ModelCompiler.java src/my/ramses/RamsesUI.java
```
```bash
git commit -m "Wire the Compile button to the gfortran build

prepare() re-extracts the kit rather than restoring parts of it. Splicing
an already-spliced router emits a duplicate case label and fails with an
error that reads like a user mistake, and a stale Release_ tree would let
a previous build's objects survive; re-unpacking ~12 MB costs well under
a second and makes every compile start from the same state.

A failed build leaves ramsesExec pointing at the bundled engine, so a
compile failure never leaves the app unable to simulate."
```

---

### Task 7: Acceptance, pin update and documentation

**Files:**
- Modify: `README.md`
- Modify: `versions.properties` (only if uramses tags a marker-carrying release)
- Create: `docs/superpowers/plans/compile-acceptance-results.md`

**Interfaces:**
- Consumes: everything above.
- Produces: `docs/superpowers/plans/compile-acceptance-results.md`, recording `COMPILE_ACCEPTANCE: pass` or `pass-with-gaps` plus a per-row status, in the shape of P1's `platform-matrix-results.md`.

- [ ] **Step 1: Run the Linux round trip**

Launch the GUI:
```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
java -jar dist/stepss.jar
```

In the Codegen tab: *Load Files for Codegen* → select `../stepss-uramses/custom_models/exc_ENTSOE_lim.txt` → *Run Codegen* → confirm `exc_ENTSOE_lim.f90` appears in the pane and *Compile* becomes enabled → *Compile*.

Expected: the pane streams `check-deps` output including `OK: module version 15 matches gfortran`, then compiler lines, then `Build succeeded: .../uramses/Release_l/dynsim`, and a dialog saying simulations will now use it.

Record the result.

- [ ] **Step 2: Run a simulation on the custom engine**

Load a Nordic case from `../stepss-test-systems`, whose `.dat` names `exc_ENTSOE_lim` on at least one generator, and run a dynamic simulation.

Expected: it runs to completion and writes trajectories. This is what proves the spliced router actually resolved the model — a model missing from the router fails at run time with an unresolved model name, which is exactly the failure the strict `kindOf` guards against.

- [ ] **Step 3: Verify idempotence on the real pipeline**

Without restarting, press *Compile* a second time.

Expected: it succeeds again. Then confirm exactly one case survived:
```bash
grep -c "case('exc_ENTSOE_lim')" ~/.stepss/tools/uramses/src/usr_exc_models.f90
```
(Substitute the tool directory the fingerprint reports if it differs.)
Expected: `1`. A `2` means the pristine reset is not working and Task 6's `prepare()` is at fault.

- [ ] **Step 4: Run the no-regression trajectory comparison**

Compile with **no** custom models registered is not possible through the UI, so compare the custom-built engine against the bundled one on a case that uses only built-in models:

```bash
cd /home/apetros/Code/stepss/stepss-uramses
```
```bash
python3 tools/compare_trj.py <bundled-engine-output.trj> <custom-engine-output.trj>
```
Expected: differences within the script's tolerance. Both engines are RAMSES 3.55 from the same commit, so a real difference indicates a mis-specified build rather than an engine difference.

Record the tolerance the script reports.

- [ ] **Step 5: Verify the two failure paths**

Missing compiler:
```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
env PATH=/usr/bin:/bin java -jar dist/stepss.jar
```
with gfortran removed from those directories, or more simply launch with `PATH` pointing somewhere without gfortran. Press *Compile*.
Expected: a dialog naming gfortran and the `apt install` line; `ramsesExec` still runs the bundled engine (verify by running a simulation afterwards).

ABI mismatch: install or point at a gfortran of a different generation and confirm `check-deps` fails with upstream's remedy text in the pane.

- [ ] **Step 6: Update the pin if uramses has tagged the markers**

If `stepss-uramses` has published a release carrying Task 1's markers, update `versions.properties`:

```bash
ant fetch-uramses
```
after changing `uramses.version`, `uramses.tag` and `uramses.source.url` to the new tag, then recompute the digest exactly as in Task 2, Step 5, and set `uramses.manifest.sha256`.

If it has not, leave the pin at `v3.55` and record in the results file that the shipped kit still needs a hand-applied marker patch — this is a known gap, not an oversight.

- [ ] **Step 7: Update the README**

Change the two rows in the *Bundled tools* table:

```markdown
| Model compilation | Custom models | yes (MSYS2/MinGW) | yes (gfortran) | yes (Homebrew gcc) |
```

Change the Features bullet:

```markdown
- **User models**: the Codegen tab generates user-written model source with CODEGEN and compiles it into a custom simulator with gfortran
```

Add to *Installation*, after the existing macOS paragraph:

```markdown
Compiling custom models is optional and needs a Fortran toolchain on your machine: `gfortran`, GNU `make`, and OpenBLAS. On Debian/Ubuntu that is `sudo apt install gfortran make libopenblas-dev`; on macOS `brew install gcc openblas`; on Windows install [MSYS2](https://www.msys2.org/) and run `pacman -S mingw-w64-x86_64-gcc-fortran mingw-w64-x86_64-openblas make` (STEPSS looks in `C:\msys64`, or wherever `MSYS2_ROOT` points). The bundled module kits are gfortran-ABI-specific and each platform's default compiler matches its own kit; if yours does not, STEPSS reports the exact compiler version to install. Everything else in STEPSS works without any of this.
```

- [ ] **Step 8: Write the acceptance results file**

Create `docs/superpowers/plans/compile-acceptance-results.md` with a verdict line (`COMPILE_ACCEPTANCE: pass` or `pass-with-gaps`) and a table with one row per check from Steps 1-5, plus rows for Windows and macOS marked with the hardware they need. Follow the shape of `docs/superpowers/plans/platform-matrix-results.md`.

State plainly which rows were not run and why. Windows and macOS have no hardware here, exactly as in P1.

- [ ] **Step 9: Final build and commit**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant clean jar
```
```bash
tools/compile-harness.sh
```
Expected: BUILD SUCCESSFUL and `ALL CHECKS PASSED`.

```bash
git add README.md versions.properties docs/superpowers/plans/compile-acceptance-results.md
```
```bash
git commit -m "Record compile acceptance and document the toolchain prerequisite

Compiling custom models is optional and needs gfortran, make and
OpenBLAS on the user's machine; everything else in STEPSS works without
them, so the README says so rather than listing it as a requirement.

Windows and macOS rows are unrun for want of hardware, matching P1's
platform matrix."
```

---

## Self-Review

**Spec coverage.** Every section of the spec maps to a task: the marker patch and router normalisation to Task 1; the payload block, `UramsesKitPack`, manifest verification and repack to Task 2; `Kind.ZIP_TREE`, per-platform kit filtering and lazy extraction to Task 3; reset/stage/splice and the strict kind parsing to Tasks 4 and 6; the probe, `check-deps` and the versioned-compiler scan to Task 5; build, adopt and the error dialogs to Task 6; all five verification items, the prerequisites table and the deferred pin to Task 7. The spec's Windows MSYS2 PATH handling is in Task 5 (`extraPathEntries`) and Task 6 (`buildEnvironment`).

**Placeholder scan.** One value is deliberately deferred: `uramses.manifest.sha256=COMPUTE` in Task 2, Step 1, which Step 6 replaces with the digest Step 5 prints. It cannot be known before the archive is fetched and hashed, and the two-phase bootstrap is spelled out rather than left as a TODO. Task 7 Step 4's tolerance and Step 8's per-row statuses are outputs to record, not inputs to invent.

**Type consistency.** `RouterSplicer.kindOf/modelNameOf/routerFor/splice` are used with those exact names and signatures in `CompileHarness`, `ModelCompiler` and the Task 4 verification harness. `Toolchain.extractOnDemand`, `Toolchain.URAMSES` and `Toolchain.moduleKitDir` are defined in Task 3 and consumed in Tasks 5 and 6. `FortranToolchain.Probe`'s three public fields (`make`, `fc`, `problem`) are read in `ModelCompiler.build` exactly as declared. `ModelCompiler.Listener.onFinished(int, File, String)` carries the same three-argument shape at its definition, its anonymous implementation in `RamsesUI`, and the `reportCompileOutcome` call it delegates to.

**One risk the plan cannot remove.** Task 4, Step 5 and all of Task 7 depend on the bundled kit carrying Task 1's markers. Until `stepss-uramses` tags a release with them, the extracted kit must be patched by hand for those steps. Every task before that point is independent of the tag.
