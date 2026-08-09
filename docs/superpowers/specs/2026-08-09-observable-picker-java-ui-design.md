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

Every `stepss-java-ui` item in the 2026-08-07 design has landed except the two
that are the point of it — the picker and the handler rewrite. The plumbing
went in as a side effect of pinning v1.2.0:

| Item | Status |
|---|---|
| `versions.properties` Linux and macOS pins bumped to 1.2.0 | done, `8eedc74` |
| `versions.properties` Windows asset and sha256 added | done, `c07a85b` |
| `Toolchain.java` Windows DYNGRAPH switched from `Kind.RAW` to the ZIP payload | done, `c07a85b` |
| Committed `src/my/ramses/dyngraph.exe` deleted | done, `c07a85b` |
| `README.md` updated | done, `c07a85b` |
| `ObservablePicker` | **this document** |
| `runDyngraphButtonActionPerformed` rewritten | **this document** |

The two commits split along a line worth keeping straight: `8eedc74` bumped
only the Linux and macOS pins and says so — "Windows still ships the committed
Intel dialog build ... whether it carries the dialog observable picker the GUI
needs is unverified". `c07a85b` is where that question was answered (the v1.2.0
Windows build reports `BUILDINFO: interface console`) and where the Windows
payload, the `Toolchain` switch and the deletion all landed.

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
        └─ DyngraphRunner.plot(sel.cmd)        dyngraph -c -t sel.cmd
                                                 -o<temp>/tempGnupOut
                                               headless; stderr on failure
```

The trajectory file stays `<temp>/output.trj`. That is already the single
contract: loading a saved `.trj` copies it there (`RamsesUI.java:3134`), so the
picker needs no file chooser of its own.

**The output base name is fixed at `<temp>/tempGnupOut`, absolute**, exactly as
today (`RamsesUI.java:3275`). It is not a free parameter: `viewCurvesButton`
opens `<temp>/tempGnupOut.plt` by name (`RamsesUI.java:3245`), and
`saveCurrentCurveButton` rewrites the `.plt` by string-replacing the
**absolute** path `<temp>/tempGnupOut.cur` inside it (`RamsesUI.java:3157,3163`).
A relative base, or any other name, still plots correctly and silently breaks
both buttons.

The replay file passes the trajectory path as its first line and `-a` is not
used. That sidesteps quoting — a path containing spaces is a whole line here,
but would be an argv-quoting problem as `-a<path>`. The `-o` argument still
carries a possibly-space-containing absolute path as one argv token, so it must
be added with `CommandLine.addArgument(value, false)`; the default handling
re-quotes and would corrupt it.

## Structure

A new `my.ramses.dyngraph` package, mirroring the `my.ramses.compile`
precedent.

| File | Responsibility | Depends on |
|---|---|---|
| `ObservableIndex.java` | Parse the index into categories, type tables, instance names and per-instance sub-lists. No Swing, no I/O. | nothing |
| `Selection.java` | One picked observable: keyword, instance name, optional sub-observable. Composes its own display label. | nothing |
| `ReplayFile.java` | Selections -> the console keyword stream. Pure text. | `Selection` |
| `ObservablePicker.java` | The modal `JDialog`. Takes an index, returns selections. No parsing, no label composition, no process calls. | `ObservableIndex`, `Selection` |
| `DyngraphRunner.java` | Both invocations, off the EDT. | `Toolchain`, `Platform` |
| `PickerHarness.java` | Headless `main()`: fixture -> parse -> scripted selections -> emit -> compare. | `ObservableIndex`, `Selection`, `ReplayFile` |

Plus `tools/picker-harness.sh`, wrapping the harness against a built classes
dir the way `tools/compile-harness.sh` does.

`PickerHarness` deliberately does **not** depend on `DyngraphRunner`: it
launches no process and needs no extracted payload, so it runs on a bare
checkout with `build/classes` present.

The split is driven by the repository's verification convention: there is no
unit-test framework and none is being added, so anything that must be checked
has to be reachable from a headless `main()`. Parsing and replay emission are
the two error-prone parts, so neither lives inside the dialog.

`RamsesUI.runDyngraphButtonActionPerformed` shrinks to orchestration: resolve
the executable, list, parse, show, write, plot, enable buttons.

### Display labels

`Selection.label()` composes the label, and it reproduces the exact strings
DYNGRAPH writes into `desc_obs` — which become the `.plt` curve titles — so the
Selected list reads the same as the plot the user gets
(`stepss-dyngraph/src/selec_observ.f90:92,123,157,203,299,316,322,355,388,432`):

```
bus <name>: <type label>
shunt <name>: <type label>
impedance load <name>: <type label>
branch <name>: <type label>
sync mach <name>: <type label>
sync mach <name>: excit control: <observable>
sync mach <name>: torque control: <observable>
injector <name>: <observable>
link <name>: <observable>
DCTL <name>: <observable>
```

Note that `SOE` and `SOT` render as `excit control:` and `torque control:`
rather than with their `TYPES` labels ("observable of excitation controller"),
because that is what the console produces.

## Interface: reading the index

`ObservableIndex.parse` consumes the format documented in
`stepss-dyngraph/README.md` and pinned by `stepss-dyngraph/tests/golden/list.txt`.
It is line-oriented and count-prefixed: every block is a `<TAG> <count>` header
followed by exactly that many lines. The parser must therefore never scan for a
delimiter — an instance legitimately named `END` or `S` must not terminate
anything.

Rules the parser enforces:

- Line 1 must be exactly `DYNGRAPH-INDEX 1`. A higher version number means a
  deliberate incompatible change, so parsing on would be a bug; refuse.
  Anything that is not a `DYNGRAPH-INDEX` header at all is also refused, but
  see the note on detection below — that check is a backstop, not the primary
  signal.
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

### Detecting an old DYNGRAPH is exit-status-first, not header-first

The 2026-08-07 design says an old DYNGRAPH "ignores `--list` and prompts for a
filename on stdin", implying the Java side should recognise that prompt on
stdout. It will not appear there. `main.f90:25` sets `log=0` and the prompt at
`main.f90:41` writes to `log`, so it goes to **stderr** and stdout stays empty;
with the child's stdin closed the subsequent `read` hits EOF and the process
exits non-zero. DYNGRAPH's own gate pins this discrimination
(`stepss-dyngraph/tools/smoke_gate.sh:144-151`, `README.md:126-129`).

So the order is: **exit status first**, and only then the header. A non-zero
exit is reported with its captured stderr — which covers both "trajectory file
missing" and "binary too old" — and the header check catches a zero-exit
program that printed something unexpected. The friendly "does not support
`--list`" wording therefore belongs on the non-zero-exit path when stdout came
back empty, not on the header mismatch.

### Charset

`--list` output is read and `sel.cmd` is written as **ISO-8859-1**, explicitly,
on both sides. DYNGRAPH has no encoding concept: names are raw bytes copied out
of the trajectory file and compared byte-for-byte on the way back in.
ISO-8859-1 maps bytes 0–255 onto the first 256 code points and back, so the
round-trip is exact for any byte the trajectory can contain. Reading as UTF-8
would turn a byte ≥ 0x80 into a replacement character and write different bytes
back.

This matters more than it looks, because the failure is silent. An unmatched
name does not abort DYNGRAPH — the console selector re-prompts — so a desynced
replay file can consume the blank and `S` lines as if they were answers and
still exit 0, having plotted fewer or different curves than the user asked for.
Nothing in the failure table below catches exit-zero-but-wrong.

A non-ASCII name will render as mojibake in the dialog. That is the accepted
trade: a wrong glyph the user can see beats a wrong plot they cannot.

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
is `SOE`, `SOT`, `I`, `T` or `D`, and for no other keyword. Terminated by a
blank line and `S`. Keywords are matched case-sensitively in upper case; only
the trailing stop accepts `s` as well.

`sel.cmd` is written into the temp directory and left there after the run, like
`output.trj` and `tempGnupOut.*`. The temp directory is already the UI's
scratch space and is cleaned as a whole; a leftover replay file is also the
first thing worth looking at when a plot comes out wrong.

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

The empty-sub-list rule is symmetric across every category that has one. `SOE`
and `SOT` are greyed out for a machine whose `EXC` or `TOR` block is empty, and
an INJECTOR, LINK or DCTL instance whose `OBS` block is empty is greyed out in
the name list for the same reason. DYNGRAPH's console does the same — it hides
such DCTLs from its prompt (`stepss-dyngraph/src/selec_observ.f90:399-408`) —
and a replay file naming one would loop to EOF. `Add` is disabled unless the
current row resolves to a complete selection. `Plot` is disabled while Selected
is empty, so the "no `.plt` written" case cannot arise from the UI.

Duplicate selections are allowed. The console permits them and they produce
duplicate columns, which is occasionally what someone wants; suppressing them
would need an equality rule the format does not imply.

The dialog opens with an empty Selected list every time. Carrying a selection
across invocations was considered and rejected as unearned complexity: names
would have to be revalidated against a freshly loaded trajectory and silently
dropped when absent.

The running list is itself the improvement over the Intel dialog, which took
at most one observable per category per round and reopened itself, so plotting
five bus voltages meant five round-trips.

## Running DYNGRAPH

Both invocations run through `DefaultExecutor` with a `PumpStreamHandler`
wired to `ByteArrayOutputStream`s, so stdout and stderr are captured rather
than inherited. The precedent for capture-and-check is the Helios run
(`RamsesUI.java:4059-4060`, a dedicated stderr buffer feeding
`reportHeliosExitStatus`) and the simulation run (`RamsesUI.java:3521`). It is
**not** `viewCurvesButtonActionPerformed`, which constructs a no-arg
`PumpStreamHandler` inheriting the JVM's streams and captures nothing
(`RamsesUI.java:3249`); that call is the precedent for the async structure
only.

`--list` needs stdout captured to a buffer; DYNGRAPH's chatty per-category
counts go to unit 0 (stderr), so stdout carries the index alone and needs no
filtering.

**Neither call runs on the EDT.** `get_observ_name` rewinds the trajectory
several times and the plot run reads the whole time series, so both scale with
file size and would freeze the UI. `--list` runs in a `SwingWorker`, with a
wait cursor, and the picker opens from its `done()` on the EDT. The plot run
uses the async `DefaultExecuteResultHandler` the repo already uses elsewhere,
re-enabling buttons or raising the error dialog on the EDT from its callback.

The plot run is not interactive under `-t`, so it gets no terminal window. Both
result buttons are **disabled before the run starts**, not merely left alone —
after a previous successful extraction they are enabled, and a failed re-run
must not leave them pointing at the stale `tempGnupOut.plt`/`.cur` that DYNGRAPH
may have truncated or half-written. On success they are re-enabled with no
dialog, exactly as today.

`PlatformLauncher.runInTerminal` is not touched, but it does become unused —
`RamsesUI.java:3278` is its only call site in the repo. It stays in place as one
of the platform helper family (`openTerminal`, `openInEditor`,
`openFileManager`, `findOnPath`, `killByName`); removing it is out of scope.

### Failure behaviour

Every failure is a modal dialog leaving no partial state:

| Condition | Behaviour |
|---|---|
| `dyngraph` missing or absent from disk | Existing "Executable not found!" dialog, unchanged |
| `--list` exits non-zero, stdout empty | "The bundled DYNGRAPH does not support `--list`", plus the captured stderr — this is the too-old case |
| `--list` exits non-zero otherwise | Captured stderr; picker never opens |
| Exits zero but line 1 is not a `DYNGRAPH-INDEX` header | Refuse; report the first line verbatim |
| `DYNGRAPH-INDEX` version above 1 | Refuse explicitly; do not parse on |
| Count mismatch, truncation, missing `END`, unexpected tag | Parse error with the offending line number |
| Every category empty | Say so; do not open an empty picker |
| `-t` run exits non-zero | Captured stderr; result buttons stay disabled |

One failure mode is **not** covered and cannot be: a replay file that desyncs
mid-stream makes the console selector re-prompt rather than abort, so DYNGRAPH
can exit zero having plotted the wrong curves. The charset rule above is what
keeps that from happening; there is no runtime check for it. Pinning the
round-trip in the harness is the mitigation.

There is no fallback to the old `-a<trj>` terminal path. The bundled DYNGRAPH
is pinned at 1.2.0 and `PayloadManifestCheck` fails the build when the payload
disagrees with `versions.properties`, so a fallback would be unreachable code
carrying a second invocation to maintain.

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
  count-driven and never scans for a delimiter;
- an **empty name line**, which `trim()` emits legitimately for an all-blank
  name and which count-driven parsing must consume as a name rather than treat
  as the replay file's terminator.

The harness feeds scripted selections through `ReplayFile` and compares the
result against an expected replay stream, also embedded, pinning the round-trip
the way DYNGRAPH's own smoke gate pins its goldens. Malformed-input cases assert
the thrown message names the right line.

Manual acceptance on all three platforms — pick, plot, view curves — is a plan
task, as in the toolchain and custom-model-compilation plans. It is the only
way to cover the dialog itself, which the harness deliberately does not touch.
Nothing runs the harness automatically; like `tools/compile-harness.sh`, it is
run by hand and by the plan's task list, not by `release.yml`.

### What no automated gate covers

The UI omits `-eps`, and DYNGRAPH's smoke gate always passes it
(`stepss-dyngraph/tools/smoke_gate.sh:90`) because without it `caption.f90`
emits `set terminal windows` on Windows and an empty terminal line elsewhere,
which would make the `.plt` golden platform-specific. So the `.plt` this UI
produces is precisely the branch no golden pins.

This is not a regression — today's `Extract Curves` omits `-eps` too — and it
is the right flag choice, since `-eps` would export an EPS file instead of
leaving a `.plt` that `viewCurvesButton` can open in a gnuplot window. But the
gap is real and the 2026-08-07 design's claim that this invocation is "byte for
byte" the gated configuration is wrong: the gate passes no `-c`, always `-eps`,
and `-o` as a separate argument.

A follow-up in `stepss-dyngraph` should add a no-`-eps` gate case, diffing the
`.plt` with the terminal line filtered so one golden serves all three
platforms. It is a separate repository, review cycle and release, so it does
not block this work.

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
| `README.md:77` | The DYNGRAPH row reads "yes (console)" for all three platforms; the console program is still what ships, but it is no longer what the user meets |
| `README.md:83` | The paragraph saying DYNGRAPH "opens in a terminal window" and "the console prompts replace it everywhere" — both become false |

`README.md:13` ("Extract Curves launches the bundled DYNGRAPH viewer on saved
output trajectories") stays true and needs no edit.

No `.form` change. `runDyngraphButton` keeps its initial state, and the picker
is a hand-written `JDialog` rather than a NetBeans form, because its controls
are populated from parsed data and its layout does not vary.

## Constraints

- `javac.source` and `javac.target` are **11**
  (`nbproject/project.properties:54-55`), so no records, no switch expressions
  and no text blocks. `var` compiles at this level — it arrived in Java 10 —
  but the repository does not use it and neither does this work; match the
  explicit generics the platform package uses. The
  `custom-model-compilation` plan lists `var` alongside the genuine
  Java-11 exclusions, which is a style rule stated as a compiler limit.
- `RamsesUI.form` is the source of truth for generated `initComponents()`.
  This work requires no change to it; do not edit it.
- This repo is a submodule of the `stepss` umbrella. Commit here, push, then
  bump the pointer in the umbrella.

## Accepted costs

- Six new classes and a wrapper script for what the 2026-08-07 design described
  as one class. The split is what makes parsing and emission verifiable without
  a display, given that this repository has no unit-test framework.
- Three replay keywords (`I`, `T`, `D`) hardcoded in `ReplayFile`, because the
  index does not carry them.
- The dialog itself is covered only by manual acceptance.
- The `.plt` the UI produces is the no-`-eps` branch, which no upstream golden
  pins. Unchanged from today, with an upstream follow-up noted above.
- A charset or ordering mistake in the replay file fails silently — DYNGRAPH
  re-prompts rather than aborting and can still exit zero. The harness
  round-trip is the only guard.
- Someone running DYNGRAPH standalone, outside `stepss-java-ui`, still gets
  console mode. Unchanged from today on every platform.
