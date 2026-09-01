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
├── assets/<note-file-stem>/<HHmmss>-<xxxx>-v.{mp4|3gp}
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

New videos are original system-camera files, stored without transcoding as
`<HHmmss>-<xxxx>-v.mp4` or `.3gp`. The capture-ID collision check is shared by
photo and video suffixes (`-o`, `-c`, `-v`). A video reference is ordinary
Markdown: `[视频 HH:mm:ss](<relative POSIX path>)`; angle brackets are required
when the relative path contains spaces.

## User-managed files and folders

Users may create Markdown notes and ordinary folders at any depth below the
Vault root. The Vault root itself and the internal root children `.obsidian`,
`.markbook`, `.trash`, `assets`, and `attachments` are not user-manageable file-library
entries. Existing entries that do not meet current creation rules remain
readable and browsable.

New note and folder base names are NFC-normalized and must be unique in their
parent directory using `Locale.ROOT` lowercase comparison; notes and folders
share that namespace. Names may contain Chinese and emoji, but must not be
empty, `.` or `..`, begin with `.`, have leading/trailing whitespace or a
trailing `.`, contain a control character or `/\\<>:\"|?*`, be a Windows reserved
name, or exceed 240 UTF-8 bytes. A new note accepts an optional `.md` suffix
and is persisted with exactly one lowercase `.md`; a folder has no suffix.
Clients never overwrite an existing child to satisfy a create or rename.

Rename and create report both the requested and provider-returned actual name.
If a document provider changes the requested name, the client must retain and
show that actual name rather than silently presenting the request as successful.

Deleting a note or folder means moving the one direct child as a whole to the
Vault-root `.trash/`. A non-empty folder must use the provider's atomic
`moveDocument` semantics; clients must not emulate unsupported moves with a
recursive copy-and-delete. If the move fails or is unsupported, the source and
its contents remain in place. Moving or renaming does not rewrite Markdown
links and does not automatically delete attachments.

For a note whose parent relative path is `p`, a new attachment reference is the
POSIX relative path from `p` to `assets/<note-stem>/...`; this applies equally
to root notes, daily notes, and arbitrarily deep ordinary notes. If a folder is
renamed and the configured daily-note directory is that folder or a descendant,
clients synchronously rewrite the matching path prefix to the provider's actual
new name. If moving a folder to `.trash/` removes the configured directory or
one of its ancestors, clients synchronously reset the configuration to the
Vault root and keep a visible warning until the user chooses a new directory.
If a provider reports successful rename but its actual name cannot be resolved,
the client resets an affected daily-note directory to the Vault root rather
than retaining a stale or requested-only path.

## Recovery and Conflicts

Temporary files and transaction markers are prefixed with `.markbook-` and must
be removed or resolved on startup. A completed note or attachment transaction
must not be deleted merely because cleanup was interrupted. An unreferenced
incomplete attachment transaction may be removed after its marker is examined.

For every attachment type, the marker is created and its final attachment name is written and
flushed before any temporary output is created. The writer creates the final attachment(s),
re-reads and hashes the note before insertion to prevent overwriting an
externally changed note, records the final attachment identity and commit stage, saves the note,
then deletes marker and cache. If any
cleanup delete fails, the marker remains. Recovery may remove an unreferenced
incomplete attachment only after every Markdown file that could be scanned was
read successfully; if any Markdown file is unreadable, it must keep the marker
and attachment rather than infer it is unreferenced. Pending external capture
state may persist only note identity/path, note-content SHA-256, caret/scroll,
cache path, final attachment identity and phase—never a private copy of the Markdown body.
After a saved Markdown link is detected, recovery only cleans marker/cache/pending state; it never
replays the insertion. A provider result that returns null or throws after final rename is treated
as unknown and keeps its marker. MP4/3GP acceptance requires a recognized container signature plus
a video track; an output filename or Provider MIME alone is insufficient.

Deletes move notes into `.trash/`; this directory is excluded from remote sync.
Permanent deletion is a separate explicit operation. Attachments are retained until a later
explicit cleanup flow, because they may still be referenced by another note.
“Clear trash” permanently deletes only the current contents directly below the Vault-root
`.trash/` after explicit user confirmation. It does not delete `assets/` or `attachments/`, and a
partial failure leaves every undeleted item in place for a later retry.
When both sides changed the same path, keep both versions under
`.markbook/conflicts/` or with a deterministic `冲突-<device>-<timestamp>` suffix.
No client silently overwrites the other version.
