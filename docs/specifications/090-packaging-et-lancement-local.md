# Packaging et lancement local

## Objectif

Définir comment un utilisateur ou un stagiaire installe, compile et lance le serveur MCP mail avec le minimum de friction.

## Décisions retenues

- Le repo inclut Maven Wrapper.
- Le build produit un jar exécutable.
- Le mode `--stdio` est le mode par défaut recommandé pour les clients MCP locaux.
- Le mode `--http` sert aux tests, aux démonstrations et à certains clients compatibles.
- En HTTP, le serveur écoute sur `127.0.0.1` par défaut.

## Comportement attendu

Installation:

```bash
git clone https://example.com/opcoach-mail-mcp.git
cd opcoach-mail-mcp
./mvnw package
```

Lancement stdio:

```bash
java -jar target/opcoach-mail-mcp.jar --stdio
```

Lancement HTTP local:

```bash
java -jar target/opcoach-mail-mcp.jar --http --port 8095
```

## Points d'attention

- Le serveur HTTP ne doit pas écouter sur `0.0.0.0` sans configuration explicite.
- Si le bind n'est pas localhost, un token d'accès doit être obligatoire.
- Les exemples doivent fonctionner sur macOS, Linux et Windows autant que possible.
- Les commandes de formation ne doivent pas demander de secrets réels dans les supports.
- Les messages d'erreur au démarrage doivent aider à corriger la configuration.

## Exemples fictifs sans secrets

Configuration Claude Desktop indicative:

```json
{
  "mcpServers": {
    "opcoach-mail": {
      "command": "java",
      "args": ["-jar", "/chemin/fictif/opcoach-mail-mcp.jar", "--stdio"]
    }
  }
}
```
