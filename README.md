# opcoach-mcp-mail

Local-first MCP server for accessing a generic IMAP/SMTP mailbox from Codex, Claude Code Pro, Claude Desktop, or any MCP-compatible client.

The server does not depend on Gmail, Microsoft 365, or any proprietary OAuth flow. It runs on your machine or on a server you control. The email password is never sent to the AI model.

## Requirements

- Java 24
- An email account compatible with IMAP and SMTP
- An app password if your provider requires one

Maven is not required: the repository includes the Maven Wrapper.

## Install and Verify

```bash
git clone https://github.com/opcoach/opcoach-mcp-mail.git
cd opcoach-mcp-mail
./mvnw clean verify
```

The standard build is non-interactive and uses only fake mail servers for tests.

## Configure

Terminal setup assistant:

```bash
./mvnw -Psetup clean verify
```

Or the temporary local mini UI:

```bash
./mvnw -Psetup-ui clean verify
```

By default, non-secret configuration is written to:

```text
~/.opcoach-mcp-mail/config.properties
```

Example:

```properties
profile=default
imap.host=imap.example.com
imap.port=993
imap.security=ssl_tls
smtp.host=smtp.example.com
smtp.port=465
smtp.security=ssl_tls
username=training@example.com
from.address=training@example.com
from.name=MCP Training
sent.mailbox=INBOX.Sent
```

The password is not written to this file. For a short workshop:

```bash
export MAIL_MCP_PASSWORD="fake-password"
```

To save the password in the local keychain:

```bash
java -jar target/opcoach-mcp-mail.jar config set-password --profile default
```

The macOS keychain is supported. On other platforms, use `MAIL_MCP_PASSWORD` temporarily until a durable backend is added.

## Run with an MCP Client

Recommended mode for Codex and Claude:

```bash
java -jar target/opcoach-mcp-mail.jar --stdio
```

Local HTTP mode:

```bash
java -jar target/opcoach-mcp-mail.jar --http --port 8095
```

The HTTP server listens on `127.0.0.1` by default. If you listen on another interface, provide a token:

```bash
java -jar target/opcoach-mcp-mail.jar --http --host 0.0.0.0 --port 8095 --token "long-random-token"
```

Convenience scripts are also available:

```bash
bin/setup-ui --profile default
bin/start-server --profile default --port 8095
bin/stop-server
```

`bin/start-server` runs the HTTP server on `127.0.0.1:8095` by default. It writes the PID file and logs under `.run/`, and builds `target/opcoach-mcp-mail.jar` automatically if it is missing.

## Codex Configuration

Example:

```json
{
  "mcpServers": {
    "opcoach-mcp-mail": {
      "command": "java",
      "args": ["-jar", "/path/to/opcoach-mcp-mail/target/opcoach-mcp-mail.jar", "--stdio"]
    }
  }
}
```

## Claude Configuration

Example:

```json
{
  "mcpServers": {
    "opcoach-mcp-mail": {
      "command": "java",
      "args": ["-jar", "/path/to/opcoach-mcp-mail/target/opcoach-mcp-mail.jar", "--stdio"]
    }
  }
}
```

## Exposed Tools

- `sendEmail`: sends a text or HTML email with base64 attachments, then attempts to copy it to Sent.
- `listMailboxes`: lists the available IMAP folders.
- `searchMessages`: searches messages with a conservative limit.
- `getMessage`: reads a specific message by UID.
- `getAttachment`: explicitly retrieves an attachment by identifier.

Searches return metadata and snippets. Attachments are never downloaded automatically.

## Security

- No destructive action in v1.
- No unlimited bulk reads.
- Email bodies and attachment contents are not written to audit logs.
- An email read by the AI remains untrusted external data.
- The AI client should request confirmation before any real send, according to its context.

## License

MIT.
