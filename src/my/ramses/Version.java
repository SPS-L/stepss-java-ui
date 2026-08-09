package my.ramses;

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
