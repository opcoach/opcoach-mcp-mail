package org.opcoach.mailmcp.mail;

import java.time.LocalDate;

public record SearchMessagesQuery(
        String mailbox,
        String fromContains,
        String toContains,
        String subjectContains,
        LocalDate since,
        LocalDate until,
        boolean unreadOnly,
        int limit,
        Long beforeUid,
        boolean mailboxExplicit
) {
}
