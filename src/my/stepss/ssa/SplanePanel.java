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
 * <p>It parts from the notebook in two places. The notebook's window is the
 * MINIMUM extent here rather than the fixed one, so this works on systems
 * other than Kundur, and this panel is interactive: clicking a pole selects
 * that mode, and that mode's circle is filled.
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

    private static final double MIN_RE = -3.0;
    private static final double MAX_RE = 0.5;
    private static final double MIN_IM = 0.0;
    private static final double MAX_IM = 9.0;
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

    public SplanePanel() {
        setPreferredSize(new Dimension(460, 360));
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                Mode hit = modeAt(event.getX(), event.getY(), getWidth(), getHeight());
                if (hit != null) {
                    setSelected(hit);
                    for (Listener listener : listeners) {
                        listener.poleSelected(hit);
                    }
                }
            }
        });
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
        repaint();
    }

    public void setSelected(Mode mode) {
        this.selected = mode;
        repaint();
    }

    public Mode selected() {
        return selected;
    }

    public String toSvg(int width, int height) {
        SvgSink sink = new SvgSink(width, height);
        render(sink, shown, selected, width, height);
        return sink.toSvg();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        render(new SwingSink(g, PlotStyle.isDark(getBackground())), shown, selected,
                getWidth(), getHeight());
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
        Bounds b = bounds(shown, width, height);
        for (Mode mode : shown) {
            double x = b.x(mode.re);
            double y = b.y(mode.im);
            if (Math.hypot(px - x, py - y) <= POLE_R + 3.0) {
                return mode;
            }
        }
        return null;
    }

    /** The axis window: the notebook's, expanded to hold everything shown. */
    private static Bounds bounds(List<Mode> shown, int width, int height) {
        double lo = MIN_RE;
        double hi = MAX_RE;
        double top = MAX_IM;
        double bot = MIN_IM;
        for (Mode mode : shown) {
            lo = Math.min(lo, mode.re * 1.1);
            hi = Math.max(hi, mode.re * 1.1);
            top = Math.max(top, mode.im * 1.1);
            bot = Math.min(bot, mode.im * 1.1);
        }
        return new Bounds(lo, hi, bot, top, width, height);
    }

    static void render(PlotSink sink, List<Mode> shown, Mode selected,
            int width, int height) {
        Bounds b = bounds(shown, width, height);

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
        sink.group("boundary");
        sink.line(b.x(0.0), b.y(b.imLo), b.x(0.0), b.y(b.imHi), "bound");
        sink.endGroup();

        // Constant-damping rays, as in the notebook: from the origin along
        // Re = -zeta*r, Im = r*sqrt(1 - zeta^2).
        sink.group("damping-rays");
        for (double zeta : new double[] {0.05, 0.10}) {
            double r = b.imHi / Math.sqrt(1.0 - zeta * zeta);
            sink.dashedLine(b.x(0.0), b.y(0.0), b.x(-zeta * r),
                    b.y(r * Math.sqrt(1.0 - zeta * zeta)), "ray");
        }
        sink.endGroup();

        // One circle per mode and nothing over it: crimson says unstable, a
        // filled disc says this is the one the table and the panels below are
        // showing. Compared by mode index rather than by identity, so a
        // selection survives a caller that rebuilt its list.
        boolean anyUnstable = false;
        sink.group("poles");
        for (Mode mode : shown) {
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
            sink.text(b.x(mode.re) + POLE_R + 3.0, b.y(mode.im) - 4.0,
                    String.format(java.util.Locale.ROOT, "%.2f Hz", mode.freqHz),
                    "start", "label");
        }
        sink.endGroup();
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
    }
}
