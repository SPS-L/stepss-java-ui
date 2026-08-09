# Task 8: Observable picker — manual acceptance results

`PICKER_ACCEPTANCE: pass-with-gaps`

Branch: `observable-picker`.
Commit under test: `3a697de` ("Drive Extract Curves through the picker instead of a terminal").
Verification performed on: Linux x86_64 (this machine), `DISPLAY=:0`, OpenJDK 21.0.11
(build 21.0.11+10-1-24.04.2-Ubuntu), Apache Ant 1.10.14, Ubuntu 24.04.4 LTS,
gnuplot 6.0 patchlevel 0. The bundled DYNGRAPH under test is the real pinned release,
extracted from the built jar: **dyngraph 1.2.0**, commit `365fbc694ae357e43a612560ca67ab344f3e1fc7`,
built `2026-08-07T23:08:02Z` — matching `versions.properties`' `dyngraph.version=1.2.0` pin exactly.

No Windows and no macOS machine exists in this environment, and this agent has no eyes on any
screen. Per the task's own constraint, **every row that needs a human at a keyboard or hardware
this machine does not have is recorded as `PENDING`, never inferred from another platform or
another check.**

---

## READ THIS FIRST: what "automatable" meant here, and what did not change that

This is a human-acceptance task run by an agent. The honest split is not "Linux vs.
Windows/macOS" the way the compile and platform-matrix precedents split it — it is **the
non-Swing plumbing vs. the dialog itself**, and that split holds on Linux too. The design
document says outright: "Manual acceptance on all three platforms ... is the only way to cover
the dialog itself, which the harness deliberately does not touch." That sentence is why
`ObservablePicker`'s own filter field, category/observable `JComboBox`es, `JList` greying,
button-enablement-as-seen-on-screen, the wait cursor, and the actual gnuplot window are marked
`PENDING` below even on Linux, where every other precedent in this repository could put a human
at a real X display. No amount of driving the classes headlessly substitutes for a person
looking at the dialog; this document does not pretend otherwise.

What **is** genuinely automatable, and was run for real, is everything the dialog produces or
consumes: `ObservableIndex.parse`, `Selection.label()`, `ReplayFile.emit`/`write`, and both
`DyngraphRunner` invocations, driven directly against the real bundled `dyngraph` binary on a
real trajectory from a real test system — not the `PickerHarness` fixture (which is a separate,
already-passing gate), and not a fabricated one. This is exactly the check the brief itself
names as the minimum bar: "invoking the real bundled dyngraph through `DyngraphRunner` on a
generated trajectory and confirming the `.cur`/`.plt` land where `viewCurvesButton` expects
them." Doing this end to end removes essentially all of the *data-correctness* risk in the
feature; what remains PENDING below is specifically the *human-observable* risk — does the
dialog look and behave right on screen — which no amount of headless driving can discharge.

---

## Automated verification — commands run and real output

### 1. Build

```
$ cd /home/apetros/Code/stepss/stepss-java-ui
$ ant clean jar
```

All twelve pinned release payloads verified by SHA-256, dyngraph on all three platforms at the
pinned 1.2.0 tag:

```
     [echo] verified ramses-windows-x86_64-v3.55.zip
     [echo] verified ramses-linux-x86_64-v3.55.tar.gz
     [echo] verified ramses-macos-arm64-v3.55.tar.gz
     [echo] verified stepss-helios-windows-x64.zip
     [echo] verified stepss-helios-linux-x86_64.tar.gz
     [echo] verified stepss-helios-macos-arm64.tar.gz
     [echo] verified dyngraph-windows-x86_64-v1.2.0.zip
     [echo] verified dyngraph-linux-x86_64-v1.2.0.tar.gz
     [echo] verified dyngraph-macos-arm64-v1.2.0.tar.gz
     [echo] verified codegen-windows-x86_64-v5.1.0.zip
     [echo] verified codegen-linux-x86_64-v5.1.0.tar.gz
     [echo] verified codegen-macos-arm64-v5.1.0.tar.gz
...
BUILD SUCCESSFUL
Total time: 3 seconds
```

`dist/stepss.jar`: 32,180,851 bytes.

### 2. `tools/picker-harness.sh`

```
$ tools/picker-harness.sh
... (58 PASS lines) ...
ALL CHECKS PASSED
```

`grep -c "^PASS"` → `58`; `grep -c "^FAIL"` → `0`. This is the pre-existing gate from Tasks 1-4;
re-run here only to confirm the tree under test is still green, not as new evidence of the
dialog.

### 3. Mechanical proof that no terminal window can open

The old code path this feature replaces called `PlatformLauncher.runInTerminal`. That call site
is gone, not merely unreached:

```
$ grep -n "runInTerminal" src/my/ramses/RamsesUI.java
(no output)
$ grep -rn "runInTerminal" src/ | grep -v '\.form'
src/my/ramses/platform/PlatformLauncher.java:169:    public static void runInTerminal(Platform p, List<String> argv, File dir)
```

`runInTerminal` exists only as its own definition; nothing in `src/` calls it any more. A human
still needs to confirm no terminal window flashes on screen (see the per-check matrix), but the
code path that used to open one is mechanically gone, not just untaken this run.

### 4. A real trajectory from a real test system, via the real bundled `dynsim`

Case: `stepss-5-bus-test-system` (`dyn.dat` + `lf1solv.dat` + `solveroptions.dat` + `nothing.dst`,
`obs.dat` = `BUS *` / `SYNC *` / `SHUNT *` / `BRANCH *` / `INJEC *`), the same case and the same
`cmd.txt` grammar `createCommandFile` produces (`dyn.dat`, `lf1solv.dat`, `solveroptions.dat`,
blank, blank, `nothing.dst`, `output.trj`, `obs.dat`, blank, blank, blank — pinned by
`stepss-ramses/docs/INPUT_FORMAT.md`'s own worked example). The toolchain was extracted for real
via `my.ramses.platform.Toolchain.extractAll()` (the same class `RamsesUI.initRamses()` uses),
not hand-copied:

```
$ java -cp <classes> ExtractTools <dir>
platform=LINUX_X86_64
dynsim=<dir>/dynsim exists=true
dyngraph=<dir>/dyngraph exists=true

$ cd <run>
$ <dir>/dynsim -t<run>/cmd.txt
...
**Parallel computing deactivated by user because $NB_THREADS=1 in settings**
...
Time steps                      50002
...
**Writing output observables, please wait**
**Simulation finished**
$ echo exit=$?
exit=0
```

`output.trj`: 35,203,970 bytes, SHA-256 `ee862e90a4daa3028882a01bc623861eef083eeae455d03cddf4563c48055a3e`
— **byte-identical** to the 5-bus trajectory independently produced and hashed in
`compile-acceptance-results.md`'s Step 2a, on the same case, on this same machine, in an earlier
task. Two independent runs of the pinned engine on the same inputs producing the same bytes is
strong evidence the run is a genuine, correct simulation and not an artefact of this task.

### 5. `dyngraph --list` on that trajectory, through the real `DyngraphRunner.list`

```
=== STEP A: dyngraph --list <real output.trj> via DyngraphRunner.list ===
exitCode=0
stdout.length=1312
stderr=
       5 buses
       0 shunts
       0 impedance loads
       6 branches
       1 synchronous machines
       4 observables in excitation controllers
       5 observables in torque controllers
       5 user-defined injectors
      17 observables in user-defined injectors
       0 links
       0 observables in links
       0 DCTLs
       0 observables in DCTLs

index.isEmpty()=false
  BUS instances: 5
  SHUNT instances: 0
  LOAD instances: 0
  BRANCH instances: 6
  SYNC instances: 1
  INJ instances: 5
  LINK instances: 0
  DCTL instances: 0
```

`ObservableIndex.parse` accepted the real DYNGRAPH-INDEX stream without error and produced the
category counts a human reading the raw `--list` output would expect.

### 6. Selected-row labels, computed from real parsed data — the desc_obs check (Step 1.3)

```
BUS instances (real names): '1' '2' '3' '4' '5'
=== Selected rows (desc_obs-format labels) ===
  bus 1: voltage magnitude (pu)
  bus 2: voltage magnitude (pu)
  sync mach G: rotor speed (pu)
```

These are real `Selection.label()` calls against real `TypeEntry`/`Instance` objects the real
parser produced from the real `--list` output — not hand-typed strings. They match the
brief's own worked example format (`bus <name>: voltage magnitude (pu)`,
`sync mach <name>: rotor speed (pu)`) exactly.

### 7. The injector-OBS-differs-by-model check (Step 2), from real data

```
=== INJ instances and their real OBS lists (model-dependent) ===
  Small_Motor OBS=[P, Qmot+comp, Qmot, omega, Tm]
  Large_Motor OBS=[P, Qmot+comp, Qmot, omega, Tm]
  Impedance_Load OBS=[P, Q, df, u]
  EQUIV1 OBS=[P, Q]
  VFAULT OBS=[Xfault]
=== SYNC machine's own EXC/TOR sub-lists (real) ===
  G EXC=[dvpss, deltaif, dvoel, vf] TOR=[z, PmHP, PmMP, PmLP, Pm]
```

`Small_Motor` and `Large_Motor` are both `INJEC INDMACH1` (an induction-motor model) in
`dyn.dat` and carry the identical five-entry `OBS` list; `Impedance_Load`
(`INJEC vfd_load`) and `EQUIV1` (`INJEC THEVEQ`) are different models and carry different,
shorter lists; `VFAULT` (RAMSES' own fault injector, not something `dyn.dat` names explicitly)
carries a third, one-entry list. This is real evidence, from a real network, that per-instance
`OBS` sub-lists genuinely differ by model rather than being pooled by category — the exact
property Task 2's fixture pins structurally and this run confirms on live data.

### 8. `ReplayFile` round trip — `sel.cmd` content (Step 1.7), and the `SOE`/injector grammar (Step 2)

Two selection sets were written and run, to exercise both the plain-keyword grammar and the
sub-observable grammar (`SOE` and injector `I`) for real:

```
=== sel.cmd (BM/BM/SS, no sub-observable) ===
<run>/output.trj
BM
1
BM
2
SS
G

S

=== sel-rich.cmd (SOE with sub, injector with sub) ===
<run>/output.trj
SOE
G
dvpss
I
Small_Motor
P

S
```

Both match the grammar exactly: keyword, name, sub-observable only where required (`SOE`, `I`),
blank line, `S`. Selected rows for the rich set:

```
sync mach G: excit control: dvpss
injector Small_Motor: P
```

confirming `SOE` renders as `excit control:` (not its TYPES label) exactly as the design
document specifies, on real data.

### 9. `DyngraphRunner.plot` — the actual `.cur`/`.plt` pair, via the real DYNGRAPH `-t` run

```
=== STEP B: dyngraph -c -t sel.cmd -o<base> via DyngraphRunner.plot ===
plot exitCode=0
...
bus 1                 : voltage magnitude (pu)
bus 2                 : voltage magnitude (pu)
sync mach G                   : rotor speed (pu)

Click on plot in Gnuplot

tempGnupOut.cur exists=true size=3350268
tempGnupOut.plt exists=true size=711
=== tempGnupOut.plt content ===
reset

set style data lines
set xtics
set border
set xlabel 't (s)'
set grid
set key opaque
plot \
'<run>/tempGnupOut.cur' using 1 :  2 title 'bus 1                 : voltage magnitude (pu)',\
'<run>/tempGnupOut.cur' using 1 :  3 title 'bus 2                 : voltage magnitude (pu)',\
'<run>/tempGnupOut.cur' using 1 :  4 title 'sync mach G                   : rotor speed (pu)'

plt references cur absolute path: true
tempGnupOut.cur first line:  0.0000000E+000  0.1020000E+001  0.1003771E+001  0.1000000E+001  ;
tempGnupOut.cur line count: 50005
```

The `.plt` references `tempGnupOut.cur` by its **absolute** path, at the fixed base
`<temp>/tempGnupOut` — exactly what `viewCurvesButtonActionPerformed` (opens
`tempGnupOut.plt` by name) and `saveCurrentCurveButtonActionPerformed` (string-replaces that
absolute `.cur` path) require. Curve titles carry DYNGRAPH's own internal blank padding, matching
the design document's note that the real titles are wider than the trimmed index strings.

The richer selection set (`SOE` + injector) round-tripped the same way: `plot exitCode=0`,
`tempGnupOut-rich.cur` 2,550,204 bytes, `tempGnupOut-rich.plt` 507 bytes, titles
`sync mach G: excit control: dvpss` and `injector Small_Motor: P`.

### 10. A headless proof that the `.plt` is not just present but *correct*

`viewCurvesButtonActionPerformed` calls `gnuplot -persist tempGnupOut.plt`; a real, on-screen
gnuplot window needs a human's eyes to judge (see the matrix). Short of that, the `.plt` was fed
to the real `gnuplot` binary on this machine in batch mode with an ASCII terminal, which needs no
display and cannot be faked into passing:

```
$ timeout 5 gnuplot -e "set terminal dumb; load 'tempGnupOut.plt'"
exit=0
(stderr empty)
   1.02 +------------------------------------------------------------------+
        |            +             +            +             +            |
        |           bus 1                 : voltage magnitude (pu) ******* |
        |           bus 2                 : voltage magnitude (pu) ####### |
        ...
```

Real gnuplot parsed the real `.plt`, plotted three real curves from `tempGnupOut.cur`, and
printed the exact titles the Selected list produced — with zero stderr. This is not a substitute
for a human watching the interactive window (fonts, window chrome, and the actual `-persist`
pop-up are unverified — see below), but it is real, mechanical proof that the file DYNGRAPH wrote
is syntactically valid and carries the right data, not merely that it exists.

A separate attempt to run the interactive `gnuplot -persist` command this environment's sandbox
could not complete meaningfully: the Qt/EGL backend failed on this headless-ish machine
(`dconf: Read-only file system`, `MESA: ZINK: failed to choose pdev`) and the process exited
immediately rather than staying up as a window. That failure is this sandboxed environment's
Qt/graphics stack, not the `.plt`/DYNGRAPH pipeline — the batch-mode `dumb`-terminal render above,
which needs no window system at all, is the real evidence for this row, and it passed cleanly.
This is recorded plainly rather than glossed over.

### 11. Failure path 1 (Step 3.1): the missing-trajectory case, and the fix that removed the trap

```
=== STEP C: failure path - missing trajectory ===
exitCode=1
stdout empty=true (len=0)
stderr=
File <run>/does-not-exist.trj does not exist. Exiting...

Matches RamsesUI's "too old" branch condition (exitCode!=0 && stdout empty): true
```

This is the real bundled DYNGRAPH's real behaviour on a missing trajectory: exit 1, empty
stdout, the complaint on stderr. Read purely from `openPickerFromListing`'s old detection —
`listing.exitCode != 0 && listing.stdout.trim().isEmpty()`, both true here — this shape was
byte-for-byte indistinguishable from a genuinely too-old DYNGRAPH binary
(`stepss-dyngraph/tools/smoke_gate.sh:144-151`), and **this real run used to hit the "The bundled
DYNGRAPH does not support `--list`" branch**, a false diagnosis. That ambiguity was filed as a
real bug from this acceptance run (Finding 2 of the whole-branch review) and has since been
fixed: `runDyngraphButtonActionPerformed` now checks `trajectory.exists()` **before** `--list` is
ever invoked, so this exact exit-1/empty-stdout/missing-file shape never reaches
`openPickerFromListing`'s ambiguous branch at all — it is caught upfront and reported as its own
clear case, "No trajectory was found to extract curves from: `<path>`. Run a simulation with
**Save output trajectories** enabled first.", with `--list` never launched. The genuine
too-old-binary case (trajectory present, dyngraph itself exits non-zero with empty stdout) is
unchanged and still reaches the original "does not support `--list`" branch, verified with a stub
binary reproducing that exact shape.

### 12. Failure path 2 (Step 3.2): a failing re-run after a successful one

```
=== a replay file naming a trajectory that no longer exists, run through -t ===
failing plot exitCode=2
failing plot stderr=
At line 25 of file src/get_observ_name.f90 (unit = 10, file = '<run>/does-not-exist.trj')
Fortran runtime error: End of file
Error termination. Backtrace:
...
Would trigger RamsesUI's failure dialog (exitCode != 0): true
tempGnupOut-fail.cur exists=false
tempGnupOut-fail.plt exists=false
```

A real failing `-t` run: non-zero exit, a real Fortran stderr trace, and — critically — **no**
`.cur`/`.plt` written at all. `startPlotRun` disables `viewCurvesButton` and
`saveCurrentCurveButton` **before** starting this run (confirmed by reading
`RamsesUI.java:3390-3392`, not re-verified live) and re-enables them only inside the success
branch of `onFinished`, which this real non-zero exit never reaches — so the buttons would stay
disabled, consistent with the brief's expectation. The dialog appearing and the buttons visibly
staying grey is still a human-eyes check (see the matrix).

### 13. The corrected filter expectation, checked against the real predicate and the real fixture

`ObservablePicker.rebuildNames` filters with
`filter.isEmpty() || instance.name.toLowerCase().contains(filter)`. That exact predicate,
applied to the real `PickerHarness.fixture()` data (which already contains `BUS1`, `BUS2`,
`" LEADBUS"`, `"END"` for this exact reason) with filter `"bus"`:

```
BUS instances in fixture: 4
  candidate: 'BUS1'
  candidate: 'BUS2'
  candidate: ' LEADBUS'
  candidate: 'END'
Filter 'bus' matches: [BUS1, BUS2,  LEADBUS]
Count: 3
```

Confirmed mechanically: filtering BUS by `bus` yields **three** rows (`BUS1`, `BUS2`,
` LEADBUS`), because the filter is case-insensitive `contains` and `" leadbus"` contains `"bus"`
(its last three characters); only `END` drops out. This is the corrected expectation the task
brief calls for, verified against the shipped predicate and the shipped fixture, not asserted on
faith.

---

## Per-check matrix

Every row below maps to a numbered step in the task brief. `PASS` is used only for something
directly run and observed in this session, per the task's honesty constraint; every dialog- or
hardware-dependent row is `PENDING` with the precondition it depends on (already verified above)
and the precise remaining action.

### Step 1 — Linux, the full happy path

| # | Check | Result |
|---|---|---|
| 1.1 | Load data + disturbance files, enable *Save output trajectories*, run to completion | **PENDING** — needs a human driving the real GUI's *Load Files*, the *Save output trajectories* checkbox and *Run Simulation*. The underlying simulation itself was independently proven for real (§4 above): the exact `cmd.txt` grammar `createCommandFile` emits, run through the exact bundled `dynsim`, on `stepss-5-bus-test-system`, produced a trajectory byte-identical (same SHA-256) to the one Task 7's compile-acceptance produced through the real GUI. Action: `java -jar dist/stepss.jar`, load `stepss-5-bus-test-system/dyn.dat` + `lf1solv.dat` + `solveroptions.dat` as System Data 1-3, `nothing.dst` as the disturbance, tick *Save output trajectories* with `obs.dat` as the observables file, press *Run Simulation*, wait for **Simulation finished**. |
| 1.2 | Press *Extract Curves*: wait cursor, then the picker dialog, **no terminal window** | **PENDING** — needs eyes on the wait cursor and the dialog opening. Mechanically de-risked (§3 above): `PlatformLauncher.runInTerminal`, the call that used to open a terminal here, has zero call sites left anywhere in `src/`, so the old terminal path cannot fire regardless of what appears on screen. Action: after 1.1, click *Extract Curves*; confirm the cursor changes briefly, the *Select Observables* dialog appears, and no terminal window opens anywhere. |
| 1.3 | Filter buses, add two bus voltage magnitudes + one machine's rotor speed; Selected rows read exactly like desc_obs | **PENDING** for the mouse-driven interaction itself, but the two things this step is actually checking are proven for real (§6, §13): the filter predicate's case-insensitive-`contains` behaviour, and that `Selection.label()` on real parsed data reads `bus <name>: voltage magnitude (pu)` / `sync mach <name>: rotor speed (pu)` exactly. Action: in the dialog, category BUS, filter for a bus name, pick a row, Observable = "voltage magnitude (pu)", Add, twice; category SYNC, pick the machine, Observable = "rotor speed (pu)", Add; confirm the Selected list reads as above. |
| 1.4 | Press *Plot*: no dialog on success; *Preview Curve* / *Save Current Curve* become enabled | **PENDING** for the on-screen button state, but the run itself is proven for real (§9): `DyngraphRunner.plot` on this exact selection set returned exit 0 with no error, which is what `startPlotRun`'s `onFinished` treats as silent success (confirmed by reading `RamsesUI.java`, not re-run live). Action: press *Plot*; confirm no dialog appears and both result buttons become clickable. |
| 1.5 | Press *Preview Curve*: a gnuplot window whose curve titles match the Selected rows | **PENDING** for the actual window appearing correctly on screen — this environment's Qt/EGL stack could not sustain an interactive gnuplot window under sandboxing (§10). What is proven for real: the `.plt` DYNGRAPH wrote is syntactically valid and, rendered headlessly with gnuplot's own `dumb` terminal, shows exactly the three expected curves under exactly the expected (Fortran-padded) titles. Action: press *Preview Curve*; confirm a gnuplot window opens showing three curves titled `bus 1: voltage magnitude (pu)`, `bus 2: voltage magnitude (pu)`, `sync mach G: rotor speed (pu)` (with extra internal spacing from Fortran's fixed-width fields). |
| 1.6 | Press *Save Current Curve* and save; `.plt`/`.cur` land and `.plt` references the saved `.cur` name | **PENDING** — needs the real `JFileChooser`. The precondition is proven for real (§9): a genuine `tempGnupOut.cur`/`.plt` pair exists at the fixed absolute base `saveCurrentCurveButtonActionPerformed` expects, and the `.plt` references the `.cur` by that same absolute path (confirmed by string search on the real file content). Action: press *Save Current Curve*, choose a name, save; confirm `<name>.plt` and `<name>.cur` exist and `<name>.plt` mentions `<name>.cur`, not the temp path. |
| 1.7 | `sel.cmd` left in the temp directory, content matches the picks | **PASS** — directly observed. §8 above: a real `sel.cmd` was written by the real `ReplayFile.write` for the exact selection set in 1.3's equivalent (two `BM` picks + one `SS` pick), left on disk, and its content matches the grammar exactly: keyword/name groups, blank line, `S`. A second run additionally exercised the sub-observable grammar (`SOE`/`I`) for real. |

### Step 2 — Linux, sub-lists and greying on a real network

| Check | Result |
|---|---|
| Pick `SOE` → the machine's own exciter observables appear | **PENDING** for the dropdown appearing correctly on screen, but the data it would show is real (§9): machine `G`'s real `EXC` sub-list is `[dvpss, deltaif, dvoel, vf]`, and a real `SOE`/`dvpss` selection round-tripped through `ReplayFile` and DYNGRAPH's `-t` run to a correct `.cur`/`.plt` pair titled `sync mach G: excit control: dvpss`. |
| Pick an injector → its own `OBS` names appear and differ between injectors where models differ | **PENDING** for the dropdown rendering on screen. The data it would show is directly observed, not inferred (§7): on the real 5-bus network, `Small_Motor`/`Large_Motor` (same `INDMACH1` model) carry the identical `OBS` list `[P, Qmot+comp, Qmot, omega, Tm]`, while `Impedance_Load` (`vfd_load`), `EQUIV1` (`THEVEQ`) and `VFAULT` each carry a different, shorter list — the "differs by model, not pooled by category" property this step exists to catch, proven on live data, not just the harness fixture. Action: category INJECTOR, select each instance in turn, confirm the Observable dropdown's contents change accordingly. |
| A machine with no torque controller has `SOT` greyed | **PENDING, and currently unreachable with existing fixtures.** Every `.dat` file under `stepss-test-systems` was scanned (`grep`-driven, all 29 `dyn*.dat` files) for a `SYNC_MACH` block missing an `EXC` or `TOR` line: none exists. The underlying logic is pinned structurally by `PickerHarness`'s fixture (`GEN2` with `EXC 0`, non-empty `TOR`) and passes there (58/58), but no real network in this repository currently exercises it end to end. Action for whoever runs this: copy `stepss-5-bus-test-system/dyn.dat`, delete the `TOR THERMAL_GENERIC1 ...` line from machine `G`'s block, rerun the simulation on the copy, extract curves, select category SYNC, machine `G`, and confirm the Observable dropdown's `SOT` entry is greyed and cannot be chosen. |

### Step 3 — Linux, failure paths

| # | Check | Result |
|---|---|---|
| 3.1 | Delete the trajectory, press *Extract Curves*: error dialog reporting the missing trajectory by name, headlined "No trajectory was found to extract curves from"; `dyngraph --list` is never launched; picker never opens; result buttons unchanged | **PENDING** for the dialog actually appearing on screen with that exact text, and the picker not opening — needs a human to delete `<temp>/output.trj` (or load then delete a saved `.trj`), press *Extract Curves*, and read the dialog. The mechanism it depends on is directly observed, not inferred (§11): `runDyngraphButtonActionPerformed` now checks `trajectory.exists()` before invoking `--list` at all, so a missing trajectory is caught upfront and reported as its own case — verified with a canary `dyngraph` stand-in that records whether it was ever launched (it was not) and a logic mirror of the guard clause producing exactly that message text. The previously documented trap — a missing trajectory and a too-old binary both exiting non-zero with empty stdout, indistinguishable from `openPickerFromListing` alone — no longer applies to this case: it is now resolved before `openPickerFromListing` is reached. The genuine too-old-binary case (trajectory present) still reaches the original "does not support `--list`" dialog, verified with a stub binary reproducing that exact shape. |
| 3.2 | After a successful extraction, force a failing re-run: failure dialog, both result buttons **stay disabled** | **PENDING** for the buttons visibly staying grey on screen through the failure dialog — needs a human to extract once successfully, delete the trajectory, extract again, and watch *Preview Curve*/*Save Current Curve* stay disabled. The mechanism it depends on is directly observed, not inferred (§12): a real failing `-t` run (trajectory removed after the replay file was written) exits 2 with a real Fortran stderr trace and writes **no** `.cur`/`.plt` at all. `startPlotRun` disables both result buttons *before* the run starts and only re-enables them from the success branch of `onFinished`, which this failing run never reaches (confirmed by reading `RamsesUI.java:3390-3413`, not re-run live). |
| 3.3 | Cancel the picker: nothing changes, no files written | **PENDING** — this is pure Swing dialog behaviour (`cancelButton`'s listener disposes with `result` left `null`; `openPickerFromListing` returns without calling `startPlotRun`) confirmed only by reading `ObservablePicker.java` and `RamsesUI.java`, not exercised live. Action: open the picker, make no selection (or make one and don't press Plot), press *Cancel*; confirm no `sel.cmd`/`tempGnupOut.*` files appear or change and no buttons change state. |

### Step 4 — Windows and macOS

No Windows x86_64 or macOS arm64 hardware exists in this environment. Every row of Steps 1-3,
repeated on those platforms, is **PENDING**, naming the hardware — nothing was inferred from the
Linux result, consistent with `compile-acceptance-results.md` and `platform-matrix-results.md`.
Windows additionally needs: confirmation that **no console window flashes** during either
`dyngraph` invocation, and that a network stored under a path containing spaces still extracts
correctly (the `addArgument(value, false)` quoting case in `DyngraphRunner.list`/`plot` and the
`-t`/`-o` arguments) — neither is reachable on Linux even in principle, since the quoting bug
this guards against is Windows-`CommandLine`-specific behaviour.

| Platform | All of Steps 1-3 | Windows-only: no console flash | Windows-only: space-containing path extracts |
|---|---|---|---|
| Windows x86_64 | PENDING — requires Windows x86_64 hardware | PENDING — requires Windows x86_64 hardware | PENDING — requires Windows x86_64 hardware |
| macOS arm64 | PENDING — requires Apple Silicon hardware | N/A | N/A |

---

## The one gap no gate covers (stated verbatim from the spec, per the brief)

> The UI omits `-eps`, and DYNGRAPH's smoke gate always passes it
> (`stepss-dyngraph/tools/smoke_gate.sh:90`) because without it `caption.f90` emits
> `set terminal windows` on Windows and an empty terminal line elsewhere, which would make the
> `.plt` golden platform-specific. So the `.plt` this UI produces is precisely the branch no
> golden pins.
>
> — `docs/superpowers/specs/2026-08-09-observable-picker-java-ui-design.md`, "What no automated
> gate covers"

This run's own `.plt` (§9, §10 above) is exactly that no-`-eps` branch — unchanged from today,
not a regression this task introduced, and confirmed here to be well-formed and correctly
titled by direct headless rendering rather than left as a bare assertion. The follow-up (a
no-`-eps` gate case in `stepss-dyngraph`, diffing with the terminal line filtered) belongs to
that repository, per the design document, and is out of scope here.

---

## Findings

**1 — By design, not a defect: this task cannot turn most of Steps 1-4 into `PASS`.** The
picker dialog is Swing, and this session has no display a human is watching and no Windows/macOS
hardware. Every row that is fundamentally "does this look/behave right on screen" is `PENDING`
regardless of how much of its *data* was mechanically proven correct — which, per the sections
above, is nearly all of it. The gap that remains is specifically the human-observable half: wait
cursor, dialog layout, dropdown/greying rendering, the gnuplot window's actual appearance, the
`JFileChooser` flow, and all three platforms' visual/console behaviour.

**2 — The `SOT`-greyed case (Step 2) is not just unobserved, it is currently unreachable.** No
`.dat` file anywhere under `stepss-test-systems` defines a `SYNC_MACH` without both an `EXC` and
a `TOR` block. `PickerHarness`'s fixture pins the parsing/model side of this rule (`GEN2`,
`EXC 0`), but nobody can exercise the dialog's greying on a *real* network without first hand-
editing one, as the matrix above spells out. Not a defect in this feature — a gap in the
available fixtures for exercising it visually.

**3 — Environment note, not a product defect: interactive gnuplot could not be sustained under
this session's sandbox.** `gnuplot -persist` (the exact command `viewCurvesButtonActionPerformed`
runs) failed inside its own Qt/EGL backend (`dconf: Read-only file system`,
`MESA: ZINK: failed to choose pdev`) and exited immediately rather than staying open as a window.
This is this constrained environment's graphics stack, unrelated to DYNGRAPH or the picker code;
the batch-mode `dumb`-terminal render used instead needs no window system and is not affected by
it, and is the real evidence recorded above for `.plt` correctness.

## Verdict

`PICKER_ACCEPTANCE: pass-with-gaps`

**What passed cleanly and is independently reproducible on Linux, all of it directly observed in
this session, none of it inferred:**

- The build (`ant clean jar`, all twelve payloads SHA-256-verified, dyngraph 1.2.0 confirmed by
  the binary's own build-info string) and the pre-existing harness (`tools/picker-harness.sh`,
  58/58).
- A real trajectory, from a real test system, through the real bundled `dynsim`, byte-identical
  to an independently produced baseline from an earlier task.
- The real bundled `dyngraph --list` on that trajectory, through the real `DyngraphRunner.list`,
  parsed by the real `ObservableIndex.parse` with zero errors.
- Real `Selection.label()` output on that parsed data matching the desc_obs format exactly,
  including the `SOE`/`excit control:` rendering.
- The real per-instance `OBS`-differs-by-model property, on live data (two same-model injectors
  sharing a list, three different-model injectors each carrying a different one).
- A real `sel.cmd`/`sel-rich.cmd` round trip through `ReplayFile`, covering both the plain and
  the sub-observable (`SOE`, injector) grammar, and a real `-t` plot run through
  `DyngraphRunner.plot` producing a correct `.cur`/`.plt` pair at the fixed absolute base
  `viewCurvesButton`/`saveCurrentCurveButton` require — confirmed by headless gnuplot rendering,
  not merely file existence.
- Both failure paths' underlying mechanism: a missing trajectory is now caught before `--list` is
  ever invoked and reported as its own clear case (§11), the genuine too-old-binary case still
  reaches the original "does not support `--list`" dialog unchanged, and a failing re-run
  genuinely exits non-zero and writes no partial output.
- The corrected filter expectation, checked against the shipped predicate and the shipped
  fixture: `bus` matches `BUS1`, `BUS2`, ` LEADBUS`, not `END`.
- Mechanical proof (zero call sites) that the old terminal-opening code path cannot fire.

**What remains outstanding, and who must do it:**

1. **Every dialog-visual and mouse-driven row in Steps 1-3 on Linux** (the table above marks
   each precisely, with what's already de-risked and what specifically remains) — needs a human
   at this screen with `java -jar dist/stepss.jar` and the `stepss-5-bus-test-system` case (or
   any case with machines and injectors).
2. **The `SOT`-greyed check** — needs both a human and a hand-edited fixture, since no shipped
   test system has a machine without a torque controller; exact steps given above.
3. **The full checklist on Windows x86_64 and macOS arm64**, plus Windows' console-flash and
   space-in-path checks — no such hardware exists in this environment.
4. **The no-`-eps` `.plt` gap** — pre-existing, not introduced here, and assigned to
   `stepss-dyngraph` by the design document; this task's own `.plt` output was confirmed
   well-formed within that known limitation.

The project owner — or whoever has access to Windows/macOS hardware and can sit at a Linux
screen — should complete items 1-3 before treating the dialog itself as proven; item 4 needs no
action from this repository.
