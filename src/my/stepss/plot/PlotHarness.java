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
