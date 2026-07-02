package org.opcoach.mailmcp.config;

import java.nio.file.Path;
import java.util.Map;

public final class ConfigurationPaths {

    public static final String CONFIG_ENV = "MAIL_MCP_CONFIG";
    public static final String CONFIG_PROPERTY = "mail.mcp.config";
    public static final String ATTACHMENT_DIR_ENV = "MAIL_MCP_ATTACHMENT_DIR";
    public static final String ATTACHMENT_DIR_PROPERTY = "mail.mcp.attachmentDir";
    public static final String HOME_DIR = ".opcoach-mcp-mail";
    public static final String CONFIG_FILE = "config.properties";
    public static final String AUDIT_FILE = "audit.log";

    private ConfigurationPaths() {
    }

    public static Path defaultConfigPath() {
        String configured = System.getProperty(CONFIG_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return defaultConfigPath(System.getenv());
    }

    static Path defaultConfigPath(Map<String, String> env) {
        String configured = env.get(CONFIG_ENV);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return defaultHomeDir().resolve(CONFIG_FILE);
    }

    public static Path defaultHomeDir() {
        return Path.of(System.getProperty("user.home"), HOME_DIR);
    }

    public static Path attachmentBaseDir() {
        String configured = System.getProperty(ATTACHMENT_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        configured = System.getenv(ATTACHMENT_DIR_ENV);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return defaultHomeDir().resolve("attachments");
    }

    static Path defaultAuditPath(Path configPath) {
        Path parent = configPath.toAbsolutePath().getParent();
        if (parent == null) {
            return Path.of(AUDIT_FILE);
        }
        return parent.resolve(AUDIT_FILE);
    }
}
