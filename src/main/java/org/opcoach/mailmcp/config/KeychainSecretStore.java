package org.opcoach.mailmcp.config;

import org.opcoach.mailmcp.security.SafeErrorMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class KeychainSecretStore implements SecretStore {

    static final String SERVICE = "opcoach-mcp-mail";
    private final String osName;

    public KeychainSecretStore() {
        this(System.getProperty("os.name", ""));
    }

    KeychainSecretStore(String osName) {
        this.osName = osName.toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<String> readPassword(String profile) {
        if (!isMacOs()) {
            return Optional.empty();
        }
        ProcessResult result = run("security", "find-generic-password", "-a", profile, "-s", SERVICE, "-w");
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(result.stdout().stripTrailing());
    }

    @Override
    public void writePassword(String profile, char[] password) {
        if (!isMacOs()) {
            throw new ConfigurationException("""
                    No durable keychain is automatically supported on this platform yet.
                    Use MAIL_MCP_PASSWORD temporarily or contribute a Linux/Windows backend.
                    """);
        }
        String secret = new String(password);
        ProcessResult result = run("security", "add-generic-password", "-a", profile, "-s", SERVICE, "-w", secret, "-U");
        if (result.exitCode() != 0) {
            throw new ConfigurationException(SafeErrorMessage.clean("Unable to save password in the keychain: " + result.stderr(), java.util.List.of(secret)));
        }
    }

    private boolean isMacOs() {
        return osName.contains("mac");
    }

    private static ProcessResult run(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(124, "", "Keychain command timed out.");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessResult(process.exitValue(), stdout, stderr);
        } catch (IOException exception) {
            return new ProcessResult(127, "", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProcessResult(130, "", "Keychain command interrupted.");
        }
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
