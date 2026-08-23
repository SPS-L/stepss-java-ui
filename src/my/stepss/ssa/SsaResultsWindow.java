package my.stepss.ssa;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import my.stepss.plot.PlotStyle;

/**
 * One small-signal run, displayed. Non-modal and independent, so several can
 * be open at once: comparing a case with and without its stabilisers means
 * putting two of these side by side, which is what the notebook does with
 * two subplots.
 */
public final class SsaResultsWindow extends JFrame {

    private final SsaResults results;
    private final ModesTableModel model;
    private final JTable table;
    private final SplanePanel splane = new SplanePanel();
    private final ModeShapePanel shape = new ModeShapePanel();
    private final JTextArea participation = new JTextArea();
    private final JCheckBox emOnly =
            new JCheckBox("electromechanical only (0.1 to 2.5 Hz)", true);
    private final JCheckBox realLimitOn = new JCheckBox("real part above");
    private final javax.swing.JTextField realLimit = new javax.swing.JTextField(6);
    private final javax.swing.JTextField pfThreshold = new javax.swing.JTextField(6);
    private final javax.swing.JTextField dampingZeta = new javax.swing.JTextField(5);
    private final JLabel shownCount = new JLabel();
    private final JButton resetZoom = new JButton("Reset zoom");

    /**
     * The real part limit the table, the s-plane and the mode shape are
     * filtered by, and the participation floor the Participation panel is
     * trimmed at. Both are held as numbers, and the fields are only their
     * text: a half-typed "-" is not a threshold, so nothing re-filters until
     * the field commits and parses.
     */
    private double realLimitValue = DEFAULT_REAL_LIMIT;
    private double pfThresholdValue = DEFAULT_PF_THRESHOLD;

    /**
     * Damping ratio of the s-plane's dashed ray. A display option rather than
     * a filter: it moves a line on the plot and hides nothing, which is why it
     * has no tick beside it and why changing it never touches the table or the
     * shown count.
     */
    private double dampingZetaValue = SplanePanel.DEFAULT_DAMPING_ZETA;

    /**
     * The limit offered when the run does not name one, which is every run a
     * current engine makes. It is the value the retired {@code real_limit}
     * parameter defaulted to, so ticking the box reproduces exactly what that
     * default used to select.
     */
    private static final double DEFAULT_REAL_LIMIT = -1.0;

    /**
     * The participation floor offered by default, which is what the retired
     * {@code pf_threshold} parameter defaulted to. The engine's own floor is
     * lower ({@code pf_floor}, $PF_THRES), so this trims a file that already
     * holds more than this; lowering it shows more without re-running, down
     * to that floor.
     */
    private static final double DEFAULT_PF_THRESHOLD = 0.05;

    /**
     * Opens one run in a window of its own. Every call makes a new window on
     * purpose: running the analysis again, or loading a second archive, is
     * nearly always done to compare the two, and each window holds its own
     * parsed copy of the results, so a later run overwriting the files on disk
     * leaves the earlier window intact.
     */
    public static void open(Component parent, SsaResults results) {
        SsaResultsWindow window = new SsaResultsWindow(results);
        my.stepss.WindowCascade.track(window, parent);
        window.setVisible(true);
    }

    public SsaResultsWindow(SsaResults results) {
        super("Small-signal results - " + results.basename()
                + " - " + results.directory().getAbsolutePath());
        this.results = results;
        this.model = new ModesTableModel(visible());
        this.table = new JTable(model);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(new TableRowSorter<ModesTableModel>(model));
        ModeCellRenderer renderer = new ModeCellRenderer(model, table);
        // JTable pre-registers renderers for Number/Double ahead of Object, so
        // registering only against Object.class never reaches columns 0 to 4.
        table.setDefaultRenderer(Integer.class, renderer);
        table.setDefaultRenderer(Double.class, renderer);
        table.setDefaultRenderer(String.class, renderer);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    showSelected();
                }
            }
        });

        emOnly.addActionListener(event -> refilter());
        realLimitOn.addActionListener(event -> refilter());
        realLimitOn.setToolTipText("Shows only the modes whose real part is"
                + " above this, the ones with the least damping. The analysis"
                + " writes every mode, so this is a question about the display"
                + " and is answered again every time the number changes.");
        realLimit.setToolTipText("Re(lambda) in 1/s. Modes at or below this"
                + " are hidden from the table, the s-plane and the mode shape.");
        commitOn(realLimit, () -> {
            realLimitValue = parseField(realLimit, realLimitValue);
            // Typing a number is only a filter change when the box is on, but
            // it should turn the box on rather than do nothing visible.
            if (!realLimitOn.isSelected()) {
                realLimitOn.setSelected(true);
            }
            refilter();
        });

        dampingZeta.setToolTipText("Damping ratio of the dashed ray on the"
                + " s-plane. Modes to the left of it are better damped than"
                + " this; 0.05 is the usual planning criterion. A display"
                + " option: it hides nothing. Between 0 and 1.");
        commitOn(dampingZeta, () -> {
            dampingZetaValue = parseZeta(dampingZeta, dampingZetaValue);
            splane.setDampingZeta(dampingZetaValue);
        });

        pfThreshold.setToolTipText("Hides participation entries below this."
                + " Entries below the run's own pf_floor were never written,"
                + " so lowering this past it shows nothing more.");
        commitOn(pfThreshold, () -> {
            pfThresholdValue = parseField(pfThreshold, pfThresholdValue);
            showSelected();
        });

        // The count is what makes the filters legible: one that empties the
        // table looks like a broken load unless the window says how many
        // modes it is holding back.
        shownCount.setFont(shownCount.getFont().deriveFont(
                shownCount.getFont().getSize2D() - 1.0f));
        syncFields();

        JPanel filters = new JPanel();
        filters.setLayout(new javax.swing.BoxLayout(filters, javax.swing.BoxLayout.Y_AXIS));
        filters.add(row(emOnly));
        filters.add(row(realLimitOn, realLimit, new JLabel(" 1/s"),
                javax.swing.Box.createHorizontalStrut(12),
                new JLabel("damping ray \u03b6 "), dampingZeta));
        filters.add(row(indent(),
                new JLabel("participation factor at least "), pfThreshold));
        filters.add(row(indent(), shownCount));
        filters.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        JPanel left = new JPanel(new BorderLayout());
        left.add(filters, BorderLayout.NORTH);
        left.add(new JScrollPane(table), BorderLayout.CENTER);

        splane.setModes(model.rows());
        splane.addSelectionListener(mode -> selectInTable(mode));

        // Disabled until there is a zoom to leave, so the button says whether
        // the plot is showing everything rather than only offering to make it.
        resetZoom.setToolTipText("Back to the window fitted around the modes"
                + " on screen. Drag a rectangle on the plot to zoom in;"
                + " double-clicking it does this too.");
        resetZoom.addActionListener(event -> splane.resetZoom());
        splane.setZoomListener(() -> resetZoom.setEnabled(splane.isZoomed()));
        resetZoom.setEnabled(splane.isZoomed());

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left,
                withSaveButton(splane, "s-plane", () -> splane.toSvg(
                        Math.max(splane.getWidth(), 500),
                        Math.max(splane.getHeight(), 400)), resetZoom));
        top.setResizeWeight(0.45);

        // A wrapped text area rather than a column of labels. The sentence a
        // reader most needs here, the refusal to show a degenerate mode's
        // participation, is long, and in a non-wrapping label it ran off the
        // panel edge and was clipped. Monospaced so the family, device and
        // variable columns line up, and read-only rather than disabled so the
        // numbers stay selectable.
        //
        // Sized in columns, because wrapping is right for the prose and wrong
        // for the table above it: a JTextArea with no column count asks for
        // whatever its content happens to need, the divider starts there, and
        // the fixed-width rows then wrapped one field onto a line of their
        // own. 66 is the widest line the panel emits, the header naming its
        // last column in full, plus a little margin. The divider is still the
        // reader's to drag; this only decides where it starts.
        participation.setEditable(false);
        participation.setColumns(66);
        participation.setLineWrap(true);
        participation.setWrapStyleWord(true);
        participation.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, participation.getFont().getSize()));
        participation.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JScrollPane participationScroll = new JScrollPane(participation);
        participationScroll.setBorder(BorderFactory.createTitledBorder("Participation"));

        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                participationScroll,
                withSaveButton(shape, "mode-shape", () -> shape.toSvg(
                        Math.max(shape.getWidth(), 400),
                        Math.max(shape.getHeight(), 400))));
        bottom.setResizeWeight(0.45);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        clearDetail();
        setPreferredSize(new Dimension(1080, 780));
        pack();
    }

    /**
     * The modes the filters leave on screen, in the order the table, the
     * s-plane and the mode shape all read them from.
     *
     * <p>The order the two are composed in matters: {@link
     * SsaResults#electromechanical} sorts by frequency and {@link
     * SsaResults#aboveRealLimit} preserves the order it is given, so this way
     * round the table comes out sorted whichever filters are on.
     */
    private List<Mode> visible() {
        List<Mode> rows = results.modes().modes();
        if (emOnly.isSelected()) {
            rows = SsaResults.electromechanical(rows);
        }
        if (realLimitOn.isSelected()) {
            rows = SsaResults.aboveRealLimit(rows, realLimitValue);
        }
        return rows;
    }

    /**
     * Rebuilds everything downstream of the filters.
     *
     * <p>The selection goes first, because a row index into the old list means
     * nothing against the new one. {@link SplanePanel#setModes} refits the
     * axis window as it goes, which is the point of filtering the plot at all:
     * dropping the far-left fast modes is what lets the plane close in around
     * the ones that are left.
     */
    private void refilter() {
        table.clearSelection();
        model.setRows(visible());
        splane.setModes(model.rows());
        syncFields();
        clearDetail();
    }

    /** Enables what the ticks make relevant, and restates the count. */
    private void syncFields() {
        realLimit.setEnabled(true);
        realLimit.setText(trim(realLimitValue));
        pfThreshold.setText(trim(pfThresholdValue));
        dampingZeta.setText(trim(dampingZetaValue));
        int shown = model.rows().size();
        int all = results.modes().modes().size();
        shownCount.setText(shown == all
                ? shown + " modes"
                : "showing " + shown + " of " + all + " modes");
    }

    /**
     * Runs {@code action} when the field commits, on Enter and on losing
     * focus. Both, because a user who types a limit and then reaches for the
     * table with the mouse has committed just as much as one who pressed
     * Enter, and a field that quietly kept the old number in that case is the
     * kind of thing that gets reported as the filter not working.
     */
    private static void commitOn(javax.swing.JTextField field, Runnable action) {
        field.addActionListener(event -> action.run());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                action.run();
            }
        });
    }

    /**
     * The field's value, or {@code fallback} if it does not parse.
     *
     * <p>Reverting beats refusing here. This is a display control, so the
     * worst a bad value can do is show the wrong modes; a modal complaint
     * about a half-typed number, from a field that commits on focus loss,
     * would fire whenever someone clicked away mid-edit.
     */
    /**
     * Reads the damping-ray field, keeping the old value when what is in the
     * box cannot be drawn. Defers to {@link SplanePanel#isDrawableZeta} rather
     * than repeating its bounds, so the field and the plot cannot disagree
     * about which numbers are allowed: a rejected value would otherwise sit in
     * the box looking accepted while the ray stayed where it was.
     */
    private static double parseZeta(javax.swing.JTextField field, double fallback) {
        try {
            double v = Double.parseDouble(field.getText().trim());
            if (SplanePanel.isDrawableZeta(v)) {
                return v;
            }
        } catch (NumberFormatException ex) {
            // fall through to the fallback
        }
        field.setText(trim(fallback));
        return fallback;
    }

    private static double parseField(javax.swing.JTextField field, double fallback) {
        try {
            double v = Double.parseDouble(field.getText().trim());
            if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                return v;
            }
        } catch (NumberFormatException ex) {
            // fall through to the fallback
        }
        field.setText(trim(fallback));
        return fallback;
    }

    /** The biggest participation in a mode, for reporting an over-tight threshold. */
    private static double largest(List<Participation> rows) {
        double best = 0.0;
        for (Participation p : rows) {
            best = Math.max(best, p.pf);
        }
        return best;
    }

    /** A threshold as a user would write it: no trailing zeros, no exponent. */
    private static String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.6f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text.isEmpty() ? "0" : text;
    }

    /** One left-aligned line of the filter panel, tight against its neighbours. */
    private static JPanel row(Component... parts) {
        JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 1));
        for (Component part : parts) {
            panel.add(part);
        }
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    /** Lines up a continuation row under the tick above it, past the box. */
    private static Component indent() {
        return javax.swing.Box.createHorizontalStrut(
                new JCheckBox().getPreferredSize().width);
    }

    private JPanel header() {
        SsaModes m = results.modes();
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        panel.add(new JLabel(results.directory().getAbsolutePath()
                + "    basename " + results.basename()
                + "    " + m.nstates() + " states, " + m.nalg() + " algebraic"));
        // What the run recorded, not what the window is filtering by: the
        // thresholds beside the table are the reader's and change freely,
        // while these describe the file and cannot.
        StringBuilder line = new StringBuilder();
        if (m.pfFloor() != null) {
            line.append("pf_floor ").append(show(m.pfFloor())).append("    ");
        }
        line.append("gap_tol ").append(show(m.gapTol()))
                .append("    t = ").append(show(m.time()))
                .append("    format v").append(m.formatVersion());
        panel.add(new JLabel(line.toString()));
        return panel;
    }

    private static String show(Double value) {
        return value == null ? "not recorded"
                : String.format(java.util.Locale.ROOT, "%g", value);
    }

    private JPanel withSaveButton(Component plot, String what,
            java.util.function.Supplier<String> svg, Component... extra) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(plot, BorderLayout.CENTER);
        JButton save = new JButton("Save plot...");
        save.addActionListener(event -> saveSvg(what, svg.get()));
        JPanel bar = new JPanel();
        for (Component component : extra) {
            bar.add(component);
        }
        bar.add(save);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private void saveSvg(String what, String svg) {
        JFileChooser chooser = new JFileChooser(results.directory());
        chooser.setDialogTitle("Save " + what + " as SVG");
        chooser.setSelectedFile(new File(results.basename() + "-" + what + ".svg"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
            target = new File(target.getParentFile(), target.getName() + ".svg");
        }
        try {
            Files.write(target.toPath(), svg.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not write " + target + "\n\n" + ex.getMessage(),
                    "Save plot", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectInTable(Mode mode) {
        for (int row = 0; row < table.getRowCount(); row++) {
            int modelRow = table.convertRowIndexToModel(row);
            if (model.rows().get(modelRow).index == mode.index) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private void clearDetail() {
        participation.setText("Select a mode.");
        participation.setCaretPosition(0);
        shape.clear();
    }

    private void showSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            clearDetail();
            return;
        }
        Mode mode = model.rows().get(table.convertRowIndexToModel(row));
        splane.setSelected(mode);
        StringBuilder text = new StringBuilder();

        if (!mode.simple) {
            // Same refusal the dial makes, for the same reason.
            text.append("Mode ").append(mode.index)
                    .append(" is degenerate (simple = 0).\n\n")
                    .append("Its eigenvectors are not unique, so its participation")
                    .append(" factors are basis-dependent and would come out")
                    .append(" differently on another machine. Not shown.\n");
        } else {
            List<Participation> inFile = results.participation().forMode(mode.index);
            List<Participation> rows = new java.util.ArrayList<Participation>();
            for (Participation p : inFile) {
                if (p.pf >= pfThresholdValue) {
                    rows.add(p);
                }
            }
            if (inFile.isEmpty()) {
                // Every mode gets participation now, and normalisation puts
                // one entry at exactly 1 in each, so no mode can fall below
                // pf_floor either. An empty mode is therefore a missing file
                // and not a threshold. _pf.dat is optional to SsaResults.load
                // and the copy-out in StepssUI copies only files that exist,
                // so this state is reachable.
                text.append("No participation rows were written for mode ")
                        .append(mode.index).append(".\n\n")
                        .append("Every mode should have some, so the")
                        .append(" participation file is missing from this")
                        .append(" directory or is incomplete.\n");
            } else if (rows.isEmpty()) {
                // A threshold the reader set, not one the file imposes, so
                // this says which and leaves the fix one field away.
                text.append("Mode ").append(mode.index).append(" has ")
                        .append(inFile.size())
                        .append(inFile.size() == 1 ? " entry" : " entries")
                        .append(", none of them at or above the participation factor")
                        .append(" threshold of ")
                        .append(trim(pfThresholdValue)).append(".\n\n")
                        .append("Its largest is ")
                        .append(String.format(java.util.Locale.ROOT, "%.4f", largest(inFile)))
                        .append(". Lower the threshold beside the table to see it.\n");
            } else {
                text.append(String.format(java.util.Locale.ROOT, "Mode %d, %.4f Hz%n%n",
                        mode.index, mode.freqHz));
                // The columns are named because the last of them is not
                // self-evident. It is named in full, which it can afford to be
                // only because it is last: the header is the widest cell in
                // its column and every row under it is a five-character
                // number, so nothing else shifts to accommodate it. The three
                // columns before it are padded to the width the rows set and
                // could not take the same treatment.
                text.append(String.format(java.util.Locale.ROOT,
                        "  %-8s %-20s %-10s %s%n", "family", "device",
                        "variable", "participation factor"));
                for (Participation p : rows) {
                    // p.device is written as parsed. Columns.slice already
                    // removed the a20 padding, and a LEADING blank is part of
                    // the name the engine stored: trimming it here is what
                    // would make " G2" and "G2" look like the same machine.
                    text.append(String.format(java.util.Locale.ROOT,
                            "  %-8s %-20s %-10s %.3f%n",
                            p.family, p.device, p.variable, p.pf));
                }
                text.append("\nParticipation factors are normalised so the")
                        .append(" largest in each mode is 1.\n");
                if (rows.size() < inFile.size()) {
                    text.append(inFile.size() - rows.size())
                            .append(" more entries are in the file below the")
                            .append(" participation factor threshold of ")
                            .append(trim(pfThresholdValue))
                            .append("; lower it beside the table to see them.\n");
                }
                // The engine's own floor is deliberately NOT mentioned here.
                // It used to be, as two sentences naming pf_floor or the v1
                // pf_threshold, on the reasoning that a device missing because
                // the engine never wrote it is a different absence from one
                // missing because this panel trimmed it. True, but it is not
                // an absence a reader can normally meet: the field beside the
                // table starts at 0.05 and the engine's floor defaults to
                // 0.001, so what is on screen is decided by the field in every
                // ordinary case, and a note about a boundary two decades below
                // it explained a distinction that was never visible. The field
                // is the filter; it says what it is doing, and it is one
                // control away.
            }
        }
        participation.setText(text.toString());
        participation.setCaretPosition(0);
        shape.show(mode.simple
                ? results.shapes().forMode(mode.index)
                : new java.util.ArrayList<ModeShapeEntry>(),
                mode.index, mode.simple);
    }

    /** The modes table. */
    private static final class ModesTableModel extends AbstractTableModel {

        private static final String[] COLUMNS =
                {"#", "f [Hz]", "zeta", "Re", "Im", "simple"};

        private List<Mode> rows;

        ModesTableModel(List<Mode> rows) {
            this.rows = rows;
        }

        List<Mode> rows() {
            return rows;
        }

        void setRows(List<Mode> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : column == 5 ? String.class : Double.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Mode mode = rows.get(row);
            switch (column) {
                case 0: return Integer.valueOf(mode.index);
                case 1: return Double.valueOf(mode.freqHz);
                case 2: return Double.valueOf(mode.zeta);
                case 3: return Double.valueOf(mode.re);
                case 4: return Double.valueOf(mode.im);
                default: return mode.simple ? "yes" : "NO";
            }
        }
    }

    /** Renders negative damping in red, and a degenerate flag in amber. */
    private static final class ModeCellRenderer
            extends javax.swing.table.DefaultTableCellRenderer {

        private final ModesTableModel model;
        private final JTable owner;

        ModeCellRenderer(ModesTableModel model, JTable owner) {
            this.model = model;
            this.owner = owner;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean selected, boolean focused, int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, selected,
                    focused, row, column);
            Mode mode = model.rows().get(owner.convertRowIndexToModel(row));
            // Set unconditionally. One renderer instance serves the Integer,
            // Double and String columns and DefaultTableCellRenderer does not
            // reset alignment per cell, so setting it only in the Double
            // branch leaves a column showing whatever the last cell painted.
            setHorizontalAlignment(value instanceof Double ? RIGHT : LEFT);
            if (value instanceof Double) {
                setText(String.format(java.util.Locale.ROOT, "%+.4f",
                        ((Double) value).doubleValue()));
            }
            if (!selected) {
                boolean dark = PlotStyle.isDark(t.getBackground());
                c.setForeground(mode.zeta < 0.0
                        ? (dark ? UNSTABLE_ON_DARK : UNSTABLE_ON_LIGHT)
                        : !mode.simple
                        ? (dark ? DEGENERATE_ON_DARK : DEGENERATE_ON_LIGHT)
                        : t.getForeground());
            }
            return c;
        }

        // The ordinary rows follow the table's own foreground rather than a
        // hardcoded black, which under a dark theme was black text on a dark
        // ground. The two flag colours cannot follow it, because they carry
        // meaning: crimson is the same crimson the s-plane draws its stability
        // boundary in, so they are lightened for a dark ground rather than
        // dropped. UNSTABLE_ON_DARK is literally PlotStyle's dark crimson, and
        // the two are meant to stay equal.
        //
        // The panels used to stay on white in both themes, on the grounds that
        // PlotStyle was one palette serving both the screen and the SVG and an
        // exported figure belongs on white. The second half of that is still
        // true and the first is not: PlotStyle now carries a light and a dark
        // column, SwingSink takes the theme's and SvgSink always takes the
        // light one, so the export is unchanged and the window no longer has
        // two white rectangles in it.
        private static final java.awt.Color UNSTABLE_ON_LIGHT = new java.awt.Color(0xdc, 0x14, 0x3c);
        private static final java.awt.Color UNSTABLE_ON_DARK = new java.awt.Color(0xff, 0x6b, 0x83);
        private static final java.awt.Color DEGENERATE_ON_LIGHT = new java.awt.Color(0xb0, 0x7d, 0x1a);
        private static final java.awt.Color DEGENERATE_ON_DARK = new java.awt.Color(0xdf, 0xa5, 0x3a);
    }
}
