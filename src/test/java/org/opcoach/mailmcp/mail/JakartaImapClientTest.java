package org.opcoach.mailmcp.mail;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.activation.DataHandler;
import jakarta.mail.Flags;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
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

    @Test
    void searchesClosedReceivedDateRangeWithUidCursorPaging() throws Exception {
        ServerSetup imap = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        GreenMail greenMail = new GreenMail(imap);
        greenMail.start();
        try {
            GreenMailUser user = greenMail.setUser("training@example.com", "training@example.com", "secret");
            MailFolder inbox = greenMail.getManagers().getImapHostManager().getInbox(user);
            appendDated(inbox, "Before range", LocalDate.of(2024, 1, 31));
            appendDated(inbox, "First day", LocalDate.of(2024, 2, 1));
            appendDated(inbox, "Middle day", LocalDate.of(2024, 2, 2));
            appendDated(inbox, "Last day", LocalDate.of(2024, 2, 3));
            appendDated(inbox, "After range", LocalDate.of(2024, 2, 4));

            MailApplicationService service = new MailApplicationService(configuration(greenMail.getImap().getPort()), "secret");

            @SuppressWarnings("unchecked")
            Map<String, Object> firstPage = (Map<String, Object>) service.searchMessages(Map.of(
                    "mailbox", "INBOX",
                    "since", "2024-02-01",
                    "until", "2024-02-03",
                    "limit", 2
            ));
            @SuppressWarnings("unchecked")
            List<MessageSummary> firstSummaries = (List<MessageSummary>) firstPage.get("messages");

            assertEquals(2, firstSummaries.size());
            assertEquals("Last day", firstSummaries.get(0).subject());
            assertEquals("Middle day", firstSummaries.get(1).subject());

            @SuppressWarnings("unchecked")
            Map<String, Object> secondPage = (Map<String, Object>) service.searchMessages(Map.of(
                    "mailbox", "INBOX",
                    "since", "2024-02-01",
                    "until", "2024-02-03",
                    "limit", 2,
                    "beforeUid", firstSummaries.getLast().uid()
            ));
            @SuppressWarnings("unchecked")
            List<MessageSummary> secondSummaries = (List<MessageSummary>) secondPage.get("messages");

            assertEquals(1, secondSummaries.size());
            assertEquals("First day", secondSummaries.getFirst().subject());
        } finally {
            greenMail.stop();
        }
    }

    @Test
    void movesAndDeletesMessagesByUid() throws Exception {
        ServerSetup imap = new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        GreenMail greenMail = new GreenMail(imap);
        greenMail.start();
        try {
            GreenMailUser user = greenMail.setUser("training@example.com", "training@example.com", "secret");
            user.deliver(simpleMessage("Move me", "client@example.com", "training@example.com"));
            user.deliver(simpleMessage("Delete me", "client@example.com", "training@example.com"));
            MailApplicationService service = new MailApplicationService(configuration(greenMail.getImap().getPort()), "secret");

            String moveUid = uidForSubject(service, "INBOX", "Move me");
            MoveMessageResult moved = (MoveMessageResult) service.moveMessage(Map.of(
                    "mailbox", "INBOX",
                    "uid", moveUid,
                    "targetMailbox", "Archive"
            ));

            assertEquals("moved", moved.action());
            assertEquals("Archive", moved.targetMailbox());
            assertEquals("Move me", subjectInMailbox(service, "Archive", "Move me"));

            String deleteUid = uidForSubject(service, "INBOX", "Delete me");
            MoveMessageResult deleted = (MoveMessageResult) service.deleteMessage(Map.of(
                    "mailbox", "INBOX",
                    "uid", deleteUid
            ));

            assertEquals("deleted", deleted.action());
            assertEquals("Trash", deleted.targetMailbox());
            assertEquals("Delete me", subjectInMailbox(service, "Trash", "Delete me"));
        } finally {
            greenMail.stop();
        }
    }

    private static void appendDated(MailFolder folder, String subject, LocalDate receivedDate) throws Exception {
        folder.appendMessage(
                simpleMessage(subject, "client@example.com", "training@example.com"),
                new Flags(),
                Date.from(receivedDate.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant())
        );
    }

    private static String uidForSubject(MailApplicationService service, String mailbox, String subject) {
        MessageSummary summary = summaryForSubject(service, mailbox, subject);
        return summary.uid();
    }

    private static String subjectInMailbox(MailApplicationService service, String mailbox, String subject) {
        return summaryForSubject(service, mailbox, subject).subject();
    }

    private static MessageSummary summaryForSubject(MailApplicationService service, String mailbox, String subject) {
        @SuppressWarnings("unchecked")
        Map<String, Object> search = (Map<String, Object>) service.searchMessages(Map.of(
                "mailbox", mailbox,
                "subjectContains", subject,
                "limit", 5
        ));
        @SuppressWarnings("unchecked")
        List<MessageSummary> summaries = (List<MessageSummary>) search.get("messages");
        assertEquals(1, summaries.size());
        return summaries.getFirst();
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
                "Trash",
                MailLimits.DEFAULTS,
                Path.of("config.properties"),
                Path.of("audit.log")
        );
    }
}
