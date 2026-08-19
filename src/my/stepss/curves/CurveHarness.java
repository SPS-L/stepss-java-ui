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
        // Two groups, so indexOf and lastIndexOf disagree, which makes this the
        // case that actually pins the choice. Synthetic on purpose: no real
        // obstypes.f90 label carries two groups, so nothing in the corpus
        // exercises the distinction.
        check("takes the first group when a label has two", "MW",
                CurveSeries.unitOf("branch X: P (MW) measured at bus (HV)"));
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
