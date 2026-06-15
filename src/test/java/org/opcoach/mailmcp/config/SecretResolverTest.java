package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretResolverTest {

    @Test
    void resolvesEnvironmentPassword() {
        MailConfiguration configuration = configuration("default");
        SecretResolver resolver = new SecretResolver(
                Map.of(SecretResolver.PASSWORD_ENV, "fake-secret"),
                profile -> Optional.empty()
        );

        ResolvedSecret secret = resolver.resolve(configuration);

        assertEquals("fake-secret", secret.value());
        assertEquals(ResolvedSecret.SecretSource.ENVIRONMENT, secret.source());
    }

    @Test
    void resolvesProfileSpecificEnvironmentPasswordFirst() {
        MailConfiguration configuration = configuration("training");
        SecretResolver resolver = new SecretResolver(
                Map.of(
                        SecretResolver.PASSWORD_ENV, "generic",
                        SecretResolver.PASSWORD_ENV + "_TRAINING", "profile-secret"
                ),
                profile -> Optional.empty()
        );

        ResolvedSecret secret = resolver.resolve(configuration);

        assertEquals("profile-secret", secret.value());
    }

    @Test
    void failsWhenNoSecretIsAvailable() {
        SecretResolver resolver = new SecretResolver(Map.of(), profile -> Optional.empty());

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> resolver.resolve(configuration("default")));

        assertEquals(true, exception.getMessage().contains("MAIL_MCP_PASSWORD"));
    }

    private static MailConfiguration configuration(String profile) {
        return new MailConfiguration(
                profile,
                new MailEndpoint("imap.example.com", 993, ConnectionSecurity.SSL_TLS),
                new MailEndpoint("smtp.example.com", 465, ConnectionSecurity.SSL_TLS),
                "training@example.com",
                "training@example.com",
                "MCP Training",
                "INBOX.Sent",
                MailLimits.DEFAULTS,
                Path.of("config.properties"),
                Path.of("audit.log")
        );
    }
}
