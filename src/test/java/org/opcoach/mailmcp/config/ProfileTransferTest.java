package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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
                "INBOX,error+warning_opcoach",
                "INBOX.Sent",
                "INBOX.Trash",
                ""
        );

        String exported = transfer.write(List.of(profile));
        String lowerCase = exported.toLowerCase(Locale.ROOT);

        assertTrue(exported.contains("profile.0.name=olivier"));
        assertTrue(exported.contains("profile.0.replyTo.address=reply@example.com"));
        assertTrue(exported.contains("profile.0.incoming.mailboxes=INBOX,error+warning_opcoach"));
        assertFalse(exported.contains("profile.0.displayName="));
        assertEquals("olivier", transfer.read(exported).getFirst().label());
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
                "INBOX",
                "Sent",
                "Trash",
                "\uD83E\uDDE0 Formation"
        );

        List<ProfileTransfer.ProfileSnapshot> imported = transfer.read(transfer.write(List.of(profile)));

        assertEquals(1, imported.size());
        assertEquals("Mail_Olivier", imported.getFirst().profile());
        assertEquals(8096, imported.getFirst().mcpPort());
        assertEquals(ConnectionSecurity.STARTTLS, imported.getFirst().smtpSecurity());
        assertEquals("INBOX", imported.getFirst().incomingMailboxes());
        assertEquals("Sent", imported.getFirst().sentMailbox());
        assertEquals("\uD83E\uDDE0 Formation", imported.getFirst().displayName());
        ServerRegistration registration = imported.getFirst().registration(new ServerRegistry(Path.of(".")));
        assertEquals("Mail_Olivier", registration.profile());
        assertEquals("\uD83E\uDDE0 Formation", registration.label());
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
