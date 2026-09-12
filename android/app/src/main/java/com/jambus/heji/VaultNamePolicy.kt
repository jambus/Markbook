package com.jambus.heji

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

enum class VaultEntryKind { NOTE, FOLDER }

sealed class VaultNameValidation {
    data class Valid(val actualName: String, val conflictKey: String) : VaultNameValidation()
    data class Invalid(val message: String) : VaultNameValidation()
}

/** Pure Vault naming rules shared by creation, rename, and unit tests. */
object VaultNamePolicy {
    private val reservedNames = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL"))
        (1..9).forEach { index ->
            add("COM$index")
            add("LPT$index")
        }
    }
    private val forbidden = setOf('/', '\\', '<', '>', ':', '"', '|', '?', '*')

    fun validate(input: String, kind: VaultEntryKind): VaultNameValidation {
        var value = Normalizer.normalize(input, Normalizer.Form.NFC)
        if (kind == VaultEntryKind.NOTE) {
            while (value.endsWith(".md", ignoreCase = true)) value = value.dropLast(3)
        }
        if (value.isEmpty()) return VaultNameValidation.Invalid("名称不能为空")
        if (value == "." || value == "..") return VaultNameValidation.Invalid("名称不能是 . 或 ..")
        if (value != value.trim()) return VaultNameValidation.Invalid("名称不能以空白开头或结尾")
        if (value.startsWith('.')) return VaultNameValidation.Invalid("名称不能以 . 开头")
        if (value.endsWith('.')) return VaultNameValidation.Invalid("名称不能以 . 结尾")
        if (value.any { it.isISOControl() || it in forbidden }) {
            return VaultNameValidation.Invalid("名称包含不支持的字符")
        }
        val reservedKey = value.substringBefore('.').uppercase(Locale.ROOT)
        if (reservedKey in reservedNames) return VaultNameValidation.Invalid("名称是系统保留名")
        val actual = if (kind == VaultEntryKind.NOTE) "$value.md" else value
        if (actual.toByteArray(StandardCharsets.UTF_8).size > MAX_UTF8_BYTES) {
            return VaultNameValidation.Invalid("名称不能超过 $MAX_UTF8_BYTES 个 UTF-8 字节")
        }
        return VaultNameValidation.Valid(actual, conflictKey(actual))
    }

    /** Files and folders share the base-name namespace; Markdown's extension is not part of it. */
    fun conflictKey(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFC)
        val base = if (normalized.endsWith(".md", ignoreCase = true)) normalized.dropLast(3) else normalized
        return base.lowercase(Locale.ROOT)
    }

    fun conflicts(candidate: VaultNameValidation.Valid, existingNames: Iterable<String>, excludeName: String? = null): Boolean =
        existingNames.any { existing ->
            existing != excludeName && conflictKey(existing) == candidate.conflictKey
        }

    private const val MAX_UTF8_BYTES = 240
}
