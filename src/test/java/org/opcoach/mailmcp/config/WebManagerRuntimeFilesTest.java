package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebManagerRuntimeFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void storesCurrentWebManagerUrlWhileProcessIsAlive() {
        WebManagerRuntimeFiles runtimeFiles = new WebManagerRuntimeFiles(tempDir);
        long pid = ProcessHandle.current().pid();

        runtimeFiles.write("http://127.0.0.1:18100/?token=test", pid);

        assertEquals("http://127.0.0.1:18100/?token=test", runtimeFiles.currentUrl().orElseThrow());
        assertTrue(Files.exists(tempDir.resolve("web-manager.url")));
        assertTrue(Files.exists(tempDir.resolve("web-manager.pid")));

        runtimeFiles.deleteIfOwnedBy(pid);

        assertFalse(Files.exists(tempDir.resolve("web-manager.url")));
        assertFalse(Files.exists(tempDir.resolve("web-manager.pid")));
    }

    @Test
    void removesStaleUrlWhenRecordedProcessIsNotAlive() {
        WebManagerRuntimeFiles runtimeFiles = new WebManagerRuntimeFiles(tempDir);

        runtimeFiles.write("http://127.0.0.1:18100/?token=stale", Long.MAX_VALUE);

        assertTrue(runtimeFiles.currentUrl().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("web-manager.url")));
        assertFalse(Files.exists(tempDir.resolve("web-manager.pid")));
    }
}
