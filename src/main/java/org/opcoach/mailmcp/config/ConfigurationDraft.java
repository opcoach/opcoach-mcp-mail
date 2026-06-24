package org.opcoach.mailmcp.config;

public record ConfigurationDraft(
        String profile,
        String imapHost,
        int imapPort,
        ConnectionSecurity imapSecurity,
        String smtpHost,
        int smtpPort,
        ConnectionSecurity smtpSecurity,
        String username,
        String fromAddress,
        String fromName,
        String replyToAddress,
        String sentMailbox,
        String trashMailbox
) {

    public ConfigurationDraft {
        require(profile, "profile");
        require(imapHost, "imap.host");
        require(smtpHost, "smtp.host");
        require(username, "username");
        require(fromAddress, "from.address");
        require(sentMailbox, "sent.mailbox");
        require(trashMailbox, "trash.mailbox");
        if (imapPort < 1 || imapPort > 65535) {
            throw new ConfigurationException("Invalid IMAP port: " + imapPort);
        }
        if (smtpPort < 1 || smtpPort > 65535) {
            throw new ConfigurationException("Invalid SMTP port: " + smtpPort);
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing required field: " + field);
        }
    }
}
