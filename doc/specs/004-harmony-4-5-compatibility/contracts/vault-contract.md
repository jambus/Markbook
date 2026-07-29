# Shared Vault Contract

This contract is shared by the Android APK, the HarmonyOS Stage HAP, desktop
Obsidian, and future sync providers. The user-selected Vault is always the
source of truth; neither client creates a second Vault or a required database.

## Layout

```text
<Vault>/
├── Daily Notes/<yyyy-MM-dd>.md
├── attachments/<uuid>-original.<ext>
├── attachments/<uuid>-corrected.<ext>
└── .markbook/
    ├── trash/
    └── conflicts/
```

Notes are UTF-8 Markdown. Images embedded from a note use POSIX relative links,
normally `../attachments/<filename>`. The corrected image is the default
visible reference, while the original remains addressable using the same UUID.
Existing `photo-<uuid>.<ext>` files remain readable for backward compatibility.

## Recovery and Conflicts

Temporary files and transaction markers are prefixed with `.markbook-` and must
be removed or resolved on startup. A completed note or attachment transaction
must not be deleted merely because cleanup was interrupted. An unreferenced
incomplete attachment transaction may be removed after its marker is examined.

Deletes move files into `.markbook/trash/` with enough metadata to restore the
original relative path; permanent deletion is a separate explicit operation.
When both sides changed the same path, keep both versions under
`.markbook/conflicts/` or with a deterministic `冲突-<device>-<timestamp>` suffix.
No client silently overwrites the other version.
