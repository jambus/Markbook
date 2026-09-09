package com.markbook.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class SyncTaskStatus {
    RUNNING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED
}

data class SyncTaskSummary(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val unchanged: Int = 0,
    val conflicts: Int = 0
)

/**
 * Provider-neutral, device-local sync state. Provider implementations may vary, but all of them
 * surface the same lifecycle, summary, error details, and interruption semantics.
 */
data class SyncTaskSnapshot(
    val providerId: String,
    val providerName: String,
    val targetName: String,
    val status: SyncTaskStatus,
    val completed: Int,
    val total: Int,
    val message: String,
    val startedAt: Long,
    val finishedAt: Long,
    val summary: SyncTaskSummary = SyncTaskSummary(),
    val errors: List<String> = emptyList()
) {
    val isRunning: Boolean get() = status == SyncTaskStatus.RUNNING

    fun statusLabel(): String = when (status) {
        SyncTaskStatus.RUNNING -> if (total > 0) "正在同步 $completed / $total" else "正在后台同步"
        SyncTaskStatus.SUCCEEDED -> "同步完成"
        SyncTaskStatus.FAILED -> "同步未完全完成"
        SyncTaskStatus.CANCELLED -> "同步已取消"
        SyncTaskStatus.INTERRUPTED -> "同步已中断，需要重试"
    }
}

/** Persists only the latest task summary; credentials and Vault content are never stored here. */
class SyncTaskStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun snapshot(): SyncTaskSnapshot? = preferences.getString(SNAPSHOT_KEY, null)?.let(::decode)

    @Synchronized
    fun begin(providerId: String, providerName: String, targetName: String): SyncTaskSnapshot? {
        if (snapshot()?.isRunning == true) return null
        return SyncTaskSnapshot(
            providerId = providerId,
            providerName = providerName,
            targetName = targetName,
            status = SyncTaskStatus.RUNNING,
            completed = 0,
            total = 0,
            message = "正在准备同步…",
            startedAt = System.currentTimeMillis(),
            finishedAt = 0L
        ).also(::save)
    }

    @Synchronized
    fun updateProgress(progress: DriveSyncProgress): SyncTaskSnapshot? = snapshot()?.takeIf { it.isRunning }?.copy(
        completed = progress.completed,
        total = progress.total,
        message = progress.message
    )?.also(::save)

    @Synchronized
    fun requestCancellation(): SyncTaskSnapshot? = snapshot()?.takeIf { it.isRunning }?.copy(
        message = "将在当前文件完成后取消…"
    )?.also(::save)

    @Synchronized
    fun finish(result: DriveSyncResult): SyncTaskSnapshot? = snapshot()?.takeIf { it.isRunning }?.copy(
        status = when {
            result.cancelled -> SyncTaskStatus.CANCELLED
            result.errors.isNotEmpty() -> SyncTaskStatus.FAILED
            else -> SyncTaskStatus.SUCCEEDED
        },
        message = finishMessage(result),
        finishedAt = System.currentTimeMillis(),
        summary = SyncTaskSummary(result.uploaded, result.downloaded, result.unchanged, result.conflicts),
        errors = result.errors.take(MAX_ERROR_DETAILS)
    )?.also(::save)

    /** A new service instance means an earlier running task cannot be trusted to have completed. */
    @Synchronized
    fun markInterruptedIfRunning(): SyncTaskSnapshot? = snapshot()?.takeIf { it.isRunning }?.copy(
        status = SyncTaskStatus.INTERRUPTED,
        message = "后台同步已中断，请重试",
        finishedAt = System.currentTimeMillis(),
        errors = listOf("应用或系统中断了后台同步；本地文件保持不变，请重新开始同步。")
    )?.also(::save)

    private fun finishMessage(result: DriveSyncResult): String = when {
        result.cancelled -> "同步已取消"
        result.errors.isNotEmpty() -> "${result.errors.size} 个文件未完成"
        result.conflicts > 0 -> "同步完成，已保留冲突副本"
        else -> "同步完成"
    }

    private fun save(value: SyncTaskSnapshot) {
        preferences.edit().putString(SNAPSHOT_KEY, encode(value).toString()).apply()
    }

    private fun encode(value: SyncTaskSnapshot): JSONObject = JSONObject().apply {
        put("providerId", value.providerId)
        put("providerName", value.providerName)
        put("targetName", value.targetName)
        put("status", value.status.name)
        put("completed", value.completed)
        put("total", value.total)
        put("message", value.message)
        put("startedAt", value.startedAt)
        put("finishedAt", value.finishedAt)
        put("uploaded", value.summary.uploaded)
        put("downloaded", value.summary.downloaded)
        put("unchanged", value.summary.unchanged)
        put("conflicts", value.summary.conflicts)
        put("errors", JSONArray(value.errors))
    }

    private fun decode(raw: String): SyncTaskSnapshot? = try {
        val value = JSONObject(raw)
        val errors = value.optJSONArray("errors") ?: JSONArray()
        SyncTaskSnapshot(
            providerId = value.getString("providerId"),
            providerName = value.getString("providerName"),
            targetName = value.getString("targetName"),
            status = SyncTaskStatus.valueOf(value.getString("status")),
            completed = value.optInt("completed"),
            total = value.optInt("total"),
            message = value.optString("message"),
            startedAt = value.optLong("startedAt"),
            finishedAt = value.optLong("finishedAt"),
            summary = SyncTaskSummary(
                value.optInt("uploaded"), value.optInt("downloaded"),
                value.optInt("unchanged"), value.optInt("conflicts")
            ),
            errors = List(errors.length()) { errors.optString(it) }.filter { it.isNotBlank() }
        )
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val PREFERENCES_NAME = "markbook_sync_tasks"
        private const val SNAPSHOT_KEY = "latest_task"
        private const val MAX_ERROR_DETAILS = 5
    }
}
