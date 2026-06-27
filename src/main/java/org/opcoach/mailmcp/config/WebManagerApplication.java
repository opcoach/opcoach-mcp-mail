package org.opcoach.mailmcp.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.opcoach.mailmcp.config.ProfileTransfer.ProfileSnapshot;
import org.opcoach.mailmcp.security.SafeErrorMessage;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public final class WebManagerApplication {

    private static final int DEFAULT_PORT = 18100;
    private static final String LOCAL_HOST = "127.0.0.1";

    private final ServerRegistry registry = ServerRegistry.defaultRegistry();
    private final ServerProcessManager processManager = ServerProcessManager.currentApplication();
    private final ProfileTransfer transfer = new ProfileTransfer();
    private final String token = newToken();

    private WebManagerApplication() {
    }

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        new WebManagerApplication().start(options.port(), options.openBrowser());
    }

    public static void run(int port) throws IOException, InterruptedException {
        new WebManagerApplication().start(port, false);
    }

    private void start(int port, boolean openBrowser) throws IOException, InterruptedException {
        HttpServer server = HttpServer.create(new InetSocketAddress(LOCAL_HOST, port), 0);
        server.createContext("/", this::handle);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0), "opcoach-mcp-mail-web-manager-stop"));
        int actualPort = server.getAddress().getPort();
        String url = "http://" + LOCAL_HOST + ":" + actualPort + "/?token=" + token;
        System.out.println("MCP Mail Local Manager started on " + url);
        System.out.println("It is bound to 127.0.0.1 only. Stop this process to close the UI.");
        if (openBrowser) {
            openBrowser(url);
        }
        new CountDownLatch(1).await();
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
            String target = path.startsWith("/import") ? "/import" : path.startsWith("/export") ? "/export" : "/";
            redirect(exchange, target, Map.of("error", SafeErrorMessage.clean(exception.getMessage())));
        }
    }

    private String mainPage(Map<String, String> query) {
        List<ServerRegistration> registrations = registry.list();
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
        body.append(serverTable(registrations, selected.profile()));
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
        body.append(pageEnd());
        return body.toString();
    }

    private String serverTable(List<ServerRegistration> registrations, String selectedProfile) {
        if (registrations.isEmpty()) {
            return "<div class=\"empty\">No profile registered yet.</div>";
        }
        StringBuilder html = new StringBuilder();
        html.append("<table><thead><tr><th>Profile</th><th>URL</th><th>Status</th></tr></thead><tbody>");
        for (ServerRegistration registration : registrations) {
            boolean selected = registration.profile().equals(selectedProfile);
            boolean running = processManager.isRunning(registration);
            html.append("<tr class=\"").append(selected ? "selected" : "").append("\">");
            html.append("<td><a href=\"").append(link("/", Map.of("profile", registration.profile()))).append("\">")
                    .append(escape(registration.profile())).append("</a></td>");
            html.append("<td><button class=\"link-button\" type=\"button\" onclick=\"copyText('")
                    .append(js(registration.url())).append("')\">").append(escape(registration.url())).append("</button></td>");
            html.append("<td><span class=\"badge ").append(running ? "running" : "stopped").append("\">")
                    .append(running ? "running" : "stopped").append("</span></td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String profileForm(ProfileForm profile) {
        StringBuilder html = new StringBuilder();
        html.append("<form method=\"post\" action=\"").append(action("/profile")).append("\" autocomplete=\"off\">");
        html.append("<input type=\"hidden\" name=\"originalProfile\" value=\"").append(escape(profile.originalProfile())).append("\">");
        html.append("<div class=\"form-grid\">");
        html.append(sectionTitle("Server"));
        html.append(input("Profile", "profile", profile.profile(), "Short name used by Codex, OptimumAI, and logs.", true));
        html.append(input("Local MCP port", "mcpPort", Integer.toString(profile.mcpPort()), "Usually 8095, 8096, 8097...", true));
        html.append(sectionTitle("Incoming mail"));
        html.append(input("IMAP host", "imapHost", profile.imapHost(), "Example: imap.example.com", true));
        html.append(input("IMAP port", "imapPort", Integer.toString(profile.imapPort()), "993 for SSL/TLS.", true));
        html.append(select("IMAP security", "imapSecurity", profile.imapSecurity()));
        html.append(sectionTitle("Outgoing mail"));
        html.append(input("SMTP host", "smtpHost", profile.smtpHost(), "Example: smtp.example.com", true));
        html.append(input("SMTP port", "smtpPort", Integer.toString(profile.smtpPort()), "465 for SSL/TLS, 587 for STARTTLS.", true));
        html.append(select("SMTP security", "smtpSecurity", profile.smtpSecurity()));
        html.append(sectionTitle("Identity"));
        html.append(input("Email username", "username", profile.username(), "", true));
        html.append(input("Sender address", "fromAddress", profile.fromAddress(), "", true));
        html.append(input("Sender name", "fromName", profile.fromName(), "", false));
        html.append(input("Reply-To address", "replyToAddress", profile.replyToAddress(), "Optional.", false));
        html.append(input("Sent folder", "sentMailbox", profile.sentMailbox(), "", true));
        html.append(input("Trash folder", "trashMailbox", profile.trashMailbox(), "", true));
        html.append(passwordInput("Mailbox password", "password", "Leave empty to keep the stored password."));
        if (LocalSecretStore.systemUsesEncryptedVault()) {
            html.append(passwordInput("Vault password", "vaultPassword", "Linux only: unlocks the local encrypted password vault."));
        }
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
            processManager.start(result.registration(), result.transientPassword(), result.transientVaultPassword());
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
                required(values, "sentMailbox"),
                required(values, "trashMailbox")
        );
        new ConfigurationWriter(registration.configFile()).write(draft);
        registry.write(registration);

        char[] password = values.getOrDefault("password", "").toCharArray();
        char[] vaultPassword = values.getOrDefault("vaultPassword", "").toCharArray();
        try {
            if (password.length > 0) {
                SecretStore store = vaultPassword.length > 0
                        ? LocalSecretStore.system(vaultPassword)
                        : LocalSecretStore.system();
                if (store.supportsDurableStorage()) {
                    store.writePassword(profile, password);
                }
            }
            String transientPassword = password.length > 0 ? new String(password) : "";
            String transientVaultPassword = password.length == 0 && vaultPassword.length > 0 ? new String(vaultPassword) : "";
            return new SaveResult(registration, transientPassword, transientVaultPassword);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(vaultPassword, '\0');
        }
    }

    private void handleStartPost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        ServerRegistration registration = requiredRegistration(values);
        processManager.start(registration);
        redirect(exchange, "/", Map.of("profile", registration.profile(), "status", "Started " + registration.profile() + "."));
    }

    private void handleStopPost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        ServerRegistration registration = requiredRegistration(values);
        processManager.stop(registration);
        redirect(exchange, "/", Map.of("profile", registration.profile(), "status", "Stopped " + registration.profile() + "."));
    }

    private void handleDeletePost(HttpExchange exchange) throws IOException {
        Map<String, String> values = postForm(exchange);
        ServerRegistration registration = requiredRegistration(values);
        processManager.stop(registration);
        String secretWarning = "";
        try {
            boolean removed = LocalSecretStore.system().deletePassword(registration.profile());
            if (!removed) {
                secretWarning = " Stored password was not present or could not be removed automatically.";
            }
        } catch (ConfigurationException exception) {
            secretWarning = " Stored password was not removed: " + SafeErrorMessage.clean(exception.getMessage());
        }
        registry.delete(registration);
        redirect(exchange, "/", Map.of("mode", "new", "status", "Deleted " + registration.profile() + "." + secretWarning));
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
            return ProfileForm.from(registration.get(), configuration, processManager.isRunning(registration.get()));
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
                && processManager.isRunning(existingRegistration.get());
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
                    body { margin:0; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color:var(--text); background:var(--surface); }
                    .hero { min-height: 190px; padding: 34px 44px; color:white; background: radial-gradient(circle at 78%% 90%%, rgba(250,189,67,.28), transparent 18%%), radial-gradient(circle at 98%% 0%%, rgba(255,255,255,.20), transparent 24%%), linear-gradient(110deg, var(--indigo), var(--soft)); display:flex; align-items:center; justify-content:space-between; gap:24px; }
                    .hero h1 { margin:0; font-size:42px; line-height:1; letter-spacing:0; }
                    .hero p { margin:16px 0 0; color:#F2F1FA; font-size:18px; }
                    .hero .eyebrow { font-weight:800; color:#E6E4F3; margin-bottom:14px; }
                    .layout { display:grid; grid-template-columns: minmax(420px, 3fr) minmax(360px, 2fr); gap:24px; padding:28px; }
                    .single { max-width: 1040px; margin:28px auto; }
                    .panel { background:white; border:1px solid var(--border); border-radius:20px; box-shadow: 8px 14px 0 rgba(75,63,114,.08); padding:24px; }
                    .panel-head { display:flex; justify-content:space-between; gap:16px; align-items:flex-start; margin-bottom:20px; }
                    .panel h2 { margin:0; font-size:28px; }
                    .panel p { margin:4px 0 0; color:var(--muted); }
                    table { width:100%%; border-collapse:collapse; }
                    th { text-align:left; color:#535057; border-bottom:1px solid var(--border); padding:10px 12px; font-size:13px; }
                    td { border-bottom:1px solid #F0EEF8; padding:14px 12px; vertical-align:middle; }
                    tr.selected { background:#F2F0FA; }
                    a { color:var(--indigo); font-weight:800; text-decoration:none; }
                    .link-button { border:0; background:transparent; color:var(--soft); cursor:pointer; font:inherit; padding:0; }
                    .badge { border-radius:999px; padding:6px 13px; font-weight:900; font-size:12px; display:inline-flex; }
                    .running { color:var(--green); background:#EAF7E4; }
                    .stopped { color:var(--rose); background:#F9DDE9; }
                    .row-actions, .form-actions { display:flex; gap:10px; flex-wrap:wrap; justify-content:flex-end; align-items:center; margin-top:22px; }
                    .inline-action { display:inline; }
                    .button { border:0; border-radius:14px; padding:12px 18px; font-weight:900; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; min-height:44px; }
                    .button:disabled { cursor:not-allowed; background:#ECECF1 !important; color:#A2A2AA !important; }
                    .primary { color:white; background:linear-gradient(110deg, var(--soft), #8F88C7); }
                    .strong { color:white; background:linear-gradient(110deg, var(--indigo), var(--rose)); }
                    .good { color:white; background:linear-gradient(110deg, var(--green), #80C048); }
                    .danger { color:white; background:linear-gradient(110deg, var(--rose), #E975A7); }
                    .warning { color:white; background:linear-gradient(110deg, #B36B00, #FABD43); }
                    .ghost { color:var(--indigo); background:white; border:1px solid #ECEAF7; }
                    .form-grid { display:grid; grid-template-columns: 1fr; gap:12px; }
                    .form-grid h3 { margin:18px 0 0; color:var(--indigo); text-transform:uppercase; font-size:13px; letter-spacing:0; }
                    label { display:grid; grid-template-columns: 180px minmax(0, 1fr); gap:12px 18px; align-items:center; font-weight:800; color:#4B4B4D; }
                    input, select, textarea { width:100%%; border:1px solid #CFCDE1; border-radius:12px; padding:11px 13px; font:inherit; color:var(--text); background:white; }
                    textarea { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; min-height:280px; }
                    small { color:#939394; font-weight:600; }
                    label small { grid-column:2; margin-top:-8px; }
                    .notice { border-radius:12px; padding:12px 14px; margin:12px 0; font-weight:800; }
                    .notice.ok { background:#EAF7E4; color:var(--green); }
                    .notice.error { background:#F9DDE9; color:var(--rose); }
                    .notice.warn { background:#FFF1CF; color:#B36B00; }
                    .empty { padding:28px; color:var(--muted); background:#FBFAFF; border:1px dashed var(--border); border-radius:16px; }
                    .check { grid-template-columns:auto 1fr; align-items:start; border:1px solid var(--border); padding:12px 14px; border-radius:14px; margin-bottom:10px; }
                    .check input { width:auto; margin-top:4px; }
                    .check small { grid-column:2; margin:0; display:block; }
                    .import-card { border:1px solid var(--border); border-radius:16px; padding:16px; margin:14px 0; }
                    .file-loader { display:block; margin-bottom:12px; }
                    @media (max-width: 980px) { .layout { grid-template-columns:1fr; padding:16px; } .hero { padding:28px 20px; display:block; } label { grid-template-columns:1fr; } label small { grid-column:1; } }
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
                </script>
                """.formatted(escape(title));
    }

    private static String hero() {
        return """
                <header class="hero">
                  <div>
                    <div class="eyebrow">OPCoach local-first MCP</div>
                    <h1>MCP Mail Local Manager</h1>
                    <p>Configure mailboxes, start local MCP servers, export/import safe profiles.</p>
                  </div>
                </header>
                """;
    }

    private static String pageEnd() {
        return "</body></html>";
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private record SaveResult(ServerRegistration registration, String transientPassword, String transientVaultPassword) {
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

    private record CliOptions(int port, boolean openBrowser) {

        static CliOptions parse(String[] args) {
            int port = DEFAULT_PORT;
            boolean openBrowser = true;
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--port" -> port = parsePort(requireValue(args, ++index, "--port"), "web manager port");
                    case "--no-open" -> openBrowser = false;
                    case "-h", "--help" -> {
                        System.out.println(help());
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown web manager argument: " + arg);
                }
            }
            return new CliOptions(port, openBrowser);
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }

        private static String help() {
            return """
                    Usage: web-manager [--port PORT] [--no-open]

                    Starts the local web manager on 127.0.0.1.
                    Default port: 18100.
                    """;
        }
    }
}
