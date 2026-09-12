package com.jambus.heji

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashSnapshotPolicyTest {
    @Test fun snapshotDoesNotTargetItemsAddedAfterConfirmation() {
        assertEquals(listOf("a"), TrashSnapshotPolicy.targets(listOf("a"), listOf("a", "new")))
    }

    @Test fun missingOrNestedIdentityIsRejected() {
        assertEquals(emptyList<String>(), TrashSnapshotPolicy.targets(listOf("nested"), listOf("direct")))
        assertEquals(1, TrashSnapshotPolicy.missingCount(listOf("nested"), listOf("direct")))
    }

    @Test fun duplicateConfirmedIdentityIsDeletedOnce() {
        assertEquals(listOf("a"), TrashSnapshotPolicy.targets(listOf("a", "a"), listOf("a")))
    }
}
