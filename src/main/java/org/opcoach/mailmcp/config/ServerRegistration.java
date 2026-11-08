package org.opcoach.mailmcp.config;

import java.nio.file.Path;

public record ServerRegistration(
        String profile,
        Path configFile,
        Path runDir,
        String host,
        int port,
        String displayName
) {

    public ServerRegistration {
        displayName = normalizeDisplayName(displayName);
    }

    public ServerRegistration(String profile, Path configFile, Path runDir, String host, int port) {
        this(profile, configFile, runDir, host, port, "");
    }

    public String label() {
        return displayName.isEmpty() ? profile : displayName;
    }

    static String normalizeDisplayName(String name) {
        if (name == null) {
            return "";
        }
        if (name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029)) {
            throw new ConfigurationException("Profile name must be a single line without control characters.");
        }
        return name.strip();
    }

    public String url() {
        return "http://" + host + ":" + port + "/mcp";
    }

    public Path pidFile() {
        return runDir.resolve("opcoach-mcp-mail.pid");
    }

    public Path logFile() {
        return runDir.resolve("opcoach-mcp-mail.log");
    }
}
