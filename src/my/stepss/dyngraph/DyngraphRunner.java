package my.stepss.dyngraph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

/**
 * The two DYNGRAPH invocations behind the observable picker, both with
 * stdout and stderr captured to buffers - the Helios capture precedent
 * (StepssUI's dedicated stderr buffer feeding reportHeliosExitStatus), not
 * viewCurvesButton's no-arg PumpStreamHandler, which inherits the JVM's
 * streams and captures nothing.
 *
 * <p><b>Neither call may run on the EDT.</b> get_observ_name rewinds the
 * trajectory several times and the plot run reads the whole time series, so
 * both scale with file size. {@link #list} is blocking, written for a
 * SwingWorker's doInBackground; {@link #plot} is asynchronous through a
 * DefaultExecuteResultHandler and reports on the executor's thread.
 *
 * <p>{@code --list} needs stdout captured alone: DYNGRAPH's chatty
 * per-category counts go to unit 0 (stderr), so stdout carries the index and
 * nothing else. Both streams decode as {@link ReplayFile#CHARSET}
 * (ISO-8859-1), the same single definition the sel.cmd write uses, so the
 * byte round trip cannot split across two charsets.
 */
public final class DyngraphRunner {

    /** Outcome of a blocking {@code --list} run. */
    public static final class ListResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        ListResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    /** Callback for the async plot run. Called off the EDT - hop with invokeLater. */
    public interface PlotListener {
        void onFinished(int exitCode, String stderr);
    }

    private final File dyngraph;
    private final File workingDir;
    private final Map environment;

    /**
     * @param dyngraph the resolved executable ({@code toolchain.dyngraph()})
     * @param workingDir the temp directory both runs execute in
     * @param environment the exec environment
     *        ({@code PlatformLauncher.execEnvironment}'s map); null inherits
     */
    public DyngraphRunner(File dyngraph, File workingDir, Map environment) {
        this.dyngraph = dyngraph;
        this.workingDir = workingDir;
        this.environment = environment;
    }

    /**
     * Runs {@code dyngraph --list <trajectory>} and returns exit status and
     * both captured streams. Blocking; call off the EDT.
     *
     * <p>Every exit value is accepted here rather than thrown: the caller's
     * detection is exit-status-first (an old DYNGRAPH ignores --list, writes
     * its filename prompt to stderr, hits EOF on its closed stdin and exits
     * non-zero with stdout empty), so the code belongs in the result, not in
     * an ExecuteException.
     */
    public ListResult list(File trajectory) throws IOException {
        CommandLine cmd = new CommandLine(dyngraph.getAbsolutePath());
        cmd.addArgument("--list");
        // Quoting disabled: the default handling re-quotes a token
        // containing spaces and the quotes reach the child.
        cmd.addArgument(trajectory.getAbsolutePath(), false);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValues(null); // accept every exit value
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        executor.setWorkingDirectory(workingDir);
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        int exit = executor.execute(cmd, environment);
        return new ListResult(exit,
                new String(stdout.toByteArray(), ReplayFile.CHARSET),
                new String(stderr.toByteArray(), ReplayFile.CHARSET));
    }

    /**
     * Runs {@code dyngraph -c -t <selCmd> -o<outputBase>} asynchronously and
     * reports through {@code listener}. Not interactive under -t, so it gets
     * no terminal window. No {@code -eps}: it would export an EPS file
     * instead of leaving a .plt that viewCurvesButton can open in a gnuplot
     * window - the same flag choice as today's Extract Curves.
     *
     * <p>The trajectory path travels as line 1 of the replay file, so it is
     * never an argv token; -o still carries a possibly-space-containing
     * absolute path as one token and is added with quoting disabled.
     */
    public void plot(File selCmd, File outputBase, final PlotListener listener)
            throws IOException {
        CommandLine cmd = new CommandLine(dyngraph.getAbsolutePath());
        cmd.addArgument("-c");
        cmd.addArgument("-t");
        cmd.addArgument(selCmd.getAbsolutePath(), false);
        cmd.addArgument("-o" + outputBase.getAbsolutePath(), false);

        // stdout is captured only to keep it out of the GUI's console; with
        // -o set, DYNGRAPH's unit 6 carries nothing of interest. stderr
        // carries the desc_obs echo lines and any real complaint, and is
        // what the listener reports on failure.
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        executor.setWorkingDirectory(workingDir);
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler() {
            @Override
            public void onProcessComplete(int exitValue) {
                listener.onFinished(exitValue,
                        new String(stderr.toByteArray(), ReplayFile.CHARSET));
            }

            @Override
            public void onProcessFailed(ExecuteException ex) {
                listener.onFinished(ex.getExitValue(),
                        new String(stderr.toByteArray(), ReplayFile.CHARSET));
            }
        };
        executor.execute(cmd, environment, handler);
    }
}
