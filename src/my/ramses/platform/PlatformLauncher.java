package my.ramses.platform;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
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
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String part : path.split(File.pathSeparator)) {
            File candidate = new File(part, exe);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    /** Hands the file to the user's default editor. Replaces bundled Notepad++. */
    public static void openInEditor(File file) throws IOException {
        if (tryDesktop(file)) {
            return;
        }
        Platform p = platformOrThrow();
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
        run(cmd, file.getParentFile());
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
        run(cmd, dir);
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
        run(cmd, dir);
    }

    /** Runs an interactive console program inside a terminal window. */
    public static void runInTerminal(Platform p, List<String> argv, File dir)
            throws IOException {
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine(argv.get(0));
            for (int i = 1; i < argv.size(); i++) {
                cmd.addArgument(argv.get(i), false);
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
        run(cmd, dir);
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

    private static void run(CommandLine cmd, File workingDir) throws IOException {
        DefaultExecutor executor = new DefaultExecutor();
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        if (workingDir != null && workingDir.isDirectory()) {
            executor.setWorkingDirectory(workingDir);
        }
        executor.execute(cmd, new org.apache.commons.exec.DefaultExecuteResultHandler());
    }
}
