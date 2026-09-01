package com.markbook.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.Locale

class CaptureFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = when (captureFile(uri)?.extension?.lowercase(Locale.ROOT)) {
        "mp4" -> "video/mp4"
        "3gp", "3gpp" -> "video/3gpp"
        "png" -> "image/png"
        else -> "image/jpeg"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = captureFile(uri) ?: return null
        file.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = captureFile(uri) ?: return null
        val columns = projection ?: arrayOf("_display_name", "_size")
        val cursor = MatrixCursor(columns)
        val values = columns.map { column ->
            when (column) {
                "_display_name" -> file.name
                "_size" -> file.length()
                else -> null
            }
        }.toTypedArray()
        cursor.addRow(values)
        return cursor
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val file = captureFile(uri) ?: return 0
        return if (file.delete()) 1 else 0
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun captureFile(uri: Uri): File? {
        val name = uri.pathSegments.lastOrNull() ?: return null
        if (name.contains("..") || name.contains('/')) return null
        return File(requireContext().cacheDir, "camera/$name")
    }
}
