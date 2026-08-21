package my.stepss.ssa;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * One small-signal run in one file: the dynamic Jacobian the analysis reduced,
 * the modes, participation factors and mode shapes it produced, and a manifest
 * naming the run.
 *
 * <p><b>This is a record of a result, not a reproducible input.</b> The data
 * files, the solver settings and the disturbance that produced the run are
 * deliberately not in it. An archive lets someone else look at the same
 * eigenvalues, the same participation factors and the same matrix; it does not
 * let them re-run the analysis. That is the smaller of the two features the
 * issue weighed, and it is the one the buttons are named after.
 *
 * <p>The manifest is what makes the archive ours, and it is the only thing
 * {@link #load} trusts to say what the run was called. Without it an archive is
 * refused rather than guessed at, because the alternative is opening an empty
 * results window on someone's holiday photos.
 *
 * <p>Pure file handling, like the rest of this package: no Swing, no
 * commons-exec, nothing that starts a process. That is what keeps
 * {@code tools/ssa-harness.sh} able to exercise it with only
 * {@code build/classes} on the classpath.
 */
public final class SsaArchive {

    /**
     * Written by ssa.f90 under the run's basename. {@code _pf} and {@code _ms}
     * are optional for the reason {@link SsaResults} gives: a v1 engine wrote
     * them only when at least one mode passed {@code real_limit}, so a run
     * that filtered everything left neither. Only the modes file is required,
     * and {@link #save} refuses without it.
     */
    public static final String[] RESULT_SUFFIXES = {
        "_modes.dat", "_pf.dat", "_ms.dat",
    };

    /**
     * The members whose absence is a result rather than a fault. A v1 engine
     * wrote these two only when at least one mode passed {@code real_limit},
     * so a run that filtered everything left neither, and an archive of one
     * without them is complete. A v2 engine writes both for every run, so
     * their absence there means an incomplete copy instead; that is still not
     * a reason to refuse an archive of what does exist.
     */
    public static final String[] OPTIONAL_SUFFIXES = {"_pf.dat", "_ms.dat"};

    /** Names the manifest inside the archive. */
    public static final String MANIFEST_NAME = "stepss-ssa.txt";

    /** The manifest's first line, up to the format version. */
    public static final String MAGIC_PREFIX = "# STEPSS small-signal archive v";

    /** The format this build writes, and the newest it will read. */
    public static final int FORMAT_VERSION = 1;

    private SsaArchive() {
    }

    /** The two archive formats offered, and how each is spelled on disk. */
    public enum Format {

        ZIP(".zip", "Zip archive (*.zip)"),
        TAR_GZ(".tar.gz", "Gzipped tar archive (*.tar.gz)");

        private final String extension;
        private final String description;

        Format(String extension, String description) {
            this.extension = extension;
            this.description = description;
        }

        public String extension() {
            return extension;
        }

        public String description() {
            return description;
        }
    }

    /**
     * What the archive records about the run inside it.
     *
     * <p>Every field but the basename may be null, and null means "not
     * recorded" rather than zero. The two threshold fields are read but never
     * written now: they carried the {@code real_limit} and {@code
     * pf_threshold} of the EIG record, which no longer has either, and the
     * one floor the engine still applies is written into the modes file
     * itself as {@code pf_floor}. They stay on this class so that an archive
     * saved by an older build still reports what it was analysed under. That
     * is the same distinction {@link SsaModes} draws for the results header,
     * and the results window already renders it as "not recorded".
     */
    public static final class Manifest {

        private final String basename;
        private final Double engineVersion;
        private final Double time;
        private final Double realLimit;
        private final Double pfThreshold;
        private final String savedBy;

        public Manifest(String basename, Double engineVersion, Double time,
                Double realLimit, Double pfThreshold, String savedBy) {
            this.basename = basename;
            this.engineVersion = engineVersion;
            this.time = time;
            this.realLimit = realLimit;
            this.pfThreshold = pfThreshold;
            this.savedBy = savedBy;
        }

        public String basename() {
            return basename;
        }

        /** The engine's own banner version, or null if it could not be read. */
        public Double engineVersion() {
            return engineVersion;
        }

        public Double time() {
            return time;
        }

        public Double realLimit() {
            return realLimit;
        }

        public Double pfThreshold() {
            return pfThreshold;
        }

        /** The STEPSS version that wrote the archive, or null. */
        public String savedBy() {
            return savedBy;
        }

        /**
         * The manifest file's contents.
         *
         * <p>A key per line, absent keys omitted rather than written as zero,
         * read back by name so a later format can add one without breaking
         * this one. The prose at the top is for whoever opens the archive in a
         * file manager and wants to know what they have without installing
         * anything.
         */
        public String text() {
            StringBuilder text = new StringBuilder();
            text.append(MAGIC_PREFIX).append(FORMAT_VERSION).append('\n');
            text.append("#\n");
            text.append("# One small-signal run, as it was analysed: the dynamic"
                    + " Jacobian the engine\n");
            text.append("# reduced, the modes, participation factors and mode"
                    + " shapes it produced, and\n");
            text.append("# this file. The data files, solver settings and"
                    + " disturbance that produced\n");
            text.append("# them are NOT here, so this records a result rather"
                    + " than reproducing it.\n");
            text.append("#\n");
            text.append("# Open it with Load dynamic Jacobian on the STEPSS"
                    + " Analysis tab.\n");
            text.append("#\n");
            text.append("basename ").append(basename).append('\n');
            append(text, "engine_version", engineVersion, "%.2f");
            append(text, "t", time, "%.6f");
            append(text, "real_limit", realLimit, "%.6f");
            append(text, "pf_threshold", pfThreshold, "%.6f");
            if (savedBy != null && !savedBy.isEmpty()) {
                text.append("saved_by ").append(savedBy).append('\n');
            }
            return text.toString();
        }

        /**
         * Reads a manifest back.
         *
         * @param text the manifest file's contents
         * @return the run it describes
         * @throws IOException if the text is not a manifest at all, was
         *     written by a newer STEPSS, or names a basename this build would
         *     refuse to write
         */
        public static Manifest parse(String text) throws IOException {
            int version = -1;
            String basename = null;
            Double engineVersion = null;
            Double time = null;
            Double realLimit = null;
            Double pfThreshold = null;
            String savedBy = null;
            for (String raw : text.split("\r\n|\n|\r", -1)) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith(MAGIC_PREFIX)) {
                    version = intOrMinusOne(line.substring(MAGIC_PREFIX.length()).trim());
                    continue;
                }
                if (line.charAt(0) == '#') {
                    continue;
                }
                int gap = firstBlank(line);
                if (gap < 0) {
                    continue;
                }
                String key = line.substring(0, gap);
                String value = line.substring(gap).trim();
                if ("basename".equals(key)) {
                    basename = value;
                } else if ("engine_version".equals(key)) {
                    engineVersion = doubleOrNull(value);
                } else if ("t".equals(key)) {
                    time = doubleOrNull(value);
                } else if ("real_limit".equals(key)) {
                    realLimit = doubleOrNull(value);
                } else if ("pf_threshold".equals(key)) {
                    pfThreshold = doubleOrNull(value);
                } else if ("saved_by".equals(key)) {
                    savedBy = value;
                }
            }
            if (version < 0) {
                throw new IOException("its " + MANIFEST_NAME
                        + " does not start with \"" + MAGIC_PREFIX + "\".");
            }
            if (version > FORMAT_VERSION) {
                throw new IOException("it is in archive format v" + version
                        + " and this STEPSS reads v" + FORMAT_VERSION
                        + ". Update STEPSS to open it.");
            }
            // The basename becomes a file name the moment it is used, and it
            // arrives from a file someone else wrote, so it is held to exactly
            // the rule the UI holds its own basenames to rather than trusted.
            if (!SsaDisturbance.validBasename(basename)) {
                throw new IOException("its " + MANIFEST_NAME
                        + " names an unusable basename \"" + basename + "\".");
            }
            return new Manifest(basename, engineVersion, time, realLimit,
                    pfThreshold, savedBy);
        }

        private static void append(StringBuilder text, String key, Double value,
                String format) {
            if (value != null) {
                text.append(key).append(' ')
                        .append(String.format(Locale.ROOT, format, value))
                        .append('\n');
            }
        }

        private static int firstBlank(String line) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ' || line.charAt(i) == '\t') {
                    return i;
                }
            }
            return -1;
        }

        private static int intOrMinusOne(String token) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                return -1;
            }
        }

        private static Double doubleOrNull(String token) {
            try {
                return Double.valueOf(token);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    /** One archive opened: the run it holds, and what it says about itself. */
    public static final class Loaded {

        private final SsaResults results;
        private final Manifest manifest;

        Loaded(SsaResults results, Manifest manifest) {
            this.results = results;
            this.manifest = manifest;
        }

        public SsaResults results() {
            return results;
        }

        public Manifest manifest() {
            return manifest;
        }
    }

    /**
     * Every file an archive of {@code basename} carries besides the manifest,
     * results first. Not all of them need exist; {@link #save} reports the ones
     * that did not.
     */
    public static String[] members(String basename) {
        String[] jacobian = SsaDisturbance.JACOBIAN_SUFFIXES;
        String[] names = new String[RESULT_SUFFIXES.length + jacobian.length];
        int at = 0;
        for (String suffix : RESULT_SUFFIXES) {
            names[at++] = basename + suffix;
        }
        for (String suffix : jacobian) {
            names[at++] = basename + suffix;
        }
        return names;
    }

    /**
     * Deletes what a previous run of {@code basename} left in {@code directory},
     * and reports whatever would not go.
     *
     * <p>Why a run has to start by doing this. The engine writes its results
     * itself and says nothing on the way out about whether it managed to, so
     * the only evidence the interface has is whether
     * {@code <basename>_modes.dat} is on disk once the process has exited. A
     * run whose initialisation failed writes nothing at all - and in a
     * directory that already holds an earlier run under the same basename,
     * that test passes on the earlier run's file. The results window then
     * opens on another case's spectrum, headed with this case's directory and
     * basename, and there is nothing in it to say so. Clearing first is what
     * makes "the modes file is there" mean "this run wrote it". The
     * power-flow path has done the same since helios started exiting 0 on an
     * aborted export; see {@code deletePFCResultFiles}.
     *
     * <p>The set cleared is exactly {@link #members}, because the files a run
     * writes and the files an archive of it carries are the same files: three
     * results from {@code EIG} and four Jacobian tables from {@code JAC}.
     * Nothing else in the directory is touched, and that matters - the
     * basename field exists so several runs can share one directory, and the
     * case's own data files usually live there too.
     *
     * <p>A name that would not delete is collected and the rest are still
     * attempted, so one stuck file cannot leave five others behind to be
     * mistaken for the next run's. The caller is expected to refuse the run
     * when the returned list is not empty rather than proceed and read
     * whatever survived.
     *
     * @param directory where the run will write, which need not exist
     * @param basename  the run's basename
     * @return the names that are still there, in {@link #members} order;
     *     empty when the directory is clear
     */
    public static List<String> clearPreviousRun(File directory, String basename) {
        List<String> stuck = new ArrayList<String>();
        for (String name : members(basename)) {
            File leftover = new File(directory, name);
            // exists() rather than a bare delete so that "was never there" and
            // "is there and will not go" stay distinguishable: only the second
            // is a reason to refuse the run.
            if (!leftover.exists()) {
                continue;
            }
            try {
                Files.delete(leftover.toPath());
            } catch (IOException | RuntimeException ex) {
                stuck.add(name);
            }
        }
        return stuck;
    }

    /**
     * Whether a member the run did not write is one it was never obliged to.
     *
     * <p>What this is for: the four Jacobian files are written together by one
     * {@code JAC} record, so a missing one is a fault worth interrupting over,
     * while {@code _pf} and {@code _ms} are absent from every run that
     * filtered all its modes. Reporting the two the same way would put a
     * warning dialog in front of an entirely ordinary result.
     */
    public static boolean isOptional(String memberName) {
        for (String suffix : OPTIONAL_SUFFIXES) {
            if (memberName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The format a file name spells out, or null if it spells neither.
     *
     * <p>{@code .tgz} is accepted on the way in because that is what a good
     * many tools produce, but never written: one spelling per format keeps the
     * two file filters honest about what the button will actually create.
     */
    public static Format formatOfName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return Format.ZIP;
        }
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return Format.TAR_GZ;
        }
        return null;
    }

    /**
     * {@code chosen}, spelled so that its name says what is inside it.
     *
     * <p>A name already carrying {@code format}'s extension is left alone. Any
     * other name gains it, including one carrying the <em>other</em> format's:
     * "run.zip" saved as a tarball becomes "run.zip.tar.gz", which is ugly and
     * true, where leaving it would be neither.
     */
    public static File named(File chosen, Format format) {
        if (formatOfName(chosen.getName()) == format) {
            return chosen;
        }
        return new File(chosen.getAbsoluteFile().getParentFile(),
                chosen.getName() + format.extension());
    }

    /**
     * Writes one run into one archive.
     *
     * <p>The archive is built beside the target under a {@code .part} name and
     * moved into place only once it is complete, so a failure part way through
     * cannot leave a truncated file that looks like an archive and fails to
     * open a year later.
     *
     * @param target where to write, extension included
     * @param format which container to write
     * @param dir where the run's files are, which is where the run wrote them
     * @param manifest what to record about the run
     * @return the member file names that were not on disk, in archive order
     * @throws IOException if the modes file is absent, or writing fails
     */
    public static List<String> save(File target, Format format, File dir,
            Manifest manifest) throws IOException {
        String basename = manifest.basename();
        File modes = new File(dir, basename + RESULT_SUFFIXES[0]);
        if (!modes.isFile()) {
            throw new IOException("There is no " + modes.getName() + " in " + dir
                    + ", so there is no analysis to archive.");
        }
        List<File> present = new ArrayList<File>();
        List<String> absent = new ArrayList<String>();
        for (String name : members(basename)) {
            File file = new File(dir, name);
            if (file.isFile()) {
                present.add(file);
            } else {
                absent.add(name);
            }
        }
        byte[] manifestBytes = manifest.text().getBytes(StandardCharsets.UTF_8);
        File whole = target.getAbsoluteFile();
        File part = new File(whole.getParentFile(), whole.getName() + ".part");
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(part));
            try {
                if (format == Format.ZIP) {
                    writeZip(out, basename, manifestBytes, present);
                } else {
                    writeTarGz(out, basename, manifestBytes, present);
                }
            } finally {
                out.close();
            }
            Files.move(part.toPath(), whole.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            part.delete();
            throw ex;
        }
        return absent;
    }

    /**
     * Unpacks an archive and loads the run in it.
     *
     * <p>Unpacks into a fresh directory of its own under {@code scratchParent},
     * never over the current run's files: an archive made from a run called
     * "ssa" would otherwise overwrite the "ssa" on disk, and the results window
     * still open on it would be describing files that no longer exist.
     *
     * <p>Nothing survives a refusal. Every failure below removes the directory
     * it was unpacking into before it throws, so an archive that turns out not
     * to be one leaves the session exactly as it was.
     *
     * @param archive the file the user chose
     * @param scratchParent a directory the application owns and cleans up
     * @return the run, and what the manifest recorded about it
     * @throws IOException with a sentence fit to show the user as it stands,
     *     "Could not open &lt;file&gt;: &lt;reason&gt;", on one line
     */
    public static Loaded load(File archive, File scratchParent) throws IOException {
        try {
            return open(archive, scratchParent);
        } catch (IOException ex) {
            // Everything below phrases its own reason as a clause and nothing
            // else, so that this one place decides how a refusal is addressed
            // to the user and no message ends up naming the file twice.
            throw new IOException("Could not open " + archive.getName()
                    + ": " + ex.getMessage(), ex);
        }
    }

    private static Loaded open(File archive, File scratchParent) throws IOException {
        Format format = sniff(archive);
        if (format == null) {
            throw new IOException("it is neither a zip nor a gzipped tar, so it"
                    + " is not a small-signal archive.");
        }
        File into = Files.createTempDirectory(scratchParent.toPath(), "ssa-").toFile();
        try {
            InputStream raw = new BufferedInputStream(new FileInputStream(archive));
            try {
                if (format == Format.ZIP) {
                    unpackZip(raw, into);
                } else {
                    unpackTarGz(raw, into);
                }
            } finally {
                raw.close();
            }
            File manifestFile = findManifest(into);
            if (manifestFile == null) {
                // One line, no newlines: every refusal here reaches the user
                // through the inline banner, which is a JLabel and renders a
                // newline as a missing glyph rather than a line break.
                throw new IOException("it carries no " + MANIFEST_NAME
                        + ", so it was not written by STEPSS.");
            }
            Manifest manifest = Manifest.parse(new String(
                    Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8));
            File dir = manifestFile.getParentFile();
            File modes = new File(dir, manifest.basename() + RESULT_SUFFIXES[0]);
            if (!modes.isFile()) {
                throw new IOException("it names the run \"" + manifest.basename()
                        + "\" but carries no " + modes.getName()
                        + ", so it holds no results to show.");
            }
            try {
                return new Loaded(SsaResults.load(dir, manifest.basename()), manifest);
            } catch (IOException ex) {
                // The parsers name a line and a column, which is exactly right
                // when the file came off the engine and exactly not enough when
                // it came out of an archive: without this the user is told a
                // column number and never which of the files it was in.
                throw new IOException("its results could not be read. "
                        + ex.getMessage(), ex);
            }
        } catch (IOException ex) {
            deleteRecursively(into);
            throw ex;
        }
    }

    /**
     * What to say once an archive is open.
     *
     * <p>The engine build is the whole reason this sentence exists. The
     * results window header reports {@code t} and whichever thresholds the
     * run recorded out of the modes file itself, and says "not recorded"
     * where the engine wrote none, but nothing in it knows which engine wrote
     * the file. An archive analysed a year ago by a different
     * build is otherwise indistinguishable from one this session produced.
     *
     * <p>Lives here rather than in the handler that shows it so that it can be
     * checked: the harness cannot load {@code StepssUI}, which drags in the
     * whole toolchain, but it can call this.
     *
     * @param manifest what the archive recorded
     * @param archiveName the file the user chose, named back to them
     * @param engineNow the version of the engine loaded now, NaN if unread
     * @return one line, no newlines, fit for the inline banner
     */
    public static String describe(Manifest manifest, String archiveName,
            double engineNow) {
        StringBuilder said = new StringBuilder("Opened the run \"")
                .append(manifest.basename()).append("\" from ")
                .append(archiveName).append('.');
        if (manifest.engineVersion() == null) {
            said.append(" It does not record which engine analysed it.");
            return said.toString();
        }
        said.append(String.format(Locale.ROOT, " Analysed by RAMSES %.2f",
                manifest.engineVersion()));
        // The same slack EngineVersion compares with, and for the same reason:
        // the banner prints f5.2 from a single precision constant, so two
        // readings of one build can differ by an ulp and must not be reported
        // as two builds.
        if (!Double.isNaN(engineNow)
                && Math.abs(engineNow - manifest.engineVersion()) > 1e-4) {
            said.append(String.format(Locale.ROOT, ", not the %.2f loaded here.",
                    engineNow));
        } else {
            said.append('.');
        }
        return said.toString();
    }

    /**
     * Which container a file is, read from its first bytes rather than its
     * name. A file renamed on the way through a mail client is still the
     * archive it was, and a {@code .zip} that is really something else is
     * refused here rather than half way through unpacking.
     */
    static Format sniff(File archive) throws IOException {
        byte[] head = new byte[2];
        InputStream in = new FileInputStream(archive);
        try {
            if (!readFully(in, head, 2)) {
                return null;
            }
        } finally {
            in.close();
        }
        if (head[0] == 'P' && head[1] == 'K') {
            return Format.ZIP;
        }
        if ((head[0] & 0xff) == 0x1f && (head[1] & 0xff) == 0x8b) {
            return Format.TAR_GZ;
        }
        return null;
    }

    /**
     * The shallowest manifest under {@code root}, or null.
     *
     * <p>Searched for rather than expected at a fixed path, so an archive a
     * user unpacked, looked at and zipped up again opens as readily as the one
     * this class wrote.
     */
    private static File findManifest(File root) {
        List<File> level = new ArrayList<File>();
        level.add(root);
        while (!level.isEmpty()) {
            List<File> next = new ArrayList<File>();
            for (File dir : level) {
                File[] kids = dir.listFiles();
                if (kids == null) {
                    continue;
                }
                for (File kid : kids) {
                    if (kid.isFile() && kid.getName().equals(MANIFEST_NAME)) {
                        return kid;
                    }
                    if (kid.isDirectory()) {
                        next.add(kid);
                    }
                }
            }
            level = next;
        }
        return null;
    }

    // ---------------------------------------------------------------- zip

    private static void writeZip(OutputStream raw, String basename,
            byte[] manifest, List<File> members) throws IOException {
        // Everything under one directory named for the run, so unpacking the
        // archive by hand puts eight files in a folder rather than loose in
        // whatever directory the user happened to be in.
        String prefix = basename + "/";
        ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8);
        zip.putNextEntry(new ZipEntry(prefix));
        zip.closeEntry();
        // The manifest first, so `unzip -p archive.zip '*/stepss-ssa.txt'`
        // and a listing both put what the archive is at the top.
        zip.putNextEntry(new ZipEntry(prefix + MANIFEST_NAME));
        zip.write(manifest);
        zip.closeEntry();
        for (File member : members) {
            ZipEntry entry = new ZipEntry(prefix + member.getName());
            entry.setTime(member.lastModified());
            zip.putNextEntry(entry);
            copyInto(member, zip, member.length());
            zip.closeEntry();
        }
        zip.finish();
    }

    private static void unpackZip(InputStream raw, File into) throws IOException {
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw),
                StandardCharsets.UTF_8);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            File out = safeChild(into, entry.getName());
            if (entry.isDirectory()) {
                mkdirs(out);
            } else {
                mkdirs(out.getParentFile());
                drain(zip, out);
            }
            zip.closeEntry();
        }
    }

    // ---------------------------------------------------------------- tar

    /**
     * Minimal POSIX/USTAR writer, matching the reader
     * {@code my.stepss.platform.ToolExtractor} already carries for the release
     * tarballs. Commons Compress is not a dependency of this project and this
     * is not reason enough to add one: the archive holds a handful of plain
     * files under one directory, which is the part of tar that has not changed
     * since 1988.
     */
    private static void writeTarGz(OutputStream raw, String basename,
            byte[] manifest, List<File> members) throws IOException {
        String prefix = basename + "/";
        long now = System.currentTimeMillis() / 1000L;
        GZIPOutputStream gz = new GZIPOutputStream(raw);
        gz.write(tarHeader(prefix, 0L, now, true));
        gz.write(tarHeader(prefix + MANIFEST_NAME, manifest.length, now, false));
        gz.write(manifest);
        pad(gz, manifest.length);
        for (File member : members) {
            long size = member.length();
            gz.write(tarHeader(prefix + member.getName(), size,
                    member.lastModified() / 1000L, false));
            copyInto(member, gz, size);
            pad(gz, size);
        }
        // Two zero blocks are the end of a tar archive. Without them GNU tar
        // reads the file, extracts everything and then reports "unexpected EOF"
        // with a non-zero exit, which is a broken archive by any script's
        // reckoning even though the files came out.
        gz.write(new byte[1024]);
        gz.finish();
    }

    private static byte[] tarHeader(String name, long size, long mtime,
            boolean directory) throws IOException {
        byte[] header = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 100) {
            // The prefix field could carry the overflow, but a basename this
            // long is a mistake worth reporting rather than a case worth
            // supporting: the results files it names are just as unwieldy.
            throw new IOException("The name \"" + name + "\" is too long for a"
                    + " tar archive. Save as .zip, or use a shorter basename.");
        }
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        putOctal(header, 100, 8, directory ? 0755 : 0644);
        putOctal(header, 108, 8, 0);
        putOctal(header, 116, 8, 0);
        putOctal(header, 124, 12, size);
        putOctal(header, 136, 12, mtime);
        // The checksum is computed with its own field full of blanks, which is
        // the convention every reader assumes.
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        header[156] = (byte) (directory ? '5' : '0');
        System.arraycopy("ustar".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 5);
        header[263] = '0';
        header[264] = '0';
        // uname and gname are left empty on purpose. The account that happened
        // to run the analysis is not part of the result, and it is not
        // something to hand to a colleague without being asked.
        int sum = 0;
        for (byte b : header) {
            sum += b & 0xff;
        }
        putOctal(header, 148, 7, sum);
        header[155] = ' ';
        return header;
    }

    /** Zero padded octal in the first {@code len - 1} bytes, then a NUL. */
    private static void putOctal(byte[] header, int off, int len, long value) {
        String digits = Long.toOctalString(value);
        int width = len - 1;
        for (int i = 0; i < width; i++) {
            int from = digits.length() - width + i;
            header[off + i] = (byte) (from < 0 ? '0' : digits.charAt(from));
        }
        header[off + len - 1] = 0;
    }

    private static void pad(OutputStream out, long size) throws IOException {
        int padding = (int) ((512L - (size % 512L)) % 512L);
        if (padding > 0) {
            out.write(new byte[padding]);
        }
    }

    private static void unpackTarGz(InputStream raw, File into) throws IOException {
        GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(raw));
        byte[] header = new byte[512];
        while (readFully(gz, header, 512)) {
            String name = cString(header, 0, 100);
            if (name.isEmpty()) {
                break;
            }
            String prefix = cString(header, 345, 155);
            if (!prefix.isEmpty()) {
                name = prefix + "/" + name;
            }
            long size = octal(header, 124, 12, name);
            char type = (char) header[156];
            if (type == '5') {
                mkdirs(safeChild(into, name));
                skip(gz, size);
            } else if (type == '0' || type == 0) {
                File out = safeChild(into, name);
                mkdirs(out.getParentFile());
                copyOut(gz, out, size);
            } else {
                // Symlinks, pax headers, GNU long names: skipped rather than
                // refused. Nothing this class needs is carried in them, and an
                // archive whose members did not survive the skip simply fails
                // the manifest check below with a reason the user can act on.
                skip(gz, size);
            }
            skip(gz, (512L - (size % 512L)) % 512L);
        }
    }

    private static long octal(byte[] header, int off, int len, String name)
            throws IOException {
        String field = cString(header, off, len);
        if (field.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(field, 8);
        } catch (NumberFormatException ex) {
            throw new IOException("the tar entry \"" + name
                    + "\" has a malformed size field.", ex);
        }
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) {
            end++;
        }
        return new String(b, off, end - off, StandardCharsets.UTF_8).trim();
    }

    // ------------------------------------------------------------- shared

    /**
     * Rejects an entry that would be written outside the unpack directory.
     *
     * <p>The whole point of this feature is opening a file someone else made,
     * so "../../.ssh/authorized_keys" is not a hypothetical entry name. The
     * canonical path is what decides, because a name can climb out and back in
     * again and still be outside on the way.
     */
    private static File safeChild(File into, String entryName) throws IOException {
        File out = new File(into, entryName);
        String root = into.getCanonicalPath();
        String path = out.getCanonicalPath();
        // The equality case is not a loophole, it is the commonest archive
        // there is: `tar czf run.tar.gz .` names its own root "./", which
        // resolves to the unpack directory itself. Refusing that would turn
        // "someone repacked it" into "this is not a STEPSS archive".
        if (!path.equals(root) && !path.startsWith(root + File.separator)) {
            throw new IOException("it holds an entry, \"" + entryName
                    + "\", that would be written outside the folder it is"
                    + " unpacked into.");
        }
        return out;
    }

    private static void mkdirs(File dir) throws IOException {
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("could not create " + dir);
        }
    }

    private static void copyInto(File file, OutputStream out, long size)
            throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] buffer = new byte[8192];
            long left = size;
            while (left > 0) {
                int want = (int) Math.min(buffer.length, left);
                int read = in.read(buffer, 0, want);
                if (read < 0) {
                    // tar has already declared the size in the header, so a
                    // file that shrank mid-write would leave the archive
                    // misaligned and every later entry unreadable. Better to
                    // fail here, where the .part file is still what gets
                    // deleted.
                    throw new IOException(file.getName()
                            + " changed while it was being archived.");
                }
                out.write(buffer, 0, read);
                left -= read;
            }
        } finally {
            in.close();
        }
    }

    private static void copyOut(InputStream in, File file, long size)
            throws IOException {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            byte[] buffer = new byte[8192];
            long left = size;
            while (left > 0) {
                int want = (int) Math.min(buffer.length, left);
                int read = in.read(buffer, 0, want);
                if (read < 0) {
                    throw new IOException("it ends part way through \""
                            + file.getName() + "\".");
                }
                out.write(buffer, 0, read);
                left -= read;
            }
        } finally {
            out.close();
        }
    }

    private static void drain(InputStream in, File file) throws IOException {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } finally {
            out.close();
        }
    }

    private static boolean readFully(InputStream in, byte[] buffer, int len)
            throws IOException {
        int at = 0;
        while (at < len) {
            int read = in.read(buffer, at, len - at);
            if (read < 0) {
                return false;
            }
            at += read;
        }
        return true;
    }

    private static void skip(InputStream in, long count) throws IOException {
        long left = count;
        byte[] buffer = new byte[8192];
        while (left > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (read < 0) {
                return;
            }
            left -= read;
        }
    }

    /** Best effort, and only ever on a directory this class created. */
    static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        File[] kids = file.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                deleteRecursively(kid);
            }
        }
        return file.delete();
    }
}
