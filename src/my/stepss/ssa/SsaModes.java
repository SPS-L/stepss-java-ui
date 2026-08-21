package my.stepss.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * &lt;base&gt;_modes.dat: the run's header metadata and one Mode per line.
 *
 * <p>Two format versions are read, and the banner on the first line is the
 * only thing that tells them apart. v1 carried a dominance column between
 * freq_hz and smp, and recorded the real_limit and pf_threshold the EIG
 * record was given; v2 has neither, records pf_floor in their place, and its
 * smp column sits exactly where v1's dom column sat. Reading a v2 file as a
 * v1 would therefore not fail: it would report every degenerate mode as
 * non-dominant and every simple one as dominant, and then read the simplicity
 * flag off the end of the line. Hence {@link #FORMAT_VERSIONS}, and hence an
 * unrecognised version being refused rather than guessed at.
 */
public final class SsaModes {

    /** Banner line, e.g. "# STEPSS SSA modes v2". */
    private static final java.util.regex.Pattern BANNER =
            java.util.regex.Pattern.compile("^#\\s*STEPSS SSA modes v(\\d+)\\s*$");

    /** The versions this reader understands, newest first. */
    static final int[] FORMAT_VERSIONS = {2, 1};

    private final List<Mode> modes;
    private final int formatVersion;
    private final int nstates;
    private final int nalg;
    private final Double time;
    private final Double realLimit;
    private final Double pfThreshold;
    private final Double pfFloor;
    private final Double gapTol;

    private SsaModes(List<Mode> modes, int formatVersion, int nstates, int nalg,
            Double time, Double realLimit, Double pfThreshold, Double pfFloor,
            Double gapTol) {
        this.modes = Collections.unmodifiableList(modes);
        this.formatVersion = formatVersion;
        this.nstates = nstates;
        this.nalg = nalg;
        this.time = time;
        this.realLimit = realLimit;
        this.pfThreshold = pfThreshold;
        this.pfFloor = pfFloor;
        this.gapTol = gapTol;
    }

    public List<Mode> modes() {
        return modes;
    }

    /**
     * Which version of the file this was, 1 or 2. Worth asking only about
     * absences: on a v1 file the engine wrote participation factors and mode
     * shapes for dominant modes alone, so a mode can legitimately have
     * neither, and on a v2 file it wrote them for every mode, so a mode with
     * neither means the file is missing or incomplete.
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
     * The dominance threshold the run was given, and null for a v2 file,
     * which is not given one: the real part limit is applied when the results
     * are read now, not when they are written.
     */
    public Double realLimit() {
        return realLimit;
    }

    /**
     * The participation threshold a v1 run was given. Null for a v2 file; see
     * {@link #pfFloor()}, which is the value that took its place and means
     * something different.
     */
    public Double pfThreshold() {
        return pfThreshold;
    }

    /**
     * The floor below which the engine wrote no participation entry, from a
     * v2 file's {@code pf_floor} header key, or null on a v1 file.
     *
     * <p>Not a rename of {@link #pfThreshold()}. That was an analysis
     * parameter on the EIG record; this is the $PF_THRES size guard on the
     * one output quadratic in the state count. It bounds what is in the file
     * and says nothing about which entries are worth reading, which is why an
     * absent device is below it rather than zero.
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
        Double realLimit = null;
        Double pfThreshold = null;
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
                // than failing the load. real_limit and pf_threshold are
                // v1-only and pf_floor is v2-only, so each simply stays null
                // on the version that does not carry it.
                if (line.contains(" nstates ")) {
                    nstates = (int) keyed(line, "nstates", 0.0);
                    nalg = (int) keyed(line, "nalg", 0.0);
                    time = keyedOrNull(line, "time");
                    realLimit = keyedOrNull(line, "real_limit");
                    pfThreshold = keyedOrNull(line, "pf_threshold");
                    pfFloor = keyedOrNull(line, "pf_floor");
                    gapTol = keyedOrNull(line, "gap_tol");
                }
                continue;
            }
            // Refused before the first row rather than after the last: a v3
            // read as though it were a v2 would produce numbers, and numbers
            // that parsed are the hardest kind of wrong to notice.
            if (!known(version)) {
                throw new IOException(version == 0
                        ? "no \"# STEPSS SSA modes vN\" banner; is this a _modes.dat file?"
                        : "unsupported _modes.dat format version " + version
                                + "; this build reads v1 and v2");
            }
            // v1 put a dominance flag at 109..111 and pushed smp to 112..114.
            // v2 dropped it, so smp sits at 109..111 instead. Same width, same
            // two legal values, and no error on either side of the mistake:
            // this offset is the whole reason the banner is checked.
            boolean v1 = version == 1;
            found.add(new Mode(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.num(line, 9, 33, lineNo),
                    Columns.num(line, 34, 58, lineNo),
                    Columns.num(line, 59, 83, lineNo),
                    Columns.num(line, 84, 108, lineNo),
                    v1 ? Boolean.valueOf(Columns.integer(line, 109, 111, lineNo) == 1) : null,
                    Columns.integer(line, v1 ? 112 : 109, v1 ? 114 : 111, lineNo) == 1));
        }
        if (found.isEmpty()) {
            throw new IOException("no mode rows found; is this a _modes.dat file?");
        }
        return new SsaModes(found, version, nstates, nalg, time, realLimit,
                pfThreshold, pfFloor, gapTol);
    }

    private static boolean known(int version) {
        for (int v : FORMAT_VERSIONS) {
            if (v == version) {
                return true;
            }
        }
        return false;
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
