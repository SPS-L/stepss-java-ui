package my.ramses.platform;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ToolExtractor {

    /**
     * Fixed classpath base that bundled tool payloads resolve against.
     * {@code ToolExtractor} lives one package deeper than the payloads
     * (which ship under {@code my/ramses/}, alongside {@code RamsesUI}), so
     * a class-relative {@code getResourceAsStream} would look under
     * {@code my/ramses/platform/} instead. Resolving against this absolute
     * base keeps ToolExtractor free of any dependency on the GUI class, and
     * composes correctly if payload resource strings later move into a
     * subdirectory (e.g. {@code "payload/ramses-linux-x86_64-v3.55.tar.gz"}
     * resolves to {@code /my/ramses/payload/ramses-linux-x86_64-v3.55.tar.gz}).
     */
    private static final String PAYLOAD_BASE = "/my/ramses/";

    /**
     * Fallback recorded in the stamp when the version file is missing or
     * empty, so a broken/absent version resource degrades the staleness
     * check rather than making the toolchain unusable.
     */
    private static final String UNKNOWN_VERSION = "unknown";

    /**
     * Resolved once per JVM run and cached; see {@link #appVersion()}.
     */
    private static volatile String cachedVersion;

    private ToolExtractor() {
    }

    public static File extract(ToolSpec spec, Platform platform, File dir)
            throws IOException {
        ToolSpec.Payload payload = spec.payloadFor(platform);
        if (payload == null) {
            throw new IOException("Tool '" + spec.id() + "' is not available on "
                    + platform + " (" + Platform.describe() + ")");
        }

        File stamp = new File(dir, ".stepss-payload-" + spec.id());
        File target = new File(dir, payload.extractedName);

        // Every payload.resource in Toolchain.java is version-invariant
        // ("dynsim.zip", "DOC.zip", ...), so the resource name alone cannot
        // tell a rebuilt payload from an old one shipped under the same
        // release. Folding in the application version means an upgrade that
        // rebuilds a same-named archive still busts the stamp.
        String stampValue = payload.resource + "|" + appVersion();

        if (!isCurrent(stamp, stampValue) || !target.exists()) {
            deleteRecursively(new File(dir, topLevelOf(payload.extractedName)));
            unpack(spec, payload, dir, platform);
            writeStamp(stamp, stampValue);
        }

        if (!target.exists()) {
            throw new IOException("Extraction of '" + spec.id() + "' did not produce "
                    + target.getAbsolutePath());
        }
        if (payload.executable && !target.setExecutable(true)) {
            throw new IOException("Could not mark executable: " + target.getAbsolutePath());
        }
        return target;
    }

    private static void unpack(ToolSpec spec, ToolSpec.Payload payload, File dir,
            Platform platform) throws IOException {
        String resolved = PAYLOAD_BASE + payload.resource;
        InputStream in = ToolExtractor.class.getResourceAsStream(resolved);
        if (in == null) {
            throw new IOException("Missing bundled resource '" + resolved
                    + "' for tool '" + spec.id() + "'");
        }
        try {
            switch (payload.kind) {
                case RAW:
                    writeStream(in, new File(dir, payload.extractedName));
                    break;
                case ZIP:
                    unpackZip(in, dir, payload);
                    break;
                case TGZ:
                    unpackTgz(in, dir, payload);
                    break;
                case ZIP_TREE:
                    unpackZipTree(in, dir, payload);
                    break;
                default:
                    throw new IOException("Unknown payload kind for " + spec.id());
            }
        } finally {
            closeQuietly(in);
        }

        // Apple's Gatekeeper quarantines files that arrive via a browser/unzip
        // origin flag; freshly unpacked binaries copied out of a JAR resource are
        // not affected today, but clearing is a cheap, idempotent no-op when
        // there is nothing to clear, and it is the only way to guarantee a
        // working toolchain on a Mac where the flag *is* present. Unconditional
        // pending a hardware spike (Task 1) that may later show this is
        // unnecessary and let it be narrowed or removed.
        if (platformNeedsQuarantineClear(platform)) {
            clearQuarantine(dir);
        }
    }

    private static void unpackZip(InputStream raw, File dir, ToolSpec.Payload payload)
            throws IOException {
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw));
        try {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (payload.member != null && !name.equals(payload.member)) {
                    zin.closeEntry();
                    continue;
                }
                File out = payload.member != null
                        ? new File(dir, payload.extractedName)
                        : safeChild(dir, name);
                if (entry.isDirectory()) {
                    mkdirs(out);
                } else {
                    mkdirs(out.getParentFile());
                    writeStream(zin, out);
                }
                zin.closeEntry();
            }
        } finally {
            closeQuietly(zin);
        }
    }

    /**
     * Unpacks the entries matching {@code payload.retain} into
     * {@code dir/payload.extractedName/}, preserving their relative paths.
     * Used for the uramses kit, where only the running platform's module
     * directory is wanted: shipping all three costs ~5 MB in the jar but
     * unpacking all three would cost ~35 MB on disk for no gain.
     */
    private static void unpackZipTree(InputStream raw, File dir, ToolSpec.Payload payload)
            throws IOException {
        File root = new File(dir, payload.extractedName);
        mkdirs(root);
        int written = 0;
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw));
        try {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && retains(payload, name)) {
                    File out = safeChild(root, name);
                    mkdirs(out.getParentFile());
                    writeStream(zin, out);
                    written++;
                }
                zin.closeEntry();
            }
        } finally {
            closeQuietly(zin);
        }
        if (written == 0) {
            throw new IOException("Payload '" + payload.resource
                    + "' produced no files for the retained prefixes " + payload.retain
                    + ". The archive layout and the manifest disagree.");
        }
    }

    private static boolean retains(ToolSpec.Payload payload, String name) {
        for (String prefix : payload.retain) {
            if (prefix.endsWith("/") ? name.startsWith(prefix) : name.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Minimal POSIX/USTAR reader. Commons Compress is not a dependency of this
     * project and the release tarballs are plain ustar, so a 512-byte header
     * walk is sufficient and avoids adding a library.
     */
    private static void unpackTgz(InputStream raw, File dir, ToolSpec.Payload payload)
            throws IOException {
        GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(raw));
        try {
            byte[] header = new byte[512];
            while (true) {
                if (!readFully(gz, header, 512)) {
                    break;
                }
                String name = cString(header, 0, 100);
                if (name.isEmpty()) {
                    break;
                }
                long size = octal(header, 124, 12, "size", name);
                char type = (char) header[156];

                boolean wanted = payload.member == null || name.equals(payload.member);
                if (wanted && isRegularFileType(type)) {
                    File out = payload.member != null
                            ? new File(dir, payload.extractedName)
                            : safeChild(dir, name);
                    mkdirs(out.getParentFile());
                    copyExactly(gz, out, size);
                } else if (wanted && type == '5') {
                    mkdirs(safeChild(dir, name));
                    skipExactly(gz, size);
                } else if (isRegularFileType(type) || type == '5') {
                    skipExactly(gz, size);
                } else {
                    throw new IOException("Unsupported tar entry type '" + type
                            + "' for entry '" + name + "'");
                }
                long pad = (512 - (size % 512)) % 512;
                skipExactly(gz, pad);
            }
        } finally {
            closeQuietly(gz);
        }
    }

    /**
     * ustar type {@code '0'} (REGTYPE) and the historic POSIX synonym
     * {@code '\0'} (AREGTYPE, a zero byte rather than the character '0')
     * both denote a plain file; some writers still emit AREGTYPE, and
     * `tar tzvf` cannot distinguish it from REGTYPE (both list as '-'), so
     * both must be accepted rather than only REGTYPE.
     */
    private static boolean isRegularFileType(char type) {
        return type == '0' || type == 0;
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

    /**
     * Returns the first path segment of {@code extractedName}, which the
     * caller deletes before unpacking. Rejects a segment that would resolve
     * to the extraction directory itself or its parent ({@code ""}, from a
     * leading slash; {@code "."}; {@code ".."}) so a malformed manifest
     * entry fails loudly instead of {@code deleteRecursively} wiping the
     * whole extraction directory (which may be the user's own working
     * directory) or reaching outside it.
     */
    private static String topLevelOf(String extractedName) throws IOException {
        int slash = extractedName.indexOf('/');
        String top = slash < 0 ? extractedName : extractedName.substring(0, slash);
        if (top.isEmpty() || top.equals(".") || top.equals("..")) {
            throw new IOException("Tool payload has an unsafe extractedName '"
                    + extractedName + "': top-level segment '" + top
                    + "' would resolve to the extraction directory itself or its parent");
        }
        return top;
    }

    private static boolean isCurrent(File stamp, String value) {
        if (!stamp.isFile()) {
            return false;
        }
        try {
            byte[] buf = new byte[(int) stamp.length()];
            java.io.FileInputStream fin = new java.io.FileInputStream(stamp);
            try {
                if (fin.read(buf) != buf.length) {
                    return false;
                }
            } finally {
                fin.close();
            }
            return new String(buf, "UTF-8").trim().equals(value);
        } catch (IOException ex) {
            return false;
        }
    }

    private static void writeStamp(File stamp, String value) throws IOException {
        OutputStream out = new FileOutputStream(stamp);
        try {
            out.write(value.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    /**
     * Reads and trims {@code /my/ramses/version.txt} once per JVM run and
     * caches the result. A missing or empty version file falls back to
     * {@link #UNKNOWN_VERSION} rather than throwing: a broken version
     * resource must degrade the staleness check, not make the toolchain
     * unusable. Deliberately not re-read per call or hashed from the
     * payload streams themselves — the payloads total ~130 MB, and hashing
     * them on every startup would be far more costly than the staleness
     * bug this guards against.
     */
    private static String appVersion() {
        String v = cachedVersion;
        if (v != null) {
            return v;
        }
        v = readVersionResource();
        cachedVersion = v;
        return v;
    }

    private static String readVersionResource() {
        InputStream in = ToolExtractor.class.getResourceAsStream(PAYLOAD_BASE + "version.txt");
        if (in == null) {
            return UNKNOWN_VERSION;
        }
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bytes.write(buf, 0, n);
            }
            String text = new String(bytes.toByteArray(), "UTF-8").trim();
            return text.isEmpty() ? UNKNOWN_VERSION : text;
        } catch (IOException ex) {
            return UNKNOWN_VERSION;
        } finally {
            closeQuietly(in);
        }
    }

    private static void mkdirs(File d) throws IOException {
        if (d != null && !d.isDirectory() && !d.mkdirs()) {
            throw new IOException("Could not create directory: " + d.getAbsolutePath());
        }
    }

    private static void writeStream(InputStream in, File out) throws IOException {
        mkdirs(out.getParentFile());
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

    private static void copyExactly(InputStream in, File out, long size)
            throws IOException {
        OutputStream fos = new FileOutputStream(out);
        try {
            byte[] buf = new byte[65536];
            long left = size;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) {
                    throw new IOException("Truncated archive entry: " + out.getName());
                }
                fos.write(buf, 0, n);
                left -= n;
            }
        } finally {
            fos.close();
        }
    }

    private static void skipExactly(InputStream in, long n) throws IOException {
        long left = n;
        byte[] scratch = new byte[8192];
        while (left > 0) {
            int r = in.read(scratch, 0, (int) Math.min(scratch.length, left));
            if (r < 0) {
                throw new IOException("Truncated archive");
            }
            left -= r;
        }
    }

    /**
     * Reads exactly {@code len} bytes into {@code buf}, or reports a clean
     * end-of-stream. A clean EOF (nothing read at all, {@code off == 0})
     * returns {@code false} so the caller stops normally. A partial read
     * followed by EOF means the archive was cut off mid-header and is
     * corrupt, so that case throws instead of silently returning.
     */
    private static boolean readFully(InputStream in, byte[] buf, int len)
            throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) {
                if (off == 0) {
                    return false;
                }
                throw new IOException("Truncated archive");
            }
            off += n;
        }
        return true;
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) {
            end++;
        }
        return new String(b, off, end - off).trim();
    }

    /**
     * Parses a fixed-width ustar numeric field as octal ASCII. Wraps
     * {@link NumberFormatException} (e.g. a non-octal field, or a GNU
     * base-256-encoded size this minimal reader does not support) as an
     * {@link IOException} naming the field and entry, since this reader
     * only declares checked {@code IOException} and an unchecked exception
     * here would otherwise cross that contract and crash callers that
     * catch only {@code IOException} (such as the UI thread).
     */
    private static long octal(byte[] b, int off, int len, String field, String entryName)
            throws IOException {
        String s = cString(b, off, len);
        if (s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException ex) {
            throw new IOException("Malformed tar header: field '" + field
                    + "' ('" + s + "') is not valid octal for entry '" + entryName + "'", ex);
        }
    }

    static boolean deleteRecursively(File f) {
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

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignore) {
        }
    }

    private static boolean platformNeedsQuarantineClear(Platform platform) {
        return platform == Platform.MACOS_ARM64;
    }

    /**
     * Clears the macOS quarantine attribute recursively. Always attempted on
     * MACOS_ARM64 (see the note in {@link #unpack}); bounded by a timeout and
     * never allowed to fail extraction, since a missing/erroring {@code xattr}
     * must not break a toolchain on a machine where quarantine was never set.
     */
    private static void clearQuarantine(File dir) {
        try {
            Process p = new ProcessBuilder("xattr", "-dr", "com.apple.quarantine",
                    dir.getAbsolutePath()).redirectErrorStream(true).start();
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            // xattr not present or not runnable: nothing to clear, or the
            // platform never applied quarantine in the first place.
        }
    }
}
