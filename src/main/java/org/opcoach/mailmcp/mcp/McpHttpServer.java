package org.opcoach.mailmcp.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;

public final class McpHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpHttpServer.class);

    private final String host;
    private final int port;
    private final String token;
    private final MailToolService toolService;

    public McpHttpServer(String host, int port, String token, MailToolService toolService) {
        this.host = host;
        this.port = port;
        this.token = token;
        this.toolService = toolService;
    }

    public void startAndJoin() throws Exception {
        HttpServletStreamableServerTransportProvider transportProvider = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .build();
        new MailMcpServerFactory().create(transportProvider, toolService);

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder("mcp", transportProvider), "/*");
        if (token != null && !token.isBlank()) {
            context.addFilter(new FilterHolder(new BearerTokenFilter(token)), "/*", EnumSet.of(DispatcherType.REQUEST));
        }
        server.setHandler(context);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(server), "opcoach-mcp-mail-http-stop"));
        server.start();
        LOGGER.info("Serveur MCP HTTP démarré sur http://{}:{}/mcp", host, connector.getLocalPort());
        server.join();
    }

    private static void stop(Server server) {
        try {
            server.stop();
        } catch (Exception ignored) {
            // Arrêt JVM en cours.
        }
    }

    private static final class BearerTokenFilter implements Filter {
        private final String expectedHeader;

        private BearerTokenFilter(String token) {
            this.expectedHeader = "Bearer " + token;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String authorization = httpRequest.getHeader("Authorization");
            if (!expectedHeader.equals(authorization)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setHeader("Cache-Control", "no-store");
                httpResponse.getWriter().write("Token HTTP manquant ou invalide.");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
