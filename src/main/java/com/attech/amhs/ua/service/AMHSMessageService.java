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
            logger.log(Level.WARNING, "AMHS send attempt " + attempt + " failed: " + e.getMessage(), e);

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
        List<MessageSummary> messages = new ArrayList<>();
        
        try {
            P7BindSession bindSession = new P7BindSession(presentationAddress, userOrAddress, password);
            bindSession.bind();
            
            int numMsgs = bindSession.getRefreshNumberOfMessages();
            if (numMsgs == 0) {
                System.out.println("No messages in mailbox");
                bindSession.unbind();
                return messages;
            }
            
            int toReceive = Math.min(maxMessages, numMsgs);
            System.out.println("Found " + numMsgs + " messages, receiving " + toReceive);
            
            for (int i = 0; i < toReceive; i++) {
                ReceiveMsg rm = bindSession.receiveNextAvailableMessage();

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
                    // Set report type and details if this is a report message
                    if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.REPORT) {
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
                        summary.setReportDetails(details.toString());
                    } else if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.IPN) {
                        summary.setReportType("IPN");
                        summary.setReportDetails("IPN receipt notification");
                    } else if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.PROBE) {
                        summary.setReportType("PROBE");
                        summary.setReportDetails("Probe message");
                    } else if (receivedMsg.getType() == com.attech.amhs.ua.isode.enums.MessageType.IPM) {
                        Integer ipnReq = receivedMsg.getIpnRequest();
                        if (ipnReq != null && (ipnReq == 1 || ipnReq == 3)) { // 1=RN, 3=both
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

                    messages.add(summary);
                } finally {
                    try {
                        rm.finishWithMessage(0, 0);
                    } catch (Exception ex) {
                        // Log and continue
                        System.err.println("Error finishing message: " + ex.getMessage());
                    }
                }
            }
            
            bindSession.unbind();
            
        } catch (X400APIException e) {
            System.err.println("Error receiving messages: " + e.getMessage());
            throw e;
        }
        
        return messages;
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
