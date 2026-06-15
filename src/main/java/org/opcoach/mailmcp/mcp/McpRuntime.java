package org.opcoach.mailmcp.mcp;

import org.opcoach.mailmcp.audit.AuditLogger;
import org.opcoach.mailmcp.MailMcpApplication.CliOptions;
import org.opcoach.mailmcp.MailMcpApplication.TransportMode;
import org.opcoach.mailmcp.config.ConfigurationLoader;
import org.opcoach.mailmcp.config.MailConfiguration;
import org.opcoach.mailmcp.config.ResolvedSecret;
import org.opcoach.mailmcp.config.SecretResolver;
import org.opcoach.mailmcp.config.ConfigurationException;
import org.opcoach.mailmcp.mail.MailApplicationService;

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
            throw new ConfigurationException("An HTTP token is required when the server does not listen on localhost.");
        }
        MailConfiguration configuration = ConfigurationLoader.defaultLoader().load(options.profile());
        ResolvedSecret secret = SecretResolver.system().resolve(configuration);
        MailToolService toolService = new MailApplicationService(
                configuration,
                secret.value(),
                AuditLogger.file(configuration.auditPath())
        );
        try {
            if (options.transportMode() == TransportMode.HTTP) {
                new McpHttpServer(options.host(), options.port(), options.httpToken(), toolService).startAndJoin();
            } else {
                new McpStdioServer(toolService).startAndWait();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConfigurationException("MCP server interrupted.", exception);
        } catch (Exception exception) {
            throw new ConfigurationException("Unable to start the MCP server: " + exception.getMessage(), exception);
        }
    }

    private static boolean isLocalhost(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
