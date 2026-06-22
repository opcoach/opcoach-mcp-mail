package org.opcoach.mailmcp.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerRegistry {

    private final Path baseDir;

    public ServerRegistry(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static ServerRegistry defaultRegistry() {
        return new ServerRegistry(ConfigurationPaths.defaultHomeDir());
    }

    public Path profilesDir() {
        return baseDir.resolve("profiles");
    }

    public Path runDir(String profile) {
        return baseDir.resolve("run").resolve(registryName(profile));
    }

    public Path configFile(String profile) {
        return profilesDir().resolve(registryName(profile) + ".properties");
    }

    public Path serversDir() {
        return baseDir.resolve("servers");
    }

    public List<ServerRegistration> list() {
        Path dir = serversDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            List<ServerRegistration> registrations = new ArrayList<>();
            stream.filter(path -> path.getFileName().toString().endsWith(".env"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> read(path).ifPresent(registrations::add));
            return registrations;
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to list registered servers: " + dir, exception);
        }
    }

    public void write(ServerRegistration registration) {
        Path file = serversDir().resolve(registryName(registration.profile()) + ".env");
        String content = """
                PROFILE=%s
                CONFIG_FILE=%s
                RUN_DIR=%s
                HOST=%s
                PORT=%d
                """.formatted(
                registration.profile(),
                registration.configFile(),
                registration.runDir(),
                registration.host(),
                registration.port()
        );
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to write server registration: " + file, exception);
        }
    }

    public void delete(String profile) {
        Path file = serversDir().resolve(registryName(profile) + ".env");
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new ConfigurationException("Unable to delete server registration: " + file, exception);
        }
    }

    private java.util.Optional<ServerRegistration> read(Path file) {
        try {
            Map<String, String> values = parseEnvFile(file);
            String profile = required(values, "PROFILE");
            return java.util.Optional.of(new ServerRegistration(
                    profile,
                    Path.of(required(values, "CONFIG_FILE")),
                    Path.of(required(values, "RUN_DIR")),
                    values.getOrDefault("HOST", "127.0.0.1"),
                    Integer.parseInt(required(values, "PORT"))
            ));
        } catch (IOException | IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private static Map<String, String> parseEnvFile(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            int index = trimmed.indexOf('=');
            values.put(trimmed.substring(0, index), trimmed.substring(index + 1));
        }
        return values;
    }

    static String registryName(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
        return sanitized.isBlank() ? "default" : sanitized;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing server registration field: " + key);
        }
        return value.trim();
    }
}
