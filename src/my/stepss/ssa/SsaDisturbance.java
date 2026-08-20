package my.stepss.ssa;

import java.util.Locale;

/**
 * Builds the disturbance file that triggers a small-signal run.
 *
 * <p>This lives here, rather than inline in the UI handler that writes it, so
 * that it can be checked: the harness cannot load {@code StepssUI}, which
 * drags in the whole toolchain, but it can call this.
 *
 * <p>The generated file deliberately carries no events other than the
 * analysis itself. The engine linearises about whatever state the system is
 * in when {@code EIG} fires, so a file with a disturbance in it would
 * linearise about a point mid-swing and describe that instant rather than the
 * steady state. Running to a later time with no events simply lets the
 * initialisation settle and then linearises about the same operating point.
 */
public final class SsaDisturbance {

    /**
     * Earliest analysis time offered, and the default. The engine needs at
     * least one step before it can apply an event, and the bundled Kundur
     * example uses this same value.
     */
    public static final double MIN_TIME = 0.001;

    /**
     * Gap between the analysis and the STOP record. The analysis is complete
     * when EIG returns, so this only has to be positive; it matches the gap
     * the bundled example leaves.
     */
    private static final double STOP_MARGIN = 0.010;

    /**
     * What the JAC record writes, given the shared basename. These sit
     * alongside the {@code _modes}, {@code _pf} and {@code _ms} files the
     * analysis writes, so one basename covers a whole run with no collision.
     */
    public static final String[] JACOBIAN_SUFFIXES = {
        "_eqs.dat", "_var.dat", "_val.dat", "_struc.dat",
    };

    private SsaDisturbance() {
    }

    /**
     * True when {@code basename} is safe as both a file name stem and a
     * quoted Fortran string. An apostrophe would terminate the EIG record's
     * quoted argument early and the engine would write nothing, and a path
     * separator would put the results where the loader does not look.
     */
    public static boolean validBasename(String basename) {
        if (basename == null || basename.isEmpty()) {
            return false;
        }
        for (int i = 0; i < basename.length(); i++) {
            char c = basename.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses an analysis time from user input.
     *
     * @param text the field contents
     * @return the time in seconds
     * @throws IllegalArgumentException if it is not a number, or is earlier
     *     than {@link #MIN_TIME}
     */
    public static double parseTime(String text) {
        String trimmed = text == null ? "" : text.trim();
        double t;
        try {
            t = Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Analysis time must be a number, not \"" + trimmed + "\".");
        }
        if (Double.isNaN(t) || Double.isInfinite(t)) {
            throw new IllegalArgumentException(
                    "Analysis time must be a finite number.");
        }
        if (t < MIN_TIME) {
            throw new IllegalArgumentException(
                    "Analysis time must be at least " + MIN_TIME + " s.");
        }
        return t;
    }

    /**
     * The disturbance file contents.
     *
     * @param basename names the three results files, and must satisfy
     *     {@link #validBasename}
     * @param t when to linearise, in seconds, at least {@link #MIN_TIME}
     * @return the file text, newline terminated
     * @throws IllegalArgumentException if either argument is rejected
     */
    public static String text(String basename, double t) {
        if (!validBasename(basename)) {
            throw new IllegalArgumentException("Invalid results basename: " + basename);
        }
        if (Double.isNaN(t) || Double.isInfinite(t) || t < MIN_TIME) {
            throw new IllegalArgumentException("Invalid analysis time: " + t);
        }
        // JAC and EIG both fire at t, and the engine acts on them in that
        // order in the same step (simul_decomp: dump_jacobian then dump_eig).
        // So the dumped Jacobian is necessarily the one the analysis reduced,
        // at the same instant, rather than a separate run that could have
        // drifted or been taken from a different case.
        return "0.000 CONTINUE SOLVER TR 0.010 0.001 0. ALL\n"
                + time(t) + " JAC '" + basename + "'\n"
                + time(t) + " EIG '" + basename + "'\n"
                + time(t + STOP_MARGIN) + " STOP\n";
    }

    /**
     * The disturbance file contents, with explicit analysis parameters.
     *
     * <p>Only for engines at or past
     * {@link EngineVersion#EIG_PARAMETERS_SINCE}. An older engine reads the
     * {@code EIG} record list-directed, takes the first two items and ignores
     * the rest without complaint, so calling this against one produces a full
     * results set computed with the engine's own defaults while the window
     * reports these values. {@link EngineVersion#supportsEigParameters} is
     * what decides.
     *
     * <p>The pair is written together or not at all, which is the grammar the
     * engine accepts: it refuses a record carrying one of the two rather than
     * half applying it.
     *
     * @param basename names the three results files, and must satisfy
     *     {@link #validBasename}
     * @param t when to linearise, in seconds, at least {@link #MIN_TIME}
     * @param realLimit dominance threshold on Re(lambda), in 1/s
     * @param pfThreshold participation factor floor, dimensionless
     * @return the file text, newline terminated
     * @throws IllegalArgumentException if any argument is rejected
     */
    public static String text(String basename, double t,
            double realLimit, double pfThreshold) {
        if (!isFinite(realLimit)) {
            throw new IllegalArgumentException(
                    "Real part limit must be a finite number.");
        }
        if (!isFinite(pfThreshold)) {
            throw new IllegalArgumentException(
                    "Participation factor (PF) threshold must be a"
                            + " finite number.");
        }
        String base = text(basename, t);
        // Rebuild only the EIG line, so the JAC record, the solver record and
        // the STOP margin stay in one place rather than being duplicated here
        // and drifting from the two-argument form.
        String eig = time(t) + " EIG '" + basename + "'\n";
        String withParams = time(t) + " EIG '" + basename + "' "
                + number(realLimit) + " " + number(pfThreshold) + "\n";
        return base.replace(eig, withParams);
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /**
     * Parses one of the two analysis parameters from user input.
     *
     * <p>Neither is range checked beyond being finite. The engine does not
     * validate them either, and a threshold this side rejects but the engine
     * would have accepted is a limit invented by the UI.
     *
     * @param text the field contents
     * @param label how to name the field if it has to be rejected
     * @return the parsed value
     * @throws IllegalArgumentException if it is not a finite number
     */
    public static double parseParameter(String text, String label) {
        String trimmed = text == null ? "" : text.trim();
        double v;
        try {
            v = Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    label + " must be a number, not \"" + trimmed + "\".");
        }
        if (!isFinite(v)) {
            throw new IllegalArgumentException(label + " must be a finite number.");
        }
        return v;
    }

    /**
     * Fixed point rather than {@code Double.toString}, which emits scientific
     * notation for small values: the engine reads these records list-directed
     * and 1.0E-3 is accepted, but a plain decimal is what every other
     * disturbance file in this project uses and is what a user comparing the
     * generated file against theirs will expect.
     */
    private static String time(double t) {
        return String.format(Locale.ROOT, "%.6f", t);
    }

    /**
     * Formats an analysis parameter for the record.
     *
     * <p>{@code Locale.ROOT} for the same reason {@link #time} uses it, and
     * more sharply: under a comma-decimal locale {@code String.valueOf} would
     * emit "-0,5", which the engine reads list-directed as two separate items
     * and so becomes an overlong record it refuses. Plain decimal rather than
     * {@code Double.toString}, which switches to scientific notation for small
     * magnitudes; the engine accepts both, but every disturbance file in this
     * project is written in plain decimal.
     */
    private static String number(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }
}
