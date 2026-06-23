package org.opcoach.mailmcp.mail;

import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
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
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.RecipientStringTerm;
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
    private Store cachedStore;

    public JakartaImapClient(MailConfiguration configuration, String password) {
        this.configuration = configuration;
        this.password = password;
    }

    public synchronized List<MailboxInfo> listMailboxes(boolean includeSpecialUse) {
        try {
            Store store = store();
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
            invalidateStore();
            throw new MailOperationException("Unable to list IMAP folders: " + exception.getMessage(), exception);
        }
    }

    public synchronized List<MessageSummary> searchMessages(SearchMessagesQuery query) {
        try {
            Store store = store();
            Folder folder = open(store, query.mailbox(), Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = uidFolder(folder);
                Message[] messages = latestMessages(folder, query);
                prefetchSummaryFields(folder, messages);
                return Arrays.stream(messages)
                        .map(message -> summary(uidFolder, message))
                        .toList();
            } finally {
                folder.close(false);
            }
        } catch (MessagingException exception) {
            invalidateStore();
            throw new MailOperationException("Unable to search IMAP messages: " + exception.getMessage(), exception);
        }
    }

    public synchronized MessageDetails getMessage(GetMessageQuery query) {
        try {
            Store store = store();
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
            invalidateStore();
            throw new MailOperationException("Unable to read the IMAP message: " + exception.getMessage(), exception);
        }
    }

    public synchronized AttachmentContent getAttachment(GetAttachmentQuery query) {
        try {
            Store store = store();
            Folder folder = open(store, query.mailbox(), Folder.READ_ONLY);
            try {
                Message message = messageByUid(folder, query.uid());
                return extractor.attachment(message, query.attachmentId(), query.maxBytes());
            } finally {
                folder.close(false);
            }
        } catch (MessagingException exception) {
            invalidateStore();
            throw new MailOperationException("Unable to retrieve the IMAP attachment: " + exception.getMessage(), exception);
        }
    }

    public synchronized MoveMessageResult moveMessage(MoveMessageCommand command) {
        return moveMessage(command, "moved");
    }

    public synchronized MoveMessageResult deleteMessage(DeleteMessageCommand command, String trashMailbox) {
        return moveMessage(new MoveMessageCommand(command.mailbox(), command.uid(), trashMailbox), "deleted");
    }

    private MoveMessageResult moveMessage(MoveMessageCommand command, String action) {
        try {
            Store store = store();
            Folder source = open(store, command.mailbox(), Folder.READ_WRITE);
            try {
                Message message = messageByUid(source, command.uid());
                Folder target = targetFolder(store, command.targetMailbox());
                move(source, message, target);
                return new MoveMessageResult(
                        Long.toString(command.uid()),
                        command.mailbox(),
                        command.targetMailbox(),
                        action
                );
            } finally {
                source.close(false);
            }
        } catch (MessagingException exception) {
            invalidateStore();
            throw new MailOperationException("Unable to move the IMAP message: " + exception.getMessage(), exception);
        }
    }

    private Store store() throws MessagingException {
        if (cachedStore != null && cachedStore.isConnected()) {
            return cachedStore;
        }
        closeCachedStore();
        cachedStore = connect();
        return cachedStore;
    }

    private Store connect() throws MessagingException {
        Session session = Session.getInstance(JakartaMailSessions.imapProperties(configuration.imap()));
        Store store = session.getStore(JakartaMailSessions.imapProtocol(configuration.imap()));
        store.connect(configuration.imap().host(), configuration.imap().port(), configuration.username(), password);
        return store;
    }

    private void invalidateStore() {
        closeCachedStore();
        cachedStore = null;
    }

    private void closeCachedStore() {
        if (cachedStore == null) {
            return;
        }
        try {
            cachedStore.close();
        } catch (MessagingException ignored) {
            // Best effort cleanup before the next connection attempt.
        }
    }

    private static Folder open(Store store, String mailbox, int mode) throws MessagingException {
        Folder folder = store.getFolder(mailbox);
        if (!folder.exists()) {
            throw new IllegalArgumentException("IMAP folder not found: " + mailbox);
        }
        folder.open(mode);
        return folder;
    }

    private static Folder targetFolder(Store store, String mailbox) throws MessagingException {
        Folder folder = store.getFolder(mailbox);
        if (!folder.exists()) {
            folder.create(Folder.HOLDS_MESSAGES);
        }
        if (!folder.exists()) {
            throw new IllegalArgumentException("IMAP target folder not found: " + mailbox);
        }
        return folder;
    }

    private static void move(Folder source, Message message, Folder target) throws MessagingException {
        if (source.getFullName().equals(target.getFullName())) {
            throw new IllegalArgumentException("Source and target IMAP folders are identical: " + source.getFullName());
        }
        if (source instanceof IMAPFolder imapFolder) {
            imapFolder.moveMessages(new Message[]{message}, target);
            return;
        }
        throw new MailOperationException("The IMAP provider does not support server-side message moves.");
    }

    private static Message[] latestMessages(Folder folder, SearchMessagesQuery query) throws MessagingException {
        SearchTerm term = searchTerm(query);
        Message[] matches;
        if (term == null) {
            int messageCount = folder.getMessageCount();
            if (messageCount <= 0) {
                return new Message[0];
            }
            int start = Math.max(1, messageCount - query.limit() + 1);
            matches = folder.getMessages(start, messageCount);
        } else {
            matches = search(folder, query);
            if (matches.length > query.limit()) {
                matches = Arrays.copyOfRange(matches, matches.length - query.limit(), matches.length);
            }
        }
        reverse(matches);
        return matches;
    }

    private static Message[] search(Folder folder, SearchMessagesQuery query) throws MessagingException {
        SearchTerm term = searchTerm(query);
        return term == null ? new Message[0] : folder.search(term);
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
            terms.add(recipientTerm(query.toContains()));
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

    private static SearchTerm recipientTerm(String value) {
        return new OrTerm(
                new OrTerm(
                        new RecipientStringTerm(Message.RecipientType.TO, value),
                        new RecipientStringTerm(Message.RecipientType.CC, value)
                ),
                new RecipientStringTerm(Message.RecipientType.BCC, value)
        );
    }

    private static void reverse(Message[] messages) {
        for (int left = 0, right = messages.length - 1; left < right; left++, right--) {
            Message current = messages[left];
            messages[left] = messages[right];
            messages[right] = current;
        }
    }

    private static void prefetchSummaryFields(Folder folder, Message[] messages) throws MessagingException {
        if (messages.length == 0) {
            return;
        }
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(FetchProfile.Item.CONTENT_INFO);
        profile.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(messages, profile);
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
                    addresses(message.getAllRecipients()),
                    format(messageDate(message)),
                    !message.isSet(Flags.Flag.SEEN),
                    DataLimiter.truncateUtf8(extracted.textBody(), configuration.limits().snippetBytes()),
                    extracted.attachments()
            );
        } catch (MessagingException exception) {
            throw new MailOperationException("Unable to summarize an IMAP message: " + exception.getMessage(), exception);
        }
    }

    private static Message messageByUid(Folder folder, long uid) throws MessagingException {
        Message message = uidFolder(folder).getMessageByUID(uid);
        if (message == null) {
            throw new IllegalArgumentException("IMAP message not found for UID: " + uid);
        }
        return message;
    }

    private static UIDFolder uidFolder(Folder folder) {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder;
        }
        throw new IllegalArgumentException("The IMAP server does not support UIDs for this folder.");
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

}
