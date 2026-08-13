package my.ramses.ssa;

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
import javax.swing.BoxLayout;
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
    private final JPanel participation = new JPanel();
    private final JCheckBox emOnly =
            new JCheckBox("electromechanical only (0.1 to 2.5 Hz)", true);

    public static void open(Component parent, SsaResults results) {
        SsaResultsWindow window = new SsaResultsWindow(results);
        window.setLocationRelativeTo(parent);
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

        participation.setLayout(new BoxLayout(participation, BoxLayout.Y_AXIS));
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
        participation.removeAll();
        participation.add(new JLabel("  Select a mode."));
        participation.revalidate();
        participation.repaint();
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
        participation.removeAll();

        if (!mode.simple) {
            // Same refusal the dial makes, for the same reason.
            participation.add(new JLabel("  Mode " + mode.index
                    + " is degenerate (simple = 0)."));
            participation.add(new JLabel("  Its eigenvectors are not unique, so its"
                    + " participation factors are basis-dependent"));
            participation.add(new JLabel("  and would come out differently on another"
                    + " machine. Not shown."));
        } else {
            List<Participation> rows = results.participation().forMode(mode.index);
            if (rows.isEmpty() && mode.dominant) {
                // The engine's dom flag says this mode passed real_limit, so
                // real_limit is not why the rows are absent. _pf.dat is
                // optional to SsaResults.load and the copy-out in RamsesUI
                // copies only files that exist, so the honest report is that
                // the rows are missing, not a filter that did not fire.
                participation.add(new JLabel("  Mode " + mode.index
                        + " was marked dominant by the engine, but no"
                        + " participation rows were written for it."));
                participation.add(new JLabel("  The participation file may be"
                        + " missing from this directory."));
            } else if (rows.isEmpty()) {
                participation.add(new JLabel("  Mode " + mode.index
                        + " was filtered out by real_limit ("
                        + show(results.modes().realLimit())
                        + "), so no participation factors were written."));
            } else {
                participation.add(new JLabel("  Mode " + mode.index + ", "
                        + String.format(java.util.Locale.ROOT, "%.4f Hz", mode.freqHz)));
                for (Participation p : rows) {
                    // p.device is shown as parsed. Columns.slice already
                    // removed the a20 padding, and a LEADING blank is part of
                    // the name the engine stored: trimming it here is what
                    // makes " G2" and "G2" look like the same machine.
                    participation.add(new JLabel(String.format(java.util.Locale.ROOT,
                            "   %-8s %-20s %-10s %.3f",
                            p.family, p.device, p.variable, p.pf)));
                }
                participation.add(new JLabel("  Entries below pf_threshold "
                        + show(results.modes().pfThreshold())
                        + " are not written, so an absent device is below it,"
                        + " not zero."));
            }
        }
        participation.revalidate();
        participation.repaint();
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
            if (value instanceof Double) {
                setText(String.format(java.util.Locale.ROOT, "%+.4f",
                        ((Double) value).doubleValue()));
                setHorizontalAlignment(RIGHT);
            }
            if (!selected) {
                c.setForeground(mode.zeta < 0.0 ? new java.awt.Color(0xdc, 0x14, 0x3c)
                        : !mode.simple ? new java.awt.Color(0xb0, 0x7d, 0x1a)
                        : java.awt.Color.BLACK);
            }
            return c;
        }
    }
}
