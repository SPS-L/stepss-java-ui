package my.stepss.curves;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Run-time observables, drawn as the engine writes them.
 *
 * <p>The engine no longer plots: it writes {@code temp_display.cur} and
 * flushes on the {@code $GP_REFRESH_RATE} cadence. This tails that file on a
 * scheduled thread, parses there, and hands the EDT finished snapshots.
 *
 * <p>One window per run. A new run opens a new one and leaves the previous one
 * behind as a static chart, which is what makes two runs comparable. At the end
 * of a run the window stops polling and stays open: the live view becomes the
 * post-run view of the same observables.
 */
public final class LiveCurveWindow extends JFrame {

    /** Never poll faster than this, whatever the settings file says. */
    private static final long MIN_POLL_MS = 100L;

    private final File cur;
    private final CurTail tail;
    private final JPanel stack = new JPanel();
    private final JLabel status = new JLabel(" ");
    private final List<CurvePanel> panels = new ArrayList<CurvePanel>();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread t = new Thread(runnable, "live-curve-poller");
                t.setDaemon(true);
                return t;
            });

    private LiveModel model;
    private String refusal;
    private int emptyPolls;
    private boolean built;
    private boolean stopped;
    /**
     * Lines read before the header could be parsed.
     *
     * <p>The engine issues the header as several separate Fortran writes and
     * flushes once at the end, so a poll can land on a file holding only part
     * of it. Deciding from one batch would let a startup race present as
     * "this engine wrote no run-time header", which accuses the user of
     * running an old engine. Lines accumulate here until something proves the
     * header complete, and the data rows among them are replayed once it is.
     */
    private final List<String> pending = new ArrayList<String>();

    /**
     * @param cur the run's temp_display.cur, which need not exist yet
     * @return the window, so the caller can tell it the run has ended
     */
    public static LiveCurveWindow open(Component parent, File cur, String title) {
        LiveCurveWindow window = new LiveCurveWindow(cur, title);
        my.stepss.WindowCascade.track(window, parent);
        window.setVisible(true);
        window.start();
        return window;
    }

    private LiveCurveWindow(File cur, String title) {
        super(title);
        this.cur = cur;
        this.tail = new CurTail(cur);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        stack.setLayout(new GridLayout(0, 1));
        status.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        add(stack, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setSize(720, 560);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                poller.shutdownNow();
            }
        });
    }

    /** The poll period for a header refresh rate in seconds. */
    static long pollMillis(double refreshSeconds) {
        long ms = Math.round(refreshSeconds * 1000.0);
        return Math.max(MIN_POLL_MS, ms);
    }

    /**
     * Whether a failed header parse over {@code seen} is a verdict or just
     * impatience.
     *
     * <p>The header is contiguous and comes first, so any line that is neither
     * blank nor a comment proves no more header is coming and a failure is
     * real. Until one arrives, a failure means the poll landed between the
     * engine's header writes and its single flush, which is a race rather than
     * a fault and must not be reported as an unsupported engine.
     *
     * <p>Package-private and static so it can be checked without a file, a
     * poller or a display.
     */
    static boolean headerFailureIsFinal(List<String> seen) {
        for (String line : seen) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return true;
            }
        }
        return false;
    }

    private void start() {
        // One second until the header says otherwise, which is also the
        // engine's own default, so the common case never reschedules.
        poller.scheduleWithFixedDelay(this::tick, 0L, 1000L, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops polling after one last read, and leaves the window open.
     *
     * <p>The final read happens after the process has exited so the last flush
     * lands: the engine writes up to the stop time and flushes on exit, and a
     * poller shut down on the exit signal alone loses whatever arrived since
     * the previous tick.
     */
    public void finish() {
        // Never throws, and that is load-bearing rather than defensive habit.
        // Closing the window shuts the poller down, so a user who closes the
        // live view before the run ends leaves this executor terminated. The
        // caller is the run watcher thread, which calls this and then re-enables
        // the Run button through invokeLater: a RejectedExecutionException here
        // would kill that thread before it got there and leave the button
        // disabled for the rest of the session, with no error a user could act
        // on. The isShutdown check handles the common case and the catch handles
        // losing the race with a close, because the two cannot be made atomic.
        if (poller.isShutdown()) {
            return;
        }
        try {
            poller.execute(() -> {
                tick();
                stopped = true;
                poller.shutdown();
                SwingUtilities.invokeLater(() -> setTitle(getTitle() + " (finished)"));
            });
        } catch (java.util.concurrent.RejectedExecutionException windowClosed) {
            // The window went away between the check above and this call. There
            // is nothing left to read and nothing left to freeze.
        }
    }

    /** Disposes the window and its poller. */
    public void close() {
        poller.shutdownNow();
        dispose();
    }

    /**
     * One poll, on the poller thread. Never touches Swing except through
     * {@code invokeLater}.
     */
    private void tick() {
        if (stopped || refusal != null) {
            return;
        }
        try {
            List<String> lines = tail.poll();
            if (tail.truncatedSinceLastPoll()) {
                // status='replace' from a re-run in the same directory. The
                // header is about to arrive again, so throw the old run away
                // rather than drawing the two on one axis. The panels stay:
                // a re-run in the same window cannot have a different
                // observable set, because Task 7 gives every run its own
                // window and stops this one's poller before that.
                pending.clear();
                if (model != null) {
                    model.reset();
                }
            }
            if (lines.isEmpty()) {
                if (model == null && ++emptyPolls == 5) {
                    say("The engine has not written any run-time observable data yet.");
                }
                return;
            }
            if (model == null) {
                pending.addAll(lines);
                try {
                    model = new LiveModel(CurHeader.parse(pending));
                } catch (CurHeader.Unsupported unsupported) {
                    // Before the first data row this means "the header is not
                    // all here yet", which is a startup race rather than a
                    // fault: the engine issues the header as several writes and
                    // flushes once at the end. Refusing here would tell the
                    // user their engine is too old on the strength of a poll
                    // that landed mid-write. After a data row there is no more
                    // header coming, so the same failure is real.
                    if (headerFailureIsFinal(pending)) {
                        refuse(unsupported.getMessage());
                    }
                    return;
                }
                SwingUtilities.invokeLater(this::buildPanels);
                // The data rows that arrived while the header was still
                // incomplete, replayed in order so nothing is lost.
                lines = new ArrayList<String>(pending);
                pending.clear();
            }
            model.accept(lines);
            final List<CurveData> snapshots = new ArrayList<CurveData>();
            for (int i = 0; i < model.panelCount(); i++) {
                snapshots.add(model.snapshot(i));
            }
            final int samples = model.samples();
            final int skipped = model.skippedRows();
            SwingUtilities.invokeLater(() -> publish(snapshots, samples, skipped));
        } catch (IOException ex) {
            refuse("temp_display.cur could not be read: " + ex.getMessage());
        }
    }

    /** On the EDT: one panel per observable, once the header is known. */
    private void buildPanels() {
        if (built) {
            return;
        }
        built = true;
        for (int i = 0; i < model.panelCount(); i++) {
            CurvePanel panel = new CurvePanel();
            panel.setAxes(model.axesOf(i));
            panels.add(panel);
            stack.add(panel);
        }
        stack.revalidate();
    }

    /** On the EDT: the snapshots the poller finished. */
    private void publish(List<CurveData> snapshots, int samples, int skipped) {
        buildPanels();
        for (int i = 0; i < panels.size() && i < snapshots.size(); i++) {
            panels.get(i).setData(snapshots.get(i));
        }
        StringBuilder text = new StringBuilder();
        text.append(samples).append(" samples");
        // Once, at the end of the line, rather than a per-row storm: a torn
        // last line is normal and would otherwise warn every second.
        if (skipped > 0) {
            text.append("    ").append(skipped).append(" unreadable row(s) skipped");
        }
        status.setText(text.toString());
    }

    private void refuse(String why) {
        refusal = why;
        SwingUtilities.invokeLater(() -> {
            stack.removeAll();
            JLabel message = new JLabel("<html><body style='width:420px'>"
                    + escape(why) + "</body></html>");
            message.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            message.setVerticalAlignment(javax.swing.SwingConstants.TOP);
            stack.add(message);
            stack.revalidate();
            stack.repaint();
            status.setText(cur.getAbsolutePath());
        });
        poller.shutdown();
    }

    private void say(String text) {
        SwingUtilities.invokeLater(() -> status.setText(text));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
