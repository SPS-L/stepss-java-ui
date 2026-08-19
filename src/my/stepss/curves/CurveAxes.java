package my.stepss.curves;

/**
 * What one {@link CurvePanel} draws around its curves.
 *
 * <p>The panel paints; this says what the frame means. It is the whole of the
 * difference between the post-analysis window, which overlays an extraction's
 * curves on one autoscaled axis with a legend, and a live window's stacked
 * panels, which each hold one observable over a fixed x range under a title.
 *
 * <p>Immutable, so a panel cannot be reconfigured behind a render.
 */
public final class CurveAxes {

    /**
     * The post-analysis frame: no title, time on x, autoscaled, legend on.
     *
     * <p>{@link CurvePanel} defaults to this, so the window built in step 2
     * keeps its exact appearance without passing anything.
     */
    public static final CurveAxes POST = new CurveAxes(
            "", "t (s)", "", Double.NaN, Double.NaN, true, false);

    /** Drawn top left, or not drawn when empty. */
    public final String title;
    /** The x axis caption, centred under the frame. */
    public final String xLabel;
    /**
     * The y axis caption.
     *
     * <p>Empty means "work it out from the data", which is what the
     * post-analysis window wants: it derives one from the curves' units and
     * says so when they disagree. A live panel knows its unit from the
     * observable's display type and passes it here.
     */
    public final String yLabel;
    /** The fixed x lower bound, or NaN to autoscale. */
    public final double xLo;
    /** The fixed x upper bound, or NaN to autoscale. */
    public final double xHi;
    /** Whether to list the series down the top left of the frame. */
    public final boolean legend;
    /** Whether to overlay the line y = x, which only RT wants. */
    public final boolean identity;

    public CurveAxes(String title, String xLabel, String yLabel,
            double xLo, double xHi, boolean legend, boolean identity) {
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.xLo = xLo;
        this.xHi = xHi;
        this.legend = legend;
        this.identity = identity;
    }

    /** Whether x comes from this spec rather than from the data. */
    public boolean xFixed() {
        return !Double.isNaN(xLo) && !Double.isNaN(xHi) && xHi > xLo;
    }
}
