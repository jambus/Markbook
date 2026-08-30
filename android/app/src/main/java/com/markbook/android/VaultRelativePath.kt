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

    private fun replacePrefix(value: List<String>, oldPrefix: List<String>, replacement: List<String>): List<String> =
        if (oldPrefix.isNotEmpty() && value.startsWith(oldPrefix)) replacement + value.drop(oldPrefix.size) else value

    private fun cleanParts(value: String): List<String> = value.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }

    private fun List<String>.startsWith(prefix: List<String>): Boolean =
        size >= prefix.size && subList(0, prefix.size) == prefix
}
