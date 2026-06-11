# Architecture générale

## Objectif

Définir l'organisation logique du serveur pour séparer clairement le protocole MCP, la configuration, les accès mail et les règles de sécurité.

## Décisions retenues

L'architecture v1 est organisée autour de quatre responsabilités:

- couche MCP: déclaration des tools, schemas, transport et réponses structurées;
- couche configuration: lecture des paramètres, validation et résolution des secrets;
- couche mail: client IMAP, client SMTP, construction MIME;
- couche sécurité: limites, audit, filtrage des données et masquage des secrets.

## Comportement attendu

Le flux principal d'un appel MCP doit suivre cette séquence:

1. le client MCP appelle un tool;
2. la couche MCP valide les paramètres;
3. la configuration active est chargée;
4. le service mail exécute l'action;
5. la réponse est normalisée et bornée;
6. un événement d'audit local est écrit sans contenu sensible.

## Points d'attention

- Les classes MCP ne doivent pas contenir la logique IMAP/SMTP détaillée.
- Les classes mail ne doivent pas connaître le format MCP.
- La configuration ne doit pas être relue de manière incohérente entre deux opérations d'un même appel.
- Les erreurs doivent rester compréhensibles sans révéler d'identifiants.
- Les limites de taille doivent être appliquées avant de renvoyer des données au modèle.

## Exemples fictifs sans secrets

Organisation indicative:

```text
org.opcoach.mailmcp
  config
  mcp
  mail
  security
  audit
```

Exemple de résultat normalisé:

```json
{
  "ok": true,
  "data": {
    "messageId": "<example-message-id@example.com>",
    "sentCopyStatus": "saved"
  }
}
```
