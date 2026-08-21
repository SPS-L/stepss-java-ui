package my.stepss;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
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
 * <p>A message lives exactly as long as the situation it describes. It stays
 * while the user reads it, and ends the moment they do something else, which
 * is what {@link #newAction} is for. The first version had no lifetime at all
 * and a warning outlived the problem it named, which is worse than a dialog:
 * a dialog is at least gone once dismissed. A confirmation additionally fades
 * on its own, so a success notice nobody acts on does not become furniture.
 */
final class InlineBanner extends JPanel {

    private final JLabel message = new JLabel();
    private final JButton actionButton = new JButton();
    private final Timer expiry;

    InlineBanner() {
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 8));
        setVisible(false);

        JButton dismiss = new JButton("Dismiss");
        dismiss.putClientProperty("JButton.buttonType", "borderless");
        dismiss.addActionListener(event -> clear());

        actionButton.putClientProperty("JButton.buttonType", "borderless");
        actionButton.setVisible(false);

        // One panel rather than two BorderLayout slots: the action belongs
        // beside Dismiss, in reading order, and a message with no action must
        // leave the line looking exactly as it does today.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(actionButton);
        buttons.add(dismiss);

        add(message, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);

        expiry = new Timer(12000, event -> clear());
        expiry.setRepeats(false);
    }

    /** Something worked. Says so, then gets out of the way. */
    void confirm(String text) {
        show(text, UIManager.getColor("Component.accentColor"), true, null, null);
    }

    /** Something is worth knowing but nothing is broken. */
    void warn(String text) {
        show(text, UIManager.getColor("Component.warning.focusedBorderColor"),
                false, null, null);
    }

    /**
     * Something is worth knowing and there is one obvious thing to do about
     * it.
     *
     * <p>Accented and fading, like {@link #confirm} rather than
     * {@link #warn}, because an available update is neither a success nor a
     * problem, and a notice nobody acts on should not become furniture.
     *
     * @param actionLabel the button's text
     * @param action      what the button does, run on the EDT
     */
    void notice(String text, String actionLabel, Runnable action) {
        show(text, UIManager.getColor("Component.accentColor"), true, actionLabel, action);
    }

    /**
     * The user has begun doing something else, so whatever is on the banner is
     * now out of date.
     *
     * <p>This exists because the first version had no lifetime at all. A
     * message stayed until it was dismissed, so "No system data files are
     * loaded" was still sitting above the tabs after the files had been loaded
     * and the simulation had run: the status bar said the run had finished
     * while the banner said it could not start. A banner reports the outcome
     * of the last thing that was asked for, and asking for something else has
     * to end it.
     *
     * <p>Clearing outright is only safe because of <em>when</em> this is
     * called: on a mouse or key press, which strictly precedes the action
     * event that a click on the same control generates. A message raised by
     * that action therefore lands after this has run. Move the caller to the
     * action event instead and every warning is cleared on its way out of the
     * click that raised it, so none of them is ever seen.
     */
    void newAction() {
        clear();
    }

    void clear() {
        onSwing(() -> {
            expiry.stop();
            setVisible(false);
            revalidate();
            repaint();
        });
    }

    /**
     * The message as one line, with every run of whitespace as a single space.
     *
     * <p>Why this is needed. A {@code JLabel} given plain text draws one line
     * and simply drops a {@code \n}, joining the words either side of it: the
     * small-signal refusal came out as "given $SCHEME DE and$OMEGA_REF SYN, so
     * the reason is elsewhere: usually a systemwith more states". The messages
     * carry newlines because they are wrapped for a dialog, and several are
     * shared with one, so the wrapping is not the thing to remove.
     *
     * <p>Replaced with a space rather than deleted, which is the whole point,
     * and runs collapsed so a blank line between paragraphs does not become a
     * gap. The alternative - wrapping the text in {@code <html>} and turning
     * newlines into {@code <br>} - would let the banner grow to any height
     * above the tabs, and would hand every message's punctuation to an HTML
     * parser.
     */
    static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private void show(final String text, final Color accent, final boolean fades,
                      final String actionLabel, final Runnable action) {
        onSwing(() -> {
            expiry.stop();
            message.setText(oneLine(text));
            message.setFont(message.getFont().deriveFont(Font.PLAIN));
            // Unconditionally, before anything is added: every message decides
            // its own action, and a button left over from the previous one
            // would offer to do something unrelated to what is now on the line.
            for (ActionListener listener : actionButton.getActionListeners()) {
                actionButton.removeActionListener(listener);
            }
            if (actionLabel == null) {
                actionButton.setVisible(false);
            } else {
                actionButton.setText(actionLabel);
                actionButton.addActionListener(event -> {
                    clear();
                    action.run();
                });
                actionButton.setVisible(true);
            }
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

    // Package-private windows into the action button, for UpdateHarness. The
    // button is otherwise private, and a test that reached it by walking
    // getComponents() would pass while asserting nothing about the contract.
    boolean actionButtonVisibleForTests() {
        return actionButton.isVisible();
    }

    String actionButtonTextForTests() {
        return actionButton.getText();
    }

    void actionButtonClickForTests() {
        actionButton.doClick();
    }
}
