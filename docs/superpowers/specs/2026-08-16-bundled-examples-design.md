# Bundled example test systems

Design for [#9](https://github.com/SPS-L/stepss-java-ui/issues/9): ship three
ready-to-run test systems inside the jar and open them from **File → Open
Examples**.

## The problem

A new user has a working STEPSS and nothing to run in it. Today they have to
know `stepss-test-systems` exists, find it, clone it, and then work out which of
its files goes in which of the ten data slots. Nothing in the interface points
at any of that.

## What ships

Three examples, one per repository. All three are public, all three are Apache
2.0, and all three are already submodules of `stepss-test-systems`.

| Example | Repository | Retained |
|---|---|---|
| Kundur two-area | `SPS-L/stepss-Kundur-Two-Area-System` | 21 KB |
| IEEE Nordic | `SPS-L/stepss-IEEE-Nordic-Test-system` | 285 KB |
| 5-bus tutorial | `SPS-L/stepss-5-bus-test-system` | 252 KB |

About 558 KB uncompressed on a 34 MB jar. Unfiltered the same three are 6 MB,
almost all of it the Nordic's `doc/` and `jupyterhub-tutorial/` (3.5 MB of its
3.9 MB) and the 5-bus `tex/` assignment source.

One entry per repository, not one per scenario. The `.dst` and data variants
travel with the extracted copy and the user swaps them into the slots as the
study requires: that is the normal way to work with these cases, and it keeps
the dialog from growing a row per disturbance file.

## Pinning: examples follow releases

The issue proposed pinning by commit SHA, on the grounds that none of the three
repositories had a tag. They now have `v1.0.0`, so the URAMSES precedent applies
unchanged and no new pin flavour is needed:

```properties
kundur.version=1.0.0
kundur.repo=SPS-L/stepss-Kundur-Two-Area-System
kundur.tag=v1.0.0
kundur.source.url=https://github.com/SPS-L/stepss-Kundur-Two-Area-System/archive/refs/tags/v1.0.0.zip
kundur.source.url.pattern=https://github.com/SPS-L/stepss-Kundur-Two-Area-System/archive/refs/tags/v@VERSION@.zip
kundur.manifest.sha256=...
```

Following releases rather than `main` dissolves the one risk the issue named.
Its worry was that "whatever is on `main` at the moment a STEPSS release happens
is what ships, and a mid-edit commit would ship with it". A release has a human
behind it, exactly as the engines' pins do.

The digest is over a **content manifest**, not over the archive, for the reason
`UramsesKitPack` documents: GitHub's generated source archives carry no
byte-stability guarantee, so a re-compression upstream would otherwise read as
tampering.

These repositories are public, so the archive comes over plain HTTPS with no
`gh` and no `STEPSS_TOKEN`, mirroring `fetch-uramses`.

### The packed name carries no version

`payload/example-kundur.zip`, not `payload/example-kundur-v1.0.0.zip`. This is
the one place examples deliberately diverge from the engines, and it removes a
whole class of drift: there is no second file naming the payload, so
`Toolchain.java`'s rename dance and `pins.rewrite_toolchain` do not apply, and an
example bump touches `versions.properties` and nothing else.

It is safe because `ToolExtractor` folds the application version into its
staleness stamp (`payload.resource + "|" + appVersion()`) precisely so a
version-invariant resource still busts on upgrade. `gpwin.zip` and the Helios
assets already depend on that same property.

## The descriptor

`src/my/stepss/examples/examples.properties`, read at build time by the pack
step and at run time by the dialog. One file answering four questions: what the
dialog says, which file fills which slot, what gets retained, and where the
documentation lives.

```properties
examples=kundur, nordic, five-bus

kundur.name=Kundur two-area
kundur.scale=11 buses, 4 machines, 60 Hz
kundur.summary=Two symmetric areas joined by a weak tie, ...
kundur.docs=https://github.com/SPS-L/stepss-Kundur-Two-Area-System
kundur.dir=kundur-two-area
kundur.data=lf.dat, dyn.dat, solveroptions.dat
kundur.dist=disturb.dst
kundur.obs=obs.dat
kundur.extra=dyn_noPSS.dat, README.md, LICENSE
```

**The retain list is not a key.** It is `data + dist + obs + extra`. Deriving it
rather than declaring it separately means two things fall out for free: the
build fails if an example stops carrying a file an entry names, and it is
impossible to ship a file no entry knows about.

Descriptions come from each repository's README, written out here rather than
parsed from it at run time. A README is prose that changes upstream for reasons
that have nothing to do with this menu, and parsing one is how a heading edit
turns into a blank dialog.

### The committed `.cfg` files are not retained

Each upstream repository ships a `sim.cfg` (the Nordic ships three) that is a
Java `Properties` file of absolute paths from whoever last saved it:

```
fileData1=C\:\\Users\\tvanc\\OneDrive\\TRAVAIL\\dat\\Kundur\\lf.dat
```

`loadConfigMenuItemActionPerformed` reads those values straight into the slot
fields, so shipping one and loading it would fill the form with someone else's
`C:\Users` paths on every platform. **Open Examples generates the configuration
from where the files actually landed and never reads an upstream `.cfg`.**

Those files are still the authority on which file belongs in which slot. That
knowledge is transcribed into the `data`/`dist`/`obs` keys above; the files
themselves are excluded from the retain list, because a `.cfg` full of dead
paths sitting beside a working generated configuration is exactly the confusion
this is meant to remove.

The 5-bus repository has no `.cfg` at all; its README states the slots in prose
and that is where its mapping comes from.

### Which combination an example opens on

Where a repository offers several, the entry loads the one that does something
the first time Run is pressed, not the inert one:

| Example | Slots | Disturbance |
|---|---|---|
| Kundur | `lf.dat`, `dyn.dat`, `solveroptions.dat` | `disturb.dst`, a +0.5 pu step on L9 |
| IEEE Nordic | `dyn_A.dat`, `volt_rat_A.dat`, `settings1.dat` | `trip_branch.dst`, upstream's `sim_trip.cfg` |
| 5-bus | `dyn.dat`, `lf1solv.dat`, `solveroptions.dat` | `nothing.dst` |

The Nordic could equally have opened on `nothing.dst` (its `sim_nothing.cfg`),
and deliberately does not: a first run that produces a flat trace teaches
nothing about a system whose whole point is what happens after a branch is lost.
`nothing.dst` ships alongside for the undisturbed run.

The 5-bus is the exception that keeps its empty disturbance file, because
upstream ships nothing else: `nothing.dst` is explicitly meant to be extended
with the records of the case being studied, and the assignment is built around
doing exactly that.

## Build

`ExamplesPack`, a `main()` on the build classpath following `UramsesKitPack`:

1. strip the archive's single top-level directory,
2. filter to the descriptor's derived retain list,
3. **fail, naming the file, if any retained path is absent from the archive**,
4. build a `<sha256>  <path>` manifest over the retained files, sorted by path,
5. compare its digest against the pin, or print it in `COMPUTE` mode,
6. repack with fixed timestamps.

Step 3 is what turns a broken upstream release into a red build rather than a
menu entry that opens onto missing files. `PayloadManifestCheck` grows a second
half asserting every descriptor-named payload was staged, so a descriptor entry
added without a pin fails at `-post-compile` rather than at run time.

## Runtime

Four classes under `my.stepss.examples`, none of them touching `Toolchain`:

| Class | Responsibility |
|---|---|
| `Example` | One parsed descriptor entry. |
| `ExampleCatalog` | Reads the descriptor resource, returns the entries in declared order. |
| `ExampleInstaller` | Extracts one payload into a directory, with `ToolExtractor`'s containment check. |
| `ExamplesDialog` | The panel. |

Routing examples through `ToolSpec`/`Toolchain` was rejected. Those are keyed by
`Platform`, which examples are not, and they extract into `toolDir` — the
temporary directory `formWindowClosing` deletes on exit. Anything the user
edited would be gone without warning.

### Where the extracted copy lands

Under a remembered examples root, in a named subdirectory per example, never in
`toolDir`.

A new `examplesDirectory` preference holds the root, so repeated opens are
siblings rather than nesting inside each other. It falls back to the current
working directory, and then to the existing Select-working-directory chooser
when neither is set, which is the state a fresh install is in.

Opening an example then **makes its directory the working directory** and
re-runs `initRamses()`, so results land beside the case and the title bar names
it. This moves a setting the user chose, which is why it happens only on an
explicit open.

An existing directory is never silently overwritten: the second thing a user
does with an example is edit it. A second open offers Reuse, Fresh copy or
Cancel, naming the path.

### The dialog

Master-detail, in the hand-built Swing idiom `LicenseDialog` already
establishes, modelled on the DIgSILENT PowerFactory examples panel:

```
┌──────────────────────────────────────────────────────────┐
│  [ STEPSS lockup ]                                       │
├──────────────────────────────────────────────────────────┤
│ ┌──────────────┐  IEEE Nordic                            │
│ │ Kundur two-… │  74 buses, 20 machines, 400/220/130 kV  │
│ │ IEEE Nordic  │                                         │
│ │ 5-bus tutor… │  The Nordic variant from IEEE PES       │
│ │              │  technical report PES-TR19 …            │
│ │              │      [ Open example ]  [ Documentation ]│
│ └──────────────┘                                         │
├──────────────────────────────────────────────────────────┤
│ ☑ Show this at startup                          [ Close ]│
└──────────────────────────────────────────────────────────┘
```

- The header uses `Branding.logo(dark)`, the lockup the About box draws, so the
  panel follows the theme without its own artwork.
- Left is a `JList` rather than the reference's column of buttons: the same
  thing to look at, but keyboard-navigable, and it grows with a descriptor entry
  instead of a relayout.
- **Documentation** opens the example's repository through
  `BareBonesBrowserLaunch`, which already backs Help → User Guide. This is what
  earns leaving `doc/`, `tex/` and the notebooks out of the jar: they are one
  click away rather than 6 MB of payload.
- **Show this at startup** is a new `showExamplesAtStartup` preference, default
  on, shown after the licence gate. It is the part that actually answers the
  issue's opening complaint. See below for how the choice is stored.
- No tabs. Three entries do not need categories, and the descriptor can grow a
  `category` key later without touching the dialog.

### Remembering the startup choice

A boolean in the same node as every other saved setting
(`my.stepss.StepssUI`), written and flushed on toggle exactly as
`DARK_THEME_KEY` and `CHECK_UPDATES_KEY` are.

**The migration is free, but only because of how `PreferenceMigration` was
written.** `node()` copies *every* key rather than an enumerated list,
because "an enumeration silently drops whatever is added later". So a new key
needs no migration code. That stays true only while every write goes through
`preferences()`: that method is what performs the migration on first call, and
`node()` returns early once `current.keys().length > 0`. A write that bypassed
it could populate the new node before the migration ran, make a legacy node look
already-migrated, and abandon the user's theme, geometry and working directory.
Nothing here does that, but it is the trap.

**Flushed on toggle, not on Close**, for the reason the two existing toggles
give: `Preferences` writes back on its own schedule, so a choice followed by a
kill or a crash is lost and the next launch contradicts the checkbox. It also
means closing the dialog with the window's X honours the choice.

**Default true, and the key stays absent until the user unticks it.** So "has
never opened the dialog" and "wants it every launch" are the same state, which
is the one the feature exists for. An existing installation that upgrades sees
the panel once on the next launch and one tick stops it.

**No second control is needed to undo it.** The dialog is always reachable from
File → Open Examples and the checkbox is in it. That is why this toggle lives in
the dialog while the theme and update-check toggles live in the Tools menu:
those have no other home.

`FIRST_RUN` stays independent. It is consumed once and set false, so a genuine
first run goes splash → licence → window → examples.

### The menu item

**File → Open Examples**, between Load configuration and Exit, because it is a
way of loading a configuration.

It is added to `StepssUI.form`, not appended by hand after `initComponents`
returns. Both are precedented here (`fileMenu.setMnemonic` is hand-written), and
the form is chosen so the next designer save keeps the item instead of deleting
it. `MenuShortcuts.applyPlatformModifier` then remaps its accelerator on macOS
with no further work, since it sweeps the whole menu bar.

## Release integration

`pins.EXAMPLES` sits beside `pins.COMPONENTS`. `bump.run` gains a third result
list, `refreshed`, and writes when `changed or refreshed` — but `proceed` in
`release.yml` keeps counting `changed` alone.

So an example release never cuts a STEPSS release, and rides along with the next
one. "An example repository got a typo fix in its README" is not a reason to
publish a new STEPSS, and refreshed-on-release is the safer default the issue
argued for. An example-only tick writes a pin that the gated commit step then
discards; the next real release re-detects it, so the operation is idempotent
and costs one small download.

## Licensing

Each extracted example carries its `LICENSE`, which is the whole of the Apache
2.0 obligation for these three. A stanza goes in `packaging/linux/copyright`
alongside the existing per-component ones, pointing at
`getting-started/license.md` as they do rather than becoming a second copy of
the facts.

## Verification

This repository has no unit-test framework; harnesses are the substitute.

- `ExamplesHarness` and `tools/examples-harness.sh`, following
  `ssa-harness.sh`: the catalog parses, every payload extracts, every
  descriptor-named file lands, no entry escapes its directory, and the generated
  slot values name files that exist.
- `tools/ci/tests` cases pinning that an example-only tick leaves `proceed`
  false and that a component tick carries the example pins with it.
- `ExamplesPack` failing on a missing named file is itself covered, since it is
  the guard the whole refresh story rests on.

## Done when

The issue's checklist, restated against this design:

- [ ] File → Open Examples lists the three examples with descriptions, and
      opening one leaves every data slot filled and a case that runs without the
      user touching a file chooser.
- [ ] The extracted copy lands under the remembered examples root, survives
      exit, and a second open does not silently overwrite an edited copy.
- [ ] No path from any upstream `sim.cfg` reaches the interface.
- [ ] Each example is pinned by tag and a content-manifest digest, refreshed by
      the release bump, with the digest verified at build time.
- [ ] The build fails if an example repository stops carrying a file its
      descriptor names.
- [ ] Only the data files are bundled: no `doc/`, no `tex/`, no notebooks.
- [ ] Each extracted example carries its `LICENSE`.
- [ ] Adding a fourth example is a descriptor entry and a pin, with no UI
      change.
