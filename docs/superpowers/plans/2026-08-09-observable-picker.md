# Observable Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give "Extract Curves" a mouse-driven observable picker on all three platforms, replacing the terminal window it opens today, by consuming `dyngraph --list` and driving DYNGRAPH through a replay file.

**Architecture:** A new `my.ramses.dyngraph` package, mirroring `my.ramses.compile`: `ObservableIndex` parses the count-prefixed `--list` index, `Selection` and `ReplayFile` turn picks into the console keyword stream, `ObservablePicker` is a hand-written modal `JDialog`, and `DyngraphRunner` owns both process invocations off the EDT. `RamsesUI.runDyngraphButtonActionPerformed` shrinks to orchestration: list, parse, show, write, plot, enable buttons. `PickerHarness` is the headless verification `main()` with the fixture embedded as string literals; it deliberately does not depend on `DyngraphRunner`, so `build/classes` alone is enough to run it — no `dist/lib`, no extracted payload.

**Tech Stack:** Java 11 (source/target 11), Swing, Apache Commons Exec 1.3, Apache Ant (NetBeans project), DYNGRAPH v1.2.0 (pinned payload).

## Global Constraints

- Java source/target is **11** (`nbproject/project.properties:54-55`): no records, no switch expressions, no text blocks. `var` compiles at this level but the repository does not use it — write explicit generics (`new HashMap<String, List<String>>()`).
- **No unit-test framework exists in this repo and none is being added.** Verification is `PickerHarness`, a headless `main()` run by `tools/picker-harness.sh`, exactly the `CompileHarness` idiom: `PASS`/`FAIL` lines, exit non-zero on any failure. The dialog itself is covered only by manual acceptance (Task 8).
- Charset is **ISO-8859-1**, explicitly, on both the `--list` read and the `sel.cmd` write. DYNGRAPH has no encoding concept; names are raw bytes compared byte-for-byte, and a UTF-8 round trip silently rewrites any byte ≥ 0x80.
- The trajectory contract is `<temp>/output.trj` (already established at `RamsesUI.java:3134`); the replay file is `<temp>/sel.cmd`, left behind after the run; the output base is the **fixed, absolute** `<temp>/tempGnupOut` — not a free parameter, because `viewCurvesButton` opens `tempGnupOut.plt` by name (`RamsesUI.java:3245`) and `saveCurrentCurveButton` string-replaces the absolute `tempGnupOut.cur` path inside the `.plt` (`RamsesUI.java:3157,3163`).
- Every argument carrying a path is added with `CommandLine.addArgument(value, false)` — the default handling re-quotes a token containing spaces and the quotes reach the child.
- **Neither dyngraph call runs on the EDT**: `--list` in a `SwingWorker` with a wait cursor, the `-t` plot run through an async `DefaultExecuteResultHandler`; all dialogs and button changes hop back with `invokeLater`.
- Detecting an old DYNGRAPH is **exit-status-first, not header-first**: the old binary's prompt goes to stderr (`main.f90` sets `log=0`), stdout stays empty, and the EOF on stdin exits non-zero. The friendly "does not support `--list`" wording belongs on the non-zero-exit-with-empty-stdout path; the header check is a backstop for a zero-exit program that printed something unexpected.
- **Names are never left-trimmed.** DYNGRAPH emits them Fortran-`trim()`-ed (trailing blanks only); a leading blank is part of the name and dropping it breaks the round trip through `-t`.
- The replay keywords for the three categories without `TYPES` blocks are `INJ`→`I`, `LINK`→`T`, `DCTL`→`D` (`stepss-dyngraph/src/selec_observ.f90:329`, `:360`, `:393`; demonstrated by `tests/nested.cmd`). They are hardcoded in `ReplayFile` and nowhere else — accepted coupling per the spec.
- Replay grammar, pinned by `stepss-dyngraph/tests/smoke.cmd` and `tests/nested.cmd`: trajectory path on line 1, then keyword, name, and a third sub-observable line when the keyword is `SOE`, `SOT`, `I`, `T` or `D` and for no other keyword; terminated by a blank line and `S`.
- `Selection.label()` reproduces the `desc_obs` formats verbatim (`selec_observ.f90:92,123,157,203,299,316,322,355,388,432`); `SOE`/`SOT` render as `excit control:` / `torque control:`, not their `TYPES` labels.
- `RamsesUI.form` is **not edited**. The picker is a hand-written `JDialog`; `runDyngraphButton` keeps its initial state, and its tooltip (form line reflected at `RamsesUI.java:1817`) already promises a selection dialog.
- The plot run passes **no `-eps`** (it would export an EPS instead of leaving a `.plt` for `viewCurvesButton`) and gets **no terminal window**. There is no fallback to the old `-a<trj>` terminal path. `PlatformLauncher.runInTerminal` is **not touched, and becomes unused**: `RamsesUI.java:3278` is its only call site in the repo, and Task 7 deletes it. Leave the method in place — it is one of a family of platform helpers (`openTerminal`, `openInEditor`, `openFileManager`, `findOnPath`, `killByName`) and removing it is out of scope here. Do not "fix" the resulting unused-method warning.
- **Never use compound git commands** (`&&`, `||`, `;`). Run each git command separately, and `cd` in its own command first. This repo is a submodule of the stepss umbrella: commit here; pushing and the umbrella pointer bump are the user's call.
- Build commands: `ant jar` is the full build (its `fetch-payloads` needs network and an authenticated `gh` the first time; later builds hit `payload-cache/`). `ant compile` exists (`nbproject/build-impl.xml:1137`) and produces `build/classes`, which is all the harness needs — use it for the red/green loops. Do not invent other targets.

---

## File Structure

**Create:**

| File | Responsibility |
|---|---|
| `src/my/ramses/dyngraph/ObservableIndex.java` | Pure parse of the `--list` index: categories, type tables, instance names, per-instance sub-lists. No Swing, no I/O. |
| `src/my/ramses/dyngraph/Selection.java` | One picked observable; composes its own `desc_obs`-format display label. |
| `src/my/ramses/dyngraph/ReplayFile.java` | Selections → the console keyword stream. Pure text, plus the ISO-8859-1 write. |
| `src/my/ramses/dyngraph/ObservablePicker.java` | The modal `JDialog`. No parsing, no label composition, no process calls. |
| `src/my/ramses/dyngraph/DyngraphRunner.java` | Both invocations, captured output, off the EDT. |
| `src/my/ramses/dyngraph/PickerHarness.java` | Headless `main()`: fixture → parse → scripted selections → emit → compare. |
| `tools/picker-harness.sh` | Wrapper running the harness against `build/classes`. |
| `docs/superpowers/plans/picker-acceptance-results.md` | Task 8's acceptance record. |

**Modify:**

| File | Change |
|---|---|
| `src/my/ramses/RamsesUI.java:3263-3282` | `runDyngraphButtonActionPerformed` becomes orchestration; two private helpers added after it; five imports added at `:35`. |
| `README.md:77` | The DYNGRAPH row drops "(console)" — the console program still ships, but it is no longer what the user meets. |
| `README.md:83` | The "opens in a terminal window" / "the console prompts replace it everywhere" paragraph — both claims become false. |

`README.md:13` ("Extract Curves launches the bundled DYNGRAPH viewer on saved output trajectories") stays true and is not edited.

---

### Task 1: PickerHarness skeleton, fixture and wrapper script

The test vehicle comes first. It compiles against a deliberately failing `ObservableIndex` stub, so the very first harness run fails with a named message — that red state is this task's deliverable.

**Files:**
- Create: `src/my/ramses/dyngraph/PickerHarness.java`
- Create: `src/my/ramses/dyngraph/ObservableIndex.java` (stub; Task 2 replaces it)
- Create: `tools/picker-harness.sh`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `PickerHarness.main(String[] args)` — exits 0 iff every check passed.
  - `PickerHarness.fixture()` returning `String` — the embedded index, newline-terminated; Task 6's dialog probe reuses it.
  - The `expect`/`pass`/`fail`/`join` check idiom every later task extends.
  - `ObservableIndex.parse(String text)` throwing `IOException` — stub signature only; the real contract lands in Task 2.

- [ ] **Step 1: Write the `ObservableIndex` stub**

```java
package my.ramses.dyngraph;

import java.io.IOException;

/**
 * Stub: the real parser lands with the harness checks that pin it. Kept
 * compiling so tools/picker-harness.sh can run - and fail, by name - before
 * the implementation exists.
 */
public final class ObservableIndex {

    private ObservableIndex() {
    }

    public static ObservableIndex parse(String text) throws IOException {
        throw new IOException("ObservableIndex.parse is not implemented yet");
    }
}
```

- [ ] **Step 2: Write `PickerHarness` with the fixture and the first check**

The fixture is a hand-extended `stepss-dyngraph/tests/golden/list.txt`, embedded as `String[]` literals joined with `\n` — **not** an external data file. The format's one trap is a leading blank in a name, which is invisible in a text file, silently stripped by many editors, and exposed to the CRLF normalisation that forced `.gitattributes` LF pins on DYNGRAPH's own goldens; in a quoted Java literal it is explicit and cannot be lost. The released golden is extended rather than reused as-is because it carries exactly one injector, one link, one DCTL, one exciter and one torque controller, each with a single observable — it cannot distinguish a correct per-instance parser from one that pools sub-lists per category.

```java
package my.ramses.dyngraph;

/**
 * Headless checks for the observable-picker pipeline: fixture -> parse ->
 * scripted selections -> emit -> compare. This repository has no unit-test
 * framework and is not gaining one, so this main() is where parsing and
 * replay emission are pinned; the dialog itself is covered by manual
 * acceptance, and DyngraphRunner deliberately stays out of here - the
 * harness launches no process and needs no extracted payload, so it runs on
 * a bare checkout with build/classes present.
 *
 * <p>The fixture is a hand-extended version of stepss-dyngraph's
 * tests/golden/list.txt, embedded as string literals rather than kept as a
 * data file: the format's one trap is a leading blank in a name, which is
 * invisible in a text file, silently stripped by many editors, and exposed
 * to CRLF normalisation. In a quoted Java literal it is explicit and cannot
 * be lost. (Java 11: no text blocks, hence the joined arrays.)
 *
 * <p>The golden is extended rather than reused as-is because it carries
 * exactly one injector, one link, one DCTL, one exciter and one torque
 * controller, each with a single observable - it cannot distinguish a
 * correct per-instance parser from one that pools sub-lists per category.
 */
public final class PickerHarness {

    /**
     * The extended index. Deliberate traps, in order of appearance: a bus
     * named " LEADBUS" (the leading blank is part of the name); a bus named
     * "END" (an instance name colliding with the terminator tag); an empty
     * LOAD name (what Fortran trim() emits for an all-blank name); a branch
     * named "S" (colliding with the stop keyword); GEN2 with "EXC 0" and a
     * non-empty TOR; INJ2 whose OBS list differs from INJ1's; DCTL2 with
     * "OBS 0"; and a DCTL observable named "ST", which is also the sync
     * keyword for mechanical torque.
     */
    private static final String[] FIXTURE_LINES = {
        "DYNGRAPH-INDEX 1",
        "TYPES BUS 2",
        "BM voltage magnitude (pu)",
        "BA voltage phase angle (deg)",
        "TYPES SHUNT 1",
        "HQ reactive power produced (Mvar)",
        "TYPES LOAD 2",
        "LP active power consumed (MW)",
        "LQ reactive power consumed (Mvar)",
        "TYPES BRANCH 6",
        "RPF P (MW) entering at FROM end",
        "RQF Q (Mvar) entering at FROM end",
        "RPT P (MW) entering at TO end",
        "RQT Q (Mvar) entering at TO end",
        "RRM magnitude of transformer ratio",
        "RRA phase angle of transformer ratio (deg)",
        "TYPES SYNC 17",
        "SP active power produced (MW)",
        "SQ reactive power produced (Mvar)",
        "SA rotor angle wrt COI (deg)",
        "SS rotor speed (pu)",
        "SFW flux in field winding (pu mach. base)",
        "SDD flux in d1 damper (pu mach. base)",
        "SQD flux in q1 damper (pu mach. base)",
        "SQW flux in q2 winding (pu mach. base)",
        "SFC field current (pu)",
        "SFV field voltage (pu)",
        "ST mechanical torque (pu)",
        "SET electromagnetic torque (pu mach. base)",
        "SSC speed of COI reference (pu)",
        "SEQ q-axis internal voltage (pu)",
        "SED d-axis internal voltage (pu)",
        "SOE observable of excitation controller",
        "SOT observable of torque controller",
        "BUS 4",
        "BUS1",
        "BUS2",
        " LEADBUS",
        "END",
        "SHUNT 1",
        "SHUNT1",
        "LOAD 2",
        "LOAD1",
        "",
        "BRANCH 2",
        "BR1-2",
        "S",
        "SYNC 2",
        "GEN1",
        "EXC 1",
        "VF",
        "TOR 1",
        "TM",
        "GEN2",
        "EXC 0",
        "TOR 1",
        "Pm",
        "INJ 2",
        "INJ1",
        "OBS 1",
        "P",
        "INJ2",
        "OBS 2",
        "Q",
        "omega",
        "LINK 1",
        "LINK1",
        "OBS 1",
        "I1",
        "DCTL 2",
        "DCTL1",
        "OBS 1",
        "ST",
        "DCTL2",
        "OBS 0",
        "END"
    };

    private static int failures = 0;

    private PickerHarness() {
    }

    /** The fixture text, exposed so a probe can drive the dialog on it. */
    public static String fixture() {
        return join(FIXTURE_LINES);
    }

    public static void main(String[] args) {
        checkFixtureParses();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkFixtureParses() {
        try {
            ObservableIndex.parse(fixture());
            pass("the extended fixture parses");
        } catch (Exception ex) {
            fail("the extended fixture parses: threw " + ex);
        }
    }

    private static String join(String[] lines) {
        return String.join("\n", lines) + "\n";
    }

    private static void expect(String what, Object want, Object got) {
        // Null-safe on both sides, like CompileHarness: a null expectation
        // must read as a comparison, not as an NPE inside the harness itself.
        if (want == null ? got == null : want.equals(got)) {
            pass(what);
        } else {
            fail(what + ": wanted <" + want + "> got <" + got + ">");
        }
    }

    private static void pass(String what) {
        System.out.println("PASS  " + what);
    }

    private static void fail(String what) {
        failures++;
        System.out.println("FAIL  " + what);
    }
}
```

(`expect` is unused until Task 2 and will draw an unused warning, not an error; it is the shared idiom every later task's checks call.)

- [ ] **Step 3: Write `tools/picker-harness.sh`**

```bash
#!/usr/bin/env bash
# Runs the headless observable-picker checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Unlike tools/compile-harness.sh, dist/lib is NOT on the classpath: PickerHarness
# deliberately depends on nothing that launches a process or references
# commons-exec types, so build/classes alone is enough to load it.
#
# 'ant compile' still needs the payloads: build.xml makes -pre-compile depend on
# stage-payloads, so fetching and staging happen BEFORE javac. On a checkout with
# neither a warm payload-cache/ nor network plus authenticated gh, the build dies
# before build/classes is created and this script has nothing to run. A warm
# payload-cache/ is enough - it does not need the network again.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -cp build/classes my.ramses.dyngraph.PickerHarness
```

Then make it executable:
```bash
chmod +x tools/picker-harness.sh
```

- [ ] **Step 4: Build and watch the first check fail**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant compile
```
(The first build fetches payloads — network plus authenticated `gh`; with a warm `payload-cache/` it is offline.)

```bash
tools/picker-harness.sh
```
Expected, exactly:
```
FAIL  the extended fixture parses: threw java.io.IOException: ObservableIndex.parse is not implemented yet
1 CHECK(S) FAILED
```
and exit status 1 (`echo $?` prints `1`).

- [ ] **Step 5: Commit**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
git add src/my/ramses/dyngraph/PickerHarness.java src/my/ramses/dyngraph/ObservableIndex.java tools/picker-harness.sh
```
```bash
git commit -m "Add the observable-picker harness and its fixture

PickerHarness is the test vehicle for the new my.ramses.dyngraph
package: this repo has no unit-test framework, so parsing and replay
emission are pinned by a headless main() run from
tools/picker-harness.sh, like the compile pipeline before it. It
deliberately depends on no process launches and no payload, so it runs
on a bare checkout with build/classes present.

The fixture extends dyngraph's released golden index with the cases
the golden cannot distinguish: per-instance sub-lists that differ
between two injectors, a machine with EXC 0, names colliding with the
END and S keywords, an all-blank name, and a leading blank - embedded
as Java string literals because a leading blank in a text file is
invisible and easily stripped.

The one check fails by design: ObservableIndex.parse is a stub until
the next commit."
```

---

### Task 2: ObservableIndex — the parser

**Files:**
- Modify: `src/my/ramses/dyngraph/ObservableIndex.java` (replace the Task 1 stub)
- Modify: `src/my/ramses/dyngraph/PickerHarness.java`

**Interfaces:**
- Consumes: `PickerHarness.fixture()`, the `expect`/`pass`/`fail` idiom (Task 1).
- Produces:
  - `ObservableIndex.parse(String text)` returning `ObservableIndex`, throwing `IOException` with the offending 1-based line number in the message.
  - `ObservableIndex.CATEGORIES` — `List<String>` of the eight tags `BUS, SHUNT, LOAD, BRANCH, SYNC, INJ, LINK, DCTL`.
  - `ObservableIndex.TYPED_CATEGORIES` — the five with `TYPES` blocks.
  - `ObservableIndex.types(String category)` returning `List<ObservableIndex.TypeEntry>` (`TypeEntry` has public final `String keyword`, `String label`).
  - `ObservableIndex.instances(String category)` returning `List<ObservableIndex.Instance>` (`Instance` has public final `String name` and `List<String> exc`, `tor`, `obs`).
  - `ObservableIndex.isEmpty()` returning `boolean` — true when every category has zero instances.

- [ ] **Step 1: Replace the stub with the full model, parse still throwing**

Overwrite `src/my/ramses/dyngraph/ObservableIndex.java` with the class below. Everything is final except `parse`, whose one-line body Step 4 replaces — this keeps the harness compiling through the red phase.

```java
package my.ramses.dyngraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The parsed model of {@code dyngraph --list} output: categories, type
 * tables, instance names and per-instance sub-lists. No Swing, no I/O, no
 * process calls, so it can be pinned headlessly by {@link PickerHarness}.
 *
 * <p>The format is documented in stepss-dyngraph's README and pinned by its
 * {@code tests/golden/list.txt}: line-oriented and count-prefixed, every
 * block a {@code <TAG> <count>} header followed by exactly that many lines.
 * The parser therefore never scans for a delimiter - an instance
 * legitimately named {@code END} or {@code S} must not terminate anything.
 *
 * <p>Names are taken verbatim. DYNGRAPH emits them Fortran-{@code trim()}-ed,
 * which strips trailing blanks only; a leading blank is part of the name, and
 * the console selector's comparison ({@code busname(k)==string18}) pads the
 * shorter operand with trailing blanks - so a trailing blank is insignificant
 * but a leading one is not. Left-trimming here would break the round trip
 * back through {@code -t}.
 */
public final class ObservableIndex {

    /** The one index format version this parser understands. */
    public static final int FORMAT_VERSION = 1;

    private static final String HEADER_PREFIX = "DYNGRAPH-INDEX ";

    /** Index tags of the eight instance categories, in emission order. */
    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "BUS", "SHUNT", "LOAD", "BRANCH", "SYNC", "INJ", "LINK", "DCTL"));

    /** The five categories whose types are fixed by the trajectory layout and carried in TYPES blocks. */
    public static final List<String> TYPED_CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "BUS", "SHUNT", "LOAD", "BRANCH", "SYNC"));

    /** One {@code <keyword> <label>} pair from a TYPES block. */
    public static final class TypeEntry {
        /** The replay keyword, echoed back verbatim in the command file (e.g. {@code BM}). */
        public final String keyword;
        /** The human-readable label the dropdown shows (e.g. {@code voltage magnitude (pu)}). */
        public final String label;

        TypeEntry(String keyword, String label) {
            this.keyword = keyword;
            this.label = label;
        }
    }

    /**
     * One instance, with its own sub-lists. Sub-lists are keyed by instance,
     * never pooled by category: injector, link, DCTL, exciter and
     * torque-controller observables are defined by the model attached to
     * each instance, so two injectors in the same network routinely expose
     * different observables. An empty sub-list is legitimate - a machine
     * with no excitation controller has {@code EXC 0}.
     */
    public static final class Instance {
        /** The name, verbatim: a leading blank is part of it. May be empty (an all-blank name). */
        public final String name;
        /** Exciter observables (SYNC only; empty for every other category). */
        public final List<String> exc;
        /** Torque-controller observables (SYNC only). */
        public final List<String> tor;
        /** Instance observables (INJ, LINK and DCTL only). */
        public final List<String> obs;

        Instance(String name, List<String> exc, List<String> tor, List<String> obs) {
            this.name = name;
            this.exc = Collections.unmodifiableList(exc);
            this.tor = Collections.unmodifiableList(tor);
            this.obs = Collections.unmodifiableList(obs);
        }
    }

    private final Map<String, List<TypeEntry>> types;
    private final Map<String, List<Instance>> instances;

    private ObservableIndex(Map<String, List<TypeEntry>> types,
                            Map<String, List<Instance>> instances) {
        this.types = types;
        this.instances = instances;
    }

    /** The TYPES table of a category, in emission order; empty for INJ/LINK/DCTL. */
    public List<TypeEntry> types(String category) {
        List<TypeEntry> found = types.get(category);
        return found == null ? Collections.<TypeEntry>emptyList() : found;
    }

    /** The instances of a category, in emission order; empty when absent. */
    public List<Instance> instances(String category) {
        List<Instance> found = instances.get(category);
        return found == null ? Collections.<Instance>emptyList() : found;
    }

    /** True when every category carries zero instances - nothing to pick. */
    public boolean isEmpty() {
        for (String category : CATEGORIES) {
            if (!instances(category).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses {@code --list} output.
     *
     * @throws IOException on any malformed input, with the offending
     *         (1-based) line number in the message. Refuses any version
     *         other than {@link #FORMAT_VERSION}: a raised version marks a
     *         deliberately incompatible format, so parsing on would be a
     *         bug.
     */
    public static ObservableIndex parse(String text) throws IOException {
        throw new IOException("ObservableIndex.parse is not implemented yet");
    }
}
```

- [ ] **Step 2: Add the parse checks to `PickerHarness`**

In `main`, after `checkFixtureParses();`, add:

```java
        checkTypeTables();
        checkInstanceNames();
        checkPerInstanceSubLists();
        checkEmptyIndexDetection();
        checkVersionRefused();
        checkForeignHeaderRefused();
        checkTruncationNamesTheLine();
        checkMissingEndNamed();
        checkUnexpectedTagNamed();
        checkMisplacedSubListNamed();
        checkUnreadableCountNamed();
```

and add the methods (below `checkFixtureParses`):

```java
    private static ObservableIndex parsedFixture() {
        try {
            return ObservableIndex.parse(fixture());
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    private static void checkTypeTables() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("type tables: fixture did not parse");
            return;
        }
        expect("BUS type count", 2, index.types("BUS").size());
        expect("SHUNT type count", 1, index.types("SHUNT").size());
        expect("LOAD type count", 2, index.types("LOAD").size());
        expect("BRANCH type count", 6, index.types("BRANCH").size());
        expect("SYNC type count", 17, index.types("SYNC").size());
        expect("first BUS keyword", "BM", index.types("BUS").get(0).keyword);
        expect("first BUS label", "voltage magnitude (pu)", index.types("BUS").get(0).label);
        expect("a label may contain spaces and parentheses", "P (MW) entering at FROM end",
                index.types("BRANCH").get(0).label);
        expect("SOE sits at SYNC entry 16", "SOE", index.types("SYNC").get(15).keyword);
        expect("SOT sits at SYNC entry 17", "SOT", index.types("SYNC").get(16).keyword);
        expect("INJ has no TYPES table", 0, index.types("INJ").size());
    }

    private static void checkInstanceNames() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("instance names: fixture did not parse");
            return;
        }
        expect("bus count", 4, index.instances("BUS").size());
        expect("first bus", "BUS1", index.instances("BUS").get(0).name);
        expect("a leading blank is part of the name", " LEADBUS",
                index.instances("BUS").get(2).name);
        expect("a bus named END is a name, not a terminator", "END",
                index.instances("BUS").get(3).name);
        expect("the block after the END-named bus still parses", "SHUNT1",
                index.instances("SHUNT").get(0).name);
        expect("an empty line is a legitimate all-blank name", "",
                index.instances("LOAD").get(1).name);
        expect("a branch named S is a name, not the stop keyword", "S",
                index.instances("BRANCH").get(1).name);
    }

    private static void checkPerInstanceSubLists() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("per-instance sub-lists: fixture did not parse");
            return;
        }
        ObservableIndex.Instance gen1 = index.instances("SYNC").get(0);
        ObservableIndex.Instance gen2 = index.instances("SYNC").get(1);
        expect("GEN1 exciter list", "[VF]", gen1.exc.toString());
        expect("GEN1 torque list", "[TM]", gen1.tor.toString());
        expect("EXC 0 parses to an empty list", 0, gen2.exc.size());
        expect("GEN2 keeps its own torque list", "[Pm]", gen2.tor.toString());
        ObservableIndex.Instance inj1 = index.instances("INJ").get(0);
        ObservableIndex.Instance inj2 = index.instances("INJ").get(1);
        expect("INJ1 observables", "[P]", inj1.obs.toString());
        expect("INJ2 observables differ from INJ1's - sub-lists are per instance, never pooled",
                "[Q, omega]", inj2.obs.toString());
        expect("LINK1 observables", "[I1]", index.instances("LINK").get(0).obs.toString());
        expect("DCTL1 observables", "[ST]", index.instances("DCTL").get(0).obs.toString());
        expect("OBS 0 parses to an empty list", 0, index.instances("DCTL").get(1).obs.size());
    }

    private static void checkEmptyIndexDetection() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("empty-index detection: fixture did not parse");
            return;
        }
        expect("the fixture is not empty", false, index.isEmpty());
        try {
            expect("an index with no instances reports empty", true,
                    ObservableIndex.parse("DYNGRAPH-INDEX 1\nEND\n").isEmpty());
        } catch (java.io.IOException ex) {
            fail("an index with no instances reports empty: threw " + ex);
        }
    }

    private static void checkVersionRefused() {
        expectParseError("a higher DYNGRAPH-INDEX version is refused",
                "DYNGRAPH-INDEX 2\nEND\n", "version 2");
    }

    private static void checkForeignHeaderRefused() {
        expectParseError("a non-index first line is reported verbatim",
                "some entirely foreign first line\n", "some entirely foreign first line");
    }

    private static void checkTruncationNamesTheLine() {
        expectParseError("a count overrunning the stream names the line",
                "DYNGRAPH-INDEX 1\nBUS 3\nBUS1\nBUS2", "line 5: unexpected end of input");
    }

    private static void checkMissingEndNamed() {
        expectParseError("a stream without END says so",
                "DYNGRAPH-INDEX 1\nBUS 1\nBUS1\n", "missing END");
    }

    private static void checkUnexpectedTagNamed() {
        expectParseError("an unexpected tag names its line",
                "DYNGRAPH-INDEX 1\nBOGUS 1\nA\nEND\n", "line 2: unexpected tag");
    }

    private static void checkMisplacedSubListNamed() {
        expectParseError("an INJ name without its OBS block names the expectation",
                "DYNGRAPH-INDEX 1\nINJ 1\nINJ1\nEND\n", "expected 'OBS <count>' after 'INJ1'");
    }

    private static void checkUnreadableCountNamed() {
        expectParseError("an unreadable count names its header line",
                "DYNGRAPH-INDEX 1\nBUS x\nEND\n", "line 2: unreadable count");
    }

    private static void expectParseError(String what, String text, String mustContain) {
        try {
            ObservableIndex.parse(text);
            fail(what + ": no exception");
        } catch (java.io.IOException ex) {
            String message = String.valueOf(ex.getMessage());
            if (message.contains(mustContain)) {
                pass(what);
            } else {
                fail(what + ": message <" + message + "> does not mention <" + mustContain + ">");
            }
        }
    }
```

- [ ] **Step 3: Run the harness and watch every new check fail**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant compile
```
```bash
tools/picker-harness.sh
```
Expected: exit 1 with `12 CHECK(S) FAILED`, including exactly these lines (among the others):
```
FAIL  the extended fixture parses: threw java.io.IOException: ObservableIndex.parse is not implemented yet
FAIL  type tables: fixture did not parse
FAIL  per-instance sub-lists: fixture did not parse
FAIL  a higher DYNGRAPH-INDEX version is refused: message <ObservableIndex.parse is not implemented yet> does not mention <version 2>
```

- [ ] **Step 4: Implement the parser**

In `ObservableIndex.java`, replace the throwing `parse` body with the real implementation and add the four private members after it:

```java
    public static ObservableIndex parse(String text) throws IOException {
        Cursor cursor = new Cursor(text);
        String header = cursor.next("the DYNGRAPH-INDEX header");
        if (!header.startsWith(HEADER_PREFIX)) {
            throw new IOException("line 1: not a DYNGRAPH-INDEX header. The first line was: \""
                    + header + "\"");
        }
        int version;
        try {
            version = Integer.parseInt(header.substring(HEADER_PREFIX.length()).trim());
        } catch (NumberFormatException ex) {
            throw new IOException("line 1: unreadable DYNGRAPH-INDEX version in \"" + header + "\"");
        }
        if (version != FORMAT_VERSION) {
            throw new IOException("line 1: DYNGRAPH-INDEX version " + version
                    + "; this STEPSS understands version " + FORMAT_VERSION
                    + " only. A raised version marks a deliberately incompatible format,"
                    + " so refusing beats guessing.");
        }

        Map<String, List<TypeEntry>> types = new HashMap<String, List<TypeEntry>>();
        Map<String, List<Instance>> instances = new HashMap<String, List<Instance>>();
        while (true) {
            String line = cursor.next("a block header or END");
            if (line.equals("END")) {
                break;
            }
            if (line.isEmpty() && !cursor.hasNext()) {
                throw cursor.error("missing END: the stream ended without one");
            }
            if (line.startsWith("TYPES ")) {
                parseTypesBlock(cursor, line, types);
            } else {
                parseInstanceBlock(cursor, line, instances);
            }
        }
        while (cursor.hasNext()) {
            String trailing = cursor.next("nothing (the stream ended at END)");
            if (!trailing.isEmpty()) {
                throw cursor.error("content after END: \"" + trailing + "\"");
            }
        }
        return new ObservableIndex(types, instances);
    }

    private static void parseTypesBlock(Cursor cursor, String header,
            Map<String, List<TypeEntry>> types) throws IOException {
        String rest = header.substring("TYPES ".length());
        int space = rest.indexOf(' ');
        if (space <= 0) {
            throw cursor.error("malformed TYPES header \"" + header
                    + "\": expected \"TYPES <CATEGORY> <count>\"");
        }
        String category = rest.substring(0, space);
        if (!TYPED_CATEGORIES.contains(category)) {
            throw cursor.error("TYPES block for unknown category '" + category + "'");
        }
        int count = cursor.count(rest.substring(space + 1), header);
        List<TypeEntry> table = types.get(category);
        if (table == null) {
            table = new ArrayList<TypeEntry>();
            types.put(category, table);
        }
        for (int i = 0; i < count; i++) {
            String entry = cursor.next("entry " + (i + 1) + " of " + count
                    + " in the TYPES " + category + " block");
            int keywordEnd = entry.indexOf(' ');
            if (keywordEnd <= 0) {
                throw cursor.error("malformed TYPES entry \"" + entry
                        + "\": expected \"<keyword> <label>\"");
            }
            table.add(new TypeEntry(entry.substring(0, keywordEnd),
                    entry.substring(keywordEnd + 1)));
        }
    }

    private static void parseInstanceBlock(Cursor cursor, String header,
            Map<String, List<Instance>> instances) throws IOException {
        int space = header.indexOf(' ');
        if (space <= 0) {
            throw cursor.error("unexpected tag \"" + header + "\"");
        }
        String category = header.substring(0, space);
        if (!CATEGORIES.contains(category)) {
            throw cursor.error("unexpected tag '" + category + "'");
        }
        int count = cursor.count(header.substring(space + 1), header);
        List<Instance> list = instances.get(category);
        if (list == null) {
            list = new ArrayList<Instance>();
            instances.put(category, list);
        }
        List<String> none = Collections.emptyList();
        for (int i = 0; i < count; i++) {
            // Verbatim, never trimmed; and read strictly by count, so a name
            // of "END", "S" or "" is just a name.
            String name = cursor.next("name " + (i + 1) + " of " + count
                    + " in the " + category + " block");
            if (category.equals("SYNC")) {
                List<String> exc = subList(cursor, "EXC", name);
                List<String> tor = subList(cursor, "TOR", name);
                list.add(new Instance(name, exc, tor, none));
            } else if (category.equals("INJ") || category.equals("LINK")
                    || category.equals("DCTL")) {
                list.add(new Instance(name, none, none, subList(cursor, "OBS", name)));
            } else {
                list.add(new Instance(name, none, none, none));
            }
        }
    }

    private static List<String> subList(Cursor cursor, String tag, String owner)
            throws IOException {
        String header = cursor.next(tag + " header for '" + owner + "'");
        if (!header.startsWith(tag + " ")) {
            throw cursor.error("expected '" + tag + " <count>' after '" + owner
                    + "', got \"" + header + "\"");
        }
        int count = cursor.count(header.substring(tag.length() + 1), header);
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            names.add(cursor.next("name " + (i + 1) + " of " + count + " in the "
                    + tag + " block of '" + owner + "'"));
        }
        return names;
    }

    /** Line supply with 1-based numbering for error messages. */
    private static final class Cursor {
        private final String[] lines;
        private int at; // index of the next unread line

        Cursor(String text) {
            String[] raw = text.split("\n", -1);
            // The child's stdout on Windows arrives CRLF-terminated. The CR
            // is the line ending, not a name byte; everything else is kept
            // exactly as read.
            for (int i = 0; i < raw.length; i++) {
                if (raw[i].endsWith("\r")) {
                    raw[i] = raw[i].substring(0, raw[i].length() - 1);
                }
            }
            this.lines = raw;
        }

        boolean hasNext() {
            return at < lines.length;
        }

        String next(String what) throws IOException {
            if (at >= lines.length) {
                throw new IOException("line " + (at + 1)
                        + ": unexpected end of input while reading " + what);
            }
            return lines[at++];
        }

        /** An error at the line most recently returned by {@link #next}. */
        IOException error(String message) {
            return new IOException("line " + at + ": " + message);
        }

        int count(String token, String header) throws IOException {
            int n;
            try {
                n = Integer.parseInt(token.trim());
            } catch (NumberFormatException ex) {
                throw error("unreadable count in \"" + header + "\"");
            }
            if (n < 0) {
                throw error("negative count in \"" + header + "\"");
            }
            return n;
        }
    }
```

- [ ] **Step 5: Run the harness and watch it pass**

```bash
ant compile
```
```bash
tools/picker-harness.sh
```
Expected: a `PASS` line for every check — including `PASS  a leading blank is part of the name`, `PASS  INJ2 observables differ from INJ1's - sub-lists are per instance, never pooled`, `PASS  a bus named END is a name, not a terminator` — ending with `ALL CHECKS PASSED`, exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/my/ramses/dyngraph/ObservableIndex.java src/my/ramses/dyngraph/PickerHarness.java
```
```bash
git commit -m "Parse the dyngraph --list index

Count-driven throughout: every block is '<TAG> <count>' plus exactly
that many lines, so an instance named END or S can never terminate
anything - the fixture proves it. Names are taken verbatim; a leading
blank is part of the name, and the console comparison pads trailing
blanks only, so left-trimming would break the -t round trip.

Sub-lists stay keyed by instance, never pooled per category: two
injectors routinely expose different observables, which is exactly
what a category-pooled parser gets wrong on any real network while
passing on any single-instance fixture.

Version 1 only; a higher DYNGRAPH-INDEX marks a deliberate format
break, so refusing beats guessing. Malformed input names the
offending line."
```

---

### Task 3: Selection — value object and desc_obs labels

**Files:**
- Create: `src/my/ramses/dyngraph/Selection.java`
- Modify: `src/my/ramses/dyngraph/PickerHarness.java`

**Interfaces:**
- Consumes: `ObservableIndex.CATEGORIES`, `ObservableIndex.TYPED_CATEGORIES` (Task 2).
- Produces:
  - `new Selection(String category, String keyword, String typeLabel, String name, String sub)` — throws `IllegalArgumentException` on any malformed shape. Public final fields `category`, `keyword`, `typeLabel`, `name`, `sub`.
  - `Selection.requiresSub(String category, String keyword)` returning `boolean` — static; true for `SOE`, `SOT` and every `INJ`/`LINK`/`DCTL` pick.
  - `Selection.label()` returning `String` — the `desc_obs`-format display label; `toString()` delegates to it so a `JList` renders it directly.

- [ ] **Step 1: Write the `Selection` stub**

Fields land now so the checks compile; validation and label composition are the checks' subject and follow in Step 4.

```java
package my.ramses.dyngraph;

/**
 * Stub shape - the fields exist so the harness checks compile; the
 * constructor validation and label composition they pin follow in this
 * task.
 */
public final class Selection {

    public final String category;
    public final String keyword;
    public final String typeLabel;
    public final String name;
    public final String sub;

    public Selection(String category, String keyword, String typeLabel, String name, String sub) {
        this.category = category;
        this.keyword = keyword;
        this.typeLabel = typeLabel;
        this.name = name;
        this.sub = sub;
    }

    public static boolean requiresSub(String category, String keyword) {
        return false;
    }

    public String label() {
        return "";
    }

    @Override
    public String toString() {
        return label();
    }
}
```

- [ ] **Step 2: Add the label and validation checks to `PickerHarness`**

In `main`, after `checkUnreadableCountNamed();`, add:

```java
        checkLabelComposition();
        checkSelectionShapeValidation();
```

and the methods:

```java
    private static void checkLabelComposition() {
        expect("bus label", "bus BUS1: voltage magnitude (pu)",
                new Selection("BUS", "BM", "voltage magnitude (pu)", "BUS1", null).label());
        expect("shunt label", "shunt SHUNT1: reactive power produced (Mvar)",
                new Selection("SHUNT", "HQ", "reactive power produced (Mvar)", "SHUNT1", null).label());
        expect("load label says impedance load", "impedance load LOAD1: active power consumed (MW)",
                new Selection("LOAD", "LP", "active power consumed (MW)", "LOAD1", null).label());
        expect("branch label", "branch BR1-2: P (MW) entering at FROM end",
                new Selection("BRANCH", "RPF", "P (MW) entering at FROM end", "BR1-2", null).label());
        expect("sync label says sync mach", "sync mach GEN1: rotor speed (pu)",
                new Selection("SYNC", "SS", "rotor speed (pu)", "GEN1", null).label());
        expect("SOE renders as excit control, not its TYPES label",
                "sync mach GEN1: excit control: VF",
                new Selection("SYNC", "SOE", "observable of excitation controller", "GEN1", "VF").label());
        expect("SOT renders as torque control, not its TYPES label",
                "sync mach GEN2: torque control: Pm",
                new Selection("SYNC", "SOT", "observable of torque controller", "GEN2", "Pm").label());
        expect("injector label", "injector INJ2: omega",
                new Selection("INJ", null, null, "INJ2", "omega").label());
        expect("link label", "link LINK1: I1",
                new Selection("LINK", null, null, "LINK1", "I1").label());
        expect("DCTL label keeps DCTL upper-case", "DCTL DCTL1: ST",
                new Selection("DCTL", null, null, "DCTL1", "ST").label());
        expect("a leading blank stays visible in the label",
                "bus  LEADBUS: voltage magnitude (pu)",
                new Selection("BUS", "BM", "voltage magnitude (pu)", " LEADBUS", null).label());
    }

    private static void checkSelectionShapeValidation() {
        expect("SOE requires a sub-observable", true, Selection.requiresSub("SYNC", "SOE"));
        expect("SOT requires a sub-observable", true, Selection.requiresSub("SYNC", "SOT"));
        expect("SS does not require a sub-observable", false, Selection.requiresSub("SYNC", "SS"));
        expect("every injector pick requires one", true, Selection.requiresSub("INJ", null));
        expectSelectionRejected("SOE without a sub-observable is rejected",
                "SYNC", "SOE", "observable of excitation controller", "GEN1", null);
        expectSelectionRejected("a sub-observable on a plain keyword is rejected",
                "BUS", "BM", "voltage magnitude (pu)", "BUS1", "X");
        expectSelectionRejected("a replay keyword on an injector is rejected - I belongs to ReplayFile",
                "INJ", "I", null, "INJ1", "P");
        expectSelectionRejected("an unknown category is rejected",
                "BOGUS", "BM", "label", "X", null);
    }

    private static void expectSelectionRejected(String what, String category,
            String keyword, String typeLabel, String name, String sub) {
        try {
            new Selection(category, keyword, typeLabel, name, sub);
            fail(what + ": no exception");
        } catch (IllegalArgumentException expected) {
            pass(what);
        }
    }
```

- [ ] **Step 3: Run the harness and watch the new checks fail**

```bash
ant compile
```
```bash
tools/picker-harness.sh
```
Expected: exit 1 with `18 CHECK(S) FAILED` — all eleven label checks (`FAIL  bus label: wanted <bus BUS1: voltage magnitude (pu)> got <>` and so on), the three `requiresSub` trues (`FAIL  SOE requires a sub-observable: wanted <true> got <false>`), and the four rejection checks (`FAIL  SOE without a sub-observable is rejected: no exception`). `PASS  SS does not require a sub-observable` passes already — the stub's constant `false` happens to be right for that one.

- [ ] **Step 4: Implement validation, `requiresSub` and `label`**

Overwrite `src/my/ramses/dyngraph/Selection.java`:

```java
package my.ramses.dyngraph;

/**
 * One picked observable: index category, replay keyword (for the five typed
 * categories), instance name and optional sub-observable.
 *
 * <p>{@link #label()} reproduces the exact strings DYNGRAPH's console
 * selector writes into {@code desc_obs} - which become the .plt curve
 * titles - so the dialog's Selected list reads the same as the plot the user
 * gets (stepss-dyngraph/src/selec_observ.f90:92,123,157,203,299,316,322,
 * 355,388,432). {@code SOE} and {@code SOT} render as {@code excit control:}
 * and {@code torque control:} rather than their TYPES labels ("observable of
 * excitation controller"), because that is what the console produces. The
 * console concatenates fixed-width Fortran strings, so the real titles carry
 * blank padding between the pieces; this label composes the same words and
 * punctuation from the already-trimmed index strings and does not reproduce
 * that padding.
 */
public final class Selection {

    /** {@link ObservableIndex} category tag: BUS, SHUNT, LOAD, BRANCH, SYNC, INJ, LINK or DCTL. */
    public final String category;
    /** Replay keyword from the category's TYPES table (BM ... SOT); null for INJ/LINK/DCTL, whose keywords {@link ReplayFile} owns. */
    public final String keyword;
    /** The keyword's TYPES label; null for INJ/LINK/DCTL. */
    public final String typeLabel;
    /** Instance name, verbatim - a leading blank is part of it. */
    public final String name;
    /** Sub-observable name; required for SOE/SOT and for INJ/LINK/DCTL, forbidden otherwise. */
    public final String sub;

    public Selection(String category, String keyword, String typeLabel, String name, String sub) {
        if (!ObservableIndex.CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unknown category: " + category);
        }
        if (name == null) {
            throw new IllegalArgumentException("Instance name must not be null");
        }
        boolean typed = ObservableIndex.TYPED_CATEGORIES.contains(category);
        if (typed) {
            if (keyword == null || typeLabel == null) {
                throw new IllegalArgumentException(category
                        + " selections carry a keyword and its label from the TYPES table");
            }
        } else {
            if (keyword != null || typeLabel != null) {
                throw new IllegalArgumentException(category
                        + " selections carry no keyword: the replay keyword is ReplayFile's, not the index's");
            }
        }
        boolean needsSub = requiresSub(category, keyword);
        if (needsSub && sub == null) {
            throw new IllegalArgumentException("A " + (typed ? keyword : category)
                    + " selection needs a sub-observable");
        }
        if (!needsSub && sub != null) {
            throw new IllegalArgumentException("A " + keyword
                    + " selection takes no sub-observable");
        }
        this.category = category;
        this.keyword = keyword;
        this.typeLabel = typeLabel;
        this.name = name;
        this.sub = sub;
    }

    /**
     * Whether a (category, keyword) pair takes a third replay line: true for
     * SOE, SOT and every INJ/LINK/DCTL pick, false for every other keyword.
     */
    public static boolean requiresSub(String category, String keyword) {
        if ("INJ".equals(category) || "LINK".equals(category) || "DCTL".equals(category)) {
            return true;
        }
        return "SOE".equals(keyword) || "SOT".equals(keyword);
    }

    /** The desc_obs-format display label; see the class comment. */
    public String label() {
        if ("BUS".equals(category)) {
            return "bus " + name + ": " + typeLabel;
        }
        if ("SHUNT".equals(category)) {
            return "shunt " + name + ": " + typeLabel;
        }
        if ("LOAD".equals(category)) {
            return "impedance load " + name + ": " + typeLabel;
        }
        if ("BRANCH".equals(category)) {
            return "branch " + name + ": " + typeLabel;
        }
        if ("SYNC".equals(category)) {
            if ("SOE".equals(keyword)) {
                return "sync mach " + name + ": excit control: " + sub;
            }
            if ("SOT".equals(keyword)) {
                return "sync mach " + name + ": torque control: " + sub;
            }
            return "sync mach " + name + ": " + typeLabel;
        }
        if ("INJ".equals(category)) {
            return "injector " + name + ": " + sub;
        }
        if ("LINK".equals(category)) {
            return "link " + name + ": " + sub;
        }
        return "DCTL " + name + ": " + sub;
    }

    /** The Selected JList renders selections through toString. */
    @Override
    public String toString() {
        return label();
    }
}
```

- [ ] **Step 5: Run the harness and watch it pass**

```bash
ant compile
```
```bash
tools/picker-harness.sh
```
Expected: `ALL CHECKS PASSED`, exit 0, including `PASS  SOE renders as excit control, not its TYPES label` and `PASS  a leading blank stays visible in the label`.

- [ ] **Step 6: Commit**

```bash
git add src/my/ramses/dyngraph/Selection.java src/my/ramses/dyngraph/PickerHarness.java
```
```bash
git commit -m "Compose selection labels the way DYNGRAPH writes desc_obs

Selection.label() reproduces the console's desc_obs formats - the
strings that become .plt curve titles - so the dialog's Selected list
reads the same as the plot the user gets. SOE and SOT render as
'excit control:' and 'torque control:' rather than their TYPES labels,
because that is what the console produces.

The constructor rejects malformed shapes (a sub-observable where none
belongs, a replay keyword on an injector) so ReplayFile can trust what
it is handed."
```

---

### Task 4: ReplayFile — emitting the console keyword stream

**Files:**
- Create: `src/my/ramses/dyngraph/ReplayFile.java`
- Modify: `src/my/ramses/dyngraph/PickerHarness.java`

**Interfaces:**
- Consumes: `Selection` (Task 3); the parsed fixture (Task 2).
- Produces:
  - `ReplayFile.CHARSET` — `public static final Charset`, ISO-8859-1; `DyngraphRunner` (Task 5) decodes `--list` output with it, so both sides of the byte round trip share one definition.
  - `ReplayFile.emit(String trajectoryPath, java.util.List<Selection> selections)` returning `String` — pure text; throws `IllegalArgumentException` on an empty list.
  - `ReplayFile.write(File selCmd, String trajectoryPath, java.util.List<Selection> selections)` throwing `IOException` — `emit` encoded as `CHARSET`.

- [ ] **Step 1: Write the `ReplayFile` stub**

```java
package my.ramses.dyngraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Stub - emit's grammar is pinned by the harness checks added alongside it
 * in this task.
 */
public final class ReplayFile {

    public static final Charset CHARSET = Charset.forName("ISO-8859-1");

    private ReplayFile() {
    }

    public static String emit(String trajectoryPath, List<Selection> selections) {
        return "";
    }

    /** Writes {@link #emit} to {@code selCmd} in {@link #CHARSET}. */
    public static void write(File selCmd, String trajectoryPath, List<Selection> selections)
            throws IOException {
        OutputStream out = new FileOutputStream(selCmd);
        try {
            out.write(emit(trajectoryPath, selections).getBytes(CHARSET));
        } finally {
            out.close();
        }
    }
}
```

- [ ] **Step 2: Add the round-trip checks to `PickerHarness`**

In `main`, after `checkSelectionShapeValidation();`, add:

```java
        checkReplayRoundTrip();
        checkReplayRejectsEmptySelection();
```

Add the expected replay stream beside `FIXTURE_LINES`, and the methods:

```java
    /**
     * The expected replay stream for the scripted picks below, embedded like
     * the fixture so the round trip is pinned byte for byte, the way
     * DYNGRAPH's own smoke gate pins its goldens. Note " LEADBUS" keeping
     * its leading blank, the names "END" and "S" travelling as name lines,
     * and "ST" travelling as a DCTL observable name - position in the
     * stream, never content, decides what a line means.
     */
    private static final String[] EXPECTED_REPLAY_LINES = {
        "output.trj",
        "BM",
        "BUS1",
        "BM",
        " LEADBUS",
        "BM",
        "END",
        "RPF",
        "S",
        "SS",
        "GEN1",
        "SOE",
        "GEN1",
        "VF",
        "SOT",
        "GEN2",
        "Pm",
        "I",
        "INJ2",
        "omega",
        "T",
        "LINK1",
        "I1",
        "D",
        "DCTL1",
        "ST",
        "",
        "S"
    };

    private static void checkReplayRoundTrip() {
        ObservableIndex index = parsedFixture();
        if (index == null) {
            fail("replay round trip: fixture did not parse");
            return;
        }
        // Every name and sub-observable below is taken from the parsed
        // index, never retyped, so this pins the full parse -> pick -> emit
        // round trip - the only guard against the silent desync failure,
        // since DYNGRAPH re-prompts rather than aborting on an unmatched
        // name and can exit 0 having plotted the wrong curves.
        ObservableIndex.TypeEntry bm = index.types("BUS").get(0);
        ObservableIndex.TypeEntry rpf = index.types("BRANCH").get(0);
        ObservableIndex.TypeEntry ss = index.types("SYNC").get(3);
        ObservableIndex.TypeEntry soe = index.types("SYNC").get(15);
        ObservableIndex.TypeEntry sot = index.types("SYNC").get(16);
        ObservableIndex.Instance gen1 = index.instances("SYNC").get(0);
        ObservableIndex.Instance gen2 = index.instances("SYNC").get(1);
        ObservableIndex.Instance inj2 = index.instances("INJ").get(1);
        ObservableIndex.Instance link1 = index.instances("LINK").get(0);
        ObservableIndex.Instance dctl1 = index.instances("DCTL").get(0);

        java.util.List<Selection> picks = new java.util.ArrayList<Selection>();
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(0).name, null));
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(2).name, null));
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(3).name, null));
        picks.add(new Selection("BRANCH", rpf.keyword, rpf.label, index.instances("BRANCH").get(1).name, null));
        picks.add(new Selection("SYNC", ss.keyword, ss.label, gen1.name, null));
        picks.add(new Selection("SYNC", soe.keyword, soe.label, gen1.name, gen1.exc.get(0)));
        picks.add(new Selection("SYNC", sot.keyword, sot.label, gen2.name, gen2.tor.get(0)));
        picks.add(new Selection("INJ", null, null, inj2.name, inj2.obs.get(1)));
        picks.add(new Selection("LINK", null, null, link1.name, link1.obs.get(0)));
        picks.add(new Selection("DCTL", null, null, dctl1.name, dctl1.obs.get(0)));

        expect("emit matches the pinned replay stream", join(EXPECTED_REPLAY_LINES),
                ReplayFile.emit("output.trj", picks));
    }

    private static void checkReplayRejectsEmptySelection() {
        try {
            ReplayFile.emit("output.trj", new java.util.ArrayList<Selection>());
            fail("an empty selection list is rejected: no exception");
        } catch (IllegalArgumentException expected) {
            pass("an empty selection list is rejected");
        }
    }
```

- [ ] **Step 3: Run the harness and watch the new checks fail**

```bash
ant compile
```
```bash
tools/picker-harness.sh
```
Expected: exit 1 with `2 CHECK(S) FAILED`:
```
FAIL  emit matches the pinned replay stream: wanted <output.trj
BM
...
S
> got <>
FAIL  an empty selection list is rejected: no exception
```

- [ ] **Step 4: Implement `ReplayFile`**

Overwrite `src/my/ramses/dyngraph/ReplayFile.java`:

```java
package my.ramses.dyngraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns selections into the console keyword stream DYNGRAPH replays under
 * {@code -t}. Pure text; the grammar is pinned upstream by
 * stepss-dyngraph/tests/smoke.cmd and tests/nested.cmd:
 *
 * <pre>
 * &lt;trajectory path&gt;      line 1
 * BM                     keyword
 * BUS1                   instance name
 * SOE                    redirects to a machine's exciter sub-list
 * GEN1
 * VF                     sub-observable, from that machine's EXC block
 *                        blank line: stop selecting
 * S                      stop
 * </pre>
 *
 * <p>Keyword then name, with a third line - the sub-observable - when the
 * keyword is SOE, SOT, I, T or D, and for no other keyword. Keywords are
 * matched case-sensitively in upper case; only the trailing stop accepts
 * {@code s} as well.
 */
public final class ReplayFile {

    /**
     * Both sides of the DYNGRAPH exchange - the {@code --list} read and the
     * {@code sel.cmd} write - use ISO-8859-1, explicitly. DYNGRAPH has no
     * encoding concept: names are raw bytes copied out of the trajectory
     * file and compared byte-for-byte on the way back in. ISO-8859-1 maps
     * bytes 0-255 onto the first 256 code points and back, so the round
     * trip is exact for any byte the trajectory can contain; UTF-8 would
     * turn a byte &gt;= 0x80 into a replacement character and write
     * different bytes back. The failure would be silent - an unmatched name
     * makes the console selector re-prompt, not abort, so DYNGRAPH can exit
     * 0 having plotted the wrong curves. A non-ASCII name renders as
     * mojibake in the dialog; a wrong glyph the user can see beats a wrong
     * plot they cannot.
     */
    public static final Charset CHARSET = Charset.forName("ISO-8859-1");

    /**
     * Replay keywords for the three categories without TYPES blocks. The
     * index does not carry them, so they are hardcoded here and nowhere
     * else - accepted coupling: they are as fixed as the trajectory layout
     * itself. Sources: stepss-dyngraph/src/selec_observ.f90:329 (I,
     * injector), :360 (T, link/twoport), :393 (D, DCTL); demonstrated by
     * stepss-dyngraph/tests/nested.cmd. Keyword space and name space are
     * disjoint: a DCTL observable named ST is a name line, never the sync
     * keyword ST, because position in the stream decides.
     */
    private static final Map<String, String> REPLAY_KEYWORDS = replayKeywords();

    private static Map<String, String> replayKeywords() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("INJ", "I");
        m.put("LINK", "T");
        m.put("DCTL", "D");
        return Collections.unmodifiableMap(m);
    }

    private ReplayFile() {
    }

    /**
     * The full replay stream: trajectory path, one keyword/name(/sub) group
     * per selection in list order - list order is column order in the .cur
     * and curve order in the .plt - then the blank stop-selecting line and
     * the {@code S} stop line.
     */
    public static String emit(String trajectoryPath, List<Selection> selections) {
        if (selections == null || selections.isEmpty()) {
            // The dialog disables Plot while nothing is selected; an empty
            // stream here is a caller bug, not a user state.
            throw new IllegalArgumentException("No selections to emit");
        }
        StringBuilder out = new StringBuilder();
        out.append(trajectoryPath).append('\n');
        for (Selection s : selections) {
            String keyword = s.keyword != null ? s.keyword : REPLAY_KEYWORDS.get(s.category);
            out.append(keyword).append('\n');
            out.append(s.name).append('\n');
            if (s.sub != null) {
                out.append(s.sub).append('\n');
            }
        }
        out.append('\n');   // empty answer at the keyword prompt: stop selecting
        out.append("S\n");  // stop instead of extracting another set
        return out.toString();
    }

    /** Writes {@link #emit} to {@code selCmd} in {@link #CHARSET}. */
    public static void write(File selCmd, String trajectoryPath, List<Selection> selections)
            throws IOException {
        OutputStream out = new FileOutputStream(selCmd);
        try {
            out.write(emit(trajectoryPath, selections).getBytes(CHARSET));
        } finally {
            out.close();
        }
    }
}
```

- [ ] **Step 5: Run the harness and watch it pass**

```bash
ant compile
```
```bash
tools/picker-harness.sh
```
Expected: `ALL CHECKS PASSED`, exit 0, including `PASS  emit matches the pinned replay stream` and `PASS  an empty selection list is rejected`.

- [ ] **Step 6: Commit**

```bash
git add src/my/ramses/dyngraph/ReplayFile.java src/my/ramses/dyngraph/PickerHarness.java
```
```bash
git commit -m "Emit the replay file DYNGRAPH's console selector consumes

Keyword, then name, then a sub-observable line for SOE/SOT/I/T/D and
no other keyword; a blank line and S terminate - exactly the grammar
tests/smoke.cmd and nested.cmd pin upstream. The trajectory path is
line 1, so a path with spaces is a whole line and never a quoting
problem.

The INJ/LINK/DCTL keywords (I, T, D) are hardcoded here as accepted
coupling: the index carries no TYPES block for those categories, and
a format break to ship three fixed letters is not worth a release.

ISO-8859-1 on the write (and the --list read, which shares this
CHARSET) because DYNGRAPH has no encoding concept - names are raw
bytes compared byte-for-byte, and a UTF-8 round trip would silently
rewrite any byte above 0x7F. A desynced replay does not abort: the
console re-prompts and can exit 0 having plotted the wrong curves, so
the harness round trip is the only guard."
```

---

### Task 5: DyngraphRunner — the two invocations

`DyngraphRunner` is deliberately outside the harness (the spec's split: the harness launches no process and needs no extracted payload). Its verification is behavioural probes against the real bundled binary — including a full list → parse → emit → plot round trip whose `.cur` is diffed against DYNGRAPH's own golden.

**Files:**
- Create: `src/my/ramses/dyngraph/DyngraphRunner.java`

**Interfaces:**
- Consumes: `ReplayFile.CHARSET` (Task 4).
- Produces:
  - `new DyngraphRunner(File dyngraph, File workingDir, java.util.Map environment)` — the resolved executable (`toolchain.dyngraph()`), the temp directory, and the exec environment (`WinEnvironment` in `RamsesUI`; null inherits). The spec's Toolchain/Platform dependency is satisfied through these already-resolved values, which is what keeps the class probe-able without the GUI.
  - `DyngraphRunner.list(File trajectory)` returning `DyngraphRunner.ListResult`, throwing `IOException` — **blocking**; call off the EDT. `ListResult` has public final `int exitCode`, `String stdout`, `String stderr`.
  - `DyngraphRunner.plot(File selCmd, File outputBase, DyngraphRunner.PlotListener listener)` throwing `IOException` — **async**, returns immediately. `PlotListener.onFinished(int exitCode, String stderr)` is called on the executor's watchdog thread, never the EDT.

- [ ] **Step 1: Write `DyngraphRunner`**

```java
package my.ramses.dyngraph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

/**
 * The two DYNGRAPH invocations behind the observable picker, both with
 * stdout and stderr captured to buffers - the Helios capture precedent
 * (RamsesUI's dedicated stderr buffer feeding reportHeliosExitStatus), not
 * viewCurvesButton's no-arg PumpStreamHandler, which inherits the JVM's
 * streams and captures nothing.
 *
 * <p><b>Neither call may run on the EDT.</b> get_observ_name rewinds the
 * trajectory several times and the plot run reads the whole time series, so
 * both scale with file size. {@link #list} is blocking, written for a
 * SwingWorker's doInBackground; {@link #plot} is asynchronous through a
 * DefaultExecuteResultHandler and reports on the executor's thread.
 *
 * <p>{@code --list} needs stdout captured alone: DYNGRAPH's chatty
 * per-category counts go to unit 0 (stderr), so stdout carries the index and
 * nothing else. Both streams decode as {@link ReplayFile#CHARSET}
 * (ISO-8859-1), the same single definition the sel.cmd write uses, so the
 * byte round trip cannot split across two charsets.
 */
public final class DyngraphRunner {

    /** Outcome of a blocking {@code --list} run. */
    public static final class ListResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        ListResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    /** Callback for the async plot run. Called off the EDT - hop with invokeLater. */
    public interface PlotListener {
        void onFinished(int exitCode, String stderr);
    }

    private final File dyngraph;
    private final File workingDir;
    private final Map environment;

    /**
     * @param dyngraph the resolved executable ({@code toolchain.dyngraph()})
     * @param workingDir the temp directory both runs execute in
     * @param environment the exec environment
     *        ({@code PlatformLauncher.execEnvironment}'s map); null inherits
     */
    public DyngraphRunner(File dyngraph, File workingDir, Map environment) {
        this.dyngraph = dyngraph;
        this.workingDir = workingDir;
        this.environment = environment;
    }

    /**
     * Runs {@code dyngraph --list <trajectory>} and returns exit status and
     * both captured streams. Blocking; call off the EDT.
     *
     * <p>Every exit value is accepted here rather than thrown: the caller's
     * detection is exit-status-first (an old DYNGRAPH ignores --list, writes
     * its filename prompt to stderr, hits EOF on its closed stdin and exits
     * non-zero with stdout empty), so the code belongs in the result, not in
     * an ExecuteException.
     */
    public ListResult list(File trajectory) throws IOException {
        CommandLine cmd = new CommandLine(dyngraph.getAbsolutePath());
        cmd.addArgument("--list");
        // Quoting disabled: the default handling re-quotes a token
        // containing spaces and the quotes reach the child.
        cmd.addArgument(trajectory.getAbsolutePath(), false);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValues(null); // accept every exit value
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        executor.setWorkingDirectory(workingDir);
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        int exit = executor.execute(cmd, environment);
        return new ListResult(exit,
                new String(stdout.toByteArray(), ReplayFile.CHARSET),
                new String(stderr.toByteArray(), ReplayFile.CHARSET));
    }

    /**
     * Runs {@code dyngraph -c -t <selCmd> -o<outputBase>} asynchronously and
     * reports through {@code listener}. Not interactive under -t, so it gets
     * no terminal window. No {@code -eps}: it would export an EPS file
     * instead of leaving a .plt that viewCurvesButton can open in a gnuplot
     * window - the same flag choice as today's Extract Curves.
     *
     * <p>The trajectory path travels as line 1 of the replay file, so it is
     * never an argv token; -o still carries a possibly-space-containing
     * absolute path as one token and is added with quoting disabled.
     */
    public void plot(File selCmd, File outputBase, final PlotListener listener)
            throws IOException {
        CommandLine cmd = new CommandLine(dyngraph.getAbsolutePath());
        cmd.addArgument("-c");
        cmd.addArgument("-t");
        cmd.addArgument(selCmd.getAbsolutePath(), false);
        cmd.addArgument("-o" + outputBase.getAbsolutePath(), false);

        // stdout is captured only to keep it out of the GUI's console; with
        // -o set, DYNGRAPH's unit 6 carries nothing of interest. stderr
        // carries the desc_obs echo lines and any real complaint, and is
        // what the listener reports on failure.
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        DefaultExecutor executor = new DefaultExecutor();
        executor.setExitValue(0);
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        executor.setWorkingDirectory(workingDir);
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler() {
            @Override
            public void onProcessComplete(int exitValue) {
                listener.onFinished(exitValue,
                        new String(stderr.toByteArray(), ReplayFile.CHARSET));
            }

            @Override
            public void onProcessFailed(ExecuteException ex) {
                listener.onFinished(ex.getExitValue(),
                        new String(stderr.toByteArray(), ReplayFile.CHARSET));
            }
        };
        executor.execute(cmd, environment, handler);
    }
}
```

- [ ] **Step 2: Full build**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant jar
```
Expected: BUILD SUCCESSFUL. (`ant jar` here, not `ant compile`: the probes below need `dist/lib` for commons-exec and the payload resources in `build/classes`.)

- [ ] **Step 3: Probe the missing-file path (exit-status and capture wiring)**

```bash
mkdir -p /tmp/claude/picker-probe/work
```
```bash
cat > /tmp/claude/picker-probe/ListProbe.java <<'EOF'
import my.ramses.dyngraph.DyngraphRunner;
import my.ramses.platform.Platform;
import my.ramses.platform.Toolchain;
import java.io.File;

public class ListProbe {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        Toolchain tc = new Toolchain(Platform.current(), dir);
        tc.extractAll();
        DyngraphRunner runner = new DyngraphRunner(tc.dyngraph(), dir, null);
        DyngraphRunner.ListResult missing = runner.list(new File(dir, "no-such.trj"));
        System.out.println("exit=" + missing.exitCode);
        System.out.println("stdout.empty=" + missing.stdout.trim().isEmpty());
        System.out.println("stderr=" + missing.stderr.trim());
    }
}
EOF
```
```bash
javac -cp "build/classes:dist/lib/*" -d /tmp/claude/picker-probe /tmp/claude/picker-probe/ListProbe.java
```
```bash
java -cp "build/classes:dist/lib/*:/tmp/claude/picker-probe" ListProbe /tmp/claude/picker-probe/tools
```
Expected, on Linux:
```
exit=1
stdout.empty=true
stderr=File /tmp/claude/picker-probe/tools/no-such.trj does not exist. Exiting...
```
This is the discrimination the whole failure table rests on: a non-zero exit with empty stdout and the complaint on captured stderr — the same shape an old, `--list`-less DYNGRAPH produces, which is why the exit code and both buffers must all come back through `ListResult`.

- [ ] **Step 4: Generate DYNGRAPH's own smoke fixture**

`stepss-dyngraph/tests/make_fixture.f90` writes `smoke.trj` into the current directory; gfortran is present on this machine (the compile-plan acceptance used it).

```bash
gfortran -O0 -ffree-form -o /tmp/claude/picker-probe/make_fixture /home/apetros/Code/stepss/stepss-dyngraph/tests/make_fixture.f90
```
```bash
cd /tmp/claude/picker-probe/work && ../make_fixture
```
Expected: `smoke.trj` exists and is non-empty (`ls -l /tmp/claude/picker-probe/work/smoke.trj`).

- [ ] **Step 5: Run the full round trip against the golden `.cur`**

The probe makes the same four picks as `stepss-dyngraph/tests/smoke.cmd` (`BM BUS1`, `BM BUS2`, `SS GEN1`, `RPF BR1-2`), so the `.cur` it produces must be byte-identical to `tests/golden/smoke.cur` — `-eps` affects only the `.plt`, and the `.cur` carries no paths.

```bash
cat > /tmp/claude/picker-probe/RoundTripProbe.java <<'EOF'
import my.ramses.dyngraph.DyngraphRunner;
import my.ramses.dyngraph.ObservableIndex;
import my.ramses.dyngraph.ReplayFile;
import my.ramses.dyngraph.Selection;
import my.ramses.platform.Platform;
import my.ramses.platform.Toolchain;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class RoundTripProbe {
    public static void main(String[] args) throws Exception {
        File work = new File(args[0]);      // must already contain smoke.trj
        File toolDir = new File(args[1]);
        toolDir.mkdirs();
        Toolchain tc = new Toolchain(Platform.current(), toolDir);
        tc.extractAll();
        DyngraphRunner runner = new DyngraphRunner(tc.dyngraph(), work, null);

        DyngraphRunner.ListResult listing = runner.list(new File(work, "smoke.trj"));
        System.out.println("list.exit=" + listing.exitCode);
        ObservableIndex index = ObservableIndex.parse(listing.stdout);
        System.out.println("parsed.empty=" + index.isEmpty());

        // The same four picks as stepss-dyngraph/tests/smoke.cmd, so the
        // .cur this produces can be diffed against tests/golden/smoke.cur.
        ObservableIndex.TypeEntry bm = index.types("BUS").get(0);
        ObservableIndex.TypeEntry ss = index.types("SYNC").get(3);
        ObservableIndex.TypeEntry rpf = index.types("BRANCH").get(0);
        List<Selection> picks = new ArrayList<Selection>();
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(0).name, null));
        picks.add(new Selection("BUS", bm.keyword, bm.label, index.instances("BUS").get(1).name, null));
        picks.add(new Selection("SYNC", ss.keyword, ss.label, index.instances("SYNC").get(0).name, null));
        picks.add(new Selection("BRANCH", rpf.keyword, rpf.label, index.instances("BRANCH").get(0).name, null));

        File selCmd = new File(work, "sel.cmd");
        ReplayFile.write(selCmd, new File(work, "smoke.trj").getAbsolutePath(), picks);

        final CountDownLatch done = new CountDownLatch(1);
        final int[] exit = new int[1];
        final String[] err = new String[1];
        runner.plot(selCmd, new File(work, "tempGnupOut"), new DyngraphRunner.PlotListener() {
            public void onFinished(int exitCode, String stderr) {
                exit[0] = exitCode;
                err[0] = stderr;
                done.countDown();
            }
        });
        done.await();
        System.out.println("plot.exit=" + exit[0]);
        System.out.println("cur.exists=" + new File(work, "tempGnupOut.cur").isFile());
        System.out.println("plt.exists=" + new File(work, "tempGnupOut.plt").isFile());
        if (exit[0] != 0) {
            System.out.println("stderr=" + err[0]);
        }
    }
}
EOF
```
```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
javac -cp "build/classes:dist/lib/*" -d /tmp/claude/picker-probe /tmp/claude/picker-probe/RoundTripProbe.java
```
```bash
java -cp "build/classes:dist/lib/*:/tmp/claude/picker-probe" RoundTripProbe /tmp/claude/picker-probe/work /tmp/claude/picker-probe/tools
```
Expected:
```
list.exit=0
parsed.empty=false
plot.exit=0
cur.exists=true
plt.exists=true
```
```bash
diff /home/apetros/Code/stepss/stepss-dyngraph/tests/golden/smoke.cur /tmp/claude/picker-probe/work/tempGnupOut.cur
```
Expected: **no output** — the `.cur` extracted through `ObservableIndex` → `Selection` → `ReplayFile` → `DyngraphRunner` is byte-identical to the golden the upstream smoke gate pins. (The `.plt` is deliberately not diffed: it embeds absolute paths, and the no-`-eps` branch this UI uses is precisely the branch no upstream golden pins — an accepted, documented gap.)

- [ ] **Step 6: Commit**

```bash
git add src/my/ramses/dyngraph/DyngraphRunner.java
```
```bash
git commit -m "Run dyngraph --list and -t off the EDT, output captured

Both invocations scale with trajectory size - get_observ_name rewinds
the file several times - so neither may run on the EDT: list() is
blocking for a SwingWorker, plot() is async through a
DefaultExecuteResultHandler.

stdout and stderr go to buffers, following the Helios capture
precedent rather than viewCurvesButton's inherit-everything handler:
the caller needs the exit status first (an old dyngraph ignores
--list, writes its prompt to stderr, hits EOF on stdin and dies with
stdout empty), then stdout to parse, then stderr to report. list()
accepts every exit value so the code arrives in the result instead of
an exception.

Path arguments are added with quoting disabled; the default handling
re-quotes a token containing spaces and the quotes reach the child.
Both streams decode as ReplayFile.CHARSET so the byte round trip
cannot split across two charsets."
```

---

### Task 6: ObservablePicker — the dialog

The dialog is the one part no harness covers (the spec accepts this); its verification here is a probe launcher over the Task 1 fixture plus an explicit checklist, and Task 8's acceptance covers it on real trajectories.

**Files:**
- Create: `src/my/ramses/dyngraph/ObservablePicker.java`

**Interfaces:**
- Consumes: `ObservableIndex` (`types`, `instances`, `TypeEntry`, `Instance`) from Task 2; `Selection`, `Selection.requiresSub` from Task 3.
- Produces:
  - `ObservablePicker.show(java.awt.Window owner, ObservableIndex index)` returning `java.util.List<Selection>` — modal; blocks until closed; returns the picks in list order (which becomes column order in the `.cur` and curve order in the `.plt`), or **null on cancel/close**. Never returns an empty list: Plot stays disabled while Selected is empty.

- [ ] **Step 1: Write `ObservablePicker`**

```java
package my.ramses.dyngraph;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * The modal observable picker: one category selector, a type-to-filter field
 * over a scrolling name list, an observable dropdown (plus a second dropdown
 * for a machine's SOE/SOT sub-list), and a running Selected list.
 *
 * <p>A filter field over a list, not one dropdown row per category: a
 * transmission network can carry thousands of buses, and a JComboBox holding
 * them is unusable. The running Selected list is itself the improvement over
 * the deleted Intel dialog, which took at most one observable per category
 * per round and reopened itself.
 *
 * <p>No parsing, no label composition, no process calls live here - those
 * are ObservableIndex, Selection and DyngraphRunner, kept out of the dialog
 * so they stay verifiable without a display. Hand-written rather than a
 * NetBeans form because the controls are populated from parsed data and the
 * layout does not vary; RamsesUI.form is untouched.
 *
 * <p>The dialog opens with an empty Selected list every time: carrying a
 * selection across invocations would need revalidation against a freshly
 * loaded trajectory and silent dropping of absent names. Duplicate
 * selections are allowed - the console permits them and they produce
 * duplicate columns, which is occasionally what someone wants.
 */
public final class ObservablePicker extends JDialog {

    /** Index tag and what the category selector shows for it. */
    private static final String[][] CATEGORY_DISPLAY = {
        {"BUS", "BUS"},
        {"SHUNT", "SHUNT"},
        {"LOAD", "LOAD"},
        {"BRANCH", "BRANCH"},
        {"SYNC", "SYNC"},
        {"INJ", "INJECTOR"},
        {"LINK", "LINK"},
        {"DCTL", "DCTL"},
    };

    private static final Color DISABLED_GRAY = Color.GRAY;

    private final ObservableIndex index;
    private List<Selection> result = null;

    private final JComboBox<CategoryItem> categoryBox = new JComboBox<CategoryItem>();
    private final JTextField filterField = new JTextField(12);
    private final DefaultListModel<ObservableIndex.Instance> nameModel =
            new DefaultListModel<ObservableIndex.Instance>();
    private final JList<ObservableIndex.Instance> nameList =
            new JList<ObservableIndex.Instance>(nameModel);
    private final JComboBox<ObservableChoice> observableBox = new JComboBox<ObservableChoice>();
    private final JLabel subLabel = new JLabel("Controller observable");
    private final JComboBox<String> subBox = new JComboBox<String>();
    private final JButton addButton = new JButton("Add");
    private final DefaultListModel<Selection> selectedModel = new DefaultListModel<Selection>();
    private final JList<Selection> selectedList = new JList<Selection>(selectedModel);
    private final JButton removeButton = new JButton("Remove");
    private final JButton clearButton = new JButton("Clear");
    private final JButton plotButton = new JButton("Plot");
    private final JButton cancelButton = new JButton("Cancel");

    /** Guards the revert inside the observable box's ActionListener. */
    private boolean rebuildingObservables = false;
    private ObservableChoice lastEnabledChoice = null;

    /**
     * Shows the picker modally. Returns the picked selections in list order -
     * the order that becomes column order in the .cur and curve order in the
     * .plt, and what Remove operates on - or null when the user cancelled.
     * Never returns an empty list: Plot stays disabled while Selected is
     * empty, so the "no .plt written" case cannot arise from the UI.
     */
    public static List<Selection> show(Window owner, ObservableIndex index) {
        ObservablePicker picker = new ObservablePicker(owner, index);
        picker.setVisible(true); // modal: blocks until disposed
        return picker.result;
    }

    private ObservablePicker(Window owner, ObservableIndex index) {
        super(owner, "Select Observables", Dialog.ModalityType.APPLICATION_MODAL);
        this.index = index;
        buildLayout();
        wireBehaviour();
        // Only categories that actually carry instances are offered, the way
        // the console omits empty categories from its keyword menu.
        DefaultComboBoxModel<CategoryItem> categories = new DefaultComboBoxModel<CategoryItem>();
        for (String[] entry : CATEGORY_DISPLAY) {
            if (!index.instances(entry[0]).isEmpty()) {
                categories.addElement(new CategoryItem(entry[0], entry[1]));
            }
        }
        categoryBox.setModel(categories);
        if (categories.getSize() > 0) {
            categoryBox.setSelectedIndex(0);
        }
        rebuildNames();
        rebuildObservables();
        updateButtons();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(520, 560));
        setLocationRelativeTo(owner);
    }

    private void buildLayout() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        top.add(new JLabel("Category"), c);
        c.gridx = 1;
        top.add(categoryBox, c);
        c.gridx = 2;
        top.add(new JLabel("Filter"), c);
        c.gridx = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        top.add(filterField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 4;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        nameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nameList.setVisibleRowCount(10);
        JScrollPane namePane = new JScrollPane(nameList);
        namePane.setBorder(BorderFactory.createTitledBorder("Names"));
        top.add(namePane, c);

        c.gridy = 2;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;
        c.gridx = 0;
        top.add(new JLabel("Observable"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        top.add(observableBox, c);

        c.gridy = 3;
        c.gridx = 0;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        top.add(subLabel, c);
        c.gridx = 1;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        top.add(subBox, c);

        c.gridy = 4;
        c.gridx = 0;
        c.gridwidth = 4;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0;
        top.add(addButton, c);

        c.gridy = 5;
        c.gridx = 0;
        c.gridwidth = 4;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        selectedList.setVisibleRowCount(8);
        JScrollPane selectedPane = new JScrollPane(selectedList);
        selectedPane.setBorder(BorderFactory.createTitledBorder("Selected"));
        top.add(selectedPane, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(removeButton);
        buttons.add(clearButton);
        buttons.add(plotButton);
        buttons.add(cancelButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
    }

    private void wireBehaviour() {
        nameList.setCellRenderer(new NameRenderer());
        observableBox.setRenderer(new ChoiceRenderer());

        categoryBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                rebuildNames();
                rebuildObservables();
                updateButtons();
            }
        });
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                rebuildNames();
                rebuildObservables();
                updateButtons();
            }

            public void removeUpdate(DocumentEvent e) {
                rebuildNames();
                rebuildObservables();
                updateButtons();
            }

            public void changedUpdate(DocumentEvent e) {
                rebuildNames();
                rebuildObservables();
                updateButtons();
            }
        });
        nameList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    // Recomputed on every (category, instance) change, not
                    // merely category change: injector/link/DCTL observables
                    // and a machine's SOE/SOT sub-lists belong to the
                    // instance, never to the category.
                    rebuildObservables();
                    updateButtons();
                }
            }
        });
        observableBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (rebuildingObservables) {
                    return;
                }
                ObservableChoice choice = (ObservableChoice) observableBox.getSelectedItem();
                if (choice != null && !choice.enabled) {
                    // Greyed, not hidden: SOE on a machine with EXC 0 stays
                    // visible but cannot be chosen - a replay file naming it
                    // would loop to EOF.
                    observableBox.setSelectedItem(lastEnabledChoice);
                    return;
                }
                lastEnabledChoice = choice;
                rebuildSubList();
                updateButtons();
            }
        });
        selectedList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateButtons();
                }
            }
        });
        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Selection picked = currentSelection();
                if (picked != null) {
                    selectedModel.addElement(picked);
                    updateButtons();
                }
            }
        });
        removeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int[] rows = selectedList.getSelectedIndices();
                for (int i = rows.length - 1; i >= 0; i--) {
                    selectedModel.remove(rows[i]);
                }
                updateButtons();
            }
        });
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectedModel.clear();
                updateButtons();
            }
        });
        plotButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                List<Selection> picked = new ArrayList<Selection>();
                for (int i = 0; i < selectedModel.size(); i++) {
                    picked.add(selectedModel.get(i));
                }
                result = picked;
                dispose();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose(); // result stays null
            }
        });
    }

    private String currentCategory() {
        CategoryItem item = (CategoryItem) categoryBox.getSelectedItem();
        return item == null ? null : item.tag;
    }

    private ObservableIndex.Instance currentInstance() {
        return nameList.getSelectedValue();
    }

    private static boolean isUntyped(String category) {
        return "INJ".equals(category) || "LINK".equals(category) || "DCTL".equals(category);
    }

    private void rebuildNames() {
        String category = currentCategory();
        ObservableIndex.Instance keep = currentInstance();
        nameModel.clear();
        if (category == null) {
            return;
        }
        String filter = filterField.getText().toLowerCase();
        for (ObservableIndex.Instance instance : index.instances(category)) {
            if (filter.isEmpty() || instance.name.toLowerCase().contains(filter)) {
                nameModel.addElement(instance);
            }
        }
        if (keep != null && nameModel.contains(keep)) {
            nameList.setSelectedValue(keep, true);
        }
    }

    private void rebuildObservables() {
        rebuildingObservables = true;
        try {
            String category = currentCategory();
            ObservableIndex.Instance instance = currentInstance();
            DefaultComboBoxModel<ObservableChoice> model =
                    new DefaultComboBoxModel<ObservableChoice>();
            if (category != null) {
                if (isUntyped(category)) {
                    if (instance != null) {
                        for (String obsName : instance.obs) {
                            model.addElement(new ObservableChoice(null, obsName, true));
                        }
                    }
                } else {
                    for (ObservableIndex.TypeEntry type : index.types(category)) {
                        boolean enabled = true;
                        if ("SOE".equals(type.keyword)) {
                            enabled = instance != null && !instance.exc.isEmpty();
                        } else if ("SOT".equals(type.keyword)) {
                            enabled = instance != null && !instance.tor.isEmpty();
                        }
                        model.addElement(new ObservableChoice(type.keyword, type.label, enabled));
                    }
                }
            }
            observableBox.setModel(model);
            // Preselect the first enabled entry, so the common case is two
            // clicks: pick a name, press Add.
            lastEnabledChoice = null;
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).enabled) {
                    observableBox.setSelectedIndex(i);
                    lastEnabledChoice = model.getElementAt(i);
                    break;
                }
            }
            if (lastEnabledChoice == null) {
                observableBox.setSelectedItem(null);
            }
        } finally {
            rebuildingObservables = false;
        }
        rebuildSubList();
    }

    private void rebuildSubList() {
        String category = currentCategory();
        ObservableIndex.Instance instance = currentInstance();
        ObservableChoice choice = (ObservableChoice) observableBox.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>();
        boolean visible = false;
        if ("SYNC".equals(category) && instance != null && choice != null) {
            if ("SOE".equals(choice.keyword)) {
                visible = true;
                for (String obsName : instance.exc) {
                    model.addElement(obsName);
                }
            } else if ("SOT".equals(choice.keyword)) {
                visible = true;
                for (String obsName : instance.tor) {
                    model.addElement(obsName);
                }
            }
        }
        subBox.setModel(model);
        if (model.getSize() > 0) {
            subBox.setSelectedIndex(0);
        }
        subLabel.setVisible(visible);
        subBox.setVisible(visible);
    }

    /** The complete selection the current row resolves to, or null. */
    private Selection currentSelection() {
        String category = currentCategory();
        ObservableIndex.Instance instance = currentInstance();
        ObservableChoice choice = (ObservableChoice) observableBox.getSelectedItem();
        if (category == null || instance == null || choice == null || !choice.enabled) {
            return null;
        }
        if (isUntyped(category)) {
            return new Selection(category, null, null, instance.name, choice.display);
        }
        if (Selection.requiresSub(category, choice.keyword)) {
            String sub = (String) subBox.getSelectedItem();
            if (sub == null) {
                return null;
            }
            return new Selection(category, choice.keyword, choice.display, instance.name, sub);
        }
        return new Selection(category, choice.keyword, choice.display, instance.name, null);
    }

    private void updateButtons() {
        // Add is disabled unless the current row resolves to a complete
        // selection; Plot is disabled while Selected is empty.
        addButton.setEnabled(currentSelection() != null);
        boolean any = !selectedModel.isEmpty();
        removeButton.setEnabled(any && selectedList.getSelectedIndex() >= 0);
        clearButton.setEnabled(any);
        plotButton.setEnabled(any);
    }

    /** Category selector entry: the index tag plus what the user sees. */
    private static final class CategoryItem {
        final String tag;
        final String display;

        CategoryItem(String tag, String display) {
            this.tag = tag;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    /**
     * Observable dropdown entry. For the five typed categories, a TYPES
     * keyword/label pair (disabled for SOE/SOT when the current machine's
     * sub-list is empty); for INJ/LINK/DCTL, an observable name from the
     * current instance's OBS block, with {@code keyword} null.
     */
    private static final class ObservableChoice {
        final String keyword;
        final String display;
        final boolean enabled;

        ObservableChoice(String keyword, String display, boolean enabled) {
            this.keyword = keyword;
            this.display = display;
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    /** Greys instances that cannot be picked, and keeps blank names visible. */
    private final class NameRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int row, boolean selected, boolean focused) {
            ObservableIndex.Instance instance = (ObservableIndex.Instance) value;
            // An all-blank name is legitimate; a lone space keeps its row a
            // normal height. Add uses instance.name, so this display-only
            // substitution never leaks into the replay file.
            String text = instance.name.isEmpty() ? " " : instance.name;
            Component c = super.getListCellRendererComponent(list, text, row, selected, focused);
            if (isUntyped(currentCategory()) && instance.obs.isEmpty()) {
                // Nothing to pick on this instance. The console does the
                // same - it hides such DCTLs from its prompt
                // (selec_observ.f90:399-408) - and a replay file naming one
                // would loop to EOF. Greyed here; Add stays disabled for it.
                c.setForeground(DISABLED_GRAY);
            }
            return c;
        }
    }

    /** Greys disabled dropdown entries (SOE/SOT with an empty sub-list). */
    private static final class ChoiceRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int row, boolean selected, boolean focused) {
            Component c = super.getListCellRendererComponent(list, value, row, selected, focused);
            if (value instanceof ObservableChoice && !((ObservableChoice) value).enabled) {
                c.setForeground(DISABLED_GRAY);
            }
            return c;
        }
    }
}
```

- [ ] **Step 2: Compile and confirm the harness is untouched**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant compile
```
```bash
tools/picker-harness.sh
```
Expected: BUILD SUCCESSFUL and `ALL CHECKS PASSED` — the dialog must not have perturbed any pinned behaviour.

- [ ] **Step 3: Drive the dialog by hand over the harness fixture**

```bash
cat > /tmp/claude/picker-probe/PickerProbe.java <<'EOF'
import my.ramses.dyngraph.ObservableIndex;
import my.ramses.dyngraph.ObservablePicker;
import my.ramses.dyngraph.PickerHarness;
import my.ramses.dyngraph.ReplayFile;
import my.ramses.dyngraph.Selection;
import java.util.List;

public class PickerProbe {
    public static void main(String[] args) throws Exception {
        ObservableIndex index = ObservableIndex.parse(PickerHarness.fixture());
        List<Selection> picks = ObservablePicker.show(null, index);
        if (picks == null) {
            System.out.println("cancelled");
            return;
        }
        for (Selection s : picks) {
            System.out.println("picked: " + s.label());
        }
        System.out.print(ReplayFile.emit("output.trj", picks));
    }
}
EOF
```
```bash
javac -cp build/classes -d /tmp/claude/picker-probe /tmp/claude/picker-probe/PickerProbe.java
```
```bash
java -cp build/classes:/tmp/claude/picker-probe PickerProbe
```
Walk this checklist in the dialog that opens (needs a display):

1. The Category box lists `BUS SHUNT LOAD BRANCH SYNC INJECTOR LINK DCTL` — all eight, since every fixture category is non-empty.
2. BUS shows four names including ` LEADBUS` (with its leading blank visible) and `END`; typing `bus` in Filter narrows to **three** rows — `BUS1`, `BUS2` **and ` LEADBUS`**, because the filter is a case-insensitive `contains` and ` leadbus` contains `bus`; only `END` drops out. Clearing it restores all four. (Typing `bus1` narrows to one.)
3. With no name selected, Add is disabled; selecting `BUS1` enables it (the Observable box preselects `voltage magnitude (pu)`).
4. SYNC → `GEN1`: choosing `observable of excitation controller` (SOE) makes the second dropdown appear holding `VF`; Add produces `sync mach GEN1: excit control: VF` in Selected.
5. SYNC → `GEN2`: the SOE entry is greyed and cannot be chosen (the selection snaps back); SOT works and offers `Pm`.
6. INJECTOR → `INJ1` offers only `P`; `INJ2` offers `Q` and `omega` — per-instance, not pooled.
7. DCTL → `DCTL2` is greyed in the name list and Add stays disabled for it; `DCTL1` offers `ST`.
8. Add `bus BUS1: voltage magnitude (pu)` twice — duplicates are allowed and both rows appear.
9. Remove deletes exactly the highlighted Selected row; Clear empties the list; Plot is disabled whenever Selected is empty.
10. Press Plot with a few rows: the terminal prints one `picked: <label>` per row in list order, then a replay stream matching the grammar (keyword, name, optional sub, blank line, `S`).
11. Run again and press Cancel (and once more closing via the title bar ✕): the terminal prints `cancelled` both times.

Expected terminal output for step 10, if the rows are `bus BUS1`, `sync mach GEN1: excit control: VF`, `injector INJ2: omega`:
```
picked: bus BUS1: voltage magnitude (pu)
picked: sync mach GEN1: excit control: VF
picked: injector INJ2: omega
output.trj
BM
BUS1
SOE
GEN1
VF
I
INJ2
omega

S
```

- [ ] **Step 4: Commit**

```bash
git add src/my/ramses/dyngraph/ObservablePicker.java
```
```bash
git commit -m "Add the observable picker dialog

One filter field over a scrolling name list instead of the Intel
dialog's eight dropdown rows: a transmission network can carry
thousands of buses, and a JComboBox holding them is unusable. Nobody
has muscle memory to protect - that dialog shipped in no release.

The observable model is recomputed on every (category, instance)
change, not merely category, because injector/link/DCTL observables
and a machine's SOE/SOT sub-lists belong to the instance. Choices
that lead nowhere are greyed rather than hidden, matching the
console, which skips DCTLs with no observables; a replay file naming
one would loop to EOF. Add and Plot stay disabled until the row, or
the list, is complete.

Hand-written JDialog, no .form change: its controls are populated
from parsed data and its layout does not vary."
```

---

### Task 7: RamsesUI orchestration and README

**Files:**
- Modify: `src/my/ramses/RamsesUI.java:3263-3282` (the handler), `:35` (imports), helpers inserted after the handler
- Modify: `README.md:77`
- Modify: `README.md:83`

**Interfaces:**
- Consumes: `DyngraphRunner` (`list`, `plot`, `ListResult`, `PlotListener`) from Task 5; `ObservableIndex.parse`/`isEmpty` from Task 2; `ObservablePicker.show` from Task 6; `ReplayFile.write` from Task 4; the existing `escapeHtml(String)` helper at `RamsesUI.java:4751`, and the existing fields `dyngraphExec`, `myTempDir`, `WinEnvironment`, `viewCurvesButton`, `saveCurrentCurveButton`, `runDyngraphButton`.
- Produces: the rewritten `runDyngraphButtonActionPerformed` plus two private helpers `openPickerFromListing` and `startPlotRun`.

- [ ] **Step 1: Add the imports**

After `import my.ramses.compile.ModelCompiler;` (line 35), add:

```java
import my.ramses.dyngraph.DyngraphRunner;
import my.ramses.dyngraph.ObservableIndex;
import my.ramses.dyngraph.ObservablePicker;
import my.ramses.dyngraph.ReplayFile;
import my.ramses.dyngraph.Selection;
```

- [ ] **Step 2: Replace the handler**

Replace the whole method at `RamsesUI.java:3263-3282` (keeping its `//GEN-FIRST`/`//GEN-LAST` marker comments exactly) with:

```java
    private void runDyngraphButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runDyngraphButtonActionPerformed
        if (dyngraphExec == null || !dyngraphExec.exists()) {
            JOptionPane.showMessageDialog(this, "<html>The file <B>dyngraph</B> does not exist.</html>", "Executable not found!", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // The single trajectory contract: a saved .trj is copied here on
        // load, so the picker needs no file chooser of its own.
        final File trajectory = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trj");
        final DyngraphRunner runner = new DyngraphRunner(dyngraphExec, myTempDir, WinEnvironment);

        // get_observ_name rewinds the trajectory several times, so --list
        // scales with file size and must not run on the EDT: a SwingWorker
        // with a wait cursor, and the picker opens from done() on the EDT.
        runDyngraphButton.setEnabled(false);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<DyngraphRunner.ListResult, Void>() {
            @Override
            protected DyngraphRunner.ListResult doInBackground() throws IOException {
                return runner.list(trajectory);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                runDyngraphButton.setEnabled(true);
                DyngraphRunner.ListResult listing;
                try {
                    listing = get();
                } catch (Exception ex) {
                    Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
                    JOptionPane.showMessageDialog(RamsesUI.this,
                            "<html>Could not run <B>dyngraph --list</B>:<br>"
                            + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                            "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                openPickerFromListing(runner, trajectory, listing);
            }
        }.execute();
    }//GEN-LAST:event_runDyngraphButtonActionPerformed
```

- [ ] **Step 3: Add the two helpers**

Insert immediately after the `//GEN-LAST:event_runDyngraphButtonActionPerformed` line:

```java
    /**
     * Continues Extract Curves on the EDT once {@code --list} has returned.
     *
     * <p>Detection is exit-status-first, not header-first: an old DYNGRAPH
     * ignores {@code --list}, writes its filename prompt to stderr (main.f90
     * sets {@code log=0}), hits EOF on its closed stdin and exits non-zero
     * with stdout empty - so "binary too old" and "trajectory file missing"
     * are both non-zero exits, told apart by the captured stderr, and the
     * header check (inside ObservableIndex.parse) only catches a zero-exit
     * program that printed something unexpected. Every failure is a modal
     * dialog leaving no partial state; the picker never opens on one.
     */
    private void openPickerFromListing(DyngraphRunner runner, File trajectory,
            DyngraphRunner.ListResult listing) {
        if (listing.exitCode != 0) {
            String reason;
            if (listing.stdout.trim().isEmpty()) {
                reason = "The bundled DYNGRAPH does not support <B>--list</B>."
                        + "<br>It reported:<br>" + escapeHtml(listing.stderr);
            } else {
                reason = "<B>dyngraph --list</B> failed (exit " + listing.exitCode
                        + "):<br>" + escapeHtml(listing.stderr);
            }
            JOptionPane.showMessageDialog(this, "<html>" + reason + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ObservableIndex index;
        try {
            index = ObservableIndex.parse(listing.stdout);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "<html>The observable index could not be read:<br>"
                    + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (index.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "<html>The trajectory file carries no observables to plot.</html>",
                    "Nothing to extract", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<Selection> selections = ObservablePicker.show(this, index);
        if (selections == null || selections.isEmpty()) {
            return; // cancelled; nothing changed
        }
        startPlotRun(runner, trajectory, selections);
    }

    /**
     * Writes the replay file and starts the non-interactive {@code -t} run.
     *
     * <p>The output base is the fixed, absolute {@code <temp>/tempGnupOut}:
     * not a free parameter, because viewCurvesButton opens tempGnupOut.plt
     * by name and saveCurrentCurveButton rewrites the .plt by
     * string-replacing the absolute tempGnupOut.cur path inside it. A
     * relative base, or any other name, still plots correctly and silently
     * breaks both buttons.
     */
    private void startPlotRun(DyngraphRunner runner, File trajectory,
            java.util.List<Selection> selections) {
        File selCmd = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "sel.cmd");
        File outputBase = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut");
        try {
            // sel.cmd is written into the temp directory and left there
            // after the run, like output.trj and tempGnupOut.*: it is the
            // first thing worth looking at when a plot comes out wrong.
            ReplayFile.write(selCmd, trajectory.getAbsolutePath(), selections);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not write the selection file:<br>"
                    + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Disabled before the run starts, not merely left alone: after a
        // previous successful extraction they are enabled, and a failed
        // re-run must not leave them pointing at the stale tempGnupOut.plt
        // and .cur that DYNGRAPH may have truncated or half-written.
        viewCurvesButton.setEnabled(false);
        saveCurrentCurveButton.setEnabled(false);
        runDyngraphButton.setEnabled(false);
        try {
            runner.plot(selCmd, outputBase, new DyngraphRunner.PlotListener() {
                public void onFinished(final int exitCode, final String stderr) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            runDyngraphButton.setEnabled(true);
                            if (exitCode == 0) {
                                // Success is silent, exactly as today: the
                                // result buttons light up and that is all.
                                viewCurvesButton.setEnabled(true);
                                saveCurrentCurveButton.setEnabled(true);
                            } else {
                                JOptionPane.showMessageDialog(RamsesUI.this,
                                        "<html>Curve extraction failed (exit " + exitCode
                                        + "):<br>" + escapeHtml(stderr) + "</html>",
                                        "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    });
                }
            });
        } catch (IOException ex) {
            runDyngraphButton.setEnabled(true);
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not start <B>dyngraph</B>:<br>"
                    + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
        }
    }
```

- [ ] **Step 4: Update the README**

At `README.md:77`, change:

```markdown
| DYNGRAPH | Curve viewer | yes (console) | yes (console) | yes (console) |
```

to:

```markdown
| DYNGRAPH | Curve viewer | yes | yes | yes |
```

At `README.md:83`, replace the paragraph:

```markdown
DYNGRAPH is the same console program on all three platforms and opens in a terminal window. Windows previously shipped a committed Intel build that drew its own observable-picker dialog; that build is gone, and with it the dialog — the console prompts replace it everywhere.
```

with:

```markdown
DYNGRAPH is the same console program on all three platforms, but Extract Curves no longer opens it in a terminal window: STEPSS reads the trajectory's observables with `dyngraph --list`, presents them in a selection dialog, and drives the extraction through a generated command file (`-t`). Running DYNGRAPH by hand, outside STEPSS, still gives the console prompts.
```

Line 13 ("Extract Curves launches the bundled DYNGRAPH viewer on saved output trajectories") stays as is.

- [ ] **Step 5: Build and run the harness**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
ant clean jar
```
Expected: BUILD SUCCESSFUL.
```bash
tools/picker-harness.sh
```
Expected: `ALL CHECKS PASSED`.
```bash
tools/compile-harness.sh
```
Expected: `ALL CHECKS PASSED` and `ALL CONSOLE SINK CHECKS PASSED` — the existing harnesses must be unaffected.

- [ ] **Step 6: Confirm the form and generated blocks were not disturbed**

```bash
git diff --stat src/my/ramses/RamsesUI.form
```
Expected: **no output** — the form is untouched (its tooltip "Click to initiate dialog for selecting the observables you want to plot" simply became true again).
```bash
git diff src/my/ramses/RamsesUI.java | grep -c "GEN-BEGIN\|GEN-END" || true
```
Expected: prints `0` — no edit landed inside a NetBeans-generated block. (The handler body between `GEN-FIRST`/`GEN-LAST` is user code, as with every other handler in the file.) The `|| true` is needed because `grep -c` exits 1 when the count is zero, which is the passing case here.

- [ ] **Step 7: Commit**

```bash
git add src/my/ramses/RamsesUI.java README.md
```
```bash
git commit -m "Drive Extract Curves through the picker instead of a terminal

runDyngraphButtonActionPerformed becomes orchestration: list, parse,
show, write, plot, enable buttons. Failure detection is
exit-status-first - a non-zero --list exit with empty stdout is the
too-old-binary case, its prompt having gone to stderr - and only a
zero exit gets its header parsed. Every failure is a modal dialog and
the picker never opens on one.

The result buttons are disabled before the plot run starts, not
merely left alone: after a previous successful extraction they are
enabled, and a failed re-run must not leave them pointing at a stale
or truncated tempGnupOut.plt. The output base stays the absolute
<temp>/tempGnupOut both result buttons hardcode.

The README's claim that the console prompts are what the user meets
is no longer true and goes; the tooltip promising a selection dialog
becomes true again without an edit."
```

---

### Task 8: Manual acceptance and the results record

Manual acceptance — pick, plot, view curves on all three platforms — is the only way to cover the dialog itself, which the harness deliberately does not touch. Follow the shape of `docs/superpowers/plans/compile-acceptance-results.md` and `platform-matrix-results.md`: record what actually ran, mark hardware-less rows `PENDING` with the hardware named, and never infer a platform's result from another's.

**Files:**
- Create: `docs/superpowers/plans/picker-acceptance-results.md`

**Interfaces:**
- Consumes: everything above; a built `dist/stepss.jar`; a test system from `../stepss-test-systems` (any case that produces an `output.trj` with machines and, ideally, injectors).
- Produces: `picker-acceptance-results.md` with a `PICKER_ACCEPTANCE: pass` or `pass-with-gaps` verdict line and one row per check below.

- [ ] **Step 1: Linux — the full happy path**

```bash
cd /home/apetros/Code/stepss/stepss-java-ui
```
```bash
java -jar dist/stepss.jar
```

1. Load a test system's data and disturbance files, enable *Save output trajectories*, and run a dynamic simulation to completion.
2. Press **Extract Curves**. Expected: a wait cursor, then the picker dialog — **no terminal window**.
3. Filter buses, add two bus voltage magnitudes and one machine's rotor speed; the Selected rows read exactly like desc_obs (`bus <name>: voltage magnitude (pu)`, `sync mach <name>: rotor speed (pu)`).
4. Press **Plot**. Expected: no dialog on success; **Preview Curve** and **Save Current Curve** become enabled.
5. Press **Preview Curve**. Expected: a gnuplot window whose curve titles match the Selected rows just picked (up to Fortran's internal blank padding).
6. Press **Save Current Curve** and save; confirm the `.plt`/`.cur` pair lands and the `.plt` references the saved `.cur` name.
7. Confirm `sel.cmd` was left in the temp directory and its content matches the picks (keyword/name/sub groups, blank line, `S`).

Record each as a row.

- [ ] **Step 2: Linux — sub-lists and greying on a real network**

On a case with an exciter-carrying machine and (if available) injectors: pick `SOE` → the machine's own exciter observables appear; pick an injector → its own `OBS` names appear and differ between injectors where models differ; a machine with no torque controller has `SOT` greyed. Record.

- [ ] **Step 3: Linux — failure paths**

1. Delete `<temp>/output.trj` (the fingerprinted temp directory the GUI reports; or use a fresh launch without a simulation, if the button is enabled from a loaded trajectory only, load then delete). Press **Extract Curves**. Expected: an error dialog carrying DYNGRAPH's own stderr (`File ... does not exist`); the picker never opens; **Preview Curve**/**Save Current Curve** unchanged.

   **The headline will read "The bundled DYNGRAPH does not support `--list`". That is expected — do not file it as a bug.** `--list` on a missing file exits non-zero with *empty stdout* and the complaint on stderr (`stepss-dyngraph/src/main.f90:160-170`), which is byte-for-byte how an old binary fails; DYNGRAPH's own gate documents the two as indistinguishable (`tools/smoke_gate.sh:144-151`). The spec's failure table knowingly accepts stderr as the disambiguator. Record the row as PASS if the stderr text is present and correct.
2. After a successful extraction (buttons enabled), force a failing re-run the same way. Expected: the failure dialog, and both result buttons **stay disabled** — they were disabled before the run started.
3. Cancel the picker. Expected: nothing changes, no files written.

Record each.

- [ ] **Step 4: Windows and macOS rows**

Run Steps 1-3 on Windows x86_64 and macOS arm64. On Windows, additionally confirm no console window flashes during either invocation, and that a network stored under a path containing spaces still extracts (the `addArgument(value, false)` case). If the hardware is not available, mark each row `PENDING` naming the hardware, exactly as the compile and platform-matrix records do.

- [ ] **Step 5: Write the results file and commit**

Create `docs/superpowers/plans/picker-acceptance-results.md`: the verdict line (`PICKER_ACCEPTANCE: pass` or `pass-with-gaps`), the commit under test, the environment (as in the two precedent files), then one table row per check from Steps 1-4 with its outcome. State plainly which rows were not run and why. Note the one gap no gate covers, verbatim from the spec: the `.plt` this UI produces is the no-`-eps` branch, which no upstream golden pins — unchanged from today, with the follow-up belonging in `stepss-dyngraph`.

```bash
git add docs/superpowers/plans/picker-acceptance-results.md
```
```bash
git commit -m "Record picker acceptance across the platform matrix

Linux rows verified on this machine; Windows and macOS rows are
recorded as PENDING with the hardware named, never inferred, matching
the compile and platform-matrix precedents. The dialog is the one
part no harness covers, so this checklist is its gate; the .plt the
UI produces remains the no-eps branch no upstream golden pins, a gap
the spec accepts and assigns to stepss-dyngraph."
```

---

## Self-Review

**Spec coverage.** Every spec section maps to a task: the package structure and harness-first split to Task 1 and the File Structure; "Interface: reading the index" — count-driven parsing, the version/header rules, the leading-blank rule, per-instance sub-lists, malformed-input line numbers — to Task 2; "Display labels" (including SOE/SOT rendering as `excit control:`/`torque control:`) to Task 3; "Interface: writing the replay file", the I/T/D map with its `selec_observ.f90` references, the blank-line + `S` terminator, and the charset rationale to Task 4; "Running DYNGRAPH" — capture-not-inherit, off-EDT, exit-status-first, no `-eps`, no terminal — to Task 5 with the message logic in Task 7; "The dialog" — filter-over-list, per-(category, instance) observable models, symmetric greying, Add/Plot gating, duplicates allowed, empty Selected on open — to Task 6; the Approach diagram, the fixed `tempGnupOut` base, the failure table, the disable-before-run rule, and both README edits to Task 7; "Testing" and manual acceptance to Tasks 1-4 and 8. All five fixture extensions the spec demands (second injector with a differing OBS list, `EXC 0` machine, leading-blank name, `END`/`S` name collisions, empty name line) are present in `FIXTURE_LINES` and asserted by name.

**Placeholder scan.** No TBDs. Every code step carries complete, compilable Java; every "run and watch it fail" step states the exact expected `FAIL` line(s) and exit status. Task 1's stub `ObservableIndex` and Tasks 3-4's stub bodies are the deliberate red states of the adapted TDD cycle, each replaced within its own or the following task — not placeholders left behind. Task 8's outcomes are results to record, not inputs to invent. Task 5 deviates from strict check-first because `DyngraphRunner` is deliberately outside the harness (the spec's own split); its gate is the behavioural probe diffing the produced `.cur` against DYNGRAPH's golden.

**Type consistency.** `ObservableIndex.parse(String)`, `types(String)`, `instances(String)`, `isEmpty()`, `CATEGORIES`, `TYPED_CATEGORIES`, `TypeEntry.keyword/label`, `Instance.name/exc/tor/obs` are used with exactly those names in `PickerHarness` (Tasks 2, 4), `Selection` (Task 3), `ObservablePicker` (Task 6), the probes (Task 5) and `RamsesUI` (Task 7). `Selection(String, String, String, String, String)`, `requiresSub`, `label()` match between definition, harness and dialog. `ReplayFile.CHARSET/emit/write` match between definition, harness, `DyngraphRunner` and `RamsesUI`. `DyngraphRunner(File, File, Map)`, `list(File)` → `ListResult{exitCode, stdout, stderr}`, `plot(File, File, PlotListener)` and `PlotListener.onFinished(int, String)` match between Task 5, both probes and Task 7. `ObservablePicker.show(Window, ObservableIndex)` → `List<Selection>` matches Tasks 6 and 7.

**One dependency worth naming.** Task 5's Steps 3-5 and Task 8 need the pinned DYNGRAPH v1.2.0 payload (fetched by `ant jar`) and, for the fixture, a local gfortran; both exist on this machine per the compile-plan acceptance. Everything the harness pins (Tasks 1-4) needs neither.
