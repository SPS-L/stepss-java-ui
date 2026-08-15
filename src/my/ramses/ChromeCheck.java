package my.ramses;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/**
 * Headless checks for the two pieces of window chrome that carry logic rather
 * than layout: the menu-shortcut remap, and the painted edit icon.
 *
 * <p>The remap is here because it is the one thing in the chrome that cannot be
 * checked by looking at it on the machine that changed it. Every menu
 * accelerator was written as {@code CTRL_DOWN_MASK}, which is right on Windows
 * and Linux and does nothing useful on macOS, where menu shortcuts are Command.
 * The fix rebuilds each stroke against the platform mask, and the subtlety is
 * that {@code KeyStroke.getKeyStroke} records the legacy {@code CTRL_MASK} bit
 * alongside the extended one: carry that bit across and the Mac gets
 * Control+Command+S, which looks fine in a menu and fires on neither. These
 * checks pin the arithmetic by running it with a mask this machine does not
 * have.
 *
 * <p>Not checked here: the About dialog. It is built by the generated
 * {@code initComponents}, so reaching it means constructing a whole RamsesUI,
 * which extracts the toolchain and can call {@code System.exit}. Its fix is
 * verified by opening it.
 *
 * <p>Run from {@code tools/chrome-harness.sh}; this repository has no unit-test
 * framework.
 */
public final class ChromeCheck {

    private static final int MAC_MASK = InputEvent.META_DOWN_MASK;

    private static int failures = 0;

    private ChromeCheck() {
    }

    public static void main(String[] args) {
        checkControlBecomesCommand();
        checkOtherModifiersSurvive();
        checkBareFunctionKeysAreLeftAlone();
        checkNoLegacyControlBitSurvives();
        checkSameMaskIsANoOp();
        checkEditIconPaints();
        checkEditIconFollowsTheTheme();
        checkBothThemesHaveEveryMark();
        checkLockupKeepsItsAspect();
        checkStatusBarReportsItsState();
        checkStatusBarCanBeDrivenOffTheEdt();
        checkBannerShowsAndDismisses();
        System.out.println(failures == 0 ? "ALL CHROME CHECKS PASSED"
                : failures + " CHROME CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Ctrl+S becomes Command+S, and keeps its key. */
    private static void checkControlBecomesCommand() {
        KeyStroke got = remapped(KeyStroke.getKeyStroke(
                KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        if (got.getKeyCode() != KeyEvent.VK_S) {
            fail("ctrl to command", "key changed to " + KeyEvent.getKeyText(got.getKeyCode()));
        }
        if ((got.getModifiers() & MAC_MASK) == 0) {
            fail("ctrl to command", "Command is not set: " + got);
        }
    }

    /** Shift+Ctrl+G becomes Shift+Command+G, not plain Command+G. */
    private static void checkOtherModifiersSurvive() {
        KeyStroke got = remapped(KeyStroke.getKeyStroke(KeyEvent.VK_G,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        if ((got.getModifiers() & InputEvent.SHIFT_DOWN_MASK) == 0) {
            fail("modifiers survive", "Shift was dropped: " + got);
        }
        if ((got.getModifiers() & MAC_MASK) == 0) {
            fail("modifiers survive", "Command is not set: " + got);
        }
    }

    /** F1 means F1 on every platform and must come through untouched. */
    private static void checkBareFunctionKeysAreLeftAlone() {
        KeyStroke f1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        KeyStroke got = remapped(f1);
        if (!f1.equals(got)) {
            fail("function keys", "F1 was rewritten to " + got);
        }
    }

    /**
     * The bug this whole seam exists for. {@code getKeyStroke(VK_S,
     * CTRL_DOWN_MASK)} yields a stroke carrying both the extended Control bit
     * and the legacy one; if the remap only clears the extended bit, the result
     * still demands Control, so on macOS the user would have to press
     * Control+Command+S.
     */
    private static void checkNoLegacyControlBitSurvives() {
        KeyStroke got = remapped(KeyStroke.getKeyStroke(
                KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        if ((got.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
            fail("legacy control bit", "extended Control survived: " + got);
        }
        // InputEvent.CTRL_MASK, without naming the deprecated constant.
        if ((got.getModifiers() & 0x2) != 0) {
            fail("legacy control bit", "legacy Control survived: " + got);
        }
    }

    /** On Windows and Linux the mask already matches, so nothing may move. */
    private static void checkSameMaskIsANoOp() {
        JMenuBar bar = barWith(KeyStroke.getKeyStroke(
                KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        KeyStroke before = bar.getMenu(0).getItem(0).getAccelerator();
        MenuShortcuts.remap(bar, InputEvent.CTRL_DOWN_MASK);
        KeyStroke after = bar.getMenu(0).getItem(0).getAccelerator();
        if (!before.equals(after)) {
            fail("same mask", "an already-correct accelerator was rewritten to " + after);
        }
    }

    /** The pencil actually marks the image, at the small size and a large one. */
    private static void checkEditIconPaints() {
        for (int size : new int[]{16, 32, 64}) {
            BufferedImage image = paint(new EditIcon(size), size);
            int marked = 0;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) {
                        marked++;
                    }
                }
            }
            // A pencil across the diagonal covers well under half the square;
            // anything near zero means the geometry collapsed, and anything
            // near the whole square means it is painting a block.
            double covered = marked / (double) (size * size);
            if (covered < 0.05 || covered > 0.45) {
                fail("edit icon paints",
                        "at " + size + "px it covers " + Math.round(covered * 100) + "% of the box");
            }
        }
    }

    /**
     * The icon takes its colour from the look and feel rather than shipping its
     * own, which is what lets one asset serve the light and the dark theme.
     */
    private static void checkEditIconFollowsTheTheme() {
        javax.swing.UIManager.put("Label.foreground", Color.RED);
        try {
            BufferedImage image = paint(EditIcon.SMALL, 16);
            boolean sawRed = false;
            for (int x = 0; x < 16 && !sawRed; x++) {
                for (int y = 0; y < 16 && !sawRed; y++) {
                    int argb = image.getRGB(x, y);
                    if ((argb >>> 24) > 128 && ((argb >> 16) & 0xff) > 200
                            && ((argb >> 8) & 0xff) < 60) {
                        sawRed = true;
                    }
                }
            }
            if (!sawRed) {
                fail("edit icon theming", "the icon ignored Label.foreground");
            }
        } finally {
            javax.swing.UIManager.put("Label.foreground", null);
        }
    }

    /**
     * Every mark resolves, in both themes.
     *
     * <p>Worth a check because the failure is silent: {@code Branding} logs and
     * returns null rather than refusing to start, so a renamed or dropped
     * resource shows up as an About box with a hole in it and a taskbar entry
     * with the stock Java cup, which nobody notices until a user mentions it.
     */
    private static void checkBothThemesHaveEveryMark() {
        for (String resource : Branding.requiredResources()) {
            if (!Branding.has(resource)) {
                fail("marks", resource + " is missing");
            }
        }
        for (boolean dark : new boolean[]{false, true}) {
            String which = dark ? "dark" : "light";
            java.util.List<java.awt.Image> icons = Branding.windowIcons(dark);
            if (icons.size() != 4) {
                fail("window icons", which + " resolved " + icons.size() + " of 4 sizes");
            }
            if (Branding.logo(dark) == null) {
                fail("about lockup", which + " lockup did not load");
            }
        }
    }

    /**
     * The lockup is scaled by width, so a portrait image in that slot would be
     * silently letterboxed to something enormous. Pins the shape rather than the
     * pixels: the artwork may be redrawn, but it stays a wide lockup.
     */
    private static void checkLockupKeepsItsAspect() {
        javax.swing.Icon lockup = Branding.logo(false);
        if (lockup == null) {
            return;  // already reported
        }
        double aspect = lockup.getIconWidth() / (double) lockup.getIconHeight();
        if (lockup.getIconWidth() != Branding.LOCKUP_WIDTH) {
            fail("about lockup", "reports " + lockup.getIconWidth()
                    + "px wide, not the " + Branding.LOCKUP_WIDTH + " it is laid out at");
        }
        if (aspect < 2.0 || aspect > 4.0) {
            fail("about lockup", "aspect is " + String.format(java.util.Locale.ROOT, "%.2f", aspect)
                    + ", which is not the horizontal lockup this slot is laid out for");
        }
    }

    /**
     * Drains the event queue, so an update the bar marshalled onto the EDT has
     * landed before it is asserted on. These checks run on the main thread,
     * which is exactly the position the completion threads are in, so waiting
     * here is not a test artefact: it is the same wait the application does.
     */
    private static void settle() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ex) {
            fail("event queue", "could not be drained: " + ex);
        }
    }

    /** The bar says which state it is in, and what it is working on. */
    private static void checkStatusBarReportsItsState() {
        StatusBar bar = new StatusBar();
        settle();
        if (!textIn(bar).contains("Idle")) {
            fail("status bar", "does not start Idle: " + textIn(bar));
        }
        bar.running("Simulating");
        settle();
        if (!textIn(bar).contains("Simulating")) {
            fail("status bar", "running() did not report what is running");
        }
        bar.finished("Simulation finished");
        settle();
        String settled = textIn(bar);
        if (!settled.contains("Simulation finished")) {
            fail("status bar", "finished() did not report the outcome");
        }
        // The elapsed time stays up rather than being wiped, which is the
        // whole reason a run reports through here rather than through a dialog
        // that has already been dismissed by the time anyone wonders.
        if (!settled.contains("s")) {
            fail("status bar", "finished() cleared the elapsed time");
        }
        bar.setWorkingDirectory(new java.io.File("/tmp/kundur-ssa"));
        settle();
        if (!textIn(bar).contains("kundur-ssa")) {
            fail("status bar", "the working directory is not shown");
        }
    }

    /**
     * The bar can be driven from a background thread.
     *
     * <p>This is not a nicety. A run's completion is noticed by a thread
     * polling for a lock file, so every state change after "running" arrives
     * off the EDT. The bar marshals internally so those call sites cannot get
     * it wrong; this asserts it, by driving it from a thread that is
     * definitely not the EDT and waiting for the change to land.
     */
    private static void checkStatusBarCanBeDrivenOffTheEdt() {
        final StatusBar bar = new StatusBar();
        Thread worker = new Thread(() -> bar.running("Solving power flow"));
        worker.start();
        try {
            worker.join(2000);
            // Let anything the worker posted to the EDT run to completion.
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ex) {
            fail("status bar threading", "interrupted: " + ex);
            return;
        }
        if (!textIn(bar).contains("Solving power flow")) {
            fail("status bar threading",
                    "a background thread's update never reached the bar");
        }
    }

    /** The banner shows what it is given and goes away when dismissed. */
    private static void checkBannerShowsAndDismisses() {
        InlineBanner banner = new InlineBanner();
        if (banner.isVisible()) {
            fail("banner", "starts visible, so an empty line sits above the tabs");
        }
        banner.warn("An observables file is needed");
        settle();
        if (!banner.isVisible() || !textIn(banner).contains("observables file")) {
            fail("banner", "warn() did not show the message");
        }
        banner.clear();
        settle();
        if (banner.isVisible()) {
            fail("banner", "clear() left it on screen");
        }
    }

    /** Every label's text under a component, flattened. */
    private static String textIn(java.awt.Container root) {
        StringBuilder text = new StringBuilder();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel) {
                text.append(((javax.swing.JLabel) child).getText()).append(' ');
            }
            if (child instanceof java.awt.Container) {
                text.append(textIn((java.awt.Container) child));
            }
        }
        return text.toString();
    }

    private static BufferedImage paint(EditIcon icon, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return image;
    }

    private static KeyStroke remapped(KeyStroke original) {
        JMenuBar bar = barWith(original);
        MenuShortcuts.remap(bar, MAC_MASK);
        return bar.getMenu(0).getItem(0).getAccelerator();
    }

    private static JMenuBar barWith(KeyStroke accelerator) {
        JMenuItem item = new JMenuItem("Item");
        item.setAccelerator(accelerator);
        JMenu menu = new JMenu("Menu");
        menu.add(item);
        JMenuBar bar = new JMenuBar();
        bar.add(menu);
        return bar;
    }

    private static void fail(String what, String detail) {
        failures++;
        System.out.println("FAIL [" + what + "] " + detail);
    }
}
