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
        checkClearEmptiesARow();
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
