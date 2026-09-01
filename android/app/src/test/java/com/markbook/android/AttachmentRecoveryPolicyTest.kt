package com.markbook.android

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentRecoveryPolicyTest {
    @Test
    fun unreadableMarkdownNeverAuthorizesAttachmentDeletion() {
        assertEquals(
            AttachmentRecoveryAction.KEEP_MARKER_AND_FILES,
            AttachmentRecoveryPolicy.action(true, AttachmentReferenceScan.UNREADABLE)
        )
    }

    @Test
    fun onlyProvenUnreferencedTransactionDeletesFiles() {
        assertEquals(
            AttachmentRecoveryAction.DELETE_FILES_AND_MARKER,
            AttachmentRecoveryPolicy.action(true, AttachmentReferenceScan.UNREFERENCED)
        )
        assertEquals(
            AttachmentRecoveryAction.DELETE_MARKER_ONLY,
            AttachmentRecoveryPolicy.action(true, AttachmentReferenceScan.REFERENCED)
        )
    }

    @Test
    fun emptyMarkerDeletesOnlyItselfSoItCannotBlockRecoveryForever() {
        assertEquals(
            AttachmentRecoveryAction.DELETE_MARKER_ONLY,
            AttachmentRecoveryPolicy.action(false, AttachmentReferenceScan.UNREADABLE)
        )
    }
}
