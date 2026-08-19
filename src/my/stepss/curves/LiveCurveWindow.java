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
    /**
     * The cadence before the header has been read, which is also the engine's
     * own {@code $GP_REFRESH_RATE} default, so the usual case never changes it.
     */
    private static final long DEFAULT_POLL_MS = 1000L;

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
    /**
     * The currently scheduled poll, held so it can be replaced once the
     * header's own refresh rate is known.
     */

    private volatile LiveModel model;
    private String refusal;
    private int emptyPolls;
    private boolean built;
    private boolean stopped;
    /**
     * Set by {@link #finish()} so the last tick stops waiting for more header.
     *
     * <p>Waiting is right while the engine might still be writing. Once it has
     * exited there is no more coming, so a header that still will not parse is
     * a verdict rather than a race, and the user is owed the reason.
     */
    private boolean lastTick;
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
        // Zero delay for the first read; every poll then schedules the next one
        // itself, at the cadence the header asks for. See pump().
        poller.schedule(this::pump, 0L, TimeUnit.MILLISECONDS);
    }

    /**
     * One poll, then the next scheduled at the interval the header asks for.
     *
     * <p>A self-rescheduling one-shot chain rather than
     * {@code scheduleWithFixedDelay} plus a cancel. The interval is only known
     * once the header has been read, and re-scheduling a fixed-delay task from
     * inside its own run needs a handle that the scheduling call has not yet
     * returned: the first poll fires at zero delay, so it can beat the
     * assignment of that handle, find it null, and skip the reschedule for the
     * life of the window. A user's {@code $GP_REFRESH_RATE} would then be
     * silently ignored, some of the time, depending on how fast the engine
     * wrote its header. Deciding the next delay at the end of each poll needs
     * no handle at all, so the race has nowhere to live.
     */
    private void pump() {
        if (stopped) {
            return;
        }
        tick();
        if (stopped || refusal != null) {
            // finish() and refuse() both end the chain: there is nothing more to
            // read, and refuse() has already shut the poller down.
            return;
        }
        LiveModel current = model;
        long next = current == null
                ? DEFAULT_POLL_MS : pollMillis(current.refresh());
        try {
            poller.schedule(this::pump, next, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException windowClosed) {
            // Closed between the poll above and here. Nothing left to schedule.
        }
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
                lastTick = true;
                // A bug inside tick() must not abort before the poller is
                // marked stopped and shut down: the finally is what makes that
                // a guarantee rather than something that merely usually holds.
                try {
                    tick();
                    SwingUtilities.invokeLater(() -> setTitle(getTitle() + " (finished)"));
                } finally {
                    stopped = true;
                    poller.shutdown();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException windowClosed) {
            // The window went away between the check above and this call. There
            // is nothing left to read and nothing left to freeze.
        }
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
                // rather than drawing the two on one axis.
                pending.clear();
                // The whole header, not just the buffers: the new run writes its
                // own, and a kept header maps the new run's columns through the
                // old observable list. accept() discards # lines, so nothing
                // else would ever notice.
                model = null;
                emptyPolls = 0;
                SwingUtilities.invokeLater(this::discardPanels);
            }
            // On the last tick, buffered header lines with no model must still
            // reach the parse attempt below: that is what lets a header that
            // never completed be reported with the engine's own message
            // instead of leaving the window blank forever.
            if (lines.isEmpty() && !(lastTick && model == null && !pending.isEmpty())) {
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
                    // header coming, so the same failure is real. Once the
                    // engine has exited (lastTick), there is no more header
                    // coming either, whatever the lines look like, and the
                    // user is owed the engine's own explanation rather than a
                    // window that stays blank forever.
                    if (lastTick || headerFailureIsFinal(pending)) {
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
        } catch (RuntimeException ex) {
            // scheduleWithFixedDelay swallows an unchecked throw into a future
            // nobody reads and stops re-scheduling, so without this the live
            // view freezes on its last snapshot with nothing said. A bug here
            // must be visible, not quiet.
            refuse("Run-time curves stopped: " + ex);
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
        stack.repaint();
    }

    /** On the EDT: drop the panels so the next header builds its own. */
    private void discardPanels() {
        built = false;
        panels.clear();
        stack.removeAll();
        stack.revalidate();
        stack.repaint();
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
