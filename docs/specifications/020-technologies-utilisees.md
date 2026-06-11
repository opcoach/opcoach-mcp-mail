# Technologies utilisées

## Objectif

Décrire les choix techniques du serveur MCP mail et donner une base stable pour l'implémentation future.

## Décisions retenues

- Langage: Java 21.
- Build: Maven avec Maven Wrapper.
- Protocole MCP: MCP Java SDK officiel.
- Mail: Jakarta Mail API avec Eclipse Angus Mail comme implémentation.
- JSON: la pile fournie ou recommandée par le SDK MCP, avec Jackson si nécessaire.
- Logs: SLF4J, avec backend simple en mode local.
- Tests: JUnit 5 et faux serveurs SMTP/IMAP.

## Comportement attendu

Le projet doit se compiler et se lancer avec des commandes standards:

```bash
./mvnw test
./mvnw package
java -jar target/opcoach-mail-mcp.jar --stdio
```

Le projet doit aussi pouvoir être lancé en HTTP local:

```bash
java -jar target/opcoach-mail-mcp.jar --http --port 8095
```

## Points d'attention

- Les versions exactes des dépendances seront figées dans le `pom.xml` au moment de l'implémentation.
- Le serveur doit éviter les dépendances lourdes non nécessaires à un outil pédagogique.
- Le SDK MCP officiel est préféré à une implémentation manuelle du protocole.
- Jakarta Mail et Angus Mail sont préférés à un client IMAP/SMTP maison.
- Le code doit rester compréhensible pour des stagiaires qui découvrent MCP.

## Exemples fictifs sans secrets

Exemple de dépendances attendues:

```xml
<dependency>
    <groupId>jakarta.mail</groupId>
    <artifactId>jakarta.mail-api</artifactId>
    <version>2.1.x</version>
</dependency>
```

```xml
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
    <version>2.0.x</version>
</dependency>
```
