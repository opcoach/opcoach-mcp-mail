package org.opcoach.mailmcp.config;

public record ResolvedSecret(String value, SecretSource source) {

    public ResolvedSecret {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Mot de passe mail absent.");
        }
        if (source == null) {
            throw new ConfigurationException("Source du secret absente.");
        }
    }

    public enum SecretSource {
        ENVIRONMENT,
        KEYCHAIN
    }
}
