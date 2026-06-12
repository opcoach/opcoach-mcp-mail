package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.config.MailConfiguration;
import org.opcoach.mailmcp.mcp.MailToolService;

import java.util.Map;

public final class MailApplicationService implements MailToolService {

    private final MailConfiguration configuration;
    private final JakartaMailSender sender;

    public MailApplicationService(MailConfiguration configuration, String password) {
        this.configuration = configuration;
        this.sender = new JakartaMailSender(configuration, password);
    }

    @Override
    public Object sendEmail(Map<String, Object> arguments) {
        SendEmailCommand command = new SendEmailCommandParser(configuration.limits()).parse(arguments);
        return sender.send(command);
    }

    @Override
    public Object listMailboxes(Map<String, Object> arguments) {
        throw new MailOperationException("listMailboxes sera disponible avec l'itération IMAP.");
    }

    @Override
    public Object searchMessages(Map<String, Object> arguments) {
        throw new MailOperationException("searchMessages sera disponible avec l'itération IMAP.");
    }

    @Override
    public Object getMessage(Map<String, Object> arguments) {
        throw new MailOperationException("getMessage sera disponible avec l'itération IMAP.");
    }

    @Override
    public Object getAttachment(Map<String, Object> arguments) {
        throw new MailOperationException("getAttachment sera disponible avec l'itération IMAP.");
    }
}
