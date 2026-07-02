package org.opcoach.mailmcp.mail;

public record SavedAttachment(
        String attachmentId,
        String filename,
        String contentType,
        long size,
        String path
) {
}
