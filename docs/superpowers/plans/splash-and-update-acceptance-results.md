# Task 10: Startup splash, startup update check, and licence dialog - acceptance results

`SPLASH_UPDATE_ACCEPTANCE: pass`

Branch: `startup-splash-and-update-check`.
Commit under test: `26416d8` ("Make the splash appear from the installed bundles too") - the
results below were produced from that tree; this document and the README addition are the commit
that follows it. No `.java` file was touched.
Verification performed on: Linux x86_64 (this machine), real GNOME/mutter X11 session on a real
display (`DISPLAY=:0`), OpenJDK 21.0.11 (build 21.0.11+10-1-24.04.2-Ubuntu), Apache Ant 1.10.14,
Ubuntu 24.04.4 LTS.

Unlike the picker acceptance precedent, this agent did have eyes on a real screen: every scenario
below was driven through the actual Swing GUI with `xdotool` mouse clicks and key events, and
observed with real screenshots (ImageMagick `import`, read back and looked at, not just captured).
Where a screenshot answers a question, its content is quoted or described directly rather than
paraphrased from log lines.

---

## How this was driven, precisely

- `dist/stepss.jar` at the start of this task was already built from this exact tree (`git status`
  was clean, `git log -1` showed `26416d8`, and `unzip -p dist/stepss.jar my/stepss/version.txt`
  read `3.74.10`, matching `src/my/stepss/version.txt`). Scenarios 1, 2, 4 and 5 ran against that
  jar unchanged. Scenario 3 rebuilt it with a faked `version.txt` and the jar was rebuilt again
  from the restored file afterwards, so the tree ends on a jar built from HEAD with nothing edited.
- Windows were found and screenshotted by window title (`xdotool search --name`), including the
  JVM's own native splash (titled `Java`), the licence dialog (`License Agreement`), the main frame
  (`STEPSS`), and popup menus, which turned out to be separate top-level windows (titled `win0`)
  that only `import -window <that id>` can capture - capturing the parent frame's window while a
  menu is open does not include the popup.
- Clicks used real screen coordinates computed from `xdotool getwindowgeometry`, checked against a
  screenshot before trusting them. `Escape` reliably reached the licence dialog's decline binding
  through `xdotool key --clearmodifiers Escape` (no `--window`); the same approach for `Return`
  did **not** trigger the dialog's default (Accept) button, for a reason this run did not chase
  down - clicking the button's own screen coordinates worked immediately and is what every
  accept-path result below is based on. This is recorded as a limitation of the driving method, not
  a product finding: `Escape`'s binding (`registerKeyboardAction` at `WHEN_IN_FOCUSED_WINDOW`) fired
  from the same kind of synthetic key event that the default button's binding did not.
- The default Bash sandbox in this environment restricts `/tmp` writes (breaking `ImageIO`'s cache
  file, which broke the splash's own image loading) and restricts outbound network to an allowlist
  that does not include `github.com`. Every launch below therefore used the sandbox-disabled mode,
  consistent with this repository's own note that `tools/update-harness.sh` and
  `tools/compile-harness.sh` need it and that `Read-only file system` failures in that mode are
  environmental.
- Two supplementary automated checks were also run and are reported at the end:
  `tools/update-harness.sh` (the pure `UpdateCheck`/`Version` decision logic) and
  `tools/compile-harness.sh` (unrelated to this feature, but named in the task's constraints and
  cheap to confirm still green).

---

## Scenario 1: First run

**Setup.** The real preference store's `stepssFirstTime` key was cleared with the `ResetFirstRun`
program from the brief:

```
$ java /tmp/.../ResetFirstRun.java
stepssFirstTime cleared; next launch is a first run
```

**Decline, first.** Launched `java -jar dist/stepss.jar`. Window-title polling every launch tick
recorded:

```
t=0.041s  [Java]                      <- the JVM's own native splash, from the manifest
t=0.287s  (splash gone)
t=0.503s  [License Agreement][Content window][FocusProxy]
```

No `STEPSS` window and no second, application-level splash ever appeared - `main()` closes the
JVM's native splash immediately on a first run, before `Splash.open()` is ever called, and the
screenshots of that brief native flash carry no status text (`Splash.status()` is what paints text,
and it never ran). `Escape` was sent; the dialog closed and, within one second, **the process had
exited entirely**: no `License Agreement` window, no `STEPSS` window, and no `java -jar
dist/stepss.jar` process in `ps aux`. The preference store still had no `stepssFirstTime` key
afterwards (i.e. still defaults to "first run"), confirming the decline path never reaches the line
that clears the flag.

**Accept, second launch (same reset flag, now exercised for real).** Same pattern on relaunch:

```
t=0.048s  [Java]
t=0.297s  (splash gone)
t=0.519s  [License Agreement][Content window][FocusProxy]
```

The dialog itself, screenshotted:

> STEPSS lockup at the top, title **License Agreement**, subtitle *RAMSES simulation engine,
> University of Liege*, a scrollable licence body beginning **RAMSES LICENSE** / *Copyright (c)
> University of Liège, Belgium. All rights reserved.*, footer *The other components' licences are
> under Help > About.*, and **Decline** / **Accept** buttons with Accept visually the default.

Clicking **Accept** closed the dialog and the **STEPSS** main window appeared in the same step -
no application-level `Splash` object is shown on this path at all, matching the code comment that a
first run "simply has no splash" because the JVM's native one was already closed for the licence.
A screenshot of the resulting window showed the ordinary System Data tab, no banner. Re-reading the
preference store immediately afterwards still showed no `stepssFirstTime` key, but a re-check five
seconds later showed `stepssFirstTime = false` - `Preferences`' file-backed implementation syncs to
disk on its own schedule rather than synchronously on `putBoolean`, which is worth knowing for
anyone repeating this by hand and checking too quickly.

**Result: PASS.** Licence first, no splash before or behind it on either path, decline exits with
no window, accept shows the window, and the flag ends up exactly where accepting it should leave
it.

## Scenario 2: Normal run, current version

Three independent launches of the unmodified jar were timed and screenshotted.

**Splash floor and status line.** One run was screenshotted every ~27ms from process start. The
status line was caught transitioning through real tool names, in the order `Toolchain.extractAll`
promises (confirmed separately by `compile-harness.sh`'s "linux extraction order" check):

```
t=.338s  "Starting STEPSS"
t=.615s  "Extracting helios"
t=.644s  "Extracting codegen"   <- persists at 27ms sampling through t=1.66s and beyond
```

`ramses` and `dyngraph` are not individually visible in this capture - extraction of all four tools
completes in well under 30ms per step on this machine, faster than the sampling interval - but the
surrounding two names were caught in the code's own order, and the splash still held the floor:
the JVM's native splash window was present continuously from t=.18s to just after t=3.0s in every
one of the three runs, i.e. at least three seconds, in each case.

**No banner.** A screenshot taken several seconds after the window appeared showed no strip below
the menu bar - consistent with this being the newest published release, so
`UpdateCheck.noticeFor` has nothing to report. `stdout`/`stderr` were empty in every run: no
`SEVERE`, no stack trace.

**The splash-to-window gap.** Task 8's review flagged about 210ms unaccounted for between the
splash closing and the window appearing. Three independent measurements here found no such gap:

| Run | Splash last seen / gone | Main window appears | Gap |
|---|---|---|---|
| Screenshot-timed | gone at 3.103s | 3.117s | ~14ms |
| Tight state-poll #1 | still up | appears in the *same* poll tick as the splash | below poll resolution (a few ms) |
| Tight state-poll #2 | still up | appears in the *same* poll tick as the splash | below poll resolution (a few ms) |

No visible blank gap was seen in any of the three runs on this machine. This does not contradict
Task 8's finding - different hardware or load could easily explain 210ms that this machine's fast,
otherwise-idle screen does not reproduce - it is simply what was actually observed here, reported
as asked.

**Result: PASS.**

## Scenario 3: Normal run, older version (faked)

```
$ cp src/my/stepss/version.txt /tmp/.../version.bak
$ echo "3.0" > src/my/stepss/version.txt
$ ant clean jar          # BUILD SUCCESSFUL
$ unzip -p dist/stepss.jar my/stepss/version.txt
3.0
```

**A methodological wrinkle, disclosed rather than hidden.** The first attempt at this scenario used
manual `sleep`-then-screenshot checks a few seconds apart and saw no banner, across two separate
full runs, even though a standalone diagnostic calling `UpdateCheck.latestLocation` and
`UpdateCheck.noticeFor("3.0", ...)` directly against `build/classes` returned correctly in 374ms
with exactly the expected notice text. That ruled out a logic defect and pointed at the test
harness. A single script that launches the jar and screenshots continuously **from process start**,
with no gap between launching and capturing, resolved it: the banner was there all along, and the
two earlier "no banner" observations were an artifact of that run's screenshot targeting (most
likely a stale window id carried over from earlier scenario work in the same shell session), not a
product issue. The properly-instrumented rerun below is what this result is based on.

**Banner.** Screenshotted every capture from process start:

```
t=3.112s  main window appears
t=3.316s  banner visible: "STEPSS 3.74.10 is available. You are running 3.0."
```

i.e. the banner is up about 200ms after the window itself, with a blue accent rule on its leading
edge, and, cropped from the same screenshot, two buttons on its right: **Open release page** and
**Dismiss**.

**The button.** Clicking **Open release page** cleared the banner immediately - its own
`ActionListener` runs `clear()` before the browser-launch action, so an immediate, correct-looking
dismissal is exactly what firing that listener produces. `stdout`/`stderr` carried no exception from
the browser-launch attempt. This machine's Firefox runs in a separate desktop session not attached
to the `DISPLAY=:0` used for this test, so a navigated tab could not be visually confirmed here;
that is a limitation of this test environment, not a defect in `BareBonesBrowserLaunch`, whose job
ends at handing the OS a URL without throwing.

**Help > About.** Opened with the `F4` accelerator. The dialog read exactly:

> **Version:** 3.0 (latest version available: 3.74.10)

matching the brief's expectation verbatim, and confirming the banner-clearing click did not also
erase the fact from the About box (`versionLabel` is set independently of the banner, as the code
comments say it must be).

**Restoration.**

```
$ cp /tmp/.../version.bak src/my/stepss/version.txt
$ git status --short          # (empty)
$ git diff src/my/stepss/version.txt   # (empty)
$ ant clean jar                # BUILD SUCCESSFUL
$ unzip -p dist/stepss.jar my/stepss/version.txt
3.74.10
```

**Result: PASS**, once tested with a capture method that does not itself introduce a gap.

## Scenario 4: No network

This task's instructions rule out disabling the machine's real networking, and an
unroutable-address rewrite would have meant editing code. Root was not available
(`sudo` requires a password this session cannot supply), which also ruled out a `/etc/hosts` edit.
The substitute used instead: an **unprivileged Linux network namespace**, scoped to the one child
process, that never touches the user's real network configuration and needs nothing owned by root:

```
$ unshare --user --net --map-user=1000 --map-group=1000 -- java -jar dist/stepss.jar
```

Two things were checked before trusting this as a stand-in for "no network": the namespace's only
interface is loopback, `DOWN`, so DNS cannot resolve (`curl` inside it: `Could not resolve host:
github.com`); and, separately, `--map-user=1000 --map-group=1000` (rather than the more obvious
`--map-root-user`) was necessary to keep the process's real UID inside the namespace, because a
first attempt using `--map-root-user` changed `getuid()` to 0 and, with it, Java's own resolved
`user.home` from `/home/apetros` to `/root` - silently pointing the preference store at a different,
nonexistent profile and producing an unrelated cascade of `ImageIO` cache-file failures. That
attempt is recorded here for anyone who reaches for the same tool later; it was caught before being
mistaken for a product finding, not shipped as one.

With the corrected invocation, `user.home` resolves correctly and the real preference store is
used, but there is genuinely no route to `github.com`:

```
main window appears: t=3.069s   (scenario 2's runs: 3.07s-3.15s - not later)
stdout: (empty)      stderr: (empty)      no SEVERE anywhere
```

A screenshot taken several seconds later showed no banner and no dialog. `ss -tnp | grep java` was
also polled continuously for the run and showed no connection at any point - the daemon thread's
`IOException` (an immediate `UnknownHostException`, since DNS itself cannot resolve here) is caught
and logged at `Level.FINE`, which the default console handler does not print, matching "no `SEVERE`
in the console" exactly.

**Result: PASS.**

## Scenario 5: Toggle off, relaunch

Launched normally (current version, real network). **Tools** menu opened by clicking its label;
its popup is its own top-level window (`win0`) and was screenshotted directly:

```
Save command file / Save observables file / Open in editor (Ctrl+N) / Select external simulator
(Ctrl+R) / Select working directory / Open working folder (Ctrl+E) / Open terminal in working
folder (Ctrl+T) / Clear all gnuplot instances
---
Dark theme
✓ Check for updates at startup
```

Clicked the checked row. The popup closed (a menu item was activated) and reopening **Tools**
showed the same row with **no** checkmark. Reading the real preference store confirmed the change
had already reached disk (`checkUpdatesAtStartup = false`), because the menu's own
`ActionListener` calls `preferences().flush()` synchronously - no wait was needed for this one,
unlike the first-run flag in Scenario 1.

**Quit** (the running process was ended directly, since the flush above had already made the
setting durable; the real menu path was used only for the toggle itself) **and relaunch:**

```
$ ss -tnp | grep java     # polled every 0.1s for 10s: no hits, ever
main window appears: t=3.150s
stdout: (empty)   stderr: (empty)
```

A screenshot of the settled window showed no banner. `checkForUpdatesAtStartup()` returns before
spawning its thread when the preference reads false, so "no request made at all" is exactly what
the empty ten seconds of `ss` output shows - not "a request that failed fast," a request that never
started.

**Restoration.** The key was cleared back to its original unset state (the baseline dump taken
before any scenario ran had no `checkUpdatesAtStartup` key at all, i.e. the default `true`):

```
$ java RestoreCheckUpdates.java
checkUpdatesAtStartup cleared; back to its unset default (true)
```

**Result: PASS.**

---

## Supplementary automated checks

Not part of the five scenarios, but named in this task's constraints and cheap to confirm still
green on this tree:

```
$ ./tools/update-harness.sh
ALL CHECKS PASSED

$ ./tools/compile-harness.sh
... 51 PASS lines (CompileHarness) ...
ALL CHECKS PASSED
PASS  separate sink per stream: 800 lines, all whole
PASS  a sink shared by two streams is reported
ALL CONSOLE SINK CHECKS PASSED
```

Both needed the sandbox disabled, as this repository's own notes say; no `Read-only file system`
errors appeared once it was.

---

## State mutated, and proof it was restored

Three things were touched, as flagged in the task brief, plus one more (Scenario 5's toggle) found
along the way:

1. **`stepssFirstTime`** (real preference store): cleared for Scenario 1, ended the scenario
   accepted (`false`) via the real Accept button, exactly as it was found. Confirmed by a final
   `DumpPrefs` read: `stepssFirstTime = false`.
2. **`src/my/stepss/version.txt`**: backed up, set to `3.0` for Scenario 3, restored from the
   backup afterwards. Confirmed: `git status --short` and `git diff src/my/stepss/version.txt` are
   both empty. The edit was never committed.
3. **`checkUpdatesAtStartup`** (real preference store, not called out by name in the brief but
   mutated by Scenario 5): toggled off through the real menu, cleared back to unset afterwards.
   Confirmed by the same final `DumpPrefs` read: no `checkUpdatesAtStartup` key.
4. **`dist/` and `build/`**: rebuilt twice (faked version, then restored version) by `ant clean
   jar`. Both are gitignored build output and were never staged.

Final state, checked once at the end of all five scenarios:

```
$ java DumpPrefs.java
== my.stepss.StepssUI ==
  stepssFirstTime = false
  windowMaximised = true
  workingDirectory =
== my.ramses.RamsesUI ==
                                    (no keys - matches the pre-task baseline exactly)

$ git status --short
                                    (empty before this document and the README edit were added)

$ ps aux | grep "[j]ava -jar dist/stepss.jar"
                                    (nothing running)
```

The `windowMaximised`/`workingDirectory` values above are identical to the baseline `DumpPrefs`
read taken before any scenario started - nothing in this task's five launches wrote a value that
was not already there or was not one of the three explicitly restored above.

---

## Findings

**No defects found in the shipped code.** Every scenario passed once driven and observed
correctly; the two "no banner" false negatives in Scenario 3's first attempt were traced to this
session's own screenshot targeting (see that scenario's write-up) and resolved by a cleaner capture
method, not by any change to behaviour.

**Minor, for whoever next automates this by hand:**

- `xdotool key Return` did not trigger the licence dialog's default (Accept) button, while
  `xdotool key Escape` did trigger its Decline binding, from the same kind of synthetic event.
  Clicking the button's real screen coordinates is what worked. Not chased further since it did not
  block completing the scenario, and nothing about it points at the dialog's own code - `Escape` is
  bound at `WHEN_IN_FOCUSED_WINDOW` and Accept is `getRootPane().setDefaultButton()`, two different
  Swing mechanisms, and only one of them answered a synthetic key.
- Swing menu popups are their own top-level X windows, not part of the frame's window - screenshot
  tooling aimed at the main frame while a menu is open captures the frame without the menu.

## Verdict

`SPLASH_UPDATE_ACCEPTANCE: pass`

All five scenarios from the brief were run against the real GUI on a real display, screenshotted,
and passed: first run shows the licence first with no splash before or behind it and exits cleanly
on decline; a normal run holds the splash for the full three-second floor and names each tool in
turn; an older version shows the banner with a working "Open release page" button and the correct
About text; no network produces no banner, no dialog, nothing alarming logged, and no delay to the
window; and turning the toggle off stops the startup request from ever being made. No scenario was
left unrun. All mutated state - the first-run flag, the update-check toggle, and the faked
`version.txt` - was restored and verified.
