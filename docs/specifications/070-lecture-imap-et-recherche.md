# Lecture IMAP et recherche

## Objectif

Définir la manière dont le serveur lit les messages, recherche dans les dossiers et limite les données retournées au modèle.

## Décisions retenues

- La lecture se fait via IMAP authentifié.
- Les messages sont référencés par mailbox et UID.
- La recherche renvoie des résultats limités.
- Le corps complet d'un message n'est renvoyé que par `getMessage`.
- Les pièces jointes sont listées mais non téléchargées automatiquement.

## Comportement attendu

`searchMessages` accepte des filtres comme:

- dossier;
- expéditeur;
- destinataire;
- sujet contenant un texte;
- date minimale;
- uniquement non lus;
- limite de résultats.

`getMessage` récupère un message précis et applique les limites de taille configurées.

## Points d'attention

- Les recherches doivent avoir une limite maximale.
- Les snippets ne doivent pas contenir plus de texte que nécessaire.
- Les corps HTML doivent être bornés.
- Les pièces jointes volumineuses doivent rester accessibles uniquement par demande explicite.
- Les UID sont préférés aux numéros de séquence IMAP, car ils sont plus stables.

## Exemples fictifs sans secrets

Exemple d'appel `getMessage`:

```json
{
  "mailbox": "INBOX",
  "uid": "12345",
  "includeHtml": false,
  "maxBodyBytes": 12000
}
```

Exemple de résultat:

```json
{
  "ok": true,
  "data": {
    "uid": "12345",
    "subject": "Demande de devis",
    "textBody": "Bonjour, pouvez-vous me transmettre...",
    "attachments": [
      {
        "attachmentId": "part-2",
        "filename": "programme.pdf",
        "contentType": "application/pdf",
        "size": 102400
      }
    ]
  }
}
```
