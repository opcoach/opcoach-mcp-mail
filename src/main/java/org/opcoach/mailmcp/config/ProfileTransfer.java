package org.opcoach.mailmcp.config;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ProfileTransfer {

    private static final String FORMAT = "opcoach-mcp-mail-profile-export";
    private static final String VERSION = "1";

    public String write(List<ProfileSnapshot> profiles) {
        Properties properties = new Properties();
        properties.setProperty("format", FORMAT);
        properties.setProperty("version", VERSION);
        properties.setProperty("profile.count", Integer.toString(profiles.size()));
        for (int index = 0; index < profiles.size(); index++) {
            ProfileSnapshot profile = profiles.get(index);
            String prefix = "profile." + index + ".";
            properties.setProperty(prefix + "name", profile.profile());
            properties.setProperty(prefix + "mcp.port", Integer.toString(profile.mcpPort()));
            properties.setProperty(prefix + "imap.host", profile.imapHost());
            properties.setProperty(prefix + "imap.port", Integer.toString(profile.imapPort()));
            properties.setProperty(prefix + "imap.security", profile.imapSecurity().propertyValue());
            properties.setProperty(prefix + "smtp.host", profile.smtpHost());
            properties.setProperty(prefix + "smtp.port", Integer.toString(profile.smtpPort()));
            properties.setProperty(prefix + "smtp.security", profile.smtpSecurity().propertyValue());
            properties.setProperty(prefix + "username", profile.username());
            properties.setProperty(prefix + "from.address", profile.fromAddress());
            properties.setProperty(prefix + "from.name", profile.fromName());
            if (!profile.replyToAddress().isBlank()) {
                properties.setProperty(prefix + "replyTo.address", profile.replyToAddress());
            }
            properties.setProperty(prefix + "sent.mailbox", profile.sentMailbox());
            properties.setProperty(prefix + "trash.mailbox", profile.trashMailbox());
        }
        try {
            StringWriter writer = new StringWriter();
            properties.store(writer, "opcoach-mcp-mail profile export. Credentials are not included.");
            return writer.toString();
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to create profile export.", exception);
        }
    }

    public List<ProfileSnapshot> read(String raw) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(raw == null ? "" : raw));
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to read profile export.", exception);
        }
        String format = properties.getProperty("format", "");
        String version = properties.getProperty("version", "");
        if (!FORMAT.equals(format) || !VERSION.equals(version)) {
            throw new ConfigurationException("Unsupported profile export format.");
        }
        int count = parseNonNegativeInt(properties.getProperty("profile.count", ""), "profile.count");
        List<ProfileSnapshot> profiles = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "profile." + index + ".";
            profiles.add(new ProfileSnapshot(
                    required(properties, prefix + "name"),
                    parsePort(required(properties, prefix + "mcp.port"), prefix + "mcp.port"),
                    required(properties, prefix + "imap.host"),
                    parsePort(required(properties, prefix + "imap.port"), prefix + "imap.port"),
                    ConnectionSecurity.parse(required(properties, prefix + "imap.security"), prefix + "imap.security"),
                    required(properties, prefix + "smtp.host"),
                    parsePort(required(properties, prefix + "smtp.port"), prefix + "smtp.port"),
                    ConnectionSecurity.parse(required(properties, prefix + "smtp.security"), prefix + "smtp.security"),
                    required(properties, prefix + "username"),
                    required(properties, prefix + "from.address"),
                    properties.getProperty(prefix + "from.name", "").trim(),
                    properties.getProperty(prefix + "replyTo.address", "").trim(),
                    required(properties, prefix + "sent.mailbox"),
                    required(properties, prefix + "trash.mailbox")
            ));
        }
        return profiles;
    }

    public ProfileSnapshot snapshot(ServerRegistration registration, MailConfiguration configuration) {
        return new ProfileSnapshot(
                registration.profile(),
                registration.port(),
                configuration.imap().host(),
                configuration.imap().port(),
                configuration.imap().security(),
                configuration.smtp().host(),
                configuration.smtp().port(),
                configuration.smtp().security(),
                configuration.username(),
                configuration.fromAddress(),
                configuration.fromName(),
                configuration.replyToAddress(),
                configuration.sentMailbox(),
                configuration.trashMailbox()
        );
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing profile export field: " + key);
        }
        return value.trim();
    }

    private static int parsePort(String raw, String key) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException(raw);
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Invalid port in profile export: " + key);
        }
    }

    private static int parseNonNegativeInt(String raw, String key) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new NumberFormatException(raw);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Invalid number in profile export: " + key);
        }
    }

    public record ProfileSnapshot(
            String profile,
            int mcpPort,
            String imapHost,
            int imapPort,
            ConnectionSecurity imapSecurity,
            String smtpHost,
            int smtpPort,
            ConnectionSecurity smtpSecurity,
            String username,
            String fromAddress,
            String fromName,
            String replyToAddress,
            String sentMailbox,
            String trashMailbox
    ) {

        public ProfileSnapshot {
            profile = ServerRegistry.registryName(profile);
            require(profile, "profile");
            require(imapHost, "imap.host");
            require(smtpHost, "smtp.host");
            require(username, "username");
            require(fromAddress, "from.address");
            require(sentMailbox, "sent.mailbox");
            require(trashMailbox, "trash.mailbox");
            if (fromName == null) {
                fromName = "";
            }
            if (replyToAddress == null) {
                replyToAddress = "";
            }
            if (imapSecurity == null) {
                throw new ConfigurationException("Missing profile export field: imap.security");
            }
            if (smtpSecurity == null) {
                throw new ConfigurationException("Missing profile export field: smtp.security");
            }
            if (mcpPort < 1 || mcpPort > 65535) {
                throw new ConfigurationException("Invalid MCP port: " + mcpPort);
            }
        }

        public ConfigurationDraft draft() {
            return new ConfigurationDraft(
                    profile,
                    imapHost,
                    imapPort,
                    imapSecurity,
                    smtpHost,
                    smtpPort,
                    smtpSecurity,
                    username,
                    fromAddress,
                    fromName,
                    replyToAddress,
                    sentMailbox,
                    trashMailbox
            );
        }

        public ServerRegistration registration(ServerRegistry registry) {
            return new ServerRegistration(
                    profile,
                    registry.configFile(profile),
                    registry.runDir(profile),
                    "127.0.0.1",
                    mcpPort
            );
        }

        private static void require(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new ConfigurationException("Missing profile export field: " + field);
            }
        }
    }
}
