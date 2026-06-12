package org.opcoach.mailmcp.mail;

public record AttachmentInfo(String attachmentId, String filename, String contentType, int size) {
}
