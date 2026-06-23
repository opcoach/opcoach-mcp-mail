package org.opcoach.mailmcp.mail;

public record MoveMessageResult(
        String uid,
        String sourceMailbox,
        String targetMailbox,
        String action
) {
}
