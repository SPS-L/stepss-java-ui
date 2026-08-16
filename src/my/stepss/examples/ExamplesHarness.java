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
            check(example.id() + " names at least one data file",
                    !example.data().isEmpty());
            check(example.id() + " fits the ten data slots",
                    example.data().size() <= 10);
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
        }
    }

    /**
     * The payload is in the jar, extracts, and leaves every named file behind.
     *
     * <p>The slot files are checked for content as well as existence: a zero
     * byte {@code .dat} would satisfy the build's completeness check and then
     * fail in the engine.
     */
    private static void checkInstalls(Example example) throws IOException {
        File dir = createTempDir("stepss-example-" + example.id());
        try {
            List<File> written = ExampleInstaller.install(example, dir);
            check(example.id() + " extracts every file it names",
                    written.size() == example.retained().size());

            List<String> missing = ExampleInstaller.missingFrom(example, dir);
            check(example.id() + " leaves nothing missing: " + missing, missing.isEmpty());

            List<String> slots = new ArrayList<>(example.data());
            slots.add(example.dist());
            slots.add(example.obs());
            for (String slot : slots) {
                File file = new File(dir, slot);
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
