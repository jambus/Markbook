package com.markbook.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
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
    val parentUri: Uri? = null
)

data class PhotoAttachments(
    val original: String,
    val corrected: String,
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

class VaultRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val preferences = context.getSharedPreferences("markbook", Context.MODE_PRIVATE)

    fun savedVaultUri(): Uri? = preferences.getString(VAULT_URI_KEY, null)?.let(Uri::parse)

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
        preferences.edit().putString(DAILY_NOTE_DIRECTORY_KEY, cleanParts.joinToString("/")).apply()
    }

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
        return listChildren(tree, directory.uri)
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
        return findChild(tree, parent, document.name)
    }

    fun relativeAttachmentPath(note: VaultDocument, attachments: PhotoAttachments): String {
        val tree = savedVaultUri() ?: return "../${attachments.relativeDirectory}/${attachments.corrected}"
        val dailyDirectory = findOrCreateDirectory(tree, dailyNoteDirectoryParts())
        val levels = if (dailyDirectory != null && note.parentUri == dailyDirectory) {
            dailyNoteDirectoryParts().size
        } else {
            1
        }
        val prefix = List(levels) { ".." }
        return (prefix + attachments.relativeDirectory + attachments.corrected).joinToString("/")
    }

    fun openDailyNote(): DailyNoteResult {
        return try {
            val tree = savedVaultUri()
                ?: return DailyNoteResult.Failure(VaultFailureKind.PERMISSION_DENIED)
            val directory = findOrCreateDirectory(tree, dailyNoteDirectoryParts())
                ?: return DailyNoteResult.Failure(VaultFailureKind.READ_FAILED)
            val name = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".md"
            val existing = findChild(tree, directory, name)
            if (existing != null) return DailyNoteResult.Existing(existing)
            val created = DocumentsContract.createDocument(
                resolver,
                directory,
                "text/markdown",
                name
            ) ?: return DailyNoteResult.Failure(VaultFailureKind.READ_FAILED)
            DailyNoteResult.Created(VaultDocument(created, name, "text/markdown", directory))
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

    /** Moves a note to the Vault-local trash without propagating a delete to sync providers. */
    fun moveNoteToTrash(document: VaultDocument): Boolean {
        val tree = savedVaultUri() ?: return false
        val sourceParent = document.parentUri ?: return false
        val trash = findOrCreateDirectory(tree, listOf(".trash")) ?: return false
        val moved = try {
            DocumentsContract.moveDocument(resolver, document.uri, sourceParent, trash)
        } catch (_: Exception) {
            null
        }
        if (moved != null) return true

        // Some document providers do not implement moveDocument. Copying then deleting keeps the
        // deletion workflow available on those providers while retaining the original on a failure.
        val trashName = uniqueTrashName(tree, trash, document.name)
        val copy = DocumentsContract.createDocument(resolver, trash, "text/markdown", trashName) ?: return false
        return try {
            resolver.openInputStream(document.uri)?.use { input ->
                resolver.openOutputStream(copy, "wt")?.use { output -> input.copyTo(output) }
                    ?: throw IllegalStateException("Unable to write trash copy")
            } ?: throw IllegalStateException("Unable to read note")
            if (!DocumentsContract.deleteDocument(resolver, document.uri)) {
                try { DocumentsContract.deleteDocument(resolver, copy) } catch (_: Exception) { }
                return false
            }
            true
        } catch (_: Exception) {
            try { DocumentsContract.deleteDocument(resolver, copy) } catch (_: Exception) { }
            false
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
        val id = nextPhotoId(tree, directory, extension) ?: return null
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
        try { DocumentsContract.deleteDocument(resolver, attachments.transactionUri) } catch (_: Exception) { }
        listOf(attachments.original, attachments.corrected).forEach { name ->
            findChild(tree, directory, name)?.let {
                try { DocumentsContract.deleteDocument(resolver, it.uri) } catch (_: Exception) { }
            }
        }
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

    fun htmlAttachmentUrl(relativePath: String): String =
        "markbook://attachment/" + Uri.encode(relativePath, "/")

    private fun findByRelativePath(tree: Uri, relativePath: String): VaultDocument? {
        val parts = normalizeRelativePath(relativePath).split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var parent = rootDocument(tree)
        var current: VaultDocument? = null
        for (part in parts) {
            current = findChild(tree, parent, part) ?: return null
            parent = current.uri
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
        val parts = path.split('/')
        if (parts.firstOrNull() == ".obsidian") return false
        if (parts.firstOrNull() == ".trash") return false
        if (parts.any { it.startsWith(".markbook-") || it.endsWith(".tmp") || it.endsWith(".bak") || it.endsWith(".txn") }) return false
        // All Markbook metadata is local state; the Vault-level .trash directory is excluded above.
        if (parts.firstOrNull() == ".markbook") return false
        return directory || parts.lastOrNull()?.isNotBlank() == true
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

    private fun nextPhotoId(tree: Uri, directory: Uri, extension: String): String? {
        val timestamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        repeat(MAX_PHOTO_NAME_ATTEMPTS) {
            val suffix = buildString(PHOTO_RANDOM_LENGTH) {
                repeat(PHOTO_RANDOM_LENGTH) {
                    append(PHOTO_RANDOM_ALPHABET[photoRandom.nextInt(PHOTO_RANDOM_ALPHABET.length)])
                }
            }
            val id = "$timestamp-$suffix"
            val originalExists = findChild(tree, directory, "$id-o.$extension") != null
            val correctedExists = findChild(tree, directory, "$id-c.$extension") != null
            if (!originalExists && !correctedExists) return id
        }
        return null
    }

    private fun findChild(tree: Uri, parent: Uri, name: String): VaultDocument? {
        return listChildren(tree, parent).firstOrNull { it.name == name }
    }

    private fun uniqueTrashName(tree: Uri, trash: Uri, name: String): String {
        if (findChild(tree, trash, name) == null) return name
        val extension = name.substringAfterLast('.', "")
        val stem = if (extension.isBlank()) name else name.removeSuffix(".$extension")
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        repeat(100) { attempt ->
            val suffix = if (attempt == 0) "-$timestamp" else "-$timestamp-${attempt + 1}"
            val candidate = "$stem$suffix${if (extension.isBlank()) "" else ".$extension"}"
            if (findChild(tree, trash, candidate) == null) return candidate
        }
        return "$stem-${UUID.randomUUID()}${if (extension.isBlank()) "" else ".$extension"}"
    }

    private fun listChildren(tree: Uri, parent: Uri): List<VaultDocument> = try {
        listChildrenStrict(tree, parent)
    } catch (_: Exception) {
        emptyList()
    }

    private fun listChildrenStrict(tree: Uri, parent: Uri): List<VaultDocument> {
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
                    add(VaultDocument(uri, name, cursor.getString(mimeIndex), parent))
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
            RecoveryArtifact.ResolvePhotoTransaction -> {
                val names = readText(document)?.lines()?.filter { it.isNotBlank() }.orEmpty()
                val committed = names.isNotEmpty() && attachmentReferencedByAnyNote(tree, names)
                try {
                    if (!committed) {
                        names.forEach { name ->
                            findChild(tree, directory, name)?.let {
                                DocumentsContract.deleteDocument(resolver, it.uri)
                            }
                        }
                    }
                    DocumentsContract.deleteDocument(resolver, document.uri)
                } catch (_: Exception) { }
            }
        }
    }

    private fun isAttachmentDirectory(relativeDirectory: String): Boolean =
        relativeDirectory.substringBefore('/') in setOf("assets", "attachments")

    private fun attachmentReferencedByAnyNote(tree: Uri, names: List<String>): Boolean =
        directoryReferencesAny(tree, rootDocument(tree), "", names)

    private fun directoryReferencesAny(
        tree: Uri,
        directory: Uri,
        relativeDirectory: String,
        names: List<String>
    ): Boolean {
        for (document in listChildrenStrict(tree, directory)) {
            if (document.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val childPath = listOf(relativeDirectory, document.name)
                    .filter { it.isNotEmpty() }
                    .joinToString("/")
                if (childPath.substringBefore('/') !in NOTE_SCAN_EXCLUDED_DIRECTORIES &&
                    directoryReferencesAny(tree, document.uri, childPath, names)) return true
            } else if (document.name.endsWith(".md", ignoreCase = true)) {
                val content = readText(document).orEmpty()
                if (names.any(content::contains)) return true
            }
        }
        return false
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

    companion object {
        private const val VAULT_URI_KEY = "vault_uri"
        private const val APPEARANCE_MODE_KEY = "appearance_mode"
        private const val DAILY_NOTE_DIRECTORY_KEY = "daily_note_directory"
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
