/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * StepssUI.java
 *
 * Created on Dec 3, 2011, 10:17:03 AM
 */
package my.stepss;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.SplashScreen;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.Utilities;
import my.stepss.compile.ModelCompiler;
import my.stepss.config.Scenario;
import my.stepss.config.ScenarioBinding;
import my.stepss.config.ScenarioFile;
import my.stepss.dyngraph.DyngraphRunner;
import my.stepss.dyngraph.ObservableIndex;
import my.stepss.dyngraph.ObservablePicker;
import my.stepss.dyngraph.ReplayFile;
import my.stepss.dyngraph.Selection;
import my.stepss.examples.Example;
import my.stepss.examples.ExampleCatalog;
import my.stepss.examples.ExampleInstaller;
import my.stepss.examples.ExamplesDialog;
import my.stepss.platform.Platform;
import my.stepss.platform.PlatformLauncher;
import my.stepss.platform.Toolchain;
import my.stepss.platform.UnsupportedPlatformException;
import org.apache.commons.exec.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;

/**
 *
 * @author p3tris
 */
public class StepssUI extends javax.swing.JFrame {

    /**
     * The one observable type that names no equipment, so it is written even
     * with the name field blank and its line is "RT RT" rather than the
     * keyword followed by whatever the field holds.
     */
    private static final String WALL_TIME = "Wall Time";

    /**
     * Every runtime observable type, in the order the Observables tab lists
     * them, mapped to the keyword RAMSES reads for it in the command file.
     *
     * <p>Read from both ends: {@link #fillObservableTypes()} builds the three
     * dropdowns out of the labels, {@link #writeObservable} turns the label
     * the user picked back into its keyword. That used to be six independent
     * copies of the same twelve strings, the array literal generated once per
     * dropdown into {@code initComponents()} and the labels again as the cases
     * of three copied switches, which is how "Center of Intertia" and "Branch
     * Rective Power" lasted as long as they did.
     *
     * <p>The duplication was worse than the typos it hid. The two halves had
     * to agree character for character, and a label corrected in a dropdown
     * but not in its switch matches no case, so the row falls through to the
     * failure return and the observable is dropped rather than plotted.
     */
    private static final Map<String, String> OBSERVABLE_TYPES = observableTypes();

    private static Map<String, String> observableTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("Bus Voltage", "BV");
        types.put("Machine Speed", "MS");
        types.put("Omega-delta of machine", "o-d");
        types.put("Active power-delta of machine", "P-d");
        types.put("Center of Inertia", "COI");
        types.put(WALL_TIME, "RT");
        types.put("Latency", "LAT");
        types.put("Branch Active Power Origin", "BPO");
        types.put("Branch Active Power Extremity", "BPE");
        types.put("Branch Reactive Power Origin", "BQO");
        types.put("Branch Reactive Power Extremity", "BQE");
        types.put("Injector Observable", "ON");
        return Collections.unmodifiableMap(types);
    }

    private Rectangle pos;

    /**
     * The startup card to report extraction progress on, or null when this
     * launch has none: a first run, where the card was closed to make way for
     * the licence, or a JVM started without one, as an IDE does.
     */
    private final Splash splash;

    /**
     * The controls Save and Load configuration persist, named once.
     *
     * <p>Field references, handed over at construction. Both handlers used to
     * find their controls by walking {@code jPanel2} and {@code jPanel4} for
     * direct children and matching on {@code getName()}, which tied the file
     * format to the layout: {@code applyModernChrome()} reparents every one of
     * those controls into a scroll pane or a wrapper panel, so from the day it
     * arrived the walk matched none of the twenty-four and Save configuration
     * wrote a file holding nothing but a date comment. Naming the set is what
     * makes the next relayout a non-event.
     */
    private final ScenarioBinding scenarioBinding;

    /**
     * Creates new form StepssUI
     */
    public StepssUI() {
        this(null);
    }

    /**
     * Creates new form StepssUI, reporting startup progress on a splash.
     *
     * @param splash the card {@code main} opened, or null for no reporting
     */
    public StepssUI(Splash splash) {
        this.splash = splash;
        initComponents();
        fillObservableTypes();
        scenarioBinding = bindScenario();
        applyModernChrome();
        // PlatformLauncher's launches (editor/terminal/file manager) run via
        // Commons Exec's async execute(), which hands a launch failure (e.g.
        // a missing xdg-open or terminal emulator) to this listener instead
        // of throwing it back to the caller. Without a listener registered,
        // such a failure is silent. Registered once, here, for the process
        // lifetime; called back off the EDT, so it hops back with invokeLater.
        PlatformLauncher.setLaunchFailureListener((description, cause) -> {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Could not " + description + ".\n\n" + cause.getMessage(),
                    "Launch failed", JOptionPane.ERROR_MESSAGE));
        });
        this_version = getVersion();
        versionLabel.setText("<html><b>Version:</b> " + this_version + "</html>");
        prefs = preferences();
        // Before initRamses(), which is what reads it: the session's working
        // directory is restored only if it is still a directory, so a case on
        // a disconnected drive comes up as no directory rather than an error.
        selWorkDir = lastWorkingDirectory();
        if (prefs.getBoolean(FIRST_RUN, true)) {
            // Nothing is shown here. main() shows the licence before this
            // constructor runs and exits if it is declined, so reaching this
            // line means it was accepted. All that is left is to record that
            // the flag has been dealt with, so a later launch does not treat
            // this one as the first.
            prefs.putBoolean(FIRST_RUN, false);
        }

//        KeyStroke ctrlGKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_G,InputEvent.CTRL_DOWN_MASK);
//        jPanel1.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlGKeyStroke, "KILLGP");
//        jPanel1.getActionMap().put("KILLGP", new AbstractAction() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                System.out.println("Killing Gnuplot");
//                clearGnuplotButtonActionPerformed(e);
//            }
//        });

        ToolTipManager.sharedInstance().setDismissDelay(6000000);
        ToolTipManager.sharedInstance().setEnabled(false);
        DefaultCaret caret = (DefaultCaret) simulationOutput.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        dataFileList.add(fileData1);
        dataFileList.add(fileData2);
        dataFileList.add(fileData3);
        dataFileList.add(fileData4);
        dataFileList.add(fileData5);
        dataFileList.add(fileData6);
        dataFileList.add(fileData7);
        dataFileList.add(fileData8);
        dataFileList.add(fileData9);
        dataFileList.add(fileData10);
        try {
            outputstream = new TextareaOutputStream(simulationOutput);
            outputstreamCG = new TextareaOutputStream(codegenPane);
            outputstreamPFC = new TextareaOutputStream(pfcPane);
            // A second sink per pane, for the child's stderr. Commons Exec
            // pumps stdout and stderr on separate threads, so passing one
            // sink to both put two threads on a single line buffer and wove
            // their characters together mid-word. Each stream buffers its
            // own partial line now; they share the text area safely because
            // appends are marshalled onto the EDT.
            outputstreamErr = new TextareaOutputStream(simulationOutput);
            outputstreamCGErr = new TextareaOutputStream(codegenPane);
            outputstreamPFCErr = new TextareaOutputStream(pfcPane);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (initRamses()) {
        } else {
            // initRamses() already showed a dialog naming the specific tool
            // and path that failed; a second, generic dialog here would just
            // repeat the failure without adding information.
            System.exit(1);
        }

        // Not from here, and not from main either: from the window actually
        // appearing. This constructor stopped being the end of startup when
        // the reveal timer arrived, and a check started here raises a banner
        // into a frame that is still up to three seconds from being shown.
        // WINDOW_OPENED is delivered once, the first time the frame is made
        // visible, whoever made it visible, so no construction path loses the
        // check and no path can run it twice. See checkForUpdatesAtStartup.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                checkForUpdatesAtStartup();
                showExamplesAtStartup();
            }
        });
    }

    /**
     * Everything about the window's chrome that the NetBeans form cannot
     * express, applied once, immediately after {@code initComponents()}.
     *
     * <p>Lives here rather than in the generated block on purpose. The form is
     * still the source of truth for what exists and where it sits; this is the
     * source of truth for how it is presented and what it is bound to. Reopening
     * StepssUI.form in the designer and letting it regenerate cannot undo any of
     * it, because all of it runs afterwards.
     */
    private void applyModernChrome() {
        // FlatLaf embeds the menu bar into the title bar by default wherever it
        // draws one. Refused here: File, Tools and Help stay where a decade of
        // users expect to find them.
        getRootPane().putClientProperty("JRootPane.menuBarEmbedded", false);
        MenuShortcuts.applyPlatformModifier(menuBar);
        fileMenu.setMnemonic(KeyEvent.VK_F);
        toolsMenu.setMnemonic(KeyEvent.VK_T);
        helpMenu.setMnemonic(KeyEvent.VK_H);
        styleEditButtons();
        styleConsoles();
        addThemeToggle();
        addUpdateToggle();
        applyBranding();
        layoutTabs();
        addStatusBar();
        expireTheBannerOnEveryAction();
        restoreSession();
    }

    /**
     * Ends the banner's message when the user starts doing something else.
     *
     * <p>The fault this fixes: a banner had no lifetime at all, so "No system
     * data files are loaded" was still sitting above the tabs after the files
     * had been loaded and the simulation had run. The status bar said the run
     * had finished while the banner said it could not start.
     *
     * <p>One listener on the event queue rather than one per control, and it
     * watches presses rather than actions. A press strictly precedes the
     * action event a click generates, so the banner is always told the gesture
     * has begun <em>before</em> the handler for it gets the chance to raise a
     * new message. Hanging listeners off each button instead makes the outcome
     * depend on the order Swing happens to call two listeners on the same
     * button in, which is undocumented, and getting it backwards means every
     * warning is cleared on its way out of the click that raised it, so none
     * of them is ever seen. That is not a thing to leave to luck, and an
     * attempt at it here did get it backwards.
     */
    private void expireTheBannerOnEveryAction() {
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED
                    || event.getID() == KeyEvent.KEY_PRESSED) {
                banner.newAction();
            }
        }, java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.KEY_EVENT_MASK);
    }


    /**
     * Puts the status bar along the bottom of the frame, below the tabs rather
     * than inside any of them, because what it reports is true of the
     * application rather than of whichever tab happens to be open.
     */
    private void addStatusBar() {
        // The content pane carries a GroupLayout holding jPanel1 alone, which
        // has no room in it for a second child. Replaced wholesale, the same
        // way each tab's layout is, rather than edited.
        java.awt.Container content = getContentPane();
        content.removeAll();
        content.setLayout(new BorderLayout());
        content.add(banner, BorderLayout.NORTH);
        content.add(jPanel1, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
    }

    /**
     * Replaces each tab's layout, leaving the form owning which controls exist.
     *
     * <p>The generated GroupLayout packed everything to the top left and let go:
     * on a maximised window the System Data tab used the top 400px and left
     * three quarters of the screen grey, with Clear Files stranded at the bottom
     * on its own, about 1,250px from the rows it clears. Only the console panes
     * grew, because only they were given a resize weight.
     *
     * <p>Every tab is BorderLayout now: what you set at the top, what you read
     * in the middle carrying the weight, what you do along the bottom. That is
     * the shape SsaResultsWindow already had, and the reason it feels current
     * beside the rest.
     *
     * <p>Done here rather than in the designer because there is no NetBeans on
     * this machine and hand-editing 196KB of generated layout across six tabs is
     * how a week becomes a month. Setting a new layout discards the generated
     * one along with its groups; the components are the panel's children either
     * way, so they survive. No control is added, removed or reordered: this
     * moves them, and the sequence a study runs in is untouched.
     */
    private void layoutTabs() {
        layoutInitializationTab();
        layoutDynamicSimulationTab();
        layoutCodegenTab();
        layoutSystemDataTab();
        layoutObservablesTab();
        layoutAnalysisTab();
    }

    /**
     * Observables, regrouped as well as relaid.
     *
     * <p>The grouping was saying something false. Save Continuous trace, Save
     * Discrete trace and the plot refresh interval each sat on the same
     * baseline as one of the three runtime observable rows, which have nothing
     * to do with them; proximity is the strongest grouping signal an interface
     * has, and this one was pointing at the wrong things. They are three
     * blocks now: what to plot while the run goes, what to write to file, and
     * which observables file to use.
     *
     * <p>jPanel7, the inline picker the wizard checkbox reveals, keeps its own
     * layout and takes the centre, so it has room when it is shown and costs
     * nothing while it is not.
     *
     * <p>The four recording checkboxes share one row. They are four independent
     * toggles of equal weight, so the 2 x 2 they used to sit in was claiming a
     * pairing that is not there, and the second row was far wider than the
     * first. Fitting them on one line is what shortened the last label:
     * "Save settings, comments and initialization data" measured 303px on its
     * own and put the row at 779px, and {@code leftRow} is a FlowLayout, which
     * wraps of its own accord once the row is wider than the panel. The wrap is
     * not merely ugly. This panel sits in BorderLayout.NORTH, so its height is
     * fixed at the one row GridBagLayout asked for, and whatever wraps onto a
     * second line is drawn outside that height and clipped away entirely: below
     * about 780px the fourth checkbox simply was not there. "Save initialization
     * data" brings the row to 632px, under the 679px the Analysis tab's SSA row
     * already needs and far under the 1,355px its own action bar does, so the
     * row cannot be the thing that runs out of width first. Lengthening any of
     * the four labels again is what would undo that, and the detail belongs in
     * the tooltip, which is where it went.
     */
    private void layoutObservablesTab() {
        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        int row = 0;
        settings.add(heading(jLabel30), span(row++));
        settings.add(observableRow(runtimeObsType, runtimeObsName), stretch(row++));
        settings.add(observableRow(runtimeObsType1, runtimeObsName1), stretch(row++));
        settings.add(observableRow(runtimeObsType2, runtimeObsName2), stretch(row++));

        settings.add(heading(new JLabel("Recording to file")), span(row++));
        settings.add(leftRow(saveContTrace, saveDiscTrace, saveOutputTrajButton,
                saveDumpButton), span(row++));

        settings.add(heading(jLabel10), span(row++));
        settings.add(fileRow("", loadObsButton, fileObs, nppObsButton), stretch(row++));
        settings.add(leftRow(observFileWizButton), span(row++));

        jPanel4.removeAll();
        jPanel4.setLayout(new BorderLayout());
        jPanel4.add(settings, BorderLayout.NORTH);
        jPanel4.add(jPanel7, BorderLayout.CENTER);
        jPanel4.add(ActionBar.create().toTheEnd().add(clearObsFileButton).build(),
                BorderLayout.SOUTH);
    }

    /**
     * Analysis, which already had section headings and is the tab the rest of
     * this pass is trying to catch up with. It keeps them, and gains a bar per
     * section instead of buttons wrapped two wide then three then one.
     *
     * <p>Two actions are marked rather than one, against the rule the other
     * tabs follow. The tab carries two independent tools, and marking neither
     * would leave the tab with no answer to what it is for while marking one
     * would claim the other is secondary, which it is not.
     */
    private void layoutAnalysisTab() {
        ActionBar.markPrimary(runDyngraphButton);
        ActionBar.markPrimary(ssaButton1);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        int row = 0;
        content.add(heading(jLabel4), span(row++));
        content.add(ActionBar.create()
                .add(runDyngraphButton)
                .add(viewCurvesButton)
                .add(saveCurrentCurveButton)
                .separate()
                .add(saveTrajToFileButton)
                .add(loadTrajToFileButton)
                .toTheEnd()
                .add(clearGnuplotButton)
                .build(), stretch(row++));

        content.add(heading(jLabel8), span(row++));
        content.add(pathRow(loadSSADir, ssaDirectory), stretch(row++));
        content.add(leftRow(ssaBasenameLabel, ssaBasename, ssaTimeLabel, ssaTime,
                ssaRealLimitLabel, ssaRealLimit, ssaPfThresholdLabel, ssaPfThreshold),
                span(row++));
        content.add(ssaEngineNote, span(row++));
        content.add(ActionBar.create()
                .add(ssaButton1)
                .separate()
                .add(saveDynJac)
                .add(loadDynJac)
                .build(), stretch(row++));

        jPanel8.removeAll();
        jPanel8.setLayout(new BorderLayout());
        jPanel8.add(content, BorderLayout.NORTH);
    }

    /** A chooser and the path it fills, the path taking the width. */
    private static JPanel pathRow(JButton chooser, JTextField path) {
        JPanel line = new JPanel(new BorderLayout(6, 0));
        line.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        line.add(chooser, BorderLayout.WEST);
        line.add(path, BorderLayout.CENTER);
        return line;
    }

    /**
     * A section heading: bold, with a rule under it to carry the width.
     *
     * <p>A label that already carries HTML is left to it. Those use it to bold
     * a file's name inside a longer line, and deriving a bold font over the top
     * would make the whole line shout instead.
     */
    private static JPanel heading(JLabel label) {
        if (!label.getText().toLowerCase(java.util.Locale.ROOT).startsWith("<html")) {
            label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        }
        JPanel block = new JPanel(new BorderLayout(0, 3));
        block.setBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0));
        block.add(label, BorderLayout.NORTH);
        block.add(new javax.swing.JSeparator(), BorderLayout.CENTER);
        return block;
    }

    /**
     * Fills the three runtime observable dropdowns from
     * {@link #OBSERVABLE_TYPES}.
     *
     * <p>The dropdowns carry no model of their own in {@code StepssUI.form}
     * any more, so this is the only thing that puts anything in them. Letting
     * the form own the list stored the twelve labels three times over in the
     * form and generated them three times into {@code initComponents()}, in a
     * block a person is not supposed to edit, where the code that has to
     * recognise those exact labels could not reach them.
     *
     * <p>One array for the three models is safe: {@code DefaultComboBoxModel}
     * copies the items it is handed rather than keeping the array.
     */
    private void fillObservableTypes() {
        String[] labels = OBSERVABLE_TYPES.keySet().toArray(new String[0]);
        runtimeObsType.setModel(new DefaultComboBoxModel(labels));
        runtimeObsType1.setModel(new DefaultComboBoxModel(labels));
        runtimeObsType2.setModel(new DefaultComboBoxModel(labels));
    }

    /**
     * The one place that says which controls a saved scenario is made of.
     *
     * <p>The set is not a judgement call: it is exactly what
     * {@link #createCommandFile()} and {@link #createPFCCommandFile()} read to
     * write {@code cmd.txt} and {@code PFCcmd.txt}, so a scenario that
     * round-trips is a run that reproduces. Adding a control to either writer
     * without adding it here is how a scenario silently stops describing the
     * run it came from, and this call is the only thing to keep in step.
     *
     * <p>Runs before {@code applyModernChrome()} and would work just as well
     * after it. That is the point.
     */
    private ScenarioBinding bindScenario() {
        return new ScenarioBinding(
                new JTextField[]{fileData1, fileData2, fileData3, fileData4, fileData5,
                    fileData6, fileData7, fileData8, fileData9, fileData10},
                fileDist, fileObs, observFileWizButton,
                new JComboBox<?>[]{runtimeObsType, runtimeObsType1, runtimeObsType2},
                new JTextField[]{runtimeObsName, runtimeObsName1, runtimeObsName2},
                saveOutputTrajButton, saveContTrace, saveDiscTrace, saveDumpButton);
    }

    /** One runtime observable: what kind, and which one. */
    private static JPanel observableRow(JComboBox<?> type, JTextField name) {
        JPanel line = new JPanel(new BorderLayout(6, 0));
        line.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        type.setPreferredSize(new Dimension(190, type.getPreferredSize().height));
        line.add(type, BorderLayout.WEST);
        line.add(name, BorderLayout.CENTER);
        return line;
    }

    /** Controls in a row at their own width, left aligned. */
    private static JPanel leftRow(JComponent... controls) {
        JPanel line = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        for (JComponent control : controls) {
            line.add(control);
        }
        return line;
    }

    /** Console in the middle, run and inspect along the bottom. */
    private void layoutInitializationTab() {
        ActionBar.markPrimary(runPF);
        JPanel bar = ActionBar.create()
                .add(runPF)
                .add(loadLFRESV2DAT)
                .separate()
                .add(loadBusOverview)
                .add(loadGens)
                .add(loadTrfos)
                .add(loadPow)
                .toTheEnd()
                .add(clearPFCOutput)
                .build();
        console(jPanel6, jScrollPane3, bar);
    }

    /**
     * The same, plus the console search, which sits at the right-hand end
     * because it acts on what is above it rather than on the case.
     */
    private void layoutDynamicSimulationTab() {
        ActionBar.markPrimary(runSimulation);
        JPanel bar = ActionBar.create()
                .add(runSimulation)
                .add(stopSimulationButton)
                .separate()
                .add(loadOutput)
                .add(loadContTrace)
                .add(loadDiscTrace)
                .add(loadDumpTraceButton)
                .add(saveSimulOutput)
                .toTheEnd()
                .add(searchTextField)
                .add(clearSimulOutput)
                .build();
        // The search field would otherwise stretch to fill the glue it follows.
        searchTextField.setMaximumSize(new Dimension(220, 28));
        console(jPanel5, jScrollPane1, bar);
    }

    /**
     * Codegen's bar moves from the top of the tab to the bottom. It is the only
     * one that sat above its console, and one tab disagreeing with three is the
     * inconsistency this pass exists to remove.
     */
    private void layoutCodegenTab() {
        ActionBar.markPrimary(execCodegen);
        JPanel bar = ActionBar.create()
                .add(loadCodegenFiles)
                .add(execCodegen)
                .separate()
                .add(displayCGfiles)
                .add(saveCGFiles)
                .separate()
                .add(Compile)
                .add(savedynsim)
                .build();
        console(jPanel3, jScrollPane2, bar);
    }

    /** The shape the three console tabs share. */
    private void console(JPanel tab, JScrollPane output, JPanel bar) {
        tab.removeAll();
        tab.setLayout(new BorderLayout());
        tab.add(output, BorderLayout.CENTER);
        tab.add(bar, BorderLayout.SOUTH);
    }

    /**
     * The file rows become a column that grows with the window, each row
     * numbered because the order they are read in is the order they are listed
     * in, and that was previously legible only by counting.
     *
     * <p>Row ten carries no Load File button. That is how it arrived: the form
     * has fileData1 through fileData10 and loadData1 through loadData9, so the
     * last row can only be typed into or opened in an editor. The gap is kept
     * rather than filled, because filling it would mean inventing a handler and
     * this pass does not add behaviour.
     */
    private void layoutSystemDataTab() {
        JPanel rows = new JPanel(new GridBagLayout());
        rows.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        JTextField[] fields = {fileData1, fileData2, fileData3, fileData4, fileData5,
            fileData6, fileData7, fileData8, fileData9, fileData10};
        JButton[] loaders = {loadData1, loadData2, loadData3, loadData4, loadData5,
            loadData6, loadData7, loadData8, loadData9, null};
        JButton[] editors = {nppData1Button, nppData2Button, nppData3Button,
            nppData4Button, nppData5Button, nppData6Button, nppData7Button,
            nppData8Button, nppData9Button, nppData10Button};

        int row = 0;
        rows.add(heading(jLabel1), span(row++));
        for (int i = 0; i < fields.length; i++) {
            rows.add(fileRow(String.valueOf(i + 1), loaders[i], fields[i], editors[i]),
                    stretch(row++));
        }
        rows.add(Box.createVerticalStrut(10), span(row++));
        rows.add(heading(jLabel9), span(row++));
        rows.add(fileRow("", loadDist, fileDist, nppDstButton), stretch(row++));
        // Absorbs the leftover height so the rows stay together at the top
        // instead of spreading out over a tall window.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = row;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.VERTICAL;
        rows.add(Box.createGlue(), filler);

        JPanel bar = ActionBar.create().toTheEnd().add(clearDataFiles).build();

        jPanel2.removeAll();
        jPanel2.setLayout(new BorderLayout());
        JScrollPane scroller = new JScrollPane(rows,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        jPanel2.add(scroller, BorderLayout.CENTER);
        jPanel2.add(bar, BorderLayout.SOUTH);
    }

    /** One numbered file row: index, loader, path, editor. */
    private JPanel fileRow(String index, JButton loader, JTextField field, JButton editor) {
        JPanel line = new JPanel(new BorderLayout(6, 0));
        line.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JPanel lead = new JPanel(new BorderLayout(6, 0));
        JLabel number = new JLabel(index, SwingConstants.RIGHT);
        number.setForeground(UIManager.getColor("Label.disabledForeground"));
        number.setPreferredSize(new Dimension(18, 1));
        lead.add(number, BorderLayout.WEST);
        if (loader != null) {
            lead.add(loader, BorderLayout.CENTER);
        } else {
            // Keeps row ten's field aligned with the nine above it.
            lead.add(Box.createRigidArea(
                    new Dimension(loadData1.getPreferredSize().width, 1)),
                    BorderLayout.CENTER);
        }

        line.add(lead, BorderLayout.WEST);
        line.add(field, BorderLayout.CENTER);
        line.add(editor, BorderLayout.EAST);
        return line;
    }

    private static GridBagConstraints span(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(4, 0, 2, 0);
        return c;
    }

    private static GridBagConstraints stretch(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        return c;
    }

    /**
     * Writes the session down: where the window was, and which working
     * directory was in use.
     *
     * <p>Both were thrown away at exit. The Preferences node was already
     * open and holding a single first-run flag, so this is the cheapest thing
     * in the whole plan and the one a user notices every single launch.
     */
    private void rememberSession() {
        Preferences saved = preferences();
        // The maximised state, not the bounds it would report while maximised:
        // restoring those gives a window that looks maximised, is not, and
        // cannot be un-maximised back to anything sensible.
        boolean maximised = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        saved.putBoolean(SESSION_MAXIMISED, maximised);
        if (!maximised) {
            Rectangle bounds = getBounds();
            saved.putInt(SESSION_X, bounds.x);
            saved.putInt(SESSION_Y, bounds.y);
            saved.putInt(SESSION_W, bounds.width);
            saved.putInt(SESSION_H, bounds.height);
        }
        saved.put(SESSION_DIR, selWorkDir == null ? "" : selWorkDir.getAbsolutePath());
        try {
            saved.flush();
        } catch (java.util.prefs.BackingStoreException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                    "Session could not be saved", ex);
        }
    }

    /**
     * Puts the window back where it was, on the tab work starts on.
     *
     * <p>Bounds are accepted only if they still land on a screen that exists.
     * A window restored onto a monitor that has since been unplugged is a
     * window the user cannot reach, and the fix for it is not obvious from
     * inside the application.
     */
    private void restoreSession() {
        Preferences saved = preferences();
        if (!saved.getBoolean(SESSION_MAXIMISED, true)) {
            Rectangle bounds = new Rectangle(
                    saved.getInt(SESSION_X, getX()), saved.getInt(SESSION_Y, getY()),
                    saved.getInt(SESSION_W, getWidth()), saved.getInt(SESSION_H, getHeight()));
            if (bounds.width > 200 && bounds.height > 150 && onAScreen(bounds)) {
                setBounds(bounds);
            }
        }
        // The tab is deliberately not restored. The six tabs are in the
        // order a study is actually run in, so System Data is not merely the
        // first of them, it is where the work starts: load the network, then
        // the disturbance, then observables, then run. Reopening on whichever
        // tab the last session happened to end on drops the user into the
        // middle of that sequence, which is the opposite of what remembering
        // the session is for.
    }

    /** Whether enough of these bounds is on a connected screen to grab. */
    private static boolean onAScreen(Rectangle bounds) {
        for (java.awt.GraphicsDevice screen : java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices()) {
            if (screen.getDefaultConfiguration().getBounds().intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the frame should come up maximised, as it always used to. */
    private static boolean startMaximised() {
        return preferences().getBoolean(SESSION_MAXIMISED, true);
    }

    /** The working directory the last session ended in, if it is still there. */
    private static File lastWorkingDirectory() {
        String path = preferences().get(SESSION_DIR, "");
        if (path.isEmpty()) {
            return null;
        }
        File directory = new File(path);
        return directory.isDirectory() ? directory : null;
    }

    /**
     * Points the window icon and the About drawing at the variant that matches
     * the current theme. Re-run by the theme toggle, which is why it is a method
     * rather than three lines in the constructor: the light mark on a dark
     * dialog is invisible, and it is the same drawing either way.
     */
    private void applyBranding() {
        boolean dark = preferences().getBoolean(DARK_THEME_KEY, false);
        setIconImages(Branding.windowIcons(dark));
        aboutBox.setIconImages(Branding.windowIcons(dark));
        // The lockup already carries the wordmark and the tagline, which is why
        // the two labels that used to repeat them are gone from the form.
        logo.setIcon(Branding.logo(dark));
    }

    /**
     * Puts the painted pencil on the twelve "edit this file" buttons and takes
     * Notepad++ out of their tooltips.
     *
     * <p>The buttons are pinned to 27px wide by the layout, so they are also
     * given the toolbar-button treatment and no margin: at the stock button
     * insets a 16px icon needs about 44px and the glyph would be clipped. The
     * client property is a no-op under a look and feel that does not know it,
     * which is what the fallback path in {@link #installTheme} leaves behind.
     */
    private void styleEditButtons() {
        JButton[] editButtons = {
            nppData1Button, nppData2Button, nppData3Button, nppData4Button,
            nppData5Button, nppData6Button, nppData7Button, nppData8Button,
            nppData9Button, nppData10Button, nppDstButton, nppObsButton};
        for (JButton button : editButtons) {
            button.setIcon(EditIcon.SMALL);
            button.setToolTipText("Open this file in your default editor");
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setMargin(new Insets(0, 0, 0, 0));
        }
    }

    /**
     * Gives the three console panes a monospaced font at the size the rest of
     * the interface is using.
     *
     * <p>The simulation console was pinned to {@code Monospaced 12} in the
     * form: a fixed pixel size that ignores the platform's text scale, so it
     * came out small on a 4K panel and unreadable at 150%. Sizing it from the
     * theme's own font means it follows whatever the user has set, and the
     * other two panes, which had no font of their own, now match it rather
     * than being proportionally spaced beside a monospaced one.
     */
    private void styleConsoles() {
        java.awt.Font base = UIManager.getFont("defaultFont");
        int size = base != null ? base.getSize()
                : new JLabel().getFont().getSize();
        java.awt.Font mono = new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, size);
        simulationOutput.setFont(mono);
        pfcPane.setFont(mono);
        codegenPane.setFont(mono);
    }

    /**
     * Adds the theme toggle to the end of the Tools menu.
     *
     * <p>Added here rather than in the form because it is the one control in
     * the window that has to survive being rebuilt by the thing it switches:
     * {@code FlatLaf.updateUI()} re-creates every component's UI delegate, and
     * an item the designer owns would be the same object either way, whereas
     * this one is created after the look and feel is already up.
     */
    private void addThemeToggle() {
        final JCheckBoxMenuItem darkTheme = new JCheckBoxMenuItem("Dark theme");
        darkTheme.setSelected(preferences().getBoolean(DARK_THEME_KEY, false));
        darkTheme.addActionListener(event -> {
            boolean dark = darkTheme.isSelected();
            preferences().putBoolean(DARK_THEME_KEY, dark);
            try {
                // Preferences writes back on its own schedule, so without this
                // a choice made and then followed by a kill or a crash is lost,
                // and the next launch silently contradicts the menu.
                preferences().flush();
            } catch (java.util.prefs.BackingStoreException ex) {
                Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                        "Theme choice could not be saved", ex);
            }
            installTheme(dark);
            com.formdev.flatlaf.FlatLaf.updateUI();
            // After updateUI, not before: it rebuilds every UI delegate, and a
            // JLabel's icon is not one of the things it rebuilds, but the About
            // dialog is re-laid-out around whatever the label holds when it is
            // next packed.
            applyBranding();
        });
        toolsMenu.addSeparator();
        toolsMenu.add(darkTheme);
    }

    /**
     * Lets a user stop the application contacting a server when it starts.
     *
     * <p>Added programmatically, next to the theme toggle and for the same
     * reason: reopening StepssUI.form in the designer cannot regenerate away
     * something the form never knew about.
     */
    private void addUpdateToggle() {
        final JCheckBoxMenuItem check = new JCheckBoxMenuItem("Check for updates at startup");
        check.setSelected(preferences().getBoolean(CHECK_UPDATES_KEY, true));
        check.addActionListener(event -> {
            preferences().putBoolean(CHECK_UPDATES_KEY, check.isSelected());
            try {
                // Preferences writes back on its own schedule, so without this
                // a choice made and then followed by a kill or a crash is lost,
                // and the next launch silently contradicts the menu.
                preferences().flush();
            } catch (java.util.prefs.BackingStoreException ex) {
                Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                        "Update check choice could not be saved", ex);
            }
        });
        toolsMenu.add(check);
    }

    /**
     * Puts the working directory in the title bar, so two STEPSS windows on two
     * networks are told apart in a taskbar, in an alt-tab switcher, and in the
     * screenshot someone sends when a run will not converge. Called from
     * {@code initRamses()}, which re-runs on every working-directory change.
     */
    private void updateWindowTitle() {
        // Only a directory the user chose. Before they choose one the working
        // directory is the extracted toolchain's temp directory, and
        // "STEPSS - stepssTools4821" would be noise dressed as context.
        String name = (selWorkDir == null) ? "" : selWorkDir.getName();
        setTitle(name.isEmpty() ? "STEPSS" : "STEPSS - " + name);
    }

    private String getVersion() {
        return getVersionFromResource();
    }

    /** The bundled version, readable before any instance exists. */
    static String getVersionFromResource() {
        InputStream in = StepssUI.class.getResourceAsStream("version.txt");
        if (in == null) {
            return "0.0";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            String line = reader.readLine();
            return (line == null || line.trim().isEmpty()) ? "0.0" : line.trim();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return "0.0";
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }


    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        fileChooser = new javax.swing.JFileChooser(new File ("."));
        aboutBox = new javax.swing.JDialog();
        logo = new javax.swing.JLabel();
        versionLabel = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        webpageLabel = new javax.swing.JLabel();
        showGnupCopyrightButton = new javax.swing.JButton();
        showApacheLicenseButton = new javax.swing.JButton();
        showKLULicenseButton = new javax.swing.JButton();
        versionLabel1 = new javax.swing.JLabel();
        showRAMSESLicenseButton = new javax.swing.JButton();
        showPFCLicenseButton = new javax.swing.JButton();
        showCODEGENLicenseButton = new javax.swing.JButton();
        versionLabel2 = new javax.swing.JLabel();
        buttonGroup1 = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel2 = new javax.swing.JPanel();
        loadData1 = new javax.swing.JButton();
        fileData1 = new javax.swing.JTextField();
        fileData2 = new javax.swing.JTextField();
        loadData2 = new javax.swing.JButton();
        fileData3 = new javax.swing.JTextField();
        loadData3 = new javax.swing.JButton();
        fileData4 = new javax.swing.JTextField();
        loadData4 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        clearDataFiles = new javax.swing.JButton();
        fileData5 = new javax.swing.JTextField();
        loadData5 = new javax.swing.JButton();
        nppData1Button = new javax.swing.JButton();
        nppData2Button = new javax.swing.JButton();
        nppData3Button = new javax.swing.JButton();
        nppData4Button = new javax.swing.JButton();
        nppData5Button = new javax.swing.JButton();
        nppDstButton = new javax.swing.JButton();
        fileDist = new javax.swing.JTextField();
        loadDist = new javax.swing.JButton();
        jLabel9 = new javax.swing.JLabel();
        loadData6 = new javax.swing.JButton();
        fileData6 = new javax.swing.JTextField();
        nppData6Button = new javax.swing.JButton();
        loadData7 = new javax.swing.JButton();
        fileData7 = new javax.swing.JTextField();
        nppData7Button = new javax.swing.JButton();
        loadData8 = new javax.swing.JButton();
        fileData8 = new javax.swing.JTextField();
        nppData8Button = new javax.swing.JButton();
        loadData9 = new javax.swing.JButton();
        fileData9 = new javax.swing.JTextField();
        nppData9Button = new javax.swing.JButton();
        fileData10 = new javax.swing.JTextField();
        nppData10Button = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        clearObsFileButton = new javax.swing.JButton();
        saveOutputTrajButton = new javax.swing.JCheckBox();
        loadObsButton = new javax.swing.JButton();
        fileObs = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        saveDumpButton = new javax.swing.JCheckBox();
        observFileWizButton = new javax.swing.JCheckBox();
        jPanel7 = new javax.swing.JPanel();
        jLabel25 = new javax.swing.JLabel();
        busObsField = new javax.swing.JTextField();
        addBusButton = new javax.swing.JButton();
        busObsList = new javax.swing.JComboBox();
        remBusObs = new javax.swing.JButton();
        jLabel26 = new javax.swing.JLabel();
        syncObsField = new javax.swing.JTextField();
        addSyncButton = new javax.swing.JButton();
        syncObsList = new javax.swing.JComboBox();
        remSyncObs = new javax.swing.JButton();
        jLabel27 = new javax.swing.JLabel();
        shuntObsField = new javax.swing.JTextField();
        addShuntButton = new javax.swing.JButton();
        shuntObsList = new javax.swing.JComboBox();
        remShuntObs = new javax.swing.JButton();
        jLabel28 = new javax.swing.JLabel();
        branchObsField = new javax.swing.JTextField();
        addBranchButton = new javax.swing.JButton();
        branchObsList = new javax.swing.JComboBox();
        remBranchObs = new javax.swing.JButton();
        jLabel29 = new javax.swing.JLabel();
        injObsField = new javax.swing.JTextField();
        addInjButton = new javax.swing.JButton();
        injObsList = new javax.swing.JComboBox();
        remInjObs = new javax.swing.JButton();
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(20, 0), new java.awt.Dimension(20, 0), new java.awt.Dimension(20, 32767));
        allBusCheckBox = new javax.swing.JCheckBox();
        allSyncCheckBox = new javax.swing.JCheckBox();
        allShuntCheckBox = new javax.swing.JCheckBox();
        allBranchCheckBox = new javax.swing.JCheckBox();
        allInjCheckBox = new javax.swing.JCheckBox();
        runtimeObsType = new javax.swing.JComboBox();
        runtimeObsName = new javax.swing.JTextField();
        jLabel30 = new javax.swing.JLabel();
        nppObsButton = new javax.swing.JButton();
        saveContTrace = new javax.swing.JCheckBox();
        saveDiscTrace = new javax.swing.JCheckBox();
        runtimeObsType1 = new javax.swing.JComboBox();
        runtimeObsName1 = new javax.swing.JTextField();
        runtimeObsType2 = new javax.swing.JComboBox();
        runtimeObsName2 = new javax.swing.JTextField();
        jPanel6 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        pfcPane = new javax.swing.JTextArea();
        runPF = new javax.swing.JButton();
        loadBusOverview = new javax.swing.JButton();
        loadLFRESV2DAT = new javax.swing.JButton();
        loadGens = new javax.swing.JButton();
        loadTrfos = new javax.swing.JButton();
        loadPow = new javax.swing.JButton();
        clearPFCOutput = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        simulationOutput = new javax.swing.JTextArea();
        runSimulation = new javax.swing.JButton();
        saveSimulOutput = new javax.swing.JButton();
        loadOutput = new javax.swing.JButton();
        loadContTrace = new javax.swing.JButton();
        loadDiscTrace = new javax.swing.JButton();
        clearSimulOutput = new javax.swing.JButton();
        loadDumpTraceButton = new javax.swing.JButton();
        stopSimulationButton = new javax.swing.JButton();
        searchTextField = new javax.swing.JTextField();
        jPanel8 = new javax.swing.JPanel();
        runDyngraphButton = new javax.swing.JButton();
        viewCurvesButton = new javax.swing.JButton();
        saveTrajToFileButton = new javax.swing.JButton();
        clearGnuplotButton = new javax.swing.JButton();
        saveCurrentCurveButton = new javax.swing.JButton();
        loadTrajToFileButton = new javax.swing.JButton();
        saveDynJac = new javax.swing.JButton();
        loadDynJac = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        ssaButton1 = new javax.swing.JButton();
        ssaDirectory = new javax.swing.JTextField();
        loadSSADir = new javax.swing.JButton();
        ssaBasenameLabel = new javax.swing.JLabel();
        ssaBasename = new javax.swing.JTextField();
        ssaTimeLabel = new javax.swing.JLabel();
        ssaTime = new javax.swing.JTextField();
        ssaRealLimitLabel = new javax.swing.JLabel();
        ssaRealLimit = new javax.swing.JTextField();
        ssaPfThresholdLabel = new javax.swing.JLabel();
        ssaPfThreshold = new javax.swing.JTextField();
        ssaEngineNote = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        codegenPane = new javax.swing.JTextArea();
        loadCodegenFiles = new javax.swing.JButton();
        execCodegen = new javax.swing.JButton();
        displayCGfiles = new javax.swing.JButton();
        saveCGFiles = new javax.swing.JButton();
        Compile = new javax.swing.JButton();
        savedynsim = new javax.swing.JButton();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        saveConfigMenuItem = new javax.swing.JMenuItem();
        loadConfigMenuItem = new javax.swing.JMenuItem();
        openExamplesMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        toolsMenu = new javax.swing.JMenu();
        saveCommandFileMenuItem = new javax.swing.JMenuItem();
        saveObsFileMenuItem = new javax.swing.JMenuItem();
        openNppButton = new javax.swing.JMenuItem();
        loadExtSimButton = new javax.swing.JMenuItem();
        selWorkDirButton = new javax.swing.JMenuItem();
        openExplButton = new javax.swing.JMenuItem();
        openTermButton = new javax.swing.JMenuItem();
        killAllGnupMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        showChangeLogButton = new javax.swing.JMenuItem();
        showUserGuideButton = new javax.swing.JMenuItem();
        checkUpdateButton = new javax.swing.JMenuItem();
        showAboutBox = new javax.swing.JMenuItem();

        fileChooser.setName("fileChooser"); // NOI18N

        aboutBox.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        aboutBox.setTitle("About");
        aboutBox.setName("aboutBox"); // NOI18N

        logo.setName("logo"); // NOI18N



        versionLabel.setText("<html><b>Version:</b> 1.0</html>");
        versionLabel.setName("versionLabel"); // NOI18N

        jLabel5.setText("<html><b>Creators:</b> Petros Aristidou and Thierry Van Cutsem</html>");
        jLabel5.setName("jLabel5"); // NOI18N

        webpageLabel.setText("<html><b>Webpage:</b> <a href=\"https://stepss.sps-lab.org/\">https://stepss.sps-lab.org/</a></html>");
        webpageLabel.setName("webpageLabel"); // NOI18N
        webpageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                webpageLabelMouseClicked(evt);
            }
        });

        showGnupCopyrightButton.setText("Gnuplot");
        showGnupCopyrightButton.setName("showGnupCopyrightButton"); // NOI18N
        showGnupCopyrightButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showGnupCopyrightButtonActionPerformed(evt);
            }
        });

        showApacheLicenseButton.setText("Apache");
        showApacheLicenseButton.setName("showApacheLicenseButton"); // NOI18N
        showApacheLicenseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showApacheLicenseButtonActionPerformed(evt);
            }
        });

        showKLULicenseButton.setText("KLU solver");
        showKLULicenseButton.setName("showKLULicenseButton"); // NOI18N
        showKLULicenseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showKLULicenseButtonActionPerformed(evt);
            }
        });

        versionLabel1.setText("<html><b>Third party licenses:</b></html>");
        versionLabel1.setName("versionLabel1"); // NOI18N

        showRAMSESLicenseButton.setText("RAMSES");
        showRAMSESLicenseButton.setName("showRAMSESLicenseButton"); // NOI18N
        showRAMSESLicenseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showRAMSESLicenseButtonActionPerformed(evt);
            }
        });

        showPFCLicenseButton.setText("Helios");
        showPFCLicenseButton.setName("showPFCLicenseButton"); // NOI18N
        showPFCLicenseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showPFCLicenseButtonActionPerformed(evt);
            }
        });

        showCODEGENLicenseButton.setText("CODEGEN");
        showCODEGENLicenseButton.setName("showCODEGENLicenseButton"); // NOI18N
        showCODEGENLicenseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showCODEGENLicenseButtonActionPerformed(evt);
            }
        });

        versionLabel2.setText("<html><b>Licenses:</b></html>");
        versionLabel2.setName("versionLabel2"); // NOI18N

        javax.swing.GroupLayout aboutBoxLayout = new javax.swing.GroupLayout(aboutBox.getContentPane());
        aboutBox.getContentPane().setLayout(aboutBoxLayout);
        aboutBoxLayout.setHorizontalGroup(
            aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aboutBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(logo)
                    .addComponent(versionLabel)
                    .addComponent(jLabel5)
                    .addComponent(webpageLabel)
                    .addGroup(aboutBoxLayout.createSequentialGroup()
                        .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(aboutBoxLayout.createSequentialGroup()
                                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(aboutBoxLayout.createSequentialGroup()
                                        .addComponent(showKLULicenseButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(showApacheLicenseButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(showGnupCopyrightButton))
                                    .addComponent(versionLabel1)))
                            .addGroup(aboutBoxLayout.createSequentialGroup()
                                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(aboutBoxLayout.createSequentialGroup()
                                        .addComponent(showRAMSESLicenseButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(showPFCLicenseButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(showCODEGENLicenseButton))
                                    .addComponent(versionLabel2))))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        aboutBoxLayout.setVerticalGroup(
            aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aboutBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(logo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(versionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(webpageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(versionLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(showRAMSESLicenseButton)
                    .addComponent(showPFCLicenseButton)
                    .addComponent(showCODEGENLicenseButton))
                .addGap(18, 18, 18)
                .addComponent(versionLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(showGnupCopyrightButton)
                    .addComponent(showApacheLicenseButton)
                    .addComponent(showKLULicenseButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("STEPSS");
        setName("Form"); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel1.setName("jPanel1"); // NOI18N

        jTabbedPane1.setName("jTabbedPane1"); // NOI18N

        jPanel2.setName("jPanel2"); // NOI18N

        loadData1.setText("Load file");
        loadData1.setToolTipText("Click to load a data file of the network.");
        loadData1.setName("loadData1"); // NOI18N
        loadData1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData1ActionPerformed(evt);
            }
        });

        fileData1.setEditable(false);
        fileData1.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData1.setName("fileData1"); // NOI18N

        fileData2.setEditable(false);
        fileData2.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData2.setName("fileData2"); // NOI18N

        loadData2.setText("Load file");
        loadData2.setToolTipText("Click to load a data file of the network.");
        loadData2.setName("loadData2"); // NOI18N
        loadData2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData2ActionPerformed(evt);
            }
        });

        fileData3.setEditable(false);
        fileData3.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData3.setName("fileData3"); // NOI18N

        loadData3.setText("Load file");
        loadData3.setToolTipText("Click to load a data file of the network.");
        loadData3.setName("loadData3"); // NOI18N
        loadData3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData3ActionPerformed(evt);
            }
        });

        fileData4.setEditable(false);
        fileData4.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData4.setName("fileData4"); // NOI18N

        loadData4.setText("Load file");
        loadData4.setToolTipText("Click to load a data file of the network.");
        loadData4.setName("loadData4"); // NOI18N
        loadData4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData4ActionPerformed(evt);
            }
        });

        jLabel1.setText("<html><b>System data files</b> (required)</html>");
        jLabel1.setName("jLabel1"); // NOI18N

        clearDataFiles.setText("Clear files");
        clearDataFiles.setName("clearDataFiles"); // NOI18N
        clearDataFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearDataFilesActionPerformed(evt);
            }
        });

        fileData5.setEditable(false);
        fileData5.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData5.setName("fileData5"); // NOI18N

        loadData5.setText("Load file");
        loadData5.setToolTipText("Click to load a data file of the network.");
        loadData5.setName("loadData5"); // NOI18N
        loadData5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData5ActionPerformed(evt);
            }
        });

        nppData1Button.setName("nppData1Button"); // NOI18N
        nppData1Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData1ButtonActionPerformed(evt);
            }
        });

        nppData2Button.setName("nppData2Button"); // NOI18N
        nppData2Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData2ButtonActionPerformed(evt);
            }
        });

        nppData3Button.setName("nppData3Button"); // NOI18N
        nppData3Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData3ButtonActionPerformed(evt);
            }
        });

        nppData4Button.setName("nppData4Button"); // NOI18N
        nppData4Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData4ButtonActionPerformed(evt);
            }
        });

        nppData5Button.setName("nppData5Button"); // NOI18N
        nppData5Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData5ButtonActionPerformed(evt);
            }
        });

        nppDstButton.setName("nppDstButton"); // NOI18N
        nppDstButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppDstButtonActionPerformed(evt);
            }
        });

        fileDist.setEditable(false);
        fileDist.setMinimumSize(new java.awt.Dimension(0, 24));
        fileDist.setName("fileDist"); // NOI18N

        loadDist.setText("Load file");
        loadDist.setToolTipText("Click to load the disturbance file with the description of the fault to be simulated.");
        loadDist.setName("loadDist"); // NOI18N
        loadDist.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDistActionPerformed(evt);
            }
        });

        jLabel9.setText("<html><b>Disturbance file</b> (required)</html>");
        jLabel9.setName("jLabel9"); // NOI18N

        loadData6.setText("Load file");
        loadData6.setToolTipText("Click to load a data file of the network.");
        loadData6.setName("loadData6"); // NOI18N
        loadData6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData6ActionPerformed(evt);
            }
        });

        fileData6.setEditable(false);
        fileData6.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData6.setName("fileData6"); // NOI18N

        nppData6Button.setName("nppData6Button"); // NOI18N
        nppData6Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData6ButtonActionPerformed(evt);
            }
        });

        loadData7.setText("Load file");
        loadData7.setToolTipText("Click to load a data file of the network.");
        loadData7.setName("loadData7"); // NOI18N
        loadData7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData7ActionPerformed(evt);
            }
        });

        fileData7.setEditable(false);
        fileData7.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData7.setName("fileData7"); // NOI18N

        nppData7Button.setName("nppData7Button"); // NOI18N
        nppData7Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData7ButtonActionPerformed(evt);
            }
        });

        loadData8.setText("Load file");
        loadData8.setToolTipText("Click to load a data file of the network.");
        loadData8.setName("loadData8"); // NOI18N
        loadData8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData8ActionPerformed(evt);
            }
        });

        fileData8.setEditable(false);
        fileData8.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData8.setName("fileData8"); // NOI18N

        nppData8Button.setName("nppData8Button"); // NOI18N
        nppData8Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData8ButtonActionPerformed(evt);
            }
        });

        loadData9.setText("Load file");
        loadData9.setToolTipText("Click to load a data file of the network.");
        loadData9.setName("loadData9"); // NOI18N
        loadData9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData9ActionPerformed(evt);
            }
        });

        fileData9.setEditable(false);
        fileData9.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData9.setName("fileData9"); // NOI18N

        nppData9Button.setName("nppData9Button"); // NOI18N
        nppData9Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData9ButtonActionPerformed(evt);
            }
        });

        fileData10.setEditable(false);
        fileData10.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData10.setName("fileData10"); // NOI18N

        nppData10Button.setName("nppData10Button"); // NOI18N
        nppData10Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData10ButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 1849, Short.MAX_VALUE)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(loadData5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fileData5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(loadData3)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fileData3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(loadData4)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fileData4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(nppData3Button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(nppData4Button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(nppData5Button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(loadData1)
                            .addComponent(loadData2))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fileData2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(fileData1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(12, 12, 12)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(nppData1Button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(nppData2Button, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jLabel9, javax.swing.GroupLayout.DEFAULT_SIZE, 1890, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(loadData6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fileData6, javax.swing.GroupLayout.DEFAULT_SIZE, 1763, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nppData6Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(loadData7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fileData7, javax.swing.GroupLayout.DEFAULT_SIZE, 1763, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nppData7Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(loadData8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fileData8, javax.swing.GroupLayout.DEFAULT_SIZE, 1763, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nppData8Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(loadDist)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fileDist, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nppDstButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(loadData9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(fileData10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(nppData10Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                .addComponent(fileData9, javax.swing.GroupLayout.DEFAULT_SIZE, 1763, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(nppData9Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(clearDataFiles)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(9, 9, 9)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(nppData1Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(loadData1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(fileData1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData2)
                        .addComponent(fileData2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData2Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData3)
                        .addComponent(fileData3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData3Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData4)
                        .addComponent(fileData4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData4Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData5)
                        .addComponent(fileData5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData5Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData6)
                        .addComponent(fileData6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData6Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData7)
                        .addComponent(fileData7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData7Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData8)
                        .addComponent(fileData8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData8Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadData9)
                        .addComponent(fileData9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(nppData9Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fileData10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(nppData10Button, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(nppDstButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loadDist)
                        .addComponent(fileDist, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 422, Short.MAX_VALUE)
                .addComponent(clearDataFiles)
                .addContainerGap())
        );

        jTabbedPane1.addTab("System Data", jPanel2);

        jPanel4.setName("jPanel4"); // NOI18N

        clearObsFileButton.setText("Clear files");
        clearObsFileButton.setName("clearObsFileButton"); // NOI18N
        clearObsFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearObsFileButtonActionPerformed(evt);
            }
        });

        saveOutputTrajButton.setText("Save output trajectory");
        saveOutputTrajButton.setToolTipText("<html>Click to save the trajectories of certain observables during the simulation. <br>\nThese observables need to be specified in a file and loaded below or with the Observable File Wizard.</html>");
        saveOutputTrajButton.setName("saveOutputTrajButton"); // NOI18N
        saveOutputTrajButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveOutputTrajButtonActionPerformed(evt);
            }
        });

        loadObsButton.setText("Load file");
        loadObsButton.setToolTipText("<html>Click to load the file with the list of observables to be saved during the simulation.<br><br>\nEach observable needs to be on one line. The possibilities are: <br>\nBUS: followed by one bus name or * to denote all buses <br>\nSYNC: followed by one synchronous machine name or * to denote all sync machines<br>\nSHUNT: followed by a bus name or * to denote all buses<br>\nBRANCH: followed by the branch name or * to denote all branches<br>\nINJEC: followed by the injector name or * to denote all injectors<br>\nLINK: followed by the dclink name or * to denote all dclinks<br><br>\nAn example of an observable file is:<br><br>\nBUS B01 <br>\nSYNC SM01 <br>\nSYNC SM02 <br>\nSYNC SM03 <br>\nBRANCH *<br><br>\nwhich will save BUS B01 observables, synchronous machines SM01, SM02 and SM03 and all the branches observables.</html>");
        loadObsButton.setName("loadObsButton"); // NOI18N
        loadObsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadObsButtonActionPerformed(evt);
            }
        });

        fileObs.setEditable(false);
        fileObs.setName("fileObs"); // NOI18N

        jLabel10.setText("<html><b>Observables file</b> (required when a trajectory is saved)</html>");
        jLabel10.setName("jLabel10"); // NOI18N

        saveDumpButton.setText("Save initialization data");
        saveDumpButton.setToolTipText("<html>Activate to write the settings, the comments and the initialization data to dump.trace. <br>\nUseful for debugging reasons.</html>");
        saveDumpButton.setName("saveDumpButton"); // NOI18N

        observFileWizButton.setText("Show observable dialog");
        observFileWizButton.setName("observFileWizButton"); // NOI18N
        observFileWizButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                observFileWizButtonActionPerformed(evt);
            }
        });

        jPanel7.setVisible(false);
        jPanel7.setName("jPanel7"); // NOI18N
        jPanel7.setLayout(new java.awt.GridBagLayout());

        jLabel25.setText("BUS");
        jLabel25.setName("jLabel25"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(jLabel25, gridBagConstraints);

        busObsField.setToolTipText("Write tha name of the bus to save the observables.");
        busObsField.setMinimumSize(new java.awt.Dimension(0, 0));
        busObsField.setName("busObsField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(busObsField, gridBagConstraints);

        addBusButton.setText("Add BUS");
        addBusButton.setName("addBusButton"); // NOI18N
        addBusButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addBusButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(addBusButton, gridBagConstraints);

        busObsList.setName("busObsList"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(busObsList, gridBagConstraints);

        remBusObs.setText("Remove");
        remBusObs.setMinimumSize(new java.awt.Dimension(64, 15));
        remBusObs.setName("remBusObs"); // NOI18N
        remBusObs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                remBusObsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 40;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(remBusObs, gridBagConstraints);

        jLabel26.setText("Synchronous Machine");
        jLabel26.setName("jLabel26"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(jLabel26, gridBagConstraints);

        syncObsField.setToolTipText("Write tha name of the synchronous machine to save the observables.");
        syncObsField.setMinimumSize(new java.awt.Dimension(0, 0));
        syncObsField.setName("syncObsField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(syncObsField, gridBagConstraints);

        addSyncButton.setText("Add Synchronous machine");
        addSyncButton.setName("addSyncButton"); // NOI18N
        addSyncButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addSyncButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(addSyncButton, gridBagConstraints);

        syncObsList.setName("syncObsList"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(syncObsList, gridBagConstraints);

        remSyncObs.setText("Remove");
        remSyncObs.setMinimumSize(new java.awt.Dimension(64, 15));
        remSyncObs.setName("remSyncObs"); // NOI18N
        remSyncObs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                remSyncObsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 40;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(remSyncObs, gridBagConstraints);

        jLabel27.setText("Shunt");
        jLabel27.setName("jLabel27"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(jLabel27, gridBagConstraints);

        shuntObsField.setToolTipText("Write tha name of the shunt to save the observables.");
        shuntObsField.setMinimumSize(new java.awt.Dimension(0, 0));
        shuntObsField.setName("shuntObsField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(shuntObsField, gridBagConstraints);

        addShuntButton.setText("Add Shunt");
        addShuntButton.setName("addShuntButton"); // NOI18N
        addShuntButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addShuntButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(addShuntButton, gridBagConstraints);

        shuntObsList.setName("shuntObsList"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(shuntObsList, gridBagConstraints);

        remShuntObs.setText("Remove");
        remShuntObs.setMinimumSize(new java.awt.Dimension(64, 15));
        remShuntObs.setName("remShuntObs"); // NOI18N
        remShuntObs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                remShuntObsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 40;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(remShuntObs, gridBagConstraints);

        jLabel28.setText("Branch");
        jLabel28.setName("jLabel28"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(jLabel28, gridBagConstraints);

        branchObsField.setToolTipText("Write tha name of the branch to save the observables.");
        branchObsField.setMinimumSize(new java.awt.Dimension(0, 0));
        branchObsField.setName("branchObsField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(branchObsField, gridBagConstraints);

        addBranchButton.setText("Add Branch");
        addBranchButton.setName("addBranchButton"); // NOI18N
        addBranchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addBranchButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(addBranchButton, gridBagConstraints);

        branchObsList.setName("branchObsList"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(branchObsList, gridBagConstraints);

        remBranchObs.setText("Remove");
        remBranchObs.setMinimumSize(new java.awt.Dimension(64, 15));
        remBranchObs.setName("remBranchObs"); // NOI18N
        remBranchObs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                remBranchObsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 40;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(remBranchObs, gridBagConstraints);

        jLabel29.setText("Injector");
        jLabel29.setName("jLabel29"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(jLabel29, gridBagConstraints);

        injObsField.setToolTipText("branch");
        injObsField.setMinimumSize(new java.awt.Dimension(0, 0));
        injObsField.setName("injObsField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(injObsField, gridBagConstraints);

        addInjButton.setText("Add Injector");
        addInjButton.setName("addInjButton"); // NOI18N
        addInjButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addInjButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(addInjButton, gridBagConstraints);

        injObsList.setName("injObsList"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 100;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(injObsList, gridBagConstraints);

        remInjObs.setText("Remove");
        remInjObs.setMinimumSize(new java.awt.Dimension(64, 15));
        remInjObs.setName("remInjObs"); // NOI18N
        remInjObs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                remInjObsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipadx = 40;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        jPanel7.add(remInjObs, gridBagConstraints);

        filler2.setName("filler2"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        jPanel7.add(filler2, gridBagConstraints);

        allBusCheckBox.setText("All");
        allBusCheckBox.setName("allBusCheckBox"); // NOI18N
        allBusCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allBusCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        jPanel7.add(allBusCheckBox, gridBagConstraints);

        allSyncCheckBox.setText("All");
        allSyncCheckBox.setName("allSyncCheckBox"); // NOI18N
        allSyncCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allSyncCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        jPanel7.add(allSyncCheckBox, gridBagConstraints);

        allShuntCheckBox.setText("All");
        allShuntCheckBox.setName("allShuntCheckBox"); // NOI18N
        allShuntCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allShuntCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        jPanel7.add(allShuntCheckBox, gridBagConstraints);

        allBranchCheckBox.setText("All");
        allBranchCheckBox.setName("allBranchCheckBox"); // NOI18N
        allBranchCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allBranchCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        jPanel7.add(allBranchCheckBox, gridBagConstraints);

        allInjCheckBox.setText("All");
        allInjCheckBox.setName("allInjCheckBox"); // NOI18N
        allInjCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allInjCheckBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        jPanel7.add(allInjCheckBox, gridBagConstraints);

        runtimeObsType.setToolTipText("<html>Click to choose the kind of observable you want to see in run-time during the simulation</html>");
        runtimeObsType.setName("runtimeObsType"); // NOI18N

        runtimeObsName.setToolTipText("<html>Here you clarify the name of the equipment you want to observe. For example:<br>\n1) if you selected Bus Voltage as the type of observable, here you should put the name of the bus.<br>\n2) if you selected Machine Speed or Center of Inertia as the type of observable, here you should put the name of the synchronous machine.<br>\n3) if you selected Wall Time as the type of observable, here you should put RT, as it will plot wall time VS Simulation time.<br><br>\nAdditionally you can pass extra commands to gnuplot in order to fine-tune the output. These commands must follow the name of the equipment and should be separated with / <br>\nSuch commands might be:<br><br>\nset yrange[0.9:1.1]<br><br>\nwhich will set the range of the y-axes between these values.</html>");
        runtimeObsName.setName("runtimeObsName"); // NOI18N

        jLabel30.setText("<html><b>Runtime observables</b></html>");
        jLabel30.setName("jLabel30"); // NOI18N

        nppObsButton.setName("nppObsButton"); // NOI18N
        nppObsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppObsButtonActionPerformed(evt);
            }
        });

        saveContTrace.setText("Save continuous trace");
        saveContTrace.setToolTipText("<html>Activate to record how the Newton solver converged at each step, in cont.trace. <br>\nUseful for debugging, and it can slow the simulation down.</html>");
        saveContTrace.setName("saveContTrace"); // NOI18N

        saveDiscTrace.setSelected(true);
        saveDiscTrace.setText("Save discrete trace");
        saveDiscTrace.setToolTipText("<html>Activate to record the discrete events in disc.trace: the switching in the disturbance file, <br>\nthe discrete controllers, and the discrete variables of injector, torque, exciter and two-port models.</html>");
        saveDiscTrace.setName("saveDiscTrace"); // NOI18N

        runtimeObsType1.setToolTipText("<html>Click to choose the kind of observable you want to see in run-time during the simulation</html>");
        runtimeObsType1.setName("runtimeObsType1"); // NOI18N

        runtimeObsName1.setToolTipText("<html>Here you clarify the name of the equipment you want to observe. For example:<br>\n1) if you selected Bus Voltage as the type of observable, here you should put the name of the bus.<br>\n2) if you selected Machine Speed or Center of Inertia as the type of observable, here you should put the name of the synchronous machine.<br>\n3) if you selected Wall Time as the type of observable, here you should put RT, as it will plot wall time VS Simulation time.<br><br>\nAdditionally you can pass extra commands to gnuplot in order to fine-tune the output. These commands must follow the name of the equipment and should be separated with / <br>\nSuch commands might be:<br><br>\nset yrange[0.9:1.1]<br><br>\nwhich will set the range of the y-axes between these values.</html>");
        runtimeObsName1.setName("runtimeObsName1"); // NOI18N

        runtimeObsType2.setToolTipText("<html>Click to choose the kind of observable you want to see in run-time during the simulation</html>");
        runtimeObsType2.setName("runtimeObsType2"); // NOI18N

        runtimeObsName2.setToolTipText("<html>Here you clarify the name of the equipment you want to observe. For example:<br>\n1) if you selected Bus Voltage as the type of observable, here you should put the name of the bus.<br>\n2) if you selected Machine Speed or Center of Inertia as the type of observable, here you should put the name of the synchronous machine.<br>\n3) if you selected Wall Time as the type of observable, here you should put RT, as it will plot wall time VS Simulation time.<br><br>\nAdditionally you can pass extra commands to gnuplot in order to fine-tune the output. These commands must follow the name of the equipment and should be separated with / <br>\nSuch commands might be:<br><br>\nset yrange[0.9:1.1]<br><br>\nwhich will set the range of the y-axes between these values.</html>");
        runtimeObsName2.setName("runtimeObsName2"); // NOI18N

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel10)
                                    .addComponent(jLabel30)
                                    .addGroup(jPanel4Layout.createSequentialGroup()
                                        .addComponent(runtimeObsType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(runtimeObsName, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(saveContTrace))
                                    .addGroup(jPanel4Layout.createSequentialGroup()
                                        .addComponent(loadObsButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(fileObs, javax.swing.GroupLayout.DEFAULT_SIZE, 1763, Short.MAX_VALUE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(nppObsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(runtimeObsType2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(runtimeObsName2, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(runtimeObsType1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(runtimeObsName1, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(saveDiscTrace))
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(saveOutputTrajButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(saveDumpButton))
                            .addComponent(observFileWizButton)
                            .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, 988, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(clearObsFileButton))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(jLabel30, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(runtimeObsType, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(runtimeObsName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(saveContTrace)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(runtimeObsName1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(runtimeObsType1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(saveDiscTrace))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(runtimeObsName2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(runtimeObsType2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveOutputTrajButton)
                    .addComponent(saveDumpButton))
                .addGap(9, 9, 9)
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(2, 2, 2)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(loadObsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(nppObsButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fileObs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(28, 28, 28)
                .addComponent(observFileWizButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, 187, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 394, Short.MAX_VALUE)
                .addComponent(clearObsFileButton)
                .addContainerGap())
        );

        jTabbedPane1.addTab("Observables", jPanel4);

        jPanel6.setName("jPanel6"); // NOI18N

        jScrollPane3.setName("jScrollPane3"); // NOI18N

        pfcPane.setColumns(20);
        pfcPane.setRows(5);
        pfcPane.setName("pfcPane"); // NOI18N
        jScrollPane3.setViewportView(pfcPane);

        runPF.setText("Run power flow");
        runPF.setToolTipText("Click to run the simulation.");
        runPF.setName("runPF"); // NOI18N
        runPF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runPFActionPerformed(evt);
            }
        });

        loadBusOverview.setText("Bus overview");
        loadBusOverview.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadBusOverview.setEnabled(false);
        loadBusOverview.setName("loadBusOverview"); // NOI18N
        loadBusOverview.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadBusOverviewActionPerformed(evt);
            }
        });

        loadLFRESV2DAT.setText("Add Helios results to data");
        loadLFRESV2DAT.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadLFRESV2DAT.setEnabled(false);
        loadLFRESV2DAT.setName("loadLFRESV2DAT"); // NOI18N
        loadLFRESV2DAT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadLFRESV2DATActionPerformed(evt);
            }
        });

        loadGens.setText("Generators & SVCs");
        loadGens.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadGens.setEnabled(false);
        loadGens.setName("loadGens"); // NOI18N
        loadGens.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadGensActionPerformed(evt);
            }
        });

        loadTrfos.setText("Adjustable transformers");
        loadTrfos.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadTrfos.setEnabled(false);
        loadTrfos.setName("loadTrfos"); // NOI18N
        loadTrfos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadTrfosActionPerformed(evt);
            }
        });

        loadPow.setText("Global power balance");
        loadPow.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadPow.setEnabled(false);
        loadPow.setName("loadPow"); // NOI18N
        loadPow.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadPowActionPerformed(evt);
            }
        });

        clearPFCOutput.setText("Clear all");
        clearPFCOutput.setToolTipText("Click to clear the above pane.");
        clearPFCOutput.setEnabled(false);
        clearPFCOutput.setName("clearPFCOutput"); // NOI18N
        clearPFCOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearPFCOutputActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(runPF)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadLFRESV2DAT)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadBusOverview)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadGens)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadTrfos, javax.swing.GroupLayout.PREFERRED_SIZE, 162, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadPow)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(clearPFCOutput)
                        .addGap(0, 945, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 845, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(runPF, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(loadLFRESV2DAT)
                    .addComponent(loadBusOverview)
                    .addComponent(loadGens)
                    .addComponent(loadTrfos)
                    .addComponent(loadPow)
                    .addComponent(clearPFCOutput))
                .addGap(17, 17, 17))
        );

        jTabbedPane1.addTab("Initialization", jPanel6);

        jPanel5.setName("jPanel5"); // NOI18N

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        simulationOutput.setEditable(false);
        simulationOutput.setColumns(20);
        simulationOutput.setRows(5);
        simulationOutput.setName("simulationOutput"); // NOI18N
        jScrollPane1.setViewportView(simulationOutput);

        runSimulation.setText("Run dynamic simulation");
        runSimulation.setToolTipText("Click to run the simulation.");
        runSimulation.setName("runSimulation"); // NOI18N
        runSimulation.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runSimulationActionPerformed(evt);
            }
        });

        saveSimulOutput.setText("Save current output");
        saveSimulOutput.setToolTipText("Click to save the information shown above to a txt file.");
        saveSimulOutput.setEnabled(false);
        saveSimulOutput.setName("saveSimulOutput"); // NOI18N
        saveSimulOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveSimulOutputActionPerformed(evt);
            }
        });

        loadOutput.setText("Load output");
        loadOutput.setToolTipText("Click to load the output of the simulation.");
        loadOutput.setEnabled(false);
        loadOutput.setName("loadOutput"); // NOI18N
        loadOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadOutputActionPerformed(evt);
            }
        });

        loadContTrace.setText("Load continuous trace");
        loadContTrace.setToolTipText("<html>Click to see the continuous trace of the simulation.<br>\nThis involves a detailed view on the Newton iterations and the time-step evolution.</html>");
        loadContTrace.setEnabled(false);
        loadContTrace.setName("loadContTrace"); // NOI18N
        loadContTrace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadContTraceActionPerformed(evt);
            }
        });

        loadDiscTrace.setText("Load discrete trace");
        loadDiscTrace.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadDiscTrace.setEnabled(false);
        loadDiscTrace.setName("loadDiscTrace"); // NOI18N
        loadDiscTrace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDiscTraceActionPerformed(evt);
            }
        });

        clearSimulOutput.setText("Clear all");
        clearSimulOutput.setToolTipText("Click to clear the above pane.");
        clearSimulOutput.setEnabled(false);
        clearSimulOutput.setName("clearSimulOutput"); // NOI18N
        clearSimulOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearSimulOutputActionPerformed(evt);
            }
        });

        loadDumpTraceButton.setText("Load initialization");
        loadDumpTraceButton.setToolTipText("<html>Click to see the initialization data of the simulation.<br>\nThis involves a detailed view of the initial state of the simulation.</html>");
        loadDumpTraceButton.setEnabled(false);
        loadDumpTraceButton.setName("loadDumpTraceButton"); // NOI18N
        loadDumpTraceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDumpTraceButtonActionPerformed(evt);
            }
        });

        stopSimulationButton.setText("Stop simulation");
        stopSimulationButton.setToolTipText("Click to end the simulation before reaching the horizon (as defined in the disturbance file).");
        stopSimulationButton.setEnabled(false);
        stopSimulationButton.setName("stopSimulationButton"); // NOI18N
        stopSimulationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopSimulationButtonActionPerformed(evt);
            }
        });

        searchTextField.setText("Search...");
        searchTextField.setToolTipText("Serch in the information above for some keyxords.");
        searchTextField.setName("searchTextField"); // NOI18N
        searchTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                searchTextFieldFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                searchTextFieldFocusLost(evt);
            }
        });
        searchTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchTextFieldActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(saveSimulOutput, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(loadOutput, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(loadContTrace, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(runSimulation))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(stopSimulationButton)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(loadDiscTrace)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadDumpTraceButton)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(searchTextField)
                    .addComponent(clearSimulOutput, javax.swing.GroupLayout.PREFERRED_SIZE, 151, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(1142, Short.MAX_VALUE))
            .addComponent(jScrollPane1)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 836, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(saveSimulOutput, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(searchTextField))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(loadOutput)
                            .addComponent(clearSimulOutput, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(runSimulation, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(stopSimulationButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(loadContTrace)
                            .addComponent(loadDiscTrace)
                            .addComponent(loadDumpTraceButton))))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Dynamic Simulation", jPanel5);

        jPanel8.setEnabled(false);
        jPanel8.setName("jPanel8"); // NOI18N

        runDyngraphButton.setText("Extract curves");
        runDyngraphButton.setToolTipText("Click to initiate dialog for selecting the observables you want to plot.");
        runDyngraphButton.setEnabled(false);
        runDyngraphButton.setName("runDyngraphButton"); // NOI18N
        runDyngraphButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runDyngraphButtonActionPerformed(evt);
            }
        });

        viewCurvesButton.setText("Preview curve");
        viewCurvesButton.setToolTipText("Click to preview the last extracted curve.");
        viewCurvesButton.setEnabled(false);
        viewCurvesButton.setName("viewCurvesButton"); // NOI18N
        viewCurvesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewCurvesButtonActionPerformed(evt);
            }
        });

        saveTrajToFileButton.setText("Save current trajectory");
        saveTrajToFileButton.setToolTipText("Click to save the trajectory file of the last simulation.");
        saveTrajToFileButton.setEnabled(false);
        saveTrajToFileButton.setName("saveTrajToFileButton"); // NOI18N
        saveTrajToFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveTrajToFileButtonActionPerformed(evt);
            }
        });

        clearGnuplotButton.setText("Clear all gnuplot instances");
        clearGnuplotButton.setToolTipText("Kills all instances of Gnuplot.");
        clearGnuplotButton.setName("clearGnuplotButton"); // NOI18N
        clearGnuplotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearGnuplotButtonActionPerformed(evt);
            }
        });

        saveCurrentCurveButton.setText("Save extracted curve");
        saveCurrentCurveButton.setToolTipText("Save the extracted plots.");
        saveCurrentCurveButton.setEnabled(false);
        saveCurrentCurveButton.setName("saveCurrentCurveButton"); // NOI18N
        saveCurrentCurveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveCurrentCurveButtonActionPerformed(evt);
            }
        });

        loadTrajToFileButton.setText("Load trajectory");
        loadTrajToFileButton.setToolTipText("Click to load a trajectory file that you saved from a previous simulation.");
        loadTrajToFileButton.setName("loadTrajToFileButton"); // NOI18N
        loadTrajToFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadTrajToFileButtonActionPerformed(evt);
            }
        });

        saveDynJac.setText("Save dynamic Jacobian...");
        saveDynJac.setToolTipText("Saves the last analysis as one archive: the Jacobian it reduced, taken at the same instant as the eigenvalues, and the results it produced");
        saveDynJac.setEnabled(false);
        saveDynJac.setName("saveDynJac"); // NOI18N
        saveDynJac.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveDynJacActionPerformed(evt);
            }
        });

        loadDynJac.setText("Load dynamic Jacobian...");
        loadDynJac.setToolTipText("Opens an archive saved by the button beside this one, on this machine or another");
        loadDynJac.setName("loadDynJac"); // NOI18N
        loadDynJac.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDynJacActionPerformed(evt);
            }
        });

        jLabel4.setText("Time-domain analysis");
        jLabel4.setName("jLabel4"); // NOI18N

        jLabel8.setText("Small-signal stability analysis");
        jLabel8.setName("jLabel8"); // NOI18N

        ssaButton1.setText("Run small-signal stability analysis");
        ssaButton1.setName("ssaButton1"); // NOI18N
        ssaButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ssaButton1ActionPerformed(evt);
            }
        });

        ssaDirectory.setEditable(false);
        ssaDirectory.setName("ssaDirectory"); // NOI18N

        loadSSADir.setText("Select results directory");
        loadSSADir.setToolTipText("Directory the Jacobian and small-signal results are copied to");
        loadSSADir.setName("loadSSADir"); // NOI18N
        loadSSADir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadSSADirActionPerformed(evt);
            }
        });

        ssaBasenameLabel.setText("Results basename");
        ssaBasenameLabel.setName("ssaBasenameLabel"); // NOI18N

        ssaBasename.setText("ssa");
        ssaBasename.setToolTipText("Names the three results files, so several runs can share one directory");
        ssaBasename.setName("ssaBasename"); // NOI18N

        ssaTimeLabel.setText("Analysis time t [s]");
        ssaTimeLabel.setName("ssaTimeLabel"); // NOI18N

        ssaTime.setText("0.001");
        ssaTime.setToolTipText("When to linearise. The default analyses the initial operating point; a later time runs an event-free simulation to that point first.");
        ssaTime.setName("ssaTime"); // NOI18N

        ssaRealLimitLabel.setText("Real part limit");
        ssaRealLimitLabel.setEnabled(false);
        ssaRealLimitLabel.setName("ssaRealLimitLabel"); // NOI18N

        ssaRealLimit.setText("-1.0");
        ssaRealLimit.setToolTipText("Needs a RAMSES release whose EIG disturbance accepts these values. The running engine does not, and would ignore them silently, so they are disabled rather than misleading.");
        ssaRealLimit.setEnabled(false);
        ssaRealLimit.setName("ssaRealLimit"); // NOI18N

        ssaPfThresholdLabel.setText("PF threshold");
        ssaPfThresholdLabel.setEnabled(false);
        ssaPfThresholdLabel.setName("ssaPfThresholdLabel"); // NOI18N

        ssaPfThreshold.setText("0.05");
        ssaPfThreshold.setToolTipText("Needs a RAMSES release whose EIG disturbance accepts these values. The running engine does not, and would ignore them silently, so they are disabled rather than misleading.");
        ssaPfThreshold.setEnabled(false);
        ssaPfThreshold.setName("ssaPfThreshold"); // NOI18N

        ssaEngineNote.setText("Real part limit and PF threshold need a newer RAMSES; the running engine ignores them.");
        ssaEngineNote.setName("ssaEngineNote"); // NOI18N

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addComponent(loadSSADir)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ssaDirectory))
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(runDyngraphButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(viewCurvesButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(saveCurrentCurveButton))
                            .addComponent(clearGnuplotButton)
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(saveTrajToFileButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(loadTrajToFileButton))
                            .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(ssaButton1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(saveDynJac)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(loadDynJac))
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(ssaBasenameLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaBasename, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(ssaTimeLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaTime, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(ssaRealLimitLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaRealLimit, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(ssaPfThresholdLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaPfThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(ssaEngineNote))
                        .addGap(0, 1492, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(11, 11, 11)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveTrajToFileButton)
                    .addComponent(loadTrajToFileButton))
                .addGap(12, 12, 12)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(saveCurrentCurveButton)
                    .addComponent(viewCurvesButton)
                    .addComponent(runDyngraphButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(clearGnuplotButton)
                .addGap(18, 18, 18)
                .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ssaDirectory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(loadSSADir))
                .addGap(14, 14, 14)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ssaBasenameLabel)
                    .addComponent(ssaBasename, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ssaTimeLabel)
                    .addComponent(ssaTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ssaRealLimitLabel)
                    .addComponent(ssaRealLimit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ssaPfThresholdLabel)
                    .addComponent(ssaPfThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ssaEngineNote)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ssaButton1)
                    .addComponent(saveDynJac)
                    .addComponent(loadDynJac))
                .addContainerGap(657, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Analysis", jPanel8);

        jPanel3.setName("jPanel3"); // NOI18N

        jScrollPane2.setName("jScrollPane2"); // NOI18N

        codegenPane.setColumns(20);
        codegenPane.setRows(5);
        codegenPane.setName("codegenPane"); // NOI18N
        jScrollPane2.setViewportView(codegenPane);

        loadCodegenFiles.setText("Load files for Codegen");
        loadCodegenFiles.setName("loadCodegenFiles"); // NOI18N
        loadCodegenFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadCodegenFilesActionPerformed(evt);
            }
        });

        execCodegen.setText("Run Codegen");
        execCodegen.setEnabled(false);
        execCodegen.setName("execCodegen"); // NOI18N
        execCodegen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                execCodegenActionPerformed(evt);
            }
        });

        displayCGfiles.setText("Display loaded files");
        displayCGfiles.setEnabled(false);
        displayCGfiles.setName("displayCGfiles"); // NOI18N
        displayCGfiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayCGfilesActionPerformed(evt);
            }
        });

        saveCGFiles.setText("Save converted files");
        saveCGFiles.setEnabled(false);
        saveCGFiles.setName("saveCGFiles"); // NOI18N
        saveCGFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveCGFilesActionPerformed(evt);
            }
        });

        Compile.setText("Compile");
        Compile.setEnabled(false);
        Compile.setName("Compile"); // NOI18N
        Compile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CompileActionPerformed(evt);
            }
        });

        savedynsim.setText("Save executable");
        savedynsim.setEnabled(false);
        savedynsim.setName("savedynsim"); // NOI18N
        savedynsim.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savedynsimActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(loadCodegenFiles)
                        .addGap(18, 18, 18)
                        .addComponent(execCodegen, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(displayCGfiles)
                        .addGap(18, 18, 18)
                        .addComponent(saveCGFiles)
                        .addGap(18, 18, 18)
                        .addComponent(Compile)
                        .addGap(18, 18, 18)
                        .addComponent(savedynsim)
                        .addGap(0, 1034, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(loadCodegenFiles)
                    .addComponent(execCodegen)
                    .addComponent(displayCGfiles)
                    .addComponent(saveCGFiles)
                    .addComponent(Compile)
                    .addComponent(savedynsim))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 844, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab("Codegen", jPanel3);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setText("File");
        fileMenu.setName("fileMenu"); // NOI18N

        saveConfigMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        saveConfigMenuItem.setText("Save configuration");
        saveConfigMenuItem.setName("saveConfigMenuItem"); // NOI18N
        saveConfigMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveConfigMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveConfigMenuItem);

        loadConfigMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        loadConfigMenuItem.setText("Load configuration");
        loadConfigMenuItem.setName("loadConfigMenuItem"); // NOI18N
        loadConfigMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadConfigMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(loadConfigMenuItem);

        openExamplesMenuItem.setText("Open Examples");
        openExamplesMenuItem.setName("openExamplesMenuItem"); // NOI18N
        openExamplesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openExamplesMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openExamplesMenuItem);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        exitMenuItem.setText("Exit");
        exitMenuItem.setName("exitMenuItem"); // NOI18N
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        toolsMenu.setText("Tools");
        toolsMenu.setName("toolsMenu"); // NOI18N

        saveCommandFileMenuItem.setText("Save command file");
        saveCommandFileMenuItem.setName("saveCommandFileMenuItem"); // NOI18N
        saveCommandFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveCommandFileMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(saveCommandFileMenuItem);

        saveObsFileMenuItem.setText("Save observables file");
        saveObsFileMenuItem.setName("saveObsFileMenuItem"); // NOI18N
        saveObsFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveObsFileMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(saveObsFileMenuItem);

        openNppButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openNppButton.setText("Open in editor");
        openNppButton.setName("openNppButton"); // NOI18N
        openNppButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openNppButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(openNppButton);

        loadExtSimButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        loadExtSimButton.setText("Select external simulator");
        loadExtSimButton.setName("loadExtSimButton"); // NOI18N
        loadExtSimButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadExtSimButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(loadExtSimButton);

        selWorkDirButton.setText("Select working directory");
        selWorkDirButton.setName("selWorkDirButton"); // NOI18N
        selWorkDirButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selWorkDirButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(selWorkDirButton);

        openExplButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openExplButton.setText("Open working folder");
        openExplButton.setEnabled(false);
        openExplButton.setName("openExplButton"); // NOI18N
        openExplButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openExplButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(openExplButton);

        openTermButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openTermButton.setText("Open terminal in working folder");
        openTermButton.setEnabled(false);
        openTermButton.setName("openTermButton"); // NOI18N
        openTermButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openTermButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(openTermButton);

        killAllGnupMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        killAllGnupMenuItem.setText("Clear all gnuplot instances");
        killAllGnupMenuItem.setName("killAllGnupMenuItem"); // NOI18N
        killAllGnupMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                killAllGnupMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(killAllGnupMenuItem);

        menuBar.add(toolsMenu);

        helpMenu.setText("Help");
        helpMenu.setName("helpMenu"); // NOI18N

        showChangeLogButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0));
        showChangeLogButton.setText("Changelog");
        showChangeLogButton.setName("showChangeLogButton"); // NOI18N
        showChangeLogButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showChangeLogButtonActionPerformed(evt);
            }
        });
        helpMenu.add(showChangeLogButton);

        showUserGuideButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        showUserGuideButton.setText("User guide");
        showUserGuideButton.setName("showUserGuideButton"); // NOI18N
        showUserGuideButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showUserGuideButtonActionPerformed(evt);
            }
        });
        helpMenu.add(showUserGuideButton);

        checkUpdateButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F3, 0));
        checkUpdateButton.setText("Check for updates");
        checkUpdateButton.setName("checkUpdateButton"); // NOI18N
        checkUpdateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkUpdateButtonActionPerformed(evt);
            }
        });
        helpMenu.add(checkUpdateButton);

        showAboutBox.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, 0));
        showAboutBox.setText("About");
        showAboutBox.setName("showAboutBox"); // NOI18N
        showAboutBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showAboutBoxActionPerformed(evt);
            }
        });
        helpMenu.add(showAboutBox);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        setSize(new java.awt.Dimension(1928, 1002));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        formWindowClosing(null);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void showAboutBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAboutBoxActionPerformed
        // pack() first, and it has to be here rather than in initComponents:
        // without it the dialog opens at whatever size it was last given, which
        // was the 500x350 minimum, and 500x350 is smaller than the layout's own
        // minimum, so GroupLayout clipped the third-party licence row off the
        // bottom edge where nothing could reach it. The logo label's inherited
        // 763x545 preferred size had to go for the same reason, or packing to
        // preferred would have produced an 800px dialog around a 152px image.
        aboutBox.pack();
        aboutBox.setLocationRelativeTo(this);
        aboutBox.setVisible(true);
    }//GEN-LAST:event_showAboutBoxActionPerformed

    /**
     * Writes the simulation command file, or returns why it could not be.
     *
     * <p>Returns null on success and a sentence on failure, rather than a
     * boolean and a dialog of its own. It used to do both: show a dialog
     * naming the specific problem, return false, and have the caller show a
     * second dialog saying the command file was not created. Two boxes for one
     * mistake, and the second one carried no information. The caller says it
     * once now, in the banner.
     */
    private String createCommandFile() {
        try {
            FileWriter ryt = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "cmd.txt");
            BufferedWriter out = new BufferedWriter(ryt);
            out.write("");
            out.flush();
            if (noSystemDataLoaded()) {
                out.close();
                return "No system data files are loaded. Add at least one on the System Data tab.";
            }
            for (JTextField s : dataFileList) {
                if (!s.getText().equals("")) {
                    out.append(s.getText());
                    out.newLine();
                }
            }

            out.newLine();

            if (saveDumpButton.isSelected() && !ssa) {
                out.append("dump.trace");
                out.newLine();
            } else {
                out.newLine();
            }

            if (!fileDist.getText().equals("")) {
                out.append(fileDist.getText());
                out.newLine();
            } else {
                out.close();
                return "No disturbance file is loaded. Add one on the System Data tab.";
            }
            if (saveOutputTrajButton.isSelected() && !ssa) {
                if (!fileObs.getText().equals("") && !observFileWizButton.isSelected()) {
                    out.append("output.trj");
                    out.newLine();
                    out.append(fileObs.getText());
                    out.newLine();
                } else if (fileObs.getText().equals("") && observFileWizButton.isSelected()) {
                    if (createCustomObsFile()) {
                        out.append("output.trj");
                        out.newLine();
                        out.append("customObs.txt");
                        out.newLine();
                    } else {
                        out.close();
                        return "The observables file could not be written.";
                    }
                } else if (!fileObs.getText().equals("") && observFileWizButton.isSelected()) {
                    if (createCustomObsFile()) {
                        FileReader obsFile = new FileReader(fileObs.getText());
                        StringBuilder fileData = new StringBuilder(1000);
                        BufferedReader bufreader = new BufferedReader(obsFile);
                        String line;
                        while ((line = bufreader.readLine()) != null) {
                            if (line.trim().length() != 0) {
                                fileData.append(line).append("\n");
                            }
                        }
                        bufreader.close();
                        obsFile = new FileReader(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt");
                        bufreader = new BufferedReader(obsFile);
                        while ((line = bufreader.readLine()) != null) {
                            if (line.trim().length() != 0) {
                                fileData.append(line).append("\n");
                            }
                        }
                        FileWriter tmpFileWriter = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt");
                        tmpFileWriter.write(fileData.toString());
                        tmpFileWriter.close();
                        out.append("output.trj");
                        out.newLine();
                        out.append("customObs.txt");
                        out.newLine();
                    } else {
                        out.close();
                        return "The observables file could not be written.";
                    }
                } else {
                    out.close();
                    return "A trajectory will be saved, so an observables file is needed. Load one on the Observables tab, or use the observable dialog.";
                }
            } else {
                out.newLine();
            }
            if (saveContTrace.isSelected() && !ssa) {
                out.append("cont.trace");
                out.newLine();
                loadContTrace.setEnabled(true);
            } else {
                out.newLine();
                loadContTrace.setEnabled(false);
            }
            if (saveDiscTrace.isSelected() && !ssa) {
                out.append("disc.trace");
                out.newLine();
                loadDiscTrace.setEnabled(true);
            } else {
                out.newLine();
                loadDiscTrace.setEnabled(false);
            }

            if (!ssa) {
                String failure = writeObservable(out, runtimeObsType, runtimeObsName);
                if (failure == null) {
                    failure = writeObservable(out, runtimeObsType1, runtimeObsName1);
                }
                if (failure == null) {
                    failure = writeObservable(out, runtimeObsType2, runtimeObsName2);
                }
                if (failure != null) {
                    out.close();
                    return failure;
                }
            }

            out.newLine();
            out.flush();
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return "The command file could not be written.";
        }
        return null;
    }

    /**
     * True when every system data row is empty, which is the one thing both
     * command files refuse to be written without.
     *
     * <p>Over all ten rows, because the loop that writes them does. This was
     * a hand-unrolled test of rows one to four sitting three lines above a
     * {@code for} over {@code dataFileList}, so a case loaded into row five or
     * later was refused with "No system data files are loaded" while the row
     * plainly showed its path. Add Helios results to data writes row ten and
     * nothing else, so it could produce that refusal on its own.
     */
    private boolean noSystemDataLoaded() {
        for (JTextField s : dataFileList) {
            if (!s.getText().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Writes one runtime observable row into the command file and returns
     * null, or returns why it could not be, on the same convention as
     * {@link #createCommandFile()}.
     *
     * <p>A blank name is how a row says it is unused, so a blank row writes
     * nothing at all. Wall Time is the exception on both counts: it names no
     * equipment, so it is written whether the field is filled or not, and its
     * line is a fixed "RT RT".
     *
     * <p>One method for what were three copies, one per row. They had already
     * drifted: the second and third read the <em>first</em> row's name field
     * for the two delta observables, so picking "Omega-delta of machine" on
     * row two plotted whichever machine row one named, or wrote a bare keyword
     * when row one was empty. Taking the field as an argument is what stops
     * that happening again.
     */
    private String writeObservable(BufferedWriter out, JComboBox type, JTextField name) throws IOException {
        String label = String.valueOf(type.getSelectedItem());
        boolean wallTime = WALL_TIME.equals(label);
        if (name.getText().isEmpty() && !wallTime) {
            return null;
        }
        String keyword = OBSERVABLE_TYPES.get(label);
        if (keyword == null) {
            return "The command file could not be written.";
        }
        out.append(wallTime ? keyword + " RT" : keyword + " " + name.getText());
        out.newLine();
        return null;
    }

    /**
     * Writes the power-flow command file, or returns why it could not be.
     *
     * <p>Returns null on success and a sentence on failure, rather than a
     * boolean and a dialog of its own. It used to do both: show a dialog
     * naming the specific problem, return false, and have the caller show a
     * second dialog saying the command file was not created. Two boxes for one
     * mistake, and the second one carried no information. The caller says it
     * once now, in the banner.
     */
    private String createPFCCommandFile() {
        try {
            FileWriter ryt = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "PFCcmd.txt");
            BufferedWriter out = new BufferedWriter(ryt);
            out.write("");
            out.flush();
            if (noSystemDataLoaded()) {
                out.close();
                return "No system data files are loaded. Add at least one on the System Data tab.";
            }
            for (JTextField s : dataFileList) {
                if (!s.getText().equals("")) {
                    out.append(s.getText());
                    out.newLine();
                }
            }

            out.newLine();
            // helios has no working output redirection ("O" on the main menu is a
            // stub), so results are captured with the display sub-menu's "X"
            // eXport command: build a table, leave its item sub-menu with a blank
            // line, then "X" plus the target file name. "X" overwrites, so every
            // table needs its own file. A single "D" keeps us in the display
            // sub-menu for all five exports.
            out.append("D\n");
            out.append("O\n");           // bus Overview
            out.append("A\n");           // all buses
            out.newLine();               // leave the item sub-menu
            out.append("X\n");
            out.append("in_net.res\n");
            out.append("T\n");           // adjustable Transformers
            out.append("A\n");
            out.newLine();
            out.append("X\n");
            out.append("in_trfo.res\n");
            out.append("G\n");           // Generators
            out.append("A\n");
            out.newLine();
            out.append("X\n");
            out.append("in_gen.res\n");
            out.append("S\n");           // SVCs
            out.append("A\n");
            out.newLine();
            out.append("X\n");
            out.append("in_svc.res\n");
            out.append("P\n");           // global Power balance (no item sub-menu)
            out.append("X\n");
            out.append("in_bal.res\n");
            out.newLine();               // leave the display sub-menu
            out.append("VT\n");
            out.append("in_volt_trfo.dat\n");
            out.append("E\n");
            out.newLine();
            out.flush();
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return "The command file could not be written.";
        }
        return null;
    }

    /**
     * Writes the Codegen command file, or returns why it could not be.
     *
     * <p>Returns null on success and a sentence on failure, rather than a
     * boolean and a dialog of its own. It used to do both: show a dialog
     * naming the specific problem, return false, and have the caller show a
     * second dialog saying the command file was not created. Two boxes for one
     * mistake, and the second one carried no information. The caller says it
     * once now, in the banner.
     */
    private String createCGCommandFile() {
        try {
            FileWriter ryt = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "CGcmd.txt");
            BufferedWriter out = new BufferedWriter(ryt);
            out.write("");
            out.flush();

            if (codeGenFiles == null) {
                out.close();
                return "No Codegen files are loaded. Load them on the Codegen tab.";
            }
            for (File temp : codeGenFiles) {
                out.append(temp.getAbsolutePath());
                out.newLine();
            }
            out.flush();
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return "The command file could not be written.";
        }
        return null;
    }


    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        Object[] options = {"Yes",
            "Yes (clear all open windows)",
            "Cancel"};
        int confirmed = JOptionPane.showOptionDialog(this, "Are you sure you want to exit? All simulation data will be lost!", "Exit Confirmation", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
        if (confirmed == JOptionPane.YES_OPTION) {
            rememberSession();
            if (toolDir == null) {
            } else {
                fileOps.deleteDirectory(toolDir);
            }
            System.exit(0);
        } else if (confirmed == JOptionPane.NO_OPTION) {
            rememberSession();
            if (toolDir == null) {
            } else {
                clearGnuplotButtonActionPerformed(null);
                stopSimulationButtonActionPerformed(null);
                fileOps.deleteDirectory(toolDir);
            }
            System.exit(0);
        }
    }//GEN-LAST:event_formWindowClosing

    /**
     * Writes the current case to a {@code .cfg} the user chooses.
     *
     * <p>No {@code fileData10.setText("")} here, and none anywhere in this
     * path. It used to run before the chooser's return value was checked, so
     * merely opening Save configuration and pressing Cancel emptied data row
     * ten, which is the row Add Helios results to data fills with
     * in_volt_trfo.dat. The next run then wrote a valid cmd.txt with the
     * power-flow initialisation file silently missing from it. Saving reads
     * the form and never writes to it.
     */
    private void saveConfigMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveConfigMenuItemActionPerformed
        Scenario scenario = scenarioBinding.read();
        if (scenario.isEmpty()) {
            banner.warn("There is no case to save yet. Add system data files on the"
                    + " System Data tab first.");
            return;
        }
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setDialogTitle("Save configuration");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("STEPSS configuration", "cfg"));
        int returnVal = fileChooser.showSaveDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fileChooser.getSelectedFile();
        if (!file.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".cfg")) {
            file = new File(file.getPath() + ".cfg");
        }
        if (file.exists()) {
            int response = JOptionPane.showConfirmDialog(this, "Overwrite existing file?",
                    "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (response != JOptionPane.OK_OPTION) {
                return;
            }
        }
        try {
            // myTempDir, not the .cfg's directory: it is the base the engines
            // resolve against, so it is what a hand-typed relative path in a
            // form field already means.
            ScenarioFile.save(scenario, file, myTempDir, this_version);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "Could not write " + file.getAbsolutePath() + "\n\n" + ex.getMessage(),
                    "Configuration not saved", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final File written = file;
        banner.notice("Configuration saved to " + written.getAbsolutePath(),
                "Open folder", () -> {
                    try {
                        PlatformLauncher.openFileManager(written.getParentFile());
                    } catch (IOException ex) {
                        Logger.getLogger(StepssUI.class.getName())
                                .log(Level.SEVERE, null, ex);
                    }
                });
    }//GEN-LAST:event_saveConfigMenuItemActionPerformed

    /**
     * Fills the case from a {@code .cfg} the user chooses.
     *
     * <p>Everything that can go wrong is said out loud. A file this build
     * cannot interpret at all - one from before the format existed, one from a
     * newer STEPSS - is refused in a dialog naming the reason; anything
     * smaller, such as a key nobody knows or a file the scenario names that is
     * no longer there, is applied as far as it can be and then listed. The
     * handler this replaces reported neither: it caught IOException alone,
     * while its own {@code new Integer(properties.getProperty(name))} threw
     * NumberFormatException on a missing key and IllegalArgumentException on a
     * stale index.
     */
    private void loadConfigMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadConfigMenuItemActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setDialogTitle("Load configuration");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("STEPSS configuration", "cfg"));
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fileChooser.getSelectedFile();
        ScenarioFile.Loaded loaded;
        try {
            loaded = ScenarioFile.load(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Configuration not loaded", JOptionPane.ERROR_MESSAGE);
            return;
        }
        java.util.List<String> problems = new ArrayList<>(loaded.problems());
        problems.addAll(scenarioBinding.apply(loaded.scenario()));
        // Set directly rather than through observFileWizButtonActionPerformed,
        // which also flips saveOutputTrajButton and would overwrite the value
        // just restored from the file.
        jPanel7.setVisible(observFileWizButton.isSelected());
        // The observable dialog's five picker lists are session state, not part
        // of the scenario, so a case saved with the dialog in use comes back
        // with it ticked over five empty lists. Left unsaid, the next run would
        // write a customObs.txt of blank lines and plot nothing.
        if (observFileWizButton.isSelected() && noObservablesPicked()) {
            problems.add("Show observable dialog is on, but no observables are"
                    + " selected. Add them on the Observables tab before running.");
        }
        if (problems.isEmpty()) {
            banner.confirm(file.getName() + " loaded.");
            return;
        }
        JOptionPane.showMessageDialog(this,
                file.getName() + " was loaded, with " + problems.size()
                + (problems.size() == 1 ? " problem:\n\n" : " problems:\n\n")
                + listed(problems),
                "Configuration loaded", JOptionPane.WARNING_MESSAGE);
    }//GEN-LAST:event_loadConfigMenuItemActionPerformed

    /**
     * The problems, as many as fit in a dialog somebody will read.
     *
     * <p>Capped because the count is not bounded by anything the user did: a
     * file that is not a scenario at all but still carries a plausible
     * {@code stepss.format} contributes one line per key it holds, and
     * {@code JOptionPane} grows to fit its message, so a few hundred of them
     * produce a dialog taller than the screen with its buttons off the bottom
     * edge and no way to dismiss it.
     */
    private static String listed(java.util.List<String> problems) {
        int shown = Math.min(problems.size(), 10);
        String text = String.join("\n\n", problems.subList(0, shown));
        if (shown < problems.size()) {
            text += "\n\nand " + (problems.size() - shown) + " more.";
        }
        return text;
    }

    /** True when the observable dialog would contribute nothing to a run. */
    private boolean noObservablesPicked() {
        return !allBusCheckBox.isSelected() && !allSyncCheckBox.isSelected()
                && !allShuntCheckBox.isSelected() && !allBranchCheckBox.isSelected()
                && !allInjCheckBox.isSelected()
                && busObsList.getItemCount() == 0 && syncObsList.getItemCount() == 0
                && shuntObsList.getItemCount() == 0 && branchObsList.getItemCount() == 0
                && injObsList.getItemCount() == 0;
    }

    private void openExamplesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openExamplesMenuItemActionPerformed
        showExamples();
    }//GEN-LAST:event_openExamplesMenuItemActionPerformed

    /**
     * Opens the examples panel on launch unless the user has turned it off.
     *
     * <p>From the frame's first {@code windowOpened}, beside the update check
     * and for the same reason: the splash holds the window back for up to three
     * seconds, and a modal dialog raised before that would sit in front of a
     * frame nobody can see yet.
     *
     * <p>Queued rather than called straight, so {@code windowOpened} returns and
     * the frame finishes painting behind the dialog instead of being blocked
     * mid-delivery by a modal window.
     */
    private void showExamplesAtStartup() {
        if (!preferences().getBoolean(SHOW_EXAMPLES_KEY, true)) {
            return;
        }
        SwingUtilities.invokeLater(this::showExamples);
    }

    /**
     * Offers the bundled test systems, and opens the one that is chosen.
     *
     * <p>Reached from File &gt; Open Examples and, unless the user has turned it
     * off, once at startup. A new user otherwise has a working STEPSS and
     * nothing to run in it: they have to know stepss-test-systems exists, find
     * it, clone it, and work out which file goes in which of the ten slots.
     */
    private void showExamples() {
        java.util.List<Example> examples;
        try {
            examples = ExampleCatalog.load();
        } catch (IOException ex) {
            // The descriptor and the payloads are both build outputs verified by
            // ExamplesPack and PayloadManifestCheck, so this is a broken jar
            // rather than anything the user did or can fix.
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE,
                    "The bundled examples could not be read", ex);
            JOptionPane.showMessageDialog(this,
                    "The bundled examples could not be read.\n\n" + ex.getMessage(),
                    "Examples unavailable", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean dark = preferences().getBoolean(DARK_THEME_KEY, false);
        ExamplesDialog.Choice choice = ExamplesDialog.show(this, examples,
                Branding.logo(dark),
                preferences().getBoolean(SHOW_EXAMPLES_KEY, true),
                BareBonesBrowserLaunch::openURL);

        rememberExamplesAtStartup(choice.showAtStartup());
        if (choice.example() != null) {
            openExample(choice.example());
        }
    }

    /**
     * Stores the startup checkbox.
     *
     * <p>Flushed here rather than on close, for the reason the theme and update
     * toggles give: Preferences writes back on its own schedule, so a choice
     * followed by a kill or a crash is lost and the next launch silently
     * contradicts what the user ticked. Flushing on the way out of the dialog
     * also means closing it with the window button honours the choice.
     */
    private void rememberExamplesAtStartup(boolean show) {
        preferences().putBoolean(SHOW_EXAMPLES_KEY, show);
        try {
            preferences().flush();
        } catch (java.util.prefs.BackingStoreException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                    "Examples-at-startup choice could not be saved", ex);
        }
    }

    /**
     * Extracts one example beside its siblings and loads it as the current case.
     *
     * <p>Never into {@code toolDir}: that is the temporary directory the
     * toolchain unpacks into and {@code formWindowClosing} deletes on exit, so
     * anything the user changed would be gone without warning. The second thing
     * a user does with an example is edit it.
     */
    private void openExample(Example example) {
        File root = examplesRoot();
        if (root == null) {
            return;
        }
        File target = new File(root, example.dir());

        if (target.exists()) {
            // Never silently overwritten. Reuse is first and is the default,
            // because an existing directory usually means the user has been
            // working in it.
            Object[] options = {"Reuse my copy", "Fresh copy", "Cancel"};
            int answer = JOptionPane.showOptionDialog(this,
                    "<html>This example is already extracted at:<br><b>"
                    + escapeHtml(target.getAbsolutePath()) + "</b><br><br>"
                    + "Reuse that copy, or replace it with a fresh one?<br>"
                    + "A fresh copy deletes anything you changed there.</html>",
                    "Example already extracted", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (answer != 0 && answer != 1) {
                return;
            }
            if (answer == 1 && !ExampleInstaller.deleteRecursively(target)) {
                JOptionPane.showMessageDialog(this,
                        "Could not remove the existing copy at\n"
                        + target.getAbsolutePath()
                        + "\n\nCheck that nothing else has the files open.",
                        "Example not replaced", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (answer == 0) {
                // Reuse, so nothing is written. A file the user deleted from
                // their own copy would leave a slot pointing at nothing, so say
                // so rather than let the run fail with an engine error.
                java.util.List<String> missing =
                        ExampleInstaller.missingFrom(example, target);
                if (!missing.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Your copy of this example is missing:\n\n  "
                            + String.join("\n  ", missing)
                            + "\n\nOpen it again and choose Fresh copy to restore it.",
                            "Example incomplete", JOptionPane.WARNING_MESSAGE);
                }
                applyExample(example, target, root);
                return;
            }
        }

        try {
            ExampleInstaller.install(example, target);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "Could not extract " + example.name() + " to\n"
                    + target.getAbsolutePath() + "\n\n" + ex.getMessage(),
                    "Example not opened", JOptionPane.ERROR_MESSAGE);
            return;
        }
        applyExample(example, target, root);
    }

    /**
     * Fills the case from where the example's files actually landed, and makes
     * its directory the working directory.
     *
     * <p>The configuration is GENERATED here, never read from the {@code
     * sim.cfg} each example repository ships. Those are pre-rewrite files of
     * absolute paths from whoever last saved them
     * ({@code fileData1=C:\Users\tvanc\...}), so loading one would fill this
     * form with someone else's paths on every platform. The descriptor records
     * which filename belongs in which slot, and the path comes from the
     * extraction.
     *
     * <p>{@link my.stepss.config.ScenarioFile} now refuses those files outright
     * rather than leaving it to this method to know better, so the reasoning
     * above is enforced on the Load configuration path too. Regenerating them
     * in the current format would not change anything here: the paths would
     * still be whoever's machine packed the example, and the descriptor would
     * still be the better source.
     */
    private void applyExample(Example example, File dir, File root) {
        JTextField[] slots = {fileData1, fileData2, fileData3, fileData4, fileData5,
            fileData6, fileData7, fileData8, fileData9, fileData10};
        for (int i = 0; i < slots.length; i++) {
            slots[i].setText(i < example.data().size()
                    ? new File(dir, example.data().get(i)).getAbsolutePath()
                    : "");
        }
        fileDist.setText(new File(dir, example.dist()).getAbsolutePath());
        fileObs.setText(new File(dir, example.obs()).getAbsolutePath());
        // What loadObsButton does once a file is chosen: an observables file
        // with the trajectory output switched off produces nothing to plot.
        saveOutputTrajButton.setSelected(true);

        // Remembered so the next example lands beside this one rather than
        // inside it. Stored before the working directory moves, because that is
        // what the root would otherwise be derived from next time.
        preferences().put(EXAMPLES_DIR, root.getAbsolutePath());

        File previous = selWorkDir;
        selWorkDir = dir;
        if (!initRamses()) {
            // Unlike the Select-working-directory path, this does not exit. The
            // toolchain was already extracted successfully for this session, so
            // a failure here is not the unusable-install case that one guards
            // against, and quitting would throw away the user's session over an
            // example. initRamses has already explained itself in a dialog.
            selWorkDir = previous;
            return;
        }
        try {
            preferences().flush();
        } catch (java.util.prefs.BackingStoreException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                    "Examples directory could not be saved", ex);
        }
        banner.notice(example.name() + " opened in " + dir.getAbsolutePath(),
                "Open folder", () -> {
                    try {
                        PlatformLauncher.openFileManager(dir);
                    } catch (IOException ex) {
                        Logger.getLogger(StepssUI.class.getName())
                                .log(Level.SEVERE, null, ex);
                    }
                });
    }

    /**
     * The directory examples are kept in, asking for one if there is none yet.
     *
     * <p>Three sources, in order: the remembered root, the current working
     * directory, and the user. The middle one is what makes the first open on
     * an established installation land somewhere the user already chose, and the
     * last is for a fresh install, which has neither.
     *
     * @return the root, or null if the user cancelled
     */
    private File examplesRoot() {
        String saved = preferences().get(EXAMPLES_DIR, "");
        if (!saved.isEmpty() && new File(saved).isDirectory()) {
            return new File(saved);
        }
        if (selWorkDir != null && selWorkDir.isDirectory()) {
            return selWorkDir;
        }
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Choose a directory to keep examples in");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File chosen = fileChooser.getSelectedFile();
        if (!chosen.isDirectory() && !chosen.mkdirs()) {
            JOptionPane.showMessageDialog(this,
                    "Could not create " + chosen.getAbsolutePath(),
                    "Examples directory", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return chosen;
    }

    private void saveCommandFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveCommandFileMenuItemActionPerformed
        try {
            String problem = createCommandFile();
            if (problem != null) {
                banner.warn(problem);
                return;
            }
            fileChooser.setSelectedFile(new File(""));
            fileChooser.setDialogTitle("Choose File");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int returnVal = fileChooser.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File dstFile = fileChooser.getSelectedFile();
                if (dstFile.exists()) {
                    int response = JOptionPane.showConfirmDialog(null, "Overwrite existing file?", "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (response == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }
                File srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "cmd.txt");
                String content = IOUtils.toString(new FileInputStream(srcFile), "UTF-8");
                // Pattern.quote, not Matcher.quoteReplacement. replaceAll
                // takes a regex first and a replacement second, and these had
                // them the other way round: quoteReplacement escapes only \
                // and $, so myTempDir went in as a pattern. myTempDir is the
                // working directory the user chose, so a case kept in
                // "Nordic32 (v2)" or "case+1" stripped nothing at all and the
                // saved file kept absolute paths into a directory that does
                // not survive the session. It failed silently, and unbalanced
                // brackets would have thrown PatternSyntaxException past the
                // IOException catch below.
                String file_str = myTempDir.getAbsolutePath() + System.getProperty("file.separator");
                content = content.replaceAll(Pattern.quote(file_str), "");
                IOUtils.write(content, new FileOutputStream(dstFile), "UTF-8");

                srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt");
                if (srcFile.exists()) {
                    content = IOUtils.toString(new FileInputStream(srcFile), "UTF-8");
                    content = content.replaceAll(Pattern.quote(file_str), "");
                    IOUtils.write(content, new FileOutputStream(srcFile), "UTF-8");
                    fileOps.copyFiletoDir(srcFile, dstFile.getAbsoluteFile());
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }

    }//GEN-LAST:event_saveCommandFileMenuItemActionPerformed

    private void saveObsFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveObsFileMenuItemActionPerformed
        try {
            fileChooser.setSelectedFile(new File(""));
            fileChooser.setDialogTitle("Choose File");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int returnVal = fileChooser.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File dstFile = fileChooser.getSelectedFile();
                if (dstFile.exists()) {
                    int response = JOptionPane.showConfirmDialog(null, "Overwrite existing file?", "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (response == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }

                if (!fileObs.getText().equals("") && !observFileWizButton.isSelected()) {
                    fileOps.copyFiletoFile(new File(fileObs.getText()), dstFile);
                } else if (fileObs.getText().equals("") && observFileWizButton.isSelected()) {
                    boolean customObsFileCreated = createCustomObsFile();
                    if (customObsFileCreated) {
                        fileOps.copyFiletoFile(new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt"), dstFile);
                    } else {
                        JOptionPane.showMessageDialog(this, "Could not create the Observables file!", "Observables file!", JOptionPane.ERROR_MESSAGE);
                    }
                } else if (!fileObs.getText().equals("") && observFileWizButton.isSelected()) {
                    boolean customObsFileCreated = createCustomObsFile();
                    if (customObsFileCreated) {
                        FileReader obsFile = new FileReader(fileObs.getText());
                        StringBuilder fileData = new StringBuilder(1000);
                        BufferedReader bufreader = new BufferedReader(obsFile);
                        String line;
                        while ((line = bufreader.readLine()) != null) {
                            if (line.trim().length() != 0) {
                                fileData.append(line).append("\n");
                            }
                        }
                        bufreader.close();
                        obsFile = new FileReader(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt");
                        bufreader = new BufferedReader(obsFile);
                        while ((line = bufreader.readLine()) != null) {
                            if (line.trim().length() != 0) {
                                fileData.append(line).append("\n");
                            }
                        }
                        FileWriter tmpFileWriter = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt");
                        tmpFileWriter.write(fileData.toString());
                        tmpFileWriter.close();
                        fileOps.copyFiletoFile(new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt"), dstFile);
                    } else {
                        JOptionPane.showMessageDialog(this, "Could not create the Observables file!", "Observables file!", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_saveObsFileMenuItemActionPerformed

    private void openTermButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openTermButtonActionPerformed
        try {
            PlatformLauncher.openTerminal(myTempDir);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_openTermButtonActionPerformed

    private void openNppButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openNppButtonActionPerformed
        nppOpen(evt, "");
    }//GEN-LAST:event_openNppButtonActionPerformed

    private void nppOpen(java.awt.event.ActionEvent evt, String filename) {
        File target = filename.isEmpty() ? myTempDir : new File(filename);
        if (!target.exists()) {
            banner.warn("<html>The file <B>" + target.getName() + "</B> does not exist.</html>");
            return;
        }
        try {
            PlatformLauncher.openInEditor(target);
        } catch (IOException ex) {
            banner.warn("Could not open an editor for " + target.getAbsolutePath()
                    + "\n\n" + ex.getMessage());
        }
    }

    private void openExplButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openExplButtonActionPerformed
        try {
            PlatformLauncher.openFileManager(myTempDir);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_openExplButtonActionPerformed

    private void loadExtSimButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadExtSimButtonActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (platform == Platform.WINDOWS_X86_64) {
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Executable", "exe");
            fileChooser.setFileFilter(filter);
        } else {
            fileChooser.setSelectedFile(new File("dynsim"));
        }
        fileChooser.setDialogTitle("Choose External Simulator");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File srcFile = fileChooser.getSelectedFile();
            if (!srcFile.exists()) {
                JOptionPane.showConfirmDialog(null, "File does not exist!", "File not found.", JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
                return;
            }
            ramsesExec = srcFile;
            ramsesExec.setExecutable(true);
        }
    }//GEN-LAST:event_loadExtSimButtonActionPerformed

    private void showChangeLogButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showChangeLogButtonActionPerformed
        // The releases page rather than stepss.sps-lab.org/changelog.txt: that
        // file is the RAMSES changelog (it mirrors SPS-L/stepss-ramses), so it
        // described the bundled engine and never this application. The release
        // notes composed by tools/ci/release.py are this repo's changelog, and
        // they embed each bumped component's upstream notes, so the RAMSES
        // history the old file carried is still reachable from here.
        BareBonesBrowserLaunch.openURL(RELEASES_URL);
    }//GEN-LAST:event_showChangeLogButtonActionPerformed

    /**
     * Reports an update-check result and offers to open the release it names.
     *
     * <p>A dialog is where the user is when they want the page, so the link
     * belongs in it rather than in a "look under Help" instruction. It is a
     * button and not an HTML anchor because JOptionPane renders its message
     * through a JLabel, which draws anchors as blue underlined text that
     * cannot be clicked - an invitation the dialog cannot honour.
     *
     * @param message     HTML message body
     * @param releaseUrl  page to open, or null to offer no link at all
     * @param messageType a JOptionPane message type, for the icon
     */
    private void showUpdateResult(String message, String releaseUrl, int messageType) {
        if (releaseUrl == null) {
            JOptionPane.showMessageDialog(this, message, "Update Manager", messageType);
            return;
        }
        Object[] options = {"Open release page", "Close"};
        int choice = JOptionPane.showOptionDialog(this, message, "Update Manager",
                JOptionPane.DEFAULT_OPTION, messageType, null, options, options[0]);
        if (choice == 0) {
            BareBonesBrowserLaunch.openURL(releaseUrl);
        }
    }

    private void showUserGuideButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showUserGuideButtonActionPerformed
        BareBonesBrowserLaunch.openURL("https://stepss.sps-lab.org/");
    }//GEN-LAST:event_showUserGuideButtonActionPerformed

    private void selWorkDirButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selWorkDirButtonActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Choose Working Directory");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            selWorkDir = fileChooser.getSelectedFile();
            if (!selWorkDir.exists()) {
                selWorkDir.mkdir();
            }
            if (!selWorkDir.isDirectory()) {
                selWorkDir.getParentFile();
            }
            if (initRamses()) {
            } else {
                // initRamses() already showed a dialog naming the specific
                // tool and path that failed; a second, generic dialog here
                // would just repeat the failure without adding information.
                System.exit(1);
            }
        }
    }//GEN-LAST:event_selWorkDirButtonActionPerformed

    private void checkUpdateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkUpdateButtonActionPerformed
        try {
            // stepss.sps-lab.org/version.txt carried the RAMSES version, so the
            // check answered a question about the bundled engine rather than
            // about this application. The releases page is the same source
            // Help->Changelog now opens, so the version offered here and the
            // notes describing it can no longer disagree.
            //
            // The request itself lives in UpdateCheck, which the startup check
            // uses too, so the two cannot drift apart over what they ask for or
            // over what counts as newer.
            String location = UpdateCheck.latestLocation(RELEASES_LATEST_URL);
            String current_version = Version.fromReleaseUrl(location);
            int[] published = current_version == null ? null : Version.key(current_version);
            int[] running = Version.key(this_version);
            String msg;

            if (published == null || running == null) {
                // A null here means a number was never understood - no release
                // has been published yet, or one of the two is not a dotted
                // integer - and neither answer this dialog otherwise gives is
                // then true. Saying so is the honest outcome: reporting "you
                // already have the newest version" off a number nobody read is
                // how a stale install stays stale.
                msg = "<html>Could not work out which version is the latest.<br />Installed: " + this_version
                        + "<br />Published: " + (current_version == null ? "not reported" : current_version)
                        + "<br /><br />The releases page lists every published version.</html>";
                // The index rather than `location`: whatever that header held,
                // it did not name a release this code could read.
                showUpdateResult(msg, RELEASES_URL, JOptionPane.WARNING_MESSAGE);
            } else if (Version.compare(published, running) > 0) {
                msg = "<html>New version " + current_version + " is now available!<br />Current version: " + this_version
                        + "<br /><br />The release page lists what has changed and carries the download.</html>";
                versionLabel.setText("<html><b>Version:</b> " + this_version + " (latest version available: " + current_version + ")</html>");
                // `location` is the redirect target, so it is the page for this
                // exact release rather than for whatever is newest whenever the
                // link is followed.
                showUpdateResult(msg, location, JOptionPane.INFORMATION_MESSAGE);
            } else {
                msg = "<html>You already have the newest version (" + this_version + ").<br /><br />"
                        + "The release page lists what changed in it.</html>";
                showUpdateResult(msg, location, JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            // Logged and shown. Left silent, an unreachable network made the
            // menu item look like it did nothing at all when the user pressed
            // it, which is the one moment they are waiting for an answer.
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not reach the releases page to check for updates.<br />"
                    + "If you are sure you have internet access, please report this.</html>",
                    "Update Manager", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_checkUpdateButtonActionPerformed

    /**
     * Asks github.com whether there is a newer STEPSS, and says so on the
     * banner if there is.
     *
     * <p>Silent about everything else. A user who did not ask for this does
     * not want an error about it: no network, a proxy, a redirect that names
     * no release, all end here with nothing said and a FINE log line. The
     * manual check under Help keeps its dialogs and its SEVERE logging,
     * because there somebody asked.
     *
     * <p>A daemon thread, so a slow DNS lookup can never hold the JVM open
     * after the user has quit, and never the EDT, so a machine with no network
     * does not pay the connect timeout before its window appears. Nothing
     * waits on it: startup is finished by the time it is even started.
     *
     * <p>Started from the frame's first {@code windowOpened}, not from the
     * constructor, and the difference is two bugs rather than a preference.
     * The splash holds the window back for up to three seconds, so a check
     * started at the end of the constructor runs against a frame nobody can
     * see yet. {@link InlineBanner} holds one message, and {@code initRamses()}
     * puts the "gnuplot was not found" warning on it during that same
     * constructor, so on a machine with no gnuplot and an update available the
     * notice replaced the warning before either had ever been on screen. And
     * the banner's twelve second expiry runs from the call, not from first
     * paint, so an early notice spent a quarter of its life behind a hidden
     * window. Both go away once the check begins after the window is up.
     */
    private void checkForUpdatesAtStartup() {
        if (!preferences().getBoolean(CHECK_UPDATES_KEY, true)) {
            return;
        }
        Thread check = new Thread(() -> {
            final String location;
            try {
                location = UpdateCheck.latestLocation(RELEASES_LATEST_URL);
            } catch (IOException ex) {
                Logger.getLogger(StepssUI.class.getName())
                        .log(Level.FINE, "Startup update check could not reach github.com", ex);
                return;
            }
            final String notice = UpdateCheck.noticeFor(this_version, location);
            if (notice == null) {
                return;
            }
            final String published = Version.fromReleaseUrl(location);
            SwingUtilities.invokeLater(() -> {
                banner.notice(notice, "Open release page",
                        () -> BareBonesBrowserLaunch.openURL(location));
                // The banner clears on the user's next click, so the About box
                // keeps the fact after it has gone.
                versionLabel.setText("<html><b>Version:</b> " + this_version
                        + " (latest version available: " + published + ")</html>");
            });
        }, "stepss-update-check");
        check.setDaemon(true);
        check.start();
    }

    /** Whether to ask github.com for a newer release at startup. */
    static final String CHECK_UPDATES_KEY = "checkUpdatesAtStartup";

    private void showGnupCopyrightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showGnupCopyrightButtonActionPerformed
        try {
            InputStream in;
            in = StepssUI.class.getResourceAsStream("gnuplotLicense.txt");
            File gnupCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "gnuplotLicense.txt");
            OutputStream streamOut;
            streamOut = FileUtils.openOutputStream(gnupCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(gnupCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showGnupCopyrightButtonActionPerformed

    private void webpageLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_webpageLabelMouseClicked
        String url = "https://stepss.sps-lab.org";
        BareBonesBrowserLaunch.openURL(url);
    }//GEN-LAST:event_webpageLabelMouseClicked

    private void showApacheLicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showApacheLicenseButtonActionPerformed
        try {
            InputStream in;
            in = StepssUI.class.getResourceAsStream("apacheLicense.txt");
            File gnupCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "apacheLicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(gnupCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(gnupCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showApacheLicenseButtonActionPerformed

    private void killAllGnupMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_killAllGnupMenuItemActionPerformed
        clearGnuplotButtonActionPerformed(evt);
    }//GEN-LAST:event_killAllGnupMenuItemActionPerformed

    private void showKLULicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showKLULicenseButtonActionPerformed
        try {
            InputStream in;
            in = StepssUI.class.getResourceAsStream("KLULicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "KLULicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showKLULicenseButtonActionPerformed

    private void loadSSADirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadSSADirActionPerformed

        fileChooser.setSelectedFile(new File(""));
        fileChooser.setDialogTitle("Choose SSA Working Directory");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            ssaDirectory.setText(file.getAbsolutePath());
        } else {
            ssaDirectory.setText("");
        }
    }//GEN-LAST:event_loadSSADirActionPerformed

    private void ssaButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ssaButton1ActionPerformed
        // Runs the analysis in the engine. RAMSES reduces the linearised model
        // to a state matrix, solves the eigenproblem and writes the three
        // results files itself, so nothing external is invoked.
        //
        // The .dst is generated rather than copied from a bundled resource,
        // because the basename is now the user's to choose: the EIG argument
        // names all three output files, so a per-run basename is what lets
        // several runs share one directory.
        try {
            String base = ssaBasename.getText().trim();
            if (base.isEmpty()) {
                base = "ssa";
                ssaBasename.setText(base);
            }
            // The basename becomes both a filename and a Fortran record, so it
            // is checked before either is written. An apostrophe closes the EIG
            // argument early and the engine then writes nothing, which would
            // surface as the "No results were produced" dialog below, naming
            // solver settings that were never the problem. A path separator
            // writes the files somewhere the loader will not look.
            if (!my.stepss.ssa.SsaDisturbance.validBasename(base)) {
                banner.warn("The results basename \"" + base + "\" cannot be used.\n\n"
                        + "It names the three results files and is written into the\n"
                        + "EIG disturbance record, so it may contain only letters,\n"
                        + "digits, dot, underscore and hyphen.");
                return;
            }

            // When to linearise. The default analyses the initial operating
            // point. A later time runs the simulation to that point with no
            // events first, which is why the generated file below carries none:
            // the engine linearises about whatever state it is in when EIG
            // fires, so an event in between would describe that instant rather
            // than an operating point.
            double analysisTime;
            try {
                analysisTime = my.stepss.ssa.SsaDisturbance.parseTime(ssaTime.getText());
            } catch (IllegalArgumentException ex) {
                banner.warn(ex.getMessage());
                return;
            }

            // The two analysis parameters, but only against an engine that
            // accepts them. applyEngineCapabilities() decides that from the
            // engine's own banner and enables the fields to match, so a
            // disabled field means the record stays two-argument.
            //
            // The asymmetry is deliberate and is why the check exists at all:
            // disturb.f90 reads this record list-directed, so an engine
            // without the feature takes the first two items and ignores the
            // rest without erroring. Writing the parameters anyway would give
            // a full results set computed with the engine's defaults, under a
            // window header claiming these values. Nothing downstream could
            // detect it.
            double realLimit = 0.0;
            double pfThreshold = 0.0;
            if (eigParametersSupported) {
                try {
                    realLimit = my.stepss.ssa.SsaDisturbance.parseParameter(
                            ssaRealLimit.getText(), "Real part limit");
                    pfThreshold = my.stepss.ssa.SsaDisturbance.parseParameter(
                            ssaPfThreshold.getText(), "PF threshold");
                } catch (IllegalArgumentException ex) {
                    banner.warn(ex.getMessage());
                    return;
                }
            }

            File dstFile = new File(myTempDir.getAbsolutePath()
                    + System.getProperty("file.separator") + base + "Eig.dst");
            String dstText = eigParametersSupported
                    ? my.stepss.ssa.SsaDisturbance.text(base, analysisTime,
                            realLimit, pfThreshold)
                    : my.stepss.ssa.SsaDisturbance.text(base, analysisTime);
            FileUtils.writeStringToFile(dstFile, dstText, "UTF-8");

            String tmpString = fileDist.getText();
            fileDist.setText(dstFile.getName());
            ssa = true;
            runSimulationActionPerformed(evt);
            simulExecutorResultHandler.waitFor();
            fileDist.setText(tmpString);

            // The analysis refuses rather than guessing when it cannot produce a
            // result it can justify, and says so through the exit code, so a
            // missing modes file is reported instead of failing silently later.
            File modes = new File(myTempDir.getAbsolutePath()
                    + System.getProperty("file.separator") + base + "_modes.dat");
            if (!modes.exists()) {
                banner.warn("No results were produced. Small-signal analysis needs $OMEGA_REF SYN and\n"
                        + "$SCHEME DE in the solver settings, and a system within $EIG_MAX_STATES.\n"
                        + "See the log for the reason.");
                return;
            }

            String[] produced = {base + "_modes.dat", base + "_pf.dat", base + "_ms.dat"};
            File resultsDir = new File(myTempDir.getAbsolutePath());
            if (!"".equals(ssaDirectory.getText())) {
                for (String name : produced) {
                    File srcFile = new File(myTempDir.getAbsolutePath()
                            + System.getProperty("file.separator") + name);
                    File dstCopy = new File(ssaDirectory.getText()
                            + System.getProperty("file.separator") + name);
                    if (srcFile.exists()) {
                        fileOps.copyFiletoFile(srcFile, dstCopy);
                    }
                }
                resultsDir = new File(ssaDirectory.getText());
            }

            // Opened from wherever the files actually are. With no results
            // directory set they stay in the tool directory, and the window
            // header names that path, so a successful run can no longer look
            // identical to a no-op.
            // The Jacobian stays where the run wrote it. Only the results are
            // copied out automatically, because the Jacobian is large and most
            // runs never want it; the button below is the deliberate step.
            //
            // Recorded here, not read off the fields when that button is
            // pressed: the two parameters are recorded only when the engine
            // actually accepted them, since an older one ignores the record
            // silently and archiving the numbers anyway would put thresholds
            // in the manifest that the run never used.
            lastRunDir = new File(myTempDir.getAbsolutePath());
            lastRunManifest = new my.stepss.ssa.SsaArchive.Manifest(base,
                    Double.isNaN(engineVersion) ? null : Double.valueOf(engineVersion),
                    Double.valueOf(analysisTime),
                    eigParametersSupported ? Double.valueOf(realLimit) : null,
                    eigParametersSupported ? Double.valueOf(pfThreshold) : null,
                    getVersion());
            saveDynJac.setEnabled(true);

            showSsaResults(resultsDir, base);
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_ssaButton1ActionPerformed

    /** Loads one run and shows it, reporting a parse failure rather than swallowing it. */
    private void showSsaResults(File dir, String basename) {
        try {
            my.stepss.ssa.SsaResultsWindow.open(this,
                    my.stepss.ssa.SsaResults.load(dir, basename));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not read the results in " + dir + "\n\n" + ex.getMessage(),
                    "Small-signal results", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveDynJacActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveDynJacActionPerformed
        // Saves the last analysis as one file: the Jacobian it reduced, the
        // modes, participation factors and mode shapes it produced, and a
        // manifest naming the run. The same run wrote all of it, the Jacobian
        // from a JAC record at the same instant as the EIG record, so the
        // matrix in the archive is necessarily the one behind the eigenvalues
        // that were displayed rather than one a later run might have taken
        // from an edited case or a different time. That is why this button is
        // enabled by a successful analysis rather than standing on its own.
        //
        // What is deliberately NOT in it: the data files, the solver settings
        // and the disturbance. The archive is a record of a result, which is
        // what the button is named after, not an input someone else can re-run.
        if (lastRunDir == null || lastRunManifest == null) {
            return;
        }
        javax.swing.filechooser.FileFilter zip =
                extensionFilter(my.stepss.ssa.SsaArchive.Format.ZIP);
        javax.swing.filechooser.FileFilter tar =
                extensionFilter(my.stepss.ssa.SsaArchive.Format.TAR_GZ);
        fileChooser.resetChoosableFileFilters();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Save the small-signal run as one archive");
        fileChooser.addChoosableFileFilter(zip);
        fileChooser.addChoosableFileFilter(tar);
        fileChooser.setFileFilter(zip);
        fileChooser.setSelectedFile(new File(lastRunManifest.basename()
                + my.stepss.ssa.SsaArchive.Format.ZIP.extension()));
        int chose = fileChooser.showSaveDialog(this);
        javax.swing.filechooser.FileFilter picked = fileChooser.getFileFilter();
        File selected = fileChooser.getSelectedFile();
        // Left as the rest of the window expects to find it. A chooser that
        // keeps two archive filters would offer them to the next handler that
        // opens a .cfg or a .trj.
        fileChooser.resetChoosableFileFilters();
        if (chose != JFileChooser.APPROVE_OPTION) {
            return;
        }

        // A name that already spells a format wins over the filter: someone
        // who typed "run.tar.gz" has said what they want more plainly than the
        // drop-down they never touched. Otherwise the filter decides and the
        // extension is appended, so the file always says what it is.
        my.stepss.ssa.SsaArchive.Format format =
                my.stepss.ssa.SsaArchive.formatOfName(selected.getName());
        if (format == null) {
            format = picked == tar
                    ? my.stepss.ssa.SsaArchive.Format.TAR_GZ
                    : my.stepss.ssa.SsaArchive.Format.ZIP;
        }
        File target = my.stepss.ssa.SsaArchive.named(selected, format);
        if (target.exists() && JOptionPane.showConfirmDialog(this,
                target + " already exists.\n\nReplace it?",
                "Save dynamic Jacobian", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        java.util.List<String> absent;
        try {
            absent = my.stepss.ssa.SsaArchive.save(target, format, lastRunDir,
                    lastRunManifest);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not write " + target + "\n\n" + ex.getMessage(),
                    "Save dynamic Jacobian", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // An absent _pf or _ms is a result, not a fault: the engine writes
        // neither when no mode passed real_limit, so a run that filtered
        // everything is complete without them. A missing Jacobian file is a
        // different matter, since one JAC record writes all four. Reporting
        // both the same way would put a warning dialog in front of an entirely
        // ordinary analysis, which is how a warning stops being read.
        StringBuilder faults = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (String name : absent) {
            if (my.stepss.ssa.SsaArchive.isOptional(name)) {
                expected.append(expected.length() == 0 ? "" : " and ").append(name);
            } else {
                faults.append("\n    ").append(name);
            }
        }
        if (faults.length() > 0) {
            JOptionPane.showMessageDialog(this,
                    "Saved the run \"" + lastRunManifest.basename() + "\" to\n"
                    + target + "\n\nThese were not in " + lastRunDir
                    + ", so they are not in the archive:" + faults,
                    "Save dynamic Jacobian", JOptionPane.WARNING_MESSAGE);
        } else if (expected.length() > 0) {
            banner.confirm("Saved the run \"" + lastRunManifest.basename()
                    + "\" to " + target + ". No mode passed real_limit, so the"
                    + " engine wrote no " + expected + " to archive.");
        } else {
            banner.confirm("Saved the run \"" + lastRunManifest.basename()
                    + "\" to " + target + ", Jacobian and results together.");
        }
    }//GEN-LAST:event_saveDynJacActionPerformed

    /** One archive format, as the save chooser offers it. */
    private static javax.swing.filechooser.FileFilter extensionFilter(
            final my.stepss.ssa.SsaArchive.Format format) {
        // Not FileNameExtensionFilter: that one matches the last dot only, so
        // ".tar.gz" reaches it as "gz" and the description would name a format
        // this application does not write on its own.
        return new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName()
                        .toLowerCase(java.util.Locale.ROOT)
                        .endsWith(format.extension());
            }

            @Override
            public String getDescription() {
                return format.description();
            }
        };
    }

    private void loadDynJacActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadDynJacActionPerformed
        // The other half of the button beside this one, and the reason the
        // archive carries a manifest at all: this opens a file that came from
        // somewhere else, so it has to be able to tell one of ours from a
        // holiday photo album and say which it got.
        fileChooser.resetChoosableFileFilters();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Open a small-signal archive");
        fileChooser.setSelectedFile(new File(""));
        // One filter over both formats, and All Files left in the drop-down
        // beside it. What makes a file one of ours is its first two bytes, not
        // its name, so an archive that came back from a mail client as
        // "archive.dat" opens perfectly well and has to remain reachable.
        javax.swing.filechooser.FileFilter archives =
                new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory()
                        || my.stepss.ssa.SsaArchive.formatOfName(file.getName()) != null;
            }

            @Override
            public String getDescription() {
                return "Small-signal archive (*.zip, *.tar.gz)";
            }
        };
        fileChooser.addChoosableFileFilter(archives);
        fileChooser.setFileFilter(archives);
        int chose = fileChooser.showOpenDialog(this);
        File archive = fileChooser.getSelectedFile();
        fileChooser.resetChoosableFileFilters();
        if (chose != JFileChooser.APPROVE_OPTION) {
            return;
        }

        // Unpacked under the tool directory, never over the current run: an
        // archive of a run called "ssa" would otherwise overwrite the "ssa" on
        // disk, and any results window already open on it would be describing
        // files that no longer exist. toolDir is also what the exit path
        // deletes, so nothing outlives the session.
        //
        // It is null only when initRamses() could not make it, which it
        // reports and then carries on from; without this the archive would be
        // refused by a stack trace rather than by a sentence.
        if (toolDir == null) {
            banner.warn("There is nowhere to unpack the archive: the toolchain"
                    + " directory could not be created when STEPSS started.");
            return;
        }
        my.stepss.ssa.SsaArchive.Loaded loaded;
        try {
            loaded = my.stepss.ssa.SsaArchive.load(archive, toolDir);
        } catch (IOException ex) {
            banner.warn(ex.getMessage());
            return;
        }

        my.stepss.ssa.SsaResultsWindow.open(this, loaded.results());
        banner.confirm(my.stepss.ssa.SsaArchive.describe(loaded.manifest(),
                archive.getName(), engineVersion));
    }//GEN-LAST:event_loadDynJacActionPerformed

    private void loadTrajToFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadTrajToFileButtonActionPerformed
        try {
            if (!savedOutputBool) {
                FileWriter outwriter;
                outwriter = new FileWriter(new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace"));
                outwriter.write(simulationOutput.getText());
                outwriter.close();
                savedOutputBool = true;
            }
            fileChooser.setSelectedFile(new File(""));
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Trajectory File", "trj");
            fileChooser.setFileFilter(filter);
            fileChooser.setDialogTitle("Choose Output Trajectory File");
            int returnVal = fileChooser.showOpenDialog(this);
            fileChooser.resetChoosableFileFilters();
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File srcFile = fileChooser.getSelectedFile();
                if (!srcFile.exists()) {
                    JOptionPane.showConfirmDialog(null, "File does not exist!", "File not found.", JOptionPane.OK_OPTION, JOptionPane.ERROR_MESSAGE);
                    return;
                }
                File destFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trj");
                if (destFile.exists()) {
                    int response = JOptionPane.showConfirmDialog(null, "Overwrite existing file?", "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (response == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }
                fileOps.copyFiletoFile(srcFile, destFile);
                saveTrajToFileButton.setEnabled(true);
                runDyngraphButton.setEnabled(true);
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadTrajToFileButtonActionPerformed

    private void saveCurrentCurveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveCurrentCurveButtonActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setDialogTitle("Choose Save File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int returnVal = fileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                File srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut.plt");
                File dstFile = new File(fileChooser.getSelectedFile().getAbsolutePath() + ".plt");
                if (srcFile.exists()) {
                    FileInputStream srcFileIn = new FileInputStream(srcFile);
                    String content = IOUtils.toString(srcFileIn, "UTF-8");
                    IOUtils.closeQuietly(srcFileIn);
                    String file_str = myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut.cur";
                    content = content.replaceAll(Pattern.quote(file_str),
                            Matcher.quoteReplacement(fileChooser.getSelectedFile().getName() + ".cur"));
                    FileOutputStream srcFileOut = new FileOutputStream(srcFile);
                    IOUtils.write(content, srcFileOut, "UTF-8");
                    IOUtils.closeQuietly(srcFileOut);
                    fileOps.copyFiletoFile(srcFile, dstFile);
                }
                srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut.cur");
                dstFile = new File(fileChooser.getSelectedFile().getAbsolutePath() + ".cur");
                if (srcFile.exists()) {
                    fileOps.copyFiletoFile(srcFile, dstFile);
                    srcFile.delete();
                }
                srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut.png");
                dstFile = new File(fileChooser.getSelectedFile().getAbsolutePath() + ".png");
                if (srcFile.exists()) {
                    fileOps.copyFiletoFile(srcFile, dstFile);
                    srcFile.delete();
                }
            } catch (IOException ex) {
                Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }//GEN-LAST:event_saveCurrentCurveButtonActionPerformed

    private void clearGnuplotButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearGnuplotButtonActionPerformed
        PlatformLauncher.killByName(platform, "wgnuplot");
        PlatformLauncher.killByName(platform, "wgnuplotR");
        PlatformLauncher.killByName(platform, "pgnuplot");
        PlatformLauncher.killByName(platform, "gnuplot");
    }//GEN-LAST:event_clearGnuplotButtonActionPerformed

    private void saveTrajToFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveTrajToFileButtonActionPerformed
        try {
            if (!savedOutputBool) {
                FileWriter outwriter;
                outwriter = new FileWriter(new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace"));
                outwriter.write(simulationOutput.getText());
                outwriter.close();
                savedOutputBool = true;
            }
            fileChooser.setSelectedFile(new File(""));
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Trajectory File", "trj");
            fileChooser.setFileFilter(filter);
            fileChooser.setDialogTitle("Choose Output Trajectory File");
            int returnVal = fileChooser.showSaveDialog(this);
            fileChooser.resetChoosableFileFilters();
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File destFile = fileChooser.getSelectedFile();
                String filePath = destFile.getPath();
                if (!filePath.toLowerCase().endsWith(".trj")) {
                    destFile = new File(filePath + ".trj");
                }
                if (destFile.exists()) {
                    int response = JOptionPane.showConfirmDialog(null, "Overwrite existing file?", "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (response == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }
                File srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trj");
                if (!srcFile.exists()) {
                    JOptionPane.showMessageDialog(this, "<html>Trajectory file not found. Sorry. Report this.</html>", "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    fileOps.copyFiletoFile(srcFile, destFile);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_saveTrajToFileButtonActionPerformed

    private void viewCurvesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewCurvesButtonActionPerformed
        try {
            if (gnuplotExec != null && gnuplotExec.exists()) {
                //                String command = gnuplotExec.getAbsolutePath() + " -persist " + myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut.plt";
                //                Runtime rt = Runtime.getRuntime();
                //                rt.exec(command);
                //                Process p = new ProcessBuilder(command).start();
                CommandLine command = new CommandLine(gnuplotExec.getAbsolutePath());
                command.addArgument("-persist");
                command.addArgument(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut.plt");
                DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
                DefaultExecutor exec = new DefaultExecutor();
                ShutdownHookProcessDestroyer processDestroyer = new ShutdownHookProcessDestroyer();
                PumpStreamHandler streamHandler = new PumpStreamHandler();
                exec.setStreamHandler(streamHandler);
                exec.setProcessDestroyer(processDestroyer);
                exec.setWorkingDirectory(myTempDir);
                exec.execute(command, WinEnvironment, resultHandler);
            } else {
                JOptionPane.showMessageDialog(this, "<html>The file <B>gnuplot</B> does not exist.</html>", "Executable not found!", JOptionPane.ERROR_MESSAGE);
            }

        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_viewCurvesButtonActionPerformed

    private void runDyngraphButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runDyngraphButtonActionPerformed
        if (dyngraphExec == null || !dyngraphExec.exists()) {
            JOptionPane.showMessageDialog(this, "<html>The file <B>dyngraph</B> does not exist.</html>", "Executable not found!", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // The single trajectory contract: a saved .trj is copied here on
        // load, so the picker needs no file chooser of its own.
        final File trajectory = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trj");
        if (!trajectory.exists()) {
            // Checked before --list ever runs, not inferred from its exit
            // status afterwards: a missing trajectory and a too-old
            // DYNGRAPH binary both exit non-zero with empty stdout (see
            // openPickerFromListing), so without this upfront check a
            // simulation that aborted before writing output.trj - reachable
            // in normal use, since runSimulationActionPerformed enables this
            // button on the "Save output trajectories" checkbox alone, not
            // on the file existing - would be misreported as an unsupported
            // binary.
            JOptionPane.showMessageDialog(this,
                    "<html>No trajectory was found to extract curves from:<br>"
                    + escapeHtml(trajectory.getAbsolutePath())
                    + "<br>Run a simulation with <B>Save output trajectories</B> enabled first.</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final DyngraphRunner runner = new DyngraphRunner(dyngraphExec, myTempDir, WinEnvironment);

        // get_observ_name rewinds the trajectory several times, so --list
        // scales with file size and must not run on the EDT: a SwingWorker
        // with a wait cursor, and the picker opens from done() on the EDT.
        runDyngraphButton.setEnabled(false);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<DyngraphRunner.ListResult, Void>() {
            @Override
            protected DyngraphRunner.ListResult doInBackground() throws IOException {
                return runner.list(trajectory);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                runDyngraphButton.setEnabled(true);
                DyngraphRunner.ListResult listing;
                try {
                    listing = get();
                } catch (Exception ex) {
                    Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
                    JOptionPane.showMessageDialog(StepssUI.this,
                            "<html>Could not run <B>dyngraph --list</B>:<br>"
                            + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                            "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                openPickerFromListing(runner, trajectory, listing);
            }
        }.execute();
    }//GEN-LAST:event_runDyngraphButtonActionPerformed

    /**
     * Continues Extract Curves on the EDT once {@code --list} has returned.
     * Only reached once {@code runDyngraphButtonActionPerformed} has
     * confirmed the trajectory exists, so a non-zero exit with empty stdout
     * here is the genuine "binary too old" case, not a missing trajectory:
     * an old DYNGRAPH ignores {@code --list}, writes its filename prompt to
     * stderr (main.f90 sets {@code log=0}), hits EOF on its closed stdin and
     * exits non-zero with stdout empty.
     *
     * <p>Detection stays exit-status-first, not header-first: the header
     * check (inside ObservableIndex.parse) only catches a zero-exit program
     * that printed something unexpected, never a non-zero exit. Every
     * failure is a modal dialog leaving no partial state; the picker never
     * opens on one.
     */
    private void openPickerFromListing(DyngraphRunner runner, File trajectory,
            DyngraphRunner.ListResult listing) {
        if (listing.exitCode != 0) {
            String reason;
            if (listing.stdout.trim().isEmpty()) {
                reason = "The bundled DYNGRAPH does not support <B>--list</B>."
                        + "<br>It reported:<br>" + escapeHtml(listing.stderr);
            } else {
                reason = "<B>dyngraph --list</B> failed (exit " + listing.exitCode
                        + "):<br>" + escapeHtml(listing.stderr);
            }
            JOptionPane.showMessageDialog(this, "<html>" + reason + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ObservableIndex index;
        try {
            index = ObservableIndex.parse(listing.stdout);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "<html>The observable index could not be read:<br>"
                    + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (index.isEmpty()) {
            banner.warn("<html>The trajectory file carries no observables to plot.</html>");
            return;
        }
        java.util.List<Selection> selections = ObservablePicker.show(this, index);
        if (selections == null || selections.isEmpty()) {
            return; // cancelled; nothing changed
        }
        startPlotRun(runner, trajectory, selections);
    }

    /**
     * Writes the replay file and starts the non-interactive {@code -t} run.
     *
     * <p>The output base is the fixed, absolute {@code <temp>/tempGnupOut}:
     * not a free parameter, because viewCurvesButton opens tempGnupOut.plt
     * by name and saveCurrentCurveButton rewrites the .plt by
     * string-replacing the absolute tempGnupOut.cur path inside it. A
     * relative base, or any other name, still plots correctly and silently
     * breaks both buttons.
     *
     * <p>Line 1 of the replay file is the trajectory's bare name
     * ({@code trajectory.getName()}, always {@code "output.trj"}), not its
     * absolute path: DyngraphRunner runs with {@code myTempDir} as the
     * working directory, so the relative name resolves the same way, and
     * being pure ASCII it survives {@link ReplayFile#CHARSET}
     * (ISO-8859-1) untouched. An absolute path here would mangle to '?' on
     * any byte outside Latin-1 - a working directory such as
     * {@code .../Πέτρος/} - leaving DYNGRAPH unable to open the trajectory
     * and the plot run failing with a raw Fortran backtrace.
     */
    private void startPlotRun(DyngraphRunner runner, File trajectory,
            java.util.List<Selection> selections) {
        File selCmd = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "sel.cmd");
        File outputBase = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "tempGnupOut");
        try {
            // sel.cmd is written into the temp directory and left there
            // after the run, like output.trj and tempGnupOut.*: it is the
            // first thing worth looking at when a plot comes out wrong.
            ReplayFile.write(selCmd, trajectory.getName(), selections);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not write the selection file:<br>"
                    + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Disabled before the run starts, not merely left alone: after a
        // previous successful extraction they are enabled, and a failed
        // re-run must not leave them pointing at the stale tempGnupOut.plt
        // and .cur that DYNGRAPH may have truncated or half-written.
        viewCurvesButton.setEnabled(false);
        saveCurrentCurveButton.setEnabled(false);
        runDyngraphButton.setEnabled(false);
        try {
            runner.plot(selCmd, outputBase, new DyngraphRunner.PlotListener() {
                public void onFinished(final int exitCode, final String stderr) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            runDyngraphButton.setEnabled(true);
                            if (exitCode == 0) {
                                // Success is silent, exactly as today: the
                                // result buttons light up and that is all.
                                viewCurvesButton.setEnabled(true);
                                saveCurrentCurveButton.setEnabled(true);
                            } else {
                                JOptionPane.showMessageDialog(StepssUI.this,
                                        "<html>Curve extraction failed (exit " + exitCode
                                        + "):<br>" + escapeHtml(stderr) + "</html>",
                                        "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    });
                }
            });
        } catch (IOException ex) {
            runDyngraphButton.setEnabled(true);
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not start <B>dyngraph</B>:<br>"
                    + escapeHtml(String.valueOf(ex.getMessage())) + "</html>",
                    "Extract Curves failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchTextFieldActionPerformed
        try {
            highlighterIndex = simulationOutput.getText().indexOf(searchTextField.getText(), highlighterIndex + highlighterLen);
            if (highlighterIndex >= 0) {
                highlighterLen = searchTextField.getText().length();
                highlighter.addHighlight(highlighterIndex, highlighterIndex + highlighterLen, DefaultHighlighter.DefaultPainter);
                caretToFirstWordOfLine(highlighterIndex);
            } else {
                if (highlighterLen == 0) {
                    banner.warn("\"" + searchTextField.getText() + "\" is not in the output.");
                    highlighterIndex = 0;
                    highlighterLen = 0;
                    highlighter.removeAllHighlights();
                } else {
                    banner.warn("No more matches. Press Enter to search from the top again.");
                    highlighterIndex = 0;
                    highlighter.removeAllHighlights();
                }
            }
        } catch (BadLocationException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_searchTextFieldActionPerformed

    /**
     * Puts the caret on the first word of the line holding {@code offset},
     * which is what scrolls that line into view: the highlight the search has
     * just painted is of no use to anyone while it sits off-screen.
     *
     * <p>The caret lands on the line's first non-blank character where the
     * line has one, and on the line start otherwise. {@code getNextWord}
     * reports "there is no next word" by throwing, which is what a match
     * inside the trailing whitespace of the output produces, and the line
     * start is the right place to stop there rather than something the caller
     * should be logging as a fault.
     */
    private void caretToFirstWordOfLine(int offset) throws BadLocationException {
        int lineStart = simulationOutput.getLineStartOffset(simulationOutput.getLineOfOffset(offset));
        simulationOutput.setCaretPosition(lineStart);
        if (lineStart < simulationOutput.getDocument().getLength()
                && Character.isWhitespace(simulationOutput.getText(lineStart, 1).charAt(0))) {
            try {
                simulationOutput.setCaretPosition(Utilities.getNextWord(simulationOutput, lineStart));
            } catch (BadLocationException noNextWord) {
                // Nothing to move to; the caret stays where the line starts.
            }
        }
    }

    private void searchTextFieldFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_searchTextFieldFocusLost
        if ("".equals(searchTextField.getText())) {
            searchTextField.setText("Search...");
        }
    }//GEN-LAST:event_searchTextFieldFocusLost

    private void searchTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_searchTextFieldFocusGained
        if (highlighterLen == 0) {
            searchTextField.setText("");
        }
        highlighterIndex = 0;
        highlighterLen = 0;
        highlighter = simulationOutput.getHighlighter();
        highlighter.removeAllHighlights();
    }//GEN-LAST:event_searchTextFieldFocusGained

    private void stopSimulationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopSimulationButtonActionPerformed
        try {
            File fileTemp = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + ".kill_RAMSES");
            if (fileTemp.exists()) {
                fileTemp.delete();
                PlatformLauncher.killByName(platform, "dynsim");
                fileTemp = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + ".lock_RAMSES");
                if (fileTemp.exists()) {
                    fileTemp.delete();
                }
                runSimulation.setEnabled(true);
            } else {
                fileTemp.createNewFile();
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_stopSimulationButtonActionPerformed

    private void loadDumpTraceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadDumpTraceButtonActionPerformed
        try {
            if (!savedOutputBool) {
                FileWriter outwriter;
                outwriter = new FileWriter(new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace"));
                outwriter.write(simulationOutput.getText());
                outwriter.close();
                savedOutputBool = true;
            }
            simulationOutput.setText("");
            BufferedReader dumpTraceFileBufReader;
            dumpTraceFileBufReader = new BufferedReader(new FileReader(new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "dump.trace")));
            String line;
            while ((line = dumpTraceFileBufReader.readLine()) != null) {
                simulationOutput.append(line);
                simulationOutput.append("\n");
            }
            dumpTraceFileBufReader.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadDumpTraceButtonActionPerformed

    private void clearSimulOutputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearSimulOutputActionPerformed
        simulationOutput.setText("");
        loadContTrace.setEnabled(false);
        loadDiscTrace.setEnabled(false);
        loadOutput.setEnabled(false);
        loadDumpTraceButton.setEnabled(false);
        clearSimulOutput.setEnabled(false);
        saveSimulOutput.setEnabled(false);
        stopSimulationButton.setEnabled(false);
        savedOutputBool = false;
        ArrayList<String> filesToDelete;
        filesToDelete = new ArrayList();
        filesToDelete.add("cont.trace");
        filesToDelete.add("disc.trace");
        filesToDelete.add("output.trace");
        filesToDelete.add("dump.trace");
        filesToDelete.add(".lock_RAMSES");
        File toDelete;
        for (String fileName : filesToDelete) {
            toDelete = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + fileName);
            if (toDelete.exists()) {
                toDelete.delete();
            }
        }
        runSimulation.setEnabled(true);
    }//GEN-LAST:event_clearSimulOutputActionPerformed

    private void loadDiscTraceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadDiscTraceActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(simulationOutput.getText());
                outwriter.close();
                savedOutputBool = true;
            }
            simulationOutput.setText("");
            File traceFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "disc.trace");
            BufferedReader traceFileBufReader = new BufferedReader(new FileReader(traceFile));
            String line;
            while ((line = traceFileBufReader.readLine()) != null) {
                simulationOutput.append(line);
                simulationOutput.append("\n");
            }
            traceFileBufReader.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadDiscTraceActionPerformed

    private void loadContTraceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadContTraceActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(simulationOutput.getText());
                outwriter.close();
                savedOutputBool = true;
            }
            simulationOutput.setText("");
            File traceFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "cont.trace");
            BufferedReader traceFileBufReader = new BufferedReader(new FileReader(traceFile));
            String line;
            while ((line = traceFileBufReader.readLine()) != null) {
                simulationOutput.append(line);
                simulationOutput.append("\n");
            }
            traceFileBufReader.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadContTraceActionPerformed

    private void loadOutputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadOutputActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(simulationOutput.getText());
                outwriter.close();
                savedOutputBool = true;
            } else {
                simulationOutput.setText("");
                File traceFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                BufferedReader traceFileBufReader = new BufferedReader(new FileReader(traceFile));
                String line;
                while ((line = traceFileBufReader.readLine()) != null) {
                    simulationOutput.append(line);
                    simulationOutput.append("\n");
                }
                traceFileBufReader.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadOutputActionPerformed

    private void saveSimulOutputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSimulOutputActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(simulationOutput.getText());
                outwriter.close();
                savedOutputBool = true;
            }
            fileChooser.setSelectedFile(new File(""));
            fileChooser.setDialogTitle("Choose File");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int returnVal = fileChooser.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (file.exists()) {
                    int response = JOptionPane.showConfirmDialog(null, "Overwrite existing file?", "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (response == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }
                FileWriter ryt = new FileWriter(file);
                BufferedWriter out = new BufferedWriter(ryt);
                out.write(simulationOutput.getText());
                out.flush();
                out.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_saveSimulOutputActionPerformed

    private void runSimulationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runSimulationActionPerformed
        String problem = createCommandFile();
        if (problem != null) {
            banner.warn(problem);
            return;
        }
        CommandLine command;

        //        simulationOutput.setText("");
        loadOutputActionPerformed(evt);
        savedOutputBool = false;

        if (ramsesExec == null || !ramsesExec.exists()) {
            JOptionPane.showMessageDialog(this, "<html>The file <B>dynsim</B> does not exist. .</html>", "Executable not found!", JOptionPane.ERROR_MESSAGE);
            return;
        } else {
            command = new CommandLine(ramsesExec.getAbsolutePath());
        }

        command.addArgument("-t" + myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "cmd.txt");

        simulExecutorResultHandler = new DefaultExecuteResultHandler();
        simulExecutor = new DefaultExecutor();
        simulExecutor.setExitValue(1);
        ShutdownHookProcessDestroyer processDestroyer = new ShutdownHookProcessDestroyer();
        if (!ssa) {
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputstream, outputstreamErr);
            simulExecutor.setStreamHandler(streamHandler);
        } else {
            ssa = false;
        }
        simulExecutor.setWorkingDirectory(myTempDir);
        simulExecutor.setProcessDestroyer(processDestroyer);
        try {
            simulExecutor.execute(command, WinEnvironment, simulExecutorResultHandler);
            statusBar.running("Simulating");
            runSimulation.setEnabled(false);
            runDyngraphButton.setEnabled(false);
            saveTrajToFileButton.setEnabled(false);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        clearSimulOutput.setEnabled(true);
        loadOutput.setEnabled(true);
        saveSimulOutput.setEnabled(true);
        stopSimulationButton.setEnabled(true);
        savedOutputBool = false;
        if (saveDumpButton.isSelected()) {
            loadDumpTraceButton.setEnabled(true);
        } else {
            loadDumpTraceButton.setEnabled(false);
        }
        (new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                    File f = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + ".lock_RAMSES");
                    while (f.exists()) {
                        Thread.sleep(2000);
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
                }
                // Marshalled rather than called straight from this thread.
                // Swing is not thread safe and these are Swing calls; they
                // have always been made from here, which mostly worked and
                // was never right.
                SwingUtilities.invokeLater(() -> {
                    statusBar.finished("Simulation finished");
                    runSimulation.setEnabled(true);
                    saveTrajToFileButton.setEnabled(true);
                    if (saveOutputTrajButton.isSelected()) {
                        saveTrajToFileButton.setEnabled(true);
                        runDyngraphButton.setEnabled(true);
                    } else {
                        saveTrajToFileButton.setEnabled(false);
                        runDyngraphButton.setEnabled(false);
                    }
                });
            }
        }).start();
    }//GEN-LAST:event_runSimulationActionPerformed

    private void nppObsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppObsButtonActionPerformed
        nppOpen(evt, fileObs.getText());
    }//GEN-LAST:event_nppObsButtonActionPerformed

    private void allInjCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allInjCheckBoxActionPerformed
        if (allInjCheckBox.isSelected()) {
            injObsField.setText("");
            injObsField.setEnabled(false);
            injObsList.setEnabled(false);
        } else {
            injObsField.setEnabled(true);
            injObsList.setEnabled(true);
        }
    }//GEN-LAST:event_allInjCheckBoxActionPerformed

    private void allBranchCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allBranchCheckBoxActionPerformed
        if (allBranchCheckBox.isSelected()) {
            branchObsField.setText("");
            branchObsField.setEnabled(false);
            branchObsList.setEnabled(false);
        } else {
            branchObsField.setEnabled(true);
            branchObsList.setEnabled(true);
        }
    }//GEN-LAST:event_allBranchCheckBoxActionPerformed

    private void allShuntCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allShuntCheckBoxActionPerformed
        if (allShuntCheckBox.isSelected()) {
            shuntObsField.setText("");
            shuntObsField.setEnabled(false);
            shuntObsList.setEnabled(false);
        } else {
            shuntObsField.setEnabled(true);
            shuntObsList.setEnabled(true);
        }
    }//GEN-LAST:event_allShuntCheckBoxActionPerformed

    private void allSyncCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allSyncCheckBoxActionPerformed
        if (allSyncCheckBox.isSelected()) {
            syncObsField.setText("");
            syncObsField.setEnabled(false);
            syncObsList.setEnabled(false);
        } else {
            syncObsField.setEnabled(true);
            syncObsList.setEnabled(true);
        }
    }//GEN-LAST:event_allSyncCheckBoxActionPerformed

    private void allBusCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allBusCheckBoxActionPerformed
        if (allBusCheckBox.isSelected()) {
            busObsField.setText("");
            busObsField.setEnabled(false);
            busObsList.setEnabled(false);
        } else {
            busObsField.setEnabled(true);
            busObsList.setEnabled(true);
        }
    }//GEN-LAST:event_allBusCheckBoxActionPerformed

    private void remInjObsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remInjObsActionPerformed
        injObsList.removeItemAt(injObsList.getSelectedIndex());
    }//GEN-LAST:event_remInjObsActionPerformed

    private void addInjButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addInjButtonActionPerformed
        if (!injObsField.getText().equals("")) {
            for (int i = 0; i < injObsList.getItemCount(); i++) {
                if (injObsField.getText().equals((injObsList.getItemAt(i).toString()))) {
                    injObsField.setText("Already in List!");
                    return;
                }
            }
            injObsList.addItem(injObsField.getText());
            injObsField.setText("");
        }
    }//GEN-LAST:event_addInjButtonActionPerformed

    private void remBranchObsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remBranchObsActionPerformed
        branchObsList.removeItemAt(branchObsList.getSelectedIndex());
    }//GEN-LAST:event_remBranchObsActionPerformed

    private void addBranchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addBranchButtonActionPerformed
        if (!branchObsField.getText().equals("")) {
            for (int i = 0; i < branchObsList.getItemCount(); i++) {
                if (branchObsField.getText().equals((branchObsList.getItemAt(i).toString()))) {
                    branchObsField.setText("Already in List!");
                    return;
                }
            }
            branchObsList.addItem(branchObsField.getText());
            branchObsField.setText("");
        }
    }//GEN-LAST:event_addBranchButtonActionPerformed

    private void remShuntObsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remShuntObsActionPerformed
        shuntObsList.removeItemAt(shuntObsList.getSelectedIndex());
    }//GEN-LAST:event_remShuntObsActionPerformed

    private void addShuntButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addShuntButtonActionPerformed
        if (!shuntObsField.getText().equals("")) {
            for (int i = 0; i < shuntObsList.getItemCount(); i++) {
                if (shuntObsField.getText().equals((shuntObsList.getItemAt(i).toString()))) {
                    shuntObsField.setText("Already in List!");
                    return;
                }
            }
            shuntObsList.addItem(shuntObsField.getText());
            shuntObsField.setText("");
        }
    }//GEN-LAST:event_addShuntButtonActionPerformed

    private void remSyncObsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remSyncObsActionPerformed
        syncObsList.removeItemAt(syncObsList.getSelectedIndex());
    }//GEN-LAST:event_remSyncObsActionPerformed

    private void addSyncButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addSyncButtonActionPerformed
        if (!syncObsField.getText().equals("")) {
            for (int i = 0; i < syncObsList.getItemCount(); i++) {
                if (syncObsField.getText().equals((syncObsList.getItemAt(i).toString()))) {
                    // syncObsField, not busObsField: this is the one of the
                    // five copies of this handler that was never re-pointed
                    // after being pasted, so a duplicate machine name put the
                    // notice in the Bus row and overwrote whatever was typed
                    // there, while the Sync row said nothing at all.
                    syncObsField.setText("Already in List!");
                    return;
                }
            }
            syncObsList.addItem(syncObsField.getText());
            syncObsField.setText("");
        }
    }//GEN-LAST:event_addSyncButtonActionPerformed

    private void remBusObsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remBusObsActionPerformed
        busObsList.removeItemAt(busObsList.getSelectedIndex());
    }//GEN-LAST:event_remBusObsActionPerformed

    private void addBusButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addBusButtonActionPerformed
        if (!busObsField.getText().equals("")) {
            for (int i = 0; i < busObsList.getItemCount(); i++) {
                if (busObsField.getText().equals((busObsList.getItemAt(i).toString()))) {
                    busObsField.setText("Already in List!");
                    return;
                }
            }
            busObsList.addItem(busObsField.getText());
            busObsField.setText("");
        }
    }//GEN-LAST:event_addBusButtonActionPerformed

    private void observFileWizButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_observFileWizButtonActionPerformed
        if (observFileWizButton.isSelected()) {
            jPanel7.setVisible(true);
            saveOutputTrajButton.setSelected(true);
        } else {
            jPanel7.setVisible(false);
            if (fileObs.getText().equals("")) {
                saveOutputTrajButton.setSelected(false);
            }
        }
    }//GEN-LAST:event_observFileWizButtonActionPerformed

    private void loadObsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadObsButtonActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Observables File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileObs.setText(file.getAbsolutePath());
        } else {
            fileObs.setText("");
        }
        if (!fileObs.getText().equals("")) {
            saveOutputTrajButton.setSelected(true);
        }
    }//GEN-LAST:event_loadObsButtonActionPerformed

    private void saveOutputTrajButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveOutputTrajButtonActionPerformed
        if (!saveOutputTrajButton.isSelected()) {
            clearObsFileButtonActionPerformed(evt);
        } else {
            banner.warn("A trajectory will be saved, so an observables file is"
                    + " needed. Load one on the Observables tab, or use the"
                    + " observable dialog.");
        }
    }//GEN-LAST:event_saveOutputTrajButtonActionPerformed

    private void clearObsFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearObsFileButtonActionPerformed
        fileObs.setText("");
        // All three rows. The loop below walks jPanel7, the wizard panel, and
        // the runtime observable rows are not in it, so clearing row one alone
        // left rows two and three still naming equipment and the next run
        // still plotting them, right after the user pressed Clear.
        runtimeObsName.setText("");
        runtimeObsName1.setText("");
        runtimeObsName2.setText("");
        for (Component c : jPanel7.getComponents()) {
            if (c instanceof JTextField) {
                ((JTextField) c).setText("");
                ((JTextField) c).setEnabled(true);
            } else if (c instanceof JComboBox) {
                ((JComboBox) c).removeAllItems();
                ((JComboBox) c).setEnabled(true);
            } else if (c instanceof JCheckBox) {
                ((JCheckBox) c).setSelected(false);
            }
        }
        saveOutputTrajButton.setSelected(false);
        observFileWizButton.setSelected(false);
        saveDumpButton.setSelected(false);
        observFileWizButtonActionPerformed(null);
    }//GEN-LAST:event_clearObsFileButtonActionPerformed

    private void nppData10ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData10ButtonActionPerformed
        nppOpen(evt, fileData10.getText());
    }//GEN-LAST:event_nppData10ButtonActionPerformed

    private void nppData9ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData9ButtonActionPerformed
        nppOpen(evt, fileData9.getText());
    }//GEN-LAST:event_nppData9ButtonActionPerformed

    private void loadData9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData9ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData9.setText(file.getAbsolutePath());
        } else {
            fileData9.setText("");
        }
    }//GEN-LAST:event_loadData9ActionPerformed

    private void nppData8ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData8ButtonActionPerformed
        nppOpen(evt, fileData8.getText());
    }//GEN-LAST:event_nppData8ButtonActionPerformed

    private void loadData8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData8ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData8.setText(file.getAbsolutePath());
        } else {
            fileData8.setText("");
        }
    }//GEN-LAST:event_loadData8ActionPerformed

    private void nppData7ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData7ButtonActionPerformed
        nppOpen(evt, fileData7.getText());
    }//GEN-LAST:event_nppData7ButtonActionPerformed

    private void loadData7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData7ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData7.setText(file.getAbsolutePath());
        } else {
            fileData7.setText("");
        }
    }//GEN-LAST:event_loadData7ActionPerformed

    private void nppData6ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData6ButtonActionPerformed
        nppOpen(evt, fileData6.getText());
    }//GEN-LAST:event_nppData6ButtonActionPerformed

    private void loadData6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData6ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData6.setText(file.getAbsolutePath());
        } else {
            fileData6.setText("");
        }
    }//GEN-LAST:event_loadData6ActionPerformed

    private void loadDistActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadDistActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Disturbance File", "dst");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Disturbance File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileDist.setText(file.getAbsolutePath());
        } else {
            fileDist.setText("");
        }
    }//GEN-LAST:event_loadDistActionPerformed

    private void nppDstButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppDstButtonActionPerformed
        nppOpen(evt, fileDist.getText());
    }//GEN-LAST:event_nppDstButtonActionPerformed

    private void nppData5ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData5ButtonActionPerformed
        nppOpen(evt, fileData5.getText());
    }//GEN-LAST:event_nppData5ButtonActionPerformed

    private void nppData4ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData4ButtonActionPerformed
        nppOpen(evt, fileData4.getText());
    }//GEN-LAST:event_nppData4ButtonActionPerformed

    private void nppData3ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData3ButtonActionPerformed
        nppOpen(evt, fileData3.getText());
    }//GEN-LAST:event_nppData3ButtonActionPerformed

    private void nppData2ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData2ButtonActionPerformed
        nppOpen(evt, fileData2.getText());
    }//GEN-LAST:event_nppData2ButtonActionPerformed

    private void nppData1ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nppData1ButtonActionPerformed
        nppOpen(evt, fileData1.getText());
    }//GEN-LAST:event_nppData1ButtonActionPerformed

    private void loadData5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData5ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData5.setText(file.getAbsolutePath());
        } else {
            fileData5.setText("");
        }
    }//GEN-LAST:event_loadData5ActionPerformed

    private void clearDataFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearDataFilesActionPerformed
        for (JTextField s : dataFileList) {
            s.setText("");
        }
        fileDist.setText("");
    }//GEN-LAST:event_clearDataFilesActionPerformed

    private void loadData4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData4ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData4.setText(file.getAbsolutePath());
        } else {
            fileData4.setText("");
        }
    }//GEN-LAST:event_loadData4ActionPerformed

    private void loadData3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData3ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData3.setText(file.getAbsolutePath());
        } else {
            fileData3.setText("");
        }
    }//GEN-LAST:event_loadData3ActionPerformed

    private void loadData2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData2ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData2.setText(file.getAbsolutePath());
        } else {
            fileData2.setText("");
        }
    }//GEN-LAST:event_loadData2ActionPerformed

    private void loadData1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadData1ActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Data File", "dat");
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogTitle("Choose Data File");
        int returnVal = fileChooser.showOpenDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            fileData1.setText(file.getAbsolutePath());
        } else {
            fileData1.setText("");
        }
    }//GEN-LAST:event_loadData1ActionPerformed

    /**
     * Deletes every file a previous power-flow run may have left in
     * {@code myTempDir}: the four legacy-named .res exports, the in_svc.res
     * export added for helios' per-table export mechanism, and the
     * in_volt_trfo.dat file whose appearance signals a completed run (see
     * the completion thread in {@link #runPFActionPerformed}). Called at the
     * start of a run so a run that helios aborts partway through cannot
     * leave any of the previous run's results looking like they belong to
     * the run just made.
     */
    private void deletePFCResultFiles() {
        String[] resultFiles = {
            "in_net.res", "in_trfo.res", "in_gen.res", "in_bal.res",
            "in_svc.res", "in_volt_trfo.dat"
        };
        for (String name : resultFiles) {
            Path p = Paths.get(myTempDir.getAbsolutePath(), name);
            if (Files.exists(p)) {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private void runPFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runPFActionPerformed
        String problem = createPFCCommandFile();
        if (problem != null) {
            banner.warn(problem);
            return;
        }
        CommandLine command;

        //        simulationOutput.setText("");
        fileData10.setText("");

        // No loadOutputActionPerformed / savedOutputBool here. That pair was
        // pasted from runSimulationActionPerformed, where it belongs: both act
        // on the Dynamic Simulation console and its output.trace backup, which
        // a power-flow run has nothing to do with. It meant pressing Run power
        // flow rewrote output.trace with the simulation console's text, and,
        // once the user had loaded a trace, silently refilled the simulation
        // console from the old run on a tab they were not looking at. This
        // tab's own reset is clearPFCOutputActionPerformed, below.

        // helios exits 0 even when it fails to converge, and can abort before
        // its final VT export; without this, a leftover .res/.dat file from
        // the previous run would still be sitting there for the load buttons
        // to display as if it belonged to the run just made. Clearing the
        // pane and disabling every result-loading button up front (reusing
        // the same reset the "Clear All" button performs) and deleting every
        // prior result file means nothing stale can survive into this run;
        // the completion thread below re-enables the buttons once helios'
        // own export produces fresh files.
        clearPFCOutputActionPerformed(evt);
        deletePFCResultFiles();

        if (heliosExec == null || !heliosExec.exists()) {
            JOptionPane.showMessageDialog(this, "<html>The file <B>helios</B> does not exist.</html>", "Executable not found!", JOptionPane.ERROR_MESSAGE);
            return;
        } else {
            command = new CommandLine(heliosExec.getAbsolutePath());
        }

        // helios matches "-t" as its own argv token and takes the path as the next
        // one; the concatenated Fortran form "-t<path>" is never recognised.
        command.addArgument("-t");
        command.addArgument(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "PFCcmd.txt");

        simulExecutorResultHandler = new DefaultExecuteResultHandler();
        simulExecutor = new DefaultExecutor();
        // helios' documented exit-status contract (../stepss-helios/README.md,
        // docs/tui-guide.md#exit-status): 0 is the only success value. 1 and 2
        // are both failures as far as Commons Exec is concerned; the distinction
        // between them is reported to the user by reportHeliosExitStatus below.
        simulExecutor.setExitValue(0);
        ShutdownHookProcessDestroyer processDestroyer = new ShutdownHookProcessDestroyer();
        // stderr also goes through a tee into heliosStderr so the completion
        // thread can look for the "helios: status: ..." line without disturbing
        // what already gets shown, merged with stdout, in pfcPane.
        final ByteArrayOutputStream heliosStderr = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputstreamPFC,
                new TeeOutputStream(outputstreamPFCErr, heliosStderr));
        simulExecutor.setStreamHandler(streamHandler);

        simulExecutor.setWorkingDirectory(myTempDir);
        simulExecutor.setProcessDestroyer(processDestroyer);
        // Captured so the completion thread below reports on the run this
        // invocation started, even if the shared simulExecutorResultHandler
        // field is reassigned by another action before the thread wakes up.
        final DefaultExecuteResultHandler resultHandler = simulExecutorResultHandler;
        try {
            simulExecutor.execute(command, WinEnvironment, simulExecutorResultHandler);
            statusBar.running("Solving power flow");
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
        (new Thread() {
            @Override
            public void run() {
                File f = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_volt_trfo.dat");
                try {
                    Thread.sleep(2000);
                    // Also bail out once helios has exited: on exit 1 (input/usage
                    // error) there may be no results at all, so in_volt_trfo.dat can
                    // legitimately never appear and this loop must not wait forever.
                    while (!f.exists() && !resultHandler.hasResult()) {
                        Thread.sleep(1000);
                    }
                    if (f.exists()) {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (f.exists()) {
                    SwingUtilities.invokeLater(() -> {
                        loadOutput.setEnabled(true);
                        loadBusOverview.setEnabled(true);
                        loadGens.setEnabled(true);
                        loadTrfos.setEnabled(true);
                        loadPow.setEnabled(true);
                        loadLFRESV2DAT.setEnabled(true);
                    });
                    statusBar.finished("Power flow finished");
                } else {
                    statusBar.failed("Power flow produced no results");
                }

                reportHeliosExitStatus(resultHandler, heliosStderr);
            }
        }).start();
        clearPFCOutput.setEnabled(true);

    }//GEN-LAST:event_runPFActionPerformed

    /**
     * Matches helios' machine-readable status line, written once to stderr on
     * every non-interactive run:
     * {@code helios: status: CONVERGED (2 iterations)} or
     * {@code helios: status: NOT_CONVERGED (max iterations)}. Group 1 is the
     * token ({@code CONVERGED}/{@code NOT_CONVERGED}/{@code NOT_RUN}), group 2
     * the optional parenthesised detail. See
     * ../stepss-helios/docs/tui-guide.md#exit-status.
     */
    private static final Pattern HELIOS_STATUS_LINE = Pattern.compile("helios: status: (\\S+)(?: \\(([^)]*)\\))?");

    /**
     * Reports the outcome of the helios run started by
     * {@link #runPFActionPerformed} per its normative exit-status contract
     * (../stepss-helios/README.md, docs/tui-guide.md#exit-status,
     * docs/api-reference.md#shared-status-contract-api-and-cli):
     * <ul>
     * <li>0 (converged): silent, exactly as before the contract existed.</li>
     * <li>2 (did not converge): a prominent warning — helios still exported
     * result files and the GUI will display them, but they are not a valid
     * solution.</li>
     * <li>1 (input/usage error): an error explaining there may be no results
     * at all.</li>
     * <li>anything else: a generic failure naming the exit value.</li>
     * </ul>
     * The pinned helios v1.2.0 predates this contract: it always exits 0 and
     * never writes the status line, so this method is a no-op with it, and
     * starts reporting the moment a contract-implementing release is pinned,
     * with no further code change.
     * <p>
     * Called off the EDT (from the completion thread above); every dialog is
     * dispatched with {@code invokeLater}, following the pattern used by the
     * launch-failure listener registered in the constructor.
     *
     * @param resultHandler the result handler for the run being reported on
     * @param heliosStderr the run's captured stderr, searched for the
     * {@code helios: status: ...} line
     */
    private void reportHeliosExitStatus(DefaultExecuteResultHandler resultHandler, ByteArrayOutputStream heliosStderr) {
        try {
            resultHandler.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!resultHandler.hasResult()) {
            return;
        }
        final HeliosStatusDialog dialog = describeHeliosExit(resultHandler.getExitValue(), heliosStderr.toString());
        if (dialog == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, dialog.message, dialog.title, dialog.messageType));
    }

    /**
     * Immutable description of the dialog {@link #reportHeliosExitStatus}
     * should show for a given helios exit status. A plain field holder
     * rather than a record: StepssUI targets Java 11 syntax.
     */
    static final class HeliosStatusDialog {

        final String title;
        final String message;
        final int messageType;

        HeliosStatusDialog(String title, String message, int messageType) {
            this.title = title;
            this.message = message;
            this.messageType = messageType;
        }
    }

    /**
     * Pure decision logic behind {@link #reportHeliosExitStatus}: given a
     * completed helios run's exit value and its raw captured stderr, decides
     * what to tell the user, per the normative exit-status contract in
     * ../stepss-helios/README.md and docs/tui-guide.md#exit-status. Touches
     * no Swing state, so it is exercised directly by tests without needing a
     * live GUI. Package-visible for that reason.
     *
     * @param exitValue the process exit value ({@link DefaultExecuteResultHandler#getExitValue()})
     * @param heliosStderrText the run's captured stderr, searched for the
     * {@code helios: status: ...} line
     * @return the dialog to show, or {@code null} for exit 0 (converged:
     * show nothing, exactly as before this contract existed)
     */
    static HeliosStatusDialog describeHeliosExit(int exitValue, String heliosStderrText) {
        if (exitValue == 0) {
            return null;
        }

        // The "helios: status: TOKEN (detail)" line, when present, distinguishes
        // *why* a non-converged run failed (max iterations, divergence, singular
        // Jacobian). It is not required: the pinned v1.2.0 binary never writes it.
        String statusDetail = null;
        Matcher statusMatcher = HELIOS_STATUS_LINE.matcher(heliosStderrText);
        if (statusMatcher.find()) {
            statusDetail = statusMatcher.group(2);
        }
        final String detailSuffix = (statusDetail == null || statusDetail.isEmpty()) ? "" : " (" + statusDetail + ")";

        switch (exitValue) {
            case 2:
                return new HeliosStatusDialog(
                        "Power Flow Did NOT Converge!",
                        "<html><body style='width: 350px'>"
                        + "<b><font color='red'>The power flow did NOT converge" + detailSuffix + ".</font></b>"
                        + "<br><br>helios still produced and exported result files, and the "
                        + "buttons below now show them, but <b>they are NOT a valid "
                        + "power-flow solution.</b> Do not use the displayed values."
                        + "</body></html>",
                        JOptionPane.WARNING_MESSAGE);
            case 1:
                return new HeliosStatusDialog(
                        "Helios Could Not Process The Input!",
                        "<html><body style='width: 350px'>"
                        + "helios reported an input or usage error and stopped early"
                        + detailSuffix + ". <b>There may be no results at all.</b>"
                        + "</body></html>",
                        JOptionPane.ERROR_MESSAGE);
            default:
                return new HeliosStatusDialog(
                        "Helios Exited Abnormally!",
                        "<html><body style='width: 350px'>"
                        + "helios exited with status " + exitValue + ", which is not a "
                        + "documented outcome. Treat any displayed results with suspicion."
                        + "</body></html>",
                        JOptionPane.ERROR_MESSAGE);
        }
    }

    // This and the three below it, loadGens, loadTrfos and loadPow, each clear
    // pfcPane and fill it from one of helios' .res exports. None of them backs
    // the pane up first, and none of them should.
    //
    // All four used to open output.trace and write pfcPane into it, pasted from
    // the Dynamic Simulation console's own save-and-restore where the same block
    // belongs. output.trace is that console's file: loadOutputActionPerformed is
    // the only thing that reads it, savedOutputBool is the only thing that says
    // whether it is current, and clearSimulOutput lists it as a file it owns.
    // Nothing on this tab ever read it back, so the copy served no purpose here
    // and each press overwrote the last one anyway.
    //
    // What kept that harmless was the one line the paste dropped: these four
    // never set savedOutputBool, so the flag stayed false, and a false flag is
    // what lets the simulation console overwrite the file with its own text
    // before ever reading it. Adding `savedOutputBool = true` here to make the
    // four consistent with their seven siblings looks like an obvious tidy-up
    // and is the bug: it would leave power-flow text in output.trace with the
    // flag claiming it is the simulation console's, and the next Load output
    // would print helios' log into the simulation pane. Deleting the blocks is
    // what removes that, so do not put them back.
    //
    // The four .res files survive in myTempDir until the next run, so every
    // table here can be shown again at will. Only helios' run log is lost when
    // the pane is cleared, because it is streamed straight into pfcPane and
    // written nowhere; Run power flow is what brings it back.
    private void loadBusOverviewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadBusOverviewActionPerformed
        try {
            pfcPane.setText("");
            File traceFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_net.res");
            BufferedReader traceFileBufReader = new BufferedReader(new FileReader(traceFile));
            String line;
            while ((line = traceFileBufReader.readLine()) != null) {
                pfcPane.append(line);
                pfcPane.append("\n");
            }
            traceFileBufReader.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadBusOverviewActionPerformed

    private void loadGensActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadGensActionPerformed
        try {
            pfcPane.setText("");

            File traceFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_gen.res");

            BufferedReader traceFileBufReader = new BufferedReader(new FileReader(traceFile));
            String line;
            while ((line = traceFileBufReader.readLine()) != null) {
                pfcPane.append(line);
                pfcPane.append("\n");
            }
            traceFileBufReader.close();

            // helios' export command overwrites, so the SVC table cannot share a
            // file with the generators the way it did under PFC; show it here.
            File svcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_svc.res");
            if (svcFile.exists() && svcFile.length() > 0) {
                BufferedReader svcBufReader = new BufferedReader(new FileReader(svcFile));
                pfcPane.append("\n");
                while ((line = svcBufReader.readLine()) != null) {
                    pfcPane.append(line);
                    pfcPane.append("\n");
                }
                svcBufReader.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadGensActionPerformed

    private void loadLFRESV2DATActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadLFRESV2DATActionPerformed
        fileData10.setText(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_volt_trfo.dat");
    }//GEN-LAST:event_loadLFRESV2DATActionPerformed

    private void loadTrfosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadTrfosActionPerformed
        try {
            pfcPane.setText("");

            File traceFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_trfo.res");

            BufferedReader traceFileBufReader = new BufferedReader(new FileReader(traceFile));
            String line;
            while ((line = traceFileBufReader.readLine()) != null) {
                pfcPane.append(line);
                pfcPane.append("\n");
            }
            traceFileBufReader.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadTrfosActionPerformed

    private void loadPowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadPowActionPerformed
        try {
            pfcPane.setText("");

            File traceFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_bal.res");

            BufferedReader traceFileBufReader = new BufferedReader(new FileReader(traceFile));
            String line;
            while ((line = traceFileBufReader.readLine()) != null) {
                pfcPane.append(line);
                pfcPane.append("\n");
            }
            traceFileBufReader.close();
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadPowActionPerformed

    private void loadCodegenFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadCodegenFilesActionPerformed
        File [] f90Files = myTempDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".f90"));
        for (File temp : f90Files) {
            temp.delete();
        }
        mfileChooser.setSelectedFile(new File(""));
        mfileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Codegen TXT File", "txt");
        mfileChooser.setFileFilter(filter);
        mfileChooser.setDialogTitle("Choose Data File");
        mfileChooser.setMultiSelectionEnabled(true);
        int returnVal = mfileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            codeGenFiles = mfileChooser.getSelectedFiles();
            codegenPane.setText("Loading Codegen Files:\n");
            for (File temp : codeGenFiles) {
                codegenPane.append(temp.getAbsolutePath()+"\n");
            }
            execCodegen.setEnabled(true);
            displayCGfiles.setEnabled(true);
        } else {
            codegenPane.setText("Error loading files.");
        }
    }//GEN-LAST:event_loadCodegenFilesActionPerformed

    private void displayCGfilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayCGfilesActionPerformed
        codegenPane.setText("Loaded Codegen Files:\n");
        File [] f90Files = myTempDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".f90"));
        for (File temp : f90Files) {
            codegenPane.append(temp.getAbsolutePath()+"\n");
        }
    }//GEN-LAST:event_displayCGfilesActionPerformed

    private void execCodegenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_execCodegenActionPerformed

    if (codegenExec == null) {
        codegenExec = toolchain.codegen();
    }

    String problem = createCGCommandFile();
    if (problem != null) {
        banner.warn(problem);
        return;
    }
    codegenPane.setText("");

    CommandLine command;
    if (!codegenExec.exists()) {
        JOptionPane.showMessageDialog(this, "<html>The file <B>CODEGEN</B> does not exist.</html>", "Executable not found!", JOptionPane.ERROR_MESSAGE);
        return;
    } else {
        command = new CommandLine(codegenExec.getAbsolutePath());
    }

    command.addArgument("-l" + myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "CGcmd.txt");

    // Compile is enabled from onProcessComplete below, once CODEGEN has
    // actually finished successfully, rather than unconditionally right
    // after execute() starts it - an immediate Compile press would
    // otherwise find no .f90 yet, or a half-written one. onProcessComplete
    // runs on Commons Exec's own watchdog thread, so the enable is
    // marshalled through invokeLater, and it defers to a build already in
    // progress rather than re-enabling Compile out from under it.
    // onProcessFailed mirrors that on the failure side, so a CODEGEN run
    // that genuinely fails leaves Compile disabled and says why, instead
    // of failing silently.
    simulExecutorResultHandler = new DefaultExecuteResultHandler() {
        @Override
        public void onProcessComplete(int exitValue) {
            super.onProcessComplete(exitValue);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (!compileInProgress) {
                        Compile.setEnabled(true);
                    }
                }
            });
        }

        @Override
        public void onProcessFailed(final ExecuteException ex) {
            super.onProcessFailed(ex);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    codegenPane.append("\nCODEGEN failed (exit " + ex.getExitValue() + ").\n");
                    if (!compileInProgress) {
                        Compile.setEnabled(false);
                    }
                }
            });
        }
    };
    simulExecutor = new DefaultExecutor();
    // CODEGEN's success exit code is 0, so that is what this executor must
    // treat as success; this was the same inverted-exit-value mistake already
    // fixed for the helios power-flow run (see runPFActionPerformed), just
    // missed here. With setExitValue(1) a genuinely successful CODEGEN run
    // (exit 0) dispatched to onProcessFailed instead of onProcessComplete,
    // so Compile could never be enabled by any reachable call site - the
    // whole feature was unreachable from a fresh launch.
    //
    // Not every executor in this class is set to 0: runSimulationActionPerformed
    // still uses setExitValue(1). That is pre-existing and deliberately left
    // alone - the only thing done with that handler is waitFor(), so nothing
    // there reads the complete/failed dispatch this value decides. Do not
    // "correct" it on the strength of this comment; it would be a behaviour
    // change to an unrelated path with no defect behind it.
    simulExecutor.setExitValue(0);
    ShutdownHookProcessDestroyer processDestroyer = new ShutdownHookProcessDestroyer();
    PumpStreamHandler streamHandler = new PumpStreamHandler(outputstreamCG, outputstreamCGErr);
    simulExecutor.setStreamHandler(streamHandler);

    simulExecutor.setWorkingDirectory(myTempDir);
    simulExecutor.setProcessDestroyer(processDestroyer);
    try {
        simulExecutor.execute(command, WinEnvironment, simulExecutorResultHandler);
    } catch (IOException ex) {
        Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
    }
    saveCGFiles.setEnabled(true);
    }//GEN-LAST:event_execCodegenActionPerformed

    private void saveCGFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveCGFilesActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setDialogTitle("Choose File Location");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnVal = fileChooser.showSaveDialog(this);
        codegenPane.setText("");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                File [] f90Files = myTempDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".f90"));
                for (File temp : f90Files) {
                    String flname = temp.getName();
                    File destFile = new File(fileChooser.getSelectedFile() + System.getProperty("file.separator") + flname);
                    codegenPane.append("Copying "+flname + " to "+destFile.toString()+ " ... ");
                    if (destFile.exists()) {
                        int response = JOptionPane.showConfirmDialog(null, "Overwrite existing file?", "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                        if (response == JOptionPane.CANCEL_OPTION) {
                            return;
                        }
                    }
                    if (!temp.exists()) {
                        JOptionPane.showMessageDialog(this, "<html>f90 file not found. Sorry. Report this.</html>", "Error", JOptionPane.ERROR_MESSAGE);
                    } else {
                        fileOps.copyFiletoFile(temp, destFile);
                        codegenPane.append("Complete.\n");
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }//GEN-LAST:event_saveCGFilesActionPerformed

    
    
    private void CompileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CompileActionPerformed
        if (compileInProgress) {
            banner.warn("<html>A build is already running. Wait for it to finish before"
                    + " starting another.</html>");
            return;
        }
        if (codeGenFiles == null || codeGenFiles.length == 0) {
            banner.warn("<html>No generated models to compile. Run Codegen first.</html>");
            return;
        }

        final java.util.List<File> generated = new java.util.ArrayList<File>();
        for (File temp : codeGenFiles) {
            String base = temp.getName().replaceFirst("[.][^.]+$", "");
            File f90 = new File(myTempDir, base + ".f90");
            if (!f90.isFile()) {
                JOptionPane.showMessageDialog(this,
                        "<html>Generated source <B>" + base + ".f90</B> was not found."
                        + "<br>Run Codegen again before compiling.</html>",
                        "Generated source missing", JOptionPane.ERROR_MESSAGE);
                return;
            }
            generated.add(f90);
        }

        compileInProgress = true;
        codegenPane.setText("Preparing the build kit...\n");
        Compile.setEnabled(false);
        // A stale success from an earlier compile must not look current
        // while this one is running, and must not survive if this one fails.
        savedynsim.setEnabled(false);
        // Remembered so every failure path below can restore the user's own
        // choice (e.g. one picked via Load External Simulator) instead of
        // discarding it. restoreExecOnFailure falls back to the bundled
        // engine only when previousExec itself was a previous compile's own
        // output inside the kit directory - a file prepare() is about to
        // delete regardless of whether this run succeeds.
        final File previousExec = ramsesExec;
        if (isInsideKitDirectory(previousExec)) {
            // prepare() is about to delete and rebuild the kit that
            // previousExec lives inside; point back at the bundled engine
            // right away, so a simulation launched mid-build (before this
            // compile has even succeeded or failed) can never target a
            // half-rebuilt or already-deleted tree.
            File bundled = toolchain.ramses();
            if (bundled != null) {
                ramsesExec = bundled;
            }
        }

        final ModelCompiler compiler = new ModelCompiler(toolchain.platform(), toolchain);
        modelCompiler = compiler;

        // prepare() re-unpacks the ~12 MB kit, and build() opens by probing
        // for a Fortran toolchain, which can spawn several gfortran
        // subprocesses each bounded at 30s - both block for long enough
        // that running them on the EDT would freeze the GUI with no
        // repaint (the "Preparing..." text above would never even appear).
        // The whole sequence therefore runs on a background thread, and
        // every Swing touch is marshalled back through invokeLater.
        //
        // prepare() and build() only throw the checked IOException, but an
        // unchecked RuntimeException escaping either would otherwise die on
        // this thread silently (no uncaught-exception handler is installed
        // here) with compileInProgress stuck true, wedging Compile behind
        // the "build is already running" guard for the rest of the session.
        // Both calls below are therefore also guarded by a RuntimeException
        // catch that clears the flag and reports the failure instead of
        // swallowing it, even though nothing in the current call graph is
        // known to throw one.
        new Thread("stepss-compile") {
            @Override
            public void run() {
                try {
                    compiler.prepare(generated);
                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            compileInProgress = false;
                            Compile.setEnabled(true);
                            ramsesExec = restoreExecOnFailure(previousExec);
                            codegenPane.append(ex.getMessage() + "\n");
                            JOptionPane.showMessageDialog(StepssUI.this,
                                    "<html>Could not prepare the build:<br>"
                                    + escapeHtml(ex.getMessage()) + "</html>",
                                    "Compilation failed", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    return;
                } catch (final RuntimeException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            compileInProgress = false;
                            Compile.setEnabled(true);
                            ramsesExec = restoreExecOnFailure(previousExec);
                            String detail = describe(ex);
                            codegenPane.append("Unexpected error: " + detail + "\n");
                            JOptionPane.showMessageDialog(StepssUI.this,
                                    "<html>An unexpected error interrupted the build:<br>"
                                    + escapeHtml(detail) + "</html>",
                                    "Compilation failed", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    return;
                }

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        codegenPane.append("Building custom simulator with gfortran...\n\n");
                    }
                });

                try {
                    compiler.build(new ModelCompiler.Listener() {
                        public void onOutput(final String line) {
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    codegenPane.append(line + "\n");
                                }
                            });
                        }

                        public void onFinished(final int exitCode, final File dynsim,
                                final String problem) {
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    reportCompileOutcome(exitCode, dynsim, problem, previousExec);
                                }
                            });
                        }
                    });
                } catch (final IOException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            compileInProgress = false;
                            Compile.setEnabled(true);
                            ramsesExec = restoreExecOnFailure(previousExec);
                            JOptionPane.showMessageDialog(StepssUI.this,
                                    "<html>Could not start the build:<br>"
                                    + escapeHtml(ex.getMessage()) + "</html>",
                                    "Compilation failed", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } catch (final RuntimeException ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            compileInProgress = false;
                            Compile.setEnabled(true);
                            ramsesExec = restoreExecOnFailure(previousExec);
                            String detail = describe(ex);
                            codegenPane.append("Unexpected error: " + detail + "\n");
                            JOptionPane.showMessageDialog(StepssUI.this,
                                    "<html>An unexpected error interrupted the build:<br>"
                                    + escapeHtml(detail) + "</html>",
                                    "Compilation failed", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        }.start();
    }//GEN-LAST:event_CompileActionPerformed

    /**
     * Adopts a freshly built simulator, or reports why there is not one. Always
     * runs on the EDT (see {@link ModelCompiler.Listener#onFinished}'s callers,
     * both wrapped in {@code invokeLater}). On any failure {@code ramsesExec} is
     * restored via {@link #restoreExecOnFailure}, so a failed compile never
     * leaves the app unable to simulate, and never silently discards a
     * simulator the user loaded themselves.
     *
     * @param previousExec what {@code ramsesExec} held before this compile
     *        started, as captured by {@link #CompileActionPerformed}
     */
    private void reportCompileOutcome(int exitCode, File dynsim, String problem, File previousExec) {
        compileInProgress = false;
        Compile.setEnabled(true);
        if (problem != null || dynsim == null) {
            ramsesExec = restoreExecOnFailure(previousExec);
            codegenPane.append("\n" + (problem == null ? "Compilation failed." : problem) + "\n");
            JOptionPane.showMessageDialog(this,
                    "<html>" + (problem == null ? "Compilation failed." : escapeHtml(problem))
                    + "</html>", "Compilation failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ramsesExec = dynsim;
        savedynsim.setEnabled(true);
        codegenPane.append("\nBuild succeeded: " + dynsim.getAbsolutePath() + "\n");
        codegenPane.append("Simulations will now run on this custom simulator.\n");
        banner.confirm("Custom simulator built. Simulations will run on it"
                + " instead of the bundled engine.");
    }

    /**
     * True when {@code file} lives inside the uramses kit directory that
     * {@link ModelCompiler#prepare} deletes and re-extracts on every
     * compile, so a value that can only ever have been a previous compile's
     * own output is never mistaken for something still on disk once that
     * step has run.
     *
     * <p>The kit path comes from {@link Toolchain#uramsesKitDirectory()},
     * which {@link ModelCompiler#prepare} also uses, so this test and the
     * directory actually deleted cannot drift apart.
     */
    private boolean isInsideKitDirectory(File file) {
        if (file == null || toolchain == null) {
            return false;
        }
        File kitDir = toolchain.uramsesKitDirectory();
        return file.getAbsolutePath().startsWith(kitDir.getAbsolutePath() + File.separator);
    }

    /**
     * The value to restore {@code ramsesExec} to when a compile fails: what
     * it held before this compile touched anything, unless that was itself
     * inside the kit directory {@code prepare()} has just deleted (a
     * previous compile's own output) - in which case the bundled engine is
     * the only thing guaranteed to still exist. Never returns null when
     * {@code toolchain.ramses()} does not either, so every failure path
     * that assigns its result upholds the binding constraint: a failed
     * build must never leave {@code ramsesExec} pointing at a file that no
     * longer exists.
     */
    private File restoreExecOnFailure(File previousExec) {
        if (previousExec == null || isInsideKitDirectory(previousExec)) {
            return toolchain.ramses();
        }
        return previousExec;
    }

    /** A throwable's message, falling back to its class name when the message is null. */
    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.toString();
    }

    /** Renders a multi-line plain-text message safely inside an HTML dialog. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\n", "<br>");
    }

    private void clearPFCOutputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearPFCOutputActionPerformed
        pfcPane.setText("");
        loadOutput.setEnabled(false);
        loadBusOverview.setEnabled(false);
        loadGens.setEnabled(false);
        loadTrfos.setEnabled(false);
        loadPow.setEnabled(false);
        loadLFRESV2DAT.setEnabled(false);
    }//GEN-LAST:event_clearPFCOutputActionPerformed

    private void showRAMSESLicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showRAMSESLicenseButtonActionPerformed
        try {
            InputStream in;
            in = StepssUI.class.getResourceAsStream("ramsesLicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "ramsesLicense.txt");
            OutputStream streamOut;
            streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showRAMSESLicenseButtonActionPerformed

    private void showPFCLicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showPFCLicenseButtonActionPerformed
        try {
            InputStream in;
            in = StepssUI.class.getResourceAsStream("heliosLicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "heliosLicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showPFCLicenseButtonActionPerformed

    private void showCODEGENLicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showCODEGENLicenseButtonActionPerformed
        try {
            InputStream in;
            in = StepssUI.class.getResourceAsStream("codegenLicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "codegenLicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showCODEGENLicenseButtonActionPerformed

    private void savedynsimActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savedynsimActionPerformed
        if (modelCompiler == null || modelCompiler.builtExecutable() == null) {
            banner.warn("<html>No compiled simulator to save. Compile first.</html>");
            return;
        }
        File source = modelCompiler.builtExecutable();
        fileChooser.setSelectedFile(new File(source.getName()));
        fileChooser.setDialogTitle("Save Compiled Simulator");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int returnVal = fileChooser.showSaveDialog(this);
        fileChooser.resetChoosableFileFilters();
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = fileChooser.getSelectedFile();
        if (dest.exists() && JOptionPane.showConfirmDialog(this, "Overwrite existing file?",
                "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.CANCEL_OPTION) {
            return;
        }
        try {
            fileOps.copyFiletoFile(source, dest);
            dest.setExecutable(true);
            codegenPane.append("Saved simulator to " + dest.getAbsolutePath() + "\n");
        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not save the simulator:<br>" + escapeHtml(describe(ex))
                    + "</html>", "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_savedynsimActionPerformed
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        final long started = System.nanoTime();
        final SplashScreen jvmSplash = SplashScreen.getSplashScreen();

        boolean dark = preferences().getBoolean(DARK_THEME_KEY, false);
        installTheme(dark);
        useThemedTitleBar(dark);

        // The licence comes before everything, including the card the JVM has
        // already put on screen. A first run must not show branding, or make a
        // network call, before its user has agreed to the engine's terms.
        if (preferences().getBoolean(FIRST_RUN, true)) {
            if (jvmSplash != null) {
                jvmSplash.close();
            }
            if (!LicenseDialog.accept(dark)) {
                System.exit(1);
                return;
            }
        }

        // Null on a first run, because the card above is closed and Java's
        // splash cannot be reopened. That launch simply has no splash, which
        // is the trade for the licence being genuinely first.
        final Splash splash = Splash.open(dark, getVersionFromResource());

        // Counted down once the window is up, or once it is certain that it is
        // not coming. Waiting on it below is what keeps the JVM alive in the
        // meantime, and it is not optional: AWT shuts itself down when nothing
        // is showing and the queue is empty, so with main already returned and
        // the frame still hidden the reveal never fires and the process exits
        // silently, splash and all. Verified, not assumed - that is exactly
        // what this did before the latch was here. The launcher thread is the
        // right one to hold: it is non-daemon by definition, it has nothing
        // else to do, and it does not decide the timing, the timer does.
        final CountDownLatch shown = new CountDownLatch(1);

        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                boolean scheduled = false;
                try {
                    final StepssUI frame = new StepssUI(splash);
                    // Showing the first window is what dismisses the splash, so
                    // the three second floor is enforced by delaying the window,
                    // never by sleeping: the EDT has extraction to get on with.
                    //
                    // The floor exists to hold a card on screen, so a launch
                    // without one has nothing to hold and reveals as soon as it
                    // is ready. There are three such launches, and waiting on
                    // any of them would buy a blank desktop and nothing else: a
                    // first run, where the card was closed above to make way for
                    // the licence, an IDE or classpath launch, which never had
                    // one, and `java -jar` on a build whose manifest names no
                    // splash image. The first run is the worst of the three,
                    // because it lands the moment a new user clicks Accept.
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                    int remaining = (splash == null)
                            ? 1 : (int) Math.max(1L, SPLASH_MINIMUM_MS - elapsedMs);
                    javax.swing.Timer reveal = new javax.swing.Timer(remaining, event -> {
                        try {
                            // This line is also what starts the update check:
                            // the frame listens for its own windowOpened. It
                            // is deliberately not called from here, so that a
                            // StepssUI built by anything other than this main
                            // still gets one.
                            frame.setVisible(true);
                            // Maximised is still the default, and stays what
                            // happens on a first run; a window sized and placed
                            // by hand now comes back that way instead of being
                            // flattened to the screen again.
                            if (startMaximised()) {
                                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                            }
                        } finally {
                            shown.countDown();
                        }
                    });
                    reveal.setRepeats(false);
                    reveal.start();
                    scheduled = true;
                } finally {
                    // Anything thrown while building the frame or arming the
                    // timer means no window is coming. Release the launcher
                    // thread rather than leave it waiting for one behind a
                    // splash that would then never come down.
                    if (!scheduled) {
                        shown.countDown();
                    }
                }
            }
        });

        try {
            shown.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** How long the splash stays up even when there is nothing left to wait for. */
    private static final long SPLASH_MINIMUM_MS = 3000L;

    /** Preferences key holding the theme choice, read at startup. */
    static final String DARK_THEME_KEY = "darkTheme";

    /** What is remembered between sessions, all in the one STEPSS node. */
    static final String SESSION_MAXIMISED = "windowMaximised";
    static final String SESSION_X = "windowX";
    static final String SESSION_Y = "windowY";
    static final String SESSION_W = "windowWidth";
    static final String SESSION_H = "windowHeight";
    static final String SESSION_DIR = "workingDirectory";

    /**
     * Where extracted examples are kept, so a second one lands beside the first
     * rather than inside it. Separate from {@link #SESSION_DIR} because opening
     * an example moves the working directory into the example, which would
     * otherwise make every subsequent example a child of the last one.
     */
    static final String EXAMPLES_DIR = "examplesDirectory";

    /**
     * Whether the examples panel opens at startup. Default true, and the key
     * stays absent until the user unticks the box, so "has never opened the
     * dialog" and "wants it every launch" are the same state - which is the one
     * the feature exists for.
     *
     * <p>Lives in the same node as everything else, so
     * {@link PreferenceMigration} carries it across the legacy node for free:
     * that class copies every key rather than an enumerated list, precisely
     * because an enumeration silently drops whatever is added later.
     */
    static final String SHOW_EXAMPLES_KEY = "showExamplesAtStartup";

    /**
     * Where the saved preferences live: the theme, the window, the working
     * directory and the first-run flag, in one node rather than several that
     * drift.
     *
     * <p>The name is a literal, not {@code getClass().getName()}, which is
     * what it used to be. That tied the node to the class name, so renaming
     * the package moved every user's settings out from under them. A
     * stored-preferences key is a compatibility surface and has no business
     * following a refactor on its own.
     *
     * <p>It did follow this one, deliberately: the node was
     * {@code my.ramses.RamsesUI} until the package became {@code my.stepss},
     * and {@link PreferenceMigration} is what makes the corrected name free
     * rather than costing every user their settings. Do not remove that call.
     * Installations that predate it still have the old node on disk.
     */
    static Preferences preferences() {
        if (cachedNode == null) {
            Preferences root = Preferences.userRoot();
            try {
                cachedNode = PreferenceMigration.node(root, LEGACY_NODE, PREFERENCES_NODE);
            } catch (java.util.prefs.BackingStoreException | RuntimeException ex) {
                // A preference store that cannot be read - or that throws
                // something unchecked, such as a value the backing store
                // reports but cannot actually return - is not a reason not to
                // start. The defaults apply for this session and the next
                // launch tries the migration again.
                Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                        "Could not migrate saved preferences", ex);
                cachedNode = root.node(PREFERENCES_NODE);
            }
            // Outside the try: firstRunKey() declares no checked exception, and
            // running it unconditionally means a migration failure still does
            // not skip converting the first-run flag on whichever node startup
            // ends up using, on either path above.
            PreferenceMigration.firstRunKey(cachedNode);
        }
        return cachedNode;
    }

    private static Preferences cachedNode;

    /** The node this application uses, matching the package it belongs to. */
    private static final String PREFERENCES_NODE = "my.stepss.StepssUI";

    /** The node it used before v3.74.7's package rename, migrated on first read. */
    private static final String LEGACY_NODE = "my.ramses.RamsesUI";

    /** Whether the licence agreement still has to be shown. */
    static final String FIRST_RUN = "stepssFirstTime";


    /**
     * Lets the title bar follow the theme instead of staying whatever the
     * desktop painted it.
     *
     * <p>Without this the window turns dark and its title bar does not, because
     * the title bar is drawn by the window manager and knows nothing about the
     * application's own choice. The fix differs per platform:
     *
     * <ul>
     * <li><b>Linux</b>: FlatLaf can draw the title bar itself, but only if asked
     * before the first window exists, which is what these two calls do. The
     * documented caveat is that this must not be used by an application that
     * switches to a <i>non-FlatLaf</i> look and feel at runtime, since that
     * would leave the window undecorated. This one only ever switches between
     * FlatLaf's own light and dark themes; the single non-FlatLaf path is the
     * fallback in {@link #installTheme}, which happens before any window is
     * created and skips this entirely.
     * <li><b>Windows 10 and 11</b>: FlatLaf decorations are on by default, so
     * the title bar already follows and there is nothing to do.
     * <li><b>macOS</b>: the title bar stays native and takes its appearance from
     * this property, which the OS reads once at startup. A theme switched
     * mid-session therefore leaves the Mac title bar until the next launch,
     * which is the one place this is not live.
     * </ul>
     *
     * <p>The menu bar is deliberately <i>not</i> embedded into the title bar,
     * which FlatLaf would otherwise do by default. File, Tools and Help stay
     * exactly where they have always been.
     */
    private static void useThemedTitleBar(boolean dark) {
        if (!(UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatLaf)) {
            return;  // the fallback look and feel has no decorations to offer
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            System.setProperty("apple.awt.application.appearance",
                    dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
        } else if (os.contains("linux") || os.contains("unix")) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }
    }

    /**
     * Installs FlatLaf and the handful of metrics the stock theme does not get
     * right for this application.
     *
     * <p>What this replaced was {@code getSystemLookAndFeelClassName()}, which
     * on Linux resolves to the GTK peer. That peer is unmaintained in OpenJDK,
     * ignores the desktop's dark preference, and rendered sibling combo boxes
     * at two different heights on the Observables tab. FlatLaf renders the same
     * way on all three platforms, scales on HiDPI, and has a dark theme.
     *
     * <p>Order matters and is the one trap here: the {@code UIManager.put}
     * calls have to come <em>after</em> {@code setup()}, because setup replaces
     * the defaults table, and before any component is constructed, because
     * components read these values once. Called again by the Tools menu toggle,
     * which is why the puts live here rather than inline in {@code main}.
     *
     * <p>Falls back to the previous behaviour if FlatLaf cannot be installed
     * for any reason, so a broken theme can never be what stops the engine from
     * being usable.
     */
    static void installTheme(boolean dark) {
        try {
            if (dark) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
            }
        } catch (RuntimeException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.WARNING,
                    "FlatLaf could not be installed; falling back to the system look and feel", ex);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                    | javax.swing.UnsupportedLookAndFeelException fallbackFailure) {
                Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, fallbackFailure);
            }
            return;
        }

        // Softer than FlatLaf's default 0, and short of a pill: these controls
        // sit in dense rows of ten, where a large radius reads as noise.
        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        // The tabs were 23px of unpadded text crammed against the menu bar and
        // were the hardest thing in the window to hit.
        UIManager.put("TabbedPane.tabHeight", 34);
        UIManager.put("TabbedPane.tabWidthMode", "preferred");
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 12);
        // Ten file rows and five observable rows benefit from the extra 2px far
        // more than the console panes lose by it.
        UIManager.put("Table.rowHeight", 22);
        UIManager.put("List.rowHeight", 20);
    }

    private final StatusBar statusBar = new StatusBar();
    private final InlineBanner banner = new InlineBanner();
    private Boolean savedOutputBool = false;
    private Platform platform;
    private Toolchain toolchain;
    private File toolDir = null;
    private File myTempDir = null;
    /**
     * Where the last successful analysis left its files, and what it was.
     * Held so <i>Save dynamic Jacobian</i> archives the matrix that analysis
     * reduced and the results it produced, rather than whatever happens to be
     * on disk now, and null until an analysis has succeeded, which is what
     * keeps that button disabled.
     *
     * <p>The manifest is built when the run finishes rather than when the
     * button is pressed, for the same reason: by then the user may have
     * changed the parameter fields, or adopted a different engine, and an
     * archive recording those would be describing a run that never happened.
     */
    private File lastRunDir = null;
    private my.stepss.ssa.SsaArchive.Manifest lastRunManifest = null;
    private File selWorkDir = null;
    private File ramsesExec = null;
    private File heliosExec = null;
    private File dyngraphExec = null;
    private File gnuplotExec = null;
    private File codegenExec = null;
    private ModelCompiler modelCompiler = null;
    // EDT-only: set at the top of CompileActionPerformed, cleared in every
    // path out of the background compile thread (prepare-failure,
    // build-start-failure, and both branches of reportCompileOutcome).
    // Guards against Compile -> Run Codegen -> Compile starting a second
    // ModelCompiler whose prepare() would delete the kit out from under the
    // running make, and against the Codegen completion handler re-enabling
    // Compile mid-build.
    private boolean compileInProgress = false;
    // initRamses() re-runs on every working-directory change, but the
    // "gnuplot not found" warning only needs to be told once per session.
    private boolean gnuplotMissingWarned = false;
    private String this_version = "0.0";
    // This repository's releases: the changelog Help->Changelog opens, and the
    // versions the update check compares against, are the same pages.
    private static final String RELEASES_URL = "https://github.com/SPS-L/stepss-java-ui/releases";
    private static final String RELEASES_LATEST_URL = RELEASES_URL + "/latest";
    private boolean ssa = false;
    // Whether the engine in use accepts real_limit and pf_threshold on its EIG
    // record. Set by applyEngineCapabilities() from the engine's own banner on
    // every initRamses(), so an adopted Codegen build re-decides it. False
    // until then, matching the fields' disabled state in the generated block.
    private boolean eigParametersSupported = false;
    // The engine's own banner version, NaN when it could not be read. Kept
    // beside the flag above because the archive records it: an archive opened
    // against a different engine build is otherwise silent about which one
    // produced the numbers in it.
    private double engineVersion = Double.NaN;
    private DefaultExecutor simulExecutor;
    private DefaultExecuteResultHandler simulExecutorResultHandler;
    private int highlighterIndex;
    private Highlighter highlighter;
    private int highlighterLen;
    private Preferences prefs;
    private Map WinEnvironment;
    private ArrayList<JTextField> dataFileList = new ArrayList();
    private TextareaOutputStream outputstream;
    private TextareaOutputStream outputstreamCG;
    private TextareaOutputStream outputstreamPFC;
    private TextareaOutputStream outputstreamErr;
    private TextareaOutputStream outputstreamCGErr;
    private TextareaOutputStream outputstreamPFCErr;
    private File[] codeGenFiles = null;
    private JFileChooser mfileChooser = new JFileChooser();
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton Compile;
    private javax.swing.JDialog aboutBox;
    private javax.swing.JButton addBranchButton;
    private javax.swing.JButton addBusButton;
    private javax.swing.JButton addInjButton;
    private javax.swing.JButton addShuntButton;
    private javax.swing.JButton addSyncButton;
    private javax.swing.JCheckBox allBranchCheckBox;
    private javax.swing.JCheckBox allBusCheckBox;
    private javax.swing.JCheckBox allInjCheckBox;
    private javax.swing.JCheckBox allShuntCheckBox;
    private javax.swing.JCheckBox allSyncCheckBox;
    private javax.swing.JTextField branchObsField;
    private javax.swing.JComboBox branchObsList;
    private javax.swing.JTextField busObsField;
    private javax.swing.JComboBox busObsList;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JMenuItem checkUpdateButton;
    private javax.swing.JButton clearDataFiles;
    private javax.swing.JButton clearGnuplotButton;
    private javax.swing.JButton clearObsFileButton;
    private javax.swing.JButton clearPFCOutput;
    private javax.swing.JButton clearSimulOutput;
    private javax.swing.JTextArea codegenPane;
    private javax.swing.JButton displayCGfiles;
    private javax.swing.JButton execCodegen;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JFileChooser fileChooser;
    private javax.swing.JTextField fileData1;
    private javax.swing.JTextField fileData10;
    private javax.swing.JTextField fileData2;
    private javax.swing.JTextField fileData3;
    private javax.swing.JTextField fileData4;
    private javax.swing.JTextField fileData5;
    private javax.swing.JTextField fileData6;
    private javax.swing.JTextField fileData7;
    private javax.swing.JTextField fileData8;
    private javax.swing.JTextField fileData9;
    private javax.swing.JTextField fileDist;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JTextField fileObs;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JTextField injObsField;
    private javax.swing.JComboBox injObsList;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JMenuItem killAllGnupMenuItem;
    private javax.swing.JButton loadBusOverview;
    private javax.swing.JButton loadCodegenFiles;
    private javax.swing.JMenuItem loadConfigMenuItem;
    private javax.swing.JButton loadContTrace;
    private javax.swing.JButton loadData1;
    private javax.swing.JButton loadData2;
    private javax.swing.JButton loadData3;
    private javax.swing.JButton loadData4;
    private javax.swing.JButton loadData5;
    private javax.swing.JButton loadData6;
    private javax.swing.JButton loadData7;
    private javax.swing.JButton loadData8;
    private javax.swing.JButton loadData9;
    private javax.swing.JButton loadDiscTrace;
    private javax.swing.JButton loadDist;
    private javax.swing.JButton loadDumpTraceButton;
    private javax.swing.JButton loadDynJac;
    private javax.swing.JMenuItem loadExtSimButton;
    private javax.swing.JButton loadGens;
    private javax.swing.JButton loadLFRESV2DAT;
    private javax.swing.JButton loadObsButton;
    private javax.swing.JButton loadOutput;
    private javax.swing.JButton loadPow;
    private javax.swing.JButton loadSSADir;
    private javax.swing.JButton loadTrajToFileButton;
    private javax.swing.JButton loadTrfos;
    private javax.swing.JLabel logo;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JButton nppData10Button;
    private javax.swing.JButton nppData1Button;
    private javax.swing.JButton nppData2Button;
    private javax.swing.JButton nppData3Button;
    private javax.swing.JButton nppData4Button;
    private javax.swing.JButton nppData5Button;
    private javax.swing.JButton nppData6Button;
    private javax.swing.JButton nppData7Button;
    private javax.swing.JButton nppData8Button;
    private javax.swing.JButton nppData9Button;
    private javax.swing.JButton nppDstButton;
    private javax.swing.JButton nppObsButton;
    private javax.swing.JCheckBox observFileWizButton;
    private javax.swing.JMenuItem openExamplesMenuItem;
    private javax.swing.JMenuItem openExplButton;
    private javax.swing.JMenuItem openNppButton;
    private javax.swing.JMenuItem openTermButton;
    private javax.swing.JTextArea pfcPane;
    private javax.swing.JButton remBranchObs;
    private javax.swing.JButton remBusObs;
    private javax.swing.JButton remInjObs;
    private javax.swing.JButton remShuntObs;
    private javax.swing.JButton remSyncObs;
    private javax.swing.JButton runDyngraphButton;
    private javax.swing.JButton runPF;
    private javax.swing.JButton runSimulation;
    private javax.swing.JTextField runtimeObsName;
    private javax.swing.JTextField runtimeObsName1;
    private javax.swing.JTextField runtimeObsName2;
    private javax.swing.JComboBox runtimeObsType;
    private javax.swing.JComboBox runtimeObsType1;
    private javax.swing.JComboBox runtimeObsType2;
    private javax.swing.JButton saveCGFiles;
    private javax.swing.JMenuItem saveCommandFileMenuItem;
    private javax.swing.JMenuItem saveConfigMenuItem;
    private javax.swing.JCheckBox saveContTrace;
    private javax.swing.JButton saveCurrentCurveButton;
    private javax.swing.JCheckBox saveDiscTrace;
    private javax.swing.JCheckBox saveDumpButton;
    private javax.swing.JMenuItem saveObsFileMenuItem;
    private javax.swing.JCheckBox saveOutputTrajButton;
    private javax.swing.JButton saveDynJac;
    private javax.swing.JButton saveSimulOutput;
    private javax.swing.JButton saveTrajToFileButton;
    private javax.swing.JButton savedynsim;
    private javax.swing.JTextField searchTextField;
    private javax.swing.JMenuItem selWorkDirButton;
    private javax.swing.JMenuItem showAboutBox;
    private javax.swing.JButton showApacheLicenseButton;
    private javax.swing.JButton showCODEGENLicenseButton;
    private javax.swing.JMenuItem showChangeLogButton;
    private javax.swing.JButton showGnupCopyrightButton;
    private javax.swing.JButton showKLULicenseButton;
    private javax.swing.JButton showPFCLicenseButton;
    private javax.swing.JButton showRAMSESLicenseButton;
    private javax.swing.JMenuItem showUserGuideButton;
    private javax.swing.JTextField shuntObsField;
    private javax.swing.JComboBox shuntObsList;
    private javax.swing.JTextArea simulationOutput;
    private javax.swing.JTextField ssaBasename;
    private javax.swing.JLabel ssaBasenameLabel;
    private javax.swing.JButton ssaButton1;
    private javax.swing.JTextField ssaDirectory;
    private javax.swing.JLabel ssaEngineNote;
    private javax.swing.JTextField ssaPfThreshold;
    private javax.swing.JLabel ssaPfThresholdLabel;
    private javax.swing.JTextField ssaRealLimit;
    private javax.swing.JLabel ssaRealLimitLabel;
    private javax.swing.JTextField ssaTime;
    private javax.swing.JLabel ssaTimeLabel;
    private javax.swing.JButton stopSimulationButton;
    private javax.swing.JTextField syncObsField;
    private javax.swing.JComboBox syncObsList;
    private javax.swing.JMenu toolsMenu;
    private javax.swing.JLabel versionLabel;
    private javax.swing.JLabel versionLabel1;
    private javax.swing.JLabel versionLabel2;
    private javax.swing.JButton viewCurvesButton;
    private javax.swing.JLabel webpageLabel;
    // End of variables declaration//GEN-END:variables

    private boolean createCustomObsFile() {
        try {
            BufferedWriter tmpBuffWriter;
            tmpBuffWriter = new BufferedWriter(new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt"));
            tmpBuffWriter.write("");
            tmpBuffWriter.flush();
            if (allBusCheckBox.isSelected()) {
                tmpBuffWriter.append("BUS *");
                tmpBuffWriter.newLine();
            } else if (busObsList.getItemCount() > 0) {
                for (int i = 0; i < busObsList.getItemCount(); i++) {
                    tmpBuffWriter.append("BUS " + busObsList.getItemAt(i).toString());
                    tmpBuffWriter.newLine();
                }
            }

            if (allSyncCheckBox.isSelected()) {
                tmpBuffWriter.append("SYNC *");
                tmpBuffWriter.newLine();
            } else if (syncObsList.getItemCount() > 0) {
                for (int i = 0; i < syncObsList.getItemCount(); i++) {
                    tmpBuffWriter.append("SYNC " + syncObsList.getItemAt(i).toString());
                    tmpBuffWriter.newLine();
                }
            }

            if (allShuntCheckBox.isSelected()) {
                tmpBuffWriter.append("SHUNT *");
                tmpBuffWriter.newLine();
            } else if (shuntObsList.getItemCount() > 0) {
                for (int i = 0; i < shuntObsList.getItemCount(); i++) {
                    tmpBuffWriter.append("SHUNT " + shuntObsList.getItemAt(i).toString());
                    tmpBuffWriter.newLine();
                }
            }

            if (allBranchCheckBox.isSelected()) {
                tmpBuffWriter.append("BRANCH *");
                tmpBuffWriter.newLine();
            } else if (branchObsList.getItemCount() > 0) {
                for (int i = 0; i < branchObsList.getItemCount(); i++) {
                    tmpBuffWriter.append("BRANCH " + branchObsList.getItemAt(i).toString() + " ");
                    tmpBuffWriter.newLine();
                }
            }

            if (allInjCheckBox.isSelected()) {
                tmpBuffWriter.append("INJEC *");
                tmpBuffWriter.newLine();
            } else if (injObsList.getItemCount() > 0) {
                for (int i = 0; i < injObsList.getItemCount(); i++) {
                    tmpBuffWriter.append("INJEC " + injObsList.getItemAt(i).toString());
                    tmpBuffWriter.newLine();
                }
            }
            tmpBuffWriter.newLine();
            tmpBuffWriter.newLine();
            tmpBuffWriter.flush();
            tmpBuffWriter.close();
            return true;


        } catch (IOException ex) {
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    /** @return the platform-appropriate command to install gnuplot from a package manager. */
    private String gnuplotInstallHint() {
        if (platform == Platform.MACOS_ARM64) {
            return "brew install gnuplot";
        }
        if (platform == Platform.WINDOWS_X86_64) {
            return "reinstall STEPSS; gnuplot ships bundled on Windows";
        }
        return "apt install gnuplot";
    }

    /**
     * The simulator {@link #initRamses} should install: a custom one built by
     * this session's last successful compile if it is still on disk, otherwise
     * the bundled engine.
     *
     * <p>{@code initRamses()} runs again on every working-directory change, and
     * unconditionally resetting {@code ramsesExec} to the bundled engine there
     * silently un-adopted a compiled simulator while the Codegen pane still
     * read "Simulations will now run on this custom simulator" and <i>Save
     * executable</i> stayed enabled - so the user simulated on the bundled
     * engine believing it was theirs. The kit lives under the tool directory,
     * not the working directory, and {@code extractAll()} skips the lazy
     * uramses kit, so the built executable genuinely survives the change; the
     * {@code isFile()} test is what keeps this honest if it ever does not.
     *
     * <p>{@link ModelCompiler#builtExecutable()} is null until a build
     * succeeds and is reset to null by every {@code prepare()}, so a failed or
     * superseded compile cannot be re-adopted here.
     */
    private File adoptedSimulator() {
        File custom = (modelCompiler == null) ? null : modelCompiler.builtExecutable();
        if (custom != null && custom.isFile()) {
            return custom;
        }
        return toolchain.ramses();
    }

    /**
     * The engine's startup banner, or an empty string if it could not be run.
     *
     * <p>Uses the {@code -v} switch, which prints the banner and returns
     * without running anything, so this costs milliseconds and cannot start a
     * simulation by accident. That path deliberately exits non-zero, so the
     * exit value is ignored and only the output is read.
     *
     * <p>Lives here rather than beside {@link my.stepss.ssa.EngineVersion}
     * because it needs commons-exec, and {@code tools/ssa-harness.sh} runs
     * that package with only {@code build/classes} on the classpath.
     */
    private String engineBanner() {
        if (ramsesExec == null || !ramsesExec.isFile()) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            CommandLine command = new CommandLine(ramsesExec.getAbsolutePath());
            command.addArgument("-v");
            DefaultExecutor exec = new DefaultExecutor();
            exec.setExitValues(null);
            exec.setWatchdog(new ExecuteWatchdog(10_000));
            exec.setStreamHandler(new PumpStreamHandler(out, out));
            exec.execute(command, WinEnvironment);
        } catch (Exception ex) {
            // An engine that cannot be run at all is reported as unknown, and
            // the caller disables the parameters. Whatever stopped it will
            // surface again, with a better message, the moment a run starts.
            return "";
        }
        return out.toString();
    }

    /**
     * Enables the two small-signal parameter fields only if the engine now in
     * use accepts them, and says so either way.
     *
     * <p>Called from {@code initRamses()} after {@code ramsesExec} is
     * resolved, so it re-evaluates whenever the engine changes: on every
     * working-directory change, and after a Codegen build is adopted. That is
     * the whole point of reading the engine rather than the pinned version,
     * since an adopted build can be older than the bundled payload.
     *
     * <p>The fields ship disabled from the generated block, so this only ever
     * has to turn them on, and {@code StepssUI.form} needs no change.
     */
    private void applyEngineCapabilities() {
        double version = my.stepss.ssa.EngineVersion.parseBanner(engineBanner());
        engineVersion = version;
        eigParametersSupported =
                my.stepss.ssa.EngineVersion.supportsEigParameters(version);

        // Which engine is actually answering, in the status bar. It is not
        // always the bundled one: a compiled simulator is adopted when this
        // session has built one, and until now nothing in the window said so
        // except a line of Codegen console output that scrolls away.
        boolean custom = ramsesExec != null && !ramsesExec.equals(toolchain.ramses());
        statusBar.setEngine((custom ? "Custom engine" : "RAMSES")
                + (version > 0
                ? " " + String.format(java.util.Locale.ROOT, "%.2f", version) : ""));

        ssaRealLimit.setEnabled(eigParametersSupported);
        ssaRealLimitLabel.setEnabled(eigParametersSupported);
        ssaPfThreshold.setEnabled(eigParametersSupported);
        ssaPfThresholdLabel.setEnabled(eigParametersSupported);
        ssaEngineNote.setVisible(!eigParametersSupported);

        if (eigParametersSupported) {
            String tip = "Passed to the EIG record and recorded in the results header.";
            ssaRealLimit.setToolTipText(tip);
            ssaPfThreshold.setToolTipText(tip);
        } else {
            // Naming the version read, rather than repeating a fixed sentence,
            // is what distinguishes "the bundled engine is old" from "you
            // adopted an older build from the Codegen tab", which look
            // identical from the Analysis tab otherwise.
            String seen = Double.isNaN(version)
                    ? "its version could not be read"
                    : "it reports version " + String.format(java.util.Locale.ROOT, "%.2f", version);
            String tip = "Needs RAMSES "
                    + String.format(java.util.Locale.ROOT, "%.2f",
                            my.stepss.ssa.EngineVersion.EIG_PARAMETERS_SINCE)
                    + " or newer. The engine in use is not: " + seen
                    + ". It would ignore these values silently, so they are"
                    + " disabled rather than misleading.";
            ssaRealLimit.setToolTipText(tip);
            ssaPfThreshold.setToolTipText(tip);
            ssaEngineNote.setToolTipText(tip);
        }
    }

    private boolean initRamses() {
        try {
            platform = Platform.current();
        } catch (UnsupportedPlatformException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Unsupported platform", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return false;
        }
        try {
            if (toolDir == null) {
                toolDir = File.createTempFile("stepssTools", "");
                if (!toolDir.delete() || !toolDir.mkdir()) {
                    throw new IOException("Could not create tool directory " + toolDir);
                }
            }
            toolchain = new Toolchain(platform, toolDir);
            toolchain.extractAll(id -> {
                if (splash != null) {
                    splash.status(id);
                }
            });

            myTempDir = (selWorkDir != null) ? selWorkDir : toolDir;
            updateWindowTitle();
            statusBar.setWorkingDirectory(selWorkDir);

            openExplButton.setEnabled(true);
            openTermButton.setEnabled(true);

            ramsesExec = adoptedSimulator();
            heliosExec = toolchain.helios();
            dyngraphExec = toolchain.dyngraph();
            codegenExec = toolchain.codegen();
            gnuplotExec = toolchain.gnuplot();
            WinEnvironment = PlatformLauncher.execEnvironment(platform, toolDir);

            // After WinEnvironment, not beside ramsesExec above: this runs the
            // engine, and on Windows it needs that environment to resolve the
            // runtime DLLs beside it.
            applyEngineCapabilities();

            if ((gnuplotExec == null || !gnuplotExec.exists()) && !gnuplotMissingWarned) {
                gnuplotMissingWarned = true;
                banner.warn("<html>gnuplot was not found, so real-time plotting is disabled."
                        + "<br>Install it and restart to enable it: <b>" + gnuplotInstallHint()
                        + "</b></html>");
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not prepare the simulation toolchain.\n\n" + ex.getMessage(),
                    "Toolchain error", JOptionPane.ERROR_MESSAGE);
            Logger.getLogger(StepssUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
}
