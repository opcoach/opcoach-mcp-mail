package org.opcoach.mailmcp.config;

public record ResolvedSecret(String value, SecretSource source) {

    public ResolvedSecret {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing mail password.");
        }
        if (source == null) {
            throw new ConfigurationException("Missing secret source.");
        }
    }

    public enum SecretSource {
        ENVIRONMENT,
        LOCAL_STORE
    }
}
