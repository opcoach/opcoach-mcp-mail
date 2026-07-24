package org.opcoach.mailmcp.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record MailConfiguration(
        String profile,
        MailEndpoint imap,
        MailEndpoint smtp,
        String username,
        String fromAddress,
        String fromName,
        String replyToAddress,
        List<String> incomingMailboxes,
        String sentMailbox,
        String trashMailbox,
        MailLimits limits,
        Path configPath,
        Path auditPath
) {

    public static final String DEFAULT_INCOMING_MAILBOX = "INBOX";

    public MailConfiguration {
        require(profile, "profile");
        require(username, "username");
        require(fromAddress, "from.address");
        incomingMailboxes = normalizeMailboxes(incomingMailboxes);
        require(sentMailbox, "sent.mailbox");
        require(trashMailbox, "trash.mailbox");
        if (imap == null) {
            throw new ConfigurationException("Missing IMAP configuration.");
        }
        if (smtp == null) {
            throw new ConfigurationException("Missing SMTP configuration.");
        }
        if (limits == null) {
            limits = MailLimits.DEFAULTS;
        }
        if (replyToAddress == null) {
            replyToAddress = "";
        }
    }

    public String defaultIncomingMailbox() {
        return incomingMailboxes.getFirst();
    }

    public String incomingMailboxesProperty() {
        return String.join(",", incomingMailboxes);
    }

    public static List<String> parseMailboxes(String rawValue, String defaultValue, String field) {
        String value = rawValue == null || rawValue.isBlank() ? defaultValue : rawValue;
        List<String> mailboxes = new ArrayList<>();
        for (String token : value.split("[,\\r\\n]+")) {
            String mailbox = token.trim();
            if (!mailbox.isBlank()) {
                mailboxes.add(mailbox);
            }
        }
        if (mailboxes.isEmpty()) {
            throw new ConfigurationException("Missing configuration field: " + field);
        }
        return normalizeMailboxes(mailboxes);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing configuration field: " + field);
        }
    }

    private static List<String> normalizeMailboxes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of(DEFAULT_INCOMING_MAILBOX);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            unique.add(value.trim());
        }
        if (unique.isEmpty()) {
            throw new ConfigurationException("Missing configuration field: incoming.mailboxes");
        }
        return List.copyOf(unique);
    }
}
