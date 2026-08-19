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
        niceStepPicksTheOneTwoFiveLadder();
        niceBoundsRoundOutward();
        rendersOnePolylinePerSeries();
        namesTheSharedUnitAndFlagsMixedOnes();
        curvesWearTheirOwnColourInOrder();
        zoomNarrowsTheAxesAndResets();
        readoutIsNullOutsideThePlotArea();
        curvesAreClippedToTheFrame();

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

    /**
     * Curve i must wear seriesClass(i). Asserting only that all four classes
     * appear somewhere in the document passes with the colours swapped, and a
     * swap is not cosmetic: the legend swatch comes from the same seriesClass
     * call, so the legend would name the wrong curve.
     */
    private static void curvesWearTheirOwnColourInOrder() {
        CurvePanel panel = new CurvePanel();
        panel.setData(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        String svg = panel.toSvg(600, 400);
        int at = 0;
        for (int i = 0; i < SMOKE_LABELS.size(); i++) {
            int start = svg.indexOf("<polyline", at);
            check("polyline " + i + " is present", true, start >= 0);
            int end = svg.indexOf("/>", start);
            check("polyline " + i + " wears series" + i, true,
                    svg.substring(start, end).contains("class=\"series" + i + "\""));
            at = end;
        }
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

    private static void zoomNarrowsTheAxesAndResets() {
        CurvePanel panel = new CurvePanel();
        panel.setData(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        check("starts unzoomed", false, panel.zoomed());
        panel.setSize(600, 400);
        String unzoomed = panel.readoutAt(300, 200);
        panel.setZoom(0.0, 0.25, 1.0, 1.25);
        check("zoom is recorded", true, panel.zoomed());
        check("zooming changes what a device point reads", false,
                unzoomed.equals(panel.readoutAt(300, 200)));
        check("still one polyline per curve when zoomed", 4,
                count(panel.toSvg(600, 400), "<polyline"));
        panel.resetZoom();
        check("reset clears it", false, panel.zoomed());
        check("resetting restores what the point read before", unzoomed,
                panel.readoutAt(300, 200));
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
        // Both presence assertions come first on purpose. Comparing two
        // indexOf results alone passes when the curves group is absent, since
        // -1 is less than any real position, so the comparison would report
        // success for a document with no curves in it at all.
        int curvesAt = svg.indexOf("id=\"curves\"");
        int legendAt = svg.indexOf("id=\"legend\"");
        check("the curves group exists", true, curvesAt >= 0);
        check("the legend group exists", true, legendAt >= 0);
        check("the legend is outside the clip", true, legendAt > curvesAt);
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

    private static void check(String what, Object expected, Object actual) {
        // java.util.Objects.equals rather than expected.equals(actual):
        // readoutIsNullOutsideThePlotArea asserts against a null expected
        // value, which the bare call NPEs on before it ever gets to compare.
        if (!java.util.Objects.equals(expected, actual)) {
            System.err.println("FAIL " + what + ": expected " + expected
                    + ", got " + actual);
            failures++;
        }
    }
}
