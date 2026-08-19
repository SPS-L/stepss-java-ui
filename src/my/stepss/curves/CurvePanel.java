package my.stepss.curves;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.swing.JPanel;
import javax.swing.UIManager;
import my.stepss.plot.PlotSink;
import my.stepss.plot.PlotStyle;
import my.stepss.plot.SvgSink;
import my.stepss.plot.SwingSink;

/**
 * One extraction's curves, all overlaid on a single y axis with a legend,
 * which is what caption.f90 draws today (one plot, N curves, "set key
 * opaque").
 *
 * <p>Painted through {@link PlotSink} rather than against Graphics2D, so the
 * exported SVG is produced by the code that painted the screen and cannot
 * drift from it. This is the same arrangement SplanePanel uses.
 */
public final class CurvePanel extends JPanel {

    private static final int PAD_LEFT = 70;
    private static final int PAD_RIGHT = 20;
    private static final int PAD_TOP = 30;
    private static final int PAD_BOTTOM = 50;
    private static final double TICK_LEN = 4.0;
    private static final int X_TICKS = 6;
    private static final int Y_TICKS = 5;
    /** Room for one legend row, and the gap above the first. */
    private static final double LEGEND_ROW = 14.0;

    private CurveData data = new CurveData(
            new java.util.ArrayList<CurveSeries>(), null, 0);
    /** The zoom window as {tLo, tHi, vLo, vHi}, or null for auto-scale. */
    private double[] zoom;
    /** Where a drag began, in device pixels, or null when not dragging. */
    private java.awt.Point dragFrom;
    /** The current drag rectangle, painted as feedback while dragging. */
    private java.awt.Rectangle dragTo;

    public CurvePanel() {
        setPreferredSize(new Dimension(760, 460));
        setToolTipText("");
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                dragFrom = event.getPoint();
                dragTo = null;
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                java.awt.Rectangle box = dragTo;
                dragFrom = null;
                dragTo = null;
                // A click, not a drag. Anything smaller than this is a
                // misclick rather than an intended window, and zooming to a
                // few pixels leaves no way back except the reset.
                if (box == null || box.width < 8 || box.height < 8) {
                    repaint();
                    return;
                }
                Bounds b = bounds(data, getWidth(), getHeight());
                setZoom(b.t(box.x), b.t(box.x + box.width),
                        b.v(box.y + box.height), b.v(box.y));
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    resetZoom();
                }
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent event) {
                if (dragFrom == null) {
                    return;
                }
                dragTo = new java.awt.Rectangle(
                        Math.min(dragFrom.x, event.getX()),
                        Math.min(dragFrom.y, event.getY()),
                        Math.abs(event.getX() - dragFrom.x),
                        Math.abs(event.getY() - dragFrom.y));
                repaint();
            }
        });
    }

    /**
     * The plot ground, re-resolved on every look-and-feel change because that
     * is what the theme toggle triggers. Set here rather than in the
     * constructor: an explicitly set background is exactly what a LAF switch
     * leaves alone, which is how the ssa panels once stayed white in a dark
     * window.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        Color ground = UIManager.getColor("Table.background");
        setBackground(ground != null ? ground : Color.WHITE);
    }

    public void setData(CurveData data) {
        this.data = data;
        repaint();
    }

    public CurveData data() {
        return data;
    }

    /**
     * Narrows both axes to this data window. The buffers are untouched:
     * zooming is a view concern, so it can never lose a sample.
     */
    public void setZoom(double tLo, double tHi, double vLo, double vHi) {
        this.zoom = new double[] {tLo, tHi, vLo, vHi};
        repaint();
    }

    public void resetZoom() {
        this.zoom = null;
        repaint();
    }

    public boolean zoomed() {
        return zoom != null;
    }

    /**
     * The data coordinates under a device point, or null when the point is
     * outside the plot area, where there is nothing to report.
     */
    public String readoutAt(int px, int py) {
        if (data.series.isEmpty()) {
            return null;
        }
        if (px < PAD_LEFT || px > getWidth() - PAD_RIGHT
                || py < PAD_TOP || py > getHeight() - PAD_BOTTOM) {
            return null;
        }
        Bounds b = bounds(data, getWidth(), getHeight());
        return String.format(Locale.ROOT, "t = %.4g, value = %.6g",
                b.t(px), b.v(py));
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent event) {
        return readoutAt(event.getX(), event.getY());
    }

    public String toSvg(int width, int height) {
        SvgSink sink = new SvgSink(width, height);
        render(sink, data, width, height);
        return sink.toSvg();
    }

    /**
     * The same figure as a raster image, on the light ground exports use.
     * Drawn with dark=false for the reason SvgSink's comment gives: a saved
     * figure should not come out inverted because of what the application
     * happened to be wearing.
     */
    public BufferedImage toPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(PlotStyle.color(PlotStyle.EXPORT_BACKGROUND));
            g.fillRect(0, 0, width, height);
            render(new SwingSink(g, false), data, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            render(new SwingSink(g, PlotStyle.isDark(getBackground())), data,
                    getWidth(), getHeight());
        } finally {
            g.dispose();
        }
    }

    void render(PlotSink sink, CurveData data, int width, int height) {
        if (data.series.isEmpty()) {
            sink.text(width / 2.0, height / 2.0, "No curves extracted",
                    "middle", "label");
            return;
        }

        Bounds b = bounds(data, width, height);

        sink.group("axes");
        sink.line(b.px(b.xLo), b.py(b.yLo), b.px(b.xHi), b.py(b.yLo), "axis");
        sink.line(b.px(b.xLo), b.py(b.yLo), b.px(b.xLo), b.py(b.yHi), "axis");
        sink.endGroup();

        sink.group("grid");
        for (double y = b.yLo + b.yStep; y < b.yHi - b.yStep / 2.0; y += b.yStep) {
            sink.line(b.px(b.xLo), b.py(y), b.px(b.xHi), b.py(y), "grid");
        }
        sink.endGroup();

        sink.group("ticks");
        for (double x = b.xLo; x <= b.xHi + b.xStep / 2.0; x += b.xStep) {
            double dx = b.px(x);
            sink.line(dx, b.py(b.yLo), dx, b.py(b.yLo) + TICK_LEN, "axis");
            sink.text(dx, b.py(b.yLo) + TICK_LEN + 13.0, tick(x), "middle", "label");
        }
        for (double y = b.yLo; y <= b.yHi + b.yStep / 2.0; y += b.yStep) {
            double dy = b.py(y);
            sink.line(b.px(b.xLo) - TICK_LEN, dy, b.px(b.xLo), dy, "axis");
            sink.text(b.px(b.xLo) - TICK_LEN - 3.0, dy + 4.0, tick(y), "end", "label");
        }
        sink.endGroup();

        sink.group("axis-titles");
        sink.text((b.px(b.xLo) + b.px(b.xHi)) / 2.0, height - 12.0,
                "t (s)", "middle", "label");
        String unit = data.commonUnit();
        if (!unit.isEmpty()) {
            sink.text(b.px(b.xLo), PAD_TOP - 12.0, unit, "start", "label");
        } else if (data.distinctUnits() > 1) {
            // Said once, on the figure, so a flat curve is explained rather
            // than mysterious, and so the note survives into an export. The
            // remedy is to extract the groups separately, which now yields
            // one window each.
            sink.text(b.px(b.xLo), PAD_TOP - 12.0,
                    "mixed units: extract separately to compare fairly",
                    "start", "label");
        }
        sink.endGroup();

        sink.clipRect(b.px(b.xLo), b.py(b.yHi),
                b.px(b.xHi) - b.px(b.xLo), b.py(b.yLo) - b.py(b.yHi));
        sink.group("curves");
        double[] xs = new double[0];
        double[] ys = new double[0];
        for (int s = 0; s < data.series.size(); s++) {
            CurveSeries one = data.series.get(s);
            if (one.t.length > xs.length) {
                xs = new double[one.t.length];
                ys = new double[one.t.length];
            }
            for (int i = 0; i < one.t.length; i++) {
                xs[i] = b.px(one.t[i]);
                ys[i] = b.py(one.v[i]);
            }
            sink.polyline(xs, ys, one.t.length, PlotStyle.seriesClass(s));
        }
        sink.endGroup();
        sink.endClip();

        sink.group("legend");
        double ly = PAD_TOP + LEGEND_ROW;
        for (int s = 0; s < data.series.size(); s++) {
            double lx = b.px(b.xLo) + 10.0;
            sink.line(lx, ly - 4.0, lx + 18.0, ly - 4.0, PlotStyle.seriesClass(s));
            sink.text(lx + 24.0, ly, data.series.get(s).label, "start", "label");
            ly += LEGEND_ROW;
        }
        sink.endGroup();

        if (dragTo != null) {
            sink.group("zoom-box");
            sink.line(dragTo.x, dragTo.y, dragTo.x + dragTo.width, dragTo.y, "axis");
            sink.line(dragTo.x, dragTo.y + dragTo.height,
                    dragTo.x + dragTo.width, dragTo.y + dragTo.height, "axis");
            sink.line(dragTo.x, dragTo.y, dragTo.x, dragTo.y + dragTo.height, "axis");
            sink.line(dragTo.x + dragTo.width, dragTo.y,
                    dragTo.x + dragTo.width, dragTo.y + dragTo.height, "axis");
            sink.endGroup();
        }
    }

    /**
     * The next round number at or below {@code raw = span / targetTicks}, on
     * the 1, 2, 5 times a power of ten ladder. A zero span means a flat curve,
     * which still needs a non-zero step or the tick loop cannot advance.
     */
    public static double niceStep(double span, int targetTicks) {
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
    public static double[] niceBounds(double lo, double hi, double step) {
        double low = Math.floor(lo / step) * step;
        double high = Math.ceil(hi / step) * step;
        if (high - low < step / 2.0) {
            low -= step;
            high += step;
        }
        return new double[] {low, high};
    }

    private Bounds bounds(CurveData data, int width, int height) {
        double tLo = Double.POSITIVE_INFINITY;
        double tHi = Double.NEGATIVE_INFINITY;
        double vLo = Double.POSITIVE_INFINITY;
        double vHi = Double.NEGATIVE_INFINITY;
        for (CurveSeries one : data.series) {
            for (int i = 0; i < one.t.length; i++) {
                tLo = Math.min(tLo, one.t[i]);
                tHi = Math.max(tHi, one.t[i]);
                vLo = Math.min(vLo, one.v[i]);
                vHi = Math.max(vHi, one.v[i]);
            }
        }
        if (tLo > tHi) {
            // Every series is empty: DYNGRAPH wrote a header-only file.
            tLo = 0.0;
            tHi = 1.0;
            vLo = 0.0;
            vHi = 1.0;
        }
        if (zoom != null) {
            tLo = zoom[0];
            tHi = zoom[1];
            vLo = zoom[2];
            vHi = zoom[3];
        }
        double xStep = niceStep(tHi - tLo, X_TICKS);
        double yStep = niceStep(vHi - vLo, Y_TICKS);
        double[] x = niceBounds(tLo, tHi, xStep);
        double[] y = niceBounds(vLo, vHi, yStep);
        return new Bounds(x[0], x[1], y[0], y[1], xStep, yStep, width, height);
    }

    /** Short enough that neighbouring ticks do not run into each other. */
    private static String tick(double value) {
        double abs = Math.abs(value);
        String text = String.format(Locale.ROOT,
                abs >= 1000.0 ? "%.0f"
                        : abs >= 10.0 ? "%.1f"
                        : abs >= 0.01 || abs == 0.0 ? "%.3f" : "%.1e", value);
        return text.startsWith("-") && text.matches("-0(\\.0+)?")
                ? text.substring(1) : text;
    }

    /** Data-to-device mapping for one render. */
    private static final class Bounds {

        private final double xLo;
        private final double xHi;
        private final double yLo;
        private final double yHi;
        private final double xStep;
        private final double yStep;
        private final int width;
        private final int height;

        Bounds(double xLo, double xHi, double yLo, double yHi,
                double xStep, double yStep, int width, int height) {
            this.xLo = xLo;
            this.xHi = xHi;
            this.yLo = yLo;
            this.yHi = yHi;
            this.xStep = xStep;
            this.yStep = yStep;
            this.width = width;
            this.height = height;
        }

        double px(double t) {
            double span = xHi - xLo;
            double frac = span == 0.0 ? 0.0 : (t - xLo) / span;
            return PAD_LEFT + frac * (width - PAD_LEFT - PAD_RIGHT);
        }

        double py(double v) {
            double span = yHi - yLo;
            double frac = span == 0.0 ? 0.0 : (v - yLo) / span;
            return height - PAD_BOTTOM - frac * (height - PAD_TOP - PAD_BOTTOM);
        }

        /** The time at a device x. The inverse of {@link #px}. */
        double t(double deviceX) {
            double plot = width - PAD_LEFT - PAD_RIGHT;
            double frac = plot == 0.0 ? 0.0 : (deviceX - PAD_LEFT) / plot;
            return xLo + frac * (xHi - xLo);
        }

        /** The value at a device y. The inverse of {@link #py}. */
        double v(double deviceY) {
            double plot = height - PAD_TOP - PAD_BOTTOM;
            double frac = plot == 0.0 ? 0.0
                    : (height - PAD_BOTTOM - deviceY) / plot;
            return yLo + frac * (yHi - yLo);
        }
    }
}
