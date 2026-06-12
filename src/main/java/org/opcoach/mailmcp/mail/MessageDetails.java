package org.opcoach.mailmcp.mail;

import java.util.List;

public record MessageDetails(
        String uid,
        String mailbox,
        String subject,
        String from,
        List<String> to,
        String receivedAt,
        boolean unread,
        String textBody,
        String htmlBody,
        List<AttachmentInfo> attachments
) {

    public MessageDetails {
        to = List.copyOf(to == null ? List.of() : to);
        attachments = List.copyOf(attachments == null ? List.of() : attachments);
    }
}
