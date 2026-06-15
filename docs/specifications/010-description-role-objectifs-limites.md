# Description, rôle, objectifs et limites

## Objectif

Définir le périmètre du projet `opcoach-mcp-mail`: un serveur MCP open source permettant à un assistant IA d'utiliser une boîte mail professionnelle via les protocoles standards IMAP et SMTP.

Le projet doit servir à la fois d'outil réel et de support pédagogique pour montrer comment connecter une IA à un système externe sans passer par Gmail ni par une intégration propriétaire.

## Décisions retenues

- Le serveur est local-first: il se lance sur le poste de l'utilisateur ou dans un environnement maîtrisé.
- Les protocoles cibles sont IMAP pour la lecture et SMTP pour l'envoi.
- Le serveur expose des outils MCP clairs, limités et auditables.
- La v1 privilégie les usages d'automatisation courants: rechercher, lire, envoyer et récupérer explicitement une pièce jointe.
- Le projet est indépendant d'OPTimumAI et doit pouvoir être cloné, compilé et lancé simplement.

## Comportement attendu

Un utilisateur doit pouvoir configurer une boîte mail professionnelle, brancher le serveur dans Codex, Claude Desktop ou un autre client MCP, puis demander des actions comme:

- rechercher les derniers mails non lus d'un dossier;
- résumer un message précis;
- générer une réponse HTML;
- envoyer un message avec une copie enregistrée dans les envoyés;
- récupérer une pièce jointe explicitement demandée.

## Points d'attention

- Le serveur ne doit jamais exposer l'ensemble d'une boîte mail sans limite.
- Les réponses MCP doivent être bornées en taille.
- Les secrets ne doivent jamais apparaître dans les fichiers, les logs, les exceptions ou les résultats.
- Le serveur doit être explicite sur les actions destructrices ou sensibles.
- La v1 ne doit pas devenir un client mail complet.

## Exemples fictifs sans secrets

Exemple de demande utilisateur:

```text
Recherche les 5 derniers mails non lus de formation@example.com et prépare une réponse, sans l'envoyer.
```

Exemple de positionnement formation:

```text
Objectif de l'atelier: connecter une IA à une boîte mail professionnelle avec MCP, IMAP et SMTP, tout en gardant les identifiants hors du modèle.
```
