package org.opcoach.mailmcp.mcp;

import java.util.Map;

public interface MailToolService {

    Object sendEmail(Map<String, Object> arguments);

    Object listMailboxes(Map<String, Object> arguments);

    Object searchMessages(Map<String, Object> arguments);

    Object getMessage(Map<String, Object> arguments);

    Object getAttachment(Map<String, Object> arguments);

    Object moveMessage(Map<String, Object> arguments);

    Object deleteMessage(Map<String, Object> arguments);
}
