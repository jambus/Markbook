package com.jambus.heji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTransactionPolicyTest {
    @Test
    fun finalVideoNameIsPersistedBeforeTemporaryCreationIsAllowed() {
        val payload = VideoTransactionPolicy.markerPayload("120000-ab12-v.mp4")
        assertEquals("120000-ab12-v.mp4\n", payload)
        assertTrue(VideoTransactionPolicy.mayCreateTemporary(payload))
        assertFalse(VideoTransactionPolicy.mayCreateTemporary(""))
    }

    @Test
    fun renameUnknownAlwaysKeepsMarkerForRecovery() {
        assertFalse(VideoTransactionPolicy.mayDeleteMarkerAfterFailure(
            finalRenameAttempted = true,
            finalVideoConfirmedAbsent = false,
            temporaryConfirmedAbsent = true
        ))
    }

    @Test
    fun onlyPreRenameFailureWithConfirmedAbsenceCanRemoveMarker() {
        assertTrue(VideoTransactionPolicy.mayDeleteMarkerAfterFailure(
            finalRenameAttempted = false,
            finalVideoConfirmedAbsent = true,
            temporaryConfirmedAbsent = true
        ))
    }

    @Test
    fun savedNoteLinkOnlyCleansUpInsteadOfReplayingInsertion() {
        assertEquals(
            PendingVideoRecoveryAction.CLEANUP_ONLY,
            PendingVideoCaptureRecoveryPolicy.action(
                PendingVideoStages.NOTE_SAVED, hasAttachmentPath = true,
                noteAlreadyReferencesAttachment = true, cacheExists = true
            )
        )
    }

    @Test
    fun committedVideoRecoveryRecognizesAngleAndPlainRelativeLinks() {
        val path = "../assets/Note/120000-ab12-v.mp4"
        assertTrue(PendingVideoLinkPolicy.isReferenced("[视频](<$path>)", path))
        assertTrue(PendingVideoLinkPolicy.isReferenced("[视频]($path)", path))
    }
}
