package my.stepss.dyngraph;

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
 * layout does not vary; StepssUI.form is untouched.
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
