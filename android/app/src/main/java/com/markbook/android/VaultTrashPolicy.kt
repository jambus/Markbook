package com.markbook.android

/** Pure naming for note-only copy-to-trash fallback. */
object VaultTrashPolicy {
    fun uniqueNoteName(original: String, existingNames: Iterable<String>, suffix: String): String {
        if (existingNames.none { VaultNamePolicy.conflictKey(it) == VaultNamePolicy.conflictKey(original) }) return original
        val stem = if (original.endsWith(".md", ignoreCase = true)) original.dropLast(3) else original
        var attempt = 1
        while (true) {
            val candidate = "$stem-$suffix${if (attempt == 1) "" else "-$attempt"}.md"
            if (existingNames.none { VaultNamePolicy.conflictKey(it) == VaultNamePolicy.conflictKey(candidate) }) return candidate
            attempt += 1
        }
    }
}
