package my.stepss;

import java.awt.Color;
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
 */
final class Splash {

    /** The card's logical size, matching tools/MakeSplash.java. */
    private static final int W = 460;
    private static final int H = 250;

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
            g.fillRect(0, 0, W, H);
            g.setColor(rule);
            g.drawRect(0, 0, W - 1, H - 1);

            Image lockup = Branding.lockupImage(dark, Branding.LOCKUP_WIDTH);
            if (lockup != null) {
                // The same 28px offset tools/MakeSplash.java uses, so the dark
                // card and the light base image place the lockup identically.
                g.drawImage(lockup, (W - Branding.LOCKUP_WIDTH) / 2, 28, null);
            }

            Font base = g.getFont();
            g.setFont(base.deriveFont(Font.PLAIN, 12f));
            g.setColor(ink);
            centred(g, "Creators: Petros Aristidou and Thierry Van Cutsem", 192);

            g.setColor(rule);
            g.drawLine(24, 210, W - 24, 210);

            g.setFont(base.deriveFont(Font.PLAIN, 11f));
            g.setColor(quiet);
            g.drawString(version, 24, 232);
            int statusWidth = g.getFontMetrics().stringWidth(status);
            g.drawString(status, W - 24 - statusWidth, 232);

            screen.update();
        } catch (IllegalStateException alreadyClosed) {
            // update() races the JVM's own close for the same reason
            // createGraphics() does. Same answer: the card is gone.
        } finally {
            g.dispose();
        }
    }

    private static void centred(Graphics2D g, String text, int y) {
        Rectangle2D bounds = g.getFontMetrics().getStringBounds(text, g);
        g.drawString(text, (int) ((W - bounds.getWidth()) / 2), y);
    }
}
