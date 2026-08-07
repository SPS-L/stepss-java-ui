package my.ramses.compile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import my.ramses.platform.Platform;
import my.ramses.platform.PlatformLauncher;
import my.ramses.platform.Toolchain;

/**
 * Locates the tools a custom-model build needs and picks a gfortran whose
 * module ABI matches the bundled kit.
 *
 * <p>A GFORTRAN {@code .mod} file can only be read by the compiler generation
 * that wrote it, and the three kits are deliberately not on a common ABI:
 * modules_l is gfortran 13.3 / ABI 15 because it is built on ubuntu-24.04,
 * while modules_m and modules_wg are gfortran 16.1 / ABI 16 because the macOS
 * and Windows runners track newer toolchains. Each platform's default compiler
 * matches its own kit today, so the versioned-compiler scan below is a fallback
 * for hosts where it does not.
 *
 * <p>The definitive check is upstream's {@code make check-deps}, which the
 * caller runs and whose message it surfaces verbatim. This class exists to pick
 * a good {@code FC=} before that runs, so the common "distro ships
 * gfortran-13 but not gfortran" case resolves without user action.
 */
public final class FortranToolchain {

    /** Versioned compilers to try, newest first, when plain gfortran does not fit. */
    private static final int[] CANDIDATE_VERSIONS = {16, 15, 14, 13, 12, 11, 10, 9};

    public static final class Probe {
        public final File make;
        /** The value to pass as {@code FC=}, or null to leave the Makefile default. */
        public final String fc;
        /** Human-readable reason this toolchain is unusable, or null when it is usable. */
        public final String problem;

        Probe(File make, String fc, String problem) {
            this.make = make;
            this.fc = fc;
            this.problem = problem;
        }
    }

    private FortranToolchain() {
    }

    public static Probe probe(File kitDir, Platform p) {
        File make = findMake(p);
        if (make == null) {
            return new Probe(null, null, missingToolMessage("make", p));
        }
        String fc = pickCompiler(kitDir, p);
        if (fc == null) {
            return new Probe(make, null, missingToolMessage("gfortran", p));
        }
        return new Probe(make, fc, null);
    }

    static File findMake(Platform p) {
        File make = PlatformLauncher.findOnPath(p.isWindows() ? "make.exe" : "make");
        if (make != null) {
            return make;
        }
        if (p.isWindows()) {
            File root = msysRoot();
            if (root != null) {
                File candidate = new File(root, "usr\\bin\\make.exe");
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** One gfortran found on PATH, carrying enough to let {@link #choose} pick among them. */
    static final class Candidate {
        final String label;
        final boolean plain;
        final int abi;

        Candidate(String label, boolean plain, int abi) {
            this.label = label;
            this.plain = plain;
            this.abi = abi;
        }
    }

    /**
     * Returns the compiler to use, preferring plain {@code gfortran} when its
     * module ABI matches the kit, then scanning versioned binaries. Scanning
     * (PATH lookups and spawning each candidate to read its ABI) lives here;
     * the leniency contract itself is {@link #choose}, kept pure and separate
     * so it is testable without depending on which compilers happen to be
     * installed on the machine running the checks.
     */
    static String pickCompiler(File kitDir, Platform p) {
        int kitAbi = kitAbi(kitDir, p);
        List<Candidate> found = new ArrayList<Candidate>();

        String plain = p.isWindows() ? "gfortran.exe" : "gfortran";
        File plainFile = PlatformLauncher.findOnPath(plain);
        if (plainFile != null) {
            found.add(new Candidate("gfortran", true, compilerAbi(plainFile.getAbsolutePath())));
        }
        for (int i = 0; i < CANDIDATE_VERSIONS.length; i++) {
            String name = "gfortran-" + CANDIDATE_VERSIONS[i] + (p.isWindows() ? ".exe" : "");
            File candidate = PlatformLauncher.findOnPath(name);
            if (candidate != null) {
                found.add(new Candidate("gfortran-" + CANDIDATE_VERSIONS[i], false,
                        compilerAbi(candidate.getAbsolutePath())));
            }
        }
        return choose(kitAbi, found);
    }

    /**
     * Pure selection: returns null only when {@code found} is empty - every
     * other case must return some compiler that actually exists on PATH,
     * matching check_kit.sh's lenient behaviour, since the definitive gate is
     * upstream's {@code make check-deps}.
     *
     * <p>When the kit ABI cannot be read there is nothing to discriminate on,
     * so the first candidate wins (plain first, since {@code found} is built
     * plain-then-versioned-newest-first). When the kit ABI is known, plain is
     * still preferred whenever its own ABI is unreadable or matches;
     * otherwise the versioned candidates are scanned for an exact match; and
     * if nothing matches at all, this still returns the first candidate
     * found rather than null; a wrong {@code FC=} only wastes one
     * {@code check-deps} round-trip, whose message is authoritative and
     * surfaces the real remedy, whereas returning null here would misreport
     * a working compiler as absent.
     */
    static String choose(int kitAbi, List<Candidate> found) {
        if (found.isEmpty()) {
            return null;
        }
        if (kitAbi < 0) {
            return found.get(0).label;
        }
        for (int i = 0; i < found.size(); i++) {
            Candidate c = found.get(i);
            if (c.plain && (c.abi < 0 || c.abi == kitAbi)) {
                return c.label;
            }
        }
        for (int i = 0; i < found.size(); i++) {
            Candidate c = found.get(i);
            if (!c.plain && c.abi == kitAbi) {
                return c.label;
            }
        }
        return found.get(0).label;
    }

    /** The ABI of the kit, read from one of its own .mod files. */
    static int kitAbi(File kitDir, Platform p) {
        File mods = new File(kitDir, Toolchain.moduleKitDir(p));
        File[] found = mods.listFiles(new java.io.FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".mod");
            }
        });
        if (found == null || found.length == 0) {
            return -1;
        }
        java.util.Arrays.sort(found);
        return moduleAbi(found[0]);
    }

    /**
     * Reads the GFORTRAN module ABI integer out of a .mod file. They are
     * gzipped text whose banner carries {@code module version '15'}.
     */
    public static int moduleAbi(File modFile) {
        InputStream in = null;
        try {
            in = new GZIPInputStream(new java.io.FileInputStream(modFile));
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) {
                return -1;
            }
            return parseAbi(new String(buf, 0, n, "UTF-8"));
        } catch (IOException ex) {
            return -1;
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * Compiles a probe module and reads the ABI the compiler emits. The
     * compiler's stdout/stderr are redirected to a file rather than piped, so
     * nothing can fill a pipe buffer and block the compiler process while
     * this thread is not reading it - that would leave {@code waitFor}'s
     * 30-second bound unreachable on a hung or misbehaving compiler, which is
     * exactly the unbounded wait this method exists to prevent (it runs on
     * the Swing event thread).
     */
    public static int compilerAbi(String fc) {
        File tmp = null;
        Process proc = null;
        try {
            tmp = File.createTempFile("stepss-abi", "");
            if (!tmp.delete() || !tmp.mkdir()) {
                return -1;
            }
            File src = new File(tmp, "probe.f90");
            writeText(src, "module probe_mod\nend module probe_mod\n");
            proc = new ProcessBuilder(fc, "-c", src.getAbsolutePath(),
                    "-o", new File(tmp, "probe.o").getAbsolutePath(),
                    "-J" + tmp.getAbsolutePath())
                    .redirectErrorStream(true)
                    .redirectOutput(new File(tmp, "probe.log"))
                    .start();
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return -1;
            }
            File mod = new File(tmp, "probe_mod.mod");
            return mod.isFile() ? moduleAbi(mod) : -1;
        } catch (IOException ex) {
            return -1;
        } catch (InterruptedException ex) {
            if (proc != null) {
                proc.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return -1;
        } finally {
            deleteRecursively(tmp);
        }
    }

    static int parseAbi(String banner) {
        String key = "module version '";
        int at = banner.indexOf(key);
        if (at < 0) {
            return -1;
        }
        int start = at + key.length();
        int end = banner.indexOf('\'', start);
        if (end < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(banner.substring(start, end).trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** MSYS2 install root on Windows, or null. */
    public static File msysRoot() {
        String env = System.getenv("MSYS2_ROOT");
        if (env != null) {
            File f = new File(env);
            if (f.isDirectory()) {
                return f;
            }
        }
        File standard = new File("C:\\msys64");
        return standard.isDirectory() ? standard : null;
    }

    /** Directories prepended to PATH for the build, so MSYS2's tools are found. */
    public static List<String> extraPathEntries(Platform p) {
        List<String> out = new ArrayList<String>();
        if (p.isWindows()) {
            File root = msysRoot();
            if (root != null) {
                out.add(new File(root, "mingw64\\bin").getAbsolutePath());
                out.add(new File(root, "usr\\bin").getAbsolutePath());
            }
        }
        return out;
    }

    static String missingToolMessage(String tool, Platform p) {
        switch (p) {
            case WINDOWS_X86_64:
                return tool + " was not found. Compiling custom models on Windows needs "
                        + "MSYS2 with the MinGW-w64 toolchain. Install MSYS2 from "
                        + "https://www.msys2.org/ and then, in an MSYS2 shell, run:\n"
                        + "    pacman -S mingw-w64-x86_64-gcc-fortran "
                        + "mingw-w64-x86_64-openblas make\n"
                        + "STEPSS looks for MSYS2 in C:\\msys64, or wherever MSYS2_ROOT points.";
            case MACOS_ARM64:
                return tool + " was not found. Compiling custom models on macOS needs "
                        + "Homebrew's GCC and OpenBLAS, plus the Command Line Tools:\n"
                        + "    brew install gcc openblas\n"
                        + "    xcode-select --install";
            case LINUX_X86_64:
            default:
                return tool + " was not found. Compiling custom models needs gfortran, "
                        + "make and OpenBLAS:\n"
                        + "    Debian/Ubuntu: sudo apt install gfortran make libopenblas-dev\n"
                        + "    Fedora/RHEL:   sudo dnf install gcc-gfortran make openblas-devel\n"
                        + "    Arch:          sudo pacman -S gcc-fortran make openblas";
        }
    }

    private static void writeText(File f, String text) throws IOException {
        java.io.OutputStream out = new java.io.FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignore) {
        }
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
}
