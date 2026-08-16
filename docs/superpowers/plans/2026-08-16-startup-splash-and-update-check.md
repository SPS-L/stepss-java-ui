# Startup splash and startup update check — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** STEPSS shows a splash while it unpacks its toolchain, tells the user at startup when a newer release exists without interrupting them, and presents its licence agreement as something built rather than defaulted.

**Architecture:** Java's native `SplashScreen` rather than a Swing window, because `extractAll()` runs on the EDT and `SplashScreen.update()` pushes pixels outside Swing's repaint pipeline, so the status line keeps moving while the EDT is busy. The update check splits into a network half and a pure decision half so the decision is testable without a network. Both preference names are corrected and carry one-time migrations.

**Tech Stack:** Java 11 (`javac.source=11`), Swing with FlatLaf 3.7.2, Ant, jpackage. No test framework: headless `main()` harnesses under `tools/*.sh` are the substitute.

**Spec:** `docs/superpowers/specs/2026-08-16-startup-splash-and-update-check-design.md`

## Global Constraints

- Language level is **Java 11**. No `var`, no records, no switch expressions.
- This repository has **no unit-test framework and is not gaining one**. Tests are `public static void main` harnesses that print `ALL CHECKS PASSED` and exit non-zero on failure, following `src/my/stepss/compile/CompileHarness.java`.
- **Never edit `StepssUI.form`,** and never touch code between `//GEN-BEGIN` and `//GEN-END` markers. New UI is added programmatically, as `addThemeToggle()` does.
- **No em-dashes** in any comment, commit message, javadoc or user-visible string. Use a spaced hyphen.
- The preferences node literal is `my.stepss.StepssUI` **only after Task 2's migration exists**. Never rename it without one.
- Splash card is **460 x 250** at 1x, `splash-460.png`, with `splash-460@2x.png` at 920 x 500.
- Splash minimum on screen is **3000 ms**, enforced by delaying the frame, not by sleeping.
- Update-check timeouts stay at **5000 ms** connect and read, as today.
- Startup failures of the update check are logged at `Level.FINE`. The manual check keeps `Level.SEVERE` and its dialogs.
- Every task ends with `ant clean jar` green before its commit.

---

### Task 1: `UpdateCheck` — split the check into network and decision

**Files:**
- Create: `src/my/stepss/UpdateCheck.java`
- Create: `src/my/stepss/UpdateHarness.java`
- Create: `tools/update-harness.sh`

**Interfaces:**
- Consumes: `Version.fromReleaseUrl(String)`, `Version.key(String)`, `Version.compare(int[], int[])`, all existing and unchanged.
- Produces: `static String UpdateCheck.latestLocation(String url) throws IOException` and `static String UpdateCheck.noticeFor(String running, String location)`. `noticeFor` returns `null` for "say nothing" and an HTML banner string otherwise. Tasks 8 uses both.

- [ ] **Step 1: Write the failing harness**

Create `src/my/stepss/UpdateHarness.java`:

```java
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
```

Create `tools/update-harness.sh`, mode 755:

```bash
#!/usr/bin/env bash
# Runs the headless update-check decision tests against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/ssa-harness.sh, dist/lib is NOT on the classpath: UpdateCheck
# and Version depend on nothing but the JDK.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.stepss.UpdateHarness
```

- [ ] **Step 2: Run it to verify it fails**

```bash
chmod +x tools/update-harness.sh
ant compile
```

Expected: `ant compile` FAILS with `cannot find symbol: class UpdateCheck`.

- [ ] **Step 3: Write the implementation**

Create `src/my/stepss/UpdateCheck.java`:

```java
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
 * <p>Both the startup check and Help -&gt; Check for updates go through here,
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
```

- [ ] **Step 4: Run the harness to verify it passes**

```bash
ant compile
./tools/update-harness.sh
```

Expected: `ALL CHECKS PASSED`, exit 0.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/UpdateCheck.java src/my/stepss/UpdateHarness.java tools/update-harness.sh
git commit -m "Split the update check into a request and a decision

The decision is pure, so every way the answer can be 'say nothing' is now
pinned by a harness instead of needing github.com. Nothing calls it yet."
```

---

### Task 2: Preference migrations — `stepssFirstTime` and the node rename

**Files:**
- Create: `src/my/stepss/PreferenceMigration.java`
- Modify: `src/my/stepss/UpdateHarness.java` (add the migration checks)
- Modify: `src/my/stepss/StepssUI.java:5926-5937` (`preferences()`, `PREFERENCES_NODE`)
- Modify: `src/my/stepss/StepssUI.java:89-99` (first-run flag read and write)

**Interfaces:**
- Produces: `static Preferences PreferenceMigration.node(Preferences root, String legacyName, String currentName)` and `static void PreferenceMigration.firstRunKey(Preferences node)`. `StepssUI.preferences()` keeps its existing signature `static Preferences preferences()` so no caller changes. `StepssUI.FIRST_RUN` is the new `String` constant `"stepssFirstTime"`, used by Task 8.

- [ ] **Step 1: Write the failing harness checks**

Add to `src/my/stepss/UpdateHarness.java`. Add these imports at the top of the file:

```java
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
```

Add the two calls to `main`, before the `System.out.println`:

```java
        checkNodeMigration();
        checkFirstRunKeyMigration();
```

Add the methods:

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```

Expected: FAILS with `cannot find symbol: class PreferenceMigration`.

- [ ] **Step 3: Write the implementation**

Create `src/my/stepss/PreferenceMigration.java`:

```java
package my.stepss;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Moves saved settings when the names they live under are corrected.
 *
 * <p>A preferences node name is not a package reference, whatever it looks
 * like. It is a location in the user's preference store - the Windows
 * registry, {@code ~/.java/.userPrefs} on Linux - and it holds the theme, the
 * window geometry, the working directory and whether the licence has been
 * accepted. Renaming the literal alone does not move any of that, it abandons
 * it, and the user opens a window in the wrong theme, in the wrong place,
 * pointed at the wrong directory, being asked to accept a licence they
 * accepted years ago.
 *
 * <p>So the names get corrected and this carries the contents across. Both
 * migrations are idempotent and run on every launch: the second launch finds
 * the work already done and returns immediately.
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
            current.put(key, legacy.get(key, null));
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

    /** The empty-string key the first-run flag used to live under. */
    private static final String LEGACY_FIRST_RUN = "";
}
```

- [ ] **Step 4: Wire it into `preferences()`**

In `src/my/stepss/StepssUI.java`, replace the `PREFERENCES_NODE` constant and its javadoc (currently lines 5930-5937) and the `preferences()` method (5913-5928) with:

```java
    /**
     * Where the saved preferences live: the theme, the window, the working
     * directory and the first-run flag, in one node rather than several that
     * drift.
     *
     * <p>The name is a literal, not {@code getClass().getName()}, which is
     * what it used to be. That tied the node to the class name, so renaming
     * the package moved every user's settings out from under them. A
     * stored-preferences key is a compatibility surface and has no business
     * following a refactor on its own.
     *
     * <p>It did follow this one, deliberately: the node was
     * {@code my.ramses.RamsesUI} until the package became {@code my.stepss},
     * and {@link PreferenceMigration} is what makes the corrected name free
     * rather than costing every user their settings. Do not remove that call.
     * Installations that predate it still have the old node on disk.
     */
    static Preferences preferences() {
        if (node == null) {
            Preferences root = Preferences.userRoot();
            try {
                node = PreferenceMigration.node(root, LEGACY_NODE, PREFERENCES_NODE);
                PreferenceMigration.firstRunKey(node);
            } catch (java.util.prefs.BackingStoreException ex) {
                // A preference store that cannot be read is not a reason not to
                // start. The defaults apply for this session and the next
                // launch tries the migration again.
                Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                        "Could not migrate saved preferences", ex);
                node = root.node(PREFERENCES_NODE);
            }
        }
        return node;
    }

    private static Preferences node;

    /** The node this application uses, matching the package it belongs to. */
    private static final String PREFERENCES_NODE = "my.stepss.StepssUI";

    /** The node it used before v3.74.7's package rename, migrated on first read. */
    private static final String LEGACY_NODE = "my.ramses.RamsesUI";

    /** Whether the licence agreement still has to be shown. */
    static final String FIRST_RUN = "stepssFirstTime";
```

Then replace the first-run block at lines 89-99 with:

```java
        if (prefs.getBoolean(FIRST_RUN, true)) {
            // The licence itself is shown from main(), before this constructor
            // runs, so that it is the first thing on screen. This only records
            // that it has been dealt with.
            prefs.putBoolean(FIRST_RUN, false);
        }
```

- [ ] **Step 5: Run the harness to verify it passes**

```bash
ant compile
./tools/update-harness.sh
```

Expected: `ALL CHECKS PASSED`.

- [ ] **Step 6: Verify a real migration by hand**

```bash
ant jar
java -jar dist/stepss.jar   # close it once the window appears
```

Then confirm the node moved and nothing was lost:

```bash
grep -o 'my\.[a-zA-Z.]*' ~/.java/.userPrefs/*/prefs.xml 2>/dev/null | sort -u
find ~/.java/.userPrefs -name prefs.xml | xargs grep -l stepss
```

Expected: a node named `my.stepss.StepssUI` carrying whatever `my.ramses.RamsesUI` held, and no `my.ramses.RamsesUI` left. If you have no prior install, set one up first with `java -jar dist/stepss.jar` from the released v3.74.10 jar.

- [ ] **Step 7: Commit**

```bash
git add src/my/stepss/PreferenceMigration.java src/my/stepss/UpdateHarness.java src/my/stepss/StepssUI.java
git commit -m "Correct both preference names, and carry the settings across

The node followed the package to my.stepss.StepssUI and the first-run flag
stopped living under the empty string. Neither rename costs a user their
theme, window, working directory or licence acceptance, because
PreferenceMigration copies every key and only then removes the old node.

Both migrations are idempotent and pinned by harness cases per branch,
against a scratch node root rather than the real one."
```

---

### Task 3: `InlineBanner` gains an action button

**Files:**
- Modify: `src/my/stepss/InlineBanner.java`
- Modify: `src/my/stepss/UpdateHarness.java` (add banner checks)

**Interfaces:**
- Produces: `void InlineBanner.notice(String text, String actionLabel, Runnable action)`. `confirm(String)` and `warn(String)` keep their signatures and behaviour. Task 8 calls `notice`.

- [ ] **Step 1: Write the failing harness checks**

Add to `main` in `UpdateHarness`, before the print:

```java
        checkBannerActionButton();
```

Add the imports:

```java
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
```

Add the method:

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```

Expected: FAILS with `cannot find symbol: method notice(...)`.

- [ ] **Step 3: Write the implementation**

In `src/my/stepss/InlineBanner.java`, add the imports `java.awt.FlowLayout` and `java.awt.event.ActionListener`, rename nothing that exists, and make these changes.

Replace the field block and constructor body:

```java
    private final JLabel message = new JLabel();
    private final JButton actionButton = new JButton();
    private final Timer expiry;

    InlineBanner() {
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 8));
        setVisible(false);

        JButton dismiss = new JButton("Dismiss");
        dismiss.putClientProperty("JButton.buttonType", "borderless");
        dismiss.addActionListener(event -> clear());

        actionButton.putClientProperty("JButton.buttonType", "borderless");
        actionButton.setVisible(false);

        // One panel rather than two BorderLayout slots: the action belongs
        // beside Dismiss, in reading order, and a message with no action must
        // leave the line looking exactly as it does today.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(actionButton);
        buttons.add(dismiss);

        add(message, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);

        expiry = new Timer(12000, event -> clear());
        expiry.setRepeats(false);
    }
```

Replace `confirm` and `warn`, and add `notice`:

```java
    /** Something worked. Says so, then gets out of the way. */
    void confirm(String text) {
        show(text, UIManager.getColor("Component.accentColor"), true, null, null);
    }

    /** Something is worth knowing but nothing is broken. */
    void warn(String text) {
        show(text, UIManager.getColor("Component.warning.focusedBorderColor"),
                false, null, null);
    }

    /**
     * Something is worth knowing and there is one obvious thing to do about
     * it.
     *
     * <p>Accented and fading, like {@link #confirm} rather than
     * {@link #warn}, because an available update is neither a success nor a
     * problem, and a notice nobody acts on should not become furniture.
     *
     * @param actionLabel the button's text
     * @param action      what the button does, run on the EDT
     */
    void notice(String text, String actionLabel, Runnable action) {
        show(text, UIManager.getColor("Component.accentColor"), true, actionLabel, action);
    }
```

Replace the private `show` with:

```java
    private void show(final String text, final Color accent, final boolean fades,
                      final String actionLabel, final Runnable action) {
        onSwing(() -> {
            expiry.stop();
            message.setText(text);
            message.setFont(message.getFont().deriveFont(Font.PLAIN));
            // Unconditionally, before anything is added: every message decides
            // its own action, and a button left over from the previous one
            // would offer to do something unrelated to what is now on the line.
            for (ActionListener listener : actionButton.getActionListeners()) {
                actionButton.removeActionListener(listener);
            }
            if (actionLabel == null) {
                actionButton.setVisible(false);
            } else {
                actionButton.setText(actionLabel);
                actionButton.addActionListener(event -> {
                    clear();
                    action.run();
                });
                actionButton.setVisible(true);
            }
            Color edge = accent != null ? accent : UIManager.getColor("Label.foreground");
            // A rule down the leading edge rather than a filled panel: the
            // window already has a tab strip and a status bar competing for
            // the eye, and a block of colour across the top would outrank the
            // work it is commenting on.
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 1, 0, edge),
                    BorderFactory.createEmptyBorder(7, 9, 7, 8)));
            setVisible(true);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
            revalidate();
            repaint();
            if (fades) {
                expiry.start();
            }
        });
    }
```

Add the three accessors the harness needs, at the end of the class:

```java
    // Package-private windows into the action button, for UpdateHarness. The
    // button is otherwise private, and a test that reached it by walking
    // getComponents() would pass while asserting nothing about the contract.
    boolean actionButtonVisibleForTests() {
        return actionButton.isVisible();
    }

    String actionButtonTextForTests() {
        return actionButton.getText();
    }

    void actionButtonClickForTests() {
        actionButton.doClick();
    }
```

- [ ] **Step 4: Run the harness to verify it passes**

```bash
ant compile
./tools/update-harness.sh
```

Expected: `ALL CHECKS PASSED`.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/InlineBanner.java src/my/stepss/UpdateHarness.java
git commit -m "Give the banner a way to offer one action

A message that names something to do about it should carry the button, not
a menu path in its text. Every message clears the previous one's button
before it draws, which the harness pins: an action left over would offer to
open a release page beside an unrelated warning."
```

---

### Task 4: `Toolchain.extractAll` reports progress

**Files:**
- Modify: `src/my/stepss/platform/Toolchain.java:137-143`
- Modify: `src/my/stepss/compile/CompileHarness.java` (add the order check)

**Interfaces:**
- Produces: `public static List<String> Toolchain.extractionOrder(Platform)` and `public void extractAll(java.util.function.Consumer<String> progress) throws IOException`. The no-arg `extractAll()` stays, so `ToolchainDump:20` is untouched. Task 8 passes a consumer.

- [ ] **Step 1: Write the failing harness check**

Add to `main` in `src/my/stepss/compile/CompileHarness.java`, before the print:

```java
        checkExtractionOrder();
```

Add the imports `my.stepss.platform.Platform` and `my.stepss.platform.Toolchain`, then the method:

```java
    /**
     * What extractAll() visits, per platform, without extracting anything.
     *
     * <p>Pinned because the splash names each tool as it goes, so this list is
     * what a user reads during startup, and because gnuplot ships on Windows
     * only while uramses is deliberately lazy on all three.
     */
    private static void checkExtractionOrder() {
        expect("linux extraction order", "[ramses, helios, dyngraph, codegen]",
                Toolchain.extractionOrder(Platform.LINUX_X86_64).toString());
        expect("macos extraction order", "[ramses, helios, dyngraph, codegen]",
                Toolchain.extractionOrder(Platform.MACOS_ARM64).toString());
        expect("windows extraction order carries gnuplot",
                "[ramses, helios, dyngraph, codegen, gnuplot]",
                Toolchain.extractionOrder(Platform.WINDOWS_X86_64).toString());
        expect("uramses stays lazy on every platform", "false",
                String.valueOf(Toolchain.extractionOrder(Platform.LINUX_X86_64)
                        .contains("uramses")));
    }
```

> If the printed order differs from the assertion, the assertion is what is
> wrong: correct the expected strings to whatever `SPECS` declares, and keep
> the check. The point is that the order is pinned, not that it is this one.

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```

Expected: FAILS with `cannot find symbol: method extractionOrder`.

- [ ] **Step 3: Write the implementation**

In `src/my/stepss/platform/Toolchain.java`, replace `extractAll` (lines 136-143) with:

```java
    /**
     * The ids {@link #extractAll} visits on this platform, in order.
     *
     * <p>Separated from the extraction so it can be asserted without unpacking
     * 34MB. The splash reads the same list as it goes, so what a user sees
     * during startup and what is pinned by CompileHarness are one thing.
     */
    public static java.util.List<String> extractionOrder(Platform platform) {
        java.util.List<String> ids = new java.util.ArrayList<String>();
        for (ToolSpec spec : SPECS) {
            if (spec.availableOn(platform) && !LAZY.contains(spec.id())) {
                ids.add(spec.id());
            }
        }
        return ids;
    }

    /** Extracts every tool available on this platform, except the lazy set. */
    public void extractAll() throws IOException {
        extractAll(null);
    }

    /**
     * Extracts every tool available on this platform, except the lazy set,
     * naming each one before it starts.
     *
     * <p>The listener is called on the calling thread, which at startup is the
     * EDT. That is deliberate and is why the splash it drives is the native
     * one: {@code SplashScreen.update()} paints outside Swing's repaint
     * pipeline, so it works from a thread that is too busy to process its own
     * paint events.
     *
     * @param progress called with each tool's id, or null to report nothing
     */
    public void extractAll(java.util.function.Consumer<String> progress) throws IOException {
        for (String id : extractionOrder(platform)) {
            if (progress != null) {
                progress.accept(id);
            }
            resolved.put(id, ToolExtractor.extract(byId(id), platform, dir));
        }
    }
```

- [ ] **Step 4: Run the harnesses to verify**

```bash
ant compile
./tools/compile-harness.sh
./tools/update-harness.sh
ant clean jar
```

Expected: both harnesses `ALL CHECKS PASSED`, and `ant clean jar` BUILD SUCCESSFUL, which is what proves the extraction still works end to end.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/platform/Toolchain.java src/my/stepss/compile/CompileHarness.java
git commit -m "Let extractAll say what it is extracting

The order is now a pure method, so it is pinned per platform without
unpacking anything, and the listener overload lets the splash name each
tool. The no-arg form is unchanged, so ToolchainDump is untouched."
```

---

### Task 5: Splash artwork and the `Branding` accessor

**Files:**
- Create: `tools/MakeSplash.java`
- Create: `src/my/stepss/splash-460.png`, `src/my/stepss/splash-460@2x.png`
- Modify: `src/my/stepss/Branding.java`

**Interfaces:**
- Produces: `static Image Branding.lockupImage(boolean dark, int width)` where width is 380, 760 or 1140, returning null when absent. `Branding.requiredResources()` grows by two entries. Tasks 6 and 7 use `lockupImage`.

- [ ] **Step 1: Write the generator**

Create `tools/MakeSplash.java`. It is a single-file source program, run with `java tools/MakeSplash.java`, not compiled into the jar.

```java
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Composites the splash card from the light lockup. Run once, and again
 * whenever the lockup changes:
 *
 *   java tools/MakeSplash.java
 *
 * Writes src/my/stepss/splash-460.png and splash-460@2x.png. The card is
 * OPAQUE and square-cornered on purpose: a rounded card needs per-pixel alpha,
 * and a splash window without translucency support draws the corners black.
 *
 * Only the light card is generated. A dark launch repaints the whole thing at
 * runtime, so shipping a second file would be a second thing to keep in step.
 */
public final class MakeSplash {

    private static final int W = 460;
    private static final int H = 250;

    public static void main(String[] args) throws Exception {
        write(1, "src/my/stepss/logo-light-380.png", "src/my/stepss/splash-460.png");
        write(2, "src/my/stepss/logo-light-760.png", "src/my/stepss/splash-460@2x.png");
        System.out.println("splash artwork written");
    }

    private static void write(int scale, String lockupPath, String out) throws Exception {
        BufferedImage lockup = ImageIO.read(new File(lockupPath));
        BufferedImage card = new BufferedImage(W * scale, H * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = card.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W * scale, H * scale);
        g.setColor(new Color(0xD8DEE4));
        g.drawRect(0, 0, W * scale - 1, H * scale - 1);
        // Centred horizontally, 28px from the top at 1x. The runtime overpaint
        // uses the same numbers, so the dark card lands the lockup in exactly
        // the same place as this one.
        g.drawImage(lockup, (W * scale - lockup.getWidth()) / 2, 28 * scale, null);
        g.dispose();
        ImageIO.write(card, "png", new File(out));
    }
}
```

- [ ] **Step 2: Run it and check the output**

```bash
java tools/MakeSplash.java
python3 -c "
import struct
for f in ('src/my/stepss/splash-460.png','src/my/stepss/splash-460@2x.png'):
    d=open(f,'rb').read(33); print(f, struct.unpack('>II', d[16:24]))
"
```

Expected: `(460, 250)` and `(920, 500)`.

- [ ] **Step 3: Add the accessor and register the assets**

In `src/my/stepss/Branding.java`, add after the `logo` method:

```java
    /**
     * The lockup as a plain image at one of its rasterised widths, for callers
     * that paint rather than hand an Icon to Swing.
     *
     * <p>{@link #logo} returns a multi-resolution Icon, which is right for a
     * JLabel and wrong for the splash: {@code SplashScreen.createGraphics()}
     * is a fixed surface at a known scale, so the caller picks its width.
     *
     * @param width 380, 760 or 1140
     * @return the image, or null when that rendering is not in the jar
     */
    static Image lockupImage(boolean dark, int width) {
        return read("logo-" + variant(dark) + "-" + width + ".png");
    }
```

In `requiredResources()`, after the loop, add:

```java
        // Outside the loop: one light card serves both themes, and it ships in
        // two densities rather than three.
        names.add(SPLASH);
        names.add(SPLASH_2X);
```

and add the constants next to `LOCKUP_WIDTH`:

```java
    /** The splash card, composited from the light lockup by tools/MakeSplash.java. */
    static final String SPLASH = "splash-460.png";

    /** Its 2x rendering, which the JDK selects by this naming convention alone. */
    static final String SPLASH_2X = "splash-460@2x.png";
```

Extend the class javadoc, after the Inkscape recipe block:

```java
 * <h2>The splash card is generated, not drawn</h2>
 *
 * <p>{@code splash-460.png} is the light lockup composited onto a plain card by
 * {@code tools/MakeSplash.java}. Regenerate it from the repository root with
 * {@code java tools/MakeSplash.java} whenever the lockup changes; it is not
 * built by Ant, because it changes about as often as the artwork does and a
 * build step for it would run on every compile for nothing.
```

- [ ] **Step 4: Verify ChromeCheck now guards them**

```bash
ant clean jar
git stash push src/my/stepss/splash-460.png
ant jar
```

Expected: the build FAILS in `ChromeCheck` naming `splash-460.png`. Then:

```bash
git stash pop
ant clean jar
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add tools/MakeSplash.java src/my/stepss/splash-460.png src/my/stepss/splash-460@2x.png src/my/stepss/Branding.java
git commit -m "Add the splash card, generated from the light lockup

Opaque and square-cornered deliberately: a rounded card needs per-pixel
alpha and a splash window without translucency draws the corners black.
Only the light card ships, because a dark launch repaints the whole thing
anyway and a second file would be a second thing to keep in step.

ChromeCheck guards both densities, so a card dropped from the jar fails the
build rather than the launch."
```

---

### Task 6: `Splash`

**Files:**
- Create: `src/my/stepss/Splash.java`

**Interfaces:**
- Consumes: `Branding.lockupImage(boolean, int)` from Task 5.
- Produces: `static Splash Splash.open(boolean dark, String version)` returning null when there is no splash, `void status(String toolId)`, `void close()`. Task 8 calls all three.

- [ ] **Step 1: Write the implementation**

There is no harness step here: every method needs a real `SplashScreen`, which only exists in a JVM launched with one, and it is verified by Task 10's acceptance run instead. Create `src/my/stepss/Splash.java`:

```java
package my.stepss;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.geom.Rectangle2D;

/**
 * The startup card, drawn on the window the JVM puts up before this code runs.
 *
 * <p>Java's own splash rather than a JWindow, and the reason is the whole
 * design: {@code initRamses()} calls {@code Toolchain.extractAll()} on the EDT
 * on every launch, so a Swing window would be unable to repaint for exactly
 * the stretch the splash exists to cover. Its status line would freeze on its
 * first message. {@link SplashScreen#update()} paints a window the JVM owns,
 * outside Swing's repaint pipeline, so it works when called from a busy EDT.
 *
 * <p>Everything here degrades to nothing. {@link SplashScreen#getSplashScreen}
 * returns null unless the JVM was launched with a splash, which is the case
 * when the main class is run from an IDE, so {@link #open} returns null and
 * every caller treats that as "no splash" rather than as a failure.
 */
final class Splash {

    /** The card's logical size, matching tools/MakeSplash.java. */
    private static final int W = 460;
    private static final int H = 250;

    private final SplashScreen screen;
    private final boolean dark;
    private final String version;
    private volatile String status = "";

    private Splash(SplashScreen screen, boolean dark, String version) {
        this.screen = screen;
        this.dark = dark;
        this.version = version;
    }

    /**
     * Paints the card and returns a handle on it, or null when this JVM has no
     * splash to paint.
     *
     * @param dark    whether the dark theme is in force
     * @param version the running version, shown bottom left
     */
    static Splash open(boolean dark, String version) {
        SplashScreen screen = SplashScreen.getSplashScreen();
        if (screen == null) {
            return null;
        }
        Splash splash = new Splash(screen, dark, version);
        splash.status("");
        return splash;
    }

    /**
     * Reports what startup is doing now.
     *
     * <p>Takes a tool id, as {@code Toolchain.extractAll} hands it out, and
     * does the wording here so Toolchain stays free of presentation.
     *
     * @param toolId a tool id such as "ramses", or "" for no status at all
     */
    void status(String toolId) {
        this.status = toolId.isEmpty() ? "Starting STEPSS" : "Extracting " + toolId;
        paint();
    }

    /**
     * Dismisses the card.
     *
     * <p>Rarely needed: the JVM closes the splash by itself as soon as the
     * first window is displayed, which on the normal path is the main frame.
     * This exists for the first run, where the splash has to be gone before
     * the licence agreement rather than behind it.
     */
    void close() {
        try {
            screen.close();
        } catch (IllegalStateException alreadyClosed) {
            // Closed by the JVM when a window appeared. Nothing to do, and
            // nothing worth logging: this is the ordinary end of its life.
        }
    }

    private void paint() {
        Graphics2D g;
        try {
            g = screen.createGraphics();
        } catch (IllegalStateException alreadyClosed) {
            // The frame became visible while extraction was still reporting.
            // The card is gone; the remaining messages have nowhere to go.
            return;
        }
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color background = dark ? new Color(0x1E2227) : Color.WHITE;
            Color ink = dark ? new Color(0xE6E9EE) : new Color(0x24292F);
            Color quiet = dark ? new Color(0x9AA4B2) : new Color(0x6B7280);
            Color rule = dark ? new Color(0x343A42) : new Color(0xD8DEE4);

            // The whole card every time, not just the status line: the base
            // image is the light card, so a dark launch has to cover it, and
            // repainting one strip would leave the previous status underneath.
            g.setColor(background);
            g.fillRect(0, 0, W, H);
            g.setColor(rule);
            g.drawRect(0, 0, W - 1, H - 1);

            Image lockup = Branding.lockupImage(dark, Branding.LOCKUP_WIDTH);
            if (lockup != null) {
                // The same 28px offset tools/MakeSplash.java uses, so the dark
                // card and the light base image place the lockup identically.
                g.drawImage(lockup, (W - Branding.LOCKUP_WIDTH) / 2, 28, null);
            }

            Font base = g.getFont();
            g.setFont(base.deriveFont(Font.PLAIN, 12f));
            g.setColor(ink);
            centred(g, "Creators: Petros Aristidou and Thierry Van Cutsem", 192);

            g.setColor(rule);
            g.drawLine(24, 210, W - 24, 210);

            g.setFont(base.deriveFont(Font.PLAIN, 11f));
            g.setColor(quiet);
            g.drawString(version, 24, 232);
            int statusWidth = g.getFontMetrics().stringWidth(status);
            g.drawString(status, W - 24 - statusWidth, 232);

            screen.update();
        } catch (IllegalStateException alreadyClosed) {
            // update() races the JVM's own close for the same reason
            // createGraphics() does. Same answer: the card is gone.
        } finally {
            g.dispose();
        }
    }

    private static void centred(Graphics2D g, String text, int y) {
        Rectangle2D bounds = g.getFontMetrics().getStringBounds(text, g);
        g.drawString(text, (int) ((W - bounds.getWidth()) / 2), y);
    }
}
```

- [ ] **Step 2: Verify it compiles and the jar still builds**

```bash
ant clean jar
```

Expected: BUILD SUCCESSFUL. Nothing calls `Splash` yet, so there is nothing to see on screen until Task 8.

- [ ] **Step 3: Commit**

```bash
git add src/my/stepss/Splash.java
git commit -m "Draw the startup card on the JVM's own splash window

Not a JWindow, because extractAll runs on the EDT and a Swing window cannot
repaint while it does. SplashScreen.update paints outside Swing's repaint
pipeline, which is the one property this needs.

Every path degrades to no splash: getSplashScreen returns null whenever the
JVM was not launched with one, and both createGraphics and update can lose
a race with the JVM closing the card when the first window appears."
```

---

### Task 7: `LicenseDialog`

**Files:**
- Create: `src/my/stepss/LicenseDialog.java`
- Modify: `src/my/stepss/UpdateHarness.java` (add the HTML transform checks)
- Modify: `src/my/stepss/StepssUI.java:772-815` (delete `licenseAgreement`)
- Modify: `src/my/stepss/StepssUI.java:2869`-adjacent menu wiring if a Help item calls it (check with `git grep -n licenseAgreement`)

**Interfaces:**
- Consumes: `Branding.logo(boolean)`, `Branding.windowIcons(boolean)`.
- Produces: `static boolean LicenseDialog.accept(boolean dark)` returning true when accepted, and `static String LicenseDialog.toHtml(String licence)`. Task 8 calls `accept`.

- [ ] **Step 1: Write the failing harness checks**

Add to `main` in `UpdateHarness`, before the print:

```java
        checkLicenceHtml();
```

Add the method:

```java
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
```

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```

Expected: FAILS with `cannot find symbol: class LicenseDialog`.

- [ ] **Step 3: Write the implementation**

Create `src/my/stepss/LicenseDialog.java`:

```java
package my.stepss;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The licence a first run has to accept before anything else happens.
 *
 * <p>It is the engine's licence, not the interface's: STEPSS' two user
 * interfaces are Apache 2.0, while RAMSES is the property of the University of
 * Liege and free for non-commercial use only. That is why the body is
 * {@code ramsesLicense.txt} and why the subtitle names the component rather
 * than the application.
 *
 * <p>The terms themselves are deliberately not summarised on screen. The bus
 * and core caps are stated in the licence text and in stepss-docs
 * {@code getting-started/license.md}, which owns those facts; a friendly
 * one-liner here would be a third copy, free to drift from both.
 */
final class LicenseDialog {

    private LicenseDialog() {
    }

    /**
     * Shows the agreement and blocks until it is answered.
     *
     * <p>Called from {@code main} before the frame exists, so the dialog has
     * no parent and centres itself on screen. It is the first window a new
     * user ever sees from STEPSS, which is why it carries the lockup and the
     * application's own icons rather than a stock dialog's.
     *
     * @return true if accepted
     */
    static boolean accept(final boolean dark) {
        final boolean[] accepted = {false};
        Runnable show = () -> accepted[0] = build(dark);
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(show);
            } catch (Exception ex) {
                // A licence that cannot be presented cannot be accepted, and
                // continuing would mean running the engine under terms nobody
                // agreed to.
                Logger.getLogger(LicenseDialog.class.getName())
                        .log(Level.SEVERE, "Could not show the licence agreement", ex);
                return false;
            }
        }
        return accepted[0];
    }

    private static boolean build(boolean dark) {
        final boolean[] accepted = {false};
        final JDialog dialog = new JDialog((java.awt.Frame) null, "License Agreement", true);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setIconImages(Branding.windowIcons(dark));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JLabel lockup = new JLabel(Branding.logo(dark));
        lockup.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel title = new JLabel("License Agreement");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 4f));
        title.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("RAMSES simulation engine, University of Liege");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitle.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        header.add(lockup);
        header.add(Box.createVerticalStrut(14));
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        JEditorPane body = new JEditorPane("text/html", toHtml(read()));
        body.setEditable(false);
        body.setCaretPosition(0);
        // Horizontal NEVER, not ALWAYS: the licence is soft-wrapped prose, so
        // the bar this used to show could never scroll anything.
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Separator.foreground")));

        JPanel middle = new JPanel(new BorderLayout());
        middle.setBorder(BorderFactory.createEmptyBorder(0, 24, 8, 24));
        middle.add(scroll, BorderLayout.CENTER);

        JLabel footer = new JLabel("The other components' licences are under Help > About.");
        footer.setForeground(UIManager.getColor("Label.disabledForeground"));

        JButton accept = new JButton("Accept");
        JButton decline = new JButton("Decline");
        accept.addActionListener(event -> {
            accepted[0] = true;
            dialog.dispose();
        });
        decline.addActionListener(event -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(decline);
        buttons.add(accept);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 24, 18, 24));
        bottom.add(footer, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);

        dialog.getContentPane().add(header, BorderLayout.NORTH);
        dialog.getContentPane().add(middle, BorderLayout.CENTER);
        dialog.getContentPane().add(bottom, BorderLayout.SOUTH);

        // Escape declines. Closing the window declines. Neither may be taken
        // for acceptance: the flag is only ever set by the Accept button.
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JPanel.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().setDefaultButton(accept);

        dialog.setSize(660, 580);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return accepted[0];
    }

    /**
     * The bundled licence text, or a stand-in naming the fault.
     *
     * <p>The previous implementation wrapped the stream in a reader before
     * testing it for null, so a missing resource was a NullPointerException
     * during construction of the first window, and the null check that
     * followed was unreachable.
     */
    private static String read() {
        InputStream in = LicenseDialog.class.getResourceAsStream("ramsesLicense.txt");
        if (in == null) {
            Logger.getLogger(LicenseDialog.class.getName())
                    .log(Level.SEVERE, "ramsesLicense.txt is missing from the jar");
            return "The licence text is missing from this build. Do not accept it;"
                    + " report this instead.";
        }
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }
            } finally {
                reader.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(LicenseDialog.class.getName())
                    .log(Level.SEVERE, "Could not read ramsesLicense.txt", ex);
        }
        return text.toString();
    }

    /**
     * The licence as HTML: first line a title, numbered sections headings,
     * everything else paragraphs.
     *
     * <p>Deliberately forgiving. Text that matches none of the rules comes out
     * as paragraphs, so a reworded licence renders no worse than the plain
     * text area it replaces, and no future edit to the licence can leave a
     * user staring at nothing.
     */
    static String toHtml(String licence) {
        StringBuilder html = new StringBuilder(
                "<html><body style='font-family:sans-serif; margin:14px;'>");
        String[] lines = licence.split("\n", -1);
        boolean titleDone = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String escaped = escape(trimmed);
            if (!titleDone) {
                html.append("<h2>").append(escaped).append("</h2>");
                titleDone = true;
            } else if (trimmed.matches("^\\d+\\..*") && trimmed.length() < 60) {
                html.append("<h3>").append(escaped).append("</h3>");
            } else {
                html.append("<p>").append(escaped).append("</p>");
            }
        }
        return html.append("</body></html>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 4: Delete the old method**

```bash
git grep -n licenseAgreement -- src
```

Delete `licenseAgreement` from `StepssUI.java` (lines 772-815) and every reference the grep found other than the constructor block already replaced in Task 2. If a Help menu item calls it, point that item at `LicenseDialog.accept(preferences().getBoolean(DARK_THEME_KEY, false))` and ignore the return, since a re-read is not a re-acceptance.

- [ ] **Step 5: Run the harness and see the dialog**

```bash
ant compile
./tools/update-harness.sh
```

Expected: `ALL CHECKS PASSED`.

Then look at it, which the harness cannot do for you:

```bash
cat > /tmp/ShowLicence.java <<'EOF'
public class ShowLicence {
    public static void main(String[] a) throws Exception {
        java.lang.reflect.Method m = Class.forName("my.stepss.LicenseDialog")
                .getDeclaredMethod("accept", boolean.class);
        m.setAccessible(true);
        System.out.println("accepted=" + m.invoke(null, Boolean.valueOf(a.length > 0)));
    }
}
EOF
javac -cp build/classes -d /tmp /tmp/ShowLicence.java
java -cp build/classes:/tmp ShowLicence          # light
java -cp build/classes:/tmp ShowLicence dark     # dark
```

Check each of: the lockup is at the top, "1. Definitions" and its siblings are bold headings, there is no horizontal scrollbar, Escape prints `accepted=false`, and Accept prints `accepted=true`.

- [ ] **Step 6: Commit**

```bash
git add src/my/stepss/LicenseDialog.java src/my/stepss/StepssUI.java src/my/stepss/UpdateHarness.java
git commit -m "Rebuild the licence agreement as a dialog worth showing first

It is about to become the first window a new user sees, so it carries the
lockup, the application's icons, and a subtitle naming whose licence it is.
The terms stay unsummarised on purpose: stepss-docs owns those facts and a
friendly one-liner here would be a third copy free to drift.

Four faults go with the old method. The stream was wrapped in a reader
before being tested for null, so a missing resource was an NPE and the null
check after it was dead. The scroll pane forced a horizontal bar that could
never scroll soft-wrapped prose. initialValue was a string that was not one
of the options. And a HeadlessException was caught, discarded, and returned
from, so a headless launch continued as though the licence were accepted."
```

---

### Task 8: The startup sequence

**Files:**
- Modify: `src/my/stepss/StepssUI.java:5879-5900` (`main`)
- Modify: `src/my/stepss/StepssUI.java:143-149` (`initRamses` call site, for the splash handle)
- Modify: `src/my/stepss/StepssUI.java:6496` (`extractAll`)
- Modify: `src/my/stepss/StepssUI.java:3613-3680` (manual check reads `UpdateCheck`)
- Modify: `src/my/stepss/StepssUI.java:710-735` (`addThemeToggle` neighbourhood, for the new toggle)

**Interfaces:**
- Consumes: everything produced by Tasks 1 through 7.
- Produces: nothing new for later tasks. Task 9 wires the build; Task 10 verifies.

- [ ] **Step 1: Rewrite `main`**

Replace lines 5879-5900 with:

```java
    public static void main(String args[]) {
        final long started = System.nanoTime();
        final SplashScreen jvmSplash = SplashScreen.getSplashScreen();

        boolean dark = preferences().getBoolean(DARK_THEME_KEY, false);
        installTheme(dark);
        useThemedTitleBar(dark);

        // The licence comes before everything, including the card the JVM has
        // already put on screen. A first run must not show branding, or make a
        // network call, before its user has agreed to the engine's terms.
        if (preferences().getBoolean(FIRST_RUN, true)) {
            if (jvmSplash != null) {
                jvmSplash.close();
            }
            if (!LicenseDialog.accept(dark)) {
                System.exit(1);
                return;
            }
        }

        // Null on a first run, because the card above is closed and Java's
        // splash cannot be reopened. That launch simply has no splash, which
        // is the trade for the licence being genuinely first.
        final Splash splash = Splash.open(dark, getVersionFromResource());

        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                StepssUI frame = new StepssUI(splash);
                // Showing the first window is what dismisses the splash, so
                // the three second floor is enforced by delaying the window,
                // never by sleeping: the EDT has extraction to get on with.
                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                int remaining = (int) Math.max(1L, SPLASH_MINIMUM_MS - elapsedMs);
                javax.swing.Timer reveal = new javax.swing.Timer(remaining, event -> {
                    frame.setVisible(true);
                    // Maximised is still the default, and stays what happens
                    // on a first run; a window sized and placed by hand now
                    // comes back that way instead of being flattened to the
                    // screen again.
                    if (startMaximised()) {
                        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    }
                });
                reveal.setRepeats(false);
                reveal.start();
            }
        });
    }

    /** How long the splash stays up even when there is nothing left to wait for. */
    private static final long SPLASH_MINIMUM_MS = 3000L;
```

Add `import java.awt.SplashScreen;` to the imports.

`getVersion()` is an instance method, so add a static form beside it and have the instance one delegate:

```java
    private String getVersion() {
        return getVersionFromResource();
    }

    /** The bundled version, readable before any instance exists. */
    static String getVersionFromResource() {
        InputStream in = StepssUI.class.getResourceAsStream("version.txt");
        if (in == null) {
            return "0.0";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            String line = reader.readLine();
            return (line == null || line.trim().isEmpty()) ? "0.0" : line.trim();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return "0.0";
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }
```

(Move the existing body of `getVersion()` into `getVersionFromResource()` verbatim rather than retyping it, and keep whatever the current `catch` block does.)

- [ ] **Step 2: Thread the splash through the constructor**

Change the constructor signature and keep a field:

```java
    private final Splash splash;

    public StepssUI() {
        this(null);
    }

    public StepssUI(Splash splash) {
        this.splash = splash;
        initComponents();
        ...
```

Leave the rest of the constructor as it is, except the first-run block already replaced in Task 2, and add the update check as its last statement, after the `initRamses()` block:

```java
        checkForUpdatesAtStartup();
```

In `initRamses`, replace line 6496:

```java
            toolchain.extractAll(id -> {
                if (splash != null) {
                    splash.status(id);
                }
            });
```

- [ ] **Step 3: Add the startup check and the toggle**

Add beside `checkUpdateButtonActionPerformed`:

```java
    /**
     * Asks github.com whether there is a newer STEPSS, and says so on the
     * banner if there is.
     *
     * <p>Silent about everything else. A user who did not ask for this does
     * not want an error about it: no network, a proxy, a redirect that names
     * no release, all end here with nothing said and a FINE log line. The
     * manual check under Help keeps its dialogs and its SEVERE logging,
     * because there somebody asked.
     *
     * <p>A daemon thread, so a slow DNS lookup can never hold the JVM open
     * after the user has quit, and never the EDT, so a machine with no network
     * does not pay the connect timeout before its window appears.
     */
    private void checkForUpdatesAtStartup() {
        if (!preferences().getBoolean(CHECK_UPDATES_KEY, true)) {
            return;
        }
        Thread check = new Thread(() -> {
            final String location;
            try {
                location = UpdateCheck.latestLocation(RELEASES_LATEST_URL);
            } catch (IOException ex) {
                Logger.getLogger(StepssUI.class.getName())
                        .log(Level.FINE, "Startup update check could not reach github.com", ex);
                return;
            }
            final String notice = UpdateCheck.noticeFor(this_version, location);
            if (notice == null) {
                return;
            }
            final String published = Version.fromReleaseUrl(location);
            SwingUtilities.invokeLater(() -> {
                banner.notice(notice, "Open release page",
                        () -> BareBonesBrowserLaunch.openURL(location));
                // The banner clears on the user's next click, so the About box
                // keeps the fact after it has gone.
                versionLabel.setText("<html><b>Version:</b> " + this_version
                        + " (latest version available: " + published + ")</html>");
            });
        }, "stepss-update-check");
        check.setDaemon(true);
        check.start();
    }

    /** Whether to ask github.com for a newer release at startup. */
    static final String CHECK_UPDATES_KEY = "checkUpdatesAtStartup";
```

Add the toggle. In `applyModernChrome()`, after `addThemeToggle();`, add `addUpdateToggle();`, and add the method beside `addThemeToggle`:

```java
    /**
     * Lets a user stop the application contacting a server when it starts.
     *
     * <p>Added programmatically, next to the theme toggle and for the same
     * reason: reopening StepssUI.form in the designer cannot regenerate away
     * something the form never knew about.
     */
    private void addUpdateToggle() {
        final JCheckBoxMenuItem check = new JCheckBoxMenuItem("Check for updates at startup");
        check.setSelected(preferences().getBoolean(CHECK_UPDATES_KEY, true));
        check.addActionListener(event -> {
            preferences().putBoolean(CHECK_UPDATES_KEY, check.isSelected());
            try {
                // Preferences writes back on its own schedule, so without this
                // a choice made and then followed by a kill or a crash is lost,
                // and the next launch silently contradicts the menu.
                preferences().flush();
            } catch (java.util.prefs.BackingStoreException ex) {
                Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                        "Update check choice could not be saved", ex);
            }
        });
        toolsMenu.add(check);
    }
```

- [ ] **Step 4: Point the manual check at `UpdateCheck`**

In `checkUpdateButtonActionPerformed`, replace the connection block (lines 3625-3639) with:

```java
            String location = UpdateCheck.latestLocation(RELEASES_LATEST_URL);
            String current_version = Version.fromReleaseUrl(location);
```

Leave everything below it exactly as it is: the three branches, their wording, their dialogs and the `IOException` handler are unchanged. Delete the now-unused `HttpURLConnection` and `URL` imports only if `git grep -n 'HttpURLConnection\|new URL(' src/my/stepss/StepssUI.java` shows no other use.

- [ ] **Step 5: Build and run it**

```bash
ant clean jar
./tools/update-harness.sh
./tools/compile-harness.sh
java -jar dist/stepss.jar
```

Expected: the splash appears immediately with the lockup and creators, its status line names each tool as it extracts, the window appears no sooner than three seconds in, and Tools carries "Check for updates at startup", checked.

- [ ] **Step 6: Commit**

```bash
git add src/my/stepss/StepssUI.java
git commit -m "Splash on startup, and say when a newer release exists

main now owns the order: licence first on a first run, then the splash,
then the window no sooner than three seconds in. The floor is enforced by
delaying the window rather than by sleeping, because showing the first
window is what dismisses the splash and the EDT has extraction to get on
with.

The update check runs on a daemon thread and says nothing it does not know:
no network, no proxy answer, no release tag all end silently at FINE. The
manual check keeps its dialogs, and both now go through UpdateCheck so they
cannot drift apart."
```

---

### Task 9: Make the splash appear from the installed bundles

**Files:**
- Modify: `manifest.mf`
- Modify: `build.xml` (`-post-jar` copy, and the `bundle` target's jpackage arguments)

**Interfaces:** none produced; this is packaging.

- [ ] **Step 1: Add the manifest attribute**

`manifest.mf` becomes:

```
Manifest-Version: 1.0
Multi-Release: true
SplashScreen-Image: my/stepss/splash-460.png
X-COMMENT: Main-Class will be added automatically by build
```

- [ ] **Step 2: Verify the jar route works and prove the bundle route does not**

```bash
ant clean jar
java -jar dist/stepss.jar
```

Expected: splash appears. Then:

```bash
ant bundle "-Dbundle.type=--type app-image"
grep -r splash bundle/STEPSS/lib/app/STEPSS.cfg || echo "NO SPLASH IN CFG"
./bundle/STEPSS/bin/STEPSS
```

Expected: `NO SPLASH IN CFG` and **no splash** from the bundle, because `build.xml:393` passes `--main-class`, so the launcher never reads the jar manifest. This step exists to see that failure before fixing it; do not skip it.

- [ ] **Step 3: Copy the artwork beside the jar**

In `build.xml`, in the `-post-jar` target, after the `<jar>` task, add:

```xml
  <!-- Beside the jar as well as inside it. jpackage is given an explicit
       --main-class, so its launchers start the class on a classpath and never
       read the jar manifest: SplashScreen-Image reaches `java -jar` and
       nothing else. The bundles get -splash: pointed at these copies, which
       --input dist carries into the application directory. -->
  <copy todir="dist">
   <fileset dir="src/my/stepss">
    <include name="splash-460.png"/>
    <include name="splash-460@2x.png"/>
   </fileset>
  </copy>
```

- [ ] **Step 4: Point the bundles at it**

In the `bundle` target's `<exec executable="jpackage">`, add after the `--main-class` line:

```xml
   <!-- $APPDIR is jpackage's own token, substituted identically on Windows,
        macOS and Linux, so this one line is the splash on all three. The @2x
        file beside it is picked up by the JDK's naming convention with no
        further argument. -->
   <arg line="--java-options -splash:$APPDIR/splash-460.png"/>
```

Ant leaves a `$` that is not followed by `{` alone, so the token reaches jpackage intact. Step 5 confirms that rather than trusting it.

- [ ] **Step 5: Verify the bundle route**

```bash
ant clean jar
ant bundle "-Dbundle.type=--type app-image"
grep splash bundle/STEPSS/lib/app/STEPSS.cfg
ls bundle/STEPSS/lib/app/splash-460*.png
./bundle/STEPSS/bin/STEPSS
```

Expected: the cfg contains `java-options=-splash:$APPDIR/splash-460.png` with the token **unexpanded in the file** (the launcher expands it), both PNGs are in `lib/app/`, and the bundle shows the splash. If the cfg shows an empty or mangled option, change the argument to `-splash:$$APPDIR/splash-460.png` and repeat, since `$$` is how Ant escapes a literal `$`.

- [ ] **Step 6: Commit**

```bash
git add manifest.mf build.xml
git commit -m "Make the splash appear from the installed bundles too

The manifest attribute only reaches 'java -jar'. jpackage is passed an
explicit --main-class, so every generated launcher starts the class on a
classpath and never opens the jar manifest: left at that, the jar would
splash and the .deb, .msi and .dmg would not, on all three platforms.

Bundles get -splash:\$APPDIR/splash-460.png instead, with the artwork copied
into dist/ so --input carries it. \$APPDIR and -splash: behave the same
everywhere, so verifying the Linux bundle verifies the mechanism."
```

---

### Task 10: Acceptance run, and the documentation that has to follow

**Files:**
- Create: `docs/superpowers/plans/splash-and-update-acceptance-results.md`
- Modify: `README.md` (the Releases section, for the new toggle)
- Modify: the umbrella `../CLAUDE.md` (the preferences-node paragraph)

- [ ] **Step 1: Run the five scenarios and record what happened**

Run each, and write what you saw into
`docs/superpowers/plans/splash-and-update-acceptance-results.md` following the
shape of `docs/superpowers/plans/compile-acceptance-results.md`.

1. **First run.** The preference store has no CLI and its on-disk directory
   names are mangled, so reset the flag through the API. Write
   `/tmp/ResetFirstRun.java`:

   ```java
   import java.util.prefs.Preferences;

   public class ResetFirstRun {
       public static void main(String[] args) throws Exception {
           Preferences node = Preferences.userRoot().node("my.stepss.StepssUI");
           node.remove("stepssFirstTime");
           node.flush();
           System.out.println("stepssFirstTime cleared; next launch is a first run");
       }
   }
   ```

   Then:

   ```bash
   java /tmp/ResetFirstRun.java
   java -jar dist/stepss.jar
   ```

   Expect the licence **first**, with no splash before it or behind it, and the
   window after acceptance. Decline once first, in a separate launch, and
   confirm the application exits without opening a window.
2. **Normal run, current version.** Expect a splash for at least three seconds,
   its status line naming ramses, helios, dyngraph and codegen in turn, and no
   banner.
3. **Normal run, older version.** Rebuild with an older `version.txt`:
   ```bash
   cp src/my/stepss/version.txt /tmp/version.bak
   echo "3.0" > src/my/stepss/version.txt
   ant clean jar && java -jar dist/stepss.jar
   cp /tmp/version.bak src/my/stepss/version.txt
   ```
   Expect the banner naming the real latest release, an "Open release page"
   button that opens it, and Help > About showing
   "(latest version available: ...)".
4. **No network.** Disable networking, launch. Expect no banner, no dialog, no
   `SEVERE` in the console, and a window that appears no later than in
   scenario 2.
5. **Toggle off.** Untick Tools > Check for updates at startup, quit, relaunch.
   Expect no request at all; confirm with `ss -tnp | grep java` during startup
   or by watching that nothing appears.

- [ ] **Step 2: Update the README**

In the Releases section of `README.md`, after the paragraph describing the
automatic release, add:

```markdown
STEPSS checks for a newer release when it starts and says so on the banner
across the top of the window, with a link to the release page. It never blocks
startup on that check and says nothing when it cannot reach github.com. Turn it
off under **Tools > Check for updates at startup**.
```

- [ ] **Step 3: Correct the umbrella CLAUDE.md**

The paragraph in `../CLAUDE.md` that reads "One string is deliberately stale:
the preferences node is still `my.ramses.RamsesUI`, pinned as a literal ..." is
now false. Replace it with:

```markdown
The preferences node followed the package to `my.stepss.StepssUI`, and
`PreferenceMigration` is what made that safe: it copies every key out of the
old `my.ramses.RamsesUI` node and only then removes it. Do not delete that
migration. Installations older than v3.74.11 still have the old node on disk,
and without the copy a rename abandons every user's theme, window, working
directory and licence acceptance rather than moving them.
```

- [ ] **Step 4: Commit, and bump the umbrella pointer**

```bash
git add docs/superpowers/plans/splash-and-update-acceptance-results.md README.md
git commit -m "Record the splash and update-check acceptance run"
```

Then, separately, in the umbrella repository:

```bash
cd ..
git add CLAUDE.md
git commit -m "The preferences node was renamed, with a migration"
git add stepss-java-ui
git commit -m "Bump java-ui: startup splash, startup update check, rebuilt licence dialog"
```

---

## Self-Review

**Spec coverage.** Every numbered section of the spec maps to a task: §1 to
Task 1, §2 to Task 3, §3 to Task 6, §4 to Task 4, §5 to Task 8, §6 to Task 8,
§7 and §7a to Task 2, §8 to Task 5, §8a to Task 9, §9 to Task 7. The spec's
testing section maps to the harness steps in Tasks 1 to 4 and 7 and to Task 10;
its "follow-up outside this repo" maps to Task 10 Step 3.

**Placeholder scan.** No TBD, no "handle edge cases", no "similar to Task N".
Every code step carries the code. One step was rewritten during this review:
Task 10 Step 1 originally told the engineer to hunt for a mangled directory
name under `~/.java/.userPrefs`, which is not a procedure; it now resets the
flag through the `Preferences` API with a program that is written out in full.

**Type consistency.** Checked across tasks: `UpdateCheck.noticeFor(String,
String)` and `latestLocation(String)` as used in Task 8 match Task 1.
`PreferenceMigration.node(Preferences, String, String)` and `firstRunKey(
Preferences)` as used in Task 2's `preferences()` match their definitions.
`StepssUI.FIRST_RUN` is defined in Task 2 and read in Tasks 2 and 8.
`InlineBanner.notice(String, String, Runnable)` in Task 3 matches the call in
Task 8. `Toolchain.extractAll(Consumer<String>)` and `extractionOrder(Platform)`
in Task 4 match Task 8's lambda and Task 4's own harness. `Branding.lockupImage(
boolean, int)`, `SPLASH` and `SPLASH_2X` in Task 5 match Task 6's use.
`Splash.open(boolean, String)`, `status(String)` and `close()` in Task 6 match
Tasks 8's three call sites. `LicenseDialog.accept(boolean)` and
`toHtml(String)` in Task 7 match Task 8 and the harness.

**One ordering note for the executor.** Task 2 introduces `StepssUI.FIRST_RUN`
and Task 7 deletes `licenseAgreement`, but Task 2's constructor edit already
stops calling it. Between Task 2 and Task 7 the method is dead code that still
compiles, which is intended: it keeps each task's build green on its own.
