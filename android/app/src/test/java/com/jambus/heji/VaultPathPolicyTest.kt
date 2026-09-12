package com.jambus.heji

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPathPolicyTest {
    @Test
    fun `all Vault internal roots are protected at any depth`() {
        listOf(".obsidian", ".markbook", ".trash", "assets", "attachments").forEach { root ->
            assertTrue("$root must be protected", VaultPathPolicy.isProtected("$root/child"))
        }
    }

    @Test
    fun `ordinary folders remain manageable`() {
        assertFalse(VaultPathPolicy.isProtected("Projects/2026"))
        assertFalse(VaultPathPolicy.isProtected(""))
    }

    @Test
    fun `root reserved names are NFC casefold protected`() {
        assertTrue(VaultPathPolicy.isProtectedRootName("ASSETS"))
        assertTrue(VaultPathPolicy.isProtectedRootName(".ObSiDiAn"))
        assertFalse(VaultPathPolicy.isProtectedRootName("assets.md"))
    }
}
