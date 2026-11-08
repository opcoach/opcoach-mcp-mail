package org.opcoach.mailmcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WebManagerRuntimeFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void keepsOptInBookmarkTokenAfterRuntimeCleanup() throws Exception {
        assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"));
        WebManagerRuntimeFiles runtimeFiles = new WebManagerRuntimeFiles(tempDir);
        assertTrue(runtimeFiles.bookmarkToken().isEmpty());
        Path tokenFile = Files.createFile(tempDir.resolve("web-manager.bookmark-token"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        String token = "a1".repeat(24);
        Files.writeString(tokenFile, token + "\n");
        long pid = ProcessHandle.current().pid();
        runtimeFiles.write("http://127.0.0.1:18100/?token=" + token, pid);
        runtimeFiles.deleteIfOwnedBy(pid);
        assertEquals(token, new WebManagerRuntimeFiles(tempDir).bookmarkToken().orElseThrow());

        Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(ConfigurationException.class, runtimeFiles::bookmarkToken);
    }

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
