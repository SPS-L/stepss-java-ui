package my.ramses.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** &lt;base&gt;_modes.dat: the run's header metadata and one Mode per line. */
public final class SsaModes {

    private final List<Mode> modes;
    private final int nstates;
    private final int nalg;
    private final Double time;
    private final Double realLimit;
    private final Double pfThreshold;
    private final Double gapTol;

    private SsaModes(List<Mode> modes, int nstates, int nalg, Double time,
            Double realLimit, Double pfThreshold, Double gapTol) {
        this.modes = Collections.unmodifiableList(modes);
        this.nstates = nstates;
        this.nalg = nalg;
        this.time = time;
        this.realLimit = realLimit;
        this.pfThreshold = pfThreshold;
        this.gapTol = gapTol;
    }

    public List<Mode> modes() {
        return modes;
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

    public Double realLimit() {
        return realLimit;
    }

    public Double pfThreshold() {
        return pfThreshold;
    }

    public Double gapTol() {
        return gapTol;
    }

    public static SsaModes parse(String text) throws IOException {
        List<Mode> found = new ArrayList<Mode>();
        int nstates = 0;
        int nalg = 0;
        Double time = null;
        Double realLimit = null;
        Double pfThreshold = null;
        Double gapTol = null;
        String[] lines = text.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.charAt(0) == '#') {
                // Read by key, not by position, so a future engine adding a
                // field does not break this; an absent key stays null rather
                // than failing the load.
                if (line.contains(" nstates ")) {
                    nstates = (int) keyed(line, "nstates", 0.0);
                    nalg = (int) keyed(line, "nalg", 0.0);
                    time = keyedOrNull(line, "time");
                    realLimit = keyedOrNull(line, "real_limit");
                    pfThreshold = keyedOrNull(line, "pf_threshold");
                    gapTol = keyedOrNull(line, "gap_tol");
                }
                continue;
            }
            found.add(new Mode(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.num(line, 9, 33, lineNo),
                    Columns.num(line, 34, 58, lineNo),
                    Columns.num(line, 59, 83, lineNo),
                    Columns.num(line, 84, 108, lineNo),
                    Columns.integer(line, 109, 111, lineNo) == 1,
                    Columns.integer(line, 112, 114, lineNo) == 1));
        }
        if (found.isEmpty()) {
            throw new IOException("no mode rows found; is this a _modes.dat file?");
        }
        return new SsaModes(found, nstates, nalg, time, realLimit, pfThreshold, gapTol);
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
