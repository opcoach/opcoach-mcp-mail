# Scénario de formation

## Objectif

Décrire un déroulé pédagogique pour utiliser le serveur MCP mail dans une formation sur l'automatisation par IA.

## Décisions retenues

- Le scénario utilise une boîte mail de test ou un serveur mail local.
- Les stagiaires ne manipulent pas de vrais secrets professionnels.
- Le scénario illustre à la fois la puissance de l'automatisation et ses risques.
- L'atelier montre la différence entre intégration propriétaire et protocole ouvert.
- Les exercices sont progressifs.

## Comportement attendu

Déroulé recommandé:

1. expliquer MCP, IMAP et SMTP;
2. lancer le serveur MCP en mode stdio;
3. brancher le serveur dans un client compatible;
4. lister les dossiers;
5. rechercher des messages non lus;
6. lire un message précis;
7. générer une réponse HTML;
8. envoyer la réponse;
9. vérifier la copie dans les envoyés;
10. analyser les logs d'audit.

## Points d'attention

- L'enseignant doit insister sur la confidentialité des emails.
- Les stagiaires doivent comprendre qu'un email entrant est une donnée non fiable.
- L'atelier doit éviter les comptes Gmail personnels.
- Les prompts doivent rester explicites sur les destinataires et les actions d'envoi.
- Les actions réelles doivent être effectuées uniquement sur des comptes de test.

## Exemples fictifs sans secrets

Prompt d'exercice:

```text
Recherche les derniers messages non lus dans INBOX, résume-les en une phrase chacun, puis propose une réponse HTML pour le plus récent sans l'envoyer.
```

Prompt d'envoi contrôlé:

```text
Envoie la réponse préparée à destinataire@example.com avec le sujet "Réponse à votre demande de formation".
```
