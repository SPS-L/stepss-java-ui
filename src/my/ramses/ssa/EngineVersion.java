package my.ramses.ssa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the engine actually in use can be asked to do, decided from the version
 * it prints in its own banner.
 *
 * <p>Read from the banner rather than from {@code versions.properties},
 * because the pinned version describes the bundled payload and the running
 * engine need not be it: a build adopted from the Codegen tab replaces it for
 * the rest of the session, and the pin says nothing about that build.
 *
 * <p>This matters for exactly one reason. {@code disturb.f90} reads its
 * records list-directed, and a list-directed read of two items silently
 * ignores anything after them. So an engine older than
 * {@link #EIG_PARAMETERS_SINCE} accepts an {@code EIG} record carrying
 * {@code real_limit} and {@code pf_threshold} without any error at all, and
 * quietly analyses with its own defaults. Nothing on the engine side can
 * report that back. This check is the only thing standing between that and a
 * window presenting thresholds the run never used.
 *
 * <p>Deliberately pure: it parses text and compares numbers, and never runs
 * anything. Obtaining the banner needs a process and so lives in the UI, which
 * already launches the engine. That keeps this class loadable by
 * {@code SsaHarness} with only {@code build/classes} on the classpath, which
 * {@code tools/ssa-harness.sh} relies on.
 */
public final class EngineVersion {

    /**
     * First RAMSES whose {@code EIG} disturbance accepts {@code real_limit}
     * and {@code pf_threshold}. Before this, both are hardcoded in
     * {@code disturb.f90} for any record-triggered analysis.
     */
    public static final double EIG_PARAMETERS_SINCE = 3.74;

    /**
     * The banner prints the version with Fortran {@code f5.2} from a single
     * precision constant, so the text carries two decimals and the parsed
     * value can sit an ulp either side of the literal. Comparing with this
     * slack keeps 3.74 from failing a {@code >= 3.74} test.
     */
    private static final double EPS = 1e-4;

    /**
     * Matches the banner's own version line. The label carries a colon, which
     * is what keeps this off the "(Full Version)" suffix on the same line.
     */
    private static final Pattern VERSION_LINE =
            Pattern.compile("Version:\\s*([0-9]+\\.[0-9]+)");

    private EngineVersion() {
    }

    /**
     * Pulls the version out of banner text.
     *
     * @param banner whatever the engine printed, may be null
     * @return the version, or {@code Double.NaN} if the text carries none
     */
    public static double parseBanner(String banner) {
        if (banner == null) {
            return Double.NaN;
        }
        Matcher m = VERSION_LINE.matcher(banner);
        if (!m.find()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /**
     * Whether a version accepts the two {@code EIG} parameters.
     *
     * <p>An unreadable version is treated as unsupported. That is the safe
     * direction: the cost of being wrong the other way is a run whose
     * displayed thresholds are not the ones it used.
     */
    public static boolean supportsEigParameters(double version) {
        return !Double.isNaN(version) && version >= EIG_PARAMETERS_SINCE - EPS;
    }
}
