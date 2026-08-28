package com.markbook.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRecoveryPolicyTest {
    @Test
    fun ignoresNonMarkbookTemporaryFiles() {
        assertEquals(RecoveryArtifact.Ignore, VaultRecoveryPolicy.classify("draft.tmp", false))
    }

    @Test
    fun classifiesTemporaryArtifactInAnyEditableDirectory() {
        assertEquals(
            RecoveryArtifact.DeleteTemporary,
            VaultRecoveryPolicy.classify(".markbook-random.tmp", false)
        )
    }

    @Test
    fun backupCarriesOriginalFilename() {
        val artifact = VaultRecoveryPolicy.classify(".markbook-Project.md.bak", false)
        assertTrue(artifact is RecoveryArtifact.RestoreBackup)
        assertEquals("Project.md", (artifact as RecoveryArtifact.RestoreBackup).originalName)
    }

    @Test
    fun photoTransactionOnlyRunsInsideAttachmentTree() {
        assertEquals(
            RecoveryArtifact.Ignore,
            VaultRecoveryPolicy.classify(".markbook-120000-ab12.txn", false)
        )
        assertEquals(
            RecoveryArtifact.ResolvePhotoTransaction,
            VaultRecoveryPolicy.classify(".markbook-120000-ab12.txn", true)
        )
    }
}
