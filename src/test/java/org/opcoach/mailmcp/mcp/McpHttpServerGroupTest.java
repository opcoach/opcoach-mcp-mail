package org.opcoach.mailmcp.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opcoach.mailmcp.config.ServerRegistration;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpHttpServerGroupTest {

    @TempDir
    Path tempDir;

    @Test
    void servesSeveralProfilesInOneServerGroup() throws Exception {
        int firstPort = freePort();
        int secondPort = freePort();

        try (McpHttpServerGroup group = new McpHttpServerGroup()) {
            group.startOrReplace(registration("first", firstPort), new UnavailableMailToolService());
            group.startOrReplace(registration("second", secondPort), new UnavailableMailToolService());

            assertTrue(group.isRunning("first"));
            assertTrue(group.isRunning("second"));
            assertEquals(200, healthStatus(firstPort));
            assertEquals(200, healthStatus(secondPort));

            group.stop("first");

            assertFalse(group.isRunning("first"));
            assertTrue(group.isRunning("second"));
            assertEquals(200, healthStatus(secondPort));
        }
    }

    private ServerRegistration registration(String profile, int port) {
        Path runDir = tempDir.resolve("run").resolve(profile);
        return new ServerRegistration(
                profile,
                tempDir.resolve(profile + ".properties"),
                runDir,
                "127.0.0.1",
                port
        );
    }

    private static int healthStatus(int port) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
