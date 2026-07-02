package org.opcoach.mailmcp.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpToolSchemas {

    private McpToolSchemas() {
    }

    public static Map<String, Object> sendEmail() {
        Map<String, Object> attachment = object(
                props(
                        entry("filename", string("Filename sent to the recipient.")),
                        entry("contentType", string("Attachment MIME type.")),
                        entry("contentBase64", string("Base64 attachment content."))
                ),
                List.of("filename", "contentBase64")
        );

        return object(
                props(
                        entry("to", array("Primary recipients.", string("Email address."))),
                        entry("cc", array("Carbon-copy recipients.", string("Email address."))),
                        entry("bcc", array("Blind carbon-copy recipients.", string("Email address."))),
                        entry("subject", string("Message subject.")),
                        entry("textBody", string("Plain-text message body.")),
                        entry("htmlBody", string("HTML message body.")),
                        entry("attachments", array("Explicit base64 attachments.", attachment))
                ),
                List.of("to", "subject")
        );
    }

    public static Map<String, Object> listMailboxes() {
        return object(
                props(entry("includeSpecialUse", bool("Include IMAP special-use attributes when available."))),
                List.of()
        );
    }

    public static Map<String, Object> searchMessages() {
        return object(
                props(
                        entry("mailbox", string("IMAP folder to query. Defaults to INBOX, or to the configured Sent folder when only toContains is provided.", "INBOX")),
                        entry("fromContains", string("Partial sender filter.")),
                        entry("toContains", string("Partial recipient filter. Use this to find recent messages sent to an email address or person.")),
                        entry("subjectContains", string("Partial subject filter.")),
                        entry("since", date("Inclusive minimum received date in ISO yyyy-MM-dd format.")),
                        entry("until", date("Inclusive maximum received date in ISO yyyy-MM-dd format.")),
                        entry("unreadOnly", bool("Limit to unread messages.", false)),
                        entry("limit", integer("Maximum number of returned messages.", 1, 25, 10)),
                        entry("beforeUid", string("Return only messages with an IMAP UID lower than this value. Use the last UID from the previous page to continue safely."))
                ),
                List.of()
        );
    }

    public static Map<String, Object> getMessage() {
        return object(
                props(
                        entry("mailbox", string("IMAP folder containing the message.", "INBOX")),
                        entry("uid", string("Stable IMAP UID for the message.")),
                        entry("includeHtml", bool("Include the bounded HTML body.", false)),
                        entry("maxBodyBytes", integer("Maximum returned plain-text body size.", 1, 100_000, 12_000))
                ),
                List.of("uid")
        );
    }

    public static Map<String, Object> getAttachment() {
        return object(
                props(
                        entry("mailbox", string("IMAP folder containing the message.", "INBOX")),
                        entry("uid", string("Stable IMAP UID for the message.")),
                        entry("attachmentId", string("Attachment identifier returned by getMessage.")),
                        entry("maxBytes", integer("Maximum inline attachment bytes before base64 encoding. Use saveAttachment for larger files.", 1, 75_000, 75_000))
                ),
                List.of("uid", "attachmentId")
        );
    }

    public static Map<String, Object> getAttachmentInfo() {
        return object(
                props(
                        entry("mailbox", string("IMAP folder containing the message.", "INBOX")),
                        entry("uid", string("Stable IMAP UID for the message.")),
                        entry("attachmentId", string("Optional attachment identifier returned by getMessage or getAttachmentInfo. When omitted, all attachments are returned."))
                ),
                List.of("uid")
        );
    }

    public static Map<String, Object> saveAttachment() {
        return object(
                props(
                        entry("mailbox", string("IMAP folder containing the message.", "INBOX")),
                        entry("uid", string("Stable IMAP UID for the message.")),
                        entry("attachmentId", string("Attachment identifier returned by getMessage or getAttachmentInfo.")),
                        entry("directory", string("Optional relative subdirectory below the local attachment root. Absolute paths and '..' are rejected.")),
                        entry("filename", string("Optional saved filename. When omitted, the attachment filename is used. Existing files are never overwritten; a numeric suffix is added.")),
                        entry("maxBytes", integer("Maximum attachment bytes accepted for local saving.", 1, 50 * 1024 * 1024, 5 * 1024 * 1024))
                ),
                List.of("uid", "attachmentId")
        );
    }

    public static Map<String, Object> moveMessage() {
        return object(
                props(
                        entry("mailbox", string("IMAP folder currently containing the message.", "INBOX")),
                        entry("uid", string("Stable IMAP UID for the message in the source folder.")),
                        entry("targetMailbox", string("IMAP folder where the message must be moved. The folder is created when the server allows it."))
                ),
                List.of("uid", "targetMailbox")
        );
    }

    public static Map<String, Object> deleteMessage() {
        return object(
                props(
                        entry("mailbox", string("IMAP folder currently containing the message.", "INBOX")),
                        entry("uid", string("Stable IMAP UID for the message in the source folder. The message is moved to the configured trash folder."))
                ),
                List.of("uid")
        );
    }

    @SafeVarargs
    private static Map<String, Object> props(Map.Entry<String, Object>... entries) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            properties.put(entry.getKey(), entry.getValue());
        }
        return properties;
    }

    private static Map.Entry<String, Object> entry(String name, Object schema) {
        return Map.entry(name, schema);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<>(required));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> string(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        return schema;
    }

    private static Map<String, Object> string(String description, String defaultValue) {
        Map<String, Object> schema = string(description);
        schema.put("default", defaultValue);
        return schema;
    }

    private static Map<String, Object> date(String description) {
        Map<String, Object> schema = string(description);
        schema.put("format", "date");
        return schema;
    }

    private static Map<String, Object> bool(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "boolean");
        schema.put("description", description);
        return schema;
    }

    private static Map<String, Object> bool(String description, boolean defaultValue) {
        Map<String, Object> schema = bool(description);
        schema.put("default", defaultValue);
        return schema;
    }

    private static Map<String, Object> integer(String description, int minimum, int maximum, int defaultValue) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("description", description);
        schema.put("minimum", minimum);
        schema.put("maximum", maximum);
        schema.put("default", defaultValue);
        return schema;
    }

    private static Map<String, Object> array(String description, Object itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", itemSchema);
        return schema;
    }
}
