# Roadmap et évolutions

## Objectif

Définir les évolutions possibles après la v1, sans compliquer le premier livrable.

## Décisions retenues

- La v1 reste centrée sur lire, rechercher, envoyer et récupérer explicitement une pièce jointe.
- La v1 inclut un assistant de configuration terminal pour réduire la friction après clonage.
- La mini UI locale de configuration est acceptable en v1 si elle reste strictement locale et protégée.
- Les fonctions de gestion complète de boîte mail sont repoussées.
- Les intégrations OAuth sont repoussées pour éviter de détourner le sujet pédagogique initial.
- Le durcissement multi-plateforme des secrets sera progressif.
- Les évolutions doivent rester compatibles avec les schemas MCP existants.

## Comportement attendu

Évolutions possibles:

- marquer un message comme lu ou non lu;
- déplacer un message vers un autre dossier;
- supprimer ou archiver un message avec confirmation stricte;
- gérer plusieurs profils mail;
- ajouter OAuth pour certains fournisseurs;
- fournir un conteneur de démonstration avec serveur mail de test;
- publier des exemples de configuration pour plusieurs clients MCP;
- générer automatiquement un extrait de configuration Codex ou Claude après l'assistant setup;
- publier des releases GitHub avec jar vérifiable.

## Points d'attention

- Les actions destructrices doivent rester hors v1.
- Les évolutions ne doivent pas réduire la confidentialité.
- La compatibilité Codex et Claude doit être vérifiée à chaque changement de schema.
- Les migrations de configuration doivent être documentées.
- Les exemples publics ne doivent jamais inclure de secrets réels.
- Une mini UI durable d'administration n'est pas prévue en v1; seule une UI éphémère de configuration est acceptable.

## Exemples fictifs sans secrets

Exemple de future commande multi-profils:

```bash
java -jar target/opcoach-mcp-mail.jar --stdio --profile formation
```

Exemple de future action sensible:

```json
{
  "tool": "moveMessage",
  "mailbox": "INBOX",
  "uid": "12345",
  "targetMailbox": "Archives"
}
```
