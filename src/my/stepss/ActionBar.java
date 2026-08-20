package my.stepss;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

/**
 * One row of actions along the bottom of a tab.
 *
 * <p>What this replaces: every tab laid its buttons out as whatever rows the
 * designer happened to wrap them into, two wide then three then one, with no
 * alignment between rows and no distinction between running a simulation and
 * clearing the console. Seventy-two buttons, all the same weight, so the one
 * reason each tab exists looked exactly like the button that throws its output
 * away.
 *
 * <p>A bar reads left to right in the order the work happens, with clearing and
 * discarding pushed to the far end past a gap, where a mis-click is a reach
 * rather than a neighbour.
 */
final class ActionBar {

    private final JPanel bar = new Bar();

    private ActionBar() {
        bar.setLayout(new BoxLayout(bar, BoxLayout.LINE_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // See Bar: the panel answers for its disabled children.
        ToolTipManager.sharedInstance().registerComponent(bar);
    }

    /**
     * A bar that shows the tooltip of a disabled button under the pointer.
     *
     * <p>Swing does not. A disabled component receives no mouse events at all,
     * so ToolTipManager never hears that the pointer is over it and nothing
     * appears. On these bars that inverts the help: five of Codegen's six
     * buttons and six of the Power Flow tab's eight start disabled, and being
     * disabled is precisely when someone wants to know what the button is for
     * and what would enable it.
     *
     * <p>The panel is the component the manager knows about, and it answers
     * for whichever child is under the pointer. Enabled children are left
     * alone and answer for themselves, so this changes nothing about the ones
     * that already worked.
     */
    private static final class Bar extends JPanel {

        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            Component under = getComponentAt(event.getPoint());
            if (under instanceof JComponent && under != this && !under.isEnabled()) {
                return ((JComponent) under).getToolTipText();
            }
            return null;
        }
    }

    static ActionBar create() {
        return new ActionBar();
    }

    /** Appends a control, with the usual spacing before it. */
    ActionBar add(JComponent control) {
        if (bar.getComponentCount() > 0) {
            bar.add(Box.createRigidArea(new Dimension(6, 0)));
        }
        control.setAlignmentY(Component.CENTER_ALIGNMENT);
        bar.add(control);
        return this;
    }

    /** A wider gap, for the seam between two kinds of action. */
    ActionBar separate() {
        bar.add(Box.createRigidArea(new Dimension(10, 0)));
        JSeparator rule = new JSeparator(SwingConstants.VERTICAL);
        rule.setMaximumSize(new Dimension(1, 22));
        bar.add(rule);
        bar.add(Box.createRigidArea(new Dimension(4, 0)));
        return this;
    }

    /** Everything added after this is pushed to the right-hand end. */
    ActionBar toTheEnd() {
        bar.add(Box.createHorizontalGlue());
        return this;
    }

    JPanel build() {
        return bar;
    }

    /**
     * Marks the one action a tab exists for.
     *
     * <p>Painted from the theme's own default-button colours rather than by
     * making it the root pane's default button. That would have been the
     * shorter route and it binds Enter to it, which on a tab full of text
     * fields means a filename typed and confirmed starts a simulation. The
     * weight is the point here; the keystroke is not.
     *
     * <p>A look and feel that does not define those colours leaves the button
     * alone rather than guessing, which is what the fallback path leaves
     * behind.
     */
    static void markPrimary(JButton button) {
        java.awt.Color background = UIManager.getColor("Button.default.background");
        java.awt.Color foreground = UIManager.getColor("Button.default.foreground");
        if (background == null || foreground == null) {
            return;
        }
        button.setBackground(background);
        button.setForeground(foreground);
    }
}
