# Observable selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the type-matching walk over `jPanel7` that Clear uses with an
object that owns the picker rows, and extend the picker from five observable
categories to the eight RAMSES accepts and the runtime dropdown from twelve
display types to fourteen.

**Architecture:** A new `my.stepss.obs` package. `ObservableCategory` is one
picker row: it carries the RAMSES file keyword, the DYNGRAPH spelling and the
human label, and it builds the four controls the row needs.
`ObservableWizard` holds the eight categories plus the rest of the Observables
tab's state, and offers `reset()`, `write(File)` and `isEmpty()`. `StepssUI`
builds `jPanel7` by looping over the categories instead of carrying 31
NetBeans-generated controls, and sixteen handlers plus `createCustomObsFile()`
collapse into delegations.

**Tech Stack:** Java 11, Swing, Ant. No new dependency: `my.stepss.obs` uses
the JDK and Swing only, so `build/classes` alone runs the harness.

**Spec:** `docs/superpowers/specs/2026-08-18-observables-selection-design.md`

## Global Constraints

- **Java source and target are 11** (`nbproject/project.properties:60-61`).
  Lambdas, streams and enums are available; anything newer is not.
- **No new dependency.** `my.stepss.obs` must import nothing beyond the JDK
  and Swing, or `tools/observables-harness.sh` stops working, because it puts
  only `build/classes` on the classpath.
- **The harness must never construct `StepssUI`.** Its constructor extracts
  the toolchain and calls `System.exit` when it cannot, so it cannot run
  outside a real installation.
- **No em-dashes** anywhere in code, comments, documentation or commit
  messages. This is a standing repository convention.
- **Build with `ant compile`.** It depends on `stage-payloads`, so a checkout
  needs either a warm `payload-cache/` or network plus authenticated `gh`
  before `build/classes` exists at all.
- **The eight file keywords are fixed by RAMSES** and must be spelled exactly
  as `add_observ` reads them (`stepss-ramses/src/io/observ.f90:171-333`):
  `BUS`, `SHUNT`, `IMPLOAD`, `BRANCH`, `SYNC`, `INJEC`, `TWOP`, `DCTL`.
- **Observable names are `character(len=20)`** and are read with
  `read(string,*) type, name`, so a name longer than 20 characters is
  truncated and one containing whitespace or a comma splits.

---

### Task 1: The eight categories, and a harness to run them against

**Files:**
- Create: `src/my/stepss/obs/ObservableCategory.java`
- Create: `src/my/stepss/obs/ObservablesHarness.java`
- Create: `tools/observables-harness.sh`

**Interfaces:**
- Consumes: nothing.
- Produces: `ObservableCategory.Kind`, an enum of eight constants with
  `String keyword()`, `String tag()`, `String label()`, `String tooltip()`.

- [ ] **Step 1: Write the failing test**

Create `src/my/stepss/obs/ObservablesHarness.java`:

```java
package my.stepss.obs;

import my.stepss.obs.ObservableCategory.Kind;

/**
 * Headless checks for the observable picker. This repository has no unit-test
 * framework and is not gaining one, so this main() is where the eight
 * categories, the file they produce and the reset are pinned.
 *
 * <p>Swing only, never a frame: the controls construct without a display, and
 * {@code StepssUI} cannot be built at all outside a real installation because
 * its constructor extracts the toolchain and exits the JVM when it cannot.
 */
public final class ObservablesHarness {

    private static int failures;

    private ObservablesHarness() {
    }

    public static void main(String[] args) {
        checkTheEightKeywords();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED"
                : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * The keywords are a contract with RAMSES, not a label set. A rename here
     * produces a file add_observ skips with a warning nobody reads, so the
     * spelling is pinned rather than derived.
     */
    private static void checkTheEightKeywords() {
        expect("eight categories", 8, Kind.values().length);
        String[] keywords = {"BUS", "SHUNT", "IMPLOAD", "BRANCH", "SYNC",
            "INJEC", "TWOP", "DCTL"};
        String[] tags = {"BUS", "SHUNT", "LOAD", "BRANCH", "SYNC",
            "INJ", "LINK", "DCTL"};
        for (int i = 0; i < keywords.length; i++) {
            expect("keyword " + i, keywords[i], Kind.values()[i].keyword());
            expect("tag " + i, tags[i], Kind.values()[i].tag());
        }
        for (Kind kind : Kind.values()) {
            if (kind.label().isEmpty()) {
                fail(kind + " has no label");
            } else {
                pass(kind + " has a label");
            }
        }
    }

    private static void expect(String what, Object want, Object got) {
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

Create `tools/observables-harness.sh`:

```bash
#!/usr/bin/env bash
# Runs the headless observable-picker checks against the built classes.
# This repository has no unit-test framework; this is the substitute.
#
# Like tools/scenario-harness.sh, dist/lib is NOT on the classpath: my.stepss.obs
# depends on nothing beyond the JDK and Swing, so build/classes alone is enough
# to load it. It builds controls but never a frame, so it runs headless.
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
exec java -Djava.awt.headless=true -cp build/classes my.stepss.obs.ObservablesHarness
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `chmod +x tools/observables-harness.sh` then `ant compile`

Expected: FAIL, javac reports `package my.stepss.obs does not exist` or
`cannot find symbol: class ObservableCategory`.

- [ ] **Step 3: Write the minimal implementation**

Create `src/my/stepss/obs/ObservableCategory.java`:

```java
package my.stepss.obs;

/**
 * One row of the observable picker: a category of equipment, the controls that
 * choose members of it, and the keyword the observables file names it by.
 *
 * <p>The categories are RAMSES', not this application's. {@code add_observ}
 * (stepss-ramses/src/io/observ.f90:171-333) accepts eight keywords, and every
 * one of them is wired end to end in the engine: allocated in
 * {@code observ_init}, named into the trajectory header by {@code observ_fin},
 * and sampled every step by its own section of {@code write_observ}. The
 * picker offered five of them until this class existed.
 */
public final class ObservableCategory {

    /**
     * The eight observable categories, in the order {@code observ_fin} writes
     * them into the trajectory and {@code dyngraph --list} emits them.
     *
     * <p>Three are spelled differently in the two places a user meets them,
     * which is why {@link #tooltip()} names both: the observables file says
     * {@code IMPLOAD}, {@code INJEC} and {@code TWOP} where DYNGRAPH's listing
     * says {@code LOAD}, {@code INJ} and {@code LINK}.
     *
     * <p>{@code IMPLOAD} and {@code LOAD} are the same block, not two kinds of
     * load: RAMSES' LOAD module holds only a conductance and a susceptance per
     * entry, so a load there is an impedance load by construction. Dynamic
     * loads are modelled as injectors and are observed under {@code INJEC}.
     */
    public enum Kind {
        BUS("BUS", "BUS", "Buses", ""),
        SHUNT("SHUNT", "SHUNT", "Shunts", ""),
        IMPLOAD("IMPLOAD", "LOAD", "Impedance loads",
                "Names beginning M_ are synthesised from the power mismatch"
                + " at that bus, not declared in the data."),
        BRANCH("BRANCH", "BRANCH", "Branches", ""),
        SYNC("SYNC", "SYNC", "Synchronous machines", ""),
        INJEC("INJEC", "INJ", "Injectors",
                "Dynamic loads are injectors, so they belong here rather than"
                + " under impedance loads."),
        TWOP("TWOP", "LINK", "Two-port injectors", ""),
        DCTL("DCTL", "DCTL", "Discrete controllers", "");

        private final String keyword;
        private final String tag;
        private final String label;
        private final String note;

        Kind(String keyword, String tag, String label, String note) {
            this.keyword = keyword;
            this.tag = tag;
            this.label = label;
            this.note = note;
        }

        /** The keyword the observables file uses, as add_observ reads it. */
        public String keyword() {
            return keyword;
        }

        /** What {@code dyngraph --list} calls this category. */
        public String tag() {
            return tag;
        }

        /** What the row says on screen. */
        public String label() {
            return label;
        }

        /** Both spellings, so a user who read one can find the other. */
        public String tooltip() {
            StringBuilder text = new StringBuilder("<html>Writes <b>")
                    .append(keyword)
                    .append(" &lt;name&gt;</b> lines into the observables file.");
            if (!keyword.equals(tag)) {
                text.append("<br>DYNGRAPH lists these under <b>")
                        .append(tag).append("</b>.");
            }
            if (!note.isEmpty()) {
                text.append("<br>").append(note);
            }
            return text.append("</html>").toString();
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `ant compile` then `tools/observables-harness.sh`

Expected: `ALL CHECKS PASSED`, exit status 0, with a PASS line for each of the
eight keywords, the eight tags and the eight labels.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/obs/ObservableCategory.java src/my/stepss/obs/ObservablesHarness.java tools/observables-harness.sh
git commit -m "The eight observable categories, named once

add_observ accepts eight keywords and the picker has always offered five.
Name the set, with the DYNGRAPH spelling beside the file keyword for the
three that differ, and add the headless harness that pins them."
```

---

### Task 2: The row's controls, and clearing them

**Files:**
- Modify: `src/my/stepss/obs/ObservableCategory.java`
- Modify: `src/my/stepss/obs/ObservablesHarness.java`

**Interfaces:**
- Consumes: `ObservableCategory.Kind` from Task 1.
- Produces: `ObservableCategory(Kind)`; `Kind kind()`; the control accessors
  `JLabel nameLabel()`, `JTextField field()`, `JButton addButton()`,
  `JComboBox<String> list()`, `JButton removeButton()`, `JCheckBox allBox()`;
  `void clear()`; `List<String> names()`; `boolean isAll()`.

- [ ] **Step 1: Write the failing test**

Add to `ObservablesHarness`, and add the call `checkClearEmptiesARow();` to
`main` immediately after `checkTheEightKeywords();`:

```java
    /**
     * Clear must empty the row and undo the disabling that ticking All does.
     * The handler this replaces walked jPanel7 matching on widget type, so it
     * cleared whatever happened to be a text field and re-enabled it without
     * anything recording why.
     */
    private static void checkClearEmptiesARow() {
        ObservableCategory row = new ObservableCategory(Kind.TWOP);
        row.field().setText("link1");
        row.list().addItem("link2");
        row.list().addItem("link3");
        row.allBox().setSelected(true);
        row.field().setEnabled(false);
        row.list().setEnabled(false);

        row.clear();

        expect("field emptied", "", row.field().getText());
        expect("field re-enabled", true, row.field().isEnabled());
        expect("list emptied", 0, row.list().getItemCount());
        expect("list re-enabled", true, row.list().isEnabled());
        expect("all unticked", false, row.allBox().isSelected());
        expect("names empty", 0, row.names().size());
        expect("isAll false", false, row.isAll());
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `ant compile`

Expected: FAIL, javac reports `constructor ObservableCategory in class
ObservableCategory cannot be applied to given types` or `cannot find symbol:
method field()`.

- [ ] **Step 3: Write the minimal implementation**

Add the imports to `ObservableCategory.java`:

```java
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
```

Add below the `Kind` enum, inside the class:

```java
    private final Kind kind;
    private final JLabel nameLabel;
    private final JTextField field = new JTextField();
    private final JButton addButton = new JButton("Add");
    private final JComboBox<String> list = new JComboBox<>();
    private final JButton removeButton = new JButton("Remove");
    private final JCheckBox allBox = new JCheckBox("All");

    /**
     * Builds the row's controls rather than adopting form-generated ones.
     *
     * <p>Eight categories is 49 controls where jPanel7 carried 31, and adding
     * eighteen through the NetBeans designer would have kept the last
     * GridBagLayout island in a window that is otherwise BorderLayout. Owning
     * them makes a ninth category a one-line change and makes it impossible to
     * add an unrelated control to a panel Clear then resets. The one-line
     * diagram row is declared the same way and for the same reason.
     *
     * @param kind which category this row chooses members of
     */
    public ObservableCategory(Kind kind) {
        this.kind = kind;
        this.nameLabel = new JLabel(kind.label());
        String tooltip = kind.tooltip();
        nameLabel.setToolTipText(tooltip);
        field.setToolTipText(tooltip);
        allBox.setToolTipText("Observe every " + kind.label().toLowerCase(
                java.util.Locale.ROOT) + " in the network.");
        removeButton.setEnabled(false);
    }

    /** Which category this row chooses members of. */
    public Kind kind() {
        return kind;
    }

    public JLabel nameLabel() {
        return nameLabel;
    }

    public JTextField field() {
        return field;
    }

    public JButton addButton() {
        return addButton;
    }

    public JComboBox<String> list() {
        return list;
    }

    public JButton removeButton() {
        return removeButton;
    }

    public JCheckBox allBox() {
        return allBox;
    }

    /**
     * Back to the state a fresh launch is in.
     *
     * <p>Re-enabling is not tidying: ticking All disables the field and the
     * list, so unticking it here has to put them back or the row is left
     * cleared and unusable.
     */
    public void clear() {
        field.setText("");
        field.setEnabled(true);
        list.removeAllItems();
        list.setEnabled(true);
        addButton.setEnabled(true);
        allBox.setSelected(false);
        syncRemoveEnabled();
    }

    /** The names chosen in this row, in the order they were added. */
    public List<String> names() {
        List<String> chosen = new ArrayList<>();
        for (int i = 0; i < list.getItemCount(); i++) {
            chosen.add(list.getItemAt(i));
        }
        return chosen;
    }

    /** True when the row asks for every member of its category. */
    public boolean isAll() {
        return allBox.isSelected();
    }

    /**
     * Remove is enabled only when there is something selected to remove.
     * Pressing it on an empty list used to be removeItemAt(-1), an uncaught
     * exception on the event thread, and Clear is what empties the lists.
     */
    private void syncRemoveEnabled() {
        removeButton.setEnabled(list.getItemCount() > 0 && !allBox.isSelected());
    }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `ant compile` then `tools/observables-harness.sh`

Expected: `ALL CHECKS PASSED`, with seven new PASS lines from
`checkClearEmptiesARow`.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/obs/ObservableCategory.java src/my/stepss/obs/ObservablesHarness.java
git commit -m "A picker row owns its controls, and knows how to clear them

Building the controls rather than adopting form-generated ones is what makes
eight categories cheap and makes the row impossible to reach by walking a
panel. Clear re-enables the field and list because ticking All disables them."
```

---

### Task 3: Adding, removing, and the All toggle

**Files:**
- Modify: `src/my/stepss/obs/ObservableCategory.java`
- Modify: `src/my/stepss/obs/ObservablesHarness.java`

**Interfaces:**
- Consumes: everything from Task 2.
- Produces: `String add()` returning null on success or a one-sentence problem;
  `void removeSelected()`; `void allToggled()`;
  `void install(java.util.function.Consumer<String> onProblem)`.

- [ ] **Step 1: Write the failing test**

Add to `ObservablesHarness`, and add `checkAddValidates();`,
`checkRemoveIsSafe();` and `checkAllTogglesTheRow();` to `main` after
`checkClearEmptiesARow();`:

```java
    /**
     * add_observ reads each line with "read(string,*) type, name" into a
     * character(len=20), so a longer name is silently truncated and one
     * carrying a space silently splits. Refusing them here is the only place
     * a user finds out.
     */
    private static void checkAddValidates() {
        ObservableCategory row = new ObservableCategory(Kind.BUS);

        row.field().setText("");
        expectRefused("blank name", row.add());

        row.field().setText("123456789012345678901");
        expectRefused("21 characters", row.add());

        row.field().setText("bus 1");
        expectRefused("name with a space", row.add());

        row.field().setText("bus,1");
        expectRefused("name with a comma", row.add());

        row.field().setText("12345678901234567890");
        expect("20 characters accepted", null, row.add());
        expect("added", 1, row.list().getItemCount());
        expect("field cleared after add", "", row.field().getText());
        expect("remove enabled after add", true, row.removeButton().isEnabled());

        row.field().setText("12345678901234567890");
        expectRefused("duplicate", row.add());
        expect("duplicate not added", 1, row.list().getItemCount());
        expect("field keeps the text", "12345678901234567890",
                row.field().getText());
    }

    /**
     * Remove used to be removeItemAt(getSelectedIndex()) with no guard, so on
     * an empty list it was removeItemAt(-1).
     */
    private static void checkRemoveIsSafe() {
        ObservableCategory row = new ObservableCategory(Kind.DCTL);
        expect("remove disabled while empty", false,
                row.removeButton().isEnabled());
        row.removeSelected();
        expect("removing from an empty list is a no-op", 0,
                row.list().getItemCount());

        row.field().setText("ctl1");
        row.add();
        row.list().setSelectedIndex(0);
        row.removeSelected();
        expect("removed", 0, row.list().getItemCount());
        expect("remove disabled again", false, row.removeButton().isEnabled());
    }

    /** Ticking All makes the field and list meaningless, so they go grey. */
    private static void checkAllTogglesTheRow() {
        ObservableCategory row = new ObservableCategory(Kind.SHUNT);
        row.field().setText("sh1");
        row.allBox().setSelected(true);
        row.allToggled();
        expect("field cleared by All", "", row.field().getText());
        expect("field disabled by All", false, row.field().isEnabled());
        expect("list disabled by All", false, row.list().isEnabled());
        expect("add disabled by All", false, row.addButton().isEnabled());

        row.allBox().setSelected(false);
        row.allToggled();
        expect("field re-enabled", true, row.field().isEnabled());
        expect("list re-enabled", true, row.list().isEnabled());
        expect("add re-enabled", true, row.addButton().isEnabled());
    }

    private static void expectRefused(String what, String problem) {
        if (problem == null) {
            fail(what + ": accepted, expected a refusal");
        } else {
            pass(what + ": " + problem);
        }
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `ant compile`

Expected: FAIL, javac reports `cannot find symbol: method add()`.

- [ ] **Step 3: Write the minimal implementation**

Add the import to `ObservableCategory.java`:

```java
import java.util.function.Consumer;
```

Add the constant beside the fields:

```java
    /**
     * RAMSES reads an observable name into a character(len=20). A longer one is
     * truncated without a word, which is worse than a refusal because the
     * truncated name usually matches nothing and the observable is dropped
     * with a warning in a log the user is not reading.
     */
    private static final int MAX_NAME = 20;
```

Add the methods:

```java
    /**
     * Adds what the field holds to this row's list.
     *
     * @return null when it was added, or one sentence saying why it was not
     */
    public String add() {
        String name = field.getText().trim();
        if (name.isEmpty()) {
            return "Type a name before pressing Add.";
        }
        if (name.length() > MAX_NAME) {
            return "RAMSES reads observable names " + MAX_NAME
                    + " characters wide, and \"" + name + "\" is "
                    + name.length() + ".";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                return "An observable name cannot contain a space or a comma,"
                        + " because RAMSES reads the line one token at a time:"
                        + " \"" + name + "\".";
            }
        }
        if (names().contains(name)) {
            return "\"" + name + "\" is already in the "
                    + kind.label().toLowerCase(java.util.Locale.ROOT) + " list.";
        }
        list.addItem(name);
        field.setText("");
        syncRemoveEnabled();
        return null;
    }

    /** Removes the selected name, or does nothing when none is selected. */
    public void removeSelected() {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            list.removeItemAt(index);
        }
        syncRemoveEnabled();
    }

    /** Greys the field and list out when All makes them meaningless. */
    public void allToggled() {
        boolean everything = allBox.isSelected();
        if (everything) {
            field.setText("");
        }
        field.setEnabled(!everything);
        list.setEnabled(!everything);
        addButton.setEnabled(!everything);
        syncRemoveEnabled();
    }

    /**
     * Wires the three buttons.
     *
     * <p>The problem sink is a parameter rather than a field so the harness can
     * collect what the user would have been told, and so this class carries no
     * reference to the window's banner.
     *
     * @param onProblem told why an Add was refused, once per refusal
     */
    public void install(Consumer<String> onProblem) {
        addButton.addActionListener(event -> {
            String problem = add();
            if (problem != null) {
                onProblem.accept(problem);
            }
        });
        removeButton.addActionListener(event -> removeSelected());
        allBox.addActionListener(event -> allToggled());
    }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `ant compile` then `tools/observables-harness.sh`

Expected: `ALL CHECKS PASSED`, with the refusal messages printed beside each
PASS so the wording is visible in the output.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/obs/ObservableCategory.java src/my/stepss/obs/ObservablesHarness.java
git commit -m "Add, remove and All, with the validation RAMSES implies

A name longer than 20 characters or carrying a space is truncated or split by
add_observ without a word, so refuse it here. Remove guards its index, which
it never did: on an empty list it was removeItemAt(-1). A duplicate reports
through the caller instead of writing \"Already in List!\" into the field,
where a second press added it as an observable."
```

---

### Task 4: Writing the observables file

**Files:**
- Modify: `src/my/stepss/obs/ObservableCategory.java`
- Create: `src/my/stepss/obs/ObservableWizard.java`
- Modify: `src/my/stepss/obs/ObservablesHarness.java`

**Interfaces:**
- Consumes: everything from Task 3.
- Produces: `void ObservableCategory.appendTo(BufferedWriter) throws IOException`;
  `ObservableWizard(JTextField, JComboBox<?>[], JTextField[], JCheckBox, JCheckBox, JCheckBox, JCheckBox, JCheckBox)`;
  `List<ObservableCategory> ObservableWizard.categories()`;
  `void ObservableWizard.write(File) throws IOException`.

- [ ] **Step 1: Write the failing test**

Add to `ObservablesHarness`, add `checkTheFileItWrites();` to `main` after
`checkAllTogglesTheRow();`, and add the imports
`java.io.File`, `java.io.IOException`, `java.nio.charset.StandardCharsets`,
`java.nio.file.Files`, `javax.swing.JCheckBox`, `javax.swing.JComboBox`,
`javax.swing.JTextField`, `javax.swing.DefaultComboBoxModel`:

```java
    /**
     * The file is the whole point of the picker, so it is compared whole
     * rather than probed. Branch lines carried a trailing space that no other
     * category had, copy-paste residue from when this was five blocks of the
     * same code; the reader does trim(adjustl(string)) before a list-directed
     * read, so dropping it changes nothing RAMSES sees.
     */
    private static void checkTheFileItWrites() throws IOException {
        ObservableWizard wizard = newWizard();
        for (ObservableCategory row : wizard.categories()) {
            switch (row.kind()) {
                case BUS:
                    row.field().setText("b1");
                    row.add();
                    row.field().setText("b2");
                    row.add();
                    break;
                case IMPLOAD:
                    row.allBox().setSelected(true);
                    break;
                case BRANCH:
                    row.field().setText("br1");
                    row.add();
                    break;
                case TWOP:
                    row.field().setText("lk1");
                    row.add();
                    break;
                case DCTL:
                    row.allBox().setSelected(true);
                    break;
                default:
                    break;
            }
        }

        File out = File.createTempFile("customObs", ".txt");
        out.deleteOnExit();
        wizard.write(out);

        String want = "BUS b1\nBUS b2\nIMPLOAD *\nBRANCH br1\nTWOP lk1\nDCTL *\n\n\n";
        String got = new String(Files.readAllBytes(out.toPath()),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        expect("the observables file", want, got);
    }

    /** A wizard over throwaway controls, with no frame anywhere near it. */
    private static ObservableWizard newWizard() {
        String[] types = {"Bus Voltage", "Machine Speed", "Wall Time"};
        JComboBox<?>[] runtimeTypes = {
            new JComboBox<>(new DefaultComboBoxModel<>(types)),
            new JComboBox<>(new DefaultComboBoxModel<>(types)),
            new JComboBox<>(new DefaultComboBoxModel<>(types))};
        JTextField[] runtimeNames = {
            new JTextField(), new JTextField(), new JTextField()};
        return new ObservableWizard(new JTextField(), runtimeTypes, runtimeNames,
                new JCheckBox(), new JCheckBox(), new JCheckBox(),
                new JCheckBox(), new JCheckBox());
    }
```

Change `main`'s signature to `public static void main(String[] args) throws IOException {`.

- [ ] **Step 2: Run it to make sure it fails**

Run: `ant compile`

Expected: FAIL, javac reports `cannot find symbol: class ObservableWizard`.

- [ ] **Step 3: Write the minimal implementation**

Add the import to `ObservableCategory.java`:

```java
import java.io.BufferedWriter;
import java.io.IOException;
```

Add the method to `ObservableCategory`:

```java
    /**
     * Writes this row's lines, or nothing when the row is empty.
     *
     * @param out the file being built
     * @throws IOException if the file cannot be written
     */
    public void appendTo(BufferedWriter out) throws IOException {
        if (allBox.isSelected()) {
            out.append(kind.keyword()).append(" *");
            out.newLine();
            return;
        }
        for (String name : names()) {
            out.append(kind.keyword()).append(' ').append(name);
            out.newLine();
        }
    }
```

Create `src/my/stepss/obs/ObservableWizard.java`:

```java
package my.stepss.obs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

/**
 * The Observables tab's state: the eight picker rows, the observables file
 * path, the three runtime observable rows and the four recording checkboxes.
 *
 * <p>Holds references to the controls it does not build, handed to it once by
 * {@code StepssUI}. It never looks at a parent, a child or a component name,
 * so where the controls sit in the window is not information this class has
 * and a relayout cannot change what it resets. That is the fix: the handler
 * this replaces asked {@code jPanel7} for its direct children and matched on
 * widget type, which worked only for as long as the generated
 * {@code GridBagLayout} happened to put every control directly on that panel.
 *
 * <p>Takes plain Swing types rather than a {@code StepssUI}, so the harness can
 * bind ordinary controls, bury them several containers deep and prove the
 * depth makes no difference.
 */
public final class ObservableWizard {

    /** The runtime observable rows the Observables tab carries. */
    public static final int RUNTIME_ROWS = 3;

    private final List<ObservableCategory> categories;
    private final JTextField observablesFile;
    private final JComboBox<?>[] runtimeTypes;
    private final JTextField[] runtimeNames;
    private final JCheckBox wizardBox;
    private final JCheckBox trajectoryBox;
    private final JCheckBox continuousBox;
    private final JCheckBox discreteBox;
    private final JCheckBox dumpBox;

    /**
     * @param observablesFile the observables file path
     * @param runtimeTypes    the three runtime observable dropdowns
     * @param runtimeNames    the three runtime observable name fields
     * @param wizardBox       Show observable dialog
     * @param trajectoryBox   Save initialization data
     * @param continuousBox   Save Continuous trace
     * @param discreteBox     Save Discrete trace
     * @param dumpBox         Save dump trace
     * @throws IllegalArgumentException if an array is the wrong length or any
     *                                  control is null
     */
    public ObservableWizard(JTextField observablesFile, JComboBox<?>[] runtimeTypes,
            JTextField[] runtimeNames, JCheckBox wizardBox, JCheckBox trajectoryBox,
            JCheckBox continuousBox, JCheckBox discreteBox, JCheckBox dumpBox) {
        // Checked here rather than at the first reset, on the same reasoning as
        // ScenarioBinding: a wiring short by one control is a tab that quietly
        // stops clearing something, which is the exact fault this replaces.
        if (runtimeTypes == null || runtimeTypes.length != RUNTIME_ROWS) {
            throw new IllegalArgumentException("expected " + RUNTIME_ROWS
                    + " runtime observable types");
        }
        if (runtimeNames == null || runtimeNames.length != RUNTIME_ROWS) {
            throw new IllegalArgumentException("expected " + RUNTIME_ROWS
                    + " runtime observable names");
        }
        this.observablesFile = require(observablesFile, "observables file");
        this.runtimeTypes = runtimeTypes.clone();
        this.runtimeNames = runtimeNames.clone();
        this.wizardBox = require(wizardBox, "observable dialog checkbox");
        this.trajectoryBox = require(trajectoryBox, "trajectory checkbox");
        this.continuousBox = require(continuousBox, "continuous trace checkbox");
        this.discreteBox = require(discreteBox, "discrete trace checkbox");
        this.dumpBox = require(dumpBox, "dump trace checkbox");
        for (int row = 0; row < RUNTIME_ROWS; row++) {
            require(this.runtimeTypes[row], "runtime observable type " + row);
            require(this.runtimeNames[row], "runtime observable name " + row);
        }
        List<ObservableCategory> rows = new ArrayList<>();
        for (ObservableCategory.Kind kind : ObservableCategory.Kind.values()) {
            rows.add(new ObservableCategory(kind));
        }
        this.categories = Collections.unmodifiableList(rows);
    }

    /** The eight rows, in the order RAMSES writes them to the trajectory. */
    public List<ObservableCategory> categories() {
        return categories;
    }

    /**
     * Writes the observables file the wizard describes.
     *
     * <p>The two trailing blank lines are what the hand-written version wrote.
     * {@code add_observ} returns early on a line whose trimmed length is zero,
     * so they are inert, and they are kept rather than removed because nothing
     * is gained by changing a file format on the way past.
     *
     * @param out where to write it
     * @throws IOException if the file cannot be written
     */
    public void write(File out) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8))) {
            for (ObservableCategory category : categories) {
                category.appendTo(writer);
            }
            writer.newLine();
            writer.newLine();
        }
    }

    private static <T> T require(T control, String what) {
        if (control == null) {
            throw new IllegalArgumentException("no " + what);
        }
        return control;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `ant compile` then `tools/observables-harness.sh`

Expected: `ALL CHECKS PASSED`, including `PASS  the observables file`.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/obs/ObservableCategory.java src/my/stepss/obs/ObservableWizard.java src/my/stepss/obs/ObservablesHarness.java
git commit -m "One writer for the observables file, across all eight categories

Replaces five copy-pasted blocks with a loop over the categories, which is
what removes the trailing space branch lines carried and no other category
did. The file is compared whole in the harness rather than probed."
```

---

### Task 5: Reset, through a tree that is not flat

**Files:**
- Modify: `src/my/stepss/obs/ObservableWizard.java`
- Modify: `src/my/stepss/obs/ObservablesHarness.java`

**Interfaces:**
- Consumes: everything from Task 4.
- Produces: `void ObservableWizard.reset()`; `boolean ObservableWizard.isEmpty()`.

- [ ] **Step 1: Write the failing test**

Add to `ObservablesHarness`, add `checkResetThroughANestedTree();` and
`checkIsEmpty();` to `main` after `checkTheFileItWrites();`, and add the
imports `javax.swing.JPanel` and `javax.swing.JScrollPane`:

```java
    /**
     * The acceptance case from the issue: fill every control, bury them, reset,
     * and assert every one came back empty.
     *
     * <p>The nesting is the point. The handler this replaces matched nothing
     * once the controls were reparented into exactly this arrangement, so a
     * check against controls sitting flat on one panel would pass while being
     * blind to the only fault that has ever occurred here.
     */
    private static void checkResetThroughANestedTree() {
        ObservableWizard wizard = newWizard();
        JPanel content = new JPanel();
        for (ObservableCategory row : wizard.categories()) {
            JPanel line = new JPanel();
            line.add(row.nameLabel());
            line.add(row.field());
            line.add(row.addButton());
            line.add(row.list());
            line.add(row.removeButton());
            line.add(row.allBox());
            JPanel wrapper = new JPanel();
            wrapper.add(line);
            content.add(wrapper);
        }
        // The third level, and the one that broke the old walk.
        new JScrollPane(content);

        for (ObservableCategory row : wizard.categories()) {
            row.field().setText("x1");
            row.add();
            row.allBox().setSelected(true);
            row.allToggled();
        }
        wizard.observablesFile().setText("/tmp/obs.dat");
        for (int row = 0; row < ObservableWizard.RUNTIME_ROWS; row++) {
            wizard.runtimeName(row).setText("g" + row);
            wizard.runtimeType(row).setSelectedIndex(2);
        }
        wizard.wizardBox().setSelected(true);
        wizard.trajectoryBox().setSelected(true);
        wizard.continuousBox().setSelected(true);
        wizard.discreteBox().setSelected(true);
        wizard.dumpBox().setSelected(true);

        wizard.reset();

        for (ObservableCategory row : wizard.categories()) {
            expect(row.kind() + " field", "", row.field().getText());
            expect(row.kind() + " list", 0, row.list().getItemCount());
            expect(row.kind() + " all", false, row.allBox().isSelected());
            expect(row.kind() + " field enabled", true, row.field().isEnabled());
            expect(row.kind() + " list enabled", true, row.list().isEnabled());
        }
        expect("observables path", "", wizard.observablesFile().getText());
        for (int row = 0; row < ObservableWizard.RUNTIME_ROWS; row++) {
            expect("runtime name " + row, "", wizard.runtimeName(row).getText());
            expect("runtime type " + row, 0,
                    wizard.runtimeType(row).getSelectedIndex());
        }
        expect("wizard box", false, wizard.wizardBox().isSelected());
        expect("trajectory box", false, wizard.trajectoryBox().isSelected());
        expect("continuous box", false, wizard.continuousBox().isSelected());
        expect("discrete box", false, wizard.discreteBox().isSelected());
        expect("dump box", false, wizard.dumpBox().isSelected());
        expect("empty after reset", true, wizard.isEmpty());
    }

    /** A ticked All contributes to a run even with an empty list. */
    private static void checkIsEmpty() {
        ObservableWizard wizard = newWizard();
        expect("a fresh wizard is empty", true, wizard.isEmpty());

        wizard.categories().get(0).field().setText("b1");
        wizard.categories().get(0).add();
        expect("a named observable is not empty", false, wizard.isEmpty());

        wizard.reset();
        wizard.categories().get(7).allBox().setSelected(true);
        expect("a ticked All is not empty", false, wizard.isEmpty());
    }
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `ant compile`

Expected: FAIL, javac reports `cannot find symbol: method reset()`.

- [ ] **Step 3: Write the minimal implementation**

Add to `ObservableWizard`:

```java
    public JTextField observablesFile() {
        return observablesFile;
    }

    public JComboBox<?> runtimeType(int row) {
        return runtimeTypes[row];
    }

    public JTextField runtimeName(int row) {
        return runtimeNames[row];
    }

    public JCheckBox wizardBox() {
        return wizardBox;
    }

    public JCheckBox trajectoryBox() {
        return trajectoryBox;
    }

    public JCheckBox continuousBox() {
        return continuousBox;
    }

    public JCheckBox discreteBox() {
        return discreteBox;
    }

    public JCheckBox dumpBox() {
        return dumpBox;
    }

    /**
     * Everything Clear clears.
     *
     * <p>All four recording checkboxes and all three runtime rows, including
     * the dropdowns. The handler this replaces emptied the three name fields
     * but left their type dropdowns set, and reset two of the four recording
     * boxes while leaving Save Continuous trace and Save Discrete trace
     * ticked, which is not something a button called Clear should do.
     *
     * <p>Showing and hiding the picker panel is not here. That stays in
     * {@code StepssUI} with the rest of the tab's visibility, which is what
     * lets this class be built without a frame.
     */
    public void reset() {
        for (ObservableCategory category : categories) {
            category.clear();
        }
        observablesFile.setText("");
        for (int row = 0; row < RUNTIME_ROWS; row++) {
            runtimeNames[row].setText("");
            if (runtimeTypes[row].getItemCount() > 0) {
                runtimeTypes[row].setSelectedIndex(0);
            }
        }
        wizardBox.setSelected(false);
        trajectoryBox.setSelected(false);
        continuousBox.setSelected(false);
        discreteBox.setSelected(false);
        dumpBox.setSelected(false);
    }

    /**
     * True when the picker would contribute nothing to a run.
     *
     * <p>A ticked All counts even over an empty list, because it is what asks
     * for every member of its category.
     */
    public boolean isEmpty() {
        for (ObservableCategory category : categories) {
            if (category.isAll() || !category.names().isEmpty()) {
                return false;
            }
        }
        return true;
    }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `ant compile` then `tools/observables-harness.sh`

Expected: `ALL CHECKS PASSED`, with 40 PASS lines from the eight rows plus the
tab state, and three from `checkIsEmpty`.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/obs/ObservableWizard.java src/my/stepss/obs/ObservablesHarness.java
git commit -m "Reset the whole Observables tab, checked through a nested tree

Clear now resets the three runtime dropdowns and all four recording
checkboxes, not the name fields and two of the boxes. The check buries the
controls three containers deep before touching them, which is the arrangement
that made the old walk match nothing."
```

---

### Task 6: Two runtime display types RAMSES accepts and the dropdown never offered

**Files:**
- Modify: `src/my/stepss/StepssUI.java:98-113` (`observableTypes`)
- Modify: `src/my/stepss/StepssUI.java:3271-3283` (`writeObservable`)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Write the failing test**

This one has no harness: `OBSERVABLE_TYPES` and `writeObservable` are private
members of a class that cannot be constructed outside an installation. The
check is by inspection against RAMSES, and it is written into the code as the
comment below. Record in the commit that it was verified by reading
`stepss-ramses/src/core/ramses.f90:818-938`.

- [ ] **Step 2: Confirm the gap before closing it**

Run:

```bash
sed -n '818,940p' ../stepss-ramses/src/core/ramses.f90 | grep -n "case("
```

Expected: eight `case(` lines covering `BV`, `MS`/`COI`, the four branch power
codes, `ON`, `TO`, `LAT`, `o-d`/`P-d`, and `RT`/`SOL`. Fourteen keywords, two
of which (`TO`, `SOL`) are absent from `observableTypes()`.

If `../stepss-ramses` is not checked out, this step is skipped and the gap is
taken on the spec's authority; nothing later depends on running it.

- [ ] **Step 3: Write the implementation**

In `StepssUI.java`, add a constant beside `WALL_TIME`:

```java
    /** The display types that name no equipment, so a blank field is normal. */
    private static final String SOLUTIONS = "Injector solutions";
```

Replace the body of `observableTypes()` with:

```java
    private static Map<String, String> observableTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("Bus Voltage", "BV");
        types.put("Machine Speed", "MS");
        types.put("Omega-delta of machine", "o-d");
        types.put("Active power-delta of machine", "P-d");
        types.put("Center of Inertia", "COI");
        types.put(WALL_TIME, "RT");
        types.put(SOLUTIONS, "SOL");
        types.put("Latency", "LAT");
        types.put("Branch Active Power Origin", "BPO");
        types.put("Branch Active Power Extremity", "BPE");
        types.put("Branch Reactive Power Origin", "BQO");
        types.put("Branch Reactive Power Extremity", "BQE");
        types.put("Injector Observable", "ON");
        types.put("Two-port injector Observable", "TO");
        return Collections.unmodifiableMap(types);
    }
```

Replace the equipment-name test in `writeObservable` so both no-equipment types
are covered:

```java
    private String writeObservable(BufferedWriter out, JComboBox type, JTextField name) throws IOException {
        String label = String.valueOf(type.getSelectedItem());
        // RT and SOL name no equipment. RAMSES treats both as a "Special
        // Display" reading a single token, so a blank field is how they are
        // normally left and must not be read as an unused row.
        boolean noEquipment = WALL_TIME.equals(label) || SOLUTIONS.equals(label);
        if (name.getText().isEmpty() && !noEquipment) {
            return null;
        }
        String keyword = OBSERVABLE_TYPES.get(label);
        if (keyword == null) {
            return "The command file could not be written.";
        }
        out.append(noEquipment ? keyword + " " + keyword
                : keyword + " " + name.getText());
        out.newLine();
        return null;
    }
```

Update the three runtime name field tooltips. **They exist twice**, once in
`StepssUI.java`'s `initComponents` (`:2104`, `:2129`, `:2135`) and once in
`StepssUI.form` as `toolTipText` properties. Edit **both**, or the next person
to open the form in NetBeans regenerates `initComponents` and the change
disappears. Find them with:

```bash
grep -n "Here you clarify the name of the equipment" src/my/stepss/StepssUI.java src/my/stepss/StepssUI.form
```

Expected: three hits in each file.

In each of the six, insert this sentence immediately before the `<br><br>`
that precedes "Additionally you can pass extra commands to gnuplot".

In `StepssUI.java`, where the tooltip is a Java string literal:

```
4) if you selected Injector Observable or Two-port injector Observable, put two names: the equipment, then the observable within its model, separated by a space.<br>
```

In `StepssUI.form`, where the same text is XML-escaped (`<` is `&lt;`), the
`<br>` is written `&lt;br&gt;`:

```
4) if you selected Injector Observable or Two-port injector Observable, put two names: the equipment, then the observable within its model, separated by a space.&lt;br&gt;
```

- [ ] **Step 4: Verify it compiles and the dropdown carries fourteen**

Run: `ant compile`

Expected: BUILD SUCCESSFUL.

Run:

```bash
grep -c "types.put" src/my/stepss/StepssUI.java
```

Expected: `14` (it is `12` before this task).

Run:

```bash
grep -c "Two-port injector Observable" src/my/stepss/StepssUI.java src/my/stepss/StepssUI.form
```

Expected: `4` in the `.java` (one dropdown entry plus three tooltips) and `3`
in the `.form`.

- [ ] **Step 5: Commit**

```bash
git add src/my/stepss/StepssUI.java
git commit -m "The runtime dropdown offers all fourteen display types

setup_runtime_observables accepts BV, MS, COI, BPO, BPE, BQO, BQE, ON, TO,
LAT, o-d, P-d, RT and SOL. The dropdown offered twelve, missing TO, which is
the two-port analogue of ON, and SOL. TO makes two-port injectors reachable
for live plotting, which they were not on either path.

SOL names no equipment, so writeObservable now covers both no-equipment types
rather than special-casing Wall Time alone.

Verified against stepss-ramses/src/core/ramses.f90:818-938."
```

---

### Task 7: Switch `StepssUI` over, and delete the generated picker

**Files:**
- Modify: `src/my/stepss/StepssUI.java` (see the deletion list below)
- Modify: `src/my/stepss/StepssUI.form:1187-1624`

**Interfaces:**
- Consumes: `ObservableWizard`, `ObservableCategory` from Tasks 2 to 5.
- Produces: nothing later tasks depend on. This is the last task.

- [ ] **Step 1: Add the wizard field and its binder**

In `StepssUI.java`, add the import:

```java
import my.stepss.obs.ObservableCategory;
import my.stepss.obs.ObservableWizard;
```

Add the field beside `scenarioBinding` (`StepssUI.java:136`):

```java
    /**
     * The Observables tab, named once.
     *
     * <p>Field references and eight rows this builds, handed over at
     * construction. Clear used to find its controls by walking jPanel7 and
     * matching on widget type, which tied what it cleared to the layout: that
     * panel was the last one applyModernChrome() left alone, and the day it
     * stopped being so, Clear would have stopped clearing without a word.
     */
    private final ObservableWizard observables;
```

Assign it in the constructor beside `scenarioBinding = bindScenario();`
(`StepssUI.java:154`), **before** `applyModernChrome()` runs, since
`layoutObservablesTab` reads `observables.categories()`:

```java
        observables = bindObservables();
```

Add the binder beside `bindScenario()` (`StepssUI.java:523`):

```java
    private ObservableWizard bindObservables() {
        ObservableWizard wizard = new ObservableWizard(fileObs,
                new JComboBox<?>[]{runtimeObsType, runtimeObsType1, runtimeObsType2},
                new JTextField[]{runtimeObsName, runtimeObsName1, runtimeObsName2},
                observFileWizButton, saveOutputTrajButton, saveContTrace,
                saveDiscTrace, saveDumpButton);
        for (ObservableCategory category : wizard.categories()) {
            category.install(banner::warn);
        }
        return wizard;
    }
```

- [ ] **Step 2: Build the picker rows in the layout**

Replace `jPanel4.add(jPanel7, BorderLayout.CENTER);` in
`layoutObservablesTab` (`StepssUI.java:410`) with:

```java
        jPanel7.removeAll();
        jPanel7.setLayout(new GridBagLayout());
        int pickerRow = 0;
        for (ObservableCategory category : observables.categories()) {
            jPanel7.add(pickerRow(category), stretch(pickerRow++));
        }
        jPanel4.add(jPanel7, BorderLayout.CENTER);
```

Add the row builder beside `observableRow` (`StepssUI.java:534`):

```java
    /**
     * One picker row: what to name on the left of centre, what has been named
     * on the right, so the two halves keep the same width down the panel.
     */
    private static JPanel pickerRow(ObservableCategory category) {
        JPanel line = new JPanel(new BorderLayout(6, 0));
        line.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JLabel name = category.nameLabel();
        name.setPreferredSize(new Dimension(150, name.getPreferredSize().height));
        line.add(name, BorderLayout.WEST);

        JPanel entry = new JPanel(new BorderLayout(6, 0));
        entry.add(category.field(), BorderLayout.CENTER);
        entry.add(leftRow(category.addButton(), category.allBox()), BorderLayout.EAST);

        JPanel chosen = new JPanel(new BorderLayout(6, 0));
        chosen.add(category.list(), BorderLayout.CENTER);
        chosen.add(category.removeButton(), BorderLayout.EAST);

        JPanel middle = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        middle.add(entry);
        middle.add(chosen);
        line.add(middle, BorderLayout.CENTER);
        return line;
    }
```

- [ ] **Step 3: Collapse the handlers**

Replace the body of `clearObsFileButtonActionPerformed`
(`StepssUI.java:5511-5535`) with:

```java
    private void clearObsFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearObsFileButtonActionPerformed
        observables.reset();
        observFileWizButtonActionPerformed(null);
    }//GEN-LAST:event_clearObsFileButtonActionPerformed
```

Replace the body of `createCustomObsFile` (`StepssUI.java:7404`) with:

```java
    private boolean createCustomObsFile() {
        try {
            observables.write(new File(myTempDir, "customObs.txt"));
            return true;
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
```

Replace the body of `noObservablesPicked` (`StepssUI.java:3647-3655`) with:

```java
    /** True when the observable dialog would contribute nothing to a run. */
    private boolean noObservablesPicked() {
        return observables.isEmpty();
    }
```

Update the comment above it (`StepssUI.java:3608-3612`) so it says eight rather
than five:

```java
        // The observable dialog's eight picker lists are session state, not part
        // of the scenario, so a case saved with the dialog in use comes back
        // with it ticked over eight empty lists. Left unsaid, the next run would
        // write a customObs.txt of blank lines and plot nothing.
```

Delete these fifteen handler methods entirely, with their `//GEN-FIRST` and
`//GEN-LAST` markers:

`addBusButtonActionPerformed`, `addSyncButtonActionPerformed`,
`addShuntButtonActionPerformed`, `addBranchButtonActionPerformed`,
`addInjButtonActionPerformed`, `remBusObsActionPerformed`,
`remSyncObsActionPerformed`, `remShuntObsActionPerformed`,
`remBranchObsActionPerformed`, `remInjObsActionPerformed`,
`allBusCheckBoxActionPerformed`, `allSyncCheckBoxActionPerformed`,
`allShuntCheckBoxActionPerformed`, `allBranchCheckBoxActionPerformed`,
`allInjCheckBoxActionPerformed`.

- [ ] **Step 4: Delete the generated controls**

**The line numbers below are as of `10a996a` and Task 6 has shifted them**, by
however many lines the `SOLUTIONS` constant and the two `types.put` calls
added above line 1045. The content anchors are the authority, not the numbers.
Find the current ones first:

```bash
grep -n "jLabel25 = new javax.swing.JLabel();\|allInjCheckBox = new javax.swing.JCheckBox();\|jLabel25.setText(\"BUS\");\|jPanel7.add(allInjCheckBox, gridBagConstraints);\|jPanel7.setLayout(new java.awt.GridBagLayout());" src/my/stepss/StepssUI.java
```

Expected: five hits, in this order: the first instantiation, the last
instantiation, `jPanel7.setLayout`, the first configuration line, the last
`jPanel7.add`.

In `src/my/stepss/StepssUI.java`, delete:

- **Instantiations**, the 31 consecutive lines from
  `jLabel25 = new javax.swing.JLabel();` to
  `allInjCheckBox = new javax.swing.JCheckBox();` inclusive (was 1045 to 1075).
  Keep the line above them, `jPanel7 = new javax.swing.JPanel();`.
- **Configuration and adds**, from `jLabel25.setText("BUS");` to
  `jPanel7.add(allInjCheckBox, gridBagConstraints);` inclusive (was 1751 to
  2099). Keep the three lines above them: `jPanel7.setVisible(false);`,
  `jPanel7.setName("jPanel7");` and
  `jPanel7.setLayout(new java.awt.GridBagLayout());`.
- **Declarations** in the `//GEN-BEGIN:variables` block, these 31 names:
  `jLabel25`, `jLabel26`, `jLabel27`, `jLabel28`, `jLabel29`,
  `busObsField`, `syncObsField`, `shuntObsField`, `branchObsField`, `injObsField`,
  `addBusButton`, `addSyncButton`, `addShuntButton`, `addBranchButton`, `addInjButton`,
  `busObsList`, `syncObsList`, `shuntObsList`, `branchObsList`, `injObsList`,
  `remBusObs`, `remSyncObs`, `remShuntObs`, `remBranchObs`, `remInjObs`,
  `filler2`,
  `allBusCheckBox`, `allSyncCheckBox`, `allShuntCheckBox`, `allBranchCheckBox`,
  `allInjCheckBox`.

In `src/my/stepss/StepssUI.form`, delete lines 1187 to 1624 inclusive: the 31
`<Component>` blocks inside `jPanel7`'s `<SubComponents>`. These numbers are
unaffected by Task 6 as long as Task 6's three `.form` tooltip edits stayed on
their existing lines, which they do because each is one long attribute value;
confirm with `sed -n '1186p;1625p;1626p' src/my/stepss/StepssUI.form`, which
must print `<SubComponents>`, `</SubComponents>` and `</Container>`. Leave
`<SubComponents>` on line 1186 and `</SubComponents>` on line 1625 adjacent,
and leave the `<Container class="javax.swing.JPanel" name="jPanel7">` element
itself. `jPanel4`'s generated `GroupLayout` refers to `jPanel7` by id
(`StepssUI.form:1063` and `:1112`), so deleting the container would leave two
dangling references.

- [ ] **Step 5: Verify nothing still refers to a deleted control**

Run:

```bash
grep -n "busObsField\|syncObsField\|shuntObsField\|branchObsField\|injObsField\|addBusButton\|addSyncButton\|addShuntButton\|addBranchButton\|addInjButton\|busObsList\|syncObsList\|shuntObsList\|branchObsList\|injObsList\|remBusObs\|remSyncObs\|remShuntObs\|remBranchObs\|remInjObs\|allBusCheckBox\|allSyncCheckBox\|allShuntCheckBox\|allBranchCheckBox\|allInjCheckBox\|filler2\|jLabel2[5-9]" src/my/stepss/StepssUI.java src/my/stepss/StepssUI.form
```

Expected: no output.

Run: `ant compile`

Expected: BUILD SUCCESSFUL, and no new deprecation or unchecked warnings
beyond those the tree already produces.

Run: `tools/observables-harness.sh` and `tools/scenario-harness.sh`

Expected: `ALL CHECKS PASSED` from both. The scenario harness matters here
because `bindScenario` and `bindObservables` share `fileObs`,
`observFileWizButton` and all three runtime rows.

- [ ] **Step 6: Check the real window**

Run: `ant jar` then launch the application, open the Observables tab and tick
**Show observable dialog**.

Confirm by eye:

1. Eight rows appear, labelled Buses, Shunts, Impedance loads, Branches,
   Synchronous machines, Injectors, Two-port injectors, Discrete controllers.
2. Hovering **Impedance loads** shows a tooltip naming both `IMPLOAD` and
   `LOAD`; hovering **Two-port injectors** names both `TWOP` and `LINK`.
3. **Remove** is greyed until a name is in the list.
4. Adding the same name twice raises the banner and leaves the field's text
   alone rather than replacing it with `Already in List!`.
5. Ticking **All** greys that row's field, list and Add.
6. **Clear** empties all eight rows, all three runtime rows including their
   dropdowns, and all four recording checkboxes.
7. The runtime dropdown lists fourteen entries, including
   **Two-port injector Observable** and **Injector solutions**.

- [ ] **Step 7: Commit**

```bash
git add src/my/stepss/StepssUI.java src/my/stepss/StepssUI.form
git commit -m "Build the observable picker, stop walking it

layoutObservablesTab builds jPanel7's eight rows from ObservableWizard instead
of the 31 controls StepssUI.form carried, so Clear resets a named set rather
than whatever a type-matching walk happens to reach. That walk worked only
because jPanel7 was the last GridBagLayout island applyModernChrome left
alone; it is now built like every other row in the window.

Sixteen handlers and createCustomObsFile become delegations, and the picker
gains the three categories add_observ accepts and it never offered: IMPLOAD,
TWOP and DCTL.

The jPanel7 Container stays in the form because jPanel4's GroupLayout refers
to it by id; only its 31 children go.

Closes #15."
```

---

## After the plan

Two follow-ups this work turned up but does not do:

1. **A stepss-ramses issue for the integrated scheme.** `simul_decomp.f90:2230`
   evaluates all fourteen runtime display types; `simul_integr.f90:443` and
   `:1251` evaluate ten and mention `ON`, `TO`, `o-d` and `P-d` nowhere in the
   file. `varcol` reserves each type's columns at parse time and gnuplot plots
   `using 1:varcol(i)`, so an unevaluated row shifts every column after it and
   every curve below it plots the wrong quantity. Task 6 adds `TO`, which makes
   a fourth type reach that path.
2. **The picker lists in the `.cfg`.** #12 deliberately left them out.
   `ObservableCategory.names()` and `isAll()` are what make that cheap now.
