package com.jambus.heji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteReadResultTest {
    @Test
    fun permissionFailureCannotBeMistakenForEmptyContent() {
        val result: NoteReadResult = NoteReadResult.Failure(VaultFailureKind.PERMISSION_DENIED)

        assertTrue(result is NoteReadResult.Failure)
        assertEquals(
            VaultFailureKind.PERMISSION_DENIED,
            (result as NoteReadResult.Failure).kind
        )
    }

    @Test
    fun emptyExistingNoteIsStillAnExplicitSuccess() {
        val result: NoteReadResult = NoteReadResult.Success("")

        assertTrue(result is NoteReadResult.Success)
        assertEquals("", (result as NoteReadResult.Success).content)
    }
}
