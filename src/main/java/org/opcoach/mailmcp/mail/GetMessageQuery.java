package org.opcoach.mailmcp.mail;

public record GetMessageQuery(String mailbox, long uid, boolean includeHtml, int maxBodyBytes) {
}
