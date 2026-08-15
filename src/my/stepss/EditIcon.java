package my.stepss;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * The pencil on the "open this file in your editor" buttons.
 *
 * <p>Replaces {@code npp.png}, which was the Notepad++ product icon. That was
 * wrong twice over: the action opens whatever editor the OS has registered, so
 * on Linux and macOS the button named a program the user does not have, and a
 * 24x24 raster was the one blurry element on any HiDPI display.
 *
 * <p>Painted rather than loaded, for two reasons. It is resolution
 * independent, so it stays sharp at any display scale, and it takes its colour
 * from the look and feel instead of carrying its own, so it follows a theme
 * change without a second asset. Everything is expressed in a unit square and
 * scaled by {@link #size}, so one instance serves any size.
 */
public final class EditIcon implements Icon {

    /** The default: matches the 16px icons the platform themes ship. */
    public static final EditIcon SMALL = new EditIcon(16);

    private final int size;

    public EditIcon(int size) {
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
            // The body sits back a shade so the tip and the ferrule read as
            // separate parts at 16px, where an outline would close up.
            g.setColor(alpha(ink, 200));
            g.fill(shape(new double[][]{
                {2.6, 11.6}, {9.9, 4.3}, {11.9, 6.3}, {4.6, 13.6}}));
            g.setColor(ink);
            g.fill(shape(new double[][]{
                {2.0, 14.2}, {2.6, 11.6}, {4.6, 13.6}}));
            g.fill(shape(new double[][]{
                {10.6, 3.6}, {12.0, 2.2}, {14.0, 4.2}, {12.6, 5.6}}));
        } finally {
            g.dispose();
        }
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

    private static GeneralPath shape(double[][] points) {
        GeneralPath path = new GeneralPath(Path2D.WIND_NON_ZERO, points.length);
        path.moveTo(points[0][0], points[0][1]);
        for (int i = 1; i < points.length; i++) {
            path.lineTo(points[i][0], points[i][1]);
        }
        path.closePath();
        return path;
    }
}
