package org.opcoach.mailmcp.mail;

import java.util.List;

public record SendEmailCommand(
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String textBody,
        String htmlBody,
        List<AttachmentPayload> attachments
) {

    public SendEmailCommand {
        to = List.copyOf(to == null ? List.of() : to);
        cc = List.copyOf(cc == null ? List.of() : cc);
        bcc = List.copyOf(bcc == null ? List.of() : bcc);
        attachments = List.copyOf(attachments == null ? List.of() : attachments);
        if (to.isEmpty()) {
            throw new IllegalArgumentException("sendEmail nécessite au moins un destinataire to.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("sendEmail nécessite un sujet.");
        }
        if ((textBody == null || textBody.isBlank()) && (htmlBody == null || htmlBody.isBlank())) {
            throw new IllegalArgumentException("sendEmail nécessite textBody ou htmlBody.");
        }
    }

    public boolean html() {
        return htmlBody != null && !htmlBody.isBlank();
    }

    public List<String> allRecipients() {
        java.util.ArrayList<String> recipients = new java.util.ArrayList<>();
        recipients.addAll(to);
        recipients.addAll(cc);
        recipients.addAll(bcc);
        return List.copyOf(recipients);
    }
}
