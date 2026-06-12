package org.opcoach.mailmcp.mail;

import java.util.List;

public record MailboxInfo(String name, String fullName, int messageCount, List<String> attributes) {

    public MailboxInfo {
        attributes = List.copyOf(attributes == null ? List.of() : attributes);
    }
}
