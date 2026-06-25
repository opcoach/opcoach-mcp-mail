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
        if (isMacOs()) {
            ProcessResult result = run("security", "find-generic-password", "-a", profile, "-s", SERVICE, "-w");
            if (result.exitCode() != 0 || result.stdout().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(result.stdout().stripTrailing());
        }
        return Optional.empty();
    }

    @Override
    public void writePassword(String profile, char[] password) {
        if (isMacOs()) {
            String secret = new String(password);
            ProcessResult result = run("security", "add-generic-password", "-a", profile, "-s", SERVICE, "-w", secret, "-U");
            if (result.exitCode() != 0) {
                throw new ConfigurationException(SafeErrorMessage.clean("Unable to save password in the keychain: " + result.stderr(), java.util.List.of(secret)));
            }
            return;
        }
        if (isWindows()) {
            throw new ConfigurationException("Windows passwords are not stored. Enter the password in the manager when starting the server.");
        }
        if (!isMacOs()) {
            throw new ConfigurationException("""
                    No durable keychain is automatically supported on this platform yet.
                    Use MAIL_MCP_PASSWORD temporarily or contribute a Linux/Windows backend.
                    """);
        }
    }

    public boolean deletePassword(String profile) {
        if (!isMacOs()) {
            return false;
        }
        ProcessResult result = run("security", "delete-generic-password", "-a", profile, "-s", SERVICE);
        if (result.exitCode() == 0) {
            return true;
        }
        String stderr = result.stderr().toLowerCase(Locale.ROOT);
        if (stderr.contains("could not be found") || stderr.contains("not found")) {
            return false;
        }
        throw new ConfigurationException("Unable to delete password from the keychain: " + result.stderr());
    }

    private boolean isMacOs() {
        return osName.contains("mac");
    }

    private boolean isWindows() {
        return osName.contains("win");
    }

    public boolean supportsDurableStorage() {
        return isMacOs();
    }

    private static ProcessResult run(String... command) {
        return runWithInput(null, command);
    }

    private static ProcessResult runWithInput(String input, String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            if (input != null) {
                try (var output = process.getOutputStream()) {
                    output.write(input.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }
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
