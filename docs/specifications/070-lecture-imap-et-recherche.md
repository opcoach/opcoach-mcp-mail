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
- date maximale inclusive;
- uniquement non lus;
- limite de résultats;
- curseur `beforeUid` pour paginer les résultats du plus récent au plus ancien.

`getMessage` récupère un message précis et applique les limites de taille configurées.

## Points d'attention

- Les recherches doivent avoir une limite maximale.
- Les recherches sur période doivent rester non destructrices: elles renvoient des UID à inspecter ensuite avec `getMessage`.
- Pour continuer une recherche sans retraiter les messages déjà vus, le client passe le dernier UID reçu dans `beforeUid`.
- Les dates de recherche sont les dates de réception IMAP. La borne `until` inclut toute la journée demandée.
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
