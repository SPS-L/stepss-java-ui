package my.stepss;

/**
 * Dotted version numbers, ordered the way tools/ci/pins.py orders them.
 *
 * <p>This is the Java mirror of {@code pins.version_key}, and the two have to
 * agree: that function decides which tag CI cuts, this class decides what the
 * update check tells the user about the very same numbers. The release
 * sequence is not a decimal - a release that moves no component becomes
 * v3.55.1 (see tools/ci/release.py:next_version) - so the numbers here have
 * one, two, or three segments and cannot be parsed as a double. Doing so threw
 * NumberFormatException on the first such release, and, on the copy of the
 * version bundled in the jar, threw it during construction of the main window.
 */
final class Version {

    private Version() {
    }

    /**
     * The version as a sequence of integers, or null if it is not one.
     *
     * <p>A segment that is not an integer ("1.2.0-rc1", "3.55b") makes the
     * whole version incomparable and yields null rather than being coerced
     * into some position in the order. Callers must read null as "do not
     * act": claiming a version is up to date is as wrong as claiming it is
     * stale when the truth is that the number was never understood.
     *
     * @param version a dotted version such as "3.55" or "3.55.1", without any
     *                leading "v"
     * @return the segments in order, or null if any segment is not an integer
     */
    static int[] key(String version) {
        if (version == null) {
            return null;
        }
        // Limit -1 so trailing empty segments survive the split: with Java's
        // default limit "3.55." parses as {3, 55}, quietly accepting a version
        // string that is malformed, where Python's split reports the empty
        // segment and the whole version is rejected.
        String[] parts = version.split("\\.", -1);
        int[] key = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                key[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return key;
    }

    /**
     * "v3.55" to "3.55". A bare version passes through unchanged.
     *
     * <p>The Java mirror of {@code pins.version_of}. Tags carry the "v",
     * {@link #key} does not accept it, and the two forms meet here.
     *
     * @param tag a tag or a bare version, never null
     * @return the version without its leading "v"
     */
    static String ofTag(String tag) {
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }

    /**
     * The version named by a GitHub release URL, or null if it names none.
     *
     * <p>Fed the Location header of github.com/&lt;repo&gt;/releases/latest,
     * which redirects to .../releases/tag/&lt;tag&gt;. A repository with no
     * releases yet does not redirect to a tag at all - it answers with the
     * releases page - so the marker is absent and the answer is null, which
     * {@link #key}'s callers already handle as "do not act". Guessing a
     * version out of a URL that does not carry one would report every install
     * as out of date.
     *
     * @param location a redirect target, or null if the response carried none
     * @return the bare version, or null if the URL names no release tag
     */
    static String fromReleaseUrl(String location) {
        if (location == null) {
            return null;
        }
        final String marker = "/releases/tag/";
        int at = location.indexOf(marker);
        if (at < 0) {
            return null;
        }
        String tag = location.substring(at + marker.length());
        // A trailing query or fragment is not part of the tag. GitHub does not
        // add one today, but neither does anything guarantee it never will,
        // and either would otherwise travel into the version string and make
        // an ordinary release look incomparable.
        int end = tag.length();
        for (int i = 0; i < tag.length(); i++) {
            char c = tag.charAt(i);
            if (c == '?' || c == '#' || c == '/') {
                end = i;
                break;
            }
        }
        tag = tag.substring(0, end);
        return tag.isEmpty() ? null : ofTag(tag);
    }

    /**
     * Orders two keys from {@link #key}.
     *
     * <p>The common prefix is compared segment by segment, and when that
     * prefix is equal the longer version is the greater one, so 3.55 &lt;
     * 3.55.1. Comparing segment by segment is also what keeps 3.9 &lt; 3.10:
     * read as decimals those two sort the wrong way round, which would have
     * reported a newer release as older.
     *
     * @param a left-hand key, never null
     * @param b right-hand key, never null
     * @return negative if a &lt; b, zero if equal, positive if a &gt; b
     */
    static int compare(int[] a, int[] b) {
        int common = Math.min(a.length, b.length);
        for (int i = 0; i < common; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i] ? -1 : 1;
            }
        }
        if (a.length == b.length) {
            return 0;
        }
        return a.length < b.length ? -1 : 1;
    }
}
