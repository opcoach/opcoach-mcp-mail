package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebManagerApplicationTest {

    @TempDir
    Path tempDir;

    @Test
    void displaysEscapedImapUsernameRatherThanSenderAddress() {
        ServerRegistration registration = registration("training");
        writeConfiguration(registration, "ops&alerts@example.test");

        assertEquals("<td class=\"email-column\">ops&amp;alerts@example.test</td>",
                WebManagerApplication.ServerRow.load(registration).emailCell());
    }

    @Test
    void displaysUnavailableForMissingOrMalformedConfig() throws Exception {
        ServerRegistration registration = registration("training");
        String unavailable = "<td class=\"email-column\">Unavailable</td>";
        assertEquals(unavailable, WebManagerApplication.ServerRow.load(registration).emailCell());

        Files.writeString(registration.configFile(), "username=\\u00xx\n");
        assertEquals(unavailable, WebManagerApplication.ServerRow.load(registration).emailCell());
    }

    @Test
    void sortsEmailInBothDirectionsIgnoringCaseAndKeepingUnavailableLast() {
        ServerRegistration zulu = registration("a-profile");
        ServerRegistration alpha = registration("b-profile");
        ServerRegistration sameEmail = registration("c-profile");
        ServerRegistration unavailable = registration("missing");
        writeConfiguration(zulu, "Zulu@example.test");
        writeConfiguration(alpha, "alpha@example.test");
        writeConfiguration(sameEmail, "ALPHA@example.test");
        List<ServerRegistration> registrations = List.of(unavailable, sameEmail, zulu, alpha);

        assertEquals(List.of(alpha, sameEmail, zulu, unavailable), emailOrder(registrations, "asc"));
        assertEquals(List.of(zulu, alpha, sameEmail, unavailable), emailOrder(registrations, "desc"));
    }

    private static List<ServerRegistration> emailOrder(List<ServerRegistration> registrations, String direction) {
        return WebManagerApplication.sortedServerRows(registrations, "email", direction).stream()
                .map(WebManagerApplication.ServerRow::registration)
                .toList();
    }

    private static void writeConfiguration(ServerRegistration registration, String username) {
        new ConfigurationWriter(registration.configFile()).write(new ConfigurationDraft(
                registration.profile(), "imap.example.test", 993, ConnectionSecurity.SSL_TLS,
                "smtp.example.test", 465, ConnectionSecurity.SSL_TLS,
                username, "sender@example.test", "", "",
                "INBOX", "Sent", "Trash"
        ));
    }

    private ServerRegistration registration(String profile) {
        ServerRegistry registry = new ServerRegistry(tempDir);
        return new ServerRegistration(profile, tempDir.resolve(profile + ".properties"),
                registry.runDir(profile), "127.0.0.1", 8095);
    }
}
