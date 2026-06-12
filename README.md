# opcoach-mail-mcp

Serveur MCP local-first pour accéder à une boîte mail IMAP/SMTP générique depuis Codex, Claude Code Pro, Claude Desktop ou tout client compatible MCP.

Le serveur ne dépend pas de Gmail, Microsoft 365 ni d'un OAuth propriétaire. Il se lance sur votre machine ou sur un serveur que vous contrôlez. Le mot de passe mail n'est jamais envoyé au modèle IA.

## Prérequis

- Java 24
- Un compte mail compatible IMAP et SMTP
- Un mot de passe applicatif si votre fournisseur le demande

Maven n'est pas requis: le dépôt inclut Maven Wrapper.

## Installer et vérifier

```bash
git clone https://github.com/opcoach/opcoach-mail-mcp.git
cd opcoach-mail-mcp
./mvnw clean verify
```

Le build standard est non interactif et utilise uniquement de faux serveurs mail pour les tests.

## Configurer

Assistant terminal:

```bash
./mvnw -Psetup clean verify
```

Ou mini UI locale éphémère:

```bash
./mvnw -Psetup-ui clean verify
```

Par défaut, la configuration non secrète est écrite dans:

```text
~/.opcoach-mail-mcp/config.properties
```

Exemple:

```properties
profile=default
imap.host=imap.example.com
imap.port=993
imap.security=ssl_tls
smtp.host=smtp.example.com
smtp.port=465
smtp.security=ssl_tls
username=formation@example.com
from.address=formation@example.com
from.name=Formation MCP
sent.mailbox=INBOX.Sent
```

Le mot de passe n'est pas écrit dans ce fichier. Pour un atelier court:

```bash
export MAIL_MCP_PASSWORD="mot-de-passe-fictif"
```

Pour enregistrer le mot de passe dans le trousseau local:

```bash
java -jar target/opcoach-mail-mcp.jar config set-password --profile default
```

Le trousseau macOS est pris en charge. Sur les autres plateformes, utilisez temporairement `MAIL_MCP_PASSWORD` tant qu'un backend durable n'est pas ajouté.

## Lancer avec un client MCP

Mode recommandé pour Codex et Claude:

```bash
java -jar target/opcoach-mail-mcp.jar --stdio
```

Mode HTTP local:

```bash
java -jar target/opcoach-mail-mcp.jar --http --port 8095
```

Le serveur HTTP écoute sur `127.0.0.1` par défaut. Si vous écoutez sur une autre interface, fournissez un jeton:

```bash
java -jar target/opcoach-mail-mcp.jar --http --host 0.0.0.0 --port 8095 --token "jeton-long-et-aléatoire"
```

## Configuration Codex

Exemple indicatif:

```json
{
  "mcpServers": {
    "opcoach-mail": {
      "command": "java",
      "args": ["-jar", "/chemin/vers/opcoach-mail-mcp/target/opcoach-mail-mcp.jar", "--stdio"]
    }
  }
}
```

## Configuration Claude

Exemple indicatif:

```json
{
  "mcpServers": {
    "opcoach-mail": {
      "command": "java",
      "args": ["-jar", "/chemin/vers/opcoach-mail-mcp/target/opcoach-mail-mcp.jar", "--stdio"]
    }
  }
}
```

## Tools exposés

- `sendEmail`: envoie un email texte ou HTML avec pièces jointes base64, puis tente une copie dans les envoyés.
- `listMailboxes`: liste les dossiers IMAP disponibles.
- `searchMessages`: recherche des messages avec une limite prudente.
- `getMessage`: lit un message précis par UID.
- `getAttachment`: récupère explicitement une pièce jointe par identifiant.

Les recherches retournent des métadonnées et des extraits. Les pièces jointes ne sont jamais téléchargées automatiquement.

## Sécurité

- Aucune action destructive en v1.
- Pas de lecture massive sans limite.
- Les corps de mails et contenus de pièces jointes ne sont pas écrits dans les logs d'audit.
- Un email lu par l'IA reste une donnée externe non fiable.
- Le client IA doit demander confirmation avant tout envoi réel selon son contexte.

## Licence

MIT.
