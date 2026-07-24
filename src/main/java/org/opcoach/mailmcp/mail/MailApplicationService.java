package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.audit.AuditEvent;
import org.opcoach.mailmcp.audit.AuditLogger;
import org.opcoach.mailmcp.config.MailConfiguration;
import org.opcoach.mailmcp.mcp.MailToolNames;
import org.opcoach.mailmcp.mcp.MailToolService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class MailApplicationService implements MailToolService {

    private final MailConfiguration configuration;
    private final JakartaMailSender sender;
    private final JakartaImapClient imapClient;
    private final MailQueryParser queryParser;
    private final AuditLogger auditLogger;

    public MailApplicationService(MailConfiguration configuration, String password) {
        this(configuration, password, AuditLogger.noop());
    }

    public MailApplicationService(MailConfiguration configuration, String password, AuditLogger auditLogger) {
        this.configuration = configuration;
        this.sender = new JakartaMailSender(configuration, password);
        this.imapClient = new JakartaImapClient(configuration, password);
        this.queryParser = new MailQueryParser(configuration.limits(), configuration.defaultIncomingMailbox());
        this.auditLogger = auditLogger;
    }

    @Override
    public Object sendEmail(Map<String, Object> arguments) {
        try {
            SendEmailCommand command = new SendEmailCommandParser(configuration.limits()).parse(arguments);
            SendEmailResult result = sender.send(command);
            auditLogger.record(AuditEvent.success(MailToolNames.SEND_EMAIL, configuration.sentMailbox(), result.messageId(), result.acceptedRecipients()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.SEND_EMAIL, configuration.sentMailbox()));
            throw exception;
        }
    }

    @Override
    public Object listMailboxes(Map<String, Object> arguments) {
        boolean includeSpecialUse = Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("includeSpecialUse", false)));
        try {
            Object result = Map.of("mailboxes", imapClient.listMailboxes(includeSpecialUse));
            auditLogger.record(AuditEvent.success(MailToolNames.LIST_MAILBOXES, null, null, List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.LIST_MAILBOXES, null));
            throw exception;
        }
    }

    @Override
    public Object searchMessages(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", configuration.defaultIncomingMailbox()));
        try {
            SearchMessagesQuery query = queryParser.search(arguments);
            if (!query.mailboxExplicit() && query.toContains() != null && query.fromContains() == null) {
                query = new SearchMessagesQuery(
                        configuration.sentMailbox(),
                        query.fromContains(),
                        query.toContains(),
                        query.subjectContains(),
                        query.since(),
                        query.until(),
                        query.unreadOnly(),
                        query.limit(),
                        query.beforeUid(),
                        false
                );
            }
            Object result = Map.of("messages", searchMessages(query));
            auditLogger.record(AuditEvent.success(MailToolNames.SEARCH_MESSAGES, query.mailbox(), null, List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.SEARCH_MESSAGES, mailbox));
            throw exception;
        }
    }

    @Override
    public Object getMessage(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", configuration.defaultIncomingMailbox()));
        try {
            GetMessageQuery query = queryParser.getMessage(arguments);
            Object result = imapClient.getMessage(query);
            auditLogger.record(AuditEvent.success(MailToolNames.GET_MESSAGE, query.mailbox(), Long.toString(query.uid()), List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.GET_MESSAGE, mailbox));
            throw exception;
        }
    }

    @Override
    public Object getAttachment(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", configuration.defaultIncomingMailbox()));
        try {
            GetAttachmentQuery query = queryParser.getAttachment(arguments);
            Object result = imapClient.getAttachment(query);
            auditLogger.record(AuditEvent.success(MailToolNames.GET_ATTACHMENT, query.mailbox(), Long.toString(query.uid()), List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.GET_ATTACHMENT, mailbox));
            throw exception;
        }
    }

    @Override
    public Object getAttachmentInfo(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", configuration.defaultIncomingMailbox()));
        try {
            GetAttachmentInfoQuery query = queryParser.getAttachmentInfo(arguments);
            Object result = Map.of("attachments", imapClient.getAttachmentInfo(query));
            auditLogger.record(AuditEvent.success(MailToolNames.GET_ATTACHMENT_INFO, query.mailbox(), Long.toString(query.uid()), List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.GET_ATTACHMENT_INFO, mailbox));
            throw exception;
        }
    }

    @Override
    public Object saveAttachment(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", configuration.defaultIncomingMailbox()));
        try {
            SaveAttachmentCommand command = queryParser.saveAttachment(arguments);
            SavedAttachment result = imapClient.saveAttachment(command);
            auditLogger.record(AuditEvent.success(MailToolNames.SAVE_ATTACHMENT, command.mailbox(), Long.toString(command.uid()), List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.SAVE_ATTACHMENT, mailbox));
            throw exception;
        }
    }

    @Override
    public Object moveMessage(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", configuration.defaultIncomingMailbox()));
        try {
            MoveMessageCommand command = queryParser.moveMessage(arguments);
            MoveMessageResult result = imapClient.moveMessage(command);
            auditLogger.record(AuditEvent.success(MailToolNames.MOVE_MESSAGE, command.mailbox(), Long.toString(command.uid()), List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.MOVE_MESSAGE, mailbox));
            throw exception;
        }
    }

    @Override
    public Object deleteMessage(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", configuration.defaultIncomingMailbox()));
        try {
            DeleteMessageCommand command = queryParser.deleteMessage(arguments);
            MoveMessageResult result = imapClient.deleteMessage(command, configuration.trashMailbox());
            auditLogger.record(AuditEvent.success(MailToolNames.DELETE_MESSAGE, command.mailbox(), Long.toString(command.uid()), List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.DELETE_MESSAGE, mailbox));
            throw exception;
        }
    }

    private List<MessageSummary> searchMessages(SearchMessagesQuery query) {
        if (query.mailboxExplicit() || configuration.incomingMailboxes().size() == 1 || configuration.sentMailbox().equals(query.mailbox())) {
            return imapClient.searchMessages(query);
        }
        if (query.beforeUid() != null) {
            throw new IllegalArgumentException("beforeUid requires an explicit mailbox when several incoming folders are configured.");
        }
        List<MessageSummary> messages = new ArrayList<>();
        for (String mailbox : configuration.incomingMailboxes()) {
            messages.addAll(imapClient.searchMessages(withMailbox(query, mailbox)));
        }
        return messages.stream()
                .sorted(Comparator.comparing(MessageSummary::receivedAt).reversed())
                .limit(query.limit())
                .toList();
    }

    private static SearchMessagesQuery withMailbox(SearchMessagesQuery query, String mailbox) {
        return new SearchMessagesQuery(
                mailbox,
                query.fromContains(),
                query.toContains(),
                query.subjectContains(),
                query.since(),
                query.until(),
                query.unreadOnly(),
                query.limit(),
                query.beforeUid(),
                true
        );
    }
}
