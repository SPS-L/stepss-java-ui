package my.stepss.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Reads and writes the {@code .cfg} a scenario is saved as.
 *
 * <p>The keys are named here, once, and nowhere else. The handler this
 * replaces derived them by walking the two tab panels and calling
 * {@code getName()} on whatever it found, which tied the file format to the
 * layout: the moment {@code applyModernChrome()} reparented the controls into
 * scroll panes and wrapper panels, {@code Container.getComponents()} stopped
 * matching any of the twenty-four controls, and Save configuration wrote a
 * file containing nothing but a date comment while Load configuration restored
 * nothing from it. Neither said a word. Naming the set explicitly is what
 * stops a future relayout doing the same thing again, and it is a stronger
 * guarantee than a recursive walk with a test watching it, because there is no
 * longer anything for a relayout to break.
 *
 * <p>{@code stepss.format} stays at 1 for the {@code diagram} key added in
 * 2026-08. {@link #checkFormat} throws on a format BELOW the one this build
 * writes, so a bump would make this build refuse every file already saved,
 * while an added key costs an older build one advisory sentence from
 * {@link #reportUnknownKeys} and nothing else. That asymmetry is why a new
 * optional key is not a format change.
 *
 * <p>It is also why {@code record.dump} becoming {@code record.init}, and
 * {@code observables.wizard} being dropped, are not format changes either.
 * Both are deliberately a clean break: a file saved before this build names
 * them, gets one advisory sentence per key from {@link #reportUnknownKeys},
 * and loads with the initialisation tick cleared. Bumping the format for that
 * would refuse the whole file instead of the one setting, and reading the old
 * names on the quiet would leave every hand-written file on a spelling this
 * class does not document.
 *
 * <p>Written by hand for the grouping and the section comments, read back with
 * {@link Properties#load(Reader)}. One small escaper against the standard
 * parser, rather than two halves of a format both written here.
 *
 * <p>UTF-8 both ways, through the {@code Reader} and {@code Writer} overloads
 * rather than the stream ones, which are ISO-8859-1 with {@code \\uXXXX}
 * escapes. A user whose name or case directory is not ASCII gets a file that
 * still reads as their path rather than as escapes, and one that survives the
 * round trip either way.
 */
public final class ScenarioFile {

    /** The format this build writes and the only one it reads. */
    public static final int FORMAT = 1;

    /** The key carrying {@link #FORMAT}, and the first line of every file. */
    static final String FORMAT_KEY = "stepss.format";

    private static final String DISTURBANCE = "disturbance";
    private static final String DIAGRAM = "diagram";
    private static final String OBSERVABLES_FILE = "observables.file";
    private static final String RECORD_TRAJECTORY = "record.trajectory";
    private static final String RECORD_CONTINUOUS = "record.continuous";
    private static final String RECORD_DISCRETE = "record.discrete";
    private static final String RECORD_INIT = "record.init";

    private ScenarioFile() {
    }

    /** A scenario read from a file, and everything doubtful about it. */
    public static final class Loaded {

        private final Scenario scenario;
        private final List<String> problems;

        Loaded(Scenario scenario, List<String> problems) {
            this.scenario = scenario;
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }

        /** The scenario, filled as far as the file allowed. */
        public Scenario scenario() {
            return scenario;
        }

        /**
         * What could not be used, one sentence each, in file order.
         *
         * <p>Empty on a clean load. A non-empty list is not a failure: every
         * key that was understood has been applied, and these are the ones
         * that were not. The alternative - refusing the whole file over one
         * unreadable line - throws away the nine settings that were fine.
         */
        public List<String> problems() {
            return problems;
        }
    }

    /**
     * Writes {@code scenario} to {@code file}.
     *
     * <p>Only non-empty data rows are written. A missing {@code data.N} reads
     * back as an empty row, so the slots survive without eight blank lines
     * in every file.
     *
     * @param scenario   the scenario to write
     * @param file       the destination, its directory the base for relative paths
     * @param workingDir the session's working directory, or null if none
     * @param savedBy    the STEPSS version, recorded as a comment for support
     * @throws IOException if the file cannot be written
     */
    public static void save(Scenario scenario, File file, File workingDir, String savedBy)
            throws IOException {
        File cfgDir = ScenarioPaths.directoryOf(file);
        Writer out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8));
        try {
            out.write("# STEPSS scenario. Save configuration wrote this; Load"
                    + " configuration reads it.\n");
            out.write("# Paths inside this file's own directory are stored"
                    + " relative to it, so the\n");
            out.write("# folder can be moved or copied whole. Everything else"
                    + " is absolute.\n");
            if (savedBy != null && !savedBy.isEmpty()) {
                out.write("# Saved by STEPSS " + savedBy + "\n");
            }
            out.write("\n");
            write(out, FORMAT_KEY, String.valueOf(FORMAT));

            out.write("\n# System data\n");
            for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
                String stored = ScenarioPaths.store(scenario.data(slot), cfgDir, workingDir);
                if (!stored.isEmpty()) {
                    write(out, dataKey(slot), stored);
                }
            }
            write(out, DISTURBANCE,
                    ScenarioPaths.store(scenario.disturbance(), cfgDir, workingDir));
            write(out, DIAGRAM,
                    ScenarioPaths.store(scenario.diagram(), cfgDir, workingDir));

            out.write("\n# Observables\n");
            write(out, OBSERVABLES_FILE,
                    ScenarioPaths.store(scenario.observablesFile(), cfgDir, workingDir));
            for (int row = 0; row < Scenario.RUNTIME_ROWS; row++) {
                write(out, runtimeTypeKey(row), scenario.runtimeType(row));
                write(out, runtimeNameKey(row), scenario.runtimeName(row));
            }

            out.write("\n# Recording\n");
            write(out, RECORD_TRAJECTORY, String.valueOf(scenario.saveTrajectory()));
            write(out, RECORD_CONTINUOUS, String.valueOf(scenario.saveContinuousTrace()));
            write(out, RECORD_DISCRETE, String.valueOf(scenario.saveDiscreteTrace()));
            write(out, RECORD_INIT, String.valueOf(scenario.saveInit()));
        } finally {
            out.close();
        }
    }

    /**
     * Reads {@code file} back into a scenario.
     *
     * <p>Throws only for a file this build cannot read at all: one written
     * before this format existed, one written by a newer STEPSS, or one that
     * will not open. Everything else - a key nobody knows, a boolean that is
     * not a boolean, an observable type no longer in the list, a file that has
     * since been deleted - is reported through {@link Loaded#problems()} and
     * the rest of the scenario still loads.
     *
     * <p>Nothing here parses an integer. The handler this replaces read combo
     * selections as {@code new Integer(properties.getProperty(name))} with no
     * null check and no bounds check, so a missing key threw
     * NumberFormatException and a stale one threw IllegalArgumentException,
     * neither caught by its {@code catch (IOException)}.
     *
     * @param file the scenario file
     * @return the scenario and any problems with it
     * @throws IOException if the file cannot be read or is not this format
     */
    public static Loaded load(File file) throws IOException {
        Properties properties = new Properties();
        Reader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
        try {
            properties.load(in);
        } finally {
            in.close();
        }
        checkFormat(properties, file);

        List<String> problems = new ArrayList<>();
        Scenario scenario = new Scenario();
        File cfgDir = ScenarioPaths.directoryOf(file);

        for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
            scenario.setData(slot, path(properties, dataKey(slot), cfgDir,
                    "System data row " + (slot + 1), problems));
        }
        scenario.setDisturbance(path(properties, DISTURBANCE, cfgDir,
                "The disturbance file", problems));
        scenario.setDiagram(path(properties, DIAGRAM, cfgDir,
                "The one-line diagram file", problems));
        scenario.setObservablesFile(path(properties, OBSERVABLES_FILE, cfgDir,
                "The observables file", problems));
        for (int row = 0; row < Scenario.RUNTIME_ROWS; row++) {
            scenario.setRuntimeType(row, properties.getProperty(runtimeTypeKey(row), ""));
            scenario.setRuntimeName(row, properties.getProperty(runtimeNameKey(row), ""));
        }
        scenario.setSaveTrajectory(flag(properties, RECORD_TRAJECTORY, problems));
        scenario.setSaveContinuousTrace(flag(properties, RECORD_CONTINUOUS, problems));
        scenario.setSaveDiscreteTrace(flag(properties, RECORD_DISCRETE, problems));
        scenario.setSaveInit(flag(properties, RECORD_INIT, problems));

        reportUnknownKeys(properties, problems);
        return new Loaded(scenario, problems);
    }

    /**
     * Every path a scenario file names, exactly as it is stored.
     *
     * <p>Unresolved, unlike everything {@link #load} hands back, and that is
     * the point: this answers "which files does this scenario refer to" for a
     * caller that has the scenario file but not the directory it will live in.
     * {@code ExamplesPack} is that caller. It reads a {@code .cfg} straight out
     * of an upstream source archive at build time, to check that every file the
     * scenario names is one the payload will carry, and a zip entry has no
     * directory to resolve against.
     *
     * <p>Here rather than in the packer because the key names are this class's,
     * once and nowhere else. A packer with its own list of key names is a
     * second place for {@code observables.file} to be spelled, and it would go
     * on passing the build while checking a key the format no longer has.
     *
     * <p>Empty slots are skipped, so the result is the files and nothing else.
     * The format number, the run-time observable rows and the recording flags
     * are not paths and are not returned.
     *
     * @param in the scenario file, read but not closed
     * @return the stored values, in file order, without duplicates
     * @throws IOException if the reader fails
     */
    public static List<String> storedPaths(Reader in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        Set<String> paths = new LinkedHashSet<>();
        for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
            addIfSet(paths, properties, dataKey(slot));
        }
        addIfSet(paths, properties, DISTURBANCE);
        addIfSet(paths, properties, DIAGRAM);
        addIfSet(paths, properties, OBSERVABLES_FILE);
        return Collections.unmodifiableList(new ArrayList<>(paths));
    }

    private static void addIfSet(Set<String> paths, Properties properties, String key) {
        String value = properties.getProperty(key, "").trim();
        if (!value.isEmpty()) {
            paths.add(value);
        }
    }

    /**
     * Refuses a file this build has no business interpreting.
     *
     * <p>A missing {@link #FORMAT_KEY} is treated as "not a scenario file",
     * with no special case for the format that predates this one. There was
     * one, explaining that such a file came from an older STEPSS and holding
     * absolute paths from the machine that saved it; the files it described
     * have since been deleted from stepss-test-systems and nothing this
     * project ships writes one, so the explanation named a situation that no
     * longer arises and a build that no longer exists. What is left is the
     * same refusal without the history lesson.
     */
    private static void checkFormat(Properties properties, File file) throws IOException {
        String declared = properties.getProperty(FORMAT_KEY);
        if (declared == null) {
            throw new IOException(file.getName() + " is not a STEPSS scenario file:"
                    + " it declares no " + FORMAT_KEY + ".");
        }
        int format;
        try {
            format = Integer.parseInt(declared.trim());
        } catch (NumberFormatException notANumber) {
            throw new IOException(file.getName() + " is not a STEPSS scenario file:"
                    + " its " + FORMAT_KEY + " is \"" + declared + "\" rather than a"
                    + " format number.");
        }
        if (format > FORMAT) {
            throw new IOException(file.getName() + " was saved by a newer STEPSS"
                    + " (scenario format " + format + "). This build reads format "
                    + FORMAT + ".\n\nUpdate STEPSS to open it.");
        }
        if (format < FORMAT) {
            throw new IOException(file.getName() + " declares scenario format "
                    + format + ", which no STEPSS release ever wrote.");
        }
    }

    /**
     * Names every key in the file that this build does not read.
     *
     * <p>A typo in a hand-edited file is otherwise indistinguishable from a
     * setting that was never saved, which is the failure mode this whole
     * rewrite exists to remove.
     *
     * <p>Two names reach this method from files that were once correct rather
     * than from a typo: {@code record.dump}, which is now {@code record.init},
     * and {@code observables.wizard}, which is no longer saved at all. They are
     * reported in the same words as any other unknown key, and re-saving the
     * scenario writes the file without them.
     */
    private static void reportUnknownKeys(Properties properties, List<String> problems) {
        Set<String> known = knownKeys();
        List<String> unknown = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (!known.contains(key)) {
                unknown.add(key);
            }
        }
        Collections.sort(unknown);
        for (String key : unknown) {
            problems.add("\"" + key + "\" is not a setting this STEPSS reads;"
                    + " it was ignored.");
        }
    }

    /** Every key this build writes, which is every key it reads. */
    static Set<String> knownKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(FORMAT_KEY);
        for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
            keys.add(dataKey(slot));
        }
        keys.add(DISTURBANCE);
        keys.add(DIAGRAM);
        keys.add(OBSERVABLES_FILE);
        for (int row = 0; row < Scenario.RUNTIME_ROWS; row++) {
            keys.add(runtimeTypeKey(row));
            keys.add(runtimeNameKey(row));
        }
        keys.add(RECORD_TRAJECTORY);
        keys.add(RECORD_CONTINUOUS);
        keys.add(RECORD_DISCRETE);
        keys.add(RECORD_INIT);
        return Collections.unmodifiableSet(keys);
    }

    private static String dataKey(int slot) {
        return "data." + (slot + 1);
    }

    private static String runtimeTypeKey(int row) {
        return "runtime." + (row + 1) + ".type";
    }

    private static String runtimeNameKey(int row) {
        return "runtime." + (row + 1) + ".name";
    }

    /** A path key, resolved to absolute and checked for still being there. */
    private static String path(Properties properties, String key, File cfgDir,
            String description, List<String> problems) {
        String resolved = ScenarioPaths.resolve(properties.getProperty(key, ""), cfgDir);
        if (ScenarioPaths.missing(resolved)) {
            problems.add(description + " is not there: " + resolved);
        }
        return resolved;
    }

    /**
     * A boolean key, or false with a problem recorded.
     *
     * <p>Not {@link Boolean#parseBoolean}, which the old handler used and
     * which answers false to every string that is not "true" - including a
     * missing key, a typo and a number - so a checkbox silently came back
     * cleared and looked exactly like one that had been saved cleared.
     */
    private static boolean flag(Properties properties, String key, List<String> problems) {
        String value = properties.getProperty(key);
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false") || trimmed.isEmpty()) {
            return false;
        }
        problems.add("\"" + key + "\" is \"" + trimmed + "\" rather than true or"
                + " false; it was read as false.");
        return false;
    }

    private static void write(Writer out, String key, String value) throws IOException {
        out.write(key + " = " + escape(value) + "\n");
    }

    /**
     * A value in the form {@link Properties#load(Reader)} reads back unchanged.
     *
     * <p>Backslash first, or the escapes added after it would themselves be
     * escaped. A leading blank is escaped because the parser strips
     * unescaped leading whitespace; {@code =} and {@code :} are not, because
     * only the first one on a line separates a key from its value, and
     * {@code #} and {@code !} are not, because they comment only at the start
     * of a line and every line here starts with a key.
     *
     * <p>Nothing above U+007F is escaped: the file is UTF-8 and is read back
     * through a UTF-8 {@code Reader}.
     */
    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case ' ':
                    escaped.append(i == 0 ? "\\ " : " ");
                    break;
                default:
                    escaped.append(c);
                    break;
            }
        }
        return escaped.toString();
    }
}
