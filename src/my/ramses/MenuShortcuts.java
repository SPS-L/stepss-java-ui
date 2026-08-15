package my.ramses;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/**
 * Puts the menu bar's accelerators on the modifier this platform actually uses.
 *
 * <p>Every accelerator in RamsesUI.form was written as {@code CTRL_DOWN_MASK},
 * which is right on Windows and Linux and wrong on macOS, where menu shortcuts
 * are Command. All ten were therefore dead on a Mac.
 *
 * <p>Written as a sweep over the menu bar rather than as ten edits in the
 * generated block so that an accelerator added in the designer later is
 * corrected too, without anyone having to remember this exists.
 *
 * <p>Its own class, rather than a method on RamsesUI, so {@link ChromeCheck} can
 * exercise it without loading RamsesUI, which drags in Commons Exec and the
 * whole toolchain behind it.
 */
final class MenuShortcuts {

    private MenuShortcuts() {
    }

    /** Applies {@link #remap} with this platform's menu modifier. */
    static void applyPlatformModifier(JMenuBar bar) {
        remap(bar, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
    }

    /**
     * Rewrites every Control-based accelerator in {@code bar} onto
     * {@code menuMask}, in place.
     *
     * <p>Separated from {@link #applyPlatformModifier} so it can be run against
     * a mask this machine does not have. There is no other way to try the macOS
     * branch on Linux, and the bit arithmetic below is precisely the part that
     * would look right here and leave every shortcut on a Mac doing nothing.
     *
     * @param bar the menu bar to rewrite
     * @param menuMask this platform's menu modifier, Command on macOS
     */
    static void remap(JMenuBar bar, int menuMask) {
        if (menuMask == InputEvent.CTRL_DOWN_MASK) {
            return;
        }
        for (int m = 0; m < bar.getMenuCount(); m++) {
            JMenu menu = bar.getMenu(m);
            if (menu == null) {
                continue;
            }
            for (int i = 0; i < menu.getItemCount(); i++) {
                JMenuItem item = menu.getItem(i);
                if (item == null || item.getAccelerator() == null) {
                    continue;
                }
                KeyStroke stroke = item.getAccelerator();
                if ((stroke.getModifiers() & InputEvent.CTRL_DOWN_MASK) == 0) {
                    continue;  // a bare function key: F1 means F1 everywhere
                }
                // Keep only the modifiers that are still meant, and let the
                // platform mask supply the rest. Rebuilding from the extended
                // masks this way also drops the legacy CTRL bit that
                // KeyStroke.getKeyStroke sets alongside CTRL_DOWN_MASK; masking
                // against the deprecated constant is the other way to reach it,
                // and leaving it set is a Control the Mac still honours.
                int keep = stroke.getModifiers() & (InputEvent.SHIFT_DOWN_MASK
                        | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK);
                item.setAccelerator(KeyStroke.getKeyStroke(
                        stroke.getKeyCode(), keep | menuMask));
            }
        }
    }
}
