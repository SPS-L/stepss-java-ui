package my.stepss.curves;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * One extraction, parsed. Held in memory by the window that shows it, so a
 * later extraction overwriting the files on disk leaves this one intact,
 * which is the same reason SsaResultsWindow keeps its own parsed copy.
 */
public final class CurveData {

    public final List<CurveSeries> series;
    /** The .cur this was read from, or null when parsed from literals. */
    public final File source;
    /**
     * Rows dropped because their field count disagreed with the label count,
     * or because a field would not parse. Surfaced once by the window rather
     * than per row: a malformed file produces one complaint, not thousands.
     */
    public final int skippedRows;

    public CurveData(List<CurveSeries> series, File source, int skippedRows) {
        this.series = Collections.unmodifiableList(series);
        this.source = source;
        this.skippedRows = skippedRows;
    }

    /** How many distinct non-empty units the curves carry. */
    public int distinctUnits() {
        java.util.Set<String> units = new java.util.HashSet<String>();
        for (CurveSeries one : series) {
            if (!one.unit.isEmpty()) {
                units.add(one.unit);
            }
        }
        return units.size();
    }

    /** The shared unit when every curve agrees on one, else "". */
    public String commonUnit() {
        if (series.isEmpty()) {
            return "";
        }
        String first = series.get(0).unit;
        for (CurveSeries one : series) {
            if (!one.unit.equals(first)) {
                return "";
            }
        }
        return first;
    }
}
