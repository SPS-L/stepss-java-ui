package my.stepss.ssa;

import my.stepss.plot.PlotStyle;
import my.stepss.plot.SvgSink;

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

    /**
     * SsaModes.modes() is unmodifiable, so these are too. Two different
     * promises about the same kind of parsed engine output is one promise
     * too many.
     */
    private static void checkParsedRowsAreUnmodifiable() {
        try {
            SsaParticipation pf = SsaParticipation.parse(join(PF_LINES));
            try {
                pf.forMode(2).clear();
                fail("participation rows are unmodifiable");
            } catch (UnsupportedOperationException ex) {
                pass("participation rows are unmodifiable");
            }
            SsaModeShapes ms = SsaModeShapes.parse(join(MS_LINES));
            try {
                ms.forMode(2).clear();
                fail("mode shape rows are unmodifiable");
            } catch (UnsupportedOperationException ex) {
                pass("mode shape rows are unmodifiable");
            }
        } catch (java.io.IOException ex) {
            fail("unmodifiable check: threw " + ex);
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

    private static java.util.List<ModeShapeEntry> shapeFixture() {
        try {
            return SsaModeShapes.parse(join(MS_LINES)).forMode(2);
        } catch (java.io.IOException ex) {
            return new java.util.ArrayList<ModeShapeEntry>();
        }
    }

    private static void checkModeShapeRendersArrows() {
        SvgSink sink = new SvgSink(360, 360);
        ModeShapePanel.render(sink, shapeFixture(), true, true, 360, 360);
        String svg = sink.toSvg();
        // One arrow is three lines: the shaft and two head strokes.
        expect("two machines give six arrow strokes", 6,
                countOf(svg, "class=\"shape\""));
        expect("machines are labelled", true, svg.contains("AREA 1 G1"));
        expect("magnitude rings are drawn", 2, countOf(svg, "class=\"grid\""));
        expect("the dial group is present", true, svg.contains("<g id=\"arrows\""));
        // The parsers work to keep a leading blank; the drawn label is the
        // only place a human sees it, so trimming there undoes all of that.
        expect("a leading blank reaches the drawn label", true,
                svg.contains("> G2</text>"));
    }

    private static void checkModeShapeRefusesDegenerate() {
        SvgSink sink = new SvgSink(360, 360);
        // dominant true on purpose: degeneracy is a refusal that outranks the
        // dom flag, so the dial must not fall through to a dominant message.
        ModeShapePanel.render(sink, shapeFixture(), false, true, 360, 360);
        String svg = sink.toSvg();
        expect("a degenerate mode draws no arrows", 0,
                countOf(svg, "class=\"shape\""));
        expect("and says why", true, svg.contains("degenerate"));
        expect("degeneracy outranks the dominant flag", false,
                svg.contains("dominant"));
    }

    /** dom == 0 with no rows: real_limit really is the reason. */
    private static void checkModeShapeReportsFilteredMode() {
        SvgSink sink = new SvgSink(360, 360);
        ModeShapePanel.render(sink, new java.util.ArrayList<ModeShapeEntry>(),
                true, false, 360, 360);
        String svg = sink.toSvg();
        expect("a filtered mode draws no arrows", 0, countOf(svg, "class=\"shape\""));
        expect("a filtered mode names real_limit", true,
                svg.contains("real_limit"));
    }

    /**
     * dom == 1 with no rows: the engine kept this mode, so real_limit did not
     * filter it and saying so would invent a cause. _ms.dat is optional to
     * SsaResults.load, which is how this state is reached.
     */
    private static void checkModeShapeReportsMissingRowsForDominantMode() {
        SvgSink sink = new SvgSink(360, 360);
        ModeShapePanel.render(sink, new java.util.ArrayList<ModeShapeEntry>(),
                true, true, 360, 360);
        String svg = sink.toSvg();
        expect("a dominant mode with no rows draws no arrows", 0,
                countOf(svg, "class=\"shape\""));
        expect("a dominant mode with no rows does not blame real_limit", false,
                svg.contains("real_limit"));
        expect("a dominant mode with no rows says the engine kept it", true,
                svg.contains("dominant"));
        expect("and points at the missing file", true,
                svg.contains("missing from this directory"));
    }

    /** Below 60 px the margin exceeds the half-extent, and r must not go negative. */
    private static void checkModeShapeClampsTinyRadius() {
        SvgSink sink = new SvgSink(40, 40);
        ModeShapePanel.render(sink, shapeFixture(), true, true, 40, 40);
        String svg = sink.toSvg();
        expect("a tiny dial emits no negative radius", false,
                svg.contains("r=\"-"));
    }

    public static void main(String[] args) throws java.io.IOException {
        checkModesParse();
        checkModesHeader();
        checkModesTime();
        checkModesPartialHeader();
        checkModesOriginZeta();
        checkModesRejectsAMangledNumber();
        checkModesRejectsEmptyInput();
        checkModesCrlf();
        checkParticipationNames();
        checkParticipationFilteredMode();
        checkParsedRowsAreUnmodifiable();
        checkModeShapes();
        checkElectromechanicalFilter();
        checkBasenameDiscoveryOnEmptyDir();
        checkSvgSinkEmitsEditableElements();
        checkSvgSinkEscapes();
        checkPlotStyleCoverageInSvg();
        checkTextFontSizesDiffer();
        checkUnclosedGroupsAutoClose();
        checkSplaneRendersExpectedElements();
        checkSplaneLabelsItsScale();
        checkSplaneLegendFollowsTheData();
        checkSplaneMinimumExtentExpands();
        checkSplaneDownwardExpansion();
        checkSplaneMarksSelection();
        checkModeShapeRendersArrows();
        checkModeShapeRefusesDegenerate();
        checkModeShapeReportsFilteredMode();
        checkModeShapeReportsMissingRowsForDominantMode();
        checkModeShapeClampsTinyRadius();
        checkDisturbanceDefaultTime();
        checkJacobianSharesTheBasename();
        checkDisturbanceLaterTime();
        checkDisturbanceRejectsBadBasename();
        checkDisturbanceRejectsEarlyOrUnreadableTime();
        checkEngineVersionParsesBanner();
        checkEngineVersionGuardsTheBoundary();
        checkDisturbanceCarriesParameters();
        checkDisturbanceRejectsUnreadableParameters();
        checkSettingsCarryTheTwoRequiredRecords();
        checkSettingsOverrideNothingElse();
        checkSettingsFileNameCannotCollide();
        checkManifestRoundTrip();
        checkManifestOmitsWhatWasNotRecorded();
        checkManifestRefusals();
        checkArchiveMembers();
        checkArchiveNaming();
        checkArchiveRoundTrip(SsaArchive.Format.ZIP);
        checkArchiveRoundTrip(SsaArchive.Format.TAR_GZ);
        checkArchiveReportsMissingMembers();
        checkArchiveRefusesToSaveWithoutModes();
        checkArchiveRefusesAForeignFile();
        checkArchiveRefusesAnArchiveOfSomethingElse();
        checkArchiveRefusesAMissingModesFile();
        checkArchiveRefusesAnEscapingEntry();
        checkArchiveAcceptsAnArchiveNamingItsOwnRoot();
        checkArchiveNamesTheEngineThatAnalysedIt();
        checkArchiveSurvivesARenamedExtension();
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

    /**
     * The design promises a line that fails to parse is reported with its
     * line number and the file rejected, rather than yielding a
     * half-populated table. Every other check here is a success or a
     * legitimate-absence case, so this is the only one that exercises it.
     *
     * <p>The mangled field is mode 3's im, on the sixth line of the fixture:
     * three comment lines then five rows. The replacement is the same width
     * as what it replaces, so the column offsets still land on it.
     */
    private static void checkModesRejectsAMangledNumber() {
        String bad = modesFixture().replace(
                "-3.919040000000000E+00", "-3.919040000000000EXX0");
        expect("the fixture was actually mangled", true,
                bad.contains("EXX0"));
        try {
            SsaModes.parse(bad);
            fail("a mangled numeric column is rejected, not half-parsed");
        } catch (java.io.IOException ex) {
            expect("the rejection names the offending line", true,
                    ex.getMessage().startsWith("line 6:"));
            expect("and quotes what it could not read", true,
                    ex.getMessage().contains("-3.919040000000000EXX0"));
        }
    }

    /** An empty or wrong file is rejected by name, not returned empty. */
    private static void checkModesRejectsEmptyInput() {
        try {
            SsaModes.parse("");
            fail("an empty modes file is rejected");
        } catch (java.io.IOException ex) {
            expect("an empty modes file says it found no rows", true,
                    ex.getMessage().contains("no mode rows found"));
        }
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

        // A directory can carry the suffix too. Offering it as a run means
        // load() then reports "no notarun_modes.dat in ...", about a name
        // that plainly does exist.
        java.io.File decoy = new java.io.File(dir, "notarun_modes.dat");
        decoy.mkdir();
        decoy.deleteOnExit();
        expect("a directory named like a modes file is not offered as a run", 1,
                SsaResults.basenames(dir).size());
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
        sink.text(0, 0, "G1 & G2 <tie> \"north\"", "middle", "label");
        String svg = sink.toSvg();
        expect("ampersand is escaped", true, svg.contains("G1 &amp; G2"));
        expect("angle brackets are escaped", true, svg.contains("&lt;tie&gt;"));
        expect("no raw bracket leaks into the markup", false, svg.contains("<tie>"));
        // Only attribute values can be broken by a quote, and today every
        // attribute value is an internal constant. The escaper is general, so
        // it is pinned as general.
        expect("the double quote is escaped too", true,
                svg.contains("&quot;north&quot;"));
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

    private static java.util.List<Mode> emFixture() {
        SsaModes m = parsedModes();
        return SsaResults.electromechanical(m.modes());
    }

    private static double extractAttribute(String svg, String elementType,
            int elementIndex, String attrName) {
        int count = 0;
        int at = 0;
        while (count <= elementIndex) {
            at = svg.indexOf("<" + elementType, at);
            if (at < 0) {
                return Double.NaN;
            }
            if (count == elementIndex) {
                int attrStart = svg.indexOf(attrName + "=\"", at);
                if (attrStart < 0) {
                    return Double.NaN;
                }
                attrStart += attrName.length() + 2;
                int attrEnd = svg.indexOf("\"", attrStart);
                if (attrEnd < 0) {
                    return Double.NaN;
                }
                try {
                    return Double.parseDouble(svg.substring(attrStart, attrEnd));
                } catch (NumberFormatException ex) {
                    return Double.NaN;
                }
            }
            count++;
            at++;
        }
        return Double.NaN;
    }

    private static void checkSplaneRendersExpectedElements() {
        SvgSink sink = new SvgSink(500, 400);
        SplanePanel.render(sink, emFixture(), null, 500, 400);
        String svg = sink.toSvg();
        expect("the stability boundary is drawn", true, svg.contains("class=\"bound\""));
        expect("constant-damping rays are dashed", true,
                svg.contains("stroke-dasharray"));
        expect("both rays are drawn", 2, countOf(svg, "class=\"ray\""));

        // One glyph per mode, its class carrying the stability. The fixture
        // holds one stable mode and one unstable one, so a second marker over
        // either of them would show up as a third element in this group.
        String poles = groupBody(svg, "poles");
        expect("one circle per mode shown", 2, countOf(poles, "<circle"));
        expect("nothing is overplotted on a pole", 0, countOf(poles, "<line"));
        expect("the stable mode is drawn as a pole", 1,
                countOf(poles, "class=\"pole\""));
        expect("the unstable mode is a crimson circle", 1,
                countOf(poles, "class=\"unstable\""));
        // The legend sample shares the class, so one hex edit restyles both.
        expect("the unstable class is used by the mode and its legend", 2,
                countOf(svg, "class=\"unstable\""));
        expect("frequencies are labelled", true, svg.contains("0.62 Hz"));
        expect("axes are labelled", true, svg.contains("Re"));
        expect("the pole group is present", true, svg.contains("<g id=\"poles\""));

        // Verify all poles are within the plot area. The poles are the first
        // circles the file carries, so their indices are 0 to poleCount - 1.
        int poleCount = countOf(poles, "<circle");
        for (int i = 0; i < poleCount; i++) {
            double cx = extractAttribute(svg, "circle", i, "cx");
            double cy = extractAttribute(svg, "circle", i, "cy");
            expect("pole " + i + " cx is inside plot area", true,
                    cx >= 60 && cx <= 480);
            expect("pole " + i + " cy is inside plot area", true,
                    cy >= 20 && cy <= 355);
        }
    }

    /**
     * The exported SVG goes into reports, where an axis captioned [1/s] with
     * nothing to measure against is not a scale.
     */
    private static void checkSplaneLabelsItsScale() {
        SvgSink sink = new SvgSink(500, 400);
        SplanePanel.render(sink, emFixture(), null, 500, 400);
        String svg = sink.toSvg();
        String ticks = groupBody(svg, "ticks");
        expect("the ticks are their own group", true, svg.contains("<g id=\"ticks\">"));
        expect("every tick drawn carries a label",
                SplanePanel.IM_TICKS + SplanePanel.RE_TICKS, countOf(ticks, "<text"));
        expect("four Im ticks and five Re ticks", 9, countOf(ticks, "<text"));
        expect("every tick label has a tick mark",
                SplanePanel.IM_TICKS + SplanePanel.RE_TICKS, countOf(ticks, "<line"));
        expect("tick labels use the existing label class",
                SplanePanel.IM_TICKS + SplanePanel.RE_TICKS,
                countOf(ticks, "class=\"label\""));
        // The fixture keeps the notebook's window: Im 0 to 9, Re -3.0 to 0.5.
        expect("the top Im grid line is labelled", true, ticks.contains(">9.00<"));
        expect("the Re axis is labelled at its left end", true,
                ticks.contains(">-3.00<"));
        expect("and at its right end", true, ticks.contains(">0.50<"));
    }

    /**
     * The crimson pole and the crimson Re = 0 boundary are the same colour
     * and different meanings, which the design answers with a legend.
     */
    private static void checkSplaneLegendFollowsTheData() {
        SvgSink withUnstable = new SvgSink(500, 400);
        SplanePanel.render(withUnstable, emFixture(), null, 500, 400);
        String svg = withUnstable.toSvg();
        expect("an unstable mode brings a legend", true,
                svg.contains("<g id=\"legend\">"));
        expect("the legend names the marker", true,
                groupBody(svg, "legend").contains("unstable"));
        expect("the legend sample is a circle", 1,
                countOf(groupBody(svg, "legend"), "<circle"));
        expect("the legend sample is not a cross", 0,
                countOf(groupBody(svg, "legend"), "<line"));

        java.util.List<Mode> stable = new java.util.ArrayList<Mode>();
        stable.add(new Mode(1, -0.43, 3.92, 0.11, 0.62, true, true));
        SvgSink none = new SvgSink(500, 400);
        SplanePanel.render(none, stable, null, 500, 400);
        String stableSvg = none.toSvg();
        expect("with nothing unstable there is no legend", false,
                stableSvg.contains("<g id=\"legend\">"));
        expect("and nothing is drawn in the unstable class", 0,
                countOf(stableSvg, "class=\"unstable\""));
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

        // Verify the pole is actually inside the plot area, not just rendered
        double cx = extractAttribute(svg, "circle", 0, "cx");
        double cy = extractAttribute(svg, "circle", 0, "cy");
        expect("the wide-bounds mode's pole cx is inside plot area", true,
                cx >= 60 && cx <= 480);
        expect("the wide-bounds mode's pole cy is inside plot area", true,
                cy >= 20 && cy <= 355);
    }

    private static void checkSplaneDownwardExpansion() {
        // A conjugate pair with both positive and negative imaginary parts must
        // fit inside the expanded window without clipping.
        java.util.List<Mode> pair = new java.util.ArrayList<Mode>();
        pair.add(new Mode(1, -0.43, 3.92, 0.11, 0.62, true, true));
        pair.add(new Mode(2, -0.43, -3.92, 0.11, 0.62, true, true));
        SvgSink sink = new SvgSink(500, 400);
        SplanePanel.render(sink, pair, null, 500, 400);
        String svg = sink.toSvg();

        // Verify both poles are inside the plot area
        double cx0 = extractAttribute(svg, "circle", 0, "cx");
        double cy0 = extractAttribute(svg, "circle", 0, "cy");
        double cx1 = extractAttribute(svg, "circle", 1, "cx");
        double cy1 = extractAttribute(svg, "circle", 1, "cy");

        expect("conjugate pair: positive im pole cx in plot area", true,
                cx0 >= 60 && cx0 <= 480);
        expect("conjugate pair: positive im pole cy in plot area", true,
                cy0 >= 20 && cy0 <= 355);
        expect("conjugate pair: negative im pole cx in plot area", true,
                cx1 >= 60 && cx1 <= 480);
        expect("conjugate pair: negative im pole cy in plot area", true,
                cy1 >= 20 && cy1 <= 355);
    }

    /**
     * The selection is the mode's own circle filled, not a second circle drawn
     * around it, so selecting a mode adds no ink to the plot.
     */
    private static void checkSplaneMarksSelection() {
        java.util.List<Mode> em = emFixture();
        SvgSink sink = new SvgSink(500, 400);
        SplanePanel.render(sink, em, em.get(0), 500, 400);
        String svg = sink.toSvg();
        String poles = groupBody(svg, "poles");
        expect("the selected pole is filled", 1,
                countOf(poles, "class=\"pole filled\""));
        expect("selecting adds no marker", 2, countOf(poles, "<circle"));
        expect("the fill is declared in the style block, not inline", true,
                svg.contains(".pole.filled { fill:"));
        expect("no colour is written onto the element", false,
                poles.contains("fill=\""));

        // Selecting an unstable mode fills it in its own colour: the fill says
        // which mode is shown below and the class still says it is unstable.
        SvgSink crimson = new SvgSink(500, 400);
        SplanePanel.render(crimson, em, em.get(1), 500, 400);
        String crimsonSvg = crimson.toSvg();
        expect("an unstable selection stays crimson", 1,
                countOf(groupBody(crimsonSvg, "poles"), "class=\"unstable filled\""));
        expect("and its fill rule is the crimson one", true,
                crimsonSvg.contains(".unstable.filled { fill: #dc143c"));

        // A rule for a marker the figure has not got is the same small untruth
        // as a legend entry for one, and is left out for the same reason.
        SvgSink none = new SvgSink(500, 400);
        SplanePanel.render(none, em, null, 500, 400);
        expect("nothing selected leaves no fill rule behind", false,
                none.toSvg().contains(".filled"));
    }

    /**
     * The body of one group. The plots never nest groups, so the first
     * &lt;/g&gt; after the opening tag is this group's own.
     */
    private static String groupBody(String svg, String id) {
        int at = svg.indexOf("<g id=\"" + id + "\">");
        if (at < 0) {
            return "";
        }
        int end = svg.indexOf("</g>", at);
        return end < 0 ? svg.substring(at) : svg.substring(at, end);
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

    private static void checkDisturbanceDefaultTime() {
        String dst = SsaDisturbance.text("ssa", SsaDisturbance.MIN_TIME);
        expect("default analysis time fires EIG at 0.001", true,
                dst.contains("0.001000 EIG 'ssa'"));
        expect("the Jacobian is dumped at the same instant", true,
                dst.contains("0.001000 JAC 'ssa'"));
        expect("STOP follows the analysis", true, dst.contains("0.011000 STOP"));
        expect("the solver record is unchanged", true,
                dst.startsWith("0.000 CONTINUE SOLVER TR 0.010 0.001 0. ALL\n"));
        expect("exactly four records", 4, dst.split("\n").length);
        // A disturbance would linearise about a point mid-swing rather than
        // about the operating point, so the generated file must carry none
        // beyond the dump and the analysis themselves.
        expect("one analysis", 1, countOf(dst, "EIG"));
        expect("one Jacobian dump", 1, countOf(dst, "JAC"));
        // dump_jacobian runs before dump_eig in the engine's step, so writing
        // them the other way round in the file would read as if the Jacobian
        // followed the reduction.
        expect("JAC is written before EIG", true, dst.indexOf("JAC") < dst.indexOf("EIG"));
    }

    private static void checkJacobianSharesTheBasename() {
        String dst = SsaDisturbance.text("run2", 3.0);
        expect("the Jacobian carries the run's basename", true,
                dst.contains("3.000000 JAC 'run2'"));
        expect("and the analysis carries the same one", true,
                dst.contains("3.000000 EIG 'run2'"));
        // Distinct suffixes, so one basename covers a whole run and two runs
        // in one directory cannot overwrite each other.
        expect("four Jacobian suffixes", 4, SsaDisturbance.JACOBIAN_SUFFIXES.length);
        for (String suffix : SsaDisturbance.JACOBIAN_SUFFIXES) {
            expect("Jacobian suffix " + suffix + " does not collide with a results file",
                    false, suffix.equals("_modes.dat") || suffix.equals("_pf.dat")
                            || suffix.equals("_ms.dat"));
        }
    }

    private static void checkDisturbanceLaterTime() {
        String dst = SsaDisturbance.text("run2", 5.0);
        expect("a later analysis time reaches the EIG record", true,
                dst.contains("5.000000 EIG 'run2'"));
        expect("and moves STOP after it", true, dst.contains("5.010000 STOP"));
        expect("the Jacobian moves with it", true, dst.contains("5.000000 JAC 'run2'"));
        expect("still no other event", 1, countOf(dst, "EIG"));
        expect("the basename reaches the record", true, dst.contains("'run2'"));
        // Fixed point, not scientific notation, so the file reads like every
        // other disturbance file in the project.
        expect("no scientific notation in the times", false, dst.contains("E-"));
    }

    private static void checkDisturbanceRejectsBadBasename() {
        expect("an apostrophe is rejected, it would end the quoted argument",
                false, SsaDisturbance.validBasename("it's"));
        expect("a path separator is rejected", false,
                SsaDisturbance.validBasename("a/b"));
        expect("a space is rejected", false, SsaDisturbance.validBasename("two words"));
        expect("an empty basename is rejected", false, SsaDisturbance.validBasename(""));
        expect("a null basename is rejected", false, SsaDisturbance.validBasename(null));
        expect("the default basename is accepted", true,
                SsaDisturbance.validBasename("ssa"));
        expect("dots, dashes and underscores are accepted", true,
                SsaDisturbance.validBasename("run-2_a.1"));
        try {
            SsaDisturbance.text("it's", 1.0);
            fail("text() rejects a bad basename");
        } catch (IllegalArgumentException expected) {
            pass("text() rejects a bad basename");
        }
    }

    private static void checkDisturbanceRejectsEarlyOrUnreadableTime() {
        expect("the default parses", SsaDisturbance.MIN_TIME,
                SsaDisturbance.parseTime(" 0.001 "));
        expect("a later time parses", 12.5, SsaDisturbance.parseTime("12.5"));
        expectTimeRejected("a time before the minimum is rejected", "0.0005");
        expectTimeRejected("zero is rejected", "0");
        expectTimeRejected("a negative time is rejected", "-1");
        expectTimeRejected("text is rejected", "soon");
        expectTimeRejected("an empty field is rejected", "");
    }

    private static void expectTimeRejected(String what, String text) {
        try {
            SsaDisturbance.parseTime(text);
            fail(what + ": no exception");
        } catch (IllegalArgumentException expected) {
            pass(what);
        }
    }

    /**
     * The real banner, as {@code ramses -v} prints it. The trap is the
     * "(Full Version)" suffix on the same line: a pattern matching bare
     * "Version" rather than "Version:" finds that instead and parses nothing.
     */
    private static final String BANNER =
            "\nRApid Multithreaded Simulation of Electric power Systems\n"
            + "Version:  3.74 (Full Version)\n\n"
            + "Part of the STEPSS simulation platform -- https://stepss.sps-lab.org\n";

    private static void checkEngineVersionParsesBanner() {
        expect("banner version", 3.74,
                round(EngineVersion.parseBanner(BANNER), 2));
        expect("banner version, limited edition", 3.74,
                round(EngineVersion.parseBanner(
                        "Version:  3.74 (Limited Version)"), 2));
        expect("banner with no version line", true,
                Double.isNaN(EngineVersion.parseBanner("no banner here")));
        expect("null banner", true,
                Double.isNaN(EngineVersion.parseBanner(null)));
    }

    /**
     * The boundary is the whole point: 3.74 is the first engine that accepts
     * the parameters, and it is printed from a single precision constant, so
     * an exact {@code >=} against the parsed text is what has to hold.
     */
    private static void checkEngineVersionGuardsTheBoundary() {
        expect("3.73 unsupported", false,
                EngineVersion.supportsEigParameters(3.73));
        expect("3.74 supported", true,
                EngineVersion.supportsEigParameters(3.74));
        expect("3.74 parsed from a banner is supported", true,
                EngineVersion.supportsEigParameters(
                        EngineVersion.parseBanner(BANNER)));
        expect("3.80 supported", true,
                EngineVersion.supportsEigParameters(3.80));
        expect("4.00 supported", true,
                EngineVersion.supportsEigParameters(4.00));
        // An engine that could not be read must not be treated as new enough.
        expect("unknown version unsupported", false,
                EngineVersion.supportsEigParameters(Double.NaN));
    }

    /**
     * The parameters ride on the EIG record only, leaving JAC, the solver
     * record and the STOP margin exactly as the two-argument form writes them.
     */
    private static void checkDisturbanceCarriesParameters() {
        String text = SsaDisturbance.text("ssa", 0.001, -0.5, 0.10);
        expect("EIG carries both parameters", true,
                text.contains("0.001000 EIG 'ssa' -0.500000 0.100000"));
        expect("JAC is untouched by the parameters", true,
                text.contains("0.001000 JAC 'ssa'\n"));
        // Same record set otherwise: only the EIG line differs from the
        // two-argument form, which is what lets one form be built from the
        // other rather than duplicated.
        String plain = SsaDisturbance.text("ssa", 0.001);
        expect("only the EIG line differs", plain.replace(
                "0.001000 EIG 'ssa'\n",
                "0.001000 EIG 'ssa' -0.500000 0.100000\n"), text);
        // A comma-decimal locale would emit "-0,5", which the engine reads as
        // two items and refuses as an overlong record.
        expect("no comma decimals", false, text.contains(","));
    }

    private static void checkDisturbanceRejectsUnreadableParameters() {
        rejectsParameter("parameter that is not a number", "abc");
        rejectsParameter("empty parameter", "");
        rejectsParameter("infinite parameter", "Infinity");
        rejectsParameter("NaN parameter", "NaN");
        try {
            SsaDisturbance.text("ssa", 0.001, Double.NaN, 0.10);
            fail("NaN real limit reaches the record: no exception");
        } catch (IllegalArgumentException expected) {
            pass("NaN real limit reaches the record");
        }
    }

    private static void rejectsParameter(String what, String text) {
        try {
            SsaDisturbance.parseParameter(text, "Real part limit");
            fail(what + ": no exception");
        } catch (IllegalArgumentException expected) {
            pass(what);
        }
    }

    /**
     * The two records the engine refuses the analysis without, spelled as
     * {@code get_settings.f90} reads them: one field each, terminated by a
     * semicolon, one record per line.
     */
    private static void checkSettingsCarryTheTwoRequiredRecords() {
        String dat = SsaSettings.text();
        expect("the decomposed scheme is set", true,
                dat.contains("\n$SCHEME DE "));
        expect("the synchronous reference frame is set", true,
                dat.contains("\n$OMEGA_REF SYN "));
        // One field each: get_settings refuses either record with any other
        // count, which would stop the whole run rather than the analysis.
        expect("$SCHEME carries one field", 1,
                fieldsOfRecord(dat, "$SCHEME"));
        expect("$OMEGA_REF carries one field", 1,
                fieldsOfRecord(dat, "$OMEGA_REF"));
        expect("both records are terminated", 2, countOf(dat, ";"));
        expect("the file ends with a newline", true, dat.endsWith("\n"));
    }

    /**
     * Nothing else is in the file. Every record here lands after the case's
     * own and therefore replaces it, so an extra one would change a user's
     * run without being asked. $EIG_MAX_STATES is the one most likely to be
     * added in sympathy: it is a memory guard, and raising it on the user's
     * behalf trades a refusal for an out-of-memory kill.
     */
    private static void checkSettingsOverrideNothingElse() {
        String dat = SsaSettings.text();
        expect("exactly two settings records", 2, countOf(dat, "$"));
        expect("$EIG_MAX_STATES is not raised behind the user's back", false,
                dat.contains("$EIG_MAX_STATES"));
        // Comments are '#', which loadrec skips. A '!' line is kept as a
        // comment record and would be carried into the run's output.
        for (String line : dat.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("$")) {
                expect("a non-record line is a skipped comment: " + trimmed,
                        true, trimmed.startsWith("#"));
            }
        }
    }

    /**
     * The generated file cannot be mistaken for, or overwrite, anything else
     * the same run writes into the same directory.
     */
    private static void checkSettingsFileNameCannotCollide() {
        expect("named from the basename", "ssaEig.dat", SsaSettings.fileName("ssa"));
        expect("and follows a renamed run", "run2Eig.dat",
                SsaSettings.fileName("run2"));
        String name = SsaSettings.fileName("ssa");
        for (String suffix : SsaDisturbance.JACOBIAN_SUFFIXES) {
            expect("does not collide with the Jacobian's " + suffix,
                    false, name.equals("ssa" + suffix));
        }
        for (String suffix : new String[] {"_modes.dat", "_pf.dat", "_ms.dat"}) {
            expect("does not collide with the results' " + suffix,
                    false, name.equals("ssa" + suffix));
        }
        expect("nor with the disturbance the same run generates", false,
                name.equals("ssaEig.dst"));
        // The name is written into the command file and opened as a file, so
        // it inherits the disturbance's basename rules rather than a second,
        // laxer set of its own.
        try {
            SsaSettings.fileName("two words");
            fail("an invalid basename is rejected: no exception");
        } catch (IllegalArgumentException expected) {
            pass("an invalid basename is rejected");
        }
    }

    /** How many whitespace-separated fields a record carries, ';' excluded. */
    private static int fieldsOfRecord(String text, String type) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(type + " ")) {
                continue;
            }
            String body = trimmed.substring(type.length()).replace(";", "").trim();
            return body.isEmpty() ? 0 : body.split("\\s+").length;
        }
        return -1;
    }

    // ------------------------------------------------------------ archives
    //
    // These are the one group here that touches the filesystem, because the
    // thing under test is a file: a manifest can be checked as a string, but
    // "a colleague can open this on another machine" cannot. Every one of them
    // works inside a directory it makes and removes, so a failed run leaves
    // nothing behind either.

    private static final String[] EQS_LINES = {"1 2 3", "4 5 6"};

    private static SsaArchive.Manifest fixtureManifest() {
        return new SsaArchive.Manifest("run", Double.valueOf(3.74),
                Double.valueOf(0.001), Double.valueOf(-1.0),
                Double.valueOf(0.05), "3.74.12");
    }

    /**
     * Lays out one run's files, as ssa.f90 would have left them, and returns
     * the directory. {@code _struc.dat} is deliberately absent: a run whose
     * Jacobian was only partly written is the case the save path has to report
     * rather than hide.
     */
    private static java.io.File runDirectory(java.io.File root) throws java.io.IOException {
        java.io.File dir = new java.io.File(root, "run-dir");
        dir.mkdirs();
        write(new java.io.File(dir, "run_modes.dat"), modesFixture());
        write(new java.io.File(dir, "run_pf.dat"), join(PF_LINES));
        write(new java.io.File(dir, "run_ms.dat"), join(MS_LINES));
        write(new java.io.File(dir, "run_eqs.dat"), join(EQS_LINES));
        write(new java.io.File(dir, "run_var.dat"), "var\n");
        write(new java.io.File(dir, "run_val.dat"), "val\n");
        return dir;
    }

    private static void write(java.io.File file, String text) throws java.io.IOException {
        java.nio.file.Files.write(file.toPath(),
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String read(java.io.File file) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static java.io.File scratch() throws java.io.IOException {
        return java.nio.file.Files.createTempDirectory("ssa-harness-").toFile();
    }

    private static void checkManifestRoundTrip() {
        try {
            SsaArchive.Manifest written = fixtureManifest();
            SsaArchive.Manifest read = SsaArchive.Manifest.parse(written.text());
            expect("basename survives the manifest", "run", read.basename());
            expect("engine version survives", Double.valueOf(3.74), read.engineVersion());
            expect("analysis time survives", Double.valueOf(0.001), read.time());
            expect("real_limit survives", Double.valueOf(-1.0), read.realLimit());
            expect("pf_threshold survives", Double.valueOf(0.05), read.pfThreshold());
            expect("the writing version survives", "3.74.12", read.savedBy());
            // Whoever opens the archive in a file manager reads this file and
            // nothing else, so it has to say what is and is not in there.
            expect("the manifest says the data files are not included", true,
                    written.text().contains("are NOT here"));
        } catch (java.io.IOException ex) {
            fail("manifest round trip: threw " + ex);
        }
    }

    /**
     * An engine older than EIG_PARAMETERS_SINCE never sees the two parameters,
     * so the archive must record that they are unknown rather than record the
     * zeros the disabled fields hold. The results window renders null as
     * "not recorded", and a recorded 0.0 would be a claim about a run.
     */
    private static void checkManifestOmitsWhatWasNotRecorded() {
        try {
            SsaArchive.Manifest sparse = new SsaArchive.Manifest("run", null,
                    Double.valueOf(0.001), null, null, null);
            String text = sparse.text();
            expect("an unknown real_limit is not written", false,
                    text.contains("real_limit"));
            expect("an unknown pf_threshold is not written", false,
                    text.contains("pf_threshold"));
            expect("an unread engine version is not written", false,
                    text.contains("engine_version"));
            SsaArchive.Manifest read = SsaArchive.Manifest.parse(text);
            expect("an absent real_limit reads back as unknown, not zero",
                    null, read.realLimit());
            expect("an absent pf_threshold reads back as unknown", null,
                    read.pfThreshold());
            expect("an absent engine version reads back as unknown", null,
                    read.engineVersion());
            expect("what was recorded still is", Double.valueOf(0.001), read.time());
        } catch (java.io.IOException ex) {
            fail("sparse manifest: threw " + ex);
        }
    }

    private static void checkManifestRefusals() {
        manifestRejected("a file with no magic line is not a manifest",
                "basename run\n");
        // A newer STEPSS may lay the archive out differently, and opening it
        // anyway would be a guess presented as a result.
        manifestRejected("a newer archive format is refused",
                SsaArchive.MAGIC_PREFIX + (SsaArchive.FORMAT_VERSION + 1)
                        + "\nbasename run\n");
        manifestRejected("a manifest with no basename is refused",
                SsaArchive.MAGIC_PREFIX + "1\nt 0.001\n");
        // The basename becomes a file name, and this one arrives from a file
        // someone else wrote.
        manifestRejected("a basename carrying a path separator is refused",
                SsaArchive.MAGIC_PREFIX + "1\nbasename ../../etc/passwd\n");
        manifestRejected("a basename carrying a space is refused",
                SsaArchive.MAGIC_PREFIX + "1\nbasename two words\n");
    }

    private static void manifestRejected(String what, String text) {
        try {
            SsaArchive.Manifest.parse(text);
            fail(what + ": no exception");
        } catch (java.io.IOException expected) {
            pass(what);
        }
    }

    private static void checkArchiveMembers() {
        String[] members = SsaArchive.members("run");
        expect("three results and four Jacobian files", 7, members.length);
        expect("the modes file leads, since it is the one that must be there",
                "run_modes.dat", members[0]);
        java.util.Set<String> unique =
                new java.util.HashSet<String>(java.util.Arrays.asList(members));
        expect("no member is archived twice", 7, unique.size());
        for (String suffix : SsaDisturbance.JACOBIAN_SUFFIXES) {
            expect("the Jacobian's " + suffix + " is a member", true,
                    unique.contains("run" + suffix));
        }
    }

    private static void checkArchiveNaming() {
        expect("a .zip name is a zip", SsaArchive.Format.ZIP,
                SsaArchive.formatOfName("run.zip"));
        expect("a .tar.gz name is a gzipped tar", SsaArchive.Format.TAR_GZ,
                SsaArchive.formatOfName("run.tar.gz"));
        expect("a .tgz name is read as one too", SsaArchive.Format.TAR_GZ,
                SsaArchive.formatOfName("run.tgz"));
        expect("the extension is matched case insensitively",
                SsaArchive.Format.ZIP, SsaArchive.formatOfName("RUN.ZIP"));
        expect("a bare name spells no format", null,
                SsaArchive.formatOfName("run"));
        // A name that already carries the right extension keeps it, so a user
        // who typed "run.zip" does not get "run.zip.zip".
        expect("an extension already present is not repeated", "run.zip",
                SsaArchive.named(new java.io.File("run.zip"),
                        SsaArchive.Format.ZIP).getName());
        expect("a bare name gains the format's extension", "run.tar.gz",
                SsaArchive.named(new java.io.File("run"),
                        SsaArchive.Format.TAR_GZ).getName());
        // The format the user picked wins over the one the name spells, so
        // saving "run.zip" as a tarball cannot produce a lying extension.
        expect("a mismatched extension is corrected", "run.zip.tar.gz",
                SsaArchive.named(new java.io.File("run.zip"),
                        SsaArchive.Format.TAR_GZ).getName());
    }

    /**
     * The whole feature in one check: analyse, save, hand the file over, open
     * it somewhere else, see the same run.
     */
    private static void checkArchiveRoundTrip(SsaArchive.Format format) {
        String label = format == SsaArchive.Format.ZIP ? "zip" : "tar.gz";
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File dir = runDirectory(root);
            java.io.File target = SsaArchive.named(
                    new java.io.File(root, "handed-over"), format);
            SsaArchive.save(target, format, dir, fixtureManifest());
            expect(label + ": saving produces one file", true, target.isFile());
            expect(label + ": and that file is not empty", true, target.length() > 0);

            // Opened from a scratch directory that has never seen the run, the
            // way the receiving machine will.
            java.io.File elsewhere = new java.io.File(root, "elsewhere");
            elsewhere.mkdirs();
            SsaArchive.Loaded loaded = SsaArchive.load(target, elsewhere);
            expect(label + ": the run keeps its name", "run",
                    loaded.results().basename());
            expect(label + ": the manifest comes back", Double.valueOf(3.74),
                    loaded.manifest().engineVersion());
            expect(label + ": every mode comes back", 5,
                    loaded.results().modes().modes().size());
            expect(label + ": the header parameters come back",
                    Double.valueOf(-1.0), loaded.results().modes().realLimit());
            expect(label + ": participation comes back", 3,
                    loaded.results().participation().forMode(2).size());
            expect(label + ": a leading blank in a device name survives the archive",
                    " G2", loaded.results().participation().forMode(2).get(2).device);
            expect(label + ": mode shapes come back", 2,
                    loaded.results().shapes().forMode(2).size());
            // The point of carrying the Jacobian at all: it has to arrive
            // byte for byte, not merely be listed.
            java.io.File eqs = new java.io.File(loaded.results().directory(),
                    "run_eqs.dat");
            expect(label + ": the Jacobian arrives", true, eqs.isFile());
            expect(label + ": the Jacobian arrives unchanged", join(EQS_LINES),
                    read(eqs));
            // Unpacked under a directory named for the run, so unpacking by
            // hand does not scatter eight files into the current directory.
            expect(label + ": the archive holds one folder named for the run",
                    "run", loaded.results().directory().getName());
        } catch (java.io.IOException ex) {
            fail(label + " round trip: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    /**
     * A Jacobian file the engine never wrote must be named, not silently
     * dropped: the archive is still worth having, and the recipient has to
     * know what is not in it.
     */
    private static void checkArchiveReportsMissingMembers() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File dir = runDirectory(root);
            java.util.List<String> absent = SsaArchive.save(
                    new java.io.File(root, "run.zip"), SsaArchive.Format.ZIP,
                    dir, fixtureManifest());
            expect("exactly the missing member is reported", "[run_struc.dat]",
                    String.valueOf(absent));
            // A missing Jacobian file is a fault: one JAC record writes all
            // four, so losing one means something went wrong.
            expect("a missing Jacobian file is not written off as optional",
                    false, SsaArchive.isOptional("run_struc.dat"));
            // These two are absent from every run that filtered all its modes,
            // and treating that as a fault would put a warning dialog in front
            // of an ordinary result.
            for (String suffix : SsaArchive.OPTIONAL_SUFFIXES) {
                expect("an absent " + suffix + " is a result, not a fault",
                        true, SsaArchive.isOptional("run" + suffix));
            }
            expect("the modes file is never optional", false,
                    SsaArchive.isOptional("run_modes.dat"));
        } catch (java.io.IOException ex) {
            fail("missing member report: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    /**
     * Without the modes file there is nothing to load back, so the archive
     * would be one that fails to open rather than one that opens empty.
     */
    private static void checkArchiveRefusesToSaveWithoutModes() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File dir = new java.io.File(root, "empty");
            dir.mkdirs();
            java.io.File target = new java.io.File(root, "run.zip");
            try {
                SsaArchive.save(target, SsaArchive.Format.ZIP, dir, fixtureManifest());
                fail("saving a run with no modes file is refused");
            } catch (java.io.IOException expected) {
                pass("saving a run with no modes file is refused");
            }
            expect("and writes nothing", false, target.exists());
            expect("not even a partial file", 0,
                    dir.getParentFile().listFiles(new java.io.FilenameFilter() {
                        @Override
                        public boolean accept(java.io.File d, String name) {
                            return name.endsWith(".part");
                        }
                    }).length);
        } catch (java.io.IOException ex) {
            fail("refusing to save without modes: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    private static void checkArchiveRefusesAForeignFile() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File notAnArchive = new java.io.File(root, "notes.txt");
            write(notAnArchive, "these are my notes\n");
            loadRejected("a plain text file is refused", notAnArchive, root,
                    "neither a zip nor a gzipped tar");
        } catch (java.io.IOException ex) {
            fail("refusing a foreign file: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    /** A real zip, just not one of ours. */
    private static void checkArchiveRefusesAnArchiveOfSomethingElse() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File zip = new java.io.File(root, "holiday.zip");
            writeZip(zip, new String[] {"beach.jpg", "hotel.jpg"},
                    new String[] {"not really a jpeg", "nor this"});
            loadRejected("a zip of something else is refused", zip, root,
                    SsaArchive.MANIFEST_NAME);
            // The near miss is the useful one: a zip holding the results
            // themselves but none of the manifest that makes it ours. It is
            // refused for the same stated reason as the holiday photos, which
            // is the whole point: what makes an archive loadable is the
            // manifest, not the presence of something that parses.
            java.io.File results = new java.io.File(root, "results.zip");
            writeZip(results, new String[] {"run_modes.dat"},
                    new String[] {modesFixture()});
            loadRejected("a hand made zip of results is refused too",
                    results, root, SsaArchive.MANIFEST_NAME);
        } catch (java.io.IOException ex) {
            fail("refusing a foreign archive: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    private static void checkArchiveRefusesAMissingModesFile() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File zip = new java.io.File(root, "hollow.zip");
            writeZip(zip,
                    new String[] {SsaArchive.MANIFEST_NAME, "run_eqs.dat"},
                    new String[] {fixtureManifest().text(), join(EQS_LINES)});
            loadRejected("an archive with no modes file says which file is missing",
                    zip, root, "run_modes.dat");
        } catch (java.io.IOException ex) {
            fail("refusing a hollow archive: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    /**
     * The archive is a file from someone else by design, so an entry that
     * climbs out of the unpack directory is the attack this feature invites.
     */
    private static void checkArchiveRefusesAnEscapingEntry() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File zip = new java.io.File(root, "escape.zip");
            writeZip(zip,
                    new String[] {SsaArchive.MANIFEST_NAME, "../../escaped.txt"},
                    new String[] {fixtureManifest().text(), "owned\n"});
            loadRejected("an entry that climbs out of the folder is refused",
                    zip, root, "outside");
            expect("and nothing is written where it aimed", false,
                    new java.io.File(root.getParentFile(), "escaped.txt").exists());
        } catch (java.io.IOException ex) {
            fail("refusing an escaping entry: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    /**
     * The other side of the escape check, and the one that is easy to get
     * wrong: {@code tar czf run.tar.gz .} names its own root "./", which
     * resolves to the unpack directory itself rather than to something inside
     * it. A guard written as "must be strictly below" refuses the commonest
     * repacked archive there is and calls it an attack.
     */
    private static void checkArchiveAcceptsAnArchiveNamingItsOwnRoot() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File zip = new java.io.File(root, "repacked.zip");
            writeZip(zip,
                    new String[] {"./", "./" + SsaArchive.MANIFEST_NAME,
                        "./run_modes.dat"},
                    new String[] {"", fixtureManifest().text(), modesFixture()});
            java.io.File elsewhere = new java.io.File(root, "elsewhere");
            elsewhere.mkdirs();
            SsaArchive.Loaded loaded = SsaArchive.load(zip, elsewhere);
            expect("an archive that names its own root still opens", "run",
                    loaded.manifest().basename());
            expect("and its results are read", 5,
                    loaded.results().modes().modes().size());
        } catch (java.io.IOException ex) {
            fail("archive naming its own root: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    /**
     * The one thing a loaded archive knows that the results window does not.
     * Its header reads t, real_limit and pf_threshold out of the modes file,
     * but no file in the run says which engine wrote it, so an archive from
     * another build looks exactly like one from this one.
     */
    private static void checkArchiveNamesTheEngineThatAnalysedIt() {
        SsaArchive.Manifest known = fixtureManifest();
        String same = SsaArchive.describe(known, "run.zip", 3.74);
        expect("the run and the file are named", true,
                same.contains("\"run\"") && same.contains("run.zip"));
        expect("the engine that analysed it is named", true,
                same.contains("RAMSES 3.74"));
        expect("the same engine is not reported as a difference", false,
                same.contains("loaded here"));
        // The banner prints f5.2 from a single precision constant, so two
        // readings of one build can differ by an ulp. Reporting that as two
        // builds would cry wolf on every load.
        expect("an ulp of difference is still the same engine", false,
                SsaArchive.describe(known, "run.zip", 3.7400001)
                        .contains("loaded here"));
        String other = SsaArchive.describe(known, "run.zip", 3.80);
        expect("a different engine is reported", true,
                other.contains("not the 3.80 loaded here"));
        // The archive still names its own engine when this session has none to
        // compare against, which is the case on a machine with no licence.
        String noEngine = SsaArchive.describe(known, "run.zip", Double.NaN);
        expect("an unreadable local engine is not reported as a difference",
                false, noEngine.contains("loaded here"));
        expect("but the archive's own engine is still named", true,
                noEngine.contains("RAMSES 3.74"));
        String unknown = SsaArchive.describe(
                new SsaArchive.Manifest("run", null, null, null, null, null),
                "run.zip", 3.74);
        expect("an archive that recorded no engine says so", true,
                unknown.contains("does not record which engine"));
        expect("and does not invent one", false, unknown.contains("RAMSES"));
        // The banner is a JLabel, which renders a newline as a missing glyph.
        for (String line : new String[] {same, other, noEngine, unknown}) {
            expect("the sentence is one line", false, line.contains("\n"));
        }
    }

    /**
     * Read from the bytes, not the name. An archive that came through a mail
     * client as "archive.dat", or one a user renamed, is still the archive it
     * was.
     */
    private static void checkArchiveSurvivesARenamedExtension() {
        java.io.File root = null;
        try {
            root = scratch();
            java.io.File dir = runDirectory(root);
            java.io.File target = new java.io.File(root, "run.tar.gz");
            SsaArchive.save(target, SsaArchive.Format.TAR_GZ, dir, fixtureManifest());
            java.io.File renamed = new java.io.File(root, "archive.dat");
            expect("the archive can be renamed", true, target.renameTo(renamed));
            java.io.File elsewhere = new java.io.File(root, "elsewhere");
            elsewhere.mkdirs();
            SsaArchive.Loaded loaded = SsaArchive.load(renamed, elsewhere);
            expect("a renamed archive still opens", "run",
                    loaded.results().basename());
        } catch (java.io.IOException ex) {
            fail("renamed archive: threw " + ex);
        } finally {
            SsaArchive.deleteRecursively(root);
        }
    }

    /**
     * Every refusal has to say why, and leave nothing behind: a half unpacked
     * archive in the scratch directory is a load that changed something.
     */
    private static void loadRejected(String what, java.io.File archive,
            java.io.File root, String expectedInMessage) {
        java.io.File scratchParent = new java.io.File(root, "scratch");
        scratchParent.mkdirs();
        try {
            SsaArchive.load(archive, scratchParent);
            fail(what + ": no exception");
        } catch (java.io.IOException expected) {
            String message = String.valueOf(expected.getMessage());
            expect(what, true, message.contains(expectedInMessage));
            expect(what + ", naming the file", true,
                    message.contains(archive.getName()));
            String[] left = scratchParent.list();
            expect(what + ", leaving nothing unpacked", 0,
                    left == null ? -1 : left.length);
        } finally {
            SsaArchive.deleteRecursively(scratchParent);
        }
    }

    private static void writeZip(java.io.File target, String[] names, String[] contents)
            throws java.io.IOException {
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(target));
        try {
            for (int i = 0; i < names.length; i++) {
                zip.putNextEntry(new java.util.zip.ZipEntry(names[i]));
                zip.write(contents[i].getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } finally {
            zip.close();
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
