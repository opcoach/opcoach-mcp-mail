package org.opcoach.mailmcp.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import org.opcoach.mailmcp.security.DataLimiter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    List<AttachmentInfo> attachmentInfo(Part root, String requestedId) {
        try {
            List<AttachmentInfo> attachments = new ArrayList<>();
            collectAttachments(root, "part-1", requestedId, attachments, true);
            if (requestedId != null && !requestedId.isBlank() && attachments.isEmpty()) {
                throw new IllegalArgumentException("Attachment not found: " + requestedId);
            }
            return List.copyOf(attachments);
        } catch (MessagingException | IOException exception) {
            throw new MailOperationException("Unable to inspect the attachments: " + exception.getMessage(), exception);
        }
    }

    SavedAttachment saveAttachment(
            Part root,
            String requestedId,
            AttachmentStorage storage,
            String directory,
            String filename,
            int maxBytes
    ) {
        try {
            SavedAttachment found = saveMatchingAttachment(root, "part-1", requestedId, storage, directory, filename, maxBytes, true);
            if (found == null) {
                throw new IllegalArgumentException("Attachment not found: " + requestedId);
            }
            return found;
        } catch (MessagingException | IOException exception) {
            throw new MailOperationException("Unable to save the attachment: " + exception.getMessage(), exception);
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

    private void collectAttachments(Part part, String partId, String requestedId, List<AttachmentInfo> attachments, boolean root)
            throws MessagingException, IOException {
        if (!root && isAttachment(part)) {
            if (requestedId == null || requestedId.isBlank() || partId.equals(requestedId)) {
                attachments.add(info(part, partId));
            }
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                collectAttachments(child, childId(partId, index + 1, root), requestedId, attachments, false);
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Message nested) {
                collectAttachments(nested, childId(partId, 1, root), requestedId, attachments, false);
            }
        }
    }

    private SavedAttachment saveMatchingAttachment(
            Part part,
            String partId,
            String requestedId,
            AttachmentStorage storage,
            String directory,
            String filename,
            int maxBytes,
            boolean root
    ) throws MessagingException, IOException {
        if (!root && isAttachment(part)) {
            if (!partId.equals(requestedId)) {
                return null;
            }
            if (part.getSize() > maxBytes) {
                throw new IllegalArgumentException("Attachment is too large: " + safeFilename(part));
            }
            Path target = storage.target(directory, filename, safeFilename(part));
            storage.createParentDirectories(target);
            OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (output) {
                long size = DataLimiter.copyAtMost(part.getInputStream(), output, maxBytes);
                storage.restrictOwnerOnly(target);
                return new SavedAttachment(
                        partId,
                        target.getFileName().toString(),
                        contentType(part),
                        size,
                        target.toString()
                );
            } catch (RuntimeException | IOException exception) {
                Files.deleteIfExists(target);
                throw exception;
            }
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart child = multipart.getBodyPart(index);
                SavedAttachment found = saveMatchingAttachment(child, childId(partId, index + 1, root), requestedId, storage, directory, filename, maxBytes, false);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Message nested) {
                return saveMatchingAttachment(nested, childId(partId, 1, root), requestedId, storage, directory, filename, maxBytes, false);
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
