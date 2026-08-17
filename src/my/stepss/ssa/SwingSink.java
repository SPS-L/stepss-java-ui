package my.stepss.ssa;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * A PlotSink over Graphics2D. Class names map to the same colours the SVG
 * style block declares, so the screen and the exported file agree on what a
 * class means, though not always on its hex: this one follows the theme and
 * the SVG does not, which is the one difference between them.
 */
final class SwingSink implements PlotSink {

    private final Graphics2D g;
    private final boolean dark;

    /**
     * @param dark which column of {@link PlotStyle} to draw in, measured by
     *     the caller off the ground it is painting on rather than asked of
     *     the look and feel
     */
    SwingSink(Graphics2D g, boolean dark) {
        this.g = g;
        this.dark = dark;
    }

    private void style(String cls, boolean dashed) {
        PlotStyle.Entry entry = PlotStyle.of(cls);
        g.setColor(PlotStyle.color(entry.hex(dark)));
        g.setStroke(dashed
                ? new BasicStroke(entry.width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10.0f, new float[] {4.0f, 3.0f}, 0.0f)
                : new BasicStroke(entry.width));
    }

    @Override
    public void group(String id) {
    }

    @Override
    public void endGroup() {
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, String cls) {
        style(cls, false);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void dashedLine(double x1, double y1, double x2, double y2, String cls) {
        style(cls, true);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    @Override
    public void circle(double cx, double cy, double r, String cls) {
        style(cls, false);
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override
    public void cross(double cx, double cy, double r, String cls) {
        style(cls, false);
        g.draw(new Line2D.Double(cx - r, cy - r, cx + r, cy + r));
        g.draw(new Line2D.Double(cx - r, cy + r, cx + r, cy - r));
    }

    @Override
    public void arrow(double x1, double y1, double x2, double y2, String cls) {
        style(cls, false);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double head = 7.0;
        double spread = Math.toRadians(22.0);
        g.draw(new Line2D.Double(x2, y2, x2 - head * Math.cos(angle - spread),
                y2 - head * Math.sin(angle - spread)));
        g.draw(new Line2D.Double(x2, y2, x2 - head * Math.cos(angle + spread),
                y2 - head * Math.sin(angle + spread)));
    }

    @Override
    public void text(double x, double y, String s, String anchor, String cls) {
        PlotStyle.Entry entry = PlotStyle.of(cls);
        g.setColor(PlotStyle.color(entry.hex(dark)));

        if (entry.fontPx != null) {
            Font font = g.getFont();
            if (font != null) {
                g.setFont(font.deriveFont((float) entry.fontPx.intValue()));
            }
        }

        int w = g.getFontMetrics().stringWidth(s);
        double dx = "middle".equals(anchor) ? -w / 2.0 : "end".equals(anchor) ? -w : 0.0;
        g.drawString(s, (float) (x + dx), (float) y);
    }
}
