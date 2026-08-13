package my.ramses.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <base>_pf.dat, indexed by mode.
 *
 * <p>Two absences are meaningful and are preserved rather than smoothed
 * over: ssa.f90 writes rows only for modes with dom == 1, so a mode present
 * in _modes.dat can be missing here entirely; and within a mode it writes
 * only entries above pf_threshold, so a missing device means "below the
 * threshold this run used", never "zero".
 */
public final class SsaParticipation {

    private final Map<Integer, List<Participation>> byMode;

    private SsaParticipation(Map<Integer, List<Participation>> byMode) {
        this.byMode = byMode;
    }

    /**
     * Rows for one mode, largest participation first. Empty when filtered out.
     * Unmodifiable, as SsaModes.modes() is: these are parsed engine output and
     * one promise about it is easier to rely on than two.
     */
    public List<Participation> forMode(int mode) {
        List<Participation> rows = byMode.get(Integer.valueOf(mode));
        return rows == null ? Collections.<Participation>emptyList()
                : Collections.unmodifiableList(rows);
    }

    public static SsaParticipation parse(String text) throws IOException {
        Map<Integer, List<Participation>> byMode =
                new LinkedHashMap<Integer, List<Participation>>();
        String[] lines = text.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int lineNo = i + 1;
            Participation row = new Participation(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.integer(line, 9, 17, lineNo),
                    Columns.num(line, 18, 42, lineNo),
                    Columns.slice(line, 43, 51).trim(),
                    Columns.slice(line, 52, 72),
                    Columns.slice(line, 73, 93).trim());
            Integer key = Integer.valueOf(row.mode);
            List<Participation> rows = byMode.get(key);
            if (rows == null) {
                rows = new ArrayList<Participation>();
                byMode.put(key, rows);
            }
            rows.add(row);
        }
        for (List<Participation> rows : byMode.values()) {
            Collections.sort(rows, new Comparator<Participation>() {
                @Override
                public int compare(Participation a, Participation b) {
                    return Double.compare(b.pf, a.pf);
                }
            });
        }
        return new SsaParticipation(byMode);
    }
}
