package my.stepss.config;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

/**
 * The named set of controls a scenario is read from and written back to.
 *
 * <p>Holds references to the controls themselves, handed to it once by
 * {@code StepssUI}. It never looks at a parent, a child or a component name,
 * so where the controls sit in the window is not information this class has
 * and a relayout cannot change what it persists. That is the fix: the handler
 * this replaces asked {@code jPanel2} and {@code jPanel4} for their direct
 * children and matched on {@code getName()}, which worked only for as long as
 * the generated {@code GroupLayout} happened to put every control directly on
 * those two panels. When {@code applyModernChrome()} moved them into a scroll
 * pane and three wrapper panels, the loops matched nothing, in both directions,
 * silently.
 *
 * <p>Takes plain Swing types rather than a {@code StepssUI}, so
 * {@link ScenarioHarness} can bind ordinary controls, bury them several
 * containers deep and prove the depth makes no difference. Binding to the
 * frame would have made the round trip untestable, because
 * {@code StepssUI}'s constructor extracts the toolchain and exits the JVM if
 * it cannot.
 */
public final class ScenarioBinding {

    private final JTextField[] dataFields;
    private final JTextField disturbanceField;
    private final JTextField observablesField;
    private final JCheckBox wizardBox;
    private final JComboBox<?>[] runtimeTypes;
    private final JTextField[] runtimeNames;
    private final JCheckBox trajectoryBox;
    private final JCheckBox continuousBox;
    private final JCheckBox discreteBox;
    private final JCheckBox dumpBox;

    /**
     * @param dataFields       the ten system data rows, in tab order
     * @param disturbanceField the disturbance path
     * @param observablesField the observables file path
     * @param wizardBox        Show observable dialog
     * @param runtimeTypes     the three runtime observable dropdowns
     * @param runtimeNames     the three runtime observable name fields
     * @param trajectoryBox    Save initialization data / output.trj
     * @param continuousBox    Save Continuous trace
     * @param discreteBox      Save Discrete trace
     * @param dumpBox          Save dump trace
     * @throws IllegalArgumentException if an array is the wrong length or any
     *                                  control is null
     */
    public ScenarioBinding(JTextField[] dataFields, JTextField disturbanceField,
            JTextField observablesField, JCheckBox wizardBox,
            JComboBox<?>[] runtimeTypes, JTextField[] runtimeNames,
            JCheckBox trajectoryBox, JCheckBox continuousBox,
            JCheckBox discreteBox, JCheckBox dumpBox) {
        // Checked here rather than at the first save. A binding wired up short
        // by one field is a scenario that silently stops carrying a row, which
        // is the exact class of fault this replaces; failing at construction
        // makes it a crash on the first launch after the mistake instead.
        require(dataFields, Scenario.DATA_SLOTS, "data fields");
        require(runtimeTypes, Scenario.RUNTIME_ROWS, "runtime observable types");
        require(runtimeNames, Scenario.RUNTIME_ROWS, "runtime observable names");
        requireNotNull(disturbanceField, "disturbance field");
        requireNotNull(observablesField, "observables field");
        requireNotNull(wizardBox, "observable dialog checkbox");
        requireNotNull(trajectoryBox, "trajectory checkbox");
        requireNotNull(continuousBox, "continuous trace checkbox");
        requireNotNull(discreteBox, "discrete trace checkbox");
        requireNotNull(dumpBox, "dump trace checkbox");
        this.dataFields = dataFields.clone();
        this.disturbanceField = disturbanceField;
        this.observablesField = observablesField;
        this.wizardBox = wizardBox;
        this.runtimeTypes = runtimeTypes.clone();
        this.runtimeNames = runtimeNames.clone();
        this.trajectoryBox = trajectoryBox;
        this.continuousBox = continuousBox;
        this.discreteBox = discreteBox;
        this.dumpBox = dumpBox;
    }

    /** What the form currently holds. */
    public Scenario read() {
        Scenario scenario = new Scenario();
        for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
            scenario.setData(slot, dataFields[slot].getText());
        }
        scenario.setDisturbance(disturbanceField.getText());
        scenario.setObservablesFile(observablesField.getText());
        scenario.setObservableWizard(wizardBox.isSelected());
        for (int row = 0; row < Scenario.RUNTIME_ROWS; row++) {
            Object selected = runtimeTypes[row].getSelectedItem();
            scenario.setRuntimeType(row, selected == null ? "" : String.valueOf(selected));
            scenario.setRuntimeName(row, runtimeNames[row].getText());
        }
        scenario.setSaveTrajectory(trajectoryBox.isSelected());
        scenario.setSaveContinuousTrace(continuousBox.isSelected());
        scenario.setSaveDiscreteTrace(discreteBox.isSelected());
        scenario.setSaveDump(dumpBox.isSelected());
        return scenario;
    }

    /**
     * Puts {@code scenario} into the form.
     *
     * @param scenario the scenario to restore
     * @return what could not be restored, one sentence each; empty on success
     */
    public List<String> apply(Scenario scenario) {
        List<String> problems = new ArrayList<>();
        for (int slot = 0; slot < Scenario.DATA_SLOTS; slot++) {
            dataFields[slot].setText(scenario.data(slot));
        }
        disturbanceField.setText(scenario.disturbance());
        observablesField.setText(scenario.observablesFile());
        wizardBox.setSelected(scenario.observableWizard());
        for (int row = 0; row < Scenario.RUNTIME_ROWS; row++) {
            selectType(row, scenario.runtimeType(row), problems);
            runtimeNames[row].setText(scenario.runtimeName(row));
        }
        trajectoryBox.setSelected(scenario.saveTrajectory());
        continuousBox.setSelected(scenario.saveContinuousTrace());
        discreteBox.setSelected(scenario.saveDiscreteTrace());
        dumpBox.setSelected(scenario.saveDump());
        return problems;
    }

    /**
     * Selects a dropdown entry by its label.
     *
     * <p>By label, and verified afterwards, because {@code setSelectedItem}
     * with an item the model does not hold leaves the selection where it was
     * and returns nothing to say so. Verifying is what turns "this scenario
     * names an observable type this build no longer has" from a row pointing
     * quietly at the wrong observable into a sentence in the load report.
     *
     * <p>An empty label is a row the file never set, not an error: the file is
     * allowed to be missing keys, and a missing type leaves the dropdown where
     * it already is.
     */
    private void selectType(int row, String label, List<String> problems) {
        if (label.isEmpty()) {
            return;
        }
        runtimeTypes[row].setSelectedItem(label);
        Object selected = runtimeTypes[row].getSelectedItem();
        if (!label.equals(selected == null ? null : String.valueOf(selected))) {
            problems.add("Runtime observable " + (row + 1) + " was saved as \""
                    + label + "\", which is not an observable type this STEPSS"
                    + " offers; the row was left as it was.");
        }
    }

    private static void require(Object[] controls, int length, String what) {
        if (controls == null || controls.length != length) {
            throw new IllegalArgumentException("Expected " + length + " " + what
                    + ", got " + (controls == null ? "null" : controls.length));
        }
        for (int i = 0; i < controls.length; i++) {
            if (controls[i] == null) {
                throw new IllegalArgumentException(what + "[" + i + "] is null");
            }
        }
    }

    private static void requireNotNull(Object control, String what) {
        if (control == null) {
            throw new IllegalArgumentException("The " + what + " is null");
        }
    }
}
