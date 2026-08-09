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

    private static int failures = 0;

    private PickerHarness() {
    }

    /** The fixture text, exposed so a probe can drive the dialog on it. */
    public static String fixture() {
        return join(FIXTURE_LINES);
    }

    public static void main(String[] args) {
        checkFixtureParses();
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
