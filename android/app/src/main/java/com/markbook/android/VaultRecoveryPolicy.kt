package com.markbook.android

sealed class RecoveryArtifact {
    object Ignore : RecoveryArtifact()
    object DeleteTemporary : RecoveryArtifact()
    data class RestoreBackup(val originalName: String) : RecoveryArtifact()
    object ResolveAttachmentTransaction : RecoveryArtifact()
}

/** Pure classification for interrupted Markbook artifacts found anywhere in a Vault. */
object VaultRecoveryPolicy {
    fun classify(name: String, attachmentDirectory: Boolean): RecoveryArtifact {
        if (!name.startsWith(".markbook-")) return RecoveryArtifact.Ignore
        return when {
            name.endsWith(".tmp") -> RecoveryArtifact.DeleteTemporary
            name.endsWith(".bak") -> {
                val original = name.removePrefix(".markbook-").removeSuffix(".bak")
                if (original.isBlank()) RecoveryArtifact.Ignore else RecoveryArtifact.RestoreBackup(original)
            }
            name.endsWith(".txn") && attachmentDirectory -> RecoveryArtifact.ResolveAttachmentTransaction
            else -> RecoveryArtifact.Ignore
        }
    }
}
