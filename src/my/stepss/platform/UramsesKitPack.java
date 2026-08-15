package my.stepss.platform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Build-time step that turns the published stepss-uramses source archive into
 * the slice the GUI ships.
 *
 * <p>Runs from build.xml's {@code stage-payloads}, following the pattern set by
 * {@link PayloadManifestCheck}: a small {@code main()} on the build classpath
 * rather than custom Ant tasks.
 *
 * <p>Verification is by content, not container. GitHub's auto-generated source
 * archives carry no byte-stability guarantee, so a digest pinned against the
 * downloaded zip can fail months later on unchanged content. Instead this
 * builds a manifest - one {@code <sha256>  <path>} line per retained file,
 * sorted by path - and digests that. Re-compression by GitHub is invisible;
 * a single changed byte in any retained file is not.
 */
public final class UramsesKitPack {

    /** Path prefixes retained from the source archive, after stripping its top-level directory. */
    private static final List<String> RETAIN = Collections.unmodifiableList(Arrays.asList(
            "build/Makefile.linux",
            "build/Makefile.macos",
            "build/Makefile.windows",
            "src/",
            "custom_models/",
            "tools/",
            "modules_l/",
            "modules_m/",
            "modules_wg/",
            "README.md",
            "LICENSE.rst"));

    private UramsesKitPack() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: UramsesKitPack <source.zip> <output.zip> <manifest-sha256|COMPUTE>");
            System.exit(2);
            return;
        }
        File source = new File(args[0]);
        File output = new File(args[1]);
        String expected = args[2];

        ZipFile zip = new ZipFile(source);
        try {
            List<String> entries = retainedEntries(zip);
            if (entries.isEmpty()) {
                System.err.println("No retained entries found in " + source
                        + ". Is this a stepss-uramses source archive?");
                System.exit(1);
                return;
            }
            String manifest = manifestOf(zip, entries);
            String digest = hex(sha256(manifest.getBytes("UTF-8")));

            if ("COMPUTE".equals(expected)) {
                System.out.println("uramses.manifest.sha256=" + digest);
                System.out.println("Retained " + entries.size() + " files. Set the digest in "
                        + "versions.properties and re-run.");
                return;
            }
            if (!digest.equals(expected)) {
                System.err.println("uramses kit manifest check FAILED");
                System.err.println("  expected " + expected);
                System.err.println("  actual   " + digest);
                System.err.println("The retained contents of " + source.getName()
                        + " differ from what versions.properties pins. This is a content"
                        + " change upstream, not a re-compression: re-compression does not"
                        + " affect this digest.");
                System.exit(1);
                return;
            }
            repack(zip, entries, output);
            System.out.println("uramses kit OK: " + entries.size() + " files -> "
                    + output.getAbsolutePath() + " (" + output.length() / 1024 + " KB)");
        } finally {
            zip.close();
        }
    }

    /**
     * Entry names with the archive's single top-level directory stripped,
     * filtered to {@link #RETAIN} and sorted. Directory entries are dropped:
     * the repacked zip carries files only, and the extractor recreates parents.
     */
    static List<String> retainedEntries(ZipFile zip) {
        List<String> kept = new ArrayList<String>();
        java.util.Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) {
                continue;
            }
            String rel = stripTopLevel(e.getName());
            if (rel != null && isRetained(rel)) {
                kept.add(rel);
            }
        }
        Collections.sort(kept);
        return kept;
    }

    /** {@code stepss-uramses-3.55/build/Makefile.linux} -> {@code build/Makefile.linux}. */
    static String stripTopLevel(String name) {
        int slash = name.indexOf('/');
        if (slash < 0 || slash == name.length() - 1) {
            return null;
        }
        return name.substring(slash + 1);
    }

    static boolean isRetained(String rel) {
        for (String prefix : RETAIN) {
            if (prefix.endsWith("/") ? rel.startsWith(prefix) : rel.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** One {@code <sha256>  <path>} line per retained file, in sorted path order. */
    static String manifestOf(ZipFile zip, List<String> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String rel : entries) {
            ZipEntry e = entryFor(zip, rel);
            InputStream in = zip.getInputStream(e);
            try {
                sb.append(hex(sha256(readAll(in)))).append("  ").append(rel).append('\n');
            } finally {
                in.close();
            }
        }
        return sb.toString();
    }

    private static ZipEntry entryFor(ZipFile zip, String rel) throws IOException {
        java.util.Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (rel.equals(stripTopLevel(e.getName()))) {
                return e;
            }
        }
        throw new IOException("Entry vanished from archive: " + rel);
    }

    static void repack(ZipFile zip, List<String> entries, File output) throws IOException {
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output));
        try {
            for (String rel : entries) {
                ZipEntry src = entryFor(zip, rel);
                ZipEntry dst = new ZipEntry(rel);
                dst.setTime(0L);
                out.putNextEntry(dst);
                InputStream in = zip.getInputStream(src);
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
