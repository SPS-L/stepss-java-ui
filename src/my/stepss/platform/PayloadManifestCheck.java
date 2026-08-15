package my.stepss.platform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Build-time guard for a split that {@code versions.properties} otherwise
 * lets drift silently: the version-suffixed resource names {@link Toolchain}
 * hardcodes (e.g. {@code "payload/ramses-linux-x86_64-v3.55.tar.gz"}) versus
 * whatever {@code stage-payloads} actually copied into
 * {@code src/my/stepss/payload/} based on the asset names in
 * {@code versions.properties}.
 *
 * <p>Bumping only {@code versions.properties} - the procedure the file's own
 * header used to document - fetches and stages the new asset under its new
 * name, verifies its digest, and lets {@code ant clean jar} finish green,
 * because nothing at build time ever compares the staged directory against
 * {@link Toolchain}'s resource strings. The mismatch then surfaces only at
 * launch, on every user's machine, as "Missing bundled resource" followed by
 * {@code System.exit(1)} — the one place a build-time check is cheaper than
 * letting it ship.
 *
 * <p>Invoked from build.xml's {@code -post-compile} target (after
 * {@code stage-payloads} has populated {@code src/my/stepss/payload/} and
 * after javac has produced the classes this needs to read {@link
 * Toolchain#SPECS}), so a mismatch fails the build itself rather than only
 * failing at runtime after the jar has shipped.
 */
public final class PayloadManifestCheck {

    private static final String PAYLOAD_PREFIX = "payload/";

    private PayloadManifestCheck() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: PayloadManifestCheck <staged-payload-dir>");
            System.exit(2);
            return;
        }
        File payloadDir = new File(args[0]);
        List<String> missing = findMissing(payloadDir);

        if (!missing.isEmpty()) {
            System.err.println("Payload manifest check FAILED: " + missing.size()
                    + " resource(s) named in Toolchain.java are not present in "
                    + payloadDir.getAbsolutePath() + ":");
            for (String m : missing) {
                System.err.println("  " + m);
            }
            System.err.println("versions.properties was updated but Toolchain.java's "
                    + "hardcoded payload resource name(s) were not kept in sync (or vice "
                    + "versa). Both must name the same version of the same asset.");
            System.exit(1);
            return;
        }

        System.out.println("Payload manifest check OK: every Toolchain.java payload "
                + "resource is present in " + payloadDir.getAbsolutePath());
    }

    /** @return one description per {@code Toolchain.SPECS} resource missing from {@code payloadDir}. */
    static List<String> findMissing(File payloadDir) {
        List<String> missing = new ArrayList<String>();
        for (ToolSpec spec : Toolchain.SPECS) {
            for (Platform p : Platform.values()) {
                ToolSpec.Payload payload = spec.payloadFor(p);
                if (payload == null || !payload.resource.startsWith(PAYLOAD_PREFIX)) {
                    // RAW payloads such as "dyngraph.exe" or "gpwin.zip" are
                    // committed directly under src/my/stepss/, not fetched
                    // into src/my/stepss/payload/, so they are out of scope
                    // for this check.
                    continue;
                }
                String name = payload.resource.substring(PAYLOAD_PREFIX.length());
                File expected = new File(payloadDir, name);
                if (!expected.isFile()) {
                    missing.add(spec.id() + "/" + p.key() + ": " + payload.resource);
                }
            }
        }
        return missing;
    }
}
