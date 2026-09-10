package com.markbook.android

/** Pure guard for irreversible deletion: only confirmed direct-child identities may be targeted. */
object TrashSnapshotPolicy {
    fun targets(confirmed: Collection<String>, currentDirect: Collection<String>): List<String> =
        confirmed.distinct().filter { it in currentDirect }

    fun missingCount(confirmed: Collection<String>, currentDirect: Collection<String>): Int =
        confirmed.distinct().count { it !in currentDirect }
}
