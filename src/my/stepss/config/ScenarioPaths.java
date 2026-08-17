package my.stepss.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Turns absolute paths into what the scenario file stores, and back.
 *
 * <p><strong>Relative paths exist inside the file and nowhere else.</strong>
 * {@link #resolve} always hands back an absolute path, so nothing downstream
 * of a load ever sees a relative one. That is not tidiness, it is the whole
 * safety argument: {@code StepssUI.createCommandFile()} copies each form field
 * verbatim into {@code cmd.txt}, {@code createPFCCommandFile()} does the same
 * into Helios's {@code PFCcmd.txt}, and both engines run with the working
 * directory as their base. A relative path that reached a form field would be
 * resolved against the working directory while having been written relative to
 * the configuration file, and the two have no reason to be the same place. The
 * run would then read a different file from the one the scenario names, or
 * none, with nothing anywhere saying so.
 *
 * <p>Storing relative is still worth doing, because the alternative is what
 * the old {@code .cfg} files demonstrate: every one of them in
 * stepss-test-systems holds paths like {@code C:\Users\tvanc\OneDrive\...},
 * which name nothing on any other machine and cannot be repaired by hand
 * without editing ten lines. A case folder holding its own {@code .cfg} now
 * moves, copies and ships intact.
 */
public final class ScenarioPaths {

    private ScenarioPaths() {
    }

    /**
     * What to write to the file for a path the form holds.
     *
     * <p>Relative only when the file sits inside the configuration file's own
     * directory tree, and then always with {@code /} separators so a scenario
     * saved on Windows opens on Linux. Anything outside that tree - another
     * drive, a shared network case, a data file kept beside the results rather
     * than beside the configuration - stays absolute, because a relative path
     * climbing out through {@code ../..} is neither portable nor readable.
     *
     * <p>A field holding a path that is not absolute is first resolved against
     * {@code workingDir}, which is how the engines read it today. The file
     * therefore records the file that would actually have run, rather than
     * re-basing the text against a different directory and silently changing
     * which file the scenario means.
     *
     * @param fieldText  the form field's text, possibly ""
     * @param cfgDir     the directory the scenario file is being written to
     * @param workingDir the session's working directory, or null if none
     * @return the value to store, or "" for an empty field
     */
    public static String store(String fieldText, File cfgDir, File workingDir) {
        if (fieldText == null || fieldText.trim().isEmpty()) {
            return "";
        }
        Path absolute = absolutise(fieldText.trim(), workingDir);
        if (cfgDir == null) {
            return absolute.toString();
        }
        Path base = normalise(cfgDir.toPath());
        Path relative;
        try {
            relative = base.relativize(absolute);
        } catch (IllegalArgumentException differentRoots) {
            // Two paths on different Windows drives cannot be expressed
            // relative to one another at all, and relativize says so by
            // throwing rather than returning something unusable.
            return absolute.toString();
        }
        for (Path segment : relative) {
            if ("..".equals(segment.toString())) {
                return absolute.toString();
            }
        }
        if (relative.toString().isEmpty()) {
            return absolute.toString();
        }
        return relative.toString().replace(File.separatorChar, '/');
    }

    /**
     * The absolute path a stored value names.
     *
     * <p>Always absolute, for the reason in the class comment. A stored value
     * that is already absolute is returned normalised; one that is relative is
     * resolved against the directory the scenario file was read from.
     *
     * <p>Normalised rather than canonicalised: {@code getCanonicalPath} also
     * resolves symbolic links, and a case reached through a symlinked share
     * would come back naming the target, which is not the path the user set up
     * and not the path they would recognise in the field.
     *
     * @param stored the value read from the file, possibly ""
     * @param cfgDir the directory the scenario file was read from
     * @return an absolute path, or "" when {@code stored} is empty
     */
    public static String resolve(String stored, File cfgDir) {
        if (stored == null || stored.trim().isEmpty()) {
            return "";
        }
        String value = stored.trim();
        File named = new File(value);
        if (named.isAbsolute() || cfgDir == null) {
            return normalise(named.toPath()).toString();
        }
        // new File(dir, "a/b.dat") accepts the stored forward slashes on
        // Windows too; only the rendered result uses the platform separator.
        return normalise(new File(cfgDir, value).toPath()).toString();
    }

    /**
     * Whether a resolved path names something that is not there.
     *
     * <p>Reported on load rather than treated as a failure. A case whose files
     * have moved is still worth opening - the paths are what the user needs to
     * see in order to fix them - but finding out at load is better than
     * finding out from an engine that stopped halfway through a run.
     *
     * @param resolved an absolute path from {@link #resolve}, possibly ""
     * @return true when the path is set and no file exists at it
     */
    public static boolean missing(String resolved) {
        return !resolved.isEmpty() && !new File(resolved).exists();
    }

    private static Path absolutise(String value, File workingDir) {
        File named = new File(value);
        if (named.isAbsolute() || workingDir == null) {
            return normalise(named.toPath());
        }
        return normalise(new File(workingDir, value).toPath());
    }

    private static Path normalise(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * The directory a scenario file sits in, for use as the base of both
     * halves above.
     *
     * @param file the scenario file, saved or loaded
     * @return its directory, never null
     */
    public static File directoryOf(File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        return parent == null ? Paths.get(".").toAbsolutePath().normalize().toFile() : parent;
    }
}
