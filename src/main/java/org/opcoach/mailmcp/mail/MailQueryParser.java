package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.config.MailLimits;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class MailQueryParser {

    private final MailLimits limits;

    public MailQueryParser(MailLimits limits) {
        this.limits = limits;
    }

    public SearchMessagesQuery search(Map<String, Object> arguments) {
        return new SearchMessagesQuery(
                string(arguments, "mailbox", "INBOX"),
                string(arguments, "fromContains", null),
                string(arguments, "toContains", null),
                string(arguments, "subjectContains", null),
                date(arguments, "since"),
                bool(arguments, "unreadOnly", false),
                limits.boundedSearchLimit(integer(arguments, "limit", limits.defaultSearchLimit()))
        );
    }

    public GetMessageQuery getMessage(Map<String, Object> arguments) {
        return new GetMessageQuery(
                string(arguments, "mailbox", "INBOX"),
                uid(arguments),
                bool(arguments, "includeHtml", false),
                Math.min(integer(arguments, "maxBodyBytes", limits.maxBodyBytes()), limits.maxBodyBytes())
        );
    }

    public GetAttachmentQuery getAttachment(Map<String, Object> arguments) {
        return new GetAttachmentQuery(
                string(arguments, "mailbox", "INBOX"),
                uid(arguments),
                required(arguments, "attachmentId"),
                Math.min(integer(arguments, "maxBytes", limits.maxAttachmentBytes()), limits.maxAttachmentBytes())
        );
    }

    private static long uid(Map<String, Object> arguments) {
        String value = required(arguments, "uid");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("uid doit être un entier IMAP positif.");
        }
    }

    private static LocalDate date(Map<String, Object> arguments, String key) {
        String value = string(arguments, key, null);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(key + " doit être au format yyyy-MM-dd.");
        }
    }

    private static boolean bool(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static int integer(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " doit être un entier.");
        }
    }

    private static String required(Map<String, Object> arguments, String key) {
        String value = string(arguments, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " est obligatoire.");
        }
        return value;
    }

    private static String string(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString().trim();
    }
}
