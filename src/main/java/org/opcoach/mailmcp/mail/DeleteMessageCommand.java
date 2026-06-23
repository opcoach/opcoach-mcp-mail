package org.opcoach.mailmcp.mail;

public record DeleteMessageCommand(
        String mailbox,
        long uid
) {
}
