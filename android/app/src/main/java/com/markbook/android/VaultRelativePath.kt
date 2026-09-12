package com.markbook.android

/** Pure POSIX path rules for Vault-relative note locations and daily-folder preferences. */
object VaultRelativePath {
    fun attachmentPath(noteParentRelativePath: String, attachmentRelativePath: String): String {
        val parents = cleanParts(noteParentRelativePath)
        return (List(parents.size) { ".." } + cleanParts(attachmentRelativePath)).joinToString("/")
    }

    fun renamedDailyDirectory(current: String, oldDirectory: String, newDirectory: String): String =
        replacePrefix(cleanParts(current), cleanParts(oldDirectory), cleanParts(newDirectory)).joinToString("/")

    fun resetIfRemoved(current: String, removedDirectory: String): String {
        val currentParts = cleanParts(current)
        val removedParts = cleanParts(removedDirectory)
        return if (removedParts.isNotEmpty() && currentParts.startsWith(removedParts)) "" else currentParts.joinToString("/")
    }

    /** Resolves a Markdown-relative path without allowing it to escape the Vault root. */
    fun resolveFromNoteParent(noteParentRelativePath: String, relativePath: String): String? {
        if (relativePath.startsWith('/') || relativePath.contains('\\') || relativePath.contains("://")) return null
        val parts = noteParentRelativePath.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.toMutableList()
        for (part in relativePath.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun replacePrefix(value: List<String>, oldPrefix: List<String>, replacement: List<String>): List<String> =
        if (oldPrefix.isNotEmpty() && value.startsWith(oldPrefix)) replacement + value.drop(oldPrefix.size) else value

    private fun cleanParts(value: String): List<String> = value.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }

    private fun List<String>.startsWith(prefix: List<String>): Boolean =
        size >= prefix.size && subList(0, prefix.size) == prefix
}
