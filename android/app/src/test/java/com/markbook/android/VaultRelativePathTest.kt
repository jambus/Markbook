package com.markbook.android

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultRelativePathTest {
    @Test
    fun `attachment paths use actual parent depth`() {
        assertEquals("assets/root/photo.jpg", VaultRelativePath.attachmentPath("", "assets/root/photo.jpg"))
        assertEquals("../assets/one/photo.jpg", VaultRelativePath.attachmentPath("one", "assets/one/photo.jpg"))
        assertEquals("../../../assets/deep/photo.jpg", VaultRelativePath.attachmentPath("one/two/three", "assets/deep/photo.jpg"))
    }

    @Test
    fun `daily directory rename rewrites descendants and removal resets them`() {
        assertEquals("Journal/2026", VaultRelativePath.renamedDailyDirectory("Daily Notes/2026", "Daily Notes", "Journal"))
        assertEquals("Other", VaultRelativePath.renamedDailyDirectory("Other", "Daily Notes", "Journal"))
        assertEquals("", VaultRelativePath.resetIfRemoved("Daily Notes/2026", "Daily Notes"))
        assertEquals("Other", VaultRelativePath.resetIfRemoved("Other", "Daily Notes"))
    }

    @Test
    fun `unaffected daily directory remains unchanged`() {
        assertEquals("Other", VaultRelativePath.renamedDailyDirectory("Other", "Daily", "Journal"))
        assertEquals("Other", VaultRelativePath.resetIfRemoved("Other", "Daily"))
    }
}
