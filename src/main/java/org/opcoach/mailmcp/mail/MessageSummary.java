package org.opcoach.mailmcp.mail;

import java.util.List;

public record MessageSummary(
        String uid,
        String subject,
        String from,
        String receivedAt,
        boolean unread,
        String snippet,
        List<AttachmentInfo> attachments
) {

    public MessageSummary {
        attachments = List.copyOf(attachments == null ? List.of() : attachments);
    }
}
