package my.stepss;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Window;

/**
 * Places a non-modal results window clear of the ones already open.
 *
 * <p>Without it every window is centred on the main frame, so the second lands
 * exactly over the first and pressing Run again looks like it did nothing. That
 * matters most where a second window is opened precisely in order to compare it
 * with the first, which is true of both callers.
 *
 * <p>Extracted from {@code SsaResultsWindow}, which had it privately, when the
 * diagram window needed the same behaviour. Two copies of a static counter is
 * two counters, and each would step around only its own kind of window.
 *
 * <p>Touched only from the event dispatch thread, which is where every caller
 * opens a window from.
 */
public final class WindowCascade {

    /** Enough of an offset to see the window underneath, and its title. */
    private static final int CASCADE_STEP = 30;

    /** Where the cascade returns to the top left rather than marching on. */
    private static final int CASCADE_WRAP = 8;

    /**
     * How many tracked windows are on screen. Counted down again as they close,
     * so a long session does not walk off the screen edge.
     */
    private static int onScreen;

    private WindowCascade() {
    }

    /**
     * Positions {@code window}, counts it in, and arranges for it to be counted
     * back out when it closes.
     *
     * <p>Call after the window is sized and before it is shown: the offset is
     * clamped against the window's own width and height, which are zero until
     * it has been packed.
     *
     * @param window the window to place
     * @param parent what to centre it on before offsetting, may be null
     */
    public static void track(Window window, Component parent) {
        window.setLocationRelativeTo(parent);
        cascade(window);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                onScreen = Math.max(0, onScreen - 1);
            }
        });
        onScreen++;
    }

    /**
     * Steps this window down and to the right of the ones already up.
     *
     * <p>Clamped to the screen the window is on, because a window whose title
     * bar is past the bottom edge cannot be moved back.
     */
    private static void cascade(Window window) {
        int rank = onScreen % CASCADE_WRAP;
        if (rank == 0) {
            return;
        }
        Rectangle screen = screenBounds(window);
        int shift = rank * CASCADE_STEP;
        int x = Math.min(window.getX() + shift,
                screen.x + screen.width - window.getWidth());
        int y = Math.min(window.getY() + shift,
                screen.y + screen.height - window.getHeight());
        window.setLocation(Math.max(screen.x, x), Math.max(screen.y, y));
    }

    /** The screen this window is on, or the default one before it has a peer. */
    private static Rectangle screenBounds(Window window) {
        java.awt.GraphicsConfiguration config = window.getGraphicsConfiguration();
        if (config == null) {
            config = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        }
        return config.getBounds();
    }
}
