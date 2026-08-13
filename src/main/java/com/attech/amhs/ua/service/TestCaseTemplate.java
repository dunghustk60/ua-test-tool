package com.attech.amhs.ua.service;

import com.attech.amhs.ua.model.TestCase;
import com.attech.amhs.ua.model.TestSubcase;
import java.util.ArrayList;
import com.attech.amhs.ua.service.TestCaseConfigLoader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Template for ICAO EUR Doc 047 Appendix A test cases CTSW001-CTSW020
 * 
 * USAGE: Replace the placeholder defaults with actual test case specifications
 * from ICAO EUR Doc 047 Appendix A. The structure below shows how to populate each test case.
 * 
 * Each test case should include:
 * - X.400 Recipient address (O/R Address format)
 * - Message subject
 * - Priority level (LOW, NORMAL, HIGH, URGENT)
 * - Message content/body
 */
public class TestCaseTemplate {

    /**
     * Load ICAO EUR Doc 047 test cases with proper defaults
     * 
     * IMPORTANT: Update this method with actual test case specifications
     * from ICAO EUR Doc 047 Appendix A (CTSW001 to CTSW020)
     * 
     * @return List of properly configured test cases
     */
    public static List<TestCase> loadICAPTestCases() {
        List<TestCase> testCases = new ArrayList<>();
        
        // CTSW001 - Basic AMHS Message Sending
        testCases.add(createCTSW001());
        
        // CTSW002 - Priority Handling
        testCases.add(createCTSW002());
        
        // CTSW003 - Recipient Addressing
        testCases.add(createCTSW003());
        
        // CTSW004 - Message Subject Handling
        testCases.add(createCTSW004());
        
        // CTSW005 - Large Message Content
        testCases.add(createCTSW005());
        
        // CTSW006 - Special Characters in Content
        testCases.add(createCTSW006());
        
        // CTSW007 - Multiple Recipients
        testCases.add(createCTSW007());
        
        // CTSW008 - Return Notification Request
        testCases.add(createCTSW008());
        
        // CTSW009 - Delivery Notification Request
        testCases.add(createCTSW009());
        
        // CTSW010 - Non-Delivery Notification
        testCases.add(createCTSW010());
        
        // CTSW011 - Read Notification Request
        testCases.add(createCTSW011());
        
        // CTSW012 - Message with Attachments
        testCases.add(createCTSW012());
        
        // CTSW013 - Encrypted Message
        testCases.add(createCTSW013());
        
        // CTSW014 - Signed Message
        testCases.add(createCTSW014());
        
        // CTSW015 - RN without a related subject message
        testCases.add(createCTSW015());
        
        // CTSW016 - Reply to Message
        testCases.add(createCTSW016());
        
        // CTSW017 - Forward Message
        testCases.add(createCTSW017());
        
        // CTSW018 - Message Recall
        testCases.add(createCTSW018());
        
        // CTSW019 - Urgent Message Handling
        testCases.add(createCTSW019());
        
        // CTSW020 - Mailbox Operations
        testCases.add(createCTSW020());
        
        return testCases;
    }

    // ========== Test Case Creators ==========
    // Each creator method should be populated with actual ICAO EUR Doc 047 specifications

    private static TestCase createCTSW001() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW001", "Basic AMHS Message Sending", 2);
        
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("Send basic AMHS message with normal priority");
        sub1.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub1.setAmhsDefault("subject", "CTSW001.1 - Basic Message Test");
        sub1.setAmhsDefault("priority", "NORMAL");
        sub1.setAmhsDefault("content", "This is a basic test message to verify AMHS message sending capability.");
        
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("Send basic AMHS message with high priority");
        sub2.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub2.setAmhsDefault("subject", "CTSW001.2 - High Priority Message");
        sub2.setAmhsDefault("priority", "HIGH");
        sub2.setAmhsDefault("content", "This is a high priority test message.");
        
        return tc;
    }

    private static TestCase createCTSW002() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW002", "Priority Handling", 2);
        
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("Test LOW priority message");
        sub1.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub1.setAmhsDefault("subject", "CTSW002.1 - Low Priority");
        sub1.setAmhsDefault("priority", "LOW");
        sub1.setAmhsDefault("content", "Low priority message test.");
        
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("Test URGENT priority message");
        sub2.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub2.setAmhsDefault("subject", "CTSW002.2 - Urgent Priority");
        sub2.setAmhsDefault("priority", "URGENT");
        sub2.setAmhsDefault("content", "Urgent priority message test.");
        
        return tc;
    }

    // ... Create remaining test cases CTSW003-CTSW020
    // Template for each:
    
    private static TestCase createCTSW003() {
        // TODO: Replace with ICAO EUR Doc 047 CTSW003 specifications
        TestCase tc = TestCaseLoader.createTestCase("CTSW003", "Recipient Addressing", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Test recipient address handling");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW003 - Recipient Test");
        sub.setAmhsDefault("priority", "NORMAL");
        sub.setAmhsDefault("content", "Recipient addressing test message.");
        return tc;
    }

    private static TestCase createCTSW004() {
        // TODO: Replace with ICAO EUR Doc 047 CTSW004 specifications
        // Load defaults from properties
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW004");
        TestCase tc = TestCaseLoader.createTestCase("CTSW004", "Message Subject Handling", 6);
        // Subcase 1 – empty ATS-message-priority
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("Empty ATS-message-priority");
        sub1.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "/CN=TestRecipient/OU=Test/O=TestOrg/PRMD=TestPRMD/ADMD=/C=US/"));
        sub1.setAmhsDefault("subject", defaults.getOrDefault("subject", "CTSW004 - Empty Priority"));
        sub1.setAmhsDefault("priority", ""); // empty
        sub1.setAmhsDefault("content", defaults.getOrDefault("content", ""));
        // Subcase 2 – invalid ATS-message-priority
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("Invalid ATS-message-priority");
        sub2.setAmhsDefault("recipient", defaults.get("recipient"));
        sub2.setAmhsDefault("subject", defaults.get("subject"));
        sub2.setAmhsDefault("priority", "INVALID");
        sub2.setAmhsDefault("content", defaults.get("content"));
        // Subcase 3 – empty ATS-message-filing-time
        TestSubcase sub3 = tc.getSubcases().get(2);
        sub3.setDescription("Empty ATS-message-filing-time");
        sub3.setAmhsDefault("recipient", defaults.get("recipient"));
        sub3.setAmhsDefault("subject", defaults.get("subject"));
        sub3.setAmhsDefault("priority", defaults.get("priority"));
        sub3.setAmhsDefault("filingTime", ""); // empty filing time
        sub3.setAmhsDefault("content", defaults.get("content"));
        // Subcase 4 – invalid ATS-message-filing-time
        TestSubcase sub4 = tc.getSubcases().get(3);
        sub4.setDescription("Invalid ATS-message-filing-time");
        sub4.setAmhsDefault("recipient", defaults.get("recipient"));
        sub4.setAmhsDefault("subject", defaults.get("subject"));
        sub4.setAmhsDefault("priority", defaults.get("priority"));
        sub4.setAmhsDefault("filingTime", "INVALID");
        sub4.setAmhsDefault("content", defaults.get("content"));
        // Subcase 5 – empty ATS-message-header and no IHE
        TestSubcase sub5 = tc.getSubcases().get(4);
        sub5.setDescription("Empty header & no IHE");
        sub5.setAmhsDefault("recipient", defaults.get("recipient"));
        sub5.setAmhsDefault("subject", defaults.get("subject"));
        sub5.setAmhsDefault("priority", defaults.get("priority"));
        sub5.setAmhsDefault("header", ""); // empty header
        // No IHE field set
        sub5.setAmhsDefault("content", defaults.get("content"));
        // Subcase 6 – placeholder for future extensions (keep same defaults)
        TestSubcase sub6 = tc.getSubcases().get(5);
        sub6.setDescription("Default subcase");
        sub6.setAmhsDefault("recipient", defaults.get("recipient"));
        sub6.setAmhsDefault("subject", defaults.get("subject"));
        sub6.setAmhsDefault("priority", defaults.get("priority"));
        sub6.setAmhsDefault("content", defaults.get("content"));
        return tc;
    }

    private static TestCase createCTSW005() {
        // TODO: Replace with ICAO EUR Doc 047 CTSW005 specifications
        // Load defaults
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW005");
        TestCase tc = TestCaseLoader.createTestCase("CTSW005", "Large Message Content", 2);
        // Subcase 1 – latest-delivery-time in the past
        TestSubcase subPast = tc.getSubcases().get(0);
        subPast.setDescription("latest-delivery-time in the past");
        subPast.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "/CN=TestRecipient/OU=Test/O=TestOrg/PRMD=TestPRMD/ADMD=/C=US/"));
        subPast.setAmhsDefault("subject", defaults.getOrDefault("subject", "CTSW005 - Past Delivery"));
        subPast.setAmhsDefault("priority", defaults.getOrDefault("priority", "NORMAL"));
        // Use ISO-8601 UTC timestamp one day before now
        Instant past = Instant.now().minusSeconds(86400);
        subPast.setAmhsDefault("latestDeliveryTime", DateTimeFormatter.ISO_INSTANT.format(past));
        subPast.setAmhsDefault("content", defaults.getOrDefault("content", ""));
        // Subcase 2 – latest-delivery-time in the future
        TestSubcase subFuture = tc.getSubcases().get(1);
        subFuture.setDescription("latest-delivery-time in the future");
        subFuture.setAmhsDefault("recipient", defaults.get("recipient"));
        subFuture.setAmhsDefault("subject", defaults.get("subject"));
        subFuture.setAmhsDefault("priority", defaults.get("priority"));
        Instant future = Instant.now().plusSeconds(86400);
        subFuture.setAmhsDefault("latestDeliveryTime", DateTimeFormatter.ISO_INSTANT.format(future));
        subFuture.setAmhsDefault("content", defaults.get("content"));
        return tc;
    }

    private static TestCase createCTSW006() {
        // Load defaults
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW006");
        int maxSize = Integer.parseInt(defaults.getOrDefault("payloadSize", "2048"));
        TestCase tc = TestCaseLoader.createTestCase("CTSW006", "Special Characters in Content", 3);
        // Helper to generate filler payload
        java.util.function.BiFunction<Integer, Boolean, String> generatePayload = (size, exceed) -> {
            int finalSize = exceed ? size + 1 : size;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < finalSize; i++) {
                sb.append('A');
            }
            return sb.toString();
        };
        // Subcase 1 – payload within limit (IA5-text)
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("IA5-text payload within limit");
        sub1.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "/CN=TestRecipient/OU=Test/O=TestOrg/PRMD=TestPRMD/ADMD=/C=US/"));
        sub1.setAmhsDefault("subject", defaults.getOrDefault("subject", "CTSW006 - Within limit"));
        sub1.setAmhsDefault("priority", defaults.getOrDefault("priority", "NORMAL"));
        sub1.setAmhsDefault("payloadType", "IA5");
        sub1.setAmhsDefault("content", generatePayload.apply(maxSize, false));
        // Subcase 2 – IA5-text payload exceeding limit
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("IA5-text payload exceeding limit");
        sub2.setAmhsDefault("recipient", defaults.get("recipient"));
        sub2.setAmhsDefault("subject", defaults.get("subject"));
        sub2.setAmhsDefault("priority", defaults.get("priority"));
        sub2.setAmhsDefault("payloadType", "IA5");
        sub2.setAmhsDefault("content", generatePayload.apply(maxSize, true));
        // Subcase 3 – File‑transfer payload exceeding limit
        TestSubcase sub3 = tc.getSubcases().get(2);
        sub3.setDescription("File‑transfer payload exceeding limit");
        sub3.setAmhsDefault("recipient", defaults.get("recipient"));
        sub3.setAmhsDefault("subject", defaults.get("subject"));
        sub3.setAmhsDefault("priority", defaults.get("priority"));
        sub3.setAmhsDefault("payloadType", "FTBP");
        sub3.setAmhsDefault("content", generatePayload.apply(maxSize, true)); // placeholder binary data as string
        return tc;
    }

    private static TestCase createCTSW007() {
        // Load defaults for CTSW007
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW007");
        TestCase tc = TestCaseLoader.createTestCase("CTSW007", "Multiple Body Parts", 4);
        // Subcase 1 – basic ia5-text + FTBP
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("Basic ia5-text + FTBP");
        sub1.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "/CN=TestRecipient/OU=Test/O=TestOrg/PRMD=TestPRMD/ADMD=/C=US/"));
        sub1.setAmhsDefault("subject", defaults.getOrDefault("subject", "CTSW007 - Subcase 1"));
        sub1.setAmhsDefault("priority", defaults.getOrDefault("priority", "NORMAL"));
        sub1.setAmhsDefault("payloadType1", "IA5");
        sub1.setAmhsDefault("payloadType2", "FTBP");
        sub1.setAmhsDefault("content", defaults.getOrDefault("content", "IA5 text with file transfer payload"));
        // Subcase 2 – general-text + FTBP
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("General-text + FTBP");
        sub2.setAmhsDefault("recipient", defaults.get("recipient"));
        sub2.setAmhsDefault("subject", defaults.get("subject"));
        sub2.setAmhsDefault("priority", defaults.get("priority"));
        sub2.setAmhsDefault("payloadType1", "GENERAL_TEXT");
        sub2.setAmhsDefault("payloadType2", "FTBP");
        sub2.setAmhsDefault("content", defaults.getOrDefault("content", "General text with file transfer payload"));
        // Subcase 3 – two ia5-text body parts
        TestSubcase sub3 = tc.getSubcases().get(2);
        sub3.setDescription("Two ia5-text body parts");
        sub3.setAmhsDefault("recipient", defaults.get("recipient"));
        sub3.setAmhsDefault("subject", defaults.get("subject"));
        sub3.setAmhsDefault("priority", defaults.get("priority"));
        sub3.setAmhsDefault("payloadType1", "IA5");
        sub3.setAmhsDefault("payloadType2", "IA5");
        sub3.setAmhsDefault("content", defaults.getOrDefault("content", "Two IA5 text parts"));
        // Subcase 4 – ia5 + general-text + FTBP
        TestSubcase sub4 = tc.getSubcases().get(3);
        sub4.setDescription("ia5 + general-text + FTBP");
        sub4.setAmhsDefault("recipient", defaults.get("recipient"));
        sub4.setAmhsDefault("subject", defaults.get("subject"));
        sub4.setAmhsDefault("priority", defaults.get("priority"));
        sub4.setAmhsDefault("payloadType1", "IA5");
        sub4.setAmhsDefault("payloadType2", "GENERAL_TEXT");
        sub4.setAmhsDefault("payloadType3", "FTBP");
        sub4.setAmhsDefault("content", defaults.getOrDefault("content", "IA5, General text and file transfer parts"));
        return tc;
    }

    

    private static TestCase createCTSW008() {
        // Load defaults for CTSW008
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW008");
        TestCase tc = TestCaseLoader.createTestCase("CTSW008", "Content-Type Variations", 4);
        // Subcase 1 – interpersonal-messaging-1988(22)
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("interpersonal-messaging-1988(22)");
        sub1.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "/CN=TestRecipient/OU=Test/O=TestOrg/PRMD=TestPRMD/ADMD=/C=US/"));
        sub1.setAmhsDefault("subject", defaults.getOrDefault("subject", "CTSW008 - Subcase 1"));
        sub1.setAmhsDefault("priority", defaults.getOrDefault("priority", "NORMAL"));
        sub1.setAmhsDefault("contentType", "interpersonal-messaging-1988(22)");
        sub1.setAmhsDefault("content", defaults.getOrDefault("content", "Message with content type 1988"));
        // Subcase 2 – interpersonal-messaging-1984(2)
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("interpersonal-messaging-1984(2)");
        sub2.setAmhsDefault("recipient", defaults.get("recipient"));
        sub2.setAmhsDefault("subject", defaults.get("subject"));
        sub2.setAmhsDefault("priority", defaults.get("priority"));
        sub2.setAmhsDefault("contentType", "interpersonal-messaging-1984(2)");
        sub2.setAmhsDefault("content", defaults.getOrDefault("content", "Message with content type 1984"));
        // Subcase 3 – edi-messaging(35)
        TestSubcase sub3 = tc.getSubcases().get(2);
        sub3.setDescription("edi-messaging(35)");
        sub3.setAmhsDefault("recipient", defaults.get("recipient"));
        sub3.setAmhsDefault("subject", defaults.get("subject"));
        sub3.setAmhsDefault("priority", defaults.get("priority"));
        sub3.setAmhsDefault("contentType", "edi-messaging(35)");
        sub3.setAmhsDefault("content", defaults.getOrDefault("content", "EDI messaging content"));
        // Subcase 4 – unidentified(0)
        TestSubcase sub4 = tc.getSubcases().get(3);
        sub4.setDescription("unidentified(0)");
        sub4.setAmhsDefault("recipient", defaults.get("recipient"));
        sub4.setAmhsDefault("subject", defaults.get("subject"));
        sub4.setAmhsDefault("priority", defaults.get("priority"));
        sub4.setAmhsDefault("contentType", "unidentified(0)");
        sub4.setAmhsDefault("content", defaults.getOrDefault("content", "Unidentified content type"));
        return tc;
    }

    

    private static TestCase createCTSW009() {
        // Load defaults for CTSW009
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW009");
        TestCase tc = TestCaseLoader.createTestCase("CTSW009", "Multiple Recipients with Flags", 2);
        // Subcase 1 – Primary AMHS user + AMQP consumer, Copy AMHS + AMQP
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("Primary + Copy recipients");
        sub1.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "amhsUser1,amqpConsumer1,amhsCopy1,amqpCopy1"));
        sub1.setAmhsDefault("subject", defaults.getOrDefault("subject", "CTSW009 - Subcase 1"));
        sub1.setAmhsDefault("priority", defaults.getOrDefault("priority", "NORMAL"));
        sub1.setAmhsDefault("originatorReportRequest", "nondelivery-report");
        sub1.setAmhsDefault("content", defaults.getOrDefault("content", "Message with primary and copy recipients"));
        // Subcase 2 – Primary AMHS user + AMQP consumer, BCC AMHS + AMQP
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("Primary + BCC recipients");
        sub2.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "amhsUser2,amqpConsumer2,amhsBcc1,amqpBcc1"));
        sub2.setAmhsDefault("subject", defaults.getOrDefault("subject", "CTSW009 - Subcase 2"));
        sub2.setAmhsDefault("priority", defaults.getOrDefault("priority", "NORMAL"));
        sub2.setAmhsDefault("originatorReportRequest", "nondelivery-report");
        sub2.setAmhsDefault("content", defaults.getOrDefault("content", "Message with primary and BCC recipients"));
        return tc;
    }



    private static TestCase createCTSW010() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW010", "Non-Delivery Notification", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Test non-delivery notification");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW010 - Non-Delivery Test");
        sub.setAmhsDefault("priority", "NORMAL");
        sub.setAmhsDefault("content", "Non-delivery notification test.");
        return tc;
    }

    private static TestCase createCTSW011() {
        // Load defaults for CTSW011 (Probe series)
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW011");
        TestCase tc = TestCaseLoader.createTestCase("CTSW011", "Probe Series", 5);
        // Probe 1 – content-length below max, reachable AMQP consumer
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("Probe 1 – below max, reachable");
        sub1.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "reachableAmqpConsumer1"));
        sub1.setAmhsDefault("contentLength", defaults.getOrDefault("contentLength", "500"));
        sub1.setAmhsDefault("content", defaults.getOrDefault("content", "Probe payload below max"));
        // Probe 2 – below max, unmapped AMQP consumer
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("Probe 2 – below max, unmapped");
        sub2.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "unmappedAmqpConsumer"));
        sub2.setAmhsDefault("contentLength", defaults.getOrDefault("contentLength", "500"));
        sub2.setAmhsDefault("content", defaults.getOrDefault("content", "Probe payload unmapped"));
        // Probe 3 – content-length above max, reachable
        TestSubcase sub3 = tc.getSubcases().get(2);
        sub3.setDescription("Probe 3 – above max, reachable");
        sub3.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "reachableAmqpConsumer2"));
        sub3.setAmhsDefault("contentLength", defaults.getOrDefault("contentLength", "3000"));
        sub3.setAmhsDefault("content", defaults.getOrDefault("content", "Probe payload above max"));
        // Probe 4 – 512 recipients
        TestSubcase sub4 = tc.getSubcases().get(3);
        sub4.setDescription("Probe 4 – 512 recipients");
        sub4.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "recipientList512"));
        sub4.setAmhsDefault("contentLength", defaults.getOrDefault("contentLength", "500"));
        sub4.setAmhsDefault("content", defaults.getOrDefault("content", "Probe with 512 recipients"));
        // Probe 5 – >512 recipients
        TestSubcase sub5 = tc.getSubcases().get(4);
        sub5.setDescription("Probe 5 – >512 recipients");
        sub5.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "recipientList513"));
        sub5.setAmhsDefault("contentLength", defaults.getOrDefault("contentLength", "500"));
        sub5.setAmhsDefault("content", defaults.getOrDefault("content", "Probe with 513 recipients"));
        return tc;
    }



    private static TestCase createCTSW012() {
        // Load defaults for CTSW012 (Probe with mixed AMQP translation)
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW012");
        TestCase tc = TestCaseLoader.createTestCase("CTSW012", "Probe Mixed Translation", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Probe with one translatable and one unknown AMQP consumer");
        sub.setAmhsDefault("recipient", defaults.getOrDefault("recipient", "translatableAmqpConsumer,unknownAmqpConsumer"));
        sub.setAmhsDefault("content", defaults.getOrDefault("content", "Probe payload for mixed translation"));
        return tc;
    }



    private static TestCase createCTSW013() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW013", "Encrypted Message", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Test encrypted message");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW013 - Encrypted Message");
        sub.setAmhsDefault("priority", "NORMAL");
        sub.setAmhsDefault("content", "Encrypted message test.");
        return tc;
    }

    private static TestCase createCTSW014() {
        // Load defaults for CTSW014 (Return Notifications)
        java.util.Map<String, String> defaults = TestCaseConfigLoader.loadDefaults("CTSW014");
        TestCase tc = TestCaseLoader.createTestCase("CTSW014", "Return Notifications", 2);
        // Subcase 1 – RN for IPM with priority SS
        TestSubcase sub1 = tc.getSubcases().get(0);
        sub1.setDescription("RN for IPM priority SS");
        sub1.setAmhsDefault("originator", defaults.getOrDefault("originator", "amhsUser"));
        sub1.setAmhsDefault("subject", defaults.getOrDefault("subject", "RN for SS priority"));
        sub1.setAmhsDefault("priority", "SS");
        sub1.setAmhsDefault("content", defaults.getOrDefault("content", "Return Notification for SS priority message"));
        // Subcase 2 – RN for IPM with priority DD
        TestSubcase sub2 = tc.getSubcases().get(1);
        sub2.setDescription("RN for IPM priority DD");
        sub2.setAmhsDefault("originator", defaults.getOrDefault("originator", "amhsUser"));
        sub2.setAmhsDefault("subject", defaults.getOrDefault("subject", "RN for DD priority"));
        sub2.setAmhsDefault("priority", "DD");
        sub2.setAmhsDefault("content", defaults.getOrDefault("content", "Return Notification for DD priority message"));
        return tc;
    }

    

    private static TestCase createCTSW015() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW015", "RN without related subject message", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("RN with a fictitious subject IPM");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW015.1");
        sub.setAmhsDefault("message-type", "rn");
        sub.setAmhsDefault("subject-ipm-id", "99999999.9999*/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("content", "RN with fictitious subject IPM");
        return tc;
    }

    private static TestCase createCTSW016() {
        // CTSW016: Processing of the current encoded-information-types (EIT)
        // Loads all 12 subcases from CTSW016.properties via TestCaseConfigLoader
        List<TestCase> loaded = com.attech.amhs.ua.service.TestCaseConfigLoader.loadAllTestCases();
        for (TestCase tc : loaded) {
            if ("CTSW016".equals(tc.getId())) {
                return tc;
            }
        }
        // Fallback: build a minimal single-subcase in case loading fails
        TestCase tc = TestCaseLoader.createTestCase("CTSW016",
                "Processing of the current encoded-information-types (EIT)", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("EIT ia5-text(2) built-in");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW016.1");
        sub.setAmhsDefault("priority", "FF");
        sub.setAmhsDefault("content", "Test EIT ia5-text");
        sub.setAmhsDefault("body-part-type", "ia5-text");
        sub.setAmhsDefault("eit-type", "built-in");
        sub.setAmhsDefault("eit-builtin-value", "2");
        sub.setAmhsDefault("expected-result", "accepted");
        return tc;
    }

    private static TestCase createCTSW017() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW017", "Forward Message", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Test forward message");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "FWD: CTSW017 - Forward Test");
        sub.setAmhsDefault("priority", "NORMAL");
        sub.setAmhsDefault("content", "Forward message test.");
        return tc;
    }

    private static TestCase createCTSW018() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW018", "Message Recall", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Test message recall");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW018 - Message Recall");
        sub.setAmhsDefault("priority", "NORMAL");
        sub.setAmhsDefault("content", "Message recall test.");
        return tc;
    }

    private static TestCase createCTSW019() {
        // CTSW019: Incoming IPM with general-text-body-part and non-ISO 646
        List<TestCase> loaded = com.attech.amhs.ua.service.TestCaseConfigLoader.loadAllTestCases();
        for (TestCase tc : loaded) {
            if ("CTSW019".equals(tc.getId())) {
                return tc;
            }
        }
        TestCase tc = TestCaseLoader.createTestCase("CTSW019", "Urgent Message Handling", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Test urgent message handling");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW019 - URGENT MESSAGE");
        sub.setAmhsDefault("priority", "URGENT");
        sub.setAmhsDefault("content", "Urgent message handling test.");
        return tc;
    }

    private static TestCase createCTSW020() {
        TestCase tc = TestCaseLoader.createTestCase("CTSW020", "Mailbox Operations", 1);
        TestSubcase sub = tc.getSubcases().get(0);
        sub.setDescription("Test mailbox operations");
        sub.setAmhsDefault("recipient", "/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        sub.setAmhsDefault("subject", "CTSW020 - Mailbox Operations");
        sub.setAmhsDefault("priority", "NORMAL");
        sub.setAmhsDefault("content", "Mailbox operations test.");
        return tc;
    }
}
