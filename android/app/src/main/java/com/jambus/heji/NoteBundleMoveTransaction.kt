package com.jambus.heji

import java.util.Base64

/** Persisted, body-free move marker. Values are Base64 so Vault paths may contain any Unicode. */
data class NoteBundleMoveTransaction(
    val id: String,
    val sourceNote: String,
    val targetNote: String,
    val sourceBundle: String,
    val targetBundle: String,
    val sourceHash: String,
    val rewrittenHash: String,
    val stage: Stage
) {
    enum class Stage { PREPARED, BUNDLE_INTENT, BUNDLE_AT_TARGET, NOTE_INTENT, NOTE_AT_TARGET, REWRITE_INTENT, REWRITTEN }

    fun serialize(): String = listOf(id, sourceNote, targetNote, sourceBundle, targetBundle, sourceHash, rewrittenHash, stage.name)
        .joinToString("\n") { Base64.getUrlEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }

    fun withStage(next: Stage) = copy(stage = next)

    companion object {
        fun parse(value: String): NoteBundleMoveTransaction? = runCatching {
            val fields = value.lineSequence().filter { it.isNotBlank() }.map { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }.toList()
            if (fields.size != 8) return null
            NoteBundleMoveTransaction(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], Stage.valueOf(fields[7]))
        }.getOrNull()
    }
}
