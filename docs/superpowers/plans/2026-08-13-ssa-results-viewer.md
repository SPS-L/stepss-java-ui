# SSA Results Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `stepss-java-ui` a results window that reads the three files RAMSES writes for a small-signal run and shows the modes, participation factors, mode shape and s-plane, with editable SVG export, and fix the three defects that make the existing SSA buttons look broken.

**Architecture:** A new `my.ramses.ssa` package holds parsing (pure, no Swing) and display (Swing, hand-written). Plots draw against a `PlotSink` interface with two backends, `SwingSink` for the screen and `SvgSink` for export, so the file and the screen come from one code path. `RamsesUI` gains only the calls that open a window; everything else lives in the new package, outside the NetBeans GUI builder.

**Tech Stack:** Java 11, Swing, Ant. `lib/` holds commons-exec 1.3 and commons-io 2.11.0 only. No charting library is added.

## Global Constraints

- **Java 11.** `javac.source=11`, `javac.target=11`. No text blocks, no `var` in fields, no records.
- **No new dependencies.** `lib/` gains nothing. No JFreeChart, no Batik, no gnuplot dependency for the plots.
- **No unit-test framework.** Checks go in a `main()` harness with fixtures as string literals, per `PickerHarness`. Harness prints `PASS  <what>` / `FAIL  <what>`, then `ALL CHECKS PASSED` or `N CHECK(S) FAILED`, and exits 0 or 1.
- **Text fields are parsed by column offset, never by `split()`.** A leading blank is part of a device name; only trailing blanks are padding.
- **No em-dashes** in any code comment, commit message or documentation this plan produces.
- **`RamsesUI.form` and `initComponents()` change together**, in NetBeans, never by hand-editing one alone.
- **Spec:** `docs/superpowers/specs/2026-08-13-ssa-results-viewer-design.md` governs. Where this plan and the spec disagree, the spec wins.
- Branch: `ssa-results-viewer`, already created, spec committed at `270bedd`.

## Column offsets, derived once

From the `ssa.f90` format strings. All ends must be clamped to line length, because a trailing all-blank `a20` can be stripped by an editor.

`_modes.dat`, `(i8,1x,4(en24.15,1x),i2,1x,i2)`:

| Field | Offsets |
|---|---|
| index | `[0,8)` |
| re | `[9,33)` |
| im | `[34,58)` |
| zeta | `[59,83)` |
| freq_hz | `[84,108)` |
| dom | `[109,111)` |
| smp | `[112,114)` |

`_pf.dat`, `(i8,1x,i8,1x,en24.15,1x,a8,1x,a20,1x,a20)`:

| Field | Offsets |
|---|---|
| mode | `[0,8)` |
| state | `[9,17)` |
| pf | `[18,42)` |
| family | `[43,51)` |
| device | `[52,72)` |
| variable | `[73,93)` |

`_ms.dat`, `(i8,1x,i8,1x,2(en24.15,1x),a20)`:

| Field | Offsets |
|---|---|
| mode | `[0,8)` |
| state | `[9,17)` |
| magnitude | `[18,42)` |
| angle_deg | `[43,67)` |
| device | `[68,88)` |

## File structure

| File | Responsibility |
|---|---|
| `src/my/ramses/ssa/Mode.java` | One `_modes.dat` row |
| `src/my/ramses/ssa/SsaModes.java` | Header metadata plus the `Mode` list, and its parser |
| `src/my/ramses/ssa/Participation.java` | One `_pf.dat` row |
| `src/my/ramses/ssa/SsaParticipation.java` | Rows indexed by mode, and its parser |
| `src/my/ramses/ssa/ModeShapeEntry.java` | One `_ms.dat` row |
| `src/my/ramses/ssa/SsaModeShapes.java` | Rows indexed by mode, and its parser |
| `src/my/ramses/ssa/SsaResults.java` | The three together, directory discovery, `load` |
| `src/my/ramses/ssa/Columns.java` | Offset slicing and numeric parsing, shared by all three parsers |
| `src/my/ramses/ssa/PlotSink.java` | Drawing primitives |
| `src/my/ramses/ssa/SwingSink.java` | `PlotSink` over `Graphics2D` |
| `src/my/ramses/ssa/SvgSink.java` | `PlotSink` emitting SVG |
| `src/my/ramses/ssa/SplanePanel.java` | s-plane geometry and interaction |
| `src/my/ramses/ssa/ModeShapePanel.java` | Polar dial geometry |
| `src/my/ramses/ssa/SsaResultsWindow.java` | Window layout, tables, selection wiring |
| `src/my/ramses/ssa/SsaHarness.java` | Headless checks |
| `tools/ssa-harness.sh` | Runs the harness against `build/classes` |
| `src/my/ramses/RamsesUI.java` | Wiring only |
| `src/my/ramses/RamsesUI.form` | Three new controls, one relabel |

---

### Task 1: `Columns` and the modes parser

**Files:**
- Create: `src/my/ramses/ssa/Columns.java`
- Create: `src/my/ramses/ssa/Mode.java`
- Create: `src/my/ramses/ssa/SsaModes.java`
- Create: `src/my/ramses/ssa/SsaHarness.java`
- Create: `tools/ssa-harness.sh`

**Interfaces:**
- Consumes: nothing.
- Produces: `Columns.slice(String line, int from, int to)` returning `String`; `Columns.num(String line, int from, int to)` returning `double`; `Columns.integer(String line, int from, int to)` returning `int`; `Mode` with public final fields `index` (int), `re`, `im`, `zeta`, `freqHz` (double), `dominant`, `simple` (boolean); `SsaModes.parse(String text)` returning `SsaModes` and throwing `IOException`; `SsaModes.modes()` returning `List<Mode>`; `SsaModes.nstates()`, `nalg()` returning `int`; `SsaModes.time()`, `realLimit()`, `pfThreshold()`, `gapTol()` returning `Double`, null when the key was absent.

- [ ] **Step 1: Write the failing checks**

Create `src/my/ramses/ssa/SsaHarness.java`. Fixture lines are literals, per `PickerHarness`, because the traps are invisible in a data file.

```java
package my.ramses.ssa;

/**
 * Headless checks for the SSA results pipeline: fixture -> parse -> query.
 * This repository has no unit-test framework and is not gaining one, so this
 * main() is where the parsers and the plot geometry are pinned; the window
 * itself is covered by manual acceptance against examples/kundur-ssa.
 *
 * <p>Fixtures are string literals rather than data files for the reason
 * PickerHarness gives: the traps here are invisible in a text file. A device
 * name carrying a leading blank survives a quoted Java literal and does not
 * survive most editors.
 */
public final class SsaHarness {

    private static int failures;

    /**
     * A modes fixture with, in order: a real mode; a conjugate pair at
     * 0.6237 Hz; a mode with negative zeta; a degenerate mode (smp 0); and a
     * mode at the origin, where ssa.f90:1369-1373 reports zeta as 0 rather
     * than NaN. Column positions are exactly those ssa.f90 writes.
     */
    private static final String[] MODES_LINES = {
        "# STEPSS SSA modes v1",
        "# nstates 5 nalg 7 time    0.000000000000000E+00 real_limit   -1.000000000000000E+00"
            + " pf_threshold   50.000000000000003E-03 gap_tol    1.000000000000000E-06",
        "#   index                       re                       im"
            + "                     zeta                  freq_hz dom smp",
        "       1  -10.000000000000000E+00    0.000000000000000E+00"
            + "    1.000000000000000E+00    0.000000000000000E+00  1  1",
        "       2 -428.700000000000000E-03    3.919040000000000E+00"
            + "  108.740000000000000E-03  623.700000000000000E-03  1  1",
        "       3 -428.700000000000000E-03   -3.919040000000000E+00"
            + "  108.740000000000000E-03  623.700000000000000E-03  1  1",
        "       4   91.400000000000000E-03    3.924700000000000E+00"
            + "  -23.300000000000000E-03  624.600000000000000E-03  1  1",
        "       5    0.000000000000000E+00    0.000000000000000E+00"
            + "    0.000000000000000E+00    0.000000000000000E+00  0  0",
    };

    static String modesFixture() {
        return join(MODES_LINES);
    }

    private static String join(String[] lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    public static void main(String[] args) {
        checkModesParse();
        checkModesHeader();
        checkModesOriginZeta();
        checkModesCrlf();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static SsaModes parsedModes() {
        try {
            return SsaModes.parse(modesFixture());
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    private static void checkModesParse() {
        SsaModes m = parsedModes();
        if (m == null) {
            fail("modes fixture parses");
            return;
        }
        expect("mode count", 5, m.modes().size());
        expect("index of the second mode", 2, m.modes().get(1).index);
        expect("re of the second mode", -0.4287, round(m.modes().get(1).re, 4));
        expect("im of the second mode", 3.91904, round(m.modes().get(1).im, 5));
        expect("zeta of the second mode", 0.10874, round(m.modes().get(1).zeta, 5));
        expect("freq of the second mode", 0.6237, round(m.modes().get(1).freqHz, 4));
        expect("a negative zeta survives the sign column", -0.0233,
                round(m.modes().get(3).zeta, 4));
        expect("the degenerate mode is flagged", false, m.modes().get(4).simple);
        expect("the degenerate mode is not dominant", false, m.modes().get(4).dominant);
        expect("a dominant mode is flagged", true, m.modes().get(0).dominant);
    }

    private static void checkModesHeader() {
        SsaModes m = parsedModes();
        if (m == null) {
            fail("modes header parses");
            return;
        }
        expect("nstates", 5, m.nstates());
        expect("nalg", 7, m.nalg());
        expect("real_limit", -1.0, m.realLimit());
        expect("pf_threshold", 0.05, round(m.pfThreshold(), 6));
        expect("gap_tol", 1.0e-6, m.gapTol());
    }

    private static void checkModesOriginZeta() {
        SsaModes m = parsedModes();
        if (m == null) {
            fail("origin mode zeta");
            return;
        }
        Mode origin = m.modes().get(4);
        expect("a mode at the origin reports zeta 0, not NaN", 0.0, origin.zeta);
        expect("a mode at the origin is not NaN in re", 0.0, origin.re);
    }

    private static void checkModesCrlf() {
        try {
            SsaModes m = SsaModes.parse(modesFixture().replace("\n", "\r\n"));
            expect("CRLF input parses to the same mode count", 5, m.modes().size());
            expect("CRLF does not corrupt the last column", true,
                    m.modes().get(0).simple);
        } catch (java.io.IOException ex) {
            fail("CRLF input parses: threw " + ex);
        }
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private static void expect(String what, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) {
            pass(what);
        } else {
            fail(what + ": wanted <" + want + "> got <" + got + ">");
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

- [ ] **Step 2: Create the runner script**

Create `tools/ssa-harness.sh`, mirroring `tools/picker-harness.sh` including its `build/classes` precondition and its exclusion of `dist/lib`:

```bash
#!/usr/bin/env bash
# Runs the headless small-signal results checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/picker-harness.sh, dist/lib is NOT on the classpath: the ssa
# package depends on nothing that launches a process or references
# commons-exec types, so build/classes alone is enough to load it.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.ramses.ssa.SsaHarness
```

Then `chmod +x tools/ssa-harness.sh`.

- [ ] **Step 3: Run the harness to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class SsaModes` and `class Mode`.

- [ ] **Step 4: Write `Columns`**

```java
package my.ramses.ssa;

import java.io.IOException;

/**
 * Fixed-offset field extraction for the three results files ssa.f90 writes.
 *
 * <p>Splitting these lines on whitespace is wrong and fails silently. The
 * a8 and a20 name fields are written as stored, so a LEADING blank is part
 * of the name while trailing blanks are padding, and a device name may
 * contain an embedded blank. PickerHarness documents the same trap for the
 * dyngraph index. Every field is therefore taken by offset.
 *
 * <p>Ends are clamped to the line length, because an all-blank trailing a20
 * is routinely stripped by editors and by CRLF normalisation.
 */
final class Columns {

    private Columns() {
    }

    /** The field at [from,to), trailing blanks removed, leading blanks kept. */
    static String slice(String line, int from, int to) {
        if (from >= line.length()) {
            return "";
        }
        String raw = line.substring(from, Math.min(to, line.length()));
        int end = raw.length();
        while (end > 0 && raw.charAt(end - 1) == ' ') {
            end--;
        }
        return raw.substring(0, end);
    }

    static double num(String line, int from, int to, int lineNo) throws IOException {
        String text = slice(line, from, to).trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            throw new IOException("line " + lineNo + ": cannot read a number from <"
                    + text + "> at columns " + (from + 1) + "-" + to);
        }
    }

    static int integer(String line, int from, int to, int lineNo) throws IOException {
        String text = slice(line, from, to).trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IOException("line " + lineNo + ": cannot read an integer from <"
                    + text + "> at columns " + (from + 1) + "-" + to);
        }
    }
}
```

- [ ] **Step 5: Write `Mode`**

```java
package my.ramses.ssa;

/** One row of &lt;base&gt;_modes.dat. */
public final class Mode {

    public final int index;
    public final double re;      //!< real part of lambda (1/s)
    public final double im;      //!< imaginary part of lambda (rad/s)
    public final double zeta;    //!< damping ratio (-)
    public final double freqHz;  //!< oscillation frequency (Hz)
    /** True when Re(lambda) passed the run's real_limit filter. */
    public final boolean dominant;
    /**
     * True when the eigenvalue is simple. When false the eigenvalue is
     * degenerate, its eigenvectors are not unique, and its participation
     * factors and mode shape are basis-dependent and must not be read.
     */
    public final boolean simple;

    Mode(int index, double re, double im, double zeta, double freqHz,
            boolean dominant, boolean simple) {
        this.index = index;
        this.re = re;
        this.im = im;
        this.zeta = zeta;
        this.freqHz = freqHz;
        this.dominant = dominant;
        this.simple = simple;
    }
}
```

- [ ] **Step 6: Write `SsaModes`**

```java
package my.ramses.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** &lt;base&gt;_modes.dat: the run's header metadata and one Mode per line. */
public final class SsaModes {

    private final List<Mode> modes;
    private final int nstates;
    private final int nalg;
    private final Double time;
    private final Double realLimit;
    private final Double pfThreshold;
    private final Double gapTol;

    private SsaModes(List<Mode> modes, int nstates, int nalg, Double time,
            Double realLimit, Double pfThreshold, Double gapTol) {
        this.modes = Collections.unmodifiableList(modes);
        this.nstates = nstates;
        this.nalg = nalg;
        this.time = time;
        this.realLimit = realLimit;
        this.pfThreshold = pfThreshold;
        this.gapTol = gapTol;
    }

    public List<Mode> modes() {
        return modes;
    }

    public int nstates() {
        return nstates;
    }

    public int nalg() {
        return nalg;
    }

    /** Null when the key was absent from the header. */
    public Double time() {
        return time;
    }

    public Double realLimit() {
        return realLimit;
    }

    public Double pfThreshold() {
        return pfThreshold;
    }

    public Double gapTol() {
        return gapTol;
    }

    public static SsaModes parse(String text) throws IOException {
        List<Mode> found = new ArrayList<Mode>();
        int nstates = 0;
        int nalg = 0;
        Double time = null;
        Double realLimit = null;
        Double pfThreshold = null;
        Double gapTol = null;
        String[] lines = text.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.charAt(0) == '#') {
                // Read by key, not by position, so a future engine adding a
                // field does not break this; an absent key stays null rather
                // than failing the load.
                if (line.contains(" nstates ")) {
                    nstates = (int) keyed(line, "nstates", 0.0);
                    nalg = (int) keyed(line, "nalg", 0.0);
                    time = keyedOrNull(line, "time");
                    realLimit = keyedOrNull(line, "real_limit");
                    pfThreshold = keyedOrNull(line, "pf_threshold");
                    gapTol = keyedOrNull(line, "gap_tol");
                }
                continue;
            }
            found.add(new Mode(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.num(line, 9, 33, lineNo),
                    Columns.num(line, 34, 58, lineNo),
                    Columns.num(line, 59, 83, lineNo),
                    Columns.num(line, 84, 108, lineNo),
                    Columns.integer(line, 109, 111, lineNo) == 1,
                    Columns.integer(line, 112, 114, lineNo) == 1));
        }
        if (found.isEmpty()) {
            throw new IOException("no mode rows found; is this a _modes.dat file?");
        }
        return new SsaModes(found, nstates, nalg, time, realLimit, pfThreshold, gapTol);
    }

    private static double keyed(String header, String key, double fallback) {
        Double value = keyedOrNull(header, key);
        return value == null ? fallback : value;
    }

    private static Double keyedOrNull(String header, String key) {
        int at = header.indexOf(' ' + key + ' ');
        if (at < 0) {
            return null;
        }
        String rest = header.substring(at + key.length() + 2).trim();
        int end = rest.indexOf(' ');
        String token = end < 0 ? rest : rest.substring(0, end);
        try {
            return Double.valueOf(token);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
```

- [ ] **Step 7: Run the harness to verify it passes**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: `ALL CHECKS PASSED`, exit 0.

- [ ] **Step 8: Commit**

```bash
git add src/my/ramses/ssa/Columns.java src/my/ramses/ssa/Mode.java \
        src/my/ramses/ssa/SsaModes.java src/my/ramses/ssa/SsaHarness.java \
        tools/ssa-harness.sh
git commit -m "Parse the small-signal modes file

Fixed-offset field extraction, because splitting these lines on whitespace
fails silently on the a20 name fields later. The header line is read by key
so an absent key stays null rather than failing the load, and a mode at the
origin keeps the zeta 0 that ssa.f90 writes instead of becoming NaN."
```

---

### Task 2: Participation and mode-shape parsers

**Files:**
- Create: `src/my/ramses/ssa/Participation.java`
- Create: `src/my/ramses/ssa/SsaParticipation.java`
- Create: `src/my/ramses/ssa/ModeShapeEntry.java`
- Create: `src/my/ramses/ssa/SsaModeShapes.java`
- Modify: `src/my/ramses/ssa/SsaHarness.java`

**Interfaces:**
- Consumes: `Columns` from Task 1.
- Produces: `Participation` with public final `mode`, `state` (int), `pf` (double), `family`, `device`, `variable` (String); `SsaParticipation.parse(String)` returning `SsaParticipation`; `SsaParticipation.forMode(int)` returning `List<Participation>` sorted descending by `pf`, empty when the mode has no rows. `ModeShapeEntry` with public final `mode`, `state` (int), `magnitude`, `angleDeg` (double), `device` (String); `SsaModeShapes.parse(String)`; `SsaModeShapes.forMode(int)` returning `List<ModeShapeEntry>` in file order.

- [ ] **Step 1: Write the failing checks**

Add to `SsaHarness`. The device fixtures carry the two traps that matter.

```java
    /**
     * A participation fixture. Traps, in order: a device name with an
     * embedded blank ("AREA 1 G1"), which whitespace splitting merges into
     * the wrong column; a device name with a LEADING blank (" G2"), which
     * over-eager trimming destroys; and mode 4, which appears in _modes.dat
     * but has no rows here because real_limit filtered it.
     */
    private static final String[] PF_LINES = {
        "# STEPSS SSA participation factors v1",
        "#    mode    state                       pf family device               variable",
        "       2        1  812.943204050653012E-03 SYN      AREA 1 G1            delta               ",
        "       2        2  852.937065403949757E-03 SYN      AREA 1 G1            omega               ",
        "       2        3  499.078992565508639E-03 SYN       G2                  delta               ",
        "       1        1  100.000000000000000E-03 TOR      G1                   x05                 ",
    };

    /** Mode shapes for mode 2, with the same leading-blank device trap. */
    private static final String[] MS_LINES = {
        "# STEPSS SSA mode shapes v1",
        "#    mode    state                magnitude                angle_deg device",
        "       2        2  830.724078309408420E-03  162.398720936339885E+00 AREA 1 G1           ",
        "       2        4    1.000000000000000E+00    0.000000000000000E+00  G2                 ",
    };

    private static void checkParticipationNames() {
        try {
            SsaParticipation pf = SsaParticipation.parse(join(PF_LINES));
            java.util.List<Participation> rows = pf.forMode(2);
            expect("participation row count for mode 2", 3, rows.size());
            expect("rows are sorted descending by pf", "omega", rows.get(0).variable);
            expect("an embedded blank survives in a device name", "AREA 1 G1",
                    rows.get(0).device);
            expect("a leading blank is part of the device name", " G2",
                    rows.get(2).device);
            expect("family is trimmed of its a8 padding", "SYN", rows.get(0).family);
            expect("variable is trimmed of its a20 padding", "delta", rows.get(2).variable);
            expect("pf value", 0.852937, round(rows.get(0).pf, 6));
        } catch (java.io.IOException ex) {
            fail("participation fixture parses: threw " + ex);
        }
    }

    private static void checkParticipationFilteredMode() {
        try {
            SsaParticipation pf = SsaParticipation.parse(join(PF_LINES));
            expect("a mode filtered by real_limit has no rows, not an error",
                    0, pf.forMode(4).size());
        } catch (java.io.IOException ex) {
            fail("filtered mode lookup: threw " + ex);
        }
    }

    private static void checkModeShapes() {
        try {
            SsaModeShapes ms = SsaModeShapes.parse(join(MS_LINES));
            java.util.List<ModeShapeEntry> rows = ms.forMode(2);
            expect("mode shape row count", 2, rows.size());
            expect("an embedded blank survives here too", "AREA 1 G1", rows.get(0).device);
            expect("a leading blank survives here too", " G2", rows.get(1).device);
            expect("magnitude", 0.830724, round(rows.get(0).magnitude, 6));
            expect("angle in degrees", 162.3987, round(rows.get(0).angleDeg, 4));
            expect("the reference entry is at angle zero", 0.0, rows.get(1).angleDeg);
        } catch (java.io.IOException ex) {
            fail("mode shape fixture parses: threw " + ex);
        }
    }
```

Register them in `main()` after `checkModesCrlf()`:

```java
        checkParticipationNames();
        checkParticipationFilteredMode();
        checkModeShapes();
```

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class SsaParticipation`.

- [ ] **Step 3: Write `Participation` and `SsaParticipation`**

```java
package my.ramses.ssa;

/** One row of &lt;base&gt;_pf.dat. */
public final class Participation {

    public final int mode;
    public final int state;
    public final double pf;      //!< participation factor, largest per mode normalised to 1 (-)
    public final String family;  //!< SYN, TOR, EXC, INJ, DCTL
    public final String device;
    public final String variable;

    Participation(int mode, int state, double pf, String family, String device,
            String variable) {
        this.mode = mode;
        this.state = state;
        this.pf = pf;
        this.family = family;
        this.device = device;
        this.variable = variable;
    }
}
```

```java
package my.ramses.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * &lt;base&gt;_pf.dat, indexed by mode.
 *
 * <p>Two absences are meaningful and are preserved rather than smoothed
 * over: ssa.f90 writes rows only for modes with dom == 1, so a mode present
 * in _modes.dat can be missing here entirely; and within a mode it writes
 * only entries above pf_threshold, so a missing device means "below the
 * threshold this run used", never "zero".
 */
public final class SsaParticipation {

    private final Map<Integer, List<Participation>> byMode;

    private SsaParticipation(Map<Integer, List<Participation>> byMode) {
        this.byMode = byMode;
    }

    /** Rows for one mode, largest participation first. Empty when filtered out. */
    public List<Participation> forMode(int mode) {
        List<Participation> rows = byMode.get(Integer.valueOf(mode));
        return rows == null ? Collections.<Participation>emptyList() : rows;
    }

    public static SsaParticipation parse(String text) throws IOException {
        Map<Integer, List<Participation>> byMode =
                new LinkedHashMap<Integer, List<Participation>>();
        String[] lines = text.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int lineNo = i + 1;
            Participation row = new Participation(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.integer(line, 9, 17, lineNo),
                    Columns.num(line, 18, 42, lineNo),
                    Columns.slice(line, 43, 51).trim(),
                    Columns.slice(line, 52, 72),
                    Columns.slice(line, 73, 93).trim());
            Integer key = Integer.valueOf(row.mode);
            List<Participation> rows = byMode.get(key);
            if (rows == null) {
                rows = new ArrayList<Participation>();
                byMode.put(key, rows);
            }
            rows.add(row);
        }
        for (List<Participation> rows : byMode.values()) {
            Collections.sort(rows, new Comparator<Participation>() {
                @Override
                public int compare(Participation a, Participation b) {
                    return Double.compare(b.pf, a.pf);
                }
            });
        }
        return new SsaParticipation(byMode);
    }
}
```

Note the asymmetry, which is deliberate: `family` and `variable` are `.trim()`ed because they are engine-generated keywords that never carry a leading blank, while `device` is not, because it is a user-supplied name where a leading blank is significant.

- [ ] **Step 4: Write `ModeShapeEntry` and `SsaModeShapes`**

```java
package my.ramses.ssa;

/** One row of &lt;base&gt;_ms.dat: a machine's rotor-speed phasor in one mode. */
public final class ModeShapeEntry {

    public final int mode;
    public final int state;
    public final double magnitude;  //!< normalised so the largest in the mode is 1 (-)
    public final double angleDeg;   //!< relative to the largest entry (deg)
    public final String device;

    ModeShapeEntry(int mode, int state, double magnitude, double angleDeg,
            String device) {
        this.mode = mode;
        this.state = state;
        this.magnitude = magnitude;
        this.angleDeg = angleDeg;
        this.device = device;
    }
}
```

```java
package my.ramses.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * &lt;base&gt;_ms.dat, indexed by mode. Kept in file order, which is state
 * order, because the phase reference is the largest-magnitude omega entry
 * and reordering would obscure which machine that was.
 */
public final class SsaModeShapes {

    private final Map<Integer, List<ModeShapeEntry>> byMode;

    private SsaModeShapes(Map<Integer, List<ModeShapeEntry>> byMode) {
        this.byMode = byMode;
    }

    public List<ModeShapeEntry> forMode(int mode) {
        List<ModeShapeEntry> rows = byMode.get(Integer.valueOf(mode));
        return rows == null ? Collections.<ModeShapeEntry>emptyList() : rows;
    }

    public static SsaModeShapes parse(String text) throws IOException {
        Map<Integer, List<ModeShapeEntry>> byMode =
                new LinkedHashMap<Integer, List<ModeShapeEntry>>();
        String[] lines = text.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int lineNo = i + 1;
            ModeShapeEntry row = new ModeShapeEntry(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.integer(line, 9, 17, lineNo),
                    Columns.num(line, 18, 42, lineNo),
                    Columns.num(line, 43, 67, lineNo),
                    Columns.slice(line, 68, 88));
            Integer key = Integer.valueOf(row.mode);
            List<ModeShapeEntry> rows = byMode.get(key);
            if (rows == null) {
                rows = new ArrayList<ModeShapeEntry>();
                byMode.put(key, rows);
            }
            rows.add(row);
        }
        return new SsaModeShapes(byMode);
    }
}
```

- [ ] **Step 5: Run the harness to verify it passes**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: `ALL CHECKS PASSED`.

- [ ] **Step 6: Commit**

```bash
git add src/my/ramses/ssa/Participation.java src/my/ramses/ssa/SsaParticipation.java \
        src/my/ramses/ssa/ModeShapeEntry.java src/my/ramses/ssa/SsaModeShapes.java \
        src/my/ramses/ssa/SsaHarness.java
git commit -m "Parse the participation and mode-shape files

Device names keep their leading blank and any embedded blank, which is why
these are sliced by column. Family and variable are trimmed, since those are
engine keywords rather than user-supplied names. A mode with no rows is an
empty list rather than an error: ssa.f90 writes these files only for modes
that passed real_limit."
```

---

### Task 3: `SsaResults` aggregate and basename discovery

**Files:**
- Create: `src/my/ramses/ssa/SsaResults.java`
- Modify: `src/my/ramses/ssa/SsaHarness.java`

**Interfaces:**
- Consumes: `SsaModes`, `SsaParticipation`, `SsaModeShapes`.
- Produces: `SsaResults.load(File dir, String basename)` returning `SsaResults`, throwing `IOException`; `SsaResults.basenames(File dir)` returning `List<String>` sorted, every `X` for which `X_modes.dat` exists; accessors `modes()`, `participation()`, `shapes()`, `directory()`, `basename()`; `SsaResults.electromechanical(List<Mode>)` returning the filtered list.

- [ ] **Step 1: Write the failing checks**

```java
    private static void checkElectromechanicalFilter() {
        SsaModes m = parsedModes();
        if (m == null) {
            fail("electromechanical filter");
            return;
        }
        java.util.List<Mode> em = SsaResults.electromechanical(m.modes());
        // Of the fixture: mode 1 is real (im 0), modes 2 and 3 are a conjugate
        // pair at 0.6237 Hz of which only the im > 0 member survives, mode 4 is
        // at 0.6246 Hz with im > 0, mode 5 sits at the origin.
        expect("conjugate pairs collapse to one member", 2, em.size());
        expect("the kept pair member has positive im", true, em.get(0).im > 0);
        expect("sorted by frequency", 0.6237, round(em.get(0).freqHz, 4));
        expect("the unstable mode is kept", -0.0233, round(em.get(1).zeta, 4));
    }

    private static void checkBasenameDiscoveryOnEmptyDir() throws java.io.IOException {
        java.io.File dir = java.nio.file.Files.createTempDirectory("ssaharness").toFile();
        dir.deleteOnExit();
        expect("an empty directory yields no basenames", 0,
                SsaResults.basenames(dir).size());
        java.io.File modes = new java.io.File(dir, "run1_modes.dat");
        java.nio.file.Files.write(modes.toPath(), modesFixture().getBytes("UTF-8"));
        modes.deleteOnExit();
        expect("one basename is discovered", 1, SsaResults.basenames(dir).size());
        expect("the basename drops the _modes.dat suffix", "run1",
                SsaResults.basenames(dir).get(0));
    }
```

Register both in `main()`, and change `main` to declare `throws java.io.IOException`.

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class SsaResults`.

- [ ] **Step 3: Write `SsaResults`**

```java
package my.ramses.ssa;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One small-signal run: the three files ssa.f90 wrote under a shared
 * basename, plus where they came from.
 *
 * <p>_pf.dat and _ms.dat are optional. The engine writes them only when at
 * least one mode passed real_limit, so a run that filtered everything leaves
 * _modes.dat alone on disk. That is a legitimate result, not a broken load.
 */
public final class SsaResults {

    private static final String MODES_SUFFIX = "_modes.dat";

    private final File directory;
    private final String basename;
    private final SsaModes modes;
    private final SsaParticipation participation;
    private final SsaModeShapes shapes;

    private SsaResults(File directory, String basename, SsaModes modes,
            SsaParticipation participation, SsaModeShapes shapes) {
        this.directory = directory;
        this.basename = basename;
        this.modes = modes;
        this.participation = participation;
        this.shapes = shapes;
    }

    public File directory() {
        return directory;
    }

    public String basename() {
        return basename;
    }

    public SsaModes modes() {
        return modes;
    }

    public SsaParticipation participation() {
        return participation;
    }

    public SsaModeShapes shapes() {
        return shapes;
    }

    /** Every basename in dir for which a modes file exists, sorted. */
    public static List<String> basenames(File dir) {
        List<String> found = new ArrayList<String>();
        String[] names = dir == null ? null : dir.list();
        if (names == null) {
            return found;
        }
        for (String name : names) {
            if (name.endsWith(MODES_SUFFIX) && name.length() > MODES_SUFFIX.length()) {
                found.add(name.substring(0, name.length() - MODES_SUFFIX.length()));
            }
        }
        Collections.sort(found);
        return found;
    }

    public static SsaResults load(File dir, String basename) throws IOException {
        File modesFile = new File(dir, basename + MODES_SUFFIX);
        if (!modesFile.isFile()) {
            throw new IOException("no " + modesFile.getName() + " in " + dir);
        }
        SsaModes parsedModes = SsaModes.parse(read(modesFile));
        File pfFile = new File(dir, basename + "_pf.dat");
        File msFile = new File(dir, basename + "_ms.dat");
        SsaParticipation parsedPf = pfFile.isFile()
                ? SsaParticipation.parse(read(pfFile))
                : SsaParticipation.parse("");
        SsaModeShapes parsedMs = msFile.isFile()
                ? SsaModeShapes.parse(read(msFile))
                : SsaModeShapes.parse("");
        return new SsaResults(dir, basename, parsedModes, parsedPf, parsedMs);
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The electromechanical band, reproducing the python-ui notebook's
     * electromechanical(): 0.1 to 2.5 Hz with Im &gt; 0, sorted by frequency.
     * The Im &gt; 0 test is what collapses each conjugate pair to a single
     * row, since the two members are one physical oscillation.
     */
    public static List<Mode> electromechanical(List<Mode> all) {
        List<Mode> kept = new ArrayList<Mode>();
        for (Mode mode : all) {
            if (mode.freqHz > 0.1 && mode.freqHz < 2.5 && mode.im > 0.0) {
                kept.add(mode);
            }
        }
        Collections.sort(kept, new Comparator<Mode>() {
            @Override
            public int compare(Mode a, Mode b) {
                return Double.compare(a.freqHz, b.freqHz);
            }
        });
        return kept;
    }
}
```

- [ ] **Step 4: Run the harness to verify it passes**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: `ALL CHECKS PASSED`.

- [ ] **Step 5: Commit**

```bash
git add src/my/ramses/ssa/SsaResults.java src/my/ramses/ssa/SsaHarness.java
git commit -m "Load a small-signal run as one object

A basename is any X with an X_modes.dat, which is what lets the viewer open
runs it did not produce. The pf and ms files are optional, since the engine
writes them only when a mode passed real_limit, so their absence loads as
empty rather than failing."
```

---

### Task 4: `PlotSink` with Swing and SVG backends

**Files:**
- Create: `src/my/ramses/ssa/PlotSink.java`
- Create: `src/my/ramses/ssa/SwingSink.java`
- Create: `src/my/ramses/ssa/SvgSink.java`
- Modify: `src/my/ramses/ssa/SsaHarness.java`

**Interfaces:**
- Consumes: nothing.
- Produces: interface `PlotSink` with `group(String id)`, `endGroup()`, `line(double x1,double y1,double x2,double y2,String cls)`, `dashedLine(...same...)`, `circle(double cx,double cy,double r,String cls)`, `cross(double cx,double cy,double r,String cls)`, `arrow(double x1,double y1,double x2,double y2,String cls)`, `text(double x,double y,String s,String anchor,String cls)` where `anchor` is `"start"`, `"middle"` or `"end"`. `SvgSink(int width,int height)`, `SvgSink.toSvg()` returning `String`. `SwingSink(Graphics2D g)`.

- [ ] **Step 1: Write the failing checks**

```java
    private static void checkSvgSinkEmitsEditableElements() {
        SvgSink sink = new SvgSink(400, 300);
        sink.group("poles");
        sink.circle(100, 100, 5, "pole");
        sink.endGroup();
        sink.group("labels");
        sink.text(110, 100, "0.62 Hz", "start", "label");
        sink.endGroup();
        String svg = sink.toSvg();
        expect("declares an svg root", true, svg.contains("<svg "));
        expect("carries the viewport", true, svg.contains("viewBox=\"0 0 400 300\""));
        expect("groups are semantic", true, svg.contains("<g id=\"poles\""));
        expect("labels are real text, not paths", true,
                svg.contains(">0.62 Hz</text>"));
        expect("text is restylable by class", true, svg.contains("class=\"label\""));
        expect("a style block exists so one edit restyles a kind", true,
                svg.contains("<style>"));
        expect("no font is embedded", true, svg.contains("sans-serif"));
        expect("groups are closed", 2, countOf(svg, "</g>"));
    }

    private static void checkSvgSinkEscapes() {
        SvgSink sink = new SvgSink(10, 10);
        sink.text(0, 0, "G1 & G2 <tie>", "middle", "label");
        String svg = sink.toSvg();
        expect("ampersand is escaped", true, svg.contains("G1 &amp; G2"));
        expect("angle brackets are escaped", true, svg.contains("&lt;tie&gt;"));
        expect("no raw bracket leaks into the markup", false, svg.contains("<tie>"));
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
```

Register both in `main()`.

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class SvgSink`.

- [ ] **Step 3: Write `PlotSink`**

```java
package my.ramses.ssa;

/**
 * The drawing primitives both plots need, in device coordinates.
 *
 * <p>The plots draw against this rather than against Graphics2D so that the
 * exported SVG is produced by the same code that painted the screen and
 * cannot drift from it. Styling is carried as a class name rather than as a
 * colour, which is what makes the exported file restylable by editing one
 * rule instead of every element.
 */
interface PlotSink {

    void group(String id);

    void endGroup();

    void line(double x1, double y1, double x2, double y2, String cls);

    void dashedLine(double x1, double y1, double x2, double y2, String cls);

    void circle(double cx, double cy, double r, String cls);

    void cross(double cx, double cy, double r, String cls);

    void arrow(double x1, double y1, double x2, double y2, String cls);

    /** anchor is "start", "middle" or "end". */
    void text(double x, double y, String s, String anchor, String cls);
}
```

- [ ] **Step 4: Write `SvgSink`**

```java
package my.ramses.ssa;

/**
 * A PlotSink that emits SVG written to be edited afterwards: real text
 * elements, semantic groups, and a style block of named classes so changing
 * one hex value restyles every element of a kind at once.
 */
final class SvgSink implements PlotSink {

    private static final String STYLE =
            "    .axis   { stroke: #333333; stroke-width: 1; fill: none; }\n"
            + "    .grid    { stroke: #cccccc; stroke-width: 0.5; fill: none; }\n"
            + "    .bound   { stroke: #dc143c; stroke-width: 1.5; fill: none; }\n"
            + "    .ray     { stroke: #999999; stroke-width: 1; fill: none; }\n"
            + "    .pole    { stroke: #1f77b4; stroke-width: 1.5; fill: none; }\n"
            + "    .unstable{ stroke: #dc143c; stroke-width: 2; fill: none; }\n"
            + "    .shape   { stroke: #1f77b4; stroke-width: 2; fill: none; }\n"
            + "    .label   { fill: #333333; font-size: 11px; stroke: none; }\n"
            + "    .title   { fill: #333333; font-size: 13px; stroke: none; }\n";

    private final StringBuilder body = new StringBuilder();
    private final int width;
    private final int height;
    private int openGroups;

    SvgSink(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void group(String id) {
        body.append("  <g id=\"").append(escape(id)).append("\">\n");
        openGroups++;
    }

    @Override
    public void endGroup() {
        if (openGroups > 0) {
            body.append("  </g>\n");
            openGroups--;
        }
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, String cls) {
        emitLine(x1, y1, x2, y2, cls, null);
    }

    @Override
    public void dashedLine(double x1, double y1, double x2, double y2, String cls) {
        emitLine(x1, y1, x2, y2, cls, "4,3");
    }

    private void emitLine(double x1, double y1, double x2, double y2, String cls,
            String dash) {
        body.append("    <line x1=\"").append(fmt(x1))
                .append("\" y1=\"").append(fmt(y1))
                .append("\" x2=\"").append(fmt(x2))
                .append("\" y2=\"").append(fmt(y2))
                .append("\" class=\"").append(escape(cls)).append('"');
        if (dash != null) {
            body.append(" stroke-dasharray=\"").append(dash).append('"');
        }
        body.append("/>\n");
    }

    @Override
    public void circle(double cx, double cy, double r, String cls) {
        body.append("    <circle cx=\"").append(fmt(cx))
                .append("\" cy=\"").append(fmt(cy))
                .append("\" r=\"").append(fmt(r))
                .append("\" class=\"").append(escape(cls)).append("\"/>\n");
    }

    @Override
    public void cross(double cx, double cy, double r, String cls) {
        emitLine(cx - r, cy - r, cx + r, cy + r, cls, null);
        emitLine(cx - r, cy + r, cx + r, cy - r, cls, null);
    }

    @Override
    public void arrow(double x1, double y1, double x2, double y2, String cls) {
        emitLine(x1, y1, x2, y2, cls, null);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double head = 7.0;
        double spread = Math.toRadians(22.0);
        emitLine(x2, y2, x2 - head * Math.cos(angle - spread),
                y2 - head * Math.sin(angle - spread), cls, null);
        emitLine(x2, y2, x2 - head * Math.cos(angle + spread),
                y2 - head * Math.sin(angle + spread), cls, null);
    }

    @Override
    public void text(double x, double y, String s, String anchor, String cls) {
        body.append("    <text x=\"").append(fmt(x))
                .append("\" y=\"").append(fmt(y))
                .append("\" text-anchor=\"").append(escape(anchor))
                .append("\" class=\"").append(escape(cls)).append("\">")
                .append(escape(s)).append("</text>\n");
    }

    String toSvg() {
        while (openGroups > 0) {
            endGroup();
        }
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
                .append("width=\"").append(width).append("\" ")
                .append("height=\"").append(height).append("\" ")
                .append("viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" ")
                .append("font-family=\"sans-serif\">\n");
        out.append("  <style>\n").append(STYLE).append("  </style>\n");
        out.append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        out.append(body);
        out.append("</svg>\n");
        return out.toString();
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 5: Write `SwingSink`**

```java
package my.ramses.ssa;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * A PlotSink over Graphics2D. Class names map to the same colours the SVG
 * style block declares, so the screen and the exported file agree.
 */
final class SwingSink implements PlotSink {

    private final Graphics2D g;

    SwingSink(Graphics2D g) {
        this.g = g;
    }

    private void style(String cls, boolean dashed) {
        float w = 1.0f;
        Color c = new Color(0x33, 0x33, 0x33);
        if ("grid".equals(cls)) {
            c = new Color(0xcc, 0xcc, 0xcc);
            w = 0.5f;
        } else if ("bound".equals(cls)) {
            c = new Color(0xdc, 0x14, 0x3c);
            w = 1.5f;
        } else if ("ray".equals(cls)) {
            c = new Color(0x99, 0x99, 0x99);
        } else if ("pole".equals(cls)) {
            c = new Color(0x1f, 0x77, 0xb4);
            w = 1.5f;
        } else if ("unstable".equals(cls)) {
            c = new Color(0xdc, 0x14, 0x3c);
            w = 2.0f;
        } else if ("shape".equals(cls)) {
            c = new Color(0x1f, 0x77, 0xb4);
            w = 2.0f;
        }
        g.setColor(c);
        g.setStroke(dashed
                ? new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10.0f, new float[] {4.0f, 3.0f}, 0.0f)
                : new BasicStroke(w));
    }

    @Override
    public void group(String id) {
    }

    @Override
    public void endGroup() {
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, String cls) {
        style(cls, false);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void dashedLine(double x1, double y1, double x2, double y2, String cls) {
        style(cls, true);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void circle(double cx, double cy, double r, String cls) {
        style(cls, false);
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override
    public void cross(double cx, double cy, double r, String cls) {
        style(cls, false);
        g.draw(new Line2D.Double(cx - r, cy - r, cx + r, cy + r));
        g.draw(new Line2D.Double(cx - r, cy + r, cx + r, cy - r));
    }

    @Override
    public void arrow(double x1, double y1, double x2, double y2, String cls) {
        style(cls, false);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double head = 7.0;
        double spread = Math.toRadians(22.0);
        g.draw(new Line2D.Double(x2, y2, x2 - head * Math.cos(angle - spread),
                y2 - head * Math.sin(angle - spread)));
        g.draw(new Line2D.Double(x2, y2, x2 - head * Math.cos(angle + spread),
                y2 - head * Math.sin(angle + spread)));
    }

    @Override
    public void text(double x, double y, String s, String anchor, String cls) {
        style(cls, false);
        g.setColor(new Color(0x33, 0x33, 0x33));
        int w = g.getFontMetrics().stringWidth(s);
        double dx = "middle".equals(anchor) ? -w / 2.0 : "end".equals(anchor) ? -w : 0.0;
        g.drawString(s, (float) (x + dx), (float) y);
    }
}
```

- [ ] **Step 6: Run the harness to verify it passes**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: `ALL CHECKS PASSED`.

- [ ] **Step 7: Commit**

```bash
git add src/my/ramses/ssa/PlotSink.java src/my/ramses/ssa/SwingSink.java \
        src/my/ramses/ssa/SvgSink.java src/my/ramses/ssa/SsaHarness.java
git commit -m "Draw plots through a sink with Swing and SVG backends

One drawing path for both the screen and the file, so an exported figure
cannot drift from what was on screen. Styling travels as a class name rather
than a colour, which is what lets the saved SVG be restyled by editing one
rule, and every label is a real text element rather than an outlined path."
```

---

### Task 5: `SplanePanel`

**Files:**
- Create: `src/my/ramses/ssa/SplanePanel.java`
- Modify: `src/my/ramses/ssa/SsaHarness.java`

**Interfaces:**
- Consumes: `PlotSink`, `SvgSink`, `SwingSink`, `Mode`, `SsaResults.electromechanical`.
- Produces: `SplanePanel(List<Mode> shown)`; `setModes(List<Mode>)`; `setSelected(Mode)`; `selected()` returning `Mode` or null; `addSelectionListener(SplanePanel.Listener)` where `Listener` has `void poleSelected(Mode mode)`; `toSvg(int width,int height)` returning `String`; `static void render(PlotSink sink, List<Mode> shown, Mode selected, int width, int height)`.

- [ ] **Step 1: Write the failing checks**

Geometry is checked by rendering into an `SvgSink` and asserting on the element set. This is why the sink abstraction earns its place: it makes the drawing testable with no display.

```java
    private static java.util.List<Mode> emFixture() {
        SsaModes m = parsedModes();
        return SsaResults.electromechanical(m.modes());
    }

    private static void checkSplaneRendersExpectedElements() {
        SvgSink sink = new SvgSink(500, 400);
        SplanePanel.render(sink, emFixture(), null, 500, 400);
        String svg = sink.toSvg();
        expect("the stability boundary is drawn", true, svg.contains("class=\"bound\""));
        expect("constant-damping rays are dashed", true,
                svg.contains("stroke-dasharray"));
        expect("both rays are drawn", 2, countOf(svg, "class=\"ray\""));
        expect("one circle per mode shown", 2, countOf(svg, "class=\"pole\""));
        expect("the unstable mode gets a cross, which is two lines", 2,
                countOf(svg, "class=\"unstable\""));
        expect("frequencies are labelled", true, svg.contains("0.62 Hz"));
        expect("axes are labelled", true, svg.contains("Re"));
        expect("the pole group is present", true, svg.contains("<g id=\"poles\""));
    }

    private static void checkSplaneMinimumExtentExpands() {
        // A mode outside the notebook's window must still be inside the axes.
        java.util.List<Mode> wide = new java.util.ArrayList<Mode>();
        wide.add(new Mode(1, -12.0, 40.0, 0.29, 6.37, true, true));
        SvgSink sink = new SvgSink(500, 400);
        SplanePanel.render(sink, wide, null, 500, 400);
        String svg = sink.toSvg();
        expect("a mode beyond the default window still draws one pole", 1,
                countOf(svg, "class=\"pole\""));
    }

    private static void checkSplaneMarksSelection() {
        java.util.List<Mode> em = emFixture();
        SvgSink sink = new SvgSink(500, 400);
        SplanePanel.render(sink, em, em.get(0), 500, 400);
        String svg = sink.toSvg();
        expect("the selected pole is ringed", true, svg.contains("<g id=\"selected\""));
    }
```

`Mode`'s constructor is package-private, and `SsaHarness` is in the same package, so the `wide` fixture compiles.

Register all three in `main()`.

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class SplanePanel`.

- [ ] **Step 3: Write `SplanePanel`**

```java
package my.ramses.ssa;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * The s-plane, following the python-ui notebook's cell 20: a crimson
 * stability boundary at Re = 0, dashed constant-damping rays, one hollow
 * circle per mode labelled with its frequency, and unstable modes
 * overplotted as a crimson cross.
 *
 * <p>The notebook's window is the MINIMUM extent, not the fixed one, so this
 * works on systems other than Kundur. Unlike the notebook it is interactive:
 * clicking a pole selects that mode, and the selection is ringed.
 */
public final class SplanePanel extends JPanel {

    /** Notified when the user clicks a pole. */
    public interface Listener {
        void poleSelected(Mode mode);
    }

    private static final double MIN_RE = -3.0;
    private static final double MAX_RE = 0.5;
    private static final double MIN_IM = 0.0;
    private static final double MAX_IM = 9.0;
    private static final int PAD_LEFT = 60;
    private static final int PAD_RIGHT = 20;
    private static final int PAD_TOP = 20;
    private static final int PAD_BOTTOM = 45;
    private static final double POLE_R = 5.0;

    private List<Mode> shown = new ArrayList<Mode>();
    private Mode selected;
    private final List<Listener> listeners = new ArrayList<Listener>();

    public SplanePanel() {
        setPreferredSize(new Dimension(460, 360));
        setBackground(java.awt.Color.WHITE);
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                Mode hit = modeAt(event.getX(), event.getY(), getWidth(), getHeight());
                if (hit != null) {
                    setSelected(hit);
                    for (Listener listener : listeners) {
                        listener.poleSelected(hit);
                    }
                }
            }
        });
    }

    public void addSelectionListener(Listener listener) {
        listeners.add(listener);
    }

    public void setModes(List<Mode> modes) {
        this.shown = new ArrayList<Mode>(modes);
        this.selected = null;
        repaint();
    }

    public void setSelected(Mode mode) {
        this.selected = mode;
        repaint();
    }

    public Mode selected() {
        return selected;
    }

    public String toSvg(int width, int height) {
        SvgSink sink = new SvgSink(width, height);
        render(sink, shown, selected, width, height);
        return sink.toSvg();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        render(new SwingSink(g), shown, selected, getWidth(), getHeight());
        g.dispose();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Mode hit = modeAt(event.getX(), event.getY(), getWidth(), getHeight());
        if (hit == null) {
            return null;
        }
        return String.format(java.util.Locale.ROOT,
                "mode %d: %.4f Hz, zeta %+.4f, lambda %+.4f %+.4fj",
                hit.index, hit.freqHz, hit.zeta, hit.re, hit.im);
    }

    private Mode modeAt(int px, int py, int width, int height) {
        Bounds b = bounds(shown, width, height);
        for (Mode mode : shown) {
            double x = b.x(mode.re);
            double y = b.y(mode.im);
            if (Math.hypot(px - x, py - y) <= POLE_R + 3.0) {
                return mode;
            }
        }
        return null;
    }

    /** The axis window: the notebook's, expanded to hold everything shown. */
    private static Bounds bounds(List<Mode> shown, int width, int height) {
        double lo = MIN_RE;
        double hi = MAX_RE;
        double top = MAX_IM;
        for (Mode mode : shown) {
            lo = Math.min(lo, mode.re * 1.1);
            hi = Math.max(hi, mode.re * 1.1);
            top = Math.max(top, mode.im * 1.1);
        }
        return new Bounds(lo, hi, MIN_IM, top, width, height);
    }

    static void render(PlotSink sink, List<Mode> shown, Mode selected,
            int width, int height) {
        Bounds b = bounds(shown, width, height);

        sink.group("axes");
        sink.line(b.x(b.reLo), b.y(b.imLo), b.x(b.reHi), b.y(b.imLo), "axis");
        sink.line(b.x(b.reLo), b.y(b.imLo), b.x(b.reLo), b.y(b.imHi), "axis");
        for (int step = 1; step <= 4; step++) {
            double im = b.imLo + (b.imHi - b.imLo) * step / 4.0;
            sink.line(b.x(b.reLo), b.y(im), b.x(b.reHi), b.y(im), "grid");
        }
        sink.text((b.x(b.reLo) + b.x(b.reHi)) / 2.0, height - 12.0,
                "Re(lambda)  [1/s]", "middle", "label");
        sink.text(14.0, (b.y(b.imLo) + b.y(b.imHi)) / 2.0,
                "Im(lambda)  [rad/s]", "middle", "label");
        sink.endGroup();

        // The stability boundary. Everything strictly left of this is stable.
        sink.group("boundary");
        sink.line(b.x(0.0), b.y(b.imLo), b.x(0.0), b.y(b.imHi), "bound");
        sink.endGroup();

        // Constant-damping rays, as in the notebook: from the origin along
        // Re = -zeta*r, Im = r*sqrt(1 - zeta^2).
        sink.group("damping-rays");
        for (double zeta : new double[] {0.05, 0.10}) {
            double r = b.imHi / Math.sqrt(1.0 - zeta * zeta);
            sink.dashedLine(b.x(0.0), b.y(0.0), b.x(-zeta * r),
                    b.y(r * Math.sqrt(1.0 - zeta * zeta)), "ray");
        }
        sink.endGroup();

        sink.group("poles");
        for (Mode mode : shown) {
            sink.circle(b.x(mode.re), b.y(mode.im), POLE_R, "pole");
        }
        sink.endGroup();

        sink.group("unstable");
        for (Mode mode : shown) {
            if (mode.zeta < 0.0) {
                sink.cross(b.x(mode.re), b.y(mode.im), POLE_R + 2.0, "unstable");
            }
        }
        sink.endGroup();

        if (selected != null) {
            sink.group("selected");
            sink.circle(b.x(selected.re), b.y(selected.im), POLE_R + 4.0, "pole");
            sink.endGroup();
        }

        sink.group("labels");
        for (Mode mode : shown) {
            sink.text(b.x(mode.re) + POLE_R + 3.0, b.y(mode.im) - 4.0,
                    String.format(java.util.Locale.ROOT, "%.2f Hz", mode.freqHz),
                    "start", "label");
        }
        sink.endGroup();
    }

    /** Data-to-device mapping for one render. */
    private static final class Bounds {

        private final double reLo;
        private final double reHi;
        private final double imLo;
        private final double imHi;
        private final int width;
        private final int height;

        Bounds(double reLo, double reHi, double imLo, double imHi,
                int width, int height) {
            this.reLo = reLo;
            this.reHi = reHi;
            this.imLo = imLo;
            this.imHi = imHi;
            this.width = width;
            this.height = height;
        }

        double x(double re) {
            double span = reHi - reLo;
            double frac = span == 0.0 ? 0.0 : (re - reLo) / span;
            return PAD_LEFT + frac * (width - PAD_LEFT - PAD_RIGHT);
        }

        double y(double im) {
            double span = imHi - imLo;
            double frac = span == 0.0 ? 0.0 : (im - imLo) / span;
            return height - PAD_BOTTOM - frac * (height - PAD_TOP - PAD_BOTTOM);
        }
    }
}
```

- [ ] **Step 4: Run the harness to verify it passes**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: `ALL CHECKS PASSED`.

- [ ] **Step 5: Commit**

```bash
git add src/my/ramses/ssa/SplanePanel.java src/my/ramses/ssa/SsaHarness.java
git commit -m "Draw the s-plane

Follows the python-ui notebook's cell 20, with the notebook's window as the
minimum extent rather than a fixed one so other systems fit. Clicking a pole
selects the mode, which the notebook cannot do. Geometry is pinned by
rendering into an SvgSink and asserting on the element set, so it is testable
without a display."
```

---

### Task 6: `ModeShapePanel`

**Files:**
- Create: `src/my/ramses/ssa/ModeShapePanel.java`
- Modify: `src/my/ramses/ssa/SsaHarness.java`

**Interfaces:**
- Consumes: `PlotSink`, `SvgSink`, `SwingSink`, `ModeShapeEntry`.
- Produces: `ModeShapePanel()`; `show(List<ModeShapeEntry> entries, int modeIndex, boolean simple)`; `toSvg(int width,int height)`; `static void render(PlotSink sink, List<ModeShapeEntry> entries, boolean simple, int width, int height)`.

- [ ] **Step 1: Write the failing checks**

```java
    private static java.util.List<ModeShapeEntry> shapeFixture() {
        try {
            return SsaModeShapes.parse(join(MS_LINES)).forMode(2);
        } catch (java.io.IOException ex) {
            return new java.util.ArrayList<ModeShapeEntry>();
        }
    }

    private static void checkModeShapeRendersArrows() {
        SvgSink sink = new SvgSink(360, 360);
        ModeShapePanel.render(sink, shapeFixture(), true, 360, 360);
        String svg = sink.toSvg();
        // One arrow is three lines: the shaft and two head strokes.
        expect("two machines give six arrow strokes", 6,
                countOf(svg, "class=\"shape\""));
        expect("machines are labelled", true, svg.contains("AREA 1 G1"));
        expect("magnitude rings are drawn", 2, countOf(svg, "class=\"grid\""));
        expect("the dial group is present", true, svg.contains("<g id=\"arrows\""));
    }

    private static void checkModeShapeRefusesDegenerate() {
        SvgSink sink = new SvgSink(360, 360);
        ModeShapePanel.render(sink, shapeFixture(), false, 360, 360);
        String svg = sink.toSvg();
        expect("a degenerate mode draws no arrows", 0,
                countOf(svg, "class=\"shape\""));
        expect("and says why", true, svg.contains("degenerate"));
    }
```

Register both in `main()`.

- [ ] **Step 2: Run to verify it fails**

Run: `ant compile`
Expected: FAIL, `cannot find symbol: class ModeShapePanel`.

- [ ] **Step 3: Write `ModeShapePanel`**

```java
package my.ramses.ssa;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * The mode-shape dial, following the python-ui notebook's cell 17: an arrow
 * per machine from the origin to (angle, magnitude), labelled at 1.12 times
 * its magnitude, with rmax 1.3.
 *
 * <p>For a degenerate mode this refuses to draw. In a degenerate eigenspace
 * the individual eigenvectors are not unique, so the picture would be
 * basis-dependent: it would come out differently on another machine while
 * looking exactly as authoritative. Refusing is the honest option.
 */
public final class ModeShapePanel extends JPanel {

    private static final double R_MAX = 1.3;

    private List<ModeShapeEntry> entries = new ArrayList<ModeShapeEntry>();
    private boolean simple = true;
    private int modeIndex;

    public ModeShapePanel() {
        setPreferredSize(new Dimension(360, 320));
        setBackground(java.awt.Color.WHITE);
    }

    public void show(List<ModeShapeEntry> entries, int modeIndex, boolean simple) {
        this.entries = new ArrayList<ModeShapeEntry>(entries);
        this.modeIndex = modeIndex;
        this.simple = simple;
        repaint();
    }

    public int modeIndex() {
        return modeIndex;
    }

    public String toSvg(int width, int height) {
        SvgSink sink = new SvgSink(width, height);
        render(sink, entries, simple, width, height);
        return sink.toSvg();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        render(new SwingSink(g), entries, simple, getWidth(), getHeight());
        g.dispose();
    }

    static void render(PlotSink sink, List<ModeShapeEntry> entries, boolean simple,
            int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double radius = Math.min(width, height) / 2.0 - 30.0;

        if (!simple) {
            sink.group("refusal");
            sink.text(cx, cy - 8.0, "This mode is degenerate (simple = 0).",
                    "middle", "title");
            sink.text(cx, cy + 12.0,
                    "Its eigenvectors are not unique, so a mode shape would be",
                    "middle", "label");
            sink.text(cx, cy + 28.0,
                    "basis-dependent and would differ on another machine.",
                    "middle", "label");
            sink.endGroup();
            return;
        }

        sink.group("rings");
        for (double r : new double[] {0.5, 1.0}) {
            sink.circle(cx, cy, radius * r / R_MAX, "grid");
        }
        sink.endGroup();

        sink.group("spokes");
        for (int deg = 0; deg < 360; deg += 30) {
            double th = Math.toRadians(deg);
            sink.line(cx, cy, cx + radius * Math.cos(th), cy - radius * Math.sin(th),
                    "axis");
        }
        sink.endGroup();

        sink.group("arrows");
        for (ModeShapeEntry entry : entries) {
            double th = Math.toRadians(entry.angleDeg);
            double rr = radius * entry.magnitude / R_MAX;
            sink.arrow(cx, cy, cx + rr * Math.cos(th), cy - rr * Math.sin(th), "shape");
        }
        sink.endGroup();

        sink.group("labels");
        for (ModeShapeEntry entry : entries) {
            double th = Math.toRadians(entry.angleDeg);
            double rr = radius * entry.magnitude * 1.12 / R_MAX;
            sink.text(cx + rr * Math.cos(th), cy - rr * Math.sin(th),
                    entry.device.trim(), "middle", "label");
        }
        sink.endGroup();
    }
}
```

- [ ] **Step 4: Run the harness to verify it passes**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: `ALL CHECKS PASSED`.

- [ ] **Step 5: Commit**

```bash
git add src/my/ramses/ssa/ModeShapePanel.java src/my/ramses/ssa/SsaHarness.java
git commit -m "Draw the mode-shape dial

Follows the python-ui notebook's cell 17. For a degenerate mode it draws the
reason instead of arrows: the eigenvectors are not unique there, so the
picture would be basis-dependent and would come out differently elsewhere
while looking just as authoritative."
```

---

### Task 7: `SsaResultsWindow`

**Files:**
- Create: `src/my/ramses/ssa/SsaResultsWindow.java`

**Interfaces:**
- Consumes: everything above.
- Produces: `SsaResultsWindow(SsaResults results)`; `static void open(java.awt.Component parent, SsaResults results)` which constructs, positions relative to `parent` and shows.

This task has no harness checks. The parsers and both plots are already pinned; what remains is layout and wiring, which is covered by the manual acceptance run in Task 9. Adding assertions over Swing containers here would pin the layout without testing behaviour.

- [ ] **Step 1: Write `SsaResultsWindow`**

```java
package my.ramses.ssa;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/**
 * One small-signal run, displayed. Non-modal and independent, so several can
 * be open at once: comparing a case with and without its stabilisers means
 * putting two of these side by side, which is what the notebook does with
 * two subplots.
 */
public final class SsaResultsWindow extends JFrame {

    private final SsaResults results;
    private final ModesTableModel model;
    private final JTable table;
    private final SplanePanel splane = new SplanePanel();
    private final ModeShapePanel shape = new ModeShapePanel();
    private final JPanel participation = new JPanel();
    private final JCheckBox emOnly =
            new JCheckBox("electromechanical only (0.1 to 2.5 Hz)", true);

    public static void open(Component parent, SsaResults results) {
        SsaResultsWindow window = new SsaResultsWindow(results);
        window.setLocationRelativeTo(parent);
        window.setVisible(true);
    }

    public SsaResultsWindow(SsaResults results) {
        super("Small-signal results - " + results.basename()
                + " - " + results.directory().getAbsolutePath());
        this.results = results;
        this.model = new ModesTableModel(SsaResults.electromechanical(
                results.modes().modes()));
        this.table = new JTable(model);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(new TableRowSorter<ModesTableModel>(model));
        table.setDefaultRenderer(Object.class, new ModeCellRenderer(model, table));
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    showSelected();
                }
            }
        });

        emOnly.addActionListener(event -> {
            model.setRows(emOnly.isSelected()
                    ? SsaResults.electromechanical(results.modes().modes())
                    : results.modes().modes());
            splane.setModes(model.rows());
            clearDetail();
        });

        JPanel left = new JPanel(new BorderLayout());
        left.add(emOnly, BorderLayout.NORTH);
        left.add(new JScrollPane(table), BorderLayout.CENTER);

        splane.setModes(model.rows());
        splane.addSelectionListener(mode -> selectInTable(mode));

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left,
                withSaveButton(splane, "s-plane", () -> splane.toSvg(
                        Math.max(splane.getWidth(), 500),
                        Math.max(splane.getHeight(), 400))));
        top.setResizeWeight(0.45);

        participation.setLayout(new BoxLayout(participation, BoxLayout.Y_AXIS));
        JScrollPane participationScroll = new JScrollPane(participation);
        participationScroll.setBorder(BorderFactory.createTitledBorder("Participation"));

        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                participationScroll,
                withSaveButton(shape, "mode-shape", () -> shape.toSvg(
                        Math.max(shape.getWidth(), 400),
                        Math.max(shape.getHeight(), 400))));
        bottom.setResizeWeight(0.45);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        clearDetail();
        setPreferredSize(new Dimension(1080, 780));
        pack();
    }

    private JPanel header() {
        SsaModes m = results.modes();
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        panel.add(new JLabel(results.directory().getAbsolutePath()
                + "    basename " + results.basename()
                + "    " + m.nstates() + " states, " + m.nalg() + " algebraic"));
        panel.add(new JLabel("real_limit " + show(m.realLimit())
                + "    pf_threshold " + show(m.pfThreshold())
                + "    gap_tol " + show(m.gapTol())
                + "    t = " + show(m.time())));
        return panel;
    }

    private static String show(Double value) {
        return value == null ? "not recorded"
                : String.format(java.util.Locale.ROOT, "%g", value);
    }

    private JPanel withSaveButton(Component plot, String what,
            java.util.function.Supplier<String> svg) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(plot, BorderLayout.CENTER);
        JButton save = new JButton("Save plot...");
        save.addActionListener(event -> saveSvg(what, svg.get()));
        JPanel bar = new JPanel();
        bar.add(save);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private void saveSvg(String what, String svg) {
        JFileChooser chooser = new JFileChooser(results.directory());
        chooser.setDialogTitle("Save " + what + " as SVG");
        chooser.setSelectedFile(new File(results.basename() + "-" + what + ".svg"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
            target = new File(target.getParentFile(), target.getName() + ".svg");
        }
        try {
            Files.write(target.toPath(), svg.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not write " + target + "\n\n" + ex.getMessage(),
                    "Save plot", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectInTable(Mode mode) {
        for (int row = 0; row < table.getRowCount(); row++) {
            int modelRow = table.convertRowIndexToModel(row);
            if (model.rows().get(modelRow).index == mode.index) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private void clearDetail() {
        participation.removeAll();
        participation.add(new JLabel("  Select a mode."));
        participation.revalidate();
        participation.repaint();
        shape.show(new java.util.ArrayList<ModeShapeEntry>(), 0, true);
    }

    private void showSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            clearDetail();
            return;
        }
        Mode mode = model.rows().get(table.convertRowIndexToModel(row));
        splane.setSelected(mode);
        participation.removeAll();

        if (!mode.simple) {
            // Same refusal the dial makes, for the same reason.
            participation.add(new JLabel("  Mode " + mode.index
                    + " is degenerate (simple = 0)."));
            participation.add(new JLabel("  Its eigenvectors are not unique, so its"
                    + " participation factors are basis-dependent"));
            participation.add(new JLabel("  and would come out differently on another"
                    + " machine. Not shown."));
        } else {
            List<Participation> rows = results.participation().forMode(mode.index);
            if (rows.isEmpty()) {
                participation.add(new JLabel("  Mode " + mode.index
                        + " was filtered out by real_limit ("
                        + show(results.modes().realLimit())
                        + "), so no participation factors were written."));
            } else {
                participation.add(new JLabel("  Mode " + mode.index + ", "
                        + String.format(java.util.Locale.ROOT, "%.4f Hz", mode.freqHz)));
                for (Participation p : rows) {
                    participation.add(new JLabel(String.format(java.util.Locale.ROOT,
                            "   %-8s %-20s %-10s %.3f",
                            p.family, p.device.trim(), p.variable, p.pf)));
                }
                participation.add(new JLabel("  Entries below pf_threshold "
                        + show(results.modes().pfThreshold())
                        + " are not written, so an absent device is below it,"
                        + " not zero."));
            }
        }
        participation.revalidate();
        participation.repaint();
        shape.show(mode.simple
                ? results.shapes().forMode(mode.index)
                : new java.util.ArrayList<ModeShapeEntry>(),
                mode.index, mode.simple);
    }

    /** The modes table. */
    private static final class ModesTableModel extends AbstractTableModel {

        private static final String[] COLUMNS =
                {"#", "f [Hz]", "zeta", "Re", "Im", "simple"};

        private List<Mode> rows;

        ModesTableModel(List<Mode> rows) {
            this.rows = rows;
        }

        List<Mode> rows() {
            return rows;
        }

        void setRows(List<Mode> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : column == 5 ? String.class : Double.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Mode mode = rows.get(row);
            switch (column) {
                case 0: return Integer.valueOf(mode.index);
                case 1: return Double.valueOf(mode.freqHz);
                case 2: return Double.valueOf(mode.zeta);
                case 3: return Double.valueOf(mode.re);
                case 4: return Double.valueOf(mode.im);
                default: return mode.simple ? "yes" : "NO";
            }
        }
    }

    /** Renders negative damping in red, and a degenerate flag in amber. */
    private static final class ModeCellRenderer
            extends javax.swing.table.DefaultTableCellRenderer {

        private final ModesTableModel model;
        private final JTable owner;

        ModeCellRenderer(ModesTableModel model, JTable owner) {
            this.model = model;
            this.owner = owner;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean selected, boolean focused, int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, selected,
                    focused, row, column);
            Mode mode = model.rows().get(owner.convertRowIndexToModel(row));
            if (value instanceof Double) {
                setText(String.format(java.util.Locale.ROOT, "%+.4f",
                        ((Double) value).doubleValue()));
                setHorizontalAlignment(RIGHT);
            }
            if (!selected) {
                c.setForeground(mode.zeta < 0.0 ? new java.awt.Color(0xdc, 0x14, 0x3c)
                        : !mode.simple ? new java.awt.Color(0xb0, 0x7d, 0x1a)
                        : java.awt.Color.BLACK);
            }
            return c;
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: build succeeds, `ALL CHECKS PASSED` (unchanged from Task 6).

- [ ] **Step 3: Commit**

```bash
git add src/my/ramses/ssa/SsaResultsWindow.java
git commit -m "Show one small-signal run in a window

Non-modal and independent so several can be open at once, which is how a
case with and without its stabilisers gets compared side by side. The
participation panel makes the same degenerate refusal the dial does, and
states that an absent device is below pf_threshold rather than zero."
```

---

### Task 8: `RamsesUI` behaviour

**Files:**
- Modify: `src/my/ramses/RamsesUI.java:3094-3153` and `:3167`
- Delete: `src/my/ramses/ssaEig.dst`

**Interfaces:**
- Consumes: `SsaResults`, `SsaResultsWindow`.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Replace `ssaButton1ActionPerformed`**

Replace the body at `RamsesUI.java:3094-3153` with:

```java
    private void ssaButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ssaButton1ActionPerformed
        // Runs the analysis in the engine. RAMSES reduces the linearised model
        // to a state matrix, solves the eigenproblem and writes the three
        // results files itself, so nothing external is invoked.
        //
        // The .dst is generated rather than copied from a bundled resource,
        // because the basename is now the user's to choose: the EIG argument
        // names all three output files, so a per-run basename is what lets
        // several runs share one directory.
        try {
            String base = ssaBasename.getText().trim();
            if (base.isEmpty()) {
                base = "ssa";
                ssaBasename.setText(base);
            }
            File dstFile = new File(myTempDir.getAbsolutePath()
                    + System.getProperty("file.separator") + base + "Eig.dst");
            FileUtils.writeStringToFile(dstFile,
                    "0.000 CONTINUE SOLVER TR 0.010 0.001 0. ALL\n"
                    + "0.000 EIG '" + base + "'\n"
                    + "0.010 STOP\n", "UTF-8");

            String tmpString = fileDist.getText();
            fileDist.setText(dstFile.getName());
            ssa = true;
            runSimulationActionPerformed(evt);
            simulExecutorResultHandler.waitFor();
            fileDist.setText(tmpString);

            // The analysis refuses rather than guessing when it cannot produce a
            // result it can justify, and says so through the exit code, so a
            // missing modes file is reported instead of failing silently later.
            File modes = new File(myTempDir.getAbsolutePath()
                    + System.getProperty("file.separator") + base + "_modes.dat");
            if (!modes.exists()) {
                JOptionPane.showMessageDialog(this,
                        "No results were produced. Small-signal analysis needs $OMEGA_REF SYN and\n"
                        + "$SCHEME DE in the solver settings, and a system within $EIG_MAX_STATES.\n"
                        + "See the log for the reason.",
                        "Small-signal analysis", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String[] produced = {base + "_modes.dat", base + "_pf.dat", base + "_ms.dat"};
            File resultsDir = new File(myTempDir.getAbsolutePath());
            if (!"".equals(ssaDirectory.getText())) {
                for (String name : produced) {
                    File srcFile = new File(myTempDir.getAbsolutePath()
                            + System.getProperty("file.separator") + name);
                    File dstCopy = new File(ssaDirectory.getText()
                            + System.getProperty("file.separator") + name);
                    if (srcFile.exists()) {
                        fileOps.copyFiletoFile(srcFile, dstCopy);
                    }
                }
                resultsDir = new File(ssaDirectory.getText());
            }

            // Opened from wherever the files actually are. With no results
            // directory set they stay in the tool directory, and the window
            // header names that path, so a successful run can no longer look
            // identical to a no-op.
            showSsaResults(resultsDir, base);
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_ssaButton1ActionPerformed

    /** Loads one run and shows it, reporting a parse failure rather than swallowing it. */
    private void showSsaResults(File dir, String basename) {
        try {
            my.ramses.ssa.SsaResultsWindow.open(this,
                    my.ramses.ssa.SsaResults.load(dir, basename));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not read the results in " + dir + "\n\n" + ex.getMessage(),
                    "Small-signal results", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void viewSsaResultsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewSsaResultsActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setDialogTitle("Choose a directory containing small-signal results");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dir = fileChooser.getSelectedFile();
        java.util.List<String> found = my.ramses.ssa.SsaResults.basenames(dir);
        if (found.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No small-signal results in " + dir + "\n\n"
                    + "Looking for a file named <basename>_modes.dat.",
                    "Small-signal results", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String chosen = found.get(0);
        if (found.size() > 1) {
            Object picked = JOptionPane.showInputDialog(this,
                    "That directory holds more than one run. Which one?",
                    "Small-signal results", JOptionPane.QUESTION_MESSAGE, null,
                    found.toArray(), found.get(0));
            if (picked == null) {
                return;
            }
            chosen = (String) picked;
        }
        showSsaResults(dir, chosen);
    }//GEN-LAST:event_viewSsaResultsActionPerformed
```

- [ ] **Step 2: Remove the fictional button gate**

At `RamsesUI.java:3167`, inside `ssaButtonActionPerformed`, delete the line:

```java
            ssaButton1.setEnabled(true);
```

The Jacobian dump and the small-signal analysis are unrelated: `ssa.f90` opens files only with `status='replace'` and never reads one, so `EIG` assembles and reduces its own Jacobian in memory and has never consumed `jac_*.dat`.

- [ ] **Step 3: Delete the bundled disturbance resource**

```bash
git rm src/my/ramses/ssaEig.dst
```

Its three references at `:3100`, `:3101` and `:3108` are gone with the rewrite in Step 1. `dampJac.dst` stays.

- [ ] **Step 4: Verify it compiles and the harness still passes**

Run: `ant compile` then `tools/ssa-harness.sh`
Expected: build succeeds, `ALL CHECKS PASSED`.

Note: this will not compile until Task 9 adds `ssaBasename` and the `viewSsaResults` button to the form. Do Task 9 before running, or expect `cannot find symbol: ssaBasename` and complete both before committing.

- [ ] **Step 5: Commit together with Task 9**

These two tasks are one compilable unit. Commit after Task 9's step 4.

---

### Task 9: Form changes, README correction, acceptance run

**Files:**
- Modify: `src/my/ramses/RamsesUI.form` and the generated block in `src/my/ramses/RamsesUI.java`
- Modify: `examples/kundur-ssa/README.md`

- [ ] **Step 1: Add the controls in NetBeans**

Open the project in NetBeans and edit `jPanel8` in the designer, so the `.form` and `initComponents()` stay in step. Add, in the Small Signal Stability group:

| Control | Name | Text | Enabled |
|---|---|---|---|
| `JButton` | `viewSsaResults` | `View results...` | yes |
| `JLabel` | `ssaBasenameLabel` | `Results basename` | yes |
| `JTextField` | `ssaBasename` | `ssa` | yes |
| `JLabel` | `ssaRealLimitLabel` | `Real part limit` | no |
| `JTextField` | `ssaRealLimit` | `-1.0` | no |
| `JLabel` | `ssaPfThresholdLabel` | `PF threshold` | no |
| `JTextField` | `ssaPfThreshold` | `0.05` | no |

Wire `viewSsaResults`' `actionPerformed` to `viewSsaResultsActionPerformed`.

Set this tooltip on both disabled fields:

```
Needs a RAMSES release whose EIG disturbance accepts these values. The
running engine does not, and would ignore them silently, so they are
disabled rather than misleading.
```

- [ ] **Step 2: Fix the two colliding labels**

On `loadSSADir`, change the text from `Select Working Directory` to `Select results directory`, and the tooltip from `Select directory to extract Jacobian matrix` to `Directory the Jacobian and small-signal results are copied to`.

`selWorkDirButton` in the Tools menu keeps `Select Working Directory`, which is now the only control with that label.

- [ ] **Step 3: Remove the initial disable of the SSSA button**

In the designer, set `ssaButton1`'s `enabled` property to true, which removes `ssaButton1.setEnabled(false);` from `initComponents()` at `:1895`.

- [ ] **Step 4: Build and run the harness**

Run: `ant clean` then `ant jar` then `tools/ssa-harness.sh`
Expected: build succeeds, `ALL CHECKS PASSED`.

- [ ] **Step 5: Manual acceptance against the published numbers**

This is the only check that exercises the engine, the parsers and the window together. `examples/kundur-ssa/README.md` records the expected answer, so the window is checked against a published number rather than against itself.

1. Launch: `java -jar dist/stepss.jar`
2. Load `examples/kundur-ssa/lf.dat`, `dyn.dat` and `solveroptions.dat` as system data, and `obs.dat` as observables.
3. Initialise.
4. Confirm **Perform small signal stability analysis** is enabled **without** pressing Extract Jacobian matrix first. This is defect 2 fixed.
5. Press it with no results directory set. Confirm a window opens and its header names the tool directory. This is defect 1 fixed.
6. Confirm the inter-area mode reads 0.6237 Hz at zeta +0.1087, and the two local modes 1.2424 and 1.2950 Hz at zeta 0.288 and 0.287.
7. Select the inter-area mode. Confirm participation lists all four machines and the dial shows four arrows.
8. Save both plots as SVG. Open each in a text editor and confirm the labels are `<text>` elements and the `<style>` block is present. Open one in Inkscape or a browser and confirm it renders.
9. Swap `dyn.dat` for `dyn_noPSS.dat` and repeat. Confirm the inter-area mode now reads 0.6246 Hz at zeta -0.0233, rendered red, and that it is marked unstable on the s-plane with a crimson cross.
10. Confirm both windows can be open at once.
11. Press **View results...**, choose the directory, and confirm it loads without re-running.

- [ ] **Step 6: Correct the stale version note**

In `examples/kundur-ssa/README.md`, delete the "Requires a RAMSES newer than 3.60" section. `versions.properties` pins `ramses.version=3.72` and the example works. Replace the pointer in the "What to do" section that refers to it.

In the same file, update the STEPSS-window instructions, which currently say the results appear in the directory and stop there, to say that a results window opens showing the modes, participation, mode shape and s-plane, and that the plots save as SVG.

- [ ] **Step 7: Commit**

```bash
git add src/my/ramses/RamsesUI.java src/my/ramses/RamsesUI.form \
        examples/kundur-ssa/README.md
git rm --cached src/my/ramses/ssaEig.dst
git commit -m "Show small-signal results instead of writing files silently

The Analysis tab now opens a results window after a run and can reopen any
directory holding a <basename>_modes.dat, which reaches runs made from a
terminal and from earlier sessions.

Three defects go with it. The SSSA button was disabled until the Jacobian
dump had run, a dependency that never existed: EIG assembles and reduces its
own Jacobian in memory and ssa.f90 never reads a file. Results are now loaded
from wherever they actually landed, so a run with no results directory set no
longer looks identical to a no-op. And loadSSADir no longer carries the same
label as the Tools menu item that does something else.

The basename is now the user's to choose, so the disturbance is generated
rather than copied from a bundled resource, and several runs can share one
directory.

The real_limit and pf_threshold fields are laid out but disabled: the EIG
record takes neither, and a list-directed read of two items means an engine
without them ignores extra fields silently."
```

---

## Self-review

**Spec coverage.** Every section of the spec maps to a task: parsing to Tasks 1 to 3, the window to Task 7, plots to Tasks 4 to 6, SVG export to Task 4 and Task 7's save button, the three defects to Tasks 8 and 9, the parameter fields to Task 9, testing to the harness threaded through Tasks 1 to 6 plus Task 9's acceptance run, and the stale README note to Task 9 step 6.

**Two gaps found and closed while reviewing.** The spec's "several prompt for which" behaviour had no task; it is now Task 8's `viewSsaResultsActionPerformed`. The spec says `_pf.dat` and `_ms.dat` are optional; Task 3's `load` now parses an empty string rather than failing when they are absent.

**Type consistency.** `SsaResults.electromechanical` is static and takes `List<Mode>` in Tasks 3, 5 and 7. `SplanePanel.render` and `ModeShapePanel.render` are both package-private statics taking `(PlotSink, ..., int width, int height)`. `PlotSink.text` takes `anchor` before `cls` everywhere. `Columns.num` and `Columns.integer` both take `lineNo` last, in all three parsers.

**One ordering hazard, called out in place.** Task 8 does not compile until Task 9 adds `ssaBasename` and the `viewSsaResults` button, because the handler references them. The two tasks share a commit, and Task 8 step 4 says so.
