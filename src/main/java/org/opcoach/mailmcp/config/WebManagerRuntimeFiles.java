package org.opcoach.mailmcp.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class WebManagerRuntimeFiles {

    private static final String URL_FILE = "web-manager.url";
    private static final String PID_FILE = "web-manager.pid";

    private final Path baseDir;

    WebManagerRuntimeFiles(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static WebManagerRuntimeFiles defaults() {
        return new WebManagerRuntimeFiles(ConfigurationPaths.defaultHomeDir());
    }

    Path urlFile() {
        return baseDir.resolve(URL_FILE);
    }

    void write(String url, long pid) {
        try {
            Files.createDirectories(baseDir);
            Files.writeString(urlFile(), url + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.writeString(pidFile(), Long.toString(pid) + System.lineSeparator(), StandardCharsets.US_ASCII);
            restrictOwnerReadWrite(urlFile());
            restrictOwnerReadWrite(pidFile());
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to write web manager runtime files in " + baseDir, exception);
        }
    }

    public Optional<String> currentUrl() {
        Optional<Long> pid = readPid();
        if (pid.isEmpty() || !isAlive(pid.get())) {
            deleteQuietly();
            return Optional.empty();
        }
        try {
            if (!Files.exists(urlFile())) {
                return Optional.empty();
            }
            String url = Files.readString(urlFile(), StandardCharsets.UTF_8).trim();
            return url.isBlank() ? Optional.empty() : Optional.of(url);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    void deleteIfOwnedBy(long pid) {
        Optional<Long> storedPid = readPid();
        if (storedPid.isPresent() && storedPid.get() == pid) {
            deleteQuietly();
        }
    }

    private Path pidFile() {
        return baseDir.resolve(PID_FILE);
    }

    private Optional<Long> readPid() {
        try {
            if (!Files.exists(pidFile())) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(Files.readString(pidFile(), StandardCharsets.US_ASCII).trim()));
        } catch (IOException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private void deleteQuietly() {
        try {
            Files.deleteIfExists(urlFile());
            Files.deleteIfExists(pidFile());
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }

    private static boolean isAlive(long pid) {
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void restrictOwnerReadWrite(Path path) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some volumes do not support POSIX permissions.
        }
    }
}
