package my.stepss.diagram;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import my.stepss.HeliosOutcome;
import my.stepss.WindowCascade;

/**
 * One power flow run's one-line diagram, displayed.
 *
 * <p>Non-modal and independent, so several can be open at once: pressing Run
 * Power Flow twice is nearly always done to compare the two, and each window
 * holds its own parsed copy of the drawing, so the next run overwriting
 * {@code in_diagram.svg} leaves the earlier window intact. That is the same
 * arrangement {@code SsaResultsWindow} makes, and for the same reason.
 *
 * <p>The banner is rendered from {@link HeliosOutcome}, which is also what the
 * modal dialog and the status bar phrasing come from. A run that did not
 * converge still gets a window, showing the template as it was, because a
 * failure the user cannot see the shape of is harder to diagnose than one they
 * can.
 */
public final class DiagramWindow extends JFrame {

    private final DiagramPanel panel;

    /**
     * Opens one run's diagram in a window of its own.
     *
     * @param parent    what to place it relative to
     * @param svg       the file to show: the annotated result, or the template
     *                  when the run produced nothing
     * @param caseName  the template's file name, for the title
     * @param runNumber which run of this session this is
     * @param outcome   what to say about the run, or null to say nothing
     */
    public static void open(Component parent, File svg, String caseName,
            int runNumber, HeliosOutcome outcome) throws IOException {
        DiagramWindow window = new DiagramWindow(svg, caseName, runNumber, outcome);
        window.pack();
        WindowCascade.track(window, parent);
        window.setVisible(true);
    }

    private DiagramWindow(File svg, String caseName, int runNumber,
            HeliosOutcome outcome) throws IOException {
        super("One-line diagram - " + caseName + " - run " + runNumber + ", "
                + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        panel = new DiagramPanel(SvgImage.load(svg));

        if (outcome != null && outcome.severity() != HeliosOutcome.Severity.OK) {
            add(banner(outcome), BorderLayout.NORTH);
        }
        add(panel, BorderLayout.CENTER);
        add(toolbar(), BorderLayout.SOUTH);
    }

    /**
     * The status strip above the drawing.
     *
     * <p>Coloured rather than merely worded, because the thing it guards
     * against is a user reading numbers off a diagram that is not a solution.
     */
    private static JPanel banner(HeliosOutcome outcome) {
        boolean error = outcome.severity() == HeliosOutcome.Severity.ERROR;
        Color ink = error ? new Color(0xB3261E) : new Color(0x8A5300);

        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.PAGE_AXIS));
        strip.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel headline = new JLabel(outcome.headline());
        headline.setForeground(ink);
        headline.setFont(headline.getFont().deriveFont(java.awt.Font.BOLD));
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        strip.add(headline);

        if (!outcome.detail().isEmpty()) {
            JLabel detail = new JLabel("<html><body style='width: 640px'>"
                    + outcome.detail() + "</body></html>");
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            strip.add(Box.createVerticalStrut(4));
            strip.add(detail);
        }
        return strip;
    }

    private JPanel toolbar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.LINE_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        bar.add(button("Fit", panel::fit));
        bar.add(Box.createRigidArea(new java.awt.Dimension(6, 0)));
        bar.add(button("Zoom in", () -> panel.zoomBy(1.25)));
        bar.add(Box.createRigidArea(new java.awt.Dimension(6, 0)));
        bar.add(button("Zoom out", () -> panel.zoomBy(0.8)));
        bar.add(Box.createHorizontalGlue());
        bar.add(button("Save as PNG...", this::savePng));
        bar.add(Box.createRigidArea(new java.awt.Dimension(6, 0)));
        bar.add(button("Save as SVG...", this::saveSvg));
        return bar;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(event -> action.run());
        return b;
    }

    /**
     * Saves the whole drawing, not what is on screen.
     *
     * <p>A saved figure goes into a report, so zoom is a reading aid here and
     * not a crop tool: a user who zoomed in to check one number and then saved
     * would otherwise get that number and nothing else.
     */
    private void savePng() {
        File target = chooseTarget("Save diagram as PNG", "diagram.png");
        if (target == null) {
            return;
        }
        try {
            javax.imageio.ImageIO.write(panel.image().renderWhole(2400), "png", target);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save " + target.getName() + "\n\n" + ex.getMessage(),
                    "Diagram not saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveSvg() {
        File target = chooseTarget("Save diagram as SVG", "diagram.svg");
        if (target == null) {
            return;
        }
        try {
            panel.image().copyTo(target);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save " + target.getName() + "\n\n" + ex.getMessage(),
                    "Diagram not saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File chooseTarget(String title, String suggested) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new File(suggested));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File target = chooser.getSelectedFile();
        if (target.exists() && JOptionPane.showConfirmDialog(this,
                target.getName() + " already exists. Replace it?",
                "Replace file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }
        return target;
    }

    static {
        // Nothing here depends on the look and feel being installed, but the
        // window is opened from a background thread's invokeLater and a missing
        // UIManager default would surface there rather than at startup.
        UIManager.getDefaults();
    }
}
