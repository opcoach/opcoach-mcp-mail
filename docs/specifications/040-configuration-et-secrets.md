# Configuration et secrets

## Objectif

Définir comment le serveur reçoit sa configuration mail et comment il protège les mots de passe.

## Décisions retenues

- La configuration non secrète est stockée dans un fichier local.
- Les secrets sont fournis par variable d'environnement pour les ateliers courts.
- Les secrets sont stockés dans le trousseau local pour les usages durables.
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
sent.mailbox=INBOX.Sent
```

Le mot de passe peut être fourni temporairement:

`replyTo.address` est optionnel. Si l'utilisateur ne renseigne rien, la propriété peut être absente et aucun header `Reply-To` n'est ajouté aux messages envoyés.

```bash
export MAIL_MCP_PASSWORD="mot-de-passe-fictif"
```

Pour un usage durable, le serveur doit permettre de l'enregistrer dans le keychain local.

L'assistant de configuration demande uniquement les paramètres nécessaires:

- hôte, port et sécurité IMAP;
- hôte, port et sécurité SMTP;
- identifiant mail;
- adresse et nom d'expéditeur;
- adresse Reply-To optionnelle;
- dossier des envoyés;
- mot de passe ou mot de passe applicatif.

Le mot de passe est saisi en mode masqué dans le terminal ou dans la mini UI locale, puis enregistré dans le trousseau local. Il n'est jamais écrit dans le fichier de configuration.

La mini UI de configuration, si elle est activée, doit:

- écouter uniquement sur `127.0.0.1`;
- utiliser un port libre choisi localement;
- exiger un jeton aléatoire affiché dans le terminal;
- refuser toute requête sans jeton;
- ne rien stocker dans le navigateur, ni `localStorage`, ni cookie persistant;
- s'arrêter automatiquement après validation ou expiration.

## Points d'attention

- Les exemples utilisent uniquement des valeurs fictives.
- En production locale, le keychain est préféré aux variables d'environnement.
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
Mot de passe enregistré dans le trousseau local pour le profil default.
```
