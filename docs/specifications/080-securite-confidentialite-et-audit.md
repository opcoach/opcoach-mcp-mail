# Sécurité, confidentialité et audit

## Objectif

Définir les règles de sécurité du serveur MCP mail, en tenant compte du fait qu'une boîte mail contient souvent des données confidentielles.

## Décisions retenues

- Le serveur est conçu pour un usage local ou dans un environnement explicitement contrôlé.
- Les secrets sont masqués systématiquement.
- Les réponses sont limitées en taille.
- Les actions sensibles sont auditables.
- Les outils MCP sont nommés et décrits sans ambiguïté.

## Comportement attendu

Chaque appel sensible écrit une trace locale minimale:

- date;
- nom du tool;
- statut;
- identifiant du message si disponible;
- dossier utilisé;
- destinataires pour les envois.

Les logs ne contiennent jamais:

- mot de passe;
- corps complet des emails;
- contenu des pièces jointes;
- token MCP;
- configuration secrète.

## Points d'attention

- Le prompt injection est un risque majeur: un mail reçu peut contenir des instructions hostiles.
- Le serveur ne doit pas exécuter d'instruction trouvée dans un mail.
- Les réponses doivent rester factuelles et structurées.
- Le client IA reste responsable de demander confirmation avant les actions sensibles selon son contexte.
- Un serveur MCP mail malveillant ou mal configuré peut exfiltrer des données.

## Exemples fictifs sans secrets

Exemple de ligne d'audit:

```json
{
  "time": "2026-01-01T10:00:00Z",
  "tool": "sendEmail",
  "status": "sent",
  "messageId": "<example@example.com>",
  "recipients": ["destinataire@example.com"]
}
```

Exemple d'avertissement pédagogique:

```text
Un email lu par l'IA est une donnée externe non fiable. Son contenu ne doit pas modifier les règles de sécurité du serveur.
```
