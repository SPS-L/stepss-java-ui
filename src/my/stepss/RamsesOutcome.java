package my.stepss;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a finished time-domain RAMSES run means, decided once.
 *
 * <p>The Dynamic Simulation tab used to read nothing from the engine's exit
 * status: the watcher thread waited for the process and then always said
 * "Simulation finished". A run that a protection device stopped, or that
 * diverged, looked the same as one that reached its STOP time, and the only
 * trace of the difference was a line somewhere in the console.
 *
 * <p>RAMSES exits 0 when the run reached STOP (or was ended with the Stop
 * button, which asks the engine to finish cleanly) and -1 when it stopped
 * itself: a model calling {@code write_msg_and_stop}, such as the
 * {@code sim_minmaxvolt} voltage guard that ends the Nordic example by design,
 * a numerical failure, and, in engines released after 3.84, a divergence. The OS shows -1 as
 * 255 on Linux and macOS and as 127 through MSYS2, so any non-zero value is
 * treated alike. Engines up to 3.84 exit 0 on a divergence, which nothing on
 * this side can tell from a finished run.
 *
 * <p>Carries no Swing, so {@link RamsesOutcomeCheck} exercises it without a
 * display, as {@code DiagramCheck} does for {@link HeliosOutcome}.
 */
public final class RamsesOutcome {

    /** How loudly to say it. */
    public enum Severity {
        /** The run reached its STOP time. */
        OK,
        /** The user stopped it; nothing to warn about. */
        STOPPED,
        /** The engine stopped it before its STOP time. */
        ERROR
    }

    /**
     * The line the engine writes before the message of a
     * {@code write_msg_and_stop} call, for example
     * {@code STOP CALL FROM DCTL sim_minmaxvolt Voltages: }. The message
     * itself is on the next line.
     */
    private static final Pattern STOP_CALL = Pattern.compile("^STOP CALL FROM (.*?):?\\s*$");

    /** Engine lines that carry a reason on their own, without a STOP CALL. */
    private static final Pattern OWN_REASON = Pattern.compile(
            "Divergence\\. Simulation stopped"
            + "|Numerical exception was raised with smallest possible time-step");

    private final Severity severity;
    private final String headline;
    private final String detail;

    private RamsesOutcome(Severity severity, String headline, String detail) {
        this.severity = severity;
        this.headline = headline;
        this.detail = detail;
    }

    /** How loudly to say it. */
    public Severity severity() {
        return severity;
    }

    /** The status-bar sentence. */
    public String headline() {
        return headline;
    }

    /** The banner text, or "" when there is nothing to add. */
    public String detail() {
        return detail;
    }

    /**
     * The outcome of a completed run.
     *
     * @param exitValue     the process exit value
     * @param stoppedByUser whether the Stop button was pressed during the run;
     *                      pressing it twice kills the process, which then
     *                      exits non-zero for a reason that is not the engine's
     * @param output        what the engine wrote to the console during this
     *                      run, searched for its reason; may be null
     * @return never null
     */
    public static RamsesOutcome of(int exitValue, boolean stoppedByUser, String output) {
        if (stoppedByUser) {
            return new RamsesOutcome(Severity.STOPPED, "Simulation stopped", "");
        }
        if (exitValue == 0) {
            return new RamsesOutcome(Severity.OK, "Simulation finished", "");
        }
        String reason = reason(output);
        return new RamsesOutcome(Severity.ERROR,
                "Simulation stopped before its STOP time",
                "RAMSES stopped the simulation before the STOP time of the"
                + " disturbance file (exit code " + exitValue + ").\n\n"
                + (reason.isEmpty()
                        ? "The engine's reason is near the end of the output below."
                        : "It reported: " + reason + "\n\nThe trajectory ends there.")
                + "\n\nA protection or limit model can end a run on purpose, as the"
                + " voltage guard does in the Nordic example; a divergence or a"
                + " numerical failure means the results after that point do not"
                + " exist.");
    }

    /**
     * The engine's last stated reason for stopping, or "" when the output
     * carries none.
     *
     * <p>The last one, because a single event often trips several devices in
     * the same step and the engine reports each: the Nordic collapse writes a
     * STOP CALL per bus out of range. Any of them is a true reason; the last is
     * the one nearest the end of the run.
     */
    static String reason(String output) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        String[] lines = output.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (OWN_REASON.matcher(line).find()) {
                return collapse(line);
            }
            Matcher stop = STOP_CALL.matcher(line);
            if (stop.matches()) {
                String message = i + 1 < lines.length ? collapse(lines[i + 1].trim()) : "";
                return message.isEmpty() ? collapse(stop.group(1))
                        : collapse(stop.group(1)) + ": " + message;
            }
        }
        return "";
    }

    /** The engine pads its columns with runs of spaces; one is enough here. */
    private static String collapse(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
