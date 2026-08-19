package my.stepss.curves;

/**
 * One extracted curve: its DYNGRAPH label, the unit read out of that label,
 * and the samples.
 *
 * <p>The arrays are exactly as long as the number of rows that parsed, so a
 * consumer can draw {@code v.length} points without carrying a count.
 */
public final class CurveSeries {

    /** The desc_obs-format label, as {@code Selection.label()} composes it. */
    public final String label;
    /** The unit from the label's last parenthesised group, or "" if it has none. */
    public final String unit;
    public final double[] t;
    public final double[] v;

    public CurveSeries(String label, String unit, double[] t, double[] v) {
        if (t.length != v.length) {
            throw new IllegalArgumentException(
                    "t and v differ in length: " + t.length + " vs " + v.length);
        }
        this.label = label;
        this.unit = unit;
        this.t = t;
        this.v = v;
    }

    /**
     * The unit out of a DYNGRAPH label.
     *
     * <p>The labels come from stepss-dyngraph/src/obstypes.f90 through
     * {@code ObservableIndex}, and carry their unit parenthesised: "voltage
     * magnitude (pu)", "P (MW) entering at FROM end". Reading it back out of
     * the label is the only source available, because the picker does not
     * carry units separately.
     *
     * <p>The FIRST parenthesised group, not the last: "P (MW) entering at FROM
     * end" puts the unit in the middle, and no label in obstypes.f90 has a
     * second group. A label with no group, or an empty one, has no unit.
     */
    public static String unitOf(String label) {
        int open = label.indexOf('(');
        if (open < 0) {
            return "";
        }
        int close = label.indexOf(')', open + 1);
        if (close < 0) {
            return "";
        }
        return label.substring(open + 1, close).trim();
    }
}
