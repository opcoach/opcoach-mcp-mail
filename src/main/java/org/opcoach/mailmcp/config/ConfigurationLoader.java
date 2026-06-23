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
                    Missing configuration.
                    Run ./mvnw -Psetup clean verify to create a local profile.
                    The password is stored only when the platform supports a local keychain.
                    Expected file: %s
                    """.formatted(configPath.toAbsolutePath()));
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to read configuration: " + configPath, exception);
        }

        String profile = value(properties, "profile", requestedProfile == null || requestedProfile.isBlank() ? "default" : requestedProfile);
        if (requestedProfile != null && !requestedProfile.isBlank() && !requestedProfile.equals(profile)) {
            throw new ConfigurationException("Requested profile " + requestedProfile + " differs from configured profile " + profile + ".");
        }

        MailEndpoint imap = endpoint(properties, "imap");
        MailEndpoint smtp = endpoint(properties, "smtp");
        MailLimits limits = MailLimits.from(properties);
        Path auditPath = Path.of(value(properties, "audit.path", ConfigurationPaths.defaultAuditPath(configPath).toString()));
        String sentMailbox = required(properties, "sent.mailbox");
        String trashMailbox = value(properties, "trash.mailbox", "INBOX.Trash");

        return new MailConfiguration(
                profile,
                imap,
                smtp,
                required(properties, "username"),
                required(properties, "from.address"),
                value(properties, "from.name", ""),
                sentMailbox,
                trashMailbox,
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
            throw new ConfigurationException("Invalid port for " + key + ": " + rawValue);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing configuration field: " + key);
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
