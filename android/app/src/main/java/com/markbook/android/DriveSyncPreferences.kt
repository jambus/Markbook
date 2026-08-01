package com.markbook.android

import android.content.Context

data class DriveVaultRoot(val id: String, val name: String)

/** Stores only non-sensitive remote selection and completion metadata. */
class DriveSyncPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("markbook_drive_sync", Context.MODE_PRIVATE)

    fun root(): DriveVaultRoot? {
        val id = preferences.getString(ROOT_ID_KEY, null) ?: return null
        val name = preferences.getString(ROOT_NAME_KEY, null) ?: "Google Drive"
        return DriveVaultRoot(id, name)
    }

    fun setRoot(root: DriveVaultRoot) {
        preferences.edit().putString(ROOT_ID_KEY, root.id).putString(ROOT_NAME_KEY, root.name).apply()
    }

    fun clearRoot() {
        preferences.edit().remove(ROOT_ID_KEY).remove(ROOT_NAME_KEY).remove(LAST_SUCCESS_KEY).apply()
    }

    fun lastSuccessAt(): Long = preferences.getLong(LAST_SUCCESS_KEY, 0L)

    fun markSuccessful() {
        preferences.edit().putLong(LAST_SUCCESS_KEY, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val ROOT_ID_KEY = "root_id"
        private const val ROOT_NAME_KEY = "root_name"
        private const val LAST_SUCCESS_KEY = "last_success"
    }
}
