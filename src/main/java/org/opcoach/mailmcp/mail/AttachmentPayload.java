package org.opcoach.mailmcp.mail;

public record AttachmentPayload(String filename, String contentType, byte[] content) {

    public AttachmentPayload {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename est obligatoire pour une pièce jointe.");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        if (content == null) {
            content = new byte[0];
        }
    }

    public int size() {
        return content.length;
    }
}
