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
     * because a field would not parse, or because a field parsed to a
     * non-finite value. Surfaced once by the window rather than per row: a
     * malformed file produces one complaint, not thousands.
     */
    public final int skippedRows;

    public CurveData(List<CurveSeries> series, File source, int skippedRows) {
        this.series = Collections.unmodifiableList(series);
        this.source = source;
        this.skippedRows = skippedRows;
    }

    /**
     * How many distinct units the curves carry, counting "no unit" as a unit
     * of its own.
     *
     * <p>Skipping the empty unit, as this once did, hid the case that needs
     * the mixed-units note most. A pu bus voltage overlaid with a unitless
     * "DCTL relay1: state" counted one distinct unit while
     * {@link #commonUnit} returned "", and CurvePanel.render drew neither the
     * y-axis unit label nor the note. A 0/1 relay state against a 1.0 pu
     * voltage is exactly the flat-curve confusion the note exists to explain.
     *
     * <p>An extraction where no curve has a unit still counts one, so it is
     * not mixed and gets no note, which is right: those curves do share a
     * scale, they just have no name for it.
     */
    public int distinctUnits() {
        java.util.Set<String> units = new java.util.HashSet<String>();
        for (CurveSeries one : series) {
            units.add(one.unit);
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
