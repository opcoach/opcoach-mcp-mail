# Envoi SMTP et MIME

## Objectif

Définir le comportement d'envoi des emails, y compris le support texte, HTML, pièces jointes et copie dans les envoyés.

## Décisions retenues

- L'envoi se fait via SMTP authentifié.
- Le message est construit au format MIME.
- `textBody` et `htmlBody` sont supportés.
- Les pièces jointes sont transmises par contenu base64.
- Après envoi SMTP réussi, le serveur tente d'ajouter le message MIME au dossier Envoyés via IMAP `APPEND`.

## Comportement attendu

Un appel `sendEmail` peut envoyer:

- un message texte simple;
- un message HTML avec alternative texte;
- un message avec pièces jointes;
- un message avec `cc` et `bcc`.

Le résultat indique:

- succès ou échec SMTP;
- identifiant du message;
- liste des destinataires acceptés;
- statut de la copie dans Envoyés;
- dossier IMAP utilisé pour la copie.

## Points d'attention

- `textBody` ou `htmlBody` doit être fourni.
- Si `htmlBody` est fourni sans `textBody`, une alternative texte simple peut être générée.
- Les pièces jointes doivent respecter une taille maximale.
- Le serveur ne lit pas de chemin local arbitraire pour joindre un fichier.
- Un échec de copie IMAP ne doit pas masquer un succès SMTP.

## Exemples fictifs sans secrets

Exemple d'appel:

```json
{
  "to": "destinataire@example.com",
  "subject": "Résultat de traitement",
  "textBody": "Bonjour, le traitement est terminé.",
  "htmlBody": "<h1>Traitement terminé</h1><p>Le résultat est disponible.</p>"
}
```

Exemple de réponse:

```json
{
  "ok": true,
  "data": {
    "status": "sent",
    "html": true,
    "sentCopyStatus": "saved",
    "sentCopyMailbox": "INBOX.Sent"
  }
}
```
