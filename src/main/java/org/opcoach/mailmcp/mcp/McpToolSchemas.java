package org.opcoach.mailmcp.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpToolSchemas {

    private McpToolSchemas() {
    }

    public static Map<String, Object> sendEmail() {
        Map<String, Object> attachment = object(
                props(
                        entry("filename", string("Nom de fichier transmis au destinataire.")),
                        entry("contentType", string("Type MIME de la pièce jointe.")),
                        entry("contentBase64", string("Contenu base64 de la pièce jointe."))
                ),
                List.of("filename", "contentBase64")
        );

        return object(
                props(
                        entry("to", array("Destinataires principaux.", string("Adresse email."))),
                        entry("cc", array("Destinataires en copie.", string("Adresse email."))),
                        entry("bcc", array("Destinataires en copie cachée.", string("Adresse email."))),
                        entry("subject", string("Sujet du message.")),
                        entry("textBody", string("Corps texte du message.")),
                        entry("htmlBody", string("Corps HTML du message.")),
                        entry("attachments", array("Pièces jointes base64 explicites.", attachment))
                ),
                List.of("to", "subject")
        );
    }

    public static Map<String, Object> listMailboxes() {
        return object(
                props(entry("includeSpecialUse", bool("Inclure les attributs spéciaux IMAP si disponibles."))),
                List.of()
        );
    }

    public static Map<String, Object> searchMessages() {
        return object(
                props(
                        entry("mailbox", string("Dossier IMAP à interroger.", "INBOX")),
                        entry("fromContains", string("Filtre partiel sur l'expéditeur.")),
                        entry("toContains", string("Filtre partiel sur les destinataires.")),
                        entry("subjectContains", string("Filtre partiel sur le sujet.")),
                        entry("since", date("Date minimale au format ISO yyyy-MM-dd.")),
                        entry("unreadOnly", bool("Limiter aux messages non lus.", false)),
                        entry("limit", integer("Nombre maximal de messages retournés.", 1, 25, 10))
                ),
                List.of()
        );
    }

    public static Map<String, Object> getMessage() {
        return object(
                props(
                        entry("mailbox", string("Dossier IMAP contenant le message.", "INBOX")),
                        entry("uid", string("UID IMAP stable du message.")),
                        entry("includeHtml", bool("Inclure le corps HTML borné.", false)),
                        entry("maxBodyBytes", integer("Taille maximale du corps texte retourné.", 1, 100_000, 12_000))
                ),
                List.of("uid")
        );
    }

    public static Map<String, Object> getAttachment() {
        return object(
                props(
                        entry("mailbox", string("Dossier IMAP contenant le message.", "INBOX")),
                        entry("uid", string("UID IMAP stable du message.")),
                        entry("attachmentId", string("Identifiant de pièce jointe fourni par getMessage.")),
                        entry("maxBytes", integer("Taille maximale de pièce jointe acceptée.", 1, 10 * 1024 * 1024, 5 * 1024 * 1024))
                ),
                List.of("uid", "attachmentId")
        );
    }

    @SafeVarargs
    private static Map<String, Object> props(Map.Entry<String, Object>... entries) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            properties.put(entry.getKey(), entry.getValue());
        }
        return properties;
    }

    private static Map.Entry<String, Object> entry(String name, Object schema) {
        return Map.entry(name, schema);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new ArrayList<>(required));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> string(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        return schema;
    }

    private static Map<String, Object> string(String description, String defaultValue) {
        Map<String, Object> schema = string(description);
        schema.put("default", defaultValue);
        return schema;
    }

    private static Map<String, Object> date(String description) {
        Map<String, Object> schema = string(description);
        schema.put("format", "date");
        return schema;
    }

    private static Map<String, Object> bool(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "boolean");
        schema.put("description", description);
        return schema;
    }

    private static Map<String, Object> bool(String description, boolean defaultValue) {
        Map<String, Object> schema = bool(description);
        schema.put("default", defaultValue);
        return schema;
    }

    private static Map<String, Object> integer(String description, int minimum, int maximum, int defaultValue) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("description", description);
        schema.put("minimum", minimum);
        schema.put("maximum", maximum);
        schema.put("default", defaultValue);
        return schema;
    }

    private static Map<String, Object> array(String description, Object itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", itemSchema);
        return schema;
    }
}
