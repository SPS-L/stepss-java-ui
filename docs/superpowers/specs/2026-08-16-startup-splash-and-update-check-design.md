# Startup splash and startup update check

**Date:** 2026-08-16
**Status:** Design approved, ready for planning
**Scope:** stepss-java-ui only. Closes
[#4](https://github.com/SPS-L/stepss-java-ui/issues/4), and adds the splash and
the licence-dialog rework asked for alongside it.

Three changes in one spec because they are one sequence: what happens between
the launcher starting the JVM and the main window appearing. Splitting them
would mean writing the startup order down three times and merging it once.

## Problem

Two startup gaps, which turn out to share one constraint.

**Nobody learns that a new release exists.** The update check runs only when
asked for, under Help -> Check for updates (F3). An install that is three
releases behind stays that way until its user happens to open a menu they have
no reason to open.

**Startup looks like nothing is happening.** `initRamses()` calls
`toolchain.extractAll()` (`StepssUI.java:6496`), and `toolDir` is a fresh temp
directory on every launch (`StepssUI.java:6490`), so *every* start unpacks
ramses, helios, dyngraph, codegen and gnuplot out of the jar before the window
appears. There is nothing on screen while that happens.

The shared constraint is that this unpacking runs **on the EDT**. That rules out
the obvious splash: a Swing `JWindow` cannot repaint while the thread that would
repaint it is unpacking tens of megabytes. Its status line would freeze on its
first message, and on some window managers the card would paint blank.

## Goals

- A launch on an older version shows a non-blocking notice naming the new
  version, one click from the download.
- A launch on the current version says nothing at all.
- A launch with no network says nothing, logs nothing alarming, and is not
  delayed by so much as a millisecond.
- A splash covers the extraction, reports what it is doing, and stays up for at
  least three seconds.
- On a first run the licence agreement is the first thing on screen, ahead of
  the splash and ahead of any network call, and it looks like the front door of
  an application rather than a text area in a stock dialog.

## Non-goals

- Downloading or installing an update. The notice links to the release page;
  the user installs it themselves.
- A progress *bar*. `extractAll` knows which tool it is on, not how far through
  the bytes it is, and a bar that cannot be accurate is worse than a line of
  text that is.
- Reworking `initRamses()`'s threading. The whole point of the chosen splash
  mechanism is that the critical startup path is left alone.
- Touching the bundled licence *texts*. Only the dialog that presents
  `ramsesLicense.txt` changes. The `.txt` files each name their own component as
  "the Software" and a sweep across them has gone wrong here before.

## Decisions taken

| Decision | Choice | Why |
|---|---|---|
| Update notice surface | Banner, with an action button added to `InlineBanner` | The interface has moved away from modal dialogs; a menu path named in text is a worse link than a link |
| Banner lifetime | Unchanged, and the About version line records it | The lifetime rule exists to stop messages outliving their situation; the About line is the durable trace |
| Opt-out | Tools menu checkbox, default on | An application contacting a server at launch should be refusable |
| Splash mechanism | Java's native `SplashScreen` | The only one that keeps painting while the EDT is busy |
| Splash timing | `max(3s, window ready)` | The update check must never delay startup |
| First run | Splash closed immediately, licence, no splash that launch | The native splash cannot be reopened once closed, and a `JWindow` after acceptance would freeze during extraction |
| Licence dialog | Rebuilt as an owned `JDialog` with the lockup | Moving it to `main` makes it the first window a new user sees; four latent faults in it are fixed on the way through |
| Preference names | `stepssFirstTime`, node `my.stepss.StepssUI`, both migrated | The names should match the package they belong to; the migration is what makes that free rather than costing every user their settings |

## Design

### 1. `UpdateCheck` (new, `src/my/stepss/UpdateCheck.java`)

Splits the existing check into a network half and a decision half, so the
decision is testable without a network.

```java
static String latestLocation() throws IOException
static String noticeFor(String running, String location)
```

`latestLocation` is the request as it stands today, moved out of
`checkUpdateButtonActionPerformed` unchanged: redirects off, 5s connect and read
timeouts, returning the `Location` header of `/releases/latest`.

`noticeFor` is pure and returns `null` for **say nothing**: a null location, a
location naming no release tag, either version unparseable, equal versions, or a
published version older than the running one. It returns the banner text only
when the published version is strictly newer. `null` rather than a boolean plus
an out-parameter, because every caller's question is "is there something to
say".

Comparison stays in `Version`, which is unchanged.

### 2. `InlineBanner` gains an action button

The EAST slot becomes a small panel holding an optional action button ahead of
Dismiss. One new method:

```java
void notice(String text, String actionLabel, Runnable action)
```

Accent colour and the 12 second fade, matching `confirm`, because an available
update is neither a success nor a problem, and a notice nobody acts on should
not become furniture. The private `show` takes the two extra parameters and
**hides the button for every other caller**, so no message can inherit the
previous one's button. `confirm(String)` and `warn(String)` keep their
signatures and behaviour.

### 3. `Splash` (new, `src/my/stepss/Splash.java`)

Wraps `java.awt.SplashScreen`.

```java
static Splash open(boolean dark, String version)   // null when there is no splash
void status(String text)
void close()
```

`open` paints the themed card: the lockup from `Branding`, the creators line,
the version, a hairline rule and an empty status line. `status` repaints the
last line and calls `SplashScreen.update()`.

The property that makes this design work: `SplashScreen.update()` pushes pixels
to a window the JVM owns, outside Swing's repaint pipeline. It therefore works
**when called from the EDT while the EDT is mid-`extractAll`**, which is exactly
when the status line needs to move. No watcher thread, no second window, no
change to how `initRamses` is threaded.

`SplashScreen.getSplashScreen()` returns null whenever the application was not
launched through the jar, which includes running the class from NetBeans.
`open` returns null there and every caller treats null as "no splash", so no
launch path depends on one existing.

### 4. `Toolchain.extractAll` reports progress

```java
public void extractAll() throws IOException                        // kept, delegates
public void extractAll(Consumer<String> progress) throws IOException
```

The listener is called with each tool's id (`ramses`, `helios`, `dyngraph`,
`codegen`, `gnuplot`) before that tool is extracted. `Splash` does the wording,
turning an id into "Extracting ramses", so `Toolchain` stays free of presentation
and the harness can assert on ids. Two callers
exist today (`StepssUI:6496` and `ToolchainDump:20`); the no-arg overload keeps
`ToolchainDump` untouched. This is the only public API change in the design, and
it is what buys a status line that names what is actually happening rather than
a fixed string that only looks like it does.

### 5. Startup sequence

`main` currently reads the theme, installs it and posts the frame's construction
to the EDT. It becomes, in order:

1. `long t0 = System.nanoTime()`, first statement.
2. `SplashScreen` handle, which may be null.
3. Read the theme preference and install it, as today.
4. **First run only** (see the key trap below): close the splash, run the
   licence agreement, record acceptance. Nothing else has happened yet, so the
   licence is genuinely first on screen and no network call has been made.
5. Otherwise: `Splash.open(dark, version)`, status "Starting STEPSS".
6. `invokeLater`: construct `StepssUI`, which extracts the toolchain and drives
   the splash's status line through the new listener.
7. Show the frame when `max(3s, construction complete)` has elapsed, using a
   `javax.swing.Timer` for the remainder rather than sleeping. Displaying the
   first window is what dismisses the native splash, so delaying the window is
   how the three second floor is enforced.
8. The update check starts when that frame is shown, and is never waited on.
   See section 6 for why it is not the end of the constructor.

`licenseAgreement` becomes `static`. It already passes `null` as its dialog
parent and touches no instance state (`StepssUI.java:797`), so this is a
modifier change and a move, not a rewrite.

**The first-run flag is renamed, and migrated.** It is read today as
`prefs.getBoolean(ramsesFirtsTime, true)` where `ramsesFirtsTime` is the **empty
string** (`StepssUI.java:89-90`), so the key on disk is `""`, under a
misspelled variable. It becomes `stepssFirstTime`.

A rename alone would make every existing installation look like a first run and
re-prompt for a licence its user accepted long ago, so it comes with a one-time
migration: if `stepssFirstTime` is absent and the legacy `""` key says the
application has run before, write `stepssFirstTime=false` and remove `""`. The
prompt is then never shown twice, and the legacy key does not linger.

### 7a. The preferences node is renamed too, and migrated wholesale

`PREFERENCES_NODE` is the literal `my.ramses.RamsesUI` (`StepssUI.java:5937`).
The package became `my.stepss` in v3.74.7 and the node name should follow it to
`my.stepss.StepssUI`.

This one needs care, and the existing comment at `StepssUI.java:5930-5937` says
why: the string is not a package reference, it is a **location in the user's
preference store** - the Windows registry, `~/.java/.userPrefs` on Linux,
`~/Library/Preferences` on macOS. Renaming it with nothing else would abandon
every existing user's theme, window geometry, working directory and licence
acceptance in a node nothing reads any more.

So the rename carries a migration, run once from `preferences()`'s first call:

1. If `my.stepss.StepssUI` already has keys, use it and do nothing else.
2. Otherwise, if `my.ramses.RamsesUI` exists, copy every key across, then
   `removeNode()` the old one and flush.
3. Otherwise this is a genuine first run and the new node starts empty.

Copying every key rather than an enumerated list matters: the node holds the
theme, five window keys, the working directory and the first-run flag today, and
an enumeration would silently drop whatever is added later.

**Follow-up outside this repo:** the umbrella `CLAUDE.md` currently states the
node is deliberately stale and must not be renamed. That paragraph is wrong once
this lands and has to be rewritten in the same pass, to say the node was renamed
*with* a migration and that the migration is the thing not to remove.

### 6. Startup update check

`checkForUpdatesAtStartup()` runs from the frame's first `windowOpened`, which
step 7 above is what delivers. It:

- Returns immediately when the new preference is off.
- Otherwise one **daemon** thread. Daemon so that a slow DNS lookup can never
  hold the JVM open after the user quits.
- On a non-null `noticeFor`, hops to the EDT to raise the banner with "Open
  release page" pointing at that exact release, and to append
  "(latest available: X)" to the About box's version label, which
  `checkUpdateButtonActionPerformed` already does at `StepssUI.java:3660`.
- Every failure returns without a word, logged at `Level.FINE`. The manual check
  keeps logging at `SEVERE` and keeps its dialogs: there, someone asked.

**Not the end of the constructor**, which is where this section first put it.
That was written before step 7 existed, and once the reveal timer landed the
constructor stopped being the end of startup: it now finishes up to three
seconds before the window is on screen. Two things broke, quietly.

`InlineBanner` holds one message. `initRamses()` raises "gnuplot was not found,
so real-time plotting is disabled" on it during that same constructor, so on a
machine with no gnuplot on `PATH` where an update is also available, the update
notice replaced that warning before either had ever been seen. The combination
could not arise before this design, because the check only ran when the user
asked for it under Help.

And the banner's twelve second expiry starts at `notice()`, not at first paint,
so a notice raised at the end of the constructor was actually on screen for
about nine seconds.

The frame therefore registers a `WindowAdapter` on itself and starts the check
from `windowOpened`, rather than `main` calling it after `frame.setVisible(true)`.
Both would fix the timing; only this one survives a `StepssUI` built by anything
other than this `main`, such as running the class from an IDE, and `windowOpened`
is delivered exactly once, so no path can start two checks. The daemon thread and
"nothing ever waits on it" are unchanged: startup is over by the time the check
begins.

### 7. Preferences

Two keys in the existing node, beside the theme and window state:

| Key | Default | Set by |
|---|---|---|
| `checkUpdatesAtStartup` | true | Tools menu checkbox |
| `stepssFirstTime` | true | Licence acceptance, migrated from the legacy `""` |

`addUpdateToggle()` follows `addThemeToggle()` exactly, including the
`flush()` and its `BackingStoreException` handling, and is added
programmatically so reopening the form in the designer cannot regenerate it
away.

### 8. Assets

Two files are committed beside the other marks:

| File | Size | Content |
|---|---|---|
| `splash-460.png` | 460 x 250 | Light card with `logo-light-380` composited, no text |
| `splash-460@2x.png` | 920 x 500 | The same from `logo-light-760` |

The base image carries the light card so the splash looks finished from the
instant the JVM paints it, before any of our code runs. A dark launch repaints
the whole card. Both files go into `Branding.requiredResources()` so
`ChromeCheck` fails when one is missing, and the compositing recipe is
documented in `Branding`'s javadoc next to the existing Inkscape recipe.

### 8a. Making the splash actually appear on all three platforms

There are two launch routes and they do **not** honour the same mechanism.

`SplashScreen-Image` in the jar manifest is read by the `java` launcher only on
the `-jar` path. `build.xml:393` passes jpackage `--main-class my.stepss.StepssUI`,
so every generated launcher starts the class on a classpath instead, and the
manifest attribute is never consulted. Left at that, `java -jar stepss.jar`
would splash and the `.deb`, `.msi` and `.dmg` would not - on all three
platforms, not on two of them. This is not a per-platform quirk, it is the
bundle route being different from the jar route everywhere.

So both routes are covered explicitly, and the same PNG serves both:

| Route | Mechanism |
|---|---|
| `java -jar dist/stepss.jar` | `SplashScreen-Image: my/stepss/splash-460.png` in `manifest.mf` |
| Every jpackage bundle | `--java-options -splash:$APPDIR/splash-460.png`, with the PNGs copied into `dist/` so `--input dist` carries them into the app directory |

`$APPDIR` is jpackage's own token, substituted identically on Windows, macOS and
Linux, and `-splash:` is a plain JVM option on all three. The `@2x` file is
picked up by the JDK's own naming convention on either route, so HiDPI needs no
extra argument.

Two consequences worth stating: the splash PNGs now live in `dist/` beside the
jar as well as inside it, which `-post-jar` handles alongside the jar assembly;
and `Splash.open` must keep working when `getSplashScreen()` returns null,
because a launch that bypasses both routes - running the class from NetBeans -
has no splash at all and must still start.

### 9. The licence dialog, rebuilt

Moving it to `main` makes it the first thing a new user ever sees from STEPSS,
which is reason enough to stop it looking like a raw `JOptionPane` around a text
area. It becomes a modal `JDialog` this code owns.

```
 ╭────────────────────────────────────────────────────────────────╮
 │   ┌──────────────────────────────┐                             │
 │   │  STEPSS lockup  380 × 129    │                             │
 │   └──────────────────────────────┘                             │
 │                                                                │
 │   License Agreement                                            │
 │   RAMSES simulation engine · University of Liège               │
 │  ────────────────────────────────────────────────────────────  │
 │   ┌──────────────────────────────────────────────────────┐     │
 │   │ RAMSES LICENSE                                    ▲  │     │
 │   │ Copyright (c) University of Liège, Belgium.       █  │     │
 │   │                                                   █  │     │
 │   │ RAMSES, the solver of differential-algebraic …    ░  │     │
 │   │                                                   ░  │     │
 │   │ 1. Definitions                                    ░  │     │
 │   │ "Software" means a copy of RAMSES, which is …     ▼  │     │
 │   └──────────────────────────────────────────────────────┘     │
 │                                                                │
 │   The other components' licences are under Help ▸ About.       │
 │                              [ Decline ]      [ Accept ]       │
 ╰────────────────────────────────────────────────────────────────╯
```

What changes and why:

- **The lockup heads it**, from `Branding.logo(dark)`, and the dialog takes
  `Branding.windowIcons(dark)` so the taskbar entry for the first window STEPSS
  ever shows is not a generic coffee cup.
- **A subtitle names what is being agreed to.** Today the title says "License
  Agreement" and the body is the engine's licence, which is deliberate
  (`StepssUI.java:91-97`) but unexplained on screen. "RAMSES simulation engine ·
  University of Liège" is metadata, not terms. **The terms are deliberately not
  summarised.** The bus and core caps live in the licence text and in
  stepss-docs `getting-started/license.md`, which owns those facts; a friendly
  summary line here would be a third copy free to drift from both.
- **The text renders as HTML in a `JEditorPane`**, with the first line as a
  title and lines matching `^\d+\. ` as bold headings, so the numbered sections
  are navigable instead of a wall. The transform is defensive: text that matches
  nothing renders verbatim, so a reworded licence can never come out worse than
  today's plain text.
- **Scrollbars behave**: vertical as needed, horizontal never. The text is
  soft-wrapped prose, so the current `HORIZONTAL_SCROLLBAR_ALWAYS` shows a bar
  that can never scroll.
- **Accept is the default button, Escape declines.** Neither is true today.
- Comfortable measure and padding, roughly 640 x 560, resizable, centred on
  screen, since there is no parent frame yet.

Four faults in the current method are fixed on the way through, all in
`StepssUI.java:772-815`:

| Line | Fault |
|---|---|
| 774-775 | `new InputStreamReader(in)` runs before the `if (in != null)` guard at 778, so a missing resource is an NPE, and the guard is dead code |
| 796 | `HORIZONTAL_SCROLLBAR_ALWAYS` on a word-wrapping text area |
| 804 | `"default"` passed as `initialValue`, a string that is not one of the options |
| 810-812 | `catch (HeadlessException e) { e.getStackTrace(); }` discards the exception and returns, so a headless launch continues **as though the licence had been accepted**. It logs and exits instead |

## Error handling

| Situation | Behaviour |
|---|---|
| No splash available (IDE launch, `getSplashScreen()` null) | Everything else proceeds; no splash, no log |
| Splash artwork missing | `Branding.read` already logs at WARNING and returns null; the card draws without the lockup |
| No network at startup | Silent, `Level.FINE`, window unaffected |
| Unparseable version either side | Silent at startup; the manual check keeps its explicit "could not work out which version is the latest" dialog |
| `initRamses()` fails | Unchanged: dialog, `System.exit(1)`. The dialog is a window, so the splash is dismissed by the JVM |
| Licence declined | Unchanged: `System.exit(1)`, with no splash on screen to leave behind |

## Testing

This repository has no unit-test framework and is not gaining one, so the
established substitute applies: a headless harness for what is pure, and a
written acceptance run for what is not.

**`UpdateHarness` + `tools/update-harness.sh`**, following `tools/ssa-harness.sh`.
Pins `UpdateCheck.noticeFor` across: newer, equal, older, null location,
location naming no tag, and an unparseable segment on each side.

**The preferences migration is pinned in the same harness**, against a scratch
node root rather than the real one, across all four branches: fresh install,
legacy node present, new node already populated, and both present. `Preferences`
needs no display, so this runs headlessly like the rest.

**`ChromeCheck`** picks up the two splash assets for free through
`Branding.requiredResources()`.

**Acceptance, recorded in `docs/superpowers/plans/`:**

1. First run (`""` preference cleared): licence first, no splash, window follows.
   Check the dialog against the four fixed faults: Escape declines, Accept has
   focus, no horizontal scrollbar, and the numbered sections render as headings.
2. Normal run, current version: splash at least 3s, status names each tool, no
   banner.
3. Normal run, older version faked by editing `version.txt` in the built jar:
   banner with a working "Open release page", About line updated.
4. No network (interface down): no banner, no dialog, window not delayed.
5. Toggle off, relaunch: no request made at all.

## Risks and what stays unverified

- **Only Linux is executed here; all three must be correct by construction.**
  That is why the splash goes through `-splash:$APPDIR/...` rather than relying
  on the manifest: the mechanism is then the same on all three, and verifying it
  on the Linux bundle verifies the mechanism rather than one platform's luck.
  What genuinely cannot be checked here is that the Windows and macOS runners
  produce a bundle at all with the new `--java-options`, so the next release
  build is the confirmation. A missing splash degrades to no splash, never to a
  failed start.
- **HiDPI.** The JDK picks `splash-460@2x.png` by naming convention on both
  routes. Verified at whatever scaling this machine offers, not at all of them.
- **The preferences migration runs once and is destructive.** It calls
  `removeNode()` on `my.ramses.RamsesUI` after copying. A bug there costs a user
  their theme, window and working directory, so it is the one piece of this work
  with a harness case per branch: fresh install, legacy node present, new node
  already populated, and both present.
- **Three seconds is a floor on a fast machine.** On a warm cache the window is
  ready well before it, so the splash is padding the start by design. If that
  reads as slow in practice, the constant is one line.

## Files

| File | Change |
|---|---|
| `src/my/stepss/UpdateCheck.java` | New |
| `src/my/stepss/Splash.java` | New |
| `src/my/stepss/UpdateHarness.java` | New |
| `tools/update-harness.sh` | New |
| `src/my/stepss/splash-460.png`, `splash-460@2x.png` | New assets |
| `src/my/stepss/InlineBanner.java` | Action button, `notice(...)` |
| `src/my/stepss/Branding.java` | Splash assets in `requiredResources`, lockup image accessor, recipe comment |
| `src/my/stepss/platform/Toolchain.java` | `extractAll(Consumer<String>)` overload |
| `src/my/stepss/LicenseDialog.java` | New, the rebuilt agreement dialog |
| `src/my/stepss/StepssUI.java` | `main` sequence, `licenseAgreement` becomes a static call into `LicenseDialog`, `checkForUpdatesAtStartup`, `addUpdateToggle`, manual check reads `UpdateCheck` |
| `manifest.mf` | `SplashScreen-Image`, for the `java -jar` route |
| `build.xml` | `--java-options -splash:$APPDIR/...` for bundles; splash PNGs copied into `dist/` |

`Version.java` and `StepssUI.form` are untouched.

Outside this repo, in the same pass: the umbrella `CLAUDE.md` paragraph stating
that the preferences node is deliberately stale.
