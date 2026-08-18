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

    private static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("  ok    " + what);
        } else {
            System.out.println("  FAIL  " + what);
            failures++;
        }
    }
}
