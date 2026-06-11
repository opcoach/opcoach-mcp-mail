# Tests et validation

## Objectif

Définir la stratégie de test qui garantit que le serveur MCP mail est fiable, compatible et sûr pour un usage en formation.

## Décisions retenues

- Les tests automatisés sont obligatoires dès la v1.
- Les tests mail utilisent de faux serveurs SMTP et IMAP.
- Les schemas MCP sont testés contre les restrictions connues des clients stricts.
- Les logs et erreurs sont testés pour éviter les fuites de secrets.
- Les limites de taille sont testées explicitement.

## Comportement attendu

La commande suivante doit valider le projet:

```bash
./mvnw test
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
- absence de secret dans les résultats.

## Points d'attention

- Les tests ne doivent pas dépendre d'une vraie boîte mail.
- Les tests ne doivent pas envoyer d'email réel.
- Les fixtures ne doivent pas contenir de données personnelles.
- Les tests doivent rester rapides pour un atelier.
- Les résultats MCP doivent être stables pour faciliter les démonstrations.

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
