package com.jambus.heji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteBundleMovePolicyTest {
    @Test fun rewritesOnlyLocalBundleLinksForDeepMove() {
        val source = "![图](../../assets/会议/100000-a-c.jpg)\n[视频](<../../assets/会议/100001-b-v.mp4>)\n[网页](https://example.com/a)"
        val moved = NoteBundleMovePolicy.rewriteBundleReferences(
            source, "工作/九月", "归档/2026/九月", "assets/会议", "归档/2026/九月/assets/会议"
        )
        assertEquals("![图](assets/会议/100000-a-c.jpg)\n[视频](<assets/会议/100001-b-v.mp4>)\n[网页](https://example.com/a)", moved)
    }

    @Test fun recognizesSharedReferenceButNotExternalOrAnchorLinks() {
        assertTrue(NoteBundleMovePolicy.isBundleReference("![图](../assets/a/x.jpg)", "项目", "assets/a"))
        assertFalse(NoteBundleMovePolicy.isBundleReference("[网页](https://x/assets/a/x.jpg)", "项目", "assets/a"))
        assertTrue(NoteBundleMovePolicy.isBundleReference("[局部](../assets/a/x.jpg#top)", "项目", "assets/a"))
    }

    @Test fun buildsNestedDestinationBundlePath() {
        assertEquals("项目/assets/会议", NoteBundleMovePolicy.bundlePath("项目/会议.md"))
        assertEquals("assets/会议", NoteBundleMovePolicy.legacyBundlePath("项目/会议.md"))
    }

    @Test fun rewritesAndDetectsBareReferenceDefinitions() {
        val source = "![图][photo]\n\n[photo]: ../assets/a/x.jpg \"说明\""
        assertTrue(NoteBundleMovePolicy.isBundleReference(source, "项目", "assets/a"))
        assertEquals(
            "![图][photo]\n\n[photo]: assets/a/x.jpg \"说明\"",
            NoteBundleMovePolicy.rewriteBundleReferences(source, "项目", "归档/项目", "assets/a", "归档/项目/assets/a")
        )
    }

    @Test fun rewritesTitlesWikiLinksHtmlAndPercentEncodedPaths() {
        val source = "![图](../assets/a/photo%20one.jpg \"说明\")\n![[../assets/a/x.jpg|图]]\n<img src=\"../assets/a/y.jpg\">"
        val moved = NoteBundleMovePolicy.rewriteBundleReferences(source, "项目", "归档/项目", "assets/a", "归档/项目/assets/a")
        assertTrue(moved.contains("assets/a/photo one.jpg"))
        assertTrue(moved.contains("\"说明\""))
        assertTrue(moved.contains("![[assets/a/x.jpg|图]]"))
        assertTrue(moved.contains("src=\"assets/a/y.jpg\""))
    }

    @Test fun rejectsUnparsedPlausibleBundlePathAndVaultEscape() {
        assertEquals(NoteBundleMovePolicy.ReferenceStatus.AMBIGUOUS,
            NoteBundleMovePolicy.referenceStatus("`../assets/a/x.jpg`", "项目", "assets/a"))
        assertTrue(NoteBundleMovePolicy.isBundleReference("![x](../../../assets/a/x.jpg)", "项目", "assets/a"))
    }
}
