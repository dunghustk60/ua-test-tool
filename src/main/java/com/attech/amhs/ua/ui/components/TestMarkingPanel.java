package com.attech.amhs.ua.ui.components;

import com.attech.amhs.ua.model.MessageLog;
import com.attech.amhs.ua.repository.TestCaseRepository;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * UI Panel for displaying all sent, received, and DR/NDR/IPN report messages
 * connected to the User/O/R Address.
 *
 * Layout (three-way vertical split):
 *   ┌──────────────────────────────┐
 *   │  DR/NDR/IPN Reports (top)   │
 *   ├──────────────────────────────┤
 *   │  Received Messages (middle) │
 *   ├──────────────────────────────┤
 *   │  Sent Messages (bottom)     │
 *   └──────────────────────────────┘
 */
public class TestMarkingPanel extends JPanel {

    private TestCaseRepository repository;
    private List<MessageLog> allMessages;
    private String currentUserAddress;
    private SimpleDateFormat dateFormat;
    private int batchUpdateDepth;

    // Reports panel (top)
    private JTextArea txtReports;
    private JScrollPane scrollReportsPane;

    // Received messages panel (middle)
    private JTextArea txtReceivedMessages;
    private JScrollPane scrollReceivedPane;

    // Sent messages panel (bottom)
    private JTextArea txtSentMessages;
    private JScrollPane scrollSentPane;

    // Split panes
    private JSplitPane splitPaneUpper;   // Received | Sent
    private JSplitPane splitPaneMain;    // Reports  | (Received | Sent)

    public TestMarkingPanel(TestCaseRepository repository) {
        this.repository = repository;
        this.allMessages = new ArrayList<>();
        this.currentUserAddress = "";
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Messages – User/O/R Address Filtered"));

        // Build the three sub-panels
        JPanel reportsPanel   = createReportsPanel();
        JPanel receivedPanel  = createReceivedMessagesPanel();
        JPanel sentPanel      = createSentMessagesPanel();

        // Received | Sent (upper split)
        splitPaneUpper = new JSplitPane(JSplitPane.VERTICAL_SPLIT, receivedPanel, sentPanel);
        splitPaneUpper.setDividerLocation(0.5);
        splitPaneUpper.setResizeWeight(0.5);
        splitPaneUpper.setContinuousLayout(true);

        // Reports on top, Received|Sent below (main split)
        splitPaneMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, reportsPanel, splitPaneUpper);
        splitPaneMain.setDividerLocation(0.30);
        splitPaneMain.setResizeWeight(0.30);
        splitPaneMain.setContinuousLayout(true);

        add(splitPaneMain, BorderLayout.CENTER);

        setPreferredSize(new Dimension(400, 400));
    }

    // ── Panel factories ───────────────────────────────────────────────────

    private JPanel createReportsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("DR/NDR/IPN Reports"));

        txtReports = new JTextArea();
        txtReports.setEditable(false);
        txtReports.setFont(new Font("Monospaced", Font.PLAIN, 10));
        txtReports.setLineWrap(true);
        txtReports.setWrapStyleWord(true);
        txtReports.setBackground(new Color(255, 253, 240)); // Warm tint to distinguish

        scrollReportsPane = new JScrollPane(txtReports);
        scrollReportsPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollReportsPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        panel.add(scrollReportsPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createReceivedMessagesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Received Messages"));

        txtReceivedMessages = new JTextArea();
        txtReceivedMessages.setEditable(false);
        txtReceivedMessages.setFont(new Font("Monospaced", Font.PLAIN, 10));
        txtReceivedMessages.setLineWrap(true);
        txtReceivedMessages.setWrapStyleWord(true);

        scrollReceivedPane = new JScrollPane(txtReceivedMessages);
        scrollReceivedPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollReceivedPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        panel.add(scrollReceivedPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSentMessagesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Sent Messages"));

        txtSentMessages = new JTextArea();
        txtSentMessages.setEditable(false);
        txtSentMessages.setFont(new Font("Monospaced", Font.PLAIN, 10));
        txtSentMessages.setLineWrap(true);
        txtSentMessages.setWrapStyleWord(true);

        scrollSentPane = new JScrollPane(txtSentMessages);
        scrollSentPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollSentPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        panel.add(scrollSentPane, BorderLayout.CENTER);
        return panel;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Add a message to the display.
     *
     * @param message    The message log entry
     * @param isReceived true if received message, false if sent
     */
    public void addMessage(MessageLog message, boolean isReceived) {
        message.setIsReceived(isReceived);
        allMessages.add(message);
        if (batchUpdateDepth == 0) {
            refreshDisplay();
        }
    }

    /** Defer display rebuilding while multiple messages are added. */
    public void beginBatchUpdate() {
        batchUpdateDepth++;
    }

    /** Complete a batch and rebuild all message displays once. */
    public void endBatchUpdate() {
        if (batchUpdateDepth > 0) {
            batchUpdateDepth--;
        }
        if (batchUpdateDepth == 0) {
            refreshDisplay();
        }
    }

    /**
     * Set the current User/O/R Address filter used to scope message display.
     */
    public void setUserAddress(String userAddress) {
        this.currentUserAddress = userAddress != null ? userAddress.trim() : "";
        refreshDisplay();
    }

    /**
     * Clear all messages from all panels.
     */
    public void clearMessages() {
        allMessages.clear();
        txtReports.setText("");
        txtReceivedMessages.setText("");
        txtSentMessages.setText("");
    }

    /**
     * Clear all sent messages from the panels.
     */
    public void clearSentMessages() {
        allMessages.removeIf(m -> !m.isReceived());
        refreshDisplay();
    }

    /**
     * Return a snapshot of all stored messages.
     */
    public List<MessageLog> getAllMessages() {
        return new ArrayList<>(allMessages);
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    /**
     * Rebuild the three text areas from the stored message list.
     * Applies address filter and message-limit settings.
     */
    private void refreshDisplay() {
        StringBuilder reportsBuilder  = new StringBuilder();
        StringBuilder receivedBuilder = new StringBuilder();
        StringBuilder sentBuilder     = new StringBuilder();

        int receivedCount = 0;
        int sentCount     = 0;
        int reportsCount  = 0;
        boolean limitActive = SettingsDialog.isLimitMessages();
        int limit           = SettingsDialog.getMessageLimit();

        // Iterate newest-first so the most recent messages appear at the top
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            MessageLog message = allMessages.get(i);

            boolean isMatching = currentUserAddress.isEmpty()
                    || messageContainsAddress(message, currentUserAddress);

            if (!isMatching) {
                continue;
            }

            // ── Report messages ──────────────────────────────────────────
            if (message.getReportType() != null && !message.getReportType().isEmpty()) {
                if (!limitActive || reportsCount < limit) {
                    reportsBuilder.append(formatReport(message)).append("\n---\n");
                    reportsCount++;
                }
            }
            // ── Regular received / sent messages ─────────────────────────
            else if (message.isReceived()) {
                if (!limitActive || receivedCount < limit) {
                    receivedBuilder.append(formatMessage(message)).append("\n---\n");
                    receivedCount++;
                }
            } else {
                if (!limitActive || sentCount < limit) {
                    sentBuilder.append(formatSentMessage(message)).append("\n---\n");
                    sentCount++;
                }
            }

            if (limitActive
                    && reportsCount >= limit
                    && receivedCount >= limit
                    && sentCount >= limit) {
                break;
            }
        }

        txtReports.setText(reportsBuilder.toString());
        txtReceivedMessages.setText(receivedBuilder.toString());
        txtSentMessages.setText(sentBuilder.toString());

        // Auto-scroll to the top (newest messages shown first)
        SwingUtilities.invokeLater(() -> {
            if (SettingsDialog.isAutoScrollMessages()) {
                txtReports.setCaretPosition(0);
                txtReceivedMessages.setCaretPosition(0);
                txtSentMessages.setCaretPosition(0);
            }
        });
    }

    // ── Formatters ────────────────────────────────────────────────────────

    /**
     * Format a received (non-report) message for display.
     */
    private String formatMessage(MessageLog message) {
        StringBuilder sb = new StringBuilder();

        // Timestamp
        sb.append("[").append(dateFormat.format(new Date(message.getTimestamp()))).append("]\n");

        // Test Case / Subcase
        if (message.getTestCaseId() != null) {
            sb.append("Case: ").append(message.getTestCaseId());
            if (message.getTestSubcaseId() != null) {
                sb.append(" / ").append(message.getTestSubcaseId());
            }
            sb.append("\n");
        }

        // Full X.400 payload if available, otherwise basic fields
        if (message.getX400Payload() != null && !message.getX400Payload().isEmpty()) {
            sb.append(message.getX400Payload());
        } else {
            if (message.getSender() != null) {
                sb.append("From: ").append(abbreviateAddress(message.getSender())).append("\n");
            }
            if (message.getRecipient() != null) {
                sb.append("To: ").append(abbreviateAddress(message.getRecipient())).append("\n");
            }
            if (message.getSubject() != null) {
                sb.append("Subject: ").append(message.getSubject()).append("\n");
            }
            if (message.getPriority() != null) {
                sb.append("Priority: ").append(message.getPriority()).append("\n");
            }
        }

        // Content
        if (message.getContent() != null && !message.getContent().isEmpty()) {
            sb.append("Content:\n").append(message.getContent()).append("\n");
        }

        // Status
        sb.append("Status: ").append(message.isSuccess() ? "SUCCESS" : "FAILED");
        if (message.getErrorMessage() != null && !message.getErrorMessage().isEmpty()) {
            sb.append(" - ").append(message.getErrorMessage());
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Format a sent message for display, including DR request type badge.
     */
    private String formatSentMessage(MessageLog message) {
        StringBuilder sb = new StringBuilder();

        // Timestamp
        sb.append("[").append(dateFormat.format(new Date(message.getTimestamp()))).append("]\n");

        // Test Case / Subcase
        if (message.getTestCaseId() != null) {
            sb.append("Case: ").append(message.getTestCaseId());
            if (message.getTestSubcaseId() != null) {
                sb.append(" / ").append(message.getTestSubcaseId());
            }
            sb.append("\n");
        }

        // Full X.400 payload if available, otherwise basic fields
        if (message.getX400Payload() != null && !message.getX400Payload().isEmpty()) {
            sb.append(message.getX400Payload());
        } else {
            if (message.getSender() != null) {
                sb.append("From: ").append(abbreviateAddress(message.getSender())).append("\n");
            }
            if (message.getRecipient() != null) {
                sb.append("To: ").append(abbreviateAddress(message.getRecipient())).append("\n");
            }
            if (message.getSubject() != null) {
                sb.append("Subject: ").append(message.getSubject()).append("\n");
            }
            if (message.getPriority() != null) {
                sb.append("Priority: ").append(message.getPriority()).append("\n");
            }
        }

        // ── DR Request Type badge ─────────────────────────────────────────
        if (message.getDrRequestType() != null && !message.getDrRequestType().isEmpty()) {
            sb.append("DR Request: [").append(message.getDrRequestType()).append("]\n");
        }

        // ── Expected DR indicator ─────────────────────────────────────────
        // Check whether a matching report has been received for this message
        String drStatus = resolveExpectedDrStatus(message);
        if (drStatus != null) {
            sb.append("Expected DR: ").append(drStatus).append("\n");
        }

        // Content
        if (message.getContent() != null && !message.getContent().isEmpty()) {
            sb.append("Content:\n").append(message.getContent()).append("\n");
        }

        // Status
        sb.append("Status: ").append(message.isSuccess() ? "SUCCESS" : "FAILED");
        if (message.getErrorMessage() != null && !message.getErrorMessage().isEmpty()) {
            sb.append(" - ").append(message.getErrorMessage());
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Format a DR/NDR/IPN report message for display.
     */
    private String formatReport(MessageLog message) {
        StringBuilder sb = new StringBuilder();

        // Timestamp + report type header
        sb.append("[").append(dateFormat.format(new Date(message.getTimestamp()))).append("] ");
        sb.append("*** ").append(message.getReportType()).append(" REPORT ***\n");

        // Test Case / Subcase
        if (message.getTestCaseId() != null) {
            sb.append("Case: ").append(message.getTestCaseId());
            if (message.getTestSubcaseId() != null) {
                sb.append(" / ").append(message.getTestSubcaseId());
            }
            sb.append("\n");
        }

        // Sender / Recipient
        if (message.getSender() != null && !message.getSender().isEmpty()) {
            sb.append("From: ").append(abbreviateAddress(message.getSender())).append("\n");
        }
        if (message.getRecipient() != null) {
            sb.append("To: ").append(abbreviateAddress(message.getRecipient())).append("\n");
        }

        // Subject
        if (message.getSubject() != null) {
            sb.append("Subject: ").append(message.getSubject()).append("\n");
        }

        // Report details (delivery time, NDR reason, diagnostic code, etc.)
        if (message.getReportDetails() != null && !message.getReportDetails().isEmpty()) {
            sb.append("Details: ").append(message.getReportDetails()).append("\n");
        }

        // Original DR request type that was sent
        if (message.getDrRequestType() != null && !message.getDrRequestType().isEmpty()) {
            sb.append("Original DR Request: ").append(message.getDrRequestType()).append("\n");
        }

        // Check expected vs actual
        String expectedDr = findExpectedDrForCase(message.getTestCaseId(), message.getTestSubcaseId());
        if (expectedDr != null) {
            boolean matches = reportMatchesExpected(message.getReportType(), expectedDr);
            sb.append("Expected: ").append(expectedDr)
              .append("  →  ").append(matches ? "✓ MATCHED" : "✗ MISMATCH").append("\n");
        }

        return sb.toString();
    }

    // ── Helper utilities ──────────────────────────────────────────────────

    /**
     * Check whether an address filter matches any address in the message.
     */
    private boolean messageContainsAddress(MessageLog message, String address) {
        if (message.getRecipient() != null && message.getRecipient().contains(address)) {
            return true;
        }
        if (message.getSender() != null && message.getSender().contains(address)) {
            return true;
        }
        return false;
    }

    /**
     * Abbreviate a long O/R address for compact display.
     */
    private String abbreviateAddress(String address) {
        if (address == null || address.length() <= 80) {
            return address;
        }
        return address.substring(0, 77) + "...";
    }

    /**
     * For a sent message, look through the stored messages to find whether a
     * matching report has been received.  Returns a human-readable status string
     * if the sent message has a DR request type, or null otherwise.
     *
     * Matching is done on (testCaseId, testSubcaseId) — i.e. we assume one sent
     * message per subcase, which is the typical test-tool usage.
     */
    private String resolveExpectedDrStatus(MessageLog sentMessage) {
        if (sentMessage.getDrRequestType() == null || sentMessage.getDrRequestType().isEmpty()) {
            return null;
        }

        String caseId    = sentMessage.getTestCaseId();
        String subcaseId = sentMessage.getTestSubcaseId();

        // Look for any report message in the list that shares the same case/subcase
        for (MessageLog m : allMessages) {
            if (m == sentMessage) continue;
            if (m.getReportType() == null || m.getReportType().isEmpty()) continue;

            boolean sameCase = (caseId != null && caseId.equals(m.getTestCaseId()))
                    || (caseId == null && m.getTestCaseId() == null);
            boolean sameSubcase = (subcaseId != null && subcaseId.equals(m.getTestSubcaseId()))
                    || (subcaseId == null && m.getTestSubcaseId() == null);

            if (sameCase && sameSubcase) {
                return "Received " + m.getReportType() + " ✓";
            }
        }

        // No matching report found yet
        return "Awaiting " + sentMessage.getDrRequestType() + " …";
    }

    /**
     * Look up the expected-result DR setting from the repository for a given
     * test case / subcase combination.  Returns a string like "DR", "NDR",
     * "IPN", or null if no expectation is stored.
     */
    private String findExpectedDrForCase(String caseId, String subcaseId) {
        if (repository == null || caseId == null) return null;

        com.attech.amhs.ua.model.TestCase tc = repository.getTestCaseById(caseId);
        if (tc == null) return null;

        if (subcaseId != null && tc.getSubcases() != null) {
            for (com.attech.amhs.ua.model.TestSubcase sc : tc.getSubcases()) {
                if (subcaseId.equals(sc.getId())) {
                    String expectedResult = sc.getAmhsDefaults().get("expected-result");
                    return (expectedResult != null && !expectedResult.trim().isEmpty())
                            ? expectedResult.trim().toUpperCase()
                            : null;
                }
            }
        }
        return null;
    }

    /**
     * Return true when the received report type satisfies the expected result.
     */
    private boolean reportMatchesExpected(String reportType, String expectedDr) {
        if (reportType == null || expectedDr == null) return false;
        String rt = reportType.toUpperCase();
        String ex = expectedDr.toUpperCase();
        // "DR/NDR" covers both DR and NDR
        if (rt.contains("NDR") && (ex.equals("NDR") || ex.equals("DR/NDR"))) return true;
        if (rt.contains("DR") && !rt.contains("NDR") && (ex.equals("DR") || ex.equals("DR/NDR"))) return true;
        if (rt.equals("IPN") && ex.equals("IPN")) return true;
        return rt.equals(ex);
    }
}
