package org.opcoach.mailmcp.mcp;

import org.opcoach.mailmcp.config.ConfigurationException;

import java.util.Map;

public final class UnavailableMailToolService implements MailToolService {

    private static ConfigurationException unavailable() {
        return new ConfigurationException("Mail service is not initialized. Check the local configuration and password.");
    }

    @Override
    public Object sendEmail(Map<String, Object> arguments) {
        throw unavailable();
    }

    @Override
    public Object listMailboxes(Map<String, Object> arguments) {
        throw unavailable();
    }

    @Override
    public Object searchMessages(Map<String, Object> arguments) {
        throw unavailable();
    }

    @Override
    public Object getMessage(Map<String, Object> arguments) {
        throw unavailable();
    }

    @Override
    public Object getAttachment(Map<String, Object> arguments) {
        throw unavailable();
    }
}
