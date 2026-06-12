package org.opcoach.mailmcp.mail;

public record AttachmentContent(
        String attachmentId,
        String filename,
        String contentType,
        int size,
        String contentBase64
) {
}
