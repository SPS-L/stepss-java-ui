package my.stepss;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * The question mark on the buttons that open the documentation.
 *
 * <p>Painted for the reasons {@link EditIcon} is painted: it stays sharp at any
 * display scale and takes its colour from the look and feel, so it follows a
 * theme change without a second asset. Everything is expressed in a unit square
 * and scaled by {@link #size}, so one instance serves any size.
 *
 * <p>Drawn rather than set as the button's text, which would have been shorter.
 * A "?" label is laid out and weighted by whatever font the platform is using,
 * so beside the painted pencil on the same row it read as a different kind of
 * control. A glyph of the same construction sits at the same weight as the
 * pencil in either theme.
 */
public final class HelpIcon implements Icon {

    /** The default: matches the 16px icons the platform themes ship. */
    public static final HelpIcon SMALL = new HelpIcon(16);

    private final int size;

    public HelpIcon(int size) {
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);
            g.scale(size / 16.0, size / 16.0);

            Color ink = foreground(c);
            // The ring sits back a shade, the way the pencil's body does, so
            // the mark inside it stays what the eye lands on at 16px.
            g.setColor(alpha(ink, 140));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Ellipse2D.Double(1.5, 1.5, 13.0, 13.0));

            g.setColor(ink);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.draw(mark());
            g.fill(new Ellipse2D.Double(7.2, 11.3, 1.7, 1.7));
        } finally {
            g.dispose();
        }
    }

    /**
     * The hook and its stem, as one stroked path: two thirds of a small
     * ellipse starting low on the left, over the top, then curving back in and
     * down to where the dot picks it up.
     */
    private static GeneralPath mark() {
        GeneralPath path = new GeneralPath(Path2D.WIND_NON_ZERO);
        path.append(new Arc2D.Double(5.2, 3.4, 5.6, 5.2, 200, -240, Arc2D.OPEN),
                false);
        path.curveTo(9.7, 8.9, 8.1, 9.2, 8.1, 10.2);
        return path;
    }

    /**
     * The look and feel's own text colour, so the icon sits at the same weight
     * as the labels beside it in either theme. Falls back to the component's
     * foreground when asked outside a running look and feel, which is what the
     * headless checks do.
     */
    private static Color foreground(Component c) {
        Color ui = UIManager.getColor("Label.foreground");
        if (ui != null) {
            return ui;
        }
        return c != null && c.getForeground() != null ? c.getForeground() : Color.DARK_GRAY;
    }

    private static Color alpha(Color base, int a) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }
}
