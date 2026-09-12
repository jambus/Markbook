package com.jambus.heji

import android.content.Context

data class DriveVaultRoot(val id: String, val name: String)

/** Stores only non-sensitive remote selection and completion metadata. */
class DriveSyncPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("heji_notes_drive_sync", Context.MODE_PRIVATE)

    fun root(vaultId: String, accountId: String): DriveVaultRoot? {
        if (vaultId.isBlank() || accountId.isBlank() || preferences.getString(VAULT_ID_KEY, null) != vaultId ||
            preferences.getString(ACCOUNT_ID_KEY, null) != accountId) return null
        val id = preferences.getString(ROOT_ID_KEY, null) ?: return null
        val name = preferences.getString(ROOT_NAME_KEY, null) ?: "Google Drive"
        return DriveVaultRoot(id, name)
    }

    fun setRoot(root: DriveVaultRoot, vaultId: String, accountId: String) {
        preferences.edit().putString(ROOT_ID_KEY, root.id).putString(ROOT_NAME_KEY, root.name)
            .putString(VAULT_ID_KEY, vaultId).putString(ACCOUNT_ID_KEY, accountId).apply()
    }

    fun clearRoot() {
        preferences.edit().remove(ROOT_ID_KEY).remove(ROOT_NAME_KEY).remove(VAULT_ID_KEY).remove(ACCOUNT_ID_KEY).remove(LAST_SUCCESS_KEY).apply()
    }

    fun lastSuccessAt(vaultId: String, rootId: String, accountId: String): Long = if (
        preferences.getString(VAULT_ID_KEY, null) == vaultId && preferences.getString(ROOT_ID_KEY, null) == rootId && preferences.getString(ACCOUNT_ID_KEY, null) == accountId
    ) preferences.getLong(LAST_SUCCESS_KEY, 0L) else 0L

    fun markSuccessful(vaultId: String, rootId: String, accountId: String) {
        if (preferences.getString(VAULT_ID_KEY, null) != vaultId || preferences.getString(ROOT_ID_KEY, null) != rootId || preferences.getString(ACCOUNT_ID_KEY, null) != accountId) return
        preferences.edit().putLong(LAST_SUCCESS_KEY, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val ROOT_ID_KEY = "root_id"
        private const val ROOT_NAME_KEY = "root_name"
        private const val VAULT_ID_KEY = "vault_id"
        private const val ACCOUNT_ID_KEY = "account_id"
        private const val LAST_SUCCESS_KEY = "last_success"
    }
}
