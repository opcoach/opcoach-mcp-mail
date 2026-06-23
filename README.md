# opcoach-mcp-mail

Local-first MCP server for accessing a generic IMAP/SMTP mailbox from Codex, Claude Code Pro, Claude Desktop, or any MCP-compatible client.

The server does not depend on Gmail, Microsoft 365, or any proprietary OAuth flow. It runs on your machine or on a server you control. The email password is never sent to the AI model.

## Requirements

- An email account compatible with IMAP and SMTP
- An app password if your provider requires one
- Internet access on first run if Java 24 is not already installed

Maven is not required: the repository includes the Maven Wrapper. For the guided local workflow, Java is not required upfront either. The scripts use an existing Java 24+ runtime when available, or download a local Eclipse Temurin JDK into `.runtime/`.

Manual Maven commands still require Java 24+ unless you run them through the provided scripts.

## Quick Local Setup

For local training or non-technical users, start here. Do not run Maven manually for this workflow.

Preferred desktop manager:

```text
Windows: double-click OPCoach MCP Mail.cmd
macOS:   double-click OPCoach MCP Mail.command
```

On Windows, `OPCoach MCP Mail.cmd` shows progress during first-time setup if Java or Maven must be downloaded. After setup, `OPCoach MCP Mail.vbs` starts the same manager without a console window.

The manager opens a Java UI to configure mailboxes, start/stop local MCP servers, and copy the MCP URL for Codex.

On macOS or Linux:

```bash
bin/manager
```

On Windows, if double-click is blocked, open PowerShell in the cloned repository and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\bin\manager.ps1
```

Do not run `mvnw.cmd` for this local workflow on Windows. The manager helper downloads Java and Maven locally when needed, and does not require a preconfigured `JAVA_HOME`.

The manager:

- uses Java 24+ if installed, or downloads a local JDK automatically;
- builds the server with the configured Maven version if needed;
- configures IMAP/SMTP settings and stores the mailbox password locally;
- starts and stops local HTTP MCP servers;
- copies the URL to paste into Codex.

At the end, copy the printed URL into Codex, for example:

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

## Developer Build

For development, with Java 24+ already available:

```bash
git clone https://github.com/opcoach/opcoach-mcp-mail.git
cd opcoach-mcp-mail
./mvnw clean verify
```

The standard build is non-interactive and uses only fake mail servers for tests.

## Multiple Mailboxes

Run the wizard once per mailbox. Each run can use a different profile and a different port:

```text
Mailbox 1 -> http://127.0.0.1:8095/mcp
Mailbox 2 -> http://127.0.0.1:8096/mcp
```

The wizard keeps configuration and runtime files separate by profile under:

```text
~/.opcoach-mcp-mail/
```

It also registers each local HTTP server under:

```text
~/.opcoach-mcp-mail/servers/
```

After rebooting the machine, restart every registered server with:

```bash
bin/start-all
```

On Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\bin\start-all.ps1
```

Passwords are not written to configuration files. On macOS, they are stored in the local keychain with the profile name. On Windows, the PowerShell scripts ask for the mailbox password when starting the server and pass it only to that server process environment.

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

`bin/setup-ui`, `bin/start-server`, and `bin/local-wizard` use Java 24+ from the system when available. If Java is missing or older, they download a local Eclipse Temurin JDK into `.runtime/`.

Windows helpers:

```powershell
.\bin\manager.ps1
.\bin\rebuild.ps1
.\bin\local-wizard.ps1
.\bin\start-all.ps1
```

These scripts also use Java 24+ from the system when available. If Java is missing or older, they download a local Eclipse Temurin JDK into `.runtime\`.

The first Windows build still needs a JDK because the project is compiled locally. After that build, the wizard can replace the full local JDK with a smaller `jlink` runtime and remove the JDK copy. To do it later without re-running mailbox setup:

```powershell
.\bin\compact-runtime.ps1
```

`bin/start-server` runs the HTTP server on `127.0.0.1:8095` by default. It writes the PID file and logs under `.run/`, and builds `target/opcoach-mcp-mail.jar` automatically if it is missing.

`bin/start-all` starts every local HTTP server registered by `bin/local-wizard`. Use it after rebooting instead of re-running each mailbox setup.

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
