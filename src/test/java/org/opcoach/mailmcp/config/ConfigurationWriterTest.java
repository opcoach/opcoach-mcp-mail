package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesOnlyNonSecretProperties() throws IOException {
        Path config = tempDir.resolve("config.properties");
        ConfigurationDraft draft = new ConfigurationDraft(
                "default",
                "imap.example.com",
                993,
                ConnectionSecurity.SSL_TLS,
                "smtp.example.com",
                465,
                ConnectionSecurity.SSL_TLS,
                "training@example.com",
                "training@example.com",
                "MCP Training",
                "",
                "INBOX.Sent",
                "INBOX.Trash"
        );

        new ConfigurationWriter(config).write(draft);

        String content = Files.readString(config);
        assertTrue(content.contains("imap.host=imap.example.com"));
        assertTrue(content.contains("trash.mailbox=INBOX.Trash"));
        assertFalse(content.contains("replyTo.address"));
        assertFalse(content.toLowerCase().contains("password"));
        assertFalse(content.toLowerCase().contains("fake-password"));
    }

    @Test
    void writesReplyToAddressWhenConfigured() throws IOException {
        Path config = tempDir.resolve("config.properties");
        ConfigurationDraft draft = new ConfigurationDraft(
                "default",
                "imap.example.com",
                993,
                ConnectionSecurity.SSL_TLS,
                "smtp.example.com",
                465,
                ConnectionSecurity.SSL_TLS,
                "training@example.com",
                "training@example.com",
                "MCP Training",
                "reply@example.com",
                "INBOX.Sent",
                "INBOX.Trash"
        );

        new ConfigurationWriter(config).write(draft);

        assertTrue(Files.readString(config).contains("replyTo.address=reply@example.com"));
    }
}
