package my.stepss;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Headless checks for what survives a launch and what does not.
 *
 * <p>This repository has no unit-test framework and is not gaining one, so
 * this main() is the substitute, in the shape {@code SsaHarness} and
 * {@code ConsoleSinkCheck} already use.
 *
 * <p>What it is here to catch. A launch keeps the licence flag and the three
 * settings with a tick box behind them, and nothing else: no window geometry,
 * no working directory, no examples root. That rule is enforced by a list of
 * key names in {@link PreferenceMigration}, and a list is exactly the kind of
 * thing that goes stale when a key is added back for a good local reason. It
 * is also enforced against the legacy node, which is copied key-by-key and can
 * therefore reintroduce every one of them years after the fact.
 *
 * <p>Nothing here goes near the real STEPSS node. Every check runs under a
 * scratch root of its own, which is removed on the way out whether the checks
 * passed or not.
 */
public final class PreferencesCheck {

    private static int failures;

    /**
     * Where the scratch nodes live, well away from {@code my.stepss.StepssUI}.
     * One flat name, so removing it on the way out leaves nothing behind.
     */
    private static final String SCRATCH_ROOT = "stepss-preferences-check";

    /** What a launch is allowed to remember. */
    private static final String[] KEPT = {
        "stepssFirstTime", "darkTheme", "checkUpdatesAtStartup",
        "showExamplesAtStartup",
    };

    /** What it is not. These are the names as they appear on disk. */
    private static final String[] FORGOTTEN = {
        "windowMaximised", "windowX", "windowY", "windowWidth", "windowHeight",
        "workingDirectory", "examplesDirectory",
    };

    private PreferencesCheck() {
    }

    public static void main(String[] args) throws BackingStoreException {
        Preferences root = Preferences.userRoot().node(SCRATCH_ROOT);
        try {
            checkSessionKeysGo(root);
            checkTheTickedSettingsStay(root);
            checkForgettingTwiceIsHarmless(root);
            checkALegacyNodeCannotReintroduceASession(root);
        } finally {
            root.removeNode();
            Preferences.userRoot().flush();
        }
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * The seven keys a previous build wrote are removed, not merely left
     * unread. An installation that has them keeps them until something takes
     * them out, and a working directory still in the node is a working
     * directory the next build to read it comes back on.
     */
    private static void checkSessionKeysGo(Preferences root) throws BackingStoreException {
        Preferences node = fresh(root, "session");
        for (String key : FORGOTTEN) {
            node.put(key, "1");
        }
        PreferenceMigration.forgetSession(node);
        for (String key : FORGOTTEN) {
            expect(key + " is forgotten", null, node.get(key, null));
        }
        expect("nothing at all is left", 0, node.keys().length);
    }

    /** And the four that are the user's own choices are not touched. */
    private static void checkTheTickedSettingsStay(Preferences root)
            throws BackingStoreException {
        Preferences node = fresh(root, "kept");
        for (String key : KEPT) {
            node.put(key, "true");
        }
        for (String key : FORGOTTEN) {
            node.put(key, "1");
        }
        PreferenceMigration.forgetSession(node);
        for (String key : KEPT) {
            expect(key + " survives the launch", "true", node.get(key, null));
        }
        expect("and only those four are left", KEPT.length, node.keys().length);
    }

    /**
     * It runs on every launch, so the ordinary case is a node that has nothing
     * to forget. That has to be silent rather than an error.
     */
    private static void checkForgettingTwiceIsHarmless(Preferences root)
            throws BackingStoreException {
        Preferences node = fresh(root, "twice");
        node.put("darkTheme", "true");
        node.put("workingDirectory", "/somewhere");
        PreferenceMigration.forgetSession(node);
        PreferenceMigration.forgetSession(node);
        expect("a second pass leaves the theme alone", "true", node.get("darkTheme", null));
        expect("and the directory still gone", null, node.get("workingDirectory", null));
    }

    /**
     * The one that cannot be caught by reading the code. {@code node()} copies
     * <em>every</em> key out of the retired {@code my.ramses.RamsesUI} node,
     * deliberately, so an installation that has not been launched since v3.74.7
     * carries a working directory and a window across on its first run under
     * this build. Forgetting has to happen after that copy, not instead of it.
     */
    private static void checkALegacyNodeCannotReintroduceASession(Preferences root)
            throws BackingStoreException {
        Preferences legacy = fresh(root, "my.ramses.RamsesUI");
        legacy.put("", "false");
        legacy.put("darkTheme", "true");
        legacy.put("workingDirectory", "/home/someone/kundur-two-area");
        legacy.put("windowMaximised", "false");
        legacy.put("examplesDirectory", "/home/someone/examples");
        legacy.flush();

        Preferences current = PreferenceMigration.node(root,
                "my.ramses.RamsesUI", "my.stepss.StepssUI");
        PreferenceMigration.firstRunKey(current);
        PreferenceMigration.forgetSession(current);

        Set<String> left = new HashSet<String>(Arrays.asList(current.keys()));
        expect("the licence acceptance came across", "false",
                current.get("stepssFirstTime", null));
        expect("so did the theme", "true", current.get("darkTheme", null));
        for (String key : FORGOTTEN) {
            expect("a legacy " + key + " does not come back", false, left.contains(key));
        }
        expect("nothing arrived but the licence flag and the theme", 2, left.size());
        try {
            current.removeNode();
        } catch (BackingStoreException ignored) {
            // The scratch root goes in main's finally either way.
        }
    }

    /** An empty node of the given name, whatever a previous run left. */
    private static Preferences fresh(Preferences root, String name)
            throws BackingStoreException {
        if (root.nodeExists(name)) {
            root.node(name).removeNode();
            root.flush();
        }
        return root.node(name);
    }

    private static void expect(String what, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) {
            System.out.println("PASS  " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what + ": wanted <" + want + "> got <" + got + ">");
        }
    }
}
