package my.ramses.ssa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <base>_ms.dat, indexed by mode. Kept in file order, which is state
 * order, because the phase reference is the largest-magnitude omega entry
 * and reordering would obscure which machine that was.
 */
public final class SsaModeShapes {

    private final Map<Integer, List<ModeShapeEntry>> byMode;

    private SsaModeShapes(Map<Integer, List<ModeShapeEntry>> byMode) {
        this.byMode = byMode;
    }

    public List<ModeShapeEntry> forMode(int mode) {
        List<ModeShapeEntry> rows = byMode.get(Integer.valueOf(mode));
        return rows == null ? Collections.<ModeShapeEntry>emptyList() : rows;
    }

    public static SsaModeShapes parse(String text) throws IOException {
        Map<Integer, List<ModeShapeEntry>> byMode =
                new LinkedHashMap<Integer, List<ModeShapeEntry>>();
        String[] lines = text.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int lineNo = i + 1;
            ModeShapeEntry row = new ModeShapeEntry(
                    Columns.integer(line, 0, 8, lineNo),
                    Columns.integer(line, 9, 17, lineNo),
                    Columns.num(line, 18, 42, lineNo),
                    Columns.num(line, 43, 67, lineNo),
                    Columns.slice(line, 68, 88));
            Integer key = Integer.valueOf(row.mode);
            List<ModeShapeEntry> rows = byMode.get(key);
            if (rows == null) {
                rows = new ArrayList<ModeShapeEntry>();
                byMode.put(key, rows);
            }
            rows.add(row);
        }
        return new SsaModeShapes(byMode);
    }
}
