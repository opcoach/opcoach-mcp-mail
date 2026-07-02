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
        return new SecretResolver(System.getenv(), LocalSecretStore.system());
    }

    public ResolvedSecret resolve(MailConfiguration configuration) {
        String profileSpecificEnv = passwordEnvName(configuration.profile());
        String profilePassword = env.get(profileSpecificEnv);
        if (profilePassword != null && !profilePassword.isBlank()) {
            LOGGER.warn("Mail password read from {}. Prefer local secret storage where durable storage is supported.", profileSpecificEnv);
            return new ResolvedSecret(profilePassword, ResolvedSecret.SecretSource.ENVIRONMENT);
        }

        String password = env.get(PASSWORD_ENV);
        if (password != null && !password.isBlank()) {
            LOGGER.warn("Mail password read from MAIL_MCP_PASSWORD. Prefer local secret storage where durable storage is supported.");
            return new ResolvedSecret(password, ResolvedSecret.SecretSource.ENVIRONMENT);
        }

        return secretStore.readPassword(configuration.profile())
                .map(value -> new ResolvedSecret(value, ResolvedSecret.SecretSource.LOCAL_STORE))
                .orElseThrow(() -> new ConfigurationException("""
                        Missing password for profile %s.
                        Enter it in the manager before starting the profile,
                        run java -jar target/opcoach-mcp-mail.jar config set-password --profile %s on systems with local secret storage,
                        or temporarily set MAIL_MCP_PASSWORD.
                        """.formatted(configuration.profile(), configuration.profile())));
    }

    public static String passwordEnvName(String profile) {
        String normalized = profile.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return PASSWORD_ENV + "_" + normalized;
    }
}
