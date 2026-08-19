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
    /** The unit from the label's first parenthesised group, or "" if it has none. */
    public final String unit;
    /**
     * The time samples, paired index-for-index with {@link #v}.
     *
     * <p>Exposed as a raw mutable array rather than copied or wrapped: these
     * arrays are the render hot path, and a defensive copy would double peak
     * allocation for the largest extraction a window holds, for no present
     * benefit, while an accessor would put a method call inside the paint
     * loop for every sample. Consumers must not mutate this array. Nothing
     * does today: {@link CurReader} is the only producer, and it never
     * retains a reference to an array once it hands ownership to a {@code
     * CurveSeries}. A future consumer that wants to decimate or reuse a
     * buffer must copy first.
     */
    public final double[] t;
    /** The value samples; see {@link #t} for the mutability contract. */
    public final double[] v;
    /**
     * An optional per-point auxiliary value, or null.
     *
     * <p>Only the run-time {@code LAT} display type has one: RAMSES writes a
     * second column beside the apparent power holding a 0 or a 1 for whether
     * the equipment was being solved at that step
     * (simul_decomp.f90:2312-2316), and the panel shades each segment
     * accordingly. Same length as {@link #t} when present. Same no-mutate
     * contract as {@link #t}.
     */
    public final double[] w;

    public CurveSeries(String label, String unit, double[] t, double[] v) {
        this(label, unit, t, v, null);
    }

    public CurveSeries(String label, String unit, double[] t, double[] v, double[] w) {
        if (t.length != v.length) {
            throw new IllegalArgumentException(
                    "t and v differ in length: " + t.length + " vs " + v.length);
        }
        if (w != null && w.length != t.length) {
            throw new IllegalArgumentException(
                    "w and t differ in length: " + w.length + " vs " + t.length);
        }
        this.label = label;
        this.unit = unit;
        this.t = t;
        this.v = v;
        this.w = w;
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
     *
     * <p>That first-vs-last choice is unexercised by the current label corpus:
     * no real obstypes.f90 label carries two groups, so a label with only one
     * cannot tell the two implementations apart. CurveHarness pins the choice
     * with a synthetic two-group label, so a future change of mind here is a
     * visible decision rather than a silent one.
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
