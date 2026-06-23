# opcoach-mcp-mail

[![Security: local-first](https://img.shields.io/badge/security-local--first-2ea44f)](#security)
[![Windows: no PowerShell](https://img.shields.io/badge/windows-no%20PowerShell-0078d4)](#windows-download)
[![Release: SHA--256](https://img.shields.io/badge/release-SHA--256%20checksum-6f42c1)](#verify-the-windows-download)

Local-first MCP server for accessing a generic IMAP/SMTP mailbox from Codex, Claude Code Pro, Claude Desktop, or any MCP-compatible client.

The server does not depend on Gmail, Microsoft 365, or any proprietary OAuth flow. It runs on your machine or on a server you control. The email password is never sent to the AI model.

## Requirements

- An email account compatible with IMAP and SMTP
- An app password if your provider requires one
- Windows users: no Java, Maven, Git, Git Bash, or PowerShell command is required when using the release ZIP
- macOS/Linux developers: Java 24+ is required for source builds

Maven is not required for developers: the repository includes the Maven Wrapper.

## Windows Download

For training or non-technical Windows users, do not clone the repository and do not run Maven.

1. Open the [latest GitHub release](https://github.com/opcoach/opcoach-mcp-mail/releases/latest).
2. Download `OPCoach-MCP-Mail-...-windows-x64.zip`.
3. Extract the ZIP archive.
4. Double-click `OPCoach MCP Mail.exe`.
5. Configure the mailbox, click `Start`, then copy the local MCP URL into Codex.

The ZIP contains a Windows Java runtime and the MCP Mail manager. The `.exe` is a small launcher; it does not install a service and it does not require administrator rights.

### Verify The Windows Download

Each release publishes a `.sha256` file next to the ZIP. On Windows, optional verification can be done from Command Prompt:

```cmd
certutil -hashfile OPCoach-MCP-Mail-...-windows-x64.zip SHA256
```

Compare the printed SHA-256 with the value in the `.sha256` file from the same GitHub release.

The Windows executable is not Authenticode-signed yet, so Windows SmartScreen may show a warning on first launch. Prefer downloads from GitHub Releases, verify the checksum for training deployments, and do not download executables from third-party mirrors.

## macOS And Linux

```bash
bin/manager
```

The manager opens a Java UI to configure mailboxes, start/stop local MCP servers, and copy the MCP URL for Codex.

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

## Developer Build

For development, with Java 24+ already available:

```bash
git clone https://github.com/opcoach/opcoach-mcp-mail.git
cd opcoach-mcp-mail
./mvnw clean verify
```

The standard build is non-interactive and uses only fake mail servers for tests.

To build the Windows distribution ZIP from macOS or Linux:

```bash
bin/build-release
```

Equivalent Maven command:

```bash
./mvnw -Pwindows-dist -DskipTests clean package
```

The Windows package is created under:

```text
target/OPCoach-MCP-Mail-...-windows-x64.zip
target/OPCoach-MCP-Mail-...-windows-x64.zip.sha256
```

The manager:

- configures IMAP/SMTP settings;
- starts and stops local HTTP MCP servers;
- copies the URL to paste into Codex.

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

After rebooting the machine, restart every registered server with:

```bash
bin/start-all
```

On Windows, reopen `OPCoach MCP Mail.exe`, select each profile, enter the mailbox password, and click `Start`.

Passwords are not written to configuration files. On macOS, they are stored in the local keychain with the profile name. In the Windows release package, passwords are not stored persistently; they are passed only to the local server process when the user starts a profile.

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
