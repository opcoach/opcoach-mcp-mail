package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebManagerApplicationTest {

    @TempDir
    Path tempDir;

    @Test
    void displaysEscapedImapUsernameRatherThanSenderAddress() {
        ServerRegistration registration = registration();
        new ConfigurationWriter(registration.configFile()).write(new ConfigurationDraft(
                registration.profile(), "imap.example.test", 993, ConnectionSecurity.SSL_TLS,
                "smtp.example.test", 465, ConnectionSecurity.SSL_TLS,
                "ops&alerts@example.test", "sender@example.test", "", "",
                "INBOX", "Sent", "Trash"
        ));

        assertEquals("<td class=\"email-column\">ops&amp;alerts@example.test</td>",
                WebManagerApplication.emailCell(registration));
    }

    @Test
    void displaysUnavailableForMissingOrMalformedConfig() throws Exception {
        ServerRegistration registration = registration();
        String unavailable = "<td class=\"email-column\">Unavailable</td>";
        assertEquals(unavailable, WebManagerApplication.emailCell(registration));

        Files.writeString(registration.configFile(), "username=\\u00xx\n");
        assertEquals(unavailable, WebManagerApplication.emailCell(registration));
    }

    private ServerRegistration registration() {
        ServerRegistry registry = new ServerRegistry(tempDir);
        String profile = "training";
        return new ServerRegistration(profile, tempDir.resolve("training.properties"),
                registry.runDir(profile), "127.0.0.1", 8095);
    }
}
