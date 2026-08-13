/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * RamsesUI.java
 *
 * Created on Dec 3, 2011, 10:17:03 AM
 */
package my.ramses;

import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
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
import my.ramses.compile.ModelCompiler;
import my.ramses.dyngraph.DyngraphRunner;
import my.ramses.dyngraph.ObservableIndex;
import my.ramses.dyngraph.ObservablePicker;
import my.ramses.dyngraph.ReplayFile;
import my.ramses.dyngraph.Selection;
import my.ramses.platform.Platform;
import my.ramses.platform.PlatformLauncher;
import my.ramses.platform.Toolchain;
import my.ramses.platform.UnsupportedPlatformException;
import org.apache.commons.exec.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;

/**
 *
 * @author p3tris
 */
public class RamsesUI extends javax.swing.JFrame {

    private Rectangle pos;

    /**
     * Creates new form RamsesUI
     */
    public RamsesUI() {
        initComponents();
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
        String fullLimited = getRamsesType();
        versionLabel.setText("<html><B><U>Version</U>:</B> " + this_version + " (" + fullLimited + " Version)</html>");
        prefs = Preferences.userRoot().node(this.getClass().getName());
        String ramsesFirtsTime = "";
        if (prefs.getBoolean(ramsesFirtsTime, true)) {
            if (fullLimited.equalsIgnoreCase("Limited")) {
                licenseAgreement(null);
            }
            prefs.putBoolean(ramsesFirtsTime, false);
        }
//        prefs.remove(ramsesFirtsTime);

//        KeyStroke ctrlGKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_G,InputEvent.CTRL_DOWN_MASK);
//        jPanel1.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlGKeyStroke, "KILLGP");
//        jPanel1.getActionMap().put("KILLGP", new AbstractAction() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                System.out.println("Killing Gnuplot");
//                clearGnuplotButtonActionPerformed(e);
//            }
//        });

        this.setIconImage(new ImageIcon(getClass().getResource("logo.png")).getImage());
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (initRamses()) {
        } else {
            // initRamses() already showed a dialog naming the specific tool
            // and path that failed; a second, generic dialog here would just
            // repeat the failure without adding information.
            System.exit(1);
        }
    }

    private String getVersion() {
        InputStream in = RamsesUI.class.getResourceAsStream("version.txt");
        if (in == null) {
            return "0.0";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            String line = reader.readLine();
            return (line == null || line.trim().isEmpty()) ? "0.0" : line.trim();
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return "0.0";
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }

    private String getRamsesType() {
        InputStream in = RamsesUI.class.getResourceAsStream("type.txt");
        if (in == null) {
            return "Limited";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            String line = reader.readLine();
            return (line == null || line.trim().isEmpty()) ? "Limited" : line.trim();
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return "Limited";
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
    }

    private void licenseAgreement(java.awt.event.ActionEvent evt) {
        try {
            InputStream in = RamsesUI.class.getResourceAsStream("ramsesLicense.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String str;
            StringBuilder buf = new StringBuilder();
            if (in != null) {
                try {
                    while ((str = reader.readLine()) != null) {
                        buf.append(str).append("\n");
                    }
                    in.close();
                } catch (IOException ex) {
                    Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            JTextArea textArea = new JTextArea(buf.toString());
            textArea.setColumns(50);
            textArea.setRows(20);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setSize(textArea.getPreferredSize().width, 1);
            JScrollPane sp = new JScrollPane(textArea,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            int response = JOptionPane.showOptionDialog(null,
                    sp,
                    "License Agreement",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new String[]{"Accept", "Decline"},
                    "default");
            if (response == JOptionPane.YES_OPTION) {
            } else {
                System.exit(1);
            }
        }
        catch(HeadlessException e) {
          e.getStackTrace();
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
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
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
        GP_REFRESH_RATE = new javax.swing.JTextField();
        jLabel31 = new javax.swing.JLabel();
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
        ssaButton = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        ssaButton1 = new javax.swing.JButton();
        ssaDirectory = new javax.swing.JTextField();
        loadSSADir = new javax.swing.JButton();
        viewSsaResults = new javax.swing.JButton();
        ssaBasenameLabel = new javax.swing.JLabel();
        ssaBasename = new javax.swing.JTextField();
        ssaRealLimitLabel = new javax.swing.JLabel();
        ssaRealLimit = new javax.swing.JTextField();
        ssaPfThresholdLabel = new javax.swing.JLabel();
        ssaPfThreshold = new javax.swing.JTextField();
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
        aboutBox.setAlwaysOnTop(true);
        aboutBox.setBounds(new java.awt.Rectangle(0, 0, 0, 0));
        aboutBox.setIconImage(new ImageIcon(getClass().getResource("logo.png")).getImage());
        aboutBox.setMinimumSize(new java.awt.Dimension(500, 350));
        aboutBox.setName("aboutBox"); // NOI18N

        logo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/logo.png"))); // NOI18N
        logo.setMaximumSize(new java.awt.Dimension(554, 440));
        logo.setMinimumSize(new java.awt.Dimension(554, 440));
        logo.setName("logo"); // NOI18N
        logo.setPreferredSize(new java.awt.Dimension(763, 545));

        jLabel2.setText("<html>Static and Transient Electric Power Systems Simulation</html>");
        jLabel2.setName("jLabel2"); // NOI18N

        jLabel3.setFont(new java.awt.Font("Bitstream Vera Sans", 0, 24)); // NOI18N
        jLabel3.setText("<html><U><B>STEPSS</B></U></html>");
        jLabel3.setName("jLabel3"); // NOI18N

        versionLabel.setText("<html><B><U>Version</U>:</B> 1.0</html>");
        versionLabel.setName("versionLabel"); // NOI18N

        jLabel5.setText("<html><B><U>Creators</U>:</B> Petros Aristidou and Thierry Van Cutsem</html>");
        jLabel5.setName("jLabel5"); // NOI18N

        webpageLabel.setText("<html><B><U>Webpage</U>:</B> <a href=\"https://stepss.sps-lab.org/\">https://stepss.sps-lab.org/</a> </html>");
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

        versionLabel1.setText("<html><B><U>Third party licenses</U>:</B></html>");
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

        versionLabel2.setText("<html><B><U>Licenses</U>:</B></html>");
        versionLabel2.setName("versionLabel2"); // NOI18N

        javax.swing.GroupLayout aboutBoxLayout = new javax.swing.GroupLayout(aboutBox.getContentPane());
        aboutBox.getContentPane().setLayout(aboutBoxLayout);
        aboutBoxLayout.setHorizontalGroup(
            aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aboutBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(aboutBoxLayout.createSequentialGroup()
                        .addGap(196, 196, 196)
                        .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3)))
                    .addGroup(aboutBoxLayout.createSequentialGroup()
                        .addComponent(logo, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(29, 29, 29)
                        .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5)
                            .addComponent(webpageLabel)
                            .addComponent(versionLabel)))
                    .addGroup(aboutBoxLayout.createSequentialGroup()
                        .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(aboutBoxLayout.createSequentialGroup()
                                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(showKLULicenseButton)
                                    .addComponent(versionLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(showApacheLicenseButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(showGnupCopyrightButton))
                            .addGroup(aboutBoxLayout.createSequentialGroup()
                                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(aboutBoxLayout.createSequentialGroup()
                                        .addComponent(showRAMSESLicenseButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(showPFCLicenseButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(showCODEGENLicenseButton))
                                    .addComponent(versionLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(110, 110, 110)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        aboutBoxLayout.setVerticalGroup(
            aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(aboutBoxLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(logo, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(aboutBoxLayout.createSequentialGroup()
                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(versionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(webpageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(18, 18, 18)
                .addComponent(versionLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(aboutBoxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(showRAMSESLicenseButton)
                    .addComponent(showPFCLicenseButton)
                    .addComponent(showCODEGENLicenseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(versionLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
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

        loadData1.setText("Load File");
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

        loadData2.setText("Load File");
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

        loadData3.setText("Load File");
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

        loadData4.setText("Load File");
        loadData4.setToolTipText("Click to load a data file of the network.");
        loadData4.setName("loadData4"); // NOI18N
        loadData4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData4ActionPerformed(evt);
            }
        });

        jLabel1.setText("<html>Load <B>System data</B> files. Necessary field!</html>");
        jLabel1.setName("jLabel1"); // NOI18N

        clearDataFiles.setText("Clear Files");
        clearDataFiles.setName("clearDataFiles"); // NOI18N
        clearDataFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearDataFilesActionPerformed(evt);
            }
        });

        fileData5.setEditable(false);
        fileData5.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData5.setName("fileData5"); // NOI18N

        loadData5.setText("Load File");
        loadData5.setToolTipText("Click to load a data file of the network.");
        loadData5.setName("loadData5"); // NOI18N
        loadData5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadData5ActionPerformed(evt);
            }
        });

        nppData1Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData1Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData1Button.setName("nppData1Button"); // NOI18N
        nppData1Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData1ButtonActionPerformed(evt);
            }
        });

        nppData2Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData2Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData2Button.setName("nppData2Button"); // NOI18N
        nppData2Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData2ButtonActionPerformed(evt);
            }
        });

        nppData3Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData3Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData3Button.setName("nppData3Button"); // NOI18N
        nppData3Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData3ButtonActionPerformed(evt);
            }
        });

        nppData4Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData4Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData4Button.setName("nppData4Button"); // NOI18N
        nppData4Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData4ButtonActionPerformed(evt);
            }
        });

        nppData5Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData5Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData5Button.setName("nppData5Button"); // NOI18N
        nppData5Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData5ButtonActionPerformed(evt);
            }
        });

        nppDstButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppDstButton.setToolTipText("Click to edit the file in Notepad++.");
        nppDstButton.setName("nppDstButton"); // NOI18N
        nppDstButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppDstButtonActionPerformed(evt);
            }
        });

        fileDist.setEditable(false);
        fileDist.setMinimumSize(new java.awt.Dimension(0, 24));
        fileDist.setName("fileDist"); // NOI18N

        loadDist.setText("Load File");
        loadDist.setToolTipText("Click to load the disturbance file with the description of the fault to be simulated.");
        loadDist.setName("loadDist"); // NOI18N
        loadDist.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDistActionPerformed(evt);
            }
        });

        jLabel9.setText("<html>Load the <B>Disturbance file</B>. Necessary field!</html>");
        jLabel9.setName("jLabel9"); // NOI18N

        loadData6.setText("Load File");
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

        nppData6Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData6Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData6Button.setName("nppData6Button"); // NOI18N
        nppData6Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData6ButtonActionPerformed(evt);
            }
        });

        loadData7.setText("Load File");
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

        nppData7Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData7Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData7Button.setName("nppData7Button"); // NOI18N
        nppData7Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData7ButtonActionPerformed(evt);
            }
        });

        loadData8.setText("Load File");
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

        nppData8Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData8Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData8Button.setName("nppData8Button"); // NOI18N
        nppData8Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData8ButtonActionPerformed(evt);
            }
        });

        loadData9.setText("Load File");
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

        nppData9Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData9Button.setToolTipText("Click to edit the file in Notepad++.");
        nppData9Button.setName("nppData9Button"); // NOI18N
        nppData9Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppData9ButtonActionPerformed(evt);
            }
        });

        fileData10.setEditable(false);
        fileData10.setMinimumSize(new java.awt.Dimension(0, 24));
        fileData10.setName("fileData10"); // NOI18N

        nppData10Button.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppData10Button.setToolTipText("Click to edit the file in Notepad++.");
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

        clearObsFileButton.setText("Clear Files");
        clearObsFileButton.setName("clearObsFileButton"); // NOI18N
        clearObsFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearObsFileButtonActionPerformed(evt);
            }
        });

        saveOutputTrajButton.setText("Save Output Trajectory");
        saveOutputTrajButton.setToolTipText("<html>Click to save the trajectories of certain observables during the simulation. <br>\nThese observables need to be specified in a file and loaded below or with the Observable File Wizard.</html>");
        saveOutputTrajButton.setName("saveOutputTrajButton"); // NOI18N
        saveOutputTrajButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveOutputTrajButtonActionPerformed(evt);
            }
        });

        loadObsButton.setText("Load File");
        loadObsButton.setToolTipText("<html>Click to load the file with the list of observables to be saved during the simulation.<br><br>\nEach observable needs to be on one line. The possibilities are: <br>\nBUS: followed by one bus name or * to denote all buses <br>\nSYNC: followed by one synchronous machine name or * to denote all sync machines<br>\nSHUNT: followed by a bus name or * to denote all buses<br>\nBRANCH: followed by the branch name or * to denote all branches<br>\nINJEC: followed by the injector name or * to denote all injectors<br>\nLINK: followed by the dclink name or * to denote all dclinks<br><br>\nAn example of an observable file is:<br><br>\nBUS B01 <br>\nSYNC SM01 <br>\nSYNC SM02 <br>\nSYNC SM03 <br>\nBRANCH *<br><br>\nwhich will save BUS B01 observables, synchronous machines SM01, SM02 and SM03 and all the branches observables.</html>");
        loadObsButton.setName("loadObsButton"); // NOI18N
        loadObsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadObsButtonActionPerformed(evt);
            }
        });

        fileObs.setEditable(false);
        fileObs.setName("fileObs"); // NOI18N

        jLabel10.setText("<html>Write the name of the <B>Observables file</B> or load. Necessary field if Trajectory has been asked</html>");
        jLabel10.setName("jLabel10"); // NOI18N

        saveDumpButton.setText("Save settings, comments and initialization data in file");
        saveDumpButton.setToolTipText("<html>Activate to save the initialization information. Useful for debugging reasons.</html>");
        saveDumpButton.setName("saveDumpButton"); // NOI18N

        observFileWizButton.setText("Show Observable dialog");
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

        runtimeObsType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Bus Voltage", "Machine Speed", "Omega-delta of machine", "Active power-delta of machine", "Center of Intertia", "Wall Time", "Latency", "Branch Active Power Origin", "Branch Active Power Extremity", "Branch Rective Power Origin", "Branch Rective Power Extremity", "Injector Observable" }));
        runtimeObsType.setToolTipText("<html>Click to choose the kind of observable you want to see in run-time during the simulation</html>");
        runtimeObsType.setName("runtimeObsType"); // NOI18N

        runtimeObsName.setToolTipText("<html>Here you clarify the name of the equipment you want to observe. For example:<br>\n1) if you selected Bus Voltage as the type of observable, here you should put the name of the bus.<br>\n2) if you selected Machine Speed or Center of Inertia as the type of observable, here you should put the name of the synchronous machine.<br>\n3) if you selected Wall Time as the type of observable, here you should put RT, as it will plot wall time VS Simulation time.<br><br>\nAdditionally you can pass extra commands to gnuplot in order to fine-tune the output. These commands must follow the name of the equipment and should be separated with / <br>\nSuch commands might be:<br><br>\nset yrange[0.9:1.1]<br><br>\nwhich will set the range of the y-axes between these values.</html>");
        runtimeObsName.setName("runtimeObsName"); // NOI18N

        jLabel30.setText("<html>Choose name and type of <B>Runtime Observable</B>:</html>");
        jLabel30.setName("jLabel30"); // NOI18N

        nppObsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/my/ramses/npp.png"))); // NOI18N
        nppObsButton.setToolTipText("Click to edit the file in Notepad++.");
        nppObsButton.setName("nppObsButton"); // NOI18N
        nppObsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nppObsButtonActionPerformed(evt);
            }
        });

        saveContTrace.setText("Save Continuous trace");
        saveContTrace.setToolTipText("<html>Activate to save the initialization information. Useful for debugging reasons.</html>");
        saveContTrace.setName("saveContTrace"); // NOI18N

        saveDiscTrace.setSelected(true);
        saveDiscTrace.setText("Save Discrete trace");
        saveDiscTrace.setToolTipText("<html>Activate to save the initialization information. Useful for debugging reasons.</html>");
        saveDiscTrace.setName("saveDiscTrace"); // NOI18N

        runtimeObsType1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Bus Voltage", "Machine Speed", "Omega-delta of machine", "Active power-delta of machine", "Center of Intertia", "Wall Time", "Latency", "Branch Active Power Origin", "Branch Active Power Extremity", "Branch Rective Power Origin", "Branch Rective Power Extremity", "Injector Observable" }));
        runtimeObsType1.setToolTipText("<html>Click to choose the kind of observable you want to see in run-time during the simulation</html>");
        runtimeObsType1.setName("runtimeObsType1"); // NOI18N

        runtimeObsName1.setToolTipText("<html>Here you clarify the name of the equipment you want to observe. For example:<br>\n1) if you selected Bus Voltage as the type of observable, here you should put the name of the bus.<br>\n2) if you selected Machine Speed or Center of Inertia as the type of observable, here you should put the name of the synchronous machine.<br>\n3) if you selected Wall Time as the type of observable, here you should put RT, as it will plot wall time VS Simulation time.<br><br>\nAdditionally you can pass extra commands to gnuplot in order to fine-tune the output. These commands must follow the name of the equipment and should be separated with / <br>\nSuch commands might be:<br><br>\nset yrange[0.9:1.1]<br><br>\nwhich will set the range of the y-axes between these values.</html>");
        runtimeObsName1.setName("runtimeObsName1"); // NOI18N

        runtimeObsType2.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Bus Voltage", "Machine Speed", "Omega-delta of machine", "Active power-delta of machine", "Center of Intertia", "Wall Time", "Latency", "Branch Active Power Origin", "Branch Active Power Extremity", "Branch Rective Power Origin", "Branch Rective Power Extremity", "Injector Observable" }));
        runtimeObsType2.setToolTipText("<html>Click to choose the kind of observable you want to see in run-time during the simulation</html>");
        runtimeObsType2.setName("runtimeObsType2"); // NOI18N

        runtimeObsName2.setToolTipText("<html>Here you clarify the name of the equipment you want to observe. For example:<br>\n1) if you selected Bus Voltage as the type of observable, here you should put the name of the bus.<br>\n2) if you selected Machine Speed or Center of Inertia as the type of observable, here you should put the name of the synchronous machine.<br>\n3) if you selected Wall Time as the type of observable, here you should put RT, as it will plot wall time VS Simulation time.<br><br>\nAdditionally you can pass extra commands to gnuplot in order to fine-tune the output. These commands must follow the name of the equipment and should be separated with / <br>\nSuch commands might be:<br><br>\nset yrange[0.9:1.1]<br><br>\nwhich will set the range of the y-axes between these values.</html>");
        runtimeObsName2.setName("runtimeObsName2"); // NOI18N

        GP_REFRESH_RATE.setName("GP_REFRESH_RATE"); // NOI18N

        jLabel31.setText("Plot refresh interval (sec):");
        jLabel31.setName("jLabel31"); // NOI18N
        jLabel31.setToolTipText("<html>A small refresh interval would slow down the simulation.</html>");

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
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel31)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(GP_REFRESH_RATE, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
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
                    .addComponent(runtimeObsType2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(GP_REFRESH_RATE, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel31))
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

        runPF.setText("Run Power Flow");
        runPF.setToolTipText("Click to run the simulation.");
        runPF.setName("runPF"); // NOI18N
        runPF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runPFActionPerformed(evt);
            }
        });

        loadBusOverview.setText("Bus Overview");
        loadBusOverview.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadBusOverview.setEnabled(false);
        loadBusOverview.setName("loadBusOverview"); // NOI18N
        loadBusOverview.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadBusOverviewActionPerformed(evt);
            }
        });

        loadLFRESV2DAT.setText("Add Helios Results to Data");
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

        loadTrfos.setText("Adjustable transfos");
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

        clearPFCOutput.setText("Clear All");
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
        simulationOutput.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        simulationOutput.setRows(5);
        simulationOutput.setName("simulationOutput"); // NOI18N
        jScrollPane1.setViewportView(simulationOutput);

        runSimulation.setText("Run Dynamic Simulation");
        runSimulation.setToolTipText("Click to run the simulation.");
        runSimulation.setName("runSimulation"); // NOI18N
        runSimulation.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runSimulationActionPerformed(evt);
            }
        });

        saveSimulOutput.setText("Save Current Output");
        saveSimulOutput.setToolTipText("Click to save the information shown above to a txt file.");
        saveSimulOutput.setEnabled(false);
        saveSimulOutput.setName("saveSimulOutput"); // NOI18N
        saveSimulOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveSimulOutputActionPerformed(evt);
            }
        });

        loadOutput.setText("Load Output");
        loadOutput.setToolTipText("Click to load the output of the simulation.");
        loadOutput.setEnabled(false);
        loadOutput.setName("loadOutput"); // NOI18N
        loadOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadOutputActionPerformed(evt);
            }
        });

        loadContTrace.setText("Load Continuous Trace");
        loadContTrace.setToolTipText("<html>Click to see the continuous trace of the simulation.<br>\nThis involves a detailed view on the Newton iterations and the time-step evolution.</html>");
        loadContTrace.setEnabled(false);
        loadContTrace.setName("loadContTrace"); // NOI18N
        loadContTrace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadContTraceActionPerformed(evt);
            }
        });

        loadDiscTrace.setText("Load Discrete Trace");
        loadDiscTrace.setToolTipText("<html>Click to see the discrete trace of the simulation.<br>\nThis involves a detailed view on the discrete changes happening during the simulation.</html>");
        loadDiscTrace.setEnabled(false);
        loadDiscTrace.setName("loadDiscTrace"); // NOI18N
        loadDiscTrace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDiscTraceActionPerformed(evt);
            }
        });

        clearSimulOutput.setText("Clear All");
        clearSimulOutput.setToolTipText("Click to clear the above pane.");
        clearSimulOutput.setEnabled(false);
        clearSimulOutput.setName("clearSimulOutput"); // NOI18N
        clearSimulOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearSimulOutputActionPerformed(evt);
            }
        });

        loadDumpTraceButton.setText("Load Initialization");
        loadDumpTraceButton.setToolTipText("<html>Click to see the initialization data of the simulation.<br>\nThis involves a detailed view of the initial state of the simulation.</html>");
        loadDumpTraceButton.setEnabled(false);
        loadDumpTraceButton.setName("loadDumpTraceButton"); // NOI18N
        loadDumpTraceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadDumpTraceButtonActionPerformed(evt);
            }
        });

        stopSimulationButton.setText("Stop Simulation");
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

        runDyngraphButton.setText("Extract Curves");
        runDyngraphButton.setToolTipText("Click to initiate dialog for selecting the observables you want to plot.");
        runDyngraphButton.setEnabled(false);
        runDyngraphButton.setName("runDyngraphButton"); // NOI18N
        runDyngraphButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runDyngraphButtonActionPerformed(evt);
            }
        });

        viewCurvesButton.setText("Preview Curve");
        viewCurvesButton.setToolTipText("Click to preview the last extracted curve.");
        viewCurvesButton.setEnabled(false);
        viewCurvesButton.setName("viewCurvesButton"); // NOI18N
        viewCurvesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewCurvesButtonActionPerformed(evt);
            }
        });

        saveTrajToFileButton.setText("Save Current Trajectory");
        saveTrajToFileButton.setToolTipText("Click to save the trajectory file of the last simulation.");
        saveTrajToFileButton.setEnabled(false);
        saveTrajToFileButton.setName("saveTrajToFileButton"); // NOI18N
        saveTrajToFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveTrajToFileButtonActionPerformed(evt);
            }
        });

        clearGnuplotButton.setText("Clear all Gnuplot instances");
        clearGnuplotButton.setToolTipText("Kills all instances of Gnuplot.");
        clearGnuplotButton.setName("clearGnuplotButton"); // NOI18N
        clearGnuplotButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearGnuplotButtonActionPerformed(evt);
            }
        });

        saveCurrentCurveButton.setText("Save Extracted Curve");
        saveCurrentCurveButton.setToolTipText("Save the extracted plots.");
        saveCurrentCurveButton.setEnabled(false);
        saveCurrentCurveButton.setName("saveCurrentCurveButton"); // NOI18N
        saveCurrentCurveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveCurrentCurveButtonActionPerformed(evt);
            }
        });

        loadTrajToFileButton.setText("Load Trajectory");
        loadTrajToFileButton.setToolTipText("Click to load a trajectory file that you saved from a previous simulation.");
        loadTrajToFileButton.setName("loadTrajToFileButton"); // NOI18N
        loadTrajToFileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadTrajToFileButtonActionPerformed(evt);
            }
        });

        ssaButton.setText("Extract Jacobian matrix");
        ssaButton.setToolTipText("Extract Jacobian matrix");
        ssaButton.setName("ssaButton"); // NOI18N
        ssaButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ssaButtonActionPerformed(evt);
            }
        });

        jLabel4.setText("<html><b><u>Time-domain analysis:</u></b></html>");
        jLabel4.setName("jLabel4"); // NOI18N

        jLabel8.setText("<html><b><u>Small Signal Stability analysis:</u></b></html>");
        jLabel8.setName("jLabel8"); // NOI18N

        ssaButton1.setText("Perform small signal stability analysis");
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

        viewSsaResults.setText("View results...");
        viewSsaResults.setToolTipText("Open small-signal results already on disk, without re-running the analysis");
        viewSsaResults.setName("viewSsaResults"); // NOI18N
        viewSsaResults.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewSsaResultsActionPerformed(evt);
            }
        });

        ssaBasenameLabel.setText("Results basename");
        ssaBasenameLabel.setName("ssaBasenameLabel"); // NOI18N

        ssaBasename.setText("ssa");
        ssaBasename.setToolTipText("Names the three results files, so several runs can share one directory");
        ssaBasename.setName("ssaBasename"); // NOI18N

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
                                .addComponent(ssaButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaButton1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(viewSsaResults))
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(ssaBasenameLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaBasename, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(ssaRealLimitLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaRealLimit, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(ssaPfThresholdLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ssaPfThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                    .addComponent(ssaRealLimitLabel)
                    .addComponent(ssaRealLimit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ssaPfThresholdLabel)
                    .addComponent(ssaPfThreshold, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ssaButton1)
                    .addComponent(ssaButton)
                    .addComponent(viewSsaResults))
                .addContainerGap(657, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Analysis", jPanel8);

        jPanel3.setName("jPanel3"); // NOI18N

        jScrollPane2.setName("jScrollPane2"); // NOI18N

        codegenPane.setColumns(20);
        codegenPane.setRows(5);
        codegenPane.setName("codegenPane"); // NOI18N
        jScrollPane2.setViewportView(codegenPane);

        loadCodegenFiles.setText("Load Files for Codegen");
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

        displayCGfiles.setText("Display Loaded Files");
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
        saveConfigMenuItem.setText("Save Configuration");
        saveConfigMenuItem.setName("saveConfigMenuItem"); // NOI18N
        saveConfigMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveConfigMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveConfigMenuItem);

        loadConfigMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        loadConfigMenuItem.setText("Load Configuration");
        loadConfigMenuItem.setName("loadConfigMenuItem"); // NOI18N
        loadConfigMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadConfigMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(loadConfigMenuItem);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.CTRL_DOWN_MASK));
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

        saveCommandFileMenuItem.setText("Save Command File");
        saveCommandFileMenuItem.setName("saveCommandFileMenuItem"); // NOI18N
        saveCommandFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveCommandFileMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(saveCommandFileMenuItem);

        saveObsFileMenuItem.setText("Save Observables File");
        saveObsFileMenuItem.setName("saveObsFileMenuItem"); // NOI18N
        saveObsFileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveObsFileMenuItemActionPerformed(evt);
            }
        });
        toolsMenu.add(saveObsFileMenuItem);

        openNppButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openNppButton.setText("Open in Editor");
        openNppButton.setName("openNppButton"); // NOI18N
        openNppButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openNppButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(openNppButton);

        loadExtSimButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        loadExtSimButton.setText("Select External Simulator");
        loadExtSimButton.setName("loadExtSimButton"); // NOI18N
        loadExtSimButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadExtSimButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(loadExtSimButton);

        selWorkDirButton.setText("Select Working Directory");
        selWorkDirButton.setName("selWorkDirButton"); // NOI18N
        selWorkDirButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selWorkDirButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(selWorkDirButton);

        openExplButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openExplButton.setText("Open Explorer in Working Folder");
        openExplButton.setEnabled(false);
        openExplButton.setName("openExplButton"); // NOI18N
        openExplButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openExplButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(openExplButton);

        openTermButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        openTermButton.setText("Open Terminal in Working Folder");
        openTermButton.setEnabled(false);
        openTermButton.setName("openTermButton"); // NOI18N
        openTermButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openTermButtonActionPerformed(evt);
            }
        });
        toolsMenu.add(openTermButton);

        killAllGnupMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        killAllGnupMenuItem.setText("Clear all Gnuplot Instances");
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

        showChangeLogButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        showChangeLogButton.setText("Changelog");
        showChangeLogButton.setName("showChangeLogButton"); // NOI18N
        showChangeLogButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showChangeLogButtonActionPerformed(evt);
            }
        });
        helpMenu.add(showChangeLogButton);

        showUserGuideButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0));
        showUserGuideButton.setText("User Guide");
        showUserGuideButton.setName("showUserGuideButton"); // NOI18N
        showUserGuideButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showUserGuideButtonActionPerformed(evt);
            }
        });
        helpMenu.add(showUserGuideButton);

        checkUpdateButton.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F3, 0));
        checkUpdateButton.setText("Check Updates");
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
        aboutBox.setLocationRelativeTo(this);
        aboutBox.setVisible(rootPaneCheckingEnabled);
    }//GEN-LAST:event_showAboutBoxActionPerformed

    private boolean createCommandFile() {
        try {
            FileWriter ryt = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "cmd.txt");
            BufferedWriter out = new BufferedWriter(ryt);
            out.write("");
            out.flush();
            if (fileData1.getText().equals("") && fileData2.getText().equals("") && fileData3.getText().equals("") && fileData4.getText().equals("")) {
                JOptionPane.showMessageDialog(this, "You didn't enter any System files", "System files!", JOptionPane.ERROR_MESSAGE);
                out.close();
                return false;
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
                JOptionPane.showMessageDialog(this, "You didn't enter any Disturbance file", "Disturbance file!", JOptionPane.ERROR_MESSAGE);
                out.close();
                return false;
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
                        JOptionPane.showMessageDialog(this, "Could not create the Observables file!", "Observables file!", JOptionPane.ERROR_MESSAGE);
                        out.close();
                        return false;
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
                        JOptionPane.showMessageDialog(this, "Could not create the Observables file!", "Observables file!", JOptionPane.ERROR_MESSAGE);
                        out.close();
                        return false;
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "You didn't enter any Observables", "Observables missing!", JOptionPane.ERROR_MESSAGE);
                    out.close();
                    return false;
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

            if ((!runtimeObsName.getText().equals("") || runtimeObsType.getSelectedItem().toString().equals("Wall Time")) && !ssa) {
                switch (runtimeObsType.getSelectedItem().toString()) {
                    case "Bus Voltage":
                        out.append("BV " + runtimeObsName.getText());
                        break;
                    case "Center of Intertia":
                        out.append("COI " + runtimeObsName.getText());
                        break;
                    case "Machine Speed":
                        out.append("MS " + runtimeObsName.getText());
                        break;
                    case "Omega-delta of machine":
                        out.append("o-d " + runtimeObsName.getText());
                        break;
                    case "Active power-delta of machine":
                        out.append("P-d " + runtimeObsName.getText());
                        break;
                    case "Wall Time":
                        out.append("RT RT");
                        break;
                    case "Latency":
                        out.append("LAT " + runtimeObsName.getText());
                        break;
                    case "Branch Active Power Origin":
                        out.append("BPO " + runtimeObsName.getText());
                        break;
                    case "Branch Active Power Extremity":
                        out.append("BPE " + runtimeObsName.getText());
                        break;
                    case "Branch Rective Power Origin":
                        out.append("BQO " + runtimeObsName.getText());
                        break;
                    case "Branch Rective Power Extremity":
                        out.append("BQE " + runtimeObsName.getText());
                        break;    
                    case "Injector Observable":
                        out.append("ON " + runtimeObsName.getText());
                        break;
                    default:
                        return false;
                }
                out.newLine();
            }

            if ((!runtimeObsName1.getText().equals("") || runtimeObsType1.getSelectedItem().toString().equals("Wall Time")) && !ssa) {
                switch (runtimeObsType1.getSelectedItem().toString()) {
                    case "Bus Voltage":
                        out.append("BV " + runtimeObsName1.getText());
                        break;
                    case "Center of Intertia":
                        out.append("COI " + runtimeObsName1.getText());
                        break;
                    case "Machine Speed":
                        out.append("MS " + runtimeObsName1.getText());
                        break;
                    case "Omega-delta of machine":
                        out.append("o-d " + runtimeObsName.getText());
                        break;
                    case "Active power-delta of machine":
                        out.append("P-d " + runtimeObsName.getText());
                        break;
                    case "Wall Time":
                        out.append("RT RT");
                        break;
                    case "Latency":
                        out.append("LAT " + runtimeObsName1.getText());
                        break;
                    case "Branch Active Power Origin":
                        out.append("BPO " + runtimeObsName1.getText());
                        break;
                    case "Branch Active Power Extremity":
                        out.append("BPE " + runtimeObsName1.getText());
                        break;
                    case "Branch Rective Power Origin":
                        out.append("BQO " + runtimeObsName1.getText());
                        break;
                    case "Branch Rective Power Extremity":
                        out.append("BQE " + runtimeObsName1.getText());
                        break;    
                    case "Injector Observable":
                        out.append("ON " + runtimeObsName1.getText());
                        break;
                    default:
                        return false;
                }
                out.newLine();
            }

            if ((!runtimeObsName2.getText().equals("") || runtimeObsType2.getSelectedItem().toString().equals("Wall Time")) && !ssa) {
                switch (runtimeObsType2.getSelectedItem().toString()) {
                    case "Bus Voltage":
                        out.append("BV " + runtimeObsName2.getText());
                        break;
                    case "Center of Intertia":
                        out.append("COI " + runtimeObsName2.getText());
                        break;
                    case "Machine Speed":
                        out.append("MS " + runtimeObsName2.getText());
                        break;
                    case "Omega-delta of machine":
                        out.append("o-d " + runtimeObsName.getText());
                        break;
                    case "Active power-delta of machine":
                        out.append("P-d " + runtimeObsName.getText());
                        break;
                    case "Wall Time":
                        out.append("RT RT");
                        break;
                    case "Latency":
                        out.append("LAT " + runtimeObsName2.getText());
                        break;
                    case "Branch Active Power Origin":
                        out.append("BPO " + runtimeObsName2.getText());
                        break;
                    case "Branch Active Power Extremity":
                        out.append("BPE " + runtimeObsName2.getText());
                        break;
                    case "Branch Rective Power Origin":
                        out.append("BQO " + runtimeObsName2.getText());
                        break;
                    case "Branch Rective Power Extremity":
                        out.append("BQE " + runtimeObsName2.getText());
                        break;    
                    case "Injector Observable":
                        out.append("ON " + runtimeObsName2.getText());
                        break;
                    default:
                        return false;
                }
                out.newLine();
            }

            out.newLine();
            out.flush();
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }

    private boolean createPFCCommandFile() {
        try {
            FileWriter ryt = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "PFCcmd.txt");
            BufferedWriter out = new BufferedWriter(ryt);
            out.write("");
            out.flush();
            if (fileData1.getText().equals("") && fileData2.getText().equals("") && fileData3.getText().equals("") && fileData4.getText().equals("")) {
                JOptionPane.showMessageDialog(this, "You didn't enter any System files", "System files!", JOptionPane.ERROR_MESSAGE);
                out.close();
                return false;
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }

    private boolean createCGCommandFile() {
        try {
            FileWriter ryt = new FileWriter(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "CGcmd.txt");
            BufferedWriter out = new BufferedWriter(ryt);
            out.write("");
            out.flush();

            if (codeGenFiles == null) {
                JOptionPane.showMessageDialog(this, "You didn't enter any CG files", "CG files!", JOptionPane.ERROR_MESSAGE);
                out.close();
                return false;
            }
            for (File temp : codeGenFiles) {
                out.append(temp.getAbsolutePath());
                out.newLine();
            }
            out.flush();
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }


    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        Object[] options = {"Yes",
            "Yes (clear all open windows)",
            "Cancel"};
        int confirmed = JOptionPane.showOptionDialog(this, "Are you sure you want to exit? All simulation data will be lost!", "Exit Confirmation", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
        if (confirmed == JOptionPane.YES_OPTION) {
            if (toolDir == null) {
            } else {
                fileOps.deleteDirectory(toolDir);
            }
            System.exit(0);
        } else if (confirmed == JOptionPane.NO_OPTION) {
            if (toolDir == null) {
            } else {
                clearGnuplotButtonActionPerformed(null);
                stopSimulationButtonActionPerformed(null);
                fileOps.deleteDirectory(toolDir);
            }
            System.exit(0);
        }
    }//GEN-LAST:event_formWindowClosing

    private void saveConfigMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveConfigMenuItemActionPerformed
        try {
            fileChooser.setSelectedFile(new File(""));
            fileChooser.setDialogTitle("Choose File");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Config File", "cfg");
            fileChooser.setFileFilter(filter);
            int returnVal = fileChooser.showSaveDialog(this);
            fileChooser.resetChoosableFileFilters();
            fileData10.setText("");
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                String filePath = file.getPath();
                if (!filePath.toLowerCase().endsWith(".cfg")) {
                    file = new File(filePath + ".cfg");
                }
                if (file.exists()) {
                    int response = JOptionPane.showConfirmDialog(null, "Overwrite existing file?", "Confirm Overwrite", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (response == JOptionPane.CANCEL_OPTION) {
                        return;
                    }
                }
                Properties properties = new Properties();
                for (Component c : jPanel2.getComponents()) {
                    if (c instanceof JTextField) {
                        properties.setProperty(((JTextField) c).getName(), ((JTextField) c).getText());
                    } else if (c instanceof JCheckBox) {
                        properties.setProperty(((JCheckBox) c).getName(), new Boolean(((JCheckBox) c).isSelected()).toString());
                    }
                }
                for (Component c : jPanel4.getComponents()) {
                    if (c instanceof JFormattedTextField) {
                        properties.setProperty(((JFormattedTextField) c).getName(), ((JFormattedTextField) c).getText());
                    } else if (c instanceof JCheckBox) {
                        properties.setProperty(((JCheckBox) c).getName(), new Boolean(((JCheckBox) c).isSelected()).toString());
                    } else if (c instanceof JTextField) {
                        properties.setProperty(((JTextField) c).getName(), ((JTextField) c).getText());
                    } else if (c instanceof JComboBox) {
                        properties.setProperty(((JComboBox) c).getName(), new Integer(((JComboBox) c).getSelectedIndex()).toString());
                    }
                }
                properties.store(new FileOutputStream(file), null);
            }
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }

    }//GEN-LAST:event_saveConfigMenuItemActionPerformed

    private void loadConfigMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadConfigMenuItemActionPerformed

        try {
            fileChooser.setSelectedFile(new File(""));
            fileChooser.setDialogTitle("Choose File");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Ramses Config File", "cfg");
            fileChooser.setFileFilter(filter);
            int returnVal = fileChooser.showOpenDialog(this);
            fileChooser.resetChoosableFileFilters();
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                FileInputStream inStream = new FileInputStream(file);
                Properties properties = new Properties();
                properties.load(inStream);
                for (Component c : jPanel2.getComponents()) {
                    if (c instanceof JTextField) {
                        ((JTextField) c).setText(properties.getProperty(((JTextField) c).getName()));
                    } else if (c instanceof JCheckBox) {
                        ((JCheckBox) c).setSelected(Boolean.parseBoolean(properties.getProperty(((JCheckBox) c).getName())));
                    }
                }
                for (Component c : jPanel4.getComponents()) {
                    if (c instanceof JFormattedTextField) {
                        ((JFormattedTextField) c).setText(properties.getProperty(((JFormattedTextField) c).getName()));
                    } else if (c instanceof JCheckBox) {
                        ((JCheckBox) c).setSelected(Boolean.parseBoolean(properties.getProperty(((JCheckBox) c).getName())));
                    } else if (c instanceof JTextField) {
                        ((JTextField) c).setText(properties.getProperty(((JTextField) c).getName()));
                    } else if (c instanceof JComboBox) {
                        ((JComboBox) c).setSelectedIndex(new Integer(properties.getProperty(((JComboBox) c).getName())));
                    }
                }
                observFileWizButtonActionPerformed(evt);
            }
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadConfigMenuItemActionPerformed

    private void saveCommandFileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveCommandFileMenuItemActionPerformed
        try {
            if (!createCommandFile()) {
                JOptionPane.showMessageDialog(this, "<html>Command File not created.</html>", "Command File Error!", JOptionPane.ERROR_MESSAGE);
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
                String file_str = myTempDir.getAbsolutePath() + System.getProperty("file.separator");
                content = content.replaceAll(Matcher.quoteReplacement(file_str), "");
                IOUtils.write(content, new FileOutputStream(dstFile), "UTF-8");

                srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "customObs.txt");
                if (srcFile.exists()) {
                    content = IOUtils.toString(new FileInputStream(srcFile), "UTF-8");
                    content = content.replaceAll(Matcher.quoteReplacement(file_str), "");
                    IOUtils.write(content, new FileOutputStream(srcFile), "UTF-8");
                    fileOps.copyFiletoDir(srcFile, dstFile.getAbsoluteFile());
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_saveObsFileMenuItemActionPerformed

    private void openTermButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openTermButtonActionPerformed
        try {
            PlatformLauncher.openTerminal(myTempDir);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_openTermButtonActionPerformed

    private void openNppButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openNppButtonActionPerformed
        nppOpen(evt, "");
    }//GEN-LAST:event_openNppButtonActionPerformed

    private void nppOpen(java.awt.event.ActionEvent evt, String filename) {
        File target = filename.isEmpty() ? myTempDir : new File(filename);
        if (!target.exists()) {
            JOptionPane.showMessageDialog(this,
                    "<html>The file <B>" + target.getName() + "</B> does not exist.</html>",
                    "File not found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            PlatformLauncher.openInEditor(target);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open an editor for " + target.getAbsolutePath()
                    + "\n\n" + ex.getMessage(),
                    "Editor error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openExplButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openExplButtonActionPerformed
        try {
            PlatformLauncher.openFileManager(myTempDir);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            // /releases/latest redirects to /releases/tag/<tag>: the redirect
            // names the release, so following it only to scrape the page for
            // the same string would be wasted work. Redirects are therefore
            // off and the Location header is read directly.
            HttpURLConnection connection =
                    (HttpURLConnection) new URL(RELEASES_LATEST_URL).openConnection();
            String location;
            try {
                connection.setInstanceFollowRedirects(false);
                // This runs on the EDT, as every action listener here does. An
                // unreachable host with no timeout set freezes the window
                // until the OS gives up, which is minutes on some networks.
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                location = connection.getHeaderField("Location");
            } finally {
                connection.disconnect();
            }
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
                versionLabel.setText("<html><B><U>Version</U>:</B> " + this_version + " (latest version available: " + current_version + ")</html>");
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not reach the releases page to check for updates.<br />"
                    + "If you are sure you have internet access, please report this.</html>",
                    "Update Manager", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_checkUpdateButtonActionPerformed

    private void showGnupCopyrightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showGnupCopyrightButtonActionPerformed
        try {
            InputStream in;
            in = RamsesUI.class.getResourceAsStream("gnuplotLicense.txt");
            File gnupCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "gnuplotLicense.txt");
            OutputStream streamOut;
            streamOut = FileUtils.openOutputStream(gnupCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(gnupCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showGnupCopyrightButtonActionPerformed

    private void webpageLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_webpageLabelMouseClicked
        String url = "https://stepss.sps-lab.org";
        BareBonesBrowserLaunch.openURL(url);
    }//GEN-LAST:event_webpageLabelMouseClicked

    private void showApacheLicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showApacheLicenseButtonActionPerformed
        try {
            InputStream in;
            in = RamsesUI.class.getResourceAsStream("apacheLicense.txt");
            File gnupCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "apacheLicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(gnupCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(gnupCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showApacheLicenseButtonActionPerformed

    private void killAllGnupMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_killAllGnupMenuItemActionPerformed
        clearGnuplotButtonActionPerformed(evt);
    }//GEN-LAST:event_killAllGnupMenuItemActionPerformed

    private void showKLULicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showKLULicenseButtonActionPerformed
        try {
            InputStream in;
            in = RamsesUI.class.getResourceAsStream("KLULicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "KLULicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            File dstFile = new File(myTempDir.getAbsolutePath()
                    + System.getProperty("file.separator") + base + "Eig.dst");
            FileUtils.writeStringToFile(dstFile,
                    "0.000 CONTINUE SOLVER TR 0.010 0.001 0. ALL\n"
                    + "0.000 EIG '" + base + "'\n"
                    + "0.010 STOP\n", "UTF-8");

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
                JOptionPane.showMessageDialog(this,
                        "No results were produced. Small-signal analysis needs $OMEGA_REF SYN and\n"
                        + "$SCHEME DE in the solver settings, and a system within $EIG_MAX_STATES.\n"
                        + "See the log for the reason.",
                        "Small-signal analysis", JOptionPane.WARNING_MESSAGE);
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
            showSsaResults(resultsDir, base);
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_ssaButton1ActionPerformed

    /** Loads one run and shows it, reporting a parse failure rather than swallowing it. */
    private void showSsaResults(File dir, String basename) {
        try {
            my.ramses.ssa.SsaResultsWindow.open(this,
                    my.ramses.ssa.SsaResults.load(dir, basename));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not read the results in " + dir + "\n\n" + ex.getMessage(),
                    "Small-signal results", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void viewSsaResultsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewSsaResultsActionPerformed
        fileChooser.setSelectedFile(new File(""));
        fileChooser.setDialogTitle("Choose a directory containing small-signal results");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dir = fileChooser.getSelectedFile();
        java.util.List<String> found = my.ramses.ssa.SsaResults.basenames(dir);
        if (found.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No small-signal results in " + dir + "\n\n"
                    + "Looking for a file named <basename>_modes.dat.",
                    "Small-signal results", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String chosen = found.get(0);
        if (found.size() > 1) {
            Object picked = JOptionPane.showInputDialog(this,
                    "That directory holds more than one run. Which one?",
                    "Small-signal results", JOptionPane.QUESTION_MESSAGE, null,
                    found.toArray(), found.get(0));
            if (picked == null) {
                return;
            }
            chosen = (String) picked;
        }
        showSsaResults(dir, chosen);
    }//GEN-LAST:event_viewSsaResultsActionPerformed

    private void ssaButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ssaButtonActionPerformed
        try {
            InputStream in = RamsesUI.class.getResourceAsStream("dampJac.dst");
            File tmpFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "dampJac.dst");
            OutputStream streamOut = FileUtils.openOutputStream(tmpFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            String tmpString = fileDist.getText();
            fileDist.setText("dampJac.dst");
            ssa = true;
            runSimulationActionPerformed(evt);
            simulExecutorResultHandler.waitFor();
            fileDist.setText(tmpString);

            if (!"".equals(ssaDirectory.getText())) {
                String[] produced = {"jac_eqs.dat", "jac_var.dat", "jac_val.dat", "jac_struc.dat"};
                for (String name : produced) {
                    File srcFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + name);
                    File dstFile = new File(ssaDirectory.getText() + System.getProperty("file.separator") + name);
                    if (srcFile.exists()) {
                        fileOps.copyFiletoFile(srcFile, dstFile);
                    }
                }
            }

        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_ssaButtonActionPerformed

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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
                    content = content.replaceAll(Matcher.quoteReplacement(file_str), fileChooser.getSelectedFile().getName() + ".cur");
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
                Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
                    Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
                    JOptionPane.showMessageDialog(RamsesUI.this,
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
            JOptionPane.showMessageDialog(this,
                    "<html>The trajectory file carries no observables to plot.</html>",
                    "Nothing to extract", JOptionPane.WARNING_MESSAGE);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
                                JOptionPane.showMessageDialog(RamsesUI.this,
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
                simulationOutput.setCaretPosition(simulationOutput.getLineOfOffset(highlighterIndex));
                RXTextUtilities.gotoFirstWordOnLine(simulationOutput, simulationOutput.getLineOfOffset(highlighterIndex) + 1);
            } else {
                if (highlighterLen == 0) {
                    JOptionPane.showMessageDialog(this, "<html>The word <B>" + searchTextField.getText() + "</B> does not exist.</html>", "Keyword not found!", JOptionPane.WARNING_MESSAGE);
                    highlighterIndex = 0;
                    highlighterLen = 0;
                    highlighter.removeAllHighlights();
                } else {
                    JOptionPane.showMessageDialog(this, "<html>No more occurences. Press enter to start again.</html>", "Keyword not found!", JOptionPane.WARNING_MESSAGE);
                    highlighterIndex = 0;
                    highlighter.removeAllHighlights();
                }
            }
        } catch (BadLocationException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_searchTextFieldActionPerformed

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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_saveSimulOutputActionPerformed

    private void runSimulationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runSimulationActionPerformed
        if (!createCommandFile()) {
            JOptionPane.showMessageDialog(this, "<html>Command File not created.</html>", "Command File Error!", JOptionPane.ERROR_MESSAGE);
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
            runSimulation.setEnabled(false);
            runDyngraphButton.setEnabled(false);
            saveTrajToFileButton.setEnabled(false);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
                    Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
                }
                runSimulation.setEnabled(true);
                saveTrajToFileButton.setEnabled(true);
                if (saveOutputTrajButton.isSelected()) {
                    saveTrajToFileButton.setEnabled(true);
                    runDyngraphButton.setEnabled(true);
                } else {
                    saveTrajToFileButton.setEnabled(false);
                    runDyngraphButton.setEnabled(false);
                }
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
                    busObsField.setText("Already in List!");
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
            JOptionPane.showMessageDialog(this, "<html>Now you need to add an observables files or use the Observable File Wizard</html>", "Observables", JOptionPane.INFORMATION_MESSAGE);
        }
    }//GEN-LAST:event_saveOutputTrajButtonActionPerformed

    private void clearObsFileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearObsFileButtonActionPerformed
        fileObs.setText("");
        runtimeObsName.setText("");
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
                    Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private void runPFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runPFActionPerformed
        if (!createPFCCommandFile()) {
            JOptionPane.showMessageDialog(this, "<html>Command File for Helios not created.</html>", "Command File Error!", JOptionPane.ERROR_MESSAGE);
            return;
        }
        CommandLine command;

        //        simulationOutput.setText("");
        fileData10.setText("");
        loadOutputActionPerformed(evt);
        savedOutputBool = false;

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
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
                    Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (f.exists()) {
                    loadOutput.setEnabled(true);
                    loadBusOverview.setEnabled(true);
                    loadGens.setEnabled(true);
                    loadTrfos.setEnabled(true);
                    loadPow.setEnabled(true);
                    loadLFRESV2DAT.setEnabled(true);
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
     * rather than a record: RamsesUI targets Java 11 syntax.
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

    private void loadBusOverviewActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadBusOverviewActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(pfcPane.getText());
                outwriter.close();
            }
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadBusOverviewActionPerformed

    private void loadGensActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadGensActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(pfcPane.getText());
                outwriter.close();
            }
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadGensActionPerformed

    private void loadLFRESV2DATActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadLFRESV2DATActionPerformed
        fileData10.setText(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "in_volt_trfo.dat");
    }//GEN-LAST:event_loadLFRESV2DATActionPerformed

    private void loadTrfosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadTrfosActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(pfcPane.getText());
                outwriter.close();
            }
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_loadTrfosActionPerformed

    private void loadPowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadPowActionPerformed
        try {
            if (!savedOutputBool) {
                File outfile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "output.trace");
                FileWriter outwriter = new FileWriter(outfile);
                outwriter.write(pfcPane.getText());
                outwriter.close();
            }
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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

    if (!createCGCommandFile()) {
        JOptionPane.showMessageDialog(this, "<html>Command File for CG not created.</html>", "Command File Error!", JOptionPane.ERROR_MESSAGE);
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
        Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
                Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }//GEN-LAST:event_saveCGFilesActionPerformed

    
    
    private void CompileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CompileActionPerformed
        if (compileInProgress) {
            JOptionPane.showMessageDialog(this,
                    "<html>A build is already running. Wait for it to finish before"
                    + " starting another.</html>",
                    "Build in progress", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (codeGenFiles == null || codeGenFiles.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "<html>No generated models to compile. Run Codegen first.</html>",
                    "Nothing to compile", JOptionPane.WARNING_MESSAGE);
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
                            JOptionPane.showMessageDialog(RamsesUI.this,
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
                            JOptionPane.showMessageDialog(RamsesUI.this,
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
                            JOptionPane.showMessageDialog(RamsesUI.this,
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
                            JOptionPane.showMessageDialog(RamsesUI.this,
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
        JOptionPane.showMessageDialog(this,
                "<html>Custom simulator built.<br>Simulations will now run on it"
                + " instead of the bundled engine.</html>",
                "Compilation complete", JOptionPane.INFORMATION_MESSAGE);
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
            in = RamsesUI.class.getResourceAsStream("ramsesLicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "ramsesLicense.txt");
            OutputStream streamOut;
            streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showRAMSESLicenseButtonActionPerformed

    private void showPFCLicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showPFCLicenseButtonActionPerformed
        try {
            InputStream in;
            in = RamsesUI.class.getResourceAsStream("heliosLicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "heliosLicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showPFCLicenseButtonActionPerformed

    private void showCODEGENLicenseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showCODEGENLicenseButtonActionPerformed
        try {
            InputStream in;
            in = RamsesUI.class.getResourceAsStream("codegenLicense.txt");
            File kluCopyrightFile = new File(myTempDir.getAbsolutePath() + System.getProperty("file.separator") + "codegenLicense.txt");
            OutputStream streamOut = FileUtils.openOutputStream(kluCopyrightFile);
            IOUtils.copy(in, streamOut);
            in.close();
            streamOut.close();
            PlatformLauncher.openInEditor(kluCopyrightFile);
        } catch (IOException ex) {
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_showCODEGENLicenseButtonActionPerformed

    private void savedynsimActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savedynsimActionPerformed
        if (modelCompiler == null || modelCompiler.builtExecutable() == null) {
            JOptionPane.showMessageDialog(this,
                    "<html>No compiled simulator to save. Compile first.</html>",
                    "Nothing to save", JOptionPane.WARNING_MESSAGE);
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this,
                    "<html>Could not save the simulator:<br>" + escapeHtml(describe(ex))
                    + "</html>", "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_savedynsimActionPerformed
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /*
         * Set the Nimbus look and feel
         */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /*
         * If Nimbus (introduced in Java SE 6) is not available, stay with the
         * default look and feel. For details see
         * http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
//            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//                if ("Nimbus".equals(info.getName())) {
//                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
//                    break;
//                }
//            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(RamsesUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /*
         * Create and display the form
         */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                RamsesUI RamsesFrame = new RamsesUI();
                RamsesFrame.setVisible(true);
                RamsesFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
        });
    }
    private Boolean savedOutputBool = false;
    private Platform platform;
    private Toolchain toolchain;
    private File toolDir = null;
    private File myTempDir = null;
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
    private javax.swing.JTextField GP_REFRESH_RATE;
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
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
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
    private javax.swing.JButton ssaButton;
    private javax.swing.JButton ssaButton1;
    private javax.swing.JTextField ssaDirectory;
    private javax.swing.JTextField ssaPfThreshold;
    private javax.swing.JLabel ssaPfThresholdLabel;
    private javax.swing.JTextField ssaRealLimit;
    private javax.swing.JLabel ssaRealLimitLabel;
    private javax.swing.JButton stopSimulationButton;
    private javax.swing.JTextField syncObsField;
    private javax.swing.JComboBox syncObsList;
    private javax.swing.JMenu toolsMenu;
    private javax.swing.JLabel versionLabel;
    private javax.swing.JLabel versionLabel1;
    private javax.swing.JLabel versionLabel2;
    private javax.swing.JButton viewCurvesButton;
    private javax.swing.JButton viewSsaResults;
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
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
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
            toolchain.extractAll();

            myTempDir = (selWorkDir != null) ? selWorkDir : toolDir;

            openExplButton.setEnabled(true);
            openTermButton.setEnabled(true);

            ramsesExec = adoptedSimulator();
            heliosExec = toolchain.helios();
            dyngraphExec = toolchain.dyngraph();
            codegenExec = toolchain.codegen();
            gnuplotExec = toolchain.gnuplot();
            WinEnvironment = PlatformLauncher.execEnvironment(platform, toolDir);

            if ((gnuplotExec == null || !gnuplotExec.exists()) && !gnuplotMissingWarned) {
                gnuplotMissingWarned = true;
                JOptionPane.showMessageDialog(this,
                        "<html>gnuplot was not found, so real-time plotting is disabled."
                        + "<br>Install it and restart to enable it: <b>" + gnuplotInstallHint()
                        + "</b></html>",
                        "gnuplot not found", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not prepare the simulation toolchain.\n\n" + ex.getMessage(),
                    "Toolchain error", JOptionPane.ERROR_MESSAGE);
            Logger.getLogger(RamsesUI.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
}
