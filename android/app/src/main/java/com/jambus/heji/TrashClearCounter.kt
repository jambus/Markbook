package com.jambus.heji

/** Counts independent deletion attempts so partial failure never becomes an all-success message. */
class TrashClearCounter {
    private var deleted = 0
    private var failed = 0

    fun record(success: Boolean) {
        if (success) deleted += 1 else failed += 1
    }

    fun result(remaining: Int): TrashClearResult = TrashClearResult(
        deleted = deleted,
        failed = failed,
        remaining = remaining
    )
}
