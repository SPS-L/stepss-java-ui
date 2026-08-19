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
     * One connected run of points as a single element, which is what keeps an
     * exported time series a few kilobytes rather than one element per sample.
     *
     * @param n how many leading entries of xs and ys to use, so a caller may
     *     pass growable arrays that have spare capacity
     */
    void polyline(double[] xs, double[] ys, int n, String cls);

    void cross(double cx, double cy, double r, String cls);

    void arrow(double x1, double y1, double x2, double y2, String cls);

    /** anchor is "start", "middle" or "end". */
    void text(double x, double y, String s, String anchor, String cls);
}
