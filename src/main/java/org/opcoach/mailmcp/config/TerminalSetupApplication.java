package org.opcoach.mailmcp.config;

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
        System.out.printf("Assistant de configuration pour le profil %s.%n", profile);
        System.out.println("L'implémentation complète arrive dans l'itération configuration.");
        return 0;
    }
}
