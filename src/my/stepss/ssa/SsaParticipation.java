package my.stepss.ssa;

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
 * <p>Absences here are meaningful and are preserved rather than smoothed
 * over. ssa.f90 writes rows for every mode and every entry above the run's
 * participation floor ($PF_THRES, recorded as pf_floor), so a missing device
 * means "below the floor this run wrote at", never "zero". Normalisation puts
 * one entry at exactly 1 in each mode, so no mode can be emptied by the floor:
 * a mode missing from a v2 file means the file is incomplete.
 *
 * <p>On a v1 file a mode can legitimately be missing entirely, because that
 * engine wrote rows only for modes above the real_limit it was given. {@link
 * SsaModes#formatVersion} is what tells the two apart, and the results window
 * says which of the two it is looking at rather than reporting one absence as
 * the other.
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
