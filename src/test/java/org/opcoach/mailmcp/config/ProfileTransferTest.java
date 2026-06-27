package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileTransferTest {

    private final ProfileTransfer transfer = new ProfileTransfer();

    @Test
    void exportsOnlyNonSecretProfileSettings() {
        ProfileTransfer.ProfileSnapshot profile = new ProfileTransfer.ProfileSnapshot(
                "olivier",
                8096,
                "imap.example.com",
                993,
                ConnectionSecurity.SSL_TLS,
                "smtp.example.com",
                465,
                ConnectionSecurity.SSL_TLS,
                "olivier@example.com",
                "olivier@example.com",
                "Olivier",
                "reply@example.com",
                "INBOX.Sent",
                "INBOX.Trash"
        );

        String exported = transfer.write(List.of(profile));
        String lowerCase = exported.toLowerCase(Locale.ROOT);

        assertTrue(exported.contains("profile.0.name=olivier"));
        assertTrue(exported.contains("profile.0.replyTo.address=reply@example.com"));
        assertFalse(lowerCase.contains("password"));
        assertFalse(lowerCase.contains("token"));
        assertFalse(lowerCase.contains("secret"));
    }

    @Test
    void importsRoundTrippedProfiles() {
        ProfileTransfer.ProfileSnapshot profile = new ProfileTransfer.ProfileSnapshot(
                "Mail Olivier",
                8096,
                "imap.example.com",
                993,
                ConnectionSecurity.SSL_TLS,
                "smtp.example.com",
                587,
                ConnectionSecurity.STARTTLS,
                "olivier@example.com",
                "olivier@example.com",
                "Olivier",
                "",
                "Sent",
                "Trash"
        );

        List<ProfileTransfer.ProfileSnapshot> imported = transfer.read(transfer.write(List.of(profile)));

        assertEquals(1, imported.size());
        assertEquals("Mail_Olivier", imported.getFirst().profile());
        assertEquals(8096, imported.getFirst().mcpPort());
        assertEquals(ConnectionSecurity.STARTTLS, imported.getFirst().smtpSecurity());
        assertEquals("Sent", imported.getFirst().sentMailbox());
    }

    @Test
    void rejectsUnknownExportFormat() {
        ConfigurationException exception = assertThrows(
                ConfigurationException.class,
                () -> transfer.read("format=other\nversion=1\n")
        );

        assertTrue(exception.getMessage().contains("Unsupported profile export format"));
    }
}
