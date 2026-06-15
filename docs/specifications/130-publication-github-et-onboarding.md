# Publication GitHub et onboarding

## Objectif

Définir les conditions nécessaires pour publier `opcoach-mcp-mail` en open source sur GitHub et rendre sa première utilisation très simple après clonage.

## Décisions retenues

- Le dépôt public doit contenir un `README.md` clair dès la première publication.
- Le dépôt doit inclure Maven Wrapper pour éviter une installation Maven préalable.
- Le chemin principal documenté est: cloner, vérifier, configurer, lancer.
- La commande standard `./mvnw clean verify` reste non interactive.
- Les assistants de configuration sont activés par profils Maven explicites.
- Une licence open source doit être choisie avant publication publique.
- Un workflow GitHub Actions exécute `./mvnw clean verify` sur chaque pull request.
- Aucune configuration personnelle, aucun secret et aucun exemple réel de boîte mail ne sont publiés.

## Comportement attendu

Le `README.md` public doit permettre cette première expérience:

```bash
git clone https://github.com/opcoach/opcoach-mcp-mail.git
cd opcoach-mcp-mail
./mvnw clean verify
./mvnw -Psetup clean verify
java -jar target/opcoach-mcp-mail.jar --stdio
```

Le dépôt GitHub doit contenir au minimum:

- `README.md`;
- `LICENSE`;
- `.gitignore`;
- `.gitattributes`;
- `SECURITY.md`;
- Maven Wrapper;
- workflow GitHub Actions de validation;
- documentation de configuration Codex et Claude.

La publication GitHub ne doit pas exiger de compte Gmail, OAuth propriétaire ou service cloud tiers.

## Points d'attention

- Le choix de licence est une décision de projet; Apache-2.0 ou MIT sont des options simples, mais le choix doit être explicite.
- Les issues GitHub ne doivent jamais demander aux utilisateurs de publier leurs identifiants ou extraits d'emails confidentiels.
- Les captures d'écran de formation doivent utiliser des boîtes fictives.
- Le workflow CI ne doit jamais accéder à une vraie boîte mail.
- Les releases ne doivent contenir que le code, les artefacts compilés et la documentation publique.
- Le dépôt doit éviter les dépendances inutiles qui compliquent l'audit de sécurité.

## Exemples fictifs sans secrets

Exemple de commande de validation publique:

```bash
./mvnw clean verify
```

Exemple de résumé GitHub Actions:

```text
Validation réussie: tests unitaires, faux serveurs SMTP/IMAP, schemas MCP, scan anti-fuite.
```

Exemple de phrase de README:

```text
Ce serveur MCP se connecte à une boîte mail IMAP/SMTP générique en restant local-first; aucun mot de passe n'est transmis au modèle IA.
```
