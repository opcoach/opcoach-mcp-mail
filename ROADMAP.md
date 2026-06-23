# Roadmap

The project is intentionally focused: provide a local-first MCP bridge for generic IMAP/SMTP mailboxes, with a desktop manager launched from a local Java build.

## Current Scope

- Configure one or more local mailbox profiles.
- Start and stop local HTTP MCP servers from the desktop manager.
- Send email through SMTP.
- List, search, and read IMAP messages.
- Retrieve attachments only when explicitly requested.
- Move messages between IMAP folders.
- Delete messages by moving them to the configured trash folder.
- Build and run locally with Java 24+ and the Maven Wrapper.

## Planned Improvements

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
- Self-contained Windows `.exe` distribution.

All future changes must preserve the privacy limits: no secrets in logs, bounded reads, no unbounded mailbox scans, and no automatic attachment retrieval.
