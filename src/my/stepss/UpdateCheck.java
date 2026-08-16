package my.stepss;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The two halves of "is there a newer STEPSS", kept apart so one of them can
 * be tested.
 *
 * <p>{@link #latestLocation} is the network. {@link #noticeFor} is the
 * decision, and it is pure, which is what lets {@link UpdateHarness} pin down
 * every way the answer can be "say nothing" without reaching github.com.
 *
 * <p>Both the startup check and Help -> Check for updates go through here,
 * so the two cannot drift into disagreeing about what counts as newer.
 */
final class UpdateCheck {

    private UpdateCheck() {
    }

    /**
     * The {@code Location} header of the releases/latest redirect, which names
     * the newest release, or null if the response carried none.
     *
     * <p>/releases/latest redirects to /releases/tag/&lt;tag&gt;, so the
     * redirect itself names the release and following it only to scrape the
     * page for the same string would be wasted work. Redirects are therefore
     * off and the header is read directly.
     *
     * <p>The timeouts are not optional. The manual check runs this on the EDT,
     * and an unreachable host with no timeout freezes the window until the OS
     * gives up, which is minutes on some networks.
     */
    static String latestLocation(String releasesLatestUrl) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(releasesLatestUrl).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            return connection.getHeaderField("Location");
        } finally {
            connection.disconnect();
        }
    }

    /**
     * What to tell the user, or null to tell them nothing.
     *
     * <p>Null covers every case where an announcement would be a guess: no
     * redirect, a redirect naming no release tag, either version not a dotted
     * integer, the same version, or an older one. Callers raise a notice only
     * on a non-null return, so "we could not work out the answer" and "you are
     * up to date" never get confused for each other at startup.
     *
     * @param running  the running version, without a leading "v"
     * @param location the redirect target from {@link #latestLocation}
     */
    static String noticeFor(String running, String location) {
        String published = Version.fromReleaseUrl(location);
        if (published == null) {
            return null;
        }
        int[] publishedKey = Version.key(published);
        int[] runningKey = Version.key(running);
        if (publishedKey == null || runningKey == null) {
            return null;
        }
        if (Version.compare(publishedKey, runningKey) <= 0) {
            return null;
        }
        return "<html>STEPSS " + published + " is available. You are running "
                + running + ".</html>";
    }
}
