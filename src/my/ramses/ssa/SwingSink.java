package my.ramses.ssa;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * A PlotSink over Graphics2D. Class names map to the same colours the SVG
 * style block declares, so the screen and the exported file agree.
 */
final class SwingSink implements PlotSink {

    private final Graphics2D g;

    SwingSink(Graphics2D g) {
        this.g = g;
    }

    private void style(String cls, boolean dashed) {
        float w = 1.0f;
        Color c = new Color(0x33, 0x33, 0x33);
        if ("grid".equals(cls)) {
            c = new Color(0xcc, 0xcc, 0xcc);
            w = 0.5f;
        } else if ("bound".equals(cls)) {
            c = new Color(0xdc, 0x14, 0x3c);
            w = 1.5f;
        } else if ("ray".equals(cls)) {
            c = new Color(0x99, 0x99, 0x99);
        } else if ("pole".equals(cls)) {
            c = new Color(0x1f, 0x77, 0xb4);
            w = 1.5f;
        } else if ("unstable".equals(cls)) {
            c = new Color(0xdc, 0x14, 0x3c);
            w = 2.0f;
        } else if ("shape".equals(cls)) {
            c = new Color(0x1f, 0x77, 0xb4);
            w = 2.0f;
        }
        g.setColor(c);
        g.setStroke(dashed
                ? new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10.0f, new float[] {4.0f, 3.0f}, 0.0f)
                : new BasicStroke(w));
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
        style(cls, false);
        g.setColor(new Color(0x33, 0x33, 0x33));
        int w = g.getFontMetrics().stringWidth(s);
        double dx = "middle".equals(anchor) ? -w / 2.0 : "end".equals(anchor) ? -w : 0.0;
        g.drawString(s, (float) (x + dx), (float) y);
    }
}
