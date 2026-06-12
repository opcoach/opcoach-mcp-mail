package org.opcoach.mailmcp.config;

public record MailEndpoint(String host, int port, ConnectionSecurity security) {

    public MailEndpoint {
        if (host == null || host.isBlank()) {
            throw new ConfigurationException("Hôte mail manquant.");
        }
        if (port < 1 || port > 65535) {
            throw new ConfigurationException("Port mail invalide pour " + host + ": " + port);
        }
        if (security == null) {
            throw new ConfigurationException("Sécurité mail manquante pour " + host + ".");
        }
    }
}
