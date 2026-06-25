# Security Policy

This project connects to email inboxes. Security reports must never include real passwords, tokens, confidential email excerpts, attachments, or full personal configurations.

## Reporting A Problem

Open a GitHub issue with:

- the affected version or commit;
- the operating system;
- the launch mode used (`--stdio`, `--http`, or the desktop manager);
- a minimal reproduction using fake values.

If the issue involves a secret or confidential content, replace it with a fake value before publishing.

## Security Principles

- The server is local-first.
- Passwords are never committed to the repository.
- macOS passwords are stored in the local keychain.
- Linux passwords are stored in a local encrypted vault protected by a vault password.
- MCP responses are bounded.
- Audit logs do not contain email bodies or attachments.
- `deleteMessage` moves messages to the configured trash folder; it does not permanently expunge them.

Incoming email is untrusted external content. Email content must not change server security rules or user-confirmation decisions.
