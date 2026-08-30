package com.markbook.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultNamePolicyTest {
    @Test
    fun `notes normalize extension and Unicode NFC`() {
        val value = VaultNamePolicy.validate("Cafe\u0301.MD.md", VaultEntryKind.NOTE) as VaultNameValidation.Valid

        assertEquals("Café.md", value.actualName)
        assertEquals("café", value.conflictKey)
    }

    @Test
    fun `Chinese and emoji names are valid`() {
        val note = VaultNamePolicy.validate("会议📌", VaultEntryKind.NOTE) as VaultNameValidation.Valid
        val folder = VaultNamePolicy.validate("项目✨", VaultEntryKind.FOLDER) as VaultNameValidation.Valid

        assertEquals("会议📌.md", note.actualName)
        assertEquals("项目✨", folder.actualName)
    }

    @Test
    fun `invalid names cover path whitespace reserved and byte limit`() {
        val values = listOf("", ".", "..", " note", "note ", "note.", ".hidden", "a/b", "NUL", "x\u0000y")

        values.forEach { value ->
            assertTrue("$value should be invalid", VaultNamePolicy.validate(value, VaultEntryKind.FOLDER) is VaultNameValidation.Invalid)
        }
        assertTrue(VaultNamePolicy.validate("😀".repeat(61), VaultEntryKind.NOTE) is VaultNameValidation.Invalid)
    }

    @Test
    fun `note and folder names share case insensitive NFC namespace`() {
        val requested = VaultNamePolicy.validate("café", VaultEntryKind.NOTE) as VaultNameValidation.Valid

        assertTrue(VaultNamePolicy.conflicts(requested, listOf("Cafe\u0301")))
        assertTrue(VaultNamePolicy.conflicts(requested, listOf("CAFÉ.md")))
        assertFalse(VaultNamePolicy.conflicts(requested, listOf("other.md")))
    }
}
