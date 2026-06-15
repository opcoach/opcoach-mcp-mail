package org.opcoach.mailmcp;

import org.opcoach.mailmcp.config.ConfigurationException;
import org.opcoach.mailmcp.config.TerminalSetupApplication;
import org.opcoach.mailmcp.mcp.McpRuntime;
import org.opcoach.mailmcp.security.SafeErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public final class MailMcpApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailMcpApplication.class);

    private MailMcpApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new MailMcpApplicationRunner().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static final class MailMcpApplicationRunner {

        int run(String[] args) {
            try {
                CliOptions options = CliOptions.parse(args);
                if (options.showHelp()) {
                    System.out.println(CliOptions.help());
                    return 0;
                }
                if (options.command() == Command.CONFIG_SETUP) {
                    return TerminalSetupApplication.runInteractive(options.profile());
                }
                if (options.command() == Command.CONFIG_SET_PASSWORD) {
                    return TerminalSetupApplication.runSetPassword(options.profile());
                }
                McpRuntime runtime = McpRuntime.create(options);
                runtime.start();
                return 0;
            } catch (ConfigurationException exception) {
                System.err.println(SafeErrorMessage.clean(exception.getMessage()));
                return 2;
            } catch (IllegalArgumentException exception) {
                System.err.println(SafeErrorMessage.clean(exception.getMessage()));
                System.err.println();
                System.err.println(CliOptions.help());
                return 2;
            } catch (Exception exception) {
                LOGGER.error("Erreur de démarrage du serveur MCP mail: {}", SafeErrorMessage.clean(exception.getMessage()));
                return 1;
            }
        }
    }

    public enum Command {
        SERVER,
        CONFIG_SETUP,
        CONFIG_SET_PASSWORD
    }

    public enum TransportMode {
        STDIO,
        HTTP
    }

    public record CliOptions(
            Command command,
            TransportMode transportMode,
            String profile,
            String host,
            int port,
            String httpToken,
            boolean showHelp
    ) {

        private static final int DEFAULT_HTTP_PORT = 8095;
        private static final String DEFAULT_HTTP_HOST = "127.0.0.1";
        private static final String DEFAULT_PROFILE = "default";

        static CliOptions parse(String[] args) {
            Command command = Command.SERVER;
            TransportMode mode = TransportMode.STDIO;
            String profile = DEFAULT_PROFILE;
            String host = DEFAULT_HTTP_HOST;
            int port = DEFAULT_HTTP_PORT;
            String token = null;
            boolean help = false;

            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                switch (arg) {
                    case "--help", "-h" -> help = true;
                    case "--stdio" -> mode = TransportMode.STDIO;
                    case "--http" -> mode = TransportMode.HTTP;
                    case "--profile" -> profile = requireValue(args, ++index, "--profile");
                    case "--host" -> host = requireValue(args, ++index, "--host");
                    case "--port" -> port = parsePort(requireValue(args, ++index, "--port"));
                    case "--token" -> token = requireValue(args, ++index, "--token");
                    case "config" -> {
                        String subCommand = requireValue(args, ++index, "config");
                        command = switch (subCommand) {
                            case "setup" -> Command.CONFIG_SETUP;
                            case "set-password" -> Command.CONFIG_SET_PASSWORD;
                            default -> throw new IllegalArgumentException("Sous-commande config inconnue: " + subCommand);
                        };
                    }
                    default -> throw new IllegalArgumentException("Argument inconnu: " + arg + " parmi " + Arrays.toString(args));
                }
            }

            return new CliOptions(command, mode, profile, host, port, token, help);
        }

        static String help() {
            return """
                    opcoach-mcp-mail

                    Usage:
                      java -jar target/opcoach-mcp-mail.jar --stdio [--profile default]
                      java -jar target/opcoach-mcp-mail.jar --http [--host 127.0.0.1] [--port 8095] [--token jeton]
                      java -jar target/opcoach-mcp-mail.jar config setup [--profile default]
                      java -jar target/opcoach-mcp-mail.jar config set-password [--profile default]

                    Le mode --stdio est recommandé pour Codex et Claude Desktop.
                    Le mode --http écoute sur 127.0.0.1 par défaut. Un jeton est obligatoire hors localhost.
                    """;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Valeur manquante pour " + option);
            }
            return args[index];
        }

        private static int parsePort(String value) {
            try {
                int port = Integer.parseInt(value);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Le port HTTP doit être compris entre 1 et 65535.");
                }
                return port;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Port HTTP invalide: " + value);
            }
        }
    }
}
