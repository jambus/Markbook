package com.jambus.heji

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTaskSnapshotTest {
    @Test fun `running task presents progress without provider-specific UI`() {
        val snapshot = SyncTaskSnapshot(
            "test", "测试提供方", "目标", SyncTaskStatus.RUNNING, 2, 5,
            "正在上传", 1L, 0L
        )
        assertEquals("正在同步 2 / 5", snapshot.statusLabel())
    }

    @Test fun `interrupted task is never presented as completed`() {
        val snapshot = SyncTaskSnapshot(
            "test", "测试提供方", "目标", SyncTaskStatus.INTERRUPTED, 0, 0,
            "后台同步已中断，请重试", 1L, 2L
        )
        assertEquals("同步已中断，需要重试", snapshot.statusLabel())
    }
}
