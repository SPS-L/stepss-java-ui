package my.ramses;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The strip along the bottom of the window: where the work is happening, what
 * engine is doing it, and whether anything is running.
 *
 * <p>What this is for. The application had 76 modal dialogs and nowhere at all
 * to say something quietly. During a run the only sign that anything was
 * happening was text scrolling in a console, and the working directory, which
 * decides where every file is read and written, was not shown anywhere in the
 * window. A status bar is the place both of those belong, and most of what the
 * dialogs currently interrupt for could live here instead.
 *
 * <p>Every state method marshals itself onto the EDT. The completion of a run
 * is noticed by a background thread polling for a lock file, so the honest
 * choice was either to make each of those call sites remember to hop, or to do
 * it once here. Doing it here also means a caller cannot get it wrong later.
 */
final class StatusBar extends JPanel {

    private final JLabel where = new JLabel();
    private final JLabel engine = new JLabel();
    private final JLabel state = new JLabel("Idle");
    private final JLabel elapsed = new JLabel();
    private final JProgressBar progress = new JProgressBar();
    private final Timer clock;

    private long startedAt;

    StatusBar() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel line = new JPanel();
        line.setLayout(new BoxLayout(line, BoxLayout.LINE_AXIS));
        line.setBorder(BorderFactory.createEmptyBorder(4, 10, 5, 10));

        quiet(where);
        quiet(engine);
        quiet(elapsed);
        state.setFont(state.getFont().deriveFont(Font.BOLD));

        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(90, 10));
        progress.setMaximumSize(new Dimension(90, 10));

        line.add(where);
        line.add(javax.swing.Box.createHorizontalGlue());
        line.add(engine);
        line.add(javax.swing.Box.createRigidArea(new Dimension(14, 0)));
        line.add(progress);
        line.add(javax.swing.Box.createRigidArea(new Dimension(8, 0)));
        line.add(state);
        line.add(javax.swing.Box.createRigidArea(new Dimension(8, 0)));
        line.add(elapsed);

        add(new JSeparator(SwingConstants.HORIZONTAL), BorderLayout.NORTH);
        add(line, BorderLayout.CENTER);

        clock = new Timer(200, event -> showElapsed());
        idle();
    }

    /** Where files are read and written. Empty until a directory is chosen. */
    void setWorkingDirectory(final File directory) {
        onSwing(() -> {
            if (directory == null) {
                where.setText("No working directory");
                where.setToolTipText(null);
            } else {
                where.setText(directory.getName());
                where.setToolTipText(directory.getAbsolutePath());
            }
        });
    }

    /** The engine banner, so which build is answering is never a guess. */
    void setEngine(final String description) {
        onSwing(() -> engine.setText(description == null ? "" : description));
    }

    void running(final String what) {
        onSwing(() -> {
            startedAt = System.currentTimeMillis();
            state.setText(what);
            state.setForeground(colour("Component.accentColor", "Label.foreground"));
            progress.setVisible(true);
            showElapsed();
            clock.start();
        });
    }

    /** Finished, with how long it took left on screen rather than cleared. */
    void finished(final String what) {
        settle(what, colour("Label.foreground", "Label.foreground"));
    }

    void failed(final String what) {
        settle(what, colour("Component.error.focusedBorderColor", "Label.foreground"));
    }

    void idle() {
        onSwing(() -> {
            clock.stop();
            progress.setVisible(false);
            state.setText("Idle");
            state.setForeground(colour("Label.disabledForeground", "Label.foreground"));
            elapsed.setText("");
        });
    }

    private void settle(final String what, final Color colour) {
        onSwing(() -> {
            clock.stop();
            progress.setVisible(false);
            showElapsed();
            state.setText(what);
            state.setForeground(colour);
        });
    }

    private void showElapsed() {
        if (startedAt == 0L) {
            elapsed.setText("");
            return;
        }
        double seconds = (System.currentTimeMillis() - startedAt) / 1000.0;
        elapsed.setText(String.format(java.util.Locale.ROOT,
                seconds < 60 ? "%.1f s" : "%.0f s", seconds));
    }

    private static void quiet(JLabel label) {
        Color disabled = UIManager.getColor("Label.disabledForeground");
        if (disabled != null) {
            label.setForeground(disabled);
        }
    }

    /** A theme colour, or a named fallback when this look and feel lacks it. */
    private static Color colour(String key, String fallbackKey) {
        Color found = UIManager.getColor(key);
        if (found != null) {
            return found;
        }
        Color fallback = UIManager.getColor(fallbackKey);
        return fallback != null ? fallback : Color.GRAY;
    }

    private static void onSwing(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
        } else {
            SwingUtilities.invokeLater(work);
        }
    }
}
