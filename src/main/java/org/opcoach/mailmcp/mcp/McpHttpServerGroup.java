package org.opcoach.mailmcp.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.opcoach.mailmcp.config.ServerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpHttpServerGroup implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpHttpServerGroup.class);
    private static final int CONNECTOR_ACCEPTORS = 1;
    private static final int CONNECTOR_SELECTORS = 1;

    private final Map<String, Endpoint> endpoints = new LinkedHashMap<>();
    private Server server;

    public synchronized void startOrReplace(ServerRegistration registration, MailToolService toolService) throws Exception {
        Map<String, Endpoint> updated = new LinkedHashMap<>(endpoints);
        updated.put(registration.profile(), new Endpoint(registration, null, toolService));
        restart(updated);
    }

    public synchronized void stop(String profile) throws Exception {
        if (!endpoints.containsKey(profile)) {
            return;
        }
        Map<String, Endpoint> updated = new LinkedHashMap<>(endpoints);
        updated.remove(profile);
        restart(updated);
    }

    public synchronized boolean isRunning(String profile) {
        return server != null && server.isRunning() && endpoints.containsKey(profile);
    }

    public synchronized List<ServerRegistration> registrations() {
        return endpoints.values().stream()
                .map(Endpoint::registration)
                .toList();
    }

    @Override
    public synchronized void close() {
        try {
            stopServer();
        } catch (Exception exception) {
            LOGGER.warn("Unable to stop embedded MCP HTTP server group: {}", exception.getMessage());
        } finally {
            endpoints.clear();
        }
    }

    private void restart(Map<String, Endpoint> updated) throws Exception {
        stopServer();
        if (updated.isEmpty()) {
            endpoints.clear();
            return;
        }
        Server nextServer = buildServer(updated);
        try {
            nextServer.start();
            server = nextServer;
            endpoints.clear();
            endpoints.putAll(updated);
            for (Endpoint endpoint : endpoints.values()) {
                ServerRegistration registration = endpoint.registration();
                LOGGER.info("MCP HTTP endpoint started on http://{}:{}/mcp for profile {}", registration.host(), registration.port(), registration.profile());
            }
        } catch (Exception exception) {
            stop(nextServer);
            endpoints.clear();
            throw exception;
        }
    }

    private Server buildServer(Map<String, Endpoint> specs) {
        QueuedThreadPool threadPool = new QueuedThreadPool(maxThreads(specs.size()), 2, 30_000);
        threadPool.setName("opcoach-mcp-mail-mcp");
        threadPool.setReservedThreads(0);

        Server nextServer = new Server(threadPool);
        List<ServletContextHandler> contexts = new ArrayList<>();
        int index = 0;
        for (Endpoint endpoint : specs.values()) {
            ServerRegistration registration = endpoint.registration();
            String connectorName = "mcp-" + index++ + "-" + safeConnectorName(registration.profile());
            ServerConnector connector = new ServerConnector(nextServer, CONNECTOR_ACCEPTORS, CONNECTOR_SELECTORS);
            connector.setName(connectorName);
            connector.setHost(registration.host());
            connector.setPort(registration.port());
            nextServer.addConnector(connector);

            ServletContextHandler context = context(endpoint);
            context.setContextPath("/");
            context.addVirtualHosts("@" + connectorName);
            contexts.add(context);
        }
        nextServer.setHandler(new ContextHandlerCollection(contexts.toArray(ServletContextHandler[]::new)));
        return nextServer;
    }

    private static int maxThreads(int endpointCount) {
        return Math.max(12, endpointCount * 4 + 8);
    }

    private static ServletContextHandler context(Endpoint endpoint) {
        HttpServletStreamableServerTransportProvider transportProvider = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .build();
        new MailMcpServerFactory().create(transportProvider, endpoint.toolService());

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addServlet(new ServletHolder("health", new HealthServlet()), "/health");
        context.addServlet(new ServletHolder("healthz", new HealthServlet()), "/healthz");
        context.addServlet(new ServletHolder("mcp", transportProvider), "/*");
        if (endpoint.token() != null && !endpoint.token().isBlank()) {
            context.addFilter(new FilterHolder(new BearerTokenFilter(endpoint.token())), "/*", EnumSet.of(DispatcherType.REQUEST));
        }
        return context;
    }

    private void stopServer() throws Exception {
        Server current = server;
        server = null;
        stop(current);
    }

    private static void stop(Server server) throws Exception {
        if (server != null && (server.isRunning() || server.isStarting())) {
            server.stop();
        }
    }

    private static String safeConnectorName(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        return sanitized.isBlank() ? "default" : sanitized;
    }

    private record Endpoint(ServerRegistration registration, String token, MailToolService toolService) {
    }

    private static final class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json; charset=utf-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"status\":\"ok\"}");
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
            if (isHealthRequest(httpRequest)) {
                chain.doFilter(request, response);
                return;
            }
            String authorization = httpRequest.getHeader("Authorization");
            if (!expectedHeader.equals(authorization)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setHeader("Cache-Control", "no-store");
                httpResponse.getWriter().write("Missing or invalid HTTP token.");
                return;
            }
            chain.doFilter(request, response);
        }

        private static boolean isHealthRequest(HttpServletRequest request) {
            String path = request.getRequestURI();
            return "/health".equals(path) || "/healthz".equals(path);
        }
    }
}
