package org.opcoach.mailmcp.mail;

public record SaveAttachmentCommand(
        String mailbox,
        long uid,
        String attachmentId,
        String directory,
        String filename,
        int maxBytes
) {
}
