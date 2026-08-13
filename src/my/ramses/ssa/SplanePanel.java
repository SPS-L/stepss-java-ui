package my.ramses.ssa;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * The s-plane, following the python-ui notebook's cell 20: a crimson
 * stability boundary at Re = 0, dashed constant-damping rays, one hollow
 * circle per mode labelled with its frequency, and unstable modes
 * overplotted as a crimson cross.
 *
 * <p>The notebook's window is the MINIMUM extent, not the fixed one, so this
 * works on systems other than Kundur. Unlike the notebook it is interactive:
 * clicking a pole selects that mode, and the selection is ringed.
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

    private List<Mode> shown = new ArrayList<Mode>();
    private Mode selected;
    private final List<Listener> listeners = new ArrayList<Listener>();

    public SplanePanel() {
        setPreferredSize(new Dimension(460, 360));
        setBackground(java.awt.Color.WHITE);
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
        render(new SwingSink(g), shown, selected, getWidth(), getHeight());
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
        sink.text(5.0, (b.y(b.imLo) + b.y(b.imHi)) / 2.0,
                "Im(lambda)  [rad/s]", "start", "label");
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

        sink.group("poles");
        for (Mode mode : shown) {
            sink.circle(b.x(mode.re), b.y(mode.im), POLE_R, "pole");
        }
        sink.endGroup();

        sink.group("unstable");
        for (Mode mode : shown) {
            if (mode.zeta < 0.0) {
                sink.cross(b.x(mode.re), b.y(mode.im), POLE_R + 2.0, "unstable");
            }
        }
        sink.endGroup();

        if (selected != null) {
            sink.group("selected");
            sink.circle(b.x(selected.re), b.y(selected.im), POLE_R + 4.0, "pole");
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
