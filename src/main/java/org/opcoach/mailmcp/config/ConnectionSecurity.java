package org.opcoach.mailmcp.config;

import java.util.Locale;

public enum ConnectionSecurity {
    SSL_TLS("ssl_tls"),
    STARTTLS("starttls"),
    NONE("none");

    private final String propertyValue;

    ConnectionSecurity(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static ConnectionSecurity parse(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing configuration field: " + fieldName);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return switch (normalized) {
            case "ssl", "ssl_tls", "tls" -> SSL_TLS;
            case "starttls", "start_tls" -> STARTTLS;
            case "none", "plain" -> NONE;
            default -> throw new ConfigurationException("Unknown security value for " + fieldName + ": " + value);
        };
    }
}
