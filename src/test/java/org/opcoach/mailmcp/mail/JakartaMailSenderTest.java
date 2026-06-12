package org.opcoach.mailmcp.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.opcoach.mailmcp.config.ConnectionSecurity;
import org.opcoach.mailmcp.config.MailConfiguration;
import org.opcoach.mailmcp.config.MailEndpoint;
import org.opcoach.mailmcp.config.MailLimits;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JakartaMailSenderTest {

    @Test
    void sendsHtmlEmailWithAttachmentAndCopiesToSentMailbox() throws Exception {
        ServerSetup smtp = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);
        ServerSetup imap = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        GreenMail greenMail = new GreenMail(new ServerSetup[]{smtp, imap});
        greenMail.start();
        try {
            greenMail.setUser("formation@example.com", "formation@example.com", "secret");
            greenMail.setUser("destinataire@example.com", "destinataire@example.com", "secret");
            MailConfiguration configuration = configuration(greenMail.getSmtp().getPort(), greenMail.getImap().getPort());
            MailApplicationService service = new MailApplicationService(configuration, "secret");
            String contentBase64 = Base64.getEncoder().encodeToString("contenu fictif".getBytes(StandardCharsets.UTF_8));

            SendEmailResult result = (SendEmailResult) service.sendEmail(Map.of(
                    "to", List.of("destinataire@example.com"),
                    "subject", "Résultat de traitement",
                    "textBody", "Bonjour, le traitement est terminé.",
                    "htmlBody", "<h1>Traitement terminé</h1><p>Le résultat est disponible.</p>",
                    "attachments", List.of(Map.of(
                            "filename", "resultat.txt",
                            "contentType", "text/plain",
                            "contentBase64", contentBase64
                    ))
            ));

            assertEquals("sent", result.status());
            assertEquals("saved", result.sentCopyStatus());
            assertNotNull(result.messageId());
            assertTrue(greenMail.waitForIncomingEmail(5_000, 1));
            MimeMessage received = greenMail.getReceivedMessages()[0];
            assertEquals("Résultat de traitement", received.getSubject());
            assertTrue(received.isMimeType("multipart/*"));
            assertSentCopyExists(configuration, "secret");
        } finally {
            greenMail.stop();
        }
    }

    private static void assertSentCopyExists(MailConfiguration configuration, String password) throws Exception {
        Session session = Session.getInstance(JakartaMailSessions.imapProperties(configuration.imap()));
        try (Store store = session.getStore(JakartaMailSessions.imapProtocol(configuration.imap()))) {
            store.connect(configuration.imap().host(), configuration.imap().port(), configuration.username(), password);
            Folder sent = store.getFolder(configuration.sentMailbox());
            sent.open(Folder.READ_ONLY);
            try {
                assertEquals(1, sent.getMessageCount());
            } finally {
                sent.close(false);
            }
        }
    }

    private static MailConfiguration configuration(int smtpPort, int imapPort) {
        return new MailConfiguration(
                "default",
                new MailEndpoint("127.0.0.1", imapPort, ConnectionSecurity.NONE),
                new MailEndpoint("127.0.0.1", smtpPort, ConnectionSecurity.NONE),
                "formation@example.com",
                "formation@example.com",
                "Formation MCP",
                "Sent",
                MailLimits.DEFAULTS,
                Path.of("config.properties"),
                Path.of("audit.log")
        );
    }
}
