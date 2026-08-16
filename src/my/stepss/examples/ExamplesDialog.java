package my.stepss.examples;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

import javax.swing.UIManager;

/**
 * The list behind File -&gt; Open Examples: pick a bundled test system, read
 * what it is, and open it.
 *
 * <p>Master-detail rather than a flat list, modelled on the PowerFactory
 * examples panel: names on the left, the full description on the right. A flat
 * list has room for two cramped lines per entry, which is a filename lottery
 * dressed as a choice; the detail pane has room for the paragraph that makes
 * the choice an informed one.
 *
 * <p>Hand-built rather than a NetBeans form, following {@code LicenseDialog}.
 * The form is for the main window; a dialog whose content is driven by a
 * descriptor has nothing for a designer to lay out.
 *
 * <p>This class chooses nothing and writes nothing. It reports which example
 * the user picked and whether they want the panel at startup, and the caller
 * does the installing: extraction touches the working directory and the
 * preferences, which are {@code StepssUI}'s to manage.
 */
public final class ExamplesDialog {

    /** What the user asked for when the dialog closed. */
    public static final class Choice {

        private final Example example;
        private final boolean showAtStartup;

        Choice(Example example, boolean showAtStartup) {
            this.example = example;
            this.showAtStartup = showAtStartup;
        }

        /** The example to open, or null if the dialog was closed without opening one. */
        public Example example() {
            return example;
        }

        /** Whether the panel should appear at the next startup. */
        public boolean showAtStartup() {
            return showAtStartup;
        }
    }

    private ExamplesDialog() {
    }

    /**
     * Shows the panel and blocks until it is closed.
     *
     * @param parent        the frame to centre on, or null
     * @param examples      the catalogue, in the order to list it
     * @param lockup        the branding lockup for the header, or null for none
     * @param showAtStartup the saved state of the startup checkbox
     * @param onDocs        opens an example's documentation URL
     * @return what the user picked; never null
     */
    public static Choice show(Window parent, final List<Example> examples, Icon lockup,
            boolean showAtStartup, final java.util.function.Consumer<String> onDocs) {

        final JDialog dialog = new JDialog(parent, "Open Examples",
                Dialog.ModalityType.APPLICATION_MODAL);
        final Example[] chosen = {null};

        // ---- header -------------------------------------------------------
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        if (lockup != null) {
            JLabel mark = new JLabel(lockup);
            mark.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            header.add(mark);
            header.add(Box.createVerticalStrut(14));
        }
        JLabel title = new JLabel("Example test systems");
        title.setFont(title.getFont().deriveFont(Font.BOLD,
                title.getFont().getSize2D() + 4f));
        title.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Opening one copies it into your examples directory and fills in the case.");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitle.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        // ---- left: the names ----------------------------------------------
        final JList<Example> list = new JList<>(examples.toArray(new Example[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(examples.size());
        list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane listScroll = new JScrollPane(list,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setPreferredSize(new Dimension(190, 0));

        // ---- right: the detail ---------------------------------------------
        final JLabel detailName = new JLabel();
        detailName.setFont(detailName.getFont().deriveFont(Font.BOLD,
                detailName.getFont().getSize2D() + 2f));
        detailName.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        final JLabel detailScale = new JLabel();
        detailScale.setForeground(UIManager.getColor("Label.disabledForeground"));
        detailScale.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        // A wrapping JTextArea rather than a JLabel with an HTML body. A JLabel
        // has no wrapping width of its own, so the width has to be baked into
        // the HTML as a pixel count - which is a second place the dialog's
        // geometry is written down, free to disagree with the first. This wraps
        // to whatever width it is actually given, and takes the summary as the
        // plain text it is, so an ampersand in a description is an ampersand
        // rather than a swallowed paragraph.
        final JTextArea detailSummary = new JTextArea();
        detailSummary.setEditable(false);
        detailSummary.setFocusable(false);
        detailSummary.setLineWrap(true);
        detailSummary.setWrapStyleWord(true);
        detailSummary.setOpaque(false);
        detailSummary.setBorder(null);
        detailSummary.setFont(UIManager.getFont("Label.font"));
        detailSummary.setForeground(UIManager.getColor("Label.foreground"));

        JScrollPane summaryScroll = new JScrollPane(detailSummary,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        summaryScroll.setBorder(null);
        summaryScroll.setOpaque(false);
        summaryScroll.getViewport().setOpaque(false);

        final JButton open = new JButton("Open example");
        final JButton docs = new JButton("Documentation");
        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        actions.add(Box.createHorizontalGlue());
        actions.add(open);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(docs);

        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        heading.add(detailName);
        heading.add(Box.createVerticalStrut(2));
        heading.add(detailScale);

        // BorderLayout, not BoxLayout down the column. BoxLayout hands a
        // component its preferred height before it reaches the ones below, so a
        // description longer than the pane pushed the two buttons off the bottom
        // of the dialog entirely and clipped its own last line. Here the heading
        // and the buttons take what they need and the description gets the rest,
        // scrolling when there is not enough - so the buttons are always
        // reachable however long a summary a descriptor entry carries.
        JPanel detail = new JPanel(new BorderLayout());
        detail.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 0));
        detail.add(heading, BorderLayout.NORTH);
        detail.add(summaryScroll, BorderLayout.CENTER);
        detail.add(actions, BorderLayout.SOUTH);

        list.addListSelectionListener(event -> {
            Example selected = list.getSelectedValue();
            boolean any = selected != null;
            open.setEnabled(any);
            docs.setEnabled(any);
            detailName.setText(any ? selected.name() : "");
            detailScale.setText(any ? selected.scale() : "");
            detailSummary.setText(any ? selected.summary() : "");
            // Back to the top when the selection changes, or a long description
            // scrolled halfway down leaves the next one opening mid-paragraph.
            detailSummary.setCaretPosition(0);
        });
        list.setSelectedIndex(0);

        open.addActionListener(event -> {
            chosen[0] = list.getSelectedValue();
            dialog.dispose();
        });
        docs.addActionListener(event -> {
            Example selected = list.getSelectedValue();
            if (selected != null && onDocs != null) {
                onDocs.accept(selected.docs());
            }
        });

        JPanel middle = new JPanel(new BorderLayout());
        middle.setBorder(BorderFactory.createEmptyBorder(0, 24, 8, 24));
        middle.add(listScroll, BorderLayout.WEST);
        middle.add(detail, BorderLayout.CENTER);

        // ---- bottom --------------------------------------------------------
        final JCheckBox atStartup = new JCheckBox("Show this at startup", showAtStartup);
        JButton close = new JButton("Close");
        close.addActionListener(event -> dialog.dispose());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 24, 18, 24));
        bottom.add(atStartup, BorderLayout.WEST);
        bottom.add(close, BorderLayout.EAST);

        dialog.getContentPane().add(header, BorderLayout.NORTH);
        dialog.getContentPane().add(middle, BorderLayout.CENTER);
        dialog.getContentPane().add(bottom, BorderLayout.SOUTH);

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        // Escape closes without opening anything: chosen[0] is only ever set by
        // the Open button, so every other way out of this dialog is a decline.
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JPanel.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().setDefaultButton(open);

        // Sized so the longest of the three descriptions fits without scrolling
        // at the default font. The scroll pane is what makes a longer one safe
        // rather than what the common case relies on.
        dialog.setSize(760, 620);
        dialog.setMinimumSize(new Dimension(560, 420));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return new Choice(chosen[0], atStartup.isSelected());
    }
}
