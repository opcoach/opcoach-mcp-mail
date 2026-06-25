package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void writesPlainEnvFileCompatibleWithScripts() throws Exception {
        ServerRegistry registry = new ServerRegistry(tempDir);
        ServerRegistration registration = new ServerRegistration(
                "Mail Olivier",
                Path.of("C:\\Users\\olivier\\mail.properties"),
                Path.of("C:\\Users\\olivier\\run"),
                "127.0.0.1",
                8096
        );

        registry.write(registration);

        String content = Files.readString(tempDir.resolve("servers").resolve("Mail_Olivier.env"));
        assertTrue(content.contains("PROFILE=Mail Olivier"));
        assertTrue(content.contains("CONFIG_FILE=C:\\Users\\olivier\\mail.properties"));
        List<ServerRegistration> registrations = registry.list();
        assertEquals(registration, registrations.getFirst());
    }

    @Test
    void readsUtf8BomEnvFiles() throws Exception {
        Path servers = tempDir.resolve("servers");
        Files.createDirectories(servers);
        Files.writeString(servers.resolve("default.env"), """
                \uFEFFPROFILE=default
                CONFIG_FILE=C:\\Users\\chrys\\mail.properties
                RUN_DIR=C:\\Users\\chrys\\run
                HOST=127.0.0.1
                PORT=8095
                """);

        List<ServerRegistration> registrations = new ServerRegistry(tempDir).list();

        assertEquals(1, registrations.size());
        assertEquals("default", registrations.getFirst().profile());
        assertEquals(8095, registrations.getFirst().port());
    }

    @Test
    void deletesRegistrationAndProfileConfiguration() throws Exception {
        ServerRegistry registry = new ServerRegistry(tempDir);
        Path configFile = tempDir.resolve("profiles").resolve("default.properties");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "mail.username=training@example.com\n");
        ServerRegistration registration = new ServerRegistration(
                "default",
                configFile,
                tempDir.resolve("run").resolve("default"),
                "127.0.0.1",
                8095
        );
        registry.write(registration);

        registry.delete(registration);

        assertTrue(Files.notExists(tempDir.resolve("servers").resolve("default.env")));
        assertTrue(Files.notExists(configFile));
        assertTrue(registry.list().isEmpty());
    }
}
