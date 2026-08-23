package my.stepss;

import java.lang.reflect.InvocationTargetException;
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

    private static void expect(String what, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
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
