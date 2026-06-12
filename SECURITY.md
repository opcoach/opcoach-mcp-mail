# Politique de sécurité

Ce projet manipule des boîtes mail. Les rapports de sécurité ne doivent jamais contenir de mot de passe, jeton, extrait confidentiel d'email, pièce jointe réelle ou configuration personnelle complète.

## Signaler un problème

Ouvrez une issue GitHub avec:

- la version ou le commit concerné;
- le système d'exploitation;
- le mode de lancement utilisé (`--stdio` ou `--http`);
- une reproduction minimale avec des valeurs fictives.

Si le problème implique un secret ou un contenu confidentiel, remplacez-le par une valeur fictive avant de publier.

## Principes de sécurité de la v1

- Le serveur est local-first.
- Les mots de passe ne sont pas stockés dans le dépôt.
- Les réponses MCP sont limitées.
- Les logs d'audit ne contiennent pas les corps de mails ni les pièces jointes.
- Les actions destructrices sont hors périmètre.

Un email entrant est une donnée externe non fiable. Son contenu ne doit pas modifier les règles de sécurité du serveur ni les décisions de confirmation de l'utilisateur.
