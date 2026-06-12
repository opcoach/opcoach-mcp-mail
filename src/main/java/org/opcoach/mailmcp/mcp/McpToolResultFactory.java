package org.opcoach.mailmcp.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.opcoach.mailmcp.security.SafeErrorMessage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpToolResultFactory {

    private final McpJsonMapper jsonMapper;

    public McpToolResultFactory(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public McpSchema.CallToolResult success(Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("data", data == null ? Map.of() : data);
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(write(payload))))
                .structuredContent(payload)
                .isError(false)
                .build();
    }

    public McpSchema.CallToolResult failure(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", SafeErrorMessage.clean(message));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", false);
        payload.put("error", error);
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(write(payload))))
                .structuredContent(payload)
                .isError(true)
                .build();
    }

    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (IOException exception) {
            return "{\"ok\":false,\"error\":{\"code\":\"serialization_error\",\"message\":\"Réponse non sérialisable.\"}}";
        }
    }
}
