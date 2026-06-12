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
        String sentMailbox
) {

    public ConfigurationDraft {
        require(profile, "profile");
        require(imapHost, "imap.host");
        require(smtpHost, "smtp.host");
        require(username, "username");
        require(fromAddress, "from.address");
        require(sentMailbox, "sent.mailbox");
        if (imapPort < 1 || imapPort > 65535) {
            throw new ConfigurationException("Port IMAP invalide: " + imapPort);
        }
        if (smtpPort < 1 || smtpPort > 65535) {
            throw new ConfigurationException("Port SMTP invalide: " + smtpPort);
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Champ obligatoire manquant: " + field);
        }
    }
}
