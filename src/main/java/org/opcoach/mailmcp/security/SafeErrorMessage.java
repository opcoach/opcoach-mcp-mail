package org.opcoach.mailmcp.security;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SafeErrorMessage {

    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile("(?i)(password|secret|token)\\s*[:=]\\s*\\S+");

    private SafeErrorMessage() {
    }

    public static String clean(String message) {
        if (message == null || message.isBlank()) {
            return "Unspecified error.";
        }
        return PASSWORD_ASSIGNMENT.matcher(message).replaceAll("$1=<masked>");
    }

    public static String clean(String message, Collection<String> secrets) {
        String cleaned = clean(message);
        if (secrets == null) {
            return cleaned;
        }
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                cleaned = cleaned.replace(secret, "<masked>");
            }
        }
        return cleaned;
    }

    public static String requireNonBlank(String value, String field) {
        if (Objects.requireNonNullElse(value, "").isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
