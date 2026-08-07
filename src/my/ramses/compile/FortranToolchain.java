package my.ramses.compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    /**
     * Returns the compiler to use, preferring plain {@code gfortran} when its
     * module ABI matches the kit, then scanning versioned binaries. Returns the
     * plain name when no ABI could be read from either side, matching
     * check_kit.sh's lenient behaviour - the definitive gate is check-deps.
     */
    static String pickCompiler(File kitDir, Platform p) {
        int kitAbi = kitAbi(kitDir, p);
        String plain = p.isWindows() ? "gfortran.exe" : "gfortran";
        File plainFile = PlatformLauncher.findOnPath(plain);

        if (plainFile != null) {
            int abi = compilerAbi(plainFile.getAbsolutePath());
            if (kitAbi < 0 || abi < 0 || abi == kitAbi) {
                return "gfortran";
            }
        }
        for (int i = 0; i < CANDIDATE_VERSIONS.length; i++) {
            String name = "gfortran-" + CANDIDATE_VERSIONS[i] + (p.isWindows() ? ".exe" : "");
            File candidate = PlatformLauncher.findOnPath(name);
            if (candidate != null && compilerAbi(candidate.getAbsolutePath()) == kitAbi) {
                return "gfortran-" + CANDIDATE_VERSIONS[i];
            }
        }
        return plainFile != null ? "gfortran" : null;
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

    /** Compiles a probe module and reads the ABI the compiler emits. */
    public static int compilerAbi(String fc) {
        File tmp = null;
        try {
            tmp = File.createTempFile("stepss-abi", "");
            if (!tmp.delete() || !tmp.mkdir()) {
                return -1;
            }
            File src = new File(tmp, "probe.f90");
            writeText(src, "module probe_mod\nend module probe_mod\n");
            Process proc = new ProcessBuilder(fc, "-c", src.getAbsolutePath(),
                    "-o", new File(tmp, "probe.o").getAbsolutePath(),
                    "-J" + tmp.getAbsolutePath())
                    .redirectErrorStream(true).start();
            drain(proc.getInputStream());
            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return -1;
            }
            File mod = new File(tmp, "probe_mod.mod");
            return mod.isFile() ? moduleAbi(mod) : -1;
        } catch (IOException ex) {
            return -1;
        } catch (InterruptedException ex) {
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

    private static void drain(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        while (r.readLine() != null) {
            // Discard: only the emitted .mod matters.
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
