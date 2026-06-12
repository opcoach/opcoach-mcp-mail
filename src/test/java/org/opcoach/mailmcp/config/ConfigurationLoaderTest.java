package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsValidProperties() throws IOException {
        Path config = tempDir.resolve("config.properties");
        Files.writeString(config, """
                profile=default
                imap.host=imap.example.com
                imap.port=993
                imap.security=ssl_tls
                smtp.host=smtp.example.com
                smtp.port=465
                smtp.security=ssl_tls
                username=formation@example.com
                from.address=formation@example.com
                from.name=Formation MCP
                sent.mailbox=INBOX.Sent
                """);

        MailConfiguration loaded = new ConfigurationLoader(config).load("default");

        assertEquals("default", loaded.profile());
        assertEquals("imap.example.com", loaded.imap().host());
        assertEquals(ConnectionSecurity.SSL_TLS, loaded.smtp().security());
        assertEquals(25, loaded.limits().maxSearchLimit());
    }

    @Test
    void rejectsMissingConfiguration() {
        ConfigurationLoader loader = new ConfigurationLoader(tempDir.resolve("absent.properties"));

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> loader.load("default"));

        assertEquals(true, exception.getMessage().contains("Configuration absente"));
    }
}
