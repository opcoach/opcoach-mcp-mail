package org.opcoach.mailmcp.mcp;

import java.util.List;

public final class MailToolNames {

    public static final String SEND_EMAIL = "sendEmail";
    public static final String LIST_MAILBOXES = "listMailboxes";
    public static final String SEARCH_MESSAGES = "searchMessages";
    public static final String GET_MESSAGE = "getMessage";
    public static final String GET_ATTACHMENT = "getAttachment";
    public static final String GET_ATTACHMENT_INFO = "getAttachmentInfo";
    public static final String SAVE_ATTACHMENT = "saveAttachment";
    public static final String MOVE_MESSAGE = "moveMessage";
    public static final String DELETE_MESSAGE = "deleteMessage";

    public static final List<String> ALL = List.of(
            SEND_EMAIL,
            LIST_MAILBOXES,
            SEARCH_MESSAGES,
            GET_MESSAGE,
            GET_ATTACHMENT,
            GET_ATTACHMENT_INFO,
            SAVE_ATTACHMENT,
            MOVE_MESSAGE,
            DELETE_MESSAGE
    );

    private MailToolNames() {
    }
}
