package com.markbook.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSearchPolicyTest {
    @Test fun filenameRanksBeforeTagAndBody() {
        assertEquals(VaultSearchSource.FILENAME, VaultSearchPolicy.match("Plan", "#plan\nplan", "plan")?.source)
        assertEquals(VaultSearchSource.TAG, VaultSearchPolicy.match("Note", "#plan\nbody", "plan")?.source)
        assertEquals(VaultSearchSource.BODY, VaultSearchPolicy.match("Note", "a plan body", "plan")?.source)
    }

    @Test fun matchingUsesNfcCaseFoldAndChineseSubstring() {
        assertTrue(VaultSearchPolicy.match("Café", "", "CAFÉ") != null)
        assertTrue(VaultSearchPolicy.match("会议记录", "", "会议") != null)
    }

    @Test fun bodyMatchingAndSnippetsUsePlainText() {
        val match = VaultSearchPolicy.match("Note", "**alpha** beta and [link](https://example.com)", "alpha beta")
        assertEquals(VaultSearchSource.BODY, match?.source)
        assertEquals("alpha beta and link", match?.snippet)
    }

    @Test fun dailyPathMustMatchExactly() {
        assertTrue(VaultSearchPolicy.isDailyNote("Daily Notes/2026-09-09.md", "Daily Notes", "2026-09-09.md"))
        assertTrue(!VaultSearchPolicy.isDailyNote("Projects/2026-09-09.md", "Daily Notes", "2026-09-09.md"))
    }

    @Test fun relativeTimeRejectsMissingAndFutureValues() {
        val now = 300_000_000L
        assertEquals("刚刚", VaultSearchPolicy.relativeTime(now - 30_000L, now))
        assertEquals("昨天", VaultSearchPolicy.relativeTime(now - 25 * 60 * 60 * 1000L, now))
        assertNull(VaultSearchPolicy.relativeTime(null, now))
        assertNull(VaultSearchPolicy.relativeTime(now + 61_000L, now))
    }
}
