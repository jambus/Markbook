package com.jambus.heji

import java.text.Normalizer

enum class VaultSearchSource { FILENAME, TAG, BODY }

data class VaultSearchMatch(
    val source: VaultSearchSource,
    val snippet: String,
    val rank: Int
)

/** Pure matching and presentation rules for the read-only Vault search. */
object VaultSearchPolicy {
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).lowercase()

    fun match(filename: String, content: String, query: String): VaultSearchMatch? {
        val needle = normalize(query).trim()
        if (needle.isEmpty()) return null
        if (normalize(filename).contains(needle)) return VaultSearchMatch(VaultSearchSource.FILENAME, filename, 0)
        val tagLine = content.lineSequence().firstOrNull { line ->
            Regex("(^|\\s)#[^\\s#]+") .findAll(line).any { normalize(it.value).contains(needle) }
        }
        if (tagLine != null) return VaultSearchMatch(VaultSearchSource.TAG, plainText(tagLine).take(96), 1)
        val bodyLine = content.lineSequence().firstOrNull { normalize(plainText(it)).contains(needle) }
        return bodyLine?.let { VaultSearchMatch(VaultSearchSource.BODY, plainText(it).take(96), 2) }
    }

    fun plainText(markdown: String): String = markdown
        .replace(Regex("!?\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[`*_~]+"), "")
        .replace(Regex("^#{1,6}\\s+"), "")
        .replace(Regex("^>\\s?"), "")
        .trim()

    fun isDailyNote(relativePath: String, dailyDirectory: String, todayFilename: String): Boolean {
        val parent = dailyDirectory.trim('/').takeIf { it.isNotEmpty() }
        val expected = listOfNotNull(parent, todayFilename).joinToString("/")
        return normalize(relativePath) == normalize(expected)
    }

    fun relativeTime(modifiedAt: Long?, now: Long): String? {
        val value = modifiedAt ?: return null
        if (value <= 0L || value > now + 60_000L) return null
        val minutes = (now - value) / 60_000L
        return when {
            minutes < 1L -> "刚刚"
            minutes < 60L -> "$minutes 分钟前"
            minutes < 24 * 60L -> "${minutes / 60} 小时前"
            minutes < 48 * 60L -> "昨天"
            else -> "${minutes / (24 * 60)} 天前"
        }
    }
}
