package my.ramses.compile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.environment.EnvironmentUtils;

import my.ramses.platform.Platform;
import my.ramses.platform.Toolchain;

/**
 * Builds a custom {@code dynsim} from CODEGEN-generated models, by driving
 * upstream's gfortran Makefiles against the bundled uramses kit.
 *
 * <p>The build definition lives upstream and is CI-tested on the same runner
 * images that produce the module kits, so the STEPSS route and the documented
 * PyRAMSES route are the same build. This class only stages inputs and runs
 * {@code make}.
 */
public final class ModelCompiler {

    public interface Listener {
        void onOutput(String line);

        /** @param problem null on success; otherwise a user-facing explanation. */
        void onFinished(int exitCode, File dynsim, String problem);
    }

    private final Platform platform;
    private final Toolchain toolchain;
    private File kitDir;
    private File built;

    public ModelCompiler(Platform platform, Toolchain toolchain) {
        this.platform = platform;
        this.toolchain = toolchain;
    }

    /**
     * Extracts the kit, resets it to pristine, stages the generated models and
     * splices them into their routers.
     *
     * <p>The reset is a full re-extraction rather than a targeted restore.
     * Re-splicing an already-spliced router would emit a duplicate case label
     * and fail the build with an error that reads like a user mistake, and a
     * stale {@code Release_*} tree would let a previous build's objects survive
     * into this one. Re-unpacking ~12 MB takes well under a second and makes
     * every compile start from the same state.
     *
     * <p>The delete must therefore be verified, not attempted:
     * {@code ToolExtractor.extract} re-unpacks only when the stamp is stale or the
     * target is missing, and the stamp is still current here. So if even one
     * file survives - on Windows the {@code dynsim.exe} a previous compile
     * produced and the user is still simulating with, or a file an antivirus
     * scanner or editor has locked - the directory still exists, extraction is
     * skipped, and this method would splice against a tree whose {@code src/}
     * it just deleted, silently and with no exception. Every later compile in
     * the session would repeat it. Failing loudly here is the only outcome
     * that leaves the user something to act on.
     */
    public void prepare(List<File> generated) throws IOException {
        if (generated == null || generated.isEmpty()) {
            throw new IOException("No generated model files to compile. Run Codegen first.");
        }

        File existing = toolchain.uramsesKitDirectory();
        deleteRecursively(existing);
        if (existing.exists()) {
            throw new IOException(resetFailedMessage(existing));
        }
        // extractOnDemand caches the extracted File the first time it runs
        // and returns that cached reference on every later call without
        // touching disk again. Without forgetExtracted first, every compile
        // after the first would hand back a File pointing at the tree just
        // deleted above instead of actually re-unpacking it.
        toolchain.forgetExtracted(Toolchain.URAMSES);
        kitDir = toolchain.extractOnDemand(Toolchain.URAMSES);
        built = null;

        Map<String, List<String>> byKind = new HashMap<String, List<String>>();
        for (File f : generated) {
            String kind;
            try {
                kind = RouterSplicer.kindOf(f.getName());
            } catch (IllegalArgumentException ex) {
                throw new IOException(ex.getMessage(), ex);
            }
            copy(f, new File(new File(kitDir, "custom_models"), f.getName()));
            List<String> models = byKind.get(kind);
            if (models == null) {
                models = new ArrayList<String>();
                byKind.put(kind, models);
            }
            models.add(RouterSplicer.modelNameOf(f.getName()));
        }

        for (Map.Entry<String, List<String>> e : byKind.entrySet()) {
            File router = new File(kitDir, RouterSplicer.routerFor(e.getKey()));
            String source = new String(Files.readAllBytes(router.toPath()), Charset.forName("UTF-8"));
            String spliced = RouterSplicer.splice(source, e.getKey(), e.getValue());
            Files.write(router.toPath(), spliced.getBytes(Charset.forName("UTF-8")));
        }
    }

    /**
     * Runs {@code check-deps} then {@code exe}, asynchronously, reporting
     * through {@code listener}. {@link #prepare} must have run first.
     */
    public void build(final Listener listener) throws IOException {
        if (kitDir == null) {
            throw new IOException("prepare() must run before build()");
        }
        FortranToolchain.Probe probe = FortranToolchain.probe(kitDir, platform);
        if (probe.problem != null) {
            listener.onFinished(-1, null, probe.problem);
            return;
        }

        final File expected = new File(kitDir, releaseDir() + "/dynsim" + platform.exeSuffix());
        CommandLine cmd = new CommandLine(probe.make.getAbsolutePath());
        cmd.addArgument("-f");
        cmd.addArgument("build/Makefile." + platform.key());
        if (probe.fc != null) {
            cmd.addArgument("FC=" + probe.fc);
        }
        cmd.addArgument("check-deps");
        cmd.addArgument("exe");

        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        executor.setWorkingDirectory(kitDir);
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        OutputStream sink = new LineSink(listener);
        executor.setStreamHandler(new PumpStreamHandler(sink, sink));

        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler() {
            @Override
            public void onProcessComplete(int exitValue) {
                built = expected.isFile() ? expected : null;
                if (built != null) {
                    built.setExecutable(true);
                }
                listener.onFinished(exitValue, built, built == null
                        ? "make reported success but " + expected.getAbsolutePath()
                          + " was not produced."
                        : null);
            }

            @Override
            public void onProcessFailed(org.apache.commons.exec.ExecuteException ex) {
                listener.onFinished(ex.getExitValue(), null,
                        "Compilation failed (exit " + ex.getExitValue()
                        + "). The build log above carries the compiler's own message.");
            }
        };
        executor.execute(cmd, buildEnvironment(), handler);
    }

    /** @return the executable produced by the last successful build, or null. */
    public File builtExecutable() {
        return built;
    }

    /** The kit root, or null before {@link #prepare}. Exposed for diagnostics. */
    public File kitDirectory() {
        return kitDir;
    }

    private String releaseDir() {
        switch (platform) {
            case WINDOWS_X86_64: return "Release_wg";
            case LINUX_X86_64:   return "Release_l";
            case MACOS_ARM64:    return "Release_m";
            default: throw new IllegalStateException("No release dir for " + platform);
        }
    }

    /**
     * Child environment for make. On Windows the MSYS2 directories are
     * prepended so make, gfortran, OpenBLAS and the bash that check-deps needs
     * are all resolvable even when STEPSS was launched outside an MSYS2 shell.
     * Returns null elsewhere, which Commons Exec reads as "inherit".
     */
    private Map buildEnvironment() throws IOException {
        List<String> extra = FortranToolchain.extraPathEntries(platform);
        if (extra.isEmpty()) {
            return null;
        }
        Map env = EnvironmentUtils.getProcEnvironment();
        StringBuilder path = new StringBuilder();
        for (String entry : extra) {
            path.append(entry).append(File.pathSeparator);
        }
        path.append((String) env.get("PATH"));
        EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + path);
        return env;
    }

    private static void copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        Files.copy(from.toPath(), to.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * The user-facing explanation for a kit that could not be reset. Names the
     * first file that survived the delete, because on Windows that file is
     * usually the answer: a {@code dynsim.exe} from a previous compile that is
     * still running cannot be deleted while it runs, and nothing else in the
     * UI would ever tell the user that is what went wrong.
     */
    static String resetFailedMessage(File kit) {
        File survivor = firstSurvivingFile(kit);
        return "Could not reset the build kit at " + kit.getAbsolutePath() + ".\n"
                + (survivor != null
                        ? "This file could not be deleted: " + survivor.getAbsolutePath() + "\n"
                        : "The directory itself could not be removed.\n")
                + "The usual cause is that a simulator built by a previous compile is"
                + " still running - stop the simulation and try again. A lock held by"
                + " an antivirus scanner or an open editor does the same thing.\n"
                + "Compiling now would build against a partly deleted kit, so the"
                + " build was stopped.";
    }

    /**
     * @return some file left behind under {@code dir}, or null if the only
     *         thing that survived was a directory. Depth-first, so the deepest
     *         surviving file is reported rather than the directory holding it.
     */
    private static File firstSurvivingFile(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return null;
        }
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].isDirectory()) {
                File deeper = firstSurvivingFile(kids[i]);
                if (deeper != null) {
                    return deeper;
                }
            } else {
                return kids[i];
            }
        }
        return null;
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                deleteRecursively(kids[i]);
            }
        }
        f.delete();
    }

    /**
     * Splits the merged stdout/stderr stream into lines for the listener.
     *
     * <p>{@code PumpStreamHandler} pumps stdout and stderr on two separate
     * threads, and both are wired to the same {@code LineSink} instance
     * (make's own output on stdout, gfortran's diagnostics on stderr - both
     * routinely active at once). Every method that touches {@code line}
     * must therefore be synchronized, including {@code write(byte[], int,
     * int)}: without overriding it, {@code OutputStream}'s default
     * implementation calls {@code write(int)} once per byte with no lock
     * held across the whole chunk, so a chunk from one thread could still
     * interleave with a chunk from the other mid-write. Left unsynchronized,
     * {@code append}/{@code setLength(0)} racing between the two pump
     * threads can throw {@code StringIndexOutOfBoundsException}, which
     * {@code StreamPumper.run} swallows - silently truncating the log
     * rather than visibly failing.
     */
    private static final class LineSink extends OutputStream {
        private final Listener listener;
        private final StringBuilder line = new StringBuilder();

        LineSink(Listener listener) {
            this.listener = listener;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                // Unconditional, unlike emitPending() below: a blank line in
                // the build log is a real newline byte and must round-trip
                // as one, not be swallowed for having an empty line buffer.
                listener.onOutput(line.toString());
                line.setLength(0);
            } else if (b != '\r') {
                line.append((char) b);
            }
        }

        /**
         * Overridden so a whole pump chunk is appended under one lock
         * acquisition rather than one lock per byte, keeping it atomic with
         * respect to the other stream's pump thread.
         */
        @Override
        public synchronized void write(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) {
                write(b[off + i]);
            }
        }

        /**
         * {@code PumpStreamHandler.stop()} flushes rather than closes a
         * non-piped sink ({@code closeWhenExhausted} is false for these),
         * and it does so exactly once, after both pump threads have
         * finished - so this is where the last, newline-less partial line
         * must be emitted, or it is silently dropped.
         */
        @Override
        public synchronized void flush() {
            emitPending();
        }

        @Override
        public synchronized void close() {
            emitPending();
        }

        private void emitPending() {
            if (line.length() > 0) {
                listener.onOutput(line.toString());
                line.setLength(0);
            }
        }
    }
}
