# Configuration et secrets

## Objectif

Définir comment le serveur reçoit sa configuration mail et comment il protège les mots de passe.

## Décisions retenues

- La configuration non secrète est stockée dans un fichier local.
- Les secrets sont fournis par variable d'environnement pour les ateliers courts.
- Les secrets sont stockés dans le trousseau local pour les usages durables.
- Aucun mot de passe n'est stocké en clair dans le repo.
- Aucun assistant IA ne reçoit le mot de passe dans un appel MCP.

## Comportement attendu

Le fichier de configuration local peut contenir:

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

Le mot de passe peut être fourni temporairement:

```bash
export MAIL_MCP_PASSWORD="mot-de-passe-fictif"
```

Pour un usage durable, le serveur doit permettre de l'enregistrer dans le keychain local.

## Points d'attention

- Les exemples utilisent uniquement des valeurs fictives.
- En production locale, le keychain est préféré aux variables d'environnement.
- Le serveur doit afficher un avertissement si un mot de passe est lu depuis l'environnement.
- Les erreurs d'authentification ne doivent pas inclure la valeur du mot de passe.
- Les fichiers de configuration doivent être exclus des commits quand ils contiennent des paramètres personnels.

## Exemples fictifs sans secrets

Commande indicative pour enregistrer un secret:

```bash
java -jar target/opcoach-mail-mcp.jar config set-password --profile default
```

Message attendu:

```text
Mot de passe enregistré dans le trousseau local pour le profil default.
```
