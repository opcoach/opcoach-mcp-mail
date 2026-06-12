package org.opcoach.mailmcp.audit;

import java.time.Instant;
import java.util.List;

public record AuditEvent(
        Instant time,
        String tool,
        String status,
        String mailbox,
        String messageId,
        List<String> recipients
) {

    public AuditEvent {
        time = time == null ? Instant.now() : time;
        recipients = List.copyOf(recipients == null ? List.of() : recipients);
    }

    public static AuditEvent success(String tool, String mailbox, String messageId, List<String> recipients) {
        return new AuditEvent(Instant.now(), tool, "success", mailbox, messageId, recipients);
    }

    public static AuditEvent failure(String tool, String mailbox) {
        return new AuditEvent(Instant.now(), tool, "failure", mailbox, null, List.of());
    }
}
