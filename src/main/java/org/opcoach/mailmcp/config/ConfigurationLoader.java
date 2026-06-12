package org.opcoach.mailmcp.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigurationLoader {

    private final Path configPath;

    public ConfigurationLoader(Path configPath) {
        this.configPath = configPath;
    }

    public static ConfigurationLoader defaultLoader() {
        return new ConfigurationLoader(ConfigurationPaths.defaultConfigPath());
    }

    public MailConfiguration load(String requestedProfile) {
        if (!Files.exists(configPath)) {
            throw new ConfigurationException("""
                    Configuration absente.
                    Lancez ./mvnw -Psetup clean verify pour créer un profil local.
                    Le mot de passe sera stocké dans le trousseau du système.
                    Fichier attendu: %s
                    """.formatted(configPath.toAbsolutePath()));
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new ConfigurationException("Impossible de lire la configuration: " + configPath, exception);
        }

        String profile = value(properties, "profile", requestedProfile == null || requestedProfile.isBlank() ? "default" : requestedProfile);
        if (requestedProfile != null && !requestedProfile.isBlank() && !requestedProfile.equals(profile)) {
            throw new ConfigurationException("Profil demandé " + requestedProfile + " différent du profil configuré " + profile + ".");
        }

        MailEndpoint imap = endpoint(properties, "imap");
        MailEndpoint smtp = endpoint(properties, "smtp");
        MailLimits limits = MailLimits.from(properties);
        Path auditPath = Path.of(value(properties, "audit.path", ConfigurationPaths.defaultAuditPath(configPath).toString()));

        return new MailConfiguration(
                profile,
                imap,
                smtp,
                required(properties, "username"),
                required(properties, "from.address"),
                value(properties, "from.name", ""),
                required(properties, "sent.mailbox"),
                limits,
                configPath,
                auditPath
        );
    }

    private static MailEndpoint endpoint(Properties properties, String prefix) {
        return new MailEndpoint(
                required(properties, prefix + ".host"),
                port(properties, prefix + ".port"),
                ConnectionSecurity.parse(required(properties, prefix + ".security"), prefix + ".security")
        );
    }

    private static int port(Properties properties, String key) {
        String rawValue = required(properties, key);
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Port invalide pour " + key + ": " + rawValue);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Champ de configuration manquant: " + key);
        }
        return value.trim();
    }

    private static String value(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
