# Configuration et secrets

## Objectif

Définir comment le serveur reçoit sa configuration mail et comment il protège les mots de passe.

## Décisions retenues

- La configuration non secrète est stockée dans un fichier local.
- Les secrets sont fournis par variable d'environnement pour les ateliers courts.
- Les secrets sont stockés dans le trousseau local sur macOS pour les usages durables.
- Les secrets sont stockés dans un vault local chiffré sur Linux pour les usages durables.
- Les secrets sont chiffrés avec DPAPI pour l'utilisateur Windows courant pour les usages durables.
- Un assistant de configuration local guide l'utilisateur lors du premier lancement.
- Une mini UI locale peut être proposée, mais uniquement sur `127.0.0.1` avec un jeton temporaire.
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
replyTo.address=reponses@example.com
incoming.mailboxes=INBOX
sent.mailbox=INBOX.Sent
trash.mailbox=INBOX.Trash
```

Le mot de passe peut être fourni temporairement:

`replyTo.address` est optionnel. Si l'utilisateur ne renseigne rien, la propriété peut être absente et aucun header `Reply-To` n'est ajouté aux messages envoyés.

`incoming.mailboxes` contient un ou plusieurs noms complets de dossiers IMAP séparés par des virgules. La valeur par défaut est `INBOX` pour préserver la compatibilité avec les profils existants. Les noms doivent correspondre exactement à ceux renvoyés par `listMailboxes`, par exemple:

```properties
incoming.mailboxes=INBOX.error_opcoach,INBOX.warning_opcoach
```

Un alias de distribution comme `error+error_opcoach` n'est pas un nom de dossier IMAP et ne doit être utilisé que si le serveur IMAP expose réellement un dossier portant ce nom.

```bash
export MAIL_MCP_PASSWORD="mot-de-passe-fictif"
```

Pour un usage durable, le serveur permet de l'enregistrer dans le trousseau local macOS, dans le vault chiffré Linux ou avec DPAPI sous Windows.

Sous Windows, les secrets chiffrés sont stockés dans `%USERPROFILE%\.opcoach-mcp-mail\windows-secrets\`. La protection DPAPI utilise la portée `CurrentUser`: seul le même utilisateur Windows, sur la même machine, peut les déchiffrer. Le mot de passe est transmis à PowerShell par l'entrée standard et n'apparaît pas dans la ligne de commande.

L'assistant de configuration demande uniquement les paramètres nécessaires:

- hôte, port et sécurité IMAP;
- hôte, port et sécurité SMTP;
- identifiant mail;
- adresse et nom d'expéditeur;
- adresse Reply-To optionnelle;
- un ou plusieurs dossiers entrants;
- dossier des envoyés;
- dossier de corbeille;
- mot de passe ou mot de passe applicatif.

Le mot de passe est saisi en mode masqué dans le terminal ou dans la mini UI locale, puis enregistré dans le stockage secret local disponible. Il n'est jamais écrit dans le fichier de configuration.

Sur Linux, le vault chiffré est protégé par un mot de passe maître. La mini UI locale peut demander ce mot de passe maître en plus du mot de passe mail. Le serveur peut aussi recevoir ce mot de passe maître au démarrage via l'entrée standard, afin d'éviter de le placer sur la ligne de commande.

La mini UI de configuration, si elle est activée, doit:

- écouter uniquement sur `127.0.0.1`;
- utiliser un port libre choisi localement;
- exiger un jeton aléatoire affiché dans le terminal;
- refuser toute requête sans jeton;
- ne rien stocker dans le navigateur, ni `localStorage`, ni cookie persistant;
- s'arrêter automatiquement après validation ou expiration.

## Points d'attention

- Les exemples utilisent uniquement des valeurs fictives.
- En production locale, le trousseau macOS ou le vault chiffré Linux est préféré aux variables d'environnement.
- Le serveur doit afficher un avertissement si un mot de passe est lu depuis l'environnement.
- Les erreurs d'authentification ne doivent pas inclure la valeur du mot de passe.
- Les fichiers de configuration doivent être exclus des commits quand ils contiennent des paramètres personnels.
- La mini UI ne doit jamais écouter sur une interface réseau publique.
- Le jeton temporaire de configuration ne doit pas être renvoyé dans les résultats MCP.

## Exemples fictifs sans secrets

Commande indicative pour enregistrer un secret:

```bash
java -jar target/opcoach-mcp-mail.jar config set-password --profile default
```

Commande indicative pour lancer l'assistant terminal:

```bash
./mvnw -Psetup clean verify
```

Commande indicative pour lancer la mini UI locale:

```bash
./mvnw -Psetup-ui clean verify
```

Message attendu:

```text
Mot de passe enregistré dans le stockage secret local pour le profil default.
```
