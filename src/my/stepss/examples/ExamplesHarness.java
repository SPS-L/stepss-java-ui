package my.stepss.examples;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Headless checks for the bundled examples, run from
 * {@code tools/examples-harness.sh}. This repository has no unit-test
 * framework; harnesses are the substitute.
 *
 * <p>What it is for: the build already refuses to ship an example whose pinned
 * release stopped carrying a named file, but that check reads the downloaded
 * archive. This one reads the jar, which is the artefact users get, and answers
 * the question the build cannot: does opening each example actually leave a
 * complete case on disk?
 *
 * <p>The dialog itself is not checked here. It is Swing layout driven by the
 * same descriptor these checks parse, so what could go wrong in it is what it
 * looks like, and that is verified by opening it.
 */
public final class ExamplesHarness {

    private static int failures = 0;

    private ExamplesHarness() {
    }

    public static void main(String[] args) throws Exception {
        List<Example> examples = ExampleCatalog.load();

        checkCatalogIsSane(examples);
        checkOptionalSlotsParse();
        checkDataIsStillRequired();
        for (Example example : examples) {
            checkInstalls(example);
        }
        checkEscapingEntryIsRefused();

        if (failures > 0) {
            System.err.println(failures + " check(s) FAILED");
            System.exit(1);
        }
        System.out.println("All examples checks passed (" + examples.size()
                + " examples).");
    }

    /**
     * Ids and directories are unique, and nothing is blank.
     *
     * <p>Two entries sharing a {@code dir} would extract over each other, and
     * the second open would silently offer the first example's files under the
     * second's name.
     */
    private static void checkCatalogIsSane(List<Example> examples) {
        check("the catalogue is not empty", !examples.isEmpty());

        Set<String> ids = new HashSet<>();
        Set<String> dirs = new HashSet<>();
        for (Example example : examples) {
            check("id '" + example.id() + "' is unique", ids.add(example.id()));
            check("dir '" + example.dir() + "' is unique", dirs.add(example.dir()));
            check(example.id() + " names a .cfg",
                    example.cfg().endsWith(".cfg"));
            check(example.id() + " has a documentation URL",
                    example.docs().startsWith("https://"));
            // The extraction directory is joined onto the examples root, so a
            // name with a separator in it would put the copy somewhere the
            // dialog never mentioned.
            check(example.id() + " has a plain directory name",
                    example.dir().indexOf('/') < 0 && example.dir().indexOf('\\') < 0
                            && !example.dir().equals(".") && !example.dir().equals(".."));
            check(example.id() + " retains its LICENSE",
                    example.retained().contains("LICENSE"));
            check(example.id() + " retains its scenario file",
                    example.retained().contains(example.cfg()));
        }
    }

    /** The descriptor text a power-flow-only entry produces. */
    private static final String PF_ONLY_DESCRIPTOR
            = "examples=pfonly\n"
            + "pfonly.name=Power flow only\n"
            + "pfonly.scale=6 buses\n"
            + "pfonly.summary=A case with no dynamic data.\n"
            + "pfonly.docs=https://example.invalid/pfonly\n"
            + "pfonly.dir=pf-only\n"
            + "pfonly.cfg=case.cfg\n"
            + "pfonly.extra=case.dat, case.svg, README.md, LICENSE\n";

    /**
     * A minimal entry parses, and its scenario file is retained without being
     * repeated.
     *
     * <p>{@code retained()} feeds {@code ExamplesPack}, which looks up each
     * name in the upstream archive, so a blank or duplicated entry there fails
     * the build with a message naming no file or digests differently from the
     * same content declared once.
     */
    private static void checkOptionalSlotsParse() throws IOException {
        List<Example> examples = ExampleCatalog.load(
                new ByteArrayInputStream(PF_ONLY_DESCRIPTOR.getBytes("UTF-8")));
        check("a power-flow-only entry parses", examples.size() == 1);
        Example only = examples.get(0);
        check("its scenario file is read", "case.cfg".equals(only.cfg()));
        check("nothing retained is blank", !only.retained().contains(""));
        check("the scenario file is retained", only.retained().contains("case.cfg"));
        check("retained holds exactly the named files, once each",
                only.retained().size() == 5);

        String repeated = PF_ONLY_DESCRIPTOR.replace(
                "pfonly.extra=case.dat", "pfonly.extra=case.cfg, case.dat");
        Example twice = ExampleCatalog.load(
                new ByteArrayInputStream(repeated.getBytes("UTF-8"))).get(0);
        check("a scenario file named twice is retained once",
                twice.retained().size() == 5);
    }

    /** A scenario file is still required, because an example without one is not one. */
    private static void checkDataIsStillRequired() {
        String descriptor = PF_ONLY_DESCRIPTOR.replace("pfonly.cfg=case.cfg\n", "");
        try {
            ExampleCatalog.load(new ByteArrayInputStream(descriptor.getBytes("UTF-8")));
            check("a descriptor with no scenario file is refused", false);
        } catch (IOException expected) {
            check("a descriptor with no scenario file is refused",
                    expected.getMessage().contains("pfonly.cfg"));
        }
    }

    /**
     * The payload is in the jar, extracts, and leaves every named file behind.
     *
     * <p>Every file the scenario names is checked for content as well as
     * existence: a zero byte {@code .dat} would satisfy the build's
     * completeness check and then fail in the engine. The scenario is loaded
     * the way {@code applyExample} loads it, so this is also the check that the
     * shipped {@code .cfg} resolves against the directory it was extracted
     * into, which is the whole reason the descriptor no longer transcribes it.
     */
    private static void checkInstalls(Example example) throws IOException {
        File dir = createTempDir("stepss-example-" + example.id());
        try {
            List<File> written = ExampleInstaller.install(example, dir);
            check(example.id() + " extracts every file it names",
                    written.size() == example.retained().size());

            List<String> missing = ExampleInstaller.missingFrom(example, dir);
            check(example.id() + " leaves nothing missing: " + missing, missing.isEmpty());

            my.stepss.config.ScenarioFile.Loaded loaded =
                    my.stepss.config.ScenarioFile.load(new File(dir, example.cfg()));
            check(example.id() + " scenario loads without problems: "
                    + loaded.problems(), loaded.problems().isEmpty());

            List<String> slots = new ArrayList<>();
            my.stepss.config.Scenario scenario = loaded.scenario();
            for (int i = 0; i < my.stepss.config.Scenario.DATA_SLOTS; i++) {
                if (!scenario.data(i).isEmpty()) {
                    slots.add(scenario.data(i));
                }
            }
            for (String optional : new String[]{scenario.disturbance(),
                scenario.observablesFile(), scenario.diagram()}) {
                if (!optional.isEmpty()) {
                    slots.add(optional);
                }
            }
            check(example.id() + " scenario fills at least one data slot", !slots.isEmpty());
            for (String slot : slots) {
                // Absolute already: ScenarioFile resolves every stored path
                // against the directory the .cfg was read from.
                File file = new File(slot);
                check(example.id() + " slot file " + slot + " has content",
                        file.isFile() && file.length() > 0);
            }

            // Re-running over the same directory is what "Reuse my copy" leads
            // to after a Fresh copy, and what a second Fresh copy does.
            ExampleInstaller.install(example, dir);
            check(example.id() + " is still complete after a second extraction",
                    ExampleInstaller.missingFrom(example, dir).isEmpty());
        } finally {
            ExampleInstaller.deleteRecursively(dir);
        }
    }

    /**
     * An archive entry pointing outside the target directory is refused.
     *
     * <p>The payloads are built by {@code ExamplesPack} from names the
     * descriptor lists, so this cannot happen today. It is checked anyway
     * because the cost of being wrong is writing a file of an upstream
     * repository's choosing anywhere the user can write, and because the guard
     * is otherwise never executed on the path it exists for.
     */
    private static void checkEscapingEntryIsRefused() throws IOException {
        File dir = createTempDir("stepss-example-escape");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(bytes);
            zip.putNextEntry(new ZipEntry("../escaped.dat"));
            zip.write("nope".getBytes("UTF-8"));
            zip.closeEntry();
            zip.close();

            boolean refused = false;
            try {
                ExampleInstaller.unpack("hostile.zip",
                        new ByteArrayInputStream(bytes.toByteArray()), dir);
            } catch (IOException expected) {
                refused = true;
            }
            check("an entry escaping the target directory is refused", refused);
            check("and nothing was written outside it",
                    !new File(dir.getParentFile(), "escaped.dat").exists());
        } finally {
            ExampleInstaller.deleteRecursively(dir);
        }
    }

    private static File createTempDir(String prefix) throws IOException {
        return java.nio.file.Files.createTempDirectory(prefix).toFile();
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("  ok    " + what);
        } else {
            System.out.println("  FAIL  " + what);
            failures++;
        }
    }
}
