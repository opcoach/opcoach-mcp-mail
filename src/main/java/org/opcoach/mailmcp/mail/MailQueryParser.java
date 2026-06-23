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
        LocalDate since = date(arguments, "since");
        LocalDate until = date(arguments, "until");
        if (since != null && until != null && since.isAfter(until)) {
            throw new IllegalArgumentException("since must be on or before until.");
        }
        return new SearchMessagesQuery(
                string(arguments, "mailbox", "INBOX"),
                string(arguments, "fromContains", null),
                string(arguments, "toContains", null),
                string(arguments, "subjectContains", null),
                since,
                until,
                bool(arguments, "unreadOnly", false),
                limits.boundedSearchLimit(integer(arguments, "limit", limits.defaultSearchLimit())),
                optionalUid(arguments, "beforeUid"),
                hasText(arguments, "mailbox")
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
        int maxAttachmentPayload = Math.max(1, (limits.maxResultBytes() * 3) / 4);
        return new GetAttachmentQuery(
                string(arguments, "mailbox", "INBOX"),
                uid(arguments),
                required(arguments, "attachmentId"),
                Math.min(integer(arguments, "maxBytes", limits.maxAttachmentBytes()), Math.min(limits.maxAttachmentBytes(), maxAttachmentPayload))
        );
    }

    public MoveMessageCommand moveMessage(Map<String, Object> arguments) {
        return new MoveMessageCommand(
                string(arguments, "mailbox", "INBOX"),
                uid(arguments),
                required(arguments, "targetMailbox")
        );
    }

    public DeleteMessageCommand deleteMessage(Map<String, Object> arguments) {
        return new DeleteMessageCommand(
                string(arguments, "mailbox", "INBOX"),
                uid(arguments)
        );
    }

    private static long uid(Map<String, Object> arguments) {
        String value = required(arguments, "uid");
        return parseUid(value, "uid");
    }

    private static Long optionalUid(Map<String, Object> arguments, String key) {
        String value = string(arguments, key, null);
        return value == null ? null : parseUid(value, key);
    }

    private static long parseUid(String value, String key) {
        try {
            long uid = Long.parseLong(value);
            if (uid <= 0) {
                throw new IllegalArgumentException(key + " must be a positive IMAP integer.");
            }
            return uid;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a positive IMAP integer.");
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
            throw new IllegalArgumentException(key + " must use yyyy-MM-dd format.");
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
            throw new IllegalArgumentException(key + " must be an integer.");
        }
    }

    private static String required(Map<String, Object> arguments, String key) {
        String value = string(arguments, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
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

    private static boolean hasText(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value != null && !value.toString().isBlank();
    }
}
