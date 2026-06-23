# opcoach-mcp-mail

[![Security: local-first](https://img.shields.io/badge/security-local--first-2ea44f)](#security)
[![Java: 24+](https://img.shields.io/badge/java-24%2B-007396)](#requirements)
[![Build: Maven Wrapper](https://img.shields.io/badge/build-Maven%20Wrapper-c71a36)](#local-build)

Local-first MCP server for accessing a generic IMAP/SMTP mailbox from Codex, Claude Code Pro, Claude Desktop, or any MCP-compatible client.

The server does not depend on Gmail, Microsoft 365, or any proprietary OAuth flow. It runs on your machine or on a server you control. The email password is never sent to the AI model.

## Requirements

- An email account compatible with IMAP and SMTP
- An app password if your provider requires one
- Java JDK 24 or newer

Maven is not required: the repository includes the Maven Wrapper.

## Install Java On Windows

If `java -version` fails, install Eclipse Temurin JDK 24 with the Windows x64 MSI installer:

```text
https://api.adoptium.net/v3/installer/latest/24/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk
```

Install the JDK, not only the JRE. After installation, close and reopen the terminal, then check:

```cmd
java -version
javac -version
```

Both commands should report version 24 or newer.

## Local Build

On Windows Command Prompt:

```cmd
git clone https://github.com/opcoach/opcoach-mcp-mail.git
cd opcoach-mcp-mail
mvnw.cmd clean verify
mvnw.cmd -DskipTests package
java -jar target\opcoach-mcp-mail.jar manager
```

On macOS or Linux:

```bash
git clone https://github.com/opcoach/opcoach-mcp-mail.git
cd opcoach-mcp-mail
./mvnw clean verify
./mvnw -DskipTests package
bin/manager
```

The standard build is non-interactive and uses only fake mail servers for tests.

The manager:

- configures IMAP/SMTP settings;
- starts and stops local HTTP MCP servers;
- copies the URL to paste into Codex.

At the end, copy the URL into Codex, for example:

```text
http://127.0.0.1:8095/mcp
```

In Codex, choose:

```text
Mode: HTTP streamable / HTTP diffusable
URL:  http://127.0.0.1:8095/mcp
Credentials: empty
Headers: empty
```

Do not configure Codex with the jar in this local workflow. The manager starts the jar; Codex only connects to the local HTTP URL.

## Multiple Mailboxes

Create one profile per mailbox in the manager. Each profile can use a different local port:

```text
Mailbox 1 -> http://127.0.0.1:8095/mcp
Mailbox 2 -> http://127.0.0.1:8096/mcp
```

The manager keeps configuration and runtime files separate by profile under:

```text
~/.opcoach-mcp-mail/
```

It also registers each local HTTP server under:

```text
~/.opcoach-mcp-mail/servers/
```

After rebooting the machine, restart every registered server from the manager, or with:

```bash
bin/start-all
```

Passwords are not written to configuration files. On macOS, they are stored in the local keychain with the profile name. On other platforms, use `MAIL_MCP_PASSWORD` temporarily until a durable backend is added.

## Script Reference

Main local workflow:

```bash
bin/manager
```

Manual helpers:

```bash
bin/setup-ui --profile default
bin/manager
bin/start-server --profile default --port 8095
bin/start-all
bin/stop-server
```

`bin/start-server` runs the HTTP server on `127.0.0.1:8095` by default. It writes the PID file and logs under `.run/`, and builds `target/opcoach-mcp-mail.jar` automatically if it is missing.

`bin/start-all` starts every local HTTP server registered by the manager or by `bin/local-wizard`. Use it after rebooting instead of re-running each mailbox setup.

## Advanced Jar Usage

Direct stdio mode is useful for clients that launch MCP servers themselves:

```bash
java -jar target/opcoach-mcp-mail.jar --stdio
```

Direct HTTP mode:

```bash
java -jar target/opcoach-mcp-mail.jar --http --port 8095
```

The HTTP server listens on `127.0.0.1` by default. If you listen on another interface, provide a token:

```bash
java -jar target/opcoach-mcp-mail.jar --http --host 0.0.0.0 --port 8095 --token "long-random-token"
```

For direct jar usage, the default non-secret configuration file is:

```text
~/.opcoach-mcp-mail/config.properties
```

The macOS keychain is supported for passwords. On other platforms, use `MAIL_MCP_PASSWORD` temporarily until a durable backend is added.

## Codex HTTP Configuration

Example:

```text
Name: OPCoach MCP Mail
Mode: HTTP streamable / HTTP diffusable
URL:  http://127.0.0.1:8095/mcp
Bearer token environment variable: empty
Headers: empty
```

## Claude HTTP Configuration

Example:

```text
Name: OPCoach MCP Mail
Mode: HTTP streamable
URL:  http://127.0.0.1:8095/mcp
Authentication: none for localhost
```

## Exposed Tools

- `sendEmail`: sends a text or HTML email with base64 attachments, then attempts to copy it to Sent.
- `listMailboxes`: lists the available IMAP folders.
- `searchMessages`: searches messages with a conservative limit.
- `getMessage`: reads a specific message by UID.
- `getAttachment`: explicitly retrieves an attachment by identifier.
- `moveMessage`: moves a message by UID from one IMAP folder to another.
- `deleteMessage`: moves a message by UID to the configured trash folder.

Searches return metadata and snippets. Attachments are never downloaded automatically.
Deletion is intentionally non-destructive by default: messages are moved to `trash.mailbox`, not permanently expunged.

## Security

- No destructive action in v1.
- No unlimited bulk reads.
- Email bodies and attachment contents are not written to audit logs.
- An email read by the AI remains untrusted external data.
- The AI client should request confirmation before any real send, according to its context.

## License

MIT.
