package my.stepss.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * &lt;base&gt;_modes.dat: the run's header metadata and one Mode per line.
 *
 * <p>One format version is read, v2, and the banner on the first line is what
 * says so. v1 is refused rather than read: it carried a dominance column
 * between freq_hz and smp, so v2's smp sits exactly where v1's dom sat, at the
 * same width with the same two legal values. A reader that guessed would
 * report the simplicity flag as the dominance flag and then read simplicity
 * off the end of the line, producing numbers rather than an error, and numbers
 * that parsed are the hardest kind of wrong to notice. Hence the banner check,
 * and hence any version this build does not know being refused.
 *
 * <p>Reading v1 was dropped deliberately, not left unimplemented. RAMSES has
 * written v2 since 3.79, the engine bundled here is well past that, and an
 * older engine is refused up front on the Analysis tab rather than at the
 * point its results fail to open.
 */
public final class SsaModes {

    /** Banner line, e.g. "# STEPSS SSA modes v2". */
    private static final java.util.regex.Pattern BANNER =
            java.util.regex.Pattern.compile("^#\\s*STEPSS SSA modes v(\\d+)\\s*$");

    /** The one version this reader understands. */
    static final int FORMAT_VERSION = 2;

    private final List<Mode> modes;
    private final int formatVersion;
    private final int nstates;
    private final int nalg;
    private final Double time;
    private final Double pfFloor;
    private final Double gapTol;

    private SsaModes(List<Mode> modes, int formatVersion, int nstates, int nalg,
            Double time, Double pfFloor, Double gapTol) {
        this.modes = Collections.unmodifiableList(modes);
        this.formatVersion = formatVersion;
        this.nstates = nstates;
        this.nalg = nalg;
        this.time = time;
        this.pfFloor = pfFloor;
        this.gapTol = gapTol;
    }

    public List<Mode> modes() {
        return modes;
    }

    /**
     * Which version the banner declared, always {@link #FORMAT_VERSION}.
     *
     * <p>Kept so the results window can show what it read rather than assert
     * it. Any other version threw before this object existed.
     */
    public int formatVersion() {
        return formatVersion;
    }

    public int nstates() {
        return nstates;
    }

    public int nalg() {
        return nalg;
    }

    /** Null when the key was absent from the header. */
    public Double time() {
        return time;
    }

    /**
     * The floor below which the engine wrote no participation entry, from the
     * header's {@code pf_floor} key, or null when the key was absent.
     *
     * <p>Not a rename of the {@code pf_threshold} a v1 file carried. That was
     * an analysis parameter on the EIG record; this is the $PF_THRES size
     * guard on the one output quadratic in the state count. It bounds what is
     * in the file and says nothing about which entries are worth reading,
     * which is why an absent device is below it rather than zero.
     */
    public Double pfFloor() {
        return pfFloor;
    }

    public Double gapTol() {
        return gapTol;
    }

    public static SsaModes parse(String text) throws IOException {
        List<Mode> found = new ArrayList<Mode>();
        int version = 0;
        int nstates = 0;
        int nalg = 0;
        Double time = null;
        Double pfFloor = null;
        Double gapTol = null;
        String[] lines = text.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.charAt(0) == '#') {
                java.util.regex.Matcher banner = BANNER.matcher(line);
                if (banner.matches()) {
                    version = Integer.parseInt(banner.group(1));
                }
                // Read by key, not by position, so a future engine adding a
                // field does not break this; an absent key stays null rather
                // than failing the load.
                if (line.contains(" nstates ")) {
                    nstates = (int) keyed(line, "nstates", 0.0);
                    nalg = (int) keyed(line, "nalg", 0.0);
                    time = keyedOrNull(line, "time");
                    pfFloor = keyedOrNull(line, "pf_floor");
                    gapTol = keyedOrNull(line, "gap_tol");
                }
                continue;
            }
            // Refused before the first row rather than after the last: a v3
            // read as though it were a v2 would produce numbers, and numbers
            // that parsed are the hardest kind of wrong to notice.
            if (version != FORMAT_VERSION) {
                throw new IOException(version == 0
                        ? "no \"# STEPSS SSA modes vN\" banner; is this a _modes.dat file?"
                        : "unsupported _modes.dat format version " + version
                                + "; this build reads v" + FORMAT_VERSION + " only"
                                + (version < FORMAT_VERSION
                                        ? ". It was written by a RAMSES older than 3.79."
                                        : ""));
            }
            // smp sits at 109..111. v1 put a dominance flag there and pushed
            // smp to 112..114, which is why the banner is checked before a
            // single row is read.
            found.add(new Mode(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.num(line, 9, 33, lineNo),
                    Columns.num(line, 34, 58, lineNo),
                    Columns.num(line, 59, 83, lineNo),
                    Columns.num(line, 84, 108, lineNo),
                    Columns.integer(line, 109, 111, lineNo) == 1));
        }
        if (found.isEmpty()) {
            throw new IOException("no mode rows found; is this a _modes.dat file?");
        }
        return new SsaModes(found, version, nstates, nalg, time, pfFloor, gapTol);
    }

    private static double keyed(String header, String key, double fallback) {
        Double value = keyedOrNull(header, key);
        return value == null ? fallback : value;
    }

    private static Double keyedOrNull(String header, String key) {
        int at = header.indexOf(' ' + key + ' ');
        if (at < 0) {
            return null;
        }
        String rest = header.substring(at + key.length() + 2).trim();
        int end = rest.indexOf(' ');
        String token = end < 0 ? rest : rest.substring(0, end);
        try {
            return Double.valueOf(token);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
