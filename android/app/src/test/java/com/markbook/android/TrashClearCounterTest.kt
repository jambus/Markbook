package com.markbook.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashClearCounterTest {
    @Test
    fun reportsPartialFailureWithoutDiscardingRemainingCount() {
        val counter = TrashClearCounter()
        counter.record(true)
        counter.record(false)
        counter.record(true)

        assertEquals(TrashClearResult(2, 1, 1), counter.result(1))
    }

    @Test
    fun emptyTrashReportsZeroes() {
        assertEquals(TrashClearResult(0, 0, 0), TrashClearCounter().result(0))
    }
}
