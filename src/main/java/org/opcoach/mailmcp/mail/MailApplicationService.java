package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.config.MailConfiguration;
import org.opcoach.mailmcp.mcp.MailToolService;

import java.util.Map;

public final class MailApplicationService implements MailToolService {

    private final MailConfiguration configuration;
    private final JakartaMailSender sender;
    private final JakartaImapClient imapClient;
    private final MailQueryParser queryParser;

    public MailApplicationService(MailConfiguration configuration, String password) {
        this.configuration = configuration;
        this.sender = new JakartaMailSender(configuration, password);
        this.imapClient = new JakartaImapClient(configuration, password);
        this.queryParser = new MailQueryParser(configuration.limits());
    }

    @Override
    public Object sendEmail(Map<String, Object> arguments) {
        SendEmailCommand command = new SendEmailCommandParser(configuration.limits()).parse(arguments);
        return sender.send(command);
    }

    @Override
    public Object listMailboxes(Map<String, Object> arguments) {
        boolean includeSpecialUse = Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("includeSpecialUse", false)));
        return Map.of("mailboxes", imapClient.listMailboxes(includeSpecialUse));
    }

    @Override
    public Object searchMessages(Map<String, Object> arguments) {
        SearchMessagesQuery query = queryParser.search(arguments);
        return Map.of("messages", imapClient.searchMessages(query));
    }

    @Override
    public Object getMessage(Map<String, Object> arguments) {
        return imapClient.getMessage(queryParser.getMessage(arguments));
    }

    @Override
    public Object getAttachment(Map<String, Object> arguments) {
        return imapClient.getAttachment(queryParser.getAttachment(arguments));
    }
}
