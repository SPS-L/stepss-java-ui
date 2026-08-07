# Cross-Platform Toolchain Layer (P1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `stepss.jar` run on Windows x86_64, Linux x86_64 and macOS arm64 by replacing 34 scattered OS checks with one platform resolver and one tool manifest, sourcing all payloads from GitHub releases, and replacing PFC with helios.

**Architecture:** A new `my.ramses.platform` package holds a `Platform` enum resolved once at startup, a `ToolSpec` descriptor per bundled tool, a `Toolchain` manifest, a single generic `ToolExtractor`, and a `PlatformLauncher` for editor/terminal/process operations. `fileOps.java` shrinks from six near-identical extract methods to one call site; `RamsesUI.java` loses its 28 branch bodies. An Ant target fetches release archives into a gitignored cache and verifies SHA-256 before packaging.

**Tech Stack:** Java 11 (source/target per `nbproject/project.properties`), Apache Ant 1.10, Swing, Apache Commons Exec 1.3, Apache Commons IO 2.11.0. No unit-test framework exists in this repo and none is added — see Verification Strategy.

## Global Constraints

- Java source/target level is **11**. No records, no text blocks, no `switch` expressions.
- Package for new classes: **`my.ramses.platform`**. Existing code stays in `my.ramses`.
- Supported platforms are exactly **Windows x86_64, Linux x86_64, macOS arm64**. Intel Macs and 32-bit are unsupported and must be reported, not silently attempted.
- Pinned component versions: **ramses 3.55, helios 1.2.0, dyngraph 1.1.0, codegen 5.1.0, uramses 3.55** (commit `994154373cb9bde833a520d257b0aac79a2227e8`).
- Windows dyngraph keeps the **committed Intel dialog build** (`src/my/ramses/dyngraph.exe`). It is the one payload not sourced from a release.
- The Codegen **Compile** step stays Windows/Intel in P1. The gfortran migration is P2.
- Every extraction failure must produce a dialog naming **tool, platform and path**. No swallowed exceptions.
- The tool extraction directory must **never** be the user's selected working directory.
- Existing GUI behavior on Windows and Linux must be unchanged through Task 9. Tasks 10+ introduce behavior changes.
- Do not commit the working tree's existing uncommitted 3.55 payload changes; they are superseded by Task 11.

## Verification Strategy

This repo has no test framework, and the deliverable is a Swing GUI that cannot be
driven headlessly without significant new infrastructure. Adding JUnit plus a GUI
driver is out of scope for P1. Verification instead rests on three mechanisms, each
of which is a real, runnable artifact produced by this plan:

1. **Extraction fingerprint diff** (`tools/dump-toolchain.sh`, Task 3). A headless
   `main` resolves the platform, extracts the full toolchain to a temp directory and
   prints a sorted listing of relative path, SHA-256 and executable bit. Running it
   before and after the refactor and diffing is the no-op proof for the extraction
   layer — the part that is deterministic and scriptable.
2. **Helios differential test** (`tools/diff-pfc-helios.sh`, Task 13). Both engines
   accept `-t cmd.txt`, so the GUI-generated command file can be run through each and
   the five result files diffed without involving the GUI.
3. **Manual GUI smoke checklist** (Task 9 and Task 15). The parts that genuinely need
   a human at a window — launching an editor, opening a terminal, watching curves —
   are enumerated as explicit steps with expected outcomes, not left to judgement.

Where a task below says "run the test", it means one of these three. No task claims a
unit test that does not exist.

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `src/my/ramses/platform/Platform.java` | Enum + one-time detection incl. Rosetta; `UnsupportedPlatformException` |
| `src/my/ramses/platform/ToolSpec.java` | Immutable per-tool, per-platform payload descriptor |
| `src/my/ramses/platform/Toolchain.java` | The manifest; extraction orchestration; resolved `File` accessors |
| `src/my/ramses/platform/ToolExtractor.java` | The single generic extract routine + version stamping |
| `src/my/ramses/platform/PlatformLauncher.java` | Editor, terminal, file manager, process kill, exec environment |
| `src/my/ramses/platform/ToolchainDump.java` | Headless fingerprint harness for the no-op proof |
| `versions.properties` | Pinned versions + SHA-256 per release asset |
| `tools/dump-toolchain.sh` | Wrapper that builds and runs `ToolchainDump` |
| `tools/diff-pfc-helios.sh` | Helios differential test |
| `tools/build-uramses-kit.sh` | Assembles `URAMSES.zip` from the pinned uramses tag |

**Modified:**

| File | Change |
|---|---|
| `src/my/ramses/fileOps.java:76-258` | Six `extractXxx` methods deleted; file reduced to zip/copy helpers |
| `src/my/ramses/RamsesUI.java` | 28 OS branch sites migrated; 7 `File` fields replaced by `Toolchain` |
| `build.xml` | `fetch-payloads` target; `-pre-compile` dependency; uramses kit target |
| `.gitignore` | Add `/payload-cache/` |
| `README.md` | Platform support table; build prerequisites |

**Deleted:** `src/my/ramses/npp.zip`, `nppLicense.txt`, `PFC`, `PFC.exe`, `pfcLicense.txt`, `dynsim.zip`, `codegen.exe`, `CODEGEN`, `dyngraph` (Linux), `URAMSES.zip`.

---

### Task 1: Gatekeeper spike on macOS

This runs first because its outcome can change `ToolExtractor`'s design. It produces a
written finding, not shipped code.

**Files:**
- Create: `docs/superpowers/specs/2026-08-06-gatekeeper-spike-result.md`

**Interfaces:**
- Consumes: nothing.
- Produces: a documented yes/no that Task 5 reads — whether `ToolExtractor` must clear
  `com.apple.quarantine` on extracted executables.

- [ ] **Step 1: Build a minimal probe jar on any platform**

```bash
mkdir -p /tmp/gk/my/probe
cd /tmp/gk
curl -sL -o my/probe/payload.bin \
  https://github.com/SPS-L/stepss-codegen/releases/download/v5.1.0/codegen-macos-arm64-v5.1.0.tar.gz
cat > Probe.java <<'EOF'
import java.io.*;
import java.nio.file.*;
public class Probe {
    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("gkprobe");
        Path out = dir.resolve("payload.tar.gz");
        try (InputStream in = Probe.class.getResourceAsStream("/my/probe/payload.bin")) {
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("EXTRACTED: " + out.toAbsolutePath());
    }
}
EOF
javac -d . Probe.java
jar cfe probe.jar Probe Probe.class my/probe/payload.bin
```

- [ ] **Step 2: Transfer probe.jar to an Apple Silicon Mac and download it via a browser**

The jar must arrive by browser download, not `scp`, so that the jar itself carries the
quarantine attribute. That is the case being tested: whether quarantine propagates from
a downloaded jar to files the JVM writes.

- [ ] **Step 3: Run and inspect the extracted file**

```bash
xattr -p com.apple.quarantine ~/Downloads/probe.jar    # expect: a quarantine string
java -jar ~/Downloads/probe.jar                         # prints EXTRACTED: <path>
tar xzf <path> -C "$(dirname <path>)"
xattr -l "$(dirname <path>)/CODEGEN"
chmod +x "$(dirname <path>)/CODEGEN"
"$(dirname <path>)/CODEGEN" -v
```

Expected outcomes, one of:
- **No quarantine attribute and `CODEGEN -v` prints a version** → extractor needs nothing.
- **Quarantine present or Gatekeeper blocks the run** → extractor must run
  `xattr -dr com.apple.quarantine <dir>` on the extraction directory after unpacking.

- [ ] **Step 4: Record the finding**

Write `docs/superpowers/specs/2026-08-06-gatekeeper-spike-result.md` containing: the
macOS version tested, the exact `xattr -l` output, whether `CODEGEN -v` ran, and a
one-line verdict `QUARANTINE_CLEARING_REQUIRED: yes|no`.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-08-06-gatekeeper-spike-result.md
git commit -m "docs: record macOS Gatekeeper quarantine spike result"
```

---

### Task 2: Payload fetch infrastructure

**Files:**
- Create: `versions.properties`
- Modify: `build.xml` (add `fetch-payloads` target)
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces: a populated `payload-cache/` directory keyed by exact asset filename, which
  Task 11 and Task 13 embed as jar resources. Ant property names
  `ramses.version`, `helios.version`, `dyngraph.version`, `codegen.version`,
  `uramses.commit` are readable by later targets.

- [ ] **Step 1: Create versions.properties with real pinned digests**

```properties
# Pinned component releases. Update = bump version + digest, then `ant fetch-payloads`.
ramses.version=3.55
ramses.repo=SPS-L/stepss-ramses
ramses.tag=v3.55
ramses.windows.asset=ramses-windows-x86_64-v3.55.zip
ramses.windows.sha256=8a14d91dc36052ec9601319120c23d6fd0239efdd8e54c82ef6691ebf624bfce
ramses.linux.asset=ramses-linux-x86_64-v3.55.tar.gz
ramses.linux.sha256=0e8470e10379043fb817ad3b7d035d548f23a52dc4b378dcfdf87a38eb50b596
ramses.macos.asset=ramses-macos-arm64-v3.55.tar.gz
ramses.macos.sha256=34e214666f68bdb5fdc8c608cf75c63d86ca25a7b1e8de038313be1b0b9b68b0

helios.version=1.2.0
helios.repo=SPS-L/stepss-helios
helios.tag=v1.2.0
helios.windows.asset=stepss-helios-windows-x64.zip
helios.windows.sha256=ab21785deefc6a6932fd5541df59b43931051957a51b904a0bbaeb91214fdda7
helios.linux.asset=stepss-helios-linux-x86_64.tar.gz
helios.linux.sha256=a2acc0b12c97a19a941d3f4bdccd6926654e8ff67cf968f9a4f1ae5be476a63b
# NOTE: helios ships macos-universal until its next release, which will be macos-arm64.
# When that lands, change the two lines below to stepss-helios-macos-arm64.tar.gz + new digest.
helios.macos.asset=stepss-helios-macos-universal.tar.gz
helios.macos.sha256=9df70e7a606dede30a1affe950fc57ab3615d9b3930fe26103cf74a05dc245d6

dyngraph.version=1.1.0
dyngraph.repo=SPS-L/stepss-dyngraph
dyngraph.tag=v1.1.0
dyngraph.linux.asset=dyngraph-linux-x86_64-v1.1.0.tar.gz
dyngraph.linux.sha256=58e5b5a0a43045e3fb9414fe2e5253a6d722c72f532ac5aa95d7358f57f2c62f
dyngraph.macos.asset=dyngraph-macos-arm64-v1.1.0.tar.gz
dyngraph.macos.sha256=d6ae0ef1f67258918b734206611bd333f754de7958fff1c5720d8e226185fe13
# Windows dyngraph is the committed Intel dialog build, not a release asset.

codegen.version=5.1.0
codegen.repo=SPS-L/stepss-codegen
codegen.tag=v5.1.0
codegen.windows.asset=codegen-windows-x86_64-v5.1.0.zip
codegen.windows.sha256=f131bf071662ae1e3d5874c1198e811d1e22823ab263fabd1160a227d3605b68
codegen.linux.asset=codegen-linux-x86_64-v5.1.0.tar.gz
codegen.linux.sha256=2a89fa940f80bb2a427b3b436938d5554aee448dc76fb10b08763f601878e8d6
codegen.macos.asset=codegen-macos-arm64-v5.1.0.tar.gz
codegen.macos.sha256=f0957053c67d84fb94bd44382e2c388c1a4be70ff789de2c715eaefc86ca4f52

uramses.repo=SPS-L/stepss-uramses
uramses.tag=v3.55
uramses.commit=994154373cb9bde833a520d257b0aac79a2227e8
```

- [ ] **Step 2: Add the cache directory to .gitignore**

Append to `.gitignore`:

```
# Fetched release payloads (see versions.properties)
/payload-cache/
```

- [ ] **Step 3: Add the fetch-payloads target to build.xml**

Insert before the closing `</project>` tag in `build.xml`:

```xml
 <property file="versions.properties"/>
 <property name="payload.cache" location="payload-cache"/>

 <macrodef name="fetch-asset">
  <attribute name="repo"/>
  <attribute name="tag"/>
  <attribute name="asset"/>
  <attribute name="sha256"/>
  <sequential>
   <mkdir dir="${payload.cache}"/>
   <get src="https://github.com/@{repo}/releases/download/@{tag}/@{asset}"
        dest="${payload.cache}/@{asset}" skipexisting="true" verbose="false"/>
   <checksum file="${payload.cache}/@{asset}" algorithm="SHA-256"
             property="@{asset}.actual"/>
   <fail message="Digest mismatch for @{asset}: expected @{sha256}, got ${@{asset}.actual}">
    <condition>
     <not><equals arg1="${@{asset}.actual}" arg2="@{sha256}"/></not>
    </condition>
   </fail>
   <echo message="verified @{asset}"/>
  </sequential>
 </macrodef>

 <target name="fetch-payloads" description="Download and verify pinned release payloads">
  <fetch-asset repo="${ramses.repo}" tag="${ramses.tag}"
               asset="${ramses.windows.asset}" sha256="${ramses.windows.sha256}"/>
  <fetch-asset repo="${ramses.repo}" tag="${ramses.tag}"
               asset="${ramses.linux.asset}" sha256="${ramses.linux.sha256}"/>
  <fetch-asset repo="${ramses.repo}" tag="${ramses.tag}"
               asset="${ramses.macos.asset}" sha256="${ramses.macos.sha256}"/>
  <fetch-asset repo="${helios.repo}" tag="${helios.tag}"
               asset="${helios.windows.asset}" sha256="${helios.windows.sha256}"/>
  <fetch-asset repo="${helios.repo}" tag="${helios.tag}"
               asset="${helios.linux.asset}" sha256="${helios.linux.sha256}"/>
  <fetch-asset repo="${helios.repo}" tag="${helios.tag}"
               asset="${helios.macos.asset}" sha256="${helios.macos.sha256}"/>
  <fetch-asset repo="${dyngraph.repo}" tag="${dyngraph.tag}"
               asset="${dyngraph.linux.asset}" sha256="${dyngraph.linux.sha256}"/>
  <fetch-asset repo="${dyngraph.repo}" tag="${dyngraph.tag}"
               asset="${dyngraph.macos.asset}" sha256="${dyngraph.macos.sha256}"/>
  <fetch-asset repo="${codegen.repo}" tag="${codegen.tag}"
               asset="${codegen.windows.asset}" sha256="${codegen.windows.sha256}"/>
  <fetch-asset repo="${codegen.repo}" tag="${codegen.tag}"
               asset="${codegen.linux.asset}" sha256="${codegen.linux.sha256}"/>
  <fetch-asset repo="${codegen.repo}" tag="${codegen.tag}"
               asset="${codegen.macos.asset}" sha256="${codegen.macos.sha256}"/>
 </target>
```

- [ ] **Step 4: Run the fetch and verify all eleven assets land**

Run: `ant fetch-payloads`
Expected: eleven `verified <asset>` lines, `BUILD SUCCESSFUL`, and:

```bash
ls payload-cache | wc -l    # expect 11
```

- [ ] **Step 5: Verify a bad digest fails the build**

Temporarily change `codegen.linux.sha256` in `versions.properties` to
`0000000000000000000000000000000000000000000000000000000000000000`, delete
`payload-cache/codegen-linux-x86_64-v5.1.0.tar.gz`, then:

Run: `ant fetch-payloads`
Expected: `BUILD FAILED` with `Digest mismatch for codegen-linux-x86_64-v5.1.0.tar.gz`.
Then restore the correct digest and re-run to confirm success.

- [ ] **Step 6: Commit**

```bash
git add versions.properties build.xml .gitignore
git commit -m "build: fetch pinned release payloads with SHA-256 verification"
```

---

### Task 3: Platform enum and detection

**Files:**
- Create: `src/my/ramses/platform/Platform.java`
- Create: `src/my/ramses/platform/UnsupportedPlatformException.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `Platform.current()` → `Platform`, throws `UnsupportedPlatformException`
  - `Platform.key()` → `String` (`"windows"` | `"linux"` | `"macos"`)
  - `Platform.isWindows()` → `boolean`
  - `Platform.exeSuffix()` → `String` (`".exe"` or `""`)
  - `Platform.describe()` → `String` (human-readable `os.name`/`os.arch` for dialogs)
  - `UnsupportedPlatformException.getMessage()` → dialog-ready text

- [ ] **Step 1: Create UnsupportedPlatformException**

```java
package my.ramses.platform;

public class UnsupportedPlatformException extends Exception {
    public UnsupportedPlatformException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create Platform**

```java
package my.ramses.platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public enum Platform {
    WINDOWS_X86_64("windows"),
    LINUX_X86_64("linux"),
    MACOS_ARM64("macos");

    private final String key;
    private static Platform resolved;

    Platform(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean isWindows() {
        return this == WINDOWS_X86_64;
    }

    public String exeSuffix() {
        return this == WINDOWS_X86_64 ? ".exe" : "";
    }

    public static String describe() {
        return System.getProperty("os.name", "?") + " / " + System.getProperty("os.arch", "?");
    }

    public static synchronized Platform current() throws UnsupportedPlatformException {
        if (resolved == null) {
            resolved = detect();
        }
        return resolved;
    }

    private static Platform detect() throws UnsupportedPlatformException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.US);

        if (os.contains("mac") || os.contains("darwin")) {
            if (arch.equals("aarch64") || arch.equals("arm64")) {
                return MACOS_ARM64;
            }
            if (isRosettaTranslated()) {
                return MACOS_ARM64;
            }
            throw new UnsupportedPlatformException(
                    "STEPSS supports Apple Silicon Macs only.\n"
                    + "Detected: " + describe() + "\n"
                    + "Intel Macs are not supported by the bundled simulation engine.");
        }
        if (os.contains("win")) {
            if (!arch.contains("64")) {
                throw new UnsupportedPlatformException(
                        "STEPSS requires 64-bit Windows.\nDetected: " + describe());
            }
            return WINDOWS_X86_64;
        }
        if (os.contains("linux")) {
            if (!arch.contains("64")) {
                throw new UnsupportedPlatformException(
                        "STEPSS requires 64-bit Linux.\nDetected: " + describe());
            }
            return LINUX_X86_64;
        }
        throw new UnsupportedPlatformException(
                "STEPSS supports Windows, Linux and macOS only.\nDetected: " + describe());
    }

    /**
     * An x86_64 JVM running under Rosetta on Apple Silicon reports os.arch=x86_64,
     * which is indistinguishable from a genuine Intel Mac by os.arch alone.
     * sysctl.proc_translated returns 1 only when the current process is translated.
     */
    private static boolean isRosettaTranslated() {
        BufferedReader reader = null;
        try {
            Process p = new ProcessBuilder("sysctl", "-n", "sysctl.proc_translated")
                    .redirectErrorStream(true).start();
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            return line != null && line.trim().equals("1");
        } catch (Exception ex) {
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify it compiles and resolves correctly on this machine**

```bash
mkdir -p /tmp/ptest
javac -d /tmp/ptest src/my/ramses/platform/Platform.java \
                    src/my/ramses/platform/UnsupportedPlatformException.java
cat > /tmp/ptest/Check.java <<'EOF'
import my.ramses.platform.Platform;
public class Check {
    public static void main(String[] a) throws Exception {
        System.out.println(Platform.describe() + " -> " + Platform.current()
                + " key=" + Platform.current().key()
                + " exe='" + Platform.current().exeSuffix() + "'");
    }
}
EOF
javac -cp /tmp/ptest -d /tmp/ptest /tmp/ptest/Check.java
java -cp /tmp/ptest Check
```

Expected on the Linux dev machine: `Linux / amd64 -> LINUX_X86_64 key=linux exe=''`

- [ ] **Step 4: Verify the unsupported path produces a usable message**

```bash
java -cp /tmp/ptest -Dos.name="Mac OS X" -Dos.arch="x86_64" Check
```

Expected: an exception whose message names Apple Silicon and echoes the detected
platform. (On a non-Mac host `sysctl` is absent, so `isRosettaTranslated()` returns
false and the Intel-Mac branch is taken, which is what we want to see.)

- [ ] **Step 5: Commit**

```bash
git add src/my/ramses/platform/Platform.java \
        src/my/ramses/platform/UnsupportedPlatformException.java
git commit -m "feat: add Platform enum with Rosetta-aware detection"
```

---

### Task 4: ToolSpec descriptor and Toolchain manifest

**Files:**
- Create: `src/my/ramses/platform/ToolSpec.java`
- Create: `src/my/ramses/platform/Toolchain.java`

**Interfaces:**
- Consumes: `Platform` from Task 3.
- Produces:
  - `ToolSpec.Kind` enum: `ZIP`, `TGZ`, `RAW`
  - `ToolSpec.Payload` with fields `resource`, `kind`, `member`, `extractedName`, `executable`
  - `ToolSpec.id()` → `String`; `ToolSpec.payloadFor(Platform)` → `Payload` or `null`
  - `Toolchain.SPECS` → `List<ToolSpec>`
  - `Toolchain.byId(String)` → `ToolSpec`
  - `Toolchain` instance accessors used by Task 8: `ramses()`, `helios()`, `dyngraph()`,
    `codegen()`, `gnuplot()`, `vswhere()`, `userGuide()`, each returning `File`

The `member` field is the path of the wanted file **inside** the archive. It is
required because layouts differ: `ramses-*.tar.gz` is flat (`ramses`),
`stepss-helios-*.tar.gz` nests (`stepss-helios-linux-x86_64/helios`), and
`stepss-helios-windows-x64.zip` is flat (`helios.exe`). A `null` member means
"unpack the whole archive", used by `gpwin.zip`, `DOC.zip` and `URAMSES.zip`.

- [ ] **Step 1: Create ToolSpec**

```java
package my.ramses.platform;

import java.util.HashMap;
import java.util.Map;

public final class ToolSpec {

    public enum Kind { ZIP, TGZ, RAW }

    public static final class Payload {
        public final String resource;
        public final Kind kind;
        public final String member;
        public final String extractedName;
        public final boolean executable;

        public Payload(String resource, Kind kind, String member,
                       String extractedName, boolean executable) {
            this.resource = resource;
            this.kind = kind;
            this.member = member;
            this.extractedName = extractedName;
            this.executable = executable;
        }
    }

    private final String id;
    private final Map<Platform, Payload> payloads = new HashMap<Platform, Payload>();

    public ToolSpec(String id) {
        this.id = id;
    }

    public ToolSpec on(Platform p, Payload payload) {
        payloads.put(p, payload);
        return this;
    }

    public String id() {
        return id;
    }

    public Payload payloadFor(Platform p) {
        return payloads.get(p);
    }

    public boolean availableOn(Platform p) {
        return payloads.containsKey(p);
    }
}
```

- [ ] **Step 2: Create Toolchain with the manifest**

Note the manifest is written for the *post-swap* payloads (Task 11/13). Until then the
resource names below do not exist in the jar. Task 7 therefore wires `Toolchain` in
against the **current** resources, and Task 11 rewrites this table. Write it now with
the current resource names so Task 7 is a true no-op:

```java
package my.ramses.platform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Toolchain {

    public static final String RAMSES = "ramses";
    public static final String PFC = "pfc";
    public static final String DYNGRAPH = "dyngraph";
    public static final String CODEGEN = "codegen";
    public static final String GNUPLOT = "gnuplot";
    public static final String VSWHERE = "vswhere";
    public static final String USERGUIDE = "userguide";
    public static final String URAMSES = "uramses";
    public static final String NPP = "npp";

    public static final List<ToolSpec> SPECS = buildSpecs();

    private static List<ToolSpec> buildSpecs() {
        List<ToolSpec> s = new ArrayList<ToolSpec>();

        s.add(new ToolSpec(RAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "dynsim.zip", ToolSpec.Kind.ZIP, null, "dynsim/dynsim.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "dynsim.zip", ToolSpec.Kind.ZIP, null, "dynsim/dynsim", true)));

        s.add(new ToolSpec(PFC)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "PFC.exe", ToolSpec.Kind.RAW, null, "PFC.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "PFC", ToolSpec.Kind.RAW, null, "PFC", true)));

        s.add(new ToolSpec(DYNGRAPH)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "dyngraph.exe", ToolSpec.Kind.RAW, null, "dyngraph.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "dyngraph", ToolSpec.Kind.RAW, null, "dyngraph", true)));

        s.add(new ToolSpec(CODEGEN)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "codegen.exe", ToolSpec.Kind.RAW, null, "codegen.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "CODEGEN", ToolSpec.Kind.RAW, null, "CODEGEN", true)));

        s.add(new ToolSpec(GNUPLOT)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "gpwin.zip", ToolSpec.Kind.ZIP, null, "gnuplot/bin/pgnuplot.exe", false)));

        s.add(new ToolSpec(VSWHERE)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "vswhere.exe", ToolSpec.Kind.RAW, null, "vswhere.exe", true)));

        s.add(new ToolSpec(NPP)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "npp.zip", ToolSpec.Kind.ZIP, null, "npp/notepad++.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "npp.zip", ToolSpec.Kind.ZIP, null, "npp/notepad++.exe", true)));

        s.add(new ToolSpec(USERGUIDE)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "DOC.zip", ToolSpec.Kind.ZIP, null, "DOC/userguide.pdf", false))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "DOC.zip", ToolSpec.Kind.ZIP, null, "DOC/userguide.pdf", false)));

        s.add(new ToolSpec(URAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "URAMSES.zip", ToolSpec.Kind.ZIP, null, "URAMSES", false)));

        return s;
    }

    public static ToolSpec byId(String id) {
        for (ToolSpec spec : SPECS) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("No such tool: " + id);
    }

    private final Platform platform;
    private final File dir;
    private final java.util.Map<String, File> resolved =
            new java.util.HashMap<String, File>();

    public Toolchain(Platform platform, File dir) {
        this.platform = platform;
        this.dir = dir;
    }

    public File directory() {
        return dir;
    }

    public Platform platform() {
        return platform;
    }

    /** Extracts every tool available on this platform. */
    public void extractAll() throws IOException {
        for (ToolSpec spec : SPECS) {
            if (spec.availableOn(platform)) {
                resolved.put(spec.id(), ToolExtractor.extract(spec, platform, dir));
            }
        }
    }

    /** @return the extracted file, or null if this tool is not available here. */
    public File get(String id) {
        return resolved.get(id);
    }

    public File ramses()    { return get(RAMSES); }
    public File pfc()       { return get(PFC); }
    public File dyngraph()  { return get(DYNGRAPH); }
    public File codegen()   { return get(CODEGEN); }
    public File vswhere()   { return get(VSWHERE); }
    public File npp()       { return get(NPP); }
    public File userGuide() { return get(USERGUIDE); }

    /**
     * Windows bundles gnuplot; elsewhere it is resolved from PATH.
     * Returns null when not found, so callers can degrade that one feature.
     */
    public File gnuplot() {
        if (platform.isWindows()) {
            return get(GNUPLOT);
        }
        return PlatformLauncher.findOnPath("gnuplot");
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `javac -d /tmp/ptest -cp /tmp/ptest src/my/ramses/platform/*.java`
Expected: errors only for the not-yet-written `ToolExtractor` and `PlatformLauncher`
(`cannot find symbol`). Any other error is a defect to fix now.

- [ ] **Step 4: Commit**

```bash
git add src/my/ramses/platform/ToolSpec.java src/my/ramses/platform/Toolchain.java
git commit -m "feat: add ToolSpec descriptor and Toolchain manifest"
```

---

### Task 5: ToolExtractor

**Files:**
- Create: `src/my/ramses/platform/ToolExtractor.java`

**Interfaces:**
- Consumes: `Platform`, `ToolSpec` from Tasks 3-4; the Gatekeeper verdict from Task 1.
- Produces: `ToolExtractor.extract(ToolSpec, Platform, File)` → `File`, throws `IOException`.

Behavior required by the spec:
- Writes **only** the current platform's payload.
- Keyed on a version stamp file `.stepss-payload-<toolId>` containing the resource name,
  not on `File.exists()` of the executable, so a refreshed payload replaces an old one.
- Rejects archive entries that escape the target directory (path traversal).
- Throws on any per-entry failure instead of printing and continuing.

- [ ] **Step 1: Write ToolExtractor**

```java
package my.ramses.platform;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ToolExtractor {

    private ToolExtractor() {
    }

    public static File extract(ToolSpec spec, Platform platform, File dir)
            throws IOException {
        ToolSpec.Payload payload = spec.payloadFor(platform);
        if (payload == null) {
            throw new IOException("Tool '" + spec.id() + "' is not available on "
                    + platform + " (" + Platform.describe() + ")");
        }

        File stamp = new File(dir, ".stepss-payload-" + spec.id());
        File target = new File(dir, payload.extractedName);

        if (!isCurrent(stamp, payload.resource) || !target.exists()) {
            deleteRecursively(new File(dir, topLevelOf(payload.extractedName)));
            unpack(spec, payload, dir);
            writeStamp(stamp, payload.resource);
        }

        if (!target.exists()) {
            throw new IOException("Extraction of '" + spec.id() + "' did not produce "
                    + target.getAbsolutePath());
        }
        if (payload.executable && !target.setExecutable(true)) {
            throw new IOException("Could not mark executable: " + target.getAbsolutePath());
        }
        return target;
    }

    private static void unpack(ToolSpec spec, ToolSpec.Payload payload, File dir)
            throws IOException {
        InputStream in = ToolExtractor.class.getResourceAsStream(payload.resource);
        if (in == null) {
            throw new IOException("Missing bundled resource '" + payload.resource
                    + "' for tool '" + spec.id() + "'");
        }
        try {
            switch (payload.kind) {
                case RAW:
                    writeStream(in, new File(dir, payload.extractedName));
                    break;
                case ZIP:
                    unpackZip(in, dir, payload);
                    break;
                case TGZ:
                    unpackTgz(in, dir, payload);
                    break;
                default:
                    throw new IOException("Unknown payload kind for " + spec.id());
            }
        } finally {
            closeQuietly(in);
        }
    }

    private static void unpackZip(InputStream raw, File dir, ToolSpec.Payload payload)
            throws IOException {
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw));
        try {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (payload.member != null && !name.equals(payload.member)) {
                    zin.closeEntry();
                    continue;
                }
                File out = payload.member != null
                        ? new File(dir, payload.extractedName)
                        : safeChild(dir, name);
                if (entry.isDirectory()) {
                    mkdirs(out);
                } else {
                    mkdirs(out.getParentFile());
                    writeStream(zin, out);
                }
                zin.closeEntry();
            }
        } finally {
            closeQuietly(zin);
        }
    }

    /**
     * Minimal POSIX/USTAR reader. Commons Compress is not a dependency of this
     * project and the release tarballs are plain ustar, so a 512-byte header
     * walk is sufficient and avoids adding a library.
     */
    private static void unpackTgz(InputStream raw, File dir, ToolSpec.Payload payload)
            throws IOException {
        GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(raw));
        try {
            byte[] header = new byte[512];
            while (true) {
                if (!readFully(gz, header, 512)) {
                    break;
                }
                String name = cString(header, 0, 100);
                if (name.isEmpty()) {
                    break;
                }
                long size = octal(header, 124, 12);
                char type = (char) header[156];

                boolean wanted = payload.member == null || name.equals(payload.member);
                if (wanted && type == '0') {
                    File out = payload.member != null
                            ? new File(dir, payload.extractedName)
                            : safeChild(dir, name);
                    mkdirs(out.getParentFile());
                    copyExactly(gz, out, size);
                } else if (wanted && type == '5') {
                    mkdirs(safeChild(dir, name));
                    skipExactly(gz, size);
                } else {
                    skipExactly(gz, size);
                }
                long pad = (512 - (size % 512)) % 512;
                skipExactly(gz, pad);
            }
        } finally {
            closeQuietly(gz);
        }
    }

    /** Rejects entries that would escape the extraction directory. */
    private static File safeChild(File dir, String entryName) throws IOException {
        File out = new File(dir, entryName);
        String root = dir.getCanonicalPath() + File.separator;
        if (!out.getCanonicalPath().startsWith(root)) {
            throw new IOException("Archive entry escapes target directory: " + entryName);
        }
        return out;
    }

    private static String topLevelOf(String extractedName) {
        int slash = extractedName.indexOf('/');
        return slash < 0 ? extractedName : extractedName.substring(0, slash);
    }

    private static boolean isCurrent(File stamp, String resource) {
        if (!stamp.isFile()) {
            return false;
        }
        try {
            byte[] buf = new byte[(int) stamp.length()];
            java.io.FileInputStream fin = new java.io.FileInputStream(stamp);
            try {
                if (fin.read(buf) != buf.length) {
                    return false;
                }
            } finally {
                fin.close();
            }
            return new String(buf, "UTF-8").trim().equals(resource);
        } catch (IOException ex) {
            return false;
        }
    }

    private static void writeStamp(File stamp, String resource) throws IOException {
        OutputStream out = new FileOutputStream(stamp);
        try {
            out.write(resource.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static void mkdirs(File d) throws IOException {
        if (d != null && !d.isDirectory() && !d.mkdirs()) {
            throw new IOException("Could not create directory: " + d.getAbsolutePath());
        }
    }

    private static void writeStream(InputStream in, File out) throws IOException {
        mkdirs(out.getParentFile());
        OutputStream fos = new FileOutputStream(out);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        } finally {
            fos.close();
        }
    }

    private static void copyExactly(InputStream in, File out, long size)
            throws IOException {
        OutputStream fos = new FileOutputStream(out);
        try {
            byte[] buf = new byte[65536];
            long left = size;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) {
                    throw new IOException("Truncated archive entry: " + out.getName());
                }
                fos.write(buf, 0, n);
                left -= n;
            }
        } finally {
            fos.close();
        }
    }

    private static void skipExactly(InputStream in, long n) throws IOException {
        long left = n;
        byte[] scratch = new byte[8192];
        while (left > 0) {
            int r = in.read(scratch, 0, (int) Math.min(scratch.length, left));
            if (r < 0) {
                throw new IOException("Truncated archive");
            }
            left -= r;
        }
    }

    private static boolean readFully(InputStream in, byte[] buf, int len)
            throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) {
                return off != 0 ? false : false;
            }
            off += n;
        }
        return true;
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) {
            end++;
        }
        return new String(b, off, end - off).trim();
    }

    private static long octal(byte[] b, int off, int len) {
        String s = cString(b, off, len);
        return s.isEmpty() ? 0L : Long.parseLong(s, 8);
    }

    static boolean deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return true;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                deleteRecursively(kid);
            }
        }
        return f.delete();
    }

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignore) {
        }
    }
}
```

- [ ] **Step 2: Apply the Gatekeeper verdict from Task 1**

If `docs/superpowers/specs/2026-08-06-gatekeeper-spike-result.md` records
`QUARANTINE_CLEARING_REQUIRED: yes`, add this call at the end of `unpack(...)`,
immediately before the closing brace of the `try` block:

```java
            if (platformNeedsQuarantineClear(platform)) {
                clearQuarantine(dir);
            }
```

with these members, and change `unpack`'s signature to accept `Platform platform`:

```java
    private static boolean platformNeedsQuarantineClear(Platform platform) {
        return platform == Platform.MACOS_ARM64;
    }

    private static void clearQuarantine(File dir) throws IOException {
        try {
            new ProcessBuilder("xattr", "-dr", "com.apple.quarantine",
                    dir.getAbsolutePath()).start().waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted clearing quarantine", ex);
        }
    }
```

If the verdict is `no`, skip this step entirely and note in the commit message that the
spike found clearing unnecessary.

- [ ] **Step 3: Verify it compiles**

Run: `javac -d /tmp/ptest -cp /tmp/ptest src/my/ramses/platform/*.java`
Expected: only `PlatformLauncher` unresolved.

- [ ] **Step 4: Commit**

```bash
git add src/my/ramses/platform/ToolExtractor.java
git commit -m "feat: add generic ToolExtractor with version stamping and traversal guard"
```

---

### Task 6: PlatformLauncher

**Files:**
- Create: `src/my/ramses/platform/PlatformLauncher.java`

**Interfaces:**
- Consumes: `Platform` from Task 3.
- Produces:
  - `PlatformLauncher.findOnPath(String)` → `File` or `null`
  - `openInEditor(File)` → `void`, throws `IOException`
  - `openTerminal(File)` → `void`, throws `IOException`
  - `openFileManager(File)` → `void`, throws `IOException`
  - `runInTerminal(Platform, java.util.List<String>, File)` → `void`, throws `IOException`
  - `killByName(Platform, String)` → `void`
  - `execEnvironment(Platform, File)` → `java.util.Map` or `null`

- [ ] **Step 1: Write PlatformLauncher**

```java
package my.ramses.platform;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.environment.EnvironmentUtils;

public final class PlatformLauncher {

    private PlatformLauncher() {
    }

    public static File findOnPath(String exe) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String part : path.split(File.pathSeparator)) {
            File candidate = new File(part, exe);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    /** Hands the file to the user's default editor. Replaces bundled Notepad++. */
    public static void openInEditor(File file) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.EDIT)) {
                try {
                    desktop.edit(file);
                    return;
                } catch (IOException ex) {
                    // No EDIT association for this type; fall through to OPEN.
                }
            }
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                try {
                    desktop.open(file);
                    return;
                } catch (IOException ex) {
                    // Fall through to the per-platform default below.
                }
            }
        }
        Platform p;
        try {
            p = Platform.current();
        } catch (UnsupportedPlatformException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("notepad.exe");
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
            cmd.addArgument("-t");
        } else {
            cmd = new CommandLine("xdg-open");
        }
        cmd.addArgument(file.getAbsolutePath(), false);
        run(cmd, file.getParentFile());
    }

    public static void openTerminal(File dir) throws IOException {
        Platform p = platformOrThrow();
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("cmd.exe");
            cmd.addArgument("/c");
            cmd.addArgument("start");
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
            cmd.addArgument("-a");
            cmd.addArgument("Terminal");
            cmd.addArgument(dir.getAbsolutePath(), false);
        } else {
            cmd = terminalOnLinux();
        }
        run(cmd, dir);
    }

    public static void openFileManager(File dir) throws IOException {
        Platform p = platformOrThrow();
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("explorer.exe");
            cmd.addArgument("/root," + dir.getAbsolutePath(), false);
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
            cmd.addArgument(dir.getAbsolutePath(), false);
        } else {
            cmd = new CommandLine("xdg-open");
            cmd.addArgument(dir.getAbsolutePath(), false);
        }
        run(cmd, dir);
    }

    /** Runs an interactive console program inside a terminal window. */
    public static void runInTerminal(Platform p, List<String> argv, File dir)
            throws IOException {
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine(argv.get(0));
            for (int i = 1; i < argv.size(); i++) {
                cmd.addArgument(argv.get(i), false);
            }
        } else if (p == Platform.MACOS_ARM64) {
            StringBuilder script = new StringBuilder();
            for (String a : argv) {
                script.append('\'').append(a.replace("'", "'\\''")).append("' ");
            }
            cmd = new CommandLine("osascript");
            cmd.addArgument("-e", false);
            cmd.addArgument("tell application \"Terminal\" to do script \"cd "
                    + dir.getAbsolutePath() + " && " + script.toString().trim() + "\"", false);
        } else {
            cmd = terminalOnLinux();
            cmd.addArgument("-e");
            for (String a : argv) {
                cmd.addArgument(a, false);
            }
        }
        run(cmd, dir);
    }

    private static CommandLine terminalOnLinux() {
        String[] candidates = {"x-terminal-emulator", "xterm", "gnome-terminal", "konsole"};
        for (String c : candidates) {
            if (findOnPath(c) != null) {
                return new CommandLine(c);
            }
        }
        return new CommandLine("xterm");
    }

    public static void killByName(Platform p, String baseName) {
        List<String> commands = new ArrayList<String>();
        if (p == Platform.WINDOWS_X86_64) {
            commands.add("taskkill /F /IM " + baseName + ".exe");
        } else {
            commands.add("killall -9 " + baseName);
        }
        for (String c : commands) {
            try {
                Runtime.getRuntime().exec(c);
            } catch (IOException ignore) {
                // Nothing to kill is a normal outcome, not an error.
            }
        }
    }

    /**
     * Windows needs gnuplot's bin directory on PATH for the exec'd children.
     * Other platforms inherit the ambient environment, signalled by null.
     */
    public static Map execEnvironment(Platform p, File toolDir) throws IOException {
        if (p != Platform.WINDOWS_X86_64) {
            return null;
        }
        Map env = EnvironmentUtils.getProcEnvironment();
        String path = (String) env.get("PATH");
        String gnuplotBin = new File(new File(toolDir, "gnuplot"), "bin").getAbsolutePath();
        EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + gnuplotBin
                + File.pathSeparator + path);
        return env;
    }

    private static Platform platformOrThrow() throws IOException {
        try {
            return Platform.current();
        } catch (UnsupportedPlatformException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private static void run(CommandLine cmd, File workingDir) throws IOException {
        DefaultExecutor executor = new DefaultExecutor();
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        if (workingDir != null && workingDir.isDirectory()) {
            executor.setWorkingDirectory(workingDir);
        }
        executor.execute(cmd, new org.apache.commons.exec.DefaultExecuteResultHandler());
    }
}
```

- [ ] **Step 2: Verify the whole package compiles**

```bash
javac -cp lib/commons-exec-1.3.jar:lib/commons-io-2.11.0.jar \
      -d /tmp/ptest src/my/ramses/platform/*.java
```

Expected: `BUILD` clean, no errors.

- [ ] **Step 3: Verify findOnPath works**

```bash
cat > /tmp/ptest/PathCheck.java <<'EOF'
import my.ramses.platform.PlatformLauncher;
public class PathCheck {
    public static void main(String[] a) {
        System.out.println("sh   -> " + PlatformLauncher.findOnPath("sh"));
        System.out.println("nope -> " + PlatformLauncher.findOnPath("definitely-not-here"));
    }
}
EOF
javac -cp /tmp/ptest -d /tmp/ptest /tmp/ptest/PathCheck.java
java -cp /tmp/ptest:lib/commons-exec-1.3.jar PathCheck
```

Expected: `sh   -> /usr/bin/sh` (or `/bin/sh`), `nope -> null`.

- [ ] **Step 4: Commit**

```bash
git add src/my/ramses/platform/PlatformLauncher.java
git commit -m "feat: add PlatformLauncher for editor, terminal and process operations"
```

---

### Task 7: Migrate fileOps to the platform layer (no-op)

**Files:**
- Modify: `src/my/ramses/fileOps.java` — delete lines 76-258 (the six `extractXxx` methods)
- Create: `src/my/ramses/platform/ToolchainDump.java`
- Create: `tools/dump-toolchain.sh`

**Interfaces:**
- Consumes: `Toolchain`, `ToolExtractor` from Tasks 4-5.
- Produces: `ToolchainDump.main(String[])` writing a deterministic fingerprint to stdout;
  `tools/dump-toolchain.sh` wrapping it.

`fileOps.deleteFiles`, `deleteDirectory`, `extractToFolder`, `copyFiletoDir` and
`copyFiletoFile` are still called elsewhere in `RamsesUI` and stay.

- [ ] **Step 1: Capture the baseline fingerprint before changing anything**

```bash
git stash list                       # confirm a clean tree
ant clean jar
mkdir -p /tmp/noop
java -cp dist/stepss.jar my.ramses.fileOps 2>/dev/null || true
```

The current code has no headless entry point, so capture the baseline from the jar's
resources instead — this is the input side of the extraction, which must not change:

```bash
unzip -l dist/stepss.jar | awk '{print $1, $4}' | sort > /tmp/noop/baseline-resources.txt
wc -l /tmp/noop/baseline-resources.txt
```

- [ ] **Step 2: Delete the six extract methods from fileOps.java**

Remove `extractDyngraph` (line 76) through `extractVswhere` (ending line 258)
inclusive. Keep `deleteFiles`, `deleteDirectory`, `extractToFolder`, `copyFiletoDir`
and `copyFiletoFile`. Remove the now-unused import `org.apache.commons.exec.OS`.

- [ ] **Step 3: Write the fingerprint harness**

```java
package my.ramses.platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ToolchainDump {

    public static void main(String[] args) throws Exception {
        Platform platform = Platform.current();
        File dir = File.createTempFile("stepss-dump", "");
        if (!dir.delete() || !dir.mkdir()) {
            throw new IllegalStateException("Could not create " + dir);
        }
        Toolchain chain = new Toolchain(platform, dir);
        chain.extractAll();

        List<String> lines = new ArrayList<String>();
        collect(dir, dir, lines);
        Collections.sort(lines);
        System.out.println("platform=" + platform);
        for (String line : lines) {
            System.out.println(line);
        }
        ToolExtractor.deleteRecursively(dir);
    }

    private static void collect(File root, File node, List<String> out) throws Exception {
        File[] kids = node.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            if (kid.isDirectory()) {
                collect(root, kid, out);
            } else {
                String rel = root.toURI().relativize(kid.toURI()).getPath();
                if (rel.startsWith(".stepss-payload-")) {
                    continue;
                }
                out.add(rel + "  " + sha256(kid) + "  exec=" + kid.canExecute());
            }
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Write the wrapper script**

```bash
#!/bin/sh
# Prints a deterministic fingerprint of the extracted toolchain.
# Usage: tools/dump-toolchain.sh > after.txt
set -e
cd "$(dirname "$0")/.."
ant -q jar > /dev/null
java -cp dist/stepss.jar my.ramses.platform.ToolchainDump
```

Then: `chmod +x tools/dump-toolchain.sh`

- [ ] **Step 5: Wire RamsesUI.initRamses to the Toolchain**

In `RamsesUI.java`, replace the body of `initRamses()` (lines 5127-5178). The critical
change is that the tool directory is **never** the user's working directory:

```java
    private boolean initRamses() {
        try {
            platform = Platform.current();
        } catch (UnsupportedPlatformException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Unsupported platform", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        try {
            if (toolDir == null) {
                toolDir = File.createTempFile("stepssTools", "");
                if (!toolDir.delete() || !toolDir.mkdir()) {
                    throw new IOException("Could not create tool directory " + toolDir);
                }
            }
            toolchain = new Toolchain(platform, toolDir);
            toolchain.extractAll();

            myTempDir = (selWorkDir != null) ? selWorkDir : toolDir;

            openExplButton.setEnabled(true);
            openTermButton.setEnabled(true);

            ramsesExec = toolchain.ramses();
            pfcExec = toolchain.pfc();
            dyngraphExec = toolchain.dyngraph();
            codegenExec = toolchain.codegen();
            nppExec = toolchain.npp();
            userguide = toolchain.userGuide();
            gnuplotExec = toolchain.gnuplot();
            WinEnvironment = PlatformLauncher.execEnvironment(platform, toolDir);

            if (gnuplotExec == null || !gnuplotExec.exists()) {
                JOptionPane.showMessageDialog(this,
                        "<html>gnuplot was not found, so real-time plotting is disabled."
                        + "<br>Install it and restart to enable it.</html>",
                        "gnuplot not found", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not prepare the simulation toolchain.\n\n" + ex.getMessage(),
                    "Toolchain error", JOptionPane.ERROR_MESSAGE);
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
```

Add these fields next to the existing ones near line 4859:

```java
    private Platform platform;
    private Toolchain toolchain;
    private File toolDir = null;
```

and these imports:

```java
import my.ramses.platform.Platform;
import my.ramses.platform.PlatformLauncher;
import my.ramses.platform.Toolchain;
import my.ramses.platform.UnsupportedPlatformException;
```

Delete the gnuplot warm-up `executor.execute(...)` block that ran gnuplot with a
3-second watchdog at old lines 5161-5172; it served only to pre-warm and now fires
before the UI exists.

- [ ] **Step 6: Stop deleting the user's working directory**

In `formWindowClosing` (lines 2621-2641), change both `fileOps.deleteDirectory(myTempDir)`
calls to delete `toolDir` instead:

```java
                fileOps.deleteDirectory(toolDir);
```

This is the data-loss fix: `myTempDir` may be the user's own folder, `toolDir` never is.

- [ ] **Step 7: Build and capture the after fingerprint**

Run:
```bash
ant clean jar
tools/dump-toolchain.sh > /tmp/noop/after.txt
head -20 /tmp/noop/after.txt
```

Expected: `platform=LINUX_X86_64` followed by sorted `path  sha256  exec=` lines
covering `dynsim/dynsim`, `PFC`, `dyngraph`, `CODEGEN`, `npp/notepad++.exe` and
`DOC/userguide.pdf`. Every file that the old code extracted must appear.

- [ ] **Step 8: Confirm the jar's resource list is unchanged**

```bash
unzip -l dist/stepss.jar | awk '{print $1, $4}' | sort > /tmp/noop/after-resources.txt
diff /tmp/noop/baseline-resources.txt /tmp/noop/after-resources.txt
```

Expected: differences confined to `my/ramses/platform/*.class` additions and
`my/ramses/fileOps.class` size. No payload resource may appear or disappear.

- [ ] **Step 9: Commit**

```bash
git add src/my/ramses/fileOps.java src/my/ramses/RamsesUI.java \
        src/my/ramses/platform/ToolchainDump.java tools/dump-toolchain.sh
git commit -m "refactor: extract toolchain through the platform layer

Behavior-preserving. Also separates the tool directory from the user's
selected working directory, which was being deleted recursively."
```

---

### Task 8: Migrate the 28 RamsesUI branch sites (no-op)

**Files:**
- Modify: `src/my/ramses/RamsesUI.java`

**Interfaces:**
- Consumes: `PlatformLauncher`, `Toolchain`, `Platform` from Tasks 3-6.
- Produces: no new API. `RamsesUI` no longer imports `org.apache.commons.exec.OS`.

- [ ] **Step 1: Replace the eight editor sites**

`nppOpen` (line 2892) becomes:

```java
    private void nppOpen(java.awt.event.ActionEvent evt, String filename) {
        File target = filename.isEmpty() ? myTempDir : new File(filename);
        if (!target.exists()) {
            JOptionPane.showMessageDialog(this,
                    "<html>The file <B>" + target.getName() + "</B> does not exist.</html>",
                    "File not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            PlatformLauncher.openInEditor(target);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open an editor for " + target.getAbsolutePath()
                    + "\n\n" + ex.getMessage(),
                    "Editor error", JOptionPane.ERROR_MESSAGE);
        }
    }
```

Then in each of the seven license viewers — `showGnupCopyrightButtonActionPerformed`
(3067), `showApacheLicenseButtonActionPerformed` (3107),
`showNppLicenseButtonActionPerformed` (3141), `showKLULicenseButtonActionPerformed`
(3181), `showRAMSESLicenseButtonActionPerformed` (4679),
`showPFCLicenseButtonActionPerformed` (4716), `showCODEGENLicenseButtonActionPerformed`
(4752) — replace the `if (nppExec.exists()) { ... }` block that builds a `CommandLine`
with a single call, keeping each method's existing code that writes the license text to
a temp file:

```java
            PlatformLauncher.openInEditor(licenseFile);
```

where `licenseFile` is that method's existing local `File` variable.

- [ ] **Step 2: Replace the two process-kill sites**

`clearGnuplotButtonActionPerformed` (3379):

```java
    private void clearGnuplotButtonActionPerformed(java.awt.event.ActionEvent evt) {
        PlatformLauncher.killByName(platform, "wgnuplot");
        PlatformLauncher.killByName(platform, "wgnuplotR");
        PlatformLauncher.killByName(platform, "pgnuplot");
        PlatformLauncher.killByName(platform, "gnuplot");
    }
```

`stopSimulationButtonActionPerformed` (3545), inside the existing `if (fileTemp.exists())`:

```java
                PlatformLauncher.killByName(platform, "dynsim");
```

- [ ] **Step 3: Replace the three launcher sites**

`openTermButtonActionPerformed` (2864) body becomes:

```java
        try {
            PlatformLauncher.openTerminal(myTempDir);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
```

`openExplButtonActionPerformed` (2916) body becomes:

```java
        try {
            PlatformLauncher.openFileManager(myTempDir);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
```

`runDyngraphButtonActionPerformed` (3466), replacing the `xterm`/direct branch:

```java
            java.util.List<String> argv = new java.util.ArrayList<String>();
            argv.add(dyngraphExec.getAbsolutePath());
            if (platform != Platform.WINDOWS_X86_64) {
                argv.add("-c");
            }
            argv.add("-a" + myTempDir.getAbsolutePath() + File.separator + "output.trj");
            argv.add("-o" + myTempDir.getAbsolutePath() + File.separator + "tempGnupOut");
            viewCurvesButton.setEnabled(true);
            saveCurrentCurveButton.setEnabled(true);
            PlatformLauncher.runInTerminal(platform, argv, myTempDir);
```

- [ ] **Step 4: Replace the four environment-injection sites**

At lines 3453, 3749, 4248 and 4450 the pattern is an `if (OS.isFamilyWindows())`
choosing between a two-arg and three-arg `execute`. Since
`PlatformLauncher.execEnvironment` returns `null` off Windows and Commons Exec treats a
null environment as "inherit", all four collapse to the three-arg form with no branch:

```java
                simulExecutor.execute(command, WinEnvironment, simulExecutorResultHandler);
```

(at 3453 the receiver is `exec` and the handler is `resultHandler`.)

- [ ] **Step 5: Fix the Codegen NPE and delete the redistributables menu item**

In `execCodegenActionPerformed` (4412) and `CompileActionPerformed` (4498), guard at
the top:

```java
        if (platform != Platform.WINDOWS_X86_64) {
            JOptionPane.showMessageDialog(this,
                    "<html>Compiling custom models requires the Intel Fortran and "
                    + "Visual Studio toolchain, which is available on Windows only."
                    + "<br>Model generation works on this platform; compilation does not.</html>",
                    "Not available on this platform", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
```

This replaces the `NullPointerException` that `fileOps.extractVswhere` threw on every
non-Windows platform. Delete `installRedLibMenuItemActionPerformed` (2770-2805) and the
`installRedLibMenuItem` widget wiring; the menu item pointed at Intel redistributables
that no longer apply.

- [ ] **Step 6: Fix getVersion null handling**

Replace `getVersion()` (132-141):

```java
    private String getVersion() {
        InputStream in = RamsesUI.class.getResourceAsStream("version.txt");
        if (in == null) {
            return "0.0";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            String line = reader.readLine();
            return (line == null || line.trim().isEmpty()) ? "0.0" : line.trim();
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return "0.0";
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }
```

Apply the same null guard to `getRamsesType()` (143-152), returning `"Limited"`.

- [ ] **Step 7: Delete the architecture warning block**

Remove lines 69-86 of the constructor (the `PROCESSOR_ARCHITECTURE` / `realArch`
check). `Platform.current()` in `initRamses()` now covers it, with a better message.

- [ ] **Step 8: Verify no OS references remain and the build is clean**

```bash
grep -n "OS.isFamily" src/my/ramses/RamsesUI.java src/my/ramses/fileOps.java
```
Expected: no output.

```bash
grep -n "import org.apache.commons.exec" src/my/ramses/RamsesUI.java
ant clean jar
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add src/my/ramses/RamsesUI.java
git commit -m "refactor: route all 28 OS branches through PlatformLauncher

Also disables Compile with a message on non-Windows instead of throwing
NullPointerException, and hardens getVersion/getRamsesType against a
missing resource."
```

---

### Task 9: No-op proof

**Files:**
- Create: `docs/superpowers/plans/noop-proof-results.md`

**Interfaces:**
- Consumes: `tools/dump-toolchain.sh` from Task 7.
- Produces: a recorded pass/fail that gates Tasks 10+.

- [ ] **Step 1: Compare fingerprints across the refactor on Linux**

```bash
git stash
ant clean jar && tools/dump-toolchain.sh > /tmp/noop/before.txt 2>/dev/null || \
  echo "pre-refactor has no dump harness; use the resource listing instead"
git stash pop
ant clean jar && tools/dump-toolchain.sh > /tmp/noop/after.txt
```

Because the harness does not exist before Task 7, the comparison that matters is
between the *extracted file set* and what the old `extractXxx` methods produced. Verify
by inspection against this expected list on Linux:

```
DOC/userguide.pdf
CODEGEN
PFC
dyngraph
dynsim/dynsim
npp/notepad++.exe
```

Run: `awk '{print $1}' /tmp/noop/after.txt | tail -n +2 | sort`
Expected: exactly the six paths above, plus the remaining files unpacked from
`dynsim.zip`, `npp.zip` and `DOC.zip`.

- [ ] **Step 2: Run the GUI smoke checklist on Linux**

Run: `java -jar dist/stepss.jar`

Verify each, recording pass/fail:
1. Window opens; About shows `Version: 3.4` (unchanged — the version bump is Task 11).
2. *System Data* → load a `.dat` from `stepss-test-systems`; the path appears.
3. Click the editor button beside it → the file opens in the system default editor.
4. *Open Terminal* → a terminal opens in the tool directory.
5. *Open Explorer* → a file manager opens.
6. Load a `.dst`, then *Run Simulation* → output streams into the pane.
7. *Stop Simulation* → the run halts.
8. Help → *User Guide* → the PDF opens.
9. Close the window, confirm exit, and verify the tool directory is gone while any
   selected working directory still contains its files.

- [ ] **Step 3: Repeat steps 1-2 on Windows**

Same checklist on a Windows x86_64 machine, plus: Codegen tab → *Compile* still reaches
the Visual Studio detection (it must not be disabled on Windows).

- [ ] **Step 4: Record results**

Write `docs/superpowers/plans/noop-proof-results.md` with a row per checklist item per
platform and a verdict line `NOOP_PROOF: pass|fail`. If any item fails, fix it and
re-run before proceeding — Tasks 10+ assume a clean baseline.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/noop-proof-results.md
git commit -m "docs: record no-op proof results for the platform layer refactor"
```

---

### Task 10: Replace the bundled user guide with the docs site; enable macOS

**Files:**
- Modify: `src/my/ramses/platform/Toolchain.java`
- Modify: `src/my/ramses/RamsesUI.java`
- Delete: `src/my/ramses/DOC.zip`

**Interfaces:**
- Consumes: everything from Tasks 3-8.
- Produces: a manifest with no `USERGUIDE` entry; `Toolchain.userGuide()` removed.

The bundled 3.4 MB `DOC.zip` is dropped. Help → User Guide opens
`https://stepss.sps-lab.org/` in the user's browser instead, so the documentation is
always current rather than frozen at build time. `Platform.MACOS_ARM64` already exists
from Task 3; after this task the macOS manifest is legitimately empty until Task 11
adds the release payloads, which is the degradation path worth proving here.

- [ ] **Step 1: Remove the USERGUIDE spec**

In `Toolchain.buildSpecs()`, delete the whole `s.add(new ToolSpec(USERGUIDE)…)` block.
Delete the `USERGUIDE` constant and the `userGuide()` accessor.

- [ ] **Step 2: Point the User Guide button at the docs site**

Replace `showUserGuideButtonActionPerformed` (`RamsesUI.java:3000`) with:

```java
    private void showUserGuideButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showUserGuideButtonActionPerformed
        BareBonesBrowserLaunch.openURL("https://stepss.sps-lab.org/");
    }//GEN-LAST:event_showUserGuideButtonActionPerformed
```

`BareBonesBrowserLaunch.openURL` already exists in this package and is used by
`webpageLabelMouseClicked` for the same host. Remove the now-unused `userguide` field
and its assignment in `initRamses()`.

- [ ] **Step 3: Delete the payload**

```bash
git rm src/my/ramses/DOC.zip
```

- [ ] **Step 4: Verify macOS resolves and degrades without throwing**

```bash
ant clean jar
java -cp dist/stepss.jar -Dos.name="Mac OS X" -Dos.arch="aarch64" \
     my.ramses.platform.ToolchainDump
```

Expected: `platform=MACOS_ARM64` and **no** file lines — every tool is currently
Windows/Linux-only. It must not throw: `extractAll()` skips tools with no payload for
the platform. If it throws, fix `Toolchain.extractAll`'s `availableOn` guard.

Then confirm Linux is unaffected apart from the removed PDF:

```bash
tools/dump-toolchain.sh | awk '{print $1}' | tail -n +2 | sort
```

Expected: the same list as Task 9 minus `DOC/userguide.pdf`.

- [ ] **Step 5: Commit**

```bash
git add src/my/ramses/platform/Toolchain.java src/my/ramses/RamsesUI.java
git rm --cached src/my/ramses/DOC.zip 2>/dev/null || true
git commit -m "feat: link Help > User Guide to the docs site; enable macOS manifest

Drops the bundled 3.4 MB DOC.zip so documentation tracks the live site
instead of the build."
```

---

### Task 11: Swap payloads to the fetched release archives

**Files:**
- Modify: `src/my/ramses/platform/Toolchain.java`
- Modify: `build.xml`
- Modify: `src/my/ramses/version.txt`
- Delete: `src/my/ramses/dynsim.zip`, `src/my/ramses/codegen.exe`, `src/my/ramses/CODEGEN`, `src/my/ramses/dyngraph`, `src/my/ramses/npp.zip`, `src/my/ramses/nppLicense.txt`

**Interfaces:**
- Consumes: `payload-cache/` from Task 2.
- Produces: jar resources named exactly as the release assets, e.g.
  `my/ramses/payload/ramses-linux-x86_64-v3.55.tar.gz`.

- [ ] **Step 1: Copy fetched payloads into the build as resources**

Add to `build.xml`, and make `-pre-compile` depend on it:

```xml
 <target name="stage-payloads" depends="fetch-payloads"
         description="Copy verified payloads into the source tree for packaging">
  <mkdir dir="src/my/ramses/payload"/>
  <copy todir="src/my/ramses/payload" flatten="true">
   <fileset dir="${payload.cache}">
    <include name="ramses-*"/>
    <include name="stepss-helios-*"/>
    <include name="dyngraph-linux-*"/>
    <include name="dyngraph-macos-*"/>
    <include name="codegen-*"/>
   </fileset>
  </copy>
 </target>

 <target name="-pre-compile" depends="stage-payloads"/>
```

Add `/src/my/ramses/payload/` to `.gitignore` — these are build inputs, not sources.

- [ ] **Step 2: Rewrite the manifest rows for ramses, dyngraph and codegen**

Replace those three specs in `buildSpecs()`:

```java
        s.add(new ToolSpec(RAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/ramses-windows-x86_64-v3.55.zip", ToolSpec.Kind.ZIP,
                "ramses.exe", "dynsim.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/ramses-linux-x86_64-v3.55.tar.gz", ToolSpec.Kind.TGZ,
                "ramses", "dynsim", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/ramses-macos-arm64-v3.55.tar.gz", ToolSpec.Kind.TGZ,
                "ramses", "dynsim", true)));

        s.add(new ToolSpec(DYNGRAPH)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "dyngraph.exe", ToolSpec.Kind.RAW, null, "dyngraph.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/dyngraph-linux-x86_64-v1.1.0.tar.gz", ToolSpec.Kind.TGZ,
                "dyngraph", "dyngraph", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/dyngraph-macos-arm64-v1.1.0.tar.gz", ToolSpec.Kind.TGZ,
                "dyngraph", "dyngraph", true)));

        s.add(new ToolSpec(CODEGEN)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/codegen-windows-x86_64-v5.1.0.zip", ToolSpec.Kind.ZIP,
                "CODEGEN.exe", "CODEGEN.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/codegen-linux-x86_64-v5.1.0.tar.gz", ToolSpec.Kind.TGZ,
                "CODEGEN", "CODEGEN", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/codegen-macos-arm64-v5.1.0.tar.gz", ToolSpec.Kind.TGZ,
                "CODEGEN", "CODEGEN", true)));
```

The ramses executable is renamed to `dynsim`/`dynsim.exe` on extraction so
`PlatformLauncher.killByName(platform, "dynsim")` in Task 8 keeps working.

- [ ] **Step 3: Remove the npp spec and delete its payload**

Delete the `NPP` spec block and the `NPP` constant from `Toolchain`, and the `npp()`
accessor. Remove `nppExec` from `RamsesUI` (field and the assignment in `initRamses`).
Then:

```bash
git rm src/my/ramses/npp.zip src/my/ramses/nppLicense.txt \
       src/my/ramses/dynsim.zip src/my/ramses/codegen.exe \
       src/my/ramses/CODEGEN src/my/ramses/dyngraph
```

Remove the *Notepad++ license* button and its handler from `RamsesUI`, since the
bundled editor is gone.

- [ ] **Step 4: Drop the obsolete dynsim PATH entry**

`PlatformLauncher.execEnvironment` (Task 6) already adds only gnuplot's `bin`. Confirm
nothing else adds a `dynsim` directory to PATH:

```bash
grep -n "dynsim" src/my/ramses/platform/PlatformLauncher.java
```
Expected: no output. The Windows ramses release is a single self-contained
`ramses.exe` with no sibling DLLs, so the old PATH entry has no purpose.

- [ ] **Step 5: Bump the version file**

Set `src/my/ramses/version.txt` to `3.55` (a single line, no trailing spaces).

- [ ] **Step 6: Build and fingerprint on Linux**

```bash
ant clean jar
tools/dump-toolchain.sh
```

Expected: `platform=LINUX_X86_64` and entries for `dynsim`, `dyngraph`, `CODEGEN`,
`PFC`, `DOC/userguide.pdf`. `dynsim` must be marked `exec=true`.

- [ ] **Step 7: Verify the extracted engine is the new one**

```bash
D=$(mktemp -d)
java -cp dist/stepss.jar my.ramses.platform.ToolchainDump | head -3
```

Then extract manually and check the version banner:

```bash
cd "$D" && unzip -o -j <path-to>/dist/stepss.jar \
  'my/ramses/payload/ramses-linux-x86_64-v3.55.tar.gz' > /dev/null
tar xzf ramses-linux-x86_64-v3.55.tar.gz
chmod +x ramses && ./ramses < /dev/null | head -2
```

Expected: a banner containing `3.55`.

- [ ] **Step 8: Commit**

```bash
git add -A src/my/ramses build.xml .gitignore
git commit -m "feat: source ramses, dyngraph and codegen from pinned releases

Drops the bundled Notepad++ in favour of the system default editor and
removes the obsolete Intel redistributable DLL path entry."
```

---

### Task 12: Remove the Intel compile path from P1

**Files:**
- Modify: `src/my/ramses/platform/Toolchain.java`
- Modify: `src/my/ramses/RamsesUI.java`
- Modify: `src/my/ramses/RamsesUI.form`
- Delete: `src/my/ramses/URAMSES.zip`, `src/my/ramses/vswhere.exe`

**Interfaces:**
- Consumes: the manifest and `PlatformLauncher` from earlier tasks.
- Produces: a manifest with no `URAMSES` and no `VSWHERE` entry; `Toolchain.vswhere()`
  removed.

**Why this replaces the original task.** P1 no longer ships the Intel runtime, so a
Compile step driven by `vswhere` + `devenv` + Intel `.vfproj` projects has nothing
behind it. Rather than refresh an Intel kit into a release that deliberately carries
no Intel runtime, P1 drops custom-model *compilation* entirely. Model *generation*
through CODEGEN keeps working on all three platforms. P2 reintroduces compilation on
gfortran using the `modules_l`/`modules_m`/`modules_wg` kits from the pinned uramses
tag.

- [ ] **Step 1: Remove the two manifest entries**

In `Toolchain.buildSpecs()`, delete the `s.add(new ToolSpec(URAMSES)…)` and
`s.add(new ToolSpec(VSWHERE)…)` blocks. Delete the `URAMSES` and `VSWHERE` constants
and the `vswhere()` accessor.

- [ ] **Step 2: Disable Compile on every platform**

Replace the body of `CompileActionPerformed` with a single message and return:

```java
    private void CompileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CompileActionPerformed
        JOptionPane.showMessageDialog(this,
                "<html>Compiling custom models is not available in this release."
                + "<br>Model generation works as usual; compilation returns in a"
                + " later release built on gfortran.</html>",
                "Not available", JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_CompileActionPerformed
```

Delete the now-unreachable body that spliced `.f90` files into `usr_*_models.f90` and
`exeramses.vfproj`, located Visual Studio via `vswhere`, and invoked `devenv`. It
depends on payloads that no longer ship, so leaving it would not compile.

`savedynsimActionPerformed` saved the executable that Compile produced. Since nothing
produces one, disable it the same way, and leave both buttons disabled in the UI so the
dialog is a fallback rather than the primary signal.

Leave `execCodegenActionPerformed` alone — model generation is cross-platform and must
keep working.

- [ ] **Step 3: Delete the payloads**

```bash
git rm src/my/ramses/URAMSES.zip src/my/ramses/vswhere.exe
```

- [ ] **Step 4: Resync the form**

Removing or disabling widgets risks the NetBeans divergence trap. After the edit:

```sh
for H in $(grep -o 'handler="[a-zA-Z0-9_]*"' src/my/ramses/RamsesUI.form | sed 's/handler="//;s/"//' | sort -u); do
  grep -q "private void $H" src/my/ramses/RamsesUI.java || echo "MISSING: $H"
done
```

Expected: no output. Keep any form edit surgical and confirm the file is still
well-formed XML.

- [ ] **Step 5: Verify**

```bash
ant clean jar
tools/dump-toolchain.sh
grep -rn "vswhere\|URAMSES" src/my/ramses/*.java
```

Expected: build succeeds; the fingerprint loses nothing on Linux (neither payload
extracted there anyway) and loses `vswhere.exe` on Windows; the grep finds no
references to the removed payloads. Confirm both are absent from the built jar.

- [ ] **Step 6: Commit**

```bash
git add -A src/my/ramses
git commit -m "feat: drop Intel custom-model compilation from this release

P1 ships no Intel runtime, so the vswhere/devenv compile path has
nothing behind it. Model generation is unaffected; compilation returns
on gfortran in a later release."
```

### Task 13: Replace PFC with helios, on helios' export mechanism

**Files:**
- Modify: `src/my/ramses/platform/Toolchain.java`
- Modify: `src/my/ramses/RamsesUI.java`, `src/my/ramses/RamsesUI.form`
- Create: `src/my/ramses/heliosLicense.txt`
- Delete: `src/my/ramses/PFC`, `src/my/ramses/PFC.exe`, `src/my/ramses/pfcLicense.txt`

**Interfaces:**
- Consumes: helios payloads staged by Task 11.
- Produces: `Toolchain.helios()` replacing `Toolchain.pfc()`.

**Why this differs from the original task.** A differential test proved helios cannot
serve the GUI's existing command file: its `O` output-redirection command is an
acknowledged stub (`MASTER_STATUS` gap H2 — `cmd_change_output` prints "Output
redirection not yet implemented"), so four of the five result files can never be
written that way. The project owner chose to rebuild result capture on helios'
**working export path** rather than wait for `O`.

Two facts make this tractable. The four `.res` files are **display text only** — each
`load*ActionPerformed` reads lines straight into `pfcPane`, nothing parses them — so
any readable format works. And helios' display submenu has an `X` command that exports
the last displayed table to a file, TXT or CSV by extension.

`in_volt_trfo.dat` is the exception: `loadLFRESV2DAT` feeds it back into the data set as
a RAMSES input, so its *format* matters, not just its content.

- [ ] **Step 1: Fix the command-line argument form**

helios' `main.cpp` matches `-t` exactly, so the GUI's concatenated `"-t" + path`
does not parse. In `runPFActionPerformed`, pass `-t` and the path as two arguments.
Leave the dynsim invocation alone — RAMSES accepts both forms.

- [ ] **Step 2: Rewrite `createPFCCommandFile()` around display-then-export**

Replace each `O <file> / D / <type> / A` block with the equivalent that displays first
and exports after: enter the display submenu, produce the table, then `X` with the
target filename. Keep the same four output names so the `load*` handlers need no
change. Keep the `VT` block for `in_volt_trfo.dat` — that command works.

Derive the exact key sequence by reading `PlainMenu::run` and its display submenu in
`stepss-helios/src/terminal/PlainMenu.cpp`; do not guess it.

- [ ] **Step 3: Prove it with the differential test**

Run the new command file through helios and the old one through PFC, on a case from
`stepss-test-systems/stepss-IEEE-Nordic-Test-system/`.

Byte-identity is **not** the criterion — different engines format differently. The
criteria are:

1. All five files are produced by helios.
2. For each, the underlying **values** match PFC: bus voltages and angles, transformer
   tap positions, generator P/Q/V, and the power balance totals. Compare numerically,
   field by field, not as text.
3. Any divergence is reported with its magnitude, not smoothed over.

A known open issue to confirm and quantify: transformer tap-position integers differed
by one between the engines. Report it precisely; do not "fix" it in the GUI.

- [ ] **Step 4: Verify `in_volt_trfo.dat` is usable as RAMSES input**

This is the step that protects a real workflow. helios writes C-style float exponents
(`e-01`) where PFC writes Fortran style (`E+00`). Take helios' `in_volt_trfo.dat`, feed
it to the bundled `dynsim` as a data file alongside a Nordic case, and confirm it loads
without a parse error. If it does not, that is a blocking finding — report it rather
than working around it.

- [ ] **Step 5: Swap the manifest**

Replace the `PFC` spec with `HELIOS`, keyed `"helios"`:

```java
        s.add(new ToolSpec(HELIOS)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/stepss-helios-windows-x64.zip", ToolSpec.Kind.ZIP,
                "helios.exe", "helios.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/stepss-helios-linux-x86_64.tar.gz", ToolSpec.Kind.TGZ,
                "stepss-helios-linux-x86_64/helios", "helios", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/stepss-helios-macos-universal.tar.gz", ToolSpec.Kind.TGZ,
                "stepss-helios-macos-universal/helios", "helios", true)));
```

Rename the accessor `pfc()` to `helios()` and the `RamsesUI` field `pfcExec` to
`heliosExec`.

- [ ] **Step 6: Relabel the UI and swap the licence**

Retitle the licence button and any user-visible "PFC" strings to "Helios", in both
`RamsesUI.java` and `RamsesUI.form`. Point its handler at `heliosLicense.txt`, taken
from the helios tarball's `LICENSE`. Update the not-found message in
`runPFActionPerformed`.

Also relabel the Tools menu item that still reads "Open Notepad++" — that path opens the
user's default editor now.

- [ ] **Step 7: Delete PFC and verify**

```bash
git rm src/my/ramses/PFC src/my/ramses/PFC.exe src/my/ramses/pfcLicense.txt
ant clean jar
tools/dump-toolchain.sh
```

Expected: `helios` present and executable, `PFC` gone, on all three platform dumps.
Confirm the form-handler check is clean and the form is well-formed XML.

- [ ] **Step 8: Commit**

```bash
git add -A src/my/ramses
git commit -m "feat: replace PFC with helios using its export mechanism

helios' O redirection is an unimplemented stub, so result capture is
rebuilt on the display-then-export path. Values verified against PFC
field by field on the Nordic test system."
```

### Task 14: Documentation

**Files:**
- Modify: `README.md`
- Modify: `NOTICE`

**Interfaces:**
- Consumes: the finished behavior from Tasks 10-13.
- Produces: no code.

- [ ] **Step 1: Update README platform and build sections**

Replace the "Current release" line with `3.55`. Replace the bundled-tools table with:

```markdown
| Tool | Role | Windows | Linux | macOS (Apple Silicon) |
|---|---|---|---|---|
| RAMSES (`dynsim`) | Dynamic simulation | yes | yes | yes |
| Helios | Power flow | yes | yes | yes |
| DYNGRAPH | Curve viewer | yes (dialog build) | yes (console) | yes (console) |
| CODEGEN | Model generation | yes | yes | yes |
| Model compilation | Custom models | yes (Intel/VS) | no | no |
| gnuplot | Real-time plotting | bundled | install separately | install separately |
| Data file editing | System default editor | yes | yes | yes |
```

Add under Installation:

- The prebuilt jar is published as a **release artifact**, not committed to the repo —
  `dist/` and `build/` are untracked. Point users at the releases page.
- Building from source needs network on first run to fetch pinned release payloads
  (`ant fetch-payloads`), and because the component repos are private it needs the
  `gh` CLI authenticated with SPS-L access. CI uses the `STEPSS_TOKEN` secret held by
  this repository; the default `GITHUB_TOKEN` is scoped to this repo alone and cannot
  reach the component repos.
- Intel Macs are not supported.

Also correct the Documentation section: the user guide is no longer a bundled PDF —
Help → User Guide opens `https://stepss.sps-lab.org/`.

- [ ] **Step 2: Update NOTICE**

Remove the Notepad++ and PFC entries. Add STEPSS-Helios under its Academic Public
License, and note that RAMSES, DYNGRAPH and CODEGEN binaries are fetched from their
respective SPS-L releases.

- [ ] **Step 3: Verify no stale references remain**

```bash
grep -rn "Notepad++\|npp\.zip\|PFC\b" README.md NOTICE | grep -v "STEPSS-PFC"
```
Expected: no output, or only historical references to STEPSS-PFC as helios' ancestor.

- [ ] **Step 4: Commit**

```bash
git add README.md NOTICE
git commit -m "docs: document three-platform support and the fetch-based build"
```

---

### Task 15: Platform matrix acceptance

**Files:**
- Create: `docs/superpowers/plans/platform-matrix-results.md`

**Interfaces:**
- Consumes: the complete build from Tasks 1-14.
- Produces: the release gate.

- [ ] **Step 1: Build a clean jar from a clean clone**

```bash
git clone <this-repo> /tmp/clean-build
cd /tmp/clean-build && git checkout cross-platform-toolchain-spec
ant clean jar
ls -la dist/stepss.jar
```

Expected: `BUILD SUCCESSFUL` with no manual steps, and a jar of roughly 32 MB. This
proves the fetch-based build reproduces from a commit plus network.

- [ ] **Step 2: Run the matrix on each platform**

On **Windows x86_64**, **Linux x86_64** and **macOS arm64**, run `java -jar dist/stepss.jar`
and record pass/fail for each:

1. App opens; About reports `Version: 3.55`.
2. Load a `.dat` and a `.dst` from `stepss-test-systems`.
3. *Run Power Flow* → helios output appears; `in_net.res` written.
4. *Run Simulation* → curves stream; simulation completes.
5. *Extract Curves* → dyngraph opens (dialog on Windows, terminal elsewhere).
6. Editor button → the file opens in the system default editor.
7. *Open Terminal* and *Open Explorer* both work.
8. Codegen tab → generation works; *Compile* is enabled on Windows, and on
   Linux/macOS shows the "Windows only" message rather than throwing.
9. Real-time plotting works, or degrades with the gnuplot message if gnuplot is absent.
10. Exit → the tool directory is removed and any selected working directory is intact.

- [ ] **Step 3: Verify the macOS prerequisite state**

On macOS, before installing anything, run the jar and attempt a simulation. Until the
upstream static builds land, expect a dynamic-link failure naming `libgfortran`.
Record whether `brew install gcc openblas` resolves it. This is the interim state the
spec documents; note it in the results file rather than treating it as a defect.

- [ ] **Step 4: Record results and gate the release**

Write `docs/superpowers/plans/platform-matrix-results.md` with a 10-row × 3-column
table and a verdict `PLATFORM_MATRIX: pass|fail`, plus any deviations.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/platform-matrix-results.md
git commit -m "docs: record platform matrix acceptance results"
```

---

## Self-Review

**Spec coverage.** Every spec section maps to a task: platform resolver → Task 3;
ToolSpec/Toolchain → Task 4; ToolExtractor with version stamping and traversal guard →
Task 5; PlatformLauncher → Task 6; the 34 branch sites → Tasks 7-8; extraction
directory no longer aliasing the working directory → Task 7 Steps 5-6; loud extraction
failures → Task 5; Compile disabled instead of NPE → Task 8 Step 5; `getVersion()` null
handling → Task 8 Step 6; fetch-with-digest build → Task 2; macOS manifest → Task 10;
release payloads → Task 11; URAMSES kit from a pinned tag → Task 12; helios replacing
PFC with the differential test → Task 13; documentation → Task 14; the three
verification layers → Tasks 9, 13 and 15.

**Two spec items deliberately not implemented here**, both external to this repo: the
upstream static macOS builds (Task 15 Step 3 records the interim state instead), and
the helios `macos-arm64` rename (Task 2's `versions.properties` carries a comment
marking the two lines to change).

**Placeholder scan.** No TBDs, no "add error handling", no "similar to Task N". Each
code step carries the actual code. The one conditional step, Task 5 Step 2, states both
branches and which artifact decides.

**Type consistency.** `Platform.current()`, `key()`, `isWindows()`, `exeSuffix()`,
`describe()` are used consistently in Tasks 4-8. `ToolSpec.Payload`'s five-argument
constructor `(resource, kind, member, extractedName, executable)` is used identically
in Tasks 4, 10, 11 and 13. `Toolchain.get(String)` backs every accessor.
`PlatformLauncher.execEnvironment` returns `Map`, matching `RamsesUI`'s existing raw
`Map WinEnvironment` field. One rename is threaded through deliberately: `Toolchain.PFC`
/ `pfc()` (Tasks 4, 7) become `HELIOS` / `helios()` in Task 13, with `RamsesUI.pfcExec`
renamed to `heliosExec` in the same task.
