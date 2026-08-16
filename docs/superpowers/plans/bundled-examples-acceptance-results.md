# Bundled examples: acceptance results

Verification record for
[`specs/2026-08-16-bundled-examples-design.md`](../specs/2026-08-16-bundled-examples-design.md)
and issue #9. Run on Linux x86_64, 2026-08-16.

## What the pins came out at

The three example repositories were tagged `v1.0.0` before any of this, so they
are pinned by tag like URAMSES rather than by commit SHA as the issue proposed.

| Example | Tag | Retained | Packed |
|---|---|---|---|
| Kundur two-area | `v1.0.0` | 8 files | 8 KB |
| IEEE Nordic | `v1.0.0` | 39 files | 69 KB |
| 5-bus tutorial | `v1.0.0` | 11 files | 204 KB |

283 KB added to a 33 MB jar. The issue budgeted "about a megabyte rather than
six"; dropping the Nordic's `doc/` and `jupyterhub-tutorial/` alone accounts for
3.5 MB of its 3.9 MB, and the 5-bus `tex/` for most of its 2.1 MB. The 204 KB
for the 5-bus is almost entirely `5_bus_oneline.png`, kept deliberately: a
teaching case is read as much as it is run.

## Verified

**The packer's two guards both fire.** Not assumed from reading them:

```
$ ExamplesPack broken.properties kundur ... COMPUTE
example 'kundur' completeness check FAILED
  vanished.dat is absent from stepss-Kundur-Two-Area-System-1.0.0.zip
exit=1

$ ExamplesPack examples.properties kundur ... 0000...0000
example 'kundur' manifest check FAILED
  expected 0000000000000000000000000000000000000000000000000000000000000000
  actual   925955a81ca9491e8badc0d472297ffc5d54bfd9dfc1059ae92f43875e49c51b
exit=1
```

The first is the one the refresh story rests on: it is what turns a broken
upstream release into a red build instead of a menu entry that opens onto
missing slots.

**`ant clean jar` runs the whole pipeline.**

```
fetch-examples:
     [java] uramses kit OK: 201 files -> ...uramses-kit-v3.74.zip (5037 KB)
     [java] example 'kundur' OK: 8 files -> ...example-kundur.zip (8 KB)
     [java] example 'nordic' OK: 39 files -> ...example-nordic.zip (69 KB)
     [java] example 'five-bus' OK: 11 files -> ...example-five-bus.zip (204 KB)
     [java] Payload manifest check OK: ...
BUILD SUCCESSFUL
```

**The harness passes against the built jar**, not just against `build/classes`:
`java -cp dist/stepss.jar my.stepss.examples.ExamplesHarness`. It checks that
ids and directories are unique, that no entry names more than the ten slots,
that every example retains its `LICENSE`, that each payload extracts leaving
every named file present and non-empty, that a second extraction over the same
directory is still complete, and that an archive entry containing `../` is
refused with nothing written outside the target.

**`ToolchainDump` still resolves all four engines** from the jar, so the added
payloads did not disturb the existing ones.

**142 release-tooling tests pass**, including eight new ones pinning that an
example release lands in `refreshed` and not in `changed`, that it rewrites
`versions.properties` and leaves `Toolchain.java` untouched, that a component
and an example move together, and that an example downgrade is refused like any
other.

## The dialog, and the bug that only looking found

`Robot.createScreenCapture` is refused under Wayland, so the first attempt to
photograph the dialog failed. `Window.printAll` into a `BufferedImage` needs no
screen capture and works, which is how the dialog was finally seen in both
themes.

**It was worth doing, because the first render was broken and nothing else had
caught it.** The detail pane was a `BoxLayout` down the Y axis holding the name,
the scale, the description and the action row. `BoxLayout` gives a component its
preferred height before it reaches the ones below it, so the description took
the whole pane: its own last line was clipped mid-sentence and **the Open
example and Documentation buttons were pushed off the bottom of the dialog
entirely**. Every check in the section above passed while that was true. A
harness can assert that an example extracts; it cannot notice that the button
for extracting it is not on screen.

The fix is structural rather than a size increase:

- the detail pane is a `BorderLayout`, so the heading and the action row take
  what they need and the description gets the rest. The buttons are reachable
  however long a summary a descriptor entry carries;
- the description is a wrapping `JTextArea` in a scroll pane, not a `JLabel`
  with an HTML body. A `JLabel` has no wrapping width of its own, so the width
  has to be baked into the HTML as a pixel count, which is a second place the
  dialog's geometry is written down and free to disagree with the first. It also
  means the summary is handled as the plain text it is, so an ampersand in a
  description stays an ampersand instead of swallowing the paragraph;
- the dialog is 760x620 with a 560x420 floor. The longest of the three
  descriptions (the Nordic's, 602 characters) fits without scrolling; the scroll
  pane is what makes a longer one safe, not what the common case relies on.

Confirmed by rendering afterwards: light and dark both correct, the dark lockup
carrying its own panel as `Branding` documents, the full description shown, both
buttons present, and Open example as the default button.

## Not verified here

**No example has been run end to end through the engine.** The slot files are
checked for existence and content, not for convergence. The Kundur and Nordic
data are the combinations the upstream `.cfg` files record, and the 5-bus one is
the combination its README states, so each is a combination that is known to
have worked rather than one invented here, but pressing Run on a freshly opened
example is still the check that has not happened.

**Nothing was clicked.** The dialog was rendered, not driven, so the paths
behind the buttons have not been exercised through the interface: opening an
example into the working directory, the Reuse / Fresh copy / Cancel prompt on a
second open, the Documentation button reaching each repository, and the startup
checkbox surviving a restart. The code behind the first two is covered by the
harness at the installer level; what is unchecked is the wiring from the button
to it.

## Decisions worth recording

**The default disturbance is the branch trip, not the undisturbed run.** The
Nordic ships `sim_nothing.cfg`, `sim_trip.cfg` and `sim_short_trip.cfg` over the
same network. Opening on `trip_branch.dst` (`sim_trip.cfg`) means a first run
produces something to look at; `nothing.dst` would produce a flat trace from a
system whose whole point is what happens after a branch is lost. `nothing.dst`
ships alongside.

Swapping which file is `dist` and which is `extra` does not change the manifest
digest, because the retained set is their union. That is why this change needed
no re-pin.

**One entry per repository.** The `.dst` and data variants travel with the
extracted copy and the user swaps them into the slots, which is how these cases
are actually worked with, and it keeps the dialog from growing a row per
disturbance file.
