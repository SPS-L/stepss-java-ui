package my.stepss;

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
}
