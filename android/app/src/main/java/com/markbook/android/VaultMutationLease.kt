package com.markbook.android

/** Process-wide serialization for structural Vault mutations and sync runs. */
object VaultMutationLease {
    enum class Kind { STRUCTURAL, SYNC }
    data class Token internal constructor(val vaultId: String, val kind: Kind, val nonce: Long)

    private val owners = mutableMapOf<String, Token>()
    private var nextNonce = 1L

    @Synchronized fun tryAcquire(vaultId: String, kind: Kind): Token? {
        if (vaultId.isBlank() || owners.containsKey(vaultId)) return null
        return Token(vaultId, kind, nextNonce++).also { owners[vaultId] = it }
    }

    @Synchronized fun release(token: Token) {
        if (owners[token.vaultId] == token) owners.remove(token.vaultId)
    }

    @Synchronized fun isHeld(vaultId: String, kind: Kind? = null): Boolean =
        owners[vaultId]?.let { kind == null || it.kind == kind } == true
}
