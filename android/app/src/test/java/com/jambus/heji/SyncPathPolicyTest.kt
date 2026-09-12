package com.jambus.heji

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPathPolicyTest {
    @Test fun `syncs markdown at any user folder depth`() {
        assertTrue(SyncPathPolicy.isAllowed("Projects/2026/plan.md", false))
    }

    @Test fun `syncs current assets but excludes legacy and local-only paths`() {
        assertTrue(SyncPathPolicy.isAllowed("assets/daily/090000-v.mp4", false))
        assertFalse(SyncPathPolicy.isAllowed("attachments/legacy/photo.jpg", false))
        assertFalse(SyncPathPolicy.isAllowed(".trash/deleted.md", false))
        assertFalse(SyncPathPolicy.isAllowed(".markbook/conflicts/note.md", false))
    }

    @Test fun `keeps user folders traversable while excluding internal roots`() {
        assertTrue(SyncPathPolicy.isAllowed("Projects", true))
        assertFalse(SyncPathPolicy.isAllowed(".obsidian", true))
    }
}
