# P1 — Cross-platform toolchain layer

**Date:** 2026-08-06
**Status:** Design approved, ready for planning
**Scope:** stepss-java-ui only. A follow-up spec (P2) covers the Codegen compile migration.

## Problem

The GUI bundles its toolchain inside `stepss.jar` and extracts it at runtime. That
extraction layer knows two platforms, Windows and "not Windows", expressed as 34
scattered `OS.isFamilyWindows()` checks — 6 in `fileOps.java`, 28 in `RamsesUI.java`.
Adding macOS naively triples them.

Three further problems are folded in because this change rewrites the code that
causes them:

- The bundled payloads are stale and hand-assembled. `dynsim.zip` carries an Intel
  build with 41.7 MB of `.pdb` debug symbols; the URAMSES kit's modules are dated
  2022-01-24 while the engine is 3.55.
- `PFC` has no macOS build, so power flow cannot work on macOS at all.
- Extraction is guarded by `File.exists()` with no version key, so a refreshed
  payload silently fails to replace an older one.

## Goals

- The jar runs on Windows x86_64, Linux x86_64 and macOS arm64.
- One place decides platform; adding a platform is a table edit, not a code sweep.
- Payloads come from published releases, so updating a component is a one-line change.
- Helios replaces PFC as the power flow engine, with evidence it produces the same files.

## Non-goals

- Intel Macs. macOS means Apple Silicon.
- 32-bit anything.
- Migrating the Codegen compile step off Intel/Visual Studio — that is P2.
- Adding a unit-test framework. Verification is by no-op proof and differential test.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | macOS = arm64 only | `ramses-macos-arm64` is single-arch; Rosetta translates x86_64→arm64, not the reverse. Helios goes arm64-only from its next release. |
| 2 | Static macOS builds are fixed upstream | ramses, dyngraph and codegen macOS builds currently need `brew install gcc` (+`openblas` for ramses). Their Linux and Windows counterparts are already statically linked, so this is a build-config gap, not an inherent constraint. The jar consumes uniform static builds. |
| 3 | Platform resolver + tool manifest | Collapses 34 branch sites into one enum and one table. |
| 4 | Archives fetched from GitHub releases at build time, checksum-verified | The repo stops carrying binaries, and a commit plus network reproduces the jar exactly — today no commit reproduces the published jar. Accepted cost: a clean build needs network once; thereafter a local cache serves it. |
| 5 | Editor → OS default | Replaces bundled Notepad++ and the Wine dependency; gives macOS the feature for free. |
| 6 | gnuplot bundled on Windows, resolved from `PATH` elsewhere | No gnuplot in any release; the current hardcoded `/usr/bin/gnuplot` is fragile. |
| 7 | Per-platform terminal launcher | `xterm` is absent on stock macOS, and `OS.isFamilyUnix()` is true there. |
| 8 | Windows dyngraph keeps the Intel dialog build | The published dyngraph binaries are console-only; the dialog observable picker exists in no release asset. This is the one documented exception to "everything from releases". |
| 9 | Helios fully replaces PFC, differential-tested | Helios' own docs say it is not yet a full drop-in replacement, so compatibility is verified rather than assumed. |
| 10 | URAMSES kit built from a pinned uramses source tag | uramses publishes no binary assets; it is a source kit. Scripting the packaging replaces hand assembly. |

### Upstream dependency

Decision 2 requires releases from `stepss-ramses`, `stepss-dyngraph` and
`stepss-codegen` whose macOS builds link `libgfortran`, `libgomp`, `libquadmath`
and (ramses only) OpenBLAS statically. Until those land, macOS builds consume the
current dynamically-linked archives and require `brew install gcc openblas`.
Implementation of everything else can proceed in parallel; only the macOS
acceptance test is blocked.

## Architecture

Three small classes in a new `my.ramses.platform` package replace the scattered checks.

**`Platform`** — enum `WINDOWS_X86_64`, `LINUX_X86_64`, `MACOS_ARM64`, resolved once at
startup from `os.name`/`os.arch`. It deliberately does not use Commons Exec's `OS`,
which has no notion of architecture and reports `isFamilyUnix()` true on macOS.

On Apple Silicon a JVM running under Rosetta reports `os.arch=x86_64`, which is
indistinguishable from a genuine Intel Mac by `os.arch` alone. The resolver checks
`sysctl.proc_translated` to tell them apart. Launching an arm64 binary from a
translated JVM works, so this affects detection only.

Anything unresolvable — Intel Mac, 32-bit, BSD — becomes an explicit *unsupported*
result reported in one dialog naming what was detected, instead of surfacing later
as a failed `exec`.

**`ToolSpec`** — an immutable descriptor per tool: id, per-platform resource path
(absent means unavailable on that platform), extracted filename, archive-or-single-file,
and whether the executable bit is needed.

**`ToolExtractor`** — one generic `extract(spec, platform, targetDir)` replacing six
near-identical `extractXxx` methods. It writes only the current platform's payload,
guards against path traversal while unpacking, and fails loudly rather than swallowing
per-entry exceptions.

**`Toolchain`** — holds the manifest and the resolved `File` handles, replacing the
seven loose `File` fields on `RamsesUI`.

**`PlatformLauncher`** — `openInEditor`, `openTerminal`, `openFileManager`,
`runInTerminal`, `killByName`, `environment`.

### Extraction keying

Extraction is keyed on a version stamp file written into the target directory, not on
`File.exists()` of the executable. A payload refresh replaces the old copy instead of
silently leaving users on the previous engine.

The extraction target is never the user's chosen working directory. Today
`initRamses()` assigns `myTempDir = selWorkDir` and then recursively deletes
`myTempDir` on the next working-directory change and on exit, destroying the user's
data files. The two directories are separated: tools extract to a private managed
directory, and the working directory is only ever read from and written to as data.

## Components

| Class | Responsibility |
|---|---|
| `Platform` | Enum + one-time detection, including the Rosetta case |
| `ToolSpec` | Immutable per-tool descriptor |
| `Toolchain` | Manifest + resolved `File` handles |
| `ToolExtractor` | The single generic extract routine |
| `PlatformLauncher` | Editor, terminal, file manager, process kill, environment |

### How the 28 `RamsesUI` sites collapse

| Pattern | Sites | Becomes |
|---|---|---|
| Run Notepad++ directly on Windows, under `wine` otherwise | 8 (`nppOpen` + 7 license viewers) | `PlatformLauncher.openInEditor(File)` |
| `taskkill /F /IM x.exe` vs `killall -9 x` | 2 | `PlatformLauncher.killByName(String)` |
| Terminal / file-manager / dyngraph launch | 3 | `PlatformLauncher` |
| Executable path selection | 6 | Manifest lookup |
| Windows `PATH` injection for `exec` | 3 | `platform.environment()` |
| Intel redistributables menu item | 1 | Deleted — obsolete once all platforms ship static builds |
| Remaining one-offs | 5 | Inline, using the resolved `Platform` |

`fileOps.java` goes from 327 lines and six methods to roughly 120 and one.
`RamsesUI` loses about 150 lines of branch bodies without gaining a tab or a widget.

## Tool manifest

Versions pinned in `versions.properties`: `ramses=3.55`, `helios=1.2.0`,
`dyngraph=1.1.0`, `codegen=5.1.0`, `uramses=3.55`.

| Tool | Source | Windows | Linux | macOS arm64 |
|---|---|---|---|---|
| ramses (`dynsim`) | stepss-ramses release | `ramses-windows-x86_64-v3.55.zip` | `ramses-linux-x86_64-v3.55.tar.gz` | `ramses-macos-arm64-v3.55.tar.gz` |
| helios | stepss-helios release | `stepss-helios-windows-x64.zip` | `stepss-helios-linux-x86_64.tar.gz` | `stepss-helios-macos-arm64.tar.gz` |
| dyngraph | dyngraph release / committed | committed Intel dialog build | `dyngraph-linux-x86_64-v1.1.0.tar.gz` | `dyngraph-macos-arm64-v1.1.0.tar.gz` |
| CODEGEN | stepss-codegen release | `codegen-windows-x86_64-v5.1.0.zip` | `codegen-linux-x86_64-v5.1.0.tar.gz` | `codegen-macos-arm64-v5.1.0.tar.gz` |
| gnuplot | bundled / host | `gpwin.zip` | resolved from `PATH` | resolved from `PATH` |
| URAMSES kit | built from uramses source tag | `URAMSES.zip` (Intel, `modules_wi`) | — | — |
| vswhere | committed | `vswhere.exe` | — | — |
| user guide | committed | `DOC.zip` | `DOC.zip` | `DOC.zip` |

Helios is the one irregular filename — no version suffix, `windows-x64` rather than
`windows-x86_64`, and `macos-universal` until the announced arm64-only re-cut. The
manifest therefore carries a per-tool filename pattern rather than one shared template.
Until helios re-cuts, the macOS build consumes `stepss-helios-macos-universal.tar.gz`;
switching to `stepss-helios-macos-arm64.tar.gz` is a one-line change.

Note that the ramses release names its executable `ramses`, while the code currently
looks for `dynsim`/`dynsim.exe`. The manifest's extracted-filename field absorbs this.

### Build and update process

`versions.properties` pins a version and a SHA-256 per asset. An Ant target
(`fetch-payloads`) downloads each archive from its GitHub release into a
gitignored cache directory, verifies the digest, and fails the build on mismatch.
A second run reuses the cache and needs no network. `package` depends on
`fetch-payloads`.

Updating a component is then a two-line edit: bump the version, update the digest.

Two payloads stay committed because no release publishes them: the Windows Intel
dyngraph build and `gpwin.zip`. `DOC.zip` also stays committed, as it is generated
from `stepss-userguide` rather than released.

### URAMSES kit packaging

`stepss-uramses` publishes no binary assets; it is a source kit, and its `modules_*`
directories and project files live in the repo. The same Ant target fetches the
source tarball for the pinned uramses tag from GitHub and assembles `URAMSES.zip`
from it — `modules_wi/` plus `src/` plus the msvs project files — restructured into
the flat `URAMSES/` layout the Codegen tab expects. Building from the fetched tag
rather than a local checkout means the kit does not depend on the state of a
sibling working copy.

This replaces hand assembly and refreshes the bundled kit from its current 2022-01-24
modules to v3.55, closing the defect where compiling a custom model silently swaps the
3.55 engine for a 2022-vintage one. P2 extends the same script to emit the
`modules_l`/`modules_m`/`modules_wg` variants, so both specs share one packaging
mechanism.

This also removes the dependency on the local `stepss-uramses` checkout, which sits at
`v3.52-8-g82fc463` while the release tag is v3.55.

## Platform behavior

| Feature | Windows | Linux | macOS arm64 |
|---|---|---|---|
| Dynamic simulation | yes | yes | yes |
| Power flow (helios) | yes | yes | yes |
| Curve extraction | Intel dialog build | console build in terminal | console build in terminal |
| Real-time plotting | bundled gnuplot | host gnuplot, probed | host gnuplot, probed |
| Open data file | OS default editor | OS default editor | OS default editor |
| Model generation | yes | yes | yes |
| Model compilation | yes (Intel/VS) | no — P2 | no — P2 |

## Error handling

Every failure produces one dialog naming the tool, the platform and the path.

- **Unsupported platform** — detected at startup and reported in one dialog naming the
  detected OS and architecture, after which the app exits. No bundled tool can run, so
  there is no useful degraded mode.
- **Extraction failure** — fails loudly. Today `extractToFolder` catches per-entry
  exceptions, prints a stack trace and continues, so a partially written payload is
  reported as a successful install.
- **Missing gnuplot** on Linux/macOS — disables real-time plotting with an actionable
  message (`brew install gnuplot`, `apt install gnuplot`); simulation, power flow and
  curve extraction keep working. Degradation is per-feature, not global.
- **Compile on non-Windows** — button disabled with an explanation. Today
  `extractVswhere` dereferences a null `File` on any non-Windows platform, throwing an
  NPE that the caller's `catch (IOException)` does not cover.
- **Missing or empty `version.txt`** — `getVersion()` guards only `IOException`, but
  `getResourceAsStream` returns null rather than throwing, so the app currently dies
  with an uncaught NPE during construction. Handled explicitly.

## Verification

**Gatekeeper spike, before the refactor.** Confirm whether binaries extracted from the
jar inherit macOS quarantine. If they do, the extractor clears the attribute on
extracted executables. This runs first because the answer could invalidate the
extraction design after it is built.

**Stage 1 no-op proof.** The refactored jar and today's jar run the same test system on
Windows and Linux and must produce byte-identical `output.trj`, `.res` files and console
output. Any difference is a refactor bug, isolated from platform and payload changes.

**Helios differential test.** The GUI-generated `PFCcmd.txt` runs through both PFC and
helios, diffing `in_net.res`, `in_trfo.res`, `in_gen.res`, `in_bal.res` and
`in_volt_trfo.dat`. The command script is adapted only where they genuinely differ.
This matters because the script's top-level `S` maps to `cmd_save_matlab` in helios
while PFC's `S` is handled in `seloutput_c.f90`, and the two may not agree.

**Platform matrix.** Manual smoke on all three platforms using cases from
`stepss-test-systems`: load data, run power flow, run a simulation, extract curves,
open a file in the editor.

## Sequence

1. Gatekeeper spike on macOS.
2. Build the platform layer; migrate all 34 sites; change nothing observable. Verify by
   no-op proof.
3. Add `MACOS_ARM64` to the enum and manifest.
4. Swap payloads to the release archives; add the URAMSES packaging target.
5. Replace PFC with helios; run the differential test; adapt the script if needed.
6. Platform matrix acceptance.

Steps 2 and 5 are the substantive ones. Step 2 is deliberately behavior-frozen so the
riskiest edit — 34 sites in a 5,200-line class with no test suite — is verifiable
against current behavior.

## Risks

| Risk | Mitigation |
|---|---|
| Helios diverges from PFC on the GUI's command script | Differential test in step 5; adapt script where they differ |
| macOS static builds not yet released upstream | Everything but macOS acceptance proceeds; macOS consumes dynamic builds with a `brew` prerequisite in the interim |
| Refactor changes behavior on Windows/Linux | No-op proof in step 2 |
| Gatekeeper blocks extracted binaries | Spike first; clear the attribute if needed |
| `Desktop.open()` has no editor association for `.dat`/`.dst` | Fall back to a platform default (`notepad`, `xdg-open`, `open -t`) |
| Build now needs network to fetch payloads | Digests are pinned, and a gitignored cache makes only the first build network-bound; CI caches it between runs |
| A release tag is re-cut with different bytes | SHA-256 verification fails the build loudly rather than silently shipping different binaries |

## Payload changes

Deleted from git: `npp.zip`, `nppLicense.txt`, `PFC`, `PFC.exe`, `pfcLicense.txt`,
`dynsim.zip`, `codegen.exe`, `CODEGEN`, `URAMSES.zip`, the Linux `dyngraph` binary,
and the *Install Intel redistributables* menu item.

Fetched at build time: per-platform ramses, helios, dyngraph and codegen archives,
plus the uramses source tarball.

Still committed: `gpwin.zip`, `DOC.zip`, `vswhere.exe`, the Windows Intel
`dyngraph.exe`, and helios license text.

Net payload falls from 49.5 MB to roughly 32 MB even while carrying three platforms
instead of two, because the 30 MB Intel `dynsim.zip` is replaced by an 11.6 MB static
MinGW build, the `.pdb` debug symbols disappear, and Notepad++ and both PFC binaries
are removed.

## Deferred to P2

Migrating the Codegen compile step from Intel/Visual Studio to gfortran on all three
platforms: bundling `modules_l`/`modules_m`/`modules_wg`, driving
`Makefile.{linux,macos,windows}`, probing for gfortran and make, and retiring
`vswhere.exe`, the Intel `URAMSES.zip` and the `devenv` logic. P2 changes a
prerequisite for existing Windows users, so it gets its own design discussion.
