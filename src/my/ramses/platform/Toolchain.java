package my.ramses.platform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Toolchain {

    public static final String RAMSES = "ramses";
    public static final String HELIOS = "helios";
    public static final String DYNGRAPH = "dyngraph";
    public static final String CODEGEN = "codegen";
    public static final String GNUPLOT = "gnuplot";
    public static final String URAMSES = "uramses";

    /**
     * The single definition of the directory the uramses kit unpacks into,
     * relative to {@link #directory()}. It is the {@code extractedName} of
     * every uramses payload below <em>and</em> what callers outside this
     * package resolve the kit against, so the name cannot drift between the
     * manifest and the code that deletes, re-extracts or recognises the kit.
     * Read it through {@link #uramsesKitDirectory()} rather than rebuilding
     * the path by hand.
     */
    public static final String URAMSES_DIR = "uramses";

    public static final List<ToolSpec> SPECS = buildSpecs();

    private static List<ToolSpec> buildSpecs() {
        List<ToolSpec> s = new ArrayList<ToolSpec>();

        s.add(new ToolSpec(RAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/ramses-windows-x86_64-v3.55.zip", ToolSpec.Kind.ZIP,
                "ramses.exe", "dynsim.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/ramses-linux-x86_64-v3.55.tar.gz", ToolSpec.Kind.TGZ,
                "ramses", "dynsim", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/ramses-macos-arm64-v3.55.tar.gz", ToolSpec.Kind.TGZ,
                "ramses", "dynsim", true)));

        s.add(new ToolSpec(HELIOS)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/stepss-helios-windows-x64.zip", ToolSpec.Kind.ZIP,
                "helios.exe", "helios.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/stepss-helios-linux-x86_64.tar.gz", ToolSpec.Kind.TGZ,
                "stepss-helios-linux-x86_64/helios", "helios", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/stepss-helios-macos-arm64.tar.gz", ToolSpec.Kind.TGZ,
                "stepss-helios-macos-arm64/helios", "helios", true)));

        s.add(new ToolSpec(DYNGRAPH)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "dyngraph.exe", ToolSpec.Kind.RAW, null, "dyngraph.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/dyngraph-linux-x86_64-v1.2.0.tar.gz", ToolSpec.Kind.TGZ,
                "dyngraph", "dyngraph", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/dyngraph-macos-arm64-v1.2.0.tar.gz", ToolSpec.Kind.TGZ,
                "dyngraph", "dyngraph", true)));

        s.add(new ToolSpec(CODEGEN)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/codegen-windows-x86_64-v5.1.0.zip", ToolSpec.Kind.ZIP,
                "CODEGEN.exe", "CODEGEN.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/codegen-linux-x86_64-v5.1.0.tar.gz", ToolSpec.Kind.TGZ,
                "CODEGEN", "CODEGEN", true))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/codegen-macos-arm64-v5.1.0.tar.gz", ToolSpec.Kind.TGZ,
                "CODEGEN", "CODEGEN", true)));

        s.add(new ToolSpec(GNUPLOT)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "gpwin.zip", ToolSpec.Kind.ZIP, null, "gnuplot/bin/pgnuplot.exe", false)));

        s.add(new ToolSpec(URAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "payload/uramses-kit-v3.55.zip", URAMSES_DIR,
                java.util.Arrays.asList("build/", "src/", "custom_models/", "tools/",
                                        "README.md", "LICENSE.rst", "modules_wg/")))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "payload/uramses-kit-v3.55.zip", URAMSES_DIR,
                java.util.Arrays.asList("build/", "src/", "custom_models/", "tools/",
                                        "README.md", "LICENSE.rst", "modules_l/")))
            .on(Platform.MACOS_ARM64, new ToolSpec.Payload(
                "payload/uramses-kit-v3.55.zip", URAMSES_DIR,
                java.util.Arrays.asList("build/", "src/", "custom_models/", "tools/",
                                        "README.md", "LICENSE.rst", "modules_m/"))));

        return s;
    }

    public static ToolSpec byId(String id) {
        for (ToolSpec spec : SPECS) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        throw new IllegalArgumentException("No such tool: " + id);
    }

    private final Platform platform;
    private final File dir;
    private final Map<String, File> resolved =
            new HashMap<String, File>();

    public Toolchain(Platform platform, File dir) {
        this.platform = platform;
        this.dir = dir;
    }

    public File directory() {
        return dir;
    }

    public Platform platform() {
        return platform;
    }

    /**
     * Tools that {@link #extractAll()} deliberately skips. The uramses kit is
     * ~12 MB unpacked and only the Codegen tab's Compile step needs it, so
     * paying for it on every launch would tax every user for a feature most
     * never open.
     */
    private static final java.util.Set<String> LAZY =
            java.util.Collections.singleton(URAMSES);

    /** Extracts every tool available on this platform, except the lazy set. */
    public void extractAll() throws IOException {
        for (ToolSpec spec : SPECS) {
            if (spec.availableOn(platform) && !LAZY.contains(spec.id())) {
                resolved.put(spec.id(), ToolExtractor.extract(spec, platform, dir));
            }
        }
    }

    /** @return the extracted file, or null if this tool is not available here. */
    public File get(String id) {
        return resolved.get(id);
    }

    public File ramses()    { return get(RAMSES); }
    public File helios()    { return get(HELIOS); }
    public File dyngraph()  { return get(DYNGRAPH); }
    public File codegen()   { return get(CODEGEN); }

    /** Extracts one tool on first use and caches the result. */
    public File extractOnDemand(String id) throws IOException {
        File existing = resolved.get(id);
        if (existing != null) {
            return existing;
        }
        File f = ToolExtractor.extract(byId(id), platform, dir);
        resolved.put(id, f);
        return f;
    }

    /**
     * Discards the cached extraction result for {@code id}, so the next
     * {@link #extractOnDemand} call re-runs {@link ToolExtractor#extract}
     * instead of returning the same {@code File} reference again.
     *
     * <p>Needed by callers that delete an extracted tree themselves (the
     * uramses kit, reset to pristine before every compile) and then need a
     * real re-unpack, not a cache hit against a directory that no longer
     * exists on disk.
     */
    public void forgetExtracted(String id) {
        resolved.remove(id);
    }

    /** @return the extracted uramses kit root, or null if it has not been extracted yet. */
    public File uramsesKit() {
        return get(URAMSES);
    }

    /**
     * Where the uramses kit lives (or will live) under {@link #directory()},
     * whether or not it has been extracted yet - unlike {@link #uramsesKit()},
     * which only answers once extraction has run. The one place this path is
     * formed, so {@code ModelCompiler.prepare} (which deletes and re-extracts
     * it) and the UI (which recognises a previous build's output inside it)
     * cannot disagree, and neither can drift from {@link #URAMSES_DIR} in the
     * payload manifest.
     */
    public File uramsesKitDirectory() {
        return new File(dir, URAMSES_DIR);
    }

    /** The module-kit directory name inside the uramses kit, per platform. */
    public static String moduleKitDir(Platform p) {
        switch (p) {
            case WINDOWS_X86_64: return "modules_wg";
            case LINUX_X86_64:   return "modules_l";
            case MACOS_ARM64:    return "modules_m";
            default: throw new IllegalArgumentException("No module kit for " + p);
        }
    }

    /**
     * Windows bundles gnuplot; elsewhere it is resolved from PATH.
     * Returns null when not found, so callers can degrade that one feature.
     */
    public File gnuplot() {
        if (platform.isWindows()) {
            return get(GNUPLOT);
        }
        return PlatformLauncher.findOnPath("gnuplot");
    }
}
