package my.stepss.curves;

import java.io.File;
import java.io.IOException;
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

    public static void main(String[] args) throws IOException {
        readsEveryColumn();
        stripsTheTrailingSemicolon();
        parsesThreeDigitExponents();
        derivesUnitsFromLabels();
        skipsShortRows();
        skipsNonNumericRows();
        skipsNonFiniteRows();
        skipsBlankLines();
        toleratesTheDegenerateTimeOnlyFile();
        twoPointsLandOnTheDerivedPixels();
        rendersOnePolylinePerSeries();
        namesTheSharedUnitAndFlagsMixedOnes();
        curvesWearTheirOwnColourInOrder();
        zoomNarrowsTheAxesAndResets();
        readoutIsNullOutsideThePlotArea();
        curvesAreClippedToTheFrame();
        csvCarriesEveryColumnAndQuotesLabels();
        toleratesRamsesCommentsAboveTheHeader();
        checkHeader();
        checkTail();

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

    /**
     * A field that parses and is still not data. gfortran writes NaN and
     * Infinity into an E15.7E3 field for a non-finite value, and
     * Double.parseDouble reads all of those spellings back as a number, so
     * this row cannot be caught by the NumberFormatException path above.
     *
     * <p>The second half of the check is why it matters: one kept NaN travels
     * through bounds() into every y coordinate, so the axis lines themselves
     * are emitted as y1="NaN" and the whole figure is blank.
     */
    private static void skipsNonFiniteRows() {
        List<String> lines = new ArrayList<String>(SMOKE);
        lines.add(" 0.7500000E+000  NaN  0.1E+001  0.1E+001  0.1E+001  ;");
        lines.add(" 0.1000000E+001  Infinity  0.1E+001  0.1E+001  0.1E+001  ;");
        lines.add(" 0.1250000E+001  +Infinity  0.1E+001  0.1E+001  0.1E+001  ;");
        lines.add(" 0.1500000E+001  -Infinity  0.1E+001  0.1E+001  0.1E+001  ;");
        CurveData data = CurReader.parse(lines, null, SMOKE_LABELS);
        check("non-finite rows counted", 4, data.skippedRows);
        check("non-finite rows not stored", 3, data.series.get(0).v.length);
        CurvePanel panel = new CurvePanel();
        panel.setData(data);
        check("no coordinate in the figure is NaN", false,
                panel.toSvg(600, 400).contains("NaN"));
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

    /**
     * Fed a headed RAMSES file, the DYNGRAPH reader must treat the comment
     * lines as comments. Counting them as unreadable rows made CurveWindow's
     * header report damage over undamaged data.
     *
     * <p>Six of them here, one per header record, because this fixture carries
     * two observables. A real capture carries one per observable and so has
     * seven at three observables, which is where the "seven" in the plan that
     * specified this check came from: it described the capture rather than the
     * fixture beneath it.
     */
    private static void toleratesRamsesCommentsAboveTheHeader() {
        CurveData headed = CurReader.parse(Arrays.asList(
                "# stepss-cur 1",
                "# tstop      240.000",
                "# refresh        1.000",
                "# ncol 3",
                "# obs 1 2 1 BV 1041",
                "# obs 2 3 1 MS g6",
                " 0.000000E+00  1.012404E+00  1.000000E+00 ",
                " 1.000000E-02  1.012000E+00  1.000100E+00 "),
                null, Arrays.asList("bus voltage (pu)", "speed (pu)"));
        check("a clean headed file reports no damage", "0",
                String.valueOf(headed.skippedRows));
        check("and every data row survives", "2",
                String.valueOf(headed.series.get(0).v.length));
    }

    /**
     * The one SVG output pinned byte for byte that the design spec's
     * Verification section asks for, kept to the two coordinates that carry
     * the geometry rather than to a whole document, because a whole-document
     * golden breaks on every cosmetic change and gets deleted by whoever it
     * next inconveniences.
     *
     * <p>Two points and one series, at a size chosen so every step of the
     * mapping is exact in binary and can be re-derived by hand:
     *
     * <pre>
     *   plot area   390 - 70 - 20 = 300 wide, 180 - 30 - 50 = 100 high
     *   t span 4    NiceScale.step(4, 6) = 1.0, NiceScale.bounds(0.5, 4.5, 1) = {0, 5}
     *   v span 4    NiceScale.step(4, 5) = 1.0, NiceScale.bounds(0.25, 4.25, 1) = {0, 5}
     *   px(0.50) = 70 + (0.50 / 5) * 300 = 70 + 30 = 100
     *   px(4.50) = 70 + (4.50 / 5) * 300 = 70 + 270 = 340
     *   py(0.25) = 180 - 50 - (0.25 / 5) * 100 = 130 - 5 = 125
     *   py(4.25) = 180 - 50 - (4.25 / 5) * 100 = 130 - 85 = 45
     * </pre>
     *
     * <p>NiceScale.bounds widens both axes past the data on purpose, so neither
     * point sits on a frame edge where a fraction of 0 or 1 would hide an
     * error in the scaling, and the x and y numbers are all different, so
     * transposing px and py cannot pass. All four padding constants appear in
     * the arithmetic above, so all four are pinned.
     *
     * <p>Derived from the constants first and confirmed against the code
     * second, never the other way round: a golden captured from the
     * implementation proves only that the implementation agrees with itself.
     */
    private static void twoPointsLandOnTheDerivedPixels() {
        CurveSeries one = new CurveSeries("bus BUS1: voltage magnitude (pu)",
                "pu", new double[] {0.5, 4.5}, new double[] {0.25, 4.25});
        CurvePanel panel = new CurvePanel();
        panel.setData(new CurveData(Arrays.asList(one), null, 0));
        String svg = panel.toSvg(390, 180);
        check("exactly one polyline to pin", 1, count(svg, "<polyline"));
        int at = svg.indexOf("points=\"");
        check("the polyline carries a points list", true, at >= 0);
        check("the two samples land on the derived pixels",
                "points=\"100.00,125.00 340.00,45.00\"",
                svg.substring(at, svg.indexOf('"', at + 8) + 1));
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
        check("one unit is one distinct unit", 1, same.distinctUnits());
        CurvePanel plain = new CurvePanel();
        plain.setData(same);
        String plainSvg = plain.toSvg(600, 400);
        check("no warning when units agree", false,
                plainSvg.contains("mixed units"));
        // The other direction of the same property: a genuinely single-unit
        // extraction must still get its axis label. Written as the whole text
        // element so a legend label ending in "(pu)" cannot satisfy it.
        check("the shared unit labels the y axis", true,
                plainSvg.contains(">pu</text>"));

        // A unit against no unit at all. Counting only non-empty units made
        // this one distinct unit with no common unit, so render drew neither
        // the axis label nor the note, which is the case that needs the note
        // most: a 0/1 relay state against a 1.0 pu voltage is the flat-curve
        // confusion the note exists to explain.
        CurveData someUnitless = CurReader.parse(
                Arrays.asList(" 0.0E+000  0.1E+001  0.0E+000  ;"), null,
                Arrays.asList(
                    "bus BUS1: voltage magnitude (pu)",
                    "DCTL relay1: state"));
        check("a unit against no unit is two distinct units", 2,
                someUnitless.distinctUnits());
        check("and there is no common unit", "", someUnitless.commonUnit());
        CurvePanel halfUnitless = new CurvePanel();
        halfUnitless.setData(someUnitless);
        String halfSvg = halfUnitless.toSvg(600, 400);
        check("pu against a unitless state is called out", true,
                halfSvg.contains("mixed units"));
        check("and no unit is claimed for the y axis", false,
                halfSvg.contains(">pu</text>"));

        // No curve has a unit: one distinct unit, so not mixed. These curves
        // do share a scale, they just have no name for it, and a note telling
        // the user to extract them separately would be wrong.
        CurveData noUnits = CurReader.parse(
                Arrays.asList(" 0.0E+000  0.1E+001  0.0E+000  ;"), null,
                Arrays.asList("DCTL relay1: state", "DCTL relay2: state"));
        check("no units at all is one distinct unit", 1,
                noUnits.distinctUnits());
        CurvePanel unitless = new CurvePanel();
        unitless.setData(noUnits);
        check("no note when nothing has a unit", false,
                unitless.toSvg(600, 400).contains("mixed units"));
    }

    /**
     * Zoom must NARROW the window, which needs three things a single readout
     * cannot see.
     *
     * <p>Reading one device point before and after says only that something
     * changed: a zoom that widened the window four times over changes every
     * readout too. So the span between two device points is read on each axis
     * and required to shrink.
     *
     * <p>A span shrinking is still not enough, because transposing the axes in
     * CurvePanel.bounds (tLo = zoom[2] and so on, an index typo that zooms
     * time to the value range) shrinks both spans as well. So the readout at
     * the panel midpoint is required to land inside the SMOKE fixture's own
     * ranges, t in 0 to 0.5 and value in 1.0 to 17.5. Transposed, the
     * midpoint reads t of about 1.11, which is off the end of a 0.5 s run.
     */
    private static void zoomNarrowsTheAxesAndResets() {
        CurvePanel panel = new CurvePanel();
        panel.setData(CurReader.parse(SMOKE, null, SMOKE_LABELS));
        check("starts unzoomed", false, panel.zoomed());
        panel.setSize(600, 400);
        String unzoomed = panel.readoutAt(300, 200);
        double wideT = readout(panel, 400, 200)[0] - readout(panel, 200, 200)[0];
        double wideV = readout(panel, 300, 100)[1] - readout(panel, 300, 300)[1];
        panel.setZoom(0.0, 0.25, 1.0, 1.25);
        check("zoom is recorded", true, panel.zoomed());
        check("zooming changes what a device point reads", false,
                unzoomed.equals(panel.readoutAt(300, 200)));
        check("the same two device x values now span less time", true,
                readout(panel, 400, 200)[0] - readout(panel, 200, 200)[0] < wideT);
        check("the same two device y values now span fewer units", true,
                readout(panel, 300, 100)[1] - readout(panel, 300, 300)[1] < wideV);
        double[] middle = readout(panel, 300, 200);
        check("the zoomed midpoint reads a time the run contains", true,
                middle[0] >= 0.0 && middle[0] <= 0.5);
        check("the zoomed midpoint reads a value the curves contain", true,
                middle[1] >= 1.0 && middle[1] <= 17.5);
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
     *
     * <p>The property is CONTAINMENT: the curves inside the clip group, the
     * legend outside it. Document position is not the same thing, and
     * asserting position is what let two separate mutations pass. Deleting
     * {@code sink.endClip()} in CurvePanel.render leaves the legend nested in
     * the clip group, so the legend is clipped, which is the exact defect this
     * method claims to prevent; moving the {@code clipRect} call to after the
     * curves group leaves it wrapping nothing, so every curve is unclipped.
     * Both keep one clipPath declaration, the clip-path attribute, and the
     * order of id="curves" and id="legend" intact.
     *
     * <p>So the clip is required to OPEN before the curves group, which kills
     * the second, and exactly two {@code </g>} are required between the two
     * group ids, which kills the first: one closing the curves group and one
     * closing the clip group, and nothing else can sit between them.
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
        // Document order next, because it is what makes the substring below
        // well formed. On its own it says nothing about containment.
        check("the legend group comes after the curves group", true,
                legendAt > curvesAt);
        int clipOpen = svg.indexOf("clip-path=\"url(#clip1)\"");
        check("the clip opens before the curves", true,
                clipOpen >= 0 && clipOpen < curvesAt);
        // Two closes between them: one for the curves group, one for the clip
        // group.
        check("the clip closes before the legend", 2,
                count(svg.substring(curvesAt, legendAt), "</g>"));
    }

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
                    "true", String.valueOf(ex.getMessage().contains("version 2,")));
            check("and the version this build supports", "true",
                    String.valueOf(ex.getMessage().contains("reads version 1 only")));
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
            check("the ncol disagreement says which number the header stated",
                    "true", String.valueOf(ex.getMessage().contains("ncol 6")));
            check("and which the records actually cover", "true",
                    String.valueOf(ex.getMessage().contains("cover 5")));
        }
    }

    /**
     * CurTail: the offset-based reader a live viewer polls once per flush.
     * Writes real files into a temporary directory, because the whole point
     * of the class is file mechanics.
     */
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

    /**
     * The two numbers behind {@code readoutAt}'s "t = %.4g, value = %.6g", as
     * {t, value}.
     *
     * <p>Parsed out of the formatted string rather than read from new
     * accessors on CurvePanel: the readout is the whole of what the panel
     * publishes about its mapping, and widening that surface to make a check
     * easier to write would put geometry into the public API that the window
     * does not use. Splitting on the comma is safe because the format carries
     * Locale.ROOT, so no grouping separator can appear inside a number.
     */
    private static double[] readout(CurvePanel panel, int px, int py) {
        String[] halves = panel.readoutAt(px, py).split(",", 2);
        return new double[] {number(halves[0]), number(halves[1])};
    }

    private static double number(String half) {
        return Double.parseDouble(half.substring(half.indexOf('=') + 1).trim());
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
