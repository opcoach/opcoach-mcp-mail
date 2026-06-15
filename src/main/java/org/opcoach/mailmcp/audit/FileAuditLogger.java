package org.opcoach.mailmcp.audit;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import org.opcoach.mailmcp.security.SafeErrorMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class FileAuditLogger implements AuditLogger {

    private final Path path;
    private final McpJsonMapper mapper;

    FileAuditLogger(Path path) {
        this(path, McpJsonDefaults.getMapper());
    }

    FileAuditLogger(Path path, McpJsonMapper mapper) {
        this.path = path;
        this.mapper = mapper;
    }

    @Override
    public synchronized void record(AuditEvent event) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = SafeErrorMessage.clean(mapper.writeValueAsString(event)) + System.lineSeparator();
            Files.writeString(
                    path,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write local audit log: " + path, exception);
        }
    }
}
