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

    /**
     * A minimal modes fixture with only nstates and nalg in the header, to
     * test that absent keys parse as null rather than throwing.
     */
    private static final String[] PARTIAL_HEADER_LINES = {
        "# STEPSS SSA modes v1",
        "# nstates 3 nalg 4",
        "#   index                       re                       im"
            + "                     zeta                  freq_hz dom smp",
        "       1  -10.000000000000000E+00    0.000000000000000E+00"
            + "    1.000000000000000E+00    0.000000000000000E+00  1  1",
        "       2 -428.700000000000000E-03    3.919040000000000E+00"
            + "  108.740000000000000E-03  623.700000000000000E-03  1  1",
        "       3    0.000000000000000E+00    0.000000000000000E+00"
            + "    0.000000000000000E+00    0.000000000000000E+00  0  0",
    };

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

    static String modesFixture() {
        return join(MODES_LINES);
    }

    static String partialHeaderFixture() {
        return join(PARTIAL_HEADER_LINES);
    }

    private static String join(String[] lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

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

    public static void main(String[] args) throws java.io.IOException {
        checkModesParse();
        checkModesHeader();
        checkModesTime();
        checkModesPartialHeader();
        checkModesOriginZeta();
        checkModesCrlf();
        checkParticipationNames();
        checkParticipationFilteredMode();
        checkModeShapes();
        checkElectromechanicalFilter();
        checkBasenameDiscoveryOnEmptyDir();
        checkSvgSinkEmitsEditableElements();
        checkSvgSinkEscapes();
        checkPlotStyleCoverageInSvg();
        checkTextFontSizesDiffer();
        checkUnclosedGroupsAutoClose();
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

    private static void checkModesTime() {
        SsaModes m = parsedModes();
        if (m == null) {
            fail("time is read from a complete header");
            return;
        }
        expect("time is read from a complete header", 0.0, m.time());
    }

    private static void checkModesPartialHeader() {
        try {
            SsaModes m = SsaModes.parse(partialHeaderFixture());
            expect("partial header nstates", 3, m.nstates());
            expect("partial header nalg", 4, m.nalg());
            expect("partial header time is null", null, m.time());
            expect("partial header real_limit is null", null, m.realLimit());
            expect("partial header pf_threshold is null", null, m.pfThreshold());
            expect("partial header gap_tol is null", null, m.gapTol());
        } catch (java.io.IOException ex) {
            fail("partial header parses: threw " + ex);
        }
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
        String tmpdir = System.getenv("TMPDIR");
        if (tmpdir == null) {
            tmpdir = System.getProperty("java.io.tmpdir");
        }
        java.nio.file.Path dirPath = java.nio.file.Files.createTempDirectory(
                java.nio.file.Paths.get(tmpdir), "ssaharness");
        java.io.File dir = dirPath.toFile();
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

    private static void checkPlotStyleCoverageInSvg() {
        SvgSink sink = new SvgSink(100, 100);
        String svg = sink.toSvg();
        for (PlotStyle.Entry entry : PlotStyle.ENTRIES) {
            expect("SVG has style rule for " + entry.cls, true,
                    svg.contains("." + entry.cls));
        }
    }

    private static void checkTextFontSizesDiffer() {
        PlotStyle.Entry label = PlotStyle.of("label");
        PlotStyle.Entry title = PlotStyle.of("title");
        expect("label font size is not null", true, label.fontPx != null);
        expect("title font size is not null", true, title.fontPx != null);
        expect("label and title have different font sizes", true,
                !label.fontPx.equals(title.fontPx));
    }

    private static void checkUnclosedGroupsAutoClose() {
        SvgSink sink = new SvgSink(100, 100);
        sink.group("a");
        sink.group("b");
        String svg = sink.toSvg();
        int openCount = countOf(svg, "<g ");
        int closeCount = countOf(svg, "</g>");
        expect("unclosed groups are auto-closed", openCount, closeCount);
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
