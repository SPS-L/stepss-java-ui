# Removing the gnuplot dependency

**Date:** 2026-08-19
**Status:** design agreed, not yet planned or implemented
**Scope:** stepss-ramses, stepss-dyngraph, stepss-java-ui, stepss-python-ui, stepss-docs, stepss-userguide, stepss-apt

## Summary

STEPSS stops requiring, bundling, probing, launching or reading gnuplot. RAMSES
and DYNGRAPH keep writing their `.cur` data files and `.plt` gnuplot scripts, so
a user who has gnuplot can still open one, but nothing in STEPSS depends on that.
The Java interface draws both the post-simulation curves and the live run-time
observables natively; the Python interface draws with matplotlib, which it already
depends on; the command line plots nothing.

## The invariant

> **STEPSS writes gnuplot files. STEPSS never reads one and never runs one.**

Files out, never in. This single rule settles a large class of downstream
questions and is the reason no component parses a `.plt`.

It is not a stylistic preference. The `.plt` RAMSES generates is a gnuplot
*program*, not a data format:

- It carries multiplot state, a colour palette, and per-panel parametric column
  remapping (`stepss-ramses/src/io/gnuplot.f90`, the three `plot ... using`
  forms).
- It embeds up to 256 characters per observable of **arbitrary user-authored
  gnuplot**, split on `/` and written verbatim (`gnup_cmds`, filled at
  `stepss-ramses/src/core/ramses.f90:804-809`). The Java tooltip at
  `StepssUI.java:1788` actively instructs users to use this.
- Six of its `set title` lines are emitted with **unterminated single quotes**.
  Real gnuplot accepts them silently. Any reader stricter than gnuplot would
  reject scripts STEPSS has shipped for years.

A "native `.plt` reader" therefore ends up either reimplementing gnuplot's
command language including its parser quirks, or silently ignoring lines it does
not understand, at which point it was never really reading the file.

The information a renderer needs is not lost by refusing to parse: it is
**upstream**. The caller chose the observables, typed the pass-through text, and
knows the equipment names. The one exception is the column layout, which is why
the `.cur` gains a header (below).

## Motivation

**Layering.** A numerical kernel should not `popen` a GUI program.
`stepss-python-ui` loads RAMSES in-process through `ctypes.CDLL`
(`src/stepss/simulator.py:87-90`) and has no subprocess mode, so today
`popen("gnuplot -persist","w")` forks out of a threaded OpenMP shared library
inside the user's Python interpreter, and for notebook users inside a Jupyter
kernel.

**Distribution.** `stepss-java-ui/src/my/stepss/gpwin.zip` is 9,025,791 bytes
committed to git and merged into a single fat jar built once for every platform,
so Linux and macOS users carry a Windows payload they never execute. On those
platforms the dependency is external instead (`gnuplot-x11 | gnuplot-qt` in the
`.deb`, `brew install gnuplot`), with a documented trap that the plain `gnuplot`
package can resolve to `gnuplot-nox`, which installs cleanly and draws nothing.

That payload is **gnuplot 4.6 built in March 2012**, with DLLs dated 2011, and
it no longer ships a working entry point for the way RAMSES drives it: gnuplot
5.0 dropped `pgnuplot.exe` from its binary distribution, which is exactly the
file `Toolchain` names.

**Correctness.** Three live defects disappear by deletion rather than by debugging:

1. **Phase-plane panels get a time x-axis.** `gnuplot.f90:79` writes
   `trim(string)` outside the `if` that guards it, so an `o-d` or `P-d` panel
   re-emits the previous panel's `set xrange [0 : t_stop]`, or a blank line when
   it is panel 1. A delta axis spanning 0 to 2π forced onto 0 to t_stop draws as
   a smear at the origin.
2. **`SOL` writes `i8` with no field separator** (`simul_decomp.f90:2283`) where
   every neighbouring field uses `1x`. `es13.6` of a negative number is exactly
   13 characters with no leading blank, so an 8-digit `counter_sol` followed by a
   negative column emits two numbers with nothing between them.
3. **Windows run-time plotting has never worked** (SPS-L/stepss-java-ui#19).

**Refreshing the bundle was evaluated and rejected**, which is part of the case
for removing the dependency rather than maintaining it. Upgrading the payload to
the current upstream Windows build (gnuplot 6.0.5, win64-mingw) was implemented
and then discarded on 2026-08-19. Its `gnuplot.exe` load-time links Qt6,
wxWidgets and `libgd`, and `libgd` drags in the whole AV1 and HEIF codec chain,
so the PE import closure is irreducible at roughly 155 MB across 75 DLLs, of
which ICU data is 33 MB, libx265 21 MB and libaom 9.4 MB. None of it is used by
STEPSS. Trimmed to that closure the payload measured 58,741,836 bytes, taking the
jar from 39 MB to 89 MB, and it would have introduced GPL-2+ (x264, x265) and
LGPL-3 (Qt6) libraries into the Windows installers. Removing the dependency
avoids all of that instead of paying it every release.

## Non-goals

- **Removing the `.plt` and `.cur` files.** Both keep being written. DYNGRAPH's
  `-eps` keeps working: it does not run gnuplot, it only writes
  `set terminal postscript eps` and `set output` into the `.plt`. The committed
  EPS figures in `stepss-test-systems` were produced by running gnuplot over such
  a script, and that workflow is preserved for whoever wants it.
- **Reimplementing gnuplot.** The pass-through feature is withdrawn, not ported.
- **Building a charting library.** See the interaction scope below.
- **Log axes, annotations, twin y axes, curve arithmetic, printing.** Recorded as
  decisions, not omissions. Nothing in the current output generates a log axis;
  the only way to obtain one today is the pass-through being withdrawn, so it is
  the most likely first request back.

## Decisions taken

| Question | Decision |
|---|---|
| Does gnuplot remain supported? | Removed altogether from STEPSS. Files still written for the 1% who have their own gnuplot. |
| How does a renderer learn the column layout? | A `#`-prefixed header in the `.cur`. No `.plt` parsing anywhere. |
| Live plot layout | Stacked, one panel per observable, sharing the time axis. No layout choice offered. |
| Post-analysis layout | One panel, one y axis, all curves of an extraction overlaid with a legend. No layout choice offered. |
| Repeated extractions | One window per extraction, cascaded. Never replaces an existing window. |
| Repeated runs | A new run opens a fresh live window; the previous one freezes into a static chart. |
| Java charting library | None. Hand-rolled Java2D on the existing `PlotSink` abstraction. |
| Python live plotting | Poll the in-process engine (`contSim`, `getObs`), do not tail a file. |
| Transition shape | Additive engine release first, deletion last. See the release sequence. |

### Why no charting library

`JFreeChart` is the only option that would save real work, since
`CombinedDomainXYPlot` does stacked panels sharing a domain axis natively and
`LogarithmicAxis` exists. It costs the project's first LGPL jar merged into a
deliberately single fat jar (`build.xml`, the `-post-jar` merge), which sits
awkwardly with LGPL relinkability and adds the first non-Apache stanza to
`packaging/linux/copyright`. `XChart` saves less than it appears: it has no
stacked shared-x layout and no colour-mapped line, so the layout layer is
hand-written regardless, and its SVG path runs through LGPL VectorGraphics2D.
JavaFX is ruled out on size, roughly 40 to 50 MB of OpenJFX per platform leg,
plus a second UI thread in a carefully single-EDT codebase.

Against that, `stepss-java-ui` already contains a hand-painted plotting
subsystem: `SplanePanel` and `ModeShapePanel` painting through `PlotSink`, with
`SwingSink` and `SvgSink` backends and a `PlotStyle` light/dark table that
re-resolves on every FlatLaf change. `batik-all-1.19.jar` is already bundled and
carries `SVGGraphics2D`, so SVG export needs no new dependency, and PNG is plain
`ImageIO`.

## The `.cur` header

Written by `setup_runtime_observables` immediately after the `open` at
`stepss-ramses/src/core/ramses.f90:945-947`, followed by a single `flush` so a
reader can parse it before any data row exists.

`get_disturb` runs at `ramses.f90:422`, well before `setup_runtime_observables`
at `:569`, so `t_dist(nbdist)` is available when the header is written.

Every line begins with `#`, which is gnuplot's default comment character, so a
`.plt` pointed at the file still plots it exactly as before.

```
# stepss-cur 1
# tstop 30.000
# refresh 1.000
# ncol 7
# obs 1 2 1 BV 4044
# obs 2 3 1 MS g6
# obs 3 4 2 LAT 4041
# obs 4 6 1 ON myinj myobs
```

Grammar, one record per line. Fields are separated by **one or more** spaces and
a reader must split on whitespace rather than on single spaces: Fortran's `f12.3`
and integer edit descriptors right-justify, so the emitted lines carry runs of
padding. The example above is shown unpadded for legibility.

- `# stepss-cur <version>` where `<version>` is an integer, currently `1`.
- `# tstop <f12.3>` the value of `t_dist(nbdist)`.
- `# refresh <f12.3>` the value of `gp_refresh_rate`.
- `# ncol <integer>` total columns per data row, including the time column.
- `# obs <index> <first column> <column count> <displaytype> <name> [<name2>]`
  one per observable, in order. Column 1 is always time and is not listed.

Rationale per field:

- **Version marker** so a future format change is a loud refusal rather than a
  misparse. Silent drift is this codebase's recurring failure mode and a reader
  that guesses is exactly that.
- **`tstop`** removes the `.dst` parser the GUI would otherwise need for its
  x-axis range. The engine has already computed it; making the GUI re-derive it
  from the disturbance file is the same duplication being removed on columns.
- **`refresh`** lets the reader poll at the cadence the writer flushes at, and is
  the only way a reader can tell that the file is written more slowly than it is
  read.
- **`ncol`** lets a reader validate a row and reject a torn final line on a count
  rather than on arithmetic.
- **Column count per observable** is the whole point. Column 1 is time and
  observables start at column 2 (`ramses.f90:778`, `varcol=2`), but the stride is
  not uniform: `LAT`, `o-d` and `P-d` advance the counter by 2
  (`ramses.f90:922,930`) while every other type advances by 1. Published once
  here instead of reimplemented in Java and again in Python.
- **`displaytype`** is carried although the caller knows it, so the mapping is
  checkable. A reader that asked for `BV` and finds `LAT` in that slot must fail
  loudly rather than draw nonsense.

Names need no quoting: the display line the user writes is itself
space-tokenised, and `ON` and `TO` legitimately carry two names, so
space-delimited output round-trips exactly what came in.

**Deliberately absent:** the `gnup_cmds` pass-through text, and all titles, axis
labels and units. The caller typed the first and can derive the rest from
`displaytype`, so including them would re-encode what the caller owns.

**Severable extension.** DYNGRAPH's `.cur` does not need this, because selection
order is column order and `ReplayFile.java:76-79` already pins that. Writing the
same header from `caption.f90` would make that contract explicit in the file and
let one Java reader serve both producers, at the cost of regenerating four golden
files in `tools/smoke_gate.sh`, which then pins the header format for free.

## Existing file formats, for the reader

**RAMSES `temp_display.cur`.** No header today. Time in `es13.6`, then per
observable: one `es13.6` column for `BV`, `MS`, `COI`, `BPO`/`BPE`/`BQO`/`BQE`,
`ON`, `TO` and `RT`; one `i8` column for `SOL`; two columns for `LAT` (S in MVA
then a 0/1 activity flag as `i2`), `o-d` (omega then delta mod 2π) and `P-d`
(P in MW then delta mod 2π). Each line ends with a bare `1x`. Flushed on the
`gp_refresh_rate` cadence, so a live reader can catch a torn final line.
Run-time observables are capped at 15 (`DIMENSIONS.f90:93`, `mxruntimeobs`),
and the Java interface exposes three input rows, so at most three panels arise
in practice.

**DYNGRAPH `<base>.cur`.** No header. `E15.7E3` fields, space separated, column 1
is time, then one column per selected observable in selection order. **Every line
ends with a literal ` ;`** which a reader must strip.

## Post-analysis viewer

Everything up to DYNGRAPH is unchanged: picker populated from `dyngraph --list`,
a `List<Selection>`, `ReplayFile.write(sel.cmd)`, `DyngraphRunner.plot(...)`.

1. **Retain the selection list.** `startPlotRun` currently drops it in a local
   (`StepssUI.java:4673`). That list is both the column map and the labels.
2. **Open a `CurveWindow` per extraction**, non-modal, positioned by
   `WindowCascade.track()` exactly as `SsaResultsWindow` is. A new extraction
   never replaces an existing window.
3. **Read `<base>.cur`**: strip the trailing ` ;`, parse `E15.7E3` fields, column
   1 is t, selection *i* is column *i+1*.
4. **Labels** come from `Selection.label()`, which reproduces DYNGRAPH's
   `desc_obs` curve titles byte for byte, and `ObservableIndex.TypeEntry.label`,
   which carries units from `stepss-dyngraph/src/obstypes.f90`. No `.plt` is read.
5. **Draw** one panel, every curve overlaid on one y axis, with a legend. This is
   what `caption.f90` produces today.

**Per-extraction basenames.** Today every extraction overwrites one fixed pair,
`tempGnupOut.cur` and `.plt`, and the name is frozen because two toolbar buttons
open it by name (`StepssUI.java:4657-4660`). Keeping several extractions alive
means each needs its own basename, which falls out naturally once a window owns
its own files.

**Toolbar changes.** With several windows alive there is no "current" extraction,
so *View curves* and *Save extracted curve* leave the Analysis tab bar and become
per-window actions. *Clear all gnuplot instances* becomes *Close all curve
windows*, in both the button (`StepssUI.java:2254-2260`) and the menu item
(`:2691`).

**Known limitation, accepted.** One panel on one y axis means an extraction mixing
units, for example a bus voltage in pu with a branch flow in MW, will let the MW
curve take the scale and flatten the pu curves onto the axis. This is the
behaviour today. The remedy available to the user is now better than it was:
extract the two groups separately and get two windows. The window should say once,
unobtrusively, when a single extraction mixes units, so the flat curves are
explained rather than mysterious.

## Live viewer

The Java interface already writes the observable request lines into the command
file (`writeObservable`, `StepssUI.java:2955-2972`) and owns the working
directory. After R1 the engine writes `temp_display.cur` with its header, flushes
on `gp_refresh_rate`, and calls nothing.

1. **Open a `LiveCurveWindow`** at run start when at least one run-time
   observable is configured. A new run opens a fresh window and leaves the
   previous one frozen as a static chart.
2. **Poll on a `ScheduledExecutorService`**, not a Swing `Timer`, because parsing
   must not run on the EDT. The interval comes from the header's `refresh`,
   defaulting to one second until the header has been read.
3. **Tail, do not re-read.** Hold a byte offset in a `FileChannel`, consume only
   up to the last newline, discard any trailing partial line, keep the offset. A
   full re-read each second is O(n²) over a run, and a long real-time run is
   exactly where that bites. **Reset the offset whenever the file length
   shrinks**, because a re-run truncates through `status='replace'`.
4. **Hand new points to the EDT** with `invokeLater`. The `PlotSink` backends are
   EDT-only, so buffers are handed over, never shared.
5. **Panels:** one per observable, stacked, sharing x over `[0, tstop]` from the
   header. Exceptions: `o-d` and `P-d` are phase-plane, taking x from the
   observable's second column with both axes autoscaled; `LAT` uses its second
   column as a 0-to-1 value driving a blue-to-red per-segment colour, matching
   `gnuplot.f90:92-96,141-142`; `RT` overlays the y=x identity line.
6. **End of run** is the process exit plus the disappearance of `.lock_RAMSES`.
   Do one final read *after* exit so the last flush lands, then leave the window
   open as a static chart, so the live view becomes the post-run view of the same
   observables.

`o-d`, `P-d` and `LAT` are run-time display types only. DYNGRAPH has no
equivalent, so they never appear in the post-analysis window, and stacked layout
gives each of them its own axes without a special case.

**Failure modes, all loud:**

- No header, or an unrecognised version marker: refuse to draw and say which,
  rather than guessing columns. Since `stepss-java-ui` pins a RAMSES version,
  this can only mean a hand-swapped engine, which is worth saying out loud.
- A row whose field count disagrees with `ncol`, or a non-numeric field: skip the
  row, count it, surface one warning at the end rather than a per-row storm.
- No `.cur` after a few polls: say the engine has written no observable data yet,
  rather than showing an empty chart that reads as a broken plot.

## The chart component

**One bounded refactor first.** `PlotSink`, `SwingSink`, `SvgSink` and
`PlotStyle` live in `my.stepss.ssa` with package-private members, so a new
package cannot reach them. Promote all four into a new `my.stepss.plot`, widening
only what the new package needs. `SplanePanel` and `ModeShapePanel` stay where
they are and import from it. The alternatives are duplicating the sink or
misfiling curve code under `ssa`, and both are worse. The existing `axis`,
`grid`, `label` and `title` entries in `PlotStyle.ENTRIES` then style the new
chart's furniture for free, and `PlotStyle.EXPORT_BACKGROUND` already pins
exports to a light ground.

**One painting component, two thin hosts.** A `CurvePanel` takes a list of series
(label, unit, colour, x and y arrays), an axis spec, and whether to draw a
legend. It paints only through `PlotSink`, so it renders to Swing and to SVG with
no branching. The post-analysis window instantiates one panel holding every curve
of its extraction; the live window instantiates one per observable. Nothing else
differs between the two views.

**Axes.** Nice-number ticks on the 1/2/5 × 10ⁿ ladder. Live x is fixed to
`[0, tstop]`; post-analysis x autoscales. y autoscales per panel with a small pad
and rounds outward so ticks land on round values.

**Legend** is a panel option, not a fixture. Post-analysis needs one because N
curves share an axis; live does not, because each panel holds one curve whose
title already names it.

**Series colours are a real addition.** `PlotStyle.ENTRIES` holds one light/dark
pair per semantic class and has no categorical cycle. Overlaying N curves needs a
palette of distinguishable colours with dark-theme variants, colourblind-safe and
legible on both grounds, extending the existing table in its existing shape
rather than sitting beside it.

**Interaction, scoped deliberately.** In: a cursor readout of t and value under
the pointer, following the mouse picking `SplanePanel` already does; rubber-band
zoom with double-click to reset. Out: pan, crosshair lock, toggling curves from
the legend.

**Export.** PNG through `ImageIO`, SVG through `SvgSink`, CSV from the in-memory
series. Post-analysis additionally keeps *Save the gnuplot pair*, copying that
window's own `.cur` and `.plt` with the existing path rewrite
(`StepssUI.java:4440-4442`), which is unambiguous now that the window owns its
basename.

**Live specifics.** Growable arrays appended by the poller, full-panel repaint
rather than dirty rectangles. At 1 Hz with at most three panels, anything
cleverer is premature.

**Layout is a view concern.** Switching anything about presentation must not
touch the point buffers or the poll offset.

## Solver settings after the change

- **`$GP_REFRESH_RATE`** keeps its name, its record and its 1 second default
  (`get_settings.f90:76`). Its meaning narrows from "how often gnuplot is poked"
  to "how often `.cur` is flushed", which is exactly what a polling reader needs.
- **`$GP_MODE`** keeps a real job: it decides which terminal the exported `.plt`
  names. It has no effect on the native chart, and the documentation must say so.
- **`$CALL_GP`** loses its meaning at R2 and becomes accept-and-ignore, reporting
  on stderr that it does nothing. It must keep parsing, because real settings
  files in `stepss-test-systems` and in users' hands set it, and deleting the
  parser would make those files fail. This follows the precedent already set by
  DYNGRAPH's `-l`.

## Deletion inventory

### stepss-ramses, R1 (additive, nothing removed)

- `src/core/ramses.f90` `setup_runtime_observables`: write the header after the
  `open` at `:945-947`, then `flush`.
- `src/io/gnuplot.f90`: split `call_gnuplot` so the `.plt` is written on first
  refresh unconditionally and only the `gnup_com` load push is gated on
  `call_gp`. Without this split, flipping the default below would stop the `.plt`
  being written until R2, because the script is written inside this subroutine.
- The three call sites stop gating the call itself: `simul_decomp.f90:2332`,
  `simul_integr.f90:492` and `:1297`. `simul_integr` is a retired solver kept for
  backward compatibility and needs only to keep compiling and behaving.
- `src/io/get_settings.f90:77`: `call_gp` default `.true.` → `.false.`
- Bug fix: `src/io/gnuplot.f90:79`, move `write(plt,"(a)")trim(string)` inside its
  guarding `if`.
- Bug fix: `src/core/simul_decomp.f90:2283`, `"(i8)"` → `"(i8,1x)"`.

### stepss-ramses, R2 (pure deletion, no behaviour change)

- All eleven tracked files under `libs/libgnup/`, including the four committed
  MSVC project files (`gnuplot.sln`, `gnuplot.vcproj`, `gnuplot.vcxproj`,
  `gnuplot.vcxproj.filters`) and `gnuplot.ncb`.
- `build/Makefile.gfortran` and `build/Makefile.ifort`: `LIB_DIRS`, `LIBS`
  (`-lgnup`), `build-libs`, `build-libgnup`, `DEP_LIBS`, the `ar x` merge line,
  the `clean-libs` `RMDIR`, and `.PHONY`.
- `src/io/gnuplot.f90`: the `gnuplot_interface` interface block declaring
  `gnup_init`, `gnup_com` and `gnup_fin`, keeping `gp_first_time`, and the two
  `gnup_com` load pushes.

  The `set terminal windows` line the script carries under `_WIN32` stays.
  Today it re-selects the terminal on every refresh, because the script is
  re-loaded, and that asymmetry against Linux (which writes no terminal line at
  all) is the untested half of issue #19. Once nothing in STEPSS loads the
  script, the re-selection cannot happen and the line becomes a reasonable
  default for whoever opens the file in their own gnuplot on Windows.
- `src/core/ramses.f90:943` (`gnup_init`) and `:993` (`gnup_fin`).
- `$CALL_GP` to accept-and-ignore.
- `CLAUDE.md:127`, which still describes libgnup as real-time visualisation.

### stepss-dyngraph

Already changed on 2026-08-19: `-l` no longer shells out to
`start wgnuplot.exe` or `gnuplot -p`; it is accepted and reports on stderr that
it does nothing. The post-extraction message names the written `.plt` instead of
telling the reader to click on a plot. `README.md` and `CLAUDE.md` updated.

Still to do:

- `src/version.f90`: `dyngraph_version` `1.3.0` → `1.4.0`, and a release.
- Severable: widen the `i3` column format in `caption.f90`, which malforms the
  `.plt` past 999 columns while `mxobs` is 20000.
- Severable: drop `set terminal windows` from `caption.f90`. This removes its
  last `#if`, makes the `.plt` byte-identical on all platforms, and would let the
  smoke gate cover the non-`-eps` path, at the cost of rewriting that gate's
  documented rationale in `CLAUDE.md`.
- Severable: the `#` header in its own `.cur`, regenerating four golden files.

### stepss-java-ui

New:

- `my.stepss.plot`: `PlotSink`, `SwingSink`, `SvgSink`, `PlotStyle` promoted from
  `my.stepss.ssa`, plus the categorical series palette.
- `my.stepss.curves`: `CurvePanel`, `CurveWindow`, `LiveCurveWindow`, and the
  `.cur` reader.
- `CurveHarness` and `tools/curve-harness.sh`.

Deleted:

- The `GNUPLOT` ToolSpec, the `GNUPLOT` constant and `Toolchain.gnuplot()`.
- `src/my/stepss/gpwin.zip` (9,025,791 bytes) and `gnuplotLicense.txt`. Deleting
  it shrinks every bundle, not just the Windows ones, because the jar is built
  once for all platforms and nothing strips the payload per platform.
- `PlatformLauncher.execEnvironment` and the `WinEnvironment` field it feeds.
  Putting the bundled gnuplot's `bin` on the child PATH was its only purpose, so
  callers pass null and inherit.
- `gnuplotExec`, `gnuplotMissingWarned`, `gnuplotInstallHint()` and the
  "gnuplot was not found" startup banner (`StepssUI.java:7105-7117`,
  `:6941-6950`).
- The Help → About → Gnuplot button and its licence extraction
  (`StepssUI.java:1221`, `:3988-3998`).
- The pass-through instructions in the three run-time observable tooltips
  (`StepssUI.java:1788`, `:1813`, `:1819`).

Changed:

- *Clear all gnuplot instances* → *Close all curve windows*, button and menu item.
- *View curves* and *Save extracted curve* move into the window.
- `startPlotRun` takes a per-extraction basename.
- `StepssUI.form` for the corresponding widgets.
- Seven `README.md` lines.

Three guards that must move in the same commit or fail the build, which is their
purpose:

- `CompileHarness.java:459-461`, whose expected Windows extraction order becomes
  `[ramses, helios, dyngraph, codegen]`.
- `PayloadManifestCheck.java:120-126`, whose special case for the one payload
  committed outside `payload/` goes away.
- `bundle.linux.deps` in `build.xml`, dropping `gnuplot-x11 | gnuplot-qt`.

Then `packaging/linux/copyright`'s gnuplot stanza, and the "gnuplot came with it"
check in `tools/deb-harness.sh`.

### stepss-python-ui

- Delete the PATH probe and `__runTimeObs__` (`src/stepss/__init__.py:52-63`,
  `src/stepss/globals.py:20`) and the `addRunObs` gate
  (`src/stepss/cases.py:429-433`).
- Add live matplotlib plotting driven by in-process polling (`contSim`,
  `getObs`, `getBusVolt`). matplotlib is already a hard dependency
  (`src/setup.py:44`, and `src/stepss/extractor.py:20` imports it
  unconditionally), and `extractor.py:26-42` is existing post-mortem plotting to
  build on.
- Fix `README.rst:75` and the docstrings at `__init__.py:19-21` and
  `cases.py:39,396`.

**Behaviour change to note in the release notes.** With the probe gone,
`addRunObs` stops discarding observables on machines without gnuplot, so
`temp_display.cur` and `.plt` begin appearing in the working directory of users
who previously saw nothing. This is a fix, but it is a change.

**Risk.** matplotlib's event loop cannot run while the calling thread is blocked
inside `ramses(...)`, so live redraw needs a background thread with a GUI-safe
backend or a pause-stepping wrapper. This is the largest unknown on the Python
side and it is a threading problem, not a parsing one.

### Documentation

- `stepss-docs`: `getting-started/installation.mdx:23,44,159`,
  `getting-started/overview.md:76`, `gui/first-run.mdx:91-105`,
  `gui/interface.mdx:57,60-61,129,145,148`, `user-guide/solver-settings.md:28,34-49`,
  `python/overview.md:18`, `python/installation.mdx:80-107`,
  `python/api-reference.md:263-317,917`.
- `stepss-userguide`: `install.tex`, `solvsett.tex`.
- `stepss-apt`: `index.html` and `.github/workflows/publish.yml`.
- `stepss-scoop` needs nothing; it never mentioned gnuplot.

## Release sequence

1. **stepss-dyngraph 1.4.0.** Nothing depends on it, so it goes first and alone.
2. **stepss-java-ui.** The `my.stepss.plot` promotion and the post-analysis
   `CurveWindow`. Requires nothing from the engine, so the chart component earns
   confidence on the half that cannot regress anything, while gnuplot is still
   bundled and *View curves* still works.
3. **stepss-ramses R1.** Additive. `$CALL_GP T` restores the pipe for a whole
   release cycle if the native chart disappoints.
4. **stepss-java-ui.** The live window and every deletion listed above. This is
   the release in which 58.7 MB and the platform dependency leave, so the
   documentation changes land **with** it, not after it.
5. **stepss-python-ui.** Probe removal and matplotlib live plotting.
6. **stepss-ramses R2.** The deletion, by then provably dead.

Step 2 before step 3 is deliberate.

**Each numbered step is one implementation plan.** This document is deliberately
not a single plan's worth of work: it spans six releases across five
repositories, and trying to plan it as one unit would produce something nobody
could review or execute. Step 1 is already coded and needs only a version bump
and a release, so the first substantial plan is step 2.

Sequencing constraint from the umbrella `CLAUDE.md`: `stepss-java-ui` re-pins its
components from `repository_dispatch`, dispatches do not retry, and a lost
dispatch means java-ui silently stops tracking a component. Each step above is a
separate STEPSS release, and a red `notify-java-ui` in a component repo is a real
failure rather than noise.

## Verification

No unit-test framework exists in `stepss-java-ui`; headless `*Harness` and
`*Check` main classes plus `tools/*-harness.sh` are the substitute, and only
`tools/deb-harness.sh` runs in CI.

- **`CurveHarness`**: header parsing including a rejected version marker, torn
  last line, ` ;` stripping, offset reset on truncation, `ncol` mismatch handling,
  nice-number tick selection over awkward ranges, and one SVG output pinned byte
  for byte.
- **`tools/smoke_gate.sh`** in stepss-dyngraph continues to pin `.cur` and `.plt`
  against goldens, and gains the header if that extension is taken.
- **RAMSES R1** must be checked for the two bug fixes with a run containing an
  `o-d` or `P-d` observable alongside a time-series one, and a `SOL` observable
  followed by a column that can go negative.
- **Issue #19 stays open until step 4** and closes on a Windows run of the live
  viewer. Since the bundle refresh was rejected, this is now the only route to
  it, and the bundled gnuplot 4.6 keeps naming a `pgnuplot.exe` entry that
  modern gnuplot no longer ships. That should be recorded on the issue so the
  reporter is not waiting on a fix that is not coming separately. Windows is the
  only platform where the current behaviour is known to be broken, so it is the
  only one that can confirm the fix.

## Open items

- Whether to take the three severable DYNGRAPH changes (`i3` widening,
  `set terminal windows` removal, `.cur` header).
- The exact categorical series palette.
- Whether the post-analysis mixed-unit notice is a status line, a legend
  annotation, or a one-time banner.
