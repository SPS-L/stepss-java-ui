# P2 — Custom-model compilation on gfortran

**Date:** 2026-08-07
**Status:** Design approved, ready for planning
**Scope:** stepss-java-ui, plus one marker change in stepss-uramses. Follows P1,
`docs/superpowers/specs/2026-08-06-cross-platform-toolchain-design.md`.

## Problem

P1 removed custom-model compilation outright. The old path was Windows-only and
Intel-only: it located Visual Studio with `vswhere.exe`, rewrote an
`exeramses.vfproj` file list, and drove `devenv /rebuild` against a hand-assembled
`URAMSES.zip` whose modules were dated 2022-01-24 while the engine was 3.55.
Dropping `dynsim.zip` took that path's runtime with it, so P1 deleted the lot and
left `CompileActionPerformed` showing an unavailable dialog on every platform.

Model *generation* still works everywhere — the Codegen tab runs CODEGEN and saves
`.f90` files. Only the build step is missing, and with it the ability to run a
simulation against a user-written model.

Three properties of the old path are worth not reinstating:

- It registered models by splicing hand-split `usr_*_models.f90.1`/`.2` template
  halves that existed only inside the hand-assembled zip. Upstream has no such
  files, and the split was invisible to anyone reading the uramses repo.
- It matched model kinds with `flname.substring(0,3)`, which silently ignored
  `dctl` models and throws on any generated name shorter than three characters.
- It asserted on `devenv`'s exit code with `assert`, which is disabled at runtime
  unless the JVM is started with `-ea`, so a failed build fell through to setting
  `ramsesExec` at a path that did not exist.

## Goals

- Compile works on Windows x86_64, Linux x86_64 and macOS arm64.
- The build is defined once, upstream, and the GUI drives it — so the STEPSS
  route and the documented PyRAMSES route are provably the same build.
- Registering a generated model into the router is a declared contract, not a
  guess about upstream's source layout.
- Compiling twice in a row produces the same result as compiling once.

## Non-goals

- The Intel / Visual Studio route. P1 deleted it; `build/msvs/` is dropped from
  the payload and `modules_wi/` is not shipped.
- Building `ramses.so`. That is PyRAMSES' artifact; the GUI needs `dynsim`.
- Bundling a Fortran compiler. Users install one.
- Editing model source in the app.
- Intel Macs, unchanged from P1.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | Drive upstream's `build/Makefile.{linux,macos,windows}` | The Makefiles already exist and are CI-tested on the same runner images that build the kits. One build definition, maintained by whoever ships the kits. `check-deps` supplies the gfortran ABI gate and the install advice for free. |
| 2 | `make` and `bash` become prerequisites; on Windows that means MSYS2 | An ABI-16 gfortran on Windows comes from MSYS2 anyway, so this is one prerequisite rather than two. |
| 3 | Generated models register at explicit marker comments added upstream | Turns an inferred coupling into a reviewable contract. A future uramses contributor sees the marker and knows not to remove it; without one, an upstream reformat breaks the GUI with no warning at edit time. |
| 4 | Payload is the uramses source zip, repacked at build time | One pin, one bump. The published `stepss-uramses-<ver>.zip` carries source *and* all four module kits, so nothing needs assembling from `stepss-ramses`' separate `uramses-modules_*` assets. |
| 5 | The payload is verified by content, not by container bytes | GitHub's auto-generated source archives carry no byte-stability guarantee — a compression change on their side in early 2023 altered checksums for already-published tags. A digest pinned against the container can fail months later on unchanged content. |
| 6 | Only the running platform's module kit is unpacked at runtime | ~12 MB on disk instead of ~35 MB, for no loss of function. |
| 7 | A successful build adopts the new `dynsim` automatically | Matches what the old path did, and reuses the engine swap that *Load External Simulator* already performs. |

### Upstream dependency

`stepss-uramses` must tag a release whose five router files carry the markers of
Decision 3. P2 pins that tag. Implementation proceeds against a local marker patch
and the pin moves to the real tag when it lands — the same shape as P1's dependency
on static macOS builds.

The markers are two comments per router, at the two points a model must be
registered:

```fortran
   external :: exc_ENTSOE_lim
   !<<STEPSS-GUI:EXTERNALS>>
```

```fortran
      case('exc_ENTSOE_lim')
         exc_ptr=>exc_ENTSOE_lim
      !<<STEPSS-GUI:CASES>>

      case default
```

They are inert comments: `make` from a plain checkout behaves exactly as it does
today, and a user hand-editing a router is unaffected.

The same patch normalises the five routers, so the GUI writes into a consistent
shape rather than working around historical drift:

- **`case default` is removed from all of them.** It appears only in
  `usr_exc_models.f90` and `usr_dctl_models.f90`, and in both the body is empty.
  An unmatched `select case` with no default already does nothing, so this is a
  pure consistency change with no behavioural effect — the caller's contract is
  unchanged, and an unresolved name still leaves the pointer untouched.
- **Commented-out entries are deleted.** There are four such lines, and they are
  not the same kind of thing:
  - `usr_tor_models.f90` carries a commented `tor_sultan` case. No `tor_sultan`
    symbol exists in `libramses.a` and it is not declared `external`, so this is
    dead text.
  - `usr_twop_models.f90` carries a commented `twop_HVDC_VSC` case. That one is
    not dead: the model is declared `external` and defined in `libramses.a`, so
    it ships in RAMSES and only the commented case makes it unnameable from a
    `.dat` file. It is **restored — uncommented — rather than deleted**, which
    makes a shipped model reachable again:

    ```fortran
       case('twop_HVDC_VSC')
             twop_ptr => twop_HVDC_VSC
    ```

    A sweep of all five routers, comparing every `external` declaration against
    the live `=>` assignments and against the symbols defined in
    `modules_l/libramses.a`, found `twop_HVDC_VSC` to be the *only*
    declared-but-undispatched model. Two earlier passes over-reported: matching
    case labels against symbol names flags models whose label differs in case
    (`case('exc_kundur')` dispatches `exc_KUNDUR`), and failing to exclude
    comment lines counts the commented entry itself as live. The pointer
    assignment is the reliable test.

## Payload

`versions.properties` gains a `uramses` block:

```properties
uramses.version=3.55
uramses.repo=SPS-L/stepss-uramses
uramses.tag=v3.55
uramses.source.url=https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v3.55.zip
uramses.manifest.sha256=<sha256 of the retained-content manifest>
```

`stepss-uramses` is the one public component, so this fetch is plain HTTPS and
needs neither `gh` nor `STEPSS_TOKEN`. A clone without SPS-L access still cannot
build the jar — the other four payloads are private — but nothing about *this*
payload adds to that.

### Fetch, verify, repack

A build-time Java tool, `UramsesKitPack`, follows the pattern already set by
`PayloadManifestCheck`. `ant fetch-payloads` downloads the source zip into
`payload-cache/`; `stage-payloads` runs the tool, which:

1. Extracts the archive. Its single top-level directory is `stepss-uramses-<version>/`
   — GitHub strips a leading `v` from the tag — and that prefix is stripped.
2. Computes a manifest: one `<sha256>  <path>` line per retained file, sorted by
   path, and takes the SHA-256 of that manifest. A mismatch against
   `uramses.manifest.sha256` fails the build, naming the first differing path.
   Re-compression by GitHub is invisible to this check; a single changed byte in
   any retained file is not.
3. Repacks the retained tree into
   `src/my/ramses/payload/uramses-kit-v<version>.zip`.

Retained: `build/Makefile.{linux,macos,windows}`, `src/`, `custom_models/`,
`tools/`, `modules_l/`, `modules_m/`, `modules_wg/`, `README.md`, `LICENSE.rst`.

Dropped: `modules_wi/` (16.5 MB, Intel, unreachable in P2), `tests/` (a 5.3 MB
baseline `.npz`), `examples/`, `.github/`, `CLAUDE.md`, `build/msvs/`.

The staged zip is roughly 5 MB, against 12 MB for the published archive; the jar
goes from 26 MB to about 31 MB. Repack determinism does not need to be
guaranteed, because verification happens on the manifest before the repack, not
on the repacked bytes.

## Architecture

One new class, `my.ramses.compile.ModelCompiler`. `CompileActionPerformed` becomes
a call into it plus the UI wiring for its outcome. The pipeline:

**1. Extract.** `ToolExtractor` unpacks the kit into `<toolDir>/uramses/`,
version-stamped like every other payload, so a bumped `uramses.version` replaces
an older kit instead of silently leaving it in place.

This needs one new capability. `ToolSpec.Payload` extracts a single named member
today; the kit needs a subtree. A `Kind.ZIP_TREE` payload carries a set of
retained path prefixes, and the extractor writes only entries matching one of
them — which is also how Decision 6 is implemented, by including only
`modules_<suffix>/` for the running platform. The existing path-traversal guard
applies unchanged.

**2. Reset to pristine.** `src/usr_*_models.f90` and `custom_models/` are restored
from the embedded archive before every compile. Without this, a second Compile
splices a second `case` for the same model and the build fails on a duplicate
case label — an error that would read as a user mistake rather than a GUI bug.

**3. Stage models.** Each CODEGEN-generated `<kind>_<NAME>.f90` is copied from the
working directory into `custom_models/`, where the Makefiles' `$(wildcard
custom_models/*.f90)` finds it. CODEGEN emits plain subroutines with no module
dependencies, so the Makefiles' explicit dependency rules — which exist for the
two example models that *are* modules — need nothing added.

**4. Splice.** For each staged model, `external :: <kind>_<NAME>` is inserted at
`!<<STEPSS-GUI:EXTERNALS>>` and the case pair at `!<<STEPSS-GUI:CASES>>` in
`src/usr_<kind>_models.f90`. Kind is the filename's leading segment up to the
first underscore, matched against the five known kinds — `exc`, `inj`, `tor`,
`twop`, `dctl`. An unrecognised kind, or a router missing its marker, aborts
before `make` runs and names the offending file.

The insertion is one rule, not five special cases: every router names its
procedure pointer `<kind>_ptr`, so the case body is always
`<kind>_ptr=><kind>_<NAME>`. The routers differ in ways the splice never has to
know about — `external ::` versus bare `external`, `modelname4` in exc/inj/tor
versus `modelname5` in twop/dctl, and a `case default` present only in exc and
dctl — because the markers, not the surrounding syntax, locate both insertion
points.

**5. Probe and check.** `make` and a suitable `gfortran` are resolved through
`PlatformLauncher.findOnPath`, then `make -f build/Makefile.<plat> check-deps`
runs. Upstream's `tools/check_kit.sh` compares the GFORTRAN module ABI baked into
the kit's `.mod` files against what the local compiler emits, and prints a
platform-specific remedy on mismatch. The GUI surfaces that output verbatim rather
than paraphrasing it, so the advice stays correct when upstream changes compilers.

If plain `gfortran` is absent or ABI-mismatched, the GUI scans `PATH` for versioned
`gfortran-<N>` binaries, probes each for its module ABI the same way `check_kit.sh`
does, and passes the first match as `FC=` — upstream explicitly supports this
override. Only if none matches does the compile stop.

**6. Build.** `make -f build/Makefile.<plat> exe`, with the working directory at
the kit root. The Makefiles live in `build/` but are always invoked from the root;
`-f` does not change make's working directory and every path inside them is
root-relative. Execution is async through Commons Exec with the Codegen pane as
the stream sink, `setExitValue(0)`, and the same `ShutdownHookProcessDestroyer`
the simulation and codegen runs use.

**7. Adopt.** On exit 0, `ramsesExec` points at `Release_<suffix>/dynsim`
(`dynsim.exe` on Windows), the file is marked executable, *Save executable*
enables, and the pane states plainly that later simulations use the custom engine.
On any non-zero exit, `ramsesExec` is left untouched — the bundled engine keeps
working — and the failure is reported.

### Platform mapping

| Platform | Makefile | Kit | Output |
|---|---|---|---|
| `WINDOWS_X86_64` | `build/Makefile.windows` | `modules_wg/` | `Release_wg/dynsim.exe` |
| `LINUX_X86_64` | `build/Makefile.linux` | `modules_l/` | `Release_l/dynsim` |
| `MACOS_ARM64` | `build/Makefile.macos` | `modules_m/` | `Release_m/dynsim` |

This is a fourth column on `Toolchain`'s existing per-platform table, not a new
branch site.

## Prerequisites

| Platform | Needs |
|---|---|
| Linux | `gfortran` at module ABI 15, `make`, OpenBLAS (`libopenblas-dev`) |
| macOS arm64 | `brew install gcc openblas` (ABI 16), `make` from the Command Line Tools |
| Windows | MSYS2 MinGW64: `mingw-w64-x86_64-gcc-fortran`, `mingw-w64-x86_64-openblas`, `make` |

The three kits are deliberately not on a common ABI: `modules_l` is gfortran 13.3 /
ABI 15 because it is built on `ubuntu-24.04`, while `modules_m` and `modules_wg`
are gfortran 16.1 / ABI 16 because the macOS and Windows runners track much newer
toolchains. Each platform's default compiler matches its own kit today, which is
why the common case needs no `FC=` override. Each kit records its own provenance in
`modules_<plat>/BUILDINFO.txt`, and `check_kit.sh` reads the ABI from the `.mod`
files themselves rather than from `BUILDINFO.txt`, so a hand-patched kit cannot
misreport.

On Windows the Makefile must run under MSYS2 MinGW64. The GUI probes for the MSYS2
root — `C:\msys64` by default, then the `MSYS2_ROOT` environment variable — and
prepends `mingw64\bin` and `usr\bin` to the child environment. That supplies
`make`, `gfortran`, OpenBLAS and the `bash` that `check-deps` needs, and it reuses
the PATH-prepending `PlatformLauncher.execEnvironment` already performs for
gnuplot and dynsim. macOS uses OpenBLAS by default and accepts
`BLAS=accelerate`; the GUI does not expose that choice.

## Error handling

Every failure produces one dialog naming what failed, and full output in the
Codegen pane. Compile stays enabled throughout, so a user can install what is
missing and retry without regenerating.

- **`make` or `gfortran` not found** — dialog names the tool and gives the
  platform's install command from the table above. On Windows it names MSYS2
  explicitly, since that is the non-obvious one.
- **ABI mismatch** — `check_kit.sh`'s message, verbatim, including the exact
  `apt install` / `brew` / `pacman` line and the `FC=` invocation it suggests.
- **OpenBLAS missing** — surfaces as a link failure. The pane carries the linker
  output and the dialog adds the platform's package name, since `cannot find
  -lopenblas` is not self-explanatory to the GUI's audience.
- **Unknown model prefix, or a router missing its marker** — refused before `make`
  runs, naming the file. A missing marker means the pinned kit predates the
  contract, and the message says so.
- **Non-zero `make` exit** — pane holds the full build log; dialog reports that
  compilation failed and points at the pane. `ramsesExec` is unchanged.
- **Compile with no generated models** — refused; the button only enables after a
  successful Run Codegen, but the check is explicit rather than assumed.

## Verification

No unit-test framework, unchanged from P1. Acceptance is behavioural:

- **Round trip, Linux.** Generate `exc_ENTSOE_lim` from the `.txt` upstream ships
  in `custom_models/`, compile, confirm `Release_l/dynsim` exists and is
  executable, then run a Nordic case whose EXC record names the model and confirm
  it produces trajectories.
- **Idempotence.** Compile twice without restarting. The second build must
  succeed, and `src/usr_exc_models.f90` must hold exactly one `case` for the
  model. This is the specific defect the pristine reset exists to prevent.
- **No regression.** The built `dynsim` on an *unmodified* Nordic case must match
  the bundled engine's `output.trj` within tolerance, compared with uramses'
  own `tools/compare_trj.py`. Both are RAMSES 3.55 from the same commit, so a
  difference indicates a mis-specified build rather than an engine difference.
- **Failure paths.** With `gfortran` removed from `PATH`, and with a deliberately
  ABI-mismatched `FC=`, confirm each produces its dialog and leaves `ramsesExec`
  pointing at the bundled engine.
- **Windows and macOS.** Manual, folded into P1's existing platform matrix, which
  is already `pass-with-gaps` pending hardware.

## Sequence

1. Marker patch to the five `stepss-uramses` routers; local until the tag lands.
2. Payload: `versions.properties` block, `UramsesKitPack`, `fetch-payloads` and
   `stage-payloads` wiring. Verifiable on its own — the staged zip either matches
   the manifest or the build fails.
3. `Kind.ZIP_TREE` in `ToolSpec`/`ToolExtractor`, with the per-platform kit filter.
4. `ModelCompiler`: reset, stage, splice. Testable without a compiler present, by
   inspecting the spliced routers.
5. Probe, `check-deps`, build, adopt. The step that needs a real toolchain.
6. UI wiring: re-enable Compile after Run Codegen, *Save executable* after a
   successful build, and remove both "not available in this release" dialogs.
7. Acceptance, and README updates to the two rows that currently read
   "not available this release".

Steps 4 and 5 are the substantive ones. Step 3 is small but touches P1 code that
every other payload depends on, so it stays separate from step 4.

## Risks

| Risk | Mitigation |
|---|---|
| The marker tag has not landed when implementation starts | Local marker patch; the pin moves when the tag does. Step 2 onward is independent of it. |
| Windows users lack MSYS2 and read the failure as a bug | The dialog names MSYS2 and the exact `pacman` line; README states the prerequisite alongside the existing Java one. |
| A distro's default gfortran drifts off the kit's ABI | `check-deps` already detects it; the versioned-`gfortran-N` scan resolves the common case without user action. |
| Upstream renames or restructures a Makefile target | The GUI calls only `check-deps` and `exe`, both documented in uramses' CLAUDE.md as the stable surface. |
| The kit and the bundled engine diverge in version | Both are pinned in `versions.properties`; the no-regression comparison catches a mismatch. |
| Extraction of a 35 MB tree slows first Compile | Only the running platform's kit is unpacked, and only on first use or after a version bump. |

## Payload changes

Added: `src/my/ramses/payload/uramses-kit-v3.55.zip`, staged from the fetched
source zip. Roughly 5 MB, taking the jar from 26 MB to about 31 MB.

Nothing is removed. `vswhere.exe` and the Intel `URAMSES.zip` were already deleted
in P1 and do not return.

## Deferred

- **A settings UI for the Fortran toolchain.** The probe plus `FC=` scan covers
  the cases we know of. A user with a compiler in a non-standard location is
  currently unserved; if that turns up in practice it wants a persisted setting,
  which the app has no store for today.
- **Building `ramses.so` from the GUI.** No GUI feature consumes it.
- **Statically linked macOS builds** of ramses, dyngraph and codegen, carried over
  from P1 and tracked upstream. Unrelated to compilation, which needs Homebrew
  gcc on macOS regardless.
- **Whether `twop_HVDC_VSC` behaves correctly.** The marker patch makes it
  dispatchable again, but nothing here exercises it: no test system in
  `stepss-test-systems` names it, and why it was commented out is not recorded.
  Restoring the case is what makes the shipped model reachable; confirming it
  produces sensible results is a RAMSES question, upstream.
