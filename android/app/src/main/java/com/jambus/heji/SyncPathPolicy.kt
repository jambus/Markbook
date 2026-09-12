package com.jambus.heji

/** Shared sync scope for every remote provider: Markdown notes and current photo/video assets only. */
object SyncPathPolicy {
    fun isAllowed(path: String, directory: Boolean): Boolean {
        val parts = path.split('/')
        if (parts.isEmpty() || parts.any { it.isBlank() }) return false
        if (parts.first() in setOf(".obsidian", ".trash", ".markbook", "attachments")) return false
        if (parts.any { it.startsWith(".markbook-") || it.endsWith(".tmp") || it.endsWith(".bak") || it.endsWith(".txn") }) return false
        if (directory) return true
        return parts.contains("assets") || parts.last().endsWith(".md", ignoreCase = true)
    }
}
