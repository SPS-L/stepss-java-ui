package my.stepss.examples;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import my.stepss.config.ScenarioFile;

/**
 * Build-time step that turns one example repository's source archive into the
 * slice the interface ships.
 *
 * <p>Runs from build.xml's {@code stage-payloads}, once per example, following
 * {@code UramsesKitPack}: a small {@code main()} on the build classpath rather
 * than a custom Ant task.
 *
 * <p>Verification is by content, not container, for the reason
 * {@code UramsesKitPack} documents at length: GitHub's auto-generated source
 * archives carry no byte-stability guarantee, so a digest pinned against the
 * downloaded zip can fail months later on unchanged content. This builds a
 * manifest - one {@code <sha256>  <path>} line per retained file, sorted by
 * path - and digests that. Re-compression is invisible to it; a changed byte in
 * any retained file is not.
 *
 * <p>What it adds over {@code UramsesKitPack} is the completeness check. The
 * retained set is not a list this class owns, it is every file the descriptor
 * entry names, so an upstream release that stopped carrying one of them fails
 * the build here, naming the file. That is the whole guard behind
 * refreshed-on-release: without it a bad upstream release becomes a menu entry
 * that opens onto missing slots on a user's machine.
 *
 * <p>Since the descriptor names a scenario file rather than a slot per file,
 * there is a second half to that guard: the {@code .cfg} is read out of the
 * archive and every path it names is checked against the retained set. Without
 * it an entry could ship a scenario naming a data file that {@code .extra}
 * forgot, and the example would open onto a slot pointing at nothing. The
 * completeness check above cannot see that, because it only looks at names the
 * descriptor already knows.
 */
public final class ExamplesPack {

    private ExamplesPack() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Usage: ExamplesPack <descriptor.properties> <id>"
                    + " <source.zip> <output.zip> <manifest-sha256|COMPUTE>");
            System.exit(2);
            return;
        }
        File descriptor = new File(args[0]);
        String id = args[1];
        File source = new File(args[2]);
        File output = new File(args[3]);
        String expected = args[4];

        Example example = read(descriptor, id);

        ZipFile zip = new ZipFile(source);
        try {
            Map<String, ZipEntry> present = filesByRelativePath(zip);
            List<String> retained = example.retained();

            List<String> missing = new ArrayList<>();
            for (String rel : retained) {
                if (!present.containsKey(rel)) {
                    missing.add(rel);
                }
            }
            if (!missing.isEmpty()) {
                System.err.println("example '" + id + "' completeness check FAILED");
                for (String rel : missing) {
                    System.err.println("  " + rel + " is absent from " + source.getName());
                }
                System.err.println("The descriptor names " + retained.size() + " file(s) and"
                        + " the pinned release of " + example.name() + " carries "
                        + (retained.size() - missing.size()) + " of them. Opening this"
                        + " example would leave slots pointing at files that do not exist,"
                        + " so the build stops here instead. Either the upstream release"
                        + " dropped or renamed the file - fix it upstream and re-pin - or"
                        + " src/my/stepss/examples/examples.properties needs updating to"
                        + " match what the example now ships.");
                System.exit(1);
                return;
            }

            String unpacked = scenarioNamesOutside(zip, present, example);
            if (unpacked != null) {
                System.err.println("example '" + id + "' scenario check FAILED");
                System.err.println("  " + example.cfg() + " names " + unpacked
                        + ", which this example does not ship.");
                System.err.println("Opening the example would fill a slot with a path"
                        + " to a file that is not there. Add it to " + id + ".extra in"
                        + " src/my/stepss/examples/examples.properties, or take it out"
                        + " of the scenario file upstream and re-pin.");
                System.exit(1);
                return;
            }

            String manifest = manifestOf(zip, present, sorted(retained));
            String digest = hex(sha256(manifest.getBytes("UTF-8")));

            if ("COMPUTE".equals(expected)) {
                System.out.println(id + ".manifest.sha256=" + digest);
                System.out.println("Retained " + retained.size() + " files. Set the digest"
                        + " in versions.properties and re-run.");
                return;
            }
            if (!digest.equals(expected)) {
                System.err.println("example '" + id + "' manifest check FAILED");
                System.err.println("  expected " + expected);
                System.err.println("  actual   " + digest);
                System.err.println("The retained contents of " + source.getName()
                        + " differ from what versions.properties pins. This is a content"
                        + " change upstream, not a re-compression: re-compression does not"
                        + " affect this digest.");
                System.exit(1);
                return;
            }

            repack(zip, present, sorted(retained), output);
            System.out.println("example '" + id + "' OK: " + retained.size()
                    + " files -> " + output.getAbsolutePath()
                    + " (" + output.length() / 1024 + " KB)");
        } finally {
            zip.close();
        }
    }

    /**
     * The first path the example's scenario file names that the payload will
     * not carry, or null when every one of them is retained.
     *
     * <p>Compared against {@code retained()} rather than against the archive:
     * a file can be present upstream and still not be packed, and it is being
     * packed that decides whether the slot resolves on a user's machine.
     */
    private static String scenarioNamesOutside(ZipFile zip, Map<String, ZipEntry> present,
            Example example) throws IOException {
        List<String> retained = example.retained();
        Reader in = new InputStreamReader(
                zip.getInputStream(present.get(example.cfg())), StandardCharsets.UTF_8);
        List<String> named;
        try {
            named = ScenarioFile.storedPaths(in);
        } finally {
            in.close();
        }
        for (String path : named) {
            if (!retained.contains(path)) {
                return path;
            }
        }
        return null;
    }

    private static Example read(File descriptor, String id) throws IOException {
        InputStream in = new FileInputStream(descriptor);
        try {
            return ExampleCatalog.byId(ExampleCatalog.load(in), id);
        } finally {
            in.close();
        }
    }

    /** A copy sorted by path, so the manifest and the repacked zip are deterministic. */
    private static List<String> sorted(List<String> paths) {
        List<String> copy = new ArrayList<>(paths);
        java.util.Collections.sort(copy);
        return copy;
    }

    /**
     * Every file in the archive, keyed by its path with the single top-level
     * directory stripped.
     *
     * <p>Built once and looked up, rather than scanning the archive per file as
     * {@code UramsesKitPack} does: the Nordic entry names 40 files, and a scan
     * each would be 40 walks of a 3.9 MB archive for no reason.
     */
    private static Map<String, ZipEntry> filesByRelativePath(ZipFile zip) {
        Map<String, ZipEntry> files = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String rel = stripTopLevel(entry.getName());
            if (rel != null) {
                files.put(rel, entry);
            }
        }
        return files;
    }

    /** {@code stepss-Kundur-Two-Area-System-1.0.0/lf.dat} -> {@code lf.dat}. */
    static String stripTopLevel(String name) {
        int slash = name.indexOf('/');
        if (slash < 0 || slash == name.length() - 1) {
            return null;
        }
        return name.substring(slash + 1);
    }

    /** One {@code <sha256>  <path>} line per retained file, in sorted path order. */
    private static String manifestOf(ZipFile zip, Map<String, ZipEntry> present,
            List<String> retained) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String rel : retained) {
            InputStream in = zip.getInputStream(present.get(rel));
            try {
                sb.append(hex(sha256(readAll(in)))).append("  ").append(rel).append('\n');
            } finally {
                in.close();
            }
        }
        return sb.toString();
    }

    /**
     * Writes the retained files into {@code output}, flat paths preserved.
     *
     * <p>Entry times are zeroed, as in {@code UramsesKitPack}, so two builds of
     * the same pin produce the same bytes.
     */
    private static void repack(ZipFile zip, Map<String, ZipEntry> present,
            List<String> retained, File output) throws IOException {
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output));
        try {
            for (String rel : retained) {
                ZipEntry dst = new ZipEntry(rel);
                dst.setTime(0L);
                out.putNextEntry(dst);
                InputStream in = zip.getInputStream(present.get(rel));
                try {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                } finally {
                    in.close();
                }
                out.closeEntry();
            }
        } finally {
            out.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            bytes.write(buf, 0, n);
        }
        return bytes.toByteArray();
    }

    private static byte[] sha256(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable", ex);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            sb.append(Character.forDigit((b[i] >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b[i] & 0xf, 16));
        }
        return sb.toString();
    }
}
