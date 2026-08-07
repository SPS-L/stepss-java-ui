package my.ramses.platform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Toolchain {

    public static final String RAMSES = "ramses";
    public static final String PFC = "pfc";
    public static final String DYNGRAPH = "dyngraph";
    public static final String CODEGEN = "codegen";
    public static final String GNUPLOT = "gnuplot";
    public static final String VSWHERE = "vswhere";
    public static final String USERGUIDE = "userguide";
    public static final String URAMSES = "uramses";
    public static final String NPP = "npp";

    public static final List<ToolSpec> SPECS = buildSpecs();

    private static List<ToolSpec> buildSpecs() {
        List<ToolSpec> s = new ArrayList<ToolSpec>();

        s.add(new ToolSpec(RAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "dynsim.zip", ToolSpec.Kind.ZIP, null, "dynsim/dynsim.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "dynsim.zip", ToolSpec.Kind.ZIP, null, "dynsim/dynsim", true)));

        s.add(new ToolSpec(PFC)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "PFC.exe", ToolSpec.Kind.RAW, null, "PFC.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "PFC", ToolSpec.Kind.RAW, null, "PFC", true)));

        s.add(new ToolSpec(DYNGRAPH)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "dyngraph.exe", ToolSpec.Kind.RAW, null, "dyngraph.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "dyngraph", ToolSpec.Kind.RAW, null, "dyngraph", true)));

        s.add(new ToolSpec(CODEGEN)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "codegen.exe", ToolSpec.Kind.RAW, null, "codegen.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "CODEGEN", ToolSpec.Kind.RAW, null, "CODEGEN", true)));

        s.add(new ToolSpec(GNUPLOT)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "gpwin.zip", ToolSpec.Kind.ZIP, null, "gnuplot/bin/pgnuplot.exe", false)));

        s.add(new ToolSpec(VSWHERE)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "vswhere.exe", ToolSpec.Kind.RAW, null, "vswhere.exe", true)));

        s.add(new ToolSpec(NPP)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "npp.zip", ToolSpec.Kind.ZIP, null, "notepad++.exe", true))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "npp.zip", ToolSpec.Kind.ZIP, null, "notepad++.exe", true)));

        s.add(new ToolSpec(USERGUIDE)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "DOC.zip", ToolSpec.Kind.ZIP, null, "DOC/userguide.pdf", false))
            .on(Platform.LINUX_X86_64, new ToolSpec.Payload(
                "DOC.zip", ToolSpec.Kind.ZIP, null, "DOC/userguide.pdf", false)));

        s.add(new ToolSpec(URAMSES)
            .on(Platform.WINDOWS_X86_64, new ToolSpec.Payload(
                "URAMSES.zip", ToolSpec.Kind.ZIP, null, "URAMSES", false)));

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

    /** Extracts every tool available on this platform. */
    public void extractAll() throws IOException {
        for (ToolSpec spec : SPECS) {
            if (spec.availableOn(platform)) {
                resolved.put(spec.id(), ToolExtractor.extract(spec, platform, dir));
            }
        }
    }

    /** @return the extracted file, or null if this tool is not available here. */
    public File get(String id) {
        return resolved.get(id);
    }

    public File ramses()    { return get(RAMSES); }
    public File pfc()       { return get(PFC); }
    public File dyngraph()  { return get(DYNGRAPH); }
    public File codegen()   { return get(CODEGEN); }
    public File vswhere()   { return get(VSWHERE); }
    public File npp()       { return get(NPP); }
    public File userGuide() { return get(USERGUIDE); }

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
