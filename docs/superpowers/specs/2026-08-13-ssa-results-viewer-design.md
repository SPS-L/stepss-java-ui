# Small-signal results viewer for `stepss-java-ui`

Read the three results files the engine writes, show them in a window with a
sortable modes table, participation factors, an s-plane and a mode-shape dial,
and export the plots as editable SVG. Fix the three defects that make the
existing SSA buttons look broken.

Status: approved 2026-08-13, and implemented with three changes made after
approval. They are recorded here rather than edited into the body, so this
stays a record of what was approved:

- **An analysis time is exposed.** The `EIG` record fires at a time the user
  sets, defaulting to the 0.001 s this document assumed throughout. A later
  value runs the simulation to that point with no events and linearises there.
- **The Jacobian is a by-product of the analysis, not a separate action.**
  "Extract Jacobian matrix" and `dampJac.dst` are deleted, contradicting the
  statement below that `dampJac.dst` stays. The generated disturbance now
  carries a `JAC` record at the same instant as the `EIG` record, and a
  *Save dynamic Jacobian* button, enabled only by a successful analysis, saves
  what that run dumped. This guarantees the saved matrix is the one behind the
  eigenvalues displayed, which a separate run could not promise.
- **The two style tables were consolidated** into one `PlotStyle`, because two
  hand-maintained tables cannot deliver the no-drift guarantee the sink
  abstraction exists for.

## Why

The Analysis tab has two SSA buttons and no viewer. `ssa_modes.dat` appears
exactly twice in the whole repository, at `RamsesUI.java:3117` and `:3128`:
once to test that the file exists, once to name it in a copy loop. **Nothing in
`stepss-java-ui` ever reads a single number back.** The buttons are a file
exporter, silent on success, and the only dialog anywhere in the path is the
failure warning.

The value of the analysis is all in what the python-ui notebook does *after*
the engine writes the files. `examples/eigenanalysis/kundur_small_signal.ipynb`
steps 3 to 8 read the modes, filter the electromechanical band, pull out the
inter-area mode, list participation factors, draw the mode shape on a polar
dial and draw the s-plane. This document brings that to the Java side.

Three defects compound the emptiness, and all three are fixed here:

1. **A successful run can vanish.** `ssaDirectory` empty means no copy-out, and
   `myTempDir` falls back to the extracted tool directory (`RamsesUI.java:5454`).
   A fully correct run is then indistinguishable from a no-op: the results land
   in `/tmp/stepssTools*/` and are never mentioned.
2. **The SSSA button is gated behind a dependency that does not exist.**
   `ssaButton1.setEnabled(false)` at `:1895`, re-enabled only at `:3167` after
   the Jacobian dump. But `EIG` assembles and reduces its own Jacobian in
   memory: `ssa.f90` opens files only with `status='replace'` and never reads
   one. The four `jac_*.dat` files are a separate deliverable, and forcing the
   Jacobian dump first is a fiction.
3. **Two different controls carry the identical label.** `loadSSADir` on the
   Analysis tab (`:1906`) and `selWorkDirButton` in the Tools menu (`:2172`)
   are both "Select Working Directory" and do different things: the first sets
   a copy-out target, the second re-inits the toolchain and changes where the
   engine runs. `loadSSADir`'s tooltip says a third thing, "Select directory to
   extract Jacobian matrix", on a control governing both buttons.

## Scope

In scope, this repository only:

- Parsing the three results files.
- A results window: modes table, participation, mode shape, s-plane.
- SVG export of both plots.
- The three defects above.
- A `basename` field, which works against the pinned engine today.
- `real_limit` and `pf_threshold` fields, shipped **disabled** behind an
  engine-version guard.

Out of scope, each needing its own spec in its own repository:

| Work | Repository | Note |
|---|---|---|
| `EIG` record accepting optional `real_limit` / `pf_threshold` | `stepss-ramses` | What un-greys the two fields here |
| Bundling a RAMSES >= 3.72 library | `stepss-python-ui` | Its `libs/lin/ramses.so` is still v3.60 and contains no SSA code at all |
| `sim.runSSA()` wrapper | `stepss-python-ui` | `run_ssa` is already declared in the bundled `ramses.h` |

## What the engine gives us

Written by `ssa.f90`, one set per run, named from the `EIG` basename:

| File | Format string | Columns |
|---|---|---|
| `<base>_modes.dat` | `(i8,1x,4(en24.15,1x),i2,1x,i2)` | index, re, im, zeta, freq_hz, dom, smp |
| `<base>_pf.dat` | `(i8,1x,i8,1x,en24.15,1x,a8,1x,a20,1x,a20)` | mode, state, pf, family, device, variable |
| `<base>_ms.dat` | `(i8,1x,i8,1x,2(en24.15,1x),a20)` | mode, state, magnitude, angle_deg, device |

`_modes.dat` carries three comment lines. The second is machine-readable and
records the parameters the run used:

```
# nstates 70 nalg 96 time 0.000E+00 real_limit -1.000E+00 pf_threshold 50.00E-03 gap_tol 1.000E-06
```

Two filters are already applied on the engine side and must be reflected, not
re-derived:

- `_pf.dat` and `_ms.dat` contain rows **only for modes with `dom(i) == 1`**,
  the ones that passed `real_limit`. A mode present in `_modes.dat` can be
  absent from both others.
- Participation rows below `pf_threshold` are simply not written. Absence means
  "below threshold", never "zero".

## Approach

```
  Perform small signal stability analysis
        |
        ├─ write <basename>.dst   ("0.000 EIG '<basename>'")
        ├─ run the engine, wait
        ├─ exit 78 or no _modes.dat  ->  existing refusal dialog, stop
        |
        └─ SsaResults.load(dir, basename)
                 |
                 └─ new SsaResultsWindow          non-modal, one per run
                          |
      +-------------------+-------------------+
      |         |         |                   |
   modes     partic.   mode shape          s-plane
   table     table       dial              scatter
      |                    |                  |
      +-- selection -------+------------------+
                                              |
                                    Save plot... -> SVG
```

## Components

A new `my.ramses.ssa` package. None of it is touched by the GUI builder, so
`RamsesUI.form` gains only what section "Changes to `RamsesUI`" lists.

| Class | Role |
|---|---|
| `Mode` | One row of `_modes.dat`: index, re, im, zeta, freqHz, dominant, simple |
| `SsaModes` | Header metadata plus the `Mode` list |
| `Participation` | One row of `_pf.dat` |
| `ModeShapeEntry` | One row of `_ms.dat` |
| `SsaResults` | The three parsed files plus their source directory and basename |
| `SsaResultsWindow` | Non-modal `JFrame`, several may be open at once |
| `SplanePanel` | s-plane, painted through `PlotSink` |
| `ModeShapePanel` | Polar dial, painted through `PlotSink` |
| `PlotSink` | Drawing primitives, so screen and SVG share one code path |
| `SwingSink` | `PlotSink` over `Graphics2D` |
| `SvgSink` | `PlotSink` emitting SVG text |
| `SsaHarness` | Headless `main()` checks, per the `PickerHarness` idiom |

### Parsing is by column offset, never by whitespace

The `a20` and `a8` fields are written as stored, so a **leading** blank is part
of the name and only trailing blanks are padding. `PickerHarness` already
documents this exact trap for the dyngraph index, with a fixture bus named
`" LEADBUS"` and an empty `LOAD` name, noting that such a blank "is invisible
in a text file, silently stripped by many editors, and exposed to CRLF
normalisation".

So: slice each line at the fixed offsets implied by the format string, then
strip **trailing** blanks only. Never `String.split("\\s+")`, which would merge
a device named `"AREA 1 G1"` into the wrong column and silently drop a leading
blank.

Numeric fields are Fortran `EN` format, always carrying an explicit `E`
exponent at these widths, so `Double.parseDouble` after trimming is correct. A
line that fails to parse is reported with its line number and the file is
rejected, rather than yielding a half-populated table.

Lines beginning `#` are comments. The `nstates ... gap_tol` line is parsed by
key, not by position, so a future engine adding a field does not break it; an
unrecognised or absent key leaves that metadata field empty rather than
failing the load.

### The window

```
+-------------------------------------------------------------------+
| .../kundur-ssa   basename ssa   70 states, 96 alg   t = 0.000      |
| real_limit -1.0   pf_threshold 0.05   gap_tol 1e-6                 |
+---------------------------------+---------------------------------+
|  [x] electromechanical only     |  s-plane         [Save plot...]  |
|   #   f [Hz]    zeta    simple  |                                  |
|  35  0.6237  +0.1087      yes <-|   selected pole ringed           |
|  27  1.2424  +0.2880      yes   |                                  |
|  41  0.6246  -0.0233      yes   |   unstable poles crimson x       |
|  52  1.8800  +0.4010       NO   |                                  |
+---------------------------------+---------------------------------+
|  Participation - mode 35        |  Mode shape - 35 [Save plot...]  |
|   G3 delta   0.955              |                                  |
|   G1 omega   0.853              |   arrows on a polar dial         |
+---------------------------------+---------------------------------+
```

The header strip names the source directory, the basename and the run's own
parameters, so the numbers on screen carry the thresholds that produced them.

The modes table sorts on any column via `TableRowSorter`. The
"electromechanical only" checkbox reproduces the notebook's `electromechanical()`:
`0.1 < f < 2.5` Hz and `Im > 0`, which also collapses each conjugate pair to
one row. It drives the s-plane at the same time.

Negative `zeta` renders red, because that is the result the analysis exists to
find.

Two honesty rules, both taken from `examples/kundur-ssa/README.md`:

- **A mode flagged `smp = 0` is degenerate.** In a degenerate eigenspace the
  individual eigenvectors are not unique, so its participation factors and mode
  shape are basis-dependent: real numbers that would come out differently on
  another machine. Both detail panels refuse to show numbers for such a mode
  and state that reason. This is a refusal, not a warning label next to
  otherwise-normal output.
- **A mode absent from `_pf.dat` was filtered by `real_limit`.** The panels say
  so explicitly instead of rendering empty.

### Plots

Painted with `Graphics2D`. No charting library is available (`lib/` holds only
commons-exec and commons-io) and none is added. Deliberately not gnuplot: the
app treats it as optional and already warns when it is missing, and a viewer
that silently loses half its content on a machine without gnuplot repeats the
defect this document exists to fix.

**s-plane**, following notebook cell 20: crimson stability boundary at
`Re = 0`; dashed grey constant-damping rays at `zeta` = 0.05 and 0.10, drawn
from the origin along `Re = -zeta*r`, `Im = r*sqrt(1 - zeta^2)`; hollow circles
for each mode in the current filter, labelled with frequency; unstable modes
overplotted as a crimson cross with a legend entry; grid; axes `Re(lambda)
[1/s]` and `Im(lambda) [rad/s]`. The notebook's window, `-3.0` to `0.5` and `0`
to `9`, is the *minimum* extent, expanded to fit when the data needs more, so
this works on systems other than Kundur.

Interactive in ways the notebook cannot be: clicking a pole selects that mode
in the table, selecting a table row rings its pole, and hovering shows index,
frequency, `zeta` and `lambda`.

**Mode shape**, following notebook cell 17: arrows from the origin to
(angle, magnitude), device label at 1.12 times magnitude, `rmax` 1.3,
magnitude rings at 0.5 and 1.0. Follows the table selection. The degenerate
refusal applies here too.

### SVG export

Both plots draw against `PlotSink` rather than against `Graphics2D` directly,
so the exported file is produced by the same code that painted the screen and
cannot drift from it. `PlotSink` needs only: line, dashed line, polyline,
circle (stroked or filled), cross, arrow, and anchored text.

The SVG is written to be edited afterwards, which is the point of choosing it:

- Every label is a real `<text>` element, never outlined paths.
- `font-family="sans-serif"`, nothing embedded.
- Semantic groups: `<g id="axes">`, `<g id="damping-rays">`, `<g id="poles">`,
  `<g id="unstable">`, `<g id="labels">`.
- A `<style>` block of named classes, so changing one hex value restyles every
  pole at once.

The file is serialised as plain text, so no raster encoder is involved and
there is no PNG path to keep in step. A `Save plot...` button sits on each
plot, mirroring the existing *Save Extracted Curve* affordance.

## Changes to `RamsesUI`

`RamsesUI.form` and the generated `initComponents()` block change together, in
NetBeans, never by hand-editing one of them:

| Change | Where |
|---|---|
| `View results...` button added to the SSA group | `jPanel8` |
| `basename` label and field | `jPanel8` |
| `Real part limit` and `PF threshold` labels and fields, disabled | `jPanel8` |
| `loadSSADir` relabelled "Select results directory", tooltip corrected | `:1906` |
| `ssaButton1.setEnabled(false)` deleted | `:1895` |

Hand-written changes, outside the generated block:

- `ssaButton1ActionPerformed` writes `<basename>.dst` dynamically instead of
  copying the static `ssaEig.dst` resource, then on success loads the results
  and opens an `SsaResultsWindow`. The existing refusal dialog is unchanged and
  still fires first.
- `ssaButton1.setEnabled(true)` deleted from `ssaButtonActionPerformed`
  (`:3167`).
- When `ssaDirectory` is empty the results are loaded from `myTempDir` and the
  window header names that path, so the files can no longer disappear quietly.
- `View results...` opens a `JFileChooser` on a directory and loads the results
  it finds. A basename is any `X` for which `X_modes.dat` exists. Exactly one
  loads directly; several prompt for which; none reports that the directory
  holds no results, naming the pattern it looked for. This is what reaches runs
  from a previous session and runs made from a terminal with
  `dynsim -t cmd.txt`, including the no-PSS variant in `examples/kundur-ssa/`.

The `ssaEig.dst` resource is deleted, along with its three references at
`RamsesUI.java:3100`, `:3101` and `:3108`, since the `.dst` is now generated.
Nothing else in `src/`, `build.xml` or `nbproject/` names it. `dampJac.dst`
stays as it is.

## Parameter fields

`basename` is enabled immediately. It is the `EIG` argument, so it works
against the pinned 3.72 engine as soon as the `.dst` is written dynamically.
It also lets several named result sets share one directory, which removes the
"run each variant in its own directory" constraint stated in
`examples/kundur-ssa/README.md`.

`real_limit` and `pf_threshold` are laid out now and shipped disabled, with a
tooltip naming the engine version they need. `disturb.f90:361-362` hardcodes
both for any `.dst`-triggered `EIG`, and java-ui drives `dynsim` as a
subprocess so it cannot reach `run_ssa`, which is the only entry point that
accepts them today.

The guard reads the version from the **engine banner**, not from
`versions.properties`, because `adoptedSimulator()` means the running engine
may be a custom build from the Codegen tab rather than the pinned payload.

This ordering matters: `read(desc_dist(i),*,err=1,end=11)disttype,jacfile` is a
list-directed read of two items, so an engine without the feature **silently
ignores** extra fields rather than erroring. Enabling the fields against an old
engine would hand back default-threshold results while the UI claimed
otherwise. The guard is what prevents that, and it is why the fields ship
disabled rather than hopeful.

## Testing

The repository has no unit-test framework and, per `PickerHarness`, is not
gaining one. `SsaHarness.main()` carries fixtures as string literals and is run
by `tools/ssa-harness.sh`, matching `tools/picker-harness.sh` including its
`build/classes` precondition and its exclusion of `dist/lib` from the
classpath.

Fixtures are string literals rather than data files for the reason
`PickerHarness` gives: the traps are invisible in a text file.

Deliberate traps:

| Trap | What it catches |
|---|---|
| Device name with an embedded blank | Whitespace splitting |
| Device name with a leading blank | Over-eager trimming |
| Mode with `smp = 0` | Degenerate refusal in both detail panels |
| Mode in `_modes.dat` but absent from `_pf.dat` | "Filtered by real_limit" path, not an empty panel |
| Mode at the origin | `zeta` reported as 0, per `ssa.f90:1369-1373`, not NaN |
| CRLF line endings | Offset arithmetic and trailing-blank stripping |
| Conjugate pair | The electromechanical filter keeping exactly one of the two |

Plot geometry is checked by driving `SvgSink` and asserting on the emitted
element set, which is why the sink abstraction earns its place: it makes the
drawing code testable without a display.

The Kundur case in `examples/kundur-ssa/` is the manual acceptance path. Its
README already records the expected answer, inter-area 0.6237 Hz at
`zeta` +0.1087 with the stabilisers and 0.6246 Hz at -0.0233 without, so the
window can be checked against a published number rather than against itself.

## Follow-up for other documents

`examples/kundur-ssa/README.md` currently warns that `versions.properties`
pins `ramses.version=3.60` and that the bundled engine "accepts the disturbance
and writes no results files". The pin is 3.72 and the example works. That
paragraph is stale and is corrected as part of this work.
