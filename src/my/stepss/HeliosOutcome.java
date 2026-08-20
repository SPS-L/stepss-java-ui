package my.stepss;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a finished Helios run means, decided once.
 *
 * <p>Two things render this: {@code StepssUI.describeHeliosExit} builds the
 * modal dialog from it, and {@code my.stepss.diagram.DiagramWindow} builds its
 * banner from it. They used to be one decision and one renderer; adding the
 * banner without this class would have made it two decisions that agree only
 * for as long as someone keeps them in step, and the whole point of the banner
 * is that it says the same thing as the status bar and the dialog.
 *
 * <p>Carries no Swing, so the harness exercises it without a display. That is
 * also what {@code describeHeliosExit} claimed for itself and never had:
 * it was package-visible "so it is exercised directly by tests" and nothing in
 * the repository called it.
 *
 * <p>The normative contract is in ../stepss-helios/README.md and
 * docs/tui-guide.md#exit-status: 0 converged, 2 did not converge, 1 input or
 * usage error, anything else undocumented.
 */
public final class HeliosOutcome {

    /** How loudly to say it. Mapped to JOptionPane constants by the caller. */
    public enum Severity {
        /** Nothing to report. */
        OK,
        /** Results exist and are not trustworthy. */
        WARNING,
        /** There may be no results at all. */
        ERROR
    }

    /**
     * Matches the machine-readable status line Helios writes once to stderr on
     * every non-interactive run, for example
     * {@code helios: status: NOT_CONVERGED (max iterations)}. Group 2 is the
     * optional parenthesised reason. Engines before the status contract never
     * write it, which is why nothing here requires it.
     */
    private static final Pattern STATUS_LINE
            = Pattern.compile("helios: status: (\\S+)(?: \\(([^)]*)\\))?");

    /**
     * Matches the message Helios writes when a data file will not load, for
     * example {@code helios: PFCcmd.txt:1: cannot open data file '/x/dyn_A.dat'}.
     *
     * <p>Worth singling out because the message is not true as written: Helios
     * reports a record it cannot parse exactly as it reports a file it cannot
     * find, so the usual cause is a data file that is present and readable and
     * carries something the power-flow reader does not accept. Left to the
     * generic exit-1 wording, the user is told to look for a missing file that
     * is sitting right there.
     */
    private static final Pattern UNREADABLE_DATA
            = Pattern.compile("cannot open data file '([^']*)'");

    private final Severity severity;
    private final String headline;
    private final String detail;

    private HeliosOutcome(Severity severity, String headline, String detail) {
        this.severity = severity;
        this.headline = headline;
        this.detail = detail;
    }

    /** How loudly to say it. */
    public Severity severity() {
        return severity;
    }

    /** The one sentence, matching the status bar's phrasing. Empty when OK. */
    public String headline() {
        return headline;
    }

    /** What follows it, or "" when the headline says everything. */
    public String detail() {
        return detail;
    }

    /**
     * The outcome of a completed run.
     *
     * @param exitValue  the process exit value
     * @param stderrText the run's captured stderr, searched for the status line
     * @return never null; {@link Severity#OK} with an empty headline for exit 0
     */
    public static HeliosOutcome of(int exitValue, String stderrText) {
        if (exitValue == 0) {
            return new HeliosOutcome(Severity.OK, "", "");
        }
        String reason = "";
        Matcher matcher = STATUS_LINE.matcher(stderrText == null ? "" : stderrText);
        if (matcher.find() && matcher.group(2) != null && !matcher.group(2).isEmpty()) {
            reason = " (" + matcher.group(2) + ")";
        }
        switch (exitValue) {
            case 2:
                return new HeliosOutcome(Severity.WARNING,
                        "The power flow did NOT converge" + reason + ".",
                        "Helios still produced and exported result files, but they"
                        + " are NOT a valid power-flow solution. Do not use these values.");
            case 1:
                Matcher data = UNREADABLE_DATA.matcher(stderrText == null ? "" : stderrText);
                if (data.find()) {
                    String name = fileName(data.group(1));
                    return new HeliosOutcome(Severity.ERROR,
                            "Helios could not read " + name + ".",
                            "It says it cannot open the file, which is also what it"
                            + " says about a file it can open and cannot parse. If "
                            + name + " is there on disk, the problem is a record"
                            + " inside it.\n\nA power flow needs the power-flow form"
                            + " of the network, with the bus loads and the generator"
                            + " dispatch. A file of dynamic models is read by RAMSES"
                            + " and not by Helios, so a case set up for the Dynamic"
                            + " Simulation tab may need its load-flow file in the data"
                            + " slot instead.");
                }
                return new HeliosOutcome(Severity.ERROR,
                        "Helios could not process the input" + reason + ".",
                        "It reported an input or usage error and stopped early."
                        + " There may be no results at all.");
            default:
                return new HeliosOutcome(Severity.ERROR,
                        "Helios exited abnormally (status " + exitValue + ").",
                        "That is not a documented outcome. Treat any displayed"
                        + " results with suspicion.");
        }
    }

    /**
     * The last path segment of *path*, or the whole of it when there is none.
     *
     * <p>Splits on both separators rather than using {@code File}: Helios
     * writes the path it was given, and a Windows path read back on any
     * platform must still shorten to its file name.
     *
     * @param path the path Helios named
     * @return the file name to put in the message
     */
    private static String fileName(String path) {
        int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = cut < 0 ? path : path.substring(cut + 1);
        return name.isEmpty() ? path : name;
    }

    /**
     * The outcome for a run that converged and still produced no diagram.
     *
     * <p>Not derivable from an exit status, because there is none to derive it
     * from: Helios' {@code 1} command catches its own exceptions and does not
     * set its error flag, so an unreadable or malformed template leaves the run
     * successful and silently draws nothing. Without this the window would show
     * an unannotated template under no banner at all and look like a solved
     * case with no numbers on it.
     *
     * @param templateName the template's file name, for the message
     */
    public static HeliosOutcome renderFailed(String templateName) {
        return new HeliosOutcome(Severity.WARNING,
                "The power flow converged, but the diagram could not be drawn.",
                "Helios could not render " + templateName + ", so this is the"
                + " template as it was, without the solved values. Check that it"
                + " is a readable SVG file.");
    }
}
