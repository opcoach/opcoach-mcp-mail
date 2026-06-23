package org.opcoach.mailmcp.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailToolRegistryTest {

    @Test
    void exposesMailTools() {
        MailToolService stub = new StubMailToolService();
        MailToolRegistry registry = new MailToolRegistry(stub, McpJsonDefaults.getMapper());

        List<String> names = registry.toolSpecifications().stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .map(McpSchema.Tool::name)
                .toList();

        assertEquals(MailToolNames.ALL, names);
    }

    @Test
    void schemasUseStrictObjectRootsWithoutTopLevelCombinators() {
        MailToolRegistry registry = new MailToolRegistry(new StubMailToolService(), McpJsonDefaults.getMapper());

        for (McpServerFeatures.SyncToolSpecification specification : registry.toolSpecifications()) {
            Map<String, Object> schema = specification.tool().inputSchema();
            assertEquals("object", schema.get("type"), specification.tool().name());
            assertTrue(schema.containsKey("properties"), specification.tool().name());
            assertFalse(schema.containsKey("oneOf"), specification.tool().name());
            assertFalse(schema.containsKey("anyOf"), specification.tool().name());
            assertFalse(schema.containsKey("allOf"), specification.tool().name());
        }
    }

    private static final class StubMailToolService implements MailToolService {

        @Override
        public Object sendEmail(Map<String, Object> arguments) {
            return ok();
        }

        @Override
        public Object listMailboxes(Map<String, Object> arguments) {
            return ok();
        }

        @Override
        public Object searchMessages(Map<String, Object> arguments) {
            return ok();
        }

        @Override
        public Object getMessage(Map<String, Object> arguments) {
            return ok();
        }

        @Override
        public Object getAttachment(Map<String, Object> arguments) {
            return ok();
        }

        @Override
        public Object moveMessage(Map<String, Object> arguments) {
            return ok();
        }

        @Override
        public Object deleteMessage(Map<String, Object> arguments) {
            return ok();
        }

        private static Map<String, Object> ok() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "ok");
            return data;
        }
    }
}
