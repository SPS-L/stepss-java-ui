package my.stepss.ssa;

/**
 * Builds the settings file a small-signal run is given after the case's own
 * data files.
 *
 * <p>The engine refuses the analysis under {@code $SCHEME IN} or
 * {@code $OMEGA_REF COI}, and both are settings the case chose for its
 * time-domain runs: {@code $OMEGA_REF COI} is the engine's own default, so a
 * case that never mentions the record lands on the refused value. Rather than
 * making the user edit their solver settings and remember to put them back,
 * the run is handed one extra data file carrying the two records, read last.
 *
 * <p>Read last is what makes it an override rather than a conflict.
 * {@code get_settings.f90} restores every default and then walks the records
 * in the order {@code loadrec.f90} read them, assigning as it goes, so the
 * last record of a kind is the one the engine ends up with. The files are read
 * in the order the command file lists them, and {@link
 * my.stepss.StepssUI#createCommandFile} writes this one after the ten data
 * rows.
 *
 * <p>Lives here, next to {@link SsaDisturbance}, for the same reason: the
 * harness cannot load {@code StepssUI}, which drags in the whole toolchain,
 * but it can call this.
 */
public final class SsaSettings {

    /**
     * What is appended to the run's basename to name the file. It mirrors the
     * {@code Eig.dst} the same run generates, and cannot collide with a
     * results file ({@code _modes}, {@code _pf}, {@code _ms}) or with one of
     * {@link SsaDisturbance#JACOBIAN_SUFFIXES}, all of which begin with an
     * underscore.
     */
    public static final String FILE_SUFFIX = "Eig.dat";

    private SsaSettings() {
    }

    /**
     * What the generated settings file is called for a given run.
     *
     * @param basename the run's basename, which must satisfy
     *     {@link SsaDisturbance#validBasename}
     * @return the file name, with no directory part
     * @throws IllegalArgumentException if the basename is rejected
     */
    public static String fileName(String basename) {
        if (!SsaDisturbance.validBasename(basename)) {
            throw new IllegalArgumentException("Invalid results basename: " + basename);
        }
        return basename + FILE_SUFFIX;
    }

    /**
     * The settings file contents.
     *
     * <p>Two records and nothing else. Every record here overrides whatever
     * the case set, so anything beyond what the analysis actually requires
     * would silently change a user's run for no reason. {@code
     * $EIG_MAX_STATES} in particular is deliberately absent: it is a memory
     * guard rather than a correctness one, and the eigensolve holds
     * 9*Nx^2 doubles at its peak, so raising it on the user's behalf would
     * trade a clear refusal for an out-of-memory kill.
     *
     * @return the file text, newline terminated
     */
    public static String text() {
        return "# Written by STEPSS for the small-signal run, and read after the\n"
                + "# case's own data files. The engine keeps the last record of each\n"
                + "# kind it reads, so these two are the ones the analysis runs under\n"
                + "# whatever the case set. Nothing else here is changed.\n"
                + "\n"
                + "$SCHEME DE                         ;\n"
                + "$OMEGA_REF SYN                     ;\n";
    }
}
