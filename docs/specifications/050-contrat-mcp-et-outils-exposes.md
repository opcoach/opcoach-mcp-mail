# Contrat MCP et outils exposés

## Objectif

Définir le contrat public MCP de la v1 et les outils que les clients IA peuvent découvrir.

## Décisions retenues

La v1 expose cinq tools:

- `sendEmail`
- `listMailboxes`
- `searchMessages`
- `getMessage`
- `getAttachment`

Les schemas MCP doivent rester compatibles avec Codex, Claude Desktop et les clients stricts:

- type racine `object`;
- pas de combinateur top-level;
- pas de schema ambigu;
- descriptions courtes et explicites.

## Comportement attendu

`sendEmail` envoie un message.

`listMailboxes` liste les dossiers disponibles.

`searchMessages` recherche des messages avec limites.

`getMessage` lit un message précis par UID.

`getAttachment` récupère explicitement une pièce jointe par identifiant.

## Points d'attention

- Aucun tool ne doit encourager l'exfiltration massive de mails.
- Les résultats de recherche renvoient des métadonnées et un extrait, pas le contenu complet.
- Les pièces jointes ne sont jamais récupérées automatiquement.
- Les paramètres optionnels doivent avoir des valeurs par défaut prudentes.
- Les erreurs doivent être structurées et actionnables.

## Exemples fictifs sans secrets

Exemple d'appel `searchMessages`:

```json
{
  "mailbox": "INBOX",
  "unreadOnly": true,
  "subjectContains": "formation",
  "limit": 5
}
```

Exemple de réponse:

```json
{
  "ok": true,
  "data": {
    "messages": [
      {
        "uid": "12345",
        "subject": "Formation MCP",
        "from": "client@example.com",
        "snippet": "Bonjour, je souhaite recevoir le programme..."
      }
    ]
  }
}
```
