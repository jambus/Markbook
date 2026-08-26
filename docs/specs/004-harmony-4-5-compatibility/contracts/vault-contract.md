# Shared Vault Contract

This contract is shared by the Android APK, the HarmonyOS Stage HAP, desktop
Obsidian, and future sync providers. The user-selected Vault is always the
source of truth; neither client creates a second Vault or a required database.

## Layout

```text
<Vault>/
├── Daily Notes/<yyyy-MM-dd>.md
├── assets/<note-file-stem>/<HHmmss>-<xxxx>-o.<ext>
├── assets/<note-file-stem>/<HHmmss>-<xxxx>-c.<ext>
├── attachments/                         # legacy, read-only compatibility
├── .trash/                              # local-only deleted notes
└── .markbook/
    └── conflicts/
```

Notes are UTF-8 Markdown. New images are stored below `assets/` in a folder
named from the Markdown filename without `.md`; a daily note `2026-07-30.md`
therefore writes into `assets/2026-07-30/`. Links use POSIX relative paths such
as `../assets/2026-07-30/143015-a3f9-c.jpg`. The corrected image is the default
visible reference, while the original remains addressable using the same capture
ID. New capture IDs use the local capture time plus a four-character lowercase
base-36 random suffix. The client checks both target names before creating
either file and retries on a collision.
Existing UUID-based names, including `photo-<uuid>.<ext>`, remain readable for
backward compatibility under the legacy `attachments/` directory.

## Recovery and Conflicts

Temporary files and transaction markers are prefixed with `.markbook-` and must
be removed or resolved on startup. A completed note or attachment transaction
must not be deleted merely because cleanup was interrupted. An unreferenced
incomplete attachment transaction may be removed after its marker is examined.

Deletes move notes into `.trash/`; this directory is excluded from remote sync.
Permanent deletion is a separate explicit operation. Attachments are retained until a later
explicit cleanup flow, because they may still be referenced by another note.
When both sides changed the same path, keep both versions under
`.markbook/conflicts/` or with a deterministic `冲突-<device>-<timestamp>` suffix.
No client silently overwrites the other version.
