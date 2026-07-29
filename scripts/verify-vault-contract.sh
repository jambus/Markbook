#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$repo_root/shared-testdata/Vault"
note="$fixture/Daily Notes/2026-01-02.md"

fail() {
  printf 'Vault contract check failed: %s\n' "$1" >&2
  exit 1
}

[[ -d "$fixture/Daily Notes" ]] || fail 'missing Daily Notes directory'
[[ -d "$fixture/attachments" ]] || fail 'missing attachments directory'
[[ -f "$note" ]] || fail 'missing Markdown fixture'

grep -Fq '[[项目索引]]' "$note" || fail 'missing wikilink fixture'
grep -Fq '../attachments/sample-original.jpg' "$note" || fail 'missing original image link'
grep -Fq '../attachments/sample-corrected.jpg' "$note" || fail 'missing corrected image link'

if find "$fixture" -type f \( -name '*.db' -o -name '*.sqlite' -o -name '*.sqlite3' \) -print -quit | grep -q .; then
  fail 'fixture contains a database file'
fi

if find "$fixture" -type f \( -name '*.tmp' -o -name '*.bak' -o -name '*.txn' \) -print -quit | grep -q .; then
  fail 'fixture contains an interrupted-write marker'
fi

printf 'Vault contract OK: UTF-8 Markdown, relative attachments, and no database state.\n'
