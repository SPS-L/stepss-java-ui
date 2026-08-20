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
        filledCircleCarriesItsFillInTheStyleBlock();
        filledCircleRuleAppearsOnlyWhenOneIsDrawn();
        filledCircleOnGraphicsDoesNotThrow();
        seriesClassesResolve();
        seriesClassesWrap();
        seriesClassesReachTheSvgStyleBlock();
        niceStepPicksTheOneTwoFiveLadder();
        niceBoundsRoundOutward();
        degenerateCasesStillAdvance();

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

    /**
     * A filled marker is styled by class like everything else. An inline fill
     * colour would draw the same circle and quietly cost the file the one
     * property the whole sink exists for: restylable by editing the style
     * block and nothing else.
     */
    private static void filledCircleCarriesItsFillInTheStyleBlock() {
        SvgSink sink = new SvgSink(200, 100);
        sink.filledCircle(50, 50, 5, "pole");
        String svg = sink.toSvg();
        check("one circle element", 1, count(svg, "<circle"));
        check("carries both classes", true, svg.contains("class=\"pole filled\""));
        // The element itself, not the whole file: toSvg paints the export
        // ground with a fill attribute of its own.
        int at = svg.indexOf("<circle");
        String element = svg.substring(at, svg.indexOf("/>", at));
        check("no colour on the element", false, element.contains("fill="));
        check("the fill rule is declared", true,
                svg.contains(".pole.filled { fill: " + PlotStyle.of("pole").lightHex));
        // .pole.filled outranks .pole on specificity, so the class's own
        // "fill: none" cannot win whatever order the two rules end up in.
        check("the class keeps its outline rule too", true, svg.contains(".pole "));
    }

    /** No rule for a marker the figure has not got. */
    private static void filledCircleRuleAppearsOnlyWhenOneIsDrawn() {
        SvgSink hollow = new SvgSink(200, 100);
        hollow.circle(50, 50, 5, "pole");
        check("a hollow circle declares no fill rule", false,
                hollow.toSvg().contains(".filled"));

        SvgSink twice = new SvgSink(200, 100);
        twice.filledCircle(10, 10, 5, "pole");
        twice.filledCircle(20, 20, 5, "pole");
        check("one rule however many circles use it", 1,
                count(twice.toSvg(), ".pole.filled"));
    }

    private static void filledCircleOnGraphicsDoesNotThrow() {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            new SwingSink(g, false).filledCircle(40, 30, 5, "pole");
            check("SwingSink.filledCircle completes", true, true);
        } catch (RuntimeException ex) {
            check("SwingSink.filledCircle completes", true, false);
        } finally {
            g.dispose();
        }
    }

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
        check("wraps a negative index", "series" + (PlotStyle.SERIES_COLOURS - 1),
                PlotStyle.seriesClass(-1));
    }

    /** The style block is generated from ENTRIES, so every class must appear. */
    private static void seriesClassesReachTheSvgStyleBlock() {
        String svg = new SvgSink(10, 10).toSvg();
        for (int i = 0; i < PlotStyle.SERIES_COLOURS; i++) {
            check("style block declares series" + i, true,
                    svg.contains(".series" + i));
        }
    }

    /** Ticks land on 1, 2 or 5 times a power of ten, never on 3.7. */
    private static void niceStepPicksTheOneTwoFiveLadder() {
        check("span 1 into 5", 0.2, NiceScale.step(1.0, 5));
        // 30/5 is 6, which is above 5 on the ladder, so it rounds up to 10.
        check("span 30 into 5", 10.0, NiceScale.step(30.0, 5));
        check("span 0.004 into 4", 0.001, NiceScale.step(0.004, 4));
        check("span 7000 into 5", 2000.0, NiceScale.step(7000.0, 5));
        // A flat curve has zero span and must still yield a usable step
        // rather than 0, which would divide by zero when placing ticks.
        check("zero span", 1.0, NiceScale.step(0.0, 5));
    }

    private static void niceBoundsRoundOutward() {
        double[] b = NiceScale.bounds(0.83, 1.04, 0.05);
        check("low rounds down", 0.80, round(b[0]));
        check("high rounds up", 1.05, round(b[1]));
        double[] flat = NiceScale.bounds(1.0, 1.0, 1.0);
        check("flat data still spans", true, flat[1] > flat[0]);
    }

    /** Cases CurveHarness could not reach because they need no panel. */
    private static void degenerateCasesStillAdvance() {
        check("a zero span still yields an advancing step",
                "1.0", String.valueOf(NiceScale.step(0.0, 6)));
        check("a flat range is widened by one step either side",
                "[4.0, 6.0]",
                java.util.Arrays.toString(NiceScale.bounds(5.0, 5.0, 1.0)));
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
        // java.util.Objects.equals rather than expected.equals(actual): a
        // check written against a null expected value NPEs on the bare call
        // before it ever gets to compare, so the failure reads as a crash in
        // the harness rather than as the assertion it was. CurveHarness's
        // readoutIsNullOutsideThePlotArea is one such check; this file has
        // none yet, and the guard belongs here before the first one is
        // written rather than after it has thrown.
        if (!java.util.Objects.equals(expected, actual)) {
            System.err.println("FAIL " + what + ": expected " + expected
                    + ", got " + actual);
            failures++;
        }
    }
}
