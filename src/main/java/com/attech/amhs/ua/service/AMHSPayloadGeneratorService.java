package com.attech.amhs.ua.service;

import com.attech.amhs.ua.model.TestSubcase;
import com.isode.x400.highlevel.P3BindSession;
import com.isode.x400.highlevel.X400APIException;
import com.isode.x400.highlevel.X400Msg;
import com.isode.x400.highlevel.X400Msg.X400_Priority;
import com.isode.x400.highlevel.BodypartIA5Text;
import com.isode.x400.highlevel.BodypartGeneralText;
import com.isode.x400.highlevel.BodypartFTBP;
import com.isode.x400api.AMHS_att;
import com.isode.x400api.MSMessage;
import com.isode.x400api.Recip;
import com.isode.x400api.X400_att;
import com.isode.x400api.X400ms;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for generating AMHS X.400 message payloads with test case defaults
 * Supports all AMHS fields required by EUR Doc 047 Appendix A test cases
 */
public class AMHSPayloadGeneratorService {

    private static final DateTimeFormatter UTC_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'");
    private static final Pattern RELATIVE_UTC_TIME =
            Pattern.compile("(?i)^now(?:([+-])(\\d+)([dhms]))?$");

    /**
     * Default empty AMHS message fields for first boot
     * Includes all required and optional attributes per ICAO Doc 020
     */
    private static final Map<String, String> EMPTY_DEFAULTS = new HashMap<>();

    static {
        // Required attributes - Basic IPM
        EMPTY_DEFAULTS.put("recipient", "");
        EMPTY_DEFAULTS.put("subject", "");
        EMPTY_DEFAULTS.put("content", "");
        EMPTY_DEFAULTS.put("priority", "FF"); // Default to FF (Normal) ATS priority
        EMPTY_DEFAULTS.put("filing-time", ""); // Required for ATS messages

        // Required for Extended IPM
        EMPTY_DEFAULTS.put("precedence", "");
        EMPTY_DEFAULTS.put("authorization-time", "");

        // Body part configuration
        EMPTY_DEFAULTS.put("body-part-type", "ia5-text"); // Default to ia5-text
        EMPTY_DEFAULTS.put("payload-size", "");
        EMPTY_DEFAULTS.put("charset-reg-number", "");
        EMPTY_DEFAULTS.put("charset-repertoire", "");
        EMPTY_DEFAULTS.put("conversion-with-loss-prohibited", "");

        // Header fields (optional but commonly used)
        EMPTY_DEFAULTS.put("originator-reference", "");
        EMPTY_DEFAULTS.put("optional-heading-info", "");
        EMPTY_DEFAULTS.put("responsibility", "");
        EMPTY_DEFAULTS.put("notify-control-position", "");

        // EIT (Encoded Information Types)
        EMPTY_DEFAULTS.put("eit-type", "");
        EMPTY_DEFAULTS.put("eit-value", "");         // alias for built-in EIT integer value
        EMPTY_DEFAULTS.put("eit-builtin-value", ""); // primary key used by CTSW016 properties
        EMPTY_DEFAULTS.put("eit-oid", "");
        EMPTY_DEFAULTS.put("eit-oids", "");
        EMPTY_DEFAULTS.put("eit-builtin", "");       // legacy alias
        EMPTY_DEFAULTS.put("eit-authority", "");

        // FTBP (File Transfer Body Part)
        EMPTY_DEFAULTS.put("ftbp-file-name", "");
        EMPTY_DEFAULTS.put("ftbp-content", "");

        // Report configuration
        EMPTY_DEFAULTS.put("originator-report-request", "");
        EMPTY_DEFAULTS.put("originating-mta-report-request", "");

        // Timing attributes
        EMPTY_DEFAULTS.put("latest-delivery-time", "");

        // Subject IPM references
        EMPTY_DEFAULTS.put("subject-ipm-id", "");
        EMPTY_DEFAULTS.put("subject-ipm-priority", "");

        // Recipient lists
        EMPTY_DEFAULTS.put("primary-recipients", "");
        EMPTY_DEFAULTS.put("recipient-precedences", "");
        EMPTY_DEFAULTS.put("copy-recipients", "");
        EMPTY_DEFAULTS.put("bcc-recipients", "");
        EMPTY_DEFAULTS.put("recipient-file", "");

        // Additional charset support
        EMPTY_DEFAULTS.put("charset-reg-numbers", "");
        EMPTY_DEFAULTS.put("charset-reg-number", "");
        EMPTY_DEFAULTS.put("charset-source", "");
        EMPTY_DEFAULTS.put("repertoire", "");
        EMPTY_DEFAULTS.put("content-type", "");
        EMPTY_DEFAULTS.put("implicit-conversion", "");
        
        // Header empty flag
        EMPTY_DEFAULTS.put("header-empty", "");

        // Size validation
        EMPTY_DEFAULTS.put("exceeds-max-size", "");
        EMPTY_DEFAULTS.put("should-reject", "");

        // Multiple body parts support
        EMPTY_DEFAULTS.put("second-body-content", "");
    }

    private static final Logger logger = LoggerFactory.getLogger(AMHSPayloadGeneratorService.class);

    /**
     * Return an independent defaults map with relative time expressions resolved.
     * Supported latest-delivery-time values are now, now+/-Nd, now+/-Nh,
     * now+/-Nm and now+/-Ns. Literal values are preserved unchanged.
     */
    public Map<String, String> resolveDynamicAmhsDefaults(Map<String, String> amhsDefaults) {
        Map<String, String> resolved = amhsDefaults != null
                ? new HashMap<>(amhsDefaults) : new HashMap<>();
        String configuredTime = resolved.get("latest-delivery-time");
        if (configuredTime == null || configuredTime.trim().isEmpty()) {
            return resolved;
        }

        String expression = configuredTime.trim();
        Matcher matcher = RELATIVE_UTC_TIME.matcher(expression);
        if (!matcher.matches()) {
            if (expression.toLowerCase().startsWith("now")) {
                throw new IllegalArgumentException("Invalid relative latest-delivery-time: "
                        + configuredTime + ". Expected now, now-1d, now+1d, now+2h, now-30m, or now+15s.");
            }
            return resolved;
        }

        ZonedDateTime resolvedTime = ZonedDateTime.now(ZoneOffset.UTC);
        if (matcher.group(1) != null) {
            long amount = Long.parseLong(matcher.group(2));
            if ("-".equals(matcher.group(1))) {
                amount = -amount;
            }
            switch (matcher.group(3).toLowerCase()) {
                case "d":
                    resolvedTime = resolvedTime.plusDays(amount);
                    break;
                case "h":
                    resolvedTime = resolvedTime.plusHours(amount);
                    break;
                case "m":
                    resolvedTime = resolvedTime.plusMinutes(amount);
                    break;
                case "s":
                    resolvedTime = resolvedTime.plusSeconds(amount);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported latest-delivery-time unit: "
                            + matcher.group(3));
            }
        }

        String utcTime = UTC_TIME_FORMATTER.format(resolvedTime);
        resolved.put("latest-delivery-time", utcTime);
        logger.info("Resolved latest-delivery-time {} to {}", configuredTime, utcTime);
        return resolved;
    }

    /**
     * ATS Priority to X.400 Priority mapping
     */
    private static final Map<String, X400_Priority> ATS_PRIORITY_MAP = new HashMap<>();

    static {
        // Basic IPM priorities - map to available X.400 priorities
        // Note: HIGHEST_PRIORITY and NON_STANDARD_PRIORITY don't exist in the library
        // Using closest available equivalents
        ATS_PRIORITY_MAP.put("KK", X400_Priority.HIGH_PRIORITY); // Highest available
        ATS_PRIORITY_MAP.put("GG", X400_Priority.HIGH_PRIORITY);
        ATS_PRIORITY_MAP.put("FF", X400_Priority.NORMAL_PRIORITY);
        ATS_PRIORITY_MAP.put("DD", X400_Priority.LOW_PRIORITY);
        ATS_PRIORITY_MAP.put("SS", X400_Priority.HIGH_PRIORITY); // Special Service should be treated above normal

        // Standard X.400 priorities - only these 3 exist in the library
        ATS_PRIORITY_MAP.put("LOW", X400_Priority.LOW_PRIORITY);
        ATS_PRIORITY_MAP.put("NORMAL", X400_Priority.NORMAL_PRIORITY);
        ATS_PRIORITY_MAP.put("HIGH", X400_Priority.HIGH_PRIORITY);
        // URGENT doesn't exist, map to HIGH
        ATS_PRIORITY_MAP.put("URGENT", X400_Priority.HIGH_PRIORITY);
    }

    /**
     * Symbolic EIT OID name to dotted-decimal OID mapping (CTSW016).
     *
     * Based on STANAG 4406 / ACP 142 id-cs-eit-authority arc:
     *   id-cs-eit-authority = 2.16.840.1.101.2.1.22
     * Valid leaf values: 1 (ia5-text), 2 (general-text), 6 (FTBP), 100 (proprietary)
     * Invalid leaf values: 3, 4, 5, 7+ (not defined in EUR Doc 047 / ACP 142)
     *
     * id-eit-file-transfer 0 = OID for file-transfer body part EIT (same arc as authority 6)
     */
    private static final Map<String, String> EIT_OID_MAP = new HashMap<>();
    private static final Map<String, String> BUILTIN_EIT_MAP = new HashMap<>();

    static {
        // id-cs-eit-authority values (STANAG 4406 / ACP 142)
        EIT_OID_MAP.put("id-cs-eit-authority-1",   "2.16.840.1.101.2.1.22.1");  // ia5-text
        EIT_OID_MAP.put("id-cs-eit-authority-2",   "2.16.840.1.101.2.1.22.2");  // general-text
        EIT_OID_MAP.put("id-cs-eit-authority-3",   "2.16.840.1.101.2.1.22.3");  // Invalid (for CTSW016)
        EIT_OID_MAP.put("id-cs-eit-authority-4",   "2.16.840.1.101.2.1.22.4");  // Invalid (for CTSW016)
        EIT_OID_MAP.put("id-cs-eit-authority-6",   "2.16.840.1.101.2.1.22.6");  // FTBP
        EIT_OID_MAP.put("id-cs-eit-authority-7",   "2.16.840.1.101.2.1.22.7");  // Invalid (for CTSW016)
        EIT_OID_MAP.put("id-cs-eit-authority-100", "2.16.840.1.101.2.1.22.100"); // proprietary
        // File-transfer EIT (id-eit-file-transfer 0 per EUR Doc 047)
        EIT_OID_MAP.put("id-eit-file-transfer-0",  "2.16.840.1.101.2.1.22.6");  // maps to FTBP
        // The Isode string API requires symbolic EIT names. "undefined" is a
        // syntactically valid representation of the unsupported built-in value 0,
        // allowing CTSW016.2 to reach the IUT and be rejected there with an NDR.
        BUILTIN_EIT_MAP.put("0", "undefined");
        BUILTIN_EIT_MAP.put("2", "ia5-text");
    }

    /**
     * Resolve a symbolic EIT OID name to its dotted-decimal form.
     * If the name is already a dotted-decimal OID (e.g. "2.6.3.4.2"), return it unchanged.
     * If it is a recognised symbolic name (e.g. "id-cs-eit-authority-1"), look it up.
     * Unknown names are returned unchanged so the X.400 library can report the error.
     */
    private String resolveEitOid(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        String mapped = EIT_OID_MAP.get(trimmed);
        return mapped != null ? mapped : trimmed;
    }

    /**
     * Resolve a comma-separated list of EIT OID names/values to dotted-decimal OIDs.
     * Each token is resolved independently; returns a list of resolved OIDs.
     */
    private List<String> resolveEitOidList(String oidList) {
        List<String> result = new ArrayList<>();
        if (oidList == null || oidList.trim().isEmpty()) return result;
        for (String token : oidList.split("[,;]")) {
            String resolved = resolveEitOid(token.trim());
            if (resolved != null && !resolved.isEmpty()) {
                result.add(resolved);
            }
        }
        return result;
    }

    /** Apply CTSW016 current/original EIT values to an IPM or Probe envelope. */
    private void applyEncodedInformationTypes(X400Msg message, Map<String, String> amhsDefaults)
            throws X400APIException {
        if (amhsDefaults == null) {
            return;
        }

        String eitBuiltin = firstNonEmpty(amhsDefaults,
                "eit-builtin-value", "eit-value", "eit-builtin");
        String rawEitOid = firstNonEmpty(amhsDefaults, "eit-oid", "eit-oids");
        List<String> eitOidList = resolveEitOidList(rawEitOid);
        String eitAuth = firstNonEmpty(amhsDefaults, "eit-authority");
        if (eitBuiltin == null && eitOidList.isEmpty() && eitAuth == null) {
            return;
        }

        List<String> eitTokens = new ArrayList<>();
        if (eitBuiltin != null) {
            String encodedBuiltin = BUILTIN_EIT_MAP.get(eitBuiltin);
            if (encodedBuiltin == null) {
                encodedBuiltin = eitBuiltin;
            }
            eitTokens.add(encodedBuiltin);
            logger.info("[CTSW016] EIT built-in: configured={}, encoded={}",
                    eitBuiltin, encodedBuiltin);
        }
        eitTokens.addAll(eitOidList);
        if (eitAuth != null) {
            eitTokens.add(eitAuth);
        }

        String finalEit = String.join(" ", eitTokens);
        message.setStringparam(X400_att.X400_S_ORIGINAL_ENCODED_INFORMATION_TYPES, finalEit);
        logger.info("[CTSW016] Set X400_S_ORIGINAL_ENCODED_INFORMATION_TYPES='{}'", finalEit);
    }

    /**
     * Get default AMHS fields for a test subcase
     * If subcase has defaults, use them; otherwise use empty defaults
     * 
     * @param subcase TestSubcase with potential default values
     * @return Map of field names to values
     */
    public Map<String, String> getDefaults(TestSubcase subcase) {
        if (subcase == null || subcase.getAmhsDefaults().isEmpty()) {
            return new HashMap<>(EMPTY_DEFAULTS);
        }

        Map<String, String> defaults = new HashMap<>(EMPTY_DEFAULTS);
        defaults.putAll(subcase.getAmhsDefaults());
        return defaults;
    }

    /**
     * Get empty defaults for first boot or new message
     * 
     * @return Map of empty default fields
     */
    public Map<String, String> getEmptyDefaults() {
        return new HashMap<>(EMPTY_DEFAULTS);
    }

    private Map<String, String> normalizeAmhsDefaults(Map<String, String> rawDefaults) {
        System.out.println("=== normalizeAmhsDefaults START ===");
        System.out.println("rawDefaults: " + rawDefaults);
        if (rawDefaults == null) {
            System.out.println("rawDefaults is null!");
            return null;
        }
        Map<String, String> normalized = new HashMap<>();
        // First, trim all values
        for (Map.Entry<String, String> entry : rawDefaults.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue().trim());
        }

        if (!normalized.containsKey("priority")) {
            putIfNotEmpty(normalized, "priority", rawDefaults.get("ats-priority"));
            putIfNotEmpty(normalized, "priority", rawDefaults.get("priority-indicator"));
            putIfNotEmpty(normalized, "priority", rawDefaults.get("subject-message-priority"));
        }
        if (!normalized.containsKey("bcc-recipients")) {
            putIfNotEmpty(normalized, "bcc-recipients", rawDefaults.get("blind-copy-recipients"));
        }
        // Handle charset-reg-numbers (plural) -> charset-reg-number (singular) mapping
        // This is critical for CTSW001 subcases 6-11 which use charset-reg-numbers=1,6
        if (!normalized.containsKey("charset-reg-number")) {
            putIfNotEmpty(normalized, "charset-reg-number", rawDefaults.get("charset-reg-numbers"));
        }
        // Also handle repertoire -> charset-repertoire mapping
        if (!normalized.containsKey("charset-repertoire")) {
            putIfNotEmpty(normalized, "charset-repertoire", rawDefaults.get("repertoire"));
        }
        // Handle body-part-types (plural) -> body-part-type (singular) mapping
        if (!normalized.containsKey("body-part-type")) {
            putIfNotEmpty(normalized, "body-part-type", rawDefaults.get("body-part-types"));
        }
        // Support both singular and plural property names for EIT OIDs
        if (!normalized.containsKey("eit-oid")) {
            putIfNotEmpty(normalized, "eit-oid", rawDefaults.get("eit-oids"));
        }
        if (!normalized.containsKey("primary-recipients")) {
            putIfNotEmpty(normalized, "primary-recipients", rawDefaults.get("recipient"));
        }
        if (!normalized.containsKey("probe") && "probe".equalsIgnoreCase(rawDefaults.get("message-type"))) {
            normalized.put("probe", rawDefaults.getOrDefault("probe", "probe"));
        }
        if (!normalized.containsKey("rn") && "rn".equalsIgnoreCase(rawDefaults.get("message-type"))) {
            normalized.put("rn", rawDefaults.getOrDefault("rn", "rn"));
        }
        if (!normalized.containsKey("conversion-with-loss-prohibited")) {
            putIfNotEmpty(normalized, "conversion-with-loss-prohibited", rawDefaults.get("conversion-with-loss"));
        }
        if (!normalized.containsKey("originator")) {
            putIfNotEmpty(normalized, "originator", rawDefaults.get("originator"));
        }
        System.out.println("normalized: " + normalized);
        System.out.println("=== normalizeAmhsDefaults END ===");
        return normalized;
    }

    private void putIfNotEmpty(Map<String, String> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.putIfAbsent(key, value.trim());
        }
    }

    private String firstNonEmpty(Map<String, String> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                String value = map.get(key);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseNonNegativeIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int resolveProbeContentLength(Map<String, String> amhsDefaults, String fallbackContent) {
        String configured = amhsDefaults != null ? amhsDefaults.get("content-length") : null;
        if (configured == null || configured.trim().isEmpty()) {
            return fallbackContent != null ? fallbackContent.getBytes(StandardCharsets.UTF_8).length : 0;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            if (parsed < 0 || parsed > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Probe content-length must be between 0 and " + Integer.MAX_VALUE + " octets: " + configured);
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Probe content-length must be a numeric octet count: " + configured, e);
        }
    }

    private List<String> splitRecipients(String recipientString) {
        List<String> recipients = new ArrayList<>();
        if (recipientString == null || recipientString.trim().isEmpty()) {
            return recipients;
        }
        for (String recip : recipientString.split("[;,]")) {
            String trimmed = recip.trim();
            if (!trimmed.isEmpty()) {
                recipients.add(trimmed);
            }
        }
        return recipients;
    }

    /**
     * Generate deterministic test O/R addresses for recipient-limit test cases.
     * CN contains exactly eight uppercase alphabetic characters. Addresses
     * alternate between the VVTS and VVNB organizational units.
     */
    private List<String> generateUniqueTestRecipients(int count) {
        List<String> recipients = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String ou = (i % 2 == 0) ? "VVTS" : "VVNB";
            int suffixValue = i / 2;
            char[] suffix = new char[4];
            for (int position = suffix.length - 1; position >= 0; position--) {
                suffix[position] = (char) ('A' + (suffixValue % 26));
                suffixValue /= 26;
            }
            String cn = ou + new String(suffix);
            recipients.add("/CN=" + cn + "/OU=" + ou
                    + "/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        }
        return recipients;
    }

    private List<String> resolveToRecipients(Map<String, String> amhsDefaults, String defaultRecipient) {
        String recipientsValue = firstNonEmpty(amhsDefaults, "primary-recipients", "recipient");
        if (recipientsValue == null || recipientsValue.trim().isEmpty()) {
            recipientsValue = defaultRecipient;
        }
        List<String> recipients = splitRecipients(recipientsValue);

        int count = parseIntOrDefault(amhsDefaults != null ? amhsDefaults.get("recipient-count") : null,
                recipients.size());
        if (count <= 0) {
            count = recipients.isEmpty() ? 1 : recipients.size();
        }

        boolean generateUniqueRecipients = amhsDefaults != null
                && "true".equalsIgnoreCase(amhsDefaults.get("generate-unique-recipients"));
        if (generateUniqueRecipients) {
            List<String> generatedRecipients = generateUniqueTestRecipients(count);
            logger.info("Generated unique test recipients: requested={}, generated={}",
                    count, generatedRecipients.size());
            return generatedRecipients;
        }

        if (recipients.isEmpty() && defaultRecipient != null && !defaultRecipient.trim().isEmpty()) {
            recipients.add(defaultRecipient.trim());
        }

        // Duplicate existing valid addresses to satisfy count, instead of generating
        // new CNs
        // which might exceed the 8-character AFTN limit and cause "Submission error"
        if (recipients.size() > 0) {
            int originalSize = recipients.size();
            for (int i = originalSize; i < count; i++) {
                recipients.add(recipients.get(i % originalSize));
            }
        }

        return recipients;
    }

    private List<String> resolvePerRecipientValues(Map<String, String> amhsDefaults,
            String key, String fallbackKey, int recipientCount) throws X400APIException {
        String configured = amhsDefaults != null ? amhsDefaults.get(key) : null;
        if ((configured == null || configured.trim().isEmpty()) && fallbackKey != null) {
            configured = amhsDefaults != null ? amhsDefaults.get(fallbackKey) : null;
        }
        List<String> values = new ArrayList<>();
        if (configured == null || configured.trim().isEmpty()) {
            return values;
        }
        for (String value : configured.split(",")) {
            if (!value.trim().isEmpty()) {
                values.add(value.trim());
            }
        }
        if (values.size() == 1 && recipientCount > 1) {
            String repeated = values.get(0);
            while (values.size() < recipientCount) {
                values.add(repeated);
            }
        }
        if (values.size() != recipientCount) {
            throw new X400APIException(key + " count " + values.size()
                    + " does not match primary recipient count " + recipientCount);
        }
        return values;
    }

    private int parseRecipientPrecedence(String configured, int recipientIndex)
            throws X400APIException {
        try {
            return Integer.parseInt(configured);
        } catch (NumberFormatException e) {
            throw new X400APIException("Invalid precedence for primary recipient "
                    + (recipientIndex + 1) + ": " + configured);
        }
    }

    private int parseRecipientResponsibility(String configured, int recipientIndex)
            throws X400APIException {
        if ("responsible".equalsIgnoreCase(configured) || "1".equals(configured)) {
            return 1;
        }
        if ("non-responsible".equalsIgnoreCase(configured)
                || "not-responsible".equalsIgnoreCase(configured)
                || "0".equals(configured)) {
            return 0;
        }
        throw new X400APIException("Invalid responsibility for primary recipient "
                + (recipientIndex + 1) + ": " + configured);
    }

    private void addRecipientIntParam(Recip recipient, int attribute, int value,
            String attributeName, int recipientIndex) throws X400APIException {
        int status = X400ms.x400_ms_recipaddintparam(recipient, attribute, value);
        if (status != 0) {
            throw new X400APIException(attributeName + " for primary recipient "
                    + (recipientIndex + 1) + ": x400_ms_recipaddintparam failed", status);
        }
    }

    private String padContent(String content, int length) {
        if (length <= 0) {
            return content != null ? content : "";
        }
        StringBuilder builder = new StringBuilder(content != null ? content : "");
        while (builder.length() < length) {
            builder.append('X');
        }
        if (builder.length() > length) {
            return builder.substring(0, length);
        }
        return builder.toString();
    }

    private String resolveBodyContent(Map<String, String> amhsDefaults, String fallbackContent) {
        String baseContent = firstNonEmpty(amhsDefaults, "content");
        if (baseContent == null) {
            baseContent = fallbackContent != null ? fallbackContent : "";
        }

        String contentLength = amhsDefaults != null ? amhsDefaults.get("content-length") : null;
        if (contentLength != null && !contentLength.trim().isEmpty()) {
            int length = parseIntOrDefault(contentLength, baseContent.length());
            return padContent(baseContent, length);
        }

        String payloadSize = amhsDefaults != null ? amhsDefaults.get("payload-size") : null;
        if (payloadSize != null && !payloadSize.trim().isEmpty()) {
            String configuredSize = payloadSize.trim();
            int numericSize = parseIntOrDefault(configuredSize, -1);
            if (numericSize > 0) {
                return padContent(baseContent, numericSize);
            }
            // Backward compatibility for existing testcase configurations.
            if ("exceed-limit".equalsIgnoreCase(configuredSize)) {
                return padContent(baseContent, 4096);
            }
            if ("within-limit".equalsIgnoreCase(configuredSize)) {
                return padContent(baseContent, 1024);
            }
        }

        return baseContent;
    }

    private List<String> parseBodyPartTypes(String bodyPartTypeSpec) {
        List<String> types = new ArrayList<>();
        if (bodyPartTypeSpec == null || bodyPartTypeSpec.trim().isEmpty()) {
            types.add("ia5-text");
            return types;
        }
        for (String type : bodyPartTypeSpec.split("[;,]")) {
            String normalized = type.trim();
            if (!normalized.isEmpty()) {
                types.add(normalized);
            }
        }
        return types.isEmpty() ? Arrays.asList("ia5-text") : types;
    }

    private List<String> buildBodyContents(Map<String, String> amhsDefaults, int count, String defaultContent) {
        List<String> contents = new ArrayList<>();
        String firstContent = resolveBodyContent(amhsDefaults, defaultContent);
        contents.add(firstContent);
        if (count > 1) {
            String secondContent = amhsDefaults != null ? amhsDefaults.getOrDefault("second-body-content", firstContent)
                    : firstContent;
            contents.add(secondContent == null ? firstContent : secondContent);
        }
        for (int i = 2; i < count; i++) {
            contents.add(firstContent);
        }
        return contents;
    }

    public X400Msg buildX400Message(P3BindSession session, String recipient, String subject,
            String content, String priority,
            Map<String, String> amhsDefaults) {
        return buildX400Message(session, recipient, subject, content, priority, amhsDefaults, null);
    }

    private int getIpnRequest(Map<String, String> amhsDefaults) {
        if (amhsDefaults == null)
            return X400Msg.IPN_NON_RECEIPT_NOTIFICATION;
        String req = amhsDefaults.get("receipt-notification");
        if (req != null) {
            String lower = req.toLowerCase().trim();
            if (lower.equals("receipt-notification") || lower.equals("rn") || lower.equals("1")) {
                return X400Msg.IPN_RECEIPT_NOTIFICATION;
            } else if (lower.equals("none") || lower.equals("0")) {
                return 0; // IPN_NONE or default
            } else if (lower.equals("non-receipt-notification") || lower.equals("nrn") || lower.equals("2")) {
                return X400Msg.IPN_NON_RECEIPT_NOTIFICATION;
            } else if (lower.equals("both") || lower.equals("3")) {
                return X400Msg.IPN_NON_RECEIPT_NOTIFICATION | X400Msg.IPN_RECEIPT_NOTIFICATION;
            }
        }
        return X400Msg.IPN_NON_RECEIPT_NOTIFICATION;
    }

    private X400Msg.DR_Request getDrRequest(Map<String, String> amhsDefaults) {
        if (amhsDefaults == null)
            return X400Msg.DR_Request.DR_NON_DELIVERY_REPORT;
        String req = amhsDefaults.get("originator-report-request");
        if (req != null) {
            String lower = req.toLowerCase().trim();
            if (lower.equals("report") || lower.equals("delivery-report") || lower.equals("dr") || lower.equals("both")
                    || lower.equals("3") || lower.equals("2") || lower.equals("audited-report")) {
                return X400Msg.DR_Request.DR_DELIVERY_REPORT;
            } else if (lower.equals("none") || lower.equals("0") || lower.equals("no-report")) {
                return X400Msg.DR_Request.DR_NO_REPORT;
            } else if (lower.equals("non-delivery-report") || lower.equals("ndr") || lower.equals("1")) {
                return X400Msg.DR_Request.DR_NON_DELIVERY_REPORT;
            }
        }
        return X400Msg.DR_Request.DR_NON_DELIVERY_REPORT;
    }

    private Integer getMtaReportRequest(Map<String, String> amhsDefaults) throws X400APIException {
        if (amhsDefaults == null) {
            return null;
        }
        String configured = amhsDefaults.get("originating-mta-report-request");
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }

        String request = configured.trim().toLowerCase();
        if (request.equals("no-report") || request.equals("none") || request.equals("0")) {
            return 0;
        }
        if (request.equals("non-delivery-report") || request.equals("ndr") || request.equals("1")) {
            return 1;
        }
        if (request.equals("delivery-report") || request.equals("dr") || request.equals("report")
                || request.equals("both") || request.equals("2")) {
            return 2;
        }
        if (request.equals("audited-report") || request.equals("3")) {
            return 3;
        }
        throw new X400APIException("Invalid originating-mta-report-request: " + configured);
    }

    /**
     * Apply AMHS fields excluding body part and filing-time.
     * Those two are handled directly in buildX400Message to ensure
     * they are set exactly once and in the correct order.
     */
    private void applyAmhsFieldsExceptBodyAndFilingTime(X400Msg message, Map<String, String> amhsDefaults)
            throws X400APIException {

        // Handle RN/IPN
        if (amhsDefaults != null && amhsDefaults.containsKey("rn")) {
            message.setIntParam(X400_att.X400_N_IS_IPN, 1);
            String subjectIpmId = amhsDefaults.get("subject-ipm-id");
            if (subjectIpmId != null && !subjectIpmId.trim().isEmpty()) {
                message.setStringparam(X400_att.X400_S_SUBJECT_IPM, subjectIpmId.trim());
            }
        }

        X400Msg.DR_Request drRequest = getDrRequest(amhsDefaults);

        // Optional Heading Info
        String ohi = amhsDefaults.get("optional-heading-info");
        if (ohi != null && !ohi.trim().isEmpty()) {
            try {
                message.setStringparam(AMHS_att.ATS_S_OPTIONAL_HEADING_INFO, ohi.trim());
            } catch (Exception e) {
                logger.warn("Failed to set optional-heading-info: {}", e.getMessage());
            }
        }

        // ATS Header raw injection (best-effort mapping to optional heading info)
        String atsHeader = amhsDefaults.get("ats-header");
        if (atsHeader != null && !atsHeader.trim().isEmpty()) {
            try {
                String currentOhi = amhsDefaults.getOrDefault("optional-heading-info", "");
                String combinedHeader = currentOhi.isEmpty() ? atsHeader.trim() : currentOhi + " " + atsHeader.trim();
                message.setStringparam(AMHS_att.ATS_S_OPTIONAL_HEADING_INFO, combinedHeader);
            } catch (Exception e) {
                logger.warn("Failed to map ats-header to ATS_S_OPTIONAL_HEADING_INFO: {}", e.getMessage());
            }
        }

        // Originator Reference
        String originatorRef = amhsDefaults.get("originator-reference");
        if (originatorRef != null && !originatorRef.trim().isEmpty()) {
            try {
                String currentOhi = amhsDefaults.getOrDefault("optional-heading-info", "");
                String newOhi = (currentOhi.isEmpty() ? "" : currentOhi + " ") + "REF:" + originatorRef.trim();
                message.setStringparam(AMHS_att.ATS_S_OPTIONAL_HEADING_INFO, newOhi);
            } catch (Exception e) {
                logger.warn("Failed to set originator-reference: {}", e.getMessage());
            }
        }

        // Originator (CTSW013)
        String originator = amhsDefaults.get("originator");
        if (originator != null && !originator.trim().isEmpty()) {
            try {
                message.setFrom(originator.trim());
            } catch (Exception e) {
                logger.warn("Failed to set originator: {}", e.getMessage());
            }
        }

        // Authorization Time
        String authTime = amhsDefaults.get("authorization-time");
        if (authTime != null && !authTime.trim().isEmpty()) {
            try {
                message.setStringparam(X400_att.X400_S_AUTHORIZATION_TIME, authTime.trim());
            } catch (Exception e) {
                logger.warn("Failed to set authorization-time: {}", e.getMessage());
            }
        }

        // Latest Delivery Time
        String latestDelivery = amhsDefaults.get("latest-delivery-time");
        if (latestDelivery != null && !latestDelivery.trim().isEmpty()) {
            try {
                message.setStringparam(X400_att.X400_S_LATEST_DELIVERY_TIME, latestDelivery.trim());
            } catch (Exception e) {
                logger.warn("Failed to set latest-delivery-time: {}", e.getMessage());
            }
        }

        // Subject IPM ID handling removed from here to prevent conflicting with its proper usage in the RN block.
        // The IPM identifier of the *current* message shouldn't blindly take the subject-ipm-id value.
        // It's handled correctly as X400_S_SUBJECT_IPM for RNs earlier in this method.

        // CC Recipients
        String ccRecipients = amhsDefaults.get("copy-recipients");
        if (ccRecipients != null && !ccRecipients.trim().isEmpty()) {
            for (String recip : ccRecipients.split("[;,]")) {
                String trimmed = recip.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        message.setCc(trimmed,
                                drRequest,
                                X400Msg.IPN_NON_RECEIPT_NOTIFICATION);
                    } catch (Exception e) {
                        logger.warn("Failed to set CC recipient {}: {}", trimmed, e.getMessage());
                    }
                }
            }
        }

        // BCC Recipients
        String bccRecipients = amhsDefaults.get("bcc-recipients");
        if (bccRecipients != null && !bccRecipients.trim().isEmpty()) {
            for (String recip : bccRecipients.split("[;,]")) {
                String trimmed = recip.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        message.setBcc(trimmed,
                                drRequest,
                                X400Msg.IPN_NON_RECEIPT_NOTIFICATION);
                    } catch (Exception e) {
                        logger.warn("Failed to set BCC recipient {}: {}", trimmed, e.getMessage());
                    }
                }
            }
        }

        // GeneralText charset attribute 802 belongs to the body part. It is applied
        // once by addBodyPart; setting it on X400Msg creates an invalid native object.
        String implicitConversion = amhsDefaults.get("implicit-conversion");
        if (implicitConversion != null && !implicitConversion.trim().isEmpty()) {
            int value = "allowed".equalsIgnoreCase(implicitConversion.trim()) ? 0 : 1;
            message.setIntParam(X400_att.X400_N_IMPLICIT_CONVERSION_PROHIBITED, value);
        }

        applyEncodedInformationTypes(message, amhsDefaults);

        // DR, RN and originating-MTA requests are per-recipient attributes. They
        // are applied while each recipient is created in buildX400Message().
    }

    /**
     * Build X.400 message from parameters with full AMHS field support
     * 
     * @param session      P3BindSession
     * @param recipient    Recipient address
     * @param subject      Message subject
     * @param content      Message content
     * @param priority     Message priority (ATS codes: KK/GG/FF/DD/SS or X.400:
     *                     LOW/NORMAL/HIGH/URGENT)
     * @param amhsDefaults Additional AMHS fields from test case configuration
     * @return Built X.400 message
     */
    public X400Msg buildX400Message(P3BindSession session, String recipient, String subject,
            String content, String priority,
            Map<String, String> amhsDefaults, String filingTime) {

        dumpMessageAttributes(recipient, subject, content, priority, amhsDefaults);

        X400Msg message = new X400Msg(session);
        // normalizing fields
        Map<String, String> normalizedDefaults = normalizeAmhsDefaults(amhsDefaults);

        try {
            // ── RECIPIENT(S) (mandatory) ───────────────────────────────────────
            List<String> toRecipients = resolveToRecipients(normalizedDefaults, recipient);
            if (toRecipients.isEmpty()) {
                throw new RuntimeException("Recipient address is required and cannot be empty");
            }
            // get delivery report
            X400Msg.DR_Request drRequest = getDrRequest(normalizedDefaults);
            int ipnRequest = getIpnRequest(normalizedDefaults);
            Integer mtaReportRequest = getMtaReportRequest(normalizedDefaults);
            List<String> recipientPrecedences = resolvePerRecipientValues(normalizedDefaults,
                    "recipient-precedences", "precedence", toRecipients.size());
            List<String> recipientResponsibilities = resolvePerRecipientValues(normalizedDefaults,
                    "responsibility", null, toRecipients.size());
            for (int i = 0; i < toRecipients.size(); i++) {
                Recip recipientObject = message.setTo(toRecipients.get(i), drRequest, ipnRequest);
                if (mtaReportRequest != null) {
                    addRecipientIntParam(recipientObject, X400_att.X400_N_MTA_REPORT_REQUEST,
                            mtaReportRequest, "originating MTA report request", i);
                }
                if (!recipientPrecedences.isEmpty()) {
                    int precedence = parseRecipientPrecedence(recipientPrecedences.get(i), i);
                    addRecipientIntParam(recipientObject, X400_att.X400_N_PRECEDENCE,
                            precedence, "precedence", i);
                }
                if (!recipientResponsibilities.isEmpty()) {
                    int responsibility = parseRecipientResponsibility(recipientResponsibilities.get(i), i);
                    addRecipientIntParam(recipientObject, X400_att.X400_N_RESPONSIBILITY,
                            responsibility, "responsibility", i);
                }
                logger.info("Primary recipient {} configured: address={}, precedence={}, responsibility={}",
                        i + 1, toRecipients.get(i),
                        recipientPrecedences.isEmpty() ? "unset" : recipientPrecedences.get(i),
                        recipientResponsibilities.isEmpty() ? "unset" : recipientResponsibilities.get(i));
            }

            // ── SUBJECT ──────────────────────────────────────────────────────
            if (subject != null && !subject.trim().isEmpty()) {
                message.setSubject(subject.trim());
            }

            // ── PRIORITY ─────────────────────────────────────────────────────
            String effectivePriority = firstNonEmpty(normalizedDefaults,
                    "priority", "ats-priority", "priority-indicator", "subject-message-priority");
            if ((effectivePriority == null || effectivePriority.trim().isEmpty()) && priority != null) {
                effectivePriority = priority;
            }
            int t = effectivePriority.length();
            X400_Priority priorityLevel = X400_Priority.NORMAL_PRIORITY; //getPriorityFromString(effectivePriority);

            message.setPriority(priorityLevel);

            // ── BODY PART(S) ──────────────────────────────────────────────────
            String safeContent = resolveBodyContent(normalizedDefaults, content);
            String bodyPartTypeSpec = firstNonEmpty(normalizedDefaults, "body-part-types", "body-part-type");
            addBodyParts(message, bodyPartTypeSpec, safeContent, normalizedDefaults);

            // ── FILING TIME (set exactly once, always) ───────────────────────
            String ft;
            if (filingTime != null && !filingTime.trim().isEmpty()) {
                ft = filingTime.trim();
            } else {
                ft = resolveFilingTime(normalizedDefaults);
            }
            message.setStringparam(AMHS_att.ATS_S_FILING_TIME, ft);
            logger.debug("Set filing-time: {}", ft);

            String atsPriority = normalizedDefaults.containsKey("ats-priority")
                    ? normalizedDefaults.get("ats-priority")
                    : firstNonEmpty(normalizedDefaults, "priority-indicator", "subject-message-priority");
            // When per-recipient precedence is present, the receiving IUT must derive
            // its AMQP priority and amhs_ats_pri from the highest precedence. Do not
            // mask that behavior by copying the GUI/default ATS priority into the IPM.
            // X.400 enum names such as NORMAL_PRIORITY are not valid ATS priority
            // indicators. Set this attribute only when an ATS value is explicit.
            if (atsPriority != null && !atsPriority.trim().isEmpty()) {
                message.setStringparam(AMHS_att.ATS_S_PRIORITY_INDICATOR, atsPriority.trim());
            }

            String extended = normalizedDefaults != null ? normalizedDefaults.get("extended") : null;
            message.setIntParam(AMHS_att.ATS_N_EXTENDED, "true".equalsIgnoreCase(extended) ? 1 : 0);

            int contentType = parseNonNegativeIntOrDefault(normalizedDefaults.get("content-type"), 22);
            message.setIntParam(X400_att.X400_N_CONTENT_TYPE, contentType);
            message.setStringparam(AMHS_att.ATS_S_TEXT, safeContent);

            if (normalizedDefaults != null) {
                String expirationTime = normalizedDefaults.get("expiration-time");
                if (expirationTime != null && !expirationTime.trim().isEmpty()) {
                    message.setStringparam(X400_att.X400_S_EXPIRY_TIME, expirationTime.trim());
                }
            }

            // ── REMAINING AMHS FIELDS (everything except body part and filing-time) ──
            if (normalizedDefaults != null && !normalizedDefaults.isEmpty()) {
                applyAmhsFieldsExceptBodyAndFilingTime(message, normalizedDefaults);
            }

        } catch (X400APIException e) {
            logger.error("Error building X.400 message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build X.400 message: " + e.getMessage(), e);
        }

        return message;
    }

    /**
     * Build X.400 message from TestSubcase with all configured AMHS fields
     * 
     * @param session P3BindSession
     * @param subcase TestSubcase with complete AMHS configuration
     * @return Built X.400 message
     */
    public X400Msg buildX400MessageFromSubcase(P3BindSession session, TestSubcase subcase) {
        Map<String, String> defaults = getDefaults(subcase);

        String recipient = defaults.get("recipient");
        String subject = defaults.get("subject");
        String content = defaults.get("content");
        String priority = defaults.get("priority");

        return buildX400Message(session, recipient, subject, content, priority, defaults);
    }

    /**
     * Build probe X400 message from TestSubcase with all configured AMHS fields
     * 
     * @param session P3BindSession
     * @param subcase TestSubcase with complete AMHS configuration
     * @return Built X.400 probe message
     */
    public X400Msg buildProbeMessageFromSubcase(P3BindSession session, TestSubcase subcase) {
        Map<String, String> defaults = getDefaults(subcase);

        String recipient = defaults.get("recipient");
        String subject = defaults.get("subject");
        String content = defaults.get("content");
        String priority = defaults.get("priority");

        // Mark as probe
        defaults.put("probe", "true");

        return buildProbeMessage(session, recipient, subject, content, priority, defaults, null);
    }

    /**
     * Apply AMHS-specific fields to X.400 message
     * Full implementation per ICAO Doc 020 and EUR Doc 047 requirements
     * 
     * @param message      X400Msg to configure
     * @param content      Message content
     * @param amhsDefaults Map of AMHS field names to values
     */
    private void applyAmhsFields(X400Msg message, String content, Map<String, String> amhsDefaults)
            throws X400APIException {
        // Handle body part type and content - support all required types
        String bodyPartType = amhsDefaults.get("body-part-type");

        // Default to ia5-text if not specified
        if (bodyPartType == null || bodyPartType.trim().isEmpty()) {
            bodyPartType = "ia5-text";
        }

        // Use content from parameter, fallback to defaults map if empty
        String effectiveContent = content;
        if (effectiveContent == null || effectiveContent.isEmpty()) {
            effectiveContent = amhsDefaults.get("content");
        }

        // Add body parts based on type specification
        addBodyParts(message, bodyPartType, effectiveContent, amhsDefaults);

        // === ATS MESSAGE HEADER ATTRIBUTES (Required for ATS messages) ===

        // Filing Time - Required for ATS messages (format: YYMMDDHHMM)
        String filingTime = resolveFilingTime(amhsDefaults);
        try {
            message.setStringparam(AMHS_att.ATS_S_FILING_TIME, filingTime);
            logger.debug("Set filing-time: {}", filingTime);
        } catch (Exception e) {
            logger.warn("Failed to set filing-time: {}", e.getMessage());
        }

        // Optional Heading Info (OHI)
        String ohi = amhsDefaults.get("optional-heading-info");
        if (ohi != null && !ohi.trim().isEmpty()) {
            try {
                message.setStringparam(AMHS_att.ATS_S_OPTIONAL_HEADING_INFO, ohi.trim());
                logger.debug("Set optional-heading-info: {}", ohi);
            } catch (Exception e) {
                logger.warn("Failed to set optional-heading-info: {}", e.getMessage());
            }
        }

        // Originator Reference
        String originatorRef = amhsDefaults.get("originator-reference");
        if (originatorRef != null && !originatorRef.trim().isEmpty()) {
            try {
                // Store in OHI if separate field not available
                String currentOhi = amhsDefaults.get("optional-heading-info");
                String newOhi = (currentOhi != null ? currentOhi + " " : "") + "REF:" + originatorRef.trim();
                message.setStringparam(AMHS_att.ATS_S_OPTIONAL_HEADING_INFO, newOhi);
                logger.debug("Set originator-reference via OHI: {}", originatorRef);
            } catch (Exception e) {
                logger.warn("Failed to set originator-reference: {}", e.getMessage());
            }
        }

        // === EXTENDED IPM ATTRIBUTES ===

        // Authorization Time - For extended IPMs
        String authTime = amhsDefaults.get("authorization-time");
        if (authTime != null && !authTime.trim().isEmpty()) {
            try {
                message.setStringparam(X400_att.X400_S_AUTHORIZATION_TIME, authTime.trim());
                logger.debug("Set authorization-time: {}", authTime);
            } catch (Exception e) {
                logger.warn("Failed to set authorization-time: {}", e.getMessage());
            }
        }

        // === PROBE SUPPORT (CTSW011-013) ===
        // NOTE: The current Isode library does not expose a dedicated probe API.
        // Probe messages are configured using an ATS_N_EXTENDED flag and a special
        // subject identifier in the message. When the 'probe' key is present,
        // the message is tagged via the extended flag and the probe identifier is
        // embedded in the content identifier for gateway routing purposes.
        String probeId = amhsDefaults.get("probe");
        if (probeId != null && !probeId.trim().isEmpty()) {
            try {
                // Mark message as extended (required for probe content type signalling)
                message.setIntParam(AMHS_att.ATS_N_EXTENDED, -1);
                // Embed probe id in the subject-identifier field
                message.setStringparam(X400_att.X400_S_SUBJECT_IDENTIFIER, probeId.trim());
                logger.debug("Probe configured - id: {}, extended flag set", probeId);
            } catch (Exception e) {
                logger.warn("Failed to configure probe fields: {}", e.getMessage());
            }
        }

        // === RESPONSIBILITY / NOTIFY-CONTROL-POSITION (CTSW020) ===
        // Responsibility is already handled in EXTENDED IPM ATTRIBUTES section above.
        // Notify-control-position: no dedicated constant exists; if the API ever
        // exposes
        // a native field, it should be mapped here. For now, preserve intent in logs.
        String notifyControlPos = amhsDefaults.get("notify-control-position");
        if (notifyControlPos != null && !notifyControlPos.trim().isEmpty()) {
            logger.info("[CTSW020] notify-control-position='{}' - no native API constant available; "
                    + "configure at gateway level", notifyControlPos);
        }

        // Latest Delivery Time
        String latestDelivery = amhsDefaults.get("latest-delivery-time");
        if (latestDelivery != null && !latestDelivery.trim().isEmpty()) {
            try {
                // X400_S_EXPIRY_TIME = 305 is the closest available constant for a delivery
                // deadline
                message.setStringparam(X400_att.X400_S_EXPIRY_TIME, latestDelivery.trim());
                logger.debug("Set latest-delivery-time via X400_S_EXPIRY_TIME: {}", latestDelivery);
            } catch (Exception e) {
                logger.warn("Failed to set latest-delivery-time: {}", e.getMessage());
            }
        }

        // Subject IPM ID (for referenced messages / RN - CTSW014-015)
        String subjectIpmId = amhsDefaults.get("subject-ipm-id");
        if (subjectIpmId != null && !subjectIpmId.trim().isEmpty()) {
            try {
                message.setMessageIPMIdentifier(subjectIpmId.trim());
                logger.debug("Set subject-ipm-id: {}", subjectIpmId);
            } catch (Exception e) {
                logger.warn("Failed to set subject-ipm-id: {}", e.getMessage());
            }
        }

        // GeneralText charset attribute 802 is applied to BodypartGeneralText in
        // addBodyPart. It must not be added to the parent message object.
        String conversionProhibited = amhsDefaults.get("conversion-with-loss-prohibited");
        String implicitConversion = amhsDefaults.get("implicit-conversion");
        
        // implicit-conversion-prohibited (CTSW019)
        if (implicitConversion != null && !implicitConversion.trim().isEmpty()) {
            try {
                // "allowed" -> 0 (false), "prohibited" -> 1 (true)
                int val = "allowed".equalsIgnoreCase(implicitConversion.trim()) ? 0 : 1;
                message.setIntParam(X400_att.X400_N_IMPLICIT_CONVERSION_PROHIBITED, val);
                logger.debug("Set implicit-conversion-prohibited: {}", val);
            } catch (Exception e) {
                logger.warn("Failed to set implicit-conversion-prohibited: {}", e.getMessage());
            }
        }

        // conversion-with-loss-prohibited: no dedicated constant; log for gateway
        if (conversionProhibited != null && !conversionProhibited.trim().isEmpty()) {
            logger.info("[CTSW017-019] conversion-with-loss-prohibited='{}' - configure at gateway level",
                    conversionProhibited);
        }

        applyEncodedInformationTypes(message, amhsDefaults);

        // === CC/BCC RECIPIENTS ===

        String ccRecipients = amhsDefaults.get("copy-recipients");
        if (ccRecipients != null && !ccRecipients.trim().isEmpty()) {
            try {
                String[] recipients = ccRecipients.split("[;,]");
                for (String recip : recipients) {
                    String trimmed = recip.trim();
                    if (!trimmed.isEmpty()) {
                        message.setCc(trimmed, X400Msg.DR_Request.DR_NON_DELIVERY_REPORT,
                                X400Msg.IPN_NON_RECEIPT_NOTIFICATION);
                        logger.debug("Added CC recipient: {}", trimmed);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to set CC recipients: {}", e.getMessage());
            }
        }

        String bccRecipients = amhsDefaults.get("bcc-recipients");
        if (bccRecipients != null && !bccRecipients.trim().isEmpty()) {
            try {
                String[] recipients = bccRecipients.split("[;,]");
                for (String recip : recipients) {
                    String trimmed = recip.trim();
                    if (!trimmed.isEmpty()) {
                        message.setBcc(trimmed, X400Msg.DR_Request.DR_NON_DELIVERY_REPORT,
                                X400Msg.IPN_NON_RECEIPT_NOTIFICATION);
                        logger.debug("Added BCC recipient: {}", trimmed);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to set BCC recipients: {}", e.getMessage());
            }
        }

        // === VALIDATION FLAGS (CTSW006/007/010) ===
        // No dedicated size/reject constants exist in AMHS_att. These are logged
        // and managed at the gateway level per EUR Doc 047 section 4.4.2.6-7.
        String headerEmpty = amhsDefaults.get("header-empty");
        boolean exceedsMaxSize = Boolean.parseBoolean(amhsDefaults.getOrDefault("exceeds-max-size", "false"));
        boolean shouldReject = Boolean.parseBoolean(amhsDefaults.getOrDefault("should-reject", "false"));
        if ("true".equalsIgnoreCase(headerEmpty))
            logger.info("[CTSW004] header-empty flag set - ATS header syntax error scenario");
        if (exceedsMaxSize)
            logger.info("[CTSW006] exceeds-max-size flag set - gateway should reject with NDR");
        if (shouldReject)
            logger.info("[CTSW007/010] should-reject flag set - gateway should reject message");

        // === SECURITY LABEL (CTSW020) ===
        // X400_S_SECURITY_LABEL = 1501 (confirmed via reflection) - BER-encoded label
        String secClass = amhsDefaults.get("security-classification");
        if (secClass != null && !secClass.trim().isEmpty()) {
            try {
                // X400_S_SECURITY_LABEL expects a BER-encoded security label string.
                // For test purposes, encode the classification name directly.
                message.setStringparam(X400_att.X400_S_SECURITY_LABEL, secClass.trim());
                logger.debug("Set security-classification via X400_S_SECURITY_LABEL: {}", secClass);
            } catch (Exception e) {
                logger.warn("Failed to set security-classification: {}", e.getMessage());
            }
        }

    }

    String resolveFilingTime(Map<String, String> amhsDefaults) {
        if (amhsDefaults != null && Boolean.parseBoolean(amhsDefaults.getOrDefault("header-empty", "false"))) {
            logger.debug("Header-empty flag set; returning blank filing time");
            return "";
        }
        String filingTime = null;
        if (amhsDefaults != null && amhsDefaults.containsKey("filing-time")) {
            filingTime = amhsDefaults.get("filing-time");
            return filingTime == null ? "" : filingTime.trim();
        }
        filingTime = formatCurrentFilingTime();
        logger.debug("Default filing-time generated: {}", filingTime);
        return filingTime;
    }

    private String formatCurrentFilingTime() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        // ICAO ATS filing time uses UTC in ddHHmm format.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddHHmm");
        return nowUtc.format(formatter);
    }

    /**
     * Check if a priority string is an ATS priority code (KK, GG, FF, DD, SS)
     * 
     * @param priority Priority string to check
     * @return true if it's an ATS priority code
     */
    private boolean isAtsPriorityCode(String priority) {
        if (priority == null || priority.length() != 2) {
            return false;
        }
        String upper = priority.toUpperCase();
        return "KK".equals(upper) || "GG".equals(upper) || "FF".equals(upper) ||
                "DD".equals(upper) || "SS".equals(upper);
    }

    /** Resolve the ISO registration numbers used by an Isode GeneralText body part. */
    private String resolveGeneralTextCharsets(Map<String, String> amhsDefaults) {
        String registrations = firstNonEmpty(amhsDefaults,
                "charset-reg-number", "charset-reg-numbers");
        String source = amhsDefaults != null ? amhsDefaults.get("charset-source") : null;
        if (source != null) {
            if ("iso-isp-12062-2-A132-cyrillic-arabic-greek-hebrew".equals(source.trim())) {
                // CTSW019.2 content selects one permitted repertoire: Cyrillic.
                registrations = "1 6 144";
            } else if ("iso-isp-12062-2-A132-cjk".equals(source.trim())) {
                // CTSW019.3 selects one permitted repertoire: Japanese JIS X 0208.
                registrations = "1 6 87";
            }
        }
        if (registrations == null || registrations.trim().isEmpty()) {
            registrations = "1 6";
        }
        return registrations.replace(',', ' ').trim().replaceAll("\\s+", " ");
    }

    /** Fail before native submission when text cannot be represented as configured. */
    private void validateGeneralTextContent(String registrations, String content)
            throws X400APIException {
        String javaCharset = null;
        List<String> values = Arrays.asList(registrations.split(" "));
        if (values.contains("87")) {
            javaCharset = "ISO-2022-JP";
        } else if (values.contains("58")) {
            javaCharset = "GB2312";
        } else if (values.contains("144")) {
            javaCharset = "ISO-8859-5";
        } else if (values.contains("127")) {
            javaCharset = "ISO-8859-6";
        } else if (values.contains("126")) {
            javaCharset = "ISO-8859-7";
        } else if (values.contains("138")) {
            javaCharset = "ISO-8859-8";
        } else if (values.contains("100")) {
            javaCharset = "ISO-8859-1";
        } else if (values.size() <= 2 && values.contains("6")) {
            javaCharset = "US-ASCII";
        }

        if (javaCharset != null && !Charset.forName(javaCharset).newEncoder().canEncode(content)) {
            throw new X400APIException("GeneralText content cannot be encoded using ISO registrations "
                    + registrations + " (" + javaCharset + ")");
        }
    }

    /**
     * Helper method to add a body part to the message
     */
    private void addBodyPart(X400Msg message, String bodyPartType, String content, Map<String, String> amhsDefaults)
            throws X400APIException {
        String safeContent = content != null ? content : "";
        String type = bodyPartType != null ? bodyPartType.trim().toLowerCase() : "ia5-text";

        if (type.contains("general-text")) {
            // Isode expects registration numbers separated by spaces. Its native
            // constructor writes both charset (802) and body data (801) attributes.
            String isodeCharsets = resolveGeneralTextCharsets(amhsDefaults);
            validateGeneralTextContent(isodeCharsets, safeContent);
            BodypartGeneralText generalText;
            List<String> registrations = Arrays.asList(isodeCharsets.split(" "));
            if (registrations.contains("87")) {
                // Isode 16 fails while converting Java Unicode CJK text through
                // X400_S_BODY_DATA (801). Encode Japanese as seven-bit ISO-2022-JP
                // and use the SDK's raw body-data constructor (attribute 812).
                Charset iso2022Jp = Charset.forName("ISO-2022-JP");
                byte[] rawBytes = safeContent.getBytes(iso2022Jp);
                for (byte rawByte : rawBytes) {
                    if ((rawByte & 0x80) != 0) {
                        throw new X400APIException("ISO-2022-JP GeneralText encoding produced non-seven-bit data");
                    }
                }
                String roundTrip = new String(rawBytes, iso2022Jp);
                if (!safeContent.equals(roundTrip)) {
                    throw new X400APIException("ISO-2022-JP GeneralText encoding failed round-trip validation");
                }
                String rawContent = new String(rawBytes, StandardCharsets.US_ASCII);
                generalText = new BodypartGeneralText(isodeCharsets, rawContent, true);
                logger.info("GeneralText raw encoding: charsets={}, encoding=ISO-2022-JP, bytes={}",
                        isodeCharsets, rawBytes.length);
            } else {
                generalText = new BodypartGeneralText(isodeCharsets, safeContent);
            }
            message.addBodypart(generalText);
        } else if (type.equals("ia5-text")) {
            BodypartIA5Text ia5 = new BodypartIA5Text(safeContent);
            String repertoire = amhsDefaults != null ? amhsDefaults.get("repertoire") : null;
            if (repertoire == null && amhsDefaults != null) {
                repertoire = amhsDefaults.get("charset-repertoire");
            }
            if (repertoire != null && !repertoire.trim().isEmpty() && !"ia5".equalsIgnoreCase(repertoire.trim())) {
                try {
                    ia5.setStringParam(803, repertoire.trim());
                } catch (Exception e) {
                    logger.debug("Could not set repertoire on BodypartIA5Text: {}", e.getMessage());
                }
            }
            message.addBodypart(ia5);
        } else if (type.contains("file-transfer") || type.equals("ftbp")) {
            // Handle File Transfer Body Part (FTBP) with proper attributes from defaults
            logger.debug("Adding file-transfer-body-part");
            String fileName = amhsDefaults != null ? amhsDefaults.get("ftbp-file-name") : null;
            String ftbpContent = amhsDefaults != null ? amhsDefaults.get("ftbp-content") : null;

            // Use default filename if not specified
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "attachment.bin";
            }

            // Prefer explicitly configured FTBP content. Otherwise use the resolved
            // body content so payload-size/content-length also apply to binary FTBPs.
            String binaryContent = ftbpContent != null && !ftbpContent.isEmpty()
                    ? ftbpContent
                    : safeContent;
            byte[] contentBytes = binaryContent.getBytes(StandardCharsets.UTF_8);

            BodypartFTBP ftbp = new BodypartFTBP(fileName.trim(), contentBytes);
            message.addBodypart(ftbp);
        } else {
            // Treat as unsupported body part explicitly so gateway rejects it, rather than
            // falling back safely
            // Using BodypartFTBP as a generic unsupported wrapper if unsupported is
            // specified
            logger.debug("Adding unsupported body part type: {}", type);
            BodypartFTBP ftbp = new BodypartFTBP((String) null);
            // Don't set application reference or other fields, making it syntactically
            // invalid or unsupported
            message.addBodypart(ftbp);
        }
    }

    /**
     * Add body parts based on type specification
     * Supports ia5-text and general-text-body-part only
     * 
     * @param message      X400Msg to add body parts to
     * @param bodyPartType Type specification (e.g., "ia5-text" or
     *                     "general-text-body-part")
     * @param content      Primary content
     * @param amhsDefaults Additional configuration (unused for simplified version)
     */
    private void addBodyParts(X400Msg message, String bodyPartTypeSpec, String content,
            Map<String, String> amhsDefaults) throws X400APIException {
        List<String> types = parseBodyPartTypes(bodyPartTypeSpec);
        List<String> contents = buildBodyContents(amhsDefaults, types.size(), content);
        for (int i = 0; i < types.size(); i++) {
            addBodyPart(message, types.get(i), contents.get(i), amhsDefaults);
        }
    }

    /**
     * Get X400_Priority from string
     * Supports both ATS priority codes (KK/GG/FF/DD/SS) and X.400 priorities
     * 
     * @param priorityString Priority as string
     * @return X400_Priority enum value
     */
    public X400_Priority getPriorityFromString(String priorityString) {
        if (priorityString == null || priorityString.isEmpty()) {
            return X400_Priority.NORMAL_PRIORITY;
        }

        String upper = priorityString.toUpperCase().trim();

        // First check ATS priority codes
        if (ATS_PRIORITY_MAP.containsKey(upper)) {
            return ATS_PRIORITY_MAP.get(upper);
        }

        // Then check standard X.400 priority names
        try {
            return X400_Priority.valueOf(upper);
        } catch (IllegalArgumentException e) {
            try {
                return X400_Priority.valueOf(upper + "_PRIORITY");
            } catch (IllegalArgumentException ex) {
                return X400_Priority.NORMAL_PRIORITY;
            }
        }
    }

    /**
     * Build a probe X.400 message.
     * 
     * @param session      P3BindSession
     * @param recipient    Recipient address
     * @param subject      Subject
     * @param content      Content
     * @param priority     Priority string
     * @param amhsDefaults Map of defaults for the probe transfer envelope
     * @param filingTime   Filing time string (optional)
     * @return X400Msg configured as a probe
     */
    public X400Msg buildProbeMessage(P3BindSession session, String recipient, String subject,
            String content, String priority, Map<String, String> amhsDefaults,
            String filingTime) {
        
        // The boolean constructor creates the native object as X400_MSG_PROBE.
        // A probe has a transfer envelope, but no IPM heading or body parts.
        X400Msg msg = new X400Msg(session, true);
        Map<String, String> normalizedDefaults = normalizeAmhsDefaults(amhsDefaults);
        try {
            List<String> toRecipients = resolveToRecipients(normalizedDefaults, recipient);
            if (toRecipients.isEmpty()) {
                throw new IllegalArgumentException("A probe requires at least one envelope recipient");
            }

            X400Msg.DR_Request drRequest = getDrRequest(normalizedDefaults);
            for (String to : toRecipients) {
                // recipientType=0 prevents creation of an IPM heading recipient.
                // IPN requests apply to delivered messages, not probes.
                msg.setRecipient(true, 0, to, drRequest, 0);
            }

            String effectivePriority = firstNonEmpty(normalizedDefaults,
                    "priority", "ats-priority", "priority-indicator", "subject-message-priority");
            if ((effectivePriority == null || effectivePriority.trim().isEmpty()) && priority != null) {
                effectivePriority = priority;
            }
            msg.setPriority(getPriorityFromString(effectivePriority));

            int contentType = parseNonNegativeIntOrDefault(normalizedDefaults.get("content-type"), 22);
            int contentLength = resolveProbeContentLength(normalizedDefaults, content);
            msg.setIntParam(X400_att.X400_N_CONTENT_TYPE, contentType);
            msg.setIntParam(X400_att.X400_N_CONTENT_LENGTH, contentLength);
            applyEncodedInformationTypes(msg, normalizedDefaults);
            logger.info("Built X.400 probe: recipients={}, content-type={}, content-length={}, priority={}, DR={}",
                    toRecipients.size(), contentType, contentLength,
                    getPriorityFromString(effectivePriority), drRequest);

        } catch (X400APIException e) {
            logger.error("Error building Probe message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build Probe message", e);
        }
        return msg;
    }

    /**
     * Validate AMHS message fields
     * 
     * @param recipient Recipient address
     * @param subject   Message subject
     * @return true if fields are valid, false otherwise
     */
    public boolean validateMessage(String recipient, String subject) {
        return recipient != null && !recipient.trim().isEmpty() &&
                subject != null && !subject.trim().isEmpty();
    }

    /**
     * Generate payload string for logging/display
     * 
     * @param recipient Recipient
     * @param subject   Subject
     * @param content   Content
     * @param priority  Priority
     * @return String representation of X.400 message
     */
    public String generatePayloadString(String recipient, String subject, String content, String priority) {
        StringBuilder payload = new StringBuilder();
        payload.append("X.400 Message Payload:\n");
        payload.append("Recipient: ").append(recipient != null ? recipient : "").append("\n");
        payload.append("Subject: ").append(subject != null ? subject : "").append("\n");
        payload.append("Priority: ").append(priority != null ? priority : "NORMAL").append("\n");
        payload.append("Content:\n").append(content != null ? content : "").append("\n");
        return payload.toString();
    }

    /**
     * TEMPORARY DIAGNOSTIC - dumps all attributes set on the message before send.
     * Remove once the missing attribute is identified.
     */
    private void dumpMessageAttributes(String recipient, String subject,
            String content, String priority, Map<String, String> defaults) {
        System.out.println("=== X400Msg PRE-SEND ATTRIBUTE DUMP ===");

        // ── Parameters we are setting ────────────────────────────────────────
        System.out.println("  [IN] recipient   : " + (recipient != null ? recipient : "NULL"));
        System.out.println("  [IN] subject     : " + (subject != null ? subject : "NULL"));
        System.out.println("  [IN] priority    : " + (priority != null ? priority : "NULL"));
        System.out.println("  [IN] content     : " + (content != null
                ? content.substring(0, Math.min(content.length(), 80))
                : "NULL"));

        // ── amhsDefaults map ─────────────────────────────────────────────────
        if (defaults == null) {
            System.out.println("  [IN] amhsDefaults: NULL (plain send path)");
        } else if (defaults.isEmpty()) {
            System.out.println("  [IN] amhsDefaults: EMPTY MAP");
        } else {
            System.out.println("  [IN] amhsDefaults entries (" + defaults.size() + "):");
            for (Map.Entry<String, String> e : defaults.entrySet()) {
                System.out.println("    [" + e.getKey() + "] = ["
                        + (e.getValue() != null ? e.getValue() : "NULL") + "]");
            }
        }

        // ── Derived values ───────────────────────────────────────────────────
        System.out.println("  [DERIVED] getPriorityFromString(\"" + priority + "\") = "
                + getPriorityFromString(priority));
        System.out.println("  [DERIVED] resolveFilingTime(defaults) = "
                + resolveFilingTime(defaults));

        // ── Null / empty checks that would cause Missing attribute ───────────
        System.out.println("  [CHECK] recipient null/empty  : "
                + (recipient == null || recipient.trim().isEmpty()));
        System.out.println("  [CHECK] subject   null/empty  : "
                + (subject == null || subject.trim().isEmpty()));
        System.out.println("  [CHECK] content   null/empty  : "
                + (content == null || content.trim().isEmpty()));
        System.out.println("  [CHECK] body-part-type in map : "
                + (defaults != null ? defaults.get("body-part-type") : "N/A (no map)"));
        System.out.println("  [CHECK] filing-time in map    : "
                + (defaults != null ? defaults.get("filing-time") : "N/A (no map)"));

        System.out.println("=== END DUMP ===");
    }
}
