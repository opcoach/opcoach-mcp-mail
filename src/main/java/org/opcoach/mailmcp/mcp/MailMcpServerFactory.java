package org.opcoach.mailmcp.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;

public final class MailMcpServerFactory {

    private static final String SERVER_NAME = "opcoach-mail-mcp";
    private static final String SERVER_VERSION = "0.1.0";

    private final McpJsonMapper jsonMapper;

    public MailMcpServerFactory() {
        this(McpJsonDefaults.getMapper());
    }

    public MailMcpServerFactory(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public McpSyncServer create(McpServerTransportProvider transportProvider, MailToolService toolService) {
        return McpServer.sync(transportProvider)
                .jsonMapper(jsonMapper)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(capabilities())
                .tools(new MailToolRegistry(toolService, jsonMapper).toolSpecifications())
                .build();
    }

    public McpSyncServer create(McpStreamableServerTransportProvider transportProvider, MailToolService toolService) {
        return McpServer.sync(transportProvider)
                .jsonMapper(jsonMapper)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(capabilities())
                .tools(new MailToolRegistry(toolService, jsonMapper).toolSpecifications())
                .build();
    }

    private static McpSchema.ServerCapabilities capabilities() {
        return McpSchema.ServerCapabilities.builder()
                .tools(true)
                .logging()
                .build();
    }
}
