package my.ramses.dyngraph;

/**
 * Headless checks for the observable-picker pipeline: fixture -> parse ->
 * scripted selections -> emit -> compare. This repository has no unit-test
 * framework and is not gaining one, so this main() is where parsing and
 * replay emission are pinned; the dialog itself is covered by manual
 * acceptance, and DyngraphRunner deliberately stays out of here - the
 * harness launches no process and needs no extracted payload, so it runs on
 * a bare checkout with build/classes present.
 *
 * <p>The fixture is a hand-extended version of stepss-dyngraph's
 * tests/golden/list.txt, embedded as string literals rather than kept as a
 * data file: the format's one trap is a leading blank in a name, which is
 * invisible in a text file, silently stripped by many editors, and exposed
 * to CRLF normalisation. In a quoted Java literal it is explicit and cannot
 * be lost. (Java 11: no text blocks, hence the joined arrays.)
 *
 * <p>The golden is extended rather than reused as-is because it carries
 * exactly one injector, one link, one DCTL, one exciter and one torque
 * controller, each with a single observable - it cannot distinguish a
 * correct per-instance parser from one that pools sub-lists per category.
 */
public final class PickerHarness {

    /**
     * The extended index. Deliberate traps, in order of appearance: a bus
     * named " LEADBUS" (the leading blank is part of the name); a bus named
     * "END" (an instance name colliding with the terminator tag); an empty
     * LOAD name (what Fortran trim() emits for an all-blank name); a branch
     * named "S" (colliding with the stop keyword); GEN2 with "EXC 0" and a
     * non-empty TOR; INJ2 whose OBS list differs from INJ1's; DCTL2 with
     * "OBS 0"; and a DCTL observable named "ST", which is also the sync
     * keyword for mechanical torque.
     */
    private static final String[] FIXTURE_LINES = {
        "DYNGRAPH-INDEX 1",
        "TYPES BUS 2",
        "BM voltage magnitude (pu)",
        "BA voltage phase angle (deg)",
        "TYPES SHUNT 1",
        "HQ reactive power produced (Mvar)",
        "TYPES LOAD 2",
        "LP active power consumed (MW)",
        "LQ reactive power consumed (Mvar)",
        "TYPES BRANCH 6",
        "RPF P (MW) entering at FROM end",
        "RQF Q (Mvar) entering at FROM end",
        "RPT P (MW) entering at TO end",
        "RQT Q (Mvar) entering at TO end",
        "RRM magnitude of transformer ratio",
        "RRA phase angle of transformer ratio (deg)",
        "TYPES SYNC 17",
        "SP active power produced (MW)",
        "SQ reactive power produced (Mvar)",
        "SA rotor angle wrt COI (deg)",
        "SS rotor speed (pu)",
        "SFW flux in field winding (pu mach. base)",
        "SDD flux in d1 damper (pu mach. base)",
        "SQD flux in q1 damper (pu mach. base)",
        "SQW flux in q2 winding (pu mach. base)",
        "SFC field current (pu)",
        "SFV field voltage (pu)",
        "ST mechanical torque (pu)",
        "SET electromagnetic torque (pu mach. base)",
        "SSC speed of COI reference (pu)",
        "SEQ q-axis internal voltage (pu)",
        "SED d-axis internal voltage (pu)",
        "SOE observable of excitation controller",
        "SOT observable of torque controller",
        "BUS 4",
        "BUS1",
        "BUS2",
        " LEADBUS",
        "END",
        "SHUNT 1",
        "SHUNT1",
        "LOAD 2",
        "LOAD1",
        "",
        "BRANCH 2",
        "BR1-2",
        "S",
        "SYNC 2",
        "GEN1",
        "EXC 1",
        "VF",
        "TOR 1",
        "TM",
        "GEN2",
        "EXC 0",
        "TOR 1",
        "Pm",
        "INJ 2",
        "INJ1",
        "OBS 1",
        "P",
        "INJ2",
        "OBS 2",
        "Q",
        "omega",
        "LINK 1",
        "LINK1",
        "OBS 1",
        "I1",
        "DCTL 2",
        "DCTL1",
        "OBS 1",
        "ST",
        "DCTL2",
        "OBS 0",
        "END"
    };

    /**
     * The expected replay stream for the scripted picks below, embedded like
     * the fixture so the round trip is pinned byte for byte, the way
     * DYNGRAPH's own smoke gate pins its goldens. Note " LEADBUS" keeping
     * its leading blank, the names "END" and "S" travelling as name lines,
     * and "ST" travelling as a DCTL observable name - position in the
     * stream, never content, decides what a line means.
     */
    private static final String[] EXPECTED_REPLAY_LINES = {
        "output.trj",
        "BM",
        "BUS1",
        "BM",
        " LEADBUS",
        "BM",
        "END",
        "RPF",
        "S",
        "SS",
        "GEN1",
        "SOE",
        "GEN1",
        "VF",
        "SOT",
        "GEN2",
        "Pm",
        "I",
        "INJ2",
        "omega",
        "T",
        "LINK1",
        "I1",
        "D",
        "DCTL1",
        "ST",
        "",
        "S"
    };

    private static int failures = 0;

    private PickerHarness() {
    }

    /** The fixture text, exposed so a probe can drive the dialog on it. */
    public static String fixture() {
        return join(FIXTURE_LINES);
    }

    public static void main(String[] args) {
        checkFixtureParses();
        checkTypeTables();
        checkInstanceNames();
        checkPerInstanceSubLists();
        checkEmptyIndexDetection();
        checkVersionRefused();
        checkForeignHeaderRefused();
        checkTruncationNamesTheLine();
        checkMissingEndNamed();
        checkUnexpectedTagNamed();
        checkMisplacedSubListNamed();
        checkUnreadableCountNamed();
        checkLabelComposition();
        checkSelectionShapeValidation();
        checkReplayRoundTrip();
        checkReplayRejectsEmptySelection();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkFixtureParses() {
        try {
            ObservableIndex.parse(fixture());
            pass("the extended fixture parses");
        } catch (Exception ex) {
            fail("the extended fixture parses: threw " + ex);
        }
    }

    private static ObservableIndex parsedFixture() {
        try {
            return ObservableIndex.parse(fixture());
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    private static void checkTypeTables() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("type tables: fixture did not parse");
            return;
        }
        expect("BUS type count", 2, index.types("BUS").size());
        expect("SHUNT type count", 1, index.types("SHUNT").size());
        expect("LOAD type count", 2, index.types("LOAD").size());
        expect("BRANCH type count", 6, index.types("BRANCH").size());
        expect("SYNC type count", 17, index.types("SYNC").size());
        expect("first BUS keyword", "BM", index.types("BUS").get(0).keyword);
        expect("first BUS label", "voltage magnitude (pu)", index.types("BUS").get(0).label);
        expect("a label may contain spaces and parentheses", "P (MW) entering at FROM end",
                index.types("BRANCH").get(0).label);
        expect("SOE sits at SYNC entry 16", "SOE", index.types("SYNC").get(15).keyword);
        expect("SOT sits at SYNC entry 17", "SOT", index.types("SYNC").get(16).keyword);
        expect("INJ has no TYPES table", 0, index.types("INJ").size());
    }

    private static void checkInstanceNames() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("instance names: fixture did not parse");
            return;
        }
        expect("bus count", 4, index.instances("BUS").size());
        expect("first bus", "BUS1", index.instances("BUS").get(0).name);
        expect("a leading blank is part of the name", " LEADBUS",
                index.instances("BUS").get(2).name);
        expect("a bus named END is a name, not a terminator", "END",
                index.instances("BUS").get(3).name);
        expect("the block after the END-named bus still parses", "SHUNT1",
                index.instances("SHUNT").get(0).name);
        expect("an empty line is a legitimate all-blank name", "",
                index.instances("LOAD").get(1).name);
        expect("a branch named S is a name, not the stop keyword", "S",
                index.instances("BRANCH").get(1).name);
    }

    private static void checkPerInstanceSubLists() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("per-instance sub-lists: fixture did not parse");
            return;
        }
        ObservableIndex.Instance gen1 = index.instances("SYNC").get(0);
        ObservableIndex.Instance gen2 = index.instances("SYNC").get(1);
        expect("GEN1 exciter list", "[VF]", gen1.exc.toString());
        expect("GEN1 torque list", "[TM]", gen1.tor.toString());
        expect("EXC 0 parses to an empty list", 0, gen2.exc.size());
        expect("GEN2 keeps its own torque list", "[Pm]", gen2.tor.toString());
        ObservableIndex.Instance inj1 = index.instances("INJ").get(0);
        ObservableIndex.Instance inj2 = index.instances("INJ").get(1);
        expect("INJ1 observables", "[P]", inj1.obs.toString());
        expect("INJ2 observables differ from INJ1's - sub-lists are per instance, never pooled",
                "[Q, omega]", inj2.obs.toString());
        expect("LINK1 observables", "[I1]", index.instances("LINK").get(0).obs.toString());
        expect("DCTL1 observables", "[ST]", index.instances("DCTL").get(0).obs.toString());
        expect("OBS 0 parses to an empty list", 0, index.instances("DCTL").get(1).obs.size());
    }

    private static void checkEmptyIndexDetection() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("empty-index detection: fixture did not parse");
            return;
        }
        expect("the fixture is not empty", false, index.isEmpty());
        try {
            expect("an index with no instances reports empty", true,
                    ObservableIndex.parse("DYNGRAPH-INDEX 1\nEND\n").isEmpty());
        } catch (java.io.IOException ex) {
            fail("an index with no instances reports empty: threw " + ex);
        }
    }

    private static void checkVersionRefused() {
        expectParseError("a higher DYNGRAPH-INDEX version is refused",
                "DYNGRAPH-INDEX 2\nEND\n", "version 2");
    }

    private static void checkForeignHeaderRefused() {
        expectParseError("a non-index first line is reported verbatim",
                "some entirely foreign first line\n", "some entirely foreign first line");
    }

    private static void checkTruncationNamesTheLine() {
        expectParseError("a count overrunning the stream names the line",
                "DYNGRAPH-INDEX 1\nBUS 3\nBUS1\nBUS2", "line 5: unexpected end of input");
    }

    private static void checkMissingEndNamed() {
        expectParseError("a stream without END says so",
                "DYNGRAPH-INDEX 1\nBUS 1\nBUS1\n", "missing END");
    }

    private static void checkUnexpectedTagNamed() {
        expectParseError("an unexpected tag names its line",
                "DYNGRAPH-INDEX 1\nBOGUS 1\nA\nEND\n", "line 2: unexpected tag");
    }

    private static void checkMisplacedSubListNamed() {
        expectParseError("an INJ name without its OBS block names the expectation",
                "DYNGRAPH-INDEX 1\nINJ 1\nINJ1\nEND\n", "expected 'OBS <count>' after 'INJ1'");
    }

    private static void checkUnreadableCountNamed() {
        expectParseError("an unreadable count names its header line",
                "DYNGRAPH-INDEX 1\nBUS x\nEND\n", "line 2: unreadable count");
    }

    private static void checkLabelComposition() {
        expect("bus label", "bus BUS1: voltage magnitude (pu)",
                new Selection("BUS", "BM", "voltage magnitude (pu)", "BUS1", null).label());
        expect("shunt label", "shunt SHUNT1: reactive power produced (Mvar)",
                new Selection("SHUNT", "HQ", "reactive power produced (Mvar)", "SHUNT1", null).label());
        expect("load label says impedance load", "impedance load LOAD1: active power consumed (MW)",
                new Selection("LOAD", "LP", "active power consumed (MW)", "LOAD1", null).label());
        expect("branch label", "branch BR1-2: P (MW) entering at FROM end",
                new Selection("BRANCH", "RPF", "P (MW) entering at FROM end", "BR1-2", null).label());
        expect("sync label says sync mach", "sync mach GEN1: rotor speed (pu)",
                new Selection("SYNC", "SS", "rotor speed (pu)", "GEN1", null).label());
        expect("SOE renders as excit control, not its TYPES label",
                "sync mach GEN1: excit control: VF",
                new Selection("SYNC", "SOE", "observable of excitation controller", "GEN1", "VF").label());
        expect("SOT renders as torque control, not its TYPES label",
                "sync mach GEN2: torque control: Pm",
                new Selection("SYNC", "SOT", "observable of torque controller", "GEN2", "Pm").label());
        expect("injector label", "injector INJ2: omega",
                new Selection("INJ", null, null, "INJ2", "omega").label());
        expect("link label", "link LINK1: I1",
                new Selection("LINK", null, null, "LINK1", "I1").label());
        expect("DCTL label keeps DCTL upper-case", "DCTL DCTL1: ST",
                new Selection("DCTL", null, null, "DCTL1", "ST").label());
        expect("a leading blank stays visible in the label",
                "bus  LEADBUS: voltage magnitude (pu)",
                new Selection("BUS", "BM", "voltage magnitude (pu)", " LEADBUS", null).label());
    }

    private static void checkSelectionShapeValidation() {
        expect("SOE requires a sub-observable", true, Selection.requiresSub("SYNC", "SOE"));
        expect("SOT requires a sub-observable", true, Selection.requiresSub("SYNC", "SOT"));
        expect("SS does not require a sub-observable", false, Selection.requiresSub("SYNC", "SS"));
        expect("every injector pick requires one", true, Selection.requiresSub("INJ", null));
        expectSelectionRejected("SOE without a sub-observable is rejected",
                "SYNC", "SOE", "observable of excitation controller", "GEN1", null);
        expectSelectionRejected("a sub-observable on a plain keyword is rejected",
                "BUS", "BM", "voltage magnitude (pu)", "BUS1", "X");
        expectSelectionRejected("a replay keyword on an injector is rejected - I belongs to ReplayFile",
                "INJ", "I", null, "INJ1", "P");
        expectSelectionRejected("an unknown category is rejected",
                "BOGUS", "BM", "label", "X", null);
    }

    private static void checkReplayRoundTrip() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("replay round trip: fixture did not parse");
            return;
        }
        // Every name and sub-observable below is taken from the parsed
        // index, never retyped, so this pins the full parse -> pick -> emit
        // round trip - the only guard against the silent desync failure,
        // since DYNGRAPH re-prompts rather than aborting on an unmatched
        // name and can exit 0 having plotted the wrong curves.
        ObservableIndex.TypeEntry bm = index.types("BUS").get(0);
        ObservableIndex.TypeEntry rpf = index.types("BRANCH").get(0);
        ObservableIndex.TypeEntry ss = index.types("SYNC").get(3);
        ObservableIndex.TypeEntry soe = index.types("SYNC").get(15);
        ObservableIndex.TypeEntry sot = index.types("SYNC").get(16);
        ObservableIndex.Instance gen1 = index.instances("SYNC").get(0);
        ObservableIndex.Instance gen2 = index.instances("SYNC").get(1);
        ObservableIndex.Instance inj2 = index.instances("INJ").get(1);
        ObservableIndex.Instance link1 = index.instances("LINK").get(0);
        ObservableIndex.Instance dctl1 = index.instances("DCTL").get(0);

        java.util.List<Selection> picks = new java.util.ArrayList<Selection>();
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(0).name, null));
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(2).name, null));
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(3).name, null));
        picks.add(new Selection("BRANCH", rpf.keyword, rpf.label, index.instances("BRANCH").get(1).name, null));
        picks.add(new Selection("SYNC", ss.keyword, ss.label, gen1.name, null));
        picks.add(new Selection("SYNC", soe.keyword, soe.label, gen1.name, gen1.exc.get(0)));
        picks.add(new Selection("SYNC", sot.keyword, sot.label, gen2.name, gen2.tor.get(0)));
        picks.add(new Selection("INJ", null, null, inj2.name, inj2.obs.get(1)));
        picks.add(new Selection("LINK", null, null, link1.name, link1.obs.get(0)));
        picks.add(new Selection("DCTL", null, null, dctl1.name, dctl1.obs.get(0)));

        expect("emit matches the pinned replay stream", join(EXPECTED_REPLAY_LINES),
                ReplayFile.emit("output.trj", picks));
    }

    private static void checkReplayRejectsEmptySelection() {
        try {
            ReplayFile.emit("output.trj", new java.util.ArrayList<Selection>());
            fail("an empty selection list is rejected: no exception");
        } catch (IllegalArgumentException expected) {
            pass("an empty selection list is rejected");
        }
    }

    private static void expectSelectionRejected(String what, String category,
            String keyword, String typeLabel, String name, String sub) {
        try {
            new Selection(category, keyword, typeLabel, name, sub);
            fail(what + ": no exception");
        } catch (IllegalArgumentException expected) {
            pass(what);
        }
    }

    private static void expectParseError(String what, String text, String mustContain) {
        try {
            ObservableIndex.parse(text);
            fail(what + ": no exception");
        } catch (java.io.IOException ex) {
            String message = String.valueOf(ex.getMessage());
            if (message.contains(mustContain)) {
                pass(what);
            } else {
                fail(what + ": message <" + message + "> does not mention <" + mustContain + ">");
            }
        }
    }

    private static String join(String[] lines) {
        return String.join("\n", lines) + "\n";
    }

    private static void expect(String what, Object want, Object got) {
        // Null-safe on both sides, like CompileHarness: a null expectation
        // must read as a comparison, not as an NPE inside the harness itself.
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
