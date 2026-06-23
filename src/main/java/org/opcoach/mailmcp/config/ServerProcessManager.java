package org.opcoach.mailmcp.config;

import org.opcoach.mailmcp.MailMcpApplication;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ServerProcessManager {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(8);

    private final Path jarPath;

    public ServerProcessManager(Path jarPath) {
        this.jarPath = jarPath;
    }

    public static ServerProcessManager currentApplication() {
        return new ServerProcessManager(currentJarPath());
    }

    public boolean isRunning(ServerRegistration registration) {
        return runningProcess(registration).isPresent();
    }

    public long start(ServerRegistration registration) {
        return start(registration, "");
    }

    public long start(ServerRegistration registration, String transientPassword) {
        Optional<ProcessHandle> existing = runningProcess(registration);
        if (existing.isPresent()) {
            return existing.get().pid();
        }
        try {
            Files.createDirectories(registration.runDir());
            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-jar");
            command.add(jarPath.toString());
            command.add("--http");
            command.add("--host");
            command.add(registration.host());
            command.add("--port");
            command.add(Integer.toString(registration.port()));
            command.add("--profile");
            command.add(registration.profile());

            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(jarPath.toAbsolutePath().getParent().toFile())
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(registration.logFile().toFile()))
                    .redirectError(ProcessBuilder.Redirect.appendTo(registration.logFile().toFile()));
            builder.environment().put(ConfigurationPaths.CONFIG_ENV, registration.configFile().toString());
            builder.environment().put("MAIL_MCP_RUN_DIR", registration.runDir().toString());
            if (transientPassword != null && !transientPassword.isBlank()) {
                builder.environment().put("MAIL_MCP_PASSWORD", transientPassword);
            }

            Files.writeString(
                    registration.logFile(),
                    "\n===== starting profile=" + registration.profile() + " port=" + registration.port() + " =====\n",
                    StandardCharsets.UTF_8,
                    Files.exists(registration.logFile())
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE
            );
            Process process = builder.start();
            Files.writeString(registration.pidFile(), Long.toString(process.pid()), StandardCharsets.US_ASCII);
            Thread.sleep(1000);
            if (!process.isAlive()) {
                Files.deleteIfExists(registration.pidFile());
                throw new ConfigurationException("Server failed to start. Check log: " + registration.logFile());
            }
            return process.pid();
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to start server for profile " + registration.profile(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConfigurationException("Server start interrupted for profile " + registration.profile(), exception);
        }
    }

    public void stop(ServerRegistration registration) {
        Optional<ProcessHandle> process = runningProcess(registration);
        if (process.isEmpty()) {
            deletePidFile(registration);
            return;
        }
        ProcessHandle handle = process.get();
        handle.destroy();
        try {
            boolean stopped = handle.onExit().completeOnTimeout(null, STOP_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).join() != null;
            if (!stopped && handle.isAlive()) {
                handle.destroyForcibly();
                handle.onExit().join();
            }
        } finally {
            deletePidFile(registration);
        }
    }

    private Optional<ProcessHandle> runningProcess(ServerRegistration registration) {
        Optional<Long> pid = readPid(registration.pidFile());
        if (pid.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid.get());
        if (handle.isEmpty() || !handle.get().isAlive()) {
            deletePidFile(registration);
            return Optional.empty();
        }
        return handle;
    }

    private static Optional<Long> readPid(Path pidFile) {
        if (!Files.exists(pidFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(Files.readString(pidFile, StandardCharsets.US_ASCII).trim()));
        } catch (IOException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static void deletePidFile(ServerRegistration registration) {
        try {
            Files.deleteIfExists(registration.pidFile());
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }

    private static Path currentJarPath() {
        try {
            URI location = MailMcpApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location);
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                return path;
            }
            Path packagedJar = path.toAbsolutePath().getParent().resolve("app").resolve("opcoach-mcp-mail.jar");
            if (Files.isRegularFile(packagedJar)) {
                return packagedJar;
            }
        } catch (Exception ignored) {
            // Fall through to the standard build output path.
        }
        Path packagedJar = Path.of("app", "opcoach-mcp-mail.jar").toAbsolutePath();
        if (Files.isRegularFile(packagedJar)) {
            return packagedJar;
        }
        return Path.of("target", "opcoach-mcp-mail.jar").toAbsolutePath();
    }

    private static String javaExecutable() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        if (isWindows()) {
            Path javaw = javaHome.resolve("bin").resolve("javaw.exe");
            if (Files.exists(javaw)) {
                return javaw.toString();
            }
            return javaHome.resolve("bin").resolve("java.exe").toString();
        }
        return javaHome.resolve("bin").resolve("java").toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
