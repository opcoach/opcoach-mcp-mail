package org.opcoach.mailmcp.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public final class McpStdioServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpStdioServer.class);

    private final MailToolService toolService;

    public McpStdioServer(MailToolService toolService) {
        this.toolService = toolService;
    }

    public void startAndWait() throws InterruptedException {
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = new MailMcpServerFactory().create(transportProvider, toolService);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.closeGracefully();
            stopped.countDown();
        }, "opcoach-mail-mcp-stdio-stop"));
        LOGGER.info("Serveur MCP stdio démarré.");
        stopped.await();
    }
}
