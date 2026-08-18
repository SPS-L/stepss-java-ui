package my.stepss;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Headless checks for the two sinks the console panes use for child-process
 * output: {@link TextareaOutputStream}, and the {@link HeliosLog.Filter} the
 * Power Flow Simulation console wraps it in.
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
        checkFilterKeepsProgressAndDropsTables();
        checkFilterKeepsWholeLinesAcrossChunks();
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

    /**
     * A genuine helios stdout capture, abridged: the run java-ui's PFCcmd.txt
     * produces, with the middle of each table cut out. Every line is verbatim,
     * trailing spaces included, because that is what the filter has to judge.
     */
    private static final String HELIOS_STDOUT
            = " Converged in 1 iterations\n"
            + "\n"
            + " bus 1        :        V= 1.0300 pu    0.00 deg         20.60 kV\n"
            + "         > STUP_1                 P=  700.1    Q=  185.0      > 5       \n"
            + "     gener G1                     P=  700.1    Q=  185.0     Vimp= 1.0300\n"
            + " \n"
            + " Exported to: in_net.res\n"
            + " 1           700.1/  185.0 > STUP_1               <  -700.1/ -102.7   5       \n"
            + "    lost :  -0.00 /  82.38 (  82.38 ser   -0.00 sh)  S =   724.1 =   78.1 %\n"
            + " \n"
            + " Exported to: in_flow.res\n"
            + "       gener          stat    P       Q       V    V-Vimp     Qmin     Qmax\n"
            + " G1                   PV     700.1   185.0 1.0300  0.0000  -9999.0   9999.0\n"
            + " \n"
            + " Exported to: in_gen.res\n"
            + "   LOAD :  total :            2734.0  /      200.0\n"
            + "   NETWORK LOSSES : total :     85.10 /     1112.67\n"
            + " \n"
            + " Exported to: in_bal.res\n"
            + " VoltRat file written to: in_volt_trfo.dat\n";

    /** What the console should be left with: progress only, tables gone. */
    private static final String EXPECTED_CONSOLE
            = " Converged in 1 iterations\n"
            + " Exported to: in_net.res\n"
            + " Exported to: in_flow.res\n"
            + " Exported to: in_gen.res\n"
            + " Exported to: in_bal.res\n"
            + " VoltRat file written to: in_volt_trfo.dat\n";

    /**
     * The filter's whole job: helios echoes every table it exports, and the six
     * buttons under the console exist to show those tables on demand, so the
     * console keeps the progress lines and nothing else.
     */
    private static void checkFilterKeepsProgressAndDropsTables() throws Exception {
        String got = filtered(HELIOS_STDOUT, HELIOS_STDOUT.length());
        if (EXPECTED_CONSOLE.equals(got)) {
            System.out.println("PASS  filter keeps progress lines and drops tables");
        } else {
            failures++;
            System.out.println("FAIL  filter output differs from expected:\n" + got);
        }
    }

    /**
     * Commons Exec hands over whatever a read returned, so chunk boundaries
     * fall mid-line constantly. A filter that judged chunks rather than lines
     * would let half a table row through whenever one straddled a boundary, so
     * every chunk size has to produce the same console text.
     */
    private static void checkFilterKeepsWholeLinesAcrossChunks() throws Exception {
        int[] chunks = {1, 3, 7, 13, 64, 1000};
        for (int chunk : chunks) {
            String got = filtered(HELIOS_STDOUT, chunk);
            if (!EXPECTED_CONSOLE.equals(got)) {
                failures++;
                System.out.println("FAIL  filter at chunk size " + chunk
                        + " produced:\n" + got);
                return;
            }
        }
        System.out.println("PASS  filter is chunk-boundary independent");
    }

    /** Pushes {@code text} through the filter in fixed-size writes. */
    private static String filtered(String text, int chunk) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        HeliosLog.Filter filter = new HeliosLog.Filter(sink);
        byte[] all = text.getBytes(StandardCharsets.ISO_8859_1);
        for (int off = 0; off < all.length; off += chunk) {
            filter.write(all, off, Math.min(chunk, all.length - off));
        }
        filter.close();
        return new String(sink.toByteArray(), StandardCharsets.ISO_8859_1);
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
