package com.markbook.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class LocalChangeState { PREPARED, COMMITTED }

data class MoveBundleChange(
    val id: String,
    val vaultId: String,
    val sourceToTarget: Map<String, String>,
    val bundleSourceToTarget: Map<String, String> = emptyMap(),
    val beforeSha256: Map<String, String>,
    val afterSha256: Map<String, String>,
    val state: LocalChangeState,
    val committedAt: Long = 0L
)

/** Provider-neutral local history. Corruption fails closed instead of becoming an empty journal. */
interface MoveChangeStore {
    fun changes(vaultId: String): List<MoveBundleChange>?
    fun acknowledge(id: String): Boolean
}

class LocalChangeJournal(context: Context) : MoveChangeStore {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    @Synchronized override fun changes(vaultId: String): List<MoveBundleChange>? = load()?.filter { it.vaultId == vaultId }

    @Synchronized fun prepare(change: MoveBundleChange): Boolean {
        val all = load() ?: return false
        return save(all.filterNot { it.id == change.id } + change.copy(state = LocalChangeState.PREPARED))
    }

    @Synchronized fun commit(id: String): Boolean {
        val all = load() ?: return false
        val current = all.firstOrNull { it.id == id } ?: return false
        return save(all.filterNot { it.id == id } + current.copy(state = LocalChangeState.COMMITTED, committedAt = System.currentTimeMillis()))
    }

    @Synchronized override fun acknowledge(id: String): Boolean {
        val all = load() ?: return false
        return save(all.filterNot { it.id == id })
    }

    private fun load(): List<MoveBundleChange>? {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> decode(array.getJSONObject(index)) }
        }.getOrNull()
    }

    private fun save(values: List<MoveBundleChange>): Boolean {
        val array = JSONArray()
        values.sortedBy { it.id }.forEach { array.put(encode(it)) }
        return preferences.edit().putString(KEY, array.toString()).commit()
    }

    private fun encode(value: MoveBundleChange) = JSONObject()
        .put("schema", 1).put("id", value.id).put("vaultId", value.vaultId)
        .put("mapping", JSONObject(value.sourceToTarget)).put("bundles", JSONObject(value.bundleSourceToTarget)).put("before", JSONObject(value.beforeSha256))
        .put("after", JSONObject(value.afterSha256)).put("state", value.state.name)
        .put("committedAt", value.committedAt)

    private fun decode(value: JSONObject): MoveBundleChange {
        require(value.getInt("schema") == 1)
        return MoveBundleChange(
            value.getString("id"), value.getString("vaultId"), value.getJSONObject("mapping").stringMap(),
            (value.optJSONObject("bundles") ?: JSONObject()).stringMap(), value.getJSONObject("before").stringMap(), value.getJSONObject("after").stringMap(),
            LocalChangeState.valueOf(value.getString("state")), value.optLong("committedAt")
        )
    }

    private fun JSONObject.stringMap(): Map<String, String> = keys().asSequence().associateWith { getString(it) }

    private companion object { const val NAME = "markbook_local_changes"; const val KEY = "journal" }
}
