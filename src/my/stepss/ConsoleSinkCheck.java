package my.stepss;

import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Headless checks for {@link TextareaOutputStream}, the sink the console panes
 * use for child-process output.
 *
 * <p>Guards the defect that shipped: every exec site handed one sink to both of
 * {@code PumpStreamHandler}'s stdout and stderr pumps, so two threads shared a
 * line buffer and helios output reached the pane with its characters woven
 * together - {@code "=h e l i-o3s.:5 s t a t Qu=s:  C-O0N.V7E R G E D"} is one
 * line interleaved with another. Lines were also dropped and duplicated,
 * because the old flush() captured and reset the buffer in two steps.
 *
 * <p>Lives in this package because {@code TextareaOutputStream} is
 * package-private; run from {@code tools/compile-harness.sh} alongside the
 * compile-pipeline checks, since this repository has no test framework.
 */
public final class ConsoleSinkCheck {

    private static final int LINES = 400;
    private static final String A = "helios: status: CONVERGED (3 iterations)";
    private static final String B = "PFCcmd.txt: 20 commands executed successfully";

    private static int failures = 0;

    private ConsoleSinkCheck() {
    }

    public static void main(String[] args) throws Exception {
        checkSeparateSinksPerStream();
        checkOneSinkSharedByTwoThreads();
        System.out.println(failures == 0 ? "ALL CONSOLE SINK CHECKS PASSED"
                : failures + " CONSOLE SINK CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * The shape production uses: one sink per stream over a shared text area,
     * written in chunks that split lines wherever a pump read happens to end.
     * Each stream buffers its own partial line, so no line is ever cut around
     * another's.
     */
    private static void checkSeparateSinksPerStream() throws Exception {
        JTextArea area = new JTextArea();
        run("separate sink per stream", area,
                new TextareaOutputStream(area), new TextareaOutputStream(area));
    }

    /**
     * The shape that shipped: both pumps on one sink. This cannot be made to
     * produce whole lines - one stream's partial line sits in the buffer while
     * the other appends to it - so the sink reports the misuse instead. This
     * check asserts it notices, which is what turns a silent regression into a
     * visible one.
     */
    private static void checkOneSinkSharedByTwoThreads() throws Exception {
        JTextArea area = new JTextArea();
        TextareaOutputStream shared = new TextareaOutputStream(area);
        Thread t1 = writer(shared, A, 7);
        Thread t2 = writer(shared, B, 11);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        if (shared.sawSharing()) {
            System.out.println("PASS  a sink shared by two streams is reported");
        } else {
            failures++;
            System.out.println("FAIL  a sink shared by two streams went unreported");
        }
    }

    private static void run(String what, JTextArea area,
            OutputStream first, OutputStream second) throws Exception {
        Thread t1 = writer(first, A, 7);
        Thread t2 = writer(second, B, 11);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        final CountDownLatch drained = new CountDownLatch(1);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                drained.countDown();
            }
        });
        drained.await();

        int intact = 0;
        int corrupt = 0;
        String firstCorrupt = null;
        for (String line : area.getText().split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals(A) || line.equals(B)) {
                intact++;
            } else {
                corrupt++;
                if (firstCorrupt == null) {
                    firstCorrupt = line;
                }
            }
        }
        if (corrupt == 0 && intact == 2 * LINES) {
            System.out.println("PASS  " + what + ": " + intact + " lines, all whole");
        } else {
            failures++;
            System.out.println("FAIL  " + what + ": " + intact + " intact of "
                    + (2 * LINES) + ", " + corrupt + " corrupt"
                    + (firstCorrupt == null ? "" : ", first <" + firstCorrupt + ">"));
        }
    }

    /**
     * Writes its line {@code LINES} times, handed over in fixed-size chunks so
     * the writes land mid-line - which is what a pump's read boundary does.
     */
    private static Thread writer(final OutputStream out, final String line, final int chunk) {
        return new Thread() {
            @Override
            public void run() {
                try {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < LINES; i++) {
                        sb.append(line).append('\n');
                    }
                    byte[] all = sb.toString().getBytes("UTF-8");
                    for (int off = 0; off < all.length; off += chunk) {
                        out.write(all, off, Math.min(chunk, all.length - off));
                    }
                    out.close();
                } catch (Exception ex) {
                    failures++;
                    System.out.println("FAIL  writer threw: " + ex);
                }
            }
        };
    }
}
