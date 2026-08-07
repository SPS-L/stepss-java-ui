# Task 7: Custom-model compilation acceptance — results

`COMPILE_ACCEPTANCE: pass-with-gaps`

Branch: `custom-model-compilation`.
Commit under test: `d5a76bf` ("Fix round 2: correct CODEGEN's inverted exit value, preserve external
simulator, harden against unchecked exceptions") — the results below were produced from that tree;
this document and the README changes are the commit that follows it.
Verification performed on: Linux x86_64 (this machine), real X display `DISPLAY=:0`,
OpenJDK 21.0.11, Apache Ant 1.10.14, GNU Fortran (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0,
GNU Make 4.3, OpenBLAS from `libopenblas-dev`, Python 3.13.11 with NumPy 2.3.5.

No Windows and no macOS machine exists in this environment, so every Windows/macOS row is
`PENDING` with the hardware named, exactly as in `platform-matrix-results.md`. Nothing was
inferred from the Linux result.

---

## READ THIS FIRST: what engine was actually under test

**The kit the shipped jar bundles cannot be compiled against.** `versions.properties` pins the
released `stepss-uramses` v3.55 source archive, and that release **predates the splice markers**
Task 1 introduced. The marker patch exists only as commit `8e7b1de` ("Add STEPSS GUI marker
comments to the model routers") on the **local, unpushed** branch `stepss-gui-markers` in the
sibling `stepss-uramses` working tree. It has not been pushed and no release carries it.

Steps 1–5 below therefore ran against a **locally built, marker-carrying kit**, not against
anything a user can download:

| | Released kit (what ships) | Local marker kit (what Steps 1–5 tested) |
|---|---|---|
| Source | `https://github.com/SPS-L/stepss-uramses/archive/refs/tags/v3.55.zip` | `git archive --prefix=stepss-uramses-3.55/ --format=zip HEAD` from the `stepss-gui-markers` working tree at `8e7b1de` |
| Container SHA-256 | `1e08fb31ef7b52a483733665c7deabaaa6d18be86959ab939d88f53e09c34cb0` | `c3a6a21cf5d79c7a84e043f68f5ba87b1c553e4156fc0a1dfb5f4b048636c155` |
| `uramses.manifest.sha256` (`UramsesKitPack` COMPUTE) | `404eb8953b83054fc9854874cce1e4b070104842ad64475156beaadc535cd0be` | `60a0620a244dc3862b532a3cd1fef4425ccecb831ee9eb94f7c5691d87c24c52` |
| Retained files | 198 | 198 |
| `STEPSS-GUI` markers in `src/usr_exc_models.f90` | **0** | 2 (`EXTERNALS`, `CASES`) |
| Resulting `dist/stepss.jar` | 32,135,928 bytes | 32,136,081 bytes |

The local kit differs from the released v3.55 tag in **six files only** — the five routers the
marker patch touches plus one README wording line:

```
$ cd stepss-uramses && git diff --stat v3.55 HEAD -- <the paths UramsesKitPack retains>
 README.md               | 2 +-
 src/usr_dctl_models.f90 | 4 ++--
 src/usr_exc_models.f90  | 3 ++-
 src/usr_inj_models.f90  | 3 ++-
 src/usr_tor_models.f90  | 4 ++--
 src/usr_twop_models.f90 | 6 ++++--
 6 files changed, 13 insertions(+), 9 deletions(-)
```

The module kits (`modules_l/`, `modules_m/`, `modules_wg/`) and every Makefile are byte-identical
to v3.55, so the compiler/ABI/link half of every result below is the real released kit's.

**Nothing from the local kit survives into this commit.** `versions.properties` still pins the
released archive and its real digest `404eb895…`; the marker kit was staged into
`payload-cache/` only for the duration of Steps 1–5 and then replaced from a byte-verified
backup. Restoration was confirmed (see *Step 9* below): `git diff versions.properties` is empty,
`ant clean jar` goes green against the released archive, and the freshly staged
`src/my/ramses/payload/uramses-kit-v3.55.zip` again contains **zero** `STEPSS-GUI` markers.

**No push, no tag, and no change to `uramses.tag`/`uramses.source.url` was made.** Publishing
`8e7b1de` is the project owner's decision, and Step 6 is deferred to them (see below).

## What "the GUI was driven" means here — precisely

Steps 1–5 were run through the **real Swing GUI on a real X display**, not programmatically:

- a real `my.ramses.RamsesUI` frame, constructed and shown (`window title=STEPSS showing=true
  size=1928x1002`);
- the real `Codegen` tab selected by a **`java.awt.Robot` mouse click on the tab**;
- *Load Files for Codegen*, *Run Codegen* and *Compile* each pressed by a **`java.awt.Robot`
  mouse click at the button's on-screen centre**. Every click was verified to have reached the
  real `JButton` by a temporary `ActionListener` spy; the log line
  `CLICK <button>: Robot click DELIVERED to the real button` appears for every one of them, and
  the `doClick()` fallback was never taken;
- the real modal `JOptionPane` dialogs were allowed to open, their text captured, and each was
  then dismissed by **clicking its own default button** (`Accept`/`OK`), the same route a user
  takes.

So button enablement, the dialogs, and EDT marshalling **were** exercised. Two things were not,
and are the honest limit of this run:

1. **The `JFileChooser` was replaced.** A native file-selection dialog cannot be driven without a
   human, so `mfileChooser` was swapped for a subclass returning `APPROVE_OPTION` and the chosen
   files. Everything downstream of the chooser — `loadCodegenFilesActionPerformed` itself
   included — is the shipped code, driven by a real click.
2. **No human looked at the screen.** Dialog *text* was captured and is quoted verbatim below;
   dialog *appearance* (layout, wrapping, whether the HTML renders legibly) was not judged.

The driver is `GuiCompileDrive.java`, written to the session scratchpad. It is a test harness, not
part of the repository, and was not committed.

---

## Step 1: Linux round trip (GUI)

Model: `custom_models/exc_ENTSOE_lim.txt`, extracted **from the staged kit itself**
(`unzip -j src/my/ramses/payload/uramses-kit-v3.55.zip custom_models/exc_ENTSOE_lim.txt`), not from
the uramses checkout.

```
BEFORE load: Compile.enabled=false execCodegen.enabled=false
CLICK Load Files for Codegen: Robot click DELIVERED to the real button
AFTER load:  execCodegen.enabled=true Compile.enabled=false
CLICK Run Codegen: Robot click DELIVERED to the real button
AFTER codegen: Compile.enabled=true
generated .f90: [<toolDir>/exc_ENTSOE_lim.f90]
CLICK Compile #1: Robot click DELIVERED to the real button
immediately after click: Compile.enabled=false      <- disabled for the duration of the build
```

Pane content, verbatim and in order:

```
Preparing the build kit...
Building custom simulator with gfortran...

Checking dependencies...

Checking for gfortran...
  Found: GNU Fortran (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0

Checking for pre-compiled modules...
  Found: modules_l/libramses.a
  Found: 56 module files in modules_l/

Checking module kit and gfortran compatibility...
  Kit:      RAMSES v3.55, built 2026-08-05T23:05:00Z on ubuntu-24.04
  Built by: GNU Fortran (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  OK: module version 15 matches gfortran

All dependencies satisfied.
...
Linking dynsim...
Executable built: Release_l/dynsim

Build succeeded: <toolDir>/uramses/Release_l/dynsim
Simulations will now run on this custom simulator.
```

Dialog raised, verbatim:

```
[Compilation complete] <html>Custom simulator built.<br>Simulations will now run on it
instead of the bundled engine.</html>
```

Afterwards: `ramsesExec=<toolDir>/uramses/Release_l/dynsim`, `savedynsim.enabled=true`,
`Compile.enabled=true`. Built binary 9,441,240 bytes.

**Result: PASS.** Every expectation in the brief's Step 1 was met, including the exact
`OK: module version 15 matches gfortran` line.

## Step 2: Simulation on the custom engine

**No case in `stepss-test-systems` names `exc_ENTSOE_lim`.** `grep -rl exc_ENTSOE_lim` over the
whole test-systems tree returns nothing; the Nordic case the brief names uses the built-in
`EXC GENERIC1`. The brief's own premise is unavailable, so this step was run two ways.

### 2a. The built engine is a working simulator (the brief's fallback)

Case: `stepss-5-bus-test-system` (`lf1solv.dat` + `dyn.dat` + `solveroptions.dat` +
`nothing.dst`, 1000 s), which uses the user-router exciter `exc_GENERIC3`.

| Engine | Exit | Result |
|---|---|---|
| Bundled RAMSES 3.55 (`ramses-linux-x86_64-v3.55.tar.gz`) | 0 | `**Simulation finished**`, `obs.trj` 35,203,970 bytes |
| Custom-built `dynsim` | 0 | `**Simulation finished**`, `obs.trj` 35,203,970 bytes |

`cmp` reports the two `obs.trj` files **byte-identical**
(`ee862e90a4daa3028882a01bc623861eef083eeae455d03cddf4563c48055a3e`).

### 2b. The spliced router really resolves a model (a stronger test the brief could not specify)

`exc_ENTSOE_lim` is *already registered* in the shipped router, so running it proves nothing about
splicing. To get a genuinely new model, the kit's own `exc_ENTSOE_lim.txt` was copied to
`exc_STEPSS_ACC.txt` with only its model-name line changed, and **both** files were loaded and
compiled through the GUI in one pass. The same 5-bus case was then run with its `EXC` record
rewritten to `EXC 'exc_STEPSS_ACC'` plus that model's 21 parameters.

| Engine | Result on the `exc_STEPSS_ACC` case |
|---|---|
| Bundled RAMSES 3.55 | `STOP CALL FROM exc_STEPSS_ACC:` / `Exciter model not found in definition` |
| Custom engine built **without** `exc_STEPSS_ACC` (Step 1's binary) | same `Exciter model not found in definition` |
| Custom engine built **with** `exc_STEPSS_ACC` spliced in | runs; 457 time steps; `**Simulation finished**`; `obs.trj` 320,600 bytes |

Three engines, one data file, one variable: whether the splice ran. This is the decisive evidence
that `RouterSplicer` registers a new model and the built simulator resolves it by name at run
time.

The spliced-model run stops at t≈457 steps on `STOP CALL FROM DCTL sim_minmaxspeed` — the machine
leaves its speed band. That is a consequence of the **parameter values invented for this test**
(no published parameter set for `exc_ENTSOE_lim` on a 5-bus machine exists), not of the pipeline:
it happens far past model resolution, past initialisation, and after the simulator has written a
valid trajectory.

**Result: PASS** (2a as the brief's sanctioned fallback, 2b as stronger proof).

## Step 3: Idempotence on the real pipeline

*Compile* was pressed a **second** time, without restarting, in every GUI run.

```
COMPILE#2 finished=true
Build succeeded: <toolDir>/uramses/Release_l/dynsim
[Compilation complete] Custom simulator built. ...
```

After two compiles, in the two-model run:

```
$ grep -c "case('exc_ENTSOE_lim')" <toolDir>/uramses/src/usr_exc_models.f90
1
$ grep -c "case('exc_STEPSS_ACC')"  <toolDir>/uramses/src/usr_exc_models.f90
1
$ grep -c "external :: exc_ENTSOE_lim" <toolDir>/uramses/src/usr_exc_models.f90
1
```

The router text shows exactly the intended two behaviours side by side — `exc_ENTSOE_lim` sitting
where the kit ships it (skipped, not re-spliced) and `exc_STEPSS_ACC` inserted immediately before
the marker:

```
   external :: exc_ENTSOE_lim
   external :: exc_STEPSS_ACC
   !<<STEPSS-GUI:EXTERNALS>>
...
      case('exc_ENTSOE_lim')
         exc_ptr=>exc_ENTSOE_lim

      case('exc_STEPSS_ACC')
         exc_ptr=>exc_STEPSS_ACC

      !<<STEPSS-GUI:CASES>>
```

**Result: PASS — `1`, not `2`.** This is the check Task 6's Critical 1 broke (`prepare()` deleting
the kit without re-extracting) and its fix restored; the fix holds on the real pipeline, not just
in the harness.

## Step 4: No-regression trajectory comparison

Two independent comparisons were run.

**(a) `tools/nordic_gate.sh` — upstream's own CI regression gate**, which runs the Nordic
voltage-collapse case (`examples/Nordic`, built-in models only) and feeds `obs.trj` to
`tools/compare_trj.py compare` against RAMSES' own committed baseline
`tests/baselines/nordic_baseline.npz`:

```
### CUSTOM ENGINE
run OK: exit 255, 8166 time steps
samples: 751  cols: 1417  max |diff|: 0.000000e+00  (worst margin at t=0.00 s, col 40)
final time: 163.1400 s  (baseline 163.1400 s)
PASS (rtol=0.0001, atol=1e-06, trip-tol=1 s)

### BUNDLED ENGINE
run OK: exit 255, 8166 time steps
samples: 751  cols: 1417  max |diff|: 0.000000e+00  (worst margin at t=0.00 s, col 40)
final time: 163.1400 s  (baseline 163.1400 s)
PASS (rtol=0.0001, atol=1e-06, trip-tol=1 s)
```

**Tolerance the script reports and applies: `rtol = 1e-4`, `atol = 1e-6`, trip-tol = 1 s**, tested
elementwise as `|a-b| <= atol + rtol*|b|` plus a final-time window (`compare_trj.py`'s
`DEFAULT_RTOL` / `DEFAULT_ATOL` / `DEFAULT_TRIP_TOL`).

Both engines produce the identical step count (8166), the identical collapse instant (163.14 s)
and **max |diff| exactly 0.000000e+00** against the reference — i.e. not merely within tolerance,
but bit-for-bit equal to RAMSES' own baseline and therefore to each other.

**(b) Direct bundled-vs-custom comparison** on the 5-bus case (Step 2a): `obs.trj` byte-identical,
same SHA-256.

**Result: PASS**, with a stronger outcome than the brief anticipated — the difference is zero, not
merely within tolerance.

## Step 5: Failure paths

### 5a. Missing compiler (gfortran off `PATH`)

Run with `PATH` set to a directory containing only symlinks to `make`, `sh`, `bash` and `env` — no
`gfortran`, no `gfortran-N`.

Dialog raised, verbatim:

```
[Compilation failed] <html>gfortran was not found. Compiling custom models needs gfortran, make
and OpenBLAS:<br>    Debian/Ubuntu: sudo apt install gfortran make libopenblas-dev<br>
Fedora/RHEL:   sudo dnf install gcc-gfortran make openblas-devel<br>    Arch:
sudo pacman -S gcc-fortran make openblas</html>
```

Recovery, checked afterwards: `ramsesExec = <toolDir>/dynsim` (the **bundled** engine, not a kit
path), `savedynsim.enabled=false`, `Compile.enabled=true`, and pressing *Compile* a second time
reproduced the same clean failure rather than wedging. Running that restored `ramsesExec` on the
5-bus case afterwards: exit 0, `**Simulation finished**`, `obs.trj` byte-identical to the
bundled-engine baseline.

**Result: PASS** — the dialog names gfortran and carries the `apt install` line, and simulation
still works on the bundled engine.

### 5b. ABI-mismatched `FC=`

No second gfortran generation can be installed here (no root, and only sps-lab.org is reachable),
so the mismatch was produced by a **wrapper script named `gfortran`** that delegates every real
compile to `/usr/bin/gfortran-13` and rewrites the ABI banner of the *probe* module it emits from
`15` to `16` — which is exactly and only what `tools/check_kit.sh` and
`FortranToolchain.compilerAbi()` read. `PATH` was arranged so this wrapper was the only Fortran
compiler visible (no `gfortran-13`…`gfortran-16` reachable), forcing
`FortranToolchain.choose`'s lenient "nothing matches, return a real compiler anyway" branch and
letting `check-deps` be the authority, as designed.

Pane content, verbatim — upstream's remedy text reaches the user unaltered:

```
Checking module kit and gfortran compatibility...
  Kit:      RAMSES v3.55, built 2026-08-05T23:05:00Z on ubuntu-24.04
  Built by: GNU Fortran (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
ERROR: modules_l/ holds GFORTRAN module version 15 but gfortran emits version 16.
The .mod files can only be read by the gfortran release that wrote them.
Install a matching gfortran and pass it explicitly, e.g. on Ubuntu
(the kit's own compiler is printed above; adjust the number to match):
  sudo apt install gfortran-13
  make -f build/Makefile.linux FC=gfortran-13
make: *** [build/Makefile.linux:138: check-deps] Error 1

Compilation failed (exit 2). The build log above carries the compiler's own message.
```

Dialog raised, verbatim:

```
[Compilation failed] <html>Compilation failed (exit 2). The build log above carries the
compiler's own message.</html>
```

`ramsesExec` restored to the bundled `<toolDir>/dynsim`; `savedynsim.enabled=false`.

**Result: PASS**, with the caveat that the compiler generation was *simulated at the exact
interface the code reads*, not installed. See the minor finding below about the dialog carrying no
remedy of its own.

### 5c. Versioned-compiler scan (Task 5's fallback, not in the brief's list)

Because 5b's rig makes a genuinely mismatched plain `gfortran` available, the complementary case
was free to run: `PATH` containing **both** the ABI-16 wrapper (as plain `gfortran`) and a real
`gfortran-13`. STEPSS rejected the plain compiler and selected the versioned one on its own:

```
  OK: module version 15 matches gfortran-13
...
Build succeeded: <toolDir>/uramses/Release_l/dynsim
```

**Result: PASS** — the "distro ships `gfortran-13` but its default `gfortran` is the wrong
generation" case resolves with no user action.

### 5d. The shipped, pinned kit (the known gap, exercised)

With the restored pin — the released v3.55 kit, no markers — the same GUI run gives:

```
Preparing the build kit...
Router for kind 'exc' does not carry the !<<STEPSS-GUI:EXTERNALS>> marker. The bundled uramses
kit predates the marker contract this build requires.

[Compilation failed] <html>Could not prepare the build:<br>Router for kind 'exc' does not carry
the !&lt;&lt;STEPSS-GUI:EXTERNALS&gt;&gt; marker. The bundled uramses kit predates the marker
contract this build requires.</html>
```

`ramsesExec` stays on the bundled engine and *Compile* re-enables. **The gap degrades safely and
says exactly what is wrong** — but a user of today's jar cannot compile a custom model at all.

## Step 6: Pin update — DEFERRED, and why

`stepss-uramses` has **not** published a release carrying Task 1's markers, so per the brief
`versions.properties` was left at `v3.55` with its real digest `404eb895…`. This is a known gap,
not an oversight.

To close it, the project owner must:

1. push `stepss-gui-markers` (or merge `8e7b1de` to the default branch) in `stepss-uramses` and
   tag a release;
2. in this repo, set `uramses.version`, `uramses.tag` and `uramses.source.url` to that tag;
3. run `ant fetch-uramses`, then
   `java -cp build/classes my.ramses.platform.UramsesKitPack payload-cache/stepss-uramses-<v>.zip /dev/null COMPUTE`
   and paste the printed digest into `uramses.manifest.sha256`;
4. rebuild and re-run Steps 1–5 against the real release.

If the tag is cut from `8e7b1de` unchanged, the manifest digest will be
`60a0620a244dc3862b532a3cd1fef4425ccecb831ee9eb94f7c5691d87c24c52` — computed here, recorded so
the owner has something to check against rather than a number to invent.

## Step 9: Restoration and final build

```
$ git diff --stat versions.properties          # (empty - back to the pinned digest)
$ sha256sum payload-cache/stepss-uramses-3.55.zip
1e08fb31ef7b52a483733665c7deabaaa6d18be86959ab939d88f53e09c34cb0     # the released archive
$ ant clean jar
     [java] uramses kit OK: 198 files -> src/my/ramses/payload/uramses-kit-v3.55.zip (4844 KB)
BUILD SUCCESSFUL
Total time: 3 seconds
$ unzip -p src/my/ramses/payload/uramses-kit-v3.55.zip src/usr_exc_models.f90 | grep -c STEPSS-GUI
0                                                    # released kit, no markers - correct
$ ls -la dist/stepss.jar
-rw-rw-r-- 1 apetros apetros 32135928 dist/stepss.jar
$ tools/compile-harness.sh
... 19 PASS lines ...
ALL CHECKS PASSED
```

`UramsesKitPack` verified the released archive against the unchanged pin, which is the real proof
that the pinned digest was restored: had it not been, the build would have failed the manifest
check rather than gone green.

---

## Per-check matrix

| # | Check | Linux x86_64 | Windows x86_64 | macOS arm64 |
|---|---|---|---|---|
| 1 | Round trip: Codegen → Compile → `Build succeeded` | **PASS** — real GUI, real Robot clicks, marker kit | PENDING — requires Windows x86_64 hardware with MSYS2/MinGW-w64 (`pacman -S mingw-w64-x86_64-gcc-fortran mingw-w64-x86_64-openblas make`) | PENDING — requires Apple Silicon hardware with `brew install gcc openblas` + Command Line Tools |
| 1b | `check-deps` prints `OK: module version 15 matches gfortran` | **PASS** — verbatim | PENDING — expect module version 16 there (`modules_wg`, gfortran 16.1) | PENDING — expect module version 16 there (`modules_m`, gfortran 16.1) |
| 2 | Simulation runs on the custom engine | **PASS** — 5-bus case, exit 0, trajectory byte-identical to the bundled engine | PENDING | PENDING |
| 2b | Spliced (genuinely new) model resolves at run time | **PASS** — three-engine differential on `exc_STEPSS_ACC` | PENDING | PENDING |
| 3 | Idempotence: compile twice, exactly one case per model | **PASS** — `1`, both models | PENDING | PENDING |
| 4 | No-regression vs the bundled engine (`compare_trj.py`) | **PASS** — `max |diff| 0.000000e+00`, rtol 1e-4 / atol 1e-6 / trip-tol 1 s | PENDING | PENDING |
| 5a | Missing gfortran → dialog + bundled engine preserved | **PASS** | PENDING — expect the MSYS2 message from `missingToolMessage`, unverified | PENDING — expect the Homebrew message, unverified |
| 5b | ABI-mismatched `FC=` → `check-deps` remedy text in the pane | **PASS (simulated compiler generation)** — see 5b | PENDING | PENDING |
| 5c | Versioned-compiler scan picks a matching `gfortran-N` | **PASS** | PENDING | PENDING |
| 5d | Shipped pinned kit fails cleanly on the missing markers | **PASS** — accurate message, engine preserved | PENDING | PENDING |
| 6 | `versions.properties` pins a marker-carrying release | **DEFERRED** — no such release exists; pin left at v3.55 | DEFERRED | DEFERRED |
| 7 | Windows MSYS2 `PATH` prepending (`extraPathEntries`/`buildEnvironment`) | N/A — returns an empty list on Linux, so the code path is unreachable here | PENDING — this is the only place it can be exercised | N/A |
| 8 | Dialog *appearance* (layout/wrapping/legibility) | **NOT VERIFIED** — text captured verbatim, but no human looked at the screen | PENDING | PENDING |
| 9 | `JFileChooser` selection flow | **NOT VERIFIED** — the chooser was stubbed; a native file dialog needs a human | PENDING | PENDING |

## Findings

**1 — HIGH, and the reason for `pass-with-gaps`: the shipped jar cannot compile anything.**
The pinned kit carries no markers, so *Compile* fails at `prepare()` for every user until
`stepss-uramses` tags a release containing `8e7b1de`. The failure is clean and self-explaining
(5d), and the README this commit ships describes the feature as working — which it is, but only
against a kit no user can obtain. **Either tag the release before shipping this branch, or hold
the README change.** This is a release-sequencing decision for the project owner.

**2 — minor: the ABI-mismatch dialog carries no remedy.** On 5b the dialog says only
`Compilation failed (exit 2). The build log above carries the compiler's own message.` The
actionable `sudo apt install gfortran-13` line is in the pane, not the dialog. A user who
dismisses the dialog without reading the pane behind it is told nothing they can act on. Contrast
5a, where the dialog carries the full install command. Not a defect in the sense of anything being
wrong, but the two failure paths are inconsistent in how much help they give.

**3 — informational: `Compile` re-enables before a fast failure can be observed as "disabled".**
On the two failing runs the driver sampled `Compile.enabled` immediately after the click and saw
`true`, because `prepare()`/the probe failed and `reportCompileOutcome` re-enabled the button
within that window. On the successful run the same sample correctly read `false`. This is correct
behaviour (the button must not stay disabled after a failure), recorded only so the log lines are
not mistaken for a defect.

**4 — informational: `exc_ENTSOE_lim` cannot, on its own, prove splicing.** The brief's Steps 1–3
name a model the kit ships pre-registered, so `splice()` correctly skips it and the router is
unchanged by the splice. Step 2b's renamed model exists because of this. Anyone repeating this
acceptance should use a model that is *not* already in the router.

## Verdict

`COMPILE_ACCEPTANCE: pass-with-gaps`

**What passed cleanly and is independently reproducible on Linux:** the full GUI round trip with
real mouse clicks; the built engine simulating a real case with a byte-identical trajectory;
idempotence over two compiles; zero trajectory difference against RAMSES' own Nordic baseline
inside `compare_trj.py`'s 1e-4/1e-6/1 s tolerance; both failure paths, plus the versioned-compiler
fallback and the missing-marker path.

**The gaps, and who must close them:**

1. **The pin (finding 1).** `stepss-uramses` must publish a release carrying `8e7b1de` before any
   user can use this feature. Until then Steps 1–5's evidence is about a kit built here, not the
   one that ships — stated at the top of this document so it cannot be misread.
2. **Windows x86_64 and macOS arm64**, every row. No such hardware exists in this environment.
   Windows additionally holds the *only* place `extraPathEntries`/`buildEnvironment`'s MSYS2 PATH
   prepending can be exercised at all.
3. **Two human-eye checks on Linux**: whether the dialogs *look* right, and the real
   `JFileChooser` selection flow.
4. **The ABI-mismatch check used a simulated compiler generation** (5b). Repeating it on a machine
   with two real gfortran generations installed would remove the one place this document reasons
   about a stand-in rather than the real thing.
