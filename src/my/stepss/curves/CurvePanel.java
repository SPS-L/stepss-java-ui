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
import my.stepss.plot.NiceScale;
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
    private CurveAxes axes = CurveAxes.POST;
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

    /** Replaces the frame spec and repaints. Never null. */
    public void setAxes(CurveAxes axes) {
        this.axes = axes;
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
        Bounds b = bounds(data, getWidth(), getHeight());
        // Against the frame's real top rather than the constant: a titled
        // panel's frame starts lower, and the band above it is not plot area.
        if (px < PAD_LEFT || px > getWidth() - PAD_RIGHT
                || py < b.padTop() || py > getHeight() - PAD_BOTTOM) {
            return null;
        }
        // Named from the axis spec, not hard-coded to t: the phase-plane panels
        // put delta on x, and calling that "t" tells the reader the wrong thing
        // about the number beside it. The post-analysis spec's "t (s)" and
        // empty y label reduce to the "t" and "value" this always said.
        return String.format(Locale.ROOT, "%s = %.4g, %s = %.6g",
                shortName(axes.xLabel, "t"), b.t(px),
                shortName(axes.yLabel, "value"), b.v(py));
    }

    /**
     * An axis caption with its parenthesised unit dropped, for a readout that
     * has to fit in a tooltip: "delta (pu)" reads out as "delta".
     *
     * @param fallback used when the caption is empty or is nothing but a unit
     */
    private static String shortName(String label, String fallback) {
        if (label.isEmpty()) {
            return fallback;
        }
        int paren = label.indexOf('(');
        String name = (paren < 0 ? label : label.substring(0, paren)).trim();
        return name.isEmpty() ? fallback : name;
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
        // Tolerance of a tick's width, not half a step. Half a step admits a
        // tick one whole step past the axis end, which never showed while the
        // bounds were always rounded onto the ladder and does as soon as a fixed
        // range is used exactly: an axis to 12.5 grew a label at 15.
        for (double x = b.xLo; x <= b.xHi + b.xStep * 1e-9; x += b.xStep) {
            double dx = b.px(x);
            sink.line(dx, b.py(b.yLo), dx, b.py(b.yLo) + TICK_LEN, "axis");
            sink.text(dx, b.py(b.yLo) + TICK_LEN + 13.0, tick(x), "middle", "label");
        }
        for (double y = b.yLo; y <= b.yHi + b.yStep * 1e-9; y += b.yStep) {
            double dy = b.py(y);
            sink.line(b.px(b.xLo) - TICK_LEN, dy, b.px(b.xLo), dy, "axis");
            sink.text(b.px(b.xLo) - TICK_LEN - 3.0, dy + 4.0, tick(y), "end", "label");
        }
        sink.endGroup();

        sink.group("axis-titles");
        sink.text((b.px(b.xLo) + b.px(b.xHi)) / 2.0, height - 12.0,
                axes.xLabel, "middle", "label");
        String unit = axes.yLabel.isEmpty() ? data.commonUnit() : axes.yLabel;
        if (!unit.isEmpty()) {
            sink.text(b.px(b.xLo), b.padTop() - 12.0, unit, "start", "label");
        } else if (data.distinctUnits() > 1) {
            // Said once, on the figure, so a flat curve is explained rather
            // than mysterious, and so the note survives into an export. The
            // remedy is to extract the groups separately, which now yields
            // one window each.
            sink.text(b.px(b.xLo), b.padTop() - 12.0,
                    "mixed units: extract separately to compare fairly",
                    "start", "label");
        }
        sink.endGroup();

        if (!axes.title.isEmpty()) {
            sink.group("title");
            sink.text((b.px(b.xLo) + b.px(b.xHi)) / 2.0, b.padTop() - 30.0,
                    axes.title, "middle", "title");
            sink.endGroup();
        }

        sink.clipRect(b.px(b.xLo), b.py(b.yHi),
                b.px(b.xHi) - b.px(b.xLo), b.py(b.yLo) - b.py(b.yHi));
        if (axes.identity) {
            // y = x, so a user can see whether the simulation is keeping up
            // with the wall clock. Inside the clip because the diagonal leaves
            // the frame whenever the two axes have different extents.
            sink.group("identity");
            double lo = Math.max(b.xLo, b.yLo);
            double hi = Math.min(b.xHi, b.yHi);
            if (hi > lo) {
                sink.dashedLine(b.px(lo), b.py(lo), b.px(hi), b.py(hi), "grid");
            }
            sink.endGroup();
        }
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
            if (one.w == null) {
                sink.polyline(xs, ys, one.t.length, PlotStyle.seriesClass(s));
            } else {
                // One element per segment rather than one per run, because the
                // class can change at every step. Only LAT takes this path and
                // only one LAT observable exists per panel, so the element
                // count is bounded by the sample count of a single curve.
                //
                // Coloured by the segment's START point, which is a deliberate
                // one-sample correction rather than a reproduction. gnuplot's
                // "with lines palette" colours a segment by its END point, so
                // the figures the engine used to draw were shifted one sample
                // against their own data: simul_decomp.f90 runs update_latency
                // after the accepted step, setting the flag for the step to
                // come, and only then writes the sample. The flag beside t(i)
                // therefore governs the interval that follows it and holds
                // forward, which is what a step plot does. Sample 0 is written
                // before the first update_latency and carries the initialised
                // all-active flag, which is also right: the first step solves
                // everything.
                for (int i = 1; i < one.t.length; i++) {
                    sink.line(xs[i - 1], ys[i - 1], xs[i], ys[i],
                            one.w[i - 1] >= 0.5 ? "active" : "latent");
                }
            }
        }
        sink.endGroup();
        sink.endClip();

        if (axes.legend) {
            sink.group("legend");
            double ly = b.padTop() + LEGEND_ROW;
            for (int s = 0; s < data.series.size(); s++) {
                double lx = b.px(b.xLo) + 10.0;
                sink.line(lx, ly - 4.0, lx + 18.0, ly - 4.0, PlotStyle.seriesClass(s));
                sink.text(lx + 24.0, ly, data.series.get(s).label, "start", "label");
                ly += LEGEND_ROW;
            }
            sink.endGroup();
        }

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
        if (axes.xFixed()) {
            // Before the zoom, so a rubber-band zoom still overrides it: a
            // fixed range is where the axis starts, not a refusal to look
            // closer.
            tLo = axes.xLo;
            tHi = axes.xHi;
        }
        if (zoom != null) {
            tLo = zoom[0];
            tHi = zoom[1];
            vLo = zoom[2];
            vHi = zoom[3];
        }
        double xStep = NiceScale.step(tHi - tLo, X_TICKS);
        double yStep = NiceScale.step(vHi - vLo, Y_TICKS);
        // A fixed x range is used exactly, not rounded outward. Rounding it made
        // a run's last sample land short of the frame's right edge whenever the
        // stop time was not already on the tick ladder: 12.5 s became an axis to
        // 15, so a completed run drew to 83% of the width and read as one that
        // stopped early. gnuplot's own "set xrange [0 : tstop]" did not round
        // either, so this is the faithful reading as well as the useful one.
        // Ticks still come off the ladder and simply stop before the end.
        double[] x = axes.xFixed() && zoom == null
                ? new double[] {tLo, tHi}
                : NiceScale.bounds(tLo, tHi, xStep);
        // y gets a small pad BEFORE rounding, which is what the design asks for.
        // Without it a curve whose extent happens to equal its rounded bounds is
        // drawn along the frame edges with half its stroke width clipped: a
        // trace spanning exactly 0.9 to 1.0 came out on the borders.
        double vPad = (vHi - vLo) * 0.05;
        double[] y = NiceScale.bounds(vLo - vPad, vHi + vPad, yStep);
        // A titled panel needs a line for the title above the one the y unit
        // occupies. 18px because the title is drawn in the "title" class, whose
        // PlotStyle entry is 13px, and 18 clears its ascent and descent with a
        // little air. Not LEGEND_ROW, which is 14 and sized for 11px labels.
        double padTop = axes.title.isEmpty() ? PAD_TOP : PAD_TOP + 18.0;
        return new Bounds(x[0], x[1], y[0], y[1], xStep, yStep, width, height, padTop);
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
        private final double padTop;

        Bounds(double xLo, double xHi, double yLo, double yHi,
                double xStep, double yStep, int width, int height, double padTop) {
            this.xLo = xLo;
            this.xHi = xHi;
            this.yLo = yLo;
            this.yHi = yHi;
            this.xStep = xStep;
            this.yStep = yStep;
            this.width = width;
            this.height = height;
            this.padTop = padTop;
        }

        double padTop() {
            return padTop;
        }

        double px(double t) {
            double span = xHi - xLo;
            double frac = span == 0.0 ? 0.0 : (t - xLo) / span;
            return PAD_LEFT + frac * (width - PAD_LEFT - PAD_RIGHT);
        }

        double py(double v) {
            double span = yHi - yLo;
            double frac = span == 0.0 ? 0.0 : (v - yLo) / span;
            return height - PAD_BOTTOM - frac * (height - padTop - PAD_BOTTOM);
        }

        /** The time at a device x. The inverse of {@link #px}. */
        double t(double deviceX) {
            double plot = width - PAD_LEFT - PAD_RIGHT;
            double frac = plot == 0.0 ? 0.0 : (deviceX - PAD_LEFT) / plot;
            return xLo + frac * (xHi - xLo);
        }

        /** The value at a device y. The inverse of {@link #py}. */
        double v(double deviceY) {
            double plot = height - padTop - PAD_BOTTOM;
            double frac = plot == 0.0 ? 0.0
                    : (height - PAD_BOTTOM - deviceY) / plot;
            return yLo + frac * (yHi - yLo);
        }
    }
}
