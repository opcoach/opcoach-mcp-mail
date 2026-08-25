package org.opcoach.mailmcp.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.opcoach.mailmcp.config.ProfileTransfer.ProfileSnapshot;
import org.opcoach.mailmcp.mail.JakartaImapClient;
import org.opcoach.mailmcp.mail.MailboxInfo;
import org.opcoach.mailmcp.security.SafeErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.Console;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebManagerApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebManagerApplication.class);
    private static final int DEFAULT_PORT = 18100;
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private final ServerRegistry registry = ServerRegistry.defaultRegistry();
    private final EmbeddedMcpServerManager mcpServerManager = new EmbeddedMcpServerManager();
    private final ProfileTransfer transfer = new ProfileTransfer();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HEALTH_TIMEOUT)
            .build();
    private final ExecutorService healthExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "opcoach-mcp-mail-web-health");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, HealthStatus> healthStatuses = new ConcurrentHashMap<>();
    private final Set<String> healthChecksInFlight = ConcurrentHashMap.newKeySet();
    private final String token = newToken();
    private final WebManagerRuntimeFiles runtimeFiles = WebManagerRuntimeFiles.defaults();
    private volatile SecretStore secretStore;
    private char[] vaultPassword;

    private WebManagerApplication() {
        this(startupVaultPassword());
    }

    private WebManagerApplication(char[] vaultPassword) {
        this.vaultPassword = copy(vaultPassword);
        this.secretStore = LocalSecretStore.system(this.vaultPassword);
    }

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        new WebManagerApplication().start(options.port(), options.openBrowser(), options.startRegistered());
    }

    public static void run(int port) throws IOException, InterruptedException {
        new WebManagerApplication().start(port, false, false);
    }

    public static void run(int port, boolean openBrowser, boolean startRegistered) throws IOException, InterruptedException {
        new WebManagerApplication().start(port, openBrowser, startRegistered);
    }

    private static char[] startupVaultPassword() {
        if (!LocalSecretStore.systemUsesEncryptedVault()) {
            return new char[0];
        }
        String configured = System.getenv(EncryptedVaultSecretStore.VAULT_PASSWORD_ENV);
        if (configured != null && !configured.isBlank()) {
            char[] password = configured.toCharArray();
            validateStartupVaultPassword(password);
            return password;
        }
        if ("1".equals(System.getenv(EncryptedVaultSecretStore.VAULT_PASSWORD_STDIN_ENV))) {
            char[] password = readRequiredVaultPassword("Vault password: ");
            validateStartupVaultPassword(password);
            return password;
        }
        while (true) {
            char[] password = readRequiredVaultPassword("Linux vault password: ");
            try {
                validateStartupVaultPassword(password);
                return password;
            } catch (ConfigurationException exception) {
                Arrays.fill(password, '\0');
                System.err.println(SafeErrorMessage.clean(exception.getMessage()));
            }
        }
    }

    private static void validateStartupVaultPassword(char[] password) {
        if (!Files.exists(EncryptedVaultSecretStore.defaultVaultPath())) {
            return;
        }
        LocalSecretStore.system(password).readPassword("__vault_validation__");
    }

    private static char[] readRequiredVaultPassword(String prompt) {
        while (true) {
            char[] password = readVaultPassword(prompt);
            if (password == null) {
                throw new ConfigurationException("Vault password is required on Linux. Set MAIL_MCP_VAULT_PASSWORD or start from an interactive terminal.");
            }
            if (password.length > 0) {
                return password;
            }
            System.err.println("Vault password is required on Linux.");
        }
    }

    private static char[] readVaultPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("%s", prompt);
        }
        try {
            System.out.print(prompt);
            StringBuilder builder = new StringBuilder();
            int value;
            while ((value = System.in.read()) != -1 && value != '\n' && value != '\r') {
                builder.append((char) value);
            }
            if (value == -1 && builder.isEmpty()) {
                return null;
            }
            return builder.toString().toCharArray();
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to read vault password from stdin.", exception);
        }
    }

    private void start(int port, boolean openBrowser, boolean startRegistered) throws IOException, InterruptedException {
        HttpServer server = HttpServer.create(new InetSocketAddress(LOCAL_HOST, port), 0);
        server.createContext("/", this::handle);
        server.start();
        long pid = ProcessHandle.current().pid();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            mcpServerManager.close();
            runtimeFiles.deleteIfOwnedBy(pid);
            healthExecutor.shutdownNow();
            clearVaultPassword();
            server.stop(0);
        }, "opcoach-mcp-mail-web-manager-stop"));
        int actualPort = server.getAddress().getPort();
        String url = "http://" + LOCAL_HOST + ":" + actualPort + "/?token=" + token;
        writeRuntimeUrl(url, pid);
        System.out.println("MCP Mail Local Manager started on " + url);
        System.out.println("URL saved in " + runtimeFiles.urlFile());
        System.out.println("It is bound to 127.0.0.1 only. Stop this process to close the UI.");
        LOGGER.info("MCP Mail Local Manager started on http://{}:{}/ (token omitted from logs)", LOCAL_HOST, actualPort);
        LOGGER.info("Web manager URL saved in {}", runtimeFiles.urlFile());
        LOGGER.info("Web manager is bound to 127.0.0.1 only");
        if (startRegistered) {
            startRegisteredProfiles();
        }
        if (openBrowser) {
            openBrowser(url);
        }
        new CountDownLatch(1).await();
    }

    private void writeRuntimeUrl(String url, long pid) {
        try {
            runtimeFiles.write(url, pid);
        } catch (ConfigurationException exception) {
            LOGGER.warn("Unable to write the web manager runtime URL: {}", SafeErrorMessage.clean(exception.getMessage()));
        }
    }

    private void startRegisteredProfiles() {
        int started = 0;
        int failures = 0;
        for (ServerRegistration registration : registry.list()) {
            try {
                mcpServerManager.start(registration, "", vaultPasswordForStart());
                queueHealthCheck(registration, true);
                started++;
            } catch (RuntimeException exception) {
                failures++;
                LOGGER.warn("Unable to start profile {}: {}", registration.profile(), SafeErrorMessage.clean(exception.getMessage()));
            }
        }
        LOGGER.info("Started {} registered MCP endpoint(s) in the web-manager process", started);
        if (failures > 0) {
            LOGGER.warn("{} registered MCP endpoint(s) could not be started; open the web manager for details", failures);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isAuthorized(exchange)) {
                send(exchange, 403, "text/plain; charset=utf-8", "Invalid token.");
                return;
            }
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery());
            if ("GET".equals(method) && "/".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", mainPage(query));
                return;
            }
            if ("GET".equals(method) && "/vault".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", vaultPage(query));
                return;
            }
            if ("POST".equals(method) && "/vault".equals(path)) {
                handleVaultPost(exchange);
                return;
            }
            if ("POST".equals(method) && "/profile".equals(path)) {
                handleProfilePost(exchange);
                return;
            }
            if ("POST".equals(method) && "/start".equals(path)) {
                handleStartPost(exchange);
                return;
            }
            if ("POST".equals(method) && "/stop".equals(path)) {
                handleStopPost(exchange);
                return;
            }
            if ("POST".equals(method) && "/delete".equals(path)) {
                handleDeletePost(exchange);
                return;
            }
            if ("GET".equals(method) && "/check/details".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", checkDetailsPage(query));
                return;
            }
            if ("GET".equals(method) && "/export".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", exportPage(query));
                return;
            }
            if ("POST".equals(method) && "/export".equals(path)) {
                handleExportPost(exchange);
                return;
            }
            if ("GET".equals(method) && "/import".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", importPage(query));
                return;
            }
            if ("POST".equals(method) && "/import/preview".equals(path)) {
                handleImportPreviewPost(exchange);
                return;
            }
            if ("POST".equals(method) && "/import/apply".equals(path)) {
                handleImportApplyPost(exchange);
                return;
            }
            send(exchange, 404, "text/plain; charset=utf-8", "Not found.");
        } catch (Exception exception) {
            String path = exchange.getRequestURI().getPath();
            String target = path.startsWith("/import") ? "/import" : path.startsWith("/export") ? "/export" : path.startsWith("/vault") ? "/vault" : "/";
            redirect(exchange, target, Map.of("error", SafeErrorMessage.clean(exception.getMessage())));
        }
    }

    private String mainPage(Map<String, String> query) {
        List<ServerRegistration> registrations = registry.list();
        queueInitialHealthChecks(registrations);
        String sort = query.getOrDefault("sort", "profile");
        String direction = query.getOrDefault("dir", "asc");
        List<ServerRegistration> sortedRegistrations = sortedRegistrations(registrations, sort, direction);
        String selectedName = selectedProfileName(query, registrations);
        ProfileForm selected = "new".equals(query.get("mode"))
                ? ProfileForm.defaults("default", firstFreePort(8095))
                : loadProfileForm(selectedName, registrations);
        String status = query.getOrDefault("status", "");
        String error = query.getOrDefault("error", "");

        StringBuilder body = new StringBuilder();
        body.append(pageStart("MCP Mail Local Manager"));
        body.append(hero());
        body.append("<main class=\"layout\">");
        body.append("<section class=\"panel servers\">");
        body.append("<div class=\"panel-head\"><div><h2>Registered servers</h2><p>Local MCP URLs and runtime status</p></div>");
        body.append("<a class=\"button ghost\" href=\"").append(link("/", Map.of("mode", "new"))).append("\">New</a></div>");
        body.append("<div class=\"panel-scroll servers-scroll\" id=\"servers-scroll\">");
        body.append(serverTable(sortedRegistrations, selected.profile(), sort, direction));
        body.append("</div>");
        body.append("<div class=\"row-actions\">");
        body.append(actionButton("/start", "Start", selected.profile(), !selected.registered() || selected.running(), "good", ""));
        body.append(actionButton("/stop", "Stop", selected.profile(), !selected.registered() || !selected.running(), "danger", ""));
        body.append(actionButton("/delete", "Delete", selected.profile(), !selected.registered(), "warning", "return confirm('Delete this local profile? Mailbox messages will not be deleted.');"));
        body.append("<a class=\"button ghost\" href=\"").append(link("/export", Map.of())).append("\">Export</a>");
        body.append("<a class=\"button ghost\" href=\"").append(link("/import", Map.of())).append("\">Import</a>");
        body.append("</div>");
        body.append("</section>");

        body.append("<section class=\"panel config\">");
        body.append("<div class=\"panel-head\"><div><h2>Profile configuration</h2><p>Mailbox settings and local MCP port</p></div></div>");
        if (!status.isBlank()) {
            body.append("<div class=\"notice ok\">").append(escape(status)).append("</div>");
        }
        if (!error.isBlank()) {
            body.append("<div class=\"notice error\">").append(escape(error)).append("</div>");
        }
        body.append(profileForm(selected));
        body.append("</section>");
        body.append("</main>");
        body.append(summaryDialog(query));
        body.append(autoRefreshScript(registrations, selected.profile(), sort, direction));
        body.append(pageEnd());
        return body.toString();
    }

    private String serverTable(List<ServerRegistration> registrations, String selectedProfile, String sort, String direction) {
        if (registrations.isEmpty()) {
            return "<div class=\"empty\">No profile registered yet.</div>";
        }
        StringBuilder html = new StringBuilder();
        html.append("<table><thead><tr><th>")
                .append(sortHeader("Profile", "profile", sort, direction))
                .append("</th><th>")
                .append(sortHeader("URL", "url", sort, direction))
                .append("</th><th>Status</th><th>Mail check</th><th class=\"view-column\">View</th></tr></thead><tbody>");
        for (ServerRegistration registration : registrations) {
            boolean selected = registration.profile().equals(selectedProfile);
            boolean running = mcpServerManager.isRunning(registration);
            HealthStatus health = healthStatuses.getOrDefault(healthKey(registration), HealthStatus.notChecked());
            String rowLink = link("/", Map.of("profile", registration.profile(), "sort", sort, "dir", direction));
            html.append("<tr class=\"").append(selected ? "selected" : "").append("\" onclick=\"location.href='")
                    .append(js(rowLink))
                    .append("'\" tabindex=\"0\" onkeydown=\"if(event.key==='Enter'){location.href='")
                    .append(js(rowLink))
                    .append("'}\">");
            html.append("<td><a onclick=\"event.stopPropagation()\" href=\"").append(rowLink).append("\">")
                    .append(escape(registration.profile())).append("</a></td>");
            html.append("<td><button class=\"link-button\" type=\"button\" onclick=\"event.stopPropagation();copyText('")
                    .append(js(registration.url())).append("')\">").append(escape(registration.url())).append("</button></td>");
            html.append("<td><span class=\"badge ").append(running ? "running" : "stopped").append("\">")
                    .append(running ? "running" : "stopped").append("</span></td>");
            html.append("<td><div class=\"mail-check\">")
                    .append(healthBadge(health));
            if (health.hasDetails()) {
                html.append("<a class=\"details-link\" onclick=\"event.stopPropagation()\" href=\"")
                        .append(link("/check/details", Map.of("profile", registration.profile())))
                        .append("\">Details</a>");
            }
            html.append("</div></td>");
            html.append("<td class=\"view-column\"><a class=\"eye-button\" onclick=\"event.stopPropagation()\" href=\"")
                    .append(link("/", Map.of("profile", registration.profile(), "summary", registration.profile(), "sort", sort, "dir", direction)))
                    .append("\" title=\"View full settings\">&#128065;</a></td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private List<ServerRegistration> sortedRegistrations(List<ServerRegistration> registrations, String sort, String direction) {
        Comparator<ServerRegistration> comparator = switch (sort) {
            case "url" -> Comparator.comparing(ServerRegistration::url, String.CASE_INSENSITIVE_ORDER);
            case "profile" -> Comparator.comparing(ServerRegistration::profile, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(ServerRegistration::profile, String.CASE_INSENSITIVE_ORDER);
        };
        if ("desc".equals(direction)) {
            comparator = comparator.reversed();
        }
        return registrations.stream().sorted(comparator).toList();
    }

    private String sortHeader(String label, String field, String sort, String direction) {
        boolean active = field.equals(sort);
        String nextDirection = active && "asc".equals(direction) ? "desc" : "asc";
        String marker = active ? ("asc".equals(direction) ? " ↑" : " ↓") : "";
        return "<a class=\"sort-link\" href=\"" + link("/", Map.of("sort", field, "dir", nextDirection)) + "\">"
                + escape(label) + marker + "</a>";
    }

    private String autoRefreshScript(List<ServerRegistration> registrations, String selectedProfile, String sort, String direction) {
        boolean checking = registrations.stream()
                .map(registration -> healthStatuses.get(healthKey(registration)))
                .anyMatch(status -> status != null && status.isChecking());
        if (!checking) {
            return "";
        }
        String refreshUrl = link("/", Map.of("profile", selectedProfile, "sort", sort, "dir", direction));
        return """
                <script id="mail-health-refresh">
                  (() => {
                    const refreshHealth = () => window.setTimeout(async () => {
                      try {
                        const response = await fetch('%s', { cache: 'no-store' });
                        if (!response.ok) return;
                        const refreshedPage = new DOMParser().parseFromString(await response.text(), 'text/html');
                        const currentList = document.getElementById('servers-scroll');
                        const refreshedList = refreshedPage.getElementById('servers-scroll');
                        if (currentList && refreshedList) {
                          const top = currentList.scrollTop;
                          const left = currentList.scrollLeft;
                          currentList.innerHTML = refreshedList.innerHTML;
                          currentList.scrollTop = top;
                          currentList.scrollLeft = left;
                        }
                        if (refreshedPage.getElementById('mail-health-refresh')) refreshHealth();
                      } catch (ignored) {
                        // A later manual refresh will recover transient network failures.
                      }
                    }, 1600);
                    refreshHealth();
                  })();
                </script>
                """.formatted(js(refreshUrl));
    }

    private void queueInitialHealthChecks(List<ServerRegistration> registrations) {
        for (ServerRegistration registration : registrations) {
            String key = healthKey(registration);
            if (!healthStatuses.containsKey(key)) {
                queueHealthCheck(registration, false);
            }
        }
    }

    private void queueHealthCheck(ServerRegistration registration, boolean force) {
        String key = healthKey(registration);
        if (!force && healthStatuses.containsKey(key)) {
            return;
        }
        if (!healthChecksInFlight.add(key)) {
            return;
        }
        healthStatuses.put(key, HealthStatus.checking());
        healthExecutor.execute(() -> {
            HealthStatus status;
            try {
                status = checkHealth(registration);
            } catch (RuntimeException exception) {
                status = HealthStatus.error(errorLabel(exception), stackTrace(exception), resolutionFor(exception));
            } finally {
                healthChecksInFlight.remove(key);
            }
            healthStatuses.put(key, status);
        });
    }

    private String summaryDialog(Map<String, String> query) {
        String profile = query.get("summary");
        if (profile == null || profile.isBlank()) {
            return "";
        }
        Optional<ServerRegistration> registration = findRegistration(profile);
        if (registration.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"modal-backdrop\"><section class=\"modal\"><div class=\"panel-head\"><div><h2>Server summary</h2><p>")
                .append(escape(registration.get().profile()))
                .append("</p></div><a class=\"button ghost\" href=\"")
                .append(link("/", Map.of("profile", query.getOrDefault("profile", registration.get().profile()),
                        "sort", query.getOrDefault("sort", "profile"),
                        "dir", query.getOrDefault("dir", "asc"))))
                .append("\">Close</a></div>");
        html.append(summaryTable(registration.get()));
        html.append("</section></div>");
        return html.toString();
    }

    private String summaryTable(ServerRegistration registration) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"summary-grid\">");
        html.append(summaryRow("Profile", registration.profile()));
        html.append(summaryRow("MCP URL", registration.url()));
        html.append(summaryRow("Run status", mcpServerManager.isRunning(registration) ? "running" : "stopped"));
        html.append(summaryRow("Config file", registration.configFile().toString()));
        if (Files.exists(registration.configFile())) {
            try {
                MailConfiguration configuration = new ConfigurationLoader(registration.configFile()).load(registration.profile());
                html.append(summaryRow("IMAP", configuration.imap().host() + ":" + configuration.imap().port() + " · " + configuration.imap().security()));
                html.append(summaryRow("SMTP", configuration.smtp().host() + ":" + configuration.smtp().port() + " · " + configuration.smtp().security()));
                html.append(summaryRow("Username", configuration.username()));
                html.append(summaryRow("Sender", configuration.fromName().isBlank()
                        ? configuration.fromAddress()
                        : configuration.fromName() + " <" + configuration.fromAddress() + ">"));
                html.append(summaryRow("Reply-To", configuration.replyToAddress().isBlank() ? "(none)" : configuration.replyToAddress()));
                html.append(summaryRow("Incoming folders", configuration.incomingMailboxesProperty()));
                html.append(summaryRow("Sent folder", configuration.sentMailbox()));
                html.append(summaryRow("Trash folder", configuration.trashMailbox()));
                html.append(summaryRow("Mail check", healthStatuses.getOrDefault(healthKey(registration), HealthStatus.notChecked()).label()));
            } catch (ConfigurationException exception) {
                html.append(summaryRow("Configuration error", SafeErrorMessage.clean(exception.getMessage())));
            }
        } else {
            html.append(summaryRow("Configuration", "Missing"));
        }
        html.append("</div>");
        return html.toString();
    }

    private static String summaryRow(String label, String value) {
        return "<div class=\"summary-label\">" + escape(label) + "</div><div class=\"summary-value\">" + escape(value) + "</div>";
    }

    private String profileForm(ProfileForm profile) {
        StringBuilder html = new StringBuilder();
        html.append("<form method=\"post\" action=\"").append(action("/profile")).append("\" autocomplete=\"off\">");
        html.append("<input type=\"hidden\" name=\"originalProfile\" value=\"").append(escape(profile.originalProfile())).append("\">");
        html.append("<div class=\"config-scroll-body\">");
        html.append("""
                <div class="config-tabs" role="tablist">
                  <button type="button" class="tab-button active" data-tab="server">Server</button>
                  <button type="button" class="tab-button" data-tab="incoming">Incoming mail</button>
                  <button type="button" class="tab-button" data-tab="outgoing">Outgoing mail</button>
                  <button type="button" class="tab-button" data-tab="identity">Identity</button>
                </div>
                """);
        html.append("<div class=\"config-section active\" data-panel=\"server\">");
        html.append("<h3>Server</h3>");
        html.append(input("Profile", "profile", profile.profile(), "Short name used by Codex, OptimumAI, and logs.", true));
        html.append(input("Local MCP port", "mcpPort", Integer.toString(profile.mcpPort()), "Usually 8095, 8096, 8097...", true));
        html.append("</div>");

        html.append("<div class=\"config-section\" data-panel=\"incoming\">");
        html.append("<h3>Incoming mail</h3>");
        html.append(input("IMAP host", "imapHost", profile.imapHost(), "Example: imap.example.com", true));
        html.append(input("IMAP port", "imapPort", Integer.toString(profile.imapPort()), "993 for SSL/TLS.", true));
        html.append(select("IMAP security", "imapSecurity", profile.imapSecurity()));
        html.append(input("Incoming folders", "incomingMailboxes", profile.incomingMailboxes(), "Comma-separated IMAP folders read by default.", true));
        html.append("</div>");

        html.append("<div class=\"config-section\" data-panel=\"outgoing\">");
        html.append("<h3>Outgoing mail</h3>");
        html.append(input("SMTP host", "smtpHost", profile.smtpHost(), "Example: smtp.example.com", true));
        html.append(input("SMTP port", "smtpPort", Integer.toString(profile.smtpPort()), "465 for SSL/TLS, 587 for STARTTLS.", true));
        html.append(select("SMTP security", "smtpSecurity", profile.smtpSecurity()));
        html.append("</div>");

        html.append("<div class=\"config-section\" data-panel=\"identity\">");
        html.append("<h3>Identity</h3>");
        html.append(input("Email username", "username", profile.username(), "", true));
        html.append(input("Sender address", "fromAddress", profile.fromAddress(), "", true));
        html.append(input("Sender name", "fromName", profile.fromName(), "", false));
        html.append(input("Reply-To address", "replyToAddress", profile.replyToAddress(), "Optional.", false));
        html.append(input("Sent folder", "sentMailbox", profile.sentMailbox(), "", true));
        html.append(input("Trash folder", "trashMailbox", profile.trashMailbox(), "", true));
        html.append(passwordInput("Mailbox password", "password", "Leave empty to keep the stored password."));
        html.append("</div>");
        html.append("</div>");
        html.append("<div class=\"form-actions\">");
        html.append("<button class=\"button ghost\" type=\"button\" onclick=\"copyText('").append(js(profile.url())).append("')\">Copy MCP URL</button>");
        html.append("<button class=\"button primary\" type=\"submit\" name=\"action\" value=\"save\">Save</button>");
        html.append("<button class=\"button strong\" type=\"submit\" name=\"action\" value=\"start\">Save and start</button>");
        html.append("</div>");
        html.append("</form>");
        return html.toString();
    }

    private void handleProfilePost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        SaveResult result = saveProfile(values);
        String action = values.getOrDefault("action", "save");
        if ("start".equals(action)) {
            mcpServerManager.start(result.registration(), result.transientPassword(), vaultPasswordForStart());
            queueHealthCheck(result.registration(), true);
            redirect(exchange, "/", Map.of("profile", result.registration().profile(), "status", "Saved and started " + result.registration().profile() + "."));
            return;
        }
        redirect(exchange, "/", Map.of("profile", result.registration().profile(), "status", "Saved " + result.registration().profile() + "."));
    }

    private SaveResult saveProfile(Map<String, String> values) {
        String profile = ServerRegistry.registryName(required(values, "profile"));
        ServerRegistration registration = new ServerRegistration(
                profile,
                registry.configFile(profile),
                registry.runDir(profile),
                LOCAL_HOST,
                parsePort(required(values, "mcpPort"), "Local MCP port")
        );
        validatePortAvailability(registration);
        ConfigurationDraft draft = new ConfigurationDraft(
                profile,
                required(values, "imapHost"),
                parsePort(required(values, "imapPort"), "IMAP port"),
                ConnectionSecurity.parse(required(values, "imapSecurity"), "imap.security"),
                required(values, "smtpHost"),
                parsePort(required(values, "smtpPort"), "SMTP port"),
                ConnectionSecurity.parse(required(values, "smtpSecurity"), "smtp.security"),
                required(values, "username"),
                required(values, "fromAddress"),
                values.getOrDefault("fromName", "").trim(),
                values.getOrDefault("replyToAddress", "").trim(),
                values.getOrDefault("incomingMailboxes", MailConfiguration.DEFAULT_INCOMING_MAILBOX).trim(),
                required(values, "sentMailbox"),
                required(values, "trashMailbox")
        );
        new ConfigurationWriter(registration.configFile()).write(draft);
        registry.write(registration);
        healthStatuses.put(healthKey(registration), HealthStatus.notChecked());

        char[] password = values.getOrDefault("password", "").toCharArray();
        try {
            if (password.length > 0) {
                if (secretStore.supportsDurableStorage()) {
                    secretStore.writePassword(profile, password);
                }
            }
            String transientPassword = password.length > 0 ? new String(password) : "";
            return new SaveResult(registration, transientPassword);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void handleStartPost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        ServerRegistration registration = requiredRegistration(values);
        mcpServerManager.start(registration, "", vaultPasswordForStart());
        queueHealthCheck(registration, true);
        redirect(exchange, "/", Map.of("profile", registration.profile(), "status", "Started " + registration.profile() + "."));
    }

    private void handleStopPost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        ServerRegistration registration = requiredRegistration(values);
        mcpServerManager.stop(registration);
        healthStatuses.put(healthKey(registration), HealthStatus.stopped());
        redirect(exchange, "/", Map.of("profile", registration.profile(), "status", "Stopped " + registration.profile() + "."));
    }

    private void handleDeletePost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        ServerRegistration registration = requiredRegistration(values);
        mcpServerManager.stop(registration);
        String secretWarning = "";
        try {
            boolean removed = secretStore.deletePassword(registration.profile());
            if (!removed) {
                secretWarning = " Stored password was not present or could not be removed automatically.";
            }
        } catch (ConfigurationException exception) {
            secretWarning = " Stored password was not removed: " + SafeErrorMessage.clean(exception.getMessage());
        }
        healthStatuses.remove(healthKey(registration));
        registry.delete(registration);
        redirect(exchange, "/", Map.of("mode", "new", "status", "Deleted " + registration.profile() + "." + secretWarning));
    }

    private String vaultPage(Map<String, String> query) {
        StringBuilder html = new StringBuilder();
        html.append(pageStart("Change vault password"));
        html.append(hero());
        html.append("<main class=\"single panel\"><div class=\"panel-head\"><div><h2>Change vault password</h2>");
        html.append("<p>Linux encrypted vault used by this server process.</p></div></div>");
        String status = query.getOrDefault("status", "");
        String error = query.getOrDefault("error", "");
        if (!status.isBlank()) {
            html.append("<div class=\"notice ok\">").append(escape(status)).append("</div>");
        }
        if (!error.isBlank()) {
            html.append("<div class=\"notice error\">").append(escape(error)).append("</div>");
        }
        if (!LocalSecretStore.systemUsesEncryptedVault()) {
            html.append("<div class=\"notice warn\">This platform does not use the Linux encrypted vault.</div>");
            html.append("<div class=\"form-actions\"><a class=\"button ghost\" href=\"").append(link("/", Map.of())).append("\">Back</a></div>");
        } else {
            html.append("<form method=\"post\" action=\"").append(action("/vault")).append("\" autocomplete=\"off\">");
            html.append(passwordInput("New vault password", "vaultPassword", "Used when this Linux server starts."));
            html.append(passwordInput("Confirm password", "confirmVaultPassword", "Use the same value."));
            html.append("<div class=\"form-actions\"><a class=\"button ghost\" href=\"").append(link("/", Map.of()))
                    .append("\">Back</a><button class=\"button strong\" type=\"submit\">Change vault password</button></div>");
            html.append("</form>");
        }
        html.append("</main>").append(pageEnd());
        return html.toString();
    }

    private void handleVaultPost(HttpExchange exchange) throws IOException {
        if (!LocalSecretStore.systemUsesEncryptedVault()) {
            redirect(exchange, "/vault", Map.of("error", "This platform does not use the Linux encrypted vault."));
            return;
        }
        Map<String, String> values = postForm(exchange);
        char[] newPassword = values.getOrDefault("vaultPassword", "").toCharArray();
        char[] confirmation = values.getOrDefault("confirmVaultPassword", "").toCharArray();
        try {
            if (newPassword.length == 0) {
                throw new ConfigurationException("Vault password is required.");
            }
            if (!Arrays.equals(newPassword, confirmation)) {
                throw new ConfigurationException("Vault password confirmation does not match.");
            }
            changeVaultPassword(newPassword);
            healthStatuses.clear();
            redirect(exchange, "/", Map.of("status", "Vault password changed. Use the new password at the next server start."));
        } finally {
            Arrays.fill(newPassword, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    private String checkDetailsPage(Map<String, String> query) {
        ServerRegistration registration = findRegistration(query.getOrDefault("profile", ""))
                .orElseThrow(() -> new ConfigurationException("Unknown profile: " + query.getOrDefault("profile", "")));
        HealthStatus health = healthStatuses.getOrDefault(healthKey(registration), HealthStatus.notChecked());
        StringBuilder html = new StringBuilder();
        html.append(pageStart("Mail check details"));
        html.append(hero());
        html.append("<main class=\"single panel\"><div class=\"panel-head\"><div><h2>Mail check details</h2><p>")
                .append(escape(registration.profile()))
                .append(" · ")
                .append(escape(registration.url()))
                .append("</p></div></div>");
        html.append("<div class=\"mail-check-detail\">")
                .append(healthBadge(health))
                .append("</div>");
        html.append("<textarea readonly spellcheck=\"false\">")
                .append(escape(health.diagnosticText(registration)))
                .append("</textarea>");
        html.append("<div class=\"form-actions\"><button class=\"button ghost\" type=\"button\" onclick=\"copyText(document.querySelector('textarea').value)\">Copy details</button>");
        html.append("<a class=\"button ghost\" href=\"")
                .append(link("/", Map.of("profile", registration.profile())))
                .append("\">Back</a></div>");
        html.append("</main>").append(pageEnd());
        return html.toString();
    }

    private String exportPage(Map<String, String> query) {
        StringBuilder html = new StringBuilder();
        html.append(pageStart("Export profiles"));
        html.append(hero());
        html.append("<main class=\"single panel\"><div class=\"panel-head\"><div><h2>Export profiles</h2>");
        html.append("<p>No password, token, or vault secret is exported.</p></div></div>");
        String error = query.getOrDefault("error", "");
        if (!error.isBlank()) {
            html.append("<div class=\"notice error\">").append(escape(error)).append("</div>");
        }
        List<ServerRegistration> registrations = registry.list();
        if (registrations.isEmpty()) {
            html.append("<div class=\"empty\">No profile to export.</div>");
        } else {
            html.append("<form method=\"post\" action=\"").append(action("/export")).append("\">");
            for (int index = 0; index < registrations.size(); index++) {
                ServerRegistration registration = registrations.get(index);
                html.append("<label class=\"check\"><input type=\"checkbox\" name=\"profile.")
                        .append(index).append("\" value=\"").append(escape(registration.profile()))
                        .append("\" checked> <span>").append(escape(registration.profile()))
                        .append("</span><small>").append(escape(registration.url())).append("</small></label>");
            }
            html.append("<div class=\"form-actions\"><a class=\"button ghost\" href=\"").append(link("/", Map.of()))
                    .append("\">Back</a><button class=\"button strong\" type=\"submit\">Download export</button></div></form>");
        }
        html.append("</main>").append(pageEnd());
        return html.toString();
    }

    private void handleExportPost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        List<ProfileSnapshot> snapshots = new ArrayList<>();
        for (String profile : values.values()) {
            Optional<ServerRegistration> registration = findRegistration(profile);
            if (registration.isPresent() && Files.exists(registration.get().configFile())) {
                MailConfiguration configuration = new ConfigurationLoader(registration.get().configFile()).load(registration.get().profile());
                snapshots.add(transfer.snapshot(registration.get(), configuration));
            }
        }
        if (snapshots.isEmpty()) {
            redirect(exchange, "/export", Map.of("error", "Select at least one valid profile."));
            return;
        }
        String export = transfer.write(snapshots);
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"opcoach-mcp-mail-profiles.properties\"");
        send(exchange, 200, "text/plain; charset=utf-8", export);
    }

    private String importPage(Map<String, String> query) {
        StringBuilder html = new StringBuilder();
        html.append(pageStart("Import profiles"));
        html.append(hero());
        html.append("<main class=\"single panel\"><div class=\"panel-head\"><div><h2>Import profiles</h2>");
        html.append("<p>Paste or load a profile export. Passwords are never included; you will enter them locally after import.</p></div></div>");
        String error = query.getOrDefault("error", "");
        if (!error.isBlank()) {
            html.append("<div class=\"notice error\">").append(escape(error)).append("</div>");
        }
        html.append("<form method=\"post\" action=\"").append(action("/import/preview")).append("\">");
        html.append("<label class=\"file-loader\">Load export file<input type=\"file\" accept=\".properties,.txt\" onchange=\"loadFile(this)\"></label>");
        html.append("<textarea id=\"payload\" name=\"payload\" rows=\"16\" spellcheck=\"false\" required></textarea>");
        html.append("<div class=\"form-actions\"><a class=\"button ghost\" href=\"").append(link("/", Map.of()))
                .append("\">Back</a><button class=\"button strong\" type=\"submit\">Preview import</button></div>");
        html.append("</form></main>").append(pageEnd());
        return html.toString();
    }

    private void handleImportPreviewPost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        List<ProfileSnapshot> snapshots = transfer.read(values.getOrDefault("payload", ""));
        StringBuilder html = new StringBuilder();
        html.append(pageStart("Import preview"));
        html.append(hero());
        html.append("<main class=\"single panel\"><div class=\"panel-head\"><div><h2>Import preview</h2>");
        html.append("<p>Existing local profiles are unchecked by default. New profiles are checked.</p></div></div>");
        html.append("<form method=\"post\" action=\"").append(action("/import/apply")).append("\">");
        html.append("<input type=\"hidden\" name=\"count\" value=\"").append(snapshots.size()).append("\">");
        for (int index = 0; index < snapshots.size(); index++) {
            ProfileSnapshot snapshot = snapshots.get(index);
            boolean exists = findRegistration(snapshot.profile()).isPresent();
            int port = suggestedImportPort(snapshot);
            html.append("<div class=\"import-card\">");
            html.append("<label class=\"check\"><input type=\"checkbox\" name=\"include.")
                    .append(index).append("\" ").append(exists ? "" : "checked").append("> <span>")
                    .append(escape(snapshot.profile())).append("</span>");
            html.append(exists ? "<small>Already configured locally; unchecked by default.</small>" : "<small>New local profile; checked by default.</small>");
            html.append("</label>");
            html.append(hidden(index, "profile", snapshot.profile()));
            html.append(hidden(index, "imapHost", snapshot.imapHost()));
            html.append(hidden(index, "imapPort", Integer.toString(snapshot.imapPort())));
            html.append(hidden(index, "imapSecurity", snapshot.imapSecurity().propertyValue()));
            html.append(hidden(index, "smtpHost", snapshot.smtpHost()));
            html.append(hidden(index, "smtpPort", Integer.toString(snapshot.smtpPort())));
            html.append(hidden(index, "smtpSecurity", snapshot.smtpSecurity().propertyValue()));
            html.append(hidden(index, "username", snapshot.username()));
            html.append(hidden(index, "fromAddress", snapshot.fromAddress()));
            html.append(hidden(index, "fromName", snapshot.fromName()));
            html.append(hidden(index, "replyToAddress", snapshot.replyToAddress()));
            html.append(hidden(index, "incomingMailboxes", snapshot.incomingMailboxes()));
            html.append(hidden(index, "sentMailbox", snapshot.sentMailbox()));
            html.append(hidden(index, "trashMailbox", snapshot.trashMailbox()));
            html.append("<label>Local MCP port<input name=\"profile.").append(index)
                    .append(".mcpPort\" value=\"").append(port).append("\" type=\"number\" min=\"1\" max=\"65535\"></label>");
            if (port != snapshot.mcpPort()) {
                html.append("<div class=\"notice warn\">Port ").append(snapshot.mcpPort())
                        .append(" is already used locally; ").append(port).append(" is suggested.</div>");
            }
            html.append("</div>");
        }
        html.append("<div class=\"form-actions\"><a class=\"button ghost\" href=\"").append(link("/import", Map.of()))
                .append("\">Back</a><button class=\"button strong\" type=\"submit\">Import selected profiles</button></div>");
        html.append("</form></main>").append(pageEnd());
        send(exchange, 200, "text/html; charset=utf-8", html.toString());
    }

    private void handleImportApplyPost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        int count = parseNonNegativeInt(values.getOrDefault("count", "0"), "count");
        int imported = 0;
        String lastProfile = "";
        for (int index = 0; index < count; index++) {
            if (!values.containsKey("include." + index)) {
                continue;
            }
            ProfileSnapshot snapshot = snapshotFromImportForm(values, index);
            ServerRegistration registration = snapshot.registration(registry);
            validatePortAvailability(registration);
            new ConfigurationWriter(registration.configFile()).write(snapshot.draft());
            registry.write(registration);
            imported++;
            lastProfile = registration.profile();
        }
        if (imported == 0) {
            redirect(exchange, "/import", Map.of("error", "No profile was selected for import."));
            return;
        }
        redirect(exchange, "/", Map.of("profile", lastProfile, "status", "Imported " + imported + " profile(s). Passwords still need to be entered locally."));
    }

    private ProfileSnapshot snapshotFromImportForm(Map<String, String> values, int index) {
        String prefix = "profile." + index + ".";
        return new ProfileSnapshot(
                required(values, prefix + "profile"),
                parsePort(required(values, prefix + "mcpPort"), "MCP port"),
                required(values, prefix + "imapHost"),
                parsePort(required(values, prefix + "imapPort"), "IMAP port"),
                ConnectionSecurity.parse(required(values, prefix + "imapSecurity"), "imap.security"),
                required(values, prefix + "smtpHost"),
                parsePort(required(values, prefix + "smtpPort"), "SMTP port"),
                ConnectionSecurity.parse(required(values, prefix + "smtpSecurity"), "smtp.security"),
                required(values, prefix + "username"),
                required(values, prefix + "fromAddress"),
                values.getOrDefault(prefix + "fromName", ""),
                values.getOrDefault(prefix + "replyToAddress", ""),
                values.getOrDefault(prefix + "incomingMailboxes", MailConfiguration.DEFAULT_INCOMING_MAILBOX),
                required(values, prefix + "sentMailbox"),
                required(values, prefix + "trashMailbox")
        );
    }

    private int suggestedImportPort(ProfileSnapshot snapshot) {
        ServerRegistration candidate = snapshot.registration(registry);
        try {
            validatePortAvailability(candidate);
            return snapshot.mcpPort();
        } catch (ConfigurationException exception) {
            return firstFreePort(8095);
        }
    }

    private String selectedProfileName(Map<String, String> query, List<ServerRegistration> registrations) {
        if ("new".equals(query.get("mode"))) {
            return "default";
        }
        String requested = query.get("profile");
        if (requested != null) {
            Optional<ServerRegistration> registration = findRegistration(requested);
            if (registration.isPresent()) {
                return registration.get().profile();
            }
        }
        return registrations.isEmpty() ? "default" : registrations.getFirst().profile();
    }

    private ProfileForm loadProfileForm(String profile, List<ServerRegistration> registrations) {
        Optional<ServerRegistration> registration = registrations.stream()
                .filter(candidate -> candidate.profile().equals(profile))
                .findFirst();
        if (registration.isEmpty() || !Files.exists(registration.get().configFile())) {
            return ProfileForm.defaults(profile, registration.map(ServerRegistration::port).orElseGet(() -> firstFreePort(8095)));
        }
        try {
            MailConfiguration configuration = new ConfigurationLoader(registration.get().configFile()).load(registration.get().profile());
            return ProfileForm.from(registration.get(), configuration, mcpServerManager.isRunning(registration.get()));
        } catch (ConfigurationException exception) {
            return ProfileForm.defaults(profile, registration.get().port());
        }
    }

    private Optional<ServerRegistration> findRegistration(String profile) {
        String normalized = ServerRegistry.registryName(profile);
        return registry.list().stream()
                .filter(candidate -> candidate.profile().equals(normalized))
                .findFirst();
    }

    private ServerRegistration requiredRegistration(Map<String, String> values) {
        String profile = required(values, "profile");
        return findRegistration(profile)
                .orElseThrow(() -> new ConfigurationException("Unknown profile: " + profile));
    }

    private void validatePortAvailability(ServerRegistration registration) {
        for (ServerRegistration existing : registry.list()) {
            boolean sameProfile = existing.profile().equals(registration.profile());
            if (!sameProfile && existing.port() == registration.port()) {
                throw new ConfigurationException("Port " + registration.port() + " is already used by profile " + existing.profile() + ".");
            }
        }
        Optional<ServerRegistration> existingRegistration = findRegistration(registration.profile());
        boolean sameRunningServer = existingRegistration.isPresent()
                && existingRegistration.get().port() == registration.port()
                && mcpServerManager.isRunning(existingRegistration.get());
        if (!sameRunningServer && !isPortFree(registration.port())) {
            throw new ConfigurationException("Port " + registration.port() + " is already in use by another process.");
        }
    }

    private int firstFreePort(int start) {
        for (int port = start; port <= 65535; port++) {
            int candidate = port;
            boolean registered = registry.list().stream().anyMatch(registration -> registration.port() == candidate);
            if (!registered && isPortFree(candidate)) {
                return candidate;
            }
        }
        return start;
    }

    private static boolean isPortFree(int port) {
        try (var ignored = new java.net.ServerSocket(port)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private synchronized String vaultPasswordForStart() {
        return vaultPassword.length == 0 ? "" : new String(vaultPassword);
    }

    private synchronized void changeVaultPassword(char[] newPassword) {
        LocalSecretStore.changeSystemVaultPassword(vaultPassword, newPassword);
        Arrays.fill(vaultPassword, '\0');
        vaultPassword = copy(newPassword);
        secretStore = LocalSecretStore.system(vaultPassword);
    }

    private synchronized void clearVaultPassword() {
        Arrays.fill(vaultPassword, '\0');
        vaultPassword = new char[0];
    }

    private HealthStatus checkHealth(ServerRegistration registration) {
        boolean running = mcpServerManager.isRunning(registration);
        if (running && !httpHealthOk(registration)) {
            return HealthStatus.warning(
                    "HTTP unavailable",
                    "GET " + healthUrl(registration) + " did not return a 2xx response within " + HEALTH_TIMEOUT.toSeconds() + " seconds.",
                    "Check that the local MCP process is still running and that no other process is bound to this port."
            );
        }
        if (!Files.exists(registration.configFile())) {
            return HealthStatus.warning(
                    "Missing config",
                    "Configuration file not found: " + registration.configFile(),
                    "Save the profile again from the web manager."
            );
        }
        MailConfiguration configuration = new ConfigurationLoader(registration.configFile()).load(registration.profile());
        String password;
        try {
            password = secretStore.readPassword(configuration.profile()).orElse("");
        } catch (ConfigurationException exception) {
            return HealthStatus.warning("Secret locked", stackTrace(exception), "Unlock the local secret store or save the profile with the vault password.");
        }
        if (password.isBlank()) {
            return HealthStatus.warning(
                    running ? "MCP ok, secret locked" : "Secret missing",
                    "No password is available for profile " + configuration.profile() + ".",
                    "Enter the mailbox password in the web manager, then save the profile again."
            );
        }
        List<MailboxInfo> mailboxes;
        try {
            mailboxes = new JakartaImapClient(configuration, password).listMailboxes(false);
        } catch (RuntimeException exception) {
            return HealthStatus.error(errorLabel(exception), stackTrace(exception), resolutionFor(exception));
        }
        List<String> missing = new ArrayList<>();
        List<MailboxInfo> incoming = new ArrayList<>();
        for (String mailbox : configuration.incomingMailboxes()) {
            MailboxInfo found = findMailbox(mailboxes, mailbox);
            if (found == null) {
                missing.add(mailbox);
            } else {
                incoming.add(found);
            }
        }
        if (findMailbox(mailboxes, configuration.sentMailbox()) == null) {
            missing.add(configuration.sentMailbox());
        }
        if (findMailbox(mailboxes, configuration.trashMailbox()) == null) {
            missing.add(configuration.trashMailbox());
        }
        if (!missing.isEmpty()) {
            String missingLabel = missing.size() == 1 ? missing.getFirst() : missing.getFirst() + " +" + (missing.size() - 1);
            return HealthStatus.warning(
                    "Missing " + missingLabel,
                    "Missing configured folder(s): " + String.join(", ", missing) + "\n\nAvailable folders:\n" + mailboxList(mailboxes),
                    "Open the mailbox folder list and update the Incoming/Sent/Trash folder names in the profile configuration."
            );
        }
        return HealthStatus.ok(incomingHealthLabel(incoming));
    }

    private boolean httpHealthOk(ServerRegistration registration) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl(registration)))
                    .timeout(HEALTH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String healthUrl(ServerRegistration registration) {
        return "http://" + registration.host() + ":" + registration.port() + "/health";
    }

    private static MailboxInfo findMailbox(List<MailboxInfo> mailboxes, String fullName) {
        for (MailboxInfo mailbox : mailboxes) {
            if (mailbox.fullName().equalsIgnoreCase(fullName)) {
                return mailbox;
            }
        }
        return null;
    }

    private static String mailboxList(List<MailboxInfo> mailboxes) {
        if (mailboxes.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (MailboxInfo mailbox : mailboxes) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(mailbox.fullName())
                    .append(" (")
                    .append(mailbox.messageCount())
                    .append(")");
        }
        return builder.toString();
    }

    private static String incomingHealthLabel(List<MailboxInfo> mailboxes) {
        if (mailboxes.isEmpty()) {
            return "OK";
        }
        if (mailboxes.size() == 1) {
            MailboxInfo mailbox = mailboxes.getFirst();
            return mailbox.fullName() + " " + mailbox.messageCount();
        }
        int total = mailboxes.stream().mapToInt(MailboxInfo::messageCount).sum();
        return total + " in " + mailboxes.size() + " folders";
    }

    private static String errorLabel(Throwable throwable) {
        String text = throwableText(throwable);
        if (containsAny(text, "auth", "login", "credential", "password", "invalid credentials")) {
            return "Error: Authentication";
        }
        if (containsAny(text, "pkix", "certpath", "certification path", "certificate_unknown")) {
            return "Error: TLS certificate";
        }
        if (containsAny(text, "timeout", "timed out", "connection", "unknown host", "network", "refused")) {
            return "Error: Network";
        }
        if (containsAny(text, "ssl", "tls", "certificate", "handshake")) {
            return "Error: TLS";
        }
        if (containsAny(text, "configuration", "missing", "invalid")) {
            return "Error: Configuration";
        }
        return "Error: IMAP";
    }

    private static String resolutionFor(Throwable throwable) {
        String text = throwableText(throwable);
        if (containsAny(text, "auth", "login", "credential", "password", "invalid credentials")) {
            return "Check the email username and app password. If the provider requires app passwords, generate a new one and save it again in the web manager.";
        }
        if (containsAny(text, "pkix", "certpath", "certification path", "certificate_unknown")) {
            return "Use the exact IMAP hostname published by the mail provider and update the trusted root certificates. On Windows, the application uses both the Java and Windows root stores unless a custom Java truststore is configured.";
        }
        if (containsAny(text, "timeout", "timed out", "connection", "unknown host", "network", "refused")) {
            return "Check the IMAP host, port, network access, firewall, and whether the provider allows IMAP connections from this machine.";
        }
        if (containsAny(text, "ssl", "tls", "certificate", "handshake")) {
            return "Check the selected security mode, port, and provider TLS certificate requirements.";
        }
        if (containsAny(text, "configuration", "missing", "invalid")) {
            return "Review the profile configuration and save it again.";
        }
        return "Review the complete exception below, then check the IMAP provider settings and mailbox folder names.";
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String throwableText(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            builder.append(current.getClass().getName()).append(' ');
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String healthKey(ServerRegistration registration) {
        return registration.profile() + "@" + registration.host() + ":" + registration.port();
    }

    private String healthBadge(HealthStatus health) {
        return "<span class=\"badge health " + health.severity().cssClass + "\">" + escape(health.label()) + "</span>";
    }

    private String actionButton(String path, String label, String profile, boolean disabled, String style, String confirmScript) {
        return """
                <form method="post" action="%s" class="inline-action" onsubmit="%s">
                  <input type="hidden" name="profile" value="%s">
                  <button class="button %s" type="submit" %s>%s</button>
                </form>
                """.formatted(action(path), confirmScript, escape(profile), style, disabled ? "disabled" : "", escape(label));
    }

    private static String input(String label, String name, String value, String hint, boolean required) {
        return """
                <label>
                  <span>%s</span>
                  <input name="%s" value="%s" %s>
                  <small>%s</small>
                </label>
                """.formatted(escape(label), escape(name), escape(value), required ? "required" : "", escape(hint));
    }

    private static String passwordInput(String label, String name, String hint) {
        return """
                <label>
                  <span>%s</span>
                  <input name="%s" type="password" autocomplete="new-password">
                  <small>%s</small>
                </label>
                """.formatted(escape(label), escape(name), escape(hint));
    }

    private static String select(String label, String name, ConnectionSecurity selected) {
        StringBuilder html = new StringBuilder();
        html.append("<label><span>").append(escape(label)).append("</span><select name=\"").append(escape(name)).append("\">");
        for (ConnectionSecurity security : ConnectionSecurity.values()) {
            html.append("<option value=\"").append(security.propertyValue()).append("\"")
                    .append(security == selected ? " selected" : "")
                    .append(">").append(security.name()).append("</option>");
        }
        html.append("</select><small></small></label>");
        return html.toString();
    }

    private static String sectionTitle(String title) {
        return "<h3>" + escape(title) + "</h3>";
    }

    private static String hidden(int index, String name, String value) {
        return "<input type=\"hidden\" name=\"profile." + index + "." + escape(name) + "\" value=\"" + escape(value) + "\">";
    }

    private String action(String path) {
        return path + "?token=" + urlEncode(token);
    }

    private String link(String path, Map<String, String> params) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("token", token);
        all.putAll(params);
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return path + "?" + query;
    }

    private void redirect(HttpExchange exchange, String path, Map<String, String> params) throws IOException {
        exchange.getResponseHeaders().add("Location", link(path, params));
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private boolean isAuthorized(HttpExchange exchange) {
        return token.equals(parseForm(exchange.getRequestURI().getRawQuery()).get("token"));
    }

    private static Map<String, String> postForm(HttpExchange exchange) throws IOException {
        return parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            values.put(urlDecode(key), urlDecode(value));
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing required field: " + key);
        }
        return value.trim();
    }

    private static int parsePort(String raw, String label) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException(raw);
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Invalid " + label + ": " + raw);
        }
    }

    private static int parseNonNegativeInt(String raw, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new NumberFormatException(raw);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Invalid " + label + ": " + raw);
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.getResponseHeaders().add("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // The printed URL is the fallback on headless systems.
        }
    }

    private static String pageStart(String title) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    :root { --indigo:#4B3F72; --soft:#6C63A6; --blue:#48A5AE; --rose:#E75294; --green:#58B025; --surface:#F8F8FC; --text:#25252A; --muted:#6F6F71; --border:#E7E4F3; }
                    * { box-sizing: border-box; }
                    body { margin:0; font:14px/1.45 Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color:var(--text); background:var(--surface); }
                    .hero { min-height: 150px; padding: 26px 36px; color:white; background: radial-gradient(circle at 78%% 90%%, rgba(250,189,67,.28), transparent 18%%), radial-gradient(circle at 98%% 0%%, rgba(255,255,255,.20), transparent 24%%), linear-gradient(110deg, var(--indigo), var(--soft)); display:flex; align-items:center; justify-content:space-between; gap:24px; }
                    .hero h1 { margin:0; font-size:34px; line-height:1; letter-spacing:0; }
                    .hero p { margin:12px 0 0; color:#F2F1FA; font-size:15px; }
                    .hero .eyebrow { font-weight:700; color:#E6E4F3; margin-bottom:10px; }
                    .hero-actions { display:flex; gap:10px; align-items:center; }
                    .hero-button { color:white; background:rgba(255,255,255,.14); border:1px solid rgba(255,255,255,.34); }
                    .layout { display:grid; grid-template-columns: minmax(420px, 3fr) minmax(360px, 2fr); gap:24px; padding:28px; height:calc(100vh - 150px); min-height:520px; }
                    .single { max-width: 1040px; margin:28px auto; }
                    .panel { background:white; border:1px solid var(--border); border-radius:16px; box-shadow: 6px 10px 0 rgba(75,63,114,.07); padding:20px; }
                    .layout .panel { display:flex; flex-direction:column; min-height:360px; overflow:hidden; }
                    .panel-head { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; margin-bottom:16px; }
                    .panel h2 { margin:0; font-size:22px; font-weight:700; }
                    .panel p { margin:4px 0 0; color:var(--muted); font-size:13px; }
                    .panel-scroll { flex:1 1 auto; min-height:0; overflow:auto; }
                    .servers-scroll { border:1px solid #F0EEF8; border-radius:14px; }
                    .servers-scroll table { min-width:720px; }
                    .servers-scroll .empty { margin:0; }
                    table { width:100%%; border-collapse:collapse; }
                    th { text-align:left; color:#535057; border-bottom:1px solid var(--border); padding:8px 10px; font-size:12px; font-weight:700; }
                    td { border-bottom:1px solid #F0EEF8; padding:10px; vertical-align:middle; }
                    tbody tr { cursor:pointer; }
                    tbody tr:hover { background:#FAF9FF; }
                    tr.selected { background:#F2F0FA; }
                    a { color:var(--indigo); font-weight:700; text-decoration:none; }
                    .sort-link { color:#535057; }
                    .link-button { border:0; background:transparent; color:var(--soft); cursor:pointer; font:inherit; padding:0; }
                    .badge { border-radius:999px; padding:5px 11px; font-weight:700; font-size:12px; display:inline-flex; }
                    .running { color:var(--green); background:#EAF7E4; }
                    .stopped { color:var(--rose); background:#F9DDE9; }
                    .health-ok { color:var(--green); background:#EAF7E4; }
                    .health-warn { color:#B36B00; background:#FFF1CF; }
                    .health-error { color:var(--rose); background:#F9DDE9; }
                    .health-neutral { color:var(--muted); background:#F0F0F4; }
                    .mail-check { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
                    .details-link { font-size:12px; }
                    .view-column { width:48px; text-align:center; }
                    .eye-button { display:inline-flex; width:30px; height:30px; align-items:center; justify-content:center; border:1px solid #ECEAF7; border-radius:999px; background:white; font-size:15px; }
                    .mail-check-detail { margin: 0 0 14px; }
                    .row-actions, .form-actions { display:flex; gap:10px; flex-wrap:wrap; justify-content:flex-end; align-items:center; margin-top:22px; }
                    .servers .row-actions, .config .form-actions { flex:0 0 auto; background:white; border-top:1px solid #F0EEF8; margin-top:14px; padding-top:14px; }
                    .inline-action { display:inline; }
                    .button { border:0; border-radius:12px; padding:10px 16px; font-weight:700; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; min-height:40px; }
                    .button:disabled { cursor:not-allowed; background:#ECECF1 !important; color:#A2A2AA !important; }
                    .primary { color:white; background:linear-gradient(110deg, var(--soft), #8F88C7); }
                    .strong { color:white; background:linear-gradient(110deg, var(--indigo), var(--rose)); }
                    .good { color:white; background:linear-gradient(110deg, var(--green), #80C048); }
                    .danger { color:white; background:linear-gradient(110deg, var(--rose), #E975A7); }
                    .warning { color:white; background:linear-gradient(110deg, #B36B00, #FABD43); }
                    .ghost { color:var(--indigo); background:white; border:1px solid #ECEAF7; }
                    .config form { display:flex; flex:1 1 auto; flex-direction:column; min-height:0; }
                    .config-scroll-body { flex:1 1 auto; min-height:0; overflow:auto; padding-right:4px; }
                    .config-tabs { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:12px; }
                    .tab-button { border:1px solid #DEDCEF; border-radius:999px; background:white; color:var(--indigo); padding:8px 12px; font-weight:700; cursor:pointer; }
                    .tab-button.active { background:linear-gradient(110deg, var(--indigo), var(--soft)); color:white; border-color:transparent; }
                    .config-section { display:none; background:#F4F3F8; border:1px solid #E4E1F0; border-radius:14px; padding:16px; }
                    .config-section.active { display:grid; grid-template-columns:1fr; gap:12px; }
                    .config-section h3 { margin:0 0 4px; color:var(--indigo); text-transform:uppercase; font-size:12px; font-weight:700; letter-spacing:0; }
                    label { display:grid; grid-template-columns: 160px minmax(0, 1fr); gap:10px 14px; align-items:center; font-weight:600; color:#4B4B4D; }
                    input, select, textarea { width:100%%; border:1px solid #CFCDE1; border-radius:10px; padding:9px 11px; font:inherit; color:var(--text); background:white; }
                    textarea { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; min-height:280px; }
                    small { color:#939394; font-weight:500; }
                    label small { grid-column:2; margin-top:-8px; }
                    .notice { border-radius:12px; padding:10px 12px; margin:12px 0; font-weight:700; }
                    .notice.ok { background:#EAF7E4; color:var(--green); }
                    .notice.error { background:#F9DDE9; color:var(--rose); }
                    .notice.warn { background:#FFF1CF; color:#B36B00; }
                    .empty { padding:28px; color:var(--muted); background:#FBFAFF; border:1px dashed var(--border); border-radius:16px; }
                    .check { grid-template-columns:auto 1fr; align-items:start; border:1px solid var(--border); padding:12px 14px; border-radius:14px; margin-bottom:10px; }
                    .check input { width:auto; margin-top:4px; }
                    .check small { grid-column:2; margin:0; display:block; }
                    .import-card { border:1px solid var(--border); border-radius:16px; padding:16px; margin:14px 0; }
                    .file-loader { display:block; margin-bottom:12px; }
                    .modal-backdrop { position:fixed; inset:0; background:rgba(37,37,42,.34); display:flex; align-items:flex-start; justify-content:center; padding:8vh 20px 20px; z-index:10; }
                    .modal { width:min(760px, 100%%); max-height:84vh; overflow:auto; background:white; border:1px solid var(--border); border-radius:18px; box-shadow:0 24px 80px rgba(37,37,42,.24); padding:22px; }
                    .summary-grid { display:grid; grid-template-columns: 160px minmax(0,1fr); gap:0; border:1px solid #E6E4F3; border-radius:14px; overflow:hidden; }
                    .summary-label, .summary-value { padding:10px 12px; border-bottom:1px solid #EDEBF7; }
                    .summary-label { background:#F4F3F8; color:#4B4B4D; font-weight:700; }
                    .summary-value { background:white; overflow-wrap:anywhere; }
                    .summary-label:nth-last-child(2), .summary-value:last-child { border-bottom:0; }
                    @media (max-width: 980px) { .layout { grid-template-columns:1fr; padding:16px; height:auto; min-height:0; } .layout .panel { min-height:420px; max-height:calc(100vh - 32px); } .hero { padding:28px 20px; display:block; } .hero-actions { margin-top:18px; } label { grid-template-columns:1fr; } label small { grid-column:1; } }
                  </style>
                </head>
                <body>
                <script>
                  function copyText(value) { navigator.clipboard.writeText(value); }
                  function loadFile(input) {
                    const file = input.files && input.files[0];
                    if (!file) return;
                    file.text().then(text => document.getElementById('payload').value = text);
                  }
                  document.addEventListener('DOMContentLoaded', () => {
                    const serversScroll = document.getElementById('servers-scroll');
                    const serversScrollKey = 'mcpMailManagerServersScroll';
                    if (serversScroll) {
                      try {
                        const savedScroll = JSON.parse(sessionStorage.getItem(serversScrollKey) || '{}');
                        serversScroll.scrollTop = Number(savedScroll.top) || 0;
                        serversScroll.scrollLeft = Number(savedScroll.left) || 0;
                      } catch (ignored) {
                        sessionStorage.removeItem(serversScrollKey);
                      }
                      const saveServersScroll = () => sessionStorage.setItem(serversScrollKey, JSON.stringify({
                        top: serversScroll.scrollTop,
                        left: serversScroll.scrollLeft
                      }));
                      serversScroll.addEventListener('scroll', saveServersScroll, { passive: true });
                      window.addEventListener('pagehide', saveServersScroll);
                    }
                    const preferredTab = localStorage.getItem('mcpMailManagerTab') || 'server';
                    const activate = (name) => {
                      document.querySelectorAll('.tab-button').forEach(button => button.classList.toggle('active', button.dataset.tab === name));
                      document.querySelectorAll('.config-section').forEach(panel => panel.classList.toggle('active', panel.dataset.panel === name));
                      localStorage.setItem('mcpMailManagerTab', name);
                    };
                    document.querySelectorAll('.tab-button').forEach(button => button.addEventListener('click', () => activate(button.dataset.tab)));
                    if (document.querySelector('[data-panel="' + preferredTab + '"]')) {
                      activate(preferredTab);
                    }
                  });
                </script>
                """.formatted(escape(title));
    }

    private String hero() {
        String vaultAction = LocalSecretStore.systemUsesEncryptedVault()
                ? "<a class=\"button hero-button\" href=\"" + link("/vault", Map.of()) + "\">Change vault password</a>"
                : "";
        return """
                <header class="hero">
                  <div>
                    <div class="eyebrow">OPCoach local-first MCP</div>
                    <h1>MCP Mail Local Manager</h1>
                    <p>Configure mailboxes, start local MCP servers, export/import safe profiles.</p>
                  </div>
                  <div class="hero-actions">%s</div>
                </header>
                """.formatted(vaultAction);
    }

    private static String pageEnd() {
        return "</body></html>";
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static char[] copy(char[] value) {
        return value == null ? new char[0] : Arrays.copyOf(value, value.length);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String js(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private record HealthStatus(String label, HealthSeverity severity, String detail, String suggestion) {

        static HealthStatus notChecked() {
            return new HealthStatus("not checked", HealthSeverity.NEUTRAL, "", "");
        }

        static HealthStatus checking() {
            return new HealthStatus("checking...", HealthSeverity.NEUTRAL, "", "");
        }

        static HealthStatus stopped() {
            return new HealthStatus("not running", HealthSeverity.NEUTRAL, "", "");
        }

        static HealthStatus ok(String label) {
            return new HealthStatus(label, HealthSeverity.OK, "", "");
        }

        static HealthStatus warning(String label, String detail, String suggestion) {
            return new HealthStatus(label, HealthSeverity.WARNING, detail, suggestion);
        }

        static HealthStatus error(String label, String detail, String suggestion) {
            return new HealthStatus(label, HealthSeverity.ERROR, detail, suggestion);
        }

        boolean hasDetails() {
            return (detail != null && !detail.isBlank()) || (suggestion != null && !suggestion.isBlank());
        }

        boolean isChecking() {
            return "checking...".equals(label);
        }

        String diagnosticText(ServerRegistration registration) {
            return """
                    Profile: %s
                    URL: %s
                    Status: %s

                    Suggested resolution:
                    %s

                    Details:
                    %s
                    """.formatted(
                    registration.profile(),
                    registration.url(),
                    label,
                    blankToDefault(suggestion, "No automatic suggestion is available."),
                    blankToDefault(detail, "No diagnostic details are available.")
            );
        }
    }

    private enum HealthSeverity {
        OK("health-ok"),
        WARNING("health-warn"),
        ERROR("health-error"),
        NEUTRAL("health-neutral");

        private final String cssClass;

        HealthSeverity(String cssClass) {
            this.cssClass = cssClass;
        }
    }

    private record SaveResult(ServerRegistration registration, String transientPassword) {
    }

    private record ProfileForm(
            String originalProfile,
            String profile,
            int mcpPort,
            String imapHost,
            int imapPort,
            ConnectionSecurity imapSecurity,
            String smtpHost,
            int smtpPort,
            ConnectionSecurity smtpSecurity,
            String username,
            String fromAddress,
            String fromName,
            String replyToAddress,
            String incomingMailboxes,
            String sentMailbox,
            String trashMailbox,
            boolean registered,
            boolean running
    ) {

        static ProfileForm defaults(String profile, int port) {
            String normalized = ServerRegistry.registryName(profile);
            return new ProfileForm(
                    normalized,
                    normalized,
                    port,
                    "imap.example.com",
                    993,
                    ConnectionSecurity.SSL_TLS,
                    "smtp.example.com",
                    465,
                    ConnectionSecurity.SSL_TLS,
                    "training@example.com",
                    "training@example.com",
                    "MCP Training",
                    "",
                    MailConfiguration.DEFAULT_INCOMING_MAILBOX,
                    "INBOX.Sent",
                    "INBOX.Trash",
                    false,
                    false
            );
        }

        static ProfileForm from(ServerRegistration registration, MailConfiguration configuration, boolean running) {
            return new ProfileForm(
                    registration.profile(),
                    registration.profile(),
                    registration.port(),
                    configuration.imap().host(),
                    configuration.imap().port(),
                    configuration.imap().security(),
                    configuration.smtp().host(),
                    configuration.smtp().port(),
                    configuration.smtp().security(),
                    configuration.username(),
                    configuration.fromAddress(),
                    configuration.fromName(),
                    configuration.replyToAddress(),
                    configuration.incomingMailboxesProperty(),
                    configuration.sentMailbox(),
                    configuration.trashMailbox(),
                    true,
                    running
            );
        }

        String url() {
            return "http://" + LOCAL_HOST + ":" + mcpPort + "/mcp";
        }
    }

    private record CliOptions(int port, boolean openBrowser, boolean startRegistered) {

        static CliOptions parse(String[] args) {
            int port = DEFAULT_PORT;
            boolean openBrowser = true;
            boolean startRegistered = false;
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--port" -> port = parsePort(requireValue(args, ++index, "--port"), "web manager port");
                    case "--no-open" -> openBrowser = false;
                    case "--start-registered" -> startRegistered = true;
                    case "-h", "--help" -> {
                        System.out.println(help());
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown web manager argument: " + arg);
                }
            }
            return new CliOptions(port, openBrowser, startRegistered);
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }

        private static String help() {
            return """
                    Usage: web-manager [--port PORT] [--no-open] [--start-registered]

                    Starts the local web manager on 127.0.0.1.
                    With --start-registered, also starts every registered MCP endpoint in this process.
                    On Linux, prompts once for the vault password when MAIL_MCP_VAULT_PASSWORD is not set.
                    Default port: 18100.
                    """;
        }
    }
}
