package my.stepss.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * Headless checks for saving and loading a scenario: bind -> fill -> save ->
 * blank -> load -> compare, plus every way the file can be wrong. This
 * repository has no unit-test framework and is not gaining one, so this
 * main() is where the format is pinned.
 *
 * <p>The controls are buried three containers deep before anything is read
 * from them, in the same shape {@code applyModernChrome()} produces: a scroll
 * pane over a panel over a row panel. That is not decoration. The handler this
 * code replaces walked {@code jPanel2.getComponents()} and matched nothing
 * once the controls were reparented into exactly that arrangement, so a round
 * trip checked against controls sitting flat on one panel would pass while
 * being blind to the only fault that has ever occurred here. Nothing in
 * {@link ScenarioBinding} looks at a parent, which is what makes the check
 * pass; the nesting is here to prove it stays true.
 *
 * <p>Swing only, never a frame: {@code JPanel} and the controls construct
 * without a display, and {@code StepssUI} cannot be built at all outside a
 * real installation because its constructor extracts the toolchain and exits
 * the JVM when it cannot.
 */
public final class ScenarioHarness {

    private static int failures;

    /** The observable types, as {@code StepssUI.OBSERVABLE_TYPES} lists them. */
    private static final String[] TYPES = {
        "Bus Voltage", "Machine Speed", "Omega-delta of machine",
        "Active power-delta of machine", "Center of Inertia", "Wall Time",
        "Latency", "Branch Active Power Origin", "Branch Active Power Extremity",
        "Branch Reactive Power Origin", "Branch Reactive Power Extremity",
        "Injector Observable"
    };

    private ScenarioHarness() {
    }

    public static void main(String[] args) throws IOException {
        checkRoundTripThroughANestedTree();
        checkTheDiagramSlotRoundTrips();
        checkAnEmptyDiagramSlotStaysEmpty();
        checkEveryLoadedPathIsAbsolute();
        checkPathsInsideTheFolderAreStoredRelative();
        checkPathsOutsideTheFolderStayAbsolute();
        checkAConfigInAnotherDirectoryStillResolves();
        checkEmptyRowsKeepTheirSlot();
        checkWindowsPathsSurviveTheEscaper();
        checkNewerFormatIsRefused();
        checkFormatlessFileIsRefused();
        checkUnreadableFormatIsRefused();
        checkAnEmptyFileLoadsWithoutThrowing();
        checkJunkBooleanIsReported();
        checkUnknownObservableTypeIsReported();
        checkUnknownKeyIsReported();
        checkRetiredKeysAreReported();
        checkMissingFilesAreReported();
        checkIsEmpty();
        checkBindingRejectsAShortWiring();
        checkDisturbanceFileGuard();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * The acceptance case from the issue: fill every persisted control, save,
     * mutate the form, load, and assert every control came back.
     */
    private static void checkRoundTripThroughANestedTree() throws IOException {
        Form form = new Form();
        File dir = tempDir("roundtrip");
        for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
            form.data[slot].setText(touch(dir, "data" + (slot + 1) + ".dat"));
        }
        form.disturbance.setText(touch(dir, "disturb.dst"));
        form.observables.setText(touch(dir, "obs.dat"));
        form.types[0].setSelectedItem("Machine Speed");
        form.types[1].setSelectedItem("Wall Time");
        form.types[2].setSelectedItem("Injector Observable");
        form.names[0].setText("g1");
        form.names[1].setText("");
        form.names[2].setText("INJ 4");
        form.trajectory.setSelected(true);
        form.continuous.setSelected(false);
        form.discrete.setSelected(true);
        form.init.setSelected(true);

        Scenario saved = form.binding.read();
        File cfg = temp(dir, "case.cfg");
        ScenarioFile.save(saved, cfg, dir, "3.74.21");

        form.blank();
        expect("the form is blank before loading", "", form.data[0].getText());

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        List<String> problems = form.binding.apply(loaded.scenario());
        expect("a clean file reports no problems from the file",
                "[]", loaded.problems().toString());
        expect("a clean file reports no problems from the form",
                "[]", problems.toString());

        for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
            expect("data row " + (slot + 1) + " came back",
                    new File(dir, "data" + (slot + 1) + ".dat").getAbsolutePath(),
                    form.data[slot].getText());
        }
        expect("the disturbance came back",
                new File(dir, "disturb.dst").getAbsolutePath(), form.disturbance.getText());
        expect("the observables file came back",
                new File(dir, "obs.dat").getAbsolutePath(), form.observables.getText());
        expect("runtime type 1 came back", "Machine Speed", form.types[0].getSelectedItem());
        expect("runtime type 2 came back", "Wall Time", form.types[1].getSelectedItem());
        expect("runtime type 3 came back", "Injector Observable", form.types[2].getSelectedItem());
        expect("runtime name 1 came back", "g1", form.names[0].getText());
        expect("runtime name 2 came back blank", "", form.names[1].getText());
        expect("runtime name 3 came back", "INJ 4", form.names[2].getText());
        expect("save trajectory came back", true, form.trajectory.isSelected());
        expect("save continuous trace came back", false, form.continuous.isSelected());
        expect("save discrete trace came back", true, form.discrete.isSelected());
        expect("save initialization data came back", true, form.init.isSelected());
    }

    /**
     * The one-line diagram path survives a save and a load.
     *
     * <p>It is part of a scenario for the same reason every other path is:
     * {@code createPFCCommandFile()} reads it, so a scenario that round-trips
     * is a run that reproduces.
     */
    private static void checkTheDiagramSlotRoundTrips() throws IOException {
        File dir = tempDir("diagram");
        Form form = new Form();
        form.data[0].setText(touch(dir, "6bus.dat"));
        String svg = touch(dir, "6bus.svg");
        form.diagram.setText(svg);

        File cfg = temp(dir, "case.cfg");
        ScenarioFile.save(form.binding.read(), cfg, dir, "3.75.0");

        form.blank();
        expect("the form was blanked", "", form.diagram.getText());

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        form.binding.apply(loaded.scenario());
        expect("the diagram slot comes back", svg, form.diagram.getText());
        expect("a clean load reports no problems", true, loaded.problems().isEmpty());
    }

    /** An empty diagram slot round-trips as empty, not as the .cfg's directory. */
    private static void checkAnEmptyDiagramSlotStaysEmpty() throws IOException {
        File dir = tempDir("nodiagram");
        Form form = new Form();
        File cfg = temp(dir, "case.cfg");
        ScenarioFile.save(form.binding.read(), cfg, dir, "3.75.0");

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        form.binding.apply(loaded.scenario());
        expect("an unset diagram slot loads empty", "", form.diagram.getText());
    }

    /**
     * The constraint the whole relative-path design rests on: nothing
     * downstream of a load ever sees a relative path.
     *
     * <p>{@code createCommandFile()} copies each field verbatim into
     * {@code cmd.txt} and {@code createPFCCommandFile()} into Helios's
     * {@code PFCcmd.txt}, and both engines run with the working directory as
     * their base, which is not where the configuration file lives. A relative
     * path reaching a field would make the run read a different file from the
     * one the scenario names.
     */
    private static void checkEveryLoadedPathIsAbsolute() throws IOException {
        File dir = tempDir("absolute");
        touch(dir, "lf.dat");
        touch(dir, "disturb.dst");
        touch(dir, "obs.dat");
        File cfg = temp(dir, "relative.cfg");
        write(cfg,
                "stepss.format = 1",
                "data.1 = lf.dat",
                "disturbance = disturb.dst",
                "observables.file = obs.dat");

        Form form = new Form();
        Scenario scenario = ScenarioFile.load(cfg).scenario();
        form.binding.apply(scenario);
        expect("a relative data row loads absolute", true,
                new File(form.data[0].getText()).isAbsolute());
        expect("a relative disturbance loads absolute", true,
                new File(form.disturbance.getText()).isAbsolute());
        expect("a relative observables file loads absolute", true,
                new File(form.observables.getText()).isAbsolute());
        expect("the relative data row resolves against the .cfg's directory",
                new File(dir, "lf.dat").getAbsolutePath(), form.data[0].getText());
    }

    private static void checkPathsInsideTheFolderAreStoredRelative() throws IOException {
        File dir = tempDir("inside");
        File nested = temp(dir, "network");
        expect("the nested case directory was created", true, nested.mkdirs());
        Scenario scenario = new Scenario();
        scenario.setData(0, touch(dir, "lf.dat"));
        scenario.setData(1, touch(nested, "dyn.dat"));
        File cfg = temp(dir, "case.cfg");
        ScenarioFile.save(scenario, cfg, dir, "3.74.21");

        String text = read(cfg);
        expect("a file beside the .cfg is stored bare", true,
                text.contains("data.1 = lf.dat\n"));
        expect("a file below the .cfg is stored with forward slashes", true,
                text.contains("data.2 = network/dyn.dat\n"));
    }

    private static void checkPathsOutsideTheFolderStayAbsolute() throws IOException {
        File cfgDir = tempDir("outside-cfg");
        File caseDir = tempDir("outside-case");
        String outside = touch(caseDir, "lf.dat");
        Scenario scenario = new Scenario();
        scenario.setData(0, outside);
        File cfg = temp(cfgDir, "case.cfg");
        ScenarioFile.save(scenario, cfg, cfgDir, "3.74.21");

        expect("a file outside the .cfg's tree is stored absolute", true,
                read(cfg).contains("data.1 = " + ScenarioFile.escape(outside) + "\n"));
        expect("and comes back as the same absolute path", outside,
                ScenarioFile.load(cfg).scenario().data(0));
    }

    /** A .cfg kept away from the case still names the same files. */
    private static void checkAConfigInAnotherDirectoryStillResolves() throws IOException {
        File caseDir = tempDir("elsewhere-case");
        File cfgDir = tempDir("elsewhere-cfg");
        String lf = touch(caseDir, "lf.dat");
        String dst = touch(caseDir, "disturb.dst");
        Scenario scenario = new Scenario();
        scenario.setData(0, lf);
        scenario.setDisturbance(dst);
        File cfg = temp(cfgDir, "case.cfg");
        ScenarioFile.save(scenario, cfg, caseDir, "3.74.21");

        Scenario back = ScenarioFile.load(cfg).scenario();
        expect("the data row survives a .cfg kept elsewhere", lf, back.data(0));
        expect("the disturbance survives a .cfg kept elsewhere", dst, back.disturbance());
    }

    /**
     * Row four empty with row five filled is a case built that way, and
     * {@code cmd.txt} lists the rows in order, so packing them down on save
     * would rewrite the study.
     */
    private static void checkEmptyRowsKeepTheirSlot() throws IOException {
        File dir = tempDir("slots");
        Scenario scenario = new Scenario();
        scenario.setData(0, touch(dir, "first.dat"));
        scenario.setData(4, touch(dir, "fifth.dat"));
        File cfg = temp(dir, "gappy.cfg");
        ScenarioFile.save(scenario, cfg, dir, "3.74.21");

        expect("an empty row writes no key", false, read(cfg).contains("data.2"));
        Scenario back = ScenarioFile.load(cfg).scenario();
        expect("row 1 kept its slot", new File(dir, "first.dat").getAbsolutePath(), back.data(0));
        expect("row 2 is still empty", "", back.data(1));
        expect("row 5 kept its slot", new File(dir, "fifth.dat").getAbsolutePath(), back.data(4));
    }

    /**
     * The escaper, against the strings that actually break a properties file:
     * a Windows path is nothing but backslashes, and a directory with a space
     * in it is the norm on Windows and macOS.
     */
    private static void checkWindowsPathsSurviveTheEscaper() {
        expect("backslashes are doubled", "C\\\\Program Files\\\\case\\\\lf.dat",
                ScenarioFile.escape("C\\Program Files\\case\\lf.dat"));
        expect("a leading blank is escaped", "\\ leading", ScenarioFile.escape(" leading"));
        expect("an inner blank is left alone", "two words", ScenarioFile.escape("two words"));
        expect("a colon needs no escape", "C:/case/lf.dat", ScenarioFile.escape("C:/case/lf.dat"));
        expect("a tab is escaped", "a\\tb", ScenarioFile.escape("a\tb"));
        expect("a hash is left alone", "case#1.dat", ScenarioFile.escape("case#1.dat"));
    }

    private static void checkNewerFormatIsRefused() throws IOException {
        File dir = tempDir("newer");
        File cfg = temp(dir, "future.cfg");
        write(cfg, "stepss.format = 2", "data.1 = lf.dat");
        expectRefused("a newer format is refused", cfg, "newer STEPSS");
    }

    private static void checkFormatlessFileIsRefused() throws IOException {
        File dir = tempDir("formatless");
        File cfg = temp(dir, "plain.cfg");
        write(cfg, "data.1 = lf.dat");
        expectRefused("a file declaring no format is refused", cfg,
                "is not a STEPSS scenario file");
    }

    private static void checkUnreadableFormatIsRefused() throws IOException {
        File dir = tempDir("garbled");
        File cfg = temp(dir, "garbled.cfg");
        write(cfg, "stepss.format = one", "data.1 = lf.dat");
        expectRefused("a non-numeric format is refused", cfg, "format number");
    }

    /**
     * A file with the format key and nothing else. The old load path threw
     * NumberFormatException on the first missing key, from inside a
     * {@code catch (IOException)} that could not see it.
     */
    private static void checkAnEmptyFileLoadsWithoutThrowing() throws IOException {
        File dir = tempDir("bare");
        File cfg = temp(dir, "bare.cfg");
        write(cfg, "stepss.format = 1");

        Form form = new Form();
        form.types[0].setSelectedItem("Latency");
        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        List<String> problems = form.binding.apply(loaded.scenario());
        expect("a file of missing keys reports nothing", "[]", loaded.problems().toString());
        expect("and applies cleanly", "[]", problems.toString());
        expect("an absent data row loads blank", "", form.data[0].getText());
        expect("an absent checkbox loads cleared", false, form.init.isSelected());
        expect("an absent type leaves the dropdown alone", "Latency",
                form.types[0].getSelectedItem());
    }

    private static void checkJunkBooleanIsReported() throws IOException {
        File dir = tempDir("junkbool");
        File cfg = temp(dir, "junk.cfg");
        write(cfg, "stepss.format = 1", "record.init = yes please");

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        expect("a junk boolean is read as false", false, loaded.scenario().saveInit());
        expectMentions("a junk boolean is reported", loaded.problems(), "record.init");
    }

    private static void checkUnknownObservableTypeIsReported() throws IOException {
        File dir = tempDir("obstype");
        File cfg = temp(dir, "stale.cfg");
        write(cfg, "stepss.format = 1", "runtime.1.type = Branch Rective Power");

        Form form = new Form();
        form.types[0].setSelectedItem("Center of Inertia");
        List<String> problems = form.binding.apply(ScenarioFile.load(cfg).scenario());
        expectMentions("a retired observable type is reported", problems,
                "Branch Rective Power");
        expect("and the row is left where it was", "Center of Inertia",
                form.types[0].getSelectedItem());
    }

    private static void checkUnknownKeyIsReported() throws IOException {
        File dir = tempDir("unknownkey");
        File cfg = temp(dir, "typo.cfg");
        write(cfg, "stepss.format = 1", "data.11 = eleventh.dat", "record.inits = true");

        List<String> problems = ScenarioFile.load(cfg).problems();
        expectMentions("a key past the last row is reported", problems, "data.11");
        expectMentions("a misspelled key is reported", problems, "record.inits");
    }

    /**
     * The two names a file saved by an older STEPSS carries.
     *
     * <p>{@code record.dump} was renamed {@code record.init} and
     * {@code observables.wizard} was dropped, both without a format bump and
     * both without a compatibility read. That choice is only defensible while
     * the user is told, so the sentence they get is what this pins: the file
     * still loads, every other key still applies, and the initialisation tick
     * comes back cleared rather than silently carrying the old value.
     */
    private static void checkRetiredKeysAreReported() throws IOException {
        File dir = tempDir("retired");
        File cfg = temp(dir, "old.cfg");
        write(cfg, "stepss.format = 1", "data.1 = " + ScenarioFile.escape(touch(dir, "lf.dat")),
                "observables.wizard = true", "record.dump = true");

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        expectMentions("the renamed key is reported", loaded.problems(), "record.dump");
        expectMentions("the dropped key is reported", loaded.problems(),
                "observables.wizard");
        expect("the retired keys do not carry over", false, loaded.scenario().saveInit());
        expect("and the rest of the file still loads",
                new File(dir, "lf.dat").getAbsolutePath(), loaded.scenario().data(0));
    }

    private static void checkMissingFilesAreReported() throws IOException {
        File dir = tempDir("gone");
        File cfg = temp(dir, "gone.cfg");
        write(cfg, "stepss.format = 1", "data.1 = deleted.dat",
                "disturbance = " + ScenarioFile.escape(touch(dir, "disturb.dst")));

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        expectMentions("a file that is not there is reported", loaded.problems(),
                "System data row 1");
        expect("a file that is there is not reported", 1, loaded.problems().size());
        expect("and the missing path is still restored so it can be fixed",
                new File(dir, "deleted.dat").getAbsolutePath(), loaded.scenario().data(0));
    }

    private static void checkIsEmpty() {
        Scenario scenario = new Scenario();
        expect("a fresh scenario is empty", true, scenario.isEmpty());
        scenario.setSaveInit(true);
        scenario.setRuntimeName(0, "g1");
        expect("settings alone do not make it a scenario", true, scenario.isEmpty());
        scenario.setData(9, "lf.dat");
        expect("the last data row is enough", false, scenario.isEmpty());
    }

    private static void checkBindingRejectsAShortWiring() {
        Form form = new Form();
        try {
            new ScenarioBinding(Arrays.copyOf(form.data, Scenario.DATA_SLOTS - 1),
                    form.disturbance, form.observables, form.diagram,
                    form.types, form.names, form.trajectory, form.continuous,
                    form.discrete, form.init);
            fail("a binding wired one field short is refused: no exception");
        } catch (IllegalArgumentException expected) {
            expect("a binding wired one field short is refused", true,
                    String.valueOf(expected.getMessage()).contains("data fields"));
        }
    }

    /**
     * The controls, buried the way {@code applyModernChrome()} buries them.
     *
     * <p>Every control goes into a row panel, every row panel into a content
     * panel, and the content panel into a scroll pane, so nothing is a direct
     * child of the panel a tab-walking loop would have started from.
     */
    private static final class Form {

        private final JTextField[] data = new JTextField[Scenario.DATA_SLOTS];
        private final JTextField disturbance = new JTextField();
        private final JTextField observables = new JTextField();
        private final JTextField diagram = new JTextField();
        private final JComboBox<?>[] types = new JComboBox<?>[Scenario.RUNTIME_ROWS];
        private final JTextField[] names = new JTextField[Scenario.RUNTIME_ROWS];
        private final JCheckBox trajectory = new JCheckBox();
        private final JCheckBox continuous = new JCheckBox();
        private final JCheckBox discrete = new JCheckBox();
        private final JCheckBox init = new JCheckBox();
        private final ScenarioBinding binding;

        Form() {
            JPanel content = new JPanel();
            for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
                data[slot] = new JTextField();
                content.add(row(data[slot]));
            }
            for (int row = 0; row < Scenario.RUNTIME_ROWS; row++) {
                types[row] = new JComboBox<>(new DefaultComboBoxModel<>(TYPES));
                names[row] = new JTextField();
                content.add(row(types[row], names[row]));
            }
            content.add(row(disturbance, observables, diagram));
            content.add(row(trajectory, continuous, discrete, init));
            // The third level, and the one that broke the old walk.
            new JScrollPane(content);
            binding = new ScenarioBinding(data, disturbance, observables, diagram,
                    types, names, trajectory, continuous, discrete, init);
        }

        private static JPanel row(java.awt.Component... controls) {
            JPanel line = new JPanel();
            for (java.awt.Component control : controls) {
                line.add(control);
            }
            return line;
        }

        void blank() {
            for (JTextField field : data) {
                field.setText("");
            }
            disturbance.setText("");
            observables.setText("");
            diagram.setText("");
            for (int row = 0; row < Scenario.RUNTIME_ROWS; row++) {
                types[row].setSelectedIndex(0);
                names[row].setText("");
            }
            trajectory.setSelected(false);
            continuous.setSelected(false);
            discrete.setSelected(false);
            init.setSelected(false);
        }
    }

    /**
     * The disturbance-file guard, which decides whether a run may start.
     *
     * <p>Every case here mirrors a rule in the engine's own reader
     * ({@code src/io/disturb.f90}). A guard that refused a file the engine
     * accepts would stop a run that would have worked, so these checks are as
     * much about what passes as about what does not.
     */
    private static void checkDisturbanceFileGuard() throws IOException {
        File dir = tempDir("dst");

        File good = temp(dir, "good.dst");
        write(good, "  0.000 CONTINUE SOLVER BD 0.020 0.001 0. ABL",
                "  1.000 FAULT BUS 4032 0. 0.8",
                "240.000 STOP");
        expect("a complete disturbance file passes", null,
                DisturbanceFile.problem(good));

        File trailing = temp(dir, "trailing.dst");
        write(trailing, "240.000 STOP ;");
        expect("STOP may carry trailing text", null,
                DisturbanceFile.problem(trailing));

        File early = temp(dir, "early.dst");
        write(early, "240.000 STOP", "300.000 FAULT BUS 4032 0. 0.8");
        expect("the engine stops reading at STOP, so what follows is ignored",
                null, DisturbanceFile.problem(early));

        File commented = temp(dir, "commented.dst");
        write(commented, "# a comment", "", "! another",
                "  1.0 FAULT BUS 4032 0. 0.8", "  9.0 STOP");
        expect("comments and blank lines are skipped", null,
                DisturbanceFile.problem(commented));

        File inComment = temp(dir, "in-comment.dst");
        write(inComment, "# 240.000 STOP", "  1.0 FAULT BUS 4032 0. 0.8");
        says("a STOP inside a comment does not count",
                DisturbanceFile.problem(inComment), "no STOP record");

        File noStop = temp(dir, "nostop.dst");
        write(noStop, "  1.000 FAULT BUS 4032 0. 0.8");
        says("a file with no STOP is refused",
                DisturbanceFile.problem(noStop), "no STOP record");
        says("and the refusal names the file",
                DisturbanceFile.problem(noStop), "nostop.dst");
        says("and says what to add",
                DisturbanceFile.problem(noStop), "240.0 STOP");

        File onlyComments = temp(dir, "comments.dst");
        write(onlyComments, "# nothing here");
        says("a file of only comments has no records",
                DisturbanceFile.problem(onlyComments), "no disturbance records");

        File untimed = temp(dir, "untimed.dst");
        write(untimed, "  1.0 FAULT BUS 4032 0. 0.8", "STOP");
        says("a STOP with no time is named for what it is",
                DisturbanceFile.problem(untimed), "STOP without a time");

        says("a file that is not there is refused",
                DisturbanceFile.problem(temp(dir, "absent.dst")), "does not exist");
        says("and so is a directory", DisturbanceFile.problem(dir),
                "does not exist");
        expect("as is no file at all", true,
                DisturbanceFile.problem(null) != null);
    }

    /** Fails unless *problem* is a sentence carrying *needle*. */
    private static void says(String what, String problem, String needle) {
        if (problem != null && problem.contains(needle)) {
            pass(what);
        } else {
            fail(what + ": <" + problem + "> does not mention <" + needle + ">");
        }
    }

    private static File tempDir(String name) throws IOException {
        File dir = Files.createTempDirectory("stepss-scenario-" + name).toFile();
        dir.deleteOnExit();
        return dir;
    }

    /**
     * Names a file inside a temporary directory and books its removal.
     *
     * <p>Everything the harness creates goes through here or through
     * {@link #tempDir}, including the files {@link ScenarioFile#save} writes
     * rather than the harness itself. {@code deleteOnExit} deletes in reverse
     * order of registration, so a directory registered before its contents is
     * empty by the time its own turn comes and actually goes; a saved
     * {@code .cfg} nobody registered is what leaves the directory behind and
     * makes every run of this harness litter {@code /tmp}.
     */
    private static File temp(File dir, String name) {
        File file = new File(dir, name);
        file.deleteOnExit();
        return file;
    }

    /** Creates an empty file and returns its absolute path. */
    private static String touch(File dir, String name) throws IOException {
        File file = temp(dir, name);
        Files.write(file.toPath(), new byte[0]);
        return file.getAbsolutePath();
    }

    private static void write(File file, String... lines) throws IOException {
        Files.write(file.toPath(),
                (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        file.deleteOnExit();
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void expectRefused(String what, File cfg, String mustContain) {
        try {
            ScenarioFile.load(cfg);
            fail(what + ": no exception");
        } catch (IOException ex) {
            String message = String.valueOf(ex.getMessage());
            if (message.contains(mustContain)) {
                pass(what);
            } else {
                fail(what + ": message <" + message + "> does not mention <"
                        + mustContain + ">");
            }
        }
    }

    private static void expectMentions(String what, List<String> problems, String needle) {
        for (String problem : problems) {
            if (problem.contains(needle)) {
                pass(what);
                return;
            }
        }
        fail(what + ": " + problems + " mentions no <" + needle + ">");
    }

    private static void expect(String what, Object want, Object got) {
        // Null-safe on both sides, like PickerHarness: a null expectation must
        // read as a comparison, not as an NPE inside the harness itself.
        if (want == null ? got == null : want.equals(got)) {
            pass(what);
        } else {
            fail(what + ": wanted <" + want + "> got <" + got + ">");
        }
    }

    private static void pass(String what) {
        System.out.println("PASS  " + what);
    }

    private static void fail(String what) {
        failures++;
        System.out.println("FAIL  " + what);
    }
}
