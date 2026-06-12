package org.opcoach.mailmcp.config;

import java.nio.file.Path;
import java.util.Map;

public final class ConfigurationPaths {

    public static final String CONFIG_ENV = "MAIL_MCP_CONFIG";
    public static final String HOME_DIR = ".opcoach-mail-mcp";
    public static final String CONFIG_FILE = "config.properties";
    public static final String AUDIT_FILE = "audit.log";

    private ConfigurationPaths() {
    }

    public static Path defaultConfigPath() {
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

    static Path defaultAuditPath(Path configPath) {
        Path parent = configPath.toAbsolutePath().getParent();
        if (parent == null) {
            return Path.of(AUDIT_FILE);
        }
        return parent.resolve(AUDIT_FILE);
    }
}
