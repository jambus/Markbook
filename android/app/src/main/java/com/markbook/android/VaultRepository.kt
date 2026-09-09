package com.markbook.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.security.SecureRandom

data class VaultDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val parentUri: Uri? = null,
    val relativePath: String = ""
) {
    val parentRelativePath: String
        get() = relativePath.substringBeforeLast('/', "")
}

data class PhotoAttachments(
    val original: String,
    val corrected: String,
    val relativeDirectory: String,
    val transactionUri: Uri
)

data class VideoAttachment(
    val name: String,
    val relativeDirectory: String,
    val transactionUri: Uri
)

data class VaultSyncFile(
    val relativePath: String,
    val document: VaultDocument
)

enum class VaultFailureKind {
    PERMISSION_DENIED,
    READ_FAILED
}

sealed class NoteReadResult {
    data class Success(val content: String) : NoteReadResult()
    data class Failure(val kind: VaultFailureKind) : NoteReadResult()
}

sealed class DailyNoteResult {
    data class Existing(val document: VaultDocument) : DailyNoteResult()
    data class Created(val document: VaultDocument) : DailyNoteResult()
    data class Failure(val kind: VaultFailureKind) : DailyNoteResult()
}

sealed class VaultRecoveryResult {
    object Success : VaultRecoveryResult()
    data class Failure(val kind: VaultFailureKind) : VaultRecoveryResult()
}

sealed class TrashContentsResult {
    data class Success(val count: Int) : TrashContentsResult()
    data class Failure(val kind: VaultFailureKind) : TrashContentsResult()
}

data class TrashClearResult(
    val deleted: Int,
    val failed: Int,
    val remaining: Int
)

enum class VaultMutationFailureKind {
    PERMISSION_DENIED,
    READ_FAILED,
    INVALID_NAME,
    NAME_CONFLICT,
    CREATE_FAILED,
    RENAME_FAILED,
    TRASH_NAME_CONFLICT,
    MOVE_UNSUPPORTED
}

enum class DailyDirectoryChange { UNCHANGED, REWRITTEN, RESET_TO_ROOT }

sealed class VaultMutationResult {
    data class Success(
        val document: VaultDocument?,
        val requestedName: String,
        val actualName: String?,
        val dailyDirectoryChange: DailyDirectoryChange = DailyDirectoryChange.UNCHANGED
    ) : VaultMutationResult()

    data class Failure(val kind: VaultMutationFailureKind, val message: String? = null) : VaultMutationResult()
}

class VaultRepository(private val context: Context, private val fixedVaultUri: Uri? = null) {
    private val resolver: ContentResolver = context.contentResolver
    private val preferences = context.getSharedPreferences("markbook", Context.MODE_PRIVATE)

    fun savedVaultUri(): Uri? = fixedVaultUri ?: preferences.getString(VAULT_URI_KEY, null)?.let(Uri::parse)

    fun rememberVault(uri: Uri) {
        val flags = IntentFlags.READ_WRITE
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some HarmonyOS 4 builds grant the tree without exposing persistable flags.
        }
        preferences.edit().putString(VAULT_URI_KEY, uri.toString()).apply()
    }

    fun clearVault() {
        preferences.edit().remove(VAULT_URI_KEY).apply()
    }

    fun appearanceMode(): String = preferences.getString(APPEARANCE_MODE_KEY, APPEARANCE_NIGHT)
        ?: APPEARANCE_NIGHT

    fun setAppearanceMode(mode: String) {
        if (mode !in setOf(APPEARANCE_DAY, APPEARANCE_NIGHT)) return
        preferences.edit().putString(APPEARANCE_MODE_KEY, mode).apply()
    }

    fun swipeDiscoveryHintSeen(): Boolean = preferences.getBoolean(SWIPE_DISCOVERY_HINT_SEEN_KEY, false)

    fun markSwipeDiscoveryHintSeen() {
        preferences.edit().putBoolean(SWIPE_DISCOVERY_HINT_SEEN_KEY, true).apply()
    }

    fun dailyNoteDirectoryPath(): String = preferences.getString(
        DAILY_NOTE_DIRECTORY_KEY,
        DEFAULT_DAILY_NOTE_DIRECTORY
    ) ?: DEFAULT_DAILY_NOTE_DIRECTORY

    fun dailyNoteDirectoryParts(): List<String> = dailyNoteDirectoryPath()
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "." && it != ".." }

    fun setDailyNoteDirectory(parts: List<String>) {
        val cleanParts = parts.map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        preferences.edit()
            .putString(DAILY_NOTE_DIRECTORY_KEY, cleanParts.joinToString("/"))
            .remove(DAILY_DIRECTORY_RESET_NOTICE_KEY)
            .apply()
    }

    fun dailyDirectoryResetNotice(): Boolean = preferences.getBoolean(DAILY_DIRECTORY_RESET_NOTICE_KEY, false)

    fun vaultRoot(): VaultDocument? {
        val tree = savedVaultUri() ?: return null
        val root = rootDocument(tree)
        val name = try {
            resolver.query(
                root,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        return VaultDocument(root, name?.takeIf { it.isNotBlank() } ?: "Vault", DocumentsContract.Document.MIME_TYPE_DIR)
    }

    fun children(directory: VaultDocument): List<VaultDocument> {
        val tree = savedVaultUri() ?: return emptyList()
        return listChildren(tree, directory.uri, directory.relativePath)
    }

    fun canReadDirectory(directory: VaultDocument): Boolean {
        val tree = savedVaultUri() ?: return false
        val parentId = try {
            DocumentsContract.getDocumentId(directory.uri)
        } catch (_: Exception) {
            return false
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return try {
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isDirectory(document: VaultDocument): Boolean =
        document.mimeType == DocumentsContract.Document.MIME_TYPE_DIR

    fun refreshDocument(document: VaultDocument): VaultDocument? {
        val tree = savedVaultUri() ?: return null
        val parent = document.parentUri ?: return null
        return findChild(tree, parent, document.name, document.parentRelativePath)
    }

    fun relativeAttachmentPath(note: VaultDocument, attachments: PhotoAttachments): String {
        return VaultRelativePath.attachmentPath(
            note.parentRelativePath,
            "${attachments.relativeDirectory}/${attachments.corrected}"
        )
    }

    fun relativeAttachmentPath(note: VaultDocument, attachment: VideoAttachment): String =
        VaultRelativePath.attachmentPath(note.parentRelativePath, "${attachment.relativeDirectory}/${attachment.name}")

    fun savePendingVideoCapture(session: PendingVideoCaptureSession) {
        preferences.edit()
            .putString(PENDING_VIDEO_NOTE_URI, session.noteUri)
            .putString(PENDING_VIDEO_NOTE_PATH, session.noteRelativePath)
            .putString(PENDING_VIDEO_HASH, session.contentSha256)
            .putInt(PENDING_VIDEO_CARET, session.caretOffset)
            .putInt(PENDING_VIDEO_SCROLL, session.scrollY)
            .putString(PENDING_VIDEO_CACHE, session.cachePath)
            .putString(PENDING_VIDEO_STAGE, session.stage)
            .putString(PENDING_VIDEO_ATTACHMENT_PATH, session.attachmentVaultPath)
            .commit()
    }

    fun pendingVideoCapture(): PendingVideoCaptureSession? {
        val noteUri = preferences.getString(PENDING_VIDEO_NOTE_URI, null) ?: return null
        val notePath = preferences.getString(PENDING_VIDEO_NOTE_PATH, null) ?: return null
        val hash = preferences.getString(PENDING_VIDEO_HASH, null) ?: return null
        val cachePath = preferences.getString(PENDING_VIDEO_CACHE, null) ?: return null
        val stage = preferences.getString(PENDING_VIDEO_STAGE, null) ?: return null
        return PendingVideoCaptureSession(
            noteUri, notePath, hash, preferences.getInt(PENDING_VIDEO_CARET, 0),
            preferences.getInt(PENDING_VIDEO_SCROLL, 0), cachePath, stage,
            preferences.getString(PENDING_VIDEO_ATTACHMENT_PATH, null)
        )
    }

    fun clearPendingVideoCapture() {
        preferences.edit().remove(PENDING_VIDEO_NOTE_URI).remove(PENDING_VIDEO_NOTE_PATH)
            .remove(PENDING_VIDEO_HASH).remove(PENDING_VIDEO_CARET).remove(PENDING_VIDEO_SCROLL)
            .remove(PENDING_VIDEO_CACHE).remove(PENDING_VIDEO_STAGE).remove(PENDING_VIDEO_ATTACHMENT_PATH).commit()
    }

    fun openDailyNote(): DailyNoteResult {
        return try {
            val tree = savedVaultUri()
                ?: return DailyNoteResult.Failure(VaultFailureKind.PERMISSION_DENIED)
            val directory = findOrCreateDirectory(tree, dailyNoteDirectoryParts())
                ?: return DailyNoteResult.Failure(VaultFailureKind.READ_FAILED)
            val name = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".md"
            val dailyPath = dailyNoteDirectoryParts().joinToString("/")
            val existing = findChild(tree, directory, name, dailyPath)
            if (existing != null) return DailyNoteResult.Existing(existing)
            val created = DocumentsContract.createDocument(
                resolver,
                directory,
                "text/markdown",
                name
            ) ?: return DailyNoteResult.Failure(VaultFailureKind.READ_FAILED)
            DailyNoteResult.Created(VaultDocument(
                created,
                name,
                "text/markdown",
                directory,
                (dailyNoteDirectoryParts() + name).joinToString("/")
            ))
        } catch (_: SecurityException) {
            DailyNoteResult.Failure(VaultFailureKind.PERMISSION_DENIED)
        } catch (_: Exception) {
            DailyNoteResult.Failure(VaultFailureKind.READ_FAILED)
        }
    }

    fun dailyNote(): VaultDocument? = when (val result = openDailyNote()) {
        is DailyNoteResult.Created -> result.document
        is DailyNoteResult.Existing -> result.document
        is DailyNoteResult.Failure -> null
    }

    fun readNote(document: VaultDocument): NoteReadResult {
        return try {
            val content = resolver.openInputStream(document.uri)?.use {
                String(it.readBytes(), StandardCharsets.UTF_8)
            } ?: return NoteReadResult.Failure(VaultFailureKind.READ_FAILED)
            NoteReadResult.Success(content)
        } catch (_: SecurityException) {
            NoteReadResult.Failure(VaultFailureKind.PERMISSION_DENIED)
        } catch (_: Exception) {
            NoteReadResult.Failure(VaultFailureKind.READ_FAILED)
        }
    }

    fun readText(document: VaultDocument): String? = when (val result = readNote(document)) {
        is NoteReadResult.Success -> result.content
        is NoteReadResult.Failure -> null
    }

    /** Resolves interrupted Markbook transactions throughout the user-editable Vault. */
    fun recoverVault(): VaultRecoveryResult {
        return try {
            val tree = savedVaultUri()
                ?: return VaultRecoveryResult.Failure(VaultFailureKind.PERMISSION_DENIED)
            recoverVaultDirectory(tree, rootDocument(tree), "")
            recoverPendingVideoCapture(tree)
            VaultRecoveryResult.Success
        } catch (_: SecurityException) {
            VaultRecoveryResult.Failure(VaultFailureKind.PERMISSION_DENIED)
        } catch (_: Exception) {
            VaultRecoveryResult.Failure(VaultFailureKind.READ_FAILED)
        }
    }

    /**
     * Reads at most [maxBytes] from a note. List rows only need the opening lines, so they must not
     * pay for reading an entire document.
     */
    fun readPreview(document: VaultDocument, maxBytes: Int): String? = try {
        resolver.openInputStream(document.uri)?.use { stream ->
            val buffer = ByteArray(maxBytes)
            var read = 0
            while (read < maxBytes) {
                val count = stream.read(buffer, read, maxBytes - read)
                if (count <= 0) break
                read += count
            }
            String(buffer, 0, read, StandardCharsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }

    fun saveText(document: VaultDocument, content: String): Boolean {
        val parent = document.parentUri ?: return false
        val tempName = ".markbook-${UUID.randomUUID()}.tmp"
        val temp = DocumentsContract.createDocument(resolver, parent, "text/plain", tempName)
            ?: return false
        var backup: Uri? = null
        return try {
            resolver.openOutputStream(temp, "wt")?.use { output ->
                output.write(content.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            } ?: throw IllegalStateException("Unable to open temporary note")

            val backupName = ".markbook-${document.name}.bak"
            backup = DocumentsContract.renameDocument(resolver, document.uri, backupName)
            val committed = DocumentsContract.renameDocument(resolver, temp, document.name)
            if (committed == null) throw IllegalStateException("Unable to commit note")
            if (backup != null) {
                try {
                    DocumentsContract.deleteDocument(resolver, backup)
                } catch (_: Exception) {
                    // The committed note is valid; the next launch can clean the backup.
                }
            }
            true
        } catch (_: Exception) {
            try {
                if (backup != null) DocumentsContract.renameDocument(resolver, backup, document.name)
                DocumentsContract.deleteDocument(resolver, temp)
            } catch (_: Exception) {
                // Recovery is retried on the next launch when the tree is available.
            }
            false
        }
    }

    fun saveText(uri: Uri, name: String, content: String, parentUri: Uri): Boolean =
        saveText(VaultDocument(uri, name, "text/markdown", parentUri), content)

    fun createNote(parent: VaultDocument, requestedName: String): VaultMutationResult =
        createChild(parent, requestedName, VaultEntryKind.NOTE)

    fun createFolder(parent: VaultDocument, requestedName: String): VaultMutationResult =
        createChild(parent, requestedName, VaultEntryKind.FOLDER)

    fun rename(document: VaultDocument, requestedName: String): VaultMutationResult {
        if (!isManageable(document)) return VaultMutationResult.Failure(VaultMutationFailureKind.RENAME_FAILED)
        val parent = document.parentUri ?: return VaultMutationResult.Failure(VaultMutationFailureKind.RENAME_FAILED)
        val kind = if (isDirectory(document)) VaultEntryKind.FOLDER else VaultEntryKind.NOTE
        val validated = VaultNamePolicy.validate(requestedName, kind)
        if (validated is VaultNameValidation.Invalid) {
            return VaultMutationResult.Failure(VaultMutationFailureKind.INVALID_NAME, validated.message)
        }
        val validName = validated as VaultNameValidation.Valid
        if (kind == VaultEntryKind.FOLDER && document.parentRelativePath.isEmpty() && VaultPathPolicy.isProtectedRootName(validName.actualName)) {
            return VaultMutationResult.Failure(VaultMutationFailureKind.INVALID_NAME, "该名称由 Vault 保留")
        }
        return try {
            val tree = savedVaultUri() ?: return VaultMutationResult.Failure(VaultMutationFailureKind.PERMISSION_DENIED)
            val existing = listChildrenStrict(tree, parent, document.parentRelativePath).map { it.name }
            if (VaultNamePolicy.conflicts(validName, existing, document.name)) {
                return VaultMutationResult.Failure(VaultMutationFailureKind.NAME_CONFLICT, "当前目录已有同名笔记或文件夹")
            }
            val renamed = DocumentsContract.renameDocument(resolver, document.uri, validName.actualName)
                ?: return VaultMutationResult.Failure(VaultMutationFailureKind.RENAME_FAILED)
            val resolved = resolveMutationDocument(parent, document.parentRelativePath, renamed)
            val actualName = resolved?.name
            val dailyChange = if (kind == VaultEntryKind.FOLDER) {
                if (resolved != null) rewriteDailyDirectoryAfterRenameCommitted(document.relativePath, resolved.relativePath)
                else resetDailyDirectoryIfRemovedCommitted(document.relativePath)
            } else DailyDirectoryChange.UNCHANGED
            VaultMutationResult.Success(
                resolved,
                validName.actualName,
                actualName,
                dailyChange
            )
        } catch (_: SecurityException) {
            VaultMutationResult.Failure(VaultMutationFailureKind.PERMISSION_DENIED)
        } catch (_: Exception) {
            VaultMutationResult.Failure(VaultMutationFailureKind.RENAME_FAILED)
        }
    }

    /** Moves one direct child atomically; a provider that cannot move leaves the source untouched. */
    fun moveToTrash(document: VaultDocument): VaultMutationResult {
        if (!isManageable(document)) return VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
        val sourceParent = document.parentUri ?: return VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
        return try {
            val tree = savedVaultUri() ?: return VaultMutationResult.Failure(VaultMutationFailureKind.PERMISSION_DENIED)
            val trash = findOrCreateDirectory(tree, listOf(".trash"))
                ?: return VaultMutationResult.Failure(VaultMutationFailureKind.CREATE_FAILED)
            val moved = try {
                DocumentsContract.moveDocument(resolver, document.uri, sourceParent, trash)
            } catch (error: SecurityException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (moved != null) {
                val resolved = resolveMutationDocument(trash, ".trash", moved)
                val dailyChange = if (isDirectory(document)) resetDailyDirectoryIfRemovedCommitted(document.relativePath) else DailyDirectoryChange.UNCHANGED
                return VaultMutationResult.Success(resolved, document.name, resolved?.name, dailyChange)
            }
            if (isDirectory(document)) return VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
            moveNoteToTrashByCopy(tree, document, trash)
        } catch (_: SecurityException) {
            VaultMutationResult.Failure(VaultMutationFailureKind.PERMISSION_DENIED)
        } catch (_: Exception) {
            VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
        }
    }

    fun trashContents(): TrashContentsResult {
        return try {
            val tree = savedVaultUri()
                ?: return TrashContentsResult.Failure(VaultFailureKind.PERMISSION_DENIED)
            val trash = findChildStrict(tree, rootDocument(tree), ".trash")
                ?: return TrashContentsResult.Success(0)
            TrashContentsResult.Success(listChildrenStrict(tree, trash.uri).size)
        } catch (_: SecurityException) {
            TrashContentsResult.Failure(VaultFailureKind.PERMISSION_DENIED)
        } catch (_: Exception) {
            TrashContentsResult.Failure(VaultFailureKind.READ_FAILED)
        }
    }

    /** Permanently deletes only the direct children of the Vault-root .trash directory. */
    fun emptyTrash(): TrashClearResult {
        val tree = savedVaultUri() ?: return TrashClearResult(0, 1, 0)
        return try {
            val trash = findChildStrict(tree, rootDocument(tree), ".trash")
                ?: return TrashClearResult(0, 0, 0)
            val children = listChildrenStrict(tree, trash.uri)
            val counter = TrashClearCounter()
            children.forEach { document ->
                val success = try {
                    DocumentsContract.deleteDocument(resolver, document.uri)
                } catch (_: Exception) {
                    false
                }
                counter.record(success)
            }
            val remaining = try {
                listChildrenStrict(tree, trash.uri).size
            } catch (_: Exception) {
                children.size
            }
            counter.result(remaining)
        } catch (_: Exception) {
            TrashClearResult(0, 1, 0)
        }
    }

    fun savePhotoPair(
        noteName: String,
        original: InputStream,
        corrected: ByteArray,
        extension: String = "jpg"
    ): PhotoAttachments? {
        val tree = savedVaultUri() ?: return null
        val noteFolder = assetFolderName(noteName)
        val relativeDirectory = "assets/$noteFolder"
        val directory = findOrCreateDirectory(tree, listOf("assets", noteFolder)) ?: return null
        val id = nextCaptureId(tree, directory) ?: return null
        val originalName = "$id-o.$extension"
        val correctedName = "$id-c.$extension"
        val marker = DocumentsContract.createDocument(
            resolver, directory, "text/plain", ".markbook-$id.txn"
        ) ?: return null
        val originalTemp = DocumentsContract.createDocument(
            resolver, directory, "image/jpeg", ".markbook-${UUID.randomUUID()}.tmp"
        ) ?: run {
            try { DocumentsContract.deleteDocument(resolver, marker) } catch (_: Exception) { }
            return null
        }
        val correctedTemp = DocumentsContract.createDocument(
            resolver, directory, "image/jpeg", ".markbook-${UUID.randomUUID()}.tmp"
        )
        if (correctedTemp == null) {
            try { DocumentsContract.deleteDocument(resolver, originalTemp) } catch (_: Exception) { }
            try { DocumentsContract.deleteDocument(resolver, marker) } catch (_: Exception) { }
            return null
        }
        var committedOriginal: Uri? = null
        var committedCorrected: Uri? = null
        return try {
            resolver.openOutputStream(marker, "wt")?.use { output ->
                output.write("$originalName\n$correctedName".toByteArray(StandardCharsets.UTF_8))
            } ?: throw IllegalStateException("Unable to write photo transaction")
            resolver.openOutputStream(originalTemp, "wt")?.use { output -> original.copyTo(output) }
                ?: throw IllegalStateException("Unable to write original")
            resolver.openOutputStream(correctedTemp, "wt")?.use { output -> output.write(corrected) }
                ?: throw IllegalStateException("Unable to write corrected")
            committedOriginal = DocumentsContract.renameDocument(resolver, originalTemp, originalName)
                ?: throw IllegalStateException("Unable to commit original")
            committedCorrected = DocumentsContract.renameDocument(resolver, correctedTemp, correctedName)
                ?: throw IllegalStateException("Unable to commit corrected")
            PhotoAttachments(originalName, correctedName, relativeDirectory, marker)
        } catch (_: Exception) {
            try { DocumentsContract.deleteDocument(resolver, marker) } catch (_: Exception) { }
            try { DocumentsContract.deleteDocument(resolver, originalTemp) } catch (_: Exception) { }
            try { DocumentsContract.deleteDocument(resolver, correctedTemp) } catch (_: Exception) { }
            try { committedOriginal?.let { DocumentsContract.deleteDocument(resolver, it) } } catch (_: Exception) { }
            try { committedCorrected?.let { DocumentsContract.deleteDocument(resolver, it) } } catch (_: Exception) { }
            null
        }
    }

    fun confirmPhotoPair(attachments: PhotoAttachments) {
        try { DocumentsContract.deleteDocument(resolver, attachments.transactionUri) } catch (_: Exception) { }
    }

    fun rollbackPhotoPair(attachments: PhotoAttachments) {
        val tree = savedVaultUri() ?: return
        val directory = findByRelativePath(tree, attachments.relativeDirectory)?.uri ?: return
        var allDeleted = true
        listOf(attachments.original, attachments.corrected).forEach { name ->
            findChild(tree, directory, name)?.let {
                try { if (!DocumentsContract.deleteDocument(resolver, it.uri)) allDeleted = false } catch (_: Exception) { allDeleted = false }
            }
        }
        if (allDeleted) try { DocumentsContract.deleteDocument(resolver, attachments.transactionUri) } catch (_: Exception) { }
    }

    /** Writes one original system-camera video under an attachment marker. Cache ownership remains with caller. */
    fun saveVideoAttachment(
        noteName: String,
        cacheFile: File,
        extension: String,
        onProgress: (copiedBytes: Long) -> Boolean
    ): VideoAttachment? {
        if (!cacheFile.isFile || cacheFile.length() <= 0L) return null
        val tree = savedVaultUri() ?: return null
        val noteFolder = assetFolderName(noteName)
        val relativeDirectory = "assets/$noteFolder"
        val directory = findOrCreateDirectory(tree, listOf("assets", noteFolder)) ?: return null
        val id = nextCaptureId(tree, directory) ?: return null
        val name = "$id-v.$extension"
        val marker = DocumentsContract.createDocument(resolver, directory, "text/plain", ".markbook-$id.txn") ?: return null
        val markerPayload = VideoTransactionPolicy.markerPayload(name)
        if (!VideoTransactionPolicy.mayCreateTemporary(markerPayload)) return null
        val markerWritten = try {
            resolver.openOutputStream(marker, "wt")?.use { output ->
                output.write(markerPayload.toByteArray(StandardCharsets.UTF_8))
                output.flush()
            } != null
        } catch (_: Exception) {
            false
        }
        if (!markerWritten) {
            try { DocumentsContract.deleteDocument(resolver, marker) } catch (_: Exception) { }
            return null
        }
        val mimeType = if (extension.equals("3gp", ignoreCase = true)) "video/3gpp" else "video/mp4"
        val temporary = DocumentsContract.createDocument(
            resolver, directory, mimeType, ".markbook-${UUID.randomUUID()}.tmp"
        ) ?: run {
            try { DocumentsContract.deleteDocument(resolver, marker) } catch (_: Exception) { }
            return null
        }
        var finalRenameAttempted = false
        return try {
            FileInputStream(cacheFile).use { input ->
                resolver.openOutputStream(temporary, "wt")?.use { output ->
                    val buffer = ByteArray(DEFAULT_SYNC_BUFFER_BYTES)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (!onProgress(copied)) throw VideoCopyCancelledException()
                    }
                    output.flush()
                } ?: throw IllegalStateException("Unable to write video")
            }
            finalRenameAttempted = true
            val committed = DocumentsContract.renameDocument(resolver, temporary, name)
                ?: throw IllegalStateException("Unable to commit video")
            VideoAttachment(name, relativeDirectory, marker)
        } catch (_: VideoCopyCancelledException) {
            cleanupVideoTransactionBeforeFinalRename(tree, directory, name, temporary, marker, finalRenameAttempted)
            null
        } catch (_: Exception) {
            cleanupVideoTransactionBeforeFinalRename(tree, directory, name, temporary, marker, finalRenameAttempted)
            null
        }
    }

    /** Returns false when marker cleanup could not complete and recovery must retain it. */
    fun confirmVideoAttachment(attachment: VideoAttachment, cacheFile: File): Boolean {
        if (cacheFile.exists() && !cacheFile.delete()) return false
        return try { DocumentsContract.deleteDocument(resolver, attachment.transactionUri) } catch (_: Exception) { false }
    }

    fun noteReferencesPendingVideo(note: VaultDocument, session: PendingVideoCaptureSession): Boolean {
        val attachment = session.attachmentVaultPath ?: return false
        val relativePath = VaultRelativePath.attachmentPath(note.parentRelativePath, attachment)
        val content = readText(note) ?: return false
        return PendingVideoLinkPolicy.isReferenced(content, relativePath)
    }

    /** Returns true once a saved link is found, even when best-effort cleanup must retry later. */
    fun cleanupPendingVideoCaptureIfCommitted(note: VaultDocument, session: PendingVideoCaptureSession): Boolean {
        if (!noteReferencesPendingVideo(note, session)) return false
        val tree = savedVaultUri() ?: return true
        cleanupCompletedPendingVideoCapture(tree, session)
        return true
    }

    private fun cleanupVideoTransactionBeforeFinalRename(
        tree: Uri,
        directory: Uri,
        finalName: String,
        temporary: Uri,
        marker: Uri,
        finalRenameAttempted: Boolean
    ) {
        val finalConfirmedAbsent = !finalRenameAttempted &&
            runCatching { findChildStrict(tree, directory, finalName) == null }.getOrDefault(false)
        val temporaryName = documentName(temporary)
        val temporaryDeleteSucceeded = try { DocumentsContract.deleteDocument(resolver, temporary) } catch (_: Exception) { false }
        val temporaryConfirmedAbsent = temporaryName != null && temporaryDeleteSucceeded &&
            runCatching { findChildStrict(tree, directory, temporaryName) == null }.getOrDefault(false)
        if (VideoTransactionPolicy.mayDeleteMarkerAfterFailure(
                finalRenameAttempted, finalConfirmedAbsent, temporaryConfirmedAbsent
            )
        ) {
            try { DocumentsContract.deleteDocument(resolver, marker) } catch (_: Exception) { }
        }
    }

    private fun recoverPendingVideoCapture(tree: Uri) {
        val session = pendingVideoCapture() ?: return
        val note = findByRelativePath(tree, session.noteRelativePath) ?: return
        val cacheExists = File(session.cachePath).isFile
        when (PendingVideoCaptureRecoveryPolicy.action(
            session.stage,
            session.attachmentVaultPath != null,
            noteReferencesPendingVideo(note, session),
            cacheExists
        )) {
            PendingVideoRecoveryAction.CLEANUP_ONLY -> cleanupPendingVideoCaptureIfCommitted(note, session)
            PendingVideoRecoveryAction.SHOW_CONFIRMATION, PendingVideoRecoveryAction.WAIT -> Unit
        }
    }

    /** A recovered saved link owns the result; cleanup must not recreate UI or save it again. */
    private fun cleanupCompletedPendingVideoCapture(tree: Uri, session: PendingVideoCaptureSession) {
        val vaultPath = session.attachmentVaultPath ?: return
        val slash = vaultPath.lastIndexOf('/')
        if (slash <= 0) return
        val directory = findByRelativePath(tree, vaultPath.substring(0, slash))?.uri ?: return
        val videoName = vaultPath.substring(slash + 1)
        val captureId = videoName.substringBeforeLast("-v.", missingDelimiterValue = "")
        if (captureId.isBlank()) return
        val marker = findChild(tree, directory, ".markbook-$captureId.txn")
        val markerClean = marker == null || try { DocumentsContract.deleteDocument(resolver, marker.uri) } catch (_: Exception) { false }
        val cache = File(session.cachePath)
        val cacheClean = !cache.exists() || cache.delete()
        if (markerClean && cacheClean) clearPendingVideoCapture()
    }

    fun openRelativeAttachment(relativePath: String): InputStream? {
        return try {
            val tree = savedVaultUri() ?: return null
            val document = findByRelativePath(tree, relativePath) ?: return null
            resolver.openInputStream(document.uri)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the user-owned files that may be synchronized. App configuration, interrupted write
     * artifacts, and the Vault-local trash never leave the device.
     */
    fun syncFiles(): List<VaultSyncFile> {
        val tree = savedVaultUri() ?: return emptyList()
        return try {
            scanSyncDirectory(tree, rootDocument(tree))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun openSyncInput(file: VaultSyncFile): InputStream? = try {
        resolver.openInputStream(file.document.uri)
    } catch (_: Exception) {
        null
    }

    /** Writes a downloaded file with the same recoverable replace protocol as note saving. */
    fun writeSyncFile(relativePath: String, mimeType: String?, input: InputStream): Boolean {
        val cleanPath = safeSyncPath(relativePath) ?: return false
        val tree = savedVaultUri() ?: return false
        val parts = cleanPath.split('/')
        val name = parts.last()
        val parent = findOrCreateDirectory(tree, parts.dropLast(1)) ?: return false
        val temp = DocumentsContract.createDocument(
            resolver,
            parent,
            mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
            ".markbook-${UUID.randomUUID()}.tmp"
        ) ?: return false
        var backup: Uri? = null
        return try {
            resolver.openOutputStream(temp, "wt")?.use { output -> copyStream(input, output) }
                ?: throw IllegalStateException("Unable to write downloaded file")
            val existing = findChild(tree, parent, name)
            if (existing != null) {
                backup = DocumentsContract.renameDocument(
                    resolver,
                    existing.uri,
                    ".markbook-$name.bak"
                )
                if (backup == null) throw IllegalStateException("Unable to protect existing file")
            }
            if (DocumentsContract.renameDocument(resolver, temp, name) == null) {
                throw IllegalStateException("Unable to commit downloaded file")
            }
            backup?.let { uri -> try { DocumentsContract.deleteDocument(resolver, uri) } catch (_: Exception) { } }
            true
        } catch (_: Exception) {
            try { backup?.let { DocumentsContract.renameDocument(resolver, it, name) } } catch (_: Exception) { }
            try { DocumentsContract.deleteDocument(resolver, temp) } catch (_: Exception) { }
            false
        }
    }

    /**
     * Commits a remote-only download only if a local file has not appeared since the scan.
     * This deliberately fails closed: the caller must keep the remote version as a conflict copy.
     */
    fun writeSyncFileIfAbsent(relativePath: String, mimeType: String?, input: InputStream): Boolean {
        val cleanPath = safeSyncPath(relativePath) ?: return false
        val tree = savedVaultUri() ?: return false
        val parts = cleanPath.split('/')
        val name = parts.last()
        val parent = findOrCreateDirectory(tree, parts.dropLast(1)) ?: return false
        if (findChild(tree, parent, name) != null) return false
        val temp = DocumentsContract.createDocument(
            resolver,
            parent,
            mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
            ".markbook-${UUID.randomUUID()}.tmp"
        ) ?: return false
        return try {
            resolver.openOutputStream(temp, "wt")?.use { output -> copyStream(input, output) }
                ?: throw IllegalStateException("Unable to write downloaded file")
            // Check immediately before final rename so a concurrently created or saved local note wins.
            if (findChild(tree, parent, name) != null) throw IllegalStateException("Local file changed during sync")
            if (DocumentsContract.renameDocument(resolver, temp, name) == null) {
                throw IllegalStateException("Unable to commit downloaded file")
            }
            true
        } catch (_: Exception) {
            try { DocumentsContract.deleteDocument(resolver, temp) } catch (_: Exception) { }
            false
        }
    }

    fun htmlAttachmentUrl(relativePath: String): String =
        "markbook://attachment/" + Uri.encode(relativePath, "/")

    private fun findByRelativePath(tree: Uri, relativePath: String): VaultDocument? {
        val parts = normalizeRelativePath(relativePath).split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var parent = rootDocument(tree)
        var parentRelativePath = ""
        var current: VaultDocument? = null
        for (part in parts) {
            current = findChild(tree, parent, part, parentRelativePath) ?: return null
            parent = current.uri
            parentRelativePath = current.relativePath
        }
        return current
    }

    private fun scanSyncDirectory(tree: Uri, directory: Uri, prefix: String = ""): List<VaultSyncFile> {
        return buildList {
            listChildren(tree, directory).forEach { child ->
                val path = listOf(prefix, child.name).filter { it.isNotEmpty() }.joinToString("/")
                if (!syncPathAllowed(path, child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR)) return@forEach
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    addAll(scanSyncDirectory(tree, child.uri, path))
                } else {
                    add(VaultSyncFile(path, child))
                }
            }
        }
    }

    private fun syncPathAllowed(path: String, directory: Boolean): Boolean {
        return SyncPathPolicy.isAllowed(path, directory)
    }

    private fun safeSyncPath(relativePath: String): String? {
        val parts = relativePath.split('/')
        if (parts.isEmpty() || parts.any { it.isBlank() || it == "." || it == ".." || it.contains('\\') }) return null
        val clean = parts.joinToString("/")
        return clean.takeIf { syncPathAllowed(it, false) }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_SYNC_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            output.write(buffer, 0, read)
        }
    }

    private fun normalizeRelativePath(relativePath: String): String {
        val parts = mutableListOf<String>()
        relativePath.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun findOrCreateDirectory(tree: Uri, parts: List<String>): Uri? {
        var parent = rootDocument(tree)
        for (part in parts) {
            val existing = findChild(tree, parent, part)
            parent = existing?.uri ?: DocumentsContract.createDocument(
                resolver,
                parent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                part
            )
                ?: return null
        }
        return parent
    }

    private fun createChild(
        parent: VaultDocument,
        requestedName: String,
        kind: VaultEntryKind
    ): VaultMutationResult {
        if (!isManageableParent(parent)) return VaultMutationResult.Failure(VaultMutationFailureKind.CREATE_FAILED)
        val validated = VaultNamePolicy.validate(requestedName, kind)
        if (validated is VaultNameValidation.Invalid) {
            return VaultMutationResult.Failure(VaultMutationFailureKind.INVALID_NAME, validated.message)
        }
        val validName = validated as VaultNameValidation.Valid
        if (kind == VaultEntryKind.FOLDER && parent.relativePath.isEmpty() && VaultPathPolicy.isProtectedRootName(validName.actualName)) {
            return VaultMutationResult.Failure(VaultMutationFailureKind.INVALID_NAME, "该名称由 Vault 保留")
        }
        return try {
            val tree = savedVaultUri() ?: return VaultMutationResult.Failure(VaultMutationFailureKind.PERMISSION_DENIED)
            val existing = listChildrenStrict(tree, parent.uri, parent.relativePath).map { it.name }
            if (VaultNamePolicy.conflicts(validName, existing)) {
                return VaultMutationResult.Failure(VaultMutationFailureKind.NAME_CONFLICT, "当前目录已有同名笔记或文件夹")
            }
            val mimeType = if (kind == VaultEntryKind.NOTE) "text/markdown" else DocumentsContract.Document.MIME_TYPE_DIR
            val created = DocumentsContract.createDocument(resolver, parent.uri, mimeType, validName.actualName)
                ?: return VaultMutationResult.Failure(VaultMutationFailureKind.CREATE_FAILED)
            val resolved = resolveMutationDocument(parent.uri, parent.relativePath, created)
            VaultMutationResult.Success(
                resolved,
                validName.actualName,
                resolved?.name
            )
        } catch (_: SecurityException) {
            VaultMutationResult.Failure(VaultMutationFailureKind.PERMISSION_DENIED)
        } catch (_: Exception) {
            VaultMutationResult.Failure(VaultMutationFailureKind.CREATE_FAILED)
        }
    }

    private fun isManageableParent(document: VaultDocument): Boolean =
        isDirectory(document) && !isInternalPath(document.relativePath)

    private fun isManageable(document: VaultDocument): Boolean =
        document.relativePath.isNotEmpty() && !isInternalPath(document.relativePath)

    private fun isInternalPath(relativePath: String): Boolean =
        VaultPathPolicy.isProtected(relativePath)

    private fun resolveMutationDocument(parent: Uri, parentRelativePath: String, uri: Uri): VaultDocument? {
        val name = runCatching { documentName(uri) }.getOrNull()
        if (name != null) {
            return VaultDocument(uri, name, null, parent, joinRelativePath(parentRelativePath, name))
        }
        return runCatching {
            listChildrenStrict(savedVaultUri() ?: return null, parent, parentRelativePath)
                .firstOrNull { it.uri == uri }
        }.getOrNull()
    }

    private fun moveNoteToTrashByCopy(tree: Uri, document: VaultDocument, trash: Uri): VaultMutationResult {
        val sourceParent = document.parentUri ?: return VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
        val existing = listChildrenStrict(tree, trash, ".trash").map { it.name }
        val fallbackName = VaultTrashPolicy.uniqueNoteName(
            document.name,
            existing,
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        )
        val copy = DocumentsContract.createDocument(resolver, trash, "text/markdown", fallbackName)
            ?: return VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
        return try {
            resolver.openInputStream(document.uri)?.use { input ->
                resolver.openOutputStream(copy, "wt")?.use { output -> input.copyTo(output) }
                    ?: throw IllegalStateException("Unable to write trash copy")
            } ?: throw IllegalStateException("Unable to read note")
            if (!DocumentsContract.deleteDocument(resolver, document.uri)) {
                runCatching { DocumentsContract.deleteDocument(resolver, copy) }
                return VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
            }
            val resolved = resolveMutationDocument(trash, ".trash", copy)
            VaultMutationResult.Success(resolved, document.name, resolved?.name)
        } catch (_: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, copy) }
            VaultMutationResult.Failure(VaultMutationFailureKind.MOVE_UNSUPPORTED)
        }
    }

    private fun rewriteDailyDirectoryAfterRenameCommitted(oldPath: String, newPath: String): DailyDirectoryChange {
        val current = dailyNoteDirectoryPath()
        val rewritten = VaultRelativePath.renamedDailyDirectory(current, oldPath, newPath)
        if (rewritten == current) return DailyDirectoryChange.UNCHANGED
        preferences.edit().putString(DAILY_NOTE_DIRECTORY_KEY, rewritten).commit()
        return DailyDirectoryChange.REWRITTEN
    }

    private fun resetDailyDirectoryIfRemovedCommitted(removedPath: String): DailyDirectoryChange {
        val current = dailyNoteDirectoryPath()
        val reset = VaultRelativePath.resetIfRemoved(current, removedPath)
        if (reset == current) return DailyDirectoryChange.UNCHANGED
        preferences.edit().putString(DAILY_NOTE_DIRECTORY_KEY, reset)
            .putBoolean(DAILY_DIRECTORY_RESET_NOTICE_KEY, true).commit()
        return DailyDirectoryChange.RESET_TO_ROOT
    }

    private fun documentName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun joinRelativePath(parent: String, name: String): String =
        listOf(parent, name).filter { it.isNotEmpty() }.joinToString("/")

    private fun nextCaptureId(tree: Uri, directory: Uri): String? {
        val timestamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        repeat(MAX_PHOTO_NAME_ATTEMPTS) {
            val suffix = buildString(PHOTO_RANDOM_LENGTH) {
                repeat(PHOTO_RANDOM_LENGTH) {
                    append(PHOTO_RANDOM_ALPHABET[photoRandom.nextInt(PHOTO_RANDOM_ALPHABET.length)])
                }
            }
            val id = "$timestamp-$suffix"
            val names = listChildren(tree, directory).map { it.name }
            if (!VideoCapturePolicy.captureIdCollides(names, id)) return id
        }
        return null
    }

    private fun findChild(tree: Uri, parent: Uri, name: String, parentRelativePath: String = ""): VaultDocument? {
        return listChildren(tree, parent, parentRelativePath).firstOrNull { it.name == name }
    }

    private fun findChildStrict(tree: Uri, parent: Uri, name: String, parentRelativePath: String = ""): VaultDocument? =
        listChildrenStrict(tree, parent, parentRelativePath).firstOrNull { it.name == name }

    private fun listChildren(tree: Uri, parent: Uri, parentRelativePath: String = ""): List<VaultDocument> = try {
        listChildrenStrict(tree, parent, parentRelativePath)
    } catch (_: Exception) {
        emptyList()
    }

    private fun listChildrenStrict(tree: Uri, parent: Uri, parentRelativePath: String = ""): List<VaultDocument> {
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return resolver.query(children, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val id = cursor.getString(idIndex) ?: continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    add(VaultDocument(
                        uri,
                        name,
                        cursor.getString(mimeIndex),
                        parent,
                        joinRelativePath(parentRelativePath, name)
                    ))
                }
            }
        } ?: throw IllegalStateException("Unable to enumerate Vault directory")
    }

    private fun recoverVaultDirectory(tree: Uri, directory: Uri, relativeDirectory: String) {
        listChildrenStrict(tree, directory).forEach { document ->
            val relativePath = listOf(relativeDirectory, document.name)
                .filter { it.isNotEmpty() }
                .joinToString("/")
            when {
                document.mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                    if (relativePath !in RECOVERY_EXCLUDED_DIRECTORIES) {
                        recoverVaultDirectory(tree, document.uri, relativePath)
                    }
                }
                else -> recoverArtifact(
                    tree,
                    directory,
                    document,
                    VaultRecoveryPolicy.classify(document.name, isAttachmentDirectory(relativeDirectory))
                )
            }
        }
    }

    private fun recoverArtifact(
        tree: Uri,
        directory: Uri,
        document: VaultDocument,
        artifact: RecoveryArtifact
    ) {
        when (artifact) {
            RecoveryArtifact.Ignore -> Unit
            RecoveryArtifact.DeleteTemporary -> {
                try { DocumentsContract.deleteDocument(resolver, document.uri) } catch (_: Exception) { }
            }
            is RecoveryArtifact.RestoreBackup -> {
                val existing = findChild(tree, directory, artifact.originalName)
                try {
                    if (existing == null) {
                        DocumentsContract.renameDocument(resolver, document.uri, artifact.originalName)
                    } else {
                        DocumentsContract.deleteDocument(resolver, document.uri)
                    }
                } catch (_: Exception) { }
            }
            RecoveryArtifact.ResolveAttachmentTransaction -> {
                val names = readText(document)?.lines()?.filter { it.isNotBlank() }.orEmpty()
                val referenceState = if (names.isEmpty()) AttachmentReferenceScan.UNREADABLE else attachmentReferenceState(tree, names)
                try {
                    when (AttachmentRecoveryPolicy.action(names.isNotEmpty(), referenceState)) {
                        AttachmentRecoveryAction.DELETE_FILES_AND_MARKER -> {
                        names.forEach { name ->
                            findChild(tree, directory, name)?.let {
                                DocumentsContract.deleteDocument(resolver, it.uri)
                            }
                        }
                        DocumentsContract.deleteDocument(resolver, document.uri)
                        }
                        AttachmentRecoveryAction.DELETE_MARKER_ONLY -> DocumentsContract.deleteDocument(resolver, document.uri)
                        AttachmentRecoveryAction.KEEP_MARKER_AND_FILES -> Unit
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun isAttachmentDirectory(relativeDirectory: String): Boolean =
        relativeDirectory.substringBefore('/') in setOf("assets", "attachments")

    private fun attachmentReferenceState(tree: Uri, names: List<String>): AttachmentReferenceScan =
        directoryReferenceState(tree, rootDocument(tree), "", names)

    private fun directoryReferenceState(
        tree: Uri,
        directory: Uri,
        relativeDirectory: String,
        names: List<String>
    ): AttachmentReferenceScan {
        for (document in listChildrenStrict(tree, directory)) {
            if (document.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val childPath = listOf(relativeDirectory, document.name)
                    .filter { it.isNotEmpty() }
                    .joinToString("/")
                if (childPath.substringBefore('/') !in NOTE_SCAN_EXCLUDED_DIRECTORIES) {
                    when (directoryReferenceState(tree, document.uri, childPath, names)) {
                        AttachmentReferenceScan.REFERENCED -> return AttachmentReferenceScan.REFERENCED
                        AttachmentReferenceScan.UNREADABLE -> return AttachmentReferenceScan.UNREADABLE
                        AttachmentReferenceScan.UNREFERENCED -> Unit
                    }
                }
            } else if (document.name.endsWith(".md", ignoreCase = true)) {
                val content = readText(document) ?: return AttachmentReferenceScan.UNREADABLE
                if (names.any(content::contains)) return AttachmentReferenceScan.REFERENCED
            }
        }
        return AttachmentReferenceScan.UNREFERENCED
    }

    private fun rootDocument(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        tree,
        DocumentsContract.getTreeDocumentId(tree)
    )

    private object IntentFlags {
        const val READ_WRITE = 3
    }

    private fun assetFolderName(noteName: String): String {
        val stem = noteName.removeSuffix(".md")
        val cleaned = stem.map { character ->
            if (character in "<>:\"/\\|?*" || character == '\u0000') '_' else character
        }.joinToString("").trim().trim('.')
        return cleaned.ifEmpty { "Untitled" }
    }

    private class VideoCopyCancelledException : Exception()

    companion object {
        private const val VAULT_URI_KEY = "vault_uri"
        private const val APPEARANCE_MODE_KEY = "appearance_mode"
        private const val SWIPE_DISCOVERY_HINT_SEEN_KEY = "swipe_discovery_hint_seen"
        private const val DAILY_NOTE_DIRECTORY_KEY = "daily_note_directory"
        private const val DAILY_DIRECTORY_RESET_NOTICE_KEY = "daily_note_directory_reset_notice"
        private const val PENDING_VIDEO_NOTE_URI = "pending_video_note_uri"
        private const val PENDING_VIDEO_NOTE_PATH = "pending_video_note_path"
        private const val PENDING_VIDEO_HASH = "pending_video_hash"
        private const val PENDING_VIDEO_CARET = "pending_video_caret"
        private const val PENDING_VIDEO_SCROLL = "pending_video_scroll"
        private const val PENDING_VIDEO_CACHE = "pending_video_cache"
        private const val PENDING_VIDEO_STAGE = "pending_video_stage"
        private const val PENDING_VIDEO_ATTACHMENT_PATH = "pending_video_attachment_path"
        private const val DEFAULT_DAILY_NOTE_DIRECTORY = "Daily Notes"
        const val APPEARANCE_DAY = "day"
        const val APPEARANCE_NIGHT = "night"
        private const val MAX_PHOTO_NAME_ATTEMPTS = 32
        private const val PHOTO_RANDOM_LENGTH = 4
        private const val PHOTO_RANDOM_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
        private const val DEFAULT_SYNC_BUFFER_BYTES = 32 * 1024
        private val RECOVERY_EXCLUDED_DIRECTORIES = setOf(".obsidian", ".trash")
        private val NOTE_SCAN_EXCLUDED_DIRECTORIES = setOf(
            ".obsidian", ".trash", ".markbook", "assets", "attachments"
        )
        private val photoRandom = SecureRandom()
    }
}
