package org.opcoach.mailmcp.mail;

import java.time.LocalDate;

public record SearchMessagesQuery(
        String mailbox,
        String fromContains,
        String toContains,
        String subjectContains,
        LocalDate since,
        boolean unreadOnly,
        int limit,
        boolean mailboxExplicit
) {
}
