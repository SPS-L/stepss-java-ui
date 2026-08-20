package my.stepss.plot;

/**
 * The drawing primitives both plots need, in device coordinates.
 *
 * <p>The plots draw against this rather than against Graphics2D so that the
 * exported SVG is produced by the same code that painted the screen and
 * cannot drift from it. Styling is carried as a class name rather than as a
 * colour, which is what makes the exported file restylable by editing one
 * rule instead of every element.
 */
public interface PlotSink {

    void group(String id);

    void endGroup();

    void line(double x1, double y1, double x2, double y2, String cls);

    void dashedLine(double x1, double y1, double x2, double y2, String cls);

    void circle(double cx, double cy, double r, String cls);

    /**
     * A circle filled in its class's own colour rather than outlined in it.
     *
     * <p>One marker for two facts: the fill says "this is the one selected"
     * without adding a second glyph over a plot that is already dense with
     * them, and the class it is filled in still says whatever the outline
     * would have said.
     */
    void filledCircle(double cx, double cy, double r, String cls);

    /**
     * One connected run of points as a single element, which is what keeps an
     * exported time series a few kilobytes rather than one element per sample.
     *
     * @param n how many leading entries of xs and ys to use, so a caller may
     *     pass growable arrays that have spare capacity
     */
    void polyline(double[] xs, double[] ys, int n, String cls);

    void arrow(double x1, double y1, double x2, double y2, String cls);

    /** anchor is "start", "middle" or "end". */
    void text(double x, double y, String s, String anchor, String cls);

    /**
     * Confines everything drawn until {@link #endClip()} to this rectangle.
     *
     * <p>One level only, which is all any caller needs: a zoomed curve extends
     * past the frame and would otherwise paint over the axes, the tick labels
     * and the legend. Nesting is not supported and not needed.
     */
    void clipRect(double x, double y, double w, double h);

    void endClip();
}
