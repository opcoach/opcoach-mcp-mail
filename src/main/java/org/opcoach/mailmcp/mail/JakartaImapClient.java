package org.opcoach.mailmcp.mail;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.opcoach.mailmcp.config.MailConfiguration;
import org.opcoach.mailmcp.security.DataLimiter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public final class JakartaImapClient {

    private final MailConfiguration configuration;
    private final String password;
    private final MimeMessageExtractor extractor = new MimeMessageExtractor();

    public JakartaImapClient(MailConfiguration configuration, String password) {
        this.configuration = configuration;
        this.password = password;
    }

    public List<MailboxInfo> listMailboxes(boolean includeSpecialUse) {
        try (Store store = connect()) {
            Folder defaultFolder = store.getDefaultFolder();
            Folder[] folders = defaultFolder.list("*");
            List<MailboxInfo> results = new ArrayList<>();
            for (Folder folder : folders) {
                results.add(mailboxInfo(folder, includeSpecialUse));
            }
            return results.stream()
                    .sorted(Comparator.comparing(MailboxInfo::fullName))
                    .toList();
        } catch (MessagingException exception) {
            throw new MailOperationException("Impossible de lister les dossiers IMAP: " + exception.getMessage(), exception);
        }
    }

    public List<MessageSummary> searchMessages(SearchMessagesQuery query) {
        try (Store store = connect()) {
            Folder folder = open(store, query.mailbox(), Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = uidFolder(folder);
                Message[] messages = search(folder, query);
                return Arrays.stream(messages)
                        .sorted(Comparator.comparing(JakartaImapClient::messageDate).reversed())
                        .limit(query.limit())
                        .map(message -> summary(uidFolder, message))
                        .toList();
            } finally {
                folder.close(false);
            }
        } catch (MessagingException exception) {
            throw new MailOperationException("Impossible de rechercher les messages IMAP: " + exception.getMessage(), exception);
        }
    }

    public MessageDetails getMessage(GetMessageQuery query) {
        try (Store store = connect()) {
            Folder folder = open(store, query.mailbox(), Folder.READ_ONLY);
            try {
                Message message = messageByUid(folder, query.uid());
                MimeMessageExtractor.ExtractedMessage extracted = extractor.extract(
                        message,
                        query.maxBodyBytes(),
                        query.includeHtml() ? configuration.limits().maxHtmlBytes() : 1
                );
                return new MessageDetails(
                        Long.toString(query.uid()),
                        query.mailbox(),
                        safeSubject(message),
                        addresses(message.getFrom()),
                        addressList(message.getAllRecipients()),
                        format(messageDate(message)),
                        !message.isSet(Flags.Flag.SEEN),
                        DataLimiter.truncateUtf8(extracted.textBody(), query.maxBodyBytes()),
                        query.includeHtml() ? DataLimiter.truncateUtf8(extracted.htmlBody(), configuration.limits().maxHtmlBytes()) : "",
                        extracted.attachments()
                );
            } finally {
                folder.close(false);
            }
        } catch (MessagingException exception) {
            throw new MailOperationException("Impossible de lire le message IMAP: " + exception.getMessage(), exception);
        }
    }

    public AttachmentContent getAttachment(GetAttachmentQuery query) {
        try (Store store = connect()) {
            Folder folder = open(store, query.mailbox(), Folder.READ_ONLY);
            try {
                Message message = messageByUid(folder, query.uid());
                return extractor.attachment(message, query.attachmentId(), query.maxBytes());
            } finally {
                folder.close(false);
            }
        } catch (MessagingException exception) {
            throw new MailOperationException("Impossible de récupérer la pièce jointe IMAP: " + exception.getMessage(), exception);
        }
    }

    private Store connect() throws MessagingException {
        Session session = Session.getInstance(JakartaMailSessions.imapProperties(configuration.imap()));
        Store store = session.getStore(JakartaMailSessions.imapProtocol(configuration.imap()));
        store.connect(configuration.imap().host(), configuration.imap().port(), configuration.username(), password);
        return store;
    }

    private static Folder open(Store store, String mailbox, int mode) throws MessagingException {
        Folder folder = store.getFolder(mailbox);
        if (!folder.exists()) {
            throw new IllegalArgumentException("Dossier IMAP introuvable: " + mailbox);
        }
        folder.open(mode);
        return folder;
    }

    private static Message[] search(Folder folder, SearchMessagesQuery query) throws MessagingException {
        SearchTerm term = searchTerm(query);
        return term == null ? folder.getMessages() : folder.search(term);
    }

    private static SearchTerm searchTerm(SearchMessagesQuery query) {
        List<SearchTerm> terms = new ArrayList<>();
        if (query.unreadOnly()) {
            terms.add(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        }
        if (query.subjectContains() != null) {
            terms.add(new SubjectTerm(query.subjectContains()));
        }
        if (query.fromContains() != null) {
            terms.add(new FromStringTerm(query.fromContains()));
        }
        if (query.toContains() != null) {
            terms.add(new RecipientContainsTerm(query.toContains()));
        }
        if (query.since() != null) {
            terms.add(new ReceivedDateTerm(ComparisonTerm.GE, date(query.since())));
        }
        if (terms.isEmpty()) {
            return null;
        }
        if (terms.size() == 1) {
            return terms.getFirst();
        }
        return new AndTerm(terms.toArray(SearchTerm[]::new));
    }

    private MailboxInfo mailboxInfo(Folder folder, boolean includeSpecialUse) {
        int count = 0;
        try {
            if ((folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
                folder.open(Folder.READ_ONLY);
                count = folder.getMessageCount();
                folder.close(false);
            }
        } catch (MessagingException ignored) {
            count = 0;
        }
        List<String> attributes = List.of();
        if (includeSpecialUse && folder instanceof IMAPFolder imapFolder) {
            try {
                attributes = Arrays.asList(imapFolder.getAttributes());
            } catch (MessagingException ignored) {
                attributes = List.of();
            }
        }
        return new MailboxInfo(folder.getName(), folder.getFullName(), count, attributes);
    }

    private MessageSummary summary(UIDFolder uidFolder, Message message) {
        try {
            long uid = uidFolder.getUID(message);
            MimeMessageExtractor.ExtractedMessage extracted = extractor.extract(
                    message,
                    configuration.limits().snippetBytes(),
                    1
            );
            return new MessageSummary(
                    Long.toString(uid),
                    safeSubject(message),
                    addresses(message.getFrom()),
                    format(messageDate(message)),
                    !message.isSet(Flags.Flag.SEEN),
                    DataLimiter.truncateUtf8(extracted.textBody(), configuration.limits().snippetBytes()),
                    extracted.attachments()
            );
        } catch (MessagingException exception) {
            throw new MailOperationException("Impossible de résumer un message IMAP: " + exception.getMessage(), exception);
        }
    }

    private static Message messageByUid(Folder folder, long uid) throws MessagingException {
        Message message = uidFolder(folder).getMessageByUID(uid);
        if (message == null) {
            throw new IllegalArgumentException("Message IMAP introuvable pour l'UID: " + uid);
        }
        return message;
    }

    private static UIDFolder uidFolder(Folder folder) {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder;
        }
        throw new IllegalArgumentException("Le serveur IMAP ne supporte pas les UID pour ce dossier.");
    }

    private static String safeSubject(Message message) throws MessagingException {
        String subject = message.getSubject();
        return subject == null ? "" : subject;
    }

    private static String addresses(Address[] addresses) {
        List<String> values = addressList(addresses);
        return values.isEmpty() ? "" : String.join(", ", values);
    }

    private static List<String> addressList(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return List.of();
        }
        return Arrays.stream(addresses)
                .map(JakartaImapClient::address)
                .toList();
    }

    private static String address(Address address) {
        if (address instanceof InternetAddress internetAddress) {
            return internetAddress.toUnicodeString();
        }
        return address.toString();
    }

    private static Date messageDate(Message message) {
        try {
            Date receivedDate = message.getReceivedDate();
            if (receivedDate != null) {
                return receivedDate;
            }
            Date sentDate = message.getSentDate();
            return sentDate == null ? new Date(0) : sentDate;
        } catch (MessagingException exception) {
            return new Date(0);
        }
    }

    private static Date date(LocalDate value) {
        return Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static String format(Date date) {
        return DateTimeFormatter.ISO_INSTANT.format(date.toInstant());
    }

    private static final class RecipientContainsTerm extends SearchTerm {
        private final String needle;

        private RecipientContainsTerm(String needle) {
            this.needle = needle.toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public boolean match(Message message) {
            try {
                Address[] recipients = message.getAllRecipients();
                if (recipients == null) {
                    return false;
                }
                return Arrays.stream(recipients)
                        .map(Address::toString)
                        .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                        .anyMatch(value -> value.contains(needle));
            } catch (MessagingException exception) {
                return false;
            }
        }
    }
}
