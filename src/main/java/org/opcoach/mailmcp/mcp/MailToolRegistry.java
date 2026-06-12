package org.opcoach.mailmcp.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.opcoach.mailmcp.config.ConfigurationException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MailToolRegistry {

    private final MailToolService mailToolService;
    private final McpToolResultFactory resultFactory;

    public MailToolRegistry(MailToolService mailToolService, McpJsonMapper jsonMapper) {
        this.mailToolService = mailToolService;
        this.resultFactory = new McpToolResultFactory(jsonMapper);
    }

    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        return List.of(
                tool(MailToolNames.SEND_EMAIL, "Envoie un email MIME via SMTP.", McpToolSchemas.sendEmail(), mailToolService::sendEmail),
                tool(MailToolNames.LIST_MAILBOXES, "Liste les dossiers IMAP disponibles.", McpToolSchemas.listMailboxes(), mailToolService::listMailboxes),
                tool(MailToolNames.SEARCH_MESSAGES, "Recherche des emails avec une limite prudente.", McpToolSchemas.searchMessages(), mailToolService::searchMessages),
                tool(MailToolNames.GET_MESSAGE, "Lit un message précis par UID IMAP.", McpToolSchemas.getMessage(), mailToolService::getMessage),
                tool(MailToolNames.GET_ATTACHMENT, "Récupère explicitement une pièce jointe par identifiant.", McpToolSchemas.getAttachment(), mailToolService::getAttachment)
        );
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> inputSchema,
            Function<Map<String, Object>, Object> handler
    ) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, inputSchema)
                .description(description)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> callTool(name, handler, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult callTool(String toolName, Function<Map<String, Object>, Object> handler, Map<String, Object> arguments) {
        try {
            return resultFactory.success(handler.apply(arguments == null ? Map.of() : arguments));
        } catch (IllegalArgumentException | ConfigurationException exception) {
            return resultFactory.failure("invalid_request", exception.getMessage());
        } catch (Exception exception) {
            return resultFactory.failure("mail_operation_failed", "Échec du tool " + toolName + ": " + exception.getMessage());
        }
    }
}
