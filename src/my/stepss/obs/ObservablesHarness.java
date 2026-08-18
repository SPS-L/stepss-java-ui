package my.stepss.obs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;
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

    public static void main(String[] args) throws IOException {
        checkTheEightKeywords();
        checkClearEmptiesARow();
        checkAddValidates();
        checkRemoveIsSafe();
        checkAllTogglesTheRow();
        checkTheFileItWrites();
        checkInstalledButtonsAreWired();
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
        expect("remove disabled after clear", false, row.removeButton().isEnabled());
        expect("names empty", 0, row.names().size());
        expect("isAll false", false, row.isAll());
    }

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

    /**
     * install() is the only path the window drives the row through, so a
     * listener wired to the wrong method would ship while every check above,
     * which calls the methods directly, went on passing.
     */
    private static void checkInstalledButtonsAreWired() {
        ObservableCategory row = new ObservableCategory(Kind.SYNC);
        List<String> problems = new ArrayList<>();
        row.install(problems::add);

        row.field().setText("g1");
        row.addButton().doClick();
        expect("Add button added the name", 1, row.list().getItemCount());
        expect("Add button reported no problem", 0, problems.size());

        row.field().setText("g1");
        row.addButton().doClick();
        expect("duplicate reported through the sink", 1, problems.size());
        expect("duplicate not added", 1, row.list().getItemCount());

        row.allBox().doClick();
        expect("All ticked by its own click", true, row.allBox().isSelected());
        expect("All disabled the field", false, row.field().isEnabled());
        expect("All disabled Add", false, row.addButton().isEnabled());

        row.allBox().doClick();
        expect("All unticked by a second click", false, row.allBox().isSelected());
        expect("field re-enabled", true, row.field().isEnabled());

        row.list().setSelectedIndex(0);
        row.removeButton().doClick();
        expect("Remove button removed the name", 0, row.list().getItemCount());
    }

    private static void expectRefused(String what, String problem) {
        if (problem == null) {
            fail(what + ": accepted, expected a refusal");
        } else {
            pass(what + ": " + problem);
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
