package my.stepss.config;

import java.util.Arrays;

/**
 * Everything a saved study is: which files it reads, which observables it
 * plots while it runs, and which traces it writes.
 *
 * <p>The contents are not a judgement call. This is exactly the set that
 * {@code StepssUI.createCommandFile()} and {@code createPFCCommandFile()}
 * read to write {@code cmd.txt} and {@code PFCcmd.txt}, so a scenario that
 * round-trips is a run that reproduces. Anything the two writers do not
 * consult - the Analysis tab's SSA parameters, the working directory, the
 * window's geometry - is deliberately absent, because restoring it would
 * change something the run does not depend on.
 *
 * <p>Carries no Swing and no I/O. That is what lets the round trip be checked
 * headlessly in {@link ScenarioHarness}: {@code StepssUI}'s constructor
 * extracts payloads and calls {@code initRamses()}, so a test that needed a
 * live frame could not run at all.
 *
 * <p>Mutable, unlike most of the value types in this codebase. It is filled
 * one field at a time from ten text fields and read back into them, and a
 * twenty-argument constructor between those two loops would be a place for
 * arguments to be swapped silently rather than a safeguard against it.
 *
 * <p>Paths held here are always absolute, whatever the file they came from
 * stored. See {@link ScenarioPaths} for why that is not negotiable.
 */
public final class Scenario {

    /** System data rows on the System Data tab, and slots in {@code cmd.txt}. */
    public static final int DATA_SLOTS = 10;

    /** Runtime observable rows on the Observables tab. */
    public static final int RUNTIME_ROWS = 3;

    private final String[] data = blanks(DATA_SLOTS);
    private String disturbance = "";
    private String observablesFile = "";
    private String diagram = "";
    private boolean observableWizard;
    private final String[] runtimeType = blanks(RUNTIME_ROWS);
    private final String[] runtimeName = blanks(RUNTIME_ROWS);
    private boolean saveTrajectory;
    private boolean saveContinuousTrace;
    private boolean saveDiscreteTrace;
    private boolean saveDump;

    private static String[] blanks(int length) {
        String[] values = new String[length];
        Arrays.fill(values, "");
        return values;
    }

    /**
     * One system data file path.
     *
     * @param slot zero-based, below {@link #DATA_SLOTS}
     * @return the path, or "" for an empty row
     */
    public String data(int slot) {
        return data[slot];
    }

    /**
     * Sets one system data file path.
     *
     * <p>The slot is part of the meaning, not an ordinal: an empty row four
     * with a filled row five is a case the user built that way, and packing
     * the rows down on save would rewrite it. Both this and the file format
     * keep every row where it was put.
     *
     * @param slot  zero-based, below {@link #DATA_SLOTS}
     * @param value the path, null read as ""
     */
    public void setData(int slot, String value) {
        data[slot] = text(value);
    }

    /** The disturbance (.dst) file path, or "". */
    public String disturbance() {
        return disturbance;
    }

    /** @param value the disturbance file path, null read as "" */
    public void setDisturbance(String value) {
        this.disturbance = text(value);
    }

    /** The observables file path, or "". */
    public String observablesFile() {
        return observablesFile;
    }

    /** @param value the observables file path, null read as "" */
    public void setObservablesFile(String value) {
        this.observablesFile = text(value);
    }

    /** The one-line diagram template (.svg) path, or "". */
    public String diagram() {
        return diagram;
    }

    /** @param value the diagram template path, null read as "" */
    public void setDiagram(String value) {
        this.diagram = text(value);
    }

    /** Whether the observable dialog builds the observables list. */
    public boolean observableWizard() {
        return observableWizard;
    }

    /** @param value whether the observable dialog is in use */
    public void setObservableWizard(boolean value) {
        this.observableWizard = value;
    }

    /**
     * One runtime observable's type, as the label the dropdown shows.
     *
     * <p>The label, never the dropdown's selected index. An index silently
     * re-points at a different observable the moment the list gains, loses or
     * reorders an entry, and it has no way to say "this is not a type I
     * know"; a label that is no longer in the list is caught on load and
     * reported. {@code OBSERVABLE_TYPES} in {@code StepssUI} is the ordered
     * table both ends read.
     *
     * @param row zero-based, below {@link #RUNTIME_ROWS}
     * @return the type label, or "" when the row was never set
     */
    public String runtimeType(int row) {
        return runtimeType[row];
    }

    /**
     * @param row   zero-based, below {@link #RUNTIME_ROWS}
     * @param value the type label, null read as ""
     */
    public void setRuntimeType(int row, String value) {
        runtimeType[row] = text(value);
    }

    /**
     * One runtime observable's equipment name.
     *
     * @param row zero-based, below {@link #RUNTIME_ROWS}
     * @return the name, or "" for an unused row
     */
    public String runtimeName(int row) {
        return runtimeName[row];
    }

    /**
     * @param row   zero-based, below {@link #RUNTIME_ROWS}
     * @param value the equipment name, null read as ""
     */
    public void setRuntimeName(int row, String value) {
        runtimeName[row] = text(value);
    }

    /** Whether the run writes output.trj. */
    public boolean saveTrajectory() {
        return saveTrajectory;
    }

    /** @param value whether the run writes output.trj */
    public void setSaveTrajectory(boolean value) {
        this.saveTrajectory = value;
    }

    /** Whether the run writes cont.trace. */
    public boolean saveContinuousTrace() {
        return saveContinuousTrace;
    }

    /** @param value whether the run writes cont.trace */
    public void setSaveContinuousTrace(boolean value) {
        this.saveContinuousTrace = value;
    }

    /** Whether the run writes disc.trace. */
    public boolean saveDiscreteTrace() {
        return saveDiscreteTrace;
    }

    /** @param value whether the run writes disc.trace */
    public void setSaveDiscreteTrace(boolean value) {
        this.saveDiscreteTrace = value;
    }

    /** Whether the run writes dump.trace. */
    public boolean saveDump() {
        return saveDump;
    }

    /** @param value whether the run writes dump.trace */
    public void setSaveDump(boolean value) {
        this.saveDump = value;
    }

    /**
     * Whether this scenario names no input files at all.
     *
     * <p>What Save configuration checks before writing anything. The old
     * handler wrote a file holding nothing but a date comment and reported
     * success, which is how the fault this class replaces went unnoticed for
     * as long as it did; refusing to write an empty scenario is the cheapest
     * guard against a repeat.
     *
     * <p>Judged on the inputs only. The four recording checkboxes and the
     * three observable rows describe what to do with a case, so a file
     * carrying those and no case is not a scenario worth reopening.
     *
     * @return true when no data row, disturbance or observables file is set
     */
    public boolean isEmpty() {
        for (String path : data) {
            if (!path.isEmpty()) {
                return false;
            }
        }
        return disturbance.isEmpty() && observablesFile.isEmpty();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
