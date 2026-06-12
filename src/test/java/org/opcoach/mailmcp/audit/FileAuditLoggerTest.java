package org.opcoach.mailmcp.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAuditLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void writesOnlyMinimalMetadata() throws Exception {
        Path audit = tempDir.resolve("audit.log");
        AuditLogger logger = AuditLogger.file(audit);

        logger.record(AuditEvent.success("sendEmail", "Sent", "<id@example.com>", List.of("destinataire@example.com")));

        String line = Files.readString(audit);
        assertTrue(line.contains("\"tool\":\"sendEmail\""));
        assertTrue(line.contains("destinataire@example.com"));
        assertFalse(line.contains("Bonjour, ceci est un corps de mail"));
        assertFalse(line.contains("secret-fictif"));
    }
}
