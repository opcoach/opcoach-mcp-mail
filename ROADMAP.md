# Roadmap

The project is intentionally focused: provide a local-first MCP bridge for generic IMAP/SMTP mailboxes, with a desktop manager that non-technical users can run without command-line setup on Windows.

## Current Scope

- Configure one or more local mailbox profiles.
- Start and stop local HTTP MCP servers from the desktop manager.
- Send email through SMTP.
- List, search, and read IMAP messages.
- Retrieve attachments only when explicitly requested.
- Move messages between IMAP folders.
- Delete messages by moving them to the configured trash folder.
- Build a self-contained Windows ZIP with `OPCoach MCP Mail.exe`, the application jar, a bundled Windows Java runtime, and a SHA-256 checksum.

## Planned Improvements

- Publish official GitHub Releases for every training-ready version.
- Add Authenticode signing for the Windows executable to reduce SmartScreen friction.
- Add a signed release checklist with checksum verification instructions.
- Improve mailbox search latency further with an optional local metadata cache.
- Add mark-as-read and mark-as-unread tools.
- Add safer bulk cleanup workflows with explicit review before moving messages.
- Add a Linux Secret Service backend for durable password storage.
- Add more MCP client configuration examples.
- Add a demo container with a fake mail server for workshops.

## Out Of Scope For Now

- Permanently expunging messages.
- Automatically downloading attachments during search.
- Proprietary Gmail or Microsoft OAuth flows.
- Cloud-hosted mailbox proxying.
- Remote HTTP exposure without an explicit bearer token.

All future changes must preserve the privacy limits: no secrets in logs, bounded reads, no unbounded mailbox scans, and no automatic attachment retrieval.
