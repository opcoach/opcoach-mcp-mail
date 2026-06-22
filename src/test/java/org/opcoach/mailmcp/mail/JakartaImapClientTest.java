package org.opcoach.mailmcp.mail;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.activation.DataHandler;
import jakarta.mail.Folder;
import jakarta.mail.Store;
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
            GreenMailUser user = greenMail.setUser("training@example.com", "training@example.com", "secret");
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
                    "subjectContains", "Training",
                    "limit", 5
            ));
            @SuppressWarnings("unchecked")
            List<MessageSummary> summaries = (List<MessageSummary>) search.get("messages");

            assertEquals(1, summaries.size());
            MessageSummary summary = summaries.getFirst();
            assertTrue(summary.snippet().contains("Hello"));
            assertFalse(summary.attachments().isEmpty());

            MessageDetails details = (MessageDetails) service.getMessage(Map.of(
                    "mailbox", "INBOX",
                    "uid", summary.uid(),
                    "includeHtml", false
            ));
            assertEquals("MCP Training", details.subject());
            assertEquals("", details.htmlBody());
            assertEquals("program.pdf", details.attachments().getFirst().filename());

            AttachmentContent attachment = (AttachmentContent) service.getAttachment(Map.of(
                    "mailbox", "INBOX",
                    "uid", summary.uid(),
                    "attachmentId", details.attachments().getFirst().attachmentId()
            ));

            assertEquals("program.pdf", attachment.filename());
            assertEquals("fake pdf content", new String(Base64.getDecoder().decode(attachment.contentBase64()), StandardCharsets.UTF_8));
        } finally {
            greenMail.stop();
        }
    }

    @Test
    void searchesNewestMessagesFromMailboxTail() throws Exception {
        ServerSetup imap = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        GreenMail greenMail = new GreenMail(imap);
        greenMail.start();
        try {
            GreenMailUser user = greenMail.setUser("training@example.com", "training@example.com", "secret");
            user.deliver(simpleMessage("Old message", "client@example.com", "training@example.com"));
            user.deliver(simpleMessage("Middle message", "client@example.com", "training@example.com"));
            user.deliver(simpleMessage("Newest message", "client@example.com", "training@example.com"));
            MailApplicationService service = new MailApplicationService(configuration(greenMail.getImap().getPort()), "secret");

            @SuppressWarnings("unchecked")
            Map<String, Object> search = (Map<String, Object>) service.searchMessages(Map.of(
                    "mailbox", "INBOX",
                    "limit", 2
            ));
            @SuppressWarnings("unchecked")
            List<MessageSummary> summaries = (List<MessageSummary>) search.get("messages");

            assertEquals(2, summaries.size());
            assertEquals("Newest message", summaries.get(0).subject());
            assertEquals("Middle message", summaries.get(1).subject());
            assertTrue(summaries.get(0).to().contains("training@example.com"));
        } finally {
            greenMail.stop();
        }
    }

    @Test
    void searchesSentMailboxByRecipientWhenMailboxIsImplicit() throws Exception {
        ServerSetup imap = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        GreenMail greenMail = new GreenMail(imap);
        greenMail.start();
        try {
            greenMail.setUser("training@example.com", "training@example.com", "secret");
            MailConfiguration configuration = configuration(greenMail.getImap().getPort());
            appendToFolder(
                    configuration,
                    "secret",
                    "Sent",
                    simpleMessage("Sent older", "training@example.com", "client@example.com"),
                    simpleMessage("Other recipient", "training@example.com", "other@example.com"),
                    simpleMessage("Sent newest", "training@example.com", "client@example.com")
            );
            MailApplicationService service = new MailApplicationService(configuration, "secret");

            @SuppressWarnings("unchecked")
            Map<String, Object> search = (Map<String, Object>) service.searchMessages(Map.of(
                    "toContains", "client@example.com",
                    "limit", 5
            ));
            @SuppressWarnings("unchecked")
            List<MessageSummary> summaries = (List<MessageSummary>) search.get("messages");

            assertEquals(2, summaries.size());
            assertEquals("Sent newest", summaries.get(0).subject());
            assertEquals("Sent older", summaries.get(1).subject());
            assertTrue(summaries.stream().allMatch(summary -> summary.to().contains("client@example.com")));
        } finally {
            greenMail.stop();
        }
    }

    private static void appendToFolder(
            MailConfiguration configuration,
            String password,
            String mailbox,
            MimeMessage... messages
    ) throws Exception {
        Session session = Session.getInstance(new Properties());
        try (Store store = session.getStore("imap")) {
            store.connect(configuration.imap().host(), configuration.imap().port(), configuration.username(), password);
            Folder folder = store.getFolder(mailbox);
            if (!folder.exists()) {
                folder.create(Folder.HOLDS_MESSAGES);
            }
            folder.open(Folder.READ_WRITE);
            try {
                folder.appendMessages(messages);
            } finally {
                folder.close(false);
            }
        }
    }

    private static MimeMessage simpleMessage(String subject, String from, String to) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject, "UTF-8");
        message.setText("Body for " + subject, "UTF-8");
        message.saveChanges();
        return message;
    }

    private static MimeMessage messageWithAttachment() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("client@example.com"));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse("training@example.com"));
        message.setSubject("MCP Training", "UTF-8");

        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Hello, I would like to receive the program.", "UTF-8");
        mixed.addBodyPart(text);

        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setDataHandler(new DataHandler(new ByteArrayDataSource("fake pdf content".getBytes(StandardCharsets.UTF_8), "application/pdf")));
        attachment.setFileName("program.pdf");
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
                "training@example.com",
                "training@example.com",
                "MCP Training",
                "Sent",
                MailLimits.DEFAULTS,
                Path.of("config.properties"),
                Path.of("audit.log")
        );
    }
}
