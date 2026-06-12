# Packaging et lancement local

## Objectif

Définir comment un utilisateur ou un stagiaire installe, compile et lance le serveur MCP mail avec le minimum de friction.

## Décisions retenues

- Le repo inclut Maven Wrapper.
- Le build produit un jar exécutable.
- Le dépôt public doit pouvoir être utilisé après un simple `git clone`.
- `./mvnw clean verify` reste non interactif pour être compatible avec GitHub Actions et les environnements CI.
- Un profil Maven explicite `-Psetup` lance l'assistant terminal de configuration.
- Un profil Maven explicite `-Psetup-ui` peut lancer une mini UI locale de configuration.
- Le mode `--stdio` est le mode par défaut recommandé pour les clients MCP locaux.
- Le mode `--http` sert aux tests, aux démonstrations et à certains clients compatibles.
- En HTTP, le serveur écoute sur `127.0.0.1` par défaut.

## Comportement attendu

Installation:

```bash
git clone https://github.com/opcoach/opcoach-mail-mcp.git
cd opcoach-mail-mcp
./mvnw clean verify
```

Configuration terminal:

```bash
./mvnw -Psetup clean verify
```

Configuration avec mini UI locale:

```bash
./mvnw -Psetup-ui clean verify
```

Lancement stdio:

```bash
java -jar target/opcoach-mail-mcp.jar --stdio
```

Lancement HTTP local:

```bash
java -jar target/opcoach-mail-mcp.jar --http --port 8095
```

Le profil `-Psetup` peut poser des questions interactives uniquement quand il est demandé explicitement. Le build standard ne doit jamais bloquer en attente d'une saisie utilisateur.

Si aucune configuration n'existe au lancement du serveur, le message d'erreur doit expliquer la commande de configuration à exécuter, sans demander de secret dans un log.

## Points d'attention

- Le serveur HTTP ne doit pas écouter sur `0.0.0.0` sans configuration explicite.
- Si le bind n'est pas localhost, un token d'accès doit être obligatoire.
- La mini UI de configuration doit refuser les connexions distantes.
- `./mvnw clean verify` ne doit pas dépendre d'une vraie boîte mail.
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

Exemple de première expérience attendue:

```text
Configuration absente.
Lancez ./mvnw -Psetup clean verify pour créer un profil local.
Le mot de passe sera stocké dans le trousseau du système.
```
