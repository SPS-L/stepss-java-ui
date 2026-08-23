package my.stepss.ssa;

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
 * <p>What it decides is whether the engine's small-signal results can be read
 * at all. From {@link #READABLE_SSA_SINCE} the analysis writes v2 files:
 * participation factors and a mode shape for every mode, with the filtering
 * left to whoever reads them. Before it the engine wrote v1, filtering by a
 * real_limit fixed on the EIG record. v1 is refused rather than read, so this
 * gates the run with a warning instead of letting it finish and fail to open.
 *
 * <p>The record itself needs no check. It is a basename and a time, which is
 * the whole grammar now and was the two-argument form every older engine
 * accepted, so what this class gates is the reading and never the writing.
 *
 * <p>Deliberately pure: it parses text and compares numbers, and never runs
 * anything. Obtaining the banner needs a process and so lives in the UI, which
 * already launches the engine. That keeps this class loadable by
 * {@code SsaHarness} with only {@code build/classes} on the classpath, which
 * {@code tools/ssa-harness.sh} relies on.
 */
public final class EngineVersion {

    /**
     * First RAMSES whose small-signal results this build can read, i.e. the
     * first that writes v2 files. From here {@code EIG} takes a basename
     * alone, the participation floor is the {@code $PF_THRES} solver setting,
     * and the real part limit is applied when the results are read.
     *
     * <p>Older engines still run every other kind of study. Only their
     * small-signal output is unreadable, because {@code SsaModes} reads v2 and
     * refuses v1 rather than guessing at a column that moved.
     */
    public static final double READABLE_SSA_SINCE = 3.79;

    /**
     * The banner prints the version with Fortran {@code f5.2} from a single
     * precision constant, so the text carries two decimals and the parsed
     * value can sit an ulp either side of the literal. Comparing with this
     * slack keeps 3.79 from failing a {@code >= 3.79} test.
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
     * Whether a version writes small-signal results this build can read.
     *
     * <p>An unreadable version is treated as not doing so. That is the safe
     * direction: it warns before a run that a current engine does not need,
     * where the other way round lets an old engine run the analysis and fail
     * only when its results will not open.
     */
    public static boolean writesReadableSsa(double version) {
        return !Double.isNaN(version) && version >= READABLE_SSA_SINCE - EPS;
    }
}
