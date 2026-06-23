package org.opcoach.mailmcp.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class WindowsDistributionBuilder {

    private WindowsDistributionBuilder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: WindowsDistributionBuilder <stagingDir> <jarPath> <runtimeUrl> <zipPath> <version>");
        }
        Path stagingDir = Path.of(args[0]);
        Path jarPath = Path.of(args[1]);
        URI runtimeUrl = URI.create(args[2]);
        Path zipPath = Path.of(args[3]);
        String version = args[4];

        Path appDir = stagingDir.resolve("app");
        Path runtimeDir = stagingDir.resolve("runtime");
        Files.createDirectories(appDir);
        Files.copy(jarPath, appDir.resolve("opcoach-mcp-mail.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        writeTextFiles(stagingDir, version);
        prepareRuntime(runtimeUrl, runtimeDir, zipPath.getParent().resolve("downloads").resolve("temurin-jre-windows-x64.zip"));
        zipDirectory(stagingDir, zipPath);
        writeChecksum(zipPath);

        System.out.println("Windows package: " + zipPath.toAbsolutePath());
        System.out.println("Checksum:        " + zipPath.toAbsolutePath() + ".sha256");
    }

    private static void writeTextFiles(Path stagingDir, String version) throws IOException {
        Files.writeString(stagingDir.resolve("README-WINDOWS.txt"), """
                OPCoach MCP Mail %s

                1. Extract the ZIP archive.
                2. Double-click "OPCoach MCP Mail.exe".
                3. Configure one mailbox profile.
                4. Click Start, then copy the local MCP URL into Codex.

                No Java, Maven, Git Bash, or PowerShell command is required.
                The server listens on 127.0.0.1 by default.
                """.formatted(version), StandardCharsets.UTF_8);

        Files.writeString(stagingDir.resolve("SECURITY-WINDOWS.txt"), """
                Security notes

                - The executable starts a local Java application bundled with this archive.
                - Mailbox passwords are not stored by the Windows package.
                - The password entered in the manager is passed only to the local server process.
                - MCP HTTP servers listen on 127.0.0.1 by default.
                - Verify the downloaded ZIP with the published .sha256 file when possible.
                - Official GitHub Release builds are Authenticode-signed before packaging.
                - Locally built packages can be unsigned and should be used only for development tests.
                """, StandardCharsets.UTF_8);
    }

    private static void prepareRuntime(URI runtimeUrl, Path runtimeDir, Path archivePath) throws Exception {
        Files.createDirectories(archivePath.getParent());
        if (!Files.exists(archivePath)) {
            download(runtimeUrl, archivePath);
        }
        deleteDirectory(runtimeDir);
        Files.createDirectories(runtimeDir);
        unzipStrippingRoot(archivePath, runtimeDir);
    }

    private static void download(URI uri, Path destination) throws IOException, InterruptedException {
        System.out.println("Downloading Windows JRE: " + uri);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(destination);
            throw new IOException("Unable to download Windows JRE: HTTP " + response.statusCode());
        }
    }

    private static void unzipStrippingRoot(Path archive, Path destination) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = strippedName(entry.getName());
                if (name.isBlank()) {
                    continue;
                }
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("Unsafe ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String strippedName(String rawName) {
        String normalized = rawName.replace('\\', '/');
        int separator = normalized.indexOf('/');
        if (separator < 0 || separator == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(separator + 1);
    }

    private static void zipDirectory(Path directory, Path zipPath) throws IOException {
        Files.createDirectories(zipPath.getParent());
        Files.deleteIfExists(zipPath);
        List<Path> paths;
        try (var stream = Files.walk(directory)) {
            paths = stream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
        String root = directory.getFileName().toString();
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Path path : paths) {
                String relative = root + "/" + directory.relativize(path).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(relative);
                output.putNextEntry(entry);
                Files.copy(path, output);
                output.closeEntry();
            }
        }
    }

    private static void writeChecksum(Path zipPath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fileInput = Files.newInputStream(zipPath);
             DigestInputStream input = new DigestInputStream(fileInput, digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        String checksum = HexFormat.of().formatHex(digest.digest());
        Files.writeString(
                Path.of(zipPath.toString() + ".sha256"),
                checksum + "  " + zipPath.getFileName() + System.lineSeparator(),
                StandardCharsets.US_ASCII
        );
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path current : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}
