package org.opcoach.mailmcp.mail;

import java.util.List;

public record SendEmailResult(
        String status,
        String messageId,
        List<String> acceptedRecipients,
        boolean html,
        String sentCopyStatus,
        String sentCopyMailbox
) {

    public SendEmailResult {
        acceptedRecipients = List.copyOf(acceptedRecipients == null ? List.of() : acceptedRecipients);
    }
}
