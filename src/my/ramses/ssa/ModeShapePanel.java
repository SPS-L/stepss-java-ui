package my.ramses.ssa;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * The mode-shape dial, following the python-ui notebook's cell 17: an arrow
 * per machine from the origin to (angle, magnitude), labelled at 1.12 times
 * its magnitude, with rmax 1.3.
 *
 * <p>For a degenerate mode this refuses to draw. In a degenerate eigenspace
 * the individual eigenvectors are not unique, so the picture would be
 * basis-dependent: it would come out differently on another machine while
 * looking exactly as authoritative. Refusing is the honest option.
 *
 * <p>A simple mode with no entries is a second, different case: the engine
 * writes &lt;base&gt;_ms.dat only for modes that passed real_limit, the same
 * guard it applies to the participation file, so an empty entry list there
 * means "filtered out", not "nothing to show". That case says so instead of
 * drawing an empty dial. The no-selection state, reached through
 * {@link #clear()}, is a third case again: no mode has been chosen yet, so
 * it draws the plain dial with neither arrows nor a message.
 */
public final class ModeShapePanel extends JPanel {

    private static final double R_MAX = 1.3;

    private List<ModeShapeEntry> entries = new ArrayList<ModeShapeEntry>();
    private boolean simple = true;
    private int modeIndex;
    private boolean noSelection = true;

    public ModeShapePanel() {
        setPreferredSize(new Dimension(360, 320));
        setBackground(java.awt.Color.WHITE);
    }

    /**
     * No mode selected yet: the plain dial, with no arrows and no message.
     * Distinct from a simple mode that turned out to have no entries, which
     * {@link #show} reports as filtered rather than as this blank state.
     */
    public void clear() {
        this.entries = new ArrayList<ModeShapeEntry>();
        this.modeIndex = 0;
        this.simple = true;
        this.noSelection = true;
        repaint();
    }

    public void show(List<ModeShapeEntry> entries, int modeIndex, boolean simple) {
        this.entries = new ArrayList<ModeShapeEntry>(entries);
        this.modeIndex = modeIndex;
        this.simple = simple;
        this.noSelection = false;
        repaint();
    }

    public int modeIndex() {
        return modeIndex;
    }

    public String toSvg(int width, int height) {
        SvgSink sink = new SvgSink(width, height);
        if (noSelection) {
            renderBlank(sink, width, height);
        } else {
            render(sink, entries, simple, width, height);
        }
        return sink.toSvg();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        if (noSelection) {
            renderBlank(new SwingSink(g), getWidth(), getHeight());
        } else {
            render(new SwingSink(g), entries, simple, getWidth(), getHeight());
        }
        g.dispose();
    }

    /** The no-selection state: the plain dial, no arrows, no message. */
    private static void renderBlank(PlotSink sink, int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double radius = Math.min(width, height) / 2.0 - 30.0;
        drawRingsAndSpokes(sink, cx, cy, radius);
    }

    static void render(PlotSink sink, List<ModeShapeEntry> entries, boolean simple,
            int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double radius = Math.min(width, height) / 2.0 - 30.0;

        if (!simple) {
            sink.group("refusal");
            sink.text(cx, cy - 8.0, "This mode is degenerate (simple = 0).",
                    "middle", "title");
            sink.text(cx, cy + 12.0,
                    "Its eigenvectors are not unique, so a mode shape would be",
                    "middle", "label");
            sink.text(cx, cy + 28.0,
                    "basis-dependent and would differ on another machine.",
                    "middle", "label");
            sink.endGroup();
            return;
        }

        if (entries.isEmpty()) {
            // Same absence the participation panel reports: the engine
            // writes _ms.dat rows only for modes that passed real_limit, so
            // no entries means filtered out, not a mode with nothing to show.
            sink.group("filtered");
            sink.text(cx, cy - 8.0, "This mode was filtered out by real_limit.",
                    "middle", "title");
            sink.text(cx, cy + 12.0,
                    "No mode shape was written for it, so none is shown here.",
                    "middle", "label");
            sink.endGroup();
            return;
        }

        drawRingsAndSpokes(sink, cx, cy, radius);

        sink.group("arrows");
        for (ModeShapeEntry entry : entries) {
            double th = Math.toRadians(entry.angleDeg);
            double rr = radius * entry.magnitude / R_MAX;
            sink.arrow(cx, cy, cx + rr * Math.cos(th), cy - rr * Math.sin(th), "shape");
        }
        sink.endGroup();

        sink.group("labels");
        for (ModeShapeEntry entry : entries) {
            double th = Math.toRadians(entry.angleDeg);
            double rr = radius * entry.magnitude * 1.12 / R_MAX;
            sink.text(cx + rr * Math.cos(th), cy - rr * Math.sin(th),
                    entry.device.trim(), "middle", "label");
        }
        sink.endGroup();
    }

    private static void drawRingsAndSpokes(PlotSink sink, double cx, double cy,
            double radius) {
        sink.group("rings");
        for (double r : new double[] {0.5, 1.0}) {
            sink.circle(cx, cy, radius * r / R_MAX, "grid");
        }
        sink.endGroup();

        sink.group("spokes");
        for (int deg = 0; deg < 360; deg += 30) {
            double th = Math.toRadians(deg);
            sink.line(cx, cy, cx + radius * Math.cos(th), cy - radius * Math.sin(th),
                    "axis");
        }
        sink.endGroup();
    }
}
