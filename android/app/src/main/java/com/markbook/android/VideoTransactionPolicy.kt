package com.markbook.android

/**
 * A provider can rename successfully and still report null/throw. Once final rename has been
 * attempted, recovery must be allowed to decide from the marker rather than deleting it here.
 */
object VideoTransactionPolicy {
    /** Marker payload is written and flushed before a temporary document may be created. */
    fun markerPayload(finalVideoName: String): String = "$finalVideoName\n"

    fun mayCreateTemporary(markerPayload: String): Boolean = markerPayload
        .lineSequence()
        .map(String::trim)
        .any { it.isNotEmpty() }

    fun mayDeleteMarkerAfterFailure(
        finalRenameAttempted: Boolean,
        finalVideoConfirmedAbsent: Boolean,
        temporaryConfirmedAbsent: Boolean
    ): Boolean = !finalRenameAttempted && finalVideoConfirmedAbsent && temporaryConfirmedAbsent
}

enum class PendingVideoRecoveryAction { SHOW_CONFIRMATION, CLEANUP_ONLY, WAIT }

/** A saved note link is authoritative; never replay an insertion after it exists. */
object PendingVideoCaptureRecoveryPolicy {
    fun action(
        stage: String,
        hasAttachmentPath: Boolean,
        noteAlreadyReferencesAttachment: Boolean,
        cacheExists: Boolean
    ): PendingVideoRecoveryAction = when {
        hasAttachmentPath && noteAlreadyReferencesAttachment -> PendingVideoRecoveryAction.CLEANUP_ONLY
        stage == PendingVideoStages.LAUNCHED || !cacheExists -> PendingVideoRecoveryAction.WAIT
        else -> PendingVideoRecoveryAction.SHOW_CONFIRMATION
    }
}

/** Pure link forms accepted when determining that a pending video was already committed. */
object PendingVideoLinkPolicy {
    fun isReferenced(markdown: String, relativePath: String): Boolean =
        markdown.contains("](<$relativePath>)") || markdown.contains("]($relativePath)")
}

object PendingVideoStages {
    const val PREPARING = "preparing"
    const val LAUNCHED = "launched"
    const val CONFIRMING = "confirming"
    const val COPYING = "copying"
    const val ATTACHMENT_WRITTEN = "attachment_written"
    const val NOTE_SAVED = "note_saved"
}
