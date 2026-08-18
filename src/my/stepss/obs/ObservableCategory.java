package my.stepss.obs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

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

    private final Kind kind;
    private final JLabel nameLabel;
    private final JTextField field = new JTextField();
    private final JButton addButton = new JButton("Add");
    private final JComboBox<String> list = new JComboBox<>();
    private final JButton removeButton = new JButton("Remove");
    private final JCheckBox allBox = new JCheckBox("All");

    /**
     * RAMSES reads an observable name into a character(len=20). A longer one is
     * truncated without a word, which is worse than a refusal because the
     * truncated name usually matches nothing and the observable is dropped
     * with a warning in a log the user is not reading.
     */
    private static final int MAX_NAME = 20;

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
        syncRemoveEnabled();
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
}
