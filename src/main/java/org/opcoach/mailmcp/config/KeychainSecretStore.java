package org.opcoach.mailmcp.config;

import org.opcoach.mailmcp.security.SafeErrorMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if (isWindows()) {
            Path secretFile = windowsSecretFile(profile);
            if (!Files.exists(secretFile)) {
                return Optional.empty();
            }
            try {
                String encrypted = Files.readString(secretFile, StandardCharsets.UTF_8);
                ProcessResult result = runWithInput(encrypted, "powershell.exe", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-Command", """
                                $blob = [Console]::In.ReadToEnd();
                                $secure = ConvertTo-SecureString -String $blob;
                                $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure);
                                try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
                                finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
                                """);
                if (result.exitCode() != 0 || result.stdout().isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(result.stdout().stripTrailing());
            } catch (IOException exception) {
                return Optional.empty();
            }
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
            String secret = new String(password);
            ProcessResult result = runWithInput(secret, "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-Command", """
                            $plain = [Console]::In.ReadToEnd();
                            $secure = ConvertTo-SecureString -String $plain -AsPlainText -Force;
                            $secure | ConvertFrom-SecureString
                            """);
            if (result.exitCode() != 0 || result.stdout().isBlank()) {
                throw new ConfigurationException(SafeErrorMessage.clean("Unable to save password with Windows DPAPI: " + result.stderr(), java.util.List.of(secret)));
            }
            try {
                Path secretFile = windowsSecretFile(profile);
                Files.createDirectories(secretFile.getParent());
                Files.writeString(secretFile, result.stdout().stripTrailing(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new ConfigurationException("Unable to write Windows DPAPI password file.", exception);
            }
            return;
        }
        if (!isMacOs()) {
            throw new ConfigurationException("""
                    No durable keychain is automatically supported on this platform yet.
                    Use MAIL_MCP_PASSWORD temporarily or contribute a Linux/Windows backend.
                    """);
        }
    }

    private boolean isMacOs() {
        return osName.contains("mac");
    }

    private boolean isWindows() {
        return osName.contains("win");
    }

    private static Path windowsSecretFile(String profile) {
        return ConfigurationPaths.defaultHomeDir()
                .resolve("secrets")
                .resolve(ServerRegistry.registryName(profile) + ".dpapi");
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
