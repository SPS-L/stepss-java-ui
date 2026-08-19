package my.stepss.curves;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * One extraction's curves, displayed. Non-modal and independent, so several
 * can be open at once: comparing two extractions means putting two of these
 * side by side, and each holds its own parsed copy, so a later extraction
 * overwriting the files on disk leaves the earlier window intact.
 *
 * <p>The same arrangement, and the same reasoning, as
 * {@code SsaResultsWindow}.
 */
public final class CurveWindow extends JFrame {

    private final CurveData data;
    private final CurvePanel panel = new CurvePanel();

    /** Opens one extraction in a window of its own. Every call makes a new one. */
    public static CurveWindow open(Component parent, CurveData data, String title) {
        CurveWindow window = new CurveWindow(data, title);
        my.stepss.WindowCascade.track(window, parent);
        window.setVisible(true);
        return window;
    }

    public CurveWindow(CurveData data, String title) {
        super(title);
        this.data = data;
        this.panel.setData(data);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(toolbar(), BorderLayout.SOUTH);
        pack();
    }

    private JPanel header() {
        JPanel head = new JPanel(new BorderLayout());
        head.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        int samples = data.series.isEmpty() ? 0 : data.series.get(0).v.length;
        StringBuilder text = new StringBuilder();
        text.append(data.series.size()).append(" curve(s), ")
                .append(samples).append(" samples");
        if (data.source != null) {
            text.append("    ").append(data.source.getAbsolutePath());
        }
        // Said once, where a malformed file is explained rather than silently
        // shortening every curve.
        if (data.skippedRows > 0) {
            text.append("    ").append(data.skippedRows)
                    .append(" unreadable row(s) skipped");
        }
        head.add(new JLabel(text.toString()), BorderLayout.WEST);
        return head;
    }

    private JPanel toolbar() {
        JPanel bar = new JPanel();
        JButton png = new JButton("Save PNG...");
        png.addActionListener(event -> savePng());
        JButton svg = new JButton("Save SVG...");
        svg.addActionListener(event -> saveText("svg", "SVG",
                panel.toSvg(exportWidth(), exportHeight())));
        JButton csv = new JButton("Save CSV...");
        csv.addActionListener(event -> saveText("csv", "CSV", toCsv(data)));
        JButton pair = new JButton("Save gnuplot pair...");
        pair.setToolTipText("Saves this extraction's .cur and .plt, for plotting"
                + " it in gnuplot yourself.");
        pair.addActionListener(event -> saveGnuplotPair());
        pair.setEnabled(data.source != null && data.source.isFile());
        JButton reset = new JButton("Reset zoom");
        reset.addActionListener(event -> panel.resetZoom());
        bar.add(png);
        bar.add(svg);
        bar.add(csv);
        bar.add(pair);
        bar.add(reset);
        return bar;
    }

    /**
     * Copies this window's own {@code .cur} and {@code .plt} to a chosen name.
     *
     * <p>Per window, not per session, which is the whole reason it lives here.
     * The Analysis tab used to carry this action and act on whichever
     * extraction was most recent, so opening an older window and pressing Save
     * wrote the newest extraction's data under the older one's chosen name.
     * A window knows its own {@code .cur}, because it parsed it, so there is
     * nothing to get wrong.
     *
     * <p>Neither original is modified or removed. The previous implementation
     * rewrote the source {@code .plt} in place before copying it and then
     * deleted the source {@code .cur}, so a second save of the same extraction
     * wrote a {@code .plt} pointing at a {@code .cur} that no longer existed.
     */
    private void saveGnuplotPair() {
        File cur = data.source;
        if (cur == null || !cur.isFile()) {
            return;
        }
        String base = cur.getName().endsWith(".cur")
                ? cur.getName().substring(0, cur.getName().length() - 4)
                : cur.getName();
        File plt = new File(cur.getParentFile(), base + ".plt");
        File target = chooseTarget("cur", "gnuplot pair");
        if (target == null) {
            return;
        }
        String targetBase = target.getName().substring(0, target.getName().length() - 4);
        try {
            Files.copy(cur.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (plt.isFile()) {
                // The .plt names its .cur by path, so the copy has to name the
                // copy. Rewritten on the way out rather than in place: the
                // original stays valid for the window that is still showing it.
                String script = new String(Files.readAllBytes(plt.toPath()),
                        StandardCharsets.ISO_8859_1);
                script = script.replace(cur.getAbsolutePath(), targetBase + ".cur")
                        .replace(cur.getName(), targetBase + ".cur");
                Files.write(new File(target.getParentFile(), targetBase + ".plt").toPath(),
                        script.getBytes(StandardCharsets.ISO_8859_1));
            }
        } catch (IOException ex) {
            failed(target, ex);
        }
    }

    private int exportWidth() {
        return Math.max(panel.getWidth(), 800);
    }

    private int exportHeight() {
        return Math.max(panel.getHeight(), 500);
    }

    private void savePng() {
        File target = chooseTarget("png", "PNG");
        if (target == null) {
            return;
        }
        try {
            javax.imageio.ImageIO.write(
                    panel.toPng(exportWidth(), exportHeight()), "png", target);
        } catch (IOException ex) {
            failed(target, ex);
        }
    }

    private void saveText(String extension, String what, String content) {
        File target = chooseTarget(extension, what);
        if (target == null) {
            return;
        }
        try {
            Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            failed(target, ex);
        }
    }

    private File chooseTarget(String extension, String what) {
        JFileChooser chooser = new JFileChooser(
                data.source == null ? null : data.source.getParentFile());
        chooser.setDialogTitle("Save curves as " + what);
        chooser.setSelectedFile(new File("curves." + extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            target = new File(target.getParentFile(), target.getName() + "." + extension);
        }
        return target;
    }

    private void failed(File target, IOException ex) {
        JOptionPane.showMessageDialog(this,
                "Could not write " + target + "\n\n" + ex.getMessage(),
                "Save curves", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * The data as CSV: one header row of labels, then one row per sample.
     *
     * <p>Labels are quoted because DYNGRAPH instance names can contain a
     * comma, and an unquoted one would silently split a column.
     */
    public static String toCsv(CurveData data) {
        StringBuilder out = new StringBuilder();
        out.append("t (s)");
        for (CurveSeries one : data.series) {
            out.append(",\"").append(one.label.replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
        if (data.series.isEmpty()) {
            return out.toString();
        }
        double[] t = data.series.get(0).t;
        for (int r = 0; r < t.length; r++) {
            out.append(trim(t[r]));
            for (CurveSeries one : data.series) {
                out.append(',').append(trim(one.v[r]));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Full precision without an exponent where one is not needed. */
    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.10g", value).trim();
        if (text.contains(".") && !text.contains("e") && !text.contains("E")) {
            text = text.replaceAll("0+$", "");
            if (text.endsWith(".")) {
                text = text + "0";
            }
        }
        return text;
    }
}
