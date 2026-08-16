package my.stepss.examples;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks a bundled example into a directory of the user's, and answers what
 * landed there.
 *
 * <p>Deliberately not routed through {@code Toolchain} and {@code ToolExtractor},
 * which is where every other bundled payload goes. Two reasons, and both matter:
 *
 * <ul>
 * <li>Those are keyed by {@code Platform}. An example is the same bytes
 * everywhere, so it would have to be registered three times to say so.
 * <li>They extract into {@code toolDir}, the temporary directory
 * {@code formWindowClosing} deletes on exit. The second thing a user does with
 * an example is edit it, and losing that on quit with no warning is the one
 * outcome this feature must not have.
 * </ul>
 *
 * <p>So examples get their own short extractor. What it does borrow is the
 * containment check: an archive entry that would escape the target directory is
 * refused rather than written.
 */
public final class ExampleInstaller {

    /** Classpath base the packed payloads resolve against, as for the toolchain. */
    private static final String PAYLOAD_BASE = "/my/stepss/";

    private ExampleInstaller() {
    }

    /**
     * Unpacks {@code example} into {@code target}, creating it if needed.
     *
     * <p>Does not consult or clear {@code target} first. Whether an existing
     * copy is reused, replaced or left alone is the caller's decision, because
     * it is a question for the user rather than for this class.
     *
     * @return the files written, in archive order
     */
    public static List<File> install(Example example, File target) throws IOException {
        String resource = PAYLOAD_BASE + example.payloadResource();
        InputStream in = ExampleInstaller.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("Missing bundled resource '" + resource
                    + "' for example '" + example.id() + "'");
        }
        try {
            return unpack(example.payloadResource(), in, target);
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * Unpacks a zip stream into {@code target}, refusing entries that escape it.
     *
     * <p>Split out from {@link #install} so the containment check can be
     * exercised against a deliberately hostile archive. Reached through a jar
     * resource in production, there is no way to hand this method a zip
     * containing {@code ../}, and a traversal guard that is never run against a
     * traversal is a guard nobody has checked.
     *
     * @param label what to name the payload in an error
     */
    static List<File> unpack(String label, InputStream payload, File target)
            throws IOException {
        mkdirs(target);
        List<File> written = new ArrayList<>();
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(payload));
        try {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    File out = safeChild(target, entry.getName());
                    mkdirs(out.getParentFile());
                    writeStream(zin, out);
                    written.add(out);
                }
                zin.closeEntry();
            }
        } finally {
            closeQuietly(zin);
        }
        if (written.isEmpty()) {
            throw new IOException("Example payload '" + label + "' contained no files.");
        }
        return written;
    }

    /**
     * The files {@code example} names that are not in {@code dir}, empty when
     * the copy is complete.
     *
     * <p>Used after installing, and by the harness. The build already refuses
     * to ship an incomplete example, so a non-empty answer here means the copy
     * on disk was damaged after extraction rather than that the payload is
     * wrong - most likely a user deleting a file from their own copy and then
     * reopening the example.
     */
    public static List<String> missingFrom(Example example, File dir) {
        List<String> missing = new ArrayList<>();
        for (String name : example.retained()) {
            if (!new File(dir, name).isFile()) {
                missing.add(name);
            }
        }
        return missing;
    }

    /** Removes a directory and everything under it. */
    public static boolean deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return true;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                deleteRecursively(kid);
            }
        }
        return f.delete();
    }

    /** Rejects entries that would escape the extraction directory. */
    private static File safeChild(File dir, String entryName) throws IOException {
        File out = new File(dir, entryName);
        String root = dir.getCanonicalPath() + File.separator;
        if (!out.getCanonicalPath().startsWith(root)) {
            throw new IOException("Archive entry escapes target directory: " + entryName);
        }
        return out;
    }

    private static void mkdirs(File d) throws IOException {
        if (d != null && !d.isDirectory() && !d.mkdirs()) {
            throw new IOException("Could not create directory: " + d.getAbsolutePath());
        }
    }

    private static void writeStream(InputStream in, File out) throws IOException {
        OutputStream fos = new FileOutputStream(out);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        } finally {
            fos.close();
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
}
