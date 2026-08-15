package my.ramses;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * A line across the top of the window for things the application wants to say
 * but has no business interrupting for.
 *
 * <p>There were 76 modal dialogs and no other way to speak. Some of them are
 * genuine questions and some report failures that have to be acknowledged, but
 * a good number were the application announcing that it had succeeded, which
 * is the one case where a dialog costs a click and returns nothing: the user
 * asked for the thing, the thing happened, and now they have to dismiss a box
 * saying so.
 *
 * <p>Not a toast. It stays until it is dismissed or replaced, because a
 * message that disappears on a timer is one a user can miss entirely, and
 * because "saved to <i>here</i>" is worth being able to re-read. The one
 * exception is {@link #confirm}, which fades after a while: a success notice
 * that outlives its usefulness becomes furniture.
 */
final class InlineBanner extends JPanel {

    private final JLabel message = new JLabel();
    private final Timer expiry;

    InlineBanner() {
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 8));
        setVisible(false);

        JButton dismiss = new JButton("Dismiss");
        dismiss.putClientProperty("JButton.buttonType", "borderless");
        dismiss.addActionListener(event -> clear());

        add(message, BorderLayout.CENTER);
        add(dismiss, BorderLayout.EAST);

        expiry = new Timer(12000, event -> clear());
        expiry.setRepeats(false);
    }

    /** Something worked. Says so, then gets out of the way. */
    void confirm(String text) {
        show(text, UIManager.getColor("Component.accentColor"), true);
    }

    /** Something is worth knowing but nothing is broken. */
    void warn(String text) {
        show(text, UIManager.getColor("Component.warning.focusedBorderColor"), false);
    }

    void clear() {
        onSwing(() -> {
            expiry.stop();
            setVisible(false);
            revalidate();
            repaint();
        });
    }

    private void show(final String text, final Color accent, final boolean fades) {
        onSwing(() -> {
            expiry.stop();
            message.setText(text);
            message.setFont(message.getFont().deriveFont(Font.PLAIN));
            Color edge = accent != null ? accent : UIManager.getColor("Label.foreground");
            // A rule down the leading edge rather than a filled panel: the
            // window already has a tab strip and a status bar competing for
            // the eye, and a block of colour across the top would outrank the
            // work it is commenting on.
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 1, 0, edge),
                    BorderFactory.createEmptyBorder(7, 9, 7, 8)));
            setVisible(true);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
            revalidate();
            repaint();
            if (fades) {
                expiry.start();
            }
        });
    }

    private static void onSwing(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
        } else {
            SwingUtilities.invokeLater(work);
        }
    }
}
