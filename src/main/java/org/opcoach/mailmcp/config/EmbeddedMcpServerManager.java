package org.opcoach.mailmcp.config;

import org.opcoach.mailmcp.audit.AuditLogger;
import org.opcoach.mailmcp.mail.MailApplicationService;
import org.opcoach.mailmcp.mcp.McpHttpServerGroup;
import org.opcoach.mailmcp.mcp.MailToolService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class EmbeddedMcpServerManager implements AutoCloseable {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(8);

    private final McpHttpServerGroup serverGroup = new McpHttpServerGroup();
    private final Map<String, ServerRegistration> activeRegistrations = new LinkedHashMap<>();
    private final long currentPid = ProcessHandle.current().pid();

    public synchronized boolean isRunning(ServerRegistration registration) {
        if (serverGroup.isRunning(registration.profile())) {
            return true;
        }
        Optional<ProcessHandle> external = externalProcess(registration);
        if (external.isPresent()) {
            return true;
        }
        deletePidFile(registration);
        return false;
    }

    public synchronized long start(ServerRegistration registration) {
        return start(registration, "", "");
    }

    public synchronized long start(ServerRegistration registration, String transientPassword) {
        return start(registration, transientPassword, "");
    }

    public synchronized long start(ServerRegistration registration, String transientPassword, String transientVaultPassword) {
        MailToolService toolService = toolService(registration, transientPassword, transientVaultPassword);
        stopExternalProcess(registration);
        try {
            serverGroup.startOrReplace(registration, toolService);
            activeRegistrations.put(registration.profile(), registration);
            writePidFiles();
            return currentPid;
        } catch (Exception exception) {
            deleteActivePidFiles();
            activeRegistrations.clear();
            throw new ConfigurationException("Unable to start embedded MCP endpoint for profile " + registration.profile(), exception);
        }
    }

    public synchronized void stop(ServerRegistration registration) {
        try {
            serverGroup.stop(registration.profile());
            activeRegistrations.remove(registration.profile());
            stopExternalProcess(registration);
            deletePidFile(registration);
            writePidFiles();
        } catch (Exception exception) {
            throw new ConfigurationException("Unable to stop embedded MCP endpoint for profile " + registration.profile(), exception);
        }
    }

    @Override
    public synchronized void close() {
        serverGroup.close();
        deleteActivePidFiles();
        activeRegistrations.clear();
    }

    private MailToolService toolService(ServerRegistration registration, String transientPassword, String transientVaultPassword) {
        MailConfiguration configuration = new ConfigurationLoader(registration.configFile()).load(registration.profile());
        SecretStore secretStore = transientVaultPassword == null || transientVaultPassword.isBlank()
                ? LocalSecretStore.system()
                : LocalSecretStore.system(transientVaultPassword.toCharArray());
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        if (transientPassword != null && !transientPassword.isBlank()) {
            env.put(SecretResolver.passwordEnvName(registration.profile()), transientPassword);
        }
        ResolvedSecret secret = new SecretResolver(env, secretStore).resolve(configuration);
        return new MailApplicationService(
                configuration,
                secret.value(),
                AuditLogger.file(configuration.auditPath())
        );
    }

    private void writePidFiles() {
        for (ServerRegistration registration : activeRegistrations.values()) {
            try {
                Files.createDirectories(registration.runDir());
                Files.writeString(registration.pidFile(), Long.toString(currentPid), StandardCharsets.US_ASCII);
            } catch (IOException exception) {
                throw new ConfigurationException("Unable to write MCP endpoint PID file: " + registration.pidFile(), exception);
            }
        }
    }

    private void deleteActivePidFiles() {
        for (ServerRegistration registration : activeRegistrations.values()) {
            deletePidFile(registration);
        }
    }

    private static void deletePidFile(ServerRegistration registration) {
        try {
            Files.deleteIfExists(registration.pidFile());
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }

    private Optional<ProcessHandle> externalProcess(ServerRegistration registration) {
        Optional<Long> pid = readPid(registration);
        if (pid.isEmpty() || pid.get() == currentPid) {
            return Optional.empty();
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid.get());
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return Optional.empty();
        }
        if (!looksLikeMailProcess(handle.get())) {
            return Optional.empty();
        }
        return handle;
    }

    private void stopExternalProcess(ServerRegistration registration) {
        Optional<Long> pid = readPid(registration);
        if (pid.isEmpty() || pid.get() == currentPid) {
            return;
        }
        Optional<ProcessHandle> process = ProcessHandle.of(pid.get());
        if (process.isEmpty() || !process.get().isAlive()) {
            deletePidFile(registration);
            return;
        }
        ProcessHandle handle = process.get();
        if (!looksLikeMailProcess(handle)) {
            throw new ConfigurationException("Refusing to stop PID " + pid.get() + " because it does not look like opcoach-mcp-mail.");
        }
        handle.destroy();
        try {
            boolean stopped = handle.onExit().completeOnTimeout(null, STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).join() != null;
            if (!stopped && handle.isAlive()) {
                handle.destroyForcibly();
                handle.onExit().join();
            }
        } finally {
            deletePidFile(registration);
        }
    }

    private static Optional<Long> readPid(ServerRegistration registration) {
        if (!Files.exists(registration.pidFile())) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(Files.readString(registration.pidFile(), StandardCharsets.US_ASCII).trim()));
        } catch (IOException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static boolean looksLikeMailProcess(ProcessHandle handle) {
        String commandLine = handle.info().commandLine().orElse("");
        return commandLine.contains("opcoach-mcp-mail.jar")
                || commandLine.contains("org.opcoach.mailmcp.config.WebManagerApplication")
                || commandLine.contains("org.opcoach.mailmcp.MailMcpApplication");
    }
}
