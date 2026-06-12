package org.opcoach.mailmcp.mail;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.activation.DataHandler;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
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
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JakartaImapClientTest {

    @Test
    void searchesReadsAndFetchesAttachmentByUid() throws Exception {
        ServerSetup imap = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        GreenMail greenMail = new GreenMail(imap);
        greenMail.start();
        try {
            GreenMailUser user = greenMail.setUser("formation@example.com", "formation@example.com", "secret");
            user.deliver(messageWithAttachment());
            MailApplicationService service = new MailApplicationService(configuration(greenMail.getImap().getPort()), "secret");

            @SuppressWarnings("unchecked")
            Map<String, Object> mailboxResult = (Map<String, Object>) service.listMailboxes(Map.of());
            @SuppressWarnings("unchecked")
            List<MailboxInfo> mailboxes = (List<MailboxInfo>) mailboxResult.get("mailboxes");
            assertTrue(mailboxes.stream().anyMatch(mailbox -> "INBOX".equalsIgnoreCase(mailbox.fullName())));

            @SuppressWarnings("unchecked")
            Map<String, Object> search = (Map<String, Object>) service.searchMessages(Map.of(
                    "mailbox", "INBOX",
                    "unreadOnly", true,
                    "subjectContains", "Formation",
                    "limit", 5
            ));
            @SuppressWarnings("unchecked")
            List<MessageSummary> summaries = (List<MessageSummary>) search.get("messages");

            assertEquals(1, summaries.size());
            MessageSummary summary = summaries.getFirst();
            assertTrue(summary.snippet().contains("Bonjour"));
            assertFalse(summary.attachments().isEmpty());

            MessageDetails details = (MessageDetails) service.getMessage(Map.of(
                    "mailbox", "INBOX",
                    "uid", summary.uid(),
                    "includeHtml", false
            ));
            assertEquals("Formation MCP", details.subject());
            assertEquals("", details.htmlBody());
            assertEquals("programme.pdf", details.attachments().getFirst().filename());

            AttachmentContent attachment = (AttachmentContent) service.getAttachment(Map.of(
                    "mailbox", "INBOX",
                    "uid", summary.uid(),
                    "attachmentId", details.attachments().getFirst().attachmentId()
            ));

            assertEquals("programme.pdf", attachment.filename());
            assertEquals("contenu pdf fictif", new String(Base64.getDecoder().decode(attachment.contentBase64()), StandardCharsets.UTF_8));
        } finally {
            greenMail.stop();
        }
    }

    private static MimeMessage messageWithAttachment() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("client@example.com"));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse("formation@example.com"));
        message.setSubject("Formation MCP", "UTF-8");

        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Bonjour, je souhaite recevoir le programme.", "UTF-8");
        mixed.addBodyPart(text);

        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(new ByteArrayDataSource("contenu pdf fictif".getBytes(StandardCharsets.UTF_8), "application/pdf")));
        attachment.setFileName("programme.pdf");
        mixed.addBodyPart(attachment);

        message.setContent(mixed);
        message.saveChanges();
        return message;
    }

    private static MailConfiguration configuration(int imapPort) {
        return new MailConfiguration(
                "default",
                new MailEndpoint("127.0.0.1", imapPort, ConnectionSecurity.NONE),
                new MailEndpoint("127.0.0.1", 2525, ConnectionSecurity.NONE),
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
