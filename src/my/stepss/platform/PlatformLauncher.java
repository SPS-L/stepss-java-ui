package my.stepss.platform;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.environment.EnvironmentUtils;

/**
 * Launches external programs (editor, terminal, file manager) and kills stray
 * processes by name, in a platform-appropriate way. Replaces the bundled
 * Notepad++ (run under Wine on Linux) and the assorted taskkill/killall
 * branches that used to be scattered across the UI code.
 */
public final class PlatformLauncher {

    private PlatformLauncher() {
    }

    /** @return the first executable named {@code exe} found on PATH, or null if none. */
    public static File findOnPath(String exe) {
        return findOnPath(exe, java.util.Collections.<String>emptyList());
    }

    /**
     * Like {@link #findOnPath(String)}, but searches {@code extraDirs} first.
     *
     * <p>PATH here is the JVM's own PATH, inherited from however STEPSS was
     * launched. That is not always the whole story: on Windows the MSYS2
     * installer does not put {@code C:\msys64\mingw64\bin} on the system PATH,
     * so a user who has installed exactly the documented toolchain still has
     * no gfortran visible to this process. Callers that know about such a
     * location (see {@code FortranToolchain.extraPathEntries}) pass it here,
     * so the probe sees the same directories the child build process will get
     * prepended to its own PATH. Searching them ahead of PATH keeps the
     * toolchain the build will actually use and the toolchain reported to the
     * user the same one.
     *
     * @param extraDirs directories searched before PATH; may be null or empty,
     *        in which case this behaves exactly like {@link #findOnPath(String)}
     * @return the first executable named {@code exe}, or null if none
     */
    public static File findOnPath(String exe, List<String> extraDirs) {
        if (extraDirs != null) {
            for (String dir : extraDirs) {
                File candidate = executableAt(dir, exe);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String part : path.split(File.pathSeparator)) {
            File candidate = executableAt(part, exe);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** @return {@code dir/exe} when it is a runnable file, or null. */
    private static File executableAt(String dir, String exe) {
        File candidate = new File(dir, exe);
        return candidate.isFile() && candidate.canExecute() ? candidate : null;
    }

    /**
     * The per-platform command that opens {@code file} in a TEXT editor.
     *
     * <p>Split out of {@link #openInEditor} so it can be checked without
     * launching anything. Every branch here deliberately forces a text editor,
     * which is right for the data and disturbance files it serves and wrong for
     * anything else; see {@link #defaultApplicationCommand}.
     */
    public static CommandLine editorCommand(Platform p, File file) {
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("notepad.exe");
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
            cmd.addArgument("-t");
        } else {
            cmd = new CommandLine("xdg-open");
        }
        cmd.addArgument(file.getAbsolutePath(), false);
        return cmd;
    }

    /**
     * The per-platform command that opens {@code file} in whatever application
     * the desktop associates with its type.
     *
     * <p>The difference from {@link #editorCommand} is the whole reason this
     * exists: two of that method's three branches force a text editor, so an
     * SVG opened through it appears as XML source rather than as a drawing.
     * Here macOS gets {@code open} without {@code -t} and Windows gets the
     * shell's own {@code start}, so an installed SVG editor is used and a
     * machine with none falls through to the browser, which is a viewer.
     */
    public static CommandLine defaultApplicationCommand(Platform p, File file) {
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("cmd.exe");
            cmd.addArgument("/c");
            cmd.addArgument("start");
            // start treats its first quoted argument as the window title, so a
            // path in quotes with nothing before it opens a console instead of
            // the file. The empty title is what stops that.
            cmd.addArgument("\"\"", false);
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
        } else {
            cmd = new CommandLine("xdg-open");
        }
        cmd.addArgument(file.getAbsolutePath(), false);
        return cmd;
    }

    /** Hands the file to the user's default editor. Replaces bundled Notepad++. */
    public static void openInEditor(File file) throws IOException {
        if (tryDesktop(file)) {
            return;
        }
        run(editorCommand(platformOrThrow(), file), file.getParentFile(),
                "open an editor for " + file.getName());
    }

    /**
     * Opens {@code file} in the platform's own application for its type: an SVG
     * editor for a drawing, falling back to a viewer.
     *
     * <p>Tries {@code Desktop.EDIT} first, then {@code Desktop.OPEN}, exactly as
     * {@link #openInEditor} does, because EDIT is what reaches Inkscape for
     * anyone who has it associated. Only the per-platform fallback differs.
     */
    public static void openInDefaultApplication(File file) throws IOException {
        if (tryDesktop(file)) {
            return;
        }
        run(defaultApplicationCommand(platformOrThrow(), file),
                file.getParentFile(), "open " + file.getName());
    }

    /**
     * Tries the AWT Desktop API (EDIT, then OPEN). Returns false, never throws,
     * whenever Desktop can't do it here so the caller can fall back to a
     * per-platform command. Desktop.isDesktopSupported()/getDesktop() are known
     * to throw unchecked exceptions (HeadlessException, UnsupportedOperation-
     * Exception) on headless hosts and some minimal Linux desktops, so those
     * are caught here rather than allowed to escape this void-returning helper.
     */
    private static boolean tryDesktop(File file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.EDIT)) {
                try {
                    desktop.edit(file);
                    return true;
                } catch (IOException ex) {
                    // No EDIT association for this type; fall through to OPEN.
                }
            }
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                try {
                    desktop.open(file);
                    return true;
                } catch (IOException ex) {
                    // Fall through to the per-platform default below.
                }
            }
        } catch (RuntimeException ex) {
            // Desktop unusable here (headless, no default handlers, etc.);
            // let the per-platform command in openInEditor take over.
        }
        return false;
    }

    public static void openTerminal(File dir) throws IOException {
        Platform p = platformOrThrow();
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("cmd.exe");
            cmd.addArgument("/c");
            cmd.addArgument("start");
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
            cmd.addArgument("-a");
            cmd.addArgument("Terminal");
            cmd.addArgument(dir.getAbsolutePath(), false);
        } else {
            cmd = terminalOnLinux();
        }
        run(cmd, dir, "open a terminal");
    }

    public static void openFileManager(File dir) throws IOException {
        Platform p = platformOrThrow();
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("explorer.exe");
            cmd.addArgument("/root," + dir.getAbsolutePath(), false);
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
            cmd.addArgument(dir.getAbsolutePath(), false);
        } else {
            cmd = new CommandLine("xdg-open");
            cmd.addArgument(dir.getAbsolutePath(), false);
        }
        run(cmd, dir, "open a file manager");
    }

    /** Runs an interactive console program inside a terminal window. */
    public static void runInTerminal(Platform p, List<String> argv, File dir)
            throws IOException {
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            // Launched through `cmd /c start`, which gives a console
            // subsystem program its own console window. Running the
            // executable directly was right only while Windows shipped the
            // Intel dialog build of dyngraph: that one drew its own GUI and
            // needed no console. The release build is console on every
            // platform, and a console program started from a GUI process
            // inherits no console at all - its prompts would go nowhere and
            // the user would see an apparently dead button.
            //
            // The empty "" is the window title `start` expects; without it
            // start treats a quoted program path as the title and never runs
            // it.
            cmd = new CommandLine("cmd.exe");
            cmd.addArgument("/c", false);
            cmd.addArgument("start", false);
            cmd.addArgument("\"\"", false);
            for (String a : argv) {
                cmd.addArgument(a, false);
            }
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("osascript");
            cmd.addArgument("-e", false);
            cmd.addArgument(appleScriptRunInTerminal(argv, dir), false);
        } else {
            cmd = terminalOnLinux();
            cmd.addArgument("-e");
            for (String a : argv) {
                cmd.addArgument(a, false);
            }
        }
        run(cmd, dir, "run " + argv.get(0) + " in a terminal");
    }

    /**
     * Builds the AppleScript source for
     * {@code tell application "Terminal" to do script "<shell command>"}.
     *
     * <p>Two layers of quoting are stacked here: the shell command line that
     * Terminal.app will actually run, and the double-quoted AppleScript string
     * literal that carries it. Each layer is escaped independently and in the
     * right order so that a working directory containing spaces, single
     * quotes, double quotes or backslashes still round-trips correctly:
     * <ol>
     *   <li>every token (the {@code cd} target and each argv element) is
     *       wrapped in single quotes for the shell, per POSIX shell quoting
     *       rules (a literal {@code '} becomes {@code '\''});</li>
     *   <li>the resulting shell command line is then escaped for embedding in
     *       an AppleScript double-quoted string, where {@code \} and {@code "}
     *       are the only special characters.</li>
     * </ol>
     */
    private static String appleScriptRunInTerminal(List<String> argv, File dir) {
        StringBuilder shell = new StringBuilder();
        shell.append("cd ").append(shellQuote(dir.getAbsolutePath())).append(" && ");
        for (String a : argv) {
            shell.append(shellQuote(a)).append(' ');
        }
        String shellCommand = shell.toString().trim();
        return "tell application \"Terminal\" to do script \""
                + appleScriptQuote(shellCommand) + "\"";
    }

    /** Wraps {@code s} in single quotes, safe for a POSIX shell command line. */
    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** Escapes {@code s} for embedding inside an AppleScript double-quoted string literal. */
    private static String appleScriptQuote(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static CommandLine terminalOnLinux() {
        String[] candidates = {"x-terminal-emulator", "xterm", "gnome-terminal", "konsole"};
        for (String c : candidates) {
            if (findOnPath(c) != null) {
                return new CommandLine(c);
            }
        }
        return new CommandLine("xterm");
    }

    /**
     * Best-effort kill of every process named {@code baseName}. Never throws:
     * a non-running process is a normal, silent outcome (the kill tool exits
     * non-zero, which {@link Runtime#exec(String[])} does not surface as an
     * exception at all), but the kill tool itself being missing is a genuinely
     * different, useful signal, so that case is logged instead of being
     * swallowed alongside the normal one.
     */
    public static void killByName(Platform p, String baseName) {
        String[] command;
        if (p == Platform.WINDOWS_X86_64) {
            command = new String[] {"taskkill", "/F", "/IM", baseName + ".exe"};
        } else {
            command = new String[] {"killall", "-9", baseName};
        }
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException ex) {
            // The kill tool itself (taskkill/killall) could not be launched -
            // e.g. missing on a minimal install. Distinct from "nothing to
            // kill", which never reaches this catch block, so it is worth a
            // diagnostic rather than being silently dropped.
            System.err.println("PlatformLauncher.killByName: could not run '"
                    + command[0] + "': " + ex.getMessage());
        } catch (RuntimeException ex) {
            // Defensive: guarantee this method genuinely cannot throw.
            System.err.println("PlatformLauncher.killByName: " + ex.getMessage());
        }
    }

    /**
     * Windows needs gnuplot's bin directory on PATH for the exec'd children.
     * Other platforms inherit the ambient environment, signalled by null.
     *
     * <p>A {@code dynsim} directory used to be prepended here as well, so a
     * Codegen-built {@code Release_intel_w64/dynsim.exe} could find the Intel
     * Fortran runtime DLLs that shipped beside it in {@code dynsim.zip}.
     * Nothing is left of that arrangement: {@code dynsim.zip} is gone, the
     * bundled engine extracts to {@code dynsim.exe} as a file rather than a
     * directory - so the entry pointed at a path that never existed - and the
     * custom-model build is statically linked MinGW, which needs no runtime
     * DLLs on PATH at all.
     */
    public static Map execEnvironment(Platform p, File toolDir) throws IOException {
        if (p != Platform.WINDOWS_X86_64) {
            return null;
        }
        Map env = EnvironmentUtils.getProcEnvironment();
        String path = (String) env.get("PATH");
        String gnuplotBin = new File(new File(toolDir, "gnuplot"), "bin").getAbsolutePath();
        EnvironmentUtils.addVariableToEnvironment(env, "PATH=" + gnuplotBin
                + File.pathSeparator + path);
        return env;
    }

    private static Platform platformOrThrow() throws IOException {
        try {
            return Platform.current();
        } catch (UnsupportedPlatformException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    /**
     * Notified when a launch started via {@link #run} fails after the async
     * {@code execute} call has already returned normally — e.g. the target
     * program (xdg-open, a terminal emulator, ...) does not exist. Commons
     * Exec's async {@code execute(CommandLine, ExecuteResultHandler)}
     * overload runs the whole launch, including the {@code IOException} that
     * a missing executable throws from {@code ProcessBuilder}, on a worker
     * thread and hands it to the result handler instead of the caller; a
     * bare {@code DefaultExecuteResultHandler} just records it and returns,
     * so without this hook the failure is never seen by anyone. Set once by
     * the UI at startup ({@link #setLaunchFailureListener}); a null listener
     * means failures are only logged to stderr, which still beats silence.
     */
    private static volatile BiConsumer<String, Throwable> launchFailureListener;

    /**
     * Registers the callback used to surface an async launch failure to the
     * user. Called back on whatever thread Commons Exec's worker thread is
     * running on, not the EDT — implementations that touch Swing must hop
     * back with {@code SwingUtilities.invokeLater} themselves.
     */
    public static void setLaunchFailureListener(BiConsumer<String, Throwable> listener) {
        launchFailureListener = listener;
    }

    /**
     * Launches {@code cmd} without blocking the caller. {@code description}
     * is a short present-tense fragment ("open a terminal") used only if the
     * launch fails, to name what was being attempted.
     */
    private static void run(CommandLine cmd, File workingDir, String description) throws IOException {
        DefaultExecutor executor = new DefaultExecutor();
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        if (workingDir != null && workingDir.isDirectory()) {
            executor.setWorkingDirectory(workingDir);
        }
        executor.execute(cmd, new ExecuteResultHandler() {
            @Override
            public void onProcessComplete(int exitValue) {
                // Fire-and-forget launch; nothing to do on success.
            }

            @Override
            public void onProcessFailed(ExecuteException e) {
                BiConsumer<String, Throwable> listener = launchFailureListener;
                if (listener != null) {
                    listener.accept(description, e);
                } else {
                    System.err.println("PlatformLauncher: could not " + description
                            + ": " + e.getMessage());
                }
            }
        });
    }
}
