package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.config.MailLimits;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class SendEmailCommandParser {

    private final MailLimits limits;

    public SendEmailCommandParser(MailLimits limits) {
        this.limits = limits;
    }

    public SendEmailCommand parse(Map<String, Object> arguments) {
        return new SendEmailCommand(
                addresses(arguments.get("to")),
                addresses(arguments.get("cc")),
                addresses(arguments.get("bcc")),
                string(arguments, "subject"),
                string(arguments, "textBody"),
                string(arguments, "htmlBody"),
                attachments(arguments.get("attachments"))
        );
    }

    private List<AttachmentPayload> attachments(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawAttachments)) {
            throw new IllegalArgumentException("attachments doit être un tableau.");
        }
        List<AttachmentPayload> attachments = new ArrayList<>();
        for (Object rawAttachment : rawAttachments) {
            if (!(rawAttachment instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Chaque pièce jointe doit être un objet.");
            }
            String filename = required(map, "filename");
            String contentType = optional(map, "contentType", "application/octet-stream");
            String contentBase64 = required(map, "contentBase64");
            byte[] decoded = Base64.getDecoder().decode(contentBase64);
            if (decoded.length > limits.maxAttachmentBytes()) {
                throw new IllegalArgumentException("Pièce jointe trop volumineuse: " + filename);
            }
            attachments.add(new AttachmentPayload(filename, contentType, decoded));
        }
        return List.copyOf(attachments);
    }

    private static List<String> addresses(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String string) {
            return splitAddresses(string);
        }
        if (value instanceof List<?> list) {
            List<String> addresses = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    addresses.add(item.toString().trim());
                }
            }
            return List.copyOf(addresses);
        }
        throw new IllegalArgumentException("Les destinataires doivent être une chaîne ou un tableau.");
    }

    private static List<String> splitAddresses(String raw) {
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String value : raw.split(",")) {
            if (!value.isBlank()) {
                values.add(value.trim());
            }
        }
        return List.copyOf(values);
    }

    private static String string(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? null : value.toString();
    }

    private static String required(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Champ de pièce jointe manquant: " + key);
        }
        return value.toString();
    }

    private static String optional(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }
}
