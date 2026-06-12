package org.opcoach.mailmcp.mcp;

import org.opcoach.mailmcp.MailMcpApplication.CliOptions;
import org.opcoach.mailmcp.MailMcpApplication.TransportMode;
import org.opcoach.mailmcp.config.ConfigurationException;

public final class McpRuntime {

    private final CliOptions options;

    private McpRuntime(CliOptions options) {
        this.options = options;
    }

    public static McpRuntime create(CliOptions options) {
        return new McpRuntime(options);
    }

    public void start() {
        if (options.transportMode() == TransportMode.HTTP && !isLocalhost(options.host()) && isBlank(options.httpToken())) {
            throw new ConfigurationException("Un jeton HTTP est obligatoire quand le serveur n'écoute pas sur localhost.");
        }
        throw new ConfigurationException("""
                Configuration absente ou serveur non initialisé.
                Lancez ./mvnw -Psetup clean verify pour créer un profil local.
                Le mot de passe sera stocké dans le trousseau du système quand il est disponible.
                """);
    }

    private static boolean isLocalhost(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
