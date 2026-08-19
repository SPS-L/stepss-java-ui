package my.stepss.plot;

/**
 * Round-number axis ticks on the 1, 2, 5 times a power of ten ladder.
 *
 * <p>Here rather than on a panel because every panel needs it: the
 * post-analysis window has one set of axes and the live window has one per
 * observable, and an axis ladder is arithmetic rather than painting.
 */
public final class NiceScale {

    private NiceScale() {
    }

    /**
     * The next round number at or below {@code raw = span / targetTicks}, on
     * the 1, 2, 5 times a power of ten ladder. A zero span means a flat curve,
     * which still needs a non-zero step or the tick loop cannot advance.
     */
    public static double step(double span, int targetTicks) {
        if (span <= 0.0 || targetTicks <= 0) {
            return 1.0;
        }
        double raw = span / targetTicks;
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)));
        double normalised = raw / magnitude;
        double nice;
        if (normalised <= 1.0) {
            nice = 1.0;
        } else if (normalised <= 2.0) {
            nice = 2.0;
        } else if (normalised <= 5.0) {
            nice = 5.0;
        } else {
            nice = 10.0;
        }
        return nice * magnitude;
    }

    /**
     * {@code lo} and {@code hi} widened outward to whole multiples of
     * {@code step}, so every tick is a round number and the data sits inside
     * the frame. Returns {lo, hi}; a flat range is widened by one step so the
     * axis has an extent.
     */
    public static double[] bounds(double lo, double hi, double step) {
        double low = Math.floor(lo / step) * step;
        double high = Math.ceil(hi / step) * step;
        if (high - low < step / 2.0) {
            low -= step;
            high += step;
        }
        return new double[] {low, high};
    }
}
