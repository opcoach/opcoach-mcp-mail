package org.opcoach.mailmcp.config;

import java.nio.file.Path;

public record MailConfiguration(
        String profile,
        MailEndpoint imap,
        MailEndpoint smtp,
        String username,
        String fromAddress,
        String fromName,
        String sentMailbox,
        MailLimits limits,
        Path configPath,
        Path auditPath
) {

    public MailConfiguration {
        require(profile, "profile");
        require(username, "username");
        require(fromAddress, "from.address");
        require(sentMailbox, "sent.mailbox");
        if (imap == null) {
            throw new ConfigurationException("Configuration IMAP manquante.");
        }
        if (smtp == null) {
            throw new ConfigurationException("Configuration SMTP manquante.");
        }
        if (limits == null) {
            limits = MailLimits.DEFAULTS;
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Champ de configuration manquant: " + field);
        }
    }
}
