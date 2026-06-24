package org.opcoach.mailmcp.config;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

public final class ConfigurationWriter {

    private final Path configPath;

    public ConfigurationWriter(Path configPath) {
        this.configPath = configPath;
    }

    public void write(ConfigurationDraft draft) {
        Properties properties = new Properties();
        properties.setProperty("profile", draft.profile());
        properties.setProperty("imap.host", draft.imapHost());
        properties.setProperty("imap.port", Integer.toString(draft.imapPort()));
        properties.setProperty("imap.security", draft.imapSecurity().propertyValue());
        properties.setProperty("smtp.host", draft.smtpHost());
        properties.setProperty("smtp.port", Integer.toString(draft.smtpPort()));
        properties.setProperty("smtp.security", draft.smtpSecurity().propertyValue());
        properties.setProperty("username", draft.username());
        properties.setProperty("from.address", draft.fromAddress());
        properties.setProperty("from.name", draft.fromName());
        if (draft.replyToAddress() != null && !draft.replyToAddress().isBlank()) {
            properties.setProperty("replyTo.address", draft.replyToAddress().trim());
        }
        properties.setProperty("sent.mailbox", draft.sentMailbox());
        properties.setProperty("trash.mailbox", draft.trashMailbox());

        try {
            Path parent = configPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "Local opcoach-mcp-mail configuration. No secrets in this file.");
            }
            restrictOwnerReadWrite(configPath);
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to write configuration: " + configPath, exception);
        }
    }

    private static void restrictOwnerReadWrite(Path path) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some volumes do not support POSIX permissions.
        }
    }
}
