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
        System.out.printf("opcoach-mcp-mail setup assistant for profile %s.%n", profile);
        System.out.printf("Written file: %s%n", configPath.toAbsolutePath());

        ConfigurationDraft draft = new ConfigurationDraft(
                profile,
                prompter.ask("IMAP host", "imap.example.com"),
                prompter.askInt("Port IMAP", 993),
                prompter.askSecurity("IMAP security", ConnectionSecurity.SSL_TLS),
                prompter.ask("SMTP host", "smtp.example.com"),
                prompter.askInt("Port SMTP", 465),
                prompter.askSecurity("SMTP security", ConnectionSecurity.SSL_TLS),
                prompter.ask("Email username", "training@example.com"),
                prompter.ask("Sender address", "training@example.com"),
                prompter.ask("Sender name", "MCP Training"),
                prompter.ask("Reply-To address (optional)", ""),
                prompter.ask("Sent folder", "INBOX.Sent"),
                prompter.ask("Trash folder", "INBOX.Trash")
        );

        new ConfigurationWriter(configPath).write(draft);
        char[] password = prompter.askPassword("Password or app password");
        try {
            if (password.length > 0) {
                SecretStore secretStore = secretStoreForWrite(prompter);
                if (secretStore.supportsDurableStorage()) {
                    secretStore.writePassword(profile, password);
                    System.out.printf("Password saved in the local secret store for profile %s.%n", profile);
                } else {
                    System.out.println("Password not stored on this platform. Use MAIL_MCP_PASSWORD when starting the server.");
                }
            } else {
                System.out.println("No password saved. You can use MAIL_MCP_PASSWORD temporarily.");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
        System.out.println("Configuration complete.");
        return 0;
    }

    public static int runSetPassword(String profile) {
        TerminalPrompter prompter = new TerminalPrompter();
        char[] password = prompter.askPassword("Password or app password");
        if (password.length == 0) {
            System.err.println("Empty password: no change made.");
            return 2;
        }
        try {
            SecretStore secretStore = secretStoreForWrite(prompter);
            if (!secretStore.supportsDurableStorage()) {
                System.err.println("Durable password storage is not supported on this platform.");
                return 2;
            }
            secretStore.writePassword(profile, password);
            System.out.printf("Password saved in the local secret store for profile %s.%n", profile);
            return 0;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static SecretStore secretStoreForWrite(TerminalPrompter prompter) {
        if (!LocalSecretStore.systemUsesEncryptedVault() || hasVaultPasswordEnvironment()) {
            return LocalSecretStore.system();
        }
        System.out.println("Linux encrypted vault: choose the vault password that protects local mail secrets.");
        char[] vaultPassword = prompter.askPassword("Vault password");
        try {
            if (vaultPassword.length == 0) {
                throw new ConfigurationException("Vault password is required to store the mail password on Linux.");
            }
            return LocalSecretStore.system(vaultPassword);
        } finally {
            Arrays.fill(vaultPassword, '\0');
        }
    }

    private static boolean hasVaultPasswordEnvironment() {
        String value = System.getenv(EncryptedVaultSecretStore.VAULT_PASSWORD_ENV);
        return value != null && !value.isBlank();
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
                        System.err.println("Port must be between 1 and 65535.");
                    } else {
                        return parsed;
                    }
                } catch (NumberFormatException exception) {
                    System.err.println("Please enter an integer.");
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
