package org.opcoach.mailmcp.audit;

import java.nio.file.Path;

public interface AuditLogger {

    void record(AuditEvent event);

    static AuditLogger noop() {
        return event -> {
        };
    }

    static AuditLogger file(Path path) {
        return new FileAuditLogger(path);
    }
}
