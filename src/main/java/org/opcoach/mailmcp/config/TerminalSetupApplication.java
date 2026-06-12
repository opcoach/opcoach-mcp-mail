package org.opcoach.mailmcp.config;

import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;

public final class TerminalSetupApplication {

    private TerminalSetupApplication() {
    }

    public static void main(String[] args) {
        String profile = args.length > 0 ? args[0] : "default";
        int exitCode = runInteractive(profile);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int runInteractive(String profile) {
        TerminalPrompter prompter = new TerminalPrompter();
        Path configPath = ConfigurationPaths.defaultConfigPath();
        System.out.printf("Assistant de configuration opcoach-mail-mcp pour le profil %s.%n", profile);
        System.out.printf("Fichier écrit: %s%n", configPath.toAbsolutePath());

        ConfigurationDraft draft = new ConfigurationDraft(
                profile,
                prompter.ask("Hôte IMAP", "imap.example.com"),
                prompter.askInt("Port IMAP", 993),
                prompter.askSecurity("Sécurité IMAP", ConnectionSecurity.SSL_TLS),
                prompter.ask("Hôte SMTP", "smtp.example.com"),
                prompter.askInt("Port SMTP", 465),
                prompter.askSecurity("Sécurité SMTP", ConnectionSecurity.SSL_TLS),
                prompter.ask("Identifiant mail", "formation@example.com"),
                prompter.ask("Adresse d'expéditeur", "formation@example.com"),
                prompter.ask("Nom d'expéditeur", "Formation MCP"),
                prompter.ask("Dossier des envoyés", "INBOX.Sent")
        );

        new ConfigurationWriter(configPath).write(draft);
        char[] password = prompter.askPassword("Mot de passe ou mot de passe applicatif");
        try {
            if (password.length > 0) {
                new KeychainSecretStore().writePassword(profile, password);
                System.out.printf("Mot de passe enregistré dans le trousseau local pour le profil %s.%n", profile);
            } else {
                System.out.println("Aucun mot de passe enregistré. Vous pourrez utiliser MAIL_MCP_PASSWORD temporairement.");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
        System.out.println("Configuration terminée.");
        return 0;
    }

    public static int runSetPassword(String profile) {
        TerminalPrompter prompter = new TerminalPrompter();
        char[] password = prompter.askPassword("Mot de passe ou mot de passe applicatif");
        if (password.length == 0) {
            System.err.println("Mot de passe vide: aucun changement effectué.");
            return 2;
        }
        try {
            new KeychainSecretStore().writePassword(profile, password);
            System.out.printf("Mot de passe enregistré dans le trousseau local pour le profil %s.%n", profile);
            return 0;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    static final class TerminalPrompter {

        private final Console console;
        private final Scanner scanner;

        TerminalPrompter() {
            this.console = System.console();
            this.scanner = console == null ? new Scanner(System.in) : null;
        }

        String ask(String label, String defaultValue) {
            String prompt = "%s [%s]: ".formatted(label, defaultValue);
            String value = readLine(prompt);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return value.trim();
        }

        int askInt(String label, int defaultValue) {
            while (true) {
                String value = ask(label, Integer.toString(defaultValue));
                try {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1 || parsed > 65535) {
                        System.err.println("Le port doit être compris entre 1 et 65535.");
                    } else {
                        return parsed;
                    }
                } catch (NumberFormatException exception) {
                    System.err.println("Veuillez saisir un entier.");
                }
            }
        }

        ConnectionSecurity askSecurity(String label, ConnectionSecurity defaultValue) {
            while (true) {
                String value = ask(label + " (ssl_tls, starttls, none)", defaultValue.propertyValue());
                try {
                    return ConnectionSecurity.parse(value, label);
                } catch (ConfigurationException exception) {
                    System.err.println(exception.getMessage());
                }
            }
        }

        char[] askPassword(String label) {
            if (console != null) {
                char[] value = console.readPassword("%s: ", label);
                return value == null ? new char[0] : value;
            }
            System.out.printf("%s: ", label);
            if (!scanner.hasNextLine()) {
                return new char[0];
            }
            return scanner.nextLine().toCharArray();
        }

        private String readLine(String prompt) {
            if (console != null) {
                return console.readLine(prompt);
            }
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return "";
            }
            return scanner.nextLine();
        }
    }
}
