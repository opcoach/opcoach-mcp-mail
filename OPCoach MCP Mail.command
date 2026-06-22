#!/usr/bin/env bash
cd "$(dirname "$0")" || exit 1
bin/manager >/tmp/opcoach-mcp-mail-manager.log 2>&1 &
