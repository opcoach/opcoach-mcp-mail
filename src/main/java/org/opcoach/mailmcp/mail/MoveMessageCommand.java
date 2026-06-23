package org.opcoach.mailmcp.mail;

public record MoveMessageCommand(
        String mailbox,
        long uid,
        String targetMailbox
) {
}
