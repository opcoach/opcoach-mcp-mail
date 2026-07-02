package org.opcoach.mailmcp.mail;

public record GetAttachmentInfoQuery(String mailbox, long uid, String attachmentId) {
}
