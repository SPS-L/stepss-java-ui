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
