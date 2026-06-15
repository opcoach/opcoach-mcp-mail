package org.opcoach.mailmcp.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import org.opcoach.mailmcp.security.DataLimiter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class MimeMessageExtractor {

    ExtractedMessage extract(Part root, int maxTextBytes, int maxHtmlBytes) {
        ExtractState state = new ExtractState();
        try {
            visit(root, "part-1", state, true);
        } catch (MessagingException | IOException exception) {
            throw new MailOperationException("Unable to extract the MIME message: " + exception.getMessage(), exception);
        }
        return new ExtractedMessage(
                DataLimiter.truncateUtf8(state.text.toString(), maxTextBytes),
                DataLimiter.truncateUtf8(state.html.toString(), maxHtmlBytes),
                List.copyOf(state.attachments)
        );
    }

    AttachmentContent attachment(Part root, String requestedId, int maxBytes) {
        try {
            FoundAttachment found = findAttachment(root, "part-1", requestedId, maxBytes, true);
            if (found == null) {
                throw new IllegalArgumentException("Attachment not found: " + requestedId);
            }
            return found.content();
        } catch (MessagingException | IOException exception) {
            throw new MailOperationException("Unable to read the attachment: " + exception.getMessage(), exception);
        }
    }

    private void visit(Part part, String partId, ExtractState state, boolean root) throws MessagingException, IOException {
        if (!root && isAttachment(part)) {
            state.attachments.add(info(part, partId));
            return;
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content != null) {
                state.text.append(content).append('\n');
            }
            return;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (content != null) {
                state.html.append(content).append('\n');
            }
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                visit(child, childId(partId, index + 1, root), state, false);
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Message nested) {
                visit(nested, childId(partId, 1, root), state, false);
            }
        }
    }

    private FoundAttachment findAttachment(Part part, String partId, String requestedId, int maxBytes, boolean root)
            throws MessagingException, IOException {
        if (!root && isAttachment(part)) {
            if (!partId.equals(requestedId)) {
                return null;
            }
            if (part.getSize() > maxBytes) {
                throw new IllegalArgumentException("Attachment is too large: " + safeFilename(part));
            }
            byte[] content = DataLimiter.readAtMost(part.getInputStream(), maxBytes);
            AttachmentContent attachment = new AttachmentContent(
                    partId,
                    safeFilename(part),
                    contentType(part),
                    content.length,
                    Base64.getEncoder().encodeToString(content)
            );
            return new FoundAttachment(attachment);
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                FoundAttachment found = findAttachment(child, childId(partId, index + 1, root), requestedId, maxBytes, false);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static AttachmentInfo info(Part part, String partId) throws MessagingException {
        return new AttachmentInfo(partId, safeFilename(part), contentType(part), Math.max(part.getSize(), 0));
    }

    private static boolean isAttachment(Part part) throws MessagingException {
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
            return true;
        }
        if (Part.INLINE.equalsIgnoreCase(disposition) && part.getFileName() != null) {
            return true;
        }
        return part.getFileName() != null && !part.getFileName().isBlank();
    }

    private static String childId(String parent, int index, boolean parentIsRoot) {
        if (parentIsRoot) {
            return "part-" + index;
        }
        return parent + "." + index;
    }

    private static String safeFilename(Part part) throws MessagingException {
        String filename = part.getFileName();
        return filename == null || filename.isBlank() ? "attachment" : filename;
    }

    private static String contentType(Part part) throws MessagingException {
        String contentType = part.getContentType();
        int semicolon = contentType.indexOf(';');
        return semicolon >= 0 ? contentType.substring(0, semicolon).trim() : contentType;
    }

    record ExtractedMessage(String textBody, String htmlBody, List<AttachmentInfo> attachments) {
    }

    private static final class ExtractState {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder html = new StringBuilder();
        private final List<AttachmentInfo> attachments = new ArrayList<>();
    }

    private record FoundAttachment(AttachmentContent content) {
    }
}
