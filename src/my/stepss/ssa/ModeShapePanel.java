package my.stepss.ssa;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import my.stepss.plot.PlotSink;
import my.stepss.plot.PlotStyle;
import my.stepss.plot.SvgSink;
import my.stepss.plot.SwingSink;
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
 * <p>A simple mode with no entries is a second case. The engine writes a mode
 * shape for every mode, so an absence means the file is missing or
 * incomplete, and that is the only explanation offered. There used to be a
 * second, naming the real_limit a v1 engine filtered by; v1 results are no
 * longer read at all, so the flag that distinguished the two is gone and
 * naming real_limit would now invent a cause.
 * &lt;base&gt;_ms.dat is optional to {@link SsaResults#load}, and
 * the copy-out in StepssUI copies only the files that exist, so the missing
 * case is reachable on either version. The no-selection state, reached
 * through {@link #clear()}, is a third case again: no mode has been chosen
 * yet, so it draws the plain dial with neither arrows nor a message.
 */
public final class ModeShapePanel extends JPanel {

    private static final double R_MAX = 1.3;
    /** Below this the dial has no room left once the margin is taken. */
    private static final double MIN_RADIUS = 1.0;

    private List<ModeShapeEntry> entries = new ArrayList<ModeShapeEntry>();
    private boolean simple = true;
    private int modeIndex;
    private boolean noSelection = true;

    public ModeShapePanel() {
        setPreferredSize(new Dimension(360, 320));
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

    /**
     * No mode selected yet: the plain dial, with no arrows and no message.
     * Distinct from a simple mode that turned out to have no entries, which
     * {@link #show} reports as a missing file rather than as this blank state.
     */
    public void clear() {
        this.entries = new ArrayList<ModeShapeEntry>();
        this.modeIndex = 0;
        this.simple = true;
        this.noSelection = true;
        repaint();
    }

    /**
     * @param simple the mode's smp flag, false for a degenerate eigenvalue
     */
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
        boolean dark = PlotStyle.isDark(getBackground());
        if (noSelection) {
            renderBlank(new SwingSink(g, dark), getWidth(), getHeight());
        } else {
            render(new SwingSink(g, dark), entries, simple,
                    getWidth(), getHeight());
        }
        g.dispose();
    }

    /** The no-selection state: the plain dial, no arrows, no message. */
    private static void renderBlank(PlotSink sink, int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        drawRingsAndSpokes(sink, cx, cy, radius(width, height));
    }

    /**
     * The dial radius, never negative. Below 60 px the margin exceeds the
     * half-extent, and a negative r is an error in SVG rather than an empty
     * circle, so the whole exported file would be rejected.
     */
    private static double radius(int width, int height) {
        return Math.max(Math.min(width, height) / 2.0 - 30.0, MIN_RADIUS);
    }

    static void render(PlotSink sink, List<ModeShapeEntry> entries, boolean simple,
            int width, int height) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double radius = radius(width, height);

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
            // The one honest explanation left. The engine writes a mode shape
            // for every mode, so an empty one is a missing or incomplete file
            // rather than a threshold.
            sink.group("no-rows");
            sink.text(cx, cy - 8.0,
                    "No mode shape rows were written for this mode.",
                    "middle", "title");
            sink.text(cx, cy + 12.0,
                    "Every mode should have one, so the mode-shape file is"
                    + " missing from this directory or is incomplete.",
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
            // Not trimmed: Columns.slice already removed the a20 padding, and
            // a LEADING blank is part of the device name as the engine stored
            // it. Trimming here is what makes two similarly named machines
            // indistinguishable against the network data file.
            sink.text(cx + rr * Math.cos(th), cy - rr * Math.sin(th),
                    entry.device, "middle", "label");
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
