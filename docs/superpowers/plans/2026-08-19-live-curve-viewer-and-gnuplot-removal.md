# Live Curve Viewer and Gnuplot Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace RAMSES run-time gnuplot plotting with a native Swing live curve window that tails `temp_display.cur`, then delete every trace of gnuplot from `stepss-java-ui`.

**Architecture:** The engine already writes a `# stepss-cur 1` header and flushes on `gp_refresh_rate` (stepss-ramses step 3, merged). A new `CurHeader` parses that header, a new `CurTail` reads only the bytes appended since the last poll, a new `LiveModel` maps rows onto per-observable growable buffers and hands the EDT immutable snapshots, and `LiveCurveWindow` stacks one existing `CurvePanel` per observable. `CurvePanel` is generalised with an axis spec so the same painting component serves both the post-analysis window built in step 2 and the live one. Once the live window works, the bundled gnuplot payload, its PATH plumbing, its toolchain entry and its UI surface all go.

**Tech Stack:** Java 11 (`javac.source=11`), Swing, FlatLaf, Apache Commons Exec, Ant. No test framework: headless `*Harness` main classes plus `tools/*-harness.sh`.

**Spec:** `docs/superpowers/specs/2026-08-19-remove-gnuplot-dependency-design.md` — sections *The `.cur` header*, *Existing file formats*, *Live viewer*, *The chart component*, *Deletion inventory → stepss-java-ui*, *Verification*. This is step 4 of that document's six-step release sequence.

## Global Constraints

- **This is step 4 of six.** Step 2 (post-analysis viewer) and step 3 (engine header) are merged. Step 5 (python-ui) and step 6 (ramses R2, delete `libgnup`) are out of scope and must not be touched.
- **Scope is `stepss-java-ui` only.** No engine change, no change to any other `stepss-*` repo. The `stepss-docs`, `stepss-userguide` and `stepss-apt` edits the spec lists under *Documentation* are a separate follow-on pass; this plan owns `README.md` and `packaging/linux/copyright` because they live in this repo.
- **The invariant, verbatim from the spec:** *STEPSS writes gnuplot files. STEPSS never reads one and never runs one.* Nothing in this plan may read a `.plt`, and after Task 8 nothing may execute a gnuplot binary. The `.plt` remains written, unread, for users who want it.
- **The engine writes the column map; Java never re-derives it.** Column layout comes from the header's `obs` records. Do not reimplement the `varcol` stride (`LAT`, `o-d` and `P-d` advance by 2, everything else by 1) anywhere in Java.
- **No parsing on the EDT.** Polling and parsing run on a `ScheduledExecutorService`; only finished immutable snapshots cross to the EDT via `invokeLater`. `PlotSink` backends are EDT-only.
- **Styling is a class name, never a colour.** Every hex a figure draws must come from `PlotStyle.ENTRIES`, so an exported SVG is restylable by editing one rule. New visual states need new entries, not inline colours.
- **Java 11.** No `var` in new code (the existing code does not use it), no records, no text blocks. Explicit type arguments on collection construction, matching the surrounding style (`new ArrayList<CurveSeries>()`).
- **No em-dashes** in code comments, Javadoc, commit messages or documentation prose. Use a colon, a comma, or two sentences.
- **Every harness check must be able to fail.** After writing a check, mutate the code it covers so the check goes red, confirm it does, revert the mutation. A check that passes against a deliberately broken implementation is worse than no check: eight of these were found and fixed during steps 2 and 3. Record the mutation you used in the task report.
- **`ant clean` before any harness run.** `<jar update="true">` never removes entries and stale `build/classes` files stay on the classpath, which during step 2 put two copies of the same class in front of a harness.
- **Release gate, not a code constraint:** `versions.properties` pins `ramses.version=3.76`, and the header lands in the *next* RAMSES release. The live window therefore reports "this engine writes no run-time header" against the currently pinned engine. That is correct behaviour and must not be softened into a guess. It also means this branch must not be released before a RAMSES release carrying step 3 and the resulting java-ui re-pin. Task 9 records that in the README; cutting the release is the maintainer's call.

---

## File Structure

**New:**

| File | Responsibility |
|---|---|
| `src/my/stepss/plot/NiceScale.java` | The 1/2/5 tick ladder, shared by every panel |
| `src/my/stepss/curves/CurHeader.java` | Parses the `# stepss-cur 1` header into observable records |
| `src/my/stepss/curves/CurTail.java` | Incremental byte-offset reader over a growing file |
| `src/my/stepss/curves/CurveAxes.java` | One panel's axis spec: labels, fixed or autoscaled x, legend, identity line |
| `src/my/stepss/curves/LiveModel.java` | Header plus appended rows to per-observable snapshots |
| `src/my/stepss/curves/LiveCurveWindow.java` | Swing host: stacked panels, poller, freeze at end of run |

**Modified:**

| File | Change |
|---|---|
| `src/my/stepss/plot/PlotStyle.java` | Two latency classes; `NiceScale` unaffected |
| `src/my/stepss/plot/PlotHarness.java` | Gains the tick-ladder checks moved out of `CurveHarness` |
| `src/my/stepss/curves/CurvePanel.java` | Takes a `CurveAxes`; delegates the ladder; draws latency segments and the identity line |
| `src/my/stepss/curves/CurveSeries.java` | Optional per-point `w` array for latency shading |
| `src/my/stepss/curves/CurReader.java` | Ignores `#` comment lines instead of counting them as damage |
| `src/my/stepss/curves/CurveHarness.java` | Header, tail, model, axes and latency checks |
| `src/my/stepss/StepssUI.java` | Live window wiring; every gnuplot deletion |
| `src/my/stepss/StepssUI.form` | Matching widget changes |
| `src/my/stepss/platform/Toolchain.java` | `GNUPLOT` spec, constant and `gnuplot()` deleted |
| `src/my/stepss/platform/PlatformLauncher.java` | `execEnvironment` deleted |
| `src/my/stepss/platform/PayloadManifestCheck.java` | The out-of-`payload/` special case goes |
| `src/my/stepss/compile/CompileHarness.java` | Windows extraction order loses gnuplot |
| `src/my/stepss/dyngraph/DyngraphRunner.java` | Drops the always-null environment parameter |
| `build.xml` | `bundle.linux.deps` drops `gnuplot-x11 \| gnuplot-qt` |
| `packaging/linux/copyright` | The gnuplot stanza goes |
| `tools/deb-harness.sh` | The "gnuplot came with it" check goes |
| `versions.properties` | The `gpwin.zip` comment goes |
| `README.md` | Gnuplot is gone; the live window is documented |

**Deleted:** `src/my/stepss/gpwin.zip` (9,025,791 bytes), `src/my/stepss/gnuplotLicense.txt`.

---

## Rulings made while writing this plan

Recorded here so an implementer does not re-open them, and so a reviewer can
overturn them against evidence rather than taste.

1. **The latency flag is boolean, so it gets two style classes, not a colour ramp.** The spec's live-viewer section says `LAT` uses its second column "as a 0-to-1 value driving a blue-to-red per-segment colour". The writer disagrees: `simul_decomp.f90:2312-2316` writes a literal `1` or `0` through `i2` from the `isactive` logical. `gnuplot.f90:95-96` sets `cbrange [0:1]` with a two-stop palette, which for a 0/1 input yields exactly blue or red and never an intermediate. A continuous ramp would therefore be unreachable code. Two entries, `latent` and `active`. Cost if wrong: a future engine that writes a fraction shades in two buckets until a ramp is added.
2. **Snapshots cross to the EDT, not shared growable buffers.** `tsample` defaults to `1.0e-2` s (`get_settings.f90:75`) so a 240 s run writes about 24,000 rows, and at most three observables exist in the GUI. A full copy per poll is at worst about 1 MB, once per second, against a young generation. The alternative, handing over a volatile array reference plus a captured length, is a copy-on-grow data race whose correctness no headless harness can demonstrate. Cost if wrong: garbage pressure on runs far longer than any this GUI can configure; the documented upgrade is the volatile handover.
3. **`DyngraphRunner` loses its `environment` parameter rather than keeping it null.** The spec says callers "pass null and inherit". `PlatformLauncher.execEnvironment` was the only thing that ever supplied a non-null value and it is deleted, and `DyngraphRunner` has exactly one caller. A parameter every caller passes null to is dead weight. Cost if wrong: re-adding a parameter, a two-line change.
4. **The latency observation window leaves the panel title.** `gnuplot.f90:98` titles a `LAT` panel with `aver_time_window` from the settings file. The header does not carry it and the GUI does not parse settings, so the title is `Equipment: <name>`. Cost if wrong: a user comparing against an old gnuplot figure loses one number, which is in their settings file.
5. **`CurReader`'s comment tolerance is hardening, not a user-visible bug fix.** Fed a RAMSES `.cur`, today's `parse` counts each of the seven `#` lines as an unreadable row and the window reports "7 unreadable row(s) skipped" over perfectly good data. No user path reaches that today, because the post-analysis window only ever opens DYNGRAPH output. It is fixed here because step 4 puts a headed `.cur` in the same working directory as the DYNGRAPH ones, one file-chooser slip apart. Cost if wrong: nothing; the change cannot affect a file with no comment lines.

---

## Task 1: Promote the tick ladder into `my.stepss.plot`

The final reviewer of step 2 flagged this: `niceStep` and `niceBounds` are
public statics on `CurvePanel`, and the live viewer needs the same ladder for
its stacked panels. Leaving them there forces this plan either to import a
painting component for its arithmetic or to duplicate it.

**Files:**
- Create: `src/my/stepss/plot/NiceScale.java`
- Modify: `src/my/stepss/curves/CurvePanel.java:299-332` (delete both methods), `:363-366` (call sites)
- Modify: `src/my/stepss/plot/PlotHarness.java` (gains the checks)
- Modify: `src/my/stepss/curves/CurveHarness.java` (loses them)

**Interfaces:**
- Produces: `my.stepss.plot.NiceScale.step(double span, int targetTicks)` and `my.stepss.plot.NiceScale.bounds(double lo, double hi, double step)`, both `public static`, same semantics as the methods they replace.
- Consumes: nothing.

- [ ] **Step 1: Create `NiceScale` with both methods moved verbatim**

Bodies are unchanged; only the class, the names and the Javadoc's owner move.

```java
package my.stepss.plot;

/**
 * Round-number axis ticks on the 1, 2, 5 times a power of ten ladder.
 *
 * <p>Here rather than on a panel because every panel needs it: the
 * post-analysis window has one set of axes and the live window has one per
 * observable, and an axis ladder is arithmetic rather than painting.
 */
public final class NiceScale {

    private NiceScale() {
    }

    /**
     * The next round number at or below {@code raw = span / targetTicks}, on
     * the 1, 2, 5 times a power of ten ladder. A zero span means a flat curve,
     * which still needs a non-zero step or the tick loop cannot advance.
     */
    public static double step(double span, int targetTicks) {
        if (span <= 0.0 || targetTicks <= 0) {
            return 1.0;
        }
        double raw = span / targetTicks;
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)));
        double normalised = raw / magnitude;
        double nice;
        if (normalised <= 1.0) {
            nice = 1.0;
        } else if (normalised <= 2.0) {
            nice = 2.0;
        } else if (normalised <= 5.0) {
            nice = 5.0;
        } else {
            nice = 10.0;
        }
        return nice * magnitude;
    }

    /**
     * {@code lo} and {@code hi} widened outward to whole multiples of
     * {@code step}, so every tick is a round number and the data sits inside
     * the frame. Returns {lo, hi}; a flat range is widened by one step so the
     * axis has an extent.
     */
    public static double[] bounds(double lo, double hi, double step) {
        double low = Math.floor(lo / step) * step;
        double high = Math.ceil(hi / step) * step;
        if (high - low < step / 2.0) {
            low -= step;
            high += step;
        }
        return new double[] {low, high};
    }
}
```

- [ ] **Step 2: Delete both methods from `CurvePanel` and update its two call sites**

In `CurvePanel.bounds`, replace

```java
        double xStep = niceStep(tHi - tLo, X_TICKS);
        double yStep = niceStep(vHi - vLo, Y_TICKS);
        double[] x = niceBounds(tLo, tHi, xStep);
        double[] y = niceBounds(vLo, vHi, yStep);
```

with

```java
        double xStep = NiceScale.step(tHi - tLo, X_TICKS);
        double yStep = NiceScale.step(vHi - vLo, Y_TICKS);
        double[] x = NiceScale.bounds(tLo, tHi, xStep);
        double[] y = NiceScale.bounds(vLo, vHi, yStep);
```

and add `import my.stepss.plot.NiceScale;`. Delete the `public static double
niceStep(...)` and `public static double[] niceBounds(...)` declarations and
their Javadoc entirely. Do not leave delegating wrappers: the point is one
home for the ladder.

- [ ] **Step 3: Move the ladder checks from `CurveHarness` to `PlotHarness`**

Find every check in `CurveHarness` that calls `CurvePanel.niceStep` or
`CurvePanel.niceBounds` and move it into `PlotHarness`, rewritten against
`NiceScale`. Keep the check names and the expected values exactly as they are:
they were written against awkward ranges on purpose and they pass today.

- [ ] **Step 4: Add one check `CurveHarness` could not have had**

`NiceScale` is now reachable without a panel, so the degenerate cases are worth
pinning in `PlotHarness`:

```java
        check("a zero span still yields an advancing step",
                "1.0", String.valueOf(NiceScale.step(0.0, 6)));
        check("a flat range is widened by one step either side",
                "[4.0, 6.0]",
                java.util.Arrays.toString(NiceScale.bounds(5.0, 5.0, 1.0)));
```

- [ ] **Step 5: Build and run both harnesses**

```bash
ant clean
ant compile
tools/plot-harness.sh
tools/curve-harness.sh
```

Expected: both print their existing checks plus the two new ones, all passing.
`ant clean` is not optional; see Global Constraints.

- [ ] **Step 6: Prove the moved checks can still fail**

Change `nice = 2.0;` to `nice = 3.0;` in `NiceScale.step`, run
`tools/plot-harness.sh`, confirm a ladder check goes red, revert. Record the
mutation in the task report.

- [ ] **Step 7: Commit**

```bash
git add src/my/stepss/plot/NiceScale.java src/my/stepss/plot/PlotHarness.java \
        src/my/stepss/curves/CurvePanel.java src/my/stepss/curves/CurveHarness.java
git commit -m "refactor: give the tick ladder one home in my.stepss.plot"
```

---

## Task 2: Parse the `.cur` header, and stop counting comments as damage

**Files:**
- Create: `src/my/stepss/curves/CurHeader.java`
- Modify: `src/my/stepss/curves/CurReader.java:47-60` (comment tolerance)
- Modify: `src/my/stepss/curves/CurveHarness.java` (checks)

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `my.stepss.curves.CurHeader` with `public final double tstop;`, `public final double refresh;`, `public final int ncol;`, `public final List<CurHeader.Obs> observables;`
  - `CurHeader.Obs` with `public final int index; public final int firstColumn; public final int columnCount; public final String type; public final String name; public final String name2;`
  - `public static CurHeader parse(List<String> lines) throws CurHeader.Unsupported`
  - `public static final int VERSION = 1;`
  - `CurHeader.Unsupported extends Exception` carrying a user-facing message.

- [ ] **Step 1: Write the failing checks in `CurveHarness`**

Add a `checkHeader()` method, called from `main`, holding these. `check` is the
existing helper: `check(String what, String expected, String actual)`.

```java
    private static void checkHeader() {
        // The real capture from a locally built engine, reproduced byte for
        // byte apart from the leading indentation of the data row.
        List<String> real = java.util.Arrays.asList(
                "# stepss-cur 1",
                "# tstop      240.000",
                "# refresh        1.000",
                "# ncol 5",
                "# obs 1 2 1 BV 1041",
                "# obs 2 3 2 o-d g6",
                "# obs 3 5 1 MS g6",
                " 0.000000E+00  1.012404E+00  1.000000E+00  6.018289E+00  1.000000E+00 ");

        CurHeader h;
        try {
            h = CurHeader.parse(real);
        } catch (CurHeader.Unsupported ex) {
            check("the real capture parses", "no exception", ex.getMessage());
            return;
        }
        check("tstop comes off the padded f12.3 field", "240.0", String.valueOf(h.tstop));
        check("refresh comes off the padded f12.3 field", "1.0", String.valueOf(h.refresh));
        check("ncol is the total column count including time", "5", String.valueOf(h.ncol));
        check("one record per observable", "3", String.valueOf(h.observables.size()));
        check("the o-d observable spans two columns", "2",
                String.valueOf(h.observables.get(1).columnCount));
        check("the o-d observable starts at column three", "3",
                String.valueOf(h.observables.get(1).firstColumn));
        check("the display type is carried so the mapping is checkable", "o-d",
                h.observables.get(1).type);
        check("a one-name observable has an empty second name", "",
                h.observables.get(1).name2);

        // ON and TO legitimately carry two names, space delimited, unquoted.
        List<String> twoNames = java.util.Arrays.asList(
                "# stepss-cur 1", "# tstop 30.000", "# refresh 1.000", "# ncol 2",
                "# obs 1 2 1 ON myinj myobs");
        try {
            check("the second name of an ON observable is read", "myobs",
                    CurHeader.parse(twoNames).observables.get(0).name2);
        } catch (CurHeader.Unsupported ex) {
            check("the two-name header parses", "no exception", ex.getMessage());
        }

        // A version this build does not know is a loud refusal, never a guess.
        List<String> future = java.util.Arrays.asList(
                "# stepss-cur 2", "# tstop 30.000", "# refresh 1.000", "# ncol 2",
                "# obs 1 2 1 BV 4044");
        try {
            CurHeader.parse(future);
            check("an unknown version marker refuses", "Unsupported thrown", "parsed anyway");
        } catch (CurHeader.Unsupported ex) {
            check("an unknown version marker names the version it found",
                    "true", String.valueOf(ex.getMessage().contains("2")));
        }

        // No header at all is the state of every engine older than the map.
        try {
            CurHeader.parse(java.util.Arrays.asList(
                    " 0.000000E+00  1.012404E+00 "));
            check("a headerless file refuses", "Unsupported thrown", "parsed anyway");
        } catch (CurHeader.Unsupported ex) {
            check("a headerless file says so rather than guessing columns",
                    "true", String.valueOf(ex.getMessage().toLowerCase(
                            java.util.Locale.ROOT).contains("no run-time header")));
        }

        // The columns the records claim must add up to what ncol states, or
        // the reader is about to draw one observable's data under another's
        // name. ncol 5 with records covering 2..5 is consistent; ncol 6 is not.
        List<String> inconsistent = java.util.Arrays.asList(
                "# stepss-cur 1", "# tstop 30.000", "# refresh 1.000", "# ncol 6",
                "# obs 1 2 1 BV 4044", "# obs 2 3 2 o-d g6", "# obs 3 5 1 MS g6");
        try {
            CurHeader.parse(inconsistent);
            check("ncol disagreeing with the records refuses",
                    "Unsupported thrown", "parsed anyway");
        } catch (CurHeader.Unsupported ex) {
            check("the ncol disagreement names both numbers", "true",
                    String.valueOf(ex.getMessage().contains("6")
                            && ex.getMessage().contains("5")));
        }
    }
```

And one check that the comment tolerance is real, which belongs beside the
existing `CurReader` checks:

```java
        // Fed a headed RAMSES file, the DYNGRAPH reader must treat the
        // comment lines as comments. Counting them as unreadable rows made
        // CurveWindow's header report damage over undamaged data. Six of them
        // here, one per header record, because this fixture carries two
        // observables; the real capture has seven because it carries three.
        CurveData headed = CurReader.parse(java.util.Arrays.asList(
                "# stepss-cur 1",
                "# tstop      240.000",
                "# refresh        1.000",
                "# ncol 3",
                "# obs 1 2 1 BV 1041",
                "# obs 2 3 1 MS g6",
                " 0.000000E+00  1.012404E+00  1.000000E+00 ",
                " 1.000000E-02  1.012000E+00  1.000100E+00 "),
                null, java.util.Arrays.asList("bus voltage (pu)", "speed (pu)"));
        check("a clean headed file reports no damage", "0",
                String.valueOf(headed.skippedRows));
        check("and every data row survives", "2",
                String.valueOf(headed.series.get(0).v.length));
```

- [ ] **Step 2: Run the harness and watch it fail**

```bash
ant clean && ant compile
```

Expected: compilation fails, `CurHeader` does not exist. That is the failing
state for this task.

- [ ] **Step 3: Write `CurHeader`**

```java
package my.stepss.curves;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The header RAMSES writes at the top of {@code temp_display.cur}.
 *
 * <p>Written by {@code setup_runtime_observables} in
 * stepss-ramses/src/core/ramses.f90 immediately after the file is opened, and
 * flushed there, so a reader can learn the column layout before any data row
 * exists. Every line starts with {@code #}, which is gnuplot's comment
 * character, so the {@code .plt} beside the file still plots it unchanged.
 *
 * <pre>
 * # stepss-cur 1
 * # tstop      240.000
 * # refresh        1.000
 * # ncol 5
 * # obs 1 2 1 BV 1041
 * # obs 2 3 2 o-d g6
 * # obs 3 5 1 MS g6
 * </pre>
 *
 * <p>Fields are separated by one or more spaces and must be split on
 * whitespace, not on a single space: {@code f12.3} and the integer edit
 * descriptors right-justify, so the emitted lines carry runs of padding.
 *
 * <p>This class exists so that the column map is read rather than recomputed.
 * The stride is not uniform, {@code LAT}, {@code o-d} and {@code P-d} taking
 * two columns and everything else one, and reimplementing that arithmetic in
 * Java is exactly the duplication the header was added to remove.
 */
public final class CurHeader {

    /** The only format version this build understands. */
    public static final int VERSION = 1;

    /** Refused rather than guessed: an unreadable header is never a misparse. */
    public static final class Unsupported extends Exception {

        private static final long serialVersionUID = 1L;

        Unsupported(String message) {
            super(message);
        }
    }

    /** One observable's slice of a data row. */
    public static final class Obs {

        /** Its 1-based position in the display list. */
        public final int index;
        /** Its first column, 1-based, where column 1 is always time. */
        public final int firstColumn;
        /** How many columns it occupies: 2 for LAT, o-d and P-d, else 1. */
        public final int columnCount;
        /** The display type as the user typed it, for example BV or o-d. */
        public final String type;
        /** The equipment name. */
        public final String name;
        /** The second name ON and TO carry, or "" for every other type. */
        public final String name2;

        Obs(int index, int firstColumn, int columnCount,
                String type, String name, String name2) {
            this.index = index;
            this.firstColumn = firstColumn;
            this.columnCount = columnCount;
            this.type = type;
            this.name = name;
            this.name2 = name2;
        }
    }

    /** The simulation stop time, which fixes the live x axis. */
    public final double tstop;
    /** How often the writer flushes, in seconds, which sets the poll interval. */
    public final double refresh;
    /** Total columns per data row, including the time column. */
    public final int ncol;
    /** One record per observable, in display order. */
    public final List<Obs> observables;

    private CurHeader(double tstop, double refresh, int ncol, List<Obs> observables) {
        this.tstop = tstop;
        this.refresh = refresh;
        this.ncol = ncol;
        this.observables = Collections.unmodifiableList(observables);
    }

    /**
     * @param lines the whole file, or as much of it as has been read; parsing
     *     stops at the first line that is not a comment
     * @throws Unsupported when there is no header, when its version is not
     *     {@link #VERSION}, or when the records disagree with {@code ncol}
     */
    public static CurHeader parse(List<String> lines) throws Unsupported {
        Double tstop = null;
        Double refresh = null;
        Integer ncol = null;
        List<Obs> observables = new ArrayList<Obs>();
        boolean sawVersion = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("#")) {
                // The header is contiguous and comes first, so the first data
                // row ends it. Stopping here rather than scanning the whole
                // file is what lets a live reader parse the header out of the
                // first poll's worth of bytes.
                break;
            }
            String[] f = line.substring(1).trim().split("\\s+");
            if (f.length == 0) {
                continue;
            }
            if ("stepss-cur".equals(f[0])) {
                sawVersion = true;
                int version = intField(f, 1, "stepss-cur");
                if (version != VERSION) {
                    throw new Unsupported("This .cur file declares format version "
                            + version + ", and this build of STEPSS reads version "
                            + VERSION + " only. The engine and the interface are"
                            + " from different releases.");
                }
            } else if ("tstop".equals(f[0])) {
                tstop = doubleField(f, 1, "tstop");
            } else if ("refresh".equals(f[0])) {
                refresh = doubleField(f, 1, "refresh");
            } else if ("ncol".equals(f[0])) {
                ncol = intField(f, 1, "ncol");
            } else if ("obs".equals(f[0])) {
                if (f.length < 6) {
                    throw new Unsupported("An obs record in the .cur header has "
                            + (f.length - 1) + " fields where at least 5 are"
                            + " required: " + line);
                }
                observables.add(new Obs(
                        intField(f, 1, "obs index"),
                        intField(f, 2, "obs first column"),
                        intField(f, 3, "obs column count"),
                        f[4],
                        f[5],
                        f.length > 6 ? f[6] : ""));
            }
            // An unrecognised comment is ignored rather than refused, so a
            // later engine may add a record this build has no use for without
            // that being a version bump.
        }

        if (!sawVersion) {
            throw new Unsupported("This engine wrote no run-time header into"
                    + " temp_display.cur, so the columns cannot be identified."
                    + " Run-time curves need a RAMSES release that publishes the"
                    + " column map.");
        }
        if (tstop == null || refresh == null || ncol == null) {
            throw new Unsupported("The .cur header is missing "
                    + (tstop == null ? "tstop " : "")
                    + (refresh == null ? "refresh " : "")
                    + (ncol == null ? "ncol " : "") + "and cannot be used.");
        }

        int covered = 1;
        for (Obs o : observables) {
            if (o.firstColumn != covered + 1) {
                throw new Unsupported("Observable " + o.index + " claims column "
                        + o.firstColumn + " where column " + (covered + 1)
                        + " is next. The .cur header is inconsistent.");
            }
            covered += o.columnCount;
        }
        if (covered != ncol) {
            throw new Unsupported("The .cur header states ncol " + ncol
                    + " but its observable records cover " + covered
                    + " columns. Reading it would draw one observable's data"
                    + " under another's name.");
        }
        return new CurHeader(tstop, refresh, ncol, observables);
    }

    private static int intField(String[] f, int i, String what) throws Unsupported {
        try {
            return Integer.parseInt(field(f, i, what));
        } catch (NumberFormatException notANumber) {
            throw new Unsupported("The .cur header's " + what
                    + " is not an integer: " + f[i]);
        }
    }

    private static double doubleField(String[] f, int i, String what) throws Unsupported {
        try {
            return Double.parseDouble(field(f, i, what));
        } catch (NumberFormatException notANumber) {
            throw new Unsupported("The .cur header's " + what
                    + " is not a number: " + f[i]);
        }
    }

    private static String field(String[] f, int i, String what) throws Unsupported {
        if (i >= f.length) {
            throw new Unsupported("The .cur header's " + what + " record has no value.");
        }
        return f[i];
    }
}
```

- [ ] **Step 4: Make `CurReader` ignore comment lines**

In `CurReader.parse`, immediately after the `line.isEmpty()` guard and
**before** the `;` stripping, insert:

```java
            // A comment, not a row. DYNGRAPH writes none, but RAMSES writes
            // seven at the top of its own .cur and both files live in the same
            // working directory. Counting them as unreadable rows reported
            // damage over undamaged data.
            if (line.startsWith("#")) {
                continue;
            }
```

- [ ] **Step 5: Run the harness**

```bash
ant clean && ant compile
tools/curve-harness.sh
```

Expected: every new check passes alongside the existing ones.

- [ ] **Step 6: Prove three of the new checks can fail**

Each mutation, one at a time, revert after each:

1. In `CurHeader.parse`, change `if (version != VERSION)` to `if (false)`.
   Expected red: "an unknown version marker refuses".
2. Delete the `if (covered != ncol)` block. Expected red: "ncol disagreeing
   with the records refuses".
3. In `CurReader.parse`, delete the `line.startsWith("#")` guard. Expected
   red: "a clean headed file reports no damage" (6, not 0: the fixture above
   has six comment lines, not the real capture's seven).

Record all three in the task report. If any mutation leaves the harness green,
the check is blind and must be rewritten before the task is done.

- [ ] **Step 7: Commit**

```bash
git add src/my/stepss/curves/CurHeader.java src/my/stepss/curves/CurReader.java \
        src/my/stepss/curves/CurveHarness.java
git commit -m "feat: read the .cur column map instead of recomputing it"
```

---

## Task 3: Tail the growing file by byte offset

**Files:**
- Create: `src/my/stepss/curves/CurTail.java`
- Modify: `src/my/stepss/curves/CurveHarness.java` (checks)

**Interfaces:**
- Consumes: nothing.
- Produces: `my.stepss.curves.CurTail` with `public CurTail(File file)`, `public List<String> poll() throws IOException`, `public boolean exists()`, `public long offset()`, `public boolean truncatedSinceLastPoll()`.

- [ ] **Step 1: Write the failing checks**

Add `checkTail()` to `CurveHarness`, called from `main`. It writes real files
into a temporary directory, because the whole point of the class is file
mechanics.

```java
    private static void checkTail() throws IOException {
        File dir = java.nio.file.Files.createTempDirectory("curtail").toFile();
        File cur = new File(dir, "temp_display.cur");
        try {
            CurTail tail = new CurTail(cur);
            check("a file that does not exist yet is not an error", "false",
                    String.valueOf(tail.exists()));
            check("and yields nothing", "0", String.valueOf(tail.poll().size()));

            append(cur, "# stepss-cur 1\n# ncol 2\n");
            check("the header arrives as lines", "[# stepss-cur 1, # ncol 2]",
                    tail.poll().toString());
            check("and is not delivered twice", "[]", tail.poll().toString());

            // A torn final line is the normal state of a file being written:
            // the reader must hold it back until its newline arrives.
            append(cur, " 1.0 2.0\n 3.0 4");
            check("a complete row is delivered and a torn one held back",
                    "[ 1.0 2.0]", tail.poll().toString());
            append(cur, ".0\n");
            check("the held-back row arrives once it is terminated",
                    "[ 3.0 4.0]", tail.poll().toString());

            // status='replace' truncates, which is what a re-run does. An
            // offset kept across that would skip the new run's header and
            // then misparse whatever byte it landed on.
            write(cur, "# stepss-cur 1\n");
            List<String> afterRerun = tail.poll();
            check("a shrunk file is read from the top again",
                    "[# stepss-cur 1]", afterRerun.toString());
            check("and the reset is reported so a reader can drop its state",
                    "true", String.valueOf(tail.truncatedSinceLastPoll()));
            check("the reset flag clears on the next poll", "false",
                    String.valueOf(tail.poll().isEmpty() ? tail.truncatedSinceLastPoll() : true));

            // The offset must advance rather than the file being re-read: a
            // full re-read each second is quadratic over a run.
            long before = tail.offset();
            // Nine bytes: space, 9, dot, 0, space, 9, dot, 0, newline. Count
            // the literal rather than trusting this comment, and if it
            // disagrees fix the expected value, never the class: an offset
            // that advances by the wrong amount is the bug this pins.
            append(cur, " 9.0 9.0\n");
            tail.poll();
            check("the offset advances by exactly what was appended",
                    String.valueOf(before + 9), String.valueOf(tail.offset()));
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void append(File f, String s) throws IOException {
        try (java.io.OutputStream out = new java.io.FileOutputStream(f, true)) {
            out.write(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }
    }

    private static void write(File f, String s) throws IOException {
        try (java.io.OutputStream out = new java.io.FileOutputStream(f, false)) {
            out.write(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                deleteRecursively(kid);
            }
        }
        f.delete();
    }
```

If `CurveHarness.main` does not already declare `throws IOException`, add it.

- [ ] **Step 2: Run and watch it fail to compile**

```bash
ant clean && ant compile
```

Expected: `CurTail` does not exist.

- [ ] **Step 3: Write `CurTail`**

```java
package my.stepss.curves;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the lines appended to a file since the last call.
 *
 * <p>A live viewer polls once per flush for the whole length of a run, so
 * re-reading the file each time is quadratic in the number of rows, and a long
 * real-time run is exactly where that bites. This holds a byte offset instead
 * and consumes only up to the last newline, keeping any trailing partial line
 * for the next poll: the writer flushes mid-row, so a torn final line is the
 * normal state of the file rather than an error.
 *
 * <p>Not thread safe, and deliberately so: one poller thread owns an instance
 * for the life of a run.
 *
 * <p>ISO-8859-1 rather than UTF-8, matching {@link CurReader}: these are
 * Fortran-written bytes and a byte outside UTF-8's valid sequences would
 * otherwise be replaced rather than read.
 */
public final class CurTail {

    private final File file;
    private long offset;
    private StringBuilder partial = new StringBuilder();
    private boolean truncated;

    public CurTail(File file) {
        this.file = file;
    }

    /** Whether the writer has created the file yet. */
    public boolean exists() {
        return file.isFile();
    }

    /** How many bytes have been consumed. */
    public long offset() {
        return offset;
    }

    /**
     * Whether the most recent {@link #poll()} found the file shorter than the
     * offset and started again from the top.
     *
     * <p>A re-run opens the file with {@code status='replace'}, which
     * truncates it. A reader that kept its offset across that would skip the
     * new run's header and resume mid-row, so this is how it learns to throw
     * its parsed state away. Cleared by the following poll.
     */
    public boolean truncatedSinceLastPoll() {
        return truncated;
    }

    /**
     * @return the complete lines appended since the last call, without their
     *     line terminators, oldest first; empty when the file does not exist
     *     yet or nothing whole has been added
     */
    public List<String> poll() throws IOException {
        truncated = false;
        if (!file.isFile()) {
            return Collections.emptyList();
        }
        long length = file.length();
        if (length < offset) {
            // Shorter than what has been consumed: the file was replaced.
            offset = 0L;
            partial.setLength(0);
            truncated = true;
            length = file.length();
        }
        if (length == offset) {
            return Collections.emptyList();
        }
        byte[] buffer = new byte[(int) Math.min(length - offset, 1 << 20)];
        int read;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            read = raf.read(buffer);
        }
        if (read <= 0) {
            return Collections.emptyList();
        }
        offset += read;
        partial.append(new String(buffer, 0, read, StandardCharsets.ISO_8859_1));

        List<String> lines = new ArrayList<String>();
        int from = 0;
        for (int i = 0; i < partial.length(); i++) {
            if (partial.charAt(i) == '\n') {
                String line = partial.substring(from, i);
                // A CRLF writer leaves the carriage return on the line.
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
                from = i + 1;
            }
        }
        partial = new StringBuilder(partial.substring(from));
        return lines;
    }
}
```

Note the 1 MB read cap: `poll` is allowed to return less than everything
available, and the next poll picks up the rest. That keeps one allocation
bounded no matter how long the reader was starved.

- [ ] **Step 4: Run the harness**

```bash
ant clean && ant compile
tools/curve-harness.sh
```

Expected: all tail checks pass.

- [ ] **Step 5: Prove three checks can fail**

One at a time, reverting after each:

1. Delete the `if (length < offset)` block. Expected red: "a shrunk file is
   read from the top again".
2. Replace the partial-line retention with `partial.setLength(0);` after the
   loop. Expected red: "the held-back row arrives once it is terminated".
3. Change `raf.seek(offset)` to `raf.seek(0)`. Expected red: "and is not
   delivered twice".

Record all three in the report.

- [ ] **Step 6: Commit**

```bash
git add src/my/stepss/curves/CurTail.java src/my/stepss/curves/CurveHarness.java
git commit -m "feat: tail the run-time .cur by offset rather than re-reading it"
```

---

## Task 4: Generalise `CurvePanel` with an axis spec

The post-analysis window overlays every curve of one extraction on one y axis
with a legend and an autoscaled x. The live window needs one panel per
observable, a fixed x over `[0, tstop]`, a title, no legend, a phase-plane
variant whose x is not time, an identity line for `RT`, and two-colour
segment shading for `LAT`. Per the spec that is one painting component with an
axis spec, not two components.

**Files:**
- Create: `src/my/stepss/curves/CurveAxes.java`
- Modify: `src/my/stepss/curves/CurveSeries.java` (optional `w`)
- Modify: `src/my/stepss/curves/CurvePanel.java` (spec, title, latency, identity)
- Modify: `src/my/stepss/plot/PlotStyle.java` (two latency entries)
- Modify: `src/my/stepss/curves/CurveHarness.java` (checks)

**Interfaces:**
- Consumes: `NiceScale` from Task 1.
- Produces:
  - `my.stepss.curves.CurveAxes` with `public static final CurveAxes POST` and `public CurveAxes(String title, String xLabel, String yLabel, double xLo, double xHi, boolean legend, boolean identity)`. `xLo`/`xHi` both `Double.NaN` means autoscale.
  - `CurvePanel.setAxes(CurveAxes axes)`.
  - `CurveSeries(String label, String unit, double[] t, double[] v, double[] w)`, the four-argument constructor retained and delegating with `w = null`.
  - `PlotStyle` entries `"latent"` and `"active"`.

- [ ] **Step 1: Write the failing checks**

Add `checkAxes()` to `CurveHarness`, called from `main`. Every check goes
through `toSvg`, which is the existing way this harness inspects a render
without a display.

```java
    private static void checkAxes() {
        CurveData one = new CurveData(java.util.Arrays.asList(
                new CurveSeries("V (pu)", "pu",
                        new double[] {0.0, 1.0, 2.0}, new double[] {1.0, 1.1, 0.9})),
                null, 0);

        CurvePanel post = new CurvePanel();
        post.setData(one);
        String postSvg = post.toSvg(800, 500);
        check("the post-analysis panel still draws a legend entry", "true",
                String.valueOf(postSvg.contains("V (pu)")));
        check("and still titles its x axis with time", "true",
                String.valueOf(postSvg.contains(">t (s)<")));

        // A fixed x range is what makes a live panel's curve grow across a
        // frame whose extent was known before the first sample arrived. With
        // x autoscaled to the data, a two-second-old run would fill the frame
        // and then appear to stop moving.
        CurvePanel live = new CurvePanel();
        live.setAxes(new CurveAxes("BUS 4044", "t (s)", "V (pu)",
                0.0, 30.0, false, false));
        live.setData(one);
        String liveSvg = live.toSvg(800, 500);
        check("a fixed x range reaches the far tick", "true",
                String.valueOf(liveSvg.contains(">30.0<")));
        check("a data-fitting tick is absent, so the range is not autoscaled",
                "false", String.valueOf(liveSvg.contains(">2.000<")));
        check("the panel title is drawn", "true",
                String.valueOf(liveSvg.contains("BUS 4044")));
        check("and the legend is suppressed", "false",
                String.valueOf(liveSvg.contains("legend")));
        // The title gets a line of its own above the y unit. Both were drawn
        // at PAD_TOP - 12 in the first draft of this task, one centred and one
        // left-anchored: that does not collide at 800px wide and does on a
        // stacked panel a third that wide.
        check("the title sits above the y unit rather than on it", "true",
                String.valueOf(textY(liveSvg, "BUS 4044")
                        < textY(liveSvg, "V (pu)")));

        // RT overlays y = x so a user sees at a glance whether the simulation
        // is keeping up with the wall clock, matching gnuplot.f90:143 which
        // plots column 1 against itself alongside the trace.
        CurvePanel rt = new CurvePanel();
        rt.setAxes(new CurveAxes("Simulated VS Real time", "simulation time (s)",
                "elapsed time (s)", 0.0, 10.0, false, true));
        rt.setData(one);
        String rtSvg = rt.toSvg(800, 500);
        // On the dash pattern, not on the group id: a group is emitted whether
        // or not anything lands inside it, so asserting on the id alone cannot
        // tell "drawn" from "opened and closed empty". Nothing else in this
        // panel calls dashedLine.
        check("the identity line is actually drawn", "true",
                String.valueOf(rtSvg.contains("stroke-dasharray")));
        check("and a panel that did not ask for one has none", "false",
                String.valueOf(postSvg.contains("stroke-dasharray")));

        // LAT's second column is a 0/1 activity flag, so a segment is drawn
        // in one of exactly two classes.
        CurveData latency = new CurveData(java.util.Arrays.asList(
                new CurveSeries("S (MVA)", "MVA",
                        new double[] {0.0, 1.0, 2.0},
                        new double[] {10.0, 11.0, 12.0},
                        new double[] {0.0, 1.0, 1.0})),
                null, 0);
        CurvePanel lat = new CurvePanel();
        lat.setAxes(new CurveAxes("Equipment: 4041", "t (s)", "S (MVA)",
                0.0, 30.0, false, false));
        lat.setData(latency);
        String latSvg = lat.toSvg(800, 500);
        check("an idle segment is drawn in the latent class", "true",
                String.valueOf(latSvg.contains("class=\"latent\"")));
        check("an active segment is drawn in the active class", "true",
                String.valueOf(latSvg.contains("class=\"active\"")));
        check("and no single-colour series class is used for it", "false",
                String.valueOf(latSvg.contains("class=\"series0\"")));
    }

    /**
     * The y coordinate of the SVG text element carrying {@code content}, or
     * NaN when there is none.
     *
     * <p>Reads the attribute rather than matching a formatted number, so a
     * check about relative position does not also pin the coordinate format.
     */
    private static double textY(String svg, String content) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<text x=\"[^\"]*\" y=\"([^\"]*)\"[^>]*>"
                        + java.util.regex.Pattern.quote(content) + "</text>").matcher(svg);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }
```

- [ ] **Step 2: Run and watch it fail to compile**

```bash
ant clean && ant compile
```

Expected: `CurveAxes` does not exist and `CurveSeries` has no five-argument
constructor.

- [ ] **Step 3: Write `CurveAxes`**

```java
package my.stepss.curves;

/**
 * What one {@link CurvePanel} draws around its curves.
 *
 * <p>The panel paints; this says what the frame means. It is the whole of the
 * difference between the post-analysis window, which overlays an extraction's
 * curves on one autoscaled axis with a legend, and a live window's stacked
 * panels, which each hold one observable over a fixed x range under a title.
 *
 * <p>Immutable, so a panel cannot be reconfigured behind a render.
 */
public final class CurveAxes {

    /**
     * The post-analysis frame: no title, time on x, autoscaled, legend on.
     *
     * <p>{@link CurvePanel} defaults to this, so the window built in step 2
     * keeps its exact appearance without passing anything.
     */
    public static final CurveAxes POST = new CurveAxes(
            "", "t (s)", "", Double.NaN, Double.NaN, true, false);

    /** Drawn top left, or not drawn when empty. */
    public final String title;
    /** The x axis caption, centred under the frame. */
    public final String xLabel;
    /**
     * The y axis caption.
     *
     * <p>Empty means "work it out from the data", which is what the
     * post-analysis window wants: it derives one from the curves' units and
     * says so when they disagree. A live panel knows its unit from the
     * observable's display type and passes it here.
     */
    public final String yLabel;
    /** The fixed x lower bound, or NaN to autoscale. */
    public final double xLo;
    /** The fixed x upper bound, or NaN to autoscale. */
    public final double xHi;
    /** Whether to list the series down the top left of the frame. */
    public final boolean legend;
    /** Whether to overlay the line y = x, which only RT wants. */
    public final boolean identity;

    public CurveAxes(String title, String xLabel, String yLabel,
            double xLo, double xHi, boolean legend, boolean identity) {
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.xLo = xLo;
        this.xHi = xHi;
        this.legend = legend;
        this.identity = identity;
    }

    /** Whether x comes from this spec rather than from the data. */
    public boolean xFixed() {
        return !Double.isNaN(xLo) && !Double.isNaN(xHi) && xHi > xLo;
    }
}
```

- [ ] **Step 4: Give `CurveSeries` an optional weight array**

Add the field, the five-argument constructor, and keep the four-argument one:

```java
    /**
     * An optional per-point auxiliary value, or null.
     *
     * <p>Only the run-time {@code LAT} display type has one: RAMSES writes a
     * second column beside the apparent power holding a 0 or a 1 for whether
     * the equipment was being solved at that step
     * (simul_decomp.f90:2312-2316), and the panel shades each segment
     * accordingly. Same length as {@link #t} when present. Same no-mutate
     * contract as {@link #t}.
     */
    public final double[] w;

    public CurveSeries(String label, String unit, double[] t, double[] v) {
        this(label, unit, t, v, null);
    }

    public CurveSeries(String label, String unit, double[] t, double[] v, double[] w) {
        if (t.length != v.length) {
            throw new IllegalArgumentException(
                    "t and v differ in length: " + t.length + " vs " + v.length);
        }
        if (w != null && w.length != t.length) {
            throw new IllegalArgumentException(
                    "w and t differ in length: " + w.length + " vs " + t.length);
        }
        this.label = label;
        this.unit = unit;
        this.t = t;
        this.v = v;
        this.w = w;
    }
```

- [ ] **Step 5: Add the two latency entries to `PlotStyle.ENTRIES`**

Append after `series7`, keeping the existing formatting:

```java
        new Entry("latent", "#0072b2", "#6cb8e6", 1.5f, null),
        new Entry("active", "#dc143c", "#ff6b83", 1.5f, null),
```

And document the pair above `ENTRIES`, beside the existing palette note:

```java
     * The last two entries are not a palette slot. gnuplot.f90:95-96 shades a
     * LAT trace through a two-stop blue-to-red palette over cbrange [0:1], and
     * the engine writes that input as a literal 0 or 1 through an i2
     * descriptor, so the "ramp" only ever takes its two end values. Two
     * classes rather than a graded set of them: a ramp would be unreachable
     * code, and every drawn hex has to be in this table for an exported figure
     * to stay restylable.
```

- [ ] **Step 6: Teach `CurvePanel` the spec**

Add the field and setter:

```java
    private CurveAxes axes = CurveAxes.POST;

    /** Replaces the frame spec and repaints. Never null. */
    public void setAxes(CurveAxes axes) {
        this.axes = axes;
        repaint();
    }
```

In `bounds`, honour a fixed x. Replace

```java
        if (zoom != null) {
```

with

```java
        if (axes.xFixed()) {
            // Before the zoom, so a rubber-band zoom still overrides it: a
            // fixed range is where the axis starts, not a refusal to look
            // closer.
            tLo = axes.xLo;
            tHi = axes.xHi;
        }
        if (zoom != null) {
```

In `render`, four changes:

1. The x axis caption comes from the spec. Replace `"t (s)"` in the
   `axis-titles` group with `axes.xLabel`.

2. The y caption prefers the spec. Replace

```java
        String unit = data.commonUnit();
```

with

```java
        String unit = axes.yLabel.isEmpty() ? data.commonUnit() : axes.yLabel;
```

3. The title, on a line of its own above the y unit. The unit is drawn
left-anchored at `PAD_TOP - 12.0` today, so a centred title at the same y
collides on a narrow panel. Give a titled panel a taller top margin instead of
sharing the line.

`PAD_TOP` becomes the untitled value and `Bounds` carries the effective one, so
an untitled panel keeps byte-identical geometry and the pinned SVG from step 2
still matches. In `Bounds`, replace every use of the constant `PAD_TOP` with a
field:

```java
        private final double padTop;
```

set from a new last constructor parameter, and used in `py` and `v` where
`PAD_TOP` appears now. Add an accessor `double padTop() { return padTop; }` so
`render` can place text against it.

In `bounds`, compute it and pass it:

```java
        // A titled panel needs a line for the title above the one the y unit
        // occupies. 18px is the label row height the legend already uses.
        double padTop = axes.title.isEmpty() ? PAD_TOP : PAD_TOP + 18.0;
        return new Bounds(x[0], x[1], y[0], y[1], xStep, yStep, width, height, padTop);
```

In `render`, the y unit and the mixed-units notice move from `PAD_TOP - 12.0`
to `b.padTop() - 12.0`, and the title goes above them:

```java
        if (!axes.title.isEmpty()) {
            sink.group("title");
            sink.text((b.px(b.xLo) + b.px(b.xHi)) / 2.0, b.padTop() - 30.0,
                    axes.title, "middle", "title");
            sink.endGroup();
        }
```

The legend's `double ly = PAD_TOP + LEGEND_ROW;` becomes `b.padTop() +
LEGEND_ROW`, which is unchanged for the untitled post-analysis panel and right
for a titled one.

4. The identity line and the latency shading, inside the existing clip so
neither paints over the furniture. Replace the body of the `curves` group's
per-series loop so that a series carrying `w` is drawn segment by segment:

```java
        if (axes.identity) {
            // y = x, so a user can see whether the simulation is keeping up
            // with the wall clock. Inside the clip because the diagonal leaves
            // the frame whenever the two axes have different extents.
            sink.group("identity");
            double lo = Math.max(b.xLo, b.yLo);
            double hi = Math.min(b.xHi, b.yHi);
            if (hi > lo) {
                sink.dashedLine(b.px(lo), b.py(lo), b.px(hi), b.py(hi), "grid");
            }
            sink.endGroup();
        }
        sink.group("curves");
        double[] xs = new double[0];
        double[] ys = new double[0];
        for (int s = 0; s < data.series.size(); s++) {
            CurveSeries one = data.series.get(s);
            if (one.t.length > xs.length) {
                xs = new double[one.t.length];
                ys = new double[one.t.length];
            }
            for (int i = 0; i < one.t.length; i++) {
                xs[i] = b.px(one.t[i]);
                ys[i] = b.py(one.v[i]);
            }
            if (one.w == null) {
                sink.polyline(xs, ys, one.t.length, PlotStyle.seriesClass(s));
            } else {
                // One element per segment rather than one per run, because the
                // class can change at every step. Only LAT takes this path and
                // only one LAT observable exists per panel, so the element
                // count is bounded by the sample count of a single curve.
                for (int i = 1; i < one.t.length; i++) {
                    sink.line(xs[i - 1], ys[i - 1], xs[i], ys[i],
                            one.w[i] >= 0.5 ? "active" : "latent");
                }
            }
        }
        sink.endGroup();
```

5. Fix the pointer hit test, which the taller margin otherwise breaks.
`readoutAt` (`CurvePanel.java:143-154`) rejects a point outside the frame by
comparing against the **constant** `PAD_TOP`, so on a titled panel the band
between `PAD_TOP` and the real frame top is accepted while sitting above the y
axis maximum: hovering the title returns a confident, wrong readout instead of
null. Compute the bounds first and hit-test against them:

```java
    public String readoutAt(int px, int py) {
        if (data.series.isEmpty()) {
            return null;
        }
        Bounds b = bounds(data, getWidth(), getHeight());
        // Against the frame's real top rather than the constant: a titled
        // panel's frame starts lower, and the band above it is not plot area.
        if (px < PAD_LEFT || px > getWidth() - PAD_RIGHT
                || py < b.padTop() || py > getHeight() - PAD_BOTTOM) {
            return null;
        }
        return String.format(Locale.ROOT, "t = %.4g, value = %.6g",
                b.t(px), b.v(py));
    }
```

This is invisible on the post-analysis panel, where the title is empty and the
two values are equal, which is why no existing check covers it: step 2's pinned
SVG guards rendering and this is hit-testing. Add a check for it beside the
others in `checkAxes()`, sizing the panel because `readoutAt` reads
`getWidth()`:

```java
        // A titled panel's frame starts below PAD_TOP, so the band between
        // them is not plot area and must report nothing.
        CurvePanel hit = new CurvePanel();
        hit.setAxes(new CurveAxes("BUS 4044", "t (s)", "V (pu)",
                0.0, 30.0, false, false));
        hit.setData(one);
        hit.setSize(800, 500);
        check("a point in the title band reads out nothing", "null",
                String.valueOf(hit.readoutAt(400, 36)));
        check("a point inside the frame still reads out", "true",
                String.valueOf(hit.readoutAt(400, 250) != null));
```

6. Gate the legend on the spec:

```java
        if (axes.legend) {
            sink.group("legend");
            ...unchanged...
            sink.endGroup();
        }
```

- [ ] **Step 7: Run both harnesses**

```bash
ant clean && ant compile
tools/curve-harness.sh
tools/plot-harness.sh
```

Expected: every check passes, **including the byte-for-byte pinned SVG from
step 2**. That check is what proves the post-analysis window is unchanged by
this generalisation. If it fails, `CurveAxes.POST` does not reproduce the old
behaviour and the difference must be found rather than the golden updated.

- [ ] **Step 8: Prove four checks can fail**

One at a time, reverting after each:

1. Delete the `axes.xFixed()` block in `bounds`. Expected red: "a fixed x
   range reaches the far tick" and "a data-fitting tick is absent".
2. Change `if (axes.legend)` to `if (true)`. Expected red: "and the legend is
   suppressed".
3. Change `one.w[i] >= 0.5` to `false`. Expected red: "an active segment is
   drawn in the active class".
4. Change `if (axes.identity)` to `if (false)`. Expected red: "the identity
   line is actually drawn".
5. Change the title's y from `b.padTop() - 30.0` to `b.padTop() - 12.0`.
   Expected red: "the title sits above the y unit rather than on it".

Record all five in the report.

- [ ] **Step 9: Commit**

```bash
git add src/my/stepss/curves/CurveAxes.java src/my/stepss/curves/CurveSeries.java \
        src/my/stepss/curves/CurvePanel.java src/my/stepss/plot/PlotStyle.java \
        src/my/stepss/curves/CurveHarness.java
git commit -m "feat: give CurvePanel an axis spec so one component serves both views"
```

---

## Task 5: Map header plus rows onto per-observable buffers

The Swing-free half of the live viewer, so it can be checked headlessly.

**Files:**
- Create: `src/my/stepss/curves/LiveModel.java`
- Modify: `src/my/stepss/curves/CurveHarness.java` (checks)

**Interfaces:**
- Consumes: `CurHeader`, `CurveAxes`, `CurveData`, `CurveSeries`.
- Produces:
  - `my.stepss.curves.LiveModel` with `public LiveModel(CurHeader header)`, `public void accept(List<String> lines)`, `public int panelCount()`, `public CurveAxes axesOf(int panel)`, `public CurveData snapshot(int panel)`, `public int skippedRows()`, `public int samples()`.
  - `public static CurveAxes axesFor(CurHeader.Obs obs, double tstop)`, exposed so the harness can check the display-type table without feeding rows.

- [ ] **Step 1: Write the failing checks**

Add `checkLiveModel()` to `CurveHarness`, called from `main`.

```java
    private static void checkLiveModel() throws CurHeader.Unsupported {
        CurHeader h = CurHeader.parse(java.util.Arrays.asList(
                "# stepss-cur 1", "# tstop 30.000", "# refresh 1.000", "# ncol 6",
                "# obs 1 2 1 BV 4044",
                "# obs 2 3 2 o-d g6",
                "# obs 3 5 1 MS g6"));
        LiveModel model = new LiveModel(h);
        check("one panel per observable", "3", String.valueOf(model.panelCount()));

        model.accept(java.util.Arrays.asList(
                " 0.000000E+00  1.010000E+00  1.000000E+00  0.100000E+00  1.000000E+00  0.0 ",
                " 1.000000E-02  1.020000E+00  1.001000E+00  0.200000E+00  1.002000E+00  0.0 "));
        check("no row is skipped when the count matches ncol", "0",
                String.valueOf(model.skippedRows()));
        check("both samples land", "2", String.valueOf(model.samples()));

        CurveData bv = model.snapshot(0);
        check("a one-column observable yields one series", "1",
                String.valueOf(bv.series.size()));
        check("its x is time", "0.01", String.valueOf(bv.series.get(0).t[1]));
        check("its y is its own column", "1.02",
                String.valueOf(bv.series.get(0).v[1]));

        // o-d is a phase plane: gnuplot.f90:147 plots varcol+1 against varcol,
        // and simul_decomp.f90:2318-2319 writes omega then delta, so x is the
        // SECOND of the pair and y is the first.
        CurveData od = model.snapshot(1);
        check("a phase-plane observable takes x from its second column", "0.2",
                String.valueOf(od.series.get(0).t[1]));
        check("and y from its first", "1.001",
                String.valueOf(od.series.get(0).v[1]));
        check("a phase plane does not fix x to the run length", "false",
                String.valueOf(model.axesOf(1).xFixed()));
        check("a time series does fix x to the run length", "true",
                String.valueOf(model.axesOf(0).xFixed()));
        check("and fixes it at tstop", "30.0",
                String.valueOf(model.axesOf(0).xHi));

        // A short row is the torn last line the writer leaves mid-flush. It is
        // counted, not drawn, and never grows a buffer.
        model.accept(java.util.Arrays.asList(" 2.000000E-02  1.03"));
        check("a short row is counted rather than drawn", "1",
                String.valueOf(model.skippedRows()));
        check("and does not extend the curves", "2",
                String.valueOf(model.samples()));

        // A re-run truncates the file, so the model must be able to forget.
        model.reset();
        check("a reset empties the buffers", "0", String.valueOf(model.samples()));
        check("but keeps the panels", "3", String.valueOf(model.panelCount()));

        // The display-type table, which is the whole of what a user reads on
        // each panel. Values from gnuplot.f90:84-133.
        check("BV titles its panel with the bus", "BUS 4044",
                LiveModel.axesFor(h.observables.get(0), 30.0).title);
        check("BV labels y in per unit volts", "V (pu)",
                LiveModel.axesFor(h.observables.get(0), 30.0).yLabel);
        check("a phase plane labels x with delta", "delta (pu)",
                LiveModel.axesFor(h.observables.get(1), 30.0).xLabel);
        check("MS titles its panel with the machine", "Machine g6",
                LiveModel.axesFor(h.observables.get(2), 30.0).title);
        check("RT asks for the identity line", "true",
                String.valueOf(LiveModel.axesFor(
                        new CurHeader.Obs(1, 2, 1, "RT", "RT", ""), 30.0).identity));
        check("SOL does not", "false",
                String.valueOf(LiveModel.axesFor(
                        new CurHeader.Obs(1, 2, 1, "SOL", "SOL", ""), 30.0).identity));
        check("a lower-case display type is read the same as upper", "BUS 4044",
                LiveModel.axesFor(
                        new CurHeader.Obs(1, 2, 1, "bv", "4044", ""), 30.0).title);
        check("an unknown display type still gets a usable frame", "t (s)",
                LiveModel.axesFor(
                        new CurHeader.Obs(1, 2, 1, "ZZZ", "x", ""), 30.0).xLabel);

        // LAT carries the activity flag through to the series, which is the
        // only reason CurveSeries has a weight array at all.
        CurHeader lat = CurHeader.parse(java.util.Arrays.asList(
                "# stepss-cur 1", "# tstop 30.000", "# refresh 1.000", "# ncol 3",
                "# obs 1 2 2 LAT 4041"));
        LiveModel latModel = new LiveModel(lat);
        latModel.accept(java.util.Arrays.asList(
                " 0.000000E+00  1.500000E+02  0 ",
                " 1.000000E-02  1.510000E+02  1 "));
        check("the latency flag reaches the series", "[0.0, 1.0]",
                java.util.Arrays.toString(latModel.snapshot(0).series.get(0).w));
        check("and the apparent power is the y value", "151.0",
                String.valueOf(latModel.snapshot(0).series.get(0).v[1]));
    }
```

`LiveModel.reset()` is used above; add it to the produced interface.

- [ ] **Step 2: Run and watch it fail to compile**

- [ ] **Step 3: Write `LiveModel`**

```java
package my.stepss.curves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The run-time observables of one simulation, as they arrive.
 *
 * <p>Owns growable buffers, one per observable, fed by whoever is tailing
 * {@code temp_display.cur}. Every buffer is private and every read produces an
 * immutable {@link CurveData} snapshot, because the thread that appends is not
 * the thread that paints: the {@link my.stepss.plot.PlotSink} backends are EDT
 * only, so what crosses over must be finished.
 *
 * <p>Copying the whole history per snapshot rather than sharing the buffers is
 * deliberate. {@code tsample} defaults to 10 ms and the interface offers three
 * observable rows, so a four-minute run holds about 24,000 samples across at
 * most three panels: about a megabyte, copied once per flush. Handing over a
 * volatile array reference and a captured length would avoid that at the cost
 * of a copy-on-grow race no headless check can demonstrate.
 *
 * <p>Not thread safe. One poller thread owns an instance.
 */
public final class LiveModel {

    /** How the columns of one row become one panel's series. */
    private static final class Panel {

        private final CurHeader.Obs obs;
        private final CurveAxes axes;
        private final boolean phasePlane;
        private final boolean weighted;
        private double[] x = new double[256];
        private double[] y = new double[256];
        private double[] w;
        private int n;

        Panel(CurHeader.Obs obs, double tstop) {
            this.obs = obs;
            this.axes = axesFor(obs, tstop);
            String type = obs.type.toUpperCase(Locale.ROOT);
            this.phasePlane = "O-D".equals(type) || "P-D".equals(type);
            this.weighted = "LAT".equals(type);
            if (weighted) {
                this.w = new double[256];
            }
        }

        void append(double[] row) {
            if (n == x.length) {
                x = Arrays.copyOf(x, n * 2);
                y = Arrays.copyOf(y, n * 2);
                if (w != null) {
                    w = Arrays.copyOf(w, n * 2);
                }
            }
            int first = obs.firstColumn - 1;
            // A phase plane plots the pair against each other: gnuplot.f90:147
            // uses varcol+1 for x and varcol for y, and the engine writes
            // omega then delta, so x is the second column of the pair.
            x[n] = phasePlane ? row[first + 1] : row[0];
            y[n] = row[first];
            if (w != null) {
                w[n] = row[first + 1];
            }
            n++;
        }

        void reset() {
            n = 0;
        }

        CurveData snapshot() {
            double[] xs = Arrays.copyOf(x, n);
            double[] ys = Arrays.copyOf(y, n);
            double[] ws = w == null ? null : Arrays.copyOf(w, n);
            String label = axes.title.isEmpty() ? obs.type : axes.title;
            return new CurveData(java.util.Collections.singletonList(
                    new CurveSeries(label, axes.yLabel, xs, ys, ws)), null, 0);
        }
    }

    private final CurHeader header;
    private final List<Panel> panels = new ArrayList<Panel>();
    private int skipped;
    private int samples;

    public LiveModel(CurHeader header) {
        this.header = header;
        for (CurHeader.Obs obs : header.observables) {
            panels.add(new Panel(obs, header.tstop));
        }
    }

    public int panelCount() {
        return panels.size();
    }

    public CurveAxes axesOf(int panel) {
        return panels.get(panel).axes;
    }

    public CurveData snapshot(int panel) {
        return panels.get(panel).snapshot();
    }

    /** How many rows could not be read, over the whole run. */
    public int skippedRows() {
        return skipped;
    }

    /** How many rows have been drawn, over the whole run. */
    public int samples() {
        return samples;
    }

    /** Throws the buffers away, keeping the panels, for a re-run's truncation. */
    public void reset() {
        for (Panel p : panels) {
            p.reset();
        }
        samples = 0;
        skipped = 0;
    }

    /**
     * Appends whatever of these lines is data.
     *
     * <p>A comment is header, already parsed. A row whose field count
     * disagrees with {@code ncol} is the torn last line the writer leaves when
     * it flushes mid-row, or damage; either way it is counted and dropped
     * rather than guessed at. A non-finite field is dropped for the reason
     * {@link CurReader} documents: it propagates through the axis arithmetic
     * and blanks the frame.
     */
    public void accept(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\s+");
            if (fields.length != header.ncol) {
                skipped++;
                continue;
            }
            double[] row = new double[header.ncol];
            boolean ok = true;
            for (int i = 0; i < header.ncol; i++) {
                try {
                    row[i] = Double.parseDouble(fields[i]);
                } catch (NumberFormatException notANumber) {
                    ok = false;
                    break;
                }
                if (!Double.isFinite(row[i])) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                skipped++;
                continue;
            }
            for (Panel p : panels) {
                p.append(row);
            }
            samples++;
        }
    }

    /**
     * The frame for one observable, from its display type.
     *
     * <p>Titles, axis captions and the identity overlay are transcribed from
     * the gnuplot script the engine used to write, stepss-ramses
     * src/io/gnuplot.f90:84-133, so a user's figures do not change meaning
     * along with their renderer. Three deliberate departures:
     *
     * <ul>
     * <li>The {@code LAT} title drops its observation window, which lives in
     * the settings file rather than in the header.</li>
     * <li>The {@code TO} x caption drops its run of padding spaces, which was
     * a hand-made centring hack for a layout this does not have.</li>
     * <li>{@code o-d} and {@code P-d} gain a title. gnuplot.f90 sets none for
     * either, and because it never unsets one either, a phase-plane panel
     * inherited whatever title the panel above it had set. That is a bug in
     * the script rather than an intended blank, so it is not reproduced.</li>
     * </ul>
     */
    public static CurveAxes axesFor(CurHeader.Obs obs, double tstop) {
        String type = obs.type.toUpperCase(Locale.ROOT);
        switch (type) {
            case "RT":
                return new CurveAxes("Simulated VS Real time",
                        "simulation time (s)", "elapsed time (s)",
                        0.0, tstop, false, true);
            case "SOL":
                return new CurveAxes("Solutions VS time", "t (s)",
                        "nb. of inj. solutions", 0.0, tstop, false, false);
            case "LAT":
                return new CurveAxes("Equipment: " + obs.name, "t (s)",
                        "S (MVA)", 0.0, tstop, false, false);
            case "O-D":
                return new CurveAxes("Machine " + obs.name, "delta (pu)",
                        "omega (pu)", Double.NaN, Double.NaN, false, false);
            case "P-D":
                return new CurveAxes("Machine " + obs.name, "delta (pu)",
                        "P (MW)", Double.NaN, Double.NaN, false, false);
            case "BV":
                return new CurveAxes("BUS " + obs.name, "t (s)", "V (pu)",
                        0.0, tstop, false, false);
            case "BPO":
            case "BPE":
                return new CurveAxes("BRANCH " + obs.name, "t (s)", "P (MW)",
                        0.0, tstop, false, false);
            case "BQO":
            case "BQE":
                return new CurveAxes("BRANCH " + obs.name, "t (s)", "Q (MVAr)",
                        0.0, tstop, false, false);
            case "MS":
            case "COI":
                return new CurveAxes("Machine " + obs.name, "t (s)",
                        "Omega (pu)", 0.0, tstop, false, false);
            case "ON":
                return new CurveAxes("Injector " + obs.name + " Observable "
                        + obs.name2, "t (s)", "", 0.0, tstop, false, false);
            case "TO":
                return new CurveAxes("TWOP " + obs.name + " Observable "
                        + obs.name2, "t (s)", obs.name2, 0.0, tstop, false, false);
            default:
                // gnuplot.f90's own else branch: an untitled frame over time,
                // which is better than refusing to draw a type the engine
                // accepted.
                return new CurveAxes("", "t (s)", "", 0.0, tstop, false, false);
        }
    }
}
```

- [ ] **Step 4: Run the harness**

```bash
ant clean && ant compile
tools/curve-harness.sh
```

- [ ] **Step 5: Prove four checks can fail**

One at a time, reverting after each:

1. In `Panel.append`, change `phasePlane ? row[first + 1] : row[0]` to
   `row[0]`. Expected red: "a phase-plane observable takes x from its second
   column".
2. In `axesFor`, delete the `case "O-D"` arm so it falls through to the
   default. Expected red: "a phase plane labels x with delta" and "a phase
   plane does not fix x to the run length".
3. In `accept`, change `fields.length != header.ncol` to `false`. Expected
   red: "a short row is counted rather than drawn". Note what else goes red,
   which tells you whether the check is narrow enough.
4. Change `this.weighted = "LAT".equals(type)` to `false`. Expected red: "the
   latency flag reaches the series".

Record all four in the report.

- [ ] **Step 6: Commit**

```bash
git add src/my/stepss/curves/LiveModel.java src/my/stepss/curves/CurveHarness.java
git commit -m "feat: map run-time .cur columns onto per-observable buffers"
```

---

## Task 6: The live window

**Files:**
- Create: `src/my/stepss/curves/LiveCurveWindow.java`
- Modify: `src/my/stepss/curves/CurveHarness.java` (one check)

**Interfaces:**
- Consumes: `CurHeader`, `CurTail`, `LiveModel`, `CurvePanel`, `CurveAxes`, `my.stepss.WindowCascade`.
- Produces: `my.stepss.curves.LiveCurveWindow` with `public static LiveCurveWindow open(Component parent, File cur, String title)`, `public void finish()`, `public void close()`.

- [ ] **Step 1: Write the one check that does not need a display**

The window itself needs a display, so what is checked headlessly is the poll
interval derivation, which is the one piece of arithmetic in the class.

```java
    private static void checkLiveWindow() {
        check("the poll interval comes from the header's refresh rate", "1000",
                String.valueOf(LiveCurveWindow.pollMillis(1.0)));
        check("a slower flush is polled more slowly", "5000",
                String.valueOf(LiveCurveWindow.pollMillis(5.0)));
        // A settings file may set any number, including one that would spin
        // the poller. Floor it rather than trusting the file.
        check("an absurd refresh rate is floored", "100",
                String.valueOf(LiveCurveWindow.pollMillis(0.0)));
        check("a negative refresh rate is floored too", "100",
                String.valueOf(LiveCurveWindow.pollMillis(-3.0)));

        // Whether a failed header parse is a verdict or impatience. The engine
        // issues its header as several writes and flushes once at the end, so
        // a poll can land on a partial header; calling that an unsupported
        // engine accuses the user of something that is not true.
        check("a header still arriving is not a refusal", "false",
                String.valueOf(LiveCurveWindow.headerFailureIsFinal(
                        java.util.Arrays.asList("# stepss-cur 1", "# tstop 30.000"))));
        check("nothing read yet is not a refusal either", "false",
                String.valueOf(LiveCurveWindow.headerFailureIsFinal(
                        java.util.Collections.<String>emptyList())));
        check("blank lines do not end the header", "false",
                String.valueOf(LiveCurveWindow.headerFailureIsFinal(
                        java.util.Arrays.asList("# stepss-cur 1", "", "   "))));
        // A data row proves no more header is coming, so now it is a verdict.
        check("a data row after a bad header is a refusal", "true",
                String.valueOf(LiveCurveWindow.headerFailureIsFinal(
                        java.util.Arrays.asList("# stepss-cur 1", " 0.0 1.0"))));
        check("a file that starts with data refuses at once", "true",
                String.valueOf(LiveCurveWindow.headerFailureIsFinal(
                        java.util.Arrays.asList(" 0.000000E+00  1.012404E+00 "))));
    }
```

That last case is the one that matters for the currently pinned engine: it
writes no header at all, so its first line is data, `headerFailureIsFinal`
returns true immediately, and the user gets the real explanation on the first
poll rather than after a timeout.

- [ ] **Step 2: Write `LiveCurveWindow`**

```java
package my.stepss.curves;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Run-time observables, drawn as the engine writes them.
 *
 * <p>The engine no longer plots: it writes {@code temp_display.cur} and
 * flushes on the {@code $GP_REFRESH_RATE} cadence. This tails that file on a
 * scheduled thread, parses there, and hands the EDT finished snapshots.
 *
 * <p>One window per run. A new run opens a new one and leaves the previous one
 * behind as a static chart, which is what makes two runs comparable. At the end
 * of a run the window stops polling and stays open: the live view becomes the
 * post-run view of the same observables.
 */
public final class LiveCurveWindow extends JFrame {

    /** Never poll faster than this, whatever the settings file says. */
    private static final long MIN_POLL_MS = 100L;

    private final File cur;
    private final CurTail tail;
    private final JPanel stack = new JPanel();
    private final JLabel status = new JLabel(" ");
    private final List<CurvePanel> panels = new ArrayList<CurvePanel>();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread t = new Thread(runnable, "live-curve-poller");
                t.setDaemon(true);
                return t;
            });

    private LiveModel model;
    private String refusal;
    private int emptyPolls;
    private boolean built;
    private boolean stopped;
    /**
     * Lines read before the header could be parsed.
     *
     * <p>The engine issues the header as several separate Fortran writes and
     * flushes once at the end, so a poll can land on a file holding only part
     * of it. Deciding from one batch would let a startup race present as
     * "this engine wrote no run-time header", which accuses the user of
     * running an old engine. Lines accumulate here until something proves the
     * header complete, and the data rows among them are replayed once it is.
     */
    private final List<String> pending = new ArrayList<String>();

    /**
     * @param cur the run's temp_display.cur, which need not exist yet
     * @return the window, so the caller can tell it the run has ended
     */
    public static LiveCurveWindow open(Component parent, File cur, String title) {
        LiveCurveWindow window = new LiveCurveWindow(cur, title);
        my.stepss.WindowCascade.track(window, parent);
        window.setVisible(true);
        window.start();
        return window;
    }

    private LiveCurveWindow(File cur, String title) {
        super(title);
        this.cur = cur;
        this.tail = new CurTail(cur);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        stack.setLayout(new GridLayout(0, 1));
        status.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        add(stack, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setSize(720, 560);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                poller.shutdownNow();
            }
        });
    }

    /** The poll period for a header refresh rate in seconds. */
    static long pollMillis(double refreshSeconds) {
        long ms = Math.round(refreshSeconds * 1000.0);
        return Math.max(MIN_POLL_MS, ms);
    }

    /**
     * Whether a failed header parse over {@code seen} is a verdict or just
     * impatience.
     *
     * <p>The header is contiguous and comes first, so any line that is neither
     * blank nor a comment proves no more header is coming and a failure is
     * real. Until one arrives, a failure means the poll landed between the
     * engine's header writes and its single flush, which is a race rather than
     * a fault and must not be reported as an unsupported engine.
     *
     * <p>Package-private and static so it can be checked without a file, a
     * poller or a display.
     */
    static boolean headerFailureIsFinal(List<String> seen) {
        for (String line : seen) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return true;
            }
        }
        return false;
    }

    private void start() {
        // One second until the header says otherwise, which is also the
        // engine's own default, so the common case never reschedules.
        poller.scheduleWithFixedDelay(this::tick, 0L, 1000L, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops polling after one last read, and leaves the window open.
     *
     * <p>The final read happens after the process has exited so the last flush
     * lands: the engine writes up to the stop time and flushes on exit, and a
     * poller shut down on the exit signal alone loses whatever arrived since
     * the previous tick.
     */
    public void finish() {
        poller.execute(() -> {
            tick();
            stopped = true;
            poller.shutdown();
            SwingUtilities.invokeLater(() -> setTitle(getTitle() + " (finished)"));
        });
    }

    /** Disposes the window and its poller. */
    public void close() {
        poller.shutdownNow();
        dispose();
    }

    /**
     * One poll, on the poller thread. Never touches Swing except through
     * {@code invokeLater}.
     */
    private void tick() {
        if (stopped || refusal != null) {
            return;
        }
        try {
            List<String> lines = tail.poll();
            if (tail.truncatedSinceLastPoll()) {
                // status='replace' from a re-run in the same directory. The
                // header is about to arrive again, so throw the old run away
                // rather than drawing the two on one axis. The panels stay:
                // a re-run in the same window cannot have a different
                // observable set, because Task 7 gives every run its own
                // window and stops this one's poller before that.
                pending.clear();
                if (model != null) {
                    model.reset();
                }
            }
            if (lines.isEmpty()) {
                if (model == null && ++emptyPolls == 5) {
                    say("The engine has not written any run-time observable data yet.");
                }
                return;
            }
            if (model == null) {
                pending.addAll(lines);
                try {
                    model = new LiveModel(CurHeader.parse(pending));
                } catch (CurHeader.Unsupported unsupported) {
                    // Before the first data row this means "the header is not
                    // all here yet", which is a startup race rather than a
                    // fault: the engine issues the header as several writes and
                    // flushes once at the end. Refusing here would tell the
                    // user their engine is too old on the strength of a poll
                    // that landed mid-write. After a data row there is no more
                    // header coming, so the same failure is real.
                    if (headerFailureIsFinal(pending)) {
                        refuse(unsupported.getMessage());
                    }
                    return;
                }
                SwingUtilities.invokeLater(this::buildPanels);
                // The data rows that arrived while the header was still
                // incomplete, replayed in order so nothing is lost.
                lines = new ArrayList<String>(pending);
                pending.clear();
            }
            model.accept(lines);
            final List<CurveData> snapshots = new ArrayList<CurveData>();
            for (int i = 0; i < model.panelCount(); i++) {
                snapshots.add(model.snapshot(i));
            }
            final int samples = model.samples();
            final int skipped = model.skippedRows();
            SwingUtilities.invokeLater(() -> publish(snapshots, samples, skipped));
        } catch (IOException ex) {
            refuse("temp_display.cur could not be read: " + ex.getMessage());
        }
    }

    /** On the EDT: one panel per observable, once the header is known. */
    private void buildPanels() {
        if (built) {
            return;
        }
        built = true;
        for (int i = 0; i < model.panelCount(); i++) {
            CurvePanel panel = new CurvePanel();
            panel.setAxes(model.axesOf(i));
            panels.add(panel);
            stack.add(panel);
        }
        stack.revalidate();
    }

    /** On the EDT: the snapshots the poller finished. */
    private void publish(List<CurveData> snapshots, int samples, int skipped) {
        buildPanels();
        for (int i = 0; i < panels.size() && i < snapshots.size(); i++) {
            panels.get(i).setData(snapshots.get(i));
        }
        StringBuilder text = new StringBuilder();
        text.append(samples).append(" samples");
        // Once, at the end of the line, rather than a per-row storm: a torn
        // last line is normal and would otherwise warn every second.
        if (skipped > 0) {
            text.append("    ").append(skipped).append(" unreadable row(s) skipped");
        }
        status.setText(text.toString());
    }

    private void refuse(String why) {
        refusal = why;
        SwingUtilities.invokeLater(() -> {
            stack.removeAll();
            JLabel message = new JLabel("<html><body style='width:420px'>"
                    + escape(why) + "</body></html>");
            message.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            message.setVerticalAlignment(javax.swing.SwingConstants.TOP);
            stack.add(message);
            stack.revalidate();
            stack.repaint();
            status.setText(cur.getAbsolutePath());
        });
        poller.shutdown();
    }

    private void say(String text) {
        SwingUtilities.invokeLater(() -> status.setText(text));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 3: Run the harness**

```bash
ant clean && ant compile
tools/curve-harness.sh
```

- [ ] **Step 4: Prove three checks can fail**

One at a time, reverting after each:

1. Change `Math.max(MIN_POLL_MS, ms)` to `ms`. Expected red: "an absurd refresh
   rate is floored".
2. In `headerFailureIsFinal`, change the loop body to `return true;`
   unconditionally. Expected red: "a header still arriving is not a refusal"
   and "blank lines do not end the header".
3. In `headerFailureIsFinal`, drop the `!trimmed.isEmpty()` term. Expected red:
   "blank lines do not end the header" alone, which is what tells you that
   check is narrower than the one above it.

Record all three in the report.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/curves/LiveCurveWindow.java src/my/stepss/curves/CurveHarness.java
git commit -m "feat: draw run-time observables natively as the engine writes them"
```

---

## Task 7: Wire the live window into the run, and move the curve actions

Everything here is in `StepssUI`, and the gnuplot preview button goes in this
task rather than Task 8 because Task 8 deletes the field it reads.

**Files:**
- Modify: `src/my/stepss/StepssUI.java`
- Modify: `src/my/stepss/StepssUI.form`

**Interfaces:**
- Consumes: `LiveCurveWindow.open`, `finish`, `close`.
- Produces: nothing for later tasks.

- [ ] **Step 1: Add the live-window field and its bookkeeping**

Beside `lastExtractionBase` (`:6794`):

```java
    /**
     * Every curve window this session has opened, live and post-analysis, so
     * "Close all curve windows" can close them.
     *
     * <p>A list rather than a single reference because a new run leaves the
     * previous run's window open as a frozen chart, which is what makes two
     * runs comparable.
     */
    private final java.util.List<java.awt.Window> curveWindows =
            new java.util.ArrayList<java.awt.Window>();

    /** The window tailing the run in progress, or null between runs. */
    private my.stepss.curves.LiveCurveWindow liveCurves;
```

- [ ] **Step 2: Open the window at run start**

In `runSimulationActionPerformed`, immediately after the `try` block that calls
`simulExecutor.execute(...)` succeeds, that is after
`stopSimulationButton.setEnabled(true);` and before the watcher thread:

```java
        // One window per run, opened whether or not an observable is
        // configured: with none, the engine writes no .cur and the window says
        // so, which is more useful than a run that silently plots nothing.
        if (anyRuntimeObservable()) {
            File runtimeCur = new File(myTempDir, "temp_display.cur");
            liveCurves = my.stepss.curves.LiveCurveWindow.open(this, runtimeCur,
                    "Run-time curves");
            curveWindows.add(liveCurves);
        }
```

And the predicate, beside `writeObservable`:

```java
    /**
     * Whether any run-time observable row is filled in.
     *
     * <p>The same three rows and the same "blank means unused" rule
     * {@link #writeObservable} applies, including its exception: Wall Time and
     * Solutions name no equipment, so a selected row of either counts as
     * configured with an empty name field.
     */
    private boolean anyRuntimeObservable() {
        JComboBox[] types = {runtimeObsType, runtimeObsType1, runtimeObsType2};
        JTextField[] names = {runtimeObsName, runtimeObsName1, runtimeObsName2};
        for (int i = 0; i < types.length; i++) {
            String label = String.valueOf(types[i].getSelectedItem());
            if (WALL_TIME.equals(label) || SOLUTIONS.equals(label)) {
                return true;
            }
            if (!names[i].getText().isEmpty() && OBSERVABLE_TYPES.containsKey(label)) {
                return true;
            }
        }
        return false;
    }
```

- [ ] **Step 3: Tell the window the run has ended**

In the same method's watcher thread, the loop already waits for
`.lock_RAMSES` to disappear. After the loop and **before** the
`SwingUtilities.invokeLater`, add:

```java
                // After the lock has gone, so the engine's final flush has
                // landed. LiveCurveWindow.finish does one last read on its own
                // thread rather than here.
                if (liveCurves != null) {
                    liveCurves.finish();
                    liveCurves = null;
                }
```

- [ ] **Step 4: Track the post-analysis windows too**

In `openCurveWindow` (`:4745`), the `CurveWindow.open` call currently returns
nothing. Change `CurveWindow.open` to return the window it made:

```java
    public static CurveWindow open(Component parent, CurveData data, String title) {
        CurveWindow window = new CurveWindow(data, title);
        my.stepss.WindowCascade.track(window, parent);
        window.setVisible(true);
        return window;
    }
```

and in `openCurveWindow`, add the result to `curveWindows`.

- [ ] **Step 5: Rename the kill-gnuplot control**

Three places, and the `.form` beside them.

`StepssUI.java:2254-2256`:

```java
        clearGnuplotButton.setText("Close all curve windows");
        clearGnuplotButton.setToolTipText("Closes every run-time and extracted-curve window this session opened.");
        clearGnuplotButton.setName("closeCurveWindowsButton"); // NOI18N
```

`StepssUI.java:2691`:

```java
        killAllGnupMenuItem.setText("Close all curve windows");
```

The handler at `:4467-4472` becomes:

```java
    private void clearGnuplotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearGnuplotButtonActionPerformed
        for (java.awt.Window window : curveWindows) {
            window.dispose();
        }
        curveWindows.clear();
        liveCurves = null;
    }//GEN-LAST:event_clearGnuplotButtonActionPerformed
```

Leave the field and method names as they are: renaming a NetBeans generated
widget means editing matched `//GEN-` guarded blocks in two files, and the
user-visible text is what this task owns. Note it in the report as a deliberate
inconsistency rather than an oversight.

In `StepssUI.form`, change both `value="Clear all gnuplot instances"`
occurrences (`:341`, `:1804`) to `value="Close all curve windows"` and the
tooltip at `:1805` to match the Java above.

- [ ] **Step 6: Delete the gnuplot preview button, and move saving into the window**

`viewCurvesButton` launches gnuplot on the `.plt`, which the invariant forbids
and Task 8 makes impossible. Delete its handler
(`viewCurvesButtonActionPerformed`, `:4514-4535`), its two `setEnabled` calls
in `startPlotRun`, its `setEnabled(true)` in the extraction callback, its
declaration, its `initComponents` block (`:2234-2241`), and its entries in the
two layout groups. Do the same in `StepssUI.form`. The `.cur` now opens in a
native window automatically, so a preview button has nothing left to do.

`saveCurrentCurveButton` keeps its handler: it copies this window's `.plt`,
`.cur` and `.png` out of the temporary directory, which is the "Save the
gnuplot pair" the spec keeps deliberately. Retitle it so it says what it does:

```java
        saveCurrentCurveButton.setText("Save gnuplot files");
        saveCurrentCurveButton.setToolTipText("Saves the extracted .cur and .plt, for opening in gnuplot yourself.");
```

and the same two strings in `StepssUI.form`.

- [ ] **Step 7: Strip the pass-through instructions from the three tooltips**

At `:1788`, `:1813` and `:1819` the run-time observable name tooltips end with
instructions for passing gnuplot commands through. Delete only that trailing
sentence from each; keep every example. Do the same in the three `.form`
tooltips at `:1198`, `:1239`, `:1251`. The `gnup_cmds` pass-through still
reaches the `.plt`, so this is about not advertising a gnuplot feature in a
window that no longer runs gnuplot.

- [ ] **Step 8: Build, and run the harnesses that cover this file**

```bash
ant clean && ant jar
tools/curve-harness.sh
tools/plot-harness.sh
tools/observables-harness.sh
tools/compile-harness.sh
```

Expected: all pass. `compile-harness.sh` still expects gnuplot in the Windows
extraction order at this point, which is correct: Task 8 moves it.

- [ ] **Step 9: Commit**

```bash
git add src/my/stepss/StepssUI.java src/my/stepss/StepssUI.form \
        src/my/stepss/curves/CurveWindow.java
git commit -m "feat: open a live curve window per run and retire the gnuplot preview"
```

---

## Task 8: Delete gnuplot

One commit. The three build guards fail loudly until they move with it, which
is their purpose, so splitting this leaves the tree red in between.

**Files:**
- Delete: `src/my/stepss/gpwin.zip`, `src/my/stepss/gnuplotLicense.txt`
- Modify: `src/my/stepss/platform/Toolchain.java`, `src/my/stepss/platform/PlatformLauncher.java`, `src/my/stepss/platform/PayloadManifestCheck.java`, `src/my/stepss/compile/CompileHarness.java`, `src/my/stepss/dyngraph/DyngraphRunner.java`, `src/my/stepss/StepssUI.java`, `src/my/stepss/StepssUI.form`, `build.xml`, `packaging/linux/copyright`, `tools/deb-harness.sh`, `versions.properties`

- [ ] **Step 1: The toolchain entry**

In `Toolchain.java` delete the `GNUPLOT` constant (`:16`), the `s.add(new
ToolSpec(GNUPLOT)...)` block (`:79-81`) and the `gnuplot()` method
(`:243-251`) with its Javadoc.

- [ ] **Step 2: The PATH plumbing**

In `PlatformLauncher.java` delete `execEnvironment` (`:461-482`) with its
Javadoc, and the now-unused `EnvironmentUtils` import if nothing else uses it.

In `StepssUI.java` delete the `WinEnvironment` field (`:6836`) and the line
that assigns it (`:7181`), and drop the argument from all six calls:

- `:4527` `exec.execute(command, WinEnvironment, resultHandler)` becomes
  `exec.execute(command, resultHandler)`
- `:5059`, `:5527`, `:6095` `simulExecutor.execute(command, WinEnvironment,
  handler)` becomes `simulExecutor.execute(command, handler)`
- `:7078` `exec.execute(command, WinEnvironment)` becomes
  `exec.execute(command)`
- `:4562` `new DyngraphRunner(dyngraphExec, myTempDir, WinEnvironment)` becomes
  `new DyngraphRunner(dyngraphExec, myTempDir)`

In `DyngraphRunner.java` drop the `environment` parameter, the field, its
`@param` line, and pass nothing at `:94` (`executor.execute(cmd)`) and `:143`
(`executor.execute(cmd, handler)`). Update the class Javadoc where it mentions
`PlatformLauncher.execEnvironment` (`:62`).

`:4527` sat inside `viewCurvesButtonActionPerformed`, which Task 7 already
deleted, so that call site will not be there. If it is, Task 7 was incomplete
and that is the finding, not this line.

- [ ] **Step 3: The startup probe and the banner**

In `StepssUI.java` delete `gnuplotExec` (`:6785`), `gnuplotMissingWarned`
(`:6810-6811`), `gnuplotInstallHint()` (`:7016-7025`), the
`gnuplotExec = toolchain.gnuplot();` assignment (`:7180`) and the whole
`if ((gnuplotExec == null || ...))` banner block (`:7188-7193`). Fix the
comment at `:3946-3947` that describes the banner's ordering against the
update check: it is now about the update check alone.

- [ ] **Step 4: The About button**

Delete `showGnupCopyrightButton` entirely: `setText` (`:1221`), its
declaration, its `initComponents` block, its layout entries, its handler and
the licence extraction it performs (`:3988-3998`), plus the matching `.form`
entry at `:127`. Then delete `src/my/stepss/gnuplotLicense.txt`.

- [ ] **Step 5: The payload**

```bash
git rm src/my/stepss/gpwin.zip src/my/stepss/gnuplotLicense.txt
```

- [ ] **Step 6: The three guards, in this same commit**

`CompileHarness.java:459-461`:

```java
        expect("windows extraction order", "[ramses, helios, dyngraph, codegen]",
                Toolchain.extractionOrder(Platform.WINDOWS_X86_64).toString());
```

and drop "and because gnuplot ships on Windows only while uramses is
deliberately lazy on all three" from the Javadoc above it, leaving the uramses
half.

`PayloadManifestCheck.java:118-126`: with no payload committed outside
`payload/`, the `!payload.resource.startsWith(PAYLOAD_PREFIX)` branch can no
longer be reached by a real spec. Keep the guard, because a future RAW payload
must not crash the check, but rewrite the comment so it describes a rule rather
than a member that no longer exists:

```java
                if (payload == null || !payload.resource.startsWith(PAYLOAD_PREFIX)) {
                    // A payload committed directly under src/my/stepss/ rather
                    // than fetched into src/my/stepss/payload/ is out of scope
                    // for this check. None exists today; gpwin.zip was the last
                    // one.
                    continue;
                }
```

`build.xml:518`:

```xml
            value="libgfortran5, libgomp1, libopenblas0, libgcc-s1"/>
```

and delete the comment above it (`:510-516`) that explains the gnuplot
alternation.

- [ ] **Step 7: The packaging metadata**

In `packaging/linux/copyright` delete the whole gnuplot stanza, from
`Comment: gnuplot is bundled for Windows only.` through the `Verbatim text:
Help > About > gnuplot in the application.` line (`:116-136`).

In `tools/deb-harness.sh` delete the `=== gnuplot came with it ===` block
(`:48-52`) and remove `"gnuplot-x11 | gnuplot-qt"` from the `for dep in` list
(`:116`).

In `versions.properties` remove `gpwin.zip` from the comment at `:98`, naming
the helios assets alone.

- [ ] **Step 8: Confirm nothing is left**

```bash
grep -rniI 'gnuplot\|gpwin' --exclude-dir=.git --exclude=README.md \
    --exclude-dir=docs . | grep -v '^./src/my/stepss/curves/CurReader.java'
```

Expected output: the `DyngraphRunner.java:104` line about leaving a `.plt` a
user can open in gnuplot, and nothing else. Both that and the `CurReader`
exclusion are references to the format, which stays. Anything else is a miss.

- [ ] **Step 9: Full build and every harness**

```bash
ant clean && ant jar
tools/curve-harness.sh
tools/plot-harness.sh
tools/compile-harness.sh
tools/observables-harness.sh
tools/picker-harness.sh
tools/examples-harness.sh
tools/scenario-harness.sh
tools/update-harness.sh
tools/url-harness.sh
tools/ssa-harness.sh
tools/diagram-harness.sh
```

Expected: all pass. `compile-harness.sh` is the one that proves the extraction
order and the payload manifest moved together.

- [ ] **Step 10: Confirm the jar actually shrank**

```bash
ls -l dist/stepss.jar
unzip -l dist/stepss.jar | grep -c gpwin
```

Expected: zero matches, and a jar about 9 MB smaller than the previous build.
Record both numbers in the report: the payload leaving is the headline of this
release and an assertion without a number is not evidence.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: remove the bundled gnuplot and every path that ran it"
```

---

## Task 9: Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Say gnuplot is gone**

Work through every gnuplot mention the README still has. The step 2 pass
already narrowed "bundles gnuplot" to Windows; that sentence now goes
entirely. Each of these must be true after the edit:

- The bundled-library lists no longer name gnuplot, and the payload table no
  longer carries `gpwin.zip`.
- The Linux packaging section no longer says the `.deb` depends on
  `gnuplot-x11 | gnuplot-qt`.
- Result extraction describes the native window: an extraction opens a curve
  window of its own, cascaded, with PNG, SVG and CSV export, and *Save gnuplot
  files* for the `.cur` and `.plt` pair.
- Run-time observables describe the live window: it opens with the run, tails
  the engine's `.cur` at the flush cadence, stacks one panel per observable,
  and stays open as a static chart when the run ends.
- No installation step anywhere asks a user to install gnuplot.

- [ ] **Step 2: Record the engine floor**

The live window needs an engine that publishes the column map, and
`versions.properties` still pins one that does not. Add this to the run-time
observables section, in prose rather than as a warning box, matching the
README's voice:

> Run-time curves need a RAMSES release that writes the column map into its
> `.cur`. Against an older engine the window says so rather than guessing which
> column is which observable.

- [ ] **Step 3: Check it against the guideline**

The README was corrected against the stepss-docs guideline in `b03f6dd`. Keep
what that pass established: the licence section stays a per-component table
pointing at `getting-started/license`, menu paths use `→`, and no section
claims STEPSS as a whole is Apache 2.0.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: gnuplot is gone, and the curve windows are how you plot"
```

---

## Self-Review

**Spec coverage.** Every *Live viewer* numbered point maps to a task: window
per run (7), `ScheduledExecutorService` (6), tail by offset with reset on shrink
(3), `invokeLater` handoff (5, 6), panels including the three phase-plane and
latency exceptions (4, 5), end of run after exit (6, 7). All three *Failure
modes, all loud* are in Task 2 (`Unsupported` for a missing or unknown header),
Task 5 (`ncol` mismatch counted) and Task 6 (the "no data yet" message after
five empty polls). *The chart component*'s one bounded refactor is Task 1 plus
step 2's promotion, already merged. Every line of the *Deletion inventory* is in
Task 8 except the two the earlier tasks must own: the preview button in Task 7,
because Task 8 deletes the field it reads, and the README in Task 9.

**Not covered, deliberately.** The spec's *Documentation* list is mostly other
repositories, out of this plan's scope by its own Global Constraints. Issue #19
closes on a Windows run of the live viewer, which needs a Windows machine and a
released engine; that is a release-time step, not an implementation step.

**Type consistency.** `CurveAxes` is constructed with the same seven-argument
signature in Tasks 4, 5 and 6. `CurveSeries`' five-argument constructor is
introduced in Task 4 and used in Task 5. `NiceScale.step`/`bounds` are named
consistently after Task 1. `LiveModel.reset()` appears in the Task 5 checks and
in its produced interface. `CurveWindow.open` gains a return type in Task 7,
which is the only signature this plan changes on step 2's code.

**One risk worth naming.** Task 4 changes the component that step 2's pinned
SVG check covers. That check is the plan's own guard against regressing the
post-analysis window, and `CurveAxes.POST` exists precisely so the default path
is unchanged. If it fails, the difference is a bug in this task, not a stale
golden.
