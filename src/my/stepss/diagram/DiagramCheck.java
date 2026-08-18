package my.stepss.diagram;

import my.stepss.HeliosOutcome;

/**
 * Headless checks for the annotated one-line diagram: the Helios exit-status
 * decision, the zoom and pan arithmetic, and the Batik rendering path.
 *
 * <p>Run from {@code tools/diagram-harness.sh}; this repository has no
 * unit-test framework.
 *
 * <p>The rendering checks matter more than they look. Batik reaches this
 * application through {@code batik-all}, which bundles {@code batik-script},
 * whose Rhino interpreter factory is registered through
 * {@code META-INF/services} while Rhino itself is not shipped. That is expected
 * to be harmless, and expected is not verified, so these run with exactly the
 * classpath the application has.
 */
public final class DiagramCheck {

    private static int failures = 0;

    private DiagramCheck() {
    }

    public static void main(String[] args) throws Exception {
        checkConvergedIsSilent();
        checkNotConvergedWarns();
        checkNotConvergedCarriesItsReason();
        checkInputErrorIsAnError();
        checkUndocumentedStatusIsAnError();
        checkMissingStatusLineLeavesTheHeadlineAlone();
        checkRenderFailureIsItsOwnOutcome();
        checkTheLauncherDoesNotForceATextEditor();
        checkTheEditorLauncherStillForcesOne();
        checkTheDiagramCommandBlock();
        checkHeliosDiagramLinesReachTheConsole();
        checkASvgRendersAtAll();
        checkZoomCostsNothingExtra();
        checkPanMovesTheRegion();
        checkAMalformedSvgRaises();
        checkExternalResourcesAreRefused();
        checkAnUnknownElementDoesNotLoseTheDocument();

        System.out.println(failures == 0 ? "ALL DIAGRAM CHECKS PASSED"
                : failures + " DIAGRAM CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkConvergedIsSilent() {
        HeliosOutcome outcome = HeliosOutcome.of(0, "helios: status: CONVERGED (2 iterations)\n");
        check("exit 0 is OK", outcome.severity() == HeliosOutcome.Severity.OK);
        check("exit 0 has no headline", outcome.headline().isEmpty());
    }

    private static void checkNotConvergedWarns() {
        HeliosOutcome outcome = HeliosOutcome.of(2, "");
        check("exit 2 warns", outcome.severity() == HeliosOutcome.Severity.WARNING);
        check("exit 2 says so", outcome.headline().contains("did NOT converge"));
        check("exit 2 warns off the values",
                outcome.detail().contains("Do not use these values"));
    }

    private static void checkNotConvergedCarriesItsReason() {
        HeliosOutcome outcome = HeliosOutcome.of(2,
                "helios: status: NOT_CONVERGED (max iterations)\n");
        check("exit 2 carries the reason Helios gave",
                outcome.headline().contains("(max iterations)"));
    }

    private static void checkInputErrorIsAnError() {
        HeliosOutcome outcome = HeliosOutcome.of(1, "");
        check("exit 1 errors", outcome.severity() == HeliosOutcome.Severity.ERROR);
        check("exit 1 names the input", outcome.headline().contains("could not process"));
    }

    private static void checkUndocumentedStatusIsAnError() {
        HeliosOutcome outcome = HeliosOutcome.of(3, "");
        check("exit 3 errors", outcome.severity() == HeliosOutcome.Severity.ERROR);
        check("exit 3 names its value", outcome.headline().contains("3"));
    }

    private static void checkMissingStatusLineLeavesTheHeadlineAlone() {
        // The engine before the status contract never wrote the line, so the
        // headline has to stand on its own without a dangling "()".
        HeliosOutcome outcome = HeliosOutcome.of(2, "some unrelated stderr\n");
        check("a missing status line leaves no empty parentheses",
                !outcome.headline().contains("()"));
    }

    private static void checkRenderFailureIsItsOwnOutcome() {
        HeliosOutcome outcome = HeliosOutcome.renderFailed("6bus.svg");
        check("a render failure warns",
                outcome.severity() == HeliosOutcome.Severity.WARNING);
        check("a render failure names the template",
                outcome.headline().contains("6bus.svg")
                        || outcome.detail().contains("6bus.svg"));
    }

    private static void checkTheLauncherDoesNotForceATextEditor() {
        java.io.File svg = new java.io.File("/tmp/6bus.svg");
        for (my.stepss.platform.Platform platform : my.stepss.platform.Platform.values()) {
            org.apache.commons.exec.CommandLine cmd =
                    my.stepss.platform.PlatformLauncher.defaultApplicationCommand(platform, svg);
            String line = cmd.getExecutable() + " "
                    + String.join(" ", cmd.getArguments());
            // "open -t" forces TextEdit and "notepad.exe" forces Notepad, which
            // is right for a .dat and shows an SVG as XML source.
            check(platform + " does not force a text editor",
                    !line.contains(" -t ") && !line.contains("notepad"));
            check(platform + " passes the file",
                    line.contains(svg.getAbsolutePath()));
        }
    }

    private static void checkTheEditorLauncherStillForcesOne() {
        // The twelve data-file buttons keep the behaviour they have. This check
        // is what stops a future tidy-up merging the two launchers.
        java.io.File dat = new java.io.File("/tmp/lf.dat");
        org.apache.commons.exec.CommandLine cmd =
                my.stepss.platform.PlatformLauncher.editorCommand(
                        my.stepss.platform.Platform.WINDOWS_X86_64, dat);
        check("the editor launcher still opens Notepad on Windows",
                cmd.getExecutable().contains("notepad"));
    }

    private static void checkTheDiagramCommandBlock() {
        String block = my.stepss.StepssUI.diagramCommands("/case/6bus.svg", "in_diagram.svg");
        check("the block starts with the main-menu command",
                block.startsWith("1\n"));
        check("the block names the template",
                block.contains("\n/case/6bus.svg\n"));
        check("the block names the output",
                block.endsWith("in_diagram.svg\n"));
        check("the block is exactly three lines",
                block.split("\n", -1).length == 4);
    }

    private static void checkHeliosDiagramLinesReachTheConsole() {
        // cmd_diagram writes all of these to stdout, which HeliosLog filters
        // against a fixed prefix list. Without them a failed render is silent.
        String[] lines = {
            "Open in_diagram.svg in your browser",
            "This file does not exist !",
            "output file must be different from input file !",
            "DiagramRenderer: cannot open template: /case/6bus.svg"
        };
        for (String line : lines) {
            check("the console keeps: " + line, my.stepss.HeliosLog.isProgressLine(line));
        }
        check("a table row is still dropped",
                !my.stepss.HeliosLog.isProgressLine("  A      6.000   1.0210    0.00"));
    }

    /** A minimal document with known bounds and one black square in the corner. */
    private static final String TEST_SVG
            = "<?xml version=\"1.0\"?>\n"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"100\""
            + " viewBox=\"0 0 200 100\">\n"
            + "  <rect x=\"0\" y=\"0\" width=\"20\" height=\"20\" fill=\"black\"/>\n"
            + "</svg>\n";

    private static java.io.File writeTestSvg(String body) throws java.io.IOException {
        java.io.File file = java.io.File.createTempFile("stepss-diagram", ".svg");
        file.deleteOnExit();
        java.nio.file.Files.write(file.toPath(),
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return file;
    }

    /** A pixel count, for asserting that a region actually drew something. */
    private static int inkedPixels(java.awt.image.BufferedImage image) {
        int inked = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                boolean opaque = ((argb >>> 24) & 0xff) > 0;
                boolean dark = (argb & 0xffffff) != 0xffffff;
                if (opaque && dark) {
                    inked++;
                }
            }
        }
        return inked;
    }

    /**
     * The end-to-end check, and the one that answers the Rhino question.
     *
     * <p>batik-all registers a Rhino interpreter factory through
     * META-INF/services and Rhino is not shipped. If that were not harmless,
     * this is where it would surface, on the classpath the application has.
     */
    private static void checkASvgRendersAtAll() throws Exception {
        java.io.File file = writeTestSvg(TEST_SVG);
        SvgImage image = SvgImage.load(file);
        check("the document bounds are read",
                Math.abs(image.documentBounds().getWidth() - 200.0) < 0.5
                        && Math.abs(image.documentBounds().getHeight() - 100.0) < 0.5);
        java.awt.image.BufferedImage rendered = image.renderWhole(400);
        check("the whole document renders at the width asked for",
                rendered.getWidth() == 400);
        check("it renders at the document's aspect ratio", rendered.getHeight() == 200);
        check("something was drawn", inkedPixels(rendered) > 0);
    }

    /** Zoom is a smaller AOI at the same pixel size, not a bigger image. */
    private static void checkZoomCostsNothingExtra() throws Exception {
        SvgImage image = SvgImage.load(writeTestSvg(TEST_SVG));
        java.awt.image.BufferedImage wide = image.render(
                new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100), 400, 200);
        java.awt.image.BufferedImage tight = image.render(
                new java.awt.geom.Rectangle2D.Double(0, 0, 20, 10), 400, 200);
        check("a tenfold zoom renders the same number of pixels",
                tight.getWidth() == wide.getWidth()
                        && tight.getHeight() == wide.getHeight());
        check("and it is not the same picture",
                inkedPixels(tight) != inkedPixels(wide));
        check("zooming into the square fills more of the frame",
                inkedPixels(tight) > inkedPixels(wide));
    }

    /** Panning off the square leaves an empty frame. */
    private static void checkPanMovesTheRegion() throws Exception {
        SvgImage image = SvgImage.load(writeTestSvg(TEST_SVG));
        java.awt.image.BufferedImage onIt = image.render(
                new java.awt.geom.Rectangle2D.Double(0, 0, 20, 10), 200, 100);
        java.awt.image.BufferedImage offIt = image.render(
                new java.awt.geom.Rectangle2D.Double(150, 50, 20, 10), 200, 100);
        check("the region with the square has ink", inkedPixels(onIt) > 0);
        check("the region without it does not", inkedPixels(offIt) == 0);
    }

    /** A file that is not an SVG raises rather than producing a blank image. */
    private static void checkAMalformedSvgRaises() throws Exception {
        java.io.File file = writeTestSvg("this is not markup at all");
        try {
            SvgImage.load(file);
            check("a malformed SVG is refused", false);
        } catch (java.io.IOException expected) {
            check("a malformed SVG is refused", true);
        }
    }

    /**
     * An external reference is refused rather than fetched.
     *
     * <p>Batik's default transcoder user agent both declines the fetch and
     * aborts the render, so the refusal surfaces as an IOException naming the
     * resource. Asserting the message rather than merely "it threw" is what
     * distinguishes a refused fetch from a broken file, which is the other
     * thing that throws here.
     */
    private static void checkExternalResourcesAreRefused() throws Exception {
        String hostile = TEST_SVG.replace("</svg>",
                "  <image xlink:href=\"http://127.0.0.1:1/should-not-be-fetched.png\""
                + " x=\"0\" y=\"0\" width=\"10\" height=\"10\"/>\n</svg>")
                .replace("<svg xmlns=\"http://www.w3.org/2000/svg\"",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\""
                        + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
        SvgImage image = SvgImage.load(writeTestSvg(hostile));
        try {
            image.renderWhole(200);
            check("an external reference is refused", false);
        } catch (java.io.IOException refused) {
            check("an external reference is refused", true);
            check("and the refusal names the resource",
                    String.valueOf(refused.getMessage()).contains("127.0.0.1")
                            || String.valueOf(refused.getCause()).contains("127.0.0.1"));
        }
    }

    /**
     * A metadata element the SVG DOM does not know does not lose the document.
     *
     * <p>The case this exists for is real and is the bundled example: WinFIG
     * writes {@code <version>1.0</version>} inside {@code <desc>}, which
     * inherits the SVG default namespace, and the strict DOM refuses the whole
     * drawing over it. Browsers ignore such elements, and so must a viewer of
     * files it did not author.
     */
    private static void checkAnUnknownElementDoesNotLoseTheDocument() throws Exception {
        String withCruft = TEST_SVG.replace("<rect",
                "<desc> METADATA <version id=\"v8\">1.0</version></desc>\n  <rect");
        SvgImage image = SvgImage.load(writeTestSvg(withCruft));
        java.awt.image.BufferedImage rendered = image.renderWhole(400);
        check("an unknown element in the SVG namespace is tolerated",
                rendered.getWidth() == 400);
        check("and the rest of the drawing still draws",
                inkedPixels(rendered) > 0);
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("  ok    " + what);
        } else {
            System.out.println("  FAIL  " + what);
            failures++;
        }
    }
}
