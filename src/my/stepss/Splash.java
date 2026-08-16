package my.stepss;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.geom.Rectangle2D;

/**
 * The startup card, drawn on the window the JVM puts up before this code runs.
 *
 * <p>Java's own splash rather than a JWindow, and the reason is the whole
 * design: {@code initRamses()} calls {@code Toolchain.extractAll()} on the EDT
 * on every launch, so a Swing window would be unable to repaint for exactly
 * the stretch the splash exists to cover. Its status line would freeze on its
 * first message. {@link SplashScreen#update()} paints a window the JVM owns,
 * outside Swing's repaint pipeline, so it works when called from a busy EDT.
 *
 * <p>Everything here degrades to nothing. {@link SplashScreen#getSplashScreen}
 * returns null unless the JVM was launched with a splash, which is the case
 * when the main class is run from an IDE, so {@link #open} returns null and
 * every caller treats that as "no splash" rather than as a failure.
 *
 * <h2>The surface says how big it is, and at what scale</h2>
 *
 * <p>Nothing here assumes the card is 460x250 device pixels, because on a high
 * density display it is not. Measured on JDK 21: when the JVM picks the
 * {@code @2x} card, {@link SplashScreen#getSize()} still reports the logical
 * 460x250, and {@link SplashScreen#createGraphics()} hands back a
 * {@code Graphics2D} already scaled by two. So every coordinate below is in
 * user space and reads the same at either density, and the one thing that must
 * change with the scale is which lockup rendering is asked for: the surface has
 * 760 device pixels to spend across the lockup, and handing it the 380 would
 * enlarge a small raster onto exactly the screens where that is most visible,
 * which is what {@link Branding} ships three renderings to avoid.
 */
final class Splash {

    /** The lockup's top edge, and the side margin, in user space. */
    private static final int LOCKUP_TOP = 28;
    private static final int MARGIN = 24;

    /** Baselines and the hairline rule, measured up from the card's bottom. */
    private static final int CREATORS_UP = 58;
    private static final int RULE_UP = 40;
    private static final int FOOTER_UP = 18;

    private final SplashScreen screen;
    private final boolean dark;
    private final String version;
    private volatile String status = "";

    private Splash(SplashScreen screen, boolean dark, String version) {
        this.screen = screen;
        this.dark = dark;
        this.version = version;
    }

    /**
     * Paints the card and returns a handle on it, or null when this JVM has no
     * splash to paint.
     *
     * @param dark    whether the dark theme is in force
     * @param version the running version, shown bottom left
     */
    static Splash open(boolean dark, String version) {
        SplashScreen screen = SplashScreen.getSplashScreen();
        if (screen == null) {
            return null;
        }
        Splash splash = new Splash(screen, dark, version);
        splash.status("");
        return splash;
    }

    /**
     * Reports what startup is doing now.
     *
     * <p>Takes a tool id, as {@code Toolchain.extractAll} hands it out, and
     * does the wording here so Toolchain stays free of presentation.
     *
     * @param toolId a tool id such as "ramses", or "" for no status at all
     */
    void status(String toolId) {
        this.status = toolId.isEmpty() ? "Starting STEPSS" : "Extracting " + toolId;
        paint();
    }

    /**
     * Dismisses the card.
     *
     * <p>Rarely needed: the JVM closes the splash by itself as soon as the
     * first window is displayed, which on the normal path is the main frame.
     * This exists for the first run, where the splash has to be gone before
     * the licence agreement rather than behind it.
     */
    void close() {
        try {
            screen.close();
        } catch (IllegalStateException alreadyClosed) {
            // Closed by the JVM when a window appeared. Nothing to do, and
            // nothing worth logging: this is the ordinary end of its life.
        }
    }

    private void paint() {
        Graphics2D g;
        try {
            g = screen.createGraphics();
        } catch (IllegalStateException alreadyClosed) {
            // The frame became visible while extraction was still reporting.
            // The card is gone; the remaining messages have nowhere to go.
            return;
        }
        try {
            // The surface, not the constants that made it: a logical size and
            // a device scale, both taken from the thing being drawn on.
            Dimension size = screen.getSize();
            render(g, size.width, size.height, dark, version, status);
            screen.update();
        } catch (IllegalStateException alreadyClosed) {
            // update() races the JVM's own close for the same reason
            // createGraphics() does. Same answer: the card is gone.
        } finally {
            g.dispose();
        }
    }

    /**
     * Draws the whole card into {@code g}, in user space.
     *
     * <p>Separate from {@link #paint} so it can be rendered into a
     * {@link java.awt.image.BufferedImage} at a chosen scale, which is the only
     * way to look at what this produces: a real splash surface cannot be read
     * back.
     *
     * @param w      the card's width in user space
     * @param h      its height in user space
     * @param status the line shown bottom right, already worded
     */
    static void render(Graphics2D g, int w, int h, boolean dark,
                       String version, String status) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color background = dark ? new Color(0x1E2227) : Color.WHITE;
        Color ink = dark ? new Color(0xE6E9EE) : new Color(0x24292F);
        Color quiet = dark ? new Color(0x9AA4B2) : new Color(0x6B7280);
        Color rule = dark ? new Color(0x343A42) : new Color(0xD8DEE4);

        // The whole card every time, not just the status line: the base
        // image is the light card, so a dark launch has to cover it, and
        // repainting one strip would leave the previous status underneath.
        g.setColor(background);
        g.fillRect(0, 0, w, h);
        g.setColor(rule);
        g.drawRect(0, 0, w - 1, h - 1);

        // The rendering the surface can actually show, drawn at the layout
        // width so it lands 1:1 rather than enlarged. Falls back to the base
        // width if a density is missing from the jar, which leaves a soft
        // lockup rather than no lockup; ChromeCheck is what stops it happening.
        int lockupWidth = Branding.lockupWidthFor(g.getTransform().getScaleX());
        Image lockup = Branding.lockupImage(dark, lockupWidth);
        if (lockup == null && lockupWidth != Branding.LOCKUP_WIDTH) {
            lockup = Branding.lockupImage(dark, Branding.LOCKUP_WIDTH);
        }
        if (lockup != null) {
            // The same 28px offset tools/MakeSplash.java uses, so the dark
            // card and the light base image place the lockup identically.
            int drawnHeight = Math.round(Branding.LOCKUP_WIDTH
                    * lockup.getHeight(null) / (float) lockup.getWidth(null));
            g.drawImage(lockup, (w - Branding.LOCKUP_WIDTH) / 2, LOCKUP_TOP,
                    Branding.LOCKUP_WIDTH, drawnHeight, null);
        }

        Font base = g.getFont();
        g.setFont(base.deriveFont(Font.PLAIN, 12f));
        g.setColor(ink);
        centred(g, w, "Creators: Petros Aristidou and Thierry Van Cutsem",
                h - CREATORS_UP);

        g.setColor(rule);
        g.drawLine(MARGIN, h - RULE_UP, w - MARGIN, h - RULE_UP);

        g.setFont(base.deriveFont(Font.PLAIN, 11f));
        g.setColor(quiet);
        g.drawString(version, MARGIN, h - FOOTER_UP);
        int statusWidth = g.getFontMetrics().stringWidth(status);
        g.drawString(status, w - MARGIN - statusWidth, h - FOOTER_UP);
    }

    private static void centred(Graphics2D g, int cardWidth, String text, int y) {
        Rectangle2D bounds = g.getFontMetrics().getStringBounds(text, g);
        g.drawString(text, (int) ((cardWidth - bounds.getWidth()) / 2), y);
    }
}
