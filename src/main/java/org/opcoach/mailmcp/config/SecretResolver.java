package org.opcoach.mailmcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

public final class SecretResolver {

    public static final String PASSWORD_ENV = "MAIL_MCP_PASSWORD";

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretResolver.class);

    private final Map<String, String> env;
    private final SecretStore secretStore;

    public SecretResolver(Map<String, String> env, SecretStore secretStore) {
        this.env = Map.copyOf(env);
        this.secretStore = secretStore;
    }

    public static SecretResolver system() {
        return new SecretResolver(System.getenv(), new KeychainSecretStore());
    }

    public ResolvedSecret resolve(MailConfiguration configuration) {
        String profileSpecificEnv = profileSpecificEnv(configuration.profile());
        String profilePassword = env.get(profileSpecificEnv);
        if (profilePassword != null && !profilePassword.isBlank()) {
            LOGGER.warn("Mot de passe mail lu depuis {}. Préférez le trousseau local pour un usage durable.", profileSpecificEnv);
            return new ResolvedSecret(profilePassword, ResolvedSecret.SecretSource.ENVIRONMENT);
        }

        String password = env.get(PASSWORD_ENV);
        if (password != null && !password.isBlank()) {
            LOGGER.warn("Mot de passe mail lu depuis MAIL_MCP_PASSWORD. Préférez le trousseau local pour un usage durable.");
            return new ResolvedSecret(password, ResolvedSecret.SecretSource.ENVIRONMENT);
        }

        return secretStore.readPassword(configuration.profile())
                .map(value -> new ResolvedSecret(value, ResolvedSecret.SecretSource.KEYCHAIN))
                .orElseThrow(() -> new ConfigurationException("""
                        Mot de passe absent pour le profil %s.
                        Lancez java -jar target/opcoach-mail-mcp.jar config set-password --profile %s
                        ou exportez temporairement MAIL_MCP_PASSWORD.
                        """.formatted(configuration.profile(), configuration.profile())));
    }

    private static String profileSpecificEnv(String profile) {
        String normalized = profile.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return PASSWORD_ENV + "_" + normalized;
    }
}
