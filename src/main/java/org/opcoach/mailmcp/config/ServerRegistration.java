package org.opcoach.mailmcp.config;

import java.nio.file.Path;

public record ServerRegistration(
        String profile,
        Path configFile,
        Path runDir,
        String host,
        int port
) {

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
