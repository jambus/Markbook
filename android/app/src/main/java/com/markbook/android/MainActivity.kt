package com.markbook.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var repository: VaultRepository
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var currentNote: VaultDocument? = null
    private var browserDirectory: VaultDocument? = null
    private val browserPath = mutableListOf<String>()
    private val browserHistory = mutableListOf<VaultDocument>()
    private var browserScrollY = 0
    private var dailyFolderDirectory: VaultDocument? = null
    private val dailyFolderPath = mutableListOf<String>()
    private val dailyFolderHistory = mutableListOf<VaultDocument>()
    private var captureFile: File? = null
    private var captureUri: Uri? = null
    private var editorView: PhotoEditorView? = null
    private var savePending = false
    private var queuedExtra: String? = null
    private var queuedCompletion: ((Boolean) -> Unit)? = null
    private var screen = Screen.WELCOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VaultRepository(this)
        applyWindowColors()
        if (repository.savedVaultUri() == null) showWelcome() else showVaultBrowser()
    }

    override fun onPause() {
        super.onPause()
        if (screen == Screen.EDITOR && ::webView.isInitialized) saveCurrentNote()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (screen) {
            Screen.EDITOR -> returnToBrowser()
            Screen.BROWSER -> {
                if (browserPath.isNotEmpty()) showParentDirectory() else super.onBackPressed()
            }
            Screen.PHOTO -> restoreEditorScreen()
            Screen.SETTINGS -> showVaultBrowser()
            Screen.DAILY_FOLDER_PICKER -> showSettings()
            Screen.WELCOME -> super.onBackPressed()
        }
    }

    private fun showWelcome(message: String? = null) {
        screen = Screen.WELCOME
        currentNote = null
        val root = pageRoot(COLOR_BACKGROUND).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "M"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(COLOR_BACKGROUND)
            background = rounded(COLOR_ACCENT, dp(18))
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        root.addView(TextView(this).apply {
            text = "Markbook"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(COLOR_PRIMARY_TEXT)
            setPadding(0, dp(20), 0, dp(6))
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "把现场记录直接写进你的 Obsidian Vault"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(dp(18), 0, dp(18), dp(28))
        }, matchWrap())
        if (message != null) {
            root.addView(infoBanner(message), matchWrap().apply { bottomMargin = dp(16) })
        }
        root.addView(action("选择 Obsidian Vault", true) { chooseVault() }, matchWrap())
        root.addView(TextView(this).apply {
            text = "选择已有文件夹后，你可以浏览笔记、编辑 Markdown，并直接插入照片。"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(12), dp(16), dp(12), 0)
        }, matchWrap())
        setContentView(root)
    }

    private fun showVaultBrowser(resetToRoot: Boolean = false) {
        val rootDirectory = repository.vaultRoot()
        if (rootDirectory == null) {
            showWelcome("无法访问 Vault，请重新选择")
            return
        }
        if (resetToRoot || browserDirectory == null) {
            browserDirectory = rootDirectory
            browserPath.clear()
            browserHistory.clear()
            browserScrollY = 0
        }
        val directory = browserDirectory ?: rootDirectory
        screen = Screen.BROWSER
        val root = pageRoot(COLOR_BACKGROUND)
        root.addView(browserHeader(), matchWrap())
        root.addView(TextView(this).apply {
            text = if (browserPath.isEmpty()) "${rootDirectory.name}  ·  Vault" else browserPath.joinToString("  /  ")
            textSize = 13f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(20), dp(6), dp(20), dp(10))
            maxLines = 1
        }, matchWrap())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setOnScrollChangeListener { _, _, y, _, _ -> browserScrollY = y }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        if (browserPath.isNotEmpty()) {
            content.addView(action("‹  上一级", false) { showParentDirectory() }, wrapWrap().apply {
                bottomMargin = dp(8)
            })
        }
        val children = repository.children(directory)
            .filterNot { isInternalDocument(it) }
            .sortedWith(compareBy<VaultDocument> { !repository.isDirectory(it) }.thenBy { it.name.lowercase() })
        val folders = children.filter { repository.isDirectory(it) }
        val notes = children.filter { !repository.isDirectory(it) && it.name.endsWith(".md", true) }

        sectionLabel(content, "文件夹", folders.size)
        if (folders.isEmpty()) {
            content.addView(emptyState("当前目录没有文件夹"), matchWrap().apply { bottomMargin = dp(16) })
        } else {
            folders.forEach { folder ->
                content.addView(vaultRow("▸", folder.name, "文件夹") { openDirectory(folder) }, matchWrap().apply {
                    bottomMargin = dp(8)
                })
            }
        }
        sectionLabel(content, "笔记", notes.size)
        if (notes.isEmpty()) {
            content.addView(emptyState("当前目录还没有 Markdown 笔记"), matchWrap())
        } else {
            notes.forEach { note ->
                content.addView(vaultRow("•", note.name.removeSuffix(".md"), notePreview(note)) { openNote(note) }, matchWrap().apply {
                    bottomMargin = dp(8)
                })
            }
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(browserFooter(), matchWrap())
        setContentView(root)
        scroll.post { scroll.scrollTo(0, browserScrollY) }
    }

    private fun browserHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(14), dp(12), dp(8))
        background = colorBlock(COLOR_SURFACE)
        addView(TextView(this@MainActivity).apply {
            text = "Markbook"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_TEXT)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(action("设置", false) { showSettings() })
        addView(action("切换 Vault", false) { chooseVault() })
    }

    private fun browserFooter(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(16))
        background = colorBlock(COLOR_SURFACE)
        addView(TextView(this@MainActivity).apply {
            text = "文件"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(COLOR_ACCENT)
        }, LinearLayout.LayoutParams(0, dp(48), 0.34f))
        addView(action("打开今日笔记", true) { openDailyNote() }, LinearLayout.LayoutParams(0, dp(48), 0.66f))
    }

    private fun showSettings() {
        screen = Screen.SETTINGS
        val root = pageRoot(COLOR_BACKGROUND)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = colorBlock(COLOR_SURFACE)
            addView(action("‹  文件", false) { showVaultBrowser() })
            addView(TextView(this@MainActivity).apply {
                text = "设置"
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(COLOR_PRIMARY_TEXT)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(TextView(this@MainActivity), LinearLayout.LayoutParams(dp(76), dp(44)))
        }
        root.addView(toolbar, matchWrap())
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(24))
        }
        sectionLabel(content, "显示", 0)
        val appearance = repository.appearanceMode()
        content.addView(settingsRow("日间模式", "浅色背景与深色文字", appearance == VaultRepository.APPEARANCE_DAY) {
            setAppearance(VaultRepository.APPEARANCE_DAY)
        }, matchWrap().apply { bottomMargin = dp(8) })
        content.addView(settingsRow("夜间模式", "深色工作区与深色编辑纸面", appearance == VaultRepository.APPEARANCE_NIGHT) {
            setAppearance(VaultRepository.APPEARANCE_NIGHT)
        }, matchWrap())
        sectionLabel(content, "每日笔记", 0)
        val path = repository.dailyNoteDirectoryPath().ifBlank { "Vault 根目录" }
        content.addView(settingsRow("今日笔记目录", path, false) { showDailyFolderPicker(true) }, matchWrap())
        content.addView(TextView(this).apply {
            text = "设置不会移动已有笔记或附件。新建的每日笔记会按 yyyy-MM-dd.md 写入所选目录。"
            textSize = 13f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(6), dp(12), dp(6), 0)
        }, matchWrap())
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun setAppearance(mode: String) {
        repository.setAppearanceMode(mode)
        applyWindowColors()
        showSettings()
    }

    private fun showDailyFolderPicker(reset: Boolean = false) {
        val rootDirectory = repository.vaultRoot() ?: run {
            toast("无法访问 Vault，请重新选择")
            return
        }
        if (reset || dailyFolderDirectory == null) {
            dailyFolderDirectory = rootDirectory
            dailyFolderPath.clear()
            dailyFolderHistory.clear()
        }
        val directory = dailyFolderDirectory ?: rootDirectory
        screen = Screen.DAILY_FOLDER_PICKER
        val root = pageRoot(COLOR_BACKGROUND)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = colorBlock(COLOR_SURFACE)
            addView(action("‹  设置", false) { showSettings() })
            addView(TextView(this@MainActivity).apply {
                text = "选择今日笔记目录"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(COLOR_PRIMARY_TEXT)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(action("使用此目录", true) { confirmDailyFolder() })
        }
        root.addView(toolbar, matchWrap())
        root.addView(TextView(this).apply {
            text = if (dailyFolderPath.isEmpty()) "Vault 根目录" else dailyFolderPath.joinToString(" / ")
            textSize = 13f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }, matchWrap())
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        if (dailyFolderPath.isNotEmpty()) {
            content.addView(action("‹  上一级", false) { showDailyFolderParent() }, wrapWrap().apply {
                bottomMargin = dp(8)
            })
        }
        val folders = repository.children(directory)
            .filter { repository.isDirectory(it) && !isInternalDocument(it) }
            .sortedBy { it.name.lowercase() }
        sectionLabel(content, "文件夹", folders.size)
        if (folders.isEmpty()) {
            content.addView(emptyState("当前目录没有可选子文件夹"), matchWrap())
        } else {
            folders.forEach { folder ->
                content.addView(vaultRow("▸", folder.name, "文件夹") { openDailyFolder(folder) }, matchWrap().apply {
                    bottomMargin = dp(8)
                })
            }
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun openDailyFolder(folder: VaultDocument) {
        dailyFolderDirectory?.let { dailyFolderHistory.add(it) }
        dailyFolderPath.add(folder.name)
        dailyFolderDirectory = folder
        showDailyFolderPicker()
    }

    private fun showDailyFolderParent() {
        if (dailyFolderHistory.isEmpty()) return
        dailyFolderDirectory = dailyFolderHistory.removeAt(dailyFolderHistory.lastIndex)
        if (dailyFolderPath.isNotEmpty()) dailyFolderPath.removeAt(dailyFolderPath.lastIndex)
        showDailyFolderPicker()
    }

    private fun confirmDailyFolder() {
        repository.setDailyNoteDirectory(dailyFolderPath)
        showSettings()
    }

    private fun openDirectory(directory: VaultDocument) {
        browserDirectory?.let { browserHistory.add(it) }
        browserPath.add(directory.name)
        browserDirectory = directory
        browserScrollY = 0
        showVaultBrowser()
    }

    private fun showParentDirectory() {
        if (browserHistory.isEmpty()) return
        val parent = browserHistory.removeAt(browserHistory.lastIndex)
        browserDirectory = parent
        if (browserPath.isNotEmpty()) browserPath.removeAt(browserPath.lastIndex)
        browserScrollY = 0
        showVaultBrowser()
    }

    private fun openDailyNote() {
        val note = repository.dailyNote()
        if (note == null) {
            toast("无法创建今日笔记，请检查 Vault 权限")
            return
        }
        openNote(note)
    }

    private fun openNote(note: VaultDocument) {
        currentNote = note
        showEditor(note)
    }

    private fun showEditor(note: VaultDocument) {
        screen = Screen.EDITOR
        val root = pageRoot(COLOR_EDITOR_BACKGROUND)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
            background = colorBlock(COLOR_SURFACE)
        }
        toolbar.addView(action("‹  文件", false) { returnToBrowser() })
        toolbar.addView(TextView(this).apply {
            text = note.name.removeSuffix(".md")
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_TEXT)
            gravity = Gravity.CENTER
            maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        toolbar.addView(action("拍照", false) { startCamera() })
        toolbar.addView(action("保存", true) { saveCurrentNote() })
        statusView = TextView(this).apply {
            text = "已保存"
            textSize = 13f
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(dp(20), dp(7), dp(20), dp(9))
            background = colorBlock(COLOR_SURFACE)
        }
        root.addView(toolbar, matchWrap())
        root.addView(statusView, matchWrap())
        webView = WebView(this).apply {
            setBackgroundColor(COLOR_EDITOR_BACKGROUND)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = attachmentClient()
            addJavascriptInterface(EditorBridge(), "Android")
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        val content = repository.readText(note) ?: "# ${note.name.removeSuffix(".md")}\n\n"
        webView.loadDataWithBaseURL(null, MarkdownCodec.toHtml(content, repository, isNightTheme()), "text/html", "UTF-8", null)
    }

    private fun returnToBrowser() {
        saveCurrentNote { success ->
            if (success) showVaultBrowser() else toast("保存失败，请先恢复 Vault 访问权限")
        }
    }

    private fun saveCurrentNote(extra: String? = null, onComplete: (Boolean) -> Unit = {}) {
        if (screen != Screen.EDITOR || !::webView.isInitialized) {
            onComplete(false)
            return
        }
        if (savePending) {
            if (extra != null) {
                queuedExtra = listOfNotNull(queuedExtra, extra).joinToString("\n")
            }
            queuedCompletion = onComplete
            return
        }
        val note = currentNote ?: run {
            onComplete(false)
            return
        }
        savePending = true
        statusView.text = "正在保存…"
        webView.evaluateJavascript("window.markbook && window.markbook.serialize ? window.markbook.serialize() : ''") { value ->
            savePending = false
            val base = decodeJavascriptString(value)
            val content = if (extra == null) base else base.trimEnd() + "\n\n" + extra + "\n"
            val success = repository.saveText(note, content)
            if (success) {
                currentNote = repository.refreshDocument(note) ?: note
                statusView.text = if (extra == null) "已保存" else "照片已插入并保存"
            } else {
                statusView.text = "保存失败 · 请检查 Vault 权限"
            }
            if (success && extra != null) {
                webView.loadDataWithBaseURL(null, MarkdownCodec.toHtml(content, repository, isNightTheme()), "text/html", "UTF-8", null)
            }
            val nextExtra = queuedExtra
            val nextCompletion = queuedCompletion
            queuedExtra = null
            queuedCompletion = null
            if (nextExtra != null) {
                if (success) saveCurrentNote(nextExtra, nextCompletion ?: {}) else nextCompletion?.invoke(false)
            } else {
                onComplete(success)
                nextCompletion?.invoke(success)
            }
        }
    }

    private fun startCamera() {
        if (currentNote == null) {
            toast("请先打开一篇笔记")
            return
        }
        saveCurrentNote { success ->
            if (!success) {
                toast("请先保存笔记，再拍照")
                return@saveCurrentNote
            }
            launchCamera()
        }
    }

    private fun launchCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        val directory = File(cacheDir, "camera").apply { mkdirs() }
        captureFile = File(directory, "capture-${UUID.randomUUID()}.jpg")
        captureUri = Uri.parse("content://$FILE_PROVIDER_AUTHORITY/capture/${captureFile!!.name}")
        val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
            putExtra("output", captureUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("photo", captureUri)
        }
        try {
            startActivityForResult(intent, CAMERA_REQUEST)
        } catch (_: Exception) {
            toast("系统相机不可用")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode != RESULT_OK) {
            discardCaptureFile()
            return
        }
        if (resultCode != RESULT_OK) return
        if (requestCode == VAULT_REQUEST) {
            data?.data?.let {
                repository.rememberVault(it)
                browserDirectory = null
                browserPath.clear()
                showVaultBrowser(true)
            }
            return
        }
        if (requestCode == CAMERA_REQUEST) {
            val bitmap = captureFile?.let { decodeCapturePreview(it) }
            if (bitmap == null) {
                toast("无法读取照片")
                discardCaptureFile()
            } else {
                showPhotoEditor(bitmap)
            }
        }
    }

    private fun decodeCapturePreview(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longestSide / sampleSize > MAX_PREVIEW_SIDE) sampleSize *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            toast("需要相机权限才能拍照")
        }
    }

    private fun showPhotoEditor(bitmap: Bitmap) {
        screen = Screen.PHOTO
        val root = pageRoot(Color.BLACK)
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        val view = PhotoEditorView(this, bitmap)
        editorView = view
        controls.addView(action("矩形", false) { view.mode = PhotoEditMode.RECTANGLE })
        controls.addView(action("四点校正", false) { view.mode = PhotoEditMode.PERSPECTIVE })
        controls.addView(action("取消", false) { restoreEditorScreen() })
        controls.addView(action("插入", true) { commitPhoto(bitmap, view) })
        root.addView(controls, matchWrap())
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun commitPhoto(bitmap: Bitmap, view: PhotoEditorView) {
        val input = captureFile?.let { FileInputStream(it) }
        if (input == null) {
            restoreEditorScreen()
            return
        }
        val corrected = view.outputJpeg()
        val noteName = currentNote?.name
        val attachments = if (noteName == null) null else input.use { repository.savePhotoPair(noteName, it, corrected) }
        if (attachments == null) {
            toast("照片保存失败，请检查 Vault 空间或权限")
            restoreEditorScreen()
            return
        }
        val note = currentNote ?: run {
            repository.rollbackPhotoPair(attachments)
            restoreEditorScreen()
            return
        }
        val imageLink = "![${attachments.corrected}](${repository.relativeAttachmentPath(note, attachments)})"
        val content = repository.readText(note).orEmpty().trimEnd() + "\n\n$imageLink\n"
        val success = repository.saveText(note, content)
        if (success) {
            currentNote = repository.refreshDocument(note) ?: note
            repository.confirmPhotoPair(attachments)
        } else {
            repository.rollbackPhotoPair(attachments)
            toast("无法更新笔记，照片尚未插入")
        }
        if (!bitmap.isRecycled) bitmap.recycle()
        discardCaptureFile()
        editorView = null
        if (success) {
            showEditor(currentNote ?: note)
            statusView.text = "照片已插入并保存"
        } else {
            restoreEditorScreen()
        }
    }

    private fun restoreEditorScreen() {
        discardCaptureFile()
        editorView = null
        val note = currentNote
        if (note == null) showVaultBrowser() else showEditor(note)
    }

    private fun attachmentClient(): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): WebResourceResponse? {
            val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
            if (uri.scheme != "markbook" || uri.host != "attachment") return super.shouldInterceptRequest(view, request)
            val path = Uri.decode(uri.path.orEmpty()).trimStart('/')
            val stream = repository.openRelativeAttachment(path) ?: return null
            val mime = when {
                path.endsWith(".png", true) -> "image/png"
                path.endsWith(".webp", true) -> "image/webp"
                else -> "image/jpeg"
            }
            return WebResourceResponse(mime, null, stream)
        }
    }

    private inner class EditorBridge {
        @JavascriptInterface
        fun onChanged() {
            if (screen != Screen.EDITOR) return
            statusView.text = "未保存"
            handler.removeCallbacksAndMessages(AUTOSAVE_TOKEN)
            handler.postAtTime({ saveCurrentNote() }, AUTOSAVE_TOKEN, System.currentTimeMillis() + 600)
        }
    }

    private fun chooseVault() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, VAULT_REQUEST)
    }

    private fun sectionLabel(container: LinearLayout, label: String, count: Int) {
        container.addView(TextView(this).apply {
            text = "$label  $count"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(4), dp(14), dp(4), dp(8))
        }, matchWrap())
    }

    private fun vaultRow(icon: String, title: String, subtitle: String, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(14), dp(8), dp(12), dp(8))
        background = rounded(COLOR_ROW, dp(14))
        isClickable = true
        isFocusable = true
        contentDescription = title
        setOnClickListener { action() }
        addView(TextView(this@MainActivity).apply {
            text = icon
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(COLOR_ACCENT)
        }, LinearLayout.LayoutParams(dp(32), dp(44)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                maxLines = 1
                setTextColor(COLOR_PRIMARY_TEXT)
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 13f
                maxLines = 1
                setTextColor(COLOR_MUTED_TEXT)
                setPadding(0, dp(3), 0, 0)
            }, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = "›"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(COLOR_MUTED_TEXT)
        }, LinearLayout.LayoutParams(dp(28), dp(44)))
    }

    private fun settingsRow(title: String, subtitle: String, selected: Boolean, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(68)
        setPadding(dp(16), dp(10), dp(12), dp(10))
        background = rounded(COLOR_ROW, dp(14))
        isClickable = true
        isFocusable = true
        contentDescription = "$title，$subtitle"
        setOnClickListener { action() }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTextColor(COLOR_PRIMARY_TEXT)
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 13f
                maxLines = 1
                setTextColor(COLOR_MUTED_TEXT)
                setPadding(0, dp(3), 0, 0)
            }, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = if (selected) "已启用  ✓" else "›"
            textSize = if (selected) 13f else 26f
            gravity = Gravity.CENTER
            setTextColor(if (selected) COLOR_ACCENT else COLOR_MUTED_TEXT)
        }, LinearLayout.LayoutParams(dp(72), dp(44)))
    }

    private fun emptyState(message: String): View = TextView(this).apply {
        text = message
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(COLOR_MUTED_TEXT)
        setPadding(dp(16), dp(22), dp(16), dp(22))
        background = rounded(COLOR_ROW, dp(14))
    }

    private fun infoBanner(message: String): View = TextView(this).apply {
        text = message
        textSize = 14f
        setTextColor(COLOR_PRIMARY_TEXT)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(COLOR_ROW, dp(12))
    }

    private fun action(label: String, primary: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = if (primary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        gravity = Gravity.CENTER
        minimumHeight = dp(44)
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(if (primary) COLOR_ON_ACCENT else COLOR_PRIMARY_TEXT)
        background = rounded(if (primary) COLOR_ACCENT else COLOR_ROW, dp(12))
        isClickable = true
        isFocusable = true
        contentDescription = label
        setOnClickListener { onClick() }
    }

    private fun isInternalDocument(document: VaultDocument): Boolean {
        if (document.name.startsWith(".")) return true
        return repository.isDirectory(document) && document.name in setOf("assets", "attachments", ".markbook")
    }

    private fun notePreview(note: VaultDocument): String {
        val value = repository.readText(note).orEmpty()
            .lineSequence()
            .map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("![") }
            .orEmpty()
        return if (value.isBlank()) "空白笔记" else value.take(88)
    }

    private fun decodeJavascriptString(value: String): String = try {
        org.json.JSONTokener(value).nextValue() as? String ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun discardCaptureFile() {
        captureFile?.delete()
        captureFile = null
        captureUri = null
    }

    private fun pageRoot(color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(color)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun colorBlock(color: Int): GradientDrawable = GradientDrawable().apply { setColor(color) }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)

    private fun wrapWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, -2)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun isNightTheme(): Boolean = repository.appearanceMode() == VaultRepository.APPEARANCE_NIGHT

    private fun applyWindowColors() {
        window.statusBarColor = COLOR_SURFACE
        window.navigationBarColor = COLOR_SURFACE
    }

    private val COLOR_BACKGROUND: Int
        get() = if (isNightTheme()) Color.rgb(18, 22, 28) else Color.rgb(245, 245, 248)
    private val COLOR_SURFACE: Int
        get() = if (isNightTheme()) Color.rgb(27, 32, 40) else Color.WHITE
    private val COLOR_ROW: Int
        get() = if (isNightTheme()) Color.rgb(37, 44, 54) else Color.rgb(232, 232, 238)
    private val COLOR_EDITOR_BACKGROUND: Int
        get() = if (isNightTheme()) Color.rgb(29, 33, 40) else Color.rgb(250, 250, 248)
    private val COLOR_ACCENT: Int
        get() = if (isNightTheme()) Color.rgb(174, 146, 255) else Color.rgb(103, 80, 164)
    private val COLOR_PRIMARY_TEXT: Int
        get() = if (isNightTheme()) Color.rgb(239, 242, 247) else Color.rgb(28, 28, 33)
    private val COLOR_SECONDARY_TEXT: Int
        get() = if (isNightTheme()) Color.rgb(193, 201, 211) else Color.rgb(82, 82, 92)
    private val COLOR_MUTED_TEXT: Int
        get() = if (isNightTheme()) Color.rgb(148, 160, 174) else Color.rgb(112, 112, 124)
    private val COLOR_ON_ACCENT: Int
        get() = Color.WHITE

    private enum class Screen { WELCOME, BROWSER, EDITOR, PHOTO, SETTINGS, DAILY_FOLDER_PICKER }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val VAULT_REQUEST = 1002
        private const val CAMERA_REQUEST = 1003
        private const val FILE_PROVIDER_AUTHORITY = "com.markbook.android.fileprovider"
        private const val MAX_PREVIEW_SIDE = 4096
        private val AUTOSAVE_TOKEN = Any()
    }
}
