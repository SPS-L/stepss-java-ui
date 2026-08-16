package my.stepss;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The licence a first run has to accept before anything else happens.
 *
 * <p>It is the engine's licence, not the interface's: STEPSS' two user
 * interfaces are Apache 2.0, while RAMSES is the property of the University of
 * Liege and free for non-commercial use only. That is why the body is
 * {@code ramsesLicense.txt} and why the subtitle names the component rather
 * than the application.
 *
 * <p>The terms themselves are deliberately not summarised on screen. The bus
 * and core caps are stated in the licence text and in stepss-docs
 * {@code getting-started/license.md}, which owns those facts; a friendly
 * one-liner here would be a third copy, free to drift from both.
 */
final class LicenseDialog {

    private LicenseDialog() {
    }

    /**
     * Shows the agreement and blocks until it is answered.
     *
     * <p>Called from {@code main} before the frame exists, so the dialog has
     * no parent and centres itself on screen. It is the first window a new
     * user ever sees from STEPSS, which is why it carries the lockup and the
     * application's own icons rather than a stock dialog's.
     *
     * @return true if accepted
     */
    static boolean accept(final boolean dark) {
        final boolean[] accepted = {false};
        Runnable show = () -> accepted[0] = build(dark);
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(show);
            } catch (Exception ex) {
                // A licence that cannot be presented cannot be accepted, and
                // continuing would mean running the engine under terms nobody
                // agreed to.
                Logger.getLogger(LicenseDialog.class.getName())
                        .log(Level.SEVERE, "Could not show the licence agreement", ex);
                return false;
            }
        }
        return accepted[0];
    }

    private static boolean build(boolean dark) {
        final boolean[] accepted = {false};
        final JDialog dialog = new JDialog((java.awt.Frame) null, "License Agreement", true);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setIconImages(Branding.windowIcons(dark));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JLabel lockup = new JLabel(Branding.logo(dark));
        lockup.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel title = new JLabel("License Agreement");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 4f));
        title.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("RAMSES simulation engine, University of Liege");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitle.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        header.add(lockup);
        header.add(Box.createVerticalStrut(14));
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        JEditorPane body = new JEditorPane("text/html", toHtml(read()));
        body.setEditable(false);
        body.setCaretPosition(0);
        // Horizontal NEVER, not ALWAYS: the licence is soft-wrapped prose, so
        // the bar this used to show could never scroll anything.
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Separator.foreground")));

        JPanel middle = new JPanel(new BorderLayout());
        middle.setBorder(BorderFactory.createEmptyBorder(0, 24, 8, 24));
        middle.add(scroll, BorderLayout.CENTER);

        JLabel footer = new JLabel("The other components' licences are under Help > About.");
        footer.setForeground(UIManager.getColor("Label.disabledForeground"));

        JButton accept = new JButton("Accept");
        JButton decline = new JButton("Decline");
        accept.addActionListener(event -> {
            accepted[0] = true;
            dialog.dispose();
        });
        decline.addActionListener(event -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(decline);
        buttons.add(accept);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 24, 18, 24));
        bottom.add(footer, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);

        dialog.getContentPane().add(header, BorderLayout.NORTH);
        dialog.getContentPane().add(middle, BorderLayout.CENTER);
        dialog.getContentPane().add(bottom, BorderLayout.SOUTH);

        // Escape declines. Closing the window declines. Neither may be taken
        // for acceptance: the flag is only ever set by the Accept button.
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JPanel.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().setDefaultButton(accept);

        dialog.setSize(660, 580);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return accepted[0];
    }

    /**
     * The bundled licence text, or a stand-in naming the fault.
     *
     * <p>The previous implementation wrapped the stream in a reader before
     * testing it for null, so a missing resource was a NullPointerException
     * during construction of the first window, and the null check that
     * followed was unreachable.
     */
    private static String read() {
        InputStream in = LicenseDialog.class.getResourceAsStream("ramsesLicense.txt");
        if (in == null) {
            Logger.getLogger(LicenseDialog.class.getName())
                    .log(Level.SEVERE, "ramsesLicense.txt is missing from the jar");
            return "The licence text is missing from this build. Do not accept it;"
                    + " report this instead.";
        }
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }
            } finally {
                reader.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(LicenseDialog.class.getName())
                    .log(Level.SEVERE, "Could not read ramsesLicense.txt", ex);
        }
        return text.toString();
    }

    /**
     * The licence as HTML: first line a title, numbered sections headings,
     * everything else paragraphs.
     *
     * <p>Deliberately forgiving. Text that matches none of the rules comes out
     * as paragraphs, so a reworded licence renders no worse than the plain
     * text area it replaces, and no future edit to the licence can leave a
     * user staring at nothing.
     */
    static String toHtml(String licence) {
        StringBuilder html = new StringBuilder(
                "<html><body style='font-family:sans-serif; margin:14px;'>");
        String[] lines = licence.split("\n", -1);
        boolean titleDone = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String escaped = escape(trimmed);
            if (!titleDone) {
                html.append("<h2>").append(escaped).append("</h2>");
                titleDone = true;
            } else if (trimmed.matches("^\\d+\\..*") && trimmed.length() < 60) {
                html.append("<h3>").append(escaped).append("</h3>");
            } else {
                html.append("<p>").append(escaped).append("</p>");
            }
        }
        return html.append("</body></html>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
