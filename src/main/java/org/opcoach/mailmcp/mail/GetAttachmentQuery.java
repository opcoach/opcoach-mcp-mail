package org.opcoach.mailmcp.mail;

public record GetAttachmentQuery(String mailbox, long uid, String attachmentId, int maxBytes) {
}
