package com.markbook.android

/** Keeps recovery conservative when a Vault scan cannot prove an attachment is unreferenced. */
enum class AttachmentReferenceScan { REFERENCED, UNREFERENCED, UNREADABLE }
enum class AttachmentRecoveryAction { KEEP_MARKER_AND_FILES, DELETE_FILES_AND_MARKER, DELETE_MARKER_ONLY }

object AttachmentRecoveryPolicy {
    fun action(markerHasNames: Boolean, scan: AttachmentReferenceScan): AttachmentRecoveryAction = when {
        !markerHasNames -> AttachmentRecoveryAction.DELETE_MARKER_ONLY
        scan == AttachmentReferenceScan.UNREADABLE -> AttachmentRecoveryAction.KEEP_MARKER_AND_FILES
        scan == AttachmentReferenceScan.REFERENCED -> AttachmentRecoveryAction.DELETE_MARKER_ONLY
        else -> AttachmentRecoveryAction.DELETE_FILES_AND_MARKER
    }
}
