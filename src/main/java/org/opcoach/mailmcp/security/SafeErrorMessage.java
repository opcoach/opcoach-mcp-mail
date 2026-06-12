package org.opcoach.mailmcp.security;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SafeErrorMessage {

    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile("(?i)(password|mot[- ]?de[- ]?passe|secret|token)\\s*[:=]\\s*\\S+");

    private SafeErrorMessage() {
    }

    public static String clean(String message) {
        if (message == null || message.isBlank()) {
            return "Erreur non précisée.";
        }
        return PASSWORD_ASSIGNMENT.matcher(message).replaceAll("$1=<masqué>");
    }

    public static String clean(String message, Collection<String> secrets) {
        String cleaned = clean(message);
        if (secrets == null) {
            return cleaned;
        }
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                cleaned = cleaned.replace(secret, "<masqué>");
            }
        }
        return cleaned;
    }

    public static String requireNonBlank(String value, String field) {
        if (Objects.requireNonNullElse(value, "").isBlank()) {
            throw new IllegalArgumentException("Champ obligatoire manquant: " + field);
        }
        return value;
    }
}
