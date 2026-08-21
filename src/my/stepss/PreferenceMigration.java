package my.stepss;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Moves saved settings when the names they live under are corrected.
 *
 * <p>A preferences node name is not a package reference, whatever it looks
 * like. It is a location in the user's preference store - the Windows
 * registry, {@code ~/.java/.userPrefs} on Linux - and it holds the theme,
 * whether to check for updates, whether to open the examples panel, and
 * whether the licence has been accepted. Renaming the literal alone does not
 * move any of that, it abandons it, and the user opens a window in the wrong
 * theme being asked to accept a licence they accepted years ago.
 *
 * <p>So the names get corrected and this carries the contents across. It also
 * drops the keys this build deliberately no longer keeps; see
 * {@link #forgetSession}. All three passes are idempotent and run on every
 * launch: the second launch finds the work already done and returns
 * immediately.
 */
final class PreferenceMigration {

    private PreferenceMigration() {
    }

    /**
     * The node to use, after moving a legacy node's contents into it.
     *
     * <p>Every key is copied, not an enumerated list of the ones that exist
     * today: an enumeration silently drops whatever is added later, and the
     * failure would be invisible until a user reported losing one setting.
     *
     * <p>Copying as strings preserves types. {@code putBoolean} and
     * {@code putInt} store their values as strings and their getters parse
     * them back, so a verbatim string copy round-trips a boolean and an int
     * exactly.
     *
     * @param root        the parent both nodes sit under
     * @param legacyName  the node being retired
     * @param currentName the node being adopted
     * @return the current node, populated
     */
    static Preferences node(Preferences root, String legacyName, String currentName)
            throws BackingStoreException {
        Preferences current = root.node(currentName);
        // Anything already here means a previous launch migrated, or this is a
        // new install that has been used. Either way the legacy node is not
        // the authority and must not overwrite it.
        if (current.keys().length > 0) {
            return current;
        }
        if (!root.nodeExists(legacyName)) {
            return current;
        }
        Preferences legacy = root.node(legacyName);
        for (String key : legacy.keys()) {
            // get() can return null for a key keys() just reported: a registry
            // value of an unexpected type on Windows, a value removed by a
            // second instance racing this one, a mangled entry from a roaming
            // profile. Preferences.put(key, null) throws NullPointerException,
            // which is not a BackingStoreException and would otherwise escape
            // this method mid-copy: current would be left non-empty with the
            // remaining keys never even attempted, and the next launch would
            // read current.keys().length > 0 as "already migrated" and never
            // look at legacy again. Skipping only the unreadable key instead
            // lets every other key finish copying and legacy still gets
            // retired below; the one value that could not be read was not
            // recoverable by retrying anyway.
            String value = legacy.get(key, null);
            if (value == null) {
                continue;
            }
            current.put(key, value);
        }
        // Flushed before the removal, not after: a crash between the two then
        // costs a duplicate node rather than the settings themselves.
        current.flush();
        legacy.removeNode();
        root.flush();
        return current;
    }

    /**
     * Carries acceptance of the licence from the legacy key to the named one.
     *
     * <p>The legacy key is the empty string, which is what
     * {@code prefs.getBoolean(ramsesFirtsTime, true)} read when
     * {@code ramsesFirtsTime} was a variable holding {@code ""}. Renaming it
     * without this would make every existing installation look like a first
     * run and prompt for the licence a second time.
     */
    static void firstRunKey(Preferences node) {
        if (node.get(StepssUI.FIRST_RUN, null) != null) {
            return;
        }
        String legacy = node.get(LEGACY_FIRST_RUN, null);
        if (legacy == null) {
            return;
        }
        node.putBoolean(StepssUI.FIRST_RUN, Boolean.parseBoolean(legacy));
        node.remove(LEGACY_FIRST_RUN);
    }

    /**
     * Drops every key that used to carry a session from one launch to the next.
     *
     * <p>What survives a launch is now the licence flag and the three settings
     * a user ticked a box for: the theme, the startup update check and the
     * examples panel. The window geometry, the working directory and the
     * examples root do not, and this is what makes that true of an
     * installation that already has them - not reading a key is not the same
     * as forgetting it, and a stored working directory left lying in the node
     * comes back the moment anything reads it again.
     *
     * <p>Runs on every launch rather than once behind a flag, because
     * {@link #node} copies <em>every</em> key out of the legacy node, so a
     * legacy installation can reintroduce all seven at any time.
     *
     * @param node the node in use, after any legacy contents have been copied in
     */
    static void forgetSession(Preferences node) {
        for (String key : FORGOTTEN) {
            node.remove(key);
        }
    }

    /**
     * The keys {@link #forgetSession} removes: the window geometry, the
     * working directory and the examples root.
     *
     * <p>Spelled out here rather than referenced from StepssUI because the
     * constants there are gone - these names exist only on disk now, in the
     * stores of installations that predate the change, and this is the last
     * place in the source that knows them.
     */
    private static final String[] FORGOTTEN = {
        "windowMaximised", "windowX", "windowY", "windowWidth", "windowHeight",
        "workingDirectory", "examplesDirectory",
    };

    /** The empty-string key the first-run flag used to live under. */
    private static final String LEGACY_FIRST_RUN = "";
}
