# Observable picker — the `stepss-java-ui` half

Give "Extract Curves" a mouse-driven observable picker, replacing the terminal
window it opens today, by consuming `dyngraph --list` and driving DYNGRAPH
through a replay file.

Status: approved 2026-08-09.

This is the second half of the cross-platform observable picker. The first
half shipped in `stepss-dyngraph` v1.2.0; its design document,
`stepss-dyngraph/docs/superpowers/specs/2026-08-07-cross-platform-observable-picker-design.md`,
covers both halves and states that this one "is to be re-planned in that
repository when its turn comes". This is that re-plan. Where the two documents
disagree, this one governs the Java side.

## Why

DYNGRAPH's Intel-only Windows dialog was deleted in v1.2.0, and no published
binary on any platform ever had it. So `Extract Curves` now opens a terminal
on all three platforms and asks the user to type observable names back
exactly: `read(in,"(a)")string18` followed by a literal `busname(k)==string18`,
so an 18-character bus name must match to the character or the user gets
`is not a valid bus name !` and another prompt.

`dyngraph --list` was added precisely so this UI could replace that with
dropdowns. It has been sitting unused since v1.2.0 was pinned.

## What is already done

Four of the six `stepss-java-ui` items in the 2026-08-07 design have landed:

| Item | Status |
|---|---|
| `versions.properties` bumped to 1.2.0 with Windows asset and sha256 | done, `8eedc74` |
| `Toolchain.java` Windows DYNGRAPH switched to the ZIP payload | done, `8eedc74` |
| Committed `src/my/ramses/dyngraph.exe` deleted | done, `c07a85b` |
| `README.md` updated | done, `c07a85b` |
| `ObservablePicker` | **this document** |
| `runDyngraphButtonActionPerformed` rewritten | **this document** |

`README.md:83` currently states that the console prompts replace the dialog
everywhere. That sentence becomes wrong when this work lands and is listed
under Changes below.

## Approach

```
  Extract Curves
        │
        ├─ DyngraphRunner.list(output.trj)     dyngraph --list <trj>, capture stdout
        ├─ ObservableIndex.parse(stdout)       count-prefixed text -> model
        ├─ ObservablePicker.show(index)        modal dialog -> List<Selection>
        ├─ ReplayFile.write(selections, trj)   -> <temp>/sel.cmd
        └─ DyngraphRunner.plot(sel.cmd, base)  dyngraph -c -t sel.cmd -o<base>
                                               headless; stderr on failure
```

The trajectory file stays `<temp>/output.trj`. That is already the single
contract: loading a saved `.trj` copies it there (`RamsesUI.java:3134`), so the
picker needs no file chooser of its own.

The replay file passes the trajectory path as its first line and `-a` is not
used, so the invocation is byte for byte the configuration DYNGRAPH's smoke
gate pins. It also sidesteps quoting — a path containing spaces is a whole
line here, but would be an argv-quoting problem as `-a<path>`.

## Structure

A new `my.ramses.dyngraph` package, mirroring the `my.ramses.compile`
precedent.

| File | Responsibility | Depends on |
|---|---|---|
| `ObservableIndex.java` | Parse the index into categories, type tables, instance names and per-instance sub-lists. No Swing, no I/O. | nothing |
| `Selection.java` | One picked observable: keyword, instance name, optional sub-observable, display label. | nothing |
| `ReplayFile.java` | Selections -> the console keyword stream. Pure text. | `Selection` |
| `ObservablePicker.java` | The modal `JDialog`. Takes an index, returns selections. No parsing, no formatting, no process calls. | `ObservableIndex`, `Selection` |
| `DyngraphRunner.java` | Both invocations, via `DefaultExecutor` + `PumpStreamHandler`. | `Toolchain`, `Platform` |
| `PickerHarness.java` | Headless `main()`: fixture -> parse -> scripted selections -> emit -> diff. | all but the dialog |

Plus `tools/picker-harness.sh`, wrapping the harness against a built classes
dir the way `tools/compile-harness.sh` does.

The split is driven by the repository's verification convention: there is no
unit-test framework and none is being added, so anything that must be checked
has to be reachable from a headless `main()`. Parsing and replay emission are
the two error-prone parts, so neither lives inside the dialog.

`RamsesUI.runDyngraphButtonActionPerformed` shrinks to orchestration: resolve
the executable, list, parse, show, write, plot, enable buttons.

## Interface: reading the index

`ObservableIndex.parse` consumes the format documented in
`stepss-dyngraph/README.md` and pinned by `stepss-dyngraph/tests/golden/list.txt`.
It is line-oriented and count-prefixed: every block is a `<TAG> <count>` header
followed by exactly that many lines. The parser must therefore never scan for a
delimiter — an instance legitimately named `END` or `S` must not terminate
anything.

Rules the parser enforces:

- Line 1 must be exactly `DYNGRAPH-INDEX 1`. Anything else is an error, and
  the two cases are reported differently: a line that is not a
  `DYNGRAPH-INDEX` header at all means the binary is too old to know `--list`
  (an old DYNGRAPH ignores the flag and prompts on stdin), while a higher
  version number means a deliberate incompatible change and parsing on would
  be a bug.
- `TYPES <CATEGORY> <count>` blocks carry `<keyword><space><label>` pairs for
  the five categories whose types are fixed by the trajectory layout: BUS,
  SHUNT, LOAD, BRANCH, SYNC. The keyword is echoed back in the replay file;
  the label is what the dropdown shows.
- `<CATEGORY> <count>` blocks carry instance names.
- Each `SYNC` name is followed by its own `EXC <n>` and `TOR <n>` blocks. Each
  `INJ`, `LINK` and `DCTL` name is followed by its own `OBS <n>` block.
- `END` terminates the stream.

**Names must not be left-trimmed.** DYNGRAPH emits them `trim()`-ed, which in
Fortran strips trailing blanks only. A leading blank is part of the name, and
the console selector compares with `busname(k)==string18`, which pads the
shorter operand with trailing blanks — so a trailing blank is insignificant
but a leading one is not. Dropping it breaks the round-trip back through `-t`.
This is the one real trap in the format and the harness asserts it directly.

Malformed input — a count that overruns the stream, a missing `END`, an
unexpected tag — throws with the offending line number.

### Sub-lists are keyed by instance, never by category

Injector, link, DCTL, exciter and torque-controller observables are defined by
the *model* attached to each instance, so two injectors in the same network
routinely expose different observables. The index reflects this by giving every
instance its own `OBS`/`EXC`/`TOR` block, and the model must preserve it: a
parser that collects, say, all `OBS` names into one per-category list would
appear to work on any single-instance fixture and produce wrong dropdowns on a
real network.

An empty sub-list is legitimate — a machine with no excitation controller has
`EXC 0`.

### Three keywords Java must carry

The five categories with `TYPES` blocks are fully data-driven; Java echoes
their keywords back and hardcodes none. Injectors, links and DCTLs have no
`TYPES` block, so their replay keywords are not in the index at all. They are
`I`, `T` and `D`, at `stepss-dyngraph/src/selec_observ.f90:329`, `:360` and
`:393` respectively, and demonstrated by `stepss-dyngraph/tests/nested.cmd`.

`ReplayFile` carries that three-entry map from index tag (`INJ`, `LINK`,
`DCTL`) to replay keyword (`I`, `T`, `D`) as a named constant, commented with
those references. This is accepted coupling. The alternative — a
`DYNGRAPH-INDEX 2` carrying category keywords — is not worth a format break
and a third DYNGRAPH release for three letters as fixed as the trajectory
layout itself.

Note that keyword space and name space are disjoint and must not be conflated:
the released fixture contains a DCTL observable named `ST`, which is also the
sync keyword for mechanical torque.

## Interface: writing the replay file

Nothing new in DYNGRAPH. This is the grammar its smoke gate already pins, per
`stepss-dyngraph/tests/smoke.cmd` and `tests/nested.cmd`:

```
<trajectory path>        line 1
BM                       keyword
BUS1                     instance name
SS
GEN1
SOE                      redirects to a machine's exciter sub-list
GEN1
VF                       sub-observable, from that machine's EXC block
I                        injector
INJ1
P                        sub-observable, from that instance's OBS block
                         blank line: stop selecting
S                        stop
```

Keyword then name, with a third line — the sub-observable — when the keyword
is `SOE`, `SOT`, `I`, `T` or `D`. Terminated by a blank line and `S`.

Selection order is column order in the `.cur` and curve order in the `.plt`,
so the dialog's list order is meaningful and is what Remove operates on.

## The dialog

One category selector, a type-to-filter field over a scrolling name list, an
observable dropdown, and a running Selected list.

```
  Category [ BUS        v]   Filter [ 104_      ]
  +-- Names -------------------+
  | 1041                       |
  | 1042                       |
  | 1043                       |
  | 1045                       |
  +----------------------------+
  Observable [ voltage magnitude (pu)  v]

                     [ Add ]
  +-- Selected ------------------------------+
  | bus 1041: voltage magnitude (pu)         |
  | bus 1042: voltage magnitude (pu)         |
  | sync mach g1: rotor speed (pu)           |
  +------------------------------------------+
     [ Remove ] [ Clear ]       [ Plot ] [ Cancel ]
```

The 2026-08-07 design drew eight name/type dropdown rows, one per category,
copying the Intel dialog's layout. That is rejected here: a transmission
network can carry thousands of buses, and a `JComboBox` holding them is
unusable. Nobody has muscle memory to protect, because that dialog shipped in
no release. A filter field over a list scales and keeps selection
mouse-driven, which was the actual requirement.

The Observable control's model is recomputed whenever **`(category, instance)`**
changes, not merely on category change:

| Category | Observable control | Extra control |
|---|---|---|
| BUS, SHUNT, LOAD, BRANCH | the fixed `TYPES` table | none |
| SYNC | the fixed 17 `TYPES` entries | on `SOE` or `SOT`, a second dropdown holding *that machine's* `EXC` or `TOR` list |
| INJECTOR, LINK, DCTL | *that instance's* `OBS` list | none |

`SOE` and `SOT` are greyed out for a machine whose corresponding sub-list is
empty, rather than offering an empty dropdown. `Add` is disabled unless the
current row resolves to a complete selection. `Plot` is disabled while Selected
is empty, so the "no `.plt` written" case cannot arise from the UI.

The dialog opens with an empty Selected list every time. Carrying a selection
across invocations was considered and rejected as unearned complexity: names
would have to be revalidated against a freshly loaded trajectory and silently
dropped when absent.

The running list is itself the improvement over the Intel dialog, which took
at most one observable per category per round and reopened itself, so plotting
five bus voltages meant five round-trips.

## Running DYNGRAPH

Both invocations run headless through `DefaultExecutor` with a
`PumpStreamHandler` capturing stdout and stderr, the pattern
`viewCurvesButtonActionPerformed` already uses for gnuplot
(`RamsesUI.java:3243-3254`).

`--list` needs stdout captured to a buffer; DYNGRAPH's chatty per-category
counts go to unit 0 (stderr), so stdout carries the index alone and needs no
filtering.

The plot run is not interactive under `-t`, so it gets no terminal window. On
success the UI enables `View Curves` and `Save Current Curve` exactly as
today, with no dialog. On a non-zero exit it shows the captured stderr and
leaves those buttons disabled.

`PlatformLauncher.runInTerminal` keeps its other callers and is not touched.

### Failure behaviour

Every failure is a modal dialog leaving no partial state:

| Condition | Behaviour |
|---|---|
| `dyngraph` missing or absent from disk | Existing "Executable not found!" dialog, unchanged |
| `--list` exits non-zero | Captured stderr; picker never opens |
| First line is not a `DYNGRAPH-INDEX` header | "The bundled DYNGRAPH does not support `--list`" |
| `DYNGRAPH-INDEX` version above 1 | Refuse explicitly; do not parse on |
| Count mismatch, truncation, missing `END`, unexpected tag | Parse error with the offending line number |
| Every category empty | Say so; do not open an empty picker |
| `-t` run exits non-zero | Captured stderr; `View Curves` stays disabled |

There is no fallback to the old `-a<trj>` terminal path. The bundled DYNGRAPH
is pinned at 1.2.0 and `PayloadManifestCheck` fails the build when the payload
disagrees with `versions.properties`, so a fallback would be unreachable code
carrying a second, untested invocation.

## Testing

`PickerHarness` runs headless, needs no display, and is driven by
`tools/picker-harness.sh`.

Its fixture is a hand-extended version of `stepss-dyngraph/tests/golden/list.txt`,
embedded in `PickerHarness` as `String[]` literals rather than kept as a data
file. `CompileHarness` is self-contained the same way, and here there is a
second reason: the format's one trap is a **leading blank in a name**, which is
invisible in a text file, silently stripped by many editors, and vulnerable to
the CRLF normalisation that forced `.gitattributes` LF pins on DYNGRAPH's own
goldens. In a quoted Java literal it is explicit and cannot be lost. Java 11
has no text blocks, so this is an array of lines joined with `\n`.

The released golden must be extended rather than reused as-is, because it
carries exactly one injector, one link, one DCTL, one exciter and one torque
controller, each with a single observable — so it cannot distinguish a correct
per-instance parser from one that pools sub-lists per category. The fixture
adds:

- a **second injector whose `OBS` list differs from the first**, which is the
  model-dependent case and the one that catches category-pooled sub-lists;
- a **second machine with `EXC 0` and a non-empty `TOR`**, exercising the
  greying-out rule and the empty-sub-list branch;
- a **name with a leading blank**, asserted to survive parse -> emit unchanged;
- **instance names colliding with keywords** (`END`, `S`), proving the parse is
  count-driven and never scans for a delimiter.

The harness feeds scripted selections through `ReplayFile` and compares the
result against an expected replay stream, also embedded, pinning the round-trip
the way DYNGRAPH's own smoke gate pins its goldens. Malformed-input cases assert
the thrown message names the right line.

Manual acceptance on all three platforms — pick, plot, view curves — is a plan
task, as in the toolchain and custom-model-compilation plans. It is the only
way to cover the dialog itself, which the harness deliberately does not touch.

## Changes

**Create:**

| File | Responsibility |
|---|---|
| `src/my/ramses/dyngraph/ObservableIndex.java` | Pure parse of the `--list` index |
| `src/my/ramses/dyngraph/Selection.java` | One picked observable |
| `src/my/ramses/dyngraph/ReplayFile.java` | Pure emission of the replay stream |
| `src/my/ramses/dyngraph/ObservablePicker.java` | The modal dialog |
| `src/my/ramses/dyngraph/DyngraphRunner.java` | The two process invocations |
| `src/my/ramses/dyngraph/PickerHarness.java` | Headless verification `main()`, with embedded fixtures |
| `tools/picker-harness.sh` | Harness wrapper |

**Modify:**

| File | Change |
|---|---|
| `src/my/ramses/RamsesUI.java` | `runDyngraphButtonActionPerformed` becomes orchestration; the `runDyngraphButton` tooltip at line 1817 already promises a selection dialog and becomes true again |
| `README.md` | Line 13 and the DYNGRAPH paragraph at line 83, which currently say the console prompts replace the dialog everywhere |

No `.form` change. `runDyngraphButton` keeps its initial state, and the picker
is a hand-written `JDialog` rather than a NetBeans form, because its controls
are populated from parsed data and its layout does not vary.

## Constraints

- `javac.source` and `javac.target` are **11**. No `var`, no records, no
  switch expressions, no text blocks. Match the explicit generics the platform
  package uses.
- `RamsesUI.form` is the source of truth for generated `initComponents()`.
  This work requires no change to it; do not edit it.
- This repo is a submodule of the `stepss` umbrella. Commit here, push, then
  bump the pointer in the umbrella.

## Accepted costs

- Six new files plus two fixtures for what the 2026-08-07 design described as
  one class. The split is what makes parsing and emission verifiable without a
  display, given that this repository has no unit-test framework.
- Three replay keywords (`I`, `T`, `D`) hardcoded in `ReplayFile`, because the
  index does not carry them.
- The dialog itself is covered only by manual acceptance.
- Someone running DYNGRAPH standalone, outside `stepss-java-ui`, still gets
  console mode. Unchanged from today on every platform.
