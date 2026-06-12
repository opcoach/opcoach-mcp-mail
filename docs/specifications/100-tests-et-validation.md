# Tests et validation

## Objectif

Définir la stratégie de test qui garantit que le serveur MCP mail est fiable, compatible et sûr pour un usage en formation.

## Décisions retenues

- Les tests automatisés sont obligatoires dès la v1.
- Les tests mail utilisent de faux serveurs SMTP et IMAP.
- Les schemas MCP sont testés contre les restrictions connues des clients stricts.
- Les logs et erreurs sont testés pour éviter les fuites de secrets.
- Les limites de taille sont testées explicitement.
- `./mvnw clean verify` est la commande de validation publique du projet.
- Les profils interactifs `-Psetup` et `-Psetup-ui` sont testés séparément et ne s'activent jamais par défaut.

## Comportement attendu

La commande suivante doit valider le projet:

```bash
./mvnw clean verify
```

Les tests couvrent:

- envoi texte;
- envoi HTML;
- pièce jointe base64;
- copie dans Envoyés;
- recherche IMAP bornée;
- lecture par UID;
- récupération explicite de pièce jointe;
- erreur d'authentification;
- absence de secret dans les résultats;
- absence de prompt interactif dans le build standard;
- validation des messages d'aide quand la configuration locale est absente.

Les tests d'acceptation locaux peuvent valider les assistants de configuration:

```bash
./mvnw -Psetup verify
./mvnw -Psetup-ui verify
```

Ces profils doivent être exclus des workflows CI par défaut.

## Points d'attention

- Les tests ne doivent pas dépendre d'une vraie boîte mail.
- Les tests ne doivent pas envoyer d'email réel.
- Les fixtures ne doivent pas contenir de données personnelles.
- Les tests doivent rester rapides pour un atelier.
- Les résultats MCP doivent être stables pour faciliter les démonstrations.
- Un test doit échouer si le build standard attend une saisie utilisateur.
- Les tests de mini UI doivent vérifier le bind `127.0.0.1`, le jeton temporaire et l'arrêt automatique.

## Exemples fictifs sans secrets

Exemple de scénario d'acceptation:

```text
Étant donné un serveur SMTP/IMAP de test,
quand sendEmail est appelé avec htmlBody,
alors le message est livré par SMTP,
et une copie MIME est ajoutée au dossier Envoyés.
```

Exemple de vérification sécurité:

```text
Un mot de passe fictif configuré pour le test ne doit apparaître dans aucun log ni aucune réponse MCP.
```

Exemple de vérification CI:

```text
Étant donné un dépôt fraîchement cloné,
quand ./mvnw clean verify est exécuté sans configuration mail,
alors le build se termine sans prompt,
et les tests utilisent uniquement de faux serveurs mail.
```
