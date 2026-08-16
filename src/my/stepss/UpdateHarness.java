package my.stepss;

import java.lang.reflect.InvocationTargetException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

/**
 * Headless checks for the parts of the startup update check that need no
 * network. This repository has no unit-test framework and is not gaining one,
 * so this is where the decision is pinned down; the request itself is
 * exercised by the manual Help -> Check for updates path.
 */
public final class UpdateHarness {

    private static int failures = 0;

    private static final String TAG = "https://github.com/SPS-L/stepss-java-ui/releases/tag/";

    public static void main(String[] args) {
        checkNewerIsAnnounced();
        checkNothingToSayIsSilent();
        checkUnreadableIsSilent();
        checkNodeMigration();
        checkFirstRunKeyMigration();
        checkBannerActionButton();
        checkLicenceHtml();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkNewerIsAnnounced() {
        String notice = UpdateCheck.noticeFor("3.74.10", TAG + "v3.75");
        expectNotNull("newer version announced", notice);
        expectContains("names the published version", notice, "3.75");
        expectContains("names the running version", notice, "3.74.10");
        // Segment-wise ordering, not decimal: 3.10 is newer than 3.9.
        expectNotNull("3.10 beats 3.9", UpdateCheck.noticeFor("3.9", TAG + "v3.10"));
        // A counter release is newer than the release it counts from.
        expectNotNull("3.55.1 beats 3.55", UpdateCheck.noticeFor("3.55", TAG + "v3.55.1"));
    }

    private static void checkNothingToSayIsSilent() {
        expectNull("same version is silent",
                UpdateCheck.noticeFor("3.74.10", TAG + "v3.74.10"));
        expectNull("older published is silent",
                UpdateCheck.noticeFor("3.75", TAG + "v3.74.10"));
        expectNull("3.9 does not beat 3.10",
                UpdateCheck.noticeFor("3.10", TAG + "v3.9"));
    }

    private static void checkUnreadableIsSilent() {
        expectNull("null location is silent", UpdateCheck.noticeFor("3.74.10", null));
        expectNull("no tag in location is silent", UpdateCheck.noticeFor("3.74.10",
                "https://github.com/SPS-L/stepss-java-ui/releases"));
        expectNull("unparseable published is silent",
                UpdateCheck.noticeFor("3.74.10", TAG + "v3.75b"));
        expectNull("unparseable running is silent",
                UpdateCheck.noticeFor("3.55b", TAG + "v3.75"));
    }

    private static void expectNull(String what, String actual) {
        if (actual != null) {
            System.out.println("FAIL " + what + ": expected null, got " + actual);
            failures++;
        }
    }

    private static void expectNotNull(String what, String actual) {
        if (actual == null) {
            System.out.println("FAIL " + what + ": expected a notice, got null");
            failures++;
        }
    }

    private static void expectContains(String what, String actual, String needle) {
        if (actual == null || actual.indexOf(needle) < 0) {
            System.out.println("FAIL " + what + ": " + actual + " does not contain " + needle);
            failures++;
        }
    }

    /**
     * The scratch root every migration check runs under. Never the real node:
     * these checks call removeNode(), and pointing that at a developer's own
     * settings would eat their theme and working directory.
     */
    private static final String SCRATCH = "my.stepss.harness-scratch";

    private static void checkNodeMigration() {
        try {
            // A fresh install: neither node exists, and the result is empty.
            Preferences root = freshScratch();
            Preferences made = PreferenceMigration.node(root, "legacy", "current");
            expectInt("fresh install starts empty", 0, made.keys().length);

            // A legacy install: every key moves, and the old node is gone.
            root = freshScratch();
            Preferences legacy = root.node("legacy");
            legacy.put("workingDirectory", "/home/someone/cases");
            legacy.putBoolean("darkTheme", true);
            legacy.putInt("windowWidth", 1280);
            legacy.flush();
            made = PreferenceMigration.node(root, "legacy", "current");
            expect("string key moved", "/home/someone/cases",
                    made.get("workingDirectory", null));
            expect("boolean key survives as a boolean", "true",
                    String.valueOf(made.getBoolean("darkTheme", false)));
            expectInt("int key survives as an int", 1280, made.getInt("windowWidth", 0));
            expectFalse("legacy node removed", root.nodeExists("legacy"));

            // Several keys move as a set, pinned by count rather than only by
            // naming three of them: a future edit that silently drops one key
            // out of the loop, without touching any of the three checked
            // above, would still be caught here.
            root = freshScratch();
            legacy = root.node("legacy");
            legacy.put("a", "1");
            legacy.put("b", "2");
            legacy.put("c", "3");
            legacy.put("d", "4");
            legacy.put("e", "5");
            legacy.flush();
            made = PreferenceMigration.node(root, "legacy", "current");
            expectInt("every key arrives", 5, made.keys().length);

            // A key that keys() reports but get() cannot read (a registry
            // value of an unexpected type, a value removed by a second
            // instance racing this one, a mangled roaming-profile entry) is
            // not producible through the public Preferences API against this
            // backing store: put(key, null) rejects the value at write time,
            // before it is ever stored, rather than the mismatch surfacing
            // later at read time the way it can against other backing stores.
            // So this pins what is actually observable here - the store
            // refuses to hold a null value in the first place - rather than
            // the unreachable mismatch itself. PreferenceMigration's null
            // check in the copy loop is defensive for backing stores where
            // that mismatch can happen; it is not exercised by this harness.
            root = freshScratch();
            legacy = root.node("legacy");
            boolean rejectedNullValue = false;
            try {
                legacy.put("nullKey", null);
            } catch (NullPointerException expected) {
                rejectedNullValue = true;
            }
            expectTrue("the backing store itself refuses a null value", rejectedNullValue);

            // Already migrated: the current node wins and is not overwritten.
            root = freshScratch();
            legacy = root.node("legacy");
            legacy.put("workingDirectory", "/old");
            legacy.flush();
            Preferences current = root.node("current");
            current.put("workingDirectory", "/new");
            current.flush();
            made = PreferenceMigration.node(root, "legacy", "current");
            expect("populated current node is left alone", "/new",
                    made.get("workingDirectory", null));
            expectTrue("legacy node left alone", root.nodeExists("legacy"));

            removeScratch();
        } catch (BackingStoreException ex) {
            System.out.println("FAIL node migration threw: " + ex);
            failures++;
        }
    }

    private static void checkFirstRunKeyMigration() {
        try {
            // Accepted long ago under the legacy empty-string key.
            Preferences root = freshScratch();
            Preferences node = root.node("current");
            node.putBoolean("", false);
            PreferenceMigration.firstRunKey(node);
            expectFalse("acceptance carried over", node.getBoolean("stepssFirstTime", true));
            expect("legacy key removed", null, node.get("", null));

            // A genuine first run: nothing to carry, the default stands.
            root = freshScratch();
            node = root.node("current");
            PreferenceMigration.firstRunKey(node);
            expectTrue("first run still prompts", node.getBoolean("stepssFirstTime", true));

            // Already migrated: the new key is not clobbered.
            root = freshScratch();
            node = root.node("current");
            node.putBoolean("stepssFirstTime", false);
            node.putBoolean("", true);
            PreferenceMigration.firstRunKey(node);
            expectFalse("migrated key wins", node.getBoolean("stepssFirstTime", true));

            removeScratch();
        } catch (BackingStoreException ex) {
            System.out.println("FAIL first-run key migration threw: " + ex);
            failures++;
        }
    }

    /**
     * The banner's action button must belong to the message that asked for it
     * and to no other. A button left visible from a previous message is the
     * fault worth guarding: it would offer "Open release page" beside an
     * unrelated warning.
     *
     * <p>Runs on the EDT through invokeAndWait because InlineBanner marshals
     * its own work there, so asserting from this thread would race it. Swing
     * components need no display to be constructed, so this stays headless.
     */
    private static void checkBannerActionButton() {
        try {
            final InlineBanner banner = new InlineBanner();
            final boolean[] ran = {false};

            SwingUtilities.invokeAndWait(() ->
                    banner.notice("<html>STEPSS 3.75 is available.</html>",
                            "Open release page", () -> ran[0] = true));
            SwingUtilities.invokeAndWait(() -> {
                expectTrue("notice shows the banner", banner.isVisible());
                expectTrue("action button shown", banner.actionButtonVisibleForTests());
                expect("action button labelled", "Open release page",
                        banner.actionButtonTextForTests());
                banner.actionButtonClickForTests();
            });
            expectTrue("action runs the runnable", ran[0]);

            SwingUtilities.invokeAndWait(() -> banner.warn("Something else entirely"));
            SwingUtilities.invokeAndWait(() -> expectFalse(
                    "a later warn drops the button",
                    banner.actionButtonVisibleForTests()));

            SwingUtilities.invokeAndWait(() -> banner.confirm("And something worked"));
            SwingUtilities.invokeAndWait(() -> expectFalse(
                    "a later confirm drops the button",
                    banner.actionButtonVisibleForTests()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.out.println("FAIL banner checks interrupted");
            failures++;
        } catch (InvocationTargetException ex) {
            System.out.println("FAIL banner checks threw: " + ex.getCause());
            failures++;
        }
    }

    /**
     * The licence renders as headings and paragraphs rather than a wall.
     *
     * <p>Pinned because the transform is the one part of the dialog that can
     * be wrong without looking wrong: a licence whose sections stopped being
     * recognised would still render, just flat, and nobody would notice.
     */
    private static void checkLicenceHtml() {
        String html = LicenseDialog.toHtml(
                "RAMSES LICENSE\n"
                + "Copyright (c) University of Liege, Belgium.\n"
                + "\n"
                + "1. Definitions\n"
                + "\"Software\" means a copy of RAMSES.\n");
        expectContains("first line is the title", html, "<h2>RAMSES LICENSE</h2>");
        expectContains("numbered section is a heading", html, "<h3>1. Definitions</h3>");
        expectContains("prose becomes a paragraph", html, "<p>");
        expectContains("html is wrapped", html, "<html>");

        // Angle brackets in a licence must not become markup.
        expectContains("markup is escaped", LicenseDialog.toHtml("a <b> & c"), "&lt;b&gt;");

        // Text matching no rule still renders, so a reworded licence can never
        // come out worse than plain text would have.
        String plain = LicenseDialog.toHtml("just one line");
        expectContains("unmatched text survives", plain, "just one line");
    }

    private static Preferences freshScratch() throws BackingStoreException {
        removeScratch();
        return Preferences.userRoot().node(SCRATCH);
    }

    private static void removeScratch() throws BackingStoreException {
        if (Preferences.userRoot().nodeExists(SCRATCH)) {
            Preferences.userRoot().node(SCRATCH).removeNode();
            Preferences.userRoot().flush();
        }
    }

    private static void expect(String what, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            System.out.println("FAIL " + what + ": expected " + expected + ", got " + actual);
            failures++;
        }
    }

    private static void expectInt(String what, int expected, int actual) {
        if (expected != actual) {
            System.out.println("FAIL " + what + ": expected " + expected + ", got " + actual);
            failures++;
        }
    }

    private static void expectTrue(String what, boolean actual) {
        if (!actual) {
            System.out.println("FAIL " + what + ": expected true");
            failures++;
        }
    }

    private static void expectFalse(String what, boolean actual) {
        if (actual) {
            System.out.println("FAIL " + what + ": expected false");
            failures++;
        }
    }
}
