package org.opcoach.mailmcp.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class WindowsDpapiSecretStore implements SecretStore {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(15);
    private static final String PROTECT_SCRIPT = """
            $plain = [Convert]::FromBase64String([Console]::In.ReadToEnd())
            $cipher = [System.Security.Cryptography.ProtectedData]::Protect(
              $plain, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
            [Console]::Out.Write([Convert]::ToBase64String($cipher))
            """;
    private static final String UNPROTECT_SCRIPT = """
            $cipher = [Convert]::FromBase64String([Console]::In.ReadToEnd())
            $plain = [System.Security.Cryptography.ProtectedData]::Unprotect(
              $cipher, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
            [Console]::Out.Write([Convert]::ToBase64String($plain))
            """;

    private final Path secretsDirectory;
    private final DpapiCommand command;

    public WindowsDpapiSecretStore() {
        this(ConfigurationPaths.defaultHomeDir().resolve("windows-secrets"), WindowsDpapiSecretStore::runPowerShell);
    }

    WindowsDpapiSecretStore(Path secretsDirectory, DpapiCommand command) {
        this.secretsDirectory = secretsDirectory;
        this.command = command;
    }

    @Override
    public Optional<String> readPassword(String profile) {
        Path secretFile = secretFile(profile);
        if (!Files.exists(secretFile)) {
            return Optional.empty();
        }
        try {
            String cipherText = Files.readString(secretFile, StandardCharsets.US_ASCII).trim();
            if (cipherText.isBlank()) {
                return Optional.empty();
            }
            String encodedPlainText = command.apply(Operation.UNPROTECT, cipherText);
            byte[] plainText = Base64.getDecoder().decode(encodedPlainText);
            return Optional.of(new String(plainText, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException exception) {
            throw new ConfigurationException("Unable to read the Windows-protected password for profile " + profile + ".", exception);
        }
    }

    @Override
    public void writePassword(String profile, char[] password) {
        if (password == null || password.length == 0) {
            throw new ConfigurationException("Empty mail password: no secret was written.");
        }
        String encodedPassword = Base64.getEncoder().encodeToString(new String(password).getBytes(StandardCharsets.UTF_8));
        String cipherText = command.apply(Operation.PROTECT, encodedPassword);
        Path secretFile = secretFile(profile);
        try {
            Files.createDirectories(secretsDirectory);
            Path temporary = Files.createTempFile(secretsDirectory, "password-", ".tmp");
            Files.writeString(temporary, cipherText, StandardCharsets.US_ASCII);
            try {
                Files.move(temporary, secretFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, secretFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to save the Windows-protected password for profile " + profile + ".", exception);
        }
    }

    @Override
    public boolean deletePassword(String profile) {
        try {
            return Files.deleteIfExists(secretFile(profile));
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to delete the Windows-protected password for profile " + profile + ".", exception);
        }
    }

    @Override
    public boolean supportsDurableStorage() {
        return true;
    }

    private Path secretFile(String profile) {
        String normalized = profile == null || profile.isBlank() ? "default" : profile.trim();
        String fileName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return secretsDirectory.resolve(fileName + ".dpapi");
    }

    private static String runPowerShell(Operation operation, String input) {
        String script = operation == Operation.PROTECT ? PROTECT_SCRIPT : UNPROTECT_SCRIPT;
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    script
            ).start();
            try (var output = process.getOutputStream()) {
                output.write(input.getBytes(StandardCharsets.US_ASCII));
            }
            boolean finished = process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ConfigurationException("Windows password protection timed out.");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || stdout.isBlank()) {
                throw new ConfigurationException("Windows password protection failed: " + safePowerShellError(stderr));
            }
            return stdout;
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to start Windows password protection.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConfigurationException("Windows password protection was interrupted.", exception);
        }
    }

    private static String safePowerShellError(String message) {
        return message == null || message.isBlank() ? "no details returned by Windows" : message;
    }

    enum Operation {
        PROTECT,
        UNPROTECT
    }

    @FunctionalInterface
    interface DpapiCommand {
        String apply(Operation operation, String input);
    }
}
