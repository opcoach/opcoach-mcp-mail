package org.opcoach.mailmcp.config;

public record MailEndpoint(String host, int port, ConnectionSecurity security) {

    public MailEndpoint {
        if (host == null || host.isBlank()) {
            throw new ConfigurationException("Missing mail host.");
        }
        if (port < 1 || port > 65535) {
            throw new ConfigurationException("Invalid mail port for " + host + ": " + port);
        }
        if (security == null) {
            throw new ConfigurationException("Missing mail security for " + host + ".");
        }
    }
}
