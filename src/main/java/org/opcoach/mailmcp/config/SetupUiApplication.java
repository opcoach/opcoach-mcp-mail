package org.opcoach.mailmcp.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.opcoach.mailmcp.security.SafeErrorMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SetupUiApplication {

    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private SetupUiApplication() {
    }

    public static void main(String[] args) throws Exception {
        String profile = args.length > 0 ? args[0] : "default";
        run(profile);
    }

    public static void run(String profile) throws IOException, InterruptedException {
        String token = newToken();
        CountDownLatch completed = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, profile, token, completed));
        server.start();
        int port = server.getAddress().getPort();
        System.out.printf("Mini UI locale démarrée sur http://127.0.0.1:%d/?token=%s%n", port, token);
        System.out.println("Elle s'arrêtera après validation ou expiration.");
        boolean done = completed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        server.stop(0);
        if (!done) {
            System.out.println("Mini UI expirée sans validation.");
        }
    }

    private static void handle(HttpExchange exchange, String profile, String token, CountDownLatch completed) throws IOException {
        try {
            if (!isAuthorized(exchange, token)) {
                send(exchange, 403, "text/plain; charset=utf-8", "Jeton invalide.");
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                send(exchange, 200, "text/html; charset=utf-8", form(profile, token));
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> values = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                ConfigurationDraft draft = new ConfigurationDraft(
                        profile,
                        required(values, "imapHost"),
                        parsePort(values, "imapPort"),
                        ConnectionSecurity.parse(required(values, "imapSecurity"), "imap.security"),
                        required(values, "smtpHost"),
                        parsePort(values, "smtpPort"),
                        ConnectionSecurity.parse(required(values, "smtpSecurity"), "smtp.security"),
                        required(values, "username"),
                        required(values, "fromAddress"),
                        values.getOrDefault("fromName", ""),
                        required(values, "sentMailbox")
                );
                new ConfigurationWriter(ConfigurationPaths.defaultConfigPath()).write(draft);
                char[] password = values.getOrDefault("password", "").toCharArray();
                try {
                    if (password.length > 0) {
                        new KeychainSecretStore().writePassword(profile, password);
                    }
                } finally {
                    Arrays.fill(password, '\0');
                }
                send(exchange, 200, "text/html; charset=utf-8", success());
                completed.countDown();
                return;
            }
            send(exchange, 405, "text/plain; charset=utf-8", "Méthode non supportée.");
        } catch (Exception exception) {
            send(exchange, 400, "text/plain; charset=utf-8", SafeErrorMessage.clean(exception.getMessage()));
        }
    }

    private static boolean isAuthorized(HttpExchange exchange, String token) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return false;
        }
        return parseForm(query).entrySet().stream().anyMatch(entry -> "token".equals(entry.getKey()) && token.equals(entry.getValue()));
    }

    private static String form(String profile, String token) {
        return """
                <!doctype html>
                <html lang="fr">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Configuration opcoach-mail-mcp</title>
                  <style>
                    body { font-family: system-ui, sans-serif; max-width: 760px; margin: 2rem auto; padding: 0 1rem; color: #1f2937; }
                    label { display: block; margin-top: 1rem; font-weight: 600; }
                    input, select { width: 100%%; box-sizing: border-box; padding: .55rem; margin-top: .25rem; }
                    button { margin-top: 1.5rem; padding: .7rem 1rem; font-weight: 700; }
                  </style>
                </head>
                <body>
                  <h1>Configuration mail MCP</h1>
                  <p>Profil: %s</p>
                  <form method="post" action="/?token=%s" autocomplete="off">
                    <label>Hôte IMAP<input name="imapHost" value="imap.example.com" required></label>
                    <label>Port IMAP<input name="imapPort" value="993" type="number" min="1" max="65535" required></label>
                    <label>Sécurité IMAP<select name="imapSecurity"><option>ssl_tls</option><option>starttls</option><option>none</option></select></label>
                    <label>Hôte SMTP<input name="smtpHost" value="smtp.example.com" required></label>
                    <label>Port SMTP<input name="smtpPort" value="465" type="number" min="1" max="65535" required></label>
                    <label>Sécurité SMTP<select name="smtpSecurity"><option>ssl_tls</option><option>starttls</option><option>none</option></select></label>
                    <label>Identifiant mail<input name="username" value="formation@example.com" required></label>
                    <label>Adresse d'expéditeur<input name="fromAddress" value="formation@example.com" required></label>
                    <label>Nom d'expéditeur<input name="fromName" value="Formation MCP"></label>
                    <label>Dossier des envoyés<input name="sentMailbox" value="INBOX.Sent" required></label>
                    <label>Mot de passe applicatif<input name="password" type="password" autocomplete="new-password"></label>
                    <button type="submit">Enregistrer</button>
                  </form>
                </body>
                </html>
                """.formatted(escape(profile), escape(token));
    }

    private static String success() {
        return """
                <!doctype html>
                <html lang="fr"><head><meta charset="utf-8"><title>Configuration terminée</title></head>
                <body><h1>Configuration terminée</h1><p>Vous pouvez fermer cet onglet.</p></body></html>
                """;
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
            throw new ConfigurationException("Champ obligatoire manquant: " + key);
        }
        return value.trim();
    }

    private static int parsePort(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(required(values, key));
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Port invalide: " + key);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
