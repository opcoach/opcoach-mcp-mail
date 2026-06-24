package org.opcoach.mailmcp.mail;

import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.opcoach.mailmcp.config.MailConfiguration;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class JakartaMailSender {

    private final MailConfiguration configuration;
    private final String password;

    public JakartaMailSender(MailConfiguration configuration, String password) {
        this.configuration = configuration;
        this.password = password;
    }

    public SendEmailResult send(SendEmailCommand command) {
        try {
            Session smtpSession = Session.getInstance(JakartaMailSessions.smtpProperties(configuration.smtp()));
            MimeMessage message = buildMessage(smtpSession, command);
            sendMessage(smtpSession, message);
            String copyStatus = appendToSent(message);
            return new SendEmailResult(
                    "sent",
                    message.getMessageID(),
                    command.allRecipients(),
                    command.html(),
                    copyStatus,
                    configuration.sentMailbox()
            );
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new MailOperationException("SMTP/MIME send failed: " + exception.getMessage(), exception);
        }
    }

    private MimeMessage buildMessage(Session session, SendEmailCommand command) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(configuration.fromAddress(), configuration.fromName(), "UTF-8"));
        if (!configuration.replyToAddress().isBlank()) {
            message.setReplyTo(InternetAddress.parse(configuration.replyToAddress(), false));
        }
        addRecipients(message, Message.RecipientType.TO, command.to());
        addRecipients(message, Message.RecipientType.CC, command.cc());
        addRecipients(message, Message.RecipientType.BCC, command.bcc());
        message.setSubject(command.subject(), "UTF-8");
        message.setSentDate(Date.from(Instant.now()));
        setMessageContent(message, command);
        message.saveChanges();
        return message;
    }

    private void sendMessage(Session session, MimeMessage message) throws MessagingException {
        String protocol = JakartaMailSessions.smtpProtocol(configuration.smtp());
        try (Transport transport = session.getTransport(protocol)) {
            transport.connect(configuration.smtp().host(), configuration.smtp().port(), configuration.username(), password);
            transport.sendMessage(message, message.getAllRecipients());
        }
    }

    private void setMessageContent(MimeMessage message, SendEmailCommand command) throws MessagingException {
        if (command.attachments().isEmpty()) {
            if (command.html()) {
                message.setContent(alternative(command.textBody(), command.htmlBody()));
            } else {
                message.setText(command.textBody(), "UTF-8");
            }
            return;
        }

        MimeBodyPart body = new MimeBodyPart();
        if (command.html()) {
            body.setContent(alternative(command.textBody(), command.htmlBody()));
        } else {
            body.setText(command.textBody(), "UTF-8");
        }

        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(body);
        for (AttachmentPayload attachment : command.attachments()) {
            mixed.addBodyPart(attachmentPart(attachment));
        }
        message.setContent(mixed);
    }

    private MimeMultipart alternative(String textBody, String htmlBody) throws MessagingException {
        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart text = new MimeBodyPart();
        text.setText(textBody == null || textBody.isBlank() ? htmlToText(htmlBody) : textBody, "UTF-8");
        alternative.addBodyPart(text);

        MimeBodyPart html = new MimeBodyPart();
        html.setContent(htmlBody, "text/html; charset=UTF-8");
        alternative.addBodyPart(html);
        return alternative;
    }

    private MimeBodyPart attachmentPart(AttachmentPayload attachment) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        ByteArrayDataSource dataSource = new ByteArrayDataSource(attachment.content(), attachment.contentType());
        part.setDataHandler(new DataHandler(dataSource));
        part.setFileName(attachment.filename());
        part.setDisposition(Message.ATTACHMENT);
        return part;
    }

    private String appendToSent(MimeMessage message) {
        Session imapSession = Session.getInstance(JakartaMailSessions.imapProperties(configuration.imap()));
        String protocol = JakartaMailSessions.imapProtocol(configuration.imap());
        try (Store store = imapSession.getStore(protocol)) {
            store.connect(configuration.imap().host(), configuration.imap().port(), configuration.username(), password);
            Folder sent = store.getFolder(configuration.sentMailbox());
            if (!sent.exists()) {
                sent.create(Folder.HOLDS_MESSAGES);
            }
            sent.open(Folder.READ_WRITE);
            try {
                sent.appendMessages(new Message[]{message});
                return "saved";
            } finally {
                sent.close(false);
            }
        } catch (MessagingException exception) {
            return "failed: " + exception.getClass().getSimpleName();
        }
    }

    private static void addRecipients(MimeMessage message, Message.RecipientType type, List<String> recipients) throws MessagingException {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        for (String recipient : recipients) {
            Address[] addresses = InternetAddress.parse(recipient, false);
            message.addRecipients(type, addresses);
        }
    }

    private static String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
