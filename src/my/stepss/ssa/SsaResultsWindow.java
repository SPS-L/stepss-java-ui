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
        this.model = new ModesTableModel(SsaResults.electromechanical(
                results.modes().modes()));
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

        emOnly.addActionListener(event -> {
            model.setRows(emOnly.isSelected()
                    ? SsaResults.electromechanical(results.modes().modes())
                    : results.modes().modes());
            splane.setModes(model.rows());
            clearDetail();
        });

        JPanel left = new JPanel(new BorderLayout());
        left.add(emOnly, BorderLayout.NORTH);
        left.add(new JScrollPane(table), BorderLayout.CENTER);

        splane.setModes(model.rows());
        splane.addSelectionListener(mode -> selectInTable(mode));

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left,
                withSaveButton(splane, "s-plane", () -> splane.toSvg(
                        Math.max(splane.getWidth(), 500),
                        Math.max(splane.getHeight(), 400))));
        top.setResizeWeight(0.45);

        // A wrapped text area rather than a column of labels. The two sentences
        // a reader most needs here, the degenerate refusal and the note that an
        // absent device is below pf_threshold rather than zero, are long, and
        // in non-wrapping labels they ran off the panel edge and were clipped.
        // Monospaced so the family, device and variable columns line up, and
        // read-only rather than disabled so the numbers stay selectable.
        participation.setEditable(false);
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

    private JPanel header() {
        SsaModes m = results.modes();
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        panel.add(new JLabel(results.directory().getAbsolutePath()
                + "    basename " + results.basename()
                + "    " + m.nstates() + " states, " + m.nalg() + " algebraic"));
        panel.add(new JLabel("real_limit " + show(m.realLimit())
                + "    pf_threshold " + show(m.pfThreshold())
                + "    gap_tol " + show(m.gapTol())
                + "    t = " + show(m.time())));
        return panel;
    }

    private static String show(Double value) {
        return value == null ? "not recorded"
                : String.format(java.util.Locale.ROOT, "%g", value);
    }

    private JPanel withSaveButton(Component plot, String what,
            java.util.function.Supplier<String> svg) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(plot, BorderLayout.CENTER);
        JButton save = new JButton("Save plot...");
        save.addActionListener(event -> saveSvg(what, svg.get()));
        JPanel bar = new JPanel();
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
            List<Participation> rows = results.participation().forMode(mode.index);
            if (rows.isEmpty() && mode.dominant) {
                // The engine's dom flag says this mode passed real_limit, so
                // real_limit is not why the rows are absent. _pf.dat is
                // optional to SsaResults.load and the copy-out in StepssUI
                // copies only files that exist, so the honest report is that
                // the rows are missing, not a filter that did not fire.
                text.append("Mode ").append(mode.index)
                        .append(" was marked dominant by the engine, but no")
                        .append(" participation rows were written for it.\n\n")
                        .append("The participation file may be missing from this")
                        .append(" directory.\n");
            } else if (rows.isEmpty()) {
                text.append("Mode ").append(mode.index)
                        .append(" was filtered out by real_limit (")
                        .append(show(results.modes().realLimit()))
                        .append("), so no participation factors were written.\n");
            } else {
                text.append(String.format(java.util.Locale.ROOT, "Mode %d, %.4f Hz%n%n",
                        mode.index, mode.freqHz));
                for (Participation p : rows) {
                    // p.device is written as parsed. Columns.slice already
                    // removed the a20 padding, and a LEADING blank is part of
                    // the name the engine stored: trimming it here is what
                    // would make " G2" and "G2" look like the same machine.
                    text.append(String.format(java.util.Locale.ROOT,
                            "  %-8s %-20s %-10s %.3f%n",
                            p.family, p.device, p.variable, p.pf));
                }
                text.append("\nEntries below pf_threshold ")
                        .append(show(results.modes().pfThreshold()))
                        .append(" are not written, so an absent device is below")
                        .append(" it, not zero.\n");
            }
        }
        participation.setText(text.toString());
        participation.setCaretPosition(0);
        shape.show(mode.simple
                ? results.shapes().forMode(mode.index)
                : new java.util.ArrayList<ModeShapeEntry>(),
                mode.index, mode.simple, mode.dominant);
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
