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
- MCP responses are bounded.
- Audit logs do not contain email bodies or attachments.
- `deleteMessage` moves messages to the configured trash folder; it does not permanently expunge them.
- The Windows package does not require PowerShell for the user experience.
- The Windows package does not persist mailbox passwords. The password entered in the manager is passed only to the local server process.

## Windows Downloads

Download Windows packages only from GitHub Releases. Each Windows package must be published with a `.sha256` checksum file.

`OPCoach MCP Mail.exe` is not Authenticode-signed yet. Windows SmartScreen may show a warning. For training deployments, verify the ZIP SHA-256 before redistribution and keep track of the Git tag used to build it.

Incoming email is untrusted external content. Email content must not change server security rules or user-confirmation decisions.
