# Observable selection: all eight categories, owned by one object

Replace the type-matching walk over `jPanel7` that Clear uses with an object
that owns the picker rows, and close the gap the walk was hiding: the picker
offers five of the eight observable categories RAMSES accepts, and the runtime
dropdown offers twelve of the fourteen display types it accepts.

Status: approved 2026-08-18.

Issue: SPS-L/stepss-java-ui#15. Follows SPS-L/stepss-java-ui#12, which fixed
the same class of fault in Save and Load configuration.

## Why

`clearObsFileButtonActionPerformed` (`StepssUI.java:5511`) clears the
observable dialog by walking `jPanel7.getComponents()` and matching on widget
type. It works today: `jPanel7` has 31 direct children and the loop reaches all
15 it means to.

It works only because `layoutObservablesTab` adds `jPanel7` whole into
`jPanel4`'s CENTER (`StepssUI.java:410`) and never touches its children, so the
panel keeps the generated `GridBagLayout` that puts every control directly on
it. That is precisely the arrangement `jPanel2` and `jPanel4` had before
`applyModernChrome()`, and #12 is what happened next: the controls moved one
level down, `Container.getComponents()` is not recursive, and both handlers
silently matched 0 of 24. `jPanel7` is now the last `GridBagLayout` island in a
window that is otherwise BorderLayout with `ActionBar` rows, so it is the most
likely next thing to be relaid out.

Two smaller faults in the same loop. It matches by type rather than identity,
so any text field, combo or checkbox added to `jPanel7` later is cleared by
Clear whether or not that was intended. And it re-enables fields and combos as
it goes, which is not gratuitous (`all*CheckBoxActionPerformed` disables them
when "all" is ticked, `StepssUI.java:5325-5377`) but is undocumented.

Clear is also not only a button. `saveOutputTrajButtonActionPerformed` calls it
whenever the user unticks "Save initialization data" (`StepssUI.java:5503`), so
its blast radius is the whole tab, reached two ways.

## The four contracts

Auditing the fix meant checking what the picker is supposed to produce. There
are four contracts between the three components, and two are broken.

### 1. Trajectory file: RAMSES writes, dyngraph reads. Sound.

Eight blocks, count-prefixed, fixed order. `observ_fin`
(`stepss-ramses/src/io/observ.f90:347-609`) against `get_observ_name`
(`stepss-dyngraph/src/get_observ_name.f90:25-244`):

| # | RAMSES array | values/step | dyngraph tag |
|---|---|---|---|
| 1 | `observ_bus` | 2 | `BUS` |
| 2 | `observ_shu` | 1 | `SHUNT` |
| 3 | `observ_ld` | 2 | `LOAD` |
| 4 | `observ_bra` | 6 | `BRANCH` |
| 5 | `observ_sync` | 15 + exc + tor | `SYNC` + `EXC`/`TOR` |
| 6 | `observ_inj` | per model | `INJ` + `OBS` |
| 7 | `observ_twop` | per model | `LINK` + `OBS` |
| 8 | `observ_dctl` | per model | `DCTL` + `OBS` |

RAMSES accumulates `totNumObs` as `2*bus + shu + 2*ld + 6*bra + 15*sync + exc +
tor + inj + twop + dctl`; dyngraph computes `nbvalperstep` as the identical
expression. Nothing to do.

### 2. `dyngraph --list`: dyngraph writes, java-ui reads. Sound.

`list_index.f90` emits the same eight categories in the same order, and
`ObservableIndex.CATEGORIES` (`ObservableIndex.java:38`) lists them. Nothing to
do.

### 3. Observables file: java-ui writes, RAMSES reads. Five of eight.

`add_observ` (`observ.f90:171-333`) accepts eight keywords.
`createCustomObsFile` (`StepssUI.java:7404`) writes five.

The three missing are as fully supported as the five present: each has its flag
array allocated in `observ_init`, its names written into the trajectory header
by `observ_fin`, and its own `!$omp SECTION` sampling it every step in
`write_observ` (`observ.f90:822-851` for TWOP and DCTL). The name lookups exist
too (`searl`, `seart`, `seard` in `search_names.f90`).

`IMPLOAD` and `LOAD` name the same thing. RAMSES' `LOAD` module holds only
`gil` and `bil`, a conductance and a susceptance per entry (`MODULES.f90:408`),
so a load there is an impedance load by construction; `IMPLOAD` selects out of
it, and dyngraph reads the same block and logs it as `' impedance loads'`
(`get_observ_name.f90:44`). Dynamic loads are a genuinely different thing and
are observed as injectors, under `INJEC`.

### 4. Runtime rows: java-ui writes, RAMSES reads. Twelve of fourteen.

`setup_runtime_observables` (`stepss-ramses/src/core/ramses.f90:818-938`)
accepts `BV`, `MS`, `COI`, `BPO`, `BPE`, `BQO`, `BQE`, `ON`, `TO`, `LAT`,
`o-d`, `P-d`, `RT`, `SOL`. `OBSERVABLE_TYPES` (`StepssUI.java:98-113`) offers
all but `TO` and `SOL`.

`TO` is the TWOP analogue of `ON`, so TWOP is invisible to the GUI on both
paths, not just in the file.

## Structure

Three new classes in a new `my.stepss.obs` package. The name `ObservablePicker`
is taken: `my.stepss.dyngraph.ObservablePicker` picks curves out of a finished
trajectory, which is a different job on the other side of the run.

**`ObservableCategory`** is one row. It carries the file keyword, the dyngraph
spelling, the human label, and the four controls it builds: a text field, an
Add button, a combo holding the chosen names, a Remove button, and the "all"
checkbox. It exposes `clear()`, `add()`, `removeSelected()`, `allToggled()`,
`appendTo(writer)`, and `names()`/`isAll()` for later reuse.

**`ObservableWizard`** holds the eight categories plus the rest of the tab's
state: `fileObs`, the three runtime type combos and name fields, the four
recording checkboxes and the wizard checkbox. `reset()` is the whole of Clear.
`write(File)` replaces `createCustomObsFile`.

`reset()` unticks the wizard checkbox but does not hide the panel. Showing and
hiding `jPanel7` stays in `StepssUI`, where the rest of the tab's visibility
lives, so the clear handler is `wizard.reset()` followed by the existing
`observFileWizButtonActionPerformed(null)`. Keeping the wizard free of Swing
visibility is what lets the harness build it without a frame.

Unlike `ScenarioBinding`, which holds references to controls the form owns,
`ObservableCategory` **builds its own**. Eight rows means 49 controls where
`jPanel7` has 31, and adding 18 through the NetBeans designer would leave the
last `GridBagLayout` island exactly where issue #15 says the next silent
breakage will come from. Building them makes adding a ninth category a one-line
change, retires the island, and makes the issue's third acceptance criterion
structurally true rather than merely observed: you cannot add an unrelated
control to a panel the wizard builds. The precedent is already in the tree and
documented at `StepssUI.java:7395-7402`, where the one-line diagram row was
declared as plain fields rather than form controls for this same reason.

`layoutObservablesTab` builds `jPanel7` by looping over the eight categories.

## The eight categories

In trajectory emission order, which is also `dyngraph --list` order. The
current UI order (BUS, SYNC, SHUNT, BRANCH, INJEC) matches neither. Order is
irrelevant to `add_observ`, so this is cosmetic, but it costs nothing to agree.

| Label | File keyword | dyngraph tag |
|---|---|---|
| Buses | `BUS` | `BUS` |
| Shunts | `SHUNT` | `SHUNT` |
| Impedance loads | `IMPLOAD` | `LOAD` |
| Branches | `BRANCH` | `BRANCH` |
| Synchronous machines | `SYNC` | `SYNC` |
| Injectors | `INJEC` | `INJ` |
| Two-port injectors | `TWOP` | `LINK` |
| Discrete controllers | `DCTL` | `DCTL` |

Human labels rather than the raw keyword the rows show today, because three of
the eight are spelled differently in the file and in the plot list, and a user
who reads `LINK` in dyngraph has no way to guess `TWOP`. Each row's tooltip
names both spellings.

The impedance-load tooltip also says that entries named `M_xyz` are synthesised
from the power mismatch at bus `xyz` (`MODULES.f90:422`), so the list contains
entries nobody declared.

## Behaviour changes

1. **Clear resets the whole tab.** Today it leaves the three runtime type
   dropdowns set and leaves `saveContTrace` and `saveDiscTrace` ticked while
   clearing the other two recording boxes.
2. **A duplicate Add reports through the banner** instead of writing
   `"Already in List!"` into the field. That sentinel is addable as an
   observable on a second press, and reaches the file as `BUS Already in List!`.
3. **Remove is a no-op when nothing is selected**, and its button is disabled
   while the list is empty. Today `removeItemAt(getSelectedIndex())` on an
   empty list is `removeItemAt(-1)`, an uncaught EDT exception, and Clear is
   what empties the lists.
4. **Names are validated on Add**: blank, longer than 20 characters, or
   containing whitespace or a comma is refused with a banner. `add_observ`
   parses each line with `read(string,*) type, name` into a
   `character(len=20)`, so a longer name is silently truncated and a name with
   a space silently splits.
5. **Branch lines lose their trailing space**, which the other categories never
   had. Safe: the reader does `trim(adjustl(string))` then a list-directed
   read.

## The runtime dropdown

`OBSERVABLE_TYPES` gains two entries:

- **`TO`**, "Two-port injector Observable". Like `ON`, RAMSES reads three
  tokens for it (`read(displaystring,*) displaytype, equipmentname, ObsName`),
  so the name field carries the equipment name then the observable name. `ON`
  already works this way and says so nowhere; both tooltips will.
- **`SOL`**, "Injector solutions". A solver diagnostic in the same class as the
  `RT` and `LAT` rows already offered, plotted as "nb. of inj. solutions"
  (`gnuplot.f90:88`). It names no equipment, so `writeObservable` must write it
  on a blank field, the treatment `WALL_TIME` already gets
  (`StepssUI.java:3271-3283`).

## Testing

`tools/observables-harness.sh` runs `my.stepss.obs.ObservablesHarness`
headless, in the mould of `tools/scenario-harness.sh`. This repository has no
unit-test framework and is not gaining one.

The harness builds the eight categories, buries them several containers deep,
and checks:

- **Reset.** Fill every control in all eight rows, reset, assert every one is
  empty, enabled and unticked, and that the runtime rows and recording boxes
  went with them. Nesting is the point: it is what makes the check unable to
  pass by accident if the layout changes.
- **The file.** Eight categories against a known expected `customObs.txt`,
  covering a named list, an "all" tick, an empty category and the branch line
  that no longer carries a trailing space.
- **The keywords.** All eight file keywords present and spelled as
  `add_observ` reads them, so a rename here fails loudly rather than producing
  a file RAMSES skips with a warning.
- **Add.** Duplicate refused, blank refused, 21 characters refused, a name with
  a space refused, a 20-character name accepted.
- **Remove.** No-op on an empty list, no-op with nothing selected.

## Changes

- `src/my/stepss/obs/ObservableCategory.java` (new)
- `src/my/stepss/obs/ObservableWizard.java` (new)
- `src/my/stepss/obs/ObservablesHarness.java` (new)
- `tools/observables-harness.sh` (new)
- `src/my/stepss/StepssUI.java`: `layoutObservablesTab` builds the rows;
  sixteen handlers and `createCustomObsFile` collapse into delegations;
  `OBSERVABLE_TYPES` gains `TO` and `SOL`; the 31 generated picker controls and
  their event stubs go
- `src/my/stepss/StepssUI.form`: the `jPanel7` subtree, lines 1178 to 1626

## Out of scope

**Persisting the picker lists into the `.cfg`.** #12 deliberately left them
out. `ObservableCategory.names()` and `isAll()` are what make that follow-up
cheap, and nothing here touches the scenario format.

**The form suffixes.** `add_observ` accepts `BUS-POL`/`BUS-REC` and
`BRANCH-POW`/`BRANCH-CUR` on the type token. The GUI has never emitted one and
will not start here.

**A RAMSES bug this audit turned up, which needs its own issue in
stepss-ramses.** There are three runtime evaluation switches and they disagree.
`simul_decomp.f90:2230` handles all fourteen display types; `simul_integr.f90`
handles ten and contains no reference to `ON`, `TO`, `o-d` or `P-d` anywhere in
the file. The default scheme is `DE` (`get_settings.f90:79`), so only the
integrated scheme is affected, but there the failure is worse than a missing
curve: `varcol` is computed at parse time and reserves each type's columns
regardless (2 each for `o-d` and `P-d`, 1 each for `ON` and `TO`), and gnuplot
plots `using 1:varcol(i)` (`gnuplot.f90:142-149`), so an unevaluated row shifts
every column after it and every curve below it plots the wrong quantity
silently. `o-d` and `P-d` are already offered by this application's dropdown.
Adding `TO` here adds a fourth type with the same problem, which is an argument
for fixing RAMSES, not for withholding `TO`.

## Constraints

- `StepssUI.form` and the generated `initComponents` must agree after the
  deletion, or the next person to open the form in NetBeans regenerates a file
  that does not compile.
- The harness must not construct `StepssUI`. Its constructor extracts the
  toolchain and exits the JVM when it cannot, so the round trip has to be
  checkable from plain Swing controls, which is why `ObservableCategory` takes
  no frame.
- No new dependency. `my.stepss.obs` uses the JDK and Swing only, so
  `build/classes` alone is enough to run the harness.

## Accepted costs

- **Deleting 449 lines from `StepssUI.form` plus the matching `initComponents`
  block is the bulk of the risk in this change.** It is mechanical and
  self-contained, but it is generated code and the compiler is the only thing
  checking it.
- **Three more rows of screen.** `jPanel7` grows from five categories to eight
  in a panel that already sets a 187px preferred height. It takes the CENTER of
  a BorderLayout so it has room, but the wizard is taller than it was.
- **`SOL` is undocumented upstream.** It is grouped with `RT` in RAMSES as a
  "Special Display" and its meaning is inferred from its gnuplot label. It is
  offered because `RT` and `LAT` already are, not because it is well specified.
