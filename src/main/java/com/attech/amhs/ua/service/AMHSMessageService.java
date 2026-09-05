/*
 * AMHS X.400 Message Service
 * Provides functionality for sending and receiving AMHS X.400 messages
 */
package com.attech.amhs.ua.service;

import com.isode.x400.highlevel.*;
import com.isode.x400api.X400_att;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service class for AMHS X.400 message operations
 * Provides methods for creating, sending, and receiving X.400 messages
 */
public class AMHSMessageService {
    
    private static final Logger logger = Logger.getLogger(AMHSMessageService.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    
    private boolean isConnected;
    private P3BindSession session;
    private AMHSPayloadGeneratorService payloadGenerator = new AMHSPayloadGeneratorService();
    private String lastSentFilingTime;
    private final List<PendingReceiptNotification> pendingReceiptNotifications = new ArrayList<>();
    private P7BindSession pendingReceiptNotificationSession;
    private final List<PendingInteractiveNotification> pendingInteractiveNotifications = new ArrayList<>();
    private long nextPendingInteractiveNotificationId = 1L;

    private enum ReceiveAction {
        AUTO,
        ACCEPT,
        STAGE_IPN,
        RETURN_NDR
    }

    private static final class PendingReceiptNotification {
        private final X400Msg message;
        private final String subjectMessageId;
        private final String subject;
        private final String subjectPriority;
        private final String receiptNotificationRecipient;

        private PendingReceiptNotification(X400Msg message, String subjectMessageId,
                String subject, String subjectPriority, String receiptNotificationRecipient) {
            this.message = message;
            this.subjectMessageId = subjectMessageId;
            this.subject = subject;
            this.subjectPriority = subjectPriority;
            this.receiptNotificationRecipient = receiptNotificationRecipient;
        }
    }

    private static final class PendingInteractiveNotification {
        private final long id;
        private final String originator;
        private final String subject;
        private final String textPreview;
        private final String ipmId;
        private final String messageIdentifier;
        private final int sequenceNumber;
        private final int notificationRequest;

        private PendingInteractiveNotification(long id, String originator, String subject,
                String textPreview, String ipmId, String messageIdentifier, int sequenceNumber,
                int notificationRequest) {
            this.id = id;
            this.originator = originator;
            this.subject = subject;
            this.textPreview = textPreview;
            this.ipmId = ipmId;
            this.messageIdentifier = messageIdentifier;
            this.sequenceNumber = sequenceNumber;
            this.notificationRequest = notificationRequest;
        }
    }

    /** Immutable GUI view of a received IPM for which RN/NRN can be sent. */
    public static final class PendingNotificationChoice {
        private final long id;
        private final String originator;
        private final String subject;
        private final String textPreview;
        private final String ipmId;
        private final int notificationRequest;

        private PendingNotificationChoice(PendingInteractiveNotification pending) {
            this.id = pending.id;
            this.originator = pending.originator;
            this.subject = pending.subject;
            this.textPreview = pending.textPreview;
            this.ipmId = pending.ipmId;
            this.notificationRequest = pending.notificationRequest;
        }

        public long getId() { return id; }
        public String getOriginator() { return originator; }
        public String getSubject() { return subject; }
        public String getTextPreview() { return textPreview; }
        public String getIpmId() { return ipmId; }
        public boolean canSendRn() {
            return (notificationRequest & X400Msg.IPN_RECEIPT_NOTIFICATION) != 0;
        }
        public boolean canSendNrn() {
            return (notificationRequest & X400Msg.IPN_NON_RECEIPT_NOTIFICATION) != 0;
        }
        public String getAllowedResponses() {
            if (canSendRn() && canSendNrn()) return "RN / NRN";
            if (canSendRn()) return "RN";
            if (canSendNrn()) return "NRN";
            return "None";
        }
    }

    /** Immutable GUI view of a staged CTSW014 subject message. */
    public static final class ReceiptNotificationSubject {
        private final String messageId;
        private final String priority;
        private final String subject;
        private final String recipient;

        private ReceiptNotificationSubject(String messageId, String priority,
                String subject, String recipient) {
            this.messageId = messageId;
            this.priority = priority;
            this.subject = subject;
            this.recipient = recipient;
        }

        public String getMessageId() { return messageId; }
        public String getPriority() { return priority; }
        public String getSubject() { return subject; }
        public String getRecipient() { return recipient; }

        @Override
        public String toString() {
            String compactId = messageId != null && messageId.length() > 42
                    ? messageId.substring(0, 39) + "..." : messageId;
            return priority + " | " + compactId + " | "
                    + (subject != null ? subject : "");
        }
    }
    
    // Configuration
    private String presentationAddress;
    private String userOrAddress;
    private String password;
    private boolean useP3; // false = P7 Message Store, true = P3 Channel
    private int connectTimeoutSeconds; // Connection timeout in seconds
    
    public AMHSMessageService() {
        this.isConnected = false;
        this.useP3 = false;
        this.connectTimeoutSeconds = 30; // Default 30 second timeout
    }
    
    /**
     * Set connection timeout in seconds
     * @param timeoutSeconds timeout value
     */
    public void setConnectTimeout(int timeoutSeconds) {
        this.connectTimeoutSeconds = timeoutSeconds;
    }
    
    /**
     * Configure connection parameters for P7 Message Store
     */
    public void configureP7(String presentationAddress, String userOrAddress, String password) {
        this.presentationAddress = presentationAddress;
        this.userOrAddress = userOrAddress;
        this.password = password;
        this.useP3 = false;
    }
    
    /**
     * Configure connection parameters for P3 Channel
     */
    public void configureP3(String presentationAddress, String userOrAddress, String password) {
        this.presentationAddress = presentationAddress;
        this.userOrAddress = userOrAddress;
        this.password = password;
        this.useP3 = true;
    }
    
    /**
     * Connect to the X.400 Message Store or P3 Channel
     * @return true if connection successful
     */
    public boolean connect() throws X400APIException {
        if (isConnected) {
            return true;
        }
        
        try {
            System.out.println("DEBUG: Initializing X.400 system...");
            System.out.println("DEBUG: Library path: " + System.getProperty("java.library.path"));
            System.out.println("DEBUG: Working directory: " + System.getProperty("user.dir"));
            
            String addressToUse = normalizePresentationAddress(presentationAddress);
            System.out.println("DEBUG: Original Presentation Address: " + presentationAddress);
            if (!addressToUse.equals(presentationAddress)) {
                System.out.println("DEBUG: Normalized Presentation Address: " + addressToUse);
            }
            
            // Extract host and port for diagnostic purposes
            String extractedHost = extractIP(presentationAddress);
            System.out.println("DEBUG: Extracted host: " + extractedHost);
            
            if (useP3) {
                System.out.println("Connecting to P3 Channel...");
                System.out.println("DEBUG: Attempting P3 bind with address: " + addressToUse);
                P3BindSession p3Session = new P3BindSession(addressToUse, userOrAddress, password);
                session = p3Session;
            } else {
                System.out.println("Connecting to P7 Message Store...");
                System.out.println("DEBUG: Attempting P7 bind with address: " + addressToUse);
                P7BindSession p7Session = new P7BindSession(addressToUse, userOrAddress, password);
                session = p7Session;
            }

            session.SetSummarizeOnBind(false);
            
            //session.SetSummarizeOnBind(false);
            // Set connection timeout before binding - use a longer timeout for initial connection
            //try {
                // Increase timeout to 90 seconds for more reliable connections
                //int effectiveTimeout = Math.max(connectTimeoutSeconds, 90);
                //session.SetTimeout(effectiveTimeout);
                //System.out.println("DEBUG: Connection timeout set to " + effectiveTimeout + " seconds");
            //} catch (Exception e) {
            //    System.out.println("DEBUG: Could not set timeout: " + e.getMessage());
            //}
            
            System.out.println("DEBUG: Calling bind()...");
            session.bind();
            
            isConnected = true;
            System.out.println("Connected successfully");
            return true;
            
        } catch (UnsatisfiedLinkError e) {
            String errorMsg = "Native library loading error: " + e.getMessage();
            System.err.println("ERROR: " + errorMsg);
            System.err.println("The Isode X.400 native libraries (DLLs) are not properly installed.");
            System.err.println("Required files: pthreadvc2.dll, CJavaInterface.dll");
            System.err.println("These files should be in: lib/amd64/ or lib/ directory");
            System.err.println("Please ensure you have installed the Isode X.400 libraries correctly.");
            throw new X400APIException(errorMsg);
        } catch (X400APIException e) {
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Throwable e) {
            String errorMsg = "Unexpected error during connection: " + e.getClass().getName() + " - " + e.getMessage();
            System.err.println("ERROR: " + errorMsg);
            e.printStackTrace();
            throw new X400APIException(errorMsg);
        }
    }
    
    /**
     * Extract IP address from presentation address string
     */
    private String extractIP(String address) {
        if (address == null) return "unknown";
        if (address.contains("://")) {
            String[] parts = address.split("://");
            if (parts.length > 1) {
                String host = parts[1].split("[:/+|]")[0];
                return host;
            }
        }
        if (address.contains("=")) {
            String[] parts = address.split("=");
            if (parts.length > 1) {
                String host = parts[1].split("[:/+|]")[0];
                return host;
            }
        }
        return address;
    }

    /**
     * Normalize presentation address formats for ISODE X.400 bind.
     * Supports multiple formats:
     * - URI format: "3001"/URI+0000+URL+itot://192.168.22.186:3001
     * - Internet format: "3001"/Internet=192.168.22.186+3001
     * - Direct IP format: 192.168.22.186:3001
     */
    private String normalizePresentationAddress(String address) {
        if (address == null) {
            return null;
        }

        String normalized = address.trim();

        // Handle direct IP:port format (e.g., "192.168.22.186:3001")
        if (normalized.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+")) {
            // Convert to Internet format with default selector "3001"
            int colonIdx = normalized.lastIndexOf(':');
            String host = normalized.substring(0, colonIdx);
            String port = normalized.substring(colonIdx + 1);
            normalized = "\"3001\"/Internet=" + host + "+" + port;
            System.out.println("DEBUG: Converted IP:port to " + normalized);
        }

        // Collapse duplicated quote characters before the transport descriptor
        normalized = normalized.replaceAll("\"{2,}/", "\"/");

        // Convert URI-based transport syntax to Internet transport syntax if needed
        if (normalized.contains("URI+0000+URL+itot://") || normalized.contains("URI+0000+URL+tcp://") || normalized.contains("URI+0000+URL+http://")) {
            String[] parts = normalized.split("URI\\+0000\\+URL\\+");
            if (parts.length == 2) {
                // Strip any trailing / that survived the split so prefix + "/Internet=..." doesn't create //
                String prefix = parts[0].replaceAll("/+$", "");
                String uriPart = parts[1];
                int schemeEnd = uriPart.indexOf("://");
                if (schemeEnd >= 0) {
                    String hostPort = uriPart.substring(schemeEnd + 3);
                    int colonIndex = hostPort.lastIndexOf(":");
                    if (colonIndex > 0) {
                        String host = hostPort.substring(0, colonIndex);
                        String port = hostPort.substring(colonIndex + 1);
                        normalized = prefix + "/Internet=" + host + "+" + port;
                    }
                }
            }
        }

        // Also handle case where address already has Internet= but might have extra quotes or formatting issues
        if (normalized.contains("Internet=")) {
            // Ensure proper format: "selector"/Internet=host+port
            // Remove any extra whitespace
            normalized = normalized.replaceAll("\\s+", "");
            // Ensure single quotes around selector if present
            normalized = normalized.replaceAll("\"+", "\"");
        }

        System.out.println("DEBUG: Final normalized address: " + normalized);
        return normalized;
    }

    /**
     * Disconnect from the X.400 system
     */
    public void disconnect() {
        discardPendingReceiptNotifications();
        discardPendingInteractiveNotifications();
        isConnected = false;
        if (session != null) {
            try {
                session.unbind();
            } catch (X400APIException e) {
                System.err.println("Disconnect error: " + e.getMessage());
            }
        }
        session = null;
        System.out.println("Disconnected");
    }
    
    /**
     * Check if currently connected
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    public String getLastSentFilingTime() {
        return lastSentFilingTime;
    }

    /** Resolve dynamic AMHS values without modifying the supplied map. */
    public Map<String, String> resolveDynamicAmhsDefaults(Map<String, String> amhsDefaults) {
        return payloadGenerator.resolveDynamicAmhsDefaults(amhsDefaults);
    }
    
    /**
     * Send an X.400 message with full AMHS configuration
     * @param recipient O/R Address of the recipient
     * @param subject Message subject
     * @param content Message content
     * @param priority Message priority
     * @param amhsDefaults Additional AMHS configuration fields
     * @return Message submission ID
     */
    public String sendMessage(String recipient, String subject, String content,
                          X400Msg.X400_Priority priority, Map<String, String> amhsDefaults)
        throws X400APIException {

    if (!isConnected) {
        throw new X400APIException("Not connected to X.400 system");
    }

    // Resolve relative values into a private copy immediately before the
    // message is classified and built. The caller's test-case map is unchanged.
    amhsDefaults = resolveDynamicAmhsDefaults(amhsDefaults);

    if (isCtsw014ReceiptNotification(amhsDefaults)) {
        lastSentFilingTime = null;
        return sendPendingReceiptNotification(amhsDefaults);
    }

    if (isStandaloneFictitiousReceiptNotification(amhsDefaults)) {
        lastSentFilingTime = null;
        return sendStandaloneReceiptNotification(recipient, amhsDefaults);
    }

    if (amhsDefaults != null
            && "rn".equalsIgnoreCase(trimToNull(amhsDefaults.get("message-type")))) {
        throw new X400APIException("RN submission requires either a staged CTSW014 subject IPM "
                + "or a configured CTSW015 subject-ipm-id.");
    }

    String filingTimeUsed = payloadGenerator.resolveFilingTime(amhsDefaults);
    lastSentFilingTime = filingTimeUsed;
    logger.log(Level.INFO, "Using filing-time: " + filingTimeUsed);
    System.out.println("DEBUG: Filing-time used: " + filingTimeUsed);

    X400APIException lastException = null;

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        P7BindSession bindSession = null;
        try {
            logger.log(Level.INFO, "Attempting to send AMHS message (attempt " + attempt + "/" + MAX_RETRIES + ")");

            String addressToUse = normalizePresentationAddress(presentationAddress);
            logger.log(Level.INFO, "Normalized presentation address for send: " + addressToUse);
            System.out.println("DEBUG: Normalized presentation address for send: " + addressToUse);

            // Fresh dedicated session for message submission
            bindSession = new P7BindSession(addressToUse, userOrAddress, password);
            bindSession.bind();

            X400Msg x400msg;
            // Determine if this is a probe message (CTSW011-013). Testcase resources
            // use message-type=probe; retain support for the legacy probe key.
            String messageType = amhsDefaults != null ? amhsDefaults.get("message-type") : null;
            String probe = amhsDefaults != null ? amhsDefaults.get("probe") : null;
            boolean isProbe = "probe".equalsIgnoreCase(messageType != null ? messageType.trim() : "")
                    || (probe != null && !probe.trim().isEmpty());
            if (isProbe) {
                // Build a probe message using the dedicated builder
                x400msg = payloadGenerator.buildProbeMessage(
                        bindSession,
                        recipient,
                        subject,
                        content,
                        priority != null ? priority.toString() : "NORMAL",
                        amhsDefaults,
                        filingTimeUsed
                );
            } else {
                // Standard message
                x400msg = payloadGenerator.buildX400Message(
                        bindSession,
                        recipient,
                        subject,
                        content,
                        priority != null ? priority.toString() : "NORMAL",
                        amhsDefaults,
                        filingTimeUsed
                );
            }
            // Send the constructed message
            x400msg.sendMsg(bindSession);
            String msgSubId = x400msg.getMessageIdentifier();
            System.out.println("AMHS Message submitted.");
            System.out.println("Message Submission ID: " + msgSubId);
            logger.log(Level.INFO, "AMHS Message sent successfully with ID: " + msgSubId);

            bindSession.unbind();
            return msgSubId;

        } catch (X400APIException e) {
            lastException = e;
            int nativeCode = e.getNativeErrorCode();
            logger.log(Level.WARNING, "AMHS send attempt " + attempt + " failed: " + e.getMessage()
                    + " [nativeCode=" + nativeCode + ", "
                    + X400APIException.nativeErrorToString(nativeCode) + "]", e);

            if (bindSession != null) {
                try { bindSession.unbind(); } catch (X400APIException ex) {
                    logger.log(Level.FINE, "Error unbinding after failed send: " + ex.getMessage());
                }
                bindSession = null;
            }

            if (attempt < MAX_RETRIES) {
                try {
                    logger.log(Level.INFO, "Waiting " + RETRY_DELAY_MS + "ms before retry...");
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastException;
                }
            }
        }
    }

    throw (lastException != null)
            ? lastException
            : new X400APIException("Failed to send AMHS message after " + MAX_RETRIES + " attempts");
}
    
    /**
     * Send an X.400 message
     * @param recipient O/R Address of the recipient
     * @param subject Message subject
     * @param content Message content (IA5 text)
     * @param priority Message priority (optional)
     * @return Message submission ID
     */
    public String sendMessage(String recipient, String subject, String content, 
                               X400Msg.X400_Priority priority) throws X400APIException {
        return sendMessage(recipient, subject, content, priority, null);
    }
    
    /**
     * Send a message with default normal priority
     */
    public String sendMessage(String recipient, String subject, String content) 
            throws X400APIException {
        return sendMessage(recipient, subject, content, X400Msg.X400_Priority.NORMAL_PRIORITY);
    }
    
    /**
     * Receive messages from the mailbox
     * @param maxMessages Maximum number of messages to receive
     * @return List of received messages
     */
    public List<MessageSummary> receiveMessages(int maxMessages) throws X400APIException {
        return receiveMessages(maxMessages, null);
    }

    /**
     * Receives messages and optionally stages a real CTSW014 receipt
     * notification from a matching subject IPM.
     */
    public List<MessageSummary> receiveMessages(int maxMessages, Map<String, String> selectedDefaults)
            throws X400APIException {
        return receiveMessages(maxMessages, selectedDefaults, null);
    }

    public List<MessageSummary> receiveMessages(int maxMessages, Map<String, String> selectedDefaults,
            Consumer<PendingNotificationChoice> pendingChoiceConsumer) throws X400APIException {
        List<MessageSummary> messages = new ArrayList<>();
        boolean prepareReceiptNotification = isCtsw014ReceiptNotification(selectedDefaults);
        ReceiveAction receiveAction = resolveReceiveAction(selectedDefaults);
        int configuredNdrReason = receiveAction == ReceiveAction.RETURN_NDR
                ? resolveResponseReason(selectedDefaults, "non-delivery-reason", 1) : 0;
        P7BindSession bindSession = null;
        boolean retainBindSession = false;

        if (prepareReceiptNotification) {
            discardPendingReceiptNotifications();
        }
        if (receiveAction == ReceiveAction.STAGE_IPN) {
            synchronized (this) {
                if (!pendingInteractiveNotifications.isEmpty()) {
                    throw new X400APIException("Send or discard the pending RN/NRN responses before receiving more messages.");
                }
            }
            return inspectInteractiveNotificationMessages(maxMessages, pendingChoiceConsumer);
        }
        
        try {
            bindSession = new P7BindSession(normalizePresentationAddress(presentationAddress), userOrAddress, password);
            bindSession.bind();
            
            int numMsgs = bindSession.getRefreshNumberOfMessages();
            if (numMsgs == 0) {
                System.out.println("No messages in mailbox");
                bindSession.unbind();
                bindSession = null;
                return messages;
            }
            
            int toReceive = receiveAction == ReceiveAction.AUTO
                    ? Math.min(maxMessages, numMsgs)
                    : Math.min(numMsgs, Math.min(maxMessages, numMsgs));
            System.out.println("Found " + numMsgs + " messages, receiving " + toReceive);
            
            for (int i = 0; i < toReceive; i++) {
                ReceiveMsg rm = bindSession.receiveNextAvailableMessage();
                int finishStatus = receiveAction == ReceiveAction.RETURN_NDR
                        ? X400_att.X400_E_NONDELIVERED : X400_att.X400_E_NOERROR;
                int finishReason = receiveAction == ReceiveAction.RETURN_NDR
                        ? configuredNdrReason : 0;
                boolean shouldFinishMessage = true;

                MessageSummary summary = new MessageSummary();
                try {
                    try {
                        summary.setSubject(rm.getSubject());
                    } catch (Exception e) {
                        summary.setSubject(null);
                    }
                    try {
                        summary.setSender(rm.getFrom());
                    } catch (Exception e) {
                        summary.setSender(null);
                    }
                    try {
                        summary.setContent(rm.getTextContent());
                    } catch (Exception e) {
                        String emsg = e.getMessage() != null ? e.getMessage() : "";
                        if (emsg.contains("status = 80") || emsg.contains("COMPLEX_BODY") || emsg.contains("x400_ms_msggetstrparam")) {
                            // Complex/non-text body — treat content as null and continue
                            summary.setContent(null);
                        } else {
                            // Unknown error — rethrow as X400APIException so outer handler deals with it
                            throw new X400APIException(e.getMessage());
                        }
                    }
                    
                    com.attech.amhs.ua.isode.ReceivedMessage1 receivedMsg = new com.attech.amhs.ua.isode.ReceivedMessage1(rm, 0);
                    if (receiveAction == ReceiveAction.RETURN_NDR) {
                        summary.setReportDetails("Returned NDR for received message; non-delivery reason="
                                + finishReason + ".");
                    }
                    // Set report type and details if this is a report message
                    else if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.REPORT) {
                        summary.setReportType("DR/NDR");
                        StringBuilder details = new StringBuilder();
                        if (receivedMsg.getReportRecips() != null && !receivedMsg.getReportRecips().isEmpty()) {
                            for (com.attech.amhs.ua.isode.ReportRecipient recip : receivedMsg.getReportRecips()) {
                                if (details.length() > 0) details.append("; ");
                                details.append("Addr: ").append(recip.getAddress());
                                if (recip.getDeliveryTime() != null) {
                                    details.append(", Delivered: ").append(recip.getDeliveryTime());
                                }
                                if (recip.getNonDeliveryReason() != null) {
                                    details.append(", NDR Reason: ").append(recip.getNonDeliveryReason());
                                }
                                if (recip.getNonDeliveryDiagnosticCode() != null) {
                                    details.append(", Diagnostic: ").append(recip.getNonDeliveryDiagnosticCode());
                                }
                                if (recip.getSuplementInfo() != null && !recip.getSuplementInfo().isEmpty()) {
                                    details.append(", Supp Info: ").append(recip.getSuplementInfo());
                                }
                            }
                        }
                        String ctsw015Validation = validateCtsw015Ndr(receivedMsg, selectedDefaults);
                        if (ctsw015Validation != null) {
                            if (details.length() > 0) details.append("; ");
                            details.append(ctsw015Validation);
                        }
                        summary.setReportDetails(details.toString());
                    } else if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.IPN) {
                        summary.setReportType("IPN");
                        summary.setReportDetails("IPN receipt notification");
                    } else if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.PROBE) {
                        summary.setReportType("PROBE");
                        summary.setReportDetails("Probe message");
                    } else if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.IPM) {
                        Integer ipnReq = receivedMsg.getIpnRequest();
                        if (receiveAction == ReceiveAction.AUTO
                                && ipnReq != null && (ipnReq == 1 || ipnReq == 3)) { // 1=RN, 3=both
                            String actualSubjectPriority = null;
                            try {
                                actualSubjectPriority = trimToNull(
                                        rm.getStringParam(com.isode.x400api.AMHS_att.ATS_S_PRIORITY_INDICATOR));
                            } catch (Exception ex) {
                                logger.log(Level.FINE, "ATS priority is not directly available: " + ex.getMessage());
                            }
                            if (actualSubjectPriority == null) {
                                actualSubjectPriority = trimToNull(receivedMsg.getAtsPriority());
                            }
                            if (prepareReceiptNotification) {
                                String subjectMessageId = trimToNull(
                                        rm.getStringParam(X400_att.X400_S_IPM_IDENTIFIER));
                                System.out.println("--------------------------------X400_S_IPM_IDENTIFIER = "+subjectMessageId);
                                if (subjectMessageId == null) {
                                    subjectMessageId = trimToNull(rm.getMessageIdentifier());
                                }
                                logger.log(Level.INFO, "Staging CTSW014 RN for subject priority "
                                        + actualSubjectPriority + ".");
                                if (!"SS".equalsIgnoreCase(actualSubjectPriority)
                                        && !"DD".equalsIgnoreCase(actualSubjectPriority)) {
                                    summary.setReportDetails("RN not staged for CTSW014: subject IPM priority is "
                                            + actualSubjectPriority + ", expected SS or DD.");
                                } else {
                                    X400Msg generated = rm.generateIPN(bindSession, -1);
                                    if (generated == null) {
                                        summary.setReportDetails("RN was not staged: the subject message does not permit an IPN.");
                                    } else {
                                        synchronized (this) {
                                            pendingReceiptNotifications.add(new PendingReceiptNotification(
                                                    generated, subjectMessageId, summary.getSubject(),
                                                    actualSubjectPriority, summary.getSender()));
                                            pendingReceiptNotificationSession = bindSession;
                                        }
                                        retainBindSession = true;
                                        summary.setMessageId(subjectMessageId);
                                        summary.setSubjectPriority(actualSubjectPriority);
                                        summary.setReceiptNotificationStaged(true);
                                        summary.setReportDetails("RN staged for CTSW014 subject "
                                                + subjectMessageId + " with priority " + actualSubjectPriority + ".");
                                    }
                                }
                            } else {
                                logger.log(Level.INFO, "Message requests IPN. Automatically generating and sending IPN.");
                                try {
                                    com.isode.x400api.MSMessage ipnMessage = new com.isode.x400api.MSMessage();
                                    int result = com.isode.x400api.X400ms.x400_ms_msgmakeIPN(rm, -1, ipnMessage);
                                    if (result == com.isode.x400api.X400_att.X400_E_NOERROR) {
                                        result = com.isode.x400api.X400ms.x400_ms_msgsend(ipnMessage);
                                        if (result == com.isode.x400api.X400_att.X400_E_NOERROR) {
                                            logger.log(Level.INFO, "Successfully returned IPN.");
                                            summary.setReportDetails("Automatically returned IPN. Route: " + userOrAddress + " -> " + rm.getFrom());
                                        } else {
                                            logger.log(Level.WARNING, "Failed to send IPN: " + result);
                                        }
                                    } else {
                                        logger.log(Level.WARNING, "Failed to make IPN: " + result);
                                    }
                                } catch (Exception ex) {
                                    logger.log(Level.WARNING, "Error generating IPN: " + ex.getMessage());
                                }
                            }
                        }
                    }

                    messages.add(summary);
                } finally {
                    if (shouldFinishMessage) {
                        try {
                            int finishResult = rm.finishWithMessage(finishStatus, finishReason);
                            if (finishResult != X400_att.X400_E_NOERROR) {
                                logger.log(Level.WARNING, "Failed to finish received message: " + finishResult);
                            }
                        } catch (Exception ex) {
                            // Log and continue
                            System.err.println("Error finishing message: " + ex.getMessage());
                        }
                    }
                }

            }
            
            if (!retainBindSession) {
                bindSession.unbind();
                bindSession = null;
            }
            
        } catch (X400APIException e) {
            System.err.println("Error receiving messages: " + e.getMessage());
            throw e;
        } finally {
            if (bindSession != null && !retainBindSession) {
                try {
                    bindSession.unbind();
                } catch (X400APIException ex) {
                    logger.log(Level.FINE, "Error unbinding receive session: " + ex.getMessage());
                }
            }
        }
        
        return messages;
    }

    private ReceiveAction resolveReceiveAction(Map<String, String> defaults) throws X400APIException {
        String configured = defaults != null ? trimToNull(defaults.get("receive-action")) : null;
        if (configured == null || "auto".equalsIgnoreCase(configured)) {
            return ReceiveAction.AUTO;
        }
        if ("accept".equalsIgnoreCase(configured) || "none".equalsIgnoreCase(configured)) {
            return ReceiveAction.ACCEPT;
        }
        if ("stage-ipn".equalsIgnoreCase(configured)
                || "rn".equalsIgnoreCase(configured) || "return-rn".equalsIgnoreCase(configured)
                || "nrn".equalsIgnoreCase(configured) || "return-nrn".equalsIgnoreCase(configured)) {
            return ReceiveAction.STAGE_IPN;
        }
        if ("ndr".equalsIgnoreCase(configured) || "return-ndr".equalsIgnoreCase(configured)) {
            return ReceiveAction.RETURN_NDR;
        }
        throw new X400APIException("Invalid receive-action: " + configured
                + ". Expected auto, accept, stage-ipn, or return-ndr.");
    }

    private int resolveResponseReason(Map<String, String> defaults, String key, int defaultValue)
            throws X400APIException {
        String configured = defaults != null ? trimToNull(defaults.get(key)) : null;
        if (configured == null) {
            return defaultValue;
        }
        if ("ipm-discarded".equalsIgnoreCase(configured)) {
            return 0;
        }
        if ("ipm-auto-forwarded".equalsIgnoreCase(configured)) {
            return 1;
        }
        if ("unable-to-transfer".equalsIgnoreCase(configured)) {
            return 1;
        }
        try {
            int reason = Integer.parseInt(configured);
            if (reason < 0) {
                throw new NumberFormatException();
            }
            return reason;
        } catch (NumberFormatException ex) {
            throw new X400APIException("Invalid " + key + ": " + configured);
        }
    }

    private List<MessageSummary> inspectInteractiveNotificationMessages(int maxMessages,
            Consumer<PendingNotificationChoice> pendingChoiceConsumer) throws X400APIException {
        List<MessageSummary> messages = new ArrayList<>();
        P7BindSession bindSession = null;
        try {
            bindSession = new P7BindSession(normalizePresentationAddress(presentationAddress),
                    userOrAddress, password);
            bindSession.bind();
            ArrayList<ListResult> mailbox = bindSession.listMailbox(null,
                    P7BindSession.Entry_Class.MS_ENTRY_CLASS_STORED_MESSAGES, false);
            int inspected = 0;
            for (int index = 1; index < mailbox.size() && inspected < maxMessages; index++) {
                ListResult listed = mailbox.get(index);
                ReceiveMsg received = null;
                try {
                    received = new ReceiveMsg(bindSession, listed.getSequenceNumber());
                    MessageSummary summary = createInteractiveMessageSummary(received);
                    messages.add(summary);
                    inspected++;

                    com.attech.amhs.ua.isode.ReceivedMessage1 parsed =
                            new com.attech.amhs.ua.isode.ReceivedMessage1(received, 0);
                    if (parsed.getType() != com.attech.amhs.ua.isode.enums.MessageType.IPM) {
                        summary.setReportDetails("Not staged: only IPMs can return RN or NRN.");
                        continue;
                    }

                    int request = parsed.getIpnRequest() != null ? parsed.getIpnRequest() : 0;
                    int supportedRequest = request
                            & (X400Msg.IPN_RECEIPT_NOTIFICATION | X400Msg.IPN_NON_RECEIPT_NOTIFICATION);
                    if (supportedRequest == 0) {
                        summary.setReportDetails("Not staged: the IPM did not request RN or NRN.");
                        continue;
                    }

                    String ipmId = trimToNull(parsed.getIpmId());
                    if (ipmId == null) {
                        ipmId = trimToNull(received.getStringParam(X400_att.X400_S_IPM_IDENTIFIER));
                    }
                    String messageIdentifier = trimToNull(received.getMessageIdentifier());
                    PendingInteractiveNotification pending;
                    PendingNotificationChoice choice;
                    synchronized (this) {
                        pending = new PendingInteractiveNotification(
                                nextPendingInteractiveNotificationId++, summary.getSender(),
                                summary.getSubject(), createTextPreview(summary.getContent()), ipmId,
                                messageIdentifier, listed.getSequenceNumber(), supportedRequest);
                        pendingInteractiveNotifications.add(pending);
                        choice = new PendingNotificationChoice(pending);
                    }
                    summary.setMessageId(messageIdentifier);
                    summary.setReportDetails("RN/NRN response available for user selection. IPM ID="
                            + ipmId + ".");
                    if (pendingChoiceConsumer != null) {
                        pendingChoiceConsumer.accept(choice);
                    }
                } finally {
                    if (received != null) {
                        try {
                            received.delete(true);
                        } catch (Exception ex) {
                            logger.log(Level.FINE, "Error releasing inspected message object: " + ex.getMessage());
                        }
                    }
                }
            }
            return messages;
        } finally {
            if (bindSession != null) {
                try {
                    bindSession.unbind();
                } catch (Exception ex) {
                    logger.log(Level.FINE, "Error unbinding mailbox inspection session: " + ex.getMessage());
                }
            }
        }
    }

    private MessageSummary createInteractiveMessageSummary(ReceiveMsg received) throws X400APIException {
        MessageSummary summary = new MessageSummary();
        try {
            summary.setSubject(received.getSubject());
        } catch (Exception ex) {
            summary.setSubject(null);
        }
        try {
            summary.setSender(received.getFrom());
        } catch (Exception ex) {
            summary.setSender(null);
        }
        try {
            summary.setContent(received.getTextContent());
        } catch (Exception ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "";
            if (message.contains("status = 80") || message.contains("COMPLEX_BODY")
                    || message.contains("x400_ms_msggetstrparam")) {
                summary.setContent(null);
            } else {
                throw new X400APIException(ex.getMessage());
            }
        }
        return summary;
    }

    private String createTextPreview(String content) {
        if (content == null) return "";
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 77) + "..." : compact;
    }

    public synchronized List<PendingNotificationChoice> getPendingNotificationChoices() {
        List<PendingNotificationChoice> choices = new ArrayList<>();
        for (PendingInteractiveNotification pending : pendingInteractiveNotifications) {
            choices.add(new PendingNotificationChoice(pending));
        }
        return choices;
    }

    public synchronized String sendPendingNotification(long id, boolean sendRn, int nrnReason)
            throws X400APIException {
        PendingInteractiveNotification pending = findPendingInteractiveNotification(id);
        if (pending == null) {
            throw new X400APIException("The selected received message is no longer available.");
        }
        int requiredBit = sendRn ? X400Msg.IPN_RECEIPT_NOTIFICATION
                : X400Msg.IPN_NON_RECEIPT_NOTIFICATION;
        String responseName = sendRn ? "RN" : "NRN";
        if ((pending.notificationRequest & requiredBit) == 0) {
            throw new X400APIException("The received IPM did not request an " + responseName + ".");
        }
        if (!sendRn && nrnReason != 0 && nrnReason != 1) {
            throw new X400APIException("NRN reason must be 0 (ipm-discarded) or 1 (ipm-auto-forwarded).");
        }

        P7BindSession bindSession = null;
        ReceiveMsg received = null;
        X400Msg response = null;
        boolean receivedFinished = false;
        boolean responseSent = false;
        try {
            bindSession = new P7BindSession(normalizePresentationAddress(presentationAddress),
                    userOrAddress, password);
            bindSession.bind();
            received = new ReceiveMsg(bindSession, pending.sequenceNumber);
            verifyPendingMessageIdentity(received, pending);
            response = received.generateIPN(bindSession, sendRn ? -1 : nrnReason);
            if (response == null) {
                throw new X400APIException("Isode could not generate the requested " + responseName + ".");
            }
            response.sendMsg(bindSession);
            responseSent = true;
            String responseMessageId = null;
            try {
                responseMessageId = response.getMessageIdentifier();
            } catch (Exception ex) {
                logger.log(Level.FINE, "Notification sent but its message ID is unavailable: " + ex.getMessage());
            }
            int finishResult = received.finishWithMessage(X400_att.X400_E_NOERROR, 0);
            receivedFinished = true;
            if (finishResult != X400_att.X400_E_NOERROR) {
                logger.log(Level.WARNING, "Notification was sent, but finishing source IPM returned "
                        + finishResult + ".");
            }
            logger.log(Level.INFO, responseName + " sent for received IPM " + pending.ipmId + ".");
            return responseMessageId != null ? responseMessageId : "(unavailable)";
        } finally {
            if (responseSent) {
                pendingInteractiveNotifications.remove(pending);
            }
            deleteGeneratedMessage(response);
            if (received != null && !receivedFinished) {
                try {
                    received.delete(true);
                } catch (Exception ex) {
                    logger.log(Level.FINE, "Error releasing selected message object: " + ex.getMessage());
                }
            }
            if (bindSession != null) {
                try {
                    bindSession.unbind();
                } catch (Exception ex) {
                    logger.log(Level.FINE, "Error unbinding RN/NRN response session: " + ex.getMessage());
                }
            }
        }
    }

    public synchronized void discardPendingNotification(long id) throws X400APIException {
        PendingInteractiveNotification pending = findPendingInteractiveNotification(id);
        if (pending == null) {
            throw new X400APIException("The selected received message is no longer available.");
        }
        P7BindSession bindSession = null;
        ReceiveMsg received = null;
        boolean receivedFinished = false;
        try {
            bindSession = new P7BindSession(normalizePresentationAddress(presentationAddress),
                    userOrAddress, password);
            bindSession.bind();
            received = new ReceiveMsg(bindSession, pending.sequenceNumber);
            verifyPendingMessageIdentity(received, pending);
            int finishResult = received.finishWithMessage(X400_att.X400_E_NOERROR, 0);
            receivedFinished = true;
            if (finishResult != X400_att.X400_E_NOERROR) {
                throw new X400APIException("Failed to finish the discarded message", finishResult);
            }
            pendingInteractiveNotifications.remove(pending);
        } finally {
            if (received != null && !receivedFinished) {
                try {
                    received.delete(true);
                } catch (Exception ex) {
                    logger.log(Level.FINE, "Error releasing discarded message object: " + ex.getMessage());
                }
            }
            if (bindSession != null) {
                try {
                    bindSession.unbind();
                } catch (Exception ex) {
                    logger.log(Level.FINE, "Error unbinding discard session: " + ex.getMessage());
                }
            }
        }
    }

    private void verifyPendingMessageIdentity(ReceiveMsg received, PendingInteractiveNotification pending)
            throws X400APIException {
        String actualIdentifier = trimToNull(received.getMessageIdentifier());
        if (pending.messageIdentifier != null && !pending.messageIdentifier.equals(actualIdentifier)) {
            throw new X400APIException("The mailbox sequence now identifies a different message. Refresh the list.");
        }
    }

    private PendingInteractiveNotification findPendingInteractiveNotification(long id) {
        for (PendingInteractiveNotification pending : pendingInteractiveNotifications) {
            if (pending.id == id) return pending;
        }
        return null;
    }

    private synchronized void discardPendingInteractiveNotifications() {
        pendingInteractiveNotifications.clear();
    }

    private void deleteGeneratedMessage(X400Msg message) {
        if (message == null) return;
        try {
            message.delete(false);
        } catch (Exception ex) {
            logger.log(Level.FINE, "Error deleting generated notification: " + ex.getMessage());
        }
    }

    private boolean isCtsw014ReceiptNotification(Map<String, String> defaults) {
        if (defaults == null) {
            return false;
        }
        String messageType = trimToNull(defaults.get("message-type"));
        return "rn".equalsIgnoreCase(messageType)
                && trimToNull(defaults.get("subject-message-priority")) != null;
    }

    private boolean isStandaloneFictitiousReceiptNotification(Map<String, String> defaults) {
        if (defaults == null) {
            return false;
        }
        return "rn".equalsIgnoreCase(trimToNull(defaults.get("message-type")))
                && trimToNull(defaults.get("subject-ipm-id")) != null
                && trimToNull(defaults.get("subject-message-priority")) == null;
    }

    private String sendPendingReceiptNotification(Map<String, String> defaults) throws X400APIException {
        String requiredPriority = trimToNull(defaults.get("subject-message-priority"));
        String requiredMessageId = trimToNull(defaults.get("subject-message-id"));
        synchronized (this) {
            if (pendingReceiptNotifications.isEmpty() || pendingReceiptNotificationSession == null) {
                throw new X400APIException("CTSW014 requires a subject IPM. Select this subcase, receive an IPM "
                        + "that requests an RN and has ATS priority " + requiredPriority
                        + ", then press Send defaults again.");
            }

            if (requiredMessageId == null) {
                throw new X400APIException("Select an RN Subject IPM before sending CTSW014 "
                        + requiredPriority + ".");
            }

            PendingReceiptNotification selected = null;
            for (PendingReceiptNotification pending : pendingReceiptNotifications) {
                boolean idMatches = requiredMessageId.equals(pending.subjectMessageId);
                boolean priorityMatches = requiredPriority == null
                        || requiredPriority.equalsIgnoreCase(pending.subjectPriority);
                if (idMatches && priorityMatches) {
                    selected = pending;
                    break;
                }
            }
            if (selected == null) {
                throw new X400APIException("No staged CTSW014 subject matches priority " + requiredPriority
                        + (requiredMessageId != null ? " and message ID " + requiredMessageId : "") + ".");
            }

            try {
                selected.message.sendMsg(pendingReceiptNotificationSession);
                String messageId = selected.message.getMessageIdentifier();
                logger.log(Level.INFO, "CTSW014 RN sent successfully with ID: " + messageId
                        + ", subject: " + selected.subjectMessageId
                        + ", priority: " + selected.subjectPriority
                        + ", originator: " + userOrAddress
                        + ", recipient: " + selected.receiptNotificationRecipient + ".");
                return messageId;
            } finally {
                deletePendingReceiptNotification(selected);
            }
        }
    }

    public synchronized List<ReceiptNotificationSubject> getPendingReceiptNotificationSubjects() {
        List<ReceiptNotificationSubject> subjects = new ArrayList<>();
        for (PendingReceiptNotification pending : pendingReceiptNotifications) {
            subjects.add(new ReceiptNotificationSubject(pending.subjectMessageId,
                    pending.subjectPriority, pending.subject,
                    pending.receiptNotificationRecipient));
        }
        return subjects;
    }

    public synchronized List<ReceiptNotificationSubject> getPendingReceiptNotificationSubjects(
            String priority) {
        List<ReceiptNotificationSubject> subjects = new ArrayList<>();
        for (PendingReceiptNotification pending : pendingReceiptNotifications) {
            if (priority != null && !priority.equalsIgnoreCase(pending.subjectPriority)) {
                continue;
            }
            subjects.add(new ReceiptNotificationSubject(pending.subjectMessageId,
                    pending.subjectPriority, pending.subject,
                    pending.receiptNotificationRecipient));
        }
        return subjects;
    }

    public synchronized ReceiptNotificationSubject getFirstPendingReceiptNotificationSubject(String priority) {
        for (PendingReceiptNotification pending : pendingReceiptNotifications) {
            if (priority != null && priority.equalsIgnoreCase(pending.subjectPriority)) {
                return new ReceiptNotificationSubject(pending.subjectMessageId,
                        pending.subjectPriority, pending.subject,
                        pending.receiptNotificationRecipient);
            }
        }
        return null;
    }

    private void deletePendingReceiptNotification(PendingReceiptNotification pending) {
        pendingReceiptNotifications.remove(pending);
        try {
            pending.message.delete(false);
        } catch (Exception ex) {
            logger.log(Level.FINE, "Error deleting pending RN object: " + ex.getMessage());
        }
        if (pendingReceiptNotifications.isEmpty()) {
            closePendingReceiptNotificationSession();
        }
    }

    private synchronized void discardPendingReceiptNotifications() {
        for (PendingReceiptNotification pending : new ArrayList<>(pendingReceiptNotifications)) {
            try {
                pending.message.delete(false);
            } catch (Exception ex) {
                logger.log(Level.FINE, "Error deleting pending RN object: " + ex.getMessage());
            }
        }
        pendingReceiptNotifications.clear();
        closePendingReceiptNotificationSession();
    }

    private void closePendingReceiptNotificationSession() {
        if (pendingReceiptNotificationSession != null) {
            try {
                pendingReceiptNotificationSession.unbind();
            } catch (Exception ex) {
                logger.log(Level.FINE, "Error closing pending RN session: " + ex.getMessage());
            }
        }
        pendingReceiptNotificationSession = null;
    }

    private String sendStandaloneReceiptNotification(String recipient, Map<String, String> defaults)
            throws X400APIException {
        String configuredSubjectId = trimToNull(defaults.get("subject-ipm-id"));
        String subjectIpmId = toSyntacticallyValidFictitiousIpmId(configuredSubjectId, recipient);
        P7BindSession bindSession = null;
        X400Msg receiptNotification = null;
        try {
            bindSession = new P7BindSession(normalizePresentationAddress(presentationAddress),
                    userOrAddress, password);
            bindSession.bind();

            receiptNotification = new X400Msg(bindSession);
            receiptNotification.setFrom(userOrAddress);
            receiptNotification.setRecipient(true, 0, recipient,
                    X400Msg.DR_Request.DR_NON_DELIVERY_REPORT, 0);
            receiptNotification.setPriority(X400Msg.X400_Priority.NORMAL_PRIORITY);
            receiptNotification.setIntParam(X400_att.X400_N_CONTENT_TYPE, 22);
            receiptNotification.setIntParam(X400_att.X400_N_IS_IPN, 1);
            receiptNotification.setStringparam(X400_att.X400_S_SUBJECT_IPM, subjectIpmId);
            receiptNotification.setStringparam(X400_att.X400_S_RECEIPT_TIME, currentUtcTime());
            receiptNotification.sendMsg(bindSession);

            String messageId = receiptNotification.getMessageIdentifier();
            logger.log(Level.INFO, "CTSW015 standalone RN sent with ID: " + messageId
                    + ", originator: " + userOrAddress + ", recipient: " + recipient
                    + ", fictitious subject IPM: " + subjectIpmId + ".");
            return messageId;
        } finally {
            if (receiptNotification != null) {
                try { receiptNotification.delete(false); } catch (Exception ex) {
                    logger.log(Level.FINE, "Error deleting CTSW015 RN object: " + ex.getMessage());
                }
            }
            if (bindSession != null) {
                try { bindSession.unbind(); } catch (Exception ex) {
                    logger.log(Level.FINE, "Error closing CTSW015 RN session: " + ex.getMessage());
                }
            }
        }
    }

    private String toSyntacticallyValidFictitiousIpmId(String configuredValue, String recipient) {
        if (configuredValue != null && configuredValue.contains("*") && configuredValue.contains("/")) {
            return configuredValue;
        }
        return "99999999.9999*" + recipient;
    }

    private String currentUtcTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyMMddHHmmss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String validateCtsw015Ndr(com.attech.amhs.ua.isode.ReceivedMessage1 receivedMessage,
            Map<String, String> selectedDefaults) {
        if (!isStandaloneFictitiousReceiptNotification(selectedDefaults)) {
            return null;
        }

        String expectedReason = trimToNull(selectedDefaults.get("expected-ndr-reason"));
        String expectedDiagnostic = trimToNull(selectedDefaults.get("expected-ndr-diagnostic"));
        String expectedSupplementary = trimToNull(selectedDefaults.get("expected-ndr-supplementary"));
        boolean matched = false;
        if (receivedMessage.getReportRecips() != null) {
            for (com.attech.amhs.ua.isode.ReportRecipient recipient : receivedMessage.getReportRecips()) {
                boolean reasonMatches = "unable-to-transfer".equalsIgnoreCase(expectedReason)
                        && Integer.valueOf(1).equals(recipient.getNonDeliveryReason());
                boolean diagnosticMatches = "invalid-arguments".equalsIgnoreCase(expectedDiagnostic)
                        && Integer.valueOf(11).equals(recipient.getNonDeliveryDiagnosticCode());
                String actualSupplementary = trimToNull(recipient.getSuplementInfo());
                boolean supplementaryMatches = expectedSupplementary != null
                        && actualSupplementary != null
                        && actualSupplementary.trim().equalsIgnoreCase(expectedSupplementary.trim());
                if (reasonMatches && diagnosticMatches && supplementaryMatches) {
                    matched = true;
                    break;
                }
            }
        }

        if (matched) {
            return "CTSW015 PASS: NDR contains unable-to-transfer, invalid-arguments, and the expected supplementary information. "
                    + "Verify IUT Control Position logging and RN storage manually.";
        }
        return "CTSW015 FAIL: received report does not contain all expected NDR elements "
                + "(reason=unable-to-transfer[1], diagnostic=invalid-arguments[11], supplementary-information exact match).";
    }
    
    /**
     * Wait for new messages with timeout
     * @param timeoutSeconds Timeout in seconds
     * @return true if new message arrived
     */
    public boolean waitForNewMessage(int timeoutSeconds) throws X400APIException {
        if (!isConnected) {
            throw new X400APIException("Not connected to X.400 system");
        }
        
        try {
            P7BindSession bindSession = new P7BindSession(presentationAddress, userOrAddress, password);
            bindSession.bind();
            
            System.out.println("Waiting for new message (" + timeoutSeconds + " seconds)...");
            int status = bindSession.waitForNewMessages(timeoutSeconds);
            
            bindSession.unbind();
            
            if (status == X400_att.X400_E_NOERROR) {
                System.out.println("New message received!");
                return true;
            } else if (status == X400_att.X400_E_TIMED_OUT) {
                System.out.println("Timeout - no new messages");
                return false;
            } else {
                System.out.println("Error waiting for messages: " + status);
                return false;
            }
            
        } catch (X400APIException e) {
            System.err.println("Error waiting for messages: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get mailbox summary (list of messages without downloading)
     * @return List of message summaries
     */
    public List<MessageSummary> getMailboxSummary() throws X400APIException {
        List<MessageSummary> summaries = new ArrayList<>();
        
        try {
            P7BindSession bindSession = new P7BindSession(presentationAddress, userOrAddress, password);
            bindSession.bind();
            
            ArrayList<ListResult> listArray = bindSession.listMailbox(null, 
                P7BindSession.Entry_Class.MS_ENTRY_CLASS_STORED_MESSAGES, false);
            
            for (int i = 1; i < listArray.size(); i++) {
                ListResult lr = listArray.get(i);
                MessageSummary summary = new MessageSummary();
                summary.setSubject(lr.getSubject());
                summary.setSender(lr.getSender());
                summary.setSubmissionTime(lr.getSubmissionTime());
                summary.setMessageId(lr.getMsgID());
                summary.setContentLength(lr.getContLength());
                summaries.add(summary);
            }
            
            bindSession.unbind();
            
        } catch (X400APIException e) {
            System.err.println("Error getting mailbox summary: " + e.getMessage());
            throw e;
        }
        
        return summaries;
    }
    
    /**
     * Inner class for message summary
     */
    public static class MessageSummary {
        private String subject;
        private String sender;
        private String submissionTime;
        private String messageId;
        private int contentLength;
        private String content;
        private String reportType;         // DR, NDR, IPN, or null for regular messages
        private String reportDetails;      // Detailed report information
        private String drRequestType;      // The DR request type set when sending
        private String subjectPriority;
        private boolean receiptNotificationStaged;
        
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }
        
        public String getSubmissionTime() { return submissionTime; }
        public void setSubmissionTime(String submissionTime) { this.submissionTime = submissionTime; }
        
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        
        public int getContentLength() { return contentLength; }
        public void setContentLength(int contentLength) { this.contentLength = contentLength; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getReportType() { return reportType; }
        public void setReportType(String reportType) { this.reportType = reportType; }
        
        public String getReportDetails() { return reportDetails; }
        public void setReportDetails(String reportDetails) { this.reportDetails = reportDetails; }
        
        public String getDrRequestType() { return drRequestType; }
        public void setDrRequestType(String drRequestType) { this.drRequestType = drRequestType; }

        public String getSubjectPriority() { return subjectPriority; }
        public void setSubjectPriority(String subjectPriority) { this.subjectPriority = subjectPriority; }

        public boolean isReceiptNotificationStaged() { return receiptNotificationStaged; }
        public void setReceiptNotificationStaged(boolean receiptNotificationStaged) {
            this.receiptNotificationStaged = receiptNotificationStaged;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("From: ").append(sender).append(" | Subject: ").append(subject);
            if (submissionTime != null) {
                sb.append(" | Time: ").append(submissionTime);
            }
            if (reportType != null) {
                sb.append(" | Report Type: ").append(reportType);
            }
            return sb.toString();
        }
    }
}
