package com.jambus.heji

import org.junit.Assert.*
import org.junit.Test

class VaultMutationLeaseTest {
    @Test fun sameVaultSyncAndMoveAreExclusiveButDifferentVaultsAreIndependent() {
        val sync = VaultMutationLease.tryAcquire("vault-a", VaultMutationLease.Kind.SYNC)!!
        assertNull(VaultMutationLease.tryAcquire("vault-a", VaultMutationLease.Kind.STRUCTURAL))
        val other = VaultMutationLease.tryAcquire("vault-b", VaultMutationLease.Kind.STRUCTURAL)!!
        VaultMutationLease.release(sync)
        val move = VaultMutationLease.tryAcquire("vault-a", VaultMutationLease.Kind.STRUCTURAL)
        assertNotNull(move)
        VaultMutationLease.release(move!!)
        VaultMutationLease.release(other)
    }
}
