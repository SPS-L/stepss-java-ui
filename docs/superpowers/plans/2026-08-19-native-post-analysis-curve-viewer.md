# Native Post-Analysis Curve Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Draw the curves DYNGRAPH extracts in a native Swing window, one window per extraction, without gnuplot.

**Architecture:** Promote the existing `PlotSink`/`PlotStyle` abstraction out of `my.stepss.ssa` into a shared `my.stepss.plot` package and give it a polyline primitive and a categorical colour cycle. Add `my.stepss.curves`, which parses DYNGRAPH's `.cur` into series, paints them through the same sink (so screen and exported SVG cannot drift), and hosts them in a cascaded non-modal window. Nothing in the RAMSES engine changes, and gnuplot stays bundled with **View curves** still working throughout.

**Tech Stack:** Java 11, Swing, FlatLaf (theme), Ant. No new third-party jars. PNG through `javax.imageio`, SVG through the repository's own `SvgSink`.

**Spec:** `docs/superpowers/specs/2026-08-19-remove-gnuplot-dependency-design.md` (this plan implements release-sequence **step 2** only)

## Global Constraints

- **Java source/target is 11** (`nbproject/project.properties:60-61`). No `var`, no records, no switch expressions.
- **No new third-party jar.** Anything added to `dist/lib` must also be merged in `build.xml`'s `-post-jar`, and the standalone `java -jar dist/stepss.jar` must keep working. This plan adds none.
- **No unit-test framework exists.** Tests are headless `main()` classes plus `tools/*-harness.sh`, following `tools/ssa-harness.sh` exactly.
- **Exports always use the light palette**, whatever theme the application wears (`PlotStyle.EXPORT_BACKGROUND`, and the reasoning in `SvgSink`'s class comment).
- **On-screen panels follow the theme** by measuring background luminance (`PlotStyle.isDark`), re-resolved in `updateUI()` because a LAF switch leaves an explicitly set background alone.
- **Do not touch the RAMSES engine, DYNGRAPH, or the gnuplot payload in this step.** **View curves** and **Save extracted curve** must still work when this step ships.
- **Package-private stays package-private unless a task widens it deliberately.** The promotion in Task 1 is the only widening.
- Styling is carried as a **class name**, never a colour, so an exported SVG stays restylable by editing one CSS rule.

---

### Task 1: Promote the plot sink into `my.stepss.plot`

Pure refactor. The four classes are package-private inside `my.stepss.ssa`, so a new package cannot reach them; duplicating them or filing curve code under `ssa` are the alternatives and both are worse.

**Files:**
- Create: `src/my/stepss/plot/PlotSink.java` (moved from `src/my/stepss/ssa/PlotSink.java`)
- Create: `src/my/stepss/plot/PlotStyle.java` (moved from `src/my/stepss/ssa/PlotStyle.java`)
- Create: `src/my/stepss/plot/SwingSink.java` (moved from `src/my/stepss/ssa/SwingSink.java`)
- Create: `src/my/stepss/plot/SvgSink.java` (moved from `src/my/stepss/ssa/SvgSink.java`)
- Delete: the four originals under `src/my/stepss/ssa/`
- Modify: `src/my/stepss/ssa/SplanePanel.java` (imports)
- Modify: `src/my/stepss/ssa/ModeShapePanel.java` (imports)
- Modify: `src/my/stepss/ssa/SsaResultsWindow.java` (imports — uses `PlotStyle.isDark` at `:353`)
- Modify: `src/my/stepss/ssa/SsaHarness.java` (imports — 17 `new SvgSink(...)`, plus `PlotStyle.Entry`, `ENTRIES` and `of`)
- Test: `tools/ssa-harness.sh` (existing, unchanged — it is the regression proof)

**Four files need imports, not two.** All four use these types by simple name
today, which is legal only while they share the package. `SsaHarness` is the
one that matters most: it is the class `tools/ssa-harness.sh` runs, so without
its import this task has no regression proof at all. The operative rule is to
add an import wherever the compiler needs one inside `my.stepss.ssa`.

**Interfaces:**
- Consumes: nothing.
- Produces: `public interface my.stepss.plot.PlotSink`; `public final class my.stepss.plot.PlotStyle` with `public static final Entry[] ENTRIES`, `public static Entry of(String)`, `public static Color color(String)`, `public static boolean isDark(Color)`, `public static final String EXPORT_BACKGROUND`, and `public static final class Entry` whose fields `cls`, `lightHex`, `darkHex`, `width`, `fontPx` and method `hex(boolean)` are all public; `public final class my.stepss.plot.SwingSink implements PlotSink` with `public SwingSink(Graphics2D, boolean)`; `public final class my.stepss.plot.SvgSink implements PlotSink` with `public SvgSink(int, int)` and `public String toSvg()`.

- [ ] **Step 1: Run the existing harness to record the green baseline**

```bash
ant compile
tools/ssa-harness.sh
```

Expected: PASS. Record the exact final line; Step 6 must reproduce it.

- [ ] **Step 2: Move the four files and rewrite their package declaration**

```bash
mkdir -p src/my/stepss/plot
git mv src/my/stepss/ssa/PlotSink.java  src/my/stepss/plot/PlotSink.java
git mv src/my/stepss/ssa/PlotStyle.java src/my/stepss/plot/PlotStyle.java
git mv src/my/stepss/ssa/SwingSink.java src/my/stepss/plot/SwingSink.java
git mv src/my/stepss/ssa/SvgSink.java   src/my/stepss/plot/SvgSink.java
sed -i 's/^package my\.stepss\.ssa;/package my.stepss.plot;/' src/my/stepss/plot/*.java
```

- [ ] **Step 3: Widen the four types and the members `my.stepss.ssa` uses**

In `src/my/stepss/plot/PlotSink.java`, change the declaration only:

```java
public interface PlotSink {
```

In `src/my/stepss/plot/PlotStyle.java`:

```java
public final class PlotStyle {

    public static final class Entry {
        public final String cls;
        public final String lightHex;
        public final String darkHex;
        public final float width;
        public final Integer fontPx;

        Entry(String cls, String lightHex, String darkHex, float width, Integer fontPx) {
            this.cls = cls;
            this.lightHex = lightHex;
            this.darkHex = darkHex;
            this.width = width;
            this.fontPx = fontPx;
        }

        /** This class's colour on the ground in use. */
        public String hex(boolean dark) {
            return dark ? darkHex : lightHex;
        }
    }
```

and widen the four statics, leaving their bodies and comments exactly as they are:

```java
    public static final Entry[] ENTRIES = {
```
```java
    public static final String EXPORT_BACKGROUND = "#ffffff";
```
```java
    public static Entry of(String cls) {
```
```java
    public static Color color(String hex) {
```
```java
    public static boolean isDark(Color background) {
```

In `src/my/stepss/plot/SwingSink.java`:

```java
public final class SwingSink implements PlotSink {
```
```java
    public SwingSink(Graphics2D g, boolean dark) {
```

In `src/my/stepss/plot/SvgSink.java`:

```java
public final class SvgSink implements PlotSink {
```
```java
    public SvgSink(int width, int height) {
```
```java
    public String toSvg() {
```

The `Entry` constructor stays package-private: only the `ENTRIES` table builds one, and it lives in the same file.

- [ ] **Step 4: Add the imports the ssa package now needs**

`SsaResultsWindow.java` needs `import my.stepss.plot.PlotStyle;` and
`SsaHarness.java` needs `import my.stepss.plot.SvgSink;` and
`import my.stepss.plot.PlotStyle;`.

In `src/my/stepss/ssa/SplanePanel.java` and `src/my/stepss/ssa/ModeShapePanel.java`, in alphabetical position, which for `my.stepss.plot` means **after** the `java.util.*` and `javax.swing.*` imports rather than after the `java.awt.*` block. Both files sort their imports strictly today and should keep doing so:

```java
import my.stepss.plot.PlotSink;
import my.stepss.plot.PlotStyle;
import my.stepss.plot.SvgSink;
import my.stepss.plot.SwingSink;
```

Both files reference all four (`render(PlotSink ...)`, `PlotStyle.isDark`, `new SvgSink(...)`, `new SwingSink(...)`). If javac reports an unused import in either file, delete that one import from that file rather than leaving it.

- [ ] **Step 5: Compile and fix what the compiler names**

Run: `ant compile`
Expected: BUILD SUCCESSFUL. Any `PlotSink is not public` or `cannot find symbol` message names a member Step 3 missed; widen exactly that member and rerun. Do not widen anything the compiler has not asked for.

- [ ] **Step 6: Run the harness and compare against the baseline**

Run: `tools/ssa-harness.sh`
Expected: PASS, with the same final line recorded in Step 1. A pure move that changes harness output is not a pure move.

- [ ] **Step 7: Commit**

```bash
git add -A src/my/stepss/plot src/my/stepss/ssa
git commit -m "refactor: move the plot sink into my.stepss.plot so two subsystems can share it"
```

---

### Task 2: Add the polyline primitive

A time series is thousands of points. `PlotSink` today has only `line`, so drawing one curve as segments would emit one `<line>` element per sample and make the exported SVG unusable. One primitive fixes that for both backends.

**Files:**
- Modify: `src/my/stepss/plot/PlotSink.java`
- Modify: `src/my/stepss/plot/SwingSink.java`
- Modify: `src/my/stepss/plot/SvgSink.java`
- Create: `src/my/stepss/plot/PlotHarness.java`
- Create: `tools/plot-harness.sh`

**Interfaces:**
- Consumes: Task 1's `my.stepss.plot` package.
- Produces: `void polyline(double[] xs, double[] ys, int n, String cls)` on `PlotSink`, implemented by both sinks. `n` is the count of leading entries to use, so a caller may pass growable arrays with spare capacity. `public final class my.stepss.plot.PlotHarness` with `public static void main(String[])`, exit 0 on pass and 1 on failure.

- [ ] **Step 1: Write the failing harness**

Create `src/my/stepss/plot/PlotHarness.java`:

```java
package my.stepss.plot;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Headless checks for the shared plot sink. This repository has no unit-test
 * framework and is not gaining one, so this main() is where the sink's
 * contract is pinned.
 *
 * <p>The Swing backend is exercised against a BufferedImage rather than a real
 * component, which is what makes it runnable with no display.
 */
public final class PlotHarness {

    private static int failures;

    public static void main(String[] args) {
        polylineEmitsOneSvgElement();
        polylineHonoursCount();
        polylineOnGraphicsDoesNotThrow();
        polylineWithNoPointsIsIgnored();

        if (failures > 0) {
            System.err.println(failures + " plot check(s) FAILED");
            System.exit(1);
        }
        System.out.println("plot harness OK");
    }

    /** One polyline is one element, not one element per segment. */
    private static void polylineEmitsOneSvgElement() {
        SvgSink sink = new SvgSink(200, 100);
        sink.polyline(new double[] {0, 1, 2, 3}, new double[] {9, 8, 7, 6}, 4, "series0");
        String svg = sink.toSvg();
        check("one <polyline> element", 1, count(svg, "<polyline"));
        check("no <line> elements", 0, count(svg, "<line"));
        check("carries the class", true, svg.contains("class=\"series0\""));
        check("carries every point", true,
                svg.contains("0.00,9.00 1.00,8.00 2.00,7.00 3.00,6.00"));
    }

    /** n bounds the points used, so spare array capacity is not drawn. */
    private static void polylineHonoursCount() {
        SvgSink sink = new SvgSink(200, 100);
        sink.polyline(new double[] {0, 1, 99, 99}, new double[] {5, 4, 99, 99}, 2, "series1");
        String svg = sink.toSvg();
        check("stops at n", true, svg.contains("points=\"0.00,5.00 1.00,4.00\""));
        check("ignores the tail", false, svg.contains("99.00"));
    }

    private static void polylineOnGraphicsDoesNotThrow() {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            new SwingSink(g, false).polyline(
                    new double[] {0, 40, 79}, new double[] {0, 30, 59}, 3, "series0");
            check("SwingSink.polyline completes", true, true);
        } catch (RuntimeException ex) {
            check("SwingSink.polyline completes", true, false);
        } finally {
            g.dispose();
        }
    }

    /** A series with nothing in it must not emit a degenerate element. */
    private static void polylineWithNoPointsIsIgnored() {
        SvgSink sink = new SvgSink(200, 100);
        sink.polyline(new double[0], new double[0], 0, "series0");
        check("no element for an empty series", 0, count(sink.toSvg(), "<polyline"));
    }

    private static int count(String haystack, String needle) {
        int total = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            total++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return total;
    }

    private static void check(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            System.err.println("FAIL " + what + ": expected " + expected
                    + ", got " + actual);
            failures++;
        }
    }
}
```

Create `tools/plot-harness.sh`:

```bash
#!/usr/bin/env bash
# Runs the headless shared-plot-sink checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/ssa-harness.sh, dist/lib is NOT on the classpath: my.stepss.plot
# depends on nothing beyond the JDK, so build/classes alone is enough.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.stepss.plot.PlotHarness
```

```bash
chmod +x tools/plot-harness.sh
```

- [ ] **Step 2: Run it to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: method polyline(double[],double[],int,String)`.

- [ ] **Step 3: Add the primitive to the interface**

In `src/my/stepss/plot/PlotSink.java`, after `circle`:

```java
    /**
     * One connected run of points as a single element, which is what keeps an
     * exported time series a few kilobytes rather than one element per sample.
     *
     * @param n how many leading entries of xs and ys to use, so a caller may
     *     pass growable arrays that have spare capacity
     */
    void polyline(double[] xs, double[] ys, int n, String cls);
```

- [ ] **Step 4: Implement it on the Swing backend**

In `src/my/stepss/plot/SwingSink.java`, add the import:

```java
import java.awt.geom.Path2D;
```

and the method after `circle`:

```java
    @Override
    public void polyline(double[] xs, double[] ys, int n, String cls) {
        if (n < 2) {
            return;
        }
        style(cls, false);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            path.lineTo(xs[i], ys[i]);
        }
        g.draw(path);
    }
```

- [ ] **Step 5: Implement it on the SVG backend**

In `src/my/stepss/plot/SvgSink.java`, after `circle`:

```java
    @Override
    public void polyline(double[] xs, double[] ys, int n, String cls) {
        if (n < 2) {
            return;
        }
        body.append("    <polyline points=\"");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                body.append(' ');
            }
            body.append(fmt(xs[i])).append(',').append(fmt(ys[i]));
        }
        body.append("\" class=\"").append(escape(cls)).append("\"/>\n");
    }
```

- [ ] **Step 6: Run the harness and the ssa regression**

Run: `ant compile && tools/plot-harness.sh && tools/ssa-harness.sh`
Expected: `plot harness OK`, then the ssa harness PASS. The ssa run proves the widened interface did not disturb the two existing panels.

- [ ] **Step 7: Commit**

```bash
git add src/my/stepss/plot tools/plot-harness.sh
git commit -m "feat: add a polyline primitive to the plot sink"
```

---

### Task 3: Categorical series colours

`PlotStyle.ENTRIES` holds one light/dark pair per semantic class and has no cycle, so N overlaid curves have no colours to take. The palette is Okabe-Ito, which is colourblind-safe, with each dark-ground variant lightened from its own light value so the two themes say the same thing.

**Files:**
- Modify: `src/my/stepss/plot/PlotStyle.java`
- Modify: `src/my/stepss/plot/PlotHarness.java`

**Interfaces:**
- Consumes: Task 1's `PlotStyle`.
- Produces: `public static final int SERIES_COLOURS` (value 8) and `public static String seriesClass(int index)` on `PlotStyle`, returning `"series0"` through `"series7"` and wrapping by modulo. Eight new `ENTRIES` rows named `series0`..`series7`.

- [ ] **Step 1: Write the failing checks**

In `src/my/stepss/plot/PlotHarness.java`, add the calls to `main` after the existing four:

```java
        seriesClassesResolve();
        seriesClassesWrap();
        seriesClassesReachTheSvgStyleBlock();
```

and the methods before `count`:

```java
    /** Every cycle slot must be a real entry, not the axis fallback. */
    private static void seriesClassesResolve() {
        for (int i = 0; i < PlotStyle.SERIES_COLOURS; i++) {
            String cls = PlotStyle.seriesClass(i);
            check("seriesClass(" + i + ") names itself", "series" + i, cls);
            check(cls + " is a real entry", cls, PlotStyle.of(cls).cls);
            check(cls + " differs light and dark", false,
                    PlotStyle.of(cls).lightHex.equals(PlotStyle.of(cls).darkHex));
        }
    }

    private static void seriesClassesWrap() {
        check("wraps past the end", "series0",
                PlotStyle.seriesClass(PlotStyle.SERIES_COLOURS));
        check("wraps twice round", "series1",
                PlotStyle.seriesClass(PlotStyle.SERIES_COLOURS * 2 + 1));
        // The two above pass under either operator, because `%` and floorMod
        // agree on non-negative operands, so neither guards the regression
        // this method exists to catch. The next one does: `-1 % 8` is -1,
        // giving "series-1", which of() would resolve silently to the axis
        // colour. The one after it pins the wrap direction at an exact
        // multiple, where the two operators do agree.
        check("wraps a negative index", "series" + (PlotStyle.SERIES_COLOURS - 1),
                PlotStyle.seriesClass(-1));
        check("wraps a negative index past the end", "series0",
                PlotStyle.seriesClass(-PlotStyle.SERIES_COLOURS));
    }

    /** The style block is generated from ENTRIES, so every class must appear. */
    private static void seriesClassesReachTheSvgStyleBlock() {
        String svg = new SvgSink(10, 10).toSvg();
        for (int i = 0; i < PlotStyle.SERIES_COLOURS; i++) {
            check("style block declares series" + i, true,
                    svg.contains(".series" + i));
        }
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: variable SERIES_COLOURS`.

- [ ] **Step 3: Add the palette and the accessor**

In `src/my/stepss/plot/PlotStyle.java`, add eight rows at the end of the `ENTRIES` initialiser, after the `title` row:

```java
        new Entry("series0", "#0072b2", "#6cb8e6", 1.5f, null),
        new Entry("series1", "#d55e00", "#f59457", 1.5f, null),
        new Entry("series2", "#009e73", "#4fd1a8", 1.5f, null),
        new Entry("series3", "#cc79a7", "#e2a6c6", 1.5f, null),
        new Entry("series4", "#e69f00", "#f2c14e", 1.5f, null),
        new Entry("series5", "#56b4e9", "#9ad4f2", 1.5f, null),
        new Entry("series6", "#8b4513", "#c58a5e", 1.5f, null),
        new Entry("series7", "#6a3d9a", "#b18ad6", 1.5f, null),
```

and after `EXPORT_BACKGROUND`:

```java
    /**
     * How many distinct curve colours exist before the cycle repeats.
     *
     * <p>Okabe-Ito, which is distinguishable under the common forms of colour
     * blindness. Its yellow and black are deliberately not here: yellow is
     * illegible on the light ground exports always use, and black is what the
     * axis furniture is drawn in, so a curve wearing it would read as part of
     * the frame.
     */
    public static final int SERIES_COLOURS = 8;

    /**
     * The style class for curve {@code index}, wrapping when an extraction has
     * more curves than the palette has colours. Wrapping rather than
     * generating a colour keeps every drawn hex inside {@link #ENTRIES}, which
     * is what makes an exported figure restylable by editing one rule.
     */
    public static String seriesClass(int index) {
        return "series" + Math.floorMod(index, SERIES_COLOURS);
    }
```

`Math.floorMod` rather than `%` so a negative index cannot produce `series-1`, which would silently fall through `of()` to the axis colour.

- [ ] **Step 4: Run the harness**

Run: `ant compile && tools/plot-harness.sh`
Expected: `plot harness OK`.

- [ ] **Step 5: Confirm the ssa panels are unaffected**

Run: `tools/ssa-harness.sh`
Expected: PASS. `of()` is a linear scan over `ENTRIES`, so appending rows must not change what existing class names resolve to.

- [ ] **Step 6: Commit**

```bash
git add src/my/stepss/plot
git commit -m "feat: add a colourblind-safe series colour cycle to PlotStyle"
```

---

### Task 4: Parse DYNGRAPH's `.cur`

The data contract, and the one place a format detail can silently corrupt a plot. Format, from `stepss-dyngraph/src/extract.f90:63-67`: no header, `E15.7E3` fields, whitespace separated, column 1 is time, then one column per selection **in selection order**, and **every line ends with a literal ` ;`**.

**Files:**
- Create: `src/my/stepss/curves/CurveSeries.java`
- Create: `src/my/stepss/curves/CurveData.java`
- Create: `src/my/stepss/curves/CurReader.java`
- Create: `src/my/stepss/curves/CurveHarness.java`
- Create: `tools/curve-harness.sh`

**Interfaces:**
- Consumes: nothing outside the JDK.
- Produces:
  - `public final class CurveSeries` with public final fields `String label`, `String unit`, `double[] t`, `double[] v`, constructor `CurveSeries(String label, String unit, double[] t, double[] v)`, and `public static String unitOf(String label)`.
  - `public final class CurveData` with public final fields `java.util.List<CurveSeries> series`, `File source`, `int skippedRows`, and constructor `CurveData(List<CurveSeries>, File, int)`.
  - `public final class CurReader` with `public static CurveData read(File cur, java.util.List<String> labels) throws IOException` and `public static CurveData parse(java.util.List<String> lines, File source, java.util.List<String> labels)`.
  - `public final class CurveHarness` with `public static void main(String[])`.

- [ ] **Step 1: Write the failing harness**

Create `src/my/stepss/curves/CurveHarness.java`:

```java
package my.stepss.curves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Headless checks for the curve pipeline: .cur text -> series. This
 * repository has no unit-test framework and is not gaining one, so this
 * main() is where the reader and the plot geometry are pinned; the window
 * itself is covered by manual acceptance.
 *
 * <p>Fixtures are string literals rather than data files, for the reason
 * SsaHarness gives: the traps here are invisible in a text file. The trailing
 * " ;" that ends every DYNGRAPH data line, and the three-digit exponent in
 * E15.7E3, both survive a quoted Java literal and do not survive most
 * editors.
 */
public final class CurveHarness {

    private static int failures;

    /**
     * The first three rows of stepss-dyngraph/tests/golden/smoke.cur, verbatim:
     * four observables, so five columns, each line closed with " ;".
     */
    private static final List<String> SMOKE = Arrays.asList(
        " 0.0000000E+000  0.1000000E+001  0.3000000E+001  0.1700000E+002  0.8000000E+001  ;",
        " 0.2500000E+000  0.1250000E+001  0.3250000E+001  0.1725000E+002  0.8250000E+001  ;",
        " 0.5000000E+000  0.1500000E+001  0.3500000E+001  0.1750000E+002  0.8500000E+001  ;");

    private static final List<String> SMOKE_LABELS = Arrays.asList(
        "bus BUS1: voltage magnitude (pu)",
        "bus BUS2: voltage magnitude (pu)",
        "sync mach GEN1: rotor speed (pu)",
        "branch BR1-2: P (MW) entering at FROM end");

    public static void main(String[] args) {
        readsEveryColumn();
        stripsTheTrailingSemicolon();
        parsesThreeDigitExponents();
        derivesUnitsFromLabels();
        skipsShortRows();
        skipsNonNumericRows();
        skipsBlankLines();
        toleratesTheDegenerateTimeOnlyFile();

        if (failures > 0) {
            System.err.println(failures + " curve check(s) FAILED");
            System.exit(1);
        }
        System.out.println("curve harness OK");
    }

    private static void readsEveryColumn() {
        CurveData data = CurReader.parse(SMOKE, null, SMOKE_LABELS);
        check("one series per label", 4, data.series.size());
        check("every row kept", 0, data.skippedRows);
        check("series 0 has every sample", 3, data.series.get(0).v.length);
        check("time column is shared", 0.5, data.series.get(3).t[2]);
        check("column 2 is series 0", 1.5, data.series.get(0).v[2]);
        check("column 5 is series 3", 8.5, data.series.get(3).v[2]);
        check("labels are carried through", SMOKE_LABELS.get(2),
                data.series.get(2).label);
    }

    /** The " ;" terminator is data if it is not removed, and breaks the row. */
    private static void stripsTheTrailingSemicolon() {
        CurveData data = CurReader.parse(
                Arrays.asList(" 0.0000000E+000  0.1000000E+001  ;"),
                null, Arrays.asList("one (pu)"));
        check("semicolon does not become a column", 0, data.skippedRows);
        check("value parsed", 1.0, data.series.get(0).v[0]);
    }

    /** E15.7E3 writes a three-digit exponent; E+001 is not E+01. */
    private static void parsesThreeDigitExponents() {
        CurveData data = CurReader.parse(
                Arrays.asList(" 0.1000000E+001 -0.2500000E-002  ;"),
                null, Arrays.asList("x (pu)"));
        check("time exponent", 1.0, data.series.get(0).t[0]);
        check("negative value with negative exponent", -0.0025,
                data.series.get(0).v[0]);
    }

    private static void derivesUnitsFromLabels() {
        check("trailing parenthesis is the unit", "pu",
                CurveSeries.unitOf("bus BUS1: voltage magnitude (pu)"));
        check("unit with a space", "MW",
                CurveSeries.unitOf("branch BR1-2: P (MW) entering at FROM end"));
        check("no parenthesis means no unit", "",
                CurveSeries.unitOf("DCTL relay1: state"));
        check("empty parentheses mean no unit", "",
                CurveSeries.unitOf("odd ()"));
    }

    /** A torn or truncated row is dropped and counted, never half-read. */
    private static void skipsShortRows() {
        List<String> lines = new ArrayList<String>(SMOKE);
        lines.add(" 0.7500000E+000  0.1750000E+001  ;");
        CurveData data = CurReader.parse(lines, null, SMOKE_LABELS);
        check("short row counted", 1, data.skippedRows);
        check("short row not stored", 3, data.series.get(0).v.length);
    }

    private static void skipsNonNumericRows() {
        List<String> lines = new ArrayList<String>(SMOKE);
        lines.add(" 0.7500000E+000  NaNsense  0.1E+001  0.1E+001  0.1E+001  ;");
        CurveData data = CurReader.parse(lines, null, SMOKE_LABELS);
        check("unparseable row counted", 1, data.skippedRows);
        check("unparseable row not stored", 3, data.series.get(0).v.length);
    }

    /** Blank lines are not rows and must not inflate the skipped count. */
    private static void skipsBlankLines() {
        List<String> lines = new ArrayList<String>(SMOKE);
        lines.add("");
        lines.add("   ");
        CurveData data = CurReader.parse(lines, null, SMOKE_LABELS);
        check("blank lines are not skipped rows", 0, data.skippedRows);
        check("blank lines add no samples", 3, data.series.get(0).v.length);
    }

    /**
     * stepss-dyngraph/tests/golden/empty.cur is a time column and " ;" and
     * nothing else, which the picker can produce by selecting nothing.
     */
    private static void toleratesTheDegenerateTimeOnlyFile() {
        CurveData data = CurReader.parse(
                Arrays.asList(" 0.0000000E+000  ;"), null,
                new ArrayList<String>());
        check("no series", 0, data.series.size());
        check("no rows skipped", 0, data.skippedRows);
    }

    private static void check(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            System.err.println("FAIL " + what + ": expected " + expected
                    + ", got " + actual);
            failures++;
        }
    }
}
```

Create `tools/curve-harness.sh`:

```bash
#!/usr/bin/env bash
# Runs the headless curve-viewer checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/ssa-harness.sh, dist/lib is NOT on the classpath: my.stepss.curves
# depends on my.stepss.plot and the JDK and on nothing that launches a process,
# so build/classes alone is enough to load it.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.stepss.curves.CurveHarness
```

```bash
chmod +x tools/curve-harness.sh
```

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `package my.stepss.curves does not exist` / `cannot find symbol: class CurReader`.

- [ ] **Step 3: Write `CurveSeries`**

Create `src/my/stepss/curves/CurveSeries.java`:

```java
package my.stepss.curves;

/**
 * One extracted curve: its DYNGRAPH label, the unit read out of that label,
 * and the samples.
 *
 * <p>The arrays are exactly as long as the number of rows that parsed, so a
 * consumer can draw {@code v.length} points without carrying a count.
 */
public final class CurveSeries {

    /** The desc_obs-format label, as {@code Selection.label()} composes it. */
    public final String label;
    /** The unit from the label's last parenthesised group, or "" if it has none. */
    public final String unit;
    public final double[] t;
    public final double[] v;

    public CurveSeries(String label, String unit, double[] t, double[] v) {
        if (t.length != v.length) {
            throw new IllegalArgumentException(
                    "t and v differ in length: " + t.length + " vs " + v.length);
        }
        this.label = label;
        this.unit = unit;
        this.t = t;
        this.v = v;
    }

    /**
     * The unit out of a DYNGRAPH label.
     *
     * <p>The labels come from stepss-dyngraph/src/obstypes.f90 through
     * {@code ObservableIndex}, and carry their unit parenthesised: "voltage
     * magnitude (pu)", "P (MW) entering at FROM end". Reading it back out of
     * the label is the only source available, because the picker does not
     * carry units separately.
     *
     * <p>The FIRST parenthesised group, not the last: "P (MW) entering at FROM
     * end" puts the unit in the middle, and no label in obstypes.f90 has a
     * second group. A label with no group, or an empty one, has no unit.
     */
    public static String unitOf(String label) {
        int open = label.indexOf('(');
        if (open < 0) {
            return "";
        }
        int close = label.indexOf(')', open + 1);
        if (close < 0) {
            return "";
        }
        return label.substring(open + 1, close).trim();
    }
}
```

- [ ] **Step 4: Write `CurveData`**

Create `src/my/stepss/curves/CurveData.java`:

```java
package my.stepss.curves;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * One extraction, parsed. Held in memory by the window that shows it, so a
 * later extraction overwriting the files on disk leaves this one intact,
 * which is the same reason SsaResultsWindow keeps its own parsed copy.
 */
public final class CurveData {

    public final List<CurveSeries> series;
    /** The .cur this was read from, or null when parsed from literals. */
    public final File source;
    /**
     * Rows dropped because their field count disagreed with the label count,
     * or because a field would not parse. Surfaced once by the window rather
     * than per row: a malformed file produces one complaint, not thousands.
     */
    public final int skippedRows;

    public CurveData(List<CurveSeries> series, File source, int skippedRows) {
        this.series = Collections.unmodifiableList(series);
        this.source = source;
        this.skippedRows = skippedRows;
    }

    /** How many distinct non-empty units the curves carry. */
    public int distinctUnits() {
        java.util.Set<String> units = new java.util.HashSet<String>();
        for (CurveSeries one : series) {
            if (!one.unit.isEmpty()) {
                units.add(one.unit);
            }
        }
        return units.size();
    }

    /** The shared unit when every curve agrees on one, else "". */
    public String commonUnit() {
        if (series.isEmpty()) {
            return "";
        }
        String first = series.get(0).unit;
        for (CurveSeries one : series) {
            if (!one.unit.equals(first)) {
                return "";
            }
        }
        return first;
    }
}
```

- [ ] **Step 5: Write `CurReader`**

Create `src/my/stepss/curves/CurReader.java`:

```java
package my.stepss.curves;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the data file DYNGRAPH writes beside its .plt.
 *
 * <p>Format, from stepss-dyngraph/src/extract.f90:63-67: no header; one row
 * per time step written as {@code 20000(E15.7E3,1x)} followed by {@code ' ;'};
 * column 1 is time and column i+1 is selection i, because selection order IS
 * column order (stepss-java-ui ReplayFile:76-79, and caption.f90:35-40
 * generates the .plt from the same ordering).
 *
 * <p>The .plt sitting beside it is never read. It is a gnuplot program rather
 * than a data format, it embeds arbitrary user-authored gnuplot, and the
 * caller already knows every label; see the design spec's invariant.
 */
public final class CurReader {

    private CurReader() {
    }

    /**
     * @param cur the file DYNGRAPH wrote
     * @param labels one label per selection, in selection order
     */
    public static CurveData read(File cur, List<String> labels) throws IOException {
        List<String> lines = Files.readAllLines(cur.toPath(), StandardCharsets.ISO_8859_1);
        return parse(lines, cur, labels);
    }

    /**
     * The parse, split out so the harness can drive it from string literals.
     *
     * <p>ISO-8859-1 above rather than UTF-8 for the same reason ReplayFile
     * uses it: these are Fortran-written bytes, and a byte outside UTF-8's
     * valid sequences would otherwise be replaced rather than read.
     */
    public static CurveData parse(List<String> lines, File source, List<String> labels) {
        int columns = labels.size() + 1;
        List<double[]> rows = new ArrayList<double[]>();
        int skipped = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            // The row terminator, not a field. Written unconditionally by
            // extract.f90, including on the degenerate time-only file.
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\\s+");
            if (fields.length != columns) {
                skipped++;
                continue;
            }
            double[] row = new double[columns];
            boolean ok = true;
            for (int i = 0; i < columns; i++) {
                try {
                    row[i] = Double.parseDouble(fields[i]);
                } catch (NumberFormatException notANumber) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                rows.add(row);
            } else {
                skipped++;
            }
        }

        List<CurveSeries> series = new ArrayList<CurveSeries>();
        for (int s = 0; s < labels.size(); s++) {
            double[] t = new double[rows.size()];
            double[] v = new double[rows.size()];
            for (int r = 0; r < rows.size(); r++) {
                t[r] = rows.get(r)[0];
                v[r] = rows.get(r)[s + 1];
            }
            String label = labels.get(s);
            series.add(new CurveSeries(label, CurveSeries.unitOf(label), t, v));
        }
        return new CurveData(series, source, skipped);
    }
}
```

- [ ] **Step 6: Run the harness**

Run: `ant compile && tools/curve-harness.sh`
Expected: `curve harness OK`.

If `derivesUnitsFromLabels` fails on the `MW` case, the implementation took the last parenthesised group rather than the first; `unitOf` uses `indexOf`, not `lastIndexOf`, and the comment says why.

- [ ] **Step 7: Verify against a real DYNGRAPH file if one is to hand**

```bash
ls stepss-dyngraph/tests/golden/*.cur 2>/dev/null || echo "no sibling checkout; skip"
```

If present, compare the first data line of `smoke.cur` against the `SMOKE` literal above character for character. A mismatch means the fixture is wrong and the harness is testing fiction.

- [ ] **Step 8: Commit**

```bash
git add src/my/stepss/curves tools/curve-harness.sh
git commit -m "feat: read DYNGRAPH .cur files into curve series"
```

---

### Task 5: `CurvePanel`, geometry and rendering

Follows `SplanePanel` exactly: a `render` that takes a `PlotSink`, a `paintComponent` that hands it a `SwingSink`, a `toSvg` that hands it an `SvgSink`, and an `updateUI` that re-resolves the ground.

**Files:**
- Create: `src/my/stepss/curves/CurvePanel.java`
- Modify: `src/my/stepss/curves/CurveHarness.java`

**Interfaces:**
- Consumes: Task 2's `polyline`, Task 3's `PlotStyle.seriesClass`, Task 4's `CurveData`/`CurveSeries`.
- Produces: `public final class CurvePanel extends javax.swing.JPanel` with `public CurvePanel()`, `public void setData(CurveData data)`, `public String toSvg(int width, int height)`, `public java.awt.image.BufferedImage toPng(int width, int height)`; and the statics the harness drives: `public static double niceStep(double span, int targetTicks)`, `public static double[] niceBounds(double lo, double hi, double step)`.

- [ ] **Step 1: Write the failing geometry checks**

In `src/my/stepss/curves/CurveHarness.java`, add to `main`:

```java
        niceStepPicksTheOneTwoFiveLadder();
        niceBoundsRoundOutward();
        rendersOnePolylinePerSeries();
        namesTheSharedUnitAndFlagsMixedOnes();
```

and the methods before `check`:

```java
    /** Ticks land on 1, 2 or 5 times a power of ten, never on 3.7. */
    private static void niceStepPicksTheOneTwoFiveLadder() {
        check("span 1 into 5", 0.2, CurvePanel.niceStep(1.0, 5));
        // 30/5 is 6, which is above 5 on the ladder, so it rounds up to 10.
        check("span 30 into 5", 10.0, CurvePanel.niceStep(30.0, 5));
        check("span 0.004 into 4", 0.001, CurvePanel.niceStep(0.004, 4));
        check("span 7000 into 5", 2000.0, CurvePanel.niceStep(7000.0, 5));
        // A flat curve has zero span and must still yield a usable step
        // rather than 0, which would divide by zero when placing ticks.
        check("zero span", 1.0, CurvePanel.niceStep(0.0, 5));
    }

    private static void niceBoundsRoundOutward() {
        double[] b = CurvePanel.niceBounds(0.83, 1.04, 0.05);
        check("low rounds down", 0.80, round(b[0]));
        check("high rounds up", 1.05, round(b[1]));
        double[] flat = CurvePanel.niceBounds(1.0, 1.0, 1.0);
        check("flat data still spans", true, flat[1] > flat[0]);
    }

    private static void rendersOnePolylinePerSeries() {
        CurvePanel panel = new CurvePanel();
        panel.setData(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        String svg = panel.toSvg(600, 400);
        check("one polyline per curve", 4, count(svg, "<polyline"));
        for (int i = 0; i < 4; i++) {
            check("curve " + i + " wears its cycle colour", true,
                    svg.contains("class=\"series" + i + "\""));
        }
        check("legend names a curve", true, svg.contains("rotor speed"));
        check("x axis is labelled", true, svg.contains("t (s)"));
    }

    private static void namesTheSharedUnitAndFlagsMixedOnes() {
        CurveData mixed = CurReader.parse(SMOKE, null, SMOKE_LABELS);
        check("smoke fixture mixes pu and MW", 2, mixed.distinctUnits());
        check("no common unit", "", mixed.commonUnit());
        CurvePanel panel = new CurvePanel();
        panel.setData(mixed);
        check("mixed units are called out", true,
                panel.toSvg(600, 400).contains("mixed units"));

        List<String> pu = Arrays.asList(
            "bus BUS1: voltage magnitude (pu)",
            "bus BUS2: voltage magnitude (pu)");
        CurveData same = CurReader.parse(
                Arrays.asList(" 0.0E+000  0.1E+001  0.1E+001  ;"), null, pu);
        check("one unit is the common one", "pu", same.commonUnit());
        CurvePanel plain = new CurvePanel();
        plain.setData(same);
        check("no warning when units agree", false,
                plain.toSvg(600, 400).contains("mixed units"));
    }

    private static double round(double value) {
        return Math.round(value * 1e6) / 1e6;
    }

    private static int count(String haystack, String needle) {
        int total = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            total++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return total;
    }
```

Add the import `java.util.Arrays` is already present; no new import is needed.

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class CurvePanel`.

- [ ] **Step 3: Write `CurvePanel`**

Create `src/my/stepss/curves/CurvePanel.java`:

```java
package my.stepss.curves;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.swing.JPanel;
import javax.swing.UIManager;
import my.stepss.plot.PlotSink;
import my.stepss.plot.PlotStyle;
import my.stepss.plot.SvgSink;
import my.stepss.plot.SwingSink;

/**
 * One extraction's curves, all overlaid on a single y axis with a legend,
 * which is what caption.f90 draws today (one plot, N curves, "set key
 * opaque").
 *
 * <p>Painted through {@link PlotSink} rather than against Graphics2D, so the
 * exported SVG is produced by the code that painted the screen and cannot
 * drift from it. This is the same arrangement SplanePanel uses.
 */
public final class CurvePanel extends JPanel {

    private static final int PAD_LEFT = 70;
    private static final int PAD_RIGHT = 20;
    private static final int PAD_TOP = 30;
    private static final int PAD_BOTTOM = 50;
    private static final double TICK_LEN = 4.0;
    private static final int X_TICKS = 6;
    private static final int Y_TICKS = 5;
    /** Room for one legend row, and the gap above the first. */
    private static final double LEGEND_ROW = 14.0;

    private CurveData data = new CurveData(
            new java.util.ArrayList<CurveSeries>(), null, 0);

    public CurvePanel() {
        setPreferredSize(new Dimension(760, 460));
    }

    /**
     * The plot ground, re-resolved on every look-and-feel change because that
     * is what the theme toggle triggers. Set here rather than in the
     * constructor: an explicitly set background is exactly what a LAF switch
     * leaves alone, which is how the ssa panels once stayed white in a dark
     * window.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        Color ground = UIManager.getColor("Table.background");
        setBackground(ground != null ? ground : Color.WHITE);
    }

    public void setData(CurveData data) {
        this.data = data;
        repaint();
    }

    public CurveData data() {
        return data;
    }

    public String toSvg(int width, int height) {
        SvgSink sink = new SvgSink(width, height);
        render(sink, data, width, height);
        return sink.toSvg();
    }

    /**
     * The same figure as a raster image, on the light ground exports use.
     * Drawn with dark=false for the reason SvgSink's comment gives: a saved
     * figure should not come out inverted because of what the application
     * happened to be wearing.
     */
    public BufferedImage toPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(PlotStyle.color(PlotStyle.EXPORT_BACKGROUND));
            g.fillRect(0, 0, width, height);
            render(new SwingSink(g, false), data, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            render(new SwingSink(g, PlotStyle.isDark(getBackground())), data,
                    getWidth(), getHeight());
        } finally {
            g.dispose();
        }
    }

    static void render(PlotSink sink, CurveData data, int width, int height) {
        if (data.series.isEmpty()) {
            sink.text(width / 2.0, height / 2.0, "No curves extracted",
                    "middle", "label");
            return;
        }

        Bounds b = bounds(data, width, height);

        sink.group("axes");
        sink.line(b.px(b.xLo), b.py(b.yLo), b.px(b.xHi), b.py(b.yLo), "axis");
        sink.line(b.px(b.xLo), b.py(b.yLo), b.px(b.xLo), b.py(b.yHi), "axis");
        sink.endGroup();

        sink.group("grid");
        for (double y = b.yLo + b.yStep; y < b.yHi - b.yStep / 2.0; y += b.yStep) {
            sink.line(b.px(b.xLo), b.py(y), b.px(b.xHi), b.py(y), "grid");
        }
        sink.endGroup();

        sink.group("ticks");
        for (double x = b.xLo; x <= b.xHi + b.xStep / 2.0; x += b.xStep) {
            double dx = b.px(x);
            sink.line(dx, b.py(b.yLo), dx, b.py(b.yLo) + TICK_LEN, "axis");
            sink.text(dx, b.py(b.yLo) + TICK_LEN + 13.0, tick(x), "middle", "label");
        }
        for (double y = b.yLo; y <= b.yHi + b.yStep / 2.0; y += b.yStep) {
            double dy = b.py(y);
            sink.line(b.px(b.xLo) - TICK_LEN, dy, b.px(b.xLo), dy, "axis");
            sink.text(b.px(b.xLo) - TICK_LEN - 3.0, dy + 4.0, tick(y), "end", "label");
        }
        sink.endGroup();

        sink.group("axis-titles");
        sink.text((b.px(b.xLo) + b.px(b.xHi)) / 2.0, height - 12.0,
                "t (s)", "middle", "label");
        String unit = data.commonUnit();
        if (!unit.isEmpty()) {
            sink.text(b.px(b.xLo), PAD_TOP - 12.0, unit, "start", "label");
        } else if (data.distinctUnits() > 1) {
            // Said once, on the figure, so a flat curve is explained rather
            // than mysterious, and so the note survives into an export. The
            // remedy is to extract the groups separately, which now yields
            // one window each.
            sink.text(b.px(b.xLo), PAD_TOP - 12.0,
                    "mixed units: extract separately to compare fairly",
                    "start", "label");
        }
        sink.endGroup();

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
            sink.polyline(xs, ys, one.t.length, PlotStyle.seriesClass(s));
        }
        sink.endGroup();

        sink.group("legend");
        double ly = PAD_TOP + LEGEND_ROW;
        for (int s = 0; s < data.series.size(); s++) {
            double lx = b.px(b.xLo) + 10.0;
            sink.line(lx, ly - 4.0, lx + 18.0, ly - 4.0, PlotStyle.seriesClass(s));
            sink.text(lx + 24.0, ly, data.series.get(s).label, "start", "label");
            ly += LEGEND_ROW;
        }
        sink.endGroup();
    }

    /**
     * The next round number at or below {@code raw = span / targetTicks}, on
     * the 1, 2, 5 times a power of ten ladder. A zero span means a flat curve,
     * which still needs a non-zero step or the tick loop cannot advance.
     */
    public static double niceStep(double span, int targetTicks) {
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
    public static double[] niceBounds(double lo, double hi, double step) {
        double low = Math.floor(lo / step) * step;
        double high = Math.ceil(hi / step) * step;
        if (high - low < step / 2.0) {
            low -= step;
            high += step;
        }
        return new double[] {low, high};
    }

    private static Bounds bounds(CurveData data, int width, int height) {
        double tLo = Double.POSITIVE_INFINITY;
        double tHi = Double.NEGATIVE_INFINITY;
        double vLo = Double.POSITIVE_INFINITY;
        double vHi = Double.NEGATIVE_INFINITY;
        for (CurveSeries one : data.series) {
            for (int i = 0; i < one.t.length; i++) {
                tLo = Math.min(tLo, one.t[i]);
                tHi = Math.max(tHi, one.t[i]);
                vLo = Math.min(vLo, one.v[i]);
                vHi = Math.max(vHi, one.v[i]);
            }
        }
        if (tLo > tHi) {
            // Every series is empty: DYNGRAPH wrote a header-only file.
            tLo = 0.0;
            tHi = 1.0;
            vLo = 0.0;
            vHi = 1.0;
        }
        double xStep = niceStep(tHi - tLo, X_TICKS);
        double yStep = niceStep(vHi - vLo, Y_TICKS);
        double[] x = niceBounds(tLo, tHi, xStep);
        double[] y = niceBounds(vLo, vHi, yStep);
        return new Bounds(x[0], x[1], y[0], y[1], xStep, yStep, width, height);
    }

    /** Short enough that neighbouring ticks do not run into each other. */
    private static String tick(double value) {
        double abs = Math.abs(value);
        String text = String.format(Locale.ROOT,
                abs >= 1000.0 ? "%.0f"
                        : abs >= 10.0 ? "%.1f"
                        : abs >= 0.01 || abs == 0.0 ? "%.3f" : "%.1e", value);
        return text.startsWith("-") && text.matches("-0(\\.0+)?")
                ? text.substring(1) : text;
    }

    /** Data-to-device mapping for one render. */
    private static final class Bounds {

        private final double xLo;
        private final double xHi;
        private final double yLo;
        private final double yHi;
        private final double xStep;
        private final double yStep;
        private final int width;
        private final int height;

        Bounds(double xLo, double xHi, double yLo, double yHi,
                double xStep, double yStep, int width, int height) {
            this.xLo = xLo;
            this.xHi = xHi;
            this.yLo = yLo;
            this.yHi = yHi;
            this.xStep = xStep;
            this.yStep = yStep;
            this.width = width;
            this.height = height;
        }

        double px(double t) {
            double span = xHi - xLo;
            double frac = span == 0.0 ? 0.0 : (t - xLo) / span;
            return PAD_LEFT + frac * (width - PAD_LEFT - PAD_RIGHT);
        }

        double py(double v) {
            double span = yHi - yLo;
            double frac = span == 0.0 ? 0.0 : (v - yLo) / span;
            return height - PAD_BOTTOM - frac * (height - PAD_TOP - PAD_BOTTOM);
        }
    }
}
```

- [ ] **Step 4: Run the harness**

Run: `ant compile && tools/curve-harness.sh`
Expected: `curve harness OK`.

- [ ] **Step 5: Eyeball one exported figure**

```bash
cat > /tmp/CurveDump.java <<'EOF'
import java.nio.file.*;
import java.util.*;
import my.stepss.curves.*;
public class CurveDump {
  public static void main(String[] a) throws Exception {
    List<String> rows = new ArrayList<>();
    for (int i = 0; i <= 120; i++) {
      double t = i * 0.25;
      rows.add(String.format(Locale.ROOT, " %.7E %.7E %.7E  ;",
          t, 1.0 - 0.15 * Math.exp(-t / 3.0) * Math.sin(t),
          300.0 - 120.0 * Math.exp(-t / 5.0)));
    }
    CurveData d = CurReader.parse(rows, null, Arrays.asList(
        "bus BUS1: voltage magnitude (pu)",
        "branch BR1-2: P (MW) entering at FROM end"));
    CurvePanel p = new CurvePanel();
    p.setData(d);
    Files.write(Paths.get("/tmp/curve.svg"), p.toSvg(800, 500).getBytes("UTF-8"));
    javax.imageio.ImageIO.write(p.toPng(800, 500), "png", new java.io.File("/tmp/curve.png"));
    System.out.println("wrote /tmp/curve.svg and /tmp/curve.png");
  }
}
EOF
javac -cp build/classes -d /tmp /tmp/CurveDump.java
java -cp build/classes:/tmp CurveDump
```

Open both. Expected: two curves, a legend that does not overlap them, round tick numbers on both axes, and the mixed-units note. The MW curve dominating the scale is correct and expected behaviour for this step; the spec accepts it.

- [ ] **Step 6: Commit**

```bash
git add src/my/stepss/curves
git commit -m "feat: draw an extraction's curves overlaid on one axis"
```

---

### Task 6: Cursor readout and rubber-band zoom

The interaction the spec scopes in, and no more. Pan, crosshair lock and legend toggling are explicitly out.

**Files:**
- Modify: `src/my/stepss/curves/CurvePanel.java`
- Modify: `src/my/stepss/curves/CurveHarness.java`

**Interfaces:**
- Consumes: Task 5's `CurvePanel`.
- Produces: on `CurvePanel`, `public void setZoom(double tLo, double tHi, double vLo, double vHi)`, `public void resetZoom()`, `public boolean zoomed()`, and `public String readoutAt(int px, int py)` returning a `t = ..., value = ...` string or null when the pointer is outside the plot area. On `PlotSink`, `void clipRect(double x, double y, double w, double h)` and `void endClip()`, implemented by both sinks.

**Why this task also touches the sink:** zooming makes clipping mandatory. Without it a zoomed curve is drawn at its full extent and paints over the axes, the tick labels and the legend, which reads as a broken plot rather than a zoomed one. The primitive belongs here rather than in Task 2 because nothing before this task can produce out-of-frame geometry.

- [ ] **Step 1: Write the failing checks**

In `src/my/stepss/curves/CurveHarness.java`, add to `main`:

```java
        zoomNarrowsTheAxesAndResets();
        readoutIsNullOutsideThePlotArea();
        curvesAreClippedToTheFrame();
```

and the methods before `round`:

```java
    private static void zoomNarrowsTheAxesAndResets() {
        CurvePanel panel = new CurvePanel();
        panel.setData(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        check("starts unzoomed", false, panel.zoomed());
        panel.setZoom(0.0, 0.25, 1.0, 1.25);
        check("zoom is recorded", true, panel.zoomed());
        String zoomedSvg = panel.toSvg(600, 400);
        check("still one polyline per curve when zoomed", 4,
                count(zoomedSvg, "<polyline"));
        panel.resetZoom();
        check("reset clears it", false, panel.zoomed());
    }

    private static void readoutIsNullOutsideThePlotArea() {
        CurvePanel panel = new CurvePanel();
        panel.setData(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        panel.setSize(600, 400);
        check("outside left is null", null, panel.readoutAt(2, 200));
        check("outside top is null", null, panel.readoutAt(300, 2));
        String inside = panel.readoutAt(300, 200);
        check("inside reads out", true, inside != null && inside.contains("t ="));
    }

    /**
     * Without a clip, a zoomed curve paints over the axes and the legend. The
     * clip is what makes zoom look like zoom rather than like a defect.
     */
    private static void curvesAreClippedToTheFrame() {
        CurvePanel panel = new CurvePanel();
        panel.setData(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        String svg = panel.toSvg(600, 400);
        check("a clip path is declared", 1, count(svg, "<clipPath"));
        check("the curves group is clipped", true,
                svg.contains("clip-path=\"url(#clip1)\""));
        check("the legend is outside the clip", true,
                svg.indexOf("id=\"legend\"") > svg.indexOf("id=\"curves\""));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: method zoomed()`.

- [ ] **Step 3: Add the zoom state and the readout**

In `src/my/stepss/curves/CurvePanel.java`, add fields after `data`:

```java
    /** The zoom window as {tLo, tHi, vLo, vHi}, or null for auto-scale. */
    private double[] zoom;
    /** Where a drag began, in device pixels, or null when not dragging. */
    private java.awt.Point dragFrom;
    /** The current drag rectangle, painted as feedback while dragging. */
    private java.awt.Rectangle dragTo;
```

Add to the constructor body, after `setPreferredSize`:

```java
        setToolTipText("");
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                dragFrom = event.getPoint();
                dragTo = null;
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                java.awt.Rectangle box = dragTo;
                dragFrom = null;
                dragTo = null;
                // A click, not a drag. Anything smaller than this is a
                // misclick rather than an intended window, and zooming to a
                // few pixels leaves no way back except the reset.
                if (box == null || box.width < 8 || box.height < 8) {
                    repaint();
                    return;
                }
                Bounds b = bounds(data, getWidth(), getHeight());
                setZoom(b.t(box.x), b.t(box.x + box.width),
                        b.v(box.y + box.height), b.v(box.y));
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    resetZoom();
                }
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent event) {
                if (dragFrom == null) {
                    return;
                }
                dragTo = new java.awt.Rectangle(
                        Math.min(dragFrom.x, event.getX()),
                        Math.min(dragFrom.y, event.getY()),
                        Math.abs(event.getX() - dragFrom.x),
                        Math.abs(event.getY() - dragFrom.y));
                repaint();
            }
        });
```

Add the public methods after `data()`:

```java
    /**
     * Narrows both axes to this data window. The buffers are untouched:
     * zooming is a view concern, so it can never lose a sample.
     */
    public void setZoom(double tLo, double tHi, double vLo, double vHi) {
        this.zoom = new double[] {tLo, tHi, vLo, vHi};
        repaint();
    }

    public void resetZoom() {
        this.zoom = null;
        repaint();
    }

    public boolean zoomed() {
        return zoom != null;
    }

    /**
     * The data coordinates under a device point, or null when the point is
     * outside the plot area, where there is nothing to report.
     */
    public String readoutAt(int px, int py) {
        if (data.series.isEmpty()) {
            return null;
        }
        if (px < PAD_LEFT || px > getWidth() - PAD_RIGHT
                || py < PAD_TOP || py > getHeight() - PAD_BOTTOM) {
            return null;
        }
        Bounds b = bounds(data, getWidth(), getHeight());
        return String.format(Locale.ROOT, "t = %.4g, value = %.6g",
                b.t(px), b.v(py));
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        return readoutAt(event.getX(), event.getY());
    }
```

- [ ] **Step 4: Add clipping to the sink**

In `src/my/stepss/plot/PlotSink.java`, after `polyline`:

```java
    /**
     * Confines everything drawn until {@link #endClip()} to this rectangle.
     *
     * <p>One level only, which is all any caller needs: a zoomed curve extends
     * past the frame and would otherwise paint over the axes, the tick labels
     * and the legend. Nesting is not supported and not needed.
     */
    void clipRect(double x, double y, double w, double h);

    void endClip();
```

In `src/my/stepss/plot/SwingSink.java`, add a field beside `dark`:

```java
    private java.awt.Shape savedClip;
```

and the two methods after `polyline`:

```java
    @Override
    public void clipRect(double x, double y, double w, double h) {
        savedClip = g.getClip();
        g.clip(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
    }

    @Override
    public void endClip() {
        g.setClip(savedClip);
        savedClip = null;
    }
```

In `src/my/stepss/plot/SvgSink.java`, add a field beside `openGroups`:

```java
    private int clipCount;
```

and the two methods after `polyline`:

```java
    @Override
    public void clipRect(double x, double y, double w, double h) {
        clipCount++;
        String id = "clip" + clipCount;
        body.append("  <clipPath id=\"").append(id).append("\">\n")
                .append("    <rect x=\"").append(fmt(x))
                .append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(w))
                .append("\" height=\"").append(fmt(h))
                .append("\"/>\n  </clipPath>\n");
        // Counted as a group, so toSvg's auto-close covers an unbalanced clip
        // exactly as it already covers an unbalanced group.
        body.append("  <g clip-path=\"url(#").append(id).append(")\">\n");
        openGroups++;
    }

    @Override
    public void endClip() {
        endGroup();
    }
```

In `src/my/stepss/curves/CurvePanel.java`, wrap the curves group in `render`. Replace:

```java
        sink.group("curves");
```

with:

```java
        sink.clipRect(b.px(b.xLo), b.py(b.yHi),
                b.px(b.xHi) - b.px(b.xLo), b.py(b.yLo) - b.py(b.yHi));
        sink.group("curves");
```

and replace the `sink.endGroup();` that closes it (the one immediately before `sink.group("legend");`) with:

```java
        sink.endGroup();
        sink.endClip();
```

Run: `ant compile && tools/plot-harness.sh && tools/ssa-harness.sh`
Expected: both pass. The two ssa panels implement `PlotSink` only through the two sinks, so widening the interface does not touch them, and the ssa harness proves it.

- [ ] **Step 5: Make `bounds` honour the zoom and gain the inverse mapping**

In `CurvePanel`, `bounds` is currently static and reads only `data`. Change it to an instance method so it can see `zoom`, and change the two call sites in `render` accordingly by passing the bounds in.

Replace the `render` signature and its first lines:

```java
    void render(PlotSink sink, CurveData data, int width, int height) {
        if (data.series.isEmpty()) {
            sink.text(width / 2.0, height / 2.0, "No curves extracted",
                    "middle", "label");
            return;
        }

        Bounds b = bounds(data, width, height);
```

(drop the `static` modifier only; the body is otherwise unchanged) and at the end of `render`, after the legend group, add the drag feedback:

```java
        if (dragTo != null) {
            sink.group("zoom-box");
            sink.line(dragTo.x, dragTo.y, dragTo.x + dragTo.width, dragTo.y, "axis");
            sink.line(dragTo.x, dragTo.y + dragTo.height,
                    dragTo.x + dragTo.width, dragTo.y + dragTo.height, "axis");
            sink.line(dragTo.x, dragTo.y, dragTo.x, dragTo.y + dragTo.height, "axis");
            sink.line(dragTo.x + dragTo.width, dragTo.y,
                    dragTo.x + dragTo.width, dragTo.y + dragTo.height, "axis");
            sink.endGroup();
        }
```

Change `bounds` from `private static Bounds bounds(...)` to `private Bounds bounds(...)` and insert the zoom override immediately before the `xStep` line:

```java
        if (zoom != null) {
            tLo = zoom[0];
            tHi = zoom[1];
            vLo = zoom[2];
            vHi = zoom[3];
        }
```

Add the two inverse mappings to `Bounds`, after `py`:

```java
        /** The time at a device x. The inverse of {@link #px}. */
        double t(double deviceX) {
            double plot = width - PAD_LEFT - PAD_RIGHT;
            double frac = plot == 0.0 ? 0.0 : (deviceX - PAD_LEFT) / plot;
            return xLo + frac * (xHi - xLo);
        }

        /** The value at a device y. The inverse of {@link #py}. */
        double v(double deviceY) {
            double plot = height - PAD_TOP - PAD_BOTTOM;
            double frac = plot == 0.0 ? 0.0
                    : (height - PAD_BOTTOM - deviceY) / plot;
            return yLo + frac * (yHi - yLo);
        }
```

Because `render` is no longer static, `toSvg`, `toPng` and `paintComponent` already call it as an instance method and need no change.

- [ ] **Step 6: Run the harness**

Run: `ant compile && tools/curve-harness.sh`
Expected: `curve harness OK`.

- [ ] **Step 7: Commit**

```bash
git add src/my/stepss/plot src/my/stepss/curves
git commit -m "feat: add a cursor readout and rubber-band zoom to the curve panel"
```

---

### Task 7: `CurveWindow`

The host. One per extraction, cascaded, owning its own data and its own exports, following `SsaResultsWindow.open`.

**Files:**
- Create: `src/my/stepss/curves/CurveWindow.java`
- Modify: `src/my/stepss/curves/CurveHarness.java`

**Interfaces:**
- Consumes: Tasks 4 to 6, and `my.stepss.WindowCascade.track(Window, Component)`.
- Produces: `public final class CurveWindow extends javax.swing.JFrame` with `public static void open(java.awt.Component parent, CurveData data, String title)` and `public static String toCsv(CurveData data)`.

- [ ] **Step 1: Write the failing CSV check**

In `src/my/stepss/curves/CurveHarness.java`, add to `main`:

```java
        csvCarriesEveryColumnAndQuotesLabels();
```

and before `round`:

```java
    private static void csvCarriesEveryColumnAndQuotesLabels() {
        String csv = CurveWindow.toCsv(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        String[] lines = csv.split("\n");
        check("header plus three rows", 4, lines.length);
        check("time column is named", true, lines[0].startsWith("t (s),"));
        check("labels are quoted", true,
                lines[0].contains("\"bus BUS1: voltage magnitude (pu)\""));
        check("first data row", true, lines[1].startsWith("0.0,"));
        check("last row carries the last column", true, lines[3].endsWith(",8.5"));

        // A label containing a comma must not become two columns.
        String odd = CurveWindow.toCsv(CurReader.parse(
                Arrays.asList(" 0.0E+000  0.1E+001  ;"), null,
                Arrays.asList("bus A,B: voltage magnitude (pu)")));
        check("comma in a label stays inside its quotes", true,
                odd.split("\n")[0].contains("\"bus A,B: voltage magnitude (pu)\""));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class CurveWindow`.

- [ ] **Step 3: Write `CurveWindow`**

Create `src/my/stepss/curves/CurveWindow.java`:

```java
package my.stepss.curves;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * One extraction's curves, displayed. Non-modal and independent, so several
 * can be open at once: comparing two extractions means putting two of these
 * side by side, and each holds its own parsed copy, so a later extraction
 * overwriting the files on disk leaves the earlier window intact.
 *
 * <p>The same arrangement, and the same reasoning, as
 * {@code SsaResultsWindow}.
 */
public final class CurveWindow extends JFrame {

    private final CurveData data;
    private final CurvePanel panel = new CurvePanel();

    /** Opens one extraction in a window of its own. Every call makes a new one. */
    public static void open(Component parent, CurveData data, String title) {
        CurveWindow window = new CurveWindow(data, title);
        my.stepss.WindowCascade.track(window, parent);
        window.setVisible(true);
    }

    public CurveWindow(CurveData data, String title) {
        super(title);
        this.data = data;
        this.panel.setData(data);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(toolbar(), BorderLayout.SOUTH);
        pack();
    }

    private JPanel header() {
        JPanel head = new JPanel(new BorderLayout());
        head.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        int samples = data.series.isEmpty() ? 0 : data.series.get(0).v.length;
        StringBuilder text = new StringBuilder();
        text.append(data.series.size()).append(" curve(s), ")
                .append(samples).append(" samples");
        if (data.source != null) {
            text.append("    ").append(data.source.getAbsolutePath());
        }
        // Said once, where a malformed file is explained rather than silently
        // shortening every curve.
        if (data.skippedRows > 0) {
            text.append("    ").append(data.skippedRows)
                    .append(" unreadable row(s) skipped");
        }
        head.add(new JLabel(text.toString()), BorderLayout.WEST);
        return head;
    }

    private JPanel toolbar() {
        JPanel bar = new JPanel();
        JButton png = new JButton("Save PNG...");
        png.addActionListener(event -> savePng());
        JButton svg = new JButton("Save SVG...");
        svg.addActionListener(event -> saveText("svg", "SVG",
                panel.toSvg(exportWidth(), exportHeight())));
        JButton csv = new JButton("Save CSV...");
        csv.addActionListener(event -> saveText("csv", "CSV", toCsv(data)));
        JButton reset = new JButton("Reset zoom");
        reset.addActionListener(event -> panel.resetZoom());
        bar.add(png);
        bar.add(svg);
        bar.add(csv);
        bar.add(reset);
        return bar;
    }

    private int exportWidth() {
        return Math.max(panel.getWidth(), 800);
    }

    private int exportHeight() {
        return Math.max(panel.getHeight(), 500);
    }

    private void savePng() {
        File target = chooseTarget("png", "PNG");
        if (target == null) {
            return;
        }
        try {
            javax.imageio.ImageIO.write(
                    panel.toPng(exportWidth(), exportHeight()), "png", target);
        } catch (IOException ex) {
            failed(target, ex);
        }
    }

    private void saveText(String extension, String what, String content) {
        File target = chooseTarget(extension, what);
        if (target == null) {
            return;
        }
        try {
            Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            failed(target, ex);
        }
    }

    private File chooseTarget(String extension, String what) {
        JFileChooser chooser = new JFileChooser(
                data.source == null ? null : data.source.getParentFile());
        chooser.setDialogTitle("Save curves as " + what);
        chooser.setSelectedFile(new File("curves." + extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            target = new File(target.getParentFile(), target.getName() + "." + extension);
        }
        return target;
    }

    private void failed(File target, IOException ex) {
        JOptionPane.showMessageDialog(this,
                "Could not write " + target + "\n\n" + ex.getMessage(),
                "Save curves", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * The data as CSV: one header row of labels, then one row per sample.
     *
     * <p>Labels are quoted because DYNGRAPH instance names can contain a
     * comma, and an unquoted one would silently split a column.
     */
    public static String toCsv(CurveData data) {
        StringBuilder out = new StringBuilder();
        out.append("t (s)");
        for (CurveSeries one : data.series) {
            out.append(",\"").append(one.label.replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
        if (data.series.isEmpty()) {
            return out.toString();
        }
        double[] t = data.series.get(0).t;
        for (int r = 0; r < t.length; r++) {
            out.append(trim(t[r]));
            for (CurveSeries one : data.series) {
                out.append(',').append(trim(one.v[r]));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Full precision without an exponent where one is not needed. */
    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.10g", value).trim();
        if (text.contains(".") && !text.contains("e") && !text.contains("E")) {
            text = text.replaceAll("0+$", "");
            if (text.endsWith(".")) {
                text = text + "0";
            }
        }
        return text;
    }
}
```

- [ ] **Step 4: Run the harness**

Run: `ant compile && tools/curve-harness.sh`
Expected: `curve harness OK`.

If `first data row` fails, `trim` is emitting `0.000000000` for zero; the trailing-zero strip must leave `0.0`, which the `endsWith(".")` branch handles.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/curves
git commit -m "feat: host each extraction's curves in its own cascaded window"
```

---

### Task 8: Wire it into the Analysis tab

The only change outside the two new packages. **View curves** and **Save extracted curve** keep working, which means the fixed `tempGnupOut` basename becomes a tracked field rather than a constant.

**Files:**
- Modify: `src/my/stepss/StepssUI.java` (the `startPlotRun` method around `:4670-4720`, the `viewCurvesButtonActionPerformed` handler around `:4522`, the `saveCurrentCurveButtonActionPerformed` handler around `:4427`, and the field block near `:6715`)
- Test: `tools/curve-harness.sh`, `ant jar`, and manual acceptance

**Interfaces:**
- Consumes: `CurveWindow.open(Component, CurveData, String)`, `CurReader.read(File, List<String>)`, `Selection.label()`.
- Produces: nothing for later tasks; this is the last one.

- [ ] **Step 1: Add the two fields**

In `src/my/stepss/StepssUI.java`, beside `private File gnuplotExec = null;` near line 6715:

```java
    /**
     * The output base of the most recent extraction. Was a constant while one
     * extraction at a time existed and both View curves and Save extracted
     * curve opened tempGnupOut by name; now that each extraction keeps its own
     * files so several windows can stay open, those two buttons act on the
     * newest, which is what they always did.
     */
    private File lastExtractionBase;

    /** How many extractions this session has run, which names their files. */
    private int extractionCount;
```

- [ ] **Step 2: Give each extraction its own basename and keep the selections**

In `startPlotRun`, replace the `outputBase` line:

```java
        File outputBase = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut");
```

with:

```java
        // One basename per extraction, so a second extraction cannot overwrite
        // the files a window opened on the first is still showing, and so Save
        // in that window keeps meaning its own data. The first is still called
        // tempGnupOut, so anything that has ever looked for that name by hand
        // still finds the newest single-extraction session's files.
        extractionCount++;
        String base = extractionCount == 1
                ? "tempGnupOut"
                : "tempGnupOut-" + extractionCount;
        File outputBase = new File(myTempDir.getAbsolutePath()
                + System.getProperty("file.separator") + base);
        lastExtractionBase = outputBase;
```

- [ ] **Step 3: Open a window when the extraction succeeds**

In the same method, inside the `onFinished` callback's `if (exitCode == 0)` branch, after the two `setEnabled(true)` calls, insert:

```java
                                openCurveWindow(outputBase, selections);
```

and add the method after `startPlotRun`:

```java
    /**
     * Shows one extraction natively. Every successful extraction opens a new
     * window rather than replacing the last, because two are opened precisely
     * in order to compare them; WindowCascade keeps them from landing on top
     * of each other.
     *
     * <p>The labels come from the selection list the picker returned, not from
     * the generated .plt. Selection.label() reproduces DYNGRAPH's desc_obs
     * curve titles byte for byte, and selection order is column order in the
     * .cur, so the .plt carries nothing this does not already have.
     */
    private void openCurveWindow(File outputBase, java.util.List<Selection> selections) {
        java.util.List<String> labels = new java.util.ArrayList<String>();
        for (Selection selection : selections) {
            labels.add(selection.label());
        }
        File cur = new File(outputBase.getAbsolutePath() + ".cur");
        try {
            my.stepss.curves.CurveData data =
                    my.stepss.curves.CurReader.read(cur, labels);
            my.stepss.curves.CurveWindow.open(this, data,
                    "Curves - " + outputBase.getName() + " - " + labels.size()
                    + " observable(s)");
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Curves were extracted but could not be read back:<br>"
                    + escapeHtml(String.valueOf(ex.getMessage()))
                    + "<br><br>The file is " + escapeHtml(cur.getAbsolutePath())
                    + "</html>",
                    "Show curves failed", JOptionPane.ERROR_MESSAGE);
        }
    }
```

`Selection` is already imported in `StepssUI` (the picker returns `List<Selection>`); if javac says otherwise, add `import my.stepss.dyngraph.Selection;`.

- [ ] **Step 4: Point the two gnuplot buttons at the tracked base**

In `viewCurvesButtonActionPerformed`, replace the argument:

```java
                command.addArgument(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut.plt");
```

with:

```java
                command.addArgument(lastExtractionBase.getAbsolutePath() + ".plt");
```

In `saveCurrentCurveButtonActionPerformed`, replace each of the four `tempGnupOut` literals with the tracked base. The `.plt`, `.cur` and `.png` source files become:

```java
                File srcFile = new File(lastExtractionBase.getAbsolutePath() + ".plt");
```
```java
                    String file_str = lastExtractionBase.getAbsolutePath() + ".cur";
```
```java
                srcFile = new File(lastExtractionBase.getAbsolutePath() + ".cur");
```
```java
                srcFile = new File(lastExtractionBase.getAbsolutePath() + ".png");
```

Both handlers are only reachable once `viewCurvesButton` and `saveCurrentCurveButton` have been enabled, which happens only in the `exitCode == 0` branch after `lastExtractionBase` is set, so neither can see it null.

- [ ] **Step 5: Compile, run every harness, build the jar**

```bash
ant compile
tools/plot-harness.sh
tools/curve-harness.sh
tools/ssa-harness.sh
ant jar
```

Expected: three harnesses pass and `ant jar` reports BUILD SUCCESSFUL. `ant jar` matters here rather than only `ant compile`: `-post-compile` runs `PayloadManifestCheck`, so a green jar also confirms this step disturbed nothing about the payloads.

- [ ] **Step 6: Manual acceptance**

Run the application, load a trajectory, and check all of the following:

```bash
ant jar
java -jar dist/stepss.jar
```

1. **Extract curves** on several observables of one kind, then **plot**. A curve window opens with a legend, round tick numbers, and no mixed-units note.
2. **Extract curves** again with a different selection. A **second** window opens, offset from the first, and the first still shows its own curves.
3. Drag a box in one window: it zooms. Double-click: it resets. Hover: the readout names t and a value.
4. **Save PNG**, **Save SVG** and **Save CSV** each write a file. Open the SVG in a browser and the CSV in a spreadsheet; the CSV column count is one more than the number of curves.
5. Toggle the dark theme. Both windows re-theme, and a **Save SVG** taken afterwards is still on a white ground.
6. **View curves** still opens gnuplot on the newest extraction, and **Save extracted curve** still writes a working `.plt` and `.cur` pair.
7. Select observables with different units in one extraction. The mixed-units note appears and the small-magnitude curves are flat, which is the accepted behaviour for this step.

- [ ] **Step 7: Commit**

```bash
git add src/my/stepss/StepssUI.java
git commit -m "feat: show extracted curves in a native window per extraction"
```

---

## Self-review notes

Checked against the spec's post-analysis section:

- **Retain the selection list** (spec item 1): Task 8 Step 3 passes `selections` into `openCurveWindow` instead of dropping it.
- **One window per extraction, cascaded** (item 2): Task 7, via `WindowCascade.track`.
- **Read `<base>.cur`, strip ` ;`, `E15.7E3`** (item 3): Task 4, with a check per trap.
- **Labels from `Selection.label()` and `ObservableIndex`** (item 4): Task 8 Step 3; no `.plt` is opened anywhere in the new code.
- **One panel, curves overlaid, legend** (item 5): Task 5.
- **Per-extraction basenames** and the frozen-name constraint: Task 8 Step 2, with the two dependent buttons repointed in Step 4 rather than removed, because this step must leave them working.
- **PNG, SVG, CSV export**: Task 7.
- **Mixed-unit notice**: Task 5, drawn on the figure so it survives export.
- **`CurveHarness` and `tools/curve-harness.sh`**: Tasks 4 to 7.
- **Interaction scope**: Task 6 implements the readout and rubber-band zoom and nothing else, matching the spec's in/out list.

Two spec items are deliberately **not** in this plan, because they belong to later steps: the live viewer with its `.cur` tailing (step 4), and every deletion in the inventory, including the payload, the ToolSpec, the banner and the toolbar rename (step 4). The `#` header in `.cur` is a step 3 engine change and this plan's reader does not depend on it, because the post-analysis path derives its columns from selection order.

One thing the plan adds beyond the spec's letter, and why: the spec says the chart paints through `PlotSink`, but `PlotSink` has no primitive that can draw a time series without emitting one element per sample. Task 2 adds `polyline`, which is a change to a shared interface the ssa panels also implement, so it is sequenced before anything that draws a curve and its regression is the existing `tools/ssa-harness.sh`.
