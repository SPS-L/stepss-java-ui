package my.stepss;

/**
 * Headless checks for {@link RamsesOutcome}.
 *
 * <p>This repository has no unit-test framework, so this main() is the
 * substitute, in the shape {@code DiagramCheck} uses for
 * {@link HeliosOutcome}. Run it with
 * {@code java -cp build/classes my.stepss.RamsesOutcomeCheck}.
 *
 * <p>The console texts below are copied from real runs of the engine: the
 * Nordic collapse, and the Nordic case driven into divergence with an
 * unreachable {@code $NEWTON_TOLER}, by a 3.84 engine and by one that flags it.
 */
public final class RamsesOutcomeCheck {

    private static int failures;

    private static final String NORDIC_TRIP = String.join("\n",
            "t =  162.9000",
            "STOP CALL FROM DCTL sim_minmaxvolt Voltages: ",
            "t =  163.1400         Voltage out of bounds at bus g1 :  0.50",
            "STOP CALL FROM DCTL sim_minmaxvolt Voltages: ",
            "t =  163.1400         Voltage out of bounds at bus g6 :  0.50",
            "",
            "Total elapsed time                            3.1000 s",
            "**Simulation finished**");

    private static final String DIVERGED = String.join("\n",
            "**Parallel computing activated (threads: 2)**",
            " ",
            "t =    0.0010            Divergence. Simulation stopped",
            "",
            "Total elapsed time                            0.0048 s",
            "**Writing output observables, please wait**",
            "**Simulation finished**");

    private RamsesOutcomeCheck() {
    }

    public static void main(String[] args) {
        checkReachingStopIsOk();
        checkTheUserStopIsNotAnError();
        checkAnyNonZeroExitIsAnError();
        checkTheTripNamesItsDeviceAndMessage();
        checkTheDivergenceIsQuoted();
        checkTheLastReasonWins();
        checkNoReasonPointsAtTheOutput();
        checkAStopCallWithNoMessageKeepsItsSource();
        System.out.println(failures == 0 ? "ALL RAMSES OUTCOME CHECKS PASSED"
                : failures + " RAMSES OUTCOME CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkReachingStopIsOk() {
        RamsesOutcome outcome = RamsesOutcome.of(0, false, "**Simulation finished**");
        check("exit 0 is OK", outcome.severity() == RamsesOutcome.Severity.OK);
        check("exit 0 says finished", outcome.headline().equals("Simulation finished"));
        check("exit 0 has nothing for the banner", outcome.detail().isEmpty());
    }

    /** Pressing Stop twice kills the process, so its exit value is not the engine's. */
    private static void checkTheUserStopIsNotAnError() {
        RamsesOutcome killed = RamsesOutcome.of(137, true, DIVERGED);
        check("a user stop is STOPPED, whatever the exit",
                killed.severity() == RamsesOutcome.Severity.STOPPED);
        check("and it has nothing for the banner", killed.detail().isEmpty());
        check("a clean user stop is STOPPED too",
                RamsesOutcome.of(0, true, "").severity() == RamsesOutcome.Severity.STOPPED);
    }

    /** -1 reaches Java as 255 on Linux and macOS, 127 through MSYS2, -1 on Windows. */
    private static void checkAnyNonZeroExitIsAnError() {
        for (int exit : new int[]{255, 127, -1, 1}) {
            RamsesOutcome outcome = RamsesOutcome.of(exit, false, "");
            check("exit " + exit + " is an error",
                    outcome.severity() == RamsesOutcome.Severity.ERROR);
            check("exit " + exit + " is named", outcome.detail().contains("exit code " + exit));
        }
    }

    private static void checkTheTripNamesItsDeviceAndMessage() {
        RamsesOutcome outcome = RamsesOutcome.of(255, false, NORDIC_TRIP);
        check("the trip says the run stopped early",
                outcome.headline().contains("before its STOP time"));
        expect("the trip names the device and quotes the message",
                "DCTL sim_minmaxvolt Voltages: t = 163.1400 Voltage out of bounds at bus g6 : 0.50",
                RamsesOutcome.reason(NORDIC_TRIP));
        check("and the banner carries it", outcome.detail().contains("bus g6"));
    }

    private static void checkTheDivergenceIsQuoted() {
        expect("a divergence is quoted from its own line",
                "t = 0.0010 Divergence. Simulation stopped",
                RamsesOutcome.reason(DIVERGED));
    }

    private static void checkTheLastReasonWins() {
        String both = NORDIC_TRIP + "\n" + DIVERGED;
        check("the reason nearest the end is the one given",
                RamsesOutcome.reason(both).contains("Divergence"));
    }

    private static void checkNoReasonPointsAtTheOutput() {
        RamsesOutcome outcome = RamsesOutcome.of(255, false, "t =  1.0000\n**Simulation finished**");
        expect("a progress line is not a reason", "",
                RamsesOutcome.reason("t =  1.0000\n**Simulation finished**"));
        check("without a reason the banner points at the output",
                outcome.detail().contains("near the end of the output"));
        check("null output is handled", RamsesOutcome.reason(null).isEmpty());
    }

    private static void checkAStopCallWithNoMessageKeepsItsSource() {
        expect("a trailing STOP CALL keeps its source", "INJ x",
                RamsesOutcome.reason("STOP CALL FROM INJ x: "));
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + name);
        }
    }

    private static void expect(String name, String want, String got) {
        if (!want.equals(got)) {
            failures++;
            System.out.println("FAIL: " + name + "\n  want: " + want + "\n  got:  " + got);
        }
    }
}
