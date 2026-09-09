package com.markbook.android

import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class DriveSyncProgress(val completed: Int, val total: Int, val message: String)

data class DriveSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val unchanged: Int,
    val conflicts: Int,
    val errors: List<String>,
    val cancelled: Boolean
) {
    val isSuccessful: Boolean get() = errors.isEmpty() && !cancelled
}

/**
 * First Android sync slice. It compares the complete user-owned Vault each time and never
 * propagates deletions. A divergent path keeps both files instead of choosing a silent winner.
 */
class GoogleDriveSyncService(
    private val repository: VaultRepository,
    private val api: GoogleDriveApi,
    private val cancelled: AtomicBoolean = AtomicBoolean(false),
    private val onProgress: (DriveSyncProgress) -> Unit = {}
) {
    fun sync(root: DriveVaultRoot): DriveSyncResult {
        val errors = mutableListOf<String>()
        val remoteFiles = linkedMapOf<String, DriveItem>()
        val remoteFolders = linkedMapOf<String, String>()
        remoteFolders[""] = root.id
        try {
            scanRemote(root.id, "", remoteFiles, remoteFolders)
        } catch (failure: Exception) {
            return DriveSyncResult(0, 0, 0, 0, listOf(userMessage(failure)), false)
        }
        if (cancelled.get()) return DriveSyncResult(0, 0, 0, 0, emptyList(), true)

        val localFiles = repository.syncFiles().associateBy { it.relativePath }
        var uploaded = 0
        var downloaded = 0
        var unchanged = 0
        var conflicts = 0
        val total = localFiles.size + remoteFiles.keys.minus(localFiles.keys).size
        var completed = 0

        localFiles.toSortedMap().forEach { (path, local) ->
            if (cancelled.get()) return result(uploaded, downloaded, unchanged, conflicts, errors, true)
            val remote = remoteFiles[path]
            try {
                when {
                    remote == null -> {
                        report(completed, total, "正在上传 $path")
                        upload(path, local, remoteFolders)
                        uploaded++
                    }
                    remote.md5 != null && remote.md5.equals(md5(repository.openSyncInput(local)), true) -> {
                        unchanged++
                    }
                    else -> {
                        report(completed, total, "发现冲突：$path")
                        val stamp = conflictStamp()
                        val remoteConflictPath = conflictPath(path, "Google Drive", stamp)
                        api.download(remote).use { input ->
                            val saved = repository.writeSyncFileIfAbsent(
                                remoteConflictPath,
                                remote.mimeType,
                                input
                            )
                            if (!saved) throw IllegalStateException("Unable to preserve Drive conflict copy")
                        }
                        val parentId = ensureRemoteFolder(path.substringBeforeLast('/', ""), remoteFolders)
                        api.copy(remote.id, parentId, remoteConflictPath.substringAfterLast('/'))
                        val input = repository.openSyncInput(local)
                            ?: throw IllegalStateException("Unable to read local file")
                        input.use { api.replace(remote.id, local.document.mimeType ?: "application/octet-stream", it) }
                        conflicts++
                    }
                }
            } catch (failure: Exception) {
                errors += "$path: ${userMessage(failure)}"
            }
            completed++
            report(completed, total, "已比较 $completed / $total")
        }

        remoteFiles.toSortedMap().forEach { (path, remote) ->
            if (localFiles.containsKey(path)) return@forEach
            if (cancelled.get()) return result(uploaded, downloaded, unchanged, conflicts, errors, true)
            try {
                report(completed, total, "正在下载 $path")
                val downloadedToOriginalPath = api.download(remote).use { input ->
                    repository.writeSyncFileIfAbsent(path, remote.mimeType, input)
                }
                if (downloadedToOriginalPath) {
                    downloaded++
                } else {
                    val remoteConflictPath = conflictPath(path, "Google Drive", conflictStamp())
                    val preserved = api.download(remote).use { input ->
                        repository.writeSyncFileIfAbsent(remoteConflictPath, remote.mimeType, input)
                    }
                    if (!preserved) throw IllegalStateException("Unable to preserve remote conflict copy")
                    conflicts++
                    report(completed, total, "本地文件在同步期间发生变化，已保留冲突副本：$path")
                }
            } catch (failure: Exception) {
                errors += "$path: ${userMessage(failure)}"
            }
            completed++
            report(completed, total, "已比较 $completed / $total")
        }
        return result(uploaded, downloaded, unchanged, conflicts, errors, false)
    }

    private fun scanRemote(
        parentId: String,
        prefix: String,
        files: MutableMap<String, DriveItem>,
        folders: MutableMap<String, String>
    ) {
        if (cancelled.get()) return
        api.listChildren(parentId).forEach { item ->
            val path = join(prefix, item.name)
            if (!remotePathAllowed(path, item.mimeType == GoogleDriveApi.FOLDER_MIME_TYPE)) return@forEach
            if (item.mimeType == GoogleDriveApi.FOLDER_MIME_TYPE) {
                folders[path] = item.id
                scanRemote(item.id, path, files, folders)
            } else if (!item.mimeType.startsWith("application/vnd.google-apps.")) {
                if (files.put(path, item) != null) {
                    throw DriveApiException("Google Drive contains duplicate paths; rename one before syncing")
                }
            }
        }
    }

    private fun upload(
        path: String,
        local: VaultSyncFile,
        folders: MutableMap<String, String>
    ) {
        val parentPath = path.substringBeforeLast('/', "")
        val parentId = ensureRemoteFolder(parentPath, folders)
        val input = repository.openSyncInput(local) ?: throw IllegalStateException("Unable to read local file")
        input.use { api.upload(parentId, path.substringAfterLast('/'), local.document.mimeType ?: "application/octet-stream", it) }
    }

    private fun ensureRemoteFolder(path: String, folders: MutableMap<String, String>): String {
        folders[path]?.let { return it }
        val parentPath = path.substringBeforeLast('/', "")
        val parent = ensureRemoteFolder(parentPath, folders)
        val name = path.substringAfterLast('/')
        val existing = api.listChildren(parent).firstOrNull {
            it.name == name && it.mimeType == GoogleDriveApi.FOLDER_MIME_TYPE
        }
        val id = (existing ?: api.createFolder(parent, name)).id
        folders[path] = id
        return id
    }

    private fun md5(input: InputStream?): String? {
        if (input == null) return null
        return input.use {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }

    private fun conflictPath(path: String, source: String, stamp: String): String {
        val parent = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        return join(parent, "$stem ($source conflict $stamp)$extension")
    }

    private fun remotePathAllowed(path: String, directory: Boolean): Boolean {
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." || it.contains('\\') }) return false
        return SyncPathPolicy.isAllowed(path, directory)
    }

    private fun join(parent: String, name: String): String = if (parent.isBlank()) name else "$parent/$name"

    private fun conflictStamp(): String = SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.US).format(Date())

    private fun report(completed: Int, total: Int, message: String) = onProgress(DriveSyncProgress(completed, total, message))

    private fun result(
        uploaded: Int, downloaded: Int, unchanged: Int, conflicts: Int, errors: List<String>, cancelled: Boolean
    ) = DriveSyncResult(uploaded, downloaded, unchanged, conflicts, errors, cancelled)

    private fun userMessage(failure: Exception): String = when (failure) {
        is DriveApiException -> failure.message ?: "Google Drive request failed"
        else -> "无法完成此文件"
    }
}
