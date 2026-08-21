package my.stepss.ssa;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import my.stepss.plot.PlotSink;
import my.stepss.plot.PlotStyle;
import my.stepss.plot.SvgSink;
import my.stepss.plot.SwingSink;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * The s-plane, following the python-ui notebook's cell 20: a crimson
 * stability boundary at Re = 0, dashed constant-damping rays, and one circle
 * per mode labelled with its frequency.
 *
 * <p>It parts from the notebook in two places. The axis window is fitted to
 * the modes on screen rather than fixed, so this works on systems other than
 * Kundur and so that filtering the modes actually zooms the plane; and this
 * panel is interactive: clicking a pole selects that mode and fills its
 * circle, dragging a rectangle zooms into it, and double-clicking or {@link
 * #resetZoom()} goes back to the fitted window.
 *
 * <p>The fitted window always contains Re = 0. The stability boundary is what
 * the plot is read against, so a view that has scrolled off it shows damping
 * with nothing to measure it from. A manual zoom is free to leave it, which
 * is why everything drawn is clipped to the window rather than assumed to
 * fall inside it: unclipped, the boundary and the damping rays are lines
 * whose endpoints are computed from the data and would be painted straight
 * across the axis labels.
 *
 * <p>A mode is marked by the one circle it already has, drawn crimson when it
 * is unstable. Both facts used to add a glyph instead: a crimson cross over an
 * unstable mode's circle, and a second, larger circle around the selected
 * one, so the markers doubled up in exactly the part of the plot worth reading
 * closely, near the boundary. The notebook's cell 20 lost its cross in the
 * same pass, so the two still show a mode the same way.
 */
public final class SplanePanel extends JPanel {

    /** Notified when the user clicks a pole. */
    public interface Listener {
        void poleSelected(Mode mode);
    }

    /** The window used when there is nothing to fit to: the notebook's. */
    private static final double EMPTY_MIN_RE = -3.0;
    private static final double EMPTY_MAX_RE = 0.5;
    private static final double EMPTY_MIN_IM = 0.0;
    private static final double EMPTY_MAX_IM = 9.0;
    /** Margin around the fitted extent, as a fraction of it. */
    private static final double FIT_PAD = 0.06;
    /**
     * Smallest margin in data units, which is also what keeps a window around
     * a single mode, or around a set sharing one real part, from collapsing
     * to zero width. A zero span makes every mapped coordinate the same
     * pixel, and in the exported SVG a zero-length axis.
     */
    private static final double MIN_PAD = 0.5;
    /** Below this the drag was a click, not a zoom. */
    private static final int DRAG_SLOP = 6;
    private static final int PAD_LEFT = 60;
    private static final int PAD_RIGHT = 20;
    private static final int PAD_TOP = 20;
    private static final int PAD_BOTTOM = 45;
    private static final double POLE_R = 5.0;
    /** Im ticks, one per horizontal grid line, so the two cannot disagree. */
    static final int IM_TICKS = 4;
    /** Re ticks along the bottom axis, both ends included. */
    static final int RE_TICKS = 5;
    private static final double TICK_LEN = 4.0;
    /** Room reserved at the right for the legend sample and its caption. */
    private static final double LEGEND_W = 130.0;

    private List<Mode> shown = new ArrayList<Mode>();
    private Mode selected;
    private final List<Listener> listeners = new ArrayList<Listener>();
    /**
     * The manual zoom window as {reLo, reHi, imLo, imHi}, or null for the
     * fitted one. Held in data units rather than pixels so that resizing the
     * panel, and exporting at a different size, keep showing the same region
     * of the plane rather than the same rectangle of screen.
     */
    private double[] zoom;
    private java.awt.Point dragFrom;
    private java.awt.Point dragTo;
    private Runnable zoomListener;

    public SplanePanel() {
        setPreferredSize(new Dimension(460, 360));
        setToolTipText("");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                // A double-click is the quickest way back out of a zoom, and
                // costs nothing: the first click of it selects a pole, which
                // is harmless, and the pair then resets the view.
                if (event.getClickCount() == 2) {
                    resetZoom();
                    return;
                }
                Mode hit = modeAt(event.getX(), event.getY(), getWidth(), getHeight());
                if (hit != null) {
                    setSelected(hit);
                    for (Listener listener : listeners) {
                        listener.poleSelected(hit);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                dragFrom = event.getPoint();
                dragTo = null;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragFrom != null) {
                    dragTo = event.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                java.awt.Point from = dragFrom;
                java.awt.Point to = dragTo;
                dragFrom = null;
                dragTo = null;
                repaint();
                // Below the slop this was a click that wandered a pixel or
                // two, and zooming on it would make selecting a pole a
                // gamble. mouseClicked fires for that case and selects.
                if (from == null || to == null
                        || Math.abs(to.x - from.x) < DRAG_SLOP
                        || Math.abs(to.y - from.y) < DRAG_SLOP) {
                    return;
                }
                zoomToDevice(from, to);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Notified whenever the zoom window changes, so a button can follow it. */
    public void setZoomListener(Runnable listener) {
        this.zoomListener = listener;
    }

    /** Whether a manual zoom is in force, i.e. whether a reset would do anything. */
    public boolean isZoomed() {
        return zoom != null;
    }

    /**
     * Back to the window fitted to whatever is on screen.
     *
     * <p>Also what a change of filter does, through {@link #setModes}: a
     * manual zoom is a rectangle chosen against one set of modes, and keeping
     * it across a change of that set is how a filter comes to look as though
     * it removed everything.
     */
    public void resetZoom() {
        if (zoom != null) {
            zoom = null;
            repaint();
            fireZoomChanged();
        }
    }

    private void fireZoomChanged() {
        if (zoomListener != null) {
            zoomListener.run();
        }
    }

    /** Turns a dragged rectangle in pixels into a window in data units. */
    private void zoomToDevice(java.awt.Point from, java.awt.Point to) {
        Bounds b = bounds(shown, zoom, getWidth(), getHeight());
        double re1 = b.re(Math.min(from.x, to.x));
        double re2 = b.re(Math.max(from.x, to.x));
        // y grows downward, so the lower pixel is the higher imaginary part.
        double im1 = b.im(Math.max(from.y, to.y));
        double im2 = b.im(Math.min(from.y, to.y));
        if (re2 - re1 <= 0.0 || im2 - im1 <= 0.0) {
            return;
        }
        zoom = new double[] {re1, re2, im1, im2};
        repaint();
        fireZoomChanged();
    }

    /**
     * The plot ground, re-resolved on every look-and-feel change because that
     * is what the theme toggle triggers. Set here rather than once in the
     * constructor: an explicitly set background is exactly what a LAF switch
     * leaves alone, which is how these two panels stayed white in a dark
     * window while everything around them re-themed.
     *
     * <p>Table.background rather than Panel.background, so the plot keeps
     * sitting on the same content surface the modes table beside it uses:
     * white against light grey as before, and a slightly raised panel in dark
     * rather than melting into the window.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        java.awt.Color ground = javax.swing.UIManager.getColor("Table.background");
        setBackground(ground != null ? ground : java.awt.Color.WHITE);
    }

    public void addSelectionListener(Listener listener) {
        listeners.add(listener);
    }

    public void setModes(List<Mode> modes) {
        this.shown = new ArrayList<Mode>(modes);
        this.selected = null;
        // Refit rather than hold the old rectangle. The window is fitted to
        // the modes on screen, so a filter that removed the far-left fast
        // modes is meant to close the plane in around what is left; that is
        // most of the point of having the filter.
        this.zoom = null;
        repaint();
        fireZoomChanged();
    }

    public void setSelected(Mode mode) {
        this.selected = mode;
        repaint();
    }

    public Mode selected() {
        return selected;
    }

    /**
     * The plot as SVG, at the zoom now on screen. What you see is what is
     * exported: a reader who zoomed in to make a point would otherwise save
     * the whole plane and have to make it again in a caption.
     */
    public String toSvg(int width, int height) {
        SvgSink sink = new SvgSink(width, height);
        render(sink, shown, selected, zoom, width, height);
        return sink.toSvg();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        render(new SwingSink(g, PlotStyle.isDark(getBackground())), shown, selected,
                zoom, getWidth(), getHeight());
        // The rubber band is screen furniture, not part of the figure, so it
        // is drawn here and never reaches the SVG sink.
        if (dragFrom != null && dragTo != null) {
            g.setColor(getForeground());
            g.setStroke(new java.awt.BasicStroke(1.0f, java.awt.BasicStroke.CAP_BUTT,
                    java.awt.BasicStroke.JOIN_MITER, 10.0f, new float[] {4.0f, 4.0f}, 0.0f));
            g.drawRect(Math.min(dragFrom.x, dragTo.x), Math.min(dragFrom.y, dragTo.y),
                    Math.abs(dragTo.x - dragFrom.x), Math.abs(dragTo.y - dragFrom.y));
        }
        g.dispose();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Mode hit = modeAt(event.getX(), event.getY(), getWidth(), getHeight());
        if (hit == null) {
            return null;
        }
        return String.format(java.util.Locale.ROOT,
                "mode %d: %.4f Hz, zeta %+.4f, lambda %+.4f %+.4fj",
                hit.index, hit.freqHz, hit.zeta, hit.re, hit.im);
    }

    private Mode modeAt(int px, int py, int width, int height) {
        Bounds b = bounds(shown, zoom, width, height);
        for (Mode mode : shown) {
            // Only what is drawn can be hit. Without this a pole scrolled off
            // by a zoom still answers tooltips and clicks, from a point on
            // the axis border where its coordinates happened to clamp.
            if (!b.contains(mode.re, mode.im)) {
                continue;
            }
            double x = b.x(mode.re);
            double y = b.y(mode.im);
            if (Math.hypot(px - x, py - y) <= POLE_R + 3.0) {
                return mode;
            }
        }
        return null;
    }

    /**
     * The axis window: the manual zoom if there is one, otherwise fitted to
     * everything shown.
     *
     * @param zoom {reLo, reHi, imLo, imHi} in data units, or null to fit
     */
    private static Bounds bounds(List<Mode> shown, double[] zoom, int width, int height) {
        if (zoom != null) {
            return new Bounds(zoom[0], zoom[1], zoom[2], zoom[3], width, height);
        }
        if (shown.isEmpty()) {
            return new Bounds(EMPTY_MIN_RE, EMPTY_MAX_RE, EMPTY_MIN_IM, EMPTY_MAX_IM,
                    width, height);
        }
        // Re starts at 0 on both sides so the stability boundary is always in
        // the fitted window: it is what every reading of this plot is made
        // against. Im is fitted to the data alone, since nothing is measured
        // from the real axis.
        double lo = 0.0;
        double hi = 0.0;
        double bot = Double.POSITIVE_INFINITY;
        double top = Double.NEGATIVE_INFINITY;
        for (Mode mode : shown) {
            lo = Math.min(lo, mode.re);
            hi = Math.max(hi, mode.re);
            bot = Math.min(bot, mode.im);
            top = Math.max(top, mode.im);
        }
        double rePad = Math.max((hi - lo) * FIT_PAD, MIN_PAD);
        double imPad = Math.max((top - bot) * FIT_PAD, MIN_PAD);
        return new Bounds(lo - rePad, hi + rePad, bot - imPad, top + imPad,
                width, height);
    }

    /** Renders at the fitted window; the harness and any caller without a zoom. */
    static void render(PlotSink sink, List<Mode> shown, Mode selected,
            int width, int height) {
        render(sink, shown, selected, null, width, height);
    }

    static void render(PlotSink sink, List<Mode> shown, Mode selected,
            double[] zoom, int width, int height) {
        Bounds b = bounds(shown, zoom, width, height);

        sink.group("axes");
        sink.line(b.x(b.reLo), b.y(b.imLo), b.x(b.reHi), b.y(b.imLo), "axis");
        sink.line(b.x(b.reLo), b.y(b.imLo), b.x(b.reLo), b.y(b.imHi), "axis");
        for (int step = 1; step <= 4; step++) {
            double im = b.imLo + (b.imHi - b.imLo) * step / 4.0;
            sink.line(b.x(b.reLo), b.y(im), b.x(b.reHi), b.y(im), "grid");
        }
        sink.text((b.x(b.reLo) + b.x(b.reHi)) / 2.0, height - 12.0,
                "Re(lambda)  [1/s]", "middle", "label");
        sink.text(b.x(b.reLo), PAD_TOP - 6.0,
                "Im(lambda)  [rad/s]", "start", "label");
        sink.endGroup();

        // The axis titles name the units; without tick labels there is still
        // nothing to measure against, which matters most in the exported SVG
        // because a report reader cannot hover for a tooltip. The left margin
        // has been free since the Im title moved above the plot area, and
        // PAD_BOTTOM already reserves the room under the Re axis.
        sink.group("ticks");
        for (int step = 1; step <= IM_TICKS; step++) {
            double im = b.imLo + (b.imHi - b.imLo) * step / (double) IM_TICKS;
            double y = b.y(im);
            sink.line(b.x(b.reLo) - TICK_LEN, y, b.x(b.reLo), y, "axis");
            sink.text(b.x(b.reLo) - TICK_LEN - 3.0, y + 4.0, tick(im), "end", "label");
        }
        for (int step = 0; step < RE_TICKS; step++) {
            double re = b.reLo + (b.reHi - b.reLo) * step / (double) (RE_TICKS - 1);
            double x = b.x(re);
            double y = b.y(b.imLo);
            sink.line(x, y, x, y + TICK_LEN, "axis");
            sink.text(x, y + TICK_LEN + 13.0, tick(re), "middle", "label");
        }
        sink.endGroup();

        // The stability boundary. Everything strictly left of this is stable.
        // Clipped, and so possibly absent: a manual zoom into a group of
        // well-damped modes need not contain Re = 0 at all, and the honest
        // picture then has no boundary in it rather than one ruled down the
        // edge of the axis.
        sink.group("boundary");
        clippedLine(sink, b, 0.0, b.imLo, 0.0, b.imHi, "bound", false);
        sink.endGroup();

        // Constant-damping rays, as in the notebook: from the origin along
        // Re = -zeta*r, Im = r*sqrt(1 - zeta^2). Drawn from the origin
        // outward past the top of the window and then clipped, so a window
        // that excludes the origin still shows the part of each ray crossing
        // it, at the right angle.
        sink.group("damping-rays");
        for (double zeta : new double[] {0.05, 0.10}) {
            double reach = Math.max(Math.abs(b.imLo), Math.abs(b.imHi));
            double r = reach / Math.sqrt(1.0 - zeta * zeta);
            clippedLine(sink, b, 0.0, 0.0, -zeta * r,
                    r * Math.sqrt(1.0 - zeta * zeta), "ray", true);
        }
        sink.endGroup();

        // One circle per mode and nothing over it: crimson says unstable, a
        // filled disc says this is the one the table and the panels below are
        // showing. Compared by mode index rather than by identity, so a
        // selection survives a caller that rebuilt its list.
        boolean anyUnstable = false;
        sink.group("poles");
        for (Mode mode : shown) {
            if (!b.contains(mode.re, mode.im)) {
                continue;
            }
            boolean unstable = mode.zeta < 0.0;
            anyUnstable |= unstable;
            String cls = unstable ? "unstable" : "pole";
            double x = b.x(mode.re);
            double y = b.y(mode.im);
            if (selected != null && mode.index == selected.index) {
                sink.filledCircle(x, y, POLE_R, cls);
            } else {
                sink.circle(x, y, POLE_R, cls);
            }
        }
        sink.endGroup();

        // The crimson circle and the crimson Re = 0 boundary are two different
        // meanings in one colour, so the marker that flags data is named. Only
        // drawn when there is something to name: a legend entry for a marker
        // that is not on the figure is its own small untruth. It carries the
        // same class as the unstable poles, so one hex edit restyles both, and
        // the same radius, so the key and the plot show the same marker.
        if (anyUnstable) {
            double lx = width - PAD_RIGHT - LEGEND_W;
            double ly = PAD_TOP + 10.0;
            sink.group("legend");
            sink.circle(lx, ly, POLE_R, "unstable");
            sink.text(lx + POLE_R + 9.0, ly + 4.0, "unstable (zeta < 0)",
                    "start", "label");
            sink.endGroup();
        }

        sink.group("labels");
        for (Mode mode : shown) {
            if (!b.contains(mode.re, mode.im)) {
                continue;
            }
            sink.text(b.x(mode.re) + POLE_R + 3.0, b.y(mode.im) - 4.0,
                    String.format(java.util.Locale.ROOT, "%.2f Hz", mode.freqHz),
                    "start", "label");
        }
        sink.endGroup();
    }

    /**
     * Draws a line clipped to the axis window, or nothing if it falls wholly
     * outside. Liang-Barsky, in data units rather than pixels, so the same
     * arithmetic serves the screen and the exported SVG at any size.
     *
     * @param dashed whether to draw it dashed, for the damping rays
     */
    private static void clippedLine(PlotSink sink, Bounds b, double x0, double y0,
            double x1, double y1, String cls, boolean dashed) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double t0 = 0.0;
        double t1 = 1.0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0 - b.reLo, b.reHi - x0, y0 - b.imLo, b.imHi - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                // Parallel to this edge: inside or out for its whole length,
                // and q decides which.
                if (q[i] < 0.0) {
                    return;
                }
                continue;
            }
            double t = q[i] / p[i];
            if (p[i] < 0.0) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
        }
        if (t0 > t1) {
            return;
        }
        double ax = b.x(x0 + t0 * dx);
        double ay = b.y(y0 + t0 * dy);
        double bx = b.x(x0 + t1 * dx);
        double by = b.y(y0 + t1 * dy);
        if (dashed) {
            sink.dashedLine(ax, ay, bx, by, cls);
        } else {
            sink.line(ax, ay, bx, by, cls);
        }
    }

    /**
     * A tick value, kept short enough that neighbouring ticks do not run into
     * each other: two decimals close to the origin, fewer as the magnitude
     * grows. A value that rounds to zero prints without a sign, since "-0.00"
     * on an axis reads as a defect.
     */
    private static String tick(double value) {
        double abs = Math.abs(value);
        String text = String.format(java.util.Locale.ROOT,
                abs >= 100.0 ? "%.0f" : abs >= 10.0 ? "%.1f" : "%.2f", value);
        return text.startsWith("-") && text.matches("-0(\\.0+)?")
                ? text.substring(1) : text;
    }

    /** Data-to-device mapping for one render. */
    private static final class Bounds {

        private final double reLo;
        private final double reHi;
        private final double imLo;
        private final double imHi;
        private final int width;
        private final int height;

        Bounds(double reLo, double reHi, double imLo, double imHi,
                int width, int height) {
            this.reLo = reLo;
            this.reHi = reHi;
            this.imLo = imLo;
            this.imHi = imHi;
            this.width = width;
            this.height = height;
        }

        double x(double re) {
            double span = reHi - reLo;
            double frac = span == 0.0 ? 0.0 : (re - reLo) / span;
            return PAD_LEFT + frac * (width - PAD_LEFT - PAD_RIGHT);
        }

        double y(double im) {
            double span = imHi - imLo;
            double frac = span == 0.0 ? 0.0 : (im - imLo) / span;
            return height - PAD_BOTTOM - frac * (height - PAD_TOP - PAD_BOTTOM);
        }

        /** Inverse of {@link #x}, for turning a dragged rectangle into a window. */
        double re(double px) {
            double plot = width - PAD_LEFT - PAD_RIGHT;
            double frac = plot == 0.0 ? 0.0 : (px - PAD_LEFT) / plot;
            return reLo + frac * (reHi - reLo);
        }

        /** Inverse of {@link #y}. */
        double im(double py) {
            double plot = height - PAD_TOP - PAD_BOTTOM;
            double frac = plot == 0.0 ? 0.0 : (height - PAD_BOTTOM - py) / plot;
            return imLo + frac * (imHi - imLo);
        }

        boolean contains(double re, double im) {
            return re >= reLo && re <= reHi && im >= imLo && im <= imHi;
        }
    }
}
