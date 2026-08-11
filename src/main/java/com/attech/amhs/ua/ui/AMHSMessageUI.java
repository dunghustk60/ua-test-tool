/*
 * AMHS X.400 Message Tool - Main UI
 * A Swing-based UI for sending and receiving AMHS X.400 messages
 */
package com.attech.amhs.ua.ui;

import com.attech.amhs.ua.export.XlsxExporter;
import com.attech.amhs.ua.model.MessageLog;
import com.attech.amhs.ua.model.TestCase;
import com.attech.amhs.ua.model.TestSubcase;
import com.attech.amhs.ua.repository.TestCaseRepository;
import com.attech.amhs.ua.service.AMHSMessageService;
import com.attech.amhs.ua.service.TestCaseConfigLoader;
import com.attech.amhs.ua.service.TestCaseLoader;
import com.attech.amhs.ua.service.TestSessionRecorder;
import com.attech.amhs.ua.ui.components.*;
import com.isode.x400.highlevel.X400APIException;
import com.isode.x400.highlevel.X400Msg.X400_Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Main UI for AMHS X.400 Message Tool.
 *
 * Window layout:
 * ┌───────────────────────────────────────────────────────────────────────┐
 * │ TOOLBAR [timer | case pass/fail/note | msg pass/fail/note | results |
 * export | settings] │
 * ├────────────────┬──────────────────────────────┬────────────────────────┤
 * │ CASE panel │ Message Operation (top) │ Messages (right) │
 * │ (JTree) │──────────────────────────────│ │
 * │ │ Connect Config (bottom) │ │
 * ├────────────────┴──────────────────────────────┴────────────────────────┤
 * │ Action Logs (collapsible strip) │
 * └─────────────────────────────────────────────────────────────────────────┘
 */

public class AMHSMessageUI extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(AMHSMessageUI.class);
    // Services
    private AMHSMessageService messageService;
    private TestCaseRepository repository;
    private TestSessionRecorder recorder;

    // Panels
    private ToolbarPanel toolbarPanel;
    private TestCaseSelectorPanel selectorPanel;
    private DescriptionPanel descriptionPanel;
    private TestMarkingPanel markingPanel;

    // Connection-config fields (center-bottom pane)
    private JTextField txtPresentationAddress;
    private JTextField txtUserOrAddress;
    private JPasswordField txtPassword;
    private JRadioButton radioP7;
    private JRadioButton radioP3;
    private JLabel lblConnectionStatus;

    // ── Fields for message operations ─────────────────────────────────────

    // Basic fields
    private JTextField txtRecipient;
    private JTextField txtSubject;
    private JTextArea txtContent;
    private JComboBox<X400_Priority> comboPriority;

    // AMHS-specific fields (new)
    private JTextField txtOHI; // Optional Heading Info
    private JTextField txtATSPriority; // ATS Priority (KK/GG/FF/DD/SS)
    private JComboBox<String> comboEncoding;
    private JComboBox<String> comboCharset;
    private JComboBox<String> comboContentType;
    private JComboBox<String> comboBodyPartType;
    private JComboBox<String> comboMsgType;
    private JComboBox<String> comboDrReq;
    private JComboBox<String> comboRnReq;
    private JComboBox<String> comboMtaReportReq;
    private JTextField txtATSHeader; // Custom ATS Header
    private JTextField txtPayloadSize; // Generated payload size in bytes
    private JTextField txtFTBPFileName; // FTBP File Name
    private JTextArea txtFTBPContent; // FTBP Content

    // ── Constructor ───────────────────────────────────────────────────────

    public AMHSMessageUI() {
        messageService = new AMHSMessageService();
        repository = new TestCaseRepository();
        recorder = new TestSessionRecorder();

        initializeTestCases();
        initUI();
        loadConfigFromFile();
    }

    private void initializeTestCases() {
        // First load the default test cases structure
        List<TestCase> testCases = TestCaseLoader.loadDefaultTestCases();

        // Then override with cases loaded from properties files (which include AMHS
        // defaults)
        List<TestCase> configuredTestCases = TestCaseConfigLoader.loadAllTestCases();
        if (!configuredTestCases.isEmpty()) {
            testCases = configuredTestCases;
        }

        repository.initializeWithTestCases(testCases);
    }

    // ── Main UI assembly ──────────────────────────────────────────────────

    private void initUI() {
        setTitle("AMHS X.400 Message Tool – Test Case Manager");
        setSize(1440, 900);
        setMinimumSize(new Dimension(1100, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));

        // ── North: toolbar ─────────────────────────────────────────────
        toolbarPanel = new ToolbarPanel(recorder);
        wireToolbarCallbacks();
        root.add(toolbarPanel, BorderLayout.NORTH);

        // Instantiate descriptionPanel early so it's available for selectorPanel
        descriptionPanel = new DescriptionPanel();
        descriptionPanel.setPreferredSize(new Dimension(0, 200));

        // ── Center: three-column split ─────────────────────────────────
        root.add(buildThreeColumnSplit(), BorderLayout.CENTER);

        // ── South: description & buttons strip ──────────────────────
        JPanel bottomStrip = new JPanel(new BorderLayout(5, 5));
        bottomStrip.add(descriptionPanel, BorderLayout.CENTER);

        // Right side buttons for bottom strip
        JPanel btnPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        btnPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton btnLoadDefaults = new JButton("Load defaults");
        btnLoadDefaults.setToolTipText("Copy default AMHS fields for selected message to the Message Operations panel");
        btnLoadDefaults.addActionListener(e -> {
            if (selectorPanel != null)
                selectorPanel.triggerLoadDefaults();
        });

        JButton btnSendDefaults = new JButton("Send defaults");
        btnSendDefaults.setToolTipText("Load defaults and immediately send the message");
        btnSendDefaults.addActionListener(e -> {
            if (selectorPanel != null)
                selectorPanel.triggerSendDefaults();
        });

        JButton btnSendAllSubcases = new JButton("Send All Subcases");
        btnSendAllSubcases.setToolTipText("Send messages for ALL subcases under the selected test case");
        btnSendAllSubcases.addActionListener(e -> {
            if (selectorPanel != null)
                selectorPanel.triggerSendAllSubcases();
        });

        btnPanel.add(btnLoadDefaults);
        btnPanel.add(btnSendDefaults);
        btnPanel.add(btnSendAllSubcases);

        JPanel btnWrapper = new JPanel(new GridBagLayout());
        btnWrapper.add(btnPanel);
        bottomStrip.add(btnWrapper, BorderLayout.EAST);

        root.add(bottomStrip, BorderLayout.SOUTH);

        setContentPane(root);
    }

    /**
     * Three-column horizontal split:
     * LEFT – case selector (JTree)
     * CENTER – message-ops (top) + connect-config (bottom)
     * RIGHT – messages
     */
    private JSplitPane buildThreeColumnSplit() {
        // LEFT panel
        selectorPanel = new TestCaseSelectorPanel(repository);
        selectorPanel.setDescriptionPanel(descriptionPanel); // Set reference to display descriptions
        selectorPanel.addSelectionChangedListener("main", this::handleLoadDefaultsForSubcase);
        selectorPanel.addDefaultsLoadedListener("main", this::handleLoadDefaultsForSubcase);
        selectorPanel.addSendDefaultsListener("main", this::handleSendDefaults);
        selectorPanel.addSendAllSubcasesListener("main", this::handleSendAllSubcases);
        JScrollPane leftScroll = new JScrollPane(selectorPanel);
        leftScroll.setMinimumSize(new Dimension(240, 0));

        // CENTER panel (message ops / connect config vertical split)
        JSplitPane centerSplit = buildCenterSplit();
        centerSplit.setMinimumSize(new Dimension(380, 0));

        // RIGHT panel – messages
        markingPanel = new TestMarkingPanel(repository);
        JScrollPane rightScroll = new JScrollPane(markingPanel);
        rightScroll.setMinimumSize(new Dimension(280, 0));

        // Left | Center
        JSplitPane leftCenter = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, leftScroll, centerSplit);
        leftCenter.setDividerLocation(270);
        leftCenter.setResizeWeight(0.0);
        leftCenter.setContinuousLayout(true);

        // (Left | Center) | Right
        JSplitPane full = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, leftCenter, rightScroll);
        full.setDividerLocation(780);
        full.setResizeWeight(0.6);
        full.setContinuousLayout(true);

        return full;
    }

    /**
     * Center column: Message Operation panel on top, Connect Config on bottom.
     * Both halves have titled borders and are separated by a draggable divider.
     */
    private JSplitPane buildCenterSplit() {
        JPanel msgOpsPanel = createMessageOperationsPanel();
        JScrollPane msgScroll = new JScrollPane(msgOpsPanel);

        JPanel connPanel = createConnectionConfigPanel();
        JScrollPane connScroll = new JScrollPane(connPanel);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, msgScroll, connScroll);
        split.setDividerLocation(0.55);
        split.setResizeWeight(0.55);
        split.setContinuousLayout(true);
        return split;
    }

    // ── Panel builders ────────────────────────────────────────────────────

    /** Center-top: Message Operation */
    private JPanel createMessageOperationsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Message Operation"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Recipient
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Recipient:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        txtRecipient = new JTextField("/CN=P7User1/OU=Sales/O=nova/PRMD=Isode/ADMD= /C=GB/", 30);
        panel.add(txtRecipient, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        JButton btnBrowseRecipient = new JButton("Browse...");
        btnBrowseRecipient.addActionListener(e -> browseAndLoadFile(txtRecipient, true));
        panel.add(btnBrowseRecipient, gbc);

        // Subject
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Subject:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        txtSubject = new JTextField("Test X.400 Message", 30);
        panel.add(txtSubject, gbc);

        // Message Type
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Msg Type:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboMsgType = new JComboBox<>(new String[] {
                "IPM", "Probe", "RN"
        });
        panel.add(comboMsgType, gbc);

        // Priority
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Priority:"), gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboPriority = new JComboBox<>(new X400_Priority[] {
                X400_Priority.NORMAL_PRIORITY,
                X400_Priority.LOW_PRIORITY,
                X400_Priority.HIGH_PRIORITY
        });
        panel.add(comboPriority, gbc);

        // ATS Priority (AMHS-specific)
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("ATS Priority:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        txtATSPriority = new JTextField("FF", 8);
        txtATSPriority.setToolTipText("ATS Priority code: KK/GG/FF/DD/SS");
        panel.add(txtATSPriority, gbc);

        // DR Request
        gbc.gridx = 2;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("DR Req:"), gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboDrReq = new JComboBox<>(new String[] {
                "None", "NDR", "DR", "Both"
        });
        comboDrReq.setSelectedItem("NDR");
        panel.add(comboDrReq, gbc);

        // RN Request
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("RN Req:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboRnReq = new JComboBox<>(new String[] {
                "None", "NRN", "RN", "Both"
        });
        comboRnReq.setSelectedItem("None");
        panel.add(comboRnReq, gbc);

        // Originating MTA Report Request
        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("MTA Report Req:"), gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboMtaReportReq = new JComboBox<>(new String[] {
                "No Report", "Non-Delivery Report", "Report", "Audited Report"
        });
        comboMtaReportReq.setSelectedItem("No Report");
        panel.add(comboMtaReportReq, gbc);

        // OHI (Optional Heading Info)
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("OHI:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        txtOHI = new JTextField("", 30);
        txtOHI.setToolTipText("Optional Heading Info");
        panel.add(txtOHI, gbc);

        // Content
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Content:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        txtContent = new JTextArea(6, 30);
        txtContent.setText("This is a test message sent via AMHS X.400.");
        panel.add(new JScrollPane(txtContent), gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JButton btnBrowseContent = new JButton("Browse...");
        btnBrowseContent.addActionListener(e -> browseAndLoadFile(txtContent, false));
        panel.add(btnBrowseContent, gbc);

        // Encoding (AMHS-specific)
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Encoding:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboEncoding = new JComboBox<>(new String[] {
                "IA5", "UTF-8", "ISO-8859-1", "General Text"
        });
        comboEncoding.setSelectedItem("IA5");
        panel.add(comboEncoding, gbc);

        // Character Set (AMHS-specific)
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Charset:"), gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboCharset = new JComboBox<>(new String[] {
                "6", "106", "3", "4", "8"
        });
        comboCharset.setSelectedItem("6");
        comboCharset.setEditable(true);
        comboCharset.setToolTipText("Charset registry number (6=IA5, 106=UTF-8)");
        panel.add(comboCharset, gbc);

        // Content Type (AMHS-specific)
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Content Type:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboContentType = new JComboBox<>(new String[] {
                "22", "0", "1", "2"
        });
        comboContentType.setSelectedItem("22");
        comboContentType.setEditable(true);
        comboContentType.setToolTipText("X.400 Content Type (22=AMHS)");
        panel.add(comboContentType, gbc);

        // ATS Header (custom)
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("ATS Header:"), gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        txtATSHeader = new JTextField("", 8);
        txtATSHeader.setToolTipText("Custom ATS Header value");
        panel.add(txtATSHeader, gbc);

        // Body Part Type (AMHS-specific - FTBP support)
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Body Part Type:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        comboBodyPartType = new JComboBox<>(new String[] {
                "ia5-text", "general-text-body-part", "file-transfer-body-part"
        });
        comboBodyPartType.setSelectedItem("ia5-text");
        comboBodyPartType.setEditable(true);
        comboBodyPartType.setToolTipText("Body part type for the message");
        panel.add(comboBodyPartType, gbc);

        // FTBP File Name (only shown/used when file-transfer-body-part is selected)
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("FTBP File Name:"), gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0.5;
        txtFTBPFileName = new JTextField("attachment.bin", 8);
        txtFTBPFileName.setToolTipText("File name for file-transfer-body-part");
        panel.add(txtFTBPFileName, gbc);

        // Generated payload size
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(new JLabel("Payload Size (bytes):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.5;
        txtPayloadSize = new JTextField("", 8);
        txtPayloadSize.setToolTipText("Positive payload size in bytes; blank keeps the content unchanged");
        panel.add(txtPayloadSize, gbc);

        // FTBP Content (multi-line for file content)
        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("FTBP Content:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;
        txtFTBPContent = new JTextArea(3, 30);
        txtFTBPContent.setText("");
        txtFTBPContent.setToolTipText("Content for file-transfer-body-part (will be converted to bytes)");
        panel.add(new JScrollPane(txtFTBPContent), gbc);

        // Buttons
        gbc.gridy = 12;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;

        gbc.gridx = 0;
        JButton btnSend = new JButton("Send Message");
        btnSend.addActionListener(e -> sendMessage());
        panel.add(btnSend, gbc);

        gbc.gridx = 1;
        JButton btnReceive = new JButton("Receive Messages");
        btnReceive.addActionListener(e -> receiveMessages());
        panel.add(btnReceive, gbc);

        gbc.gridx = 2;
        JButton btnMailbox = new JButton("Get Mailbox Summary");
        btnMailbox.addActionListener(e -> getMailboxSummary());
        panel.add(btnMailbox, gbc);

        gbc.gridx = 3;
        JButton btnClearLogs = new JButton("Clear Logs");
        btnClearLogs.addActionListener(e -> {
            if (markingPanel != null)
                markingPanel.clearMessages();
        });
        panel.add(btnClearLogs, gbc);

        return panel;
    }

    /** Center-bottom: Connect Config */
    private JPanel createConnectionConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Connect Config"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Presentation Address
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Presentation Address:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        txtPresentationAddress = new JTextField(
                "\"3001\"/Internet=nova.isode.net+3001", 30);
        panel.add(txtPresentationAddress, gbc);

        // User/O/R Address
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("User/O/R Address:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        txtUserOrAddress = new JTextField(
                "/CN=P7User1/OU=Sales/O=nova/PRMD=Isode/ADMD= /C=GB/", 30);
        panel.add(txtUserOrAddress, gbc);

        // Password + connection type
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        txtPassword = new JPasswordField("secret", 20);
        panel.add(txtPassword, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        ButtonGroup grp = new ButtonGroup();
        radioP7 = new JRadioButton("P7 (Message Store)");
        radioP3 = new JRadioButton("P3 (Channel)");
        radioP7.setSelected(true);
        grp.add(radioP7);
        grp.add(radioP3);
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        radioPanel.add(radioP7);
        radioPanel.add(radioP3);
        panel.add(radioPanel, gbc);

        // Connect / Disconnect
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        JButton btnConnect = new JButton("Connect");
        btnConnect.addActionListener(e -> connect());
        panel.add(btnConnect, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        JButton btnDisconnect = new JButton("Disconnect");
        btnDisconnect.addActionListener(e -> disconnect());
        panel.add(btnDisconnect, gbc);

        // Load / Save Config
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        JButton btnLoad = new JButton("Load Config File");
        btnLoad.addActionListener(e -> loadConfigFromFile());
        panel.add(btnLoad, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        JButton btnSave = new JButton("Save Config File");
        btnSave.addActionListener(e -> saveConfigToFile());
        panel.add(btnSave, gbc);

        // Status
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 4;
        lblConnectionStatus = new JLabel("Status: Disconnected");
        lblConnectionStatus.setForeground(Color.RED);
        panel.add(lblConnectionStatus, gbc);

        return panel;
    }

    // ── Toolbar callback wiring ────────────────────────────────────────────

    private void wireToolbarCallbacks() {
        toolbarPanel.setOnMarkCasePass(() -> markSelectedCase("PASS"));
        toolbarPanel.setOnMarkCaseFail(() -> markSelectedCase("FAIL"));
        toolbarPanel.setOnNoteCase(() -> addNoteToCase());

        toolbarPanel.setOnMarkMsgPass(() -> markSelectedMessage("PASS"));
        toolbarPanel.setOnMarkMsgFail(() -> markSelectedMessage("FAIL"));
        toolbarPanel.setOnNoteMsg(() -> addNoteToMessage());

        toolbarPanel.setOnShowResults(() -> {
            ResultsPopupDialog dlg = new ResultsPopupDialog(this, repository);
            dlg.setVisible(true);
        });

        toolbarPanel.setOnExport(() -> handleExport());

        toolbarPanel.setOnSettings(() -> {
            SettingsDialog dlg = new SettingsDialog(this);
            dlg.setVisible(true);
        });
    }

    // ── Marking helpers ────────────────────────────────────────────────────

    private void markSelectedCase(String result) {
        TestCase tc = getSelectedCase();
        if (tc == null) {
            JOptionPane.showMessageDialog(this,
                    "No test case selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        repository.markTestCase(tc.getId(), result, null);
        selectorPanel.refresh();
        appendOutput("Marked case " + tc.getId() + " as " + result);
    }

    private void markSelectedMessage(String result) {
        TestCase tc = getSelectedCase();
        TestSubcase sc = getSelectedSubcase();
        if (tc == null || sc == null) {
            JOptionPane.showMessageDialog(this,
                    "No message (subcase) selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        repository.markSubcase(tc.getId(), sc.getId(), result, null);
        selectorPanel.refresh();
        appendOutput("Marked message " + sc.getId() + " as " + result);
    }

    private void addNoteToCase() {
        TestCase tc = getSelectedCase();
        if (tc == null) {
            JOptionPane.showMessageDialog(this,
                    "No test case selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String note = JOptionPane.showInputDialog(this,
                "Enter note for case " + tc.getId() + ":",
                tc.getComment() != null ? tc.getComment() : "");
        if (note != null) {
            tc.setComment(note);
            selectorPanel.refresh();
            appendOutput("Note added to case " + tc.getId());
        }
    }

    private void addNoteToMessage() {
        TestCase tc = getSelectedCase();
        TestSubcase sc = getSelectedSubcase();
        if (tc == null || sc == null) {
            JOptionPane.showMessageDialog(this,
                    "No message (subcase) selected.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String note = JOptionPane.showInputDialog(this,
                "Enter note for message " + sc.getId() + ":",
                sc.getComment() != null ? sc.getComment() : "");
        if (note != null) {
            sc.setComment(note);
            selectorPanel.refresh();
            appendOutput("Note added to message " + sc.getId());
        }
    }

    private void handleExport() {
        JFileChooser fc = new JFileChooser(SettingsDialog.getDefaultExportPath());
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setSelectedFile(new File(
                SettingsDialog.getDefaultExportPath() + File.separator +
                        "amhs_test_results_" + System.currentTimeMillis() + ".xlsx"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                new XlsxExporter(repository, recorder.getTestSession())
                        .export(fc.getSelectedFile().getAbsolutePath());
                JOptionPane.showMessageDialog(this,
                        "Exported to:\n" + fc.getSelectedFile().getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Export failed: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Selection helpers ─────────────────────────────────────────────────

    private TestCase getSelectedCase() {
        return selectorPanel != null ? selectorPanel.getSelectedTestCase() : null;
    }

    private TestSubcase getSelectedSubcase() {
        return selectorPanel != null ? selectorPanel.getSelectedSubcase() : null;
    }

    // ── Connect / Disconnect ──────────────────────────────────────────────

    private void connect() {
        lblConnectionStatus.setText("Status: Connecting…");
        lblConnectionStatus.setForeground(Color.BLUE);
        appendOutput("Initiating connection to X.400 system…");

        String address = txtPresentationAddress.getText().trim();
        String userAddr = txtUserOrAddress.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (radioP3.isSelected()) {
            messageService.configureP3(address, userAddr, password);
        } else {
            messageService.configureP7(address, userAddr, password);
        }
        messageService.setConnectTimeout(90);

        new Thread(() -> {
            try {
                messageService.connect();
                SwingUtilities.invokeLater(() -> {
                    lblConnectionStatus.setText("Status: Connected");
                    lblConnectionStatus.setForeground(new Color(0, 140, 0));
                    appendOutput("Successfully connected to X.400 system");
                    if (markingPanel != null)
                        markingPanel.setUserAddress(userAddr);
                });
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> {
                    lblConnectionStatus.setText("Status: Connection Failed");
                    lblConnectionStatus.setForeground(Color.RED);
                    appendOutput("Connection failed: " + t.getMessage());
                    if (t instanceof UnsatisfiedLinkError ||
                            (t.getMessage() != null && t.getMessage().contains("Native library"))) {
                        appendOutput("=== NATIVE LIBRARY ERROR ===\n" +
                                "The Isode X.400 native libraries are not properly installed.\n" +
                                "Required: pthreadvc2.dll, CJavaInterface.dll in lib/amd64/");
                    }
                    if (t instanceof X400APIException) {
                        appendOutput("Error code: " + ((X400APIException) t).getNativeErrorCode());
                    } else {
                        java.io.StringWriter sw = new java.io.StringWriter();
                        t.printStackTrace(new java.io.PrintWriter(sw));
                        appendOutput(sw.toString());
                    }
                });
            }
        }).start();
    }

    private void disconnect() {
        messageService.disconnect();
        lblConnectionStatus.setText("Status: Disconnected");
        lblConnectionStatus.setForeground(Color.RED);
        appendOutput("Disconnected from X.400 system");
    }

    // ── Send / Receive ────────────────────────────────────────────────────

    private void sendMessage() {
        if (!messageService.isConnected()) {
            appendOutput("Error: Not connected. Please connect first.");
            return;
        }

        String recipient = txtRecipient.getText().trim();
        String subject = txtSubject.getText().trim();
        String content = txtContent.getText().trim();
        X400_Priority priority = (X400_Priority) comboPriority.getSelectedItem();

        if (recipient.isEmpty() || subject.isEmpty() || content.isEmpty()) {
            appendOutput("Error: Recipient, subject, and content are required.");
            return;
        }

        // ── Snapshot subcase and its defaults HERE on the EDT ─────────────────
        String payloadSize = txtPayloadSize.getText().trim();
        if (!payloadSize.isEmpty()) {
            try {
                if (Integer.parseInt(payloadSize) <= 0) {
                    appendOutput("Error: Payload Size must be a positive integer.");
                    return;
                }
            } catch (NumberFormatException e) {
                appendOutput("Error: Payload Size must be a positive integer.");
                return;
            }
        }

        TestSubcase currentSubcase = getSelectedSubcase();
        Map<String, String> defaults = currentSubcase != null
                ? new java.util.HashMap<>(currentSubcase.getAmhsDefaults())
                : new java.util.HashMap<>();

        // UI values are one-time overrides. The selected TestSubcase remains unchanged.
        // Remove aliases that otherwise take precedence over editable counterparts.
        defaults.remove("body-part-types");
        defaults.remove("charset-reg-numbers");
        defaults.remove("probe");
        defaults.remove("rn");
        defaults.putAll(collectEditableAmhsFields(recipient, subject, content, priority));

        // A manual send always uses the current filing time. Removing both keys
        // delegates generation to AMHSPayloadGeneratorService while leaving the
        // original TestSubcase map untouched. Default and batch sends do not use
        // this path, so negative filing-time test cases retain their definitions.
        defaults.remove("filing-time");
        defaults.remove("header-empty");

        // Create an effectively final copy for use in lambda
        final Map<String, String> amhsDefaults = defaults;
        final String sender = txtUserOrAddress.getText().trim();

        if (SettingsDialog.isClearOldLogsOnSend() && markingPanel != null) {
            markingPanel.clearSentMessages();
        }
        appendOutput("Sending message…");

        new Thread(() -> {
            try {
                // Use the snapshotted defaults — no more getSelectedSubcase() in the thread
                String msgId = messageService.sendMessage(recipient, subject, content, priority, amhsDefaults);
                SwingUtilities.invokeLater(() -> {
                    appendOutput("Message sent! ID: " + msgId);
                    String filingTime = messageService.getLastSentFilingTime();
                    if (filingTime != null && !filingTime.isEmpty()) {
                        appendOutput("Filing-time used: " + filingTime);
                    }
                    addMessageToMarkingPanel(sender, recipient, subject, content,
                            priority.toString(), true, null, false, amhsDefaults, null, null);
                });
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> {
                    appendOutput("Failed to send message: " + t.getMessage());
                    addMessageToMarkingPanel(sender, recipient, subject, content,
                            priority.toString(), false, t.getMessage(), false, amhsDefaults, null, null);
                });
            }
        }).start();
    }

    private void receiveMessages() {
        if (!messageService.isConnected()) {
            appendOutput("Error: Not connected. Please connect first.");
            return;
        }
        appendOutput("Receiving messages…");

        new Thread(() -> {
            try {
                List<AMHSMessageService.MessageSummary> all = messageService.getMailboxSummary();
                if (all.isEmpty()) {
                    SwingUtilities.invokeLater(() -> appendOutput("No messages found."));
                    return;
                }
                List<AMHSMessageService.MessageSummary> msgs = messageService.receiveMessages(all.size());
                SwingUtilities.invokeLater(() -> {
                    appendOutput("Received " + msgs.size() + " message(s):");
                    for (AMHSMessageService.MessageSummary m : msgs) {
                        appendOutput("From: " + m.getSender() + "  Subject: " + m.getSubject()
                                + (m.getReportType() != null ? "  [" + m.getReportType() + " Report]" : ""));
                        addMessageToMarkingPanel(m.getSender(), txtUserOrAddress.getText().trim(), m.getSubject(),
                                m.getContent(), null, true, null, true, null,
                                m.getReportType(), m.getReportDetails());
                        // Logging to DescriptionPanel has been removed per user request.
                    }
                });
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> appendOutput(
                        "Failed to receive messages: " + t.getMessage()));
            }
        }).start();
    }

    private void getMailboxSummary() {
        if (!messageService.isConnected()) {
            appendOutput("Error: Not connected. Please connect first.");
            return;
        }
        appendOutput("Getting mailbox summary…");

        new Thread(() -> {
            try {
                List<AMHSMessageService.MessageSummary> msgs = messageService.getMailboxSummary();
                SwingUtilities.invokeLater(() -> {
                    if (msgs.isEmpty()) {
                        appendOutput("Mailbox is empty.");
                    } else {
                        appendOutput("Mailbox contains " + msgs.size() + " message(s):");
                        for (AMHSMessageService.MessageSummary m : msgs) {
                            appendOutput("  From: " + m.getSender() +
                                    "  Subject: " + m.getSubject() +
                                    "  Time: " + m.getSubmissionTime() +
                                    "  Size: " + m.getContentLength() + " bytes");
                        }
                    }
                });
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> appendOutput("Failed to get mailbox summary: " + t.getMessage()));
            }
        }).start();
    }

    // ── Defaults handling ─────────────────────────────────────────────────

    private void loadBootDefaults() {
        txtRecipient.setText("/CN=VVTSOPTC/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        txtSubject.setText("Test X.400 Message");
        txtContent.setText("This is a test message sent via AMHS X.400.");
        comboPriority.setSelectedItem(X400_Priority.NORMAL_PRIORITY);
        txtATSPriority.setText("FF");
        txtOHI.setText("");
        comboMsgType.setSelectedItem("IPM");
        comboDrReq.setSelectedItem("NDR");
        comboRnReq.setSelectedItem("None");
        comboMtaReportReq.setSelectedItem("No Report");
        comboEncoding.setSelectedItem("IA5");
        comboCharset.setSelectedItem("6");
        comboContentType.setSelectedItem("22");
        txtATSHeader.setText("");
        comboBodyPartType.setSelectedItem("ia5-text");
        txtPayloadSize.setText("");
        txtFTBPFileName.setText("attachment.bin");
        txtFTBPContent.setText("");
    }

    private void handleLoadDefaultsForSubcase() {
        TestSubcase sc = getSelectedSubcase();
        if (sc == null) {
            loadBootDefaults();
            appendOutput("Loaded default configuration (boot baseline).");
            return;
        }
        if (sc.getAmhsDefaults().isEmpty()) {
            appendOutput("No default configuration available for selected subcase");
            return;
        }
        applyDefaults(sc.getAmhsDefaults());
        appendOutput("Loaded default configuration for subcase: " + sc.getId());
    }

    private void handleSendDefaults() {
        if (!messageService.isConnected()) {
            appendOutput("Error: Not connected. Please connect first.");
            return;
        }

        TestSubcase currentSubcase = getSelectedSubcase();
        if (currentSubcase == null || currentSubcase.getAmhsDefaults().isEmpty()) {
            appendOutput("No configured subcase selected.");
            return;
        }

        // Restore the form for visibility, but send an independent copy of the
        // original definition so manual edits never alter "Send defaults".
        Map<String, String> defaults = new java.util.HashMap<>(currentSubcase.getAmhsDefaults());
        applyDefaults(defaults);

        final String recipient = firstNonEmptyValue(defaults, "primary-recipients", "recipient");
        final String subject = valueOrDefault(defaults.get("subject"), "Test - " + currentSubcase.getId());
        final String content = valueOrDefault(defaults.get("content"), "Message for " + currentSubcase.getId());
        final X400_Priority priority = toX400Priority(firstNonEmptyValue(defaults,
                "priority", "ats-priority", "priority-indicator"));

        if (recipient == null || recipient.isEmpty() || subject.isEmpty() || content.isEmpty()) {
            appendOutput("Error: Recipient, subject, and content are required.");
            return;
        }

        if (SettingsDialog.isClearOldLogsOnSend() && markingPanel != null) {
            markingPanel.clearSentMessages();
        }

        new Thread(() -> {
            try {
                String msgId = messageService.sendMessage(recipient, subject, content, priority, defaults);
                SwingUtilities.invokeLater(() -> {
                    appendOutput("Defaults sent successfully. Message ID: " + msgId);
                    logSend(recipient, subject, content, priority, true, defaults);
                });
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> appendOutput("Send defaults failed: " + t.getMessage()));
            }
        }).start();
    }

    private void handleSendAllSubcases() {
        if (!messageService.isConnected()) {
            appendOutput("Error: Not connected. Please connect first.");
            return;
        }

        TestCase tc = getSelectedCase();
        if (tc == null || tc.getSubcases() == null || tc.getSubcases().isEmpty()) {
            appendOutput("No test case selected or no subcases defined.");
            return;
        }

        appendOutput("Sending messages for all " + tc.getSubcases().size() + " subcases of " + tc.getId() + "...");

        if (SettingsDialog.isClearOldLogsOnSend() && markingPanel != null) {
            markingPanel.clearSentMessages();
        }

        final String fallbackRecipient = txtRecipient.getText().trim();

        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            String lastSentIpmId = null;

            for (TestSubcase sc : tc.getSubcases()) {
                if (sc.getAmhsDefaults().isEmpty()) {
                    SwingUtilities.invokeLater(() -> appendOutput("Skipping " + sc.getId() + ": No AMHS defaults configured."));
                    failCount++;
                    continue;
                }

                try {
                    Map<String, String> defaults = new java.util.HashMap<>(sc.getAmhsDefaults());
                    String resolvedRecipient = firstNonEmptyValue(defaults, "primary-recipients", "recipient");
                    if (resolvedRecipient == null || resolvedRecipient.isEmpty()) {
                        resolvedRecipient = fallbackRecipient;
                    }
                    final String recipient = resolvedRecipient;
                    String subject = defaults.getOrDefault("subject", "Test - " + sc.getId());
                    String content = defaults.getOrDefault("content", "Message for " + sc.getId());

                    // Get priority from defaults or use current selection
                    X400_Priority priority = X400_Priority.NORMAL_PRIORITY;
                    String prioStr = defaults.get("priority");
                    if (prioStr != null) {
                        if ("KK".equalsIgnoreCase(prioStr) || "GG".equalsIgnoreCase(prioStr)) {
                            priority = X400_Priority.HIGH_PRIORITY;
                        } else if ("FF".equalsIgnoreCase(prioStr) || "NORMAL".equalsIgnoreCase(prioStr)) {
                            priority = X400_Priority.NORMAL_PRIORITY;
                        } else if ("DD".equalsIgnoreCase(prioStr) || "LOW".equalsIgnoreCase(prioStr)) {
                            priority = X400_Priority.LOW_PRIORITY;
                        } else if ("SS".equalsIgnoreCase(prioStr)) {
                            priority = X400_Priority.HIGH_PRIORITY;
                        }
                    }

                    // Auto-apply last sent IPM ID for RNs if not properly configured
                    if ("rn".equalsIgnoreCase(defaults.get("message-type"))) {
                        String currentId = defaults.get("subject-ipm-id");
                        if ((currentId == null || currentId.trim().isEmpty() || currentId.contains("PREVIOUSLY-SENT")) && lastSentIpmId != null) {
                            defaults.put("subject-ipm-id", lastSentIpmId);
                            final String appliedId = lastSentIpmId;
                            SwingUtilities.invokeLater(() -> appendOutput("Auto-applied subject-ipm-id: " + appliedId + " for RN " + sc.getId()));
                        }
                    }

                    String msgId = messageService.sendMessage(recipient, subject, content, priority, defaults);
                    if (msgId != null && !msgId.trim().isEmpty()) {
                        lastSentIpmId = msgId;
                    }
                    X400_Priority finalPriority = priority;
                    
                    SwingUtilities.invokeLater(() -> {
                        appendOutput("Sent " + sc.getId() + " - Message ID: " + msgId);
                        // Log the sent message
                        logSend(recipient, subject, content, finalPriority, true, defaults);
                    });
                    successCount++;
                } catch (Throwable t) {
                    SwingUtilities.invokeLater(() -> appendOutput("Failed to send " + sc.getId() + ": " + t.getMessage()));
                    failCount++;
                }
            }

            int finalSuccessCount = successCount;
            int finalFailCount = failCount;
            SwingUtilities.invokeLater(() -> appendOutput("Completed: " + finalSuccessCount + " succeeded, " + finalFailCount + " failed."));
        }).start();
    }

    /** Collects the editable form as per-send overrides without mutating a subcase. */
    private Map<String, String> collectEditableAmhsFields(String recipient, String subject,
            String content, X400_Priority priority) {
        Map<String, String> fields = new java.util.HashMap<>();
        fields.put("recipient", recipient);
        fields.put("primary-recipients", recipient);
        fields.put("subject", subject);
        fields.put("content", content);
        fields.put("priority", priority != null ? priority.toString() : "NORMAL_PRIORITY");
        fields.put("ats-priority", txtATSPriority.getText().trim());
        fields.put("optional-heading-info", txtOHI.getText().trim());
        fields.put("ats-header", txtATSHeader.getText().trim());
        fields.put("encoding", valueOrDefault((String) comboEncoding.getSelectedItem(), ""));
        fields.put("charset-reg-number", valueOrDefault((String) comboCharset.getSelectedItem(), ""));
        fields.put("content-type", valueOrDefault((String) comboContentType.getSelectedItem(), ""));
        fields.put("body-part-type", valueOrDefault((String) comboBodyPartType.getSelectedItem(), "ia5-text"));
        fields.put("payload-size", txtPayloadSize.getText().trim());
        fields.put("ftbp-file-name", txtFTBPFileName.getText().trim());
        fields.put("ftbp-content", txtFTBPContent.getText());
        fields.put("originator-report-request", valueOrDefault((String) comboDrReq.getSelectedItem(), "None"));
        fields.put("originating-mta-report-request",
                toMtaReportValue((String) comboMtaReportReq.getSelectedItem()));
        fields.put("receipt-notification", valueOrDefault((String) comboRnReq.getSelectedItem(), "None"));

        String messageType = valueOrDefault((String) comboMsgType.getSelectedItem(), "IPM").toLowerCase();
        fields.put("message-type", messageType);
        if ("probe".equals(messageType)) {
            fields.put("probe", "probe");
        } else if ("rn".equals(messageType)) {
            fields.put("rn", "rn");
        }
        return fields;
    }

    private String firstNonEmptyValue(Map<String, String> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    private X400_Priority toX400Priority(String value) {
        if (value == null) {
            return X400_Priority.NORMAL_PRIORITY;
        }
        String normalized = value.trim().toUpperCase();
        if ("KK".equals(normalized) || "GG".equals(normalized) || "SS".equals(normalized)
                || "HIGH".equals(normalized) || "HIGH_PRIORITY".equals(normalized)
                || "URGENT".equals(normalized)) {
            return X400_Priority.HIGH_PRIORITY;
        }
        if ("DD".equals(normalized) || "LOW".equals(normalized) || "LOW_PRIORITY".equals(normalized)) {
            return X400_Priority.LOW_PRIORITY;
        }
        return X400_Priority.NORMAL_PRIORITY;
    }

    private String toDrSelection(String value) {
        if (value == null) {
            return "NDR";
        }
        String normalized = value.trim().toLowerCase();
        if ("none".equals(normalized) || "no-report".equals(normalized) || "0".equals(normalized)) {
            return "None";
        }
        if ("both".equals(normalized) || "3".equals(normalized)) {
            return "Both";
        }
        if ("report".equals(normalized) || "delivery-report".equals(normalized)
                || "audited-report".equals(normalized) || "dr".equals(normalized)
                || "2".equals(normalized)) {
            return "DR";
        }
        return "NDR";
    }

    private String toRnSelection(String value) {
        if (value == null) {
            return "None";
        }
        String normalized = value.trim().toLowerCase();
        if ("both".equals(normalized) || "3".equals(normalized)) {
            return "Both";
        }
        if ("receipt-notification".equals(normalized) || "rn".equals(normalized)
                || "1".equals(normalized)) {
            return "RN";
        }
        if ("non-receipt-notification".equals(normalized) || "nrn".equals(normalized)
                || "2".equals(normalized)) {
            return "NRN";
        }
        return "None";
    }

    private String toMtaReportValue(String selection) {
        if (selection == null) {
            return "no-report";
        }
        String normalized = selection.trim().toLowerCase();
        if ("non-delivery report".equals(normalized) || "non-delivery-report".equals(normalized)
                || "ndr".equals(normalized) || "1".equals(normalized)) {
            return "non-delivery-report";
        }
        if ("audited report".equals(normalized) || "audited-report".equals(normalized)
                || "3".equals(normalized)) {
            return "audited-report";
        }
        if ("report".equals(normalized) || "delivery report".equals(normalized)
                || "delivery-report".equals(normalized) || "dr".equals(normalized)
                || "2".equals(normalized)) {
            return "report";
        }
        return "no-report";
    }

    private String toMtaSelection(String value) {
        String canonical = toMtaReportValue(value);
        if ("non-delivery-report".equals(canonical)) {
            return "Non-Delivery Report";
        }
        if ("audited-report".equals(canonical)) {
            return "Audited Report";
        }
        if ("report".equals(canonical)) {
            return "Report";
        }
        return "No Report";
    }

    private void applyDefaults(Map<String, String> defaults) {
        String recipient = firstNonEmptyValue(defaults, "primary-recipients", "recipient");
        txtRecipient.setText(recipient != null ? recipient : "");
        txtSubject.setText(valueOrDefault(defaults.get("subject"), ""));
        txtContent.setText(valueOrDefault(defaults.get("content"), ""));

        String atsPriority = firstNonEmptyValue(defaults,
                "ats-priority", "priority-indicator", "subject-message-priority", "priority");
        txtATSPriority.setText(atsPriority != null ? atsPriority : "");
        comboPriority.setSelectedItem(toX400Priority(firstNonEmptyValue(defaults,
                "priority", "ats-priority", "priority-indicator")));

        String messageType = firstNonEmptyValue(defaults, "message-type");
        if ("probe".equalsIgnoreCase(messageType) || defaults.containsKey("probe")) {
            comboMsgType.setSelectedItem("Probe");
        } else if ("rn".equalsIgnoreCase(messageType) || defaults.containsKey("rn")) {
            comboMsgType.setSelectedItem("RN");
        } else {
            comboMsgType.setSelectedItem("IPM");
        }

        txtOHI.setText(valueOrDefault(defaults.get("optional-heading-info"), ""));
        txtATSHeader.setText(valueOrDefault(defaults.get("ats-header"), ""));
        comboDrReq.setSelectedItem(toDrSelection(defaults.get("originator-report-request")));
        comboRnReq.setSelectedItem(toRnSelection(defaults.get("receipt-notification")));
        comboMtaReportReq.setSelectedItem(toMtaSelection(defaults.get("originating-mta-report-request")));

        String bodyPartType = firstNonEmptyValue(defaults, "body-part-types", "body-part-type");
        comboBodyPartType.setSelectedItem(bodyPartType != null ? bodyPartType : "ia5-text");

        String encoding = firstNonEmptyValue(defaults, "encoding");
        if (encoding == null) {
            encoding = bodyPartType != null && bodyPartType.toLowerCase().contains("general-text")
                    ? "General Text" : "IA5";
        }
        comboEncoding.setSelectedItem(encoding);

        String charset = firstNonEmptyValue(defaults, "charset-reg-number", "charset-reg-numbers");
        comboCharset.setSelectedItem(charset != null ? charset : "6");
        comboContentType.setSelectedItem(valueOrDefault(defaults.get("content-type"), "22"));
        txtPayloadSize.setText(valueOrDefault(defaults.get("payload-size"), ""));
        txtFTBPFileName.setText(valueOrDefault(defaults.get("ftbp-file-name"), "attachment.bin"));
        txtFTBPContent.setText(valueOrDefault(defaults.get("ftbp-content"), ""));
    }

    // ── Utility helpers ───────────────────────────────────────────────────

    private String generateDetailedPayload(String sender, String recipient, String subject, String priority,
            boolean isReceived, Map<String, String> defaults) {
        StringBuilder sb = new StringBuilder();
        sb.append("X.400 Message Attributes:\n");
        sb.append("- Sender (O/R Address): ").append(sender != null ? sender : "").append("\n");
        sb.append("- Recipient (O/R Address): ").append(recipient != null ? recipient : "").append("\n");
        sb.append("- Subject: ").append(subject != null ? subject : "").append("\n");
        sb.append("- Priority: ").append(priority != null ? priority : "NORMAL_PRIORITY").append("\n");

        if (defaults != null && !defaults.isEmpty()) {
            // List all AMHS specific fields from defaults
            defaults.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    // Avoid duplicating basic fields
                    if (!key.equals("recipient") && !key.equals("subject") && !key.equals("priority")
                            && !key.equals("content")) {
                        // Format key for readability (e.g., "filing-time" -> "Filing Time")
                        String formattedKey = key.substring(0, 1).toUpperCase() + key.substring(1).replace("-", " ");
                        sb.append("- ").append(formattedKey).append(": ").append(value).append("\n");
                    }
                }
            });
        }

        if (!isReceived) {
            sb.append("- Delivery Report Request: DR_NON_DELIVERY_REPORT\n");
            sb.append("- IPN Request: IPN_NON_RECEIPT_NOTIFICATION\n");
        }

        return sb.toString();
    }

    private void addMessageToMarkingPanel(String sender, String recipient, String subject,
            String content, String priority, boolean success,
            String errorMessage, boolean isReceived, Map<String, String> defaults,
            String reportType, String reportDetails) {
        if (markingPanel == null)
            return;
        MessageLog log = new MessageLog(
                getSelectedCase() != null ? getSelectedCase().getId() : "N/A",
                getSelectedSubcase() != null ? getSelectedSubcase().getId() : "N/A");
        log.setSender(sender);
        log.setRecipient(recipient);
        log.setSubject(subject);
        log.setContent(content);
        if (priority != null)
            log.setPriority(priority);
        log.setSuccess(success);
        if (errorMessage != null)
            log.setErrorMessage(errorMessage);
        log.setIsReceived(isReceived);

        // Set report type/details for received DR/NDR/IPN messages
        if (reportType != null && !reportType.isEmpty()) {
            log.setReportType(reportType);
        }
        if (reportDetails != null && !reportDetails.isEmpty()) {
            log.setReportDetails(reportDetails);
        }

        // Set DR request type from defaults if sending a message
        if (!isReceived && defaults != null) {
            String drRequest = defaults.get("originator-report-request");
            if (drRequest != null && !drRequest.trim().isEmpty()) {
                String lower = drRequest.toLowerCase().trim();
                if (lower.equals("report") || lower.equals("delivery-report") || lower.equals("3")) {
                    log.setDrRequestType("DR_DELIVERY_REPORT");
                } else if (lower.equals("none") || lower.equals("0")) {
                    log.setDrRequestType("DR_NO_REPORT");
                } else if (lower.equals("non-delivery-report") || lower.equals("2") || lower.equals("1")) {
                    log.setDrRequestType("DR_NON_DELIVERY_REPORT");
                }
            }
        }

        String x400Payload = generateDetailedPayload(sender, recipient, subject, priority, isReceived, defaults);

        log.setX400Payload(x400Payload);

        markingPanel.addMessage(log, isReceived);

        if (recorder != null && recorder.getTestSession() != null) {
            recorder.getTestSession().addMessageLog(log);
        }
    }

    private void logSend(String recipient, String subject, String content,
            X400_Priority priority, boolean success, Map<String, String> defaults) {
        // Logging to DescriptionPanel has been removed per user request.
        // But we still log to the sent messages list (marking panel) for successful sends
        if (!success) {
            return; // Don't log failed sends
        }
        String sender = txtUserOrAddress != null ? txtUserOrAddress.getText().trim() : "";
        addMessageToMarkingPanel(sender, recipient, subject, content, 
                priority != null ? priority.toString() : "NORMAL_PRIORITY", 
                success, // success
                null, // errorMessage
                false, // isReceived (we sent it)
                defaults,
                null, // reportType
                null); // reportDetails
    }

    private void appendOutput(String text) {
        logger.info(text.trim());
    }

    private void browseAndLoadFile(javax.swing.text.JTextComponent targetComponent, boolean removeNewlines) {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath()), "UTF-8");
                if (removeNewlines) {
                    content = content.replace("\r", "").replace("\n", "");
                }
                targetComponent.setText(content);
                appendOutput("Loaded file: " + fc.getSelectedFile().getName());
            } catch (Exception ex) {
                appendOutput("Failed to load file: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Failed to load file: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Config file I/O ───────────────────────────────────────────────────

    private static final String CONFIG_FILE = "connection.properties";

    private void loadConfigFromFile() {
        java.io.File file = new java.io.File(CONFIG_FILE);
        if (!file.exists())
            return;
        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            props.load(fis);
            txtPresentationAddress.setText(props.getProperty("presentationAddress", ""));
            txtUserOrAddress.setText(props.getProperty("userOrAddress", ""));
            txtPassword.setText(props.getProperty("password", ""));
            txtRecipient.setText(props.getProperty("recipient", ""));
            txtSubject.setText(props.getProperty("subject", ""));
            if ("P3".equalsIgnoreCase(props.getProperty("connectionType", "P7"))) {
                radioP3.setSelected(true);
            } else {
                radioP7.setSelected(true);
            }
            appendOutput("Loaded configuration from " + CONFIG_FILE);
        } catch (java.io.IOException e) {
            appendOutput("Failed to load configuration: " + e.getMessage());
        }
    }

    private void saveConfigToFile() {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("presentationAddress", txtPresentationAddress.getText().trim());
        props.setProperty("userOrAddress", txtUserOrAddress.getText().trim());
        props.setProperty("password", new String(txtPassword.getPassword()));
        props.setProperty("recipient", txtRecipient.getText().trim());
        props.setProperty("subject", txtSubject.getText().trim());
        props.setProperty("connectionType", radioP3.isSelected() ? "P3" : "P7");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "AMHS UA Test Tool Connection Settings");
            appendOutput("Saved configuration to " + CONFIG_FILE);
        } catch (java.io.IOException e) {
            appendOutput("Failed to save configuration: " + e.getMessage());
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────

    public static void main(String[] args) {
        // --- Start Plug-and-Play native library fix ---
        try {
            java.io.File jarFile = new java.io.File(AMHSMessageUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            java.io.File rootDir = jarFile.getParentFile();
            java.io.File libDir = new java.io.File(rootDir, "lib");
            
            if (libDir.exists() && libDir.isDirectory()) {
                String libAbsPath = libDir.getAbsolutePath();
                
                // Set isode.bindir
                if (System.getProperty("isode.bindir") == null) {
                    System.setProperty("isode.bindir", libAbsPath);
                }
                
                // Set java.library.path and force ClassLoader to reload sys_paths
                String currentPath = System.getProperty("java.library.path");
                if (currentPath == null || !currentPath.contains(libAbsPath)) {
                    String newPath = currentPath == null ? libAbsPath : libAbsPath + java.io.File.pathSeparator + currentPath;
                    System.setProperty("java.library.path", newPath);
                    
                    try {
                        java.lang.reflect.Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
                        fieldSysPath.setAccessible(true);
                        fieldSysPath.set(null, null);
                    } catch (Exception ignore) {
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // --- End Plug-and-Play native library fix ---

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            AMHSMessageUI ui = new AMHSMessageUI();
            ui.setVisible(true);
        });
    }
}
