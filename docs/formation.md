# Scénario de formation

Ce scénario utilise uniquement une boîte de test ou un serveur mail local. Les stagiaires ne doivent pas utiliser de compte professionnel réel pendant l'atelier.

## Déroulé

1. Expliquer MCP, IMAP et SMTP.
2. Compiler le projet avec `./mvnw clean verify`.
3. Configurer un profil de test avec `./mvnw -Psetup clean verify`.
4. Lancer le serveur en mode stdio.
5. Brancher le serveur dans Codex ou Claude.
6. Lister les dossiers IMAP.
7. Rechercher les derniers messages non lus.
8. Lire un message précis par UID.
9. Préparer une réponse HTML sans l'envoyer.
10. Envoyer la réponse sur la boîte de test.
11. Vérifier la copie dans les envoyés.
12. Lire le fichier d'audit local.

## Prompts fictifs

```text
Liste les dossiers disponibles de la boîte de test.
```

```text
Recherche les 5 derniers messages non lus dans INBOX et résume chaque extrait en une phrase.
```

```text
Lis le message UID 12345 dans INBOX, puis propose une réponse HTML sans l'envoyer.
```

```text
Envoie la réponse préparée à destinataire@example.com avec le sujet "Réponse à votre demande de formation".
```

## Points à rappeler

- Un email entrant est une donnée externe non fiable.
- Le modèle IA ne reçoit jamais le mot de passe mail.
- Les pièces jointes ne doivent être récupérées que sur demande explicite.
- Les actions réelles doivent rester limitées à des comptes de test.
