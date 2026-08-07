package my.ramses.platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public enum Platform {
    WINDOWS_X86_64("windows"),
    LINUX_X86_64("linux"),
    MACOS_ARM64("macos");

    private final String key;
    private static Platform resolved;

    Platform(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean isWindows() {
        return this == WINDOWS_X86_64;
    }

    public String exeSuffix() {
        return this == WINDOWS_X86_64 ? ".exe" : "";
    }

    public static String describe() {
        return System.getProperty("os.name", "?") + " / " + System.getProperty("os.arch", "?");
    }

    public static synchronized Platform current() throws UnsupportedPlatformException {
        if (resolved == null) {
            resolved = detect();
        }
        return resolved;
    }

    private static Platform detect() throws UnsupportedPlatformException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.US);

        if (os.contains("mac") || os.contains("darwin")) {
            if (arch.equals("aarch64") || arch.equals("arm64")) {
                return MACOS_ARM64;
            }
            if (isRosettaTranslated()) {
                return MACOS_ARM64;
            }
            throw new UnsupportedPlatformException(
                    "STEPSS supports Apple Silicon Macs only.\n"
                    + "Detected: " + describe() + "\n"
                    + "Intel Macs are not supported by the bundled simulation engine.");
        }
        if (os.contains("win")) {
            if (!arch.contains("64")) {
                throw new UnsupportedPlatformException(
                        "STEPSS requires 64-bit Windows.\nDetected: " + describe());
            }
            return WINDOWS_X86_64;
        }
        if (os.contains("linux")) {
            if (!arch.contains("64")) {
                throw new UnsupportedPlatformException(
                        "STEPSS requires 64-bit Linux.\nDetected: " + describe());
            }
            return LINUX_X86_64;
        }
        throw new UnsupportedPlatformException(
                "STEPSS supports Windows, Linux and macOS only.\nDetected: " + describe());
    }

    /**
     * An x86_64 JVM running under Rosetta on Apple Silicon reports os.arch=x86_64,
     * which is indistinguishable from a genuine Intel Mac by os.arch alone.
     * sysctl.proc_translated returns 1 only when the current process is translated.
     */
    private static boolean isRosettaTranslated() {
        BufferedReader reader = null;
        try {
            Process p = new ProcessBuilder("sysctl", "-n", "sysctl.proc_translated")
                    .redirectErrorStream(true).start();
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            return line != null && line.trim().equals("1");
        } catch (Exception ex) {
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
