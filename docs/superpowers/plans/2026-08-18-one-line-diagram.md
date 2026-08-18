# Annotated One-Line Diagrams Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `stepss-java-ui` an optional annotated SVG slot whose template Helios fills in on every power flow run, shown in a zoomable window of its own, and bundle the 6-bus microgrid as the power-flow-only example that demonstrates it.

**Architecture:** Helios already renders annotated diagrams through its `1` main-menu command, so the engine side is three extra lines in the command file the application already writes, executed by the same run that solves the power flow. The result is displayed by a new `my.stepss.diagram` package: Batik parses the SVG once, and every zoom or pan re-renders only the visible rectangle at viewport resolution through Batik's `KEY_AOI` hint, so cost is bounded by the window rather than by the zoom level. The examples descriptor learns optional slots, because a power-flow-only case has no disturbance and no observables.

**Tech Stack:** Java 11 (`javac.source=11`), Swing with FlatLaf, Apache Ant, Apache Commons Exec, Apache Batik 1.19. No unit test framework: `tools/*-harness.sh` scripts running a `main()` on the build classpath are the substitute.

**Spec:** `docs/superpowers/specs/2026-08-18-one-line-diagram-design.md`

## Global Constraints

- **Java 11 syntax only.** No records, no `switch` expressions, no text blocks. Value holders are plain classes with final fields, following `StepssUI.HeliosStatusDialog`.
- **No em-dashes** in any prose, comment, commit message or user-facing string.
- **Never use compound shell commands with git.** `cd` in its own command, then each git command separately.
- **Never commit `.claude/` or `.mcp.json`.** Both are already gitignored here; do not add paths that defeat that.
- **Batik version is 1.19**, and its two external dependencies are pinned by its own pom: `xmlgraphics-commons` **2.11** and `xml-apis-ext` **1.3.04**.
- **`xml-apis` 1.4.01 must NOT be added.** Batik declares it, but it re-declares `org.w3c.dom` interfaces the JDK already ships, and on Java 11 it shadows the platform's newer versions and produces `NoSuchMethodError` at runtime. Only `xml-apis-ext` is needed, because that one carries the SVG and SMIL DOM interfaces the JDK does not have.
- **`stepss.format` in `ScenarioFile` stays at `1`.** `checkFormat()` throws on `format < FORMAT`, so bumping it would make this build reject every `.cfg` users have already saved.
- **CI action versions** are uniform across all `stepss-*` repos. If any workflow is touched: `actions/checkout@v7`, `softprops/action-gh-release@v3`.
- **Build commands:** `ant compile` for classes, `ant stage-payloads` for the example and toolchain payloads, `ant jar` for the shipping jar. Harnesses need `ant compile` first; the examples harness also needs `ant stage-payloads`.

---

## File Structure

**New files:**

| Path | Responsibility |
|---|---|
| `src/my/stepss/HeliosOutcome.java` | The one decision about what a Helios exit status means: severity, headline, detail. Rendered as a dialog by `StepssUI` and as a banner by `DiagramWindow`. |
| `src/my/stepss/WindowCascade.java` | Placing a non-modal results window clear of the ones already up, and counting it back out when it closes. |
| `src/my/stepss/diagram/SvgImage.java` | An SVG parsed once, rendered on demand: whole document, or one rectangle of it at a given pixel size. |
| `src/my/stepss/diagram/DiagramView.java` | The zoom and pan arithmetic, pure and headless: viewport size plus zoom plus centre gives an area-of-interest rectangle. |
| `src/my/stepss/diagram/DiagramPanel.java` | The Swing component: paints `SvgImage` through `DiagramView`, handles wheel, drag and keys, coalesces re-renders. |
| `src/my/stepss/diagram/DiagramWindow.java` | The frame: banner, panel, toolbar, save buttons. |
| `src/my/stepss/diagram/DiagramCheck.java` | Headless checks for `SvgImage`, `DiagramView`, `HeliosOutcome` and the platform launcher command. |
| `tools/diagram-harness.sh` | Runs `DiagramCheck` against the built classes. |
| `lib/batik-all-1.19.jar` | Batik. |
| `lib/xmlgraphics-commons-2.11.jar` | Batik dependency. |
| `lib/xml-apis-ext-1.3.04.jar` | Batik dependency: SVG and SMIL DOM interfaces. |

**Modified files:**

| Path | Change |
|---|---|
| `src/my/stepss/examples/ExampleCatalog.java` | `optional()` helper; `.dist`, `.obs` no longer required; new `.svg` key. |
| `src/my/stepss/examples/Example.java` | `diagram()` accessor; `retained()` skips empty slots. |
| `src/my/stepss/examples/ExamplesHarness.java` | Optional slot checks; synthetic descriptor parse check. |
| `src/my/stepss/examples/examples.properties` | The `six-bus` entry, appended to the index. |
| `versions.properties` | `six-bus.*` pin block. |
| `build.xml` | `fetch-example` and `pack-example` lines for `six-bus`. |
| `tools/ci/pins.py` | `six-bus` in `EXAMPLES`. |
| `tools/ci/release.py` | `six-bus` display name. |
| `src/my/stepss/platform/PlatformLauncher.java` | `openInDefaultApplication()` and its testable command builder. |
| `src/my/stepss/config/Scenario.java` | `diagram` field. |
| `src/my/stepss/config/ScenarioBinding.java` | `diagram` field bound. |
| `src/my/stepss/config/ScenarioFile.java` | `diagram` key written and read. |
| `src/my/stepss/config/ScenarioHarness.java` | `diagram` in the round trip. |
| `src/my/stepss/HeliosLog.java` | Four new progress prefixes. |
| `src/my/stepss/ssa/SsaResultsWindow.java` | Cascade logic moves to `WindowCascade`. |
| `src/my/stepss/StepssUI.java` | Diagram row, chooser, clear, `1` command, `deletePFCResultFiles`, `applyExample` fix, `jLabel9` label, window opening. |
| `nbproject/project.properties` | Three jars on `javac.classpath`. |
| `packaging/linux/copyright` | Batik third-party stanza. |

---

## Task 1: Cut v1.0.0 of stepss-6-bus-MG

The examples pipeline pins a tag, a source archive URL and a content-manifest digest. None of those exist until the upstream repository has a release, so this is first.

**Files:**
- Modify: nothing in this repository. This task is entirely in `SPS-L/stepss-6-bus-MG`.

**Interfaces:**
- Consumes: nothing.
- Produces: the tag `v1.0.0` and the URL `https://github.com/SPS-L/stepss-6-bus-MG/archive/refs/tags/v1.0.0.zip`, both used by Task 3.

- [ ] **Step 1: Confirm the working tree is clean and on main**

The repository is a submodule of `stepss-test-systems`, checked out at `../stepss-test-systems/stepss-6-bus-MG`.

```bash
cd ../stepss-test-systems/stepss-6-bus-MG
```
```bash
git status
```
Expected: `On branch main`, `nothing to commit, working tree clean`. If it is in detached HEAD (submodules often are), run `git checkout main` then `git pull` before continuing.

- [ ] **Step 2: Confirm the five files the descriptor will name are present**

```bash
ls 6bus.dat 6bus.svg volt_rat.dat README.md LICENSE
```
Expected: all five listed, no `No such file` line. These are exactly the files Task 3's descriptor entry names, and `ExamplesPack` fails the build if the released archive lacks any of them.

- [ ] **Step 3: Push the lightweight tag**

The tag must exist on the remote **before** the release is created. `gh release create` with `--target` against an unpushed tag answers HTTP 422.

```bash
git tag v1.0.0
```
```bash
git push origin v1.0.0
```

- [ ] **Step 4: Create the release**

```bash
gh release create v1.0.0 --repo SPS-L/stepss-6-bus-MG --title "v1.0.0" --notes "First tagged release of the 6-bus microgrid power-flow case.

Six buses at 6 and 11 kV, two lines, four transformers, loads at D and E, and generators at A (slack) and F. Power-flow only: there is no dynamic data and no disturbance scenario.

Contents:

- 6bus.dat: power-flow data
- volt_rat.dat: solved bus voltages, as the Helios VT command writes them
- 6bus.svg: one-line diagram template carrying the %A to %U placeholder codes

This is the case bundled with STEPSS to demonstrate the annotated one-line diagram."
```

- [ ] **Step 5: Verify the source archive resolves**

This is the URL the Ant build will fetch, so a 404 here is a build failure later.

```bash
curl -sSL -o /dev/null -w '%{http_code}\n' https://github.com/SPS-L/stepss-6-bus-MG/archive/refs/tags/v1.0.0.zip
```
Expected: `200`

- [ ] **Step 6: Return to the java-ui repository**

```bash
cd ../../stepss-java-ui
```

No commit in this repository for this task.

---

## Task 2: Optional slots in the examples descriptor

The descriptor requires `.dist` and `.obs`. A power-flow-only example has neither, and `Example.retained()` would put an empty string into the retained set, which makes `ExamplesPack` look for a file named `""` in the upstream archive and fail the build naming nothing.

**Files:**
- Modify: `src/my/stepss/examples/ExampleCatalog.java`
- Modify: `src/my/stepss/examples/Example.java`
- Modify: `src/my/stepss/examples/ExamplesHarness.java`
- Modify: `src/my/stepss/StepssUI.java` (the `applyExample` slot filling)

**Interfaces:**
- Consumes: nothing.
- Produces: `Example.diagram()` returning `String` (the template filename, or `""`); `Example.dist()` and `Example.obs()` may now return `""`.

- [ ] **Step 1: Write the failing checks**

Add these three methods to `src/my/stepss/examples/ExamplesHarness.java`, and call them from `main()` immediately after `checkCatalogIsSane(examples)`.

```java
    /** The descriptor text a power-flow-only entry produces. */
    private static final String PF_ONLY_DESCRIPTOR
            = "examples=pfonly\n"
            + "pfonly.name=Power flow only\n"
            + "pfonly.scale=6 buses\n"
            + "pfonly.summary=A case with no dynamic data.\n"
            + "pfonly.docs=https://example.invalid/pfonly\n"
            + "pfonly.dir=pf-only\n"
            + "pfonly.data=case.dat\n"
            + "pfonly.svg=case.svg\n"
            + "pfonly.extra=README.md, LICENSE\n";

    /**
     * An entry naming no disturbance and no observables parses, and does not
     * carry an empty path into its retained set.
     *
     * <p>The empty path is the failure that matters. {@code retained()} feeds
     * {@code ExamplesPack}, which looks up each name in the upstream archive,
     * so an empty string there fails the build with a message naming no file.
     */
    private static void checkOptionalSlotsParse() throws IOException {
        List<Example> examples = ExampleCatalog.load(
                new ByteArrayInputStream(PF_ONLY_DESCRIPTOR.getBytes("UTF-8")));
        check("a power-flow-only entry parses", examples.size() == 1);
        Example only = examples.get(0);
        check("its disturbance slot is empty", only.dist().isEmpty());
        check("its observables slot is empty", only.obs().isEmpty());
        check("its diagram slot is read", "case.svg".equals(only.diagram()));
        check("nothing retained is blank", !only.retained().contains(""));
        check("the diagram is retained", only.retained().contains("case.svg"));
        check("retained holds exactly the named files, once each",
                only.retained().size() == 4);
    }

    /** A data file is still required, because an example without one is not one. */
    private static void checkDataIsStillRequired() {
        String descriptor = PF_ONLY_DESCRIPTOR.replace("pfonly.data=case.dat\n", "");
        try {
            ExampleCatalog.load(new ByteArrayInputStream(descriptor.getBytes("UTF-8")));
            check("a descriptor with no data file is refused", false);
        } catch (IOException expected) {
            check("a descriptor with no data file is refused",
                    expected.getMessage().contains("pfonly.data"));
        }
    }

    /** An entry naming a disturbance and no observables is refused as a typo. */
    private static void checkHalfADynamicCaseIsRefused() throws IOException {
        String descriptor = PF_ONLY_DESCRIPTOR
                + "pfonly.dist=case.dst\n";
        List<Example> examples = ExampleCatalog.load(
                new ByteArrayInputStream(descriptor.getBytes("UTF-8")));
        boolean paired = examples.get(0).dist().isEmpty()
                == examples.get(0).obs().isEmpty();
        check("a disturbance without observables is caught", !paired);
    }
```

Add to `main()`, after `checkCatalogIsSane(examples);`:

```java
        checkOptionalSlotsParse();
        checkDataIsStillRequired();
        checkHalfADynamicCaseIsRefused();
```

Then extend `checkCatalogIsSane` with the paired-slot and extension rules, inside its existing `for (Example example : examples)` loop:

```java
            check(example.id() + " names both a disturbance and observables, or neither",
                    example.dist().isEmpty() == example.obs().isEmpty());
            check(example.id() + " names an .svg in its diagram slot",
                    example.diagram().isEmpty() || example.diagram().endsWith(".svg"));
```

- [ ] **Step 2: Run the harness to verify it fails**

```bash
ant compile
```
```bash
tools/examples-harness.sh
```
Expected: FAIL. `ExamplesHarness.java` does not compile, because `Example.diagram()` does not exist. The `ant compile` step is where it fails, with `cannot find symbol: method diagram()`.

- [ ] **Step 3: Add `optional()` and relax the two keys in `ExampleCatalog`**

In `src/my/stepss/examples/ExampleCatalog.java`, change the `new Example(...)` call in `load(InputStream)` so the `.dist` and `.obs` arguments use `optional`, and add the `.svg` argument after `.obs`:

```java
                    split(required(props, id + ".data", id)),
                    optional(props, id + ".dist"),
                    optional(props, id + ".obs"),
                    optional(props, id + ".svg"),
                    split(props.getProperty(id + ".extra", ""))));
```

Add the helper beside `required`:

```java
    /**
     * An optional value, trimmed, or "" when the key is absent or blank.
     *
     * <p>Three keys are optional and the rest are not, which is a judgement
     * about what an example <em>is</em> rather than about tidiness. A case with
     * no disturbance and no observables is a power-flow-only case, and the
     * 6-bus microgrid is exactly that; a case with no {@code .dir} would
     * extract over the examples root, and a case with no {@code .data} is not a
     * case. {@code .svg} is optional because only a case whose repository ships
     * a diagram template can name one.
     */
    private static String optional(Properties props, String key) {
        String value = props.getProperty(key);
        return value == null ? "" : value.trim();
    }
```

Update the class comment on `required` so it no longer claims every key is required. Replace its second paragraph with:

```java
     * <p>Used for the keys that have no sensible default: an entry with no
     * {@code .dir} would extract over the examples root itself, and one with no
     * {@code .data} names no case. See {@link #optional} for the three that are
     * allowed to be absent.
```

- [ ] **Step 4: Add `diagram` to `Example` and fix `retained()`**

In `src/my/stepss/examples/Example.java`, add the field, constructor parameter and accessor:

```java
    private final String diagram;
```

The constructor signature becomes:

```java
    Example(String id, String name, String scale, String summary, String docs,
            String dir, List<String> data, String dist, String obs, String diagram,
            List<String> extra) {
```

with `this.diagram = diagram;` set alongside the others, and:

```java
    /**
     * The filename of the annotated one-line diagram template, or "".
     *
     * <p>Optional because it exists only for cases whose repository ships one.
     * When present it fills the diagram slot on the System Data tab, and Run
     * Power Flow renders it through Helios' {@code 1} command.
     */
    public String diagram() {
        return diagram;
    }
```

Replace `retained()`'s body so empty slots contribute nothing:

```java
    public List<String> retained() {
        LinkedHashSet<String> all = new LinkedHashSet<>(data);
        addIfNamed(all, dist);
        addIfNamed(all, obs);
        addIfNamed(all, diagram);
        all.addAll(extra);
        return Collections.unmodifiableList(new ArrayList<>(all));
    }

    /**
     * Adds a slot's filename unless the slot is empty.
     *
     * <p>An unconditional add is what this replaces, and with optional slots it
     * would put "" into the set. {@code ExamplesPack} looks up every retained
     * name in the upstream archive, so that becomes a build failure naming no
     * file, and the manifest digest would cover a path that is not a path.
     */
    private static void addIfNamed(LinkedHashSet<String> all, String name) {
        if (!name.isEmpty()) {
            all.add(name);
        }
    }
```

Update the `retained()` doc comment's deduplication paragraph to mention that empty slots are skipped as well as duplicates.

- [ ] **Step 5: Stop `applyExample` writing a directory path into an empty slot**

In `src/my/stepss/StepssUI.java`, `applyExample` currently reads:

```java
        fileDist.setText(new File(dir, example.dist()).getAbsolutePath());
        fileObs.setText(new File(dir, example.obs()).getAbsolutePath());
        // What loadObsButton does once a file is chosen: an observables file
        // with the trajectory output switched off produces nothing to plot.
        saveOutputTrajButton.setSelected(true);
```

Replace with:

```java
        // An empty slot is left empty rather than resolved. new File(dir, "")
        // is the example DIRECTORY, so the unconditional form put a directory
        // path into the disturbance field of a power-flow-only case and made it
        // look like a file had been loaded.
        fileDist.setText(slotPath(dir, example.dist()));
        fileObs.setText(slotPath(dir, example.obs()));
        // What loadObsButton does once a file is chosen: an observables file
        // with the trajectory output switched off produces nothing to plot.
        // Only when there is one: ticking it for a case with nothing to observe
        // sets a switch that cannot do anything.
        if (!example.obs().isEmpty()) {
            saveOutputTrajButton.setSelected(true);
        }
```

Add the helper next to `applyExample`:

```java
    /** An example's file, resolved against its directory, or "" for an unfilled slot. */
    private static String slotPath(File dir, String name) {
        return name.isEmpty() ? "" : new File(dir, name).getAbsolutePath();
    }
```

- [ ] **Step 6: Teach `ExamplesHarness.checkInstalls` to skip empty slots**

In `checkInstalls`, replace:

```java
            List<String> slots = new ArrayList<>(example.data());
            slots.add(example.dist());
            slots.add(example.obs());
```

with:

```java
            // Only the slots this example actually fills. An empty one would
            // otherwise assert that a file named "" has content.
            List<String> slots = new ArrayList<>(example.data());
            for (String optional : new String[]{example.dist(), example.obs(),
                example.diagram()}) {
                if (!optional.isEmpty()) {
                    slots.add(optional);
                }
            }
```

- [ ] **Step 7: Run the harness to verify it passes**

```bash
ant compile
```
```bash
ant stage-payloads
```
```bash
tools/examples-harness.sh
```
Expected: `All examples checks passed (3 examples).` with every `ok` line present and no `FAIL`. The three existing examples are unaffected because they name all their slots.

- [ ] **Step 8: Commit**

```bash
git add src/my/stepss/examples/ExampleCatalog.java src/my/stepss/examples/Example.java src/my/stepss/examples/ExamplesHarness.java src/my/stepss/StepssUI.java
```
```bash
git commit -m "Examples: let an entry omit its disturbance and observables

A power-flow-only case has neither, and the descriptor required both.
Example.retained() also added both unconditionally, so an omitted slot
would have put an empty path into the set ExamplesPack looks up in the
upstream archive, failing the build with a message naming no file.

Adds an optional .svg key in the same pass, for the annotated one-line
diagram template, and fixes applyExample: new File(dir, \"\") is the
example directory, so an empty slot used to show a directory path in the
disturbance field and look like a loaded file."
```

---

## Task 3: Bundle the 6-bus microgrid, and stop calling the disturbance required

**Files:**
- Modify: `src/my/stepss/examples/examples.properties`
- Modify: `versions.properties`
- Modify: `build.xml:317-319` (`fetch-examples`) and `build.xml:379-384` (`stage-payloads`)
- Modify: `tools/ci/pins.py:18`
- Modify: `tools/ci/release.py:136-138`
- Modify: `src/my/stepss/StepssUI.java` (the `jLabel9` text)

**Interfaces:**
- Consumes: the `v1.0.0` release from Task 1; `Example.diagram()` and optional slots from Task 2.
- Produces: the example id `six-bus`, whose `diagram()` is `6bus.svg`, used by the manual acceptance runs in Tasks 5, 7 and 12.

- [ ] **Step 1: Add the descriptor entry**

In `src/my/stepss/examples/examples.properties`, change the index line:

```properties
examples=kundur, nordic, five-bus, six-bus
```

and append at the end of the file:

```properties


six-bus.name=6-bus microgrid
six-bus.scale=6 buses, 2 generators, 6/11 kV
six-bus.summary=A power-flow-only case: six buses at 6 and 11 kV, two lines, \
four transformers, loads at D and E, and generators at A (slack) and F. There \
is no dynamic data and no disturbance scenario, so this example runs on the \
Power Flow tab and not the Dynamic Simulation one. It is the case the \
annotated one-line diagram is demonstrated with: 6bus.svg is a template \
carrying the placeholder codes, and Run Power Flow fills it in with the solved \
voltages, angles and flows. volt_rat.dat holds the solved bus voltages as the \
VT command writes them. Data by the Sustainable Power Systems Laboratory.
six-bus.docs=https://github.com/SPS-L/stepss-6-bus-MG
six-bus.dir=six-bus-microgrid
six-bus.data=6bus.dat
# No .dist and no .obs, deliberately. Shipping an empty .dst so this entry
# looked like the other three would be a file claiming to be a disturbance
# scenario for a case whose own README says it has none. ExampleCatalog treats
# both keys as optional; the pair must be both or neither.
six-bus.svg=6bus.svg
six-bus.extra=volt_rat.dat, README.md, LICENSE
```

Do not write a literal `%A` in the summary. `Properties` does not treat `%` specially, but the summary is rendered into HTML by the dialog and a percent-prefixed token there reads as a formatting placeholder to the next person editing it. The wording above says "the placeholder codes" instead, and the table lives in `stepss-docs`.

- [ ] **Step 2: Add the pin block with a placeholder digest**

Append to `versions.properties`:

```properties
six-bus.version=1.0.0
six-bus.repo=SPS-L/stepss-6-bus-MG
six-bus.tag=v1.0.0
six-bus.source.url=https://github.com/SPS-L/stepss-6-bus-MG/archive/refs/tags/v1.0.0.zip
six-bus.source.url.pattern=https://github.com/SPS-L/stepss-6-bus-MG/archive/refs/tags/v@VERSION@.zip
six-bus.manifest.sha256=COMPUTE
```

`COMPUTE` is replaced with the real digest in Step 5. It is written this way rather than left blank because `pack-example` passes the value straight through to `ExamplesPack`, which treats the literal `COMPUTE` as "print the digest and stop".

- [ ] **Step 3: Wire it into the build**

In `build.xml`, in the `fetch-examples` target, after the `five-bus` line:

```xml
  <fetch-example id="six-bus"  version="${six-bus.version}"  url="${six-bus.source.url}"/>
```

In `stage-payloads`, after the `five-bus` `pack-example`:

```xml
  <pack-example id="six-bus"  version="${six-bus.version}"
                sha256="${six-bus.manifest.sha256}"/>
```

- [ ] **Step 4: Fetch the archive**

```bash
ant fetch-examples
```
Expected: a `[get]` line for `stepss-6-bus-MG/archive/refs/tags/v1.0.0.zip`, and `payload-cache/example-six-bus-1.0.0.zip` on disk afterwards. Confirm:

```bash
ls -l payload-cache/example-six-bus-1.0.0.zip
```

- [ ] **Step 5: Compute the manifest digest and pin it**

```bash
ant stage-payloads
```
Expected: the run reaches the `six-bus` `pack-example` and prints two lines, then continues:

```
six-bus.manifest.sha256=<64 hex characters>
Retained 5 files. Set the digest in versions.properties and re-run.
```

Copy that digest into `versions.properties`, replacing `COMPUTE`.

If instead it prints `example 'six-bus' completeness check FAILED` naming a file, the release cut in Task 1 does not carry it. Fix that upstream and re-cut rather than editing the descriptor to match, unless the file genuinely should not be there.

- [ ] **Step 6: Re-run and verify the payload is built**

```bash
ant stage-payloads
```
Expected: `example 'six-bus' OK: 5 files -> .../src/my/stepss/payload/example-six-bus.zip (NN KB)`

- [ ] **Step 7: Add the id to the release tooling**

`tools/ci/pins.py:18`:

```python
EXAMPLES = ("kundur", "nordic", "five-bus", "six-bus")
```

`tools/ci/release.py`, in the display name table beside the other three:

```python
    "six-bus": "6-bus microgrid",
```

- [ ] **Step 8: Stop the tab claiming a disturbance is required**

In `src/my/stepss/StepssUI.java`, find:

```java
        jLabel9.setText("<html><b>Disturbance file</b> (required)</html>");
```

and change `(required)` to `(optional)`.

That is the whole change. `createPFCCommandFile()` never reads `fileDist`, so the power flow has always run without one; `createCommandFile()` already returns "No disturbance file is loaded. Add one on the System Data tab." and is the only thing that needs one.

- [ ] **Step 9: Run the harness and the release tooling tests**

```bash
ant compile
```
```bash
tools/examples-harness.sh
```
Expected: `All examples checks passed (4 examples).`

```bash
python3 -m pytest tools/ci/tests -q
```
Expected: all pass. If `pytest` is not installed, run `python3 -m unittest discover -s tools/ci/tests` instead.

- [ ] **Step 10: Verify by opening it**

```bash
ant jar
```
```bash
java -jar dist/stepss.jar
```
File > Open Examples, choose 6-bus microgrid, Fresh copy. Expected: data row 1 holds `.../six-bus-microgrid/6bus.dat`, rows 2 to 10 are empty, the disturbance row is empty and its heading now reads "(optional)", and the observables field on the next tab is empty. Close the application.

- [ ] **Step 11: Commit**

```bash
git add src/my/stepss/examples/examples.properties versions.properties build.xml tools/ci/pins.py tools/ci/release.py src/my/stepss/StepssUI.java
```
```bash
git commit -m "Bundle the 6-bus microgrid, and make the disturbance file optional

The four bundled examples were all dynamic cases, so the tab Helios
drives had no example at all. The 6-bus microgrid is power-flow only and
ships a one-line diagram template, which makes it the case the annotated
diagram is demonstrated with.

The disturbance heading said (required) and never was: the power flow
command file has never read that field, and the dynamic one already
refuses with a sentence naming the tab to fix it on."
```

---

## Task 4: `HeliosOutcome`, one decision about what an exit status means

`StepssUI.describeHeliosExit` already derives the answer from the exit value and stderr. The diagram window needs the same answer in a different shape. A second copy would drift, so the decision is factored out and both render from it.

**Files:**
- Create: `src/my/stepss/HeliosOutcome.java`
- Create: `src/my/stepss/diagram/DiagramCheck.java`
- Create: `tools/diagram-harness.sh`
- Modify: `src/my/stepss/StepssUI.java:5915` (`describeHeliosExit`)

**Interfaces:**
- Consumes: nothing.
- Produces: `HeliosOutcome.of(int exitValue, String stderrText)` returning a non-null `HeliosOutcome`; `HeliosOutcome.renderFailed(String templateName)`; instance accessors `severity()` returning `HeliosOutcome.Severity`, `headline()` and `detail()` returning `String`. `Severity` is the enum `OK`, `WARNING`, `ERROR`. Task 12 consumes all of these.

- [ ] **Step 1: Write the failing checks**

Create `src/my/stepss/diagram/DiagramCheck.java`:

```java
package my.stepss.diagram;

import my.stepss.HeliosOutcome;

/**
 * Headless checks for the annotated one-line diagram: the Helios exit-status
 * decision, the zoom and pan arithmetic, and the Batik rendering path.
 *
 * <p>Run from {@code tools/diagram-harness.sh}; this repository has no
 * unit-test framework.
 *
 * <p>The rendering checks matter more than they look. Batik reaches this
 * application through {@code batik-all}, which bundles {@code batik-script},
 * whose Rhino interpreter factory is registered through
 * {@code META-INF/services} while Rhino itself is not shipped. That is expected
 * to be harmless, and expected is not verified, so these run with exactly the
 * classpath the application has.
 */
public final class DiagramCheck {

    private static int failures = 0;

    private DiagramCheck() {
    }

    public static void main(String[] args) throws Exception {
        checkConvergedIsSilent();
        checkNotConvergedWarns();
        checkNotConvergedCarriesItsReason();
        checkInputErrorIsAnError();
        checkUndocumentedStatusIsAnError();
        checkMissingStatusLineLeavesTheHeadlineAlone();
        checkRenderFailureIsItsOwnOutcome();

        System.out.println(failures == 0 ? "ALL DIAGRAM CHECKS PASSED"
                : failures + " DIAGRAM CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkConvergedIsSilent() {
        HeliosOutcome outcome = HeliosOutcome.of(0, "helios: status: CONVERGED (2 iterations)\n");
        check("exit 0 is OK", outcome.severity() == HeliosOutcome.Severity.OK);
        check("exit 0 has no headline", outcome.headline().isEmpty());
    }

    private static void checkNotConvergedWarns() {
        HeliosOutcome outcome = HeliosOutcome.of(2, "");
        check("exit 2 warns", outcome.severity() == HeliosOutcome.Severity.WARNING);
        check("exit 2 says so", outcome.headline().contains("did NOT converge"));
        check("exit 2 warns off the values",
                outcome.detail().contains("Do not use these values"));
    }

    private static void checkNotConvergedCarriesItsReason() {
        HeliosOutcome outcome = HeliosOutcome.of(2,
                "helios: status: NOT_CONVERGED (max iterations)\n");
        check("exit 2 carries the reason Helios gave",
                outcome.headline().contains("(max iterations)"));
    }

    private static void checkInputErrorIsAnError() {
        HeliosOutcome outcome = HeliosOutcome.of(1, "");
        check("exit 1 errors", outcome.severity() == HeliosOutcome.Severity.ERROR);
        check("exit 1 names the input", outcome.headline().contains("could not process"));
    }

    private static void checkUndocumentedStatusIsAnError() {
        HeliosOutcome outcome = HeliosOutcome.of(3, "");
        check("exit 3 errors", outcome.severity() == HeliosOutcome.Severity.ERROR);
        check("exit 3 names its value", outcome.headline().contains("3"));
    }

    private static void checkMissingStatusLineLeavesTheHeadlineAlone() {
        // The engine before the status contract never wrote the line, so the
        // headline has to stand on its own without a dangling "()".
        HeliosOutcome outcome = HeliosOutcome.of(2, "some unrelated stderr\n");
        check("a missing status line leaves no empty parentheses",
                !outcome.headline().contains("()"));
    }

    private static void checkRenderFailureIsItsOwnOutcome() {
        HeliosOutcome outcome = HeliosOutcome.renderFailed("6bus.svg");
        check("a render failure warns",
                outcome.severity() == HeliosOutcome.Severity.WARNING);
        check("a render failure names the template",
                outcome.headline().contains("6bus.svg")
                        || outcome.detail().contains("6bus.svg"));
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("  ok    " + what);
        } else {
            System.out.println("  FAIL  " + what);
            failures++;
        }
    }
}
```

Create `tools/diagram-harness.sh`:

```bash
#!/usr/bin/env bash
# Runs the headless one-line diagram checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Unlike tools/ssa-harness.sh, dist/lib IS on the classpath: the diagram
# package renders through Batik, so the jars in lib/ have to be reachable.
# They are put there deliberately WITHOUT Rhino, which is the state the
# application ships in and the state these checks have to run in.
set -eu
cd "$(dirname "$0")/.."
if [ ! -d build/classes ]; then
    echo "build/classes not found - run 'ant compile' (or 'ant jar') first." >&2
    exit 1
fi
exec java -Djava.awt.headless=true \
     -cp "build/classes:lib/batik-all-1.19.jar:lib/xmlgraphics-commons-2.11.jar:lib/xml-apis-ext-1.3.04.jar" \
     my.stepss.diagram.DiagramCheck
```

```bash
chmod +x tools/diagram-harness.sh
```

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```
Expected: FAIL, `cannot find symbol: class HeliosOutcome`.

- [ ] **Step 3: Write `HeliosOutcome`**

Create `src/my/stepss/HeliosOutcome.java`:

```java
package my.stepss;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a finished Helios run means, decided once.
 *
 * <p>Two things render this: {@code StepssUI.describeHeliosExit} builds the
 * modal dialog from it, and {@code my.stepss.diagram.DiagramWindow} builds its
 * banner from it. They used to be one decision and one renderer; adding the
 * banner without this class would have made it two decisions that agree only
 * for as long as someone keeps them in step, and the whole point of the banner
 * is that it says the same thing as the status bar and the dialog.
 *
 * <p>Carries no Swing, so the harness exercises it without a display. That is
 * also what {@code describeHeliosExit} claimed for itself and never had:
 * it was package-visible "so it is exercised directly by tests" and nothing in
 * the repository called it.
 *
 * <p>The normative contract is in ../stepss-helios/README.md and
 * docs/tui-guide.md#exit-status: 0 converged, 2 did not converge, 1 input or
 * usage error, anything else undocumented.
 */
public final class HeliosOutcome {

    /** How loudly to say it. Mapped to JOptionPane constants by the caller. */
    public enum Severity {
        /** Nothing to report. */
        OK,
        /** Results exist and are not trustworthy. */
        WARNING,
        /** There may be no results at all. */
        ERROR
    }

    /**
     * Matches the machine-readable status line Helios writes once to stderr on
     * every non-interactive run, for example
     * {@code helios: status: NOT_CONVERGED (max iterations)}. Group 2 is the
     * optional parenthesised reason. Engines before the status contract never
     * write it, which is why nothing here requires it.
     */
    private static final Pattern STATUS_LINE
            = Pattern.compile("helios: status: (\\S+)(?: \\(([^)]*)\\))?");

    private final Severity severity;
    private final String headline;
    private final String detail;

    private HeliosOutcome(Severity severity, String headline, String detail) {
        this.severity = severity;
        this.headline = headline;
        this.detail = detail;
    }

    /** How loudly to say it. */
    public Severity severity() {
        return severity;
    }

    /** The one sentence, matching the status bar's phrasing. Empty when OK. */
    public String headline() {
        return headline;
    }

    /** What follows it, or "" when the headline says everything. */
    public String detail() {
        return detail;
    }

    /**
     * The outcome of a completed run.
     *
     * @param exitValue  the process exit value
     * @param stderrText the run's captured stderr, searched for the status line
     * @return never null; {@link Severity#OK} with an empty headline for exit 0
     */
    public static HeliosOutcome of(int exitValue, String stderrText) {
        if (exitValue == 0) {
            return new HeliosOutcome(Severity.OK, "", "");
        }
        String reason = "";
        Matcher matcher = STATUS_LINE.matcher(stderrText == null ? "" : stderrText);
        if (matcher.find() && matcher.group(2) != null && !matcher.group(2).isEmpty()) {
            reason = " (" + matcher.group(2) + ")";
        }
        switch (exitValue) {
            case 2:
                return new HeliosOutcome(Severity.WARNING,
                        "The power flow did NOT converge" + reason + ".",
                        "Helios still produced and exported result files, but they"
                        + " are NOT a valid power-flow solution. Do not use these values.");
            case 1:
                return new HeliosOutcome(Severity.ERROR,
                        "Helios could not process the input" + reason + ".",
                        "It reported an input or usage error and stopped early."
                        + " There may be no results at all.");
            default:
                return new HeliosOutcome(Severity.ERROR,
                        "Helios exited abnormally (status " + exitValue + ").",
                        "That is not a documented outcome. Treat any displayed"
                        + " results with suspicion.");
        }
    }

    /**
     * The outcome for a run that converged and still produced no diagram.
     *
     * <p>Not derivable from an exit status, because there is none to derive it
     * from: Helios' {@code 1} command catches its own exceptions and does not
     * set its error flag, so an unreadable or malformed template leaves the run
     * successful and silently draws nothing. Without this the window would show
     * an unannotated template under no banner at all and look like a solved
     * case with no numbers on it.
     *
     * @param templateName the template's file name, for the message
     */
    public static HeliosOutcome renderFailed(String templateName) {
        return new HeliosOutcome(Severity.WARNING,
                "The power flow converged, but the diagram could not be drawn.",
                "Helios could not render " + templateName + ", so this is the"
                + " template as it was, without the solved values. Check that it"
                + " is a readable SVG file.");
    }
}
```

- [ ] **Step 4: Make `describeHeliosExit` render from it**

In `src/my/stepss/StepssUI.java`, replace the body of `describeHeliosExit` (keeping its signature, its doc comment and `HeliosStatusDialog` exactly as they are):

```java
    static HeliosStatusDialog describeHeliosExit(int exitValue, String heliosStderrText) {
        HeliosOutcome outcome = HeliosOutcome.of(exitValue, heliosStderrText);
        if (outcome.severity() == HeliosOutcome.Severity.OK) {
            return null;
        }
        boolean warning = outcome.severity() == HeliosOutcome.Severity.WARNING;
        String title = warning ? "Power Flow Did NOT Converge!"
                : (exitValue == 1 ? "Helios Could Not Process The Input!"
                        : "Helios Exited Abnormally!");
        return new HeliosStatusDialog(title,
                "<html><body style='width: 350px'>"
                + (warning ? "<b><font color='red'>" + escapeHtml(outcome.headline())
                        + "</font></b>" : escapeHtml(outcome.headline()))
                + "<br><br>" + escapeHtml(outcome.detail())
                + "</body></html>",
                warning ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }
```

Add a note to its doc comment, after the existing list:

```java
     * <p>The decision itself lives in {@link HeliosOutcome}, because the
     * diagram window's banner has to say the same thing and two copies would
     * agree only until one of them was edited. This method is now the dialog
     * rendering of that decision, and keeps its signature and its contract.
```

Delete the now-unused `HELIOS_STATUS_LINE` field and its comment from `StepssUI`, along with the `Matcher`/`Pattern` imports if nothing else in the file uses them (check with `grep -n "Pattern\|Matcher" src/my/stepss/StepssUI.java` before removing the imports).

- [ ] **Step 5: Run the harness to verify it passes**

```bash
ant compile
```
```bash
tools/diagram-harness.sh
```
Expected: every line `ok`, then `ALL DIAGRAM CHECKS PASSED`. It runs before the jars exist because these checks touch only `HeliosOutcome`; the classpath entries for Batik are simply absent and unused.

- [ ] **Step 6: Commit**

```bash
git add src/my/stepss/HeliosOutcome.java src/my/stepss/diagram/DiagramCheck.java tools/diagram-harness.sh src/my/stepss/StepssUI.java
```
```bash
git commit -m "Factor the Helios exit-status decision into HeliosOutcome

describeHeliosExit is about to gain a second renderer: the diagram
window's banner has to say what the dialog and the status bar say. Two
copies of the decision would agree only until one was edited, so the
decision moves into a class with no Swing in it and both render from it.

That also gives it its first test coverage. It was package-visible with a
comment saying it was exercised directly by tests, and nothing in the
repository called it."
```

---

## Task 5: The diagram slot on the System Data tab

**Files:**
- Modify: `src/my/stepss/platform/PlatformLauncher.java`
- Modify: `src/my/stepss/StepssUI.java` (fields, `layoutSystemDataTab`, `styleEditButtons`, `clearDataFilesActionPerformed`, two new handlers)
- Modify: `src/my/stepss/diagram/DiagramCheck.java`

**Interfaces:**
- Consumes: nothing.
- Produces: the `StepssUI` field `fileDiagram` (a `javax.swing.JTextField`), consumed by Tasks 6, 7 and 12; `PlatformLauncher.openInDefaultApplication(File)` throwing `IOException`; `PlatformLauncher.defaultApplicationCommand(Platform, File)` returning `org.apache.commons.exec.CommandLine`.

- [ ] **Step 1: Write the failing checks**

Add to `src/my/stepss/diagram/DiagramCheck.java`, and call both from `main()`:

```java
    private static void checkTheLauncherDoesNotForceATextEditor() {
        java.io.File svg = new java.io.File("/tmp/6bus.svg");
        for (my.stepss.platform.Platform platform : my.stepss.platform.Platform.values()) {
            org.apache.commons.exec.CommandLine cmd =
                    my.stepss.platform.PlatformLauncher.defaultApplicationCommand(platform, svg);
            String line = cmd.getExecutable() + " "
                    + String.join(" ", cmd.getArguments());
            // "open -t" forces TextEdit and "notepad.exe" forces Notepad, which
            // is right for a .dat and shows an SVG as XML source.
            check(platform + " does not force a text editor",
                    !line.contains(" -t ") && !line.contains("notepad"));
            check(platform + " passes the file",
                    line.contains(svg.getAbsolutePath()));
        }
    }

    private static void checkTheEditorLauncherStillForcesOne() {
        // The twelve data-file buttons keep the behaviour they have. This check
        // is what stops a future tidy-up merging the two launchers.
        java.io.File dat = new java.io.File("/tmp/lf.dat");
        org.apache.commons.exec.CommandLine cmd =
                my.stepss.platform.PlatformLauncher.editorCommand(
                        my.stepss.platform.Platform.WINDOWS_X86_64, dat);
        check("the editor launcher still opens Notepad on Windows",
                cmd.getExecutable().contains("notepad"));
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```
Expected: FAIL, `cannot find symbol: method defaultApplicationCommand`.

- [ ] **Step 3: Add the two command builders and the new launcher**

In `src/my/stepss/platform/PlatformLauncher.java`, extract the existing per-platform fallback from `openInEditor` into a pure builder and add its sibling:

```java
    /**
     * The per-platform command that opens {@code file} in a TEXT editor.
     *
     * <p>Split out of {@link #openInEditor} so it can be checked without
     * launching anything. Every branch here deliberately forces a text editor,
     * which is right for the data and disturbance files it serves and wrong for
     * anything else; see {@link #defaultApplicationCommand}.
     */
    public static CommandLine editorCommand(Platform p, File file) {
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("notepad.exe");
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
            cmd.addArgument("-t");
        } else {
            cmd = new CommandLine("xdg-open");
        }
        cmd.addArgument(file.getAbsolutePath(), false);
        return cmd;
    }

    /**
     * The per-platform command that opens {@code file} in whatever application
     * the desktop associates with its type.
     *
     * <p>The difference from {@link #editorCommand} is the whole reason this
     * exists: two of that method's three branches force a text editor, so an
     * SVG opened through it appears as XML source rather than as a drawing.
     * Here macOS gets {@code open} without {@code -t} and Windows gets the
     * shell's own {@code start}, so an installed SVG editor is used and a
     * machine with none falls through to the browser, which is a viewer.
     */
    public static CommandLine defaultApplicationCommand(Platform p, File file) {
        CommandLine cmd;
        if (p == Platform.WINDOWS_X86_64) {
            cmd = new CommandLine("cmd.exe");
            cmd.addArgument("/c");
            cmd.addArgument("start");
            // start treats its first quoted argument as the window title, so a
            // path in quotes with nothing before it opens a console instead of
            // the file. The empty title is what stops that.
            cmd.addArgument("\"\"", false);
        } else if (p == Platform.MACOS_ARM64) {
            cmd = new CommandLine("open");
        } else {
            cmd = new CommandLine("xdg-open");
        }
        cmd.addArgument(file.getAbsolutePath(), false);
        return cmd;
    }

    /**
     * Opens {@code file} in the platform's own application for its type: an SVG
     * editor for a drawing, falling back to a viewer.
     *
     * <p>Tries {@code Desktop.EDIT} first, then {@code Desktop.OPEN}, exactly as
     * {@link #openInEditor} does, because EDIT is what reaches Inkscape for
     * anyone who has it associated. Only the per-platform fallback differs.
     */
    public static void openInDefaultApplication(File file) throws IOException {
        if (tryDesktop(file)) {
            return;
        }
        run(defaultApplicationCommand(platformOrThrow(), file),
                file.getParentFile(), "open " + file.getName());
    }
```

Then replace the tail of `openInEditor` so it uses the extracted builder:

```java
    public static void openInEditor(File file) throws IOException {
        if (tryDesktop(file)) {
            return;
        }
        run(editorCommand(platformOrThrow(), file), file.getParentFile(),
                "open an editor for " + file.getName());
    }
```

- [ ] **Step 4: Declare the three controls in `StepssUI`**

Add beside the other private Swing fields, near `private javax.swing.JTextField fileDist;` (outside the `//GEN-BEGIN`/`//GEN-END` variables block, so the NetBeans designer never rewrites them):

```java
    // The one-line diagram row. Declared here and not in StepssUI.form because
    // layoutSystemDataTab() builds the whole tab programmatically, so a form
    // control would buy nothing and would have to be kept in step with the
    // designer. See the heading built in layoutSystemDataTab for the same
    // reasoning applied to a label.
    private final javax.swing.JTextField fileDiagram = new javax.swing.JTextField();
    private final javax.swing.JButton loadDiagram = new javax.swing.JButton("Load File");
    private final javax.swing.JButton nppDiagramButton = new javax.swing.JButton();
```

Wire their handlers in the constructor, next to the other post-`initComponents` wiring (immediately before the `applyModernChrome();` call):

```java
        fileDiagram.setEditable(false);
        fileDiagram.setMinimumSize(new java.awt.Dimension(0, 24));
        loadDiagram.addActionListener(evt -> loadDiagramActionPerformed(evt));
        nppDiagramButton.addActionListener(evt -> nppOpenDefault(fileDiagram.getText()));
```

`setEditable(false)` matches `fileDist` and the ten data rows: every path in this tab is chosen through the file chooser, so a typed path that does not exist cannot get in.

- [ ] **Step 5: Add the two handlers**

Put these beside `loadDistActionPerformed` in `src/my/stepss/StepssUI.java`:

```java
    private void loadDiagramActionPerformed(java.awt.event.ActionEvent evt) {
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter =
                new FileNameExtensionFilter("Annotated one-line diagram", "svg");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose One-Line Diagram SVG");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileDiagram.setText(file.getAbsolutePath());
        } else {
            fileDiagram.setText("");
        }
    }

    /**
     * Opens a file in the platform's application for its type, rather than in a
     * text editor.
     *
     * <p>The sibling of {@link #nppOpen}, and separate from it on purpose. That
     * one falls back to Notepad and to {@code open -t}, which shows an SVG as
     * XML source; this one falls back to the shell's own open, so a drawing
     * reaches a drawing program.
     */
    private void nppOpenDefault(String filename) {
        if (filename.isEmpty()) {
            banner.warn("No one-line diagram is loaded. Add one on the System Data tab.");
            return;
        }
        File target = new File(filename);
        if (!target.exists()) {
            banner.warn("<html>The file <B>" + target.getName()
                    + "</B> does not exist.</html>");
            return;
        }
        try {
            PlatformLauncher.openInDefaultApplication(target);
        } catch (IOException ex) {
            banner.warn("Could not open " + target.getAbsolutePath()
                    + "\n\n" + ex.getMessage());
        }
    }
```

- [ ] **Step 6: Put the section on the tab**

In `layoutSystemDataTab()`, after the disturbance row and before the filler:

```java
        rows.add(Box.createVerticalStrut(10), span(row++));
        rows.add(heading(new JLabel(
                "<html><b>One-line diagram annotated SVG</b> (optional)</html>")),
                span(row++));
        rows.add(fileRow("", loadDiagram, fileDiagram, nppDiagramButton), stretch(row++));
```

`heading` already takes a `JLabel` and already accepts one built in code: `layoutAnalysisTab` does `heading(new JLabel("Recording to file"))`. The HTML form is used here to match `jLabel1` and `jLabel9`, which are the two headings directly above it.

- [ ] **Step 7: Style the button and clear the field**

In `styleEditButtons()`, add `nppDiagramButton` to the array:

```java
        JButton[] editButtons = {
            nppData1Button, nppData2Button, nppData3Button, nppData4Button,
            nppData5Button, nppData6Button, nppData7Button, nppData8Button,
            nppData9Button, nppData10Button, nppDstButton, nppObsButton,
            nppDiagramButton};
```

and change its tooltip afterwards, because this one does not open an editor:

```java
        nppDiagramButton.setToolTipText(
                "Open this diagram in your default SVG viewer or editor");
```

In `clearDataFilesActionPerformed`, after `fileDist.setText("");`:

```java
        fileDiagram.setText("");
```

- [ ] **Step 8: Fill the slot when an example names one**

In `applyExample`, after the `fileObs.setText(...)` line added in Task 2:

```java
        fileDiagram.setText(slotPath(dir, example.diagram()));
```

- [ ] **Step 9: Run the checks**

```bash
ant compile
```
```bash
tools/diagram-harness.sh
```
Expected: `ALL DIAGRAM CHECKS PASSED`, including the six new launcher lines.

- [ ] **Step 10: Verify by opening it**

```bash
ant jar
```
```bash
java -jar dist/stepss.jar
```
File > Open Examples, 6-bus microgrid, Reuse my copy. Expected: the System Data tab shows a third section headed "One-line diagram annotated SVG (optional)" holding `.../six-bus-microgrid/6bus.svg`. Press its pencil: the diagram opens in a drawing program or a browser, not as XML text. Press Clear files: all three sections empty. Close the application.

- [ ] **Step 11: Commit**

```bash
git add src/my/stepss/platform/PlatformLauncher.java src/my/stepss/StepssUI.java src/my/stepss/diagram/DiagramCheck.java
```
```bash
git commit -m "System Data: an optional one-line diagram slot

A third section under the disturbance one, naming the SVG template Helios
fills in. Built in layoutSystemDataTab like the rest of the tab, so
StepssUI.form is untouched.

Its open button is not the one the twelve data rows use. That falls back
to Notepad and to open -t, which shows an SVG as XML source, so this gets
a sibling launcher whose fallback is the shell's own open and reaches a
drawing program instead."
```

---

## Task 6: The diagram slot is part of a saved scenario

**Files:**
- Modify: `src/my/stepss/config/Scenario.java`
- Modify: `src/my/stepss/config/ScenarioBinding.java`
- Modify: `src/my/stepss/config/ScenarioFile.java`
- Modify: `src/my/stepss/config/ScenarioHarness.java`
- Modify: `src/my/stepss/StepssUI.java` (`bindScenario`)

**Interfaces:**
- Consumes: `fileDiagram` from Task 5.
- Produces: `Scenario.diagram()` and `Scenario.setDiagram(String)`; the `.cfg` key `diagram`. `ScenarioBinding`'s constructor gains an eleventh parameter, `JTextField diagramField`, placed immediately after `observablesField`.

- [ ] **Step 1: Write the failing checks**

In `src/my/stepss/config/ScenarioHarness.java`, add `diagram` to the `Form` class:

```java
        private final JTextField diagram = new JTextField();
```

built into the tree beside the others (change the existing `content.add(row(disturbance, observables, wizard));` to `content.add(row(disturbance, observables, diagram, wizard));`), passed to the binding:

```java
            binding = new ScenarioBinding(data, disturbance, observables, diagram,
                    wizard, types, names, trajectory, continuous, discrete, dump);
```

and cleared in `blank()` alongside `disturbance` and `observables`.

Add the check and call it from `main()` after `checkRoundTripThroughANestedTree();`:

```java
    /**
     * The one-line diagram path survives a save and a load.
     *
     * <p>It is part of a scenario for the same reason every other path is:
     * {@code createPFCCommandFile()} reads it, so a scenario that round-trips
     * is a run that reproduces.
     */
    private static void checkTheDiagramSlotRoundTrips() throws IOException {
        File dir = tempDir("diagram");
        Form form = new Form();
        form.data[0].setText(touch(dir, "6bus.dat"));
        String svg = touch(dir, "6bus.svg");
        form.diagram.setText(svg);

        File cfg = temp(dir, "case.cfg");
        ScenarioFile.save(form.binding.read(), cfg, dir, "3.75.0");

        form.blank();
        expect("the form was blanked", "", form.diagram.getText());

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        form.binding.apply(loaded.scenario());
        expect("the diagram slot comes back", svg, form.diagram.getText());
        expect("a clean load reports no problems", true, loaded.problems().isEmpty());
    }

    /** An empty diagram slot round-trips as empty, not as the .cfg's directory. */
    private static void checkAnEmptyDiagramSlotStaysEmpty() throws IOException {
        File dir = tempDir("nodiagram");
        Form form = new Form();
        File cfg = temp(dir, "case.cfg");
        ScenarioFile.save(form.binding.read(), cfg, dir, "3.75.0");

        ScenarioFile.Loaded loaded = ScenarioFile.load(cfg);
        form.binding.apply(loaded.scenario());
        expect("an unset diagram slot loads empty", "", form.diagram.getText());
    }
```

These use the harness's own helpers, which are not the ones the other harnesses use: `tempDir(String)` makes the directory, `temp(File, String)` names a file inside it and books its removal, `touch(File, String)` creates an empty file and returns its absolute path, and assertions are `expect(what, want, got)` rather than a boolean `check`. `ScenarioHarness` has no `check` method at all; do not add one.

`touch` rather than a written file because `ScenarioFile.load` only checks that a path still resolves to something, and an empty file resolves. `temp` rather than `new File` for the `.cfg` because `deleteOnExit` deletes in reverse registration order, so a saved file nobody registered is what leaves the directory behind and makes every run litter `/tmp`.

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```
Expected: FAIL, `cannot find symbol: method setDiagram` or a constructor arity error on `ScenarioBinding`.

- [ ] **Step 3: Add the field to `Scenario`**

In `src/my/stepss/config/Scenario.java`, beside `observablesFile`:

```java
    private String diagram = "";
```

and after the observables accessors:

```java
    /** The one-line diagram template (.svg) path, or "". */
    public String diagram() {
        return diagram;
    }

    /** @param value the diagram template path, null read as "" */
    public void setDiagram(String value) {
        this.diagram = text(value);
    }
```

- [ ] **Step 4: Bind it**

In `src/my/stepss/config/ScenarioBinding.java`: add the field `private final JTextField diagramField;`, add the constructor parameter `JTextField diagramField` immediately after `observablesField`, add `requireNotNull(diagramField, "diagram field");` beside the other null checks, assign it, add to `read()`:

```java
        scenario.setDiagram(diagramField.getText());
```

and to `apply()`:

```java
        diagramField.setText(scenario.diagram());
```

Update the constructor's `@param` list with `@param diagramField the one-line diagram template path`.

- [ ] **Step 5: Persist it**

In `src/my/stepss/config/ScenarioFile.java`, add the key beside the others:

```java
    private static final String DIAGRAM = "diagram";
```

In `save()`, in the `# System data` block after the `DISTURBANCE` write:

```java
            write(out, DIAGRAM,
                    ScenarioPaths.store(scenario.diagram(), cfgDir, workingDir));
```

In `load()`, after the disturbance line:

```java
        scenario.setDiagram(path(properties, DIAGRAM, cfgDir,
                "The one-line diagram file", problems));
```

In `knownKeys()`, after `keys.add(DISTURBANCE);`:

```java
        keys.add(DIAGRAM);
```

Add to the class comment, after the paragraph about naming the keys explicitly:

```java
 * <p>{@code stepss.format} stays at 1 for the {@code diagram} key added in
 * 2026-08. {@link #checkFormat} throws on a format BELOW the one this build
 * writes, so a bump would make this build refuse every file already saved,
 * while an added key costs an older build one advisory sentence from
 * {@link #reportUnknownKeys} and nothing else. That asymmetry is why a new
 * optional key is not a format change.
```

- [ ] **Step 6: Pass the field in `bindScenario`**

In `src/my/stepss/StepssUI.java`:

```java
        return new ScenarioBinding(
                new JTextField[]{fileData1, fileData2, fileData3, fileData4, fileData5,
                    fileData6, fileData7, fileData8, fileData9, fileData10},
                fileDist, fileObs, fileDiagram, observFileWizButton,
                new JComboBox<?>[]{runtimeObsType, runtimeObsType1, runtimeObsType2},
                new JTextField[]{runtimeObsName, runtimeObsName1, runtimeObsName2},
                saveOutputTrajButton, saveContTrace, saveDiscTrace, saveDumpButton);
```

- [ ] **Step 7: Run the checks**

```bash
ant compile
```
```bash
tools/scenario-harness.sh
```
Expected: `ALL CHECKS PASSED`, with the two new lines among them. `checkBindingRejectsAShortWiring` still passes: it builds a binding one data field short, and that is unaffected by the new parameter.

- [ ] **Step 8: Commit**

```bash
git add src/my/stepss/config/Scenario.java src/my/stepss/config/ScenarioBinding.java src/my/stepss/config/ScenarioFile.java src/my/stepss/config/ScenarioHarness.java src/my/stepss/StepssUI.java
```
```bash
git commit -m "Scenarios carry the one-line diagram slot

createPFCCommandFile reads it, and the rule that file's comment states is
that a scenario holds exactly what the two command-file writers read, so
a scenario that round-trips is a run that reproduces.

stepss.format stays at 1. checkFormat throws on a format below the one
this build writes, so bumping it would refuse every .cfg already saved,
while an added key costs an older build one advisory sentence."
```

---

## Task 7: Render the diagram in the same Helios run

**Files:**
- Modify: `src/my/stepss/HeliosLog.java`
- Modify: `src/my/stepss/StepssUI.java` (`createPFCCommandFile`, `deletePFCResultFiles`)
- Modify: `src/my/stepss/diagram/DiagramCheck.java`

**Interfaces:**
- Consumes: `fileDiagram` from Task 5.
- Produces: the constant `StepssUI.DIAGRAM_OUTPUT` (`"in_diagram.svg"`), and the static `StepssUI.diagramCommands(String templatePath, String outputName)` returning the command-file fragment as a `String`. Task 12 reads the rendered file at `myTempDir/in_diagram.svg`.

- [ ] **Step 1: Write the failing checks**

Add to `src/my/stepss/diagram/DiagramCheck.java`, called from `main()`:

```java
    private static void checkTheDiagramCommandBlock() {
        String block = my.stepss.StepssUI.diagramCommands("/case/6bus.svg", "in_diagram.svg");
        check("the block starts with the main-menu command",
                block.startsWith("1\n"));
        check("the block names the template",
                block.contains("\n/case/6bus.svg\n"));
        check("the block names the output",
                block.endsWith("in_diagram.svg\n"));
        check("the block is exactly three lines",
                block.split("\n", -1).length == 4);
    }

    private static void checkHeliosDiagramLinesReachTheConsole() {
        // cmd_diagram writes all of these to stdout, which HeliosLog filters
        // against a fixed prefix list. Without them a failed render is silent.
        String[] lines = {
            "Open in_diagram.svg in your browser",
            "This file does not exist !",
            "output file must be different from input file !",
            "DiagramRenderer: cannot open template: /case/6bus.svg"
        };
        for (String line : lines) {
            check("the console keeps: " + line, my.stepss.HeliosLog.isProgressLine(line));
        }
        check("a table row is still dropped",
                !my.stepss.HeliosLog.isProgressLine("  A      6.000   1.0210    0.00"));
    }
```

`HeliosLog` and `isProgressLine` are package-private. Make both public so the harness in `my.stepss.diagram` can reach them, and note why in the class comment:

```java
 * <p>Public rather than package-private so {@code my.stepss.diagram.DiagramCheck}
 * can exercise {@link #isProgressLine} directly. The prefix list is a soft
 * contract on Helios' wording, and a contract nothing checks is one that drifts.
```

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```
Expected: FAIL, `cannot find symbol: method diagramCommands`.

- [ ] **Step 3: Add the four progress prefixes**

In `src/my/stepss/HeliosLog.java`, add to `PROGRESS_PREFIXES`:

```java
        "Open ",
        "This file does not exist !",
        "output file must be different from input file !",
        "DiagramRenderer: ",
```

and extend the field's comment:

```java
     * <p>The last four are the {@code 1} command's own lines, from
     * {@code cmd_diagram} in PlainMenu.cpp and from {@code DiagramRenderer::render}.
     * They are all written to stdout and none of them fails the run, so without
     * them here a template that cannot be read produces no diagram, no error and
     * nothing in the console.
```

- [ ] **Step 4: Emit the command block**

In `src/my/stepss/StepssUI.java`, add the constant beside the other result-file names:

```java
    /**
     * Where Helios' {@code 1} command writes the annotated diagram.
     *
     * <p>A bare name, so it lands in the run's working directory, which
     * {@code simulExecutor.setWorkingDirectory(myTempDir)} has already set.
     */
    static final String DIAGRAM_OUTPUT = "in_diagram.svg";
```

and the pure builder:

```java
    /**
     * The three command-file lines that render one template.
     *
     * <p>{@code 1} is a main-menu command like {@code VT}, so it sits outside
     * the {@code D} display sub-menu block. {@code cmd_diagram} then reads two
     * lines: the template and the output name.
     *
     * <p>Static and returning a string so the harness can check it without a
     * live frame, which is the same reason {@code describeHeliosExit} is
     * separable from the run that calls it.
     */
    static String diagramCommands(String templatePath, String outputName) {
        return "1\n" + templatePath + "\n" + outputName + "\n";
    }
```

In `createPFCCommandFile()`, insert immediately **before** the `out.append("VT\n");` line:

```java
            // Before VT, not after. The completion thread in
            // runPFActionPerformed waits for in_volt_trfo.dat, which VT writes,
            // and treats its appearance as the run being finished. Rendering
            // after it would fire that sentinel while the diagram was still
            // being written.
            String template = fileDiagram.getText();
            if (!template.isEmpty()) {
                File templateFile = new File(template);
                if (!templateFile.isFile()) {
                    out.close();
                    return "The one-line diagram file " + templateFile.getName()
                            + " does not exist. Choose another on the System Data tab,"
                            + " or clear the slot.";
                }
                if (sameFile(templateFile, new File(myTempDir, DIAGRAM_OUTPUT))) {
                    out.close();
                    return "The one-line diagram template is the file Helios writes"
                            + " its result to. Helios refuses to overwrite its own"
                            + " input, so nothing would be drawn. Move the template"
                            + " or rename it.";
                }
                out.append(diagramCommands(templateFile.getAbsolutePath(), DIAGRAM_OUTPUT));
            }
```

and add the comparison helper beside it:

```java
    /**
     * Whether two paths name the same file, canonical paths compared.
     *
     * <p>Falls back to the absolute paths when either cannot be canonicalised,
     * which is the safe direction: a false negative here lets Helios say
     * "output file must be different from input file !" for itself, and that
     * line now reaches the console.
     */
    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException ex) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }
```

- [ ] **Step 5: Clear the previous run's diagram**

In `deletePFCResultFiles()`, add `DIAGRAM_OUTPUT` to the array:

```java
        String[] resultFiles = {
            "in_net.res", "in_trfo.res", "in_gen.res", "in_bal.res",
            "in_svc.res", "in_flow.res", "in_volt_trfo.dat", DIAGRAM_OUTPUT
        };
```

and extend the method comment with the reason, which is the same one it already gives for the other seven: a run that aborts before reaching the `1` command must not leave the previous run's diagram to be shown as this run's.

- [ ] **Step 6: Run the checks**

```bash
ant compile
```
```bash
tools/diagram-harness.sh
```
Expected: `ALL DIAGRAM CHECKS PASSED`.

- [ ] **Step 7: Verify the command file by hand**

```bash
ant jar
```
```bash
java -jar dist/stepss.jar
```
Open the 6-bus example, press Run Power Flow, then in a second terminal:

```bash
cat ~/<your working directory>/six-bus-microgrid/PFCcmd.txt
```
Expected: a `1` line followed by the absolute template path and `in_diagram.svg`, sitting between the `X`/`in_bal.res` pair and the `VT` line. And:

```bash
ls -l ~/<your working directory>/six-bus-microgrid/in_diagram.svg
```
Expected: the file exists and is roughly the size of the template. Open it in a browser: it holds the solved numbers rather than the placeholder codes. Close the application.

- [ ] **Step 8: Commit**

```bash
git add src/my/stepss/HeliosLog.java src/my/stepss/StepssUI.java src/my/stepss/diagram/DiagramCheck.java
```
```bash
git commit -m "Render the one-line diagram in the power flow run

Three lines appended to PFCcmd.txt when the slot is filled, so the
diagram comes out of the same solve rather than a second one. Emitted
before VT because the completion thread treats in_volt_trfo.dat, which VT
writes, as the run being finished.

cmd_diagram writes its errors to stdout and does not fail the run, so the
four lines it can produce are added to HeliosLog's prefix list. Without
them an unreadable template produced no diagram and nothing in the
console."
```

---

## Task 8: `SvgImage`, Batik rendering with no live canvas

**Files:**
- Create: `src/my/stepss/diagram/SvgImage.java`
- Create: `lib/batik-all-1.19.jar`, `lib/xmlgraphics-commons-2.11.jar`, `lib/xml-apis-ext-1.3.04.jar`
- Modify: `nbproject/project.properties`
- Modify: `packaging/linux/copyright`
- Modify: `src/my/stepss/diagram/DiagramCheck.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `SvgImage.load(File)` returning `SvgImage` and throwing `IOException`; `documentBounds()` returning `java.awt.geom.Rectangle2D`; `render(Rectangle2D aoi, int width, int height)` returning `java.awt.image.BufferedImage` and throwing `IOException`; `renderWhole(int width)` returning `BufferedImage` and throwing `IOException`; `copyTo(File)` throwing `IOException`. Tasks 10 and 12 consume all of these.

- [ ] **Step 1: Fetch the three jars**

```bash
curl -sSL -o lib/batik-all-1.19.jar https://repo1.maven.org/maven2/org/apache/xmlgraphics/batik-all/1.19/batik-all-1.19.jar
```
```bash
curl -sSL -o lib/xmlgraphics-commons-2.11.jar https://repo1.maven.org/maven2/org/apache/xmlgraphics/xmlgraphics-commons/2.11/xmlgraphics-commons-2.11.jar
```
```bash
curl -sSL -o lib/xml-apis-ext-1.3.04.jar https://repo1.maven.org/maven2/xml-apis/xml-apis-ext/1.3.04/xml-apis-ext-1.3.04.jar
```

Verify they are jars and not HTML error pages:

```bash
file lib/batik-all-1.19.jar lib/xmlgraphics-commons-2.11.jar lib/xml-apis-ext-1.3.04.jar
```
Expected: three `Java archive data (JAR)` lines.

Record the digests in the commit message in Step 8:

```bash
sha256sum lib/batik-all-1.19.jar lib/xmlgraphics-commons-2.11.jar lib/xml-apis-ext-1.3.04.jar
```

**Do not fetch `xml-apis` 1.4.01.** Batik declares it, but it re-declares `org.w3c.dom` interfaces the JDK already ships, and on Java 11 it shadows the platform's newer ones and produces `NoSuchMethodError` at runtime. Only `xml-apis-ext` is wanted, because that carries the SVG and SMIL DOM interfaces the JDK does not have.

- [ ] **Step 2: Put them on the classpath**

In `nbproject/project.properties`, beside the three existing `file.reference` lines:

```properties
file.reference.batik-all-1.19.jar=lib\\batik-all-1.19.jar
file.reference.xmlgraphics-commons-2.11.jar=lib\\xmlgraphics-commons-2.11.jar
file.reference.xml-apis-ext-1.3.04.jar=lib\\xml-apis-ext-1.3.04.jar
```

and extend `javac.classpath`:

```properties
javac.classpath=\
    ${file.reference.commons-exec-1.3.jar}:\
    ${file.reference.commons-io-2.11.0.jar}:\
    ${file.reference.flatlaf-3.7.2.jar}:\
    ${file.reference.batik-all-1.19.jar}:\
    ${file.reference.xmlgraphics-commons-2.11.jar}:\
    ${file.reference.xml-apis-ext-1.3.04.jar}
```

- [ ] **Step 3: Write the failing checks**

Add to `src/my/stepss/diagram/DiagramCheck.java`. The checks need a template on disk, so write one from a string rather than depending on the example payload:

```java
    /** A minimal document with known bounds and one black square in the corner. */
    private static final String TEST_SVG
            = "<?xml version=\"1.0\"?>\n"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"100\""
            + " viewBox=\"0 0 200 100\">\n"
            + "  <rect x=\"0\" y=\"0\" width=\"20\" height=\"20\" fill=\"black\"/>\n"
            + "</svg>\n";

    private static java.io.File writeTestSvg(String body) throws java.io.IOException {
        java.io.File file = java.io.File.createTempFile("stepss-diagram", ".svg");
        file.deleteOnExit();
        java.nio.file.Files.write(file.toPath(),
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return file;
    }

    /** A pixel count, for asserting that a region actually drew something. */
    private static int inkedPixels(java.awt.image.BufferedImage image) {
        int inked = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                boolean opaque = ((argb >>> 24) & 0xff) > 0;
                boolean dark = (argb & 0xffffff) != 0xffffff;
                if (opaque && dark) {
                    inked++;
                }
            }
        }
        return inked;
    }

    /**
     * The end-to-end check, and the one that answers the Rhino question.
     *
     * <p>batik-all registers a Rhino interpreter factory through
     * META-INF/services and Rhino is not shipped. If that were not harmless,
     * this is where it would surface, on the classpath the application has.
     */
    private static void checkASvgRendersAtAll() throws Exception {
        java.io.File file = writeTestSvg(TEST_SVG);
        SvgImage image = SvgImage.load(file);
        check("the document bounds are read",
                Math.abs(image.documentBounds().getWidth() - 200.0) < 0.5
                        && Math.abs(image.documentBounds().getHeight() - 100.0) < 0.5);
        java.awt.image.BufferedImage rendered = image.renderWhole(400);
        check("the whole document renders at the width asked for",
                rendered.getWidth() == 400);
        check("it renders at the document's aspect ratio", rendered.getHeight() == 200);
        check("something was drawn", inkedPixels(rendered) > 0);
    }

    /** Zoom is a smaller AOI at the same pixel size, not a bigger image. */
    private static void checkZoomCostsNothingExtra() throws Exception {
        SvgImage image = SvgImage.load(writeTestSvg(TEST_SVG));
        java.awt.image.BufferedImage wide = image.render(
                new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100), 400, 200);
        java.awt.image.BufferedImage tight = image.render(
                new java.awt.geom.Rectangle2D.Double(0, 0, 20, 10), 400, 200);
        check("a tenfold zoom renders the same number of pixels",
                tight.getWidth() == wide.getWidth()
                        && tight.getHeight() == wide.getHeight());
        check("and it is not the same picture",
                inkedPixels(tight) != inkedPixels(wide));
        check("zooming into the square fills more of the frame",
                inkedPixels(tight) > inkedPixels(wide));
    }

    /** Panning off the square leaves an empty frame. */
    private static void checkPanMovesTheRegion() throws Exception {
        SvgImage image = SvgImage.load(writeTestSvg(TEST_SVG));
        java.awt.image.BufferedImage onIt = image.render(
                new java.awt.geom.Rectangle2D.Double(0, 0, 20, 10), 200, 100);
        java.awt.image.BufferedImage offIt = image.render(
                new java.awt.geom.Rectangle2D.Double(150, 50, 20, 10), 200, 100);
        check("the region with the square has ink", inkedPixels(onIt) > 0);
        check("the region without it does not", inkedPixels(offIt) == 0);
    }

    /** A file that is not an SVG raises rather than producing a blank image. */
    private static void checkAMalformedSvgRaises() throws Exception {
        java.io.File file = writeTestSvg("this is not markup at all");
        try {
            SvgImage.load(file);
            check("a malformed SVG is refused", false);
        } catch (java.io.IOException expected) {
            check("a malformed SVG is refused", true);
        }
    }

    /**
     * An external reference is refused rather than fetched.
     *
     * <p>Batik's default transcoder user agent both declines the fetch and
     * aborts the render, so the refusal surfaces as an IOException naming the
     * resource. Asserting the message rather than merely "it threw" is what
     * distinguishes a refused fetch from a broken file, which is the other
     * thing that throws here.
     */
    private static void checkExternalResourcesAreRefused() throws Exception {
        String hostile = TEST_SVG.replace("</svg>",
                "  <image xlink:href=\"http://127.0.0.1:1/should-not-be-fetched.png\""
                + " x=\"0\" y=\"0\" width=\"10\" height=\"10\"/>\n</svg>")
                .replace("<svg xmlns=\"http://www.w3.org/2000/svg\"",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\""
                        + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
        SvgImage image = SvgImage.load(writeTestSvg(hostile));
        try {
            image.renderWhole(200);
            check("an external reference is refused", false);
        } catch (java.io.IOException refused) {
            check("an external reference is refused", true);
            check("and the refusal names the resource",
                    String.valueOf(refused.getMessage()).contains("127.0.0.1")
                            || String.valueOf(refused.getCause()).contains("127.0.0.1"));
        }
    }

    /**
     * A metadata element the SVG DOM does not know does not lose the document.
     *
     * <p>The case this exists for is real and is the bundled example: WinFIG
     * writes {@code <version>1.0</version>} inside {@code <desc>}, which
     * inherits the SVG default namespace, and the strict DOM refuses the whole
     * drawing over it. Browsers ignore such elements, and so must a viewer of
     * files it did not author.
     */
    private static void checkAnUnknownElementDoesNotLoseTheDocument() throws Exception {
        String withCruft = TEST_SVG.replace("<rect",
                "<desc> METADATA <version id=\"v8\">1.0</version></desc>\n  <rect");
        SvgImage image = SvgImage.load(writeTestSvg(withCruft));
        java.awt.image.BufferedImage rendered = image.renderWhole(400);
        check("an unknown element in the SVG namespace is tolerated",
                rendered.getWidth() == 400);
        check("and the rest of the drawing still draws",
                inkedPixels(rendered) > 0);
    }
```

Call all six from `main()`, and change `main`'s signature to `public static void main(String[] args) throws Exception` if it is not already.

- [ ] **Step 4: Run it to verify it fails**

```bash
ant compile
```
Expected: FAIL, `cannot find symbol: class SvgImage`.

- [ ] **Step 5: Write `SvgImage`**

Create `src/my/stepss/diagram/SvgImage.java`:

```java
package my.stepss.diagram;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.dom.AbstractDocument;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;

/**
 * One SVG, parsed once and rendered on demand.
 *
 * <p>Holds the parsed document rather than the file, deliberately. The window
 * showing a run's diagram must keep showing that run's diagram: the next Run
 * Power Flow deletes and rewrites {@code in_diagram.svg}, and a class that
 * re-read the file on every zoom would quietly change what an already open
 * window displayed. This is the same reasoning {@code SsaResultsWindow} records
 * for holding its own parsed results, and it is what makes comparing two runs
 * side by side true rather than nearly true.
 *
 * <p>Rendering goes through {@link ImageTranscoder} rather than
 * {@code JSVGCanvas}. The canvas starts an update-manager thread, follows
 * external references through its document loader and wires up the script
 * bridge; for a static drawing all three are surface with no purpose, and two
 * of them are the shape of CVE-2022-44729 and CVE-2022-44730. Nothing here
 * animates, scripts or fetches.
 *
 * <p>{@link #render} takes an area of interest in the document's own user
 * space, which is what keeps this affordable on a large network: the image is
 * always the size of the viewport, so an eightfold zoom costs the same as
 * fitting the whole diagram rather than eight times as much.
 */
public final class SvgImage {

    private final SVGDocument document;
    private final File source;
    private final Rectangle2D bounds;

    private SvgImage(SVGDocument document, File source, Rectangle2D bounds) {
        this.document = document;
        this.source = source;
        this.bounds = bounds;
    }

    /**
     * Parses {@code file}.
     *
     * @throws IOException if it cannot be read or is not SVG. Raising is the
     * point: a renderer that answered a malformed file with a blank image would
     * put an empty window on screen with nothing saying why.
     */
    public static SvgImage load(File file) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = lenientFactory(parser);
        SVGDocument document;
        try {
            document = (SVGDocument) factory.createDocument(file.toURI().toString());
        } catch (IOException ex) {
            throw new IOException("Could not read " + file.getName()
                    + " as an SVG file: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            // Batik reports a document that is not markup by throwing from the
            // SAX layer, which arrives here unchecked.
            throw new IOException(file.getName() + " is not a readable SVG file: "
                    + ex.getMessage(), ex);
        }
        return new SvgImage(document, file, readBounds(document, file));
    }

    /**
     * A document factory that tolerates elements the SVG DOM does not know.
     *
     * <p>Batik's strict DOM throws {@code DOMException} for any unrecognised
     * local name in the SVG namespace, and refuses the entire document over
     * it. Drawing tools emit exactly that: the bundled 6-bus example carries
     * {@code <version>1.0</version>} inside {@code <desc>}, left there by
     * WinFIG, which inherits the SVG default namespace and is not an SVG
     * element. Every browser ignores it. A viewer of files it did not author
     * has to do the same, or the first real diagram a user loads is refused
     * for a reason that has nothing to do with the drawing.
     *
     * <p>An unknown element is remapped into the null namespace, which makes a
     * generic DOM element. GVT has no bridge for one, so it contributes no
     * graphics and the rest of the drawing renders unchanged. Verified against
     * the real 6bus.svg: identical output to a copy with the element deleted.
     *
     * <p>The implementation cannot be installed by assigning the factory's
     * field. {@code SAXSVGDocumentFactory} overrides
     * {@code getDOMImplementation(String)} to return the strict singleton and
     * never consults the field (SAXSVGDocumentFactory.java:323), so the
     * override below is the hook that works.
     *
     * <p>This does not weaken the refusal of a genuinely broken file. Markup
     * that is not XML fails inside the SAX parser, before any element is
     * created, and never reaches this path.
     */
    private static SAXSVGDocumentFactory lenientFactory(String parser) {
        final SVGDOMImplementation lenient = new SVGDOMImplementation() {
            @Override
            public Element createElementNS(AbstractDocument document,
                    String namespaceURI, String qualifiedName) {
                try {
                    return super.createElementNS(document, namespaceURI, qualifiedName);
                } catch (DOMException unknownElement) {
                    return super.createElementNS(document, null, qualifiedName);
                }
            }
        };
        return new SAXSVGDocumentFactory(parser) {
            @Override
            public DOMImplementation getDOMImplementation(String version) {
                return lenient;
            }
        };
    }

    /**
     * The document's own coordinate extent: its {@code viewBox} when it has
     * one, its width and height otherwise.
     *
     * <p>Read from the attributes rather than from a built scene graph, so
     * loading costs one parse. A document with neither is refused, because
     * everything downstream divides by these numbers.
     */
    private static Rectangle2D readBounds(SVGDocument document, File file)
            throws IOException {
        String viewBox = document.getRootElement().getAttribute("viewBox");
        if (viewBox != null && !viewBox.trim().isEmpty()) {
            String[] parts = viewBox.trim().split("[\\s,]+");
            if (parts.length == 4) {
                try {
                    return new Rectangle2D.Double(
                            Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                } catch (NumberFormatException notNumbers) {
                    throw new IOException(file.getName()
                            + " has a viewBox that is not four numbers: " + viewBox);
                }
            }
        }
        double width = length(document.getRootElement().getAttribute("width"));
        double height = length(document.getRootElement().getAttribute("height"));
        if (width <= 0 || height <= 0) {
            throw new IOException(file.getName() + " declares no viewBox and no"
                    + " usable width and height, so there is nothing to say how"
                    + " large the drawing is.");
        }
        return new Rectangle2D.Double(0, 0, width, height);
    }

    /**
     * A length attribute's numeric part, unit ignored.
     *
     * <p>Only reached for a document with no viewBox, where the units decide
     * the aspect ratio and nothing else: the value is used as a ratio against
     * the other axis, so "9.9in" by "5.3in" and "9.9" by "5.3" give the same
     * shape. A document mixing units across the two axes would be wrong here,
     * and is not something a drawing tool produces.
     */
    private static double length(String value) {
        if (value == null) {
            return 0;
        }
        String digits = value.trim().replaceAll("[^0-9.\\-].*$", "");
        try {
            return digits.isEmpty() ? 0 : Double.parseDouble(digits);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /** The document's coordinate extent. Never null, never empty. */
    public Rectangle2D documentBounds() {
        return (Rectangle2D) bounds.clone();
    }

    /**
     * Renders one rectangle of the document at a given pixel size.
     *
     * <p>{@code aoi} should carry the same aspect ratio as the output. Batik
     * takes a single uniform scale from the two axes and centres the result
     * when they disagree, so matching them keeps this class's arithmetic the
     * only thing deciding what appears.
     *
     * @param aoi    the region, in the document's own user space
     * @param width  the output width in pixels, at least 1
     * @param height the output height in pixels, at least 1
     */
    public BufferedImage render(Rectangle2D aoi, int width, int height)
            throws IOException {
        Capture capture = new Capture();
        capture.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH,
                Float.valueOf(Math.max(1, width)));
        capture.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT,
                Float.valueOf(Math.max(1, height)));
        capture.addTranscodingHint(SVGAbstractTranscoder.KEY_AOI, aoi);
        try {
            capture.transcode(new TranscoderInput(document), new TranscoderOutput());
        } catch (TranscoderException ex) {
            throw new IOException("Could not draw " + source.getName() + ": "
                    + ex.getMessage(), ex);
        }
        return capture.image;
    }

    /**
     * Renders the whole document at a given width, aspect preserved.
     *
     * <p>What Save as PNG uses. A saved figure goes into a report, so it is the
     * whole drawing at a generous size rather than whatever the window happened
     * to be showing: zoom is a reading aid here, not a crop tool.
     */
    public BufferedImage renderWhole(int width) throws IOException {
        int height = (int) Math.round(width * bounds.getHeight() / bounds.getWidth());
        return render(documentBounds(), width, Math.max(1, height));
    }

    /** Copies the SVG this was loaded from to {@code dest}, for Save as SVG. */
    public void copyTo(File dest) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * An {@link ImageTranscoder} that keeps the image instead of encoding it.
     *
     * <p>This is what makes {@code batik-codec} unnecessary: that module exists
     * to write PNG bytes, and the JDK's own {@code ImageIO} does that when the
     * user asks for a file. Nothing here writes to the {@code TranscoderOutput}
     * at all.
     */
    private static final class Capture extends ImageTranscoder {

        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput output) {
            this.image = img;
        }
    }
}
```

The restrictive user agent is not written by hand: `ImageTranscoder` already builds a `BridgeContext` in static state with no scripting environment, and nothing in this class enables `KEY_EXECUTE_ONLOAD`, which is what would turn scripting on. The external-resource check in Step 3 is what holds that to account.

- [ ] **Step 6: Run the checks**

```bash
ant compile
```
```bash
tools/diagram-harness.sh
```
Expected: `ALL DIAGRAM CHECKS PASSED`.

The Rhino risk the spec flags has already been settled by a controller probe against these exact jars: `batik-all-1.19` carries zero `org/mozilla/javascript` classes yet registers `META-INF/services/org.apache.batik.script.InterpreterFactory`, and transcoding works regardless. The check is kept because it is what holds that answer in place, not because the answer is in doubt.

If it nonetheless fails with `NoClassDefFoundError: org/mozilla/javascript/...`, the fallback is to replace `batik-all` with the individual module jars, omitting `batik-script`. Stop and report rather than adding Rhino, because Rhino is a JavaScript engine and nothing here should be able to run JavaScript.

- [ ] **Step 7: Name Batik in the packaging copyright**

Read `packaging/linux/copyright` first to match its existing stanza format, then add a stanza for the three jars, Apache-2.0, upstream `https://xmlgraphics.apache.org/batik/`. Keep it a summary that points at `getting-started/license.md` in `stepss-docs` for the component licences, exactly as the file already does: these are bundled third-party libraries, not STEPSS components, so nothing about RAMSES, Helios or CODEGEN changes.

- [ ] **Step 8: Commit**

```bash
git add lib/batik-all-1.19.jar lib/xmlgraphics-commons-2.11.jar lib/xml-apis-ext-1.3.04.jar nbproject/project.properties packaging/linux/copyright src/my/stepss/diagram/SvgImage.java src/my/stepss/diagram/DiagramCheck.java
```
```bash
git commit -m "Render SVG through Batik, viewport at a time

SvgImage parses a document once and renders any rectangle of it at any
pixel size through KEY_AOI. That is what keeps a large diagram
affordable: the image is the size of the window, so an eightfold zoom
costs what fitting the whole drawing costs rather than eight times more.

Not JSVGCanvas. The canvas starts an update-manager thread, follows
external references and wires the script bridge, all of which are surface
with no purpose for a static drawing and two of which are the shape of
CVE-2022-44729 and CVE-2022-44730.

batik-all 1.19 with xmlgraphics-commons 2.11 and xml-apis-ext 1.3.04, the
versions Batik's own pom pins. xml-apis 1.4.01 is deliberately NOT added:
it re-declares org.w3c.dom interfaces the JDK ships and shadows them on
Java 11.

sha256:
  <batik-all digest>  batik-all-1.19.jar
  <xmlgraphics digest>  xmlgraphics-commons-2.11.jar
  <xml-apis-ext digest>  xml-apis-ext-1.3.04.jar"
```

Replace the three digest lines with the real output from Step 1.

---

## Task 9: `DiagramView`, the zoom and pan arithmetic

Kept separate from the Swing component so it can be checked without a display. Every navigation bug is an arithmetic bug, and arithmetic is the part that can be pinned.

**Files:**
- Create: `src/my/stepss/diagram/DiagramView.java`
- Modify: `src/my/stepss/diagram/DiagramCheck.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `new DiagramView(Rectangle2D documentBounds)`; `setViewport(int width, int height)`; `Rectangle2D aoi()`; `void fit()`; `void zoomAt(double factor, int deviceX, int deviceY)`; `double zoom()` where 1.0 is Fit; `void panBy(int deviceDx, int deviceDy)`; `void setZoom(double zoom)`. Task 10 consumes all of these.

- [ ] **Step 1: Write the failing checks**

Add to `src/my/stepss/diagram/DiagramCheck.java`, called from `main()`:

```java
    private static DiagramView view() {
        DiagramView v = new DiagramView(new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100));
        v.setViewport(400, 200);
        return v;
    }

    private static void checkFitShowsTheWholeDocument() {
        java.awt.geom.Rectangle2D aoi = view().aoi();
        check("fit spans the document width", Math.abs(aoi.getWidth() - 200) < 0.01);
        check("fit spans the document height", Math.abs(aoi.getHeight() - 100) < 0.01);
        check("fit starts at the origin",
                Math.abs(aoi.getX()) < 0.01 && Math.abs(aoi.getY()) < 0.01);
    }

    private static void checkTheAoiKeepsTheViewportAspect() {
        DiagramView v = new DiagramView(new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100));
        v.setViewport(400, 400);
        java.awt.geom.Rectangle2D aoi = v.aoi();
        check("a square viewport gets a square area of interest",
                Math.abs(aoi.getWidth() - aoi.getHeight()) < 0.01);
        check("and the document still fits inside it",
                aoi.getWidth() >= 200 - 0.01 && aoi.getHeight() >= 100 - 0.01);
    }

    private static void checkZoomShrinksTheAoi() {
        DiagramView v = view();
        v.zoomAt(2.0, 200, 100);
        check("zooming doubles the magnification", Math.abs(v.zoom() - 2.0) < 0.01);
        check("which halves the area of interest",
                Math.abs(v.aoi().getWidth() - 100) < 0.01);
    }

    private static void checkZoomIsAnchoredAtThePointer() {
        DiagramView v = view();
        // The document point under the top-left corner of the viewport.
        double beforeX = v.aoi().getX();
        double beforeY = v.aoi().getY();
        v.zoomAt(2.0, 0, 0);
        check("zooming at the top-left leaves that point where it was",
                Math.abs(v.aoi().getX() - beforeX) < 0.01
                        && Math.abs(v.aoi().getY() - beforeY) < 0.01);
    }

    private static void checkPanMovesTheAoiByTheDeviceDelta() {
        DiagramView v = view();
        v.setZoom(2.0);
        double beforeX = v.aoi().getX();
        // At zoom 2 the viewport is 400 px across showing 100 user units, so
        // one user unit is four pixels and dragging 40 px moves 10 units.
        v.panBy(-40, 0);
        check("panning moves the area of interest by the scaled delta",
                Math.abs((v.aoi().getX() - beforeX) - 10.0) < 0.01);
    }

    private static void checkZoomIsClamped() {
        DiagramView v = view();
        v.setZoom(10000.0);
        check("zoom is clamped at the top", v.zoom() <= 50.0 + 0.01);
        v.setZoom(0.0001);
        check("zoom is clamped at the bottom", v.zoom() >= 0.05 - 0.001);
    }

    private static void checkPanIsClamped() {
        DiagramView v = view();
        v.setZoom(4.0);
        v.panBy(-100000, -100000);
        check("the document cannot be pushed off to the left",
                v.aoi().intersects(new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100)));
        v.panBy(200000, 200000);
        check("or off to the right",
                v.aoi().intersects(new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100)));
    }

    private static void checkFitComesBack() {
        DiagramView v = view();
        v.setZoom(20.0);
        v.panBy(-300, -300);
        v.fit();
        check("fit returns to the whole document", Math.abs(v.zoom() - 1.0) < 0.01);
        check("and to the origin", Math.abs(v.aoi().getX()) < 0.01);
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
ant compile
```
Expected: FAIL, `cannot find symbol: class DiagramView`.

- [ ] **Step 3: Write `DiagramView`**

Create `src/my/stepss/diagram/DiagramView.java`:

```java
package my.stepss.diagram;

import java.awt.geom.Rectangle2D;

/**
 * Where the window is looking, in the document's own coordinates.
 *
 * <p>Zoom and pan expressed as a rectangle rather than as a transform, because
 * the rectangle is what {@link SvgImage#render} wants: the renderer draws one
 * region at viewport resolution, so navigation is arithmetic on that region and
 * never a scaled bitmap.
 *
 * <p>No Swing, so {@code DiagramCheck} pins the arithmetic without a display.
 * Every navigation fault is an arithmetic fault, and this is the part of it
 * that can be held to account.
 *
 * <p>Zoom is expressed relative to Fit, so 1.0 is the whole drawing and 4.0 is
 * a quarter of it in each direction, whatever the document's own units are.
 */
public final class DiagramView {

    /** Below this the drawing is a speck and the only way back is Fit. */
    private static final double MIN_ZOOM = 0.05;

    /** Above this the numbers stop being readable for a different reason. */
    private static final double MAX_ZOOM = 50.0;

    private final Rectangle2D document;

    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private double zoom = 1.0;
    private double centreX;
    private double centreY;

    public DiagramView(Rectangle2D documentBounds) {
        this.document = (Rectangle2D) documentBounds.clone();
        this.centreX = document.getCenterX();
        this.centreY = document.getCenterY();
    }

    /** Tells the view how many pixels it is being drawn into. */
    public void setViewport(int width, int height) {
        this.viewportWidth = Math.max(1, width);
        this.viewportHeight = Math.max(1, height);
    }

    /** The whole drawing, centred. */
    public void fit() {
        zoom = 1.0;
        centreX = document.getCenterX();
        centreY = document.getCenterY();
    }

    /** Magnification relative to Fit: 1.0 is the whole drawing. */
    public double zoom() {
        return zoom;
    }

    /** Sets the magnification about the current centre, clamped. */
    public void setZoom(double value) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
        clampCentre();
    }

    /**
     * Multiplies the magnification, keeping the document point currently under
     * {@code (deviceX, deviceY)} under that same pixel afterwards.
     *
     * <p>The anchoring is the whole value of this method. Zooming about the
     * centre throws away whatever the user was pointing at, which on a diagram
     * the size of a transmission network means every zoom is followed by
     * hunting for the thing you were reading.
     */
    public void zoomAt(double factor, int deviceX, int deviceY) {
        Rectangle2D before = aoi();
        double anchorX = before.getX() + before.getWidth() * deviceX / viewportWidth;
        double anchorY = before.getY() + before.getHeight() * deviceY / viewportHeight;

        setZoom(zoom * factor);

        // Put the anchor back under the same pixel by moving the centre, which
        // is the only free variable once the zoom is fixed.
        Rectangle2D after = aoi();
        double landedX = after.getX() + after.getWidth() * deviceX / viewportWidth;
        double landedY = after.getY() + after.getHeight() * deviceY / viewportHeight;
        centreX += anchorX - landedX;
        centreY += anchorY - landedY;
        clampCentre();
    }

    /** Moves the view by a drag measured in pixels. */
    public void panBy(int deviceDx, int deviceDy) {
        Rectangle2D current = aoi();
        centreX -= deviceDx * current.getWidth() / viewportWidth;
        centreY -= deviceDy * current.getHeight() / viewportHeight;
        clampCentre();
    }

    /**
     * The region to render, in document coordinates, at the viewport's aspect
     * ratio.
     *
     * <p>The aspect match is not cosmetic. Batik takes a single uniform scale
     * from the two axes and centres the result when they disagree, so an area
     * of interest with a different shape from the output would be silently
     * repositioned by the renderer and this class's arithmetic would stop
     * describing what is on screen.
     */
    public Rectangle2D aoi() {
        // At Fit the region is the document grown on one axis until it matches
        // the viewport's shape, so the whole drawing is inside it either way.
        double fitWidth = document.getWidth();
        double fitHeight = document.getHeight();
        double viewAspect = (double) viewportWidth / viewportHeight;
        if (fitWidth / fitHeight < viewAspect) {
            fitWidth = fitHeight * viewAspect;
        } else {
            fitHeight = fitWidth / viewAspect;
        }
        double width = fitWidth / zoom;
        double height = fitHeight / zoom;
        return new Rectangle2D.Double(centreX - width / 2, centreY - height / 2,
                width, height);
    }

    /**
     * Keeps at least part of the drawing on screen.
     *
     * <p>Without it a drag can put the document entirely outside the viewport,
     * and the only way back is Fit, which a user who has just lost their
     * diagram has no reason to expect.
     */
    private void clampCentre() {
        Rectangle2D current = aoi();
        double halfW = current.getWidth() / 2;
        double halfH = current.getHeight() / 2;
        centreX = Math.max(document.getMinX() - halfW / 2,
                Math.min(document.getMaxX() + halfW / 2, centreX));
        centreY = Math.max(document.getMinY() - halfH / 2,
                Math.min(document.getMaxY() + halfH / 2, centreY));
    }
}
```

- [ ] **Step 4: Run the checks**

```bash
ant compile
```
```bash
tools/diagram-harness.sh
```
Expected: `ALL DIAGRAM CHECKS PASSED`.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/diagram/DiagramView.java src/my/stepss/diagram/DiagramCheck.java
```
```bash
git commit -m "The diagram's zoom and pan arithmetic, headless

Expressed as a rectangle in the document's own coordinates rather than as
a transform, because that rectangle is what SvgImage.render takes.

Wheel zoom is anchored at the pointer. On a diagram the size of a
transmission network, zooming about the centre throws away whatever the
user was pointing at, so every zoom would be followed by hunting for the
thing they were reading."
```

---

## Task 10: `DiagramPanel`, the Swing component

**Files:**
- Create: `src/my/stepss/diagram/DiagramPanel.java`

**Interfaces:**
- Consumes: `SvgImage` (Task 8), `DiagramView` (Task 9).
- Produces: `new DiagramPanel(SvgImage image)`; `void fit()`; `void zoomBy(double factor)`; `SvgImage image()`.

- [ ] **Step 1: Write the component**

There is no headless check for this task: it is mouse handling and painting, and the arithmetic underneath it is already pinned by Task 9. Its acceptance is the manual run in Task 12.

Create `src/my/stepss/diagram/DiagramPanel.java`:

```java
package my.stepss.diagram;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The diagram, drawn, with the mouse and keys wired to {@link DiagramView}.
 *
 * <p>The one piece of real machinery here is the split between the cheap answer
 * and the correct one. Re-rendering per mouse-move event would lurch on a
 * complicated network, so a drag or a wheel burst paints the last image
 * transformed, which is immediate, and a coalescing timer issues one fresh
 * render when the gesture settles. The picture tracks the mouse and sharpens a
 * moment later, which is what decides whether this is pleasant to use on
 * anything larger than a teaching case.
 */
public final class DiagramPanel extends JComponent {

    /** Long enough to coalesce a gesture, short enough not to feel like lag. */
    private static final int SETTLE_MS = 120;

    private final SvgImage image;
    private final DiagramView view;
    private final Timer settle;

    /** The last good render, and the view rectangle it was drawn for. */
    private BufferedImage rendered;
    private java.awt.geom.Rectangle2D renderedFor;

    private Point dragFrom;

    public DiagramPanel(SvgImage image) {
        this.image = image;
        this.view = new DiagramView(image.documentBounds());
        this.settle = new Timer(SETTLE_MS, event -> {
            settle.stop();
            rerender();
        });
        settle.setRepeats(false);

        setOpaque(true);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        wireMouse();
        wireKeys();
        // Without this, growing the window leaves the previous image scaled up
        // to the new size and blurry: nothing else asks for a fresh render,
        // because rerender() is reached only from a gesture or the first paint.
        // schedule() coalesces through the settle timer, so a drag-resize
        // produces one render rather than one per pixel.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                schedule();
            }
        });
    }

    /** The document being shown, for the window's save buttons. */
    public SvgImage image() {
        return image;
    }

    /** Shows the whole drawing. */
    public void fit() {
        view.fit();
        schedule();
    }

    /** Multiplies the magnification about the viewport centre. */
    public void zoomBy(double factor) {
        view.zoomAt(factor, getWidth() / 2, getHeight() / 2);
        schedule();
    }

    private void wireMouse() {
        java.awt.event.MouseAdapter mouse = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                requestFocusInWindow();
                dragFrom = event.getPoint();
                if (event.getClickCount() == 2) {
                    fit();
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                dragFrom = null;
                schedule();
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent event) {
                if (dragFrom == null) {
                    return;
                }
                view.panBy(event.getX() - dragFrom.x, event.getY() - dragFrom.y);
                dragFrom = event.getPoint();
                schedule();
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent event) {
                double factor = Math.pow(1.15, -event.getPreciseWheelRotation());
                view.zoomAt(factor, event.getX(), event.getY());
                schedule();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void wireKeys() {
        int step = 40;
        bind("LEFT", () -> view.panBy(step, 0));
        bind("RIGHT", () -> view.panBy(-step, 0));
        bind("UP", () -> view.panBy(0, step));
        bind("DOWN", () -> view.panBy(0, -step));
        bind("PLUS", () -> view.zoomAt(1.25, getWidth() / 2, getHeight() / 2));
        bind("EQUALS", () -> view.zoomAt(1.25, getWidth() / 2, getHeight() / 2));
        bind("MINUS", () -> view.zoomAt(0.8, getWidth() / 2, getHeight() / 2));
        bind("0", view::fit);
    }

    private void bind(String key, Runnable action) {
        Object name = "diagram." + key;
        getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
                schedule();
            }
        });
    }

    /** Repaints now from what we have, and asks for a fresh render shortly. */
    private void schedule() {
        repaint();
        settle.restart();
    }

    private void rerender() {
        view.setViewport(getWidth(), getHeight());
        java.awt.geom.Rectangle2D wanted = view.aoi();
        try {
            rendered = image.render(wanted, getWidth(), getHeight());
            renderedFor = wanted;
        } catch (IOException ex) {
            // Keep the last good picture rather than blanking the window. The
            // document parsed once already, so a failure here is transient
            // (memory, most likely) and the previous image is still true of
            // where the view was.
            java.util.logging.Logger.getLogger(DiagramPanel.class.getName())
                    .log(java.util.logging.Level.WARNING, "Diagram render failed", ex);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = UIManager.getColor("Panel.background");
        g.setColor(background == null ? Color.WHITE : background);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (rendered == null || renderedFor == null) {
            view.setViewport(getWidth(), getHeight());
            rerender();
            if (rendered == null) {
                return;
            }
        }

        // Where the rendered region now sits, given where the view has moved
        // to since. Equal rectangles give an identity transform, which is the
        // settled case; during a gesture this is the cheap approximation that
        // keeps the picture under the mouse.
        view.setViewport(getWidth(), getHeight());
        java.awt.geom.Rectangle2D now = view.aoi();
        double scale = renderedFor.getWidth() / now.getWidth();
        double x = (renderedFor.getX() - now.getX()) / now.getWidth() * getWidth();
        double y = (renderedFor.getY() - now.getY()) / now.getHeight() * getHeight();

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.translate(x, y);
            g2.scale(scale, scale);
            g2.drawImage(rendered, 0, 0, null);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        return new java.awt.Dimension(900, 560);
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
ant compile
```
Expected: BUILD SUCCESSFUL, no warnings about the new file.

- [ ] **Step 3: Verify the existing checks still pass**

```bash
tools/diagram-harness.sh
```
Expected: `ALL DIAGRAM CHECKS PASSED`. The panel is not exercised by them; this confirms nothing it added broke the arithmetic underneath.

- [ ] **Step 4: Commit**

```bash
git add src/my/stepss/diagram/DiagramPanel.java
```
```bash
git commit -m "The diagram panel: wheel, drag, keys, and a settle timer

Rendering per mouse-move event would lurch on a complicated network, so a
gesture paints the last image transformed, which is immediate, and one
fresh render follows when the gesture settles. That split is what decides
whether this is pleasant on anything larger than a teaching case."
```

---

## Task 11: `WindowCascade`, extracted from the SSA window

**Files:**
- Create: `src/my/stepss/WindowCascade.java`
- Modify: `src/my/stepss/ssa/SsaResultsWindow.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `WindowCascade.track(java.awt.Window window, java.awt.Component parent)`, which positions the window relative to `parent`, steps it clear of the ones already up, counts it in, and registers the listener that counts it back out. Must be called after the window is sized and before it is shown. Task 12 consumes it.

- [ ] **Step 1: Write the class**

Create `src/my/stepss/WindowCascade.java`, moving the logic verbatim from `SsaResultsWindow`:

```java
package my.stepss;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.Window;

/**
 * Places a non-modal results window clear of the ones already open.
 *
 * <p>Without it every window is centred on the main frame, so the second lands
 * exactly over the first and pressing Run again looks like it did nothing. That
 * matters most where a second window is opened precisely in order to compare it
 * with the first, which is true of both callers.
 *
 * <p>Extracted from {@code SsaResultsWindow}, which had it privately, when the
 * diagram window needed the same behaviour. Two copies of a static counter is
 * two counters, and each would step around only its own kind of window.
 *
 * <p>Touched only from the event dispatch thread, which is where every caller
 * opens a window from.
 */
public final class WindowCascade {

    /** Enough of an offset to see the window underneath, and its title. */
    private static final int CASCADE_STEP = 30;

    /** Where the cascade returns to the top left rather than marching on. */
    private static final int CASCADE_WRAP = 8;

    /**
     * How many tracked windows are on screen. Counted down again as they close,
     * so a long session does not walk off the screen edge.
     */
    private static int onScreen;

    private WindowCascade() {
    }

    /**
     * Positions {@code window}, counts it in, and arranges for it to be counted
     * back out when it closes.
     *
     * <p>Call after the window is sized and before it is shown: the offset is
     * clamped against the window's own width and height, which are zero until
     * it has been packed.
     *
     * @param window the window to place
     * @param parent what to centre it on before offsetting, may be null
     */
    public static void track(Window window, Component parent) {
        window.setLocationRelativeTo(parent);
        cascade(window);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                onScreen = Math.max(0, onScreen - 1);
            }
        });
        onScreen++;
    }

    /**
     * Steps this window down and to the right of the ones already up.
     *
     * <p>Clamped to the screen the window is on, because a window whose title
     * bar is past the bottom edge cannot be moved back.
     */
    private static void cascade(Window window) {
        int rank = onScreen % CASCADE_WRAP;
        if (rank == 0) {
            return;
        }
        Rectangle screen = screenBounds(window);
        int shift = rank * CASCADE_STEP;
        int x = Math.min(window.getX() + shift,
                screen.x + screen.width - window.getWidth());
        int y = Math.min(window.getY() + shift,
                screen.y + screen.height - window.getHeight());
        window.setLocation(Math.max(screen.x, x), Math.max(screen.y, y));
    }

    /** The screen this window is on, or the default one before it has a peer. */
    private static Rectangle screenBounds(Window window) {
        java.awt.GraphicsConfiguration config = window.getGraphicsConfiguration();
        if (config == null) {
            config = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        }
        return config.getBounds();
    }
}
```

- [ ] **Step 2: Point the SSA window at it**

In `src/my/stepss/ssa/SsaResultsWindow.java`:

Replace the body of `open`:

```java
    public static void open(Component parent, SsaResults results) {
        SsaResultsWindow window = new SsaResultsWindow(results);
        my.stepss.WindowCascade.track(window, parent);
        window.setVisible(true);
    }
```

keeping its doc comment, which still describes why every call makes a new window.

Delete `cascade()`, `screenBounds()`, the `onScreen` field, `CASCADE_STEP` and `CASCADE_WRAP`, and delete the `WindowAdapter` registered in the constructor that decremented `onScreen`, together with its comment. Keep `setDefaultCloseOperation(DISPOSE_ON_CLOSE)`: `windowClosed` is what `WindowCascade` listens for, and it fires on dispose.

- [ ] **Step 3: Verify it compiles and the SSA checks still pass**

```bash
ant compile
```
```bash
tools/ssa-harness.sh
```
Expected: the SSA harness's usual all-passed line. It does not construct a window, so this confirms the extraction did not break the package around it.

- [ ] **Step 4: Verify the cascade by eye**

```bash
ant jar
```
```bash
java -jar dist/stepss.jar
```
Open the Kundur example, run the small-signal analysis twice from the Analysis tab. Expected: two results windows, the second offset down and right of the first, both usable. Close them and the application.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/WindowCascade.java src/my/stepss/ssa/SsaResultsWindow.java
```
```bash
git commit -m "Lift the results-window cascade out of SsaResultsWindow

The diagram window needs the same behaviour, and two copies of a static
counter is two counters: each would step around only its own kind of
window, so a diagram window would land exactly on top of an SSA one."
```

---

## Task 12: `DiagramWindow`, and Run Power Flow opening it

**Files:**
- Create: `src/my/stepss/diagram/DiagramWindow.java`
- Modify: `src/my/stepss/StepssUI.java` (`runPFActionPerformed` and its completion thread)

**Interfaces:**
- Consumes: `SvgImage` (Task 8), `DiagramPanel` (Task 10), `WindowCascade` (Task 11), `HeliosOutcome` (Task 4), `StepssUI.DIAGRAM_OUTPUT` (Task 7), `fileDiagram` (Task 5).
- Produces: `DiagramWindow.open(java.awt.Component parent, java.io.File svg, String caseName, int runNumber, HeliosOutcome outcome)`.

- [ ] **Step 1: Write the window**

Create `src/my/stepss/diagram/DiagramWindow.java`:

```java
package my.stepss.diagram;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import my.stepss.HeliosOutcome;
import my.stepss.WindowCascade;

/**
 * One power flow run's one-line diagram, displayed.
 *
 * <p>Non-modal and independent, so several can be open at once: pressing Run
 * Power Flow twice is nearly always done to compare the two, and each window
 * holds its own parsed copy of the drawing, so the next run overwriting
 * {@code in_diagram.svg} leaves the earlier window intact. That is the same
 * arrangement {@code SsaResultsWindow} makes, and for the same reason.
 *
 * <p>The banner is rendered from {@link HeliosOutcome}, which is also what the
 * modal dialog and the status bar phrasing come from. A run that did not
 * converge still gets a window, showing the template as it was, because a
 * failure the user cannot see the shape of is harder to diagnose than one they
 * can.
 */
public final class DiagramWindow extends JFrame {

    private final DiagramPanel panel;

    /**
     * Opens one run's diagram in a window of its own.
     *
     * @param parent    what to place it relative to
     * @param svg       the file to show: the annotated result, or the template
     *                  when the run produced nothing
     * @param caseName  the template's file name, for the title
     * @param runNumber which run of this session this is
     * @param outcome   what to say about the run, or null to say nothing
     */
    public static void open(Component parent, File svg, String caseName,
            int runNumber, HeliosOutcome outcome) throws IOException {
        DiagramWindow window = new DiagramWindow(svg, caseName, runNumber, outcome);
        window.pack();
        WindowCascade.track(window, parent);
        window.setVisible(true);
    }

    private DiagramWindow(File svg, String caseName, int runNumber,
            HeliosOutcome outcome) throws IOException {
        super("One-line diagram - " + caseName + " - run " + runNumber + ", "
                + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        panel = new DiagramPanel(SvgImage.load(svg));

        if (outcome != null && outcome.severity() != HeliosOutcome.Severity.OK) {
            add(banner(outcome), BorderLayout.NORTH);
        }
        add(panel, BorderLayout.CENTER);
        add(toolbar(), BorderLayout.SOUTH);
    }

    /**
     * The status strip above the drawing.
     *
     * <p>Coloured rather than merely worded, because the thing it guards
     * against is a user reading numbers off a diagram that is not a solution.
     */
    private static JPanel banner(HeliosOutcome outcome) {
        boolean error = outcome.severity() == HeliosOutcome.Severity.ERROR;
        Color ink = error ? new Color(0xB3261E) : new Color(0x8A5300);

        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.PAGE_AXIS));
        strip.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel headline = new JLabel(outcome.headline());
        headline.setForeground(ink);
        headline.setFont(headline.getFont().deriveFont(java.awt.Font.BOLD));
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        strip.add(headline);

        if (!outcome.detail().isEmpty()) {
            JLabel detail = new JLabel("<html><body style='width: 640px'>"
                    + outcome.detail() + "</body></html>");
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            strip.add(Box.createVerticalStrut(4));
            strip.add(detail);
        }
        return strip;
    }

    private JPanel toolbar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.LINE_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        bar.add(button("Fit", panel::fit));
        bar.add(Box.createRigidArea(new java.awt.Dimension(6, 0)));
        bar.add(button("Zoom in", () -> panel.zoomBy(1.25)));
        bar.add(Box.createRigidArea(new java.awt.Dimension(6, 0)));
        bar.add(button("Zoom out", () -> panel.zoomBy(0.8)));
        bar.add(Box.createHorizontalGlue());
        bar.add(button("Save as PNG...", this::savePng));
        bar.add(Box.createRigidArea(new java.awt.Dimension(6, 0)));
        bar.add(button("Save as SVG...", this::saveSvg));
        return bar;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(event -> action.run());
        return b;
    }

    /**
     * Saves the whole drawing, not what is on screen.
     *
     * <p>A saved figure goes into a report, so zoom is a reading aid here and
     * not a crop tool: a user who zoomed in to check one number and then saved
     * would otherwise get that number and nothing else.
     */
    private void savePng() {
        File target = chooseTarget("Save diagram as PNG", "diagram.png");
        if (target == null) {
            return;
        }
        try {
            javax.imageio.ImageIO.write(panel.image().renderWhole(2400), "png", target);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save " + target.getName() + "\n\n" + ex.getMessage(),
                    "Diagram not saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveSvg() {
        File target = chooseTarget("Save diagram as SVG", "diagram.svg");
        if (target == null) {
            return;
        }
        try {
            panel.image().copyTo(target);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save " + target.getName() + "\n\n" + ex.getMessage(),
                    "Diagram not saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File chooseTarget(String title, String suggested) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new File(suggested));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File target = chooser.getSelectedFile();
        if (target.exists() && JOptionPane.showConfirmDialog(this,
                target.getName() + " already exists. Replace it?",
                "Replace file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }
        return target;
    }

    static {
        // Nothing here depends on the look and feel being installed, but the
        // window is opened from a background thread's invokeLater and a missing
        // UIManager default would surface there rather than at startup.
        UIManager.getDefaults();
    }
}
```

- [ ] **Step 2: Count the runs and open the window**

In `src/my/stepss/StepssUI.java`, add the counter beside the other private fields:

```java
    /** How many power flows this session has run, for the diagram window titles. */
    private int powerFlowRun;
```

In `runPFActionPerformed`, immediately after the `deletePFCResultFiles();` call:

```java
        powerFlowRun++;
```

Capture what the completion thread needs before it starts, beside the existing `final DefaultExecuteResultHandler resultHandler = simulExecutorResultHandler;`:

```java
        // Read on the EDT and captured, so the completion thread reports on the
        // run this invocation started even if the field is edited meanwhile.
        final String diagramTemplate = fileDiagram.getText();
        final int runNumber = powerFlowRun;
```

Then in the completion thread, replace the final line `reportHeliosExitStatus(resultHandler, heliosStderr, heliosStdout);` with:

```java
                reportHeliosExitStatus(resultHandler, heliosStderr, heliosStdout);
                showDiagram(diagramTemplate, runNumber, resultHandler, heliosStderr);
```

and add the method beside `reportHeliosExitStatus`:

```java
    /**
     * Opens the run's one-line diagram, if the case has one.
     *
     * <p>Every run gets its own window, converged or not. A run that failed
     * still shows the template, because a diagram the user can see the shape of
     * is easier to reason about than an error message on its own, and the
     * banner says plainly that the numbers are not there.
     *
     * <p>Called off the EDT, from the completion thread, so the window is
     * opened through invokeLater, as every dialog on this path is.
     *
     * @param templatePath the diagram slot's contents, captured when the run
     * started
     * @param runNumber which run of this session this is, for the title
     */
    private void showDiagram(String templatePath, int runNumber,
            DefaultExecuteResultHandler resultHandler,
            ByteArrayOutputStream heliosStderr) {
        if (templatePath.isEmpty()) {
            return;
        }
        final File template = new File(templatePath);
        final File annotated = new File(myTempDir, DIAGRAM_OUTPUT);
        final boolean drawn = annotated.isFile();

        HeliosOutcome exitOutcome = resultHandler.hasResult()
                ? HeliosOutcome.of(resultHandler.getExitValue(), heliosStderr.toString())
                : HeliosOutcome.of(1, "");
        // A converged run that still drew nothing is not an exit status: Helios'
        // 1 command catches its own exceptions and leaves the run successful,
        // so the missing file is the only signal there is.
        final HeliosOutcome outcome
                = (!drawn && exitOutcome.severity() == HeliosOutcome.Severity.OK)
                ? HeliosOutcome.renderFailed(template.getName())
                : exitOutcome;
        final File toShow = drawn ? annotated : template;

        if (!toShow.isFile()) {
            banner.warn("The one-line diagram " + template.getName()
                    + " could not be found, so no diagram was shown.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                my.stepss.diagram.DiagramWindow.open(this, toShow,
                        template.getName(), runNumber, outcome);
            } catch (IOException ex) {
                Logger.getLogger(StepssUI.class.getName())
                        .log(Level.WARNING, "The diagram could not be opened", ex);
                banner.warn("The one-line diagram could not be opened.\n\n"
                        + ex.getMessage());
            }
        });
    }
```

Add the import `import my.stepss.HeliosOutcome;` if `StepssUI` does not already have it from Task 4.

- [ ] **Step 3: Build and run the acceptance sequence**

```bash
ant compile
```
```bash
tools/diagram-harness.sh
```
Expected: `ALL DIAGRAM CHECKS PASSED`.

```bash
ant jar
```
```bash
java -jar dist/stepss.jar
```

Work through these in order. Every one is a requirement from the spec.

1. File > Open Examples, 6-bus microgrid, Fresh copy. Data row 1 filled, the diagram slot filled, disturbance and observables empty.
2. Run Power Flow. A window opens titled `One-line diagram - 6bus.svg - run 1, HH:mm:ss`, no banner, solved values on the drawing rather than placeholder codes.
3. Run Power Flow again. A second window, offset down and right, both readable, both showing their own run.
4. Wheel-zoom to about 400% over bus D. The point under the cursor stays under the cursor, and the labels stay sharp rather than going soft.
5. Drag to pan across the diagram. It tracks the mouse and sharpens when the drag stops. Double-click returns to Fit.
6. Save as PNG while zoomed in. The saved file holds the whole diagram, not the visible corner.
7. Edit `6bus.dat` so it cannot converge (set `$NBITMA 1`). Run Power Flow. The window opens with the amber banner reading "The power flow did NOT converge", and the modal dialog says the same thing.
8. Clear the diagram slot. Run Power Flow. No window opens, and `PFCcmd.txt` holds no `1` line.
9. Point the slot at a file that is not an SVG (`6bus.dat` will do). Run Power Flow. The banner reports that the diagram could not be drawn and names the file.
10. Dynamic Simulation tab, Run. Refused, naming the missing disturbance file.
11. Save configuration, Clear files, Load configuration. The diagram slot comes back.

Restore `6bus.dat` afterwards, or re-open the example with Fresh copy.

- [ ] **Step 4: Commit**

```bash
git add src/my/stepss/diagram/DiagramWindow.java src/my/stepss/StepssUI.java
```
```bash
git commit -m "Show each power flow's one-line diagram in a window of its own

Every run gets a window, converged or not, because pressing Run twice is
nearly always done to compare the two and each window holds its own
parsed copy of the drawing. The next run overwrites in_diagram.svg and
the earlier window is unaffected.

A run that failed still shows the template under a banner rendered from
the same HeliosOutcome the modal dialog and the status bar phrasing come
from. A converged run that drew nothing gets its own banner: Helios' 1
command catches its own exceptions and leaves the run successful, so the
missing file is the only signal there is."
```

---

## Task 13: Documentation

**Files:**
- Modify: `README.md` in this repository
- Create: a one-line diagram section in `stepss-docs` under `user-guide/power-flow`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing code depends on.

- [ ] **Step 1: Add the slot to this repository's README**

Read `README.md` and find where it describes the System Data tab. Add the diagram slot beside the data and disturbance ones, in the register the surrounding text uses: an optional SVG template carrying placeholder codes, filled in on every Run Power Flow and shown in a window of its own, with the placeholder table in the user guide.

- [ ] **Step 2: Write the user guide section**

```bash
cd ../stepss-docs
```

Find the power flow page under `user-guide/`, and add a section covering: what a template is, how to put placeholders in one with any SVG editor, that the slot is optional and lives on the System Data tab, that every Run Power Flow produces a fresh diagram in a new window, and the placeholder table:

| Code | Argument | Value |
|---|---|---|
| `%A` | bus | voltage magnitude (pu) |
| `%B` | bus | voltage (kV) |
| `%C` | bus | angle (degrees) |
| `%D` | branch, bus | P flow at that bus end (MW) |
| `%E` | branch, bus | Q flow at that bus end (Mvar) |
| `%F` | branch | loading fraction |
| `%G` | generator | P (MW) |
| `%H` | bus | shunt Q (Mvar) |
| `%J` | transformer | tap position |
| `%K` | generator | Q (Mvar) |
| `%L` | SVC | Q (Mvar) |
| `%R` | bus | load P (MW) |
| `%S` | bus | load Q (Mvar) |
| `%T` | branch | breaker status, blank or `X` |
| `%U` | generator | breaker status, blank or `X` |

State that `%I` and `%M` through `%Q` are recognised and return the word `unknown`, because they need zone and sensitivity data the model does not carry. Document them as unsupported rather than omitting them: someone will otherwise write one into a drawing, get `unknown` on their diagram, and have nothing explaining it.

Point at `SPS-L/stepss-6-bus-MG` as the worked example, since `6bus.svg` is a template with every one of these in it.

- [ ] **Step 3: Verify the docs site builds**

```bash
npm install
```
```bash
npm run build
```
Expected: a clean build. If the site has a link checker, a failure naming the new page is a broken relative link in what was just added.

- [ ] **Step 4: Commit the docs**

```bash
git add user-guide
```
```bash
git commit -m "Document the annotated one-line diagram

The placeholder table, how to author a template, and the optional slot on
the System Data tab. %I and %M to %Q are listed as unsupported rather
than omitted: they are recognised and render the word unknown, so a user
who writes one gets it on their diagram with nothing to explain it."
```

- [ ] **Step 5: Commit the README**

```bash
cd ../stepss-java-ui
```
```bash
git add README.md
```
```bash
git commit -m "README: the one-line diagram slot on the System Data tab"
```

- [ ] **Step 6: Bump the docs pointer in the umbrella repository**

```bash
cd ..
```
```bash
git add stepss-docs
```
```bash
git commit -m "Bump docs: the annotated one-line diagram is documented"
```

Do not bump `stepss-java-ui` here until its work is merged and pushed on its own remote. A pointer to an unpushed commit breaks cloning for everyone else.

---

## Self-Review

**Spec coverage.** Every section maps to a task: Part 1 to Task 3 Step 8; Part 2 to Tasks 1, 2 and 3; Part 3 to Tasks 5 and 6; Part 4 to Task 7; Part 5 to Tasks 8, 9, 10, 11 and 12; Part 6 to Tasks 4 and 12; Testing to the checks inside each task plus Task 12 Step 3; Documentation follow-up to Task 13.

**Two places where the plan corrects the spec.**

- The spec says `heading()` "gains a string overload". It does not need one: `heading(JLabel)` already bolds a non-HTML label, and `layoutAnalysisTab` already calls `heading(new JLabel("Recording to file"))`. Task 5 Step 6 uses the existing helper.
- The spec's `ScenarioBinding` change is described without saying where the new parameter goes. Task 6 fixes it at "immediately after `observablesField`", because a constructor of eleven same-typed arguments is exactly where a silent swap happens, and `ScenarioBinding`'s own comment says the arity check exists for that reason.

**Two risks carried deliberately.**

- Task 8 Step 6 can fail on the Rhino service registration. The instruction is to stop and report rather than add Rhino, because adding a JavaScript engine to make a static drawing render is the wrong direction.
- Task 12's acceptance is manual. No harness can drive Helios, so the eleven-step sequence is the gate.

**Type consistency.** `SvgImage.render(Rectangle2D, int, int)`, `renderWhole(int)`, `documentBounds()`, `copyTo(File)` are used in Tasks 8, 10 and 12 with those exact signatures. `DiagramView.zoomAt(double, int, int)`, `panBy(int, int)`, `setZoom(double)`, `zoom()`, `aoi()`, `fit()`, `setViewport(int, int)` are defined in Task 9 and used in Task 10 unchanged. `HeliosOutcome.of(int, String)`, `renderFailed(String)`, `severity()`, `headline()`, `detail()` and `Severity.OK`/`WARNING`/`ERROR` are defined in Task 4 and used in Tasks 4 and 12 unchanged. `WindowCascade.track(Window, Component)` is defined in Task 11 and used in Tasks 11 and 12. `StepssUI.DIAGRAM_OUTPUT` and `diagramCommands(String, String)` are defined in Task 7 and used in Tasks 7 and 12. `Example.diagram()` is defined in Task 2 and used in Tasks 2, 3 and 5. `slotPath(File, String)` is defined in Task 2 and used in Tasks 2 and 5.
