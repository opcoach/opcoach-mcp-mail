package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.audit.AuditEvent;
import org.opcoach.mailmcp.audit.AuditLogger;
import org.opcoach.mailmcp.config.MailConfiguration;
import org.opcoach.mailmcp.mcp.MailToolNames;
import org.opcoach.mailmcp.mcp.MailToolService;

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
        this.queryParser = new MailQueryParser(configuration.limits());
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
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", "INBOX"));
        try {
            SearchMessagesQuery query = queryParser.search(arguments);
            if (!query.mailboxExplicit() && query.toContains() != null && query.fromContains() == null) {
                query = new SearchMessagesQuery(
                        configuration.sentMailbox(),
                        query.fromContains(),
                        query.toContains(),
                        query.subjectContains(),
                        query.since(),
                        query.unreadOnly(),
                        query.limit(),
                        false
                );
            }
            Object result = Map.of("messages", imapClient.searchMessages(query));
            auditLogger.record(AuditEvent.success(MailToolNames.SEARCH_MESSAGES, query.mailbox(), null, List.of()));
            return result;
        } catch (RuntimeException exception) {
            auditLogger.record(AuditEvent.failure(MailToolNames.SEARCH_MESSAGES, mailbox));
            throw exception;
        }
    }

    @Override
    public Object getMessage(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", "INBOX"));
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
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", "INBOX"));
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
    public Object moveMessage(Map<String, Object> arguments) {
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", "INBOX"));
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
        String mailbox = String.valueOf(arguments.getOrDefault("mailbox", "INBOX"));
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
}
