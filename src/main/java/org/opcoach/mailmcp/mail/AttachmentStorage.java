package org.opcoach.mailmcp.mail;

import org.opcoach.mailmcp.config.ConfigurationException;
import org.opcoach.mailmcp.config.ConfigurationPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

final class AttachmentStorage {

    private static final int MAX_FILENAME_LENGTH = 160;

    private final Path root;

    AttachmentStorage(String profile) {
        this.root = ConfigurationPaths.attachmentBaseDir()
                .resolve(safePathSegment(profile))
                .toAbsolutePath()
                .normalize();
    }

    Path root() {
        return root;
    }

    Path target(String directory, String requestedFilename, String fallbackFilename) {
        Path targetDirectory = targetDirectory(directory);
        String filename = safeFilename(firstPresent(requestedFilename, fallbackFilename));
        Path candidate = targetDirectory.resolve(filename).normalize();
        ensureInsideRoot(candidate);
        return unique(candidate);
    }

    void createParentDirectories(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            restrictOwnerOnly(parent);
        }
    }

    void restrictOwnerOnly(Path path) {
        try {
            Set<PosixFilePermission> permissions = Files.isDirectory(path)
                    ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some volumes do not support POSIX permissions.
        }
    }

    private Path targetDirectory(String directory) {
        String value = directory == null ? "" : directory.trim();
        if (value.isBlank()) {
            return root;
        }
        if (value.contains("\\") || value.contains(":")) {
            throw new ConfigurationException("Attachment directory must be a relative path using '/' separators.");
        }
        Path relative = Path.of(value).normalize();
        if (relative.isAbsolute() || startsWithParent(relative)) {
            throw new ConfigurationException("Attachment directory must stay inside the configured attachment root.");
        }
        Path targetDirectory = root.resolve(relative).normalize();
        ensureInsideRoot(targetDirectory);
        return targetDirectory;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw new ConfigurationException("Attachment target path escapes the configured attachment root.");
        }
    }

    private static boolean startsWithParent(Path path) {
        return path.getNameCount() > 0 && "..".equals(path.getName(0).toString());
    }

    private static String firstPresent(String requested, String fallback) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return fallback == null || fallback.isBlank() ? "attachment" : fallback;
    }

    private static String safeFilename(String filename) {
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String leaf = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        String safe = leaf.replaceAll("[\\p{Cntrl}/\\\\:<>\"|?*]+", "_").trim();
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            safe = "attachment";
        }
        if (safe.length() > MAX_FILENAME_LENGTH) {
            safe = safe.substring(0, MAX_FILENAME_LENGTH);
        }
        return safe;
    }

    private static String safePathSegment(String value) {
        return safeFilename(value == null || value.isBlank() ? "default" : value).replace(' ', '_');
    }

    private static Path unique(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        Path parent = path.getParent();
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        for (int index = 1; index < 10_000; index++) {
            Path candidate = parent.resolve(base + "-" + index + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new ConfigurationException("Unable to find a free attachment filename for: " + filename);
    }
}
