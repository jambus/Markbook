package com.markbook.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var repository: VaultRepository
    private lateinit var drivePreferences: DriveSyncPreferences
    private lateinit var driveAuth: GoogleDriveAuth
    private val driveExecutor = Executors.newSingleThreadExecutor()
    private val noteIoExecutor = Executors.newSingleThreadExecutor()
    private var webView: WebView? = null
    private var statusView: TextView? = null
    private var saveActionView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autosave = Runnable { saveCurrentNote() }
    private val saveCoordinator = RevisionSaveCoordinator()
    private val saveWaiters = mutableListOf<(Boolean) -> Unit>()
    private var editorGeneration = 0L
    private var currentNote: VaultDocument? = null
    private var browserDirectory: VaultDocument? = null
    private var currentVaultName = "Vault"
    private val browserPath = mutableListOf<String>()
    private val browserHistory = mutableListOf<VaultDocument>()
    private var browserScrollY = 0
    private var browserLoadGeneration = 0L
    private var browserMutationPending = false
    private var browserMutationMessage: String? = null
    private var dailyFolderDirectory: VaultDocument? = null
    private val dailyFolderPath = mutableListOf<String>()
    private val dailyFolderHistory = mutableListOf<VaultDocument>()
    private var dailyFolderLoadGeneration = 0L
    private var trashCount: Int? = null
    private var trashLoadFailed = false
    private var trashClearPending = false
    private var trashStatusMessage: String? = null
    private var trashStatusView: TextView? = null
    private var trashRowView: View? = null
    private var trashLoadGeneration = 0L
    private var captureFile: File? = null
    private var captureUri: Uri? = null
    private var editorView: PhotoEditorView? = null
    private var photoStatusView: TextView? = null
    private var photoInsertAction: TextView? = null
    private var photoModeActions: List<TextView> = emptyList()
    private var photoSavePending = false
    private var photoContextContent: String? = null
    private var photoContextScrollY = 0
    private var pendingEditorScrollY: Int? = null
    private var pendingCaretImagePath: String? = null
    private var screen = Screen.WELCOME
    private var driveFolderDirectory = DriveVaultRoot("root", "我的云端硬盘")
    private val driveFolderHistory = mutableListOf<DriveVaultRoot>()
    private var drivePickerFolders: List<DriveItem>? = null
    private var drivePickerError: String? = null
    private var drivePickerMessage: String? = null
    private var driveSyncCancelled: AtomicBoolean? = null
    private var driveProgressView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VaultRepository(this)
        drivePreferences = DriveSyncPreferences(this)
        driveAuth = GoogleDriveAuth(this)
        applyWindowColors()
        if (repository.savedVaultUri() == null) showWelcome() else recoverVaultAndOpenBrowser()
    }

    override fun onPause() {
        super.onPause()
        if (screen == Screen.EDITOR && webView != null) saveCurrentNote()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autosave)
        driveSyncCancelled?.set(true)
        driveExecutor.shutdownNow()
        noteIoExecutor.shutdownNow()
        releaseEditor()
        super.onDestroy()
    }

    /**
     * Editor screens create a new WebView every time. Releasing the previous instance keeps
     * repeated navigation from accumulating renderer processes.
     */
    private fun releaseEditor() {
        editorGeneration += 1L
        val editor = webView
        webView = null
        statusView = null
        saveActionView = null
        handler.removeCallbacks(autosave)
        if (editor == null) return
        (editor.parent as? android.view.ViewGroup)?.removeView(editor)
        editor.stopLoading()
        editor.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (screen) {
            Screen.EDITOR -> returnToBrowser()
            Screen.EDITOR_LOADING, Screen.EDITOR_ERROR -> showVaultBrowser()
            Screen.BROWSER -> {
                if (browserPath.isNotEmpty()) showParentDirectory() else super.onBackPressed()
            }
            Screen.PHOTO -> restoreEditorScreen()
            Screen.SETTINGS -> showVaultBrowser()
            Screen.DAILY_FOLDER_PICKER -> showSettings()
            Screen.DRIVE_SETUP, Screen.DRIVE_RESULT -> showSettings()
            Screen.DRIVE_FOLDER_PICKER, Screen.DRIVE_CONFIRM -> showDriveSetup()
            Screen.DRIVE_PROGRESS -> showSettings()
            Screen.WELCOME -> super.onBackPressed()
        }
    }

    private fun recoverVaultAndOpenBrowser() {
        releaseEditor()
        screen = Screen.BROWSER
        val root = pageRoot(COLOR_BACKGROUND).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "正在检查 Vault…"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(COLOR_SECONDARY_TEXT)
        }, matchWrap())
        setContentView(root)
        noteIoExecutor.execute {
            val result = repository.recoverVault()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (result) {
                    VaultRecoveryResult.Success -> showVaultBrowser(true)
                    is VaultRecoveryResult.Failure -> showWelcome(
                        if (result.kind == VaultFailureKind.PERMISSION_DENIED) {
                            "无法访问 Vault，请重新选择"
                        } else {
                            "无法检查当前 Vault，原文件未被修改，请重新选择或稍后重试"
                        }
                    )
                }
            }
        }
    }

    private fun showWelcome(message: String? = null) {
        releaseEditor()
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
        if (resetToRoot) {
            browserDirectory = null
            browserPath.clear()
            browserHistory.clear()
            browserScrollY = 0
        }
        releaseEditor()
        browserMutationPending = false
        screen = Screen.BROWSER
        val generation = ++browserLoadGeneration
        val requestedDirectory = browserDirectory
        val loadingRoot = pageRoot(COLOR_BACKGROUND)
        loadingRoot.addView(browserHeader(), matchWrap())
        loadingRoot.addView(emptyState("正在读取 Vault…"), matchWrap().apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            topMargin = dp(20)
        })
        setContentView(loadingRoot)
        noteIoExecutor.execute {
            val rootDirectory = repository.vaultRoot()
            if (rootDirectory == null) {
                runOnUiThread {
                    if (browserRequestIsCurrent(generation)) showWelcome("无法访问 Vault，请重新选择")
                }
                return@execute
            }
            val directory = requestedDirectory ?: rootDirectory
            val directoryReadable = repository.canReadDirectory(directory)
            val children = if (directoryReadable) {
                repository.children(directory)
                    .filterNot { isInternalDocument(it) }
                    .sortedWith(compareBy<VaultDocument> { !repository.isDirectory(it) }
                        .thenBy { it.name.lowercase() })
            } else {
                emptyList()
            }
            val previews = children
                .filter { !repository.isDirectory(it) && it.name.endsWith(".md", true) }
                .associate { it.uri.toString() to notePreview(it) }
            runOnUiThread {
                if (!browserRequestIsCurrent(generation)) return@runOnUiThread
                currentVaultName = rootDirectory.name
                if (requestedDirectory == null) browserDirectory = rootDirectory
                renderVaultBrowser(rootDirectory, directoryReadable, children, previews)
            }
        }
    }

    private fun browserRequestIsCurrent(generation: Long): Boolean =
        !isFinishing && !isDestroyed && screen == Screen.BROWSER && browserLoadGeneration == generation

    private fun renderVaultBrowser(
        rootDirectory: VaultDocument,
        directoryReadable: Boolean,
        children: List<VaultDocument>,
        previews: Map<String, String>
    ) {
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
        val folders = children.filter { repository.isDirectory(it) }
        val notes = children.filter { !repository.isDirectory(it) && it.name.endsWith(".md", true) }

        if (!directoryReadable) {
            content.addView(vaultAccessErrorState(), matchWrap())
        } else {
            browserMutationMessage?.let { message ->
                content.addView(infoBanner(message), matchWrap().apply { bottomMargin = dp(12) })
            }
            if (repository.dailyDirectoryResetNotice()) {
                content.addView(infoBanner("今日笔记目录已不可用，已改为 Vault 根目录。请在设置中重新选择目录。"),
                    matchWrap().apply { bottomMargin = dp(12) })
            }
            sectionLabel(content, "文件夹", folders.size)
            if (folders.isEmpty()) {
                content.addView(emptyState("当前目录没有文件夹"), matchWrap().apply { bottomMargin = dp(16) })
            } else {
                folders.forEach { folder ->
                    content.addView(vaultRow(
                        "▸", folder.name, "文件夹", trailingLabel = "更多",
                        trailingAction = { showDocumentMenu(folder) }
                    ) { openDirectory(folder) }, matchWrap().apply {
                        bottomMargin = dp(8)
                    })
                }
            }
            sectionLabel(content, "笔记", notes.size)
            if (notes.isEmpty()) {
                content.addView(emptyState("当前目录还没有 Markdown 笔记"), matchWrap())
            } else {
                notes.forEach { note ->
                    content.addView(
                        vaultRow(
                            "•",
                            note.name.removeSuffix(".md"),
                            previews[note.uri.toString()] ?: "空白笔记",
                            trailingLabel = "更多",
                            trailingAction = { showDocumentMenu(note) }
                        ) { openNote(note) },
                        matchWrap().apply { bottomMargin = dp(8) }
                    )
                }
            }
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (directoryReadable) root.addView(browserFooter(), matchWrap())
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
        setPadding(dp(16), dp(10), dp(16), dp(16))
        background = colorBlock(COLOR_SURFACE)
        addView(action("新建", false) { showCreateMenu() }, LinearLayout.LayoutParams(0, dp(48), 0.72f).apply {
            marginEnd = dp(8)
        })
        addView(action("打开今日笔记", true) { openDailyNote() }, LinearLayout.LayoutParams(0, dp(48), 1.28f))
    }

    private fun showSettings() {
        releaseEditor()
        screen = Screen.SETTINGS
        trashCount = null
        trashLoadFailed = false
        trashStatusMessage = null
        trashStatusView = null
        trashRowView = null
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
        sectionLabel(content, "同步")
        val driveRoot = drivePreferences.root()
        val driveStatus = when {
            driveRoot == null -> "未连接"
            !driveAuth.isAuthorized(driveAuth.currentAccount()) -> "需要重新登录 · ${driveRoot.name}"
            drivePreferences.lastSuccessAt() > 0L -> "${driveRoot.name} · 上次同步 ${formatSyncTime(drivePreferences.lastSuccessAt())}"
            else -> "已选择 ${driveRoot.name}"
        }
        content.addView(settingsRow("Google Drive", driveStatus, false) { showDriveSetup() }, matchWrap())
        sectionLabel(content, "每日笔记")
        val path = repository.dailyNoteDirectoryPath().ifBlank { "Vault 根目录" }
        content.addView(settingsRow("今日笔记目录", path, false) { showDailyFolderPicker(true) }, matchWrap())
        content.addView(TextView(this).apply {
            text = "设置不会移动已有笔记或附件。新建的每日笔记会按 yyyy-MM-dd.md 写入所选目录。"
            textSize = 13f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(6), dp(12), dp(6), 0)
        }, matchWrap())
        sectionLabel(content, "存储")
        content.addView(trashSettingsRow(), matchWrap())
        sectionLabel(content, "显示")
        val appearance = repository.appearanceMode()
        content.addView(settingsRow("日间模式", "浅色背景与深色文字", appearance == VaultRepository.APPEARANCE_DAY) {
            setAppearance(VaultRepository.APPEARANCE_DAY)
        }, matchWrap().apply { bottomMargin = dp(8) })
        content.addView(settingsRow("夜间模式", "深色工作区与深色编辑纸面", appearance == VaultRepository.APPEARANCE_NIGHT) {
            setAppearance(VaultRepository.APPEARANCE_NIGHT)
        }, matchWrap())
        sectionLabel(content, "语言")
        content.addView(settingsStatusRow("中文", "当前应用语言", "已启用  ✓", true), matchWrap().apply {
            bottomMargin = dp(8)
        })
        content.addView(settingsStatusRow("English", "完整英文翻译即将支持", "即将支持", false), matchWrap())
        sectionLabel(content, "关于")
        content.addView(
            settingsStatusRow(
                "版本",
                "v${BuildConfig.VERSION_NAME} · 构建 ${BuildConfig.VERSION_CODE}",
                "Markbook",
                false
            ),
            matchWrap()
        )
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (!trashClearPending) loadTrashStatus()
    }

    private fun trashSettingsRow(): View = LinearLayout(this).apply {
        trashRowView = this
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(68)
        setPadding(dp(16), dp(10), dp(12), dp(10))
        background = rounded(COLOR_ROW, dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            when {
                trashClearPending -> Unit
                trashLoadFailed -> loadTrashStatus()
                (trashCount ?: 0) > 0 -> confirmEmptyTrash()
            }
        }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "清空回收站"
                textSize = 16f
                setTextColor(COLOR_PRIMARY_TEXT)
            }, matchWrap())
            trashStatusView = TextView(this@MainActivity).apply {
                textSize = 13f
                maxLines = 2
                setTextColor(COLOR_MUTED_TEXT)
                setPadding(0, dp(3), 0, 0)
            }
            addView(trashStatusView, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = "›"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(COLOR_MUTED_TEXT)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        updateTrashRow()
    }

    private fun loadTrashStatus() {
        if (trashClearPending) return
        val generation = ++trashLoadGeneration
        trashCount = null
        trashLoadFailed = false
        updateTrashRow()
        noteIoExecutor.execute {
            val result = repository.trashContents()
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != trashLoadGeneration) return@runOnUiThread
                when (result) {
                    is TrashContentsResult.Success -> {
                        trashCount = result.count
                        trashLoadFailed = false
                    }
                    is TrashContentsResult.Failure -> {
                        trashCount = null
                        trashLoadFailed = true
                    }
                }
                if (screen == Screen.SETTINGS) updateTrashRow()
            }
        }
    }

    private fun updateTrashRow() {
        val status = when {
            trashClearPending -> "正在永久删除回收站内容…"
            trashStatusMessage != null -> trashStatusMessage.orEmpty()
            trashLoadFailed -> "无法读取回收站 · 点按重试"
            trashCount == null -> "正在统计回收站…"
            trashCount == 0 -> "回收站为空"
            else -> "${trashCount} 个项目 · 图片附件不会删除"
        }
        trashStatusView?.text = status
        val enabled = !trashClearPending && (trashLoadFailed || (trashCount ?: 0) > 0)
        trashRowView?.isEnabled = enabled
        trashRowView?.alpha = if (enabled) 1f else 0.62f
        trashRowView?.contentDescription = "清空回收站，$status"
    }

    private fun confirmEmptyTrash() {
        val count = trashCount ?: return
        if (count <= 0 || trashClearPending) return
        AlertDialog.Builder(this)
            .setTitle("永久清空回收站？")
            .setMessage(
                "将永久删除当前 Vault/.trash 中的 $count 个项目。此操作无法撤销。" +
                    "图片附件不会被删除。"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("永久删除") { _, _ -> emptyTrash() }
            .show()
    }

    private fun emptyTrash() {
        if (trashClearPending) return
        trashClearPending = true
        trashStatusMessage = null
        updateTrashRow()
        noteIoExecutor.execute {
            val result = repository.emptyTrash()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                trashClearPending = false
                trashCount = if (result.failed > 0) {
                    result.remaining.coerceAtLeast(result.failed)
                } else {
                    result.remaining
                }
                trashLoadFailed = false
                trashStatusMessage = when {
                    result.failed == 0 -> "已永久删除 ${result.deleted} 个项目"
                    result.deleted > 0 -> "已删除 ${result.deleted} 个，${result.failed} 个失败 · 点按重试"
                    else -> "删除失败，${result.remaining.coerceAtLeast(result.failed)} 个项目仍保留 · 点按重试"
                }
                if (screen == Screen.SETTINGS) updateTrashRow()
            }
        }
    }

    private fun setAppearance(mode: String) {
        repository.setAppearanceMode(mode)
        applyWindowColors()
        showSettings()
    }

    private fun showDriveSetup(message: String? = null) {
        releaseEditor()
        screen = Screen.DRIVE_SETUP
        val root = pageRoot(COLOR_BACKGROUND)
        root.addView(simpleToolbar("‹  设置", "Google Drive") { showSettings() }, matchWrap())
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(24))
        }
        message?.let { content.addView(infoBanner(it), matchWrap().apply { bottomMargin = dp(12) }) }
        val account = driveAuth.currentAccount()
        if (!driveAuth.isAuthorized(account)) {
            content.addView(emptyState("连接 Google 账号后，才能选择用于电脑 Obsidian 的 Drive Vault。"), matchWrap().apply {
                bottomMargin = dp(14)
            })
            content.addView(action("连接 Google Drive", true) {
                startActivityForResult(driveAuth.signInIntent(), DRIVE_SIGN_IN_REQUEST)
            }, matchWrap())
        } else {
            content.addView(TextView(this).apply {
                text = "已连接 ${account?.email ?: "Google 账号"}"
                textSize = 14f
                setTextColor(COLOR_SECONDARY_TEXT)
                setPadding(dp(6), dp(2), dp(6), dp(14))
            }, matchWrap())
            val selectedRoot = drivePreferences.root()
            content.addView(settingsRow(
                "Drive Vault",
                selectedRoot?.name ?: "尚未选择远端文件夹",
                selectedRoot != null
            ) { showDriveFolderPicker(true) }, matchWrap().apply { bottomMargin = dp(12) })
            if (selectedRoot == null) {
                content.addView(action("选择 Drive Vault", true) { showDriveFolderPicker(true) }, matchWrap())
            } else {
                content.addView(TextView(this).apply {
                    text = "同步会比较 Markdown 和 assets；不会同步 .obsidian、.trash，也不会传播删除。"
                    textSize = 13f
                    setTextColor(COLOR_MUTED_TEXT)
                    setPadding(dp(6), dp(2), dp(6), dp(14))
                }, matchWrap())
                content.addView(action("比较并同步", true) { showDriveSyncConfirmation(selectedRoot) }, matchWrap())
                content.addView(action("更换 Drive Vault", false) { showDriveFolderPicker(true) }, matchWrap().apply {
                    topMargin = dp(8)
                })
            }
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showDriveFolderPicker(reset: Boolean = false) {
        if (!driveAuth.isAuthorized(driveAuth.currentAccount())) {
            showDriveSetup("Google 账号需要重新登录")
            return
        }
        if (reset) {
            driveFolderDirectory = DriveVaultRoot("root", "我的云端硬盘")
            driveFolderHistory.clear()
        }
        drivePickerFolders = null
        drivePickerError = null
        drivePickerMessage = "正在读取 Drive 文件夹…"
        screen = Screen.DRIVE_FOLDER_PICKER
        renderDriveFolderPicker()
        loadDriveFolders()
    }

    private fun renderDriveFolderPicker() {
        val root = pageRoot(COLOR_BACKGROUND)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            background = colorBlock(COLOR_SURFACE)
            addView(action("‹  Google Drive", false) { showDriveSetup() })
            addView(TextView(this@MainActivity).apply {
                text = "选择 Drive Vault"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(COLOR_PRIMARY_TEXT)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(action("使用此目录", true) { confirmDriveFolder() })
        }
        root.addView(toolbar, matchWrap())
        root.addView(TextView(this).apply {
            text = driveFolderDirectory.name
            textSize = 13f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }, matchWrap())
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        if (driveFolderHistory.isNotEmpty()) {
            content.addView(action("‹  上一级", false) {
                driveFolderDirectory = driveFolderHistory.removeAt(driveFolderHistory.lastIndex)
                drivePickerFolders = null
                drivePickerError = null
                drivePickerMessage = "正在读取 Drive 文件夹…"
                renderDriveFolderPicker()
                loadDriveFolders()
            }, wrapWrap().apply { bottomMargin = dp(8) })
        }
        content.addView(action("新建文件夹", false) { promptCreateDriveFolder() }, matchWrap().apply {
            bottomMargin = dp(12)
        })
        when {
            drivePickerError != null -> content.addView(infoBanner(drivePickerError!!), matchWrap())
            drivePickerFolders == null -> content.addView(emptyState(drivePickerMessage ?: "正在读取 Drive 文件夹…"), matchWrap())
            drivePickerFolders!!.isEmpty() -> content.addView(emptyState("此目录没有子文件夹，仍可选择它作为 Vault。"), matchWrap())
            else -> {
                sectionLabel(content, "文件夹", drivePickerFolders!!.size)
                drivePickerFolders!!.forEach { folder ->
                    content.addView(vaultRow("▸", folder.name, "Google Drive 文件夹") {
                        driveFolderHistory += driveFolderDirectory
                        driveFolderDirectory = DriveVaultRoot(folder.id, folder.name)
                        drivePickerFolders = null
                        drivePickerError = null
                        drivePickerMessage = "正在读取 Drive 文件夹…"
                        renderDriveFolderPicker()
                        loadDriveFolders()
                    }, matchWrap().apply { bottomMargin = dp(8) })
                }
            }
        }
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadDriveFolders() {
        val location = driveFolderDirectory
        val account = driveAuth.currentAccount() ?: return
        driveExecutor.execute {
            try {
                val folders = GoogleDriveApi(driveAuth.accessToken(account)).listChildren(location.id)
                    .filter { it.mimeType == GoogleDriveApi.FOLDER_MIME_TYPE }
                    .sortedBy { it.name.lowercase() }
                runOnUiThread {
                    if (screen == Screen.DRIVE_FOLDER_PICKER && driveFolderDirectory.id == location.id) {
                        drivePickerFolders = folders
                        drivePickerMessage = null
                        renderDriveFolderPicker()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (screen == Screen.DRIVE_FOLDER_PICKER && driveFolderDirectory.id == location.id) {
                        drivePickerError = "无法读取 Google Drive，请检查网络或重新登录后重试"
                        drivePickerMessage = null
                        renderDriveFolderPicker()
                    }
                }
            }
        }
    }

    private fun confirmDriveFolder() {
        drivePreferences.setRoot(driveFolderDirectory)
        showDriveSetup("已选择 ${driveFolderDirectory.name}。同步前仍会再次确认范围。")
    }

    private fun promptCreateDriveFolder() {
        val input = EditText(this).apply {
            hint = "文件夹名称"
            setSingleLine(true)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("新建 Drive 文件夹")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (!validDriveFolderName(name)) {
                    input.error = "请输入不含 / 的文件夹名称"
                    return@setOnClickListener
                }
                dialog.dismiss()
                createDriveFolder(name)
            }
        }
        dialog.show()
    }

    private fun createDriveFolder(name: String) {
        val location = driveFolderDirectory
        val account = driveAuth.currentAccount() ?: run {
            showDriveSetup("Google 账号需要重新登录")
            return
        }
        drivePickerFolders = null
        drivePickerError = null
        drivePickerMessage = "正在创建 $name…"
        renderDriveFolderPicker()
        driveExecutor.execute {
            try {
                val api = GoogleDriveApi(driveAuth.accessToken(account))
                val folder = api.listChildren(location.id).firstOrNull {
                    it.name == name && it.mimeType == GoogleDriveApi.FOLDER_MIME_TYPE
                } ?: api.createFolder(location.id, name)
                runOnUiThread {
                    if (screen == Screen.DRIVE_FOLDER_PICKER && driveFolderDirectory.id == location.id) {
                        driveFolderHistory += location
                        driveFolderDirectory = DriveVaultRoot(folder.id, folder.name)
                        drivePickerFolders = null
                        drivePickerError = null
                        drivePickerMessage = "正在读取 Drive 文件夹…"
                        renderDriveFolderPicker()
                        loadDriveFolders()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (screen == Screen.DRIVE_FOLDER_PICKER && driveFolderDirectory.id == location.id) {
                        drivePickerError = "无法创建文件夹，请检查网络或重新登录后重试"
                        drivePickerMessage = null
                        renderDriveFolderPicker()
                    }
                }
            }
        }
    }

    private fun validDriveFolderName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')

    private fun showDriveSyncConfirmation(rootSelection: DriveVaultRoot) {
        screen = Screen.DRIVE_CONFIRM
        val root = pageRoot(COLOR_BACKGROUND)
        root.addView(simpleToolbar("‹  Google Drive", "确认同步") { showDriveSetup() }, matchWrap())
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        content.addView(TextView(this).apply {
            text = "比较并同步"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_TEXT)
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "本地 Vault：$currentVaultName\nGoogle Drive：${rootSelection.name}\n\n将比较 Markdown 与 assets。.obsidian、.trash、临时文件和本机同步信息不会上传。\n\n同名但内容不同的文件会各保留一份冲突副本；本阶段不会删除任何一端的文件。"
            textSize = 15f
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(0, dp(12), 0, dp(22))
        }, matchWrap())
        content.addView(action("开始同步", true) { startDriveSync(rootSelection) }, matchWrap())
        content.addView(action("取消", false) { showDriveSetup() }, matchWrap().apply { topMargin = dp(8) })
        root.addView(content, matchWrap())
        setContentView(root)
    }

    private fun startDriveSync(rootSelection: DriveVaultRoot) {
        val account = driveAuth.currentAccount()
        if (!driveAuth.isAuthorized(account) || account == null) {
            showDriveSetup("Google 账号需要重新登录")
            return
        }
        val cancellation = AtomicBoolean(false)
        driveSyncCancelled = cancellation
        showDriveSyncProgress("正在连接 Google Drive…", cancellation)
        driveExecutor.execute {
            val result = try {
                val token = driveAuth.accessToken(account)
                GoogleDriveSyncService(repository, GoogleDriveApi(token), cancellation) { progress ->
                    runOnUiThread {
                        if (screen == Screen.DRIVE_PROGRESS && driveSyncCancelled === cancellation) {
                            updateDriveProgress(progress)
                        }
                    }
                }.sync(rootSelection)
            } catch (_: Exception) {
                DriveSyncResult(0, 0, 0, 0, listOf("无法连接 Google Drive，请检查网络或重新登录"), false)
            }
            if (result.isSuccessful) drivePreferences.markSuccessful()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (screen == Screen.DRIVE_PROGRESS && driveSyncCancelled === cancellation) showDriveSyncResult(result)
            }
        }
    }

    private fun showDriveSyncProgress(message: String, cancellation: AtomicBoolean) {
        screen = Screen.DRIVE_PROGRESS
        val root = pageRoot(COLOR_BACKGROUND)
        root.addView(simpleToolbar("‹  设置", "正在同步") { showSettings() }, matchWrap())
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(34), dp(20), dp(24))
        }
        content.addView(TextView(this).apply {
            text = "Google Drive 同步"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_TEXT)
            gravity = Gravity.CENTER
        }, matchWrap())
        driveProgressView = TextView(this).apply {
            text = message
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(dp(8), dp(16), dp(8), dp(22))
        }
        content.addView(driveProgressView, matchWrap())
        content.addView(action("取消同步", false) {
            cancellation.set(true)
            driveProgressView?.text = "将在当前文件完成后取消…"
        }, wrapWrap())
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun updateDriveProgress(progress: DriveSyncProgress) {
        driveProgressView?.text = if (progress.total == 0) progress.message else {
            "${progress.message}\n${progress.completed} / ${progress.total}"
        }
    }

    private fun showDriveSyncResult(result: DriveSyncResult) {
        driveProgressView = null
        screen = Screen.DRIVE_RESULT
        val root = pageRoot(COLOR_BACKGROUND)
        root.addView(simpleToolbar("‹  设置", "同步结果") { showSettings() }, matchWrap())
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        val heading = when {
            result.cancelled -> "同步已取消"
            result.errors.isNotEmpty() -> "同步未完全完成"
            result.conflicts > 0 -> "同步完成，保留了冲突副本"
            else -> "同步完成"
        }
        content.addView(TextView(this).apply {
            text = heading
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_TEXT)
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "上传 ${result.uploaded} 个，下载 ${result.downloaded} 个，未变更 ${result.unchanged} 个，冲突 ${result.conflicts} 个。\n\n本阶段不会同步删除；同名冲突已分别保存为包含 Markbook 或 Google Drive 的副本。"
            textSize = 15f
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(0, dp(12), 0, dp(12))
        }, matchWrap())
        if (result.errors.isNotEmpty()) {
            content.addView(infoBanner("以下文件未完成：\n${result.errors.take(5).joinToString("\n")}"), matchWrap().apply {
                bottomMargin = dp(12)
            })
        }
        content.addView(action("返回设置", true) { showSettings() }, matchWrap())
        scroll.addView(content, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showDailyFolderPicker(reset: Boolean = false) {
        if (reset) {
            dailyFolderDirectory = null
            dailyFolderPath.clear()
            dailyFolderHistory.clear()
        }
        screen = Screen.DAILY_FOLDER_PICKER
        val generation = ++dailyFolderLoadGeneration
        val requestedDirectory = dailyFolderDirectory
        val loading = pageRoot(COLOR_BACKGROUND)
        loading.addView(simpleToolbar("‹  设置", "选择今日笔记目录") { showSettings() }, matchWrap())
        loading.addView(emptyState("正在读取 Vault 文件夹…"), matchWrap().apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            topMargin = dp(20)
        })
        setContentView(loading)
        noteIoExecutor.execute {
            val rootDirectory = repository.vaultRoot()
            val directory = requestedDirectory ?: rootDirectory
            val folders = if (directory == null) {
                emptyList()
            } else {
                repository.children(directory)
                    .filter { repository.isDirectory(it) && !isInternalDocument(it) }
                    .sortedBy { it.name.lowercase() }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || screen != Screen.DAILY_FOLDER_PICKER ||
                    generation != dailyFolderLoadGeneration) return@runOnUiThread
                if (rootDirectory == null || directory == null) {
                    showWelcome("无法访问 Vault，请重新选择")
                    return@runOnUiThread
                }
                if (requestedDirectory == null) dailyFolderDirectory = rootDirectory
                renderDailyFolderPicker(folders)
            }
        }
    }

    private fun renderDailyFolderPicker(folders: List<VaultDocument>) {
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
        showNoteLoading(null, "正在打开今日笔记…")
        val generation = editorGeneration
        noteIoExecutor.execute {
            val result = repository.openDailyNote()
            runOnUiThread {
                if (!editorRequestIsCurrent(generation)) return@runOnUiThread
                when (result) {
                    is DailyNoteResult.Created -> {
                        currentNote = result.document
                        showEditor(result.document, "")
                    }
                    is DailyNoteResult.Existing -> openNote(result.document)
                    is DailyNoteResult.Failure -> showNoteOpenError(
                        null,
                        result.kind,
                        "今日笔记尚未打开，Vault 内容未被修改"
                    ) { openDailyNote() }
                }
            }
        }
    }

    private fun openNote(note: VaultDocument) {
        currentNote = note
        showNoteLoading(note, "正在读取笔记…")
        val generation = editorGeneration
        noteIoExecutor.execute {
            val result = repository.readNote(note)
            runOnUiThread {
                if (!editorRequestIsCurrent(generation, note)) return@runOnUiThread
                when (result) {
                    is NoteReadResult.Success -> showEditor(note, result.content)
                    is NoteReadResult.Failure -> showNoteOpenError(
                        note,
                        result.kind,
                        "原笔记未被修改"
                    ) { openNote(note) }
                }
            }
        }
    }

    private fun showNoteLoading(note: VaultDocument?, message: String) {
        releaseEditor()
        currentNote = note
        screen = Screen.EDITOR_LOADING
        val root = pageRoot(COLOR_EDITOR_BACKGROUND)
        root.addView(simpleToolbar("‹  文件", note?.name?.removeSuffix(".md") ?: "今日笔记") {
            showVaultBrowser()
        }, matchWrap())
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(44), dp(24), dp(24))
            addView(TextView(this@MainActivity).apply {
                text = message
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(COLOR_SECONDARY_TEXT)
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = "读取完成前不会显示默认正文或写入文件。"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(COLOR_MUTED_TEXT)
                setPadding(0, dp(10), 0, 0)
            }, matchWrap())
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showNoteOpenError(
        note: VaultDocument?,
        kind: VaultFailureKind,
        preservationMessage: String,
        retry: () -> Unit
    ) {
        releaseEditor()
        currentNote = note
        screen = Screen.EDITOR_ERROR
        val root = pageRoot(COLOR_EDITOR_BACKGROUND)
        root.addView(simpleToolbar("‹  文件", note?.name?.removeSuffix(".md") ?: "今日笔记") {
            showVaultBrowser()
        }, matchWrap())
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
            addView(TextView(this@MainActivity).apply {
                text = if (kind == VaultFailureKind.PERMISSION_DENIED) {
                    "无法访问当前 Vault"
                } else {
                    "无法读取这篇笔记"
                }
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_PRIMARY_TEXT)
            }, matchWrap())
            addView(infoBanner("$preservationMessage。你可以重试、重新选择 Vault，或返回文件库。"),
                matchWrap().apply { topMargin = dp(14); bottomMargin = dp(16) })
            addView(action("重试", true, retry), matchWrap())
            addView(action("重新选择 Vault", false) { chooseVault() }, matchWrap().apply { topMargin = dp(8) })
            addView(action("返回文件库", false) { showVaultBrowser() }, matchWrap().apply { topMargin = dp(8) })
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun editorRequestIsCurrent(generation: Long, note: VaultDocument? = currentNote): Boolean {
        if (isFinishing || isDestroyed || generation != editorGeneration) return false
        if (screen != Screen.EDITOR_LOADING) return false
        return note == null || currentNote?.uri == note.uri
    }

    private fun showCreateMenu() {
        if (browserMutationPending) return
        browserMutationMessage = null
        AlertDialog.Builder(this)
            .setTitle("新建")
            .setItems(arrayOf("新建笔记", "新建文件夹")) { _, index ->
                if (index == 0) showCreateDialog(VaultEntryKind.NOTE) else showCreateDialog(VaultEntryKind.FOLDER)
            }
            .show()
    }

    private fun showCreateDialog(kind: VaultEntryKind) {
        val parent = browserDirectory ?: return
        val input = EditText(this).apply {
            hint = if (kind == VaultEntryKind.NOTE) "笔记名称" else "文件夹名称"
            setSingleLine(true)
            setPadding(dp(24), dp(4), dp(24), dp(4))
        }
        val verb = if (kind == VaultEntryKind.NOTE) "创建笔记" else "创建文件夹"
        val dialog = AlertDialog.Builder(this)
            .setTitle(verb)
            .setView(mutationDialogView(input, "当前位置：${parent.relativePath.ifBlank { "Vault 根目录" }}" + if (kind == VaultEntryKind.NOTE) "\n笔记会自动添加 .md" else ""))
            .setNegativeButton("取消", null)
            .setPositiveButton("创建", null)
            .create()
        dialog.setOnShowListener {
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submit.setOnClickListener {
                when (val validation = VaultNamePolicy.validate(input.text.toString(), kind)) {
                    is VaultNameValidation.Invalid -> input.error = validation.message
                    is VaultNameValidation.Valid -> {
                        submit.isEnabled = false
                        submit.text = "创建中…"
                        dialog.setCancelable(false)
                        dialog.setCanceledOnTouchOutside(false)
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                        browserMutationPending = true
                        val generation = browserLoadGeneration
                        val requestedName = input.text.toString()
                        noteIoExecutor.execute {
                            val result = if (kind == VaultEntryKind.NOTE) {
                                repository.createNote(parent, requestedName)
                            } else {
                                repository.createFolder(parent, requestedName)
                            }
                            runOnUiThread {
                                if (isFinishing || isDestroyed || screen != Screen.BROWSER || browserLoadGeneration != generation) return@runOnUiThread
                                browserMutationPending = false
                                when (result) {
                                    is VaultMutationResult.Success -> {
                                        dialog.dismiss()
                                        providerNameMessage(result)?.let(::toast)
                                        val document = result.document
                                        if (document == null) {
                                            showVaultBrowser()
                                        } else if (kind == VaultEntryKind.NOTE) openNote(document) else openDirectory(document)
                                    }
                                    is VaultMutationResult.Failure -> {
                                        submit.isEnabled = true
                                        submit.text = "创建"
                                        dialog.setCancelable(true)
                                        dialog.setCanceledOnTouchOutside(true)
                                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                                        input.error = mutationFailureMessage(result)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showDocumentMenu(document: VaultDocument) {
        if (browserMutationPending) return
        browserMutationMessage = null
        AlertDialog.Builder(this)
            .setTitle(document.name)
            .setItems(arrayOf("重命名", "移到回收站")) { _, index ->
                if (index == 0) showRenameDialog(document) else confirmMoveToTrash(document)
            }
            .show()
    }

    private fun showRenameDialog(document: VaultDocument) {
        val kind = if (repository.isDirectory(document)) VaultEntryKind.FOLDER else VaultEntryKind.NOTE
        val input = EditText(this).apply {
            setText(if (kind == VaultEntryKind.NOTE) document.name.removeSuffix(".md") else document.name)
            selectAll()
            setSingleLine(true)
            setPadding(dp(24), dp(4), dp(24), dp(4))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(mutationDialogView(input, "当前位置：${document.parentRelativePath.ifBlank { "Vault 根目录" }}\n不会更新其他 Markdown 链接"))
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submit.setOnClickListener {
                when (val validation = VaultNamePolicy.validate(input.text.toString(), kind)) {
                    is VaultNameValidation.Invalid -> input.error = validation.message
                    is VaultNameValidation.Valid -> {
                        submit.isEnabled = false
                        submit.text = "保存中…"
                        dialog.setCancelable(false)
                        dialog.setCanceledOnTouchOutside(false)
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                        browserMutationPending = true
                        val generation = browserLoadGeneration
                        val requestedName = input.text.toString()
                        noteIoExecutor.execute {
                            val result = repository.rename(document, requestedName)
                            runOnUiThread {
                                if (isFinishing || isDestroyed || screen != Screen.BROWSER || browserLoadGeneration != generation) return@runOnUiThread
                                browserMutationPending = false
                                when (result) {
                                    is VaultMutationResult.Success -> {
                                        dialog.dismiss()
                                        providerNameMessage(result)?.let(::toast)
                                        dailyDirectoryChangeMessage(result.dailyDirectoryChange)?.let(::toast)
                                        showVaultBrowser()
                                    }
                                    is VaultMutationResult.Failure -> {
                                        submit.isEnabled = true
                                        submit.text = "保存"
                                        dialog.setCancelable(true)
                                        dialog.setCanceledOnTouchOutside(true)
                                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                                        input.error = mutationFailureMessage(result)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmMoveToTrash(document: VaultDocument) {
        val label = if (repository.isDirectory(document)) "文件夹“${document.name}”" else "笔记“${document.name.removeSuffix(".md")}”"
        AlertDialog.Builder(this)
            .setTitle("移到回收站？")
            .setMessage("$label 将整体移入 Vault/.trash，不会同步到 Google Drive。Markdown 链接不会更新，图片附件不会自动删除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移到回收站") { _, _ ->
                val generation = browserLoadGeneration
                browserMutationPending = true
                val progress = AlertDialog.Builder(this)
                    .setTitle("正在移到回收站…")
                    .setMessage("正在处理 Vault 文件；请勿重复操作。")
                    .create()
                progress.setCancelable(false)
                progress.setCanceledOnTouchOutside(false)
                progress.show()
                noteIoExecutor.execute {
                    val result = repository.moveToTrash(document)
                    runOnUiThread {
                        progress.dismiss()
                        if (isFinishing || isDestroyed || screen != Screen.BROWSER || browserLoadGeneration != generation) return@runOnUiThread
                        browserMutationPending = false
                        when (result) {
                            is VaultMutationResult.Success -> {
                                toast(dailyDirectoryChangeMessage(result.dailyDirectoryChange) ?: "已移到回收站")
                                showVaultBrowser()
                            }
                            is VaultMutationResult.Failure -> {
                                browserMutationMessage = "移动未完成，源文件未改动。请从“更多”重试，或重新选择 Vault。\n${mutationFailureMessage(result)}"
                                showVaultBrowser()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun providerNameMessage(result: VaultMutationResult.Success): String? =
        when {
            result.actualName == null -> "操作已完成，名称待刷新"
            result.requestedName == result.actualName -> null
            else -> "Provider 已保存为“${result.actualName}”"
        }

    private fun mutationDialogView(input: EditText, message: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        addView(TextView(this@MainActivity).apply {
            text = message
            textSize = 13f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(16), 0, dp(16), dp(8))
        }, matchWrap())
        addView(input, matchWrap())
    }

    private fun dailyDirectoryChangeMessage(change: DailyDirectoryChange): String? = when (change) {
        DailyDirectoryChange.UNCHANGED -> null
        DailyDirectoryChange.REWRITTEN -> "今日笔记目录已随文件夹重命名更新"
        DailyDirectoryChange.RESET_TO_ROOT -> "今日笔记目录已改为 Vault 根目录"
    }

    private fun mutationFailureMessage(result: VaultMutationResult.Failure): String = result.message ?: when (result.kind) {
        VaultMutationFailureKind.PERMISSION_DENIED -> "无法访问 Vault，请重新选择"
        VaultMutationFailureKind.READ_FAILED -> "无法读取当前目录，请稍后重试"
        VaultMutationFailureKind.INVALID_NAME -> "名称不合法"
        VaultMutationFailureKind.NAME_CONFLICT -> "当前目录已有同名项目"
        VaultMutationFailureKind.CREATE_FAILED -> "无法创建，请检查 Vault 权限"
        VaultMutationFailureKind.RENAME_FAILED -> "无法重命名，请检查 Vault 权限"
        VaultMutationFailureKind.TRASH_NAME_CONFLICT -> "回收站已有同名项目"
        VaultMutationFailureKind.MOVE_UNSUPPORTED -> "无法移到回收站：当前 Provider 不支持整体移动，原文件未改动"
    }

    private fun showEditor(note: VaultDocument, content: String) {
        releaseEditor()
        saveCoordinator.reset()
        saveWaiters.clear()
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
        saveActionView = action("保存", true) { saveCurrentNote() }
        toolbar.addView(saveActionView)
        root.addView(toolbar, matchWrap())
        root.addView(TextView(this).apply {
            text = editorContext(note)
            textSize = 12f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(20), dp(7), dp(20), dp(2))
            background = colorBlock(COLOR_SURFACE)
            maxLines = 1
        }, matchWrap())
        statusView = TextView(this).apply {
            text = "已保存"
            textSize = 13f
            setTextColor(COLOR_SECONDARY_TEXT)
            setPadding(dp(20), dp(2), dp(20), dp(9))
            background = colorBlock(COLOR_SURFACE)
        }
        root.addView(statusView, matchWrap())
        val editor = WebView(this).apply {
            setBackgroundColor(COLOR_EDITOR_BACKGROUND)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = attachmentClient()
            addJavascriptInterface(EditorBridge(), "Android")
        }
        webView = editor
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(markdownToolbar(), matchWrap())
        setContentView(root)
        editor.loadDataWithBaseURL(null, renderNote(content), "text/html", "UTF-8", null)
        updateSaveStatus()
    }

    private fun renderNote(content: String): String =
        MarkdownCodec.toHtml(content, repository::htmlAttachmentUrl, isNightTheme())

    private fun markdownToolbar(): View = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        background = colorBlock(COLOR_SURFACE)
        setPadding(dp(12), dp(6), dp(12), dp(10))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            background = rounded(COLOR_ROW, dp(24))
            addView(formatIconAction(R.drawable.ic_editor_undo, "撤销") { applyEditorFormat("undo") })
            addView(formatIconAction(R.drawable.ic_editor_redo, "重做") { applyEditorFormat("redo") })
            addView(formatIconAction(R.drawable.ic_editor_heading, "标题格式") { showHeadingPicker() })
            addView(formatIconAction(R.drawable.ic_editor_bold, "加粗") { applyEditorFormat("bold") })
            addView(formatIconAction(R.drawable.ic_editor_italic, "斜体") { applyEditorFormat("italic") })
            addView(formatIconAction(R.drawable.ic_editor_tag, "添加标签") { promptForTag() })
            addView(formatIconAction(R.drawable.ic_editor_link, "添加链接") { promptForLink() })
            addView(formatIconAction(R.drawable.ic_editor_table, "插入表格") { applyEditorFormat("table") })
        }, LinearLayout.LayoutParams(-2, dp(44)))
    }

    private fun formatIconAction(icon: Int, label: String, onClick: () -> Unit): ImageButton = ImageButton(this).apply {
        setImageResource(icon)
        setColorFilter(COLOR_PRIMARY_TEXT)
        scaleType = android.widget.ImageView.ScaleType.CENTER
        minimumWidth = dp(44)
        minimumHeight = dp(44)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        val ripple = android.util.TypedValue()
        this@MainActivity.theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            ripple,
            true
        )
        if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId)
        isClickable = true
        isFocusable = true
        contentDescription = label
        tooltipText = label
        setOnClickListener { onClick() }
    }

    private fun applyEditorFormat(command: String, value: String? = null) {
        val editor = webView ?: return
        val commandArgument = org.json.JSONObject.quote(command)
        val valueArgument = value?.let { org.json.JSONObject.quote(it) } ?: "null"
        editor.evaluateJavascript(
            "window.markbook && window.markbook.applyFormat ? window.markbook.applyFormat($commandArgument, $valueArgument) : false",
            null
        )
    }

    private fun showHeadingPicker() {
        val labels = arrayOf("正文", "一级标题 H1", "二级标题 H2", "三级标题 H3", "四级标题 H4", "五级标题 H5")
        val values = arrayOf("p", "h1", "h2", "h3", "h4", "h5")
        AlertDialog.Builder(this)
            .setTitle("标题格式")
            .setItems(labels) { _, index -> applyEditorFormat("heading", values[index]) }
            .show()
    }

    private fun promptForTag() {
        promptForEditorValue("添加标签", "标签名，例如 项目", "添加") { value ->
            val tag = value.trim().removePrefix("#")
            when {
                tag.isBlank() -> "标签不能为空"
                tag.any { it == '/' || it == '\\' } -> "标签不能包含路径分隔符"
                tag.any { it.isWhitespace() } -> "标签不能包含空格"
                else -> {
                    applyEditorFormat("tag", tag)
                    null
                }
            }
        }
    }

    private fun promptForLink() {
        promptForEditorValue("添加链接", "https://example.com", "添加") { value ->
            val link = value.trim()
            if (link.isBlank()) "链接不能为空" else {
                applyEditorFormat("link", link)
                null
            }
        }
    }

    private fun promptForEditorValue(
        title: String,
        hint: String,
        confirmLabel: String,
        onConfirm: (String) -> String?
    ) {
        val input = EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
            setPadding(dp(24), dp(4), dp(24), dp(4))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton(confirmLabel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = onConfirm(input.text.toString())
                if (error == null) dialog.dismiss() else input.error = error
            }
        }
        dialog.show()
    }

    private fun returnToBrowser() {
        saveCurrentNote { success ->
            if (success) showVaultBrowser() else toast("保存失败，请先恢复 Vault 访问权限")
        }
    }

    private fun saveCurrentNote(onComplete: ((Boolean) -> Unit)? = null) {
        val editor = webView
        if (screen != Screen.EDITOR || editor == null) {
            onComplete?.invoke(false)
            return
        }
        if (!saveCoordinator.hasUnsavedChanges && !saveCoordinator.hasInFlightSave) {
            onComplete?.invoke(true)
            return
        }
        onComplete?.let(saveWaiters::add)
        handler.removeCallbacks(autosave)
        startNextSave()
    }

    private fun startNextSave() {
        val editor = webView ?: return
        val note = currentNote ?: return
        val request = saveCoordinator.beginSave() ?: run {
            updateSaveStatus()
            return
        }
        val generation = editorGeneration
        updateSaveStatus()
        editor.evaluateJavascript("window.markbook && window.markbook.serialize ? window.markbook.serialize() : ''") { value ->
            if (generation != editorGeneration || screen != Screen.EDITOR || noteIoExecutor.isShutdown) {
                return@evaluateJavascript
            }
            val content = decodeJavascriptString(value)
            noteIoExecutor.execute {
                val success = repository.saveText(note, content)
                val refreshed = if (success) repository.refreshDocument(note) else null
                runOnUiThread {
                    if (isFinishing || isDestroyed || generation != editorGeneration || screen != Screen.EDITOR) {
                        return@runOnUiThread
                    }
                    saveCoordinator.complete(request, success)
                    if (success && refreshed != null) currentNote = refreshed
                    updateSaveStatus()
                    if (!success) {
                        val pending = saveWaiters.toList()
                        saveWaiters.clear()
                        pending.forEach { it(false) }
                    } else if (saveCoordinator.hasUnsavedChanges) {
                        startNextSave()
                    } else {
                        val pending = saveWaiters.toList()
                        saveWaiters.clear()
                        pending.forEach { it(true) }
                    }
                }
            }
        }
    }

    private fun updateSaveStatus() {
        val state = saveCoordinator.state
        statusView?.text = when (state) {
            RevisionSaveCoordinator.State.SAVED -> "已保存"
            RevisionSaveCoordinator.State.DIRTY -> "未保存"
            RevisionSaveCoordinator.State.SAVING -> "正在保存…"
            RevisionSaveCoordinator.State.FAILED -> "保存失败 · 请检查 Vault 权限或存储空间后重试"
        }
        saveActionView?.isEnabled = !saveCoordinator.hasInFlightSave
        saveActionView?.alpha = if (saveCoordinator.hasInFlightSave) 0.55f else 1f
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
            capturePhotoContext { launchCamera() }
        }
    }

    private fun capturePhotoContext(onReady: () -> Unit) {
        val editor = webView
        if (editor == null) {
            onReady()
            return
        }
        photoContextScrollY = editor.scrollY
        editor.evaluateJavascript(
            "window.markbook && window.markbook.serializeWithCaret ? window.markbook.serializeWithCaret() : ''"
        ) { value ->
            photoContextContent = decodeJavascriptString(value).ifBlank { null }
            onReady()
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
                recoverVaultAndOpenBrowser()
            }
            return
        }
        if (requestCode == DRIVE_SIGN_IN_REQUEST) {
            try {
                val account = driveAuth.accountFromResult(data)
                if (driveAuth.isAuthorized(account)) {
                    showDriveSetup("Google Drive 已连接，请选择远端 Vault。")
                } else {
                    showDriveSetup("Google 账号尚未授予 Drive 访问权限")
                }
            } catch (_: ApiException) {
                showDriveSetup("无法连接 Google Drive，请重试或确认该账号是测试用户")
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
        releaseEditor()
        screen = Screen.PHOTO
        photoSavePending = false
        val root = pageRoot(Color.BLACK)
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
        }
        val view = PhotoEditorView(this, bitmap)
        editorView = view
        header.addView(action("取消", false) { if (!photoSavePending) restoreEditorScreen() })
        header.addView(TextView(this).apply {
            text = "调整照片"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        photoInsertAction = action("插入照片", true) { commitPhoto(bitmap, view) }
        header.addView(photoInsertAction)
        photoStatusView = TextView(this).apply {
            text = "拖动四个角点调整裁剪范围"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(2), dp(20), dp(10))
        }
        val modes = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        val rectangleAction = action("矩形裁剪", false) { setPhotoMode(view, PhotoEditMode.RECTANGLE) }
        val perspectiveAction = action("四点校正", false) { setPhotoMode(view, PhotoEditMode.PERSPECTIVE) }
        photoModeActions = listOf(rectangleAction, perspectiveAction)
        modes.addView(rectangleAction, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        modes.addView(perspectiveAction, LinearLayout.LayoutParams(0, dp(44), 1f))
        setPhotoMode(view, PhotoEditMode.RECTANGLE)
        root.addView(header, matchWrap())
        root.addView(photoStatusView, matchWrap())
        root.addView(modes, matchWrap())
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun commitPhoto(bitmap: Bitmap, view: PhotoEditorView) {
        if (photoSavePending) return
        val capture = captureFile
        val note = currentNote
        if (capture == null || note == null) {
            photoStatusView?.text = "照片已不可用，请返回笔记后重新拍摄"
            return
        }
        photoSavePending = true
        photoInsertAction?.isEnabled = false
        photoModeActions.forEach { it.isEnabled = false }
        photoStatusView?.text = "正在写入原图、校正图和笔记…"
        val corrected = try {
            view.outputJpeg()
        } catch (_: Exception) {
            showPhotoSaveFailure("无法处理照片，请调整后重试")
            return
        }
        noteIoExecutor.execute {
            val attachments = try {
                FileInputStream(capture).use { repository.savePhotoPair(note.name, it, corrected) }
            } catch (_: Exception) {
                null
            }
            if (attachments == null) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showPhotoSaveFailure("照片尚未插入，请检查 Vault 权限或存储空间后重试")
                }
                return@execute
            }
            val relativePath = repository.relativeAttachmentPath(note, attachments)
            val imageLink = "![${attachments.corrected}]($relativePath)"
            val content = insertPhotoAtCapturePoint(
                photoContextContent ?: repository.readText(note).orEmpty(),
                imageLink
            )
            val success = repository.saveText(note, content)
            if (success) {
                val refreshed = repository.refreshDocument(note) ?: note
                repository.confirmPhotoPair(attachments)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    currentNote = refreshed
                    pendingEditorScrollY = photoContextScrollY
                    pendingCaretImagePath = relativePath
                    photoContextContent = null
                    if (!bitmap.isRecycled) bitmap.recycle()
                    discardCaptureFile()
                    editorView = null
                    photoStatusView = null
                    photoInsertAction = null
                    photoModeActions = emptyList()
                    photoSavePending = false
                    showEditor(refreshed, content)
                    statusView?.text = "照片已插入并保存"
                }
            } else {
                repository.rollbackPhotoPair(attachments)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showPhotoSaveFailure("无法更新笔记，照片尚未插入；可重试或返回笔记")
                }
            }
        }
    }

    private fun restoreEditorScreen() {
        if (photoSavePending) return
        discardCaptureFile()
        editorView = null
        pendingEditorScrollY = photoContextScrollY
        val restoredContent = photoContextContent?.replace(MarkdownCodec.CARET_MARKER, "")
        photoContextContent = null
        photoStatusView = null
        photoInsertAction = null
        photoModeActions = emptyList()
        val note = currentNote
        if (note == null) {
            showVaultBrowser()
        } else if (restoredContent != null) {
            showEditor(note, restoredContent)
        } else {
            openNote(note)
        }
    }

    private fun setPhotoMode(view: PhotoEditorView, mode: PhotoEditMode) {
        if (photoSavePending) return
        view.mode = mode
        photoStatusView?.text = if (mode == PhotoEditMode.RECTANGLE) {
            "矩形裁剪：拖动任一角点调整范围"
        } else {
            "四点校正：分别拖动四个角点拉直画面"
        }
        photoModeActions.forEachIndexed { index, action ->
            val selected = (index == 0 && mode == PhotoEditMode.RECTANGLE) ||
                (index == 1 && mode == PhotoEditMode.PERSPECTIVE)
            action.isSelected = selected
            action.contentDescription = if (selected) "${action.text}，已选中" else action.text
            action.setTextColor(if (selected) COLOR_ON_ACCENT else COLOR_PRIMARY_TEXT)
            action.background = rounded(if (selected) COLOR_ACCENT else COLOR_ROW, dp(12))
        }
    }

    private fun showPhotoSaveFailure(message: String) {
        photoSavePending = false
        photoInsertAction?.isEnabled = true
        photoModeActions.forEach { it.isEnabled = true }
        photoStatusView?.text = message
    }

    private fun insertPhotoAtCapturePoint(base: String, imageLink: String): String {
        val insertion = "\n\n$imageLink\n\n"
        return if (base.contains(MarkdownCodec.CARET_MARKER)) {
            base.replace(MarkdownCodec.CARET_MARKER, insertion).trimEnd() + "\n"
        } else {
            base.trimEnd() + insertion
        }
    }

    private fun attachmentClient(): WebViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val scrollY = pendingEditorScrollY
            val imagePath = pendingCaretImagePath
            pendingEditorScrollY = null
            pendingCaretImagePath = null
            view?.post {
                if (scrollY != null) view.scrollTo(0, scrollY)
                if (imagePath != null) {
                    view.evaluateJavascript(
                        "window.markbook && window.markbook.focusAfterImage(${org.json.JSONObject.quote(imagePath)})",
                        null
                    )
                }
            }
        }

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
            handler.post {
                if (screen != Screen.EDITOR) return@post
                saveCoordinator.markEdited()
                updateSaveStatus()
                handler.removeCallbacks(autosave)
                handler.postDelayed(autosave, AUTOSAVE_DELAY_MS)
            }
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

    private fun sectionLabel(container: LinearLayout, label: String, count: Int? = null) {
        container.addView(TextView(this).apply {
            text = count?.let { "$label  $it" } ?: label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(dp(4), dp(14), dp(4), dp(8))
        }, matchWrap())
    }

    private fun vaultAccessErrorState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(16))
        background = rounded(COLOR_ROW, dp(14))
        addView(TextView(this@MainActivity).apply {
            text = "无法读取当前 Vault"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_TEXT)
        }, matchWrap())
        addView(TextView(this@MainActivity).apply {
            text = "文件没有被移动。请重新授予目录访问权限后再继续。"
            textSize = 13f
            setTextColor(COLOR_MUTED_TEXT)
            setPadding(0, dp(6), 0, dp(14))
        }, matchWrap())
        addView(action("重新选择 Vault", true) { chooseVault() }, wrapWrap())
    }

    private fun editorContext(note: VaultDocument): String {
        val location = note.parentRelativePath.ifBlank { "Vault 根目录" }
        return "$currentVaultName · $location · ${note.name}"
    }

    private fun vaultRow(
        icon: String,
        title: String,
        subtitle: String,
        trailingLabel: String = "",
        trailingAction: (() -> Unit)? = null,
        action: () -> Unit
    ): View = LinearLayout(this).apply {
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
        if (trailingAction == null) {
            addView(TextView(this@MainActivity).apply {
                text = "›"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(COLOR_MUTED_TEXT)
            }, LinearLayout.LayoutParams(dp(28), dp(44)))
        } else {
            addView(action(trailingLabel, false, trailingAction), LinearLayout.LayoutParams(dp(56), dp(44)))
        }
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

    private fun settingsStatusRow(title: String, subtitle: String, status: String, active: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(68)
        setPadding(dp(16), dp(10), dp(12), dp(10))
        background = rounded(COLOR_ROW, dp(14))
        contentDescription = "$title，$subtitle，$status"
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
            text = status
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(if (active) COLOR_ACCENT else COLOR_MUTED_TEXT)
        }, LinearLayout.LayoutParams(dp(92), dp(44)))
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
        val leaf = document.relativePath.substringAfterLast('/')
        return VaultPathPolicy.isProtected(document.relativePath) || leaf.startsWith(".markbook-")
    }

    private fun notePreview(note: VaultDocument): String {
        val value = repository.readPreview(note, NOTE_PREVIEW_BYTES).orEmpty()
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

    private fun simpleToolbar(backLabel: String, title: String, back: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(8))
        background = colorBlock(COLOR_SURFACE)
        addView(action(backLabel, false, back))
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(COLOR_PRIMARY_TEXT)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        addView(TextView(this@MainActivity), LinearLayout.LayoutParams(dp(96), dp(44)))
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun colorBlock(color: Int): GradientDrawable = GradientDrawable().apply { setColor(color) }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)

    private fun wrapWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-2, -2)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatSyncTime(time: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(time))

    private fun isNightTheme(): Boolean = repository.appearanceMode() == VaultRepository.APPEARANCE_NIGHT

    private fun applyWindowColors() {
        window.statusBarColor = COLOR_SURFACE
        window.navigationBarColor = COLOR_SURFACE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = if (isNightTheme()) {
                window.decorView.systemUiVisibility and lightFlags.inv()
            } else {
                window.decorView.systemUiVisibility or lightFlags
            }
        }
    }

    private val COLOR_BACKGROUND: Int
        get() = if (isNightTheme()) Color.rgb(18, 28, 24) else Color.rgb(247, 247, 242)
    private val COLOR_SURFACE: Int
        get() = if (isNightTheme()) Color.rgb(27, 38, 33) else Color.WHITE
    private val COLOR_ROW: Int
        get() = if (isNightTheme()) Color.rgb(40, 54, 47) else Color.rgb(237, 241, 237)
    private val COLOR_EDITOR_BACKGROUND: Int
        get() = if (isNightTheme()) Color.rgb(24, 35, 30) else Color.rgb(247, 247, 242)
    private val COLOR_ACCENT: Int
        get() = if (isNightTheme()) Color.rgb(141, 207, 168) else Color.rgb(47, 107, 79)
    private val COLOR_PRIMARY_TEXT: Int
        get() = if (isNightTheme()) Color.rgb(239, 246, 241) else Color.rgb(24, 32, 28)
    private val COLOR_SECONDARY_TEXT: Int
        get() = if (isNightTheme()) Color.rgb(195, 211, 201) else Color.rgb(70, 81, 75)
    private val COLOR_MUTED_TEXT: Int
        get() = if (isNightTheme()) Color.rgb(158, 178, 166) else Color.rgb(102, 113, 107)
    private val COLOR_ON_ACCENT: Int
        get() = Color.WHITE

    private enum class Screen {
        WELCOME, BROWSER, EDITOR_LOADING, EDITOR_ERROR, EDITOR, PHOTO, SETTINGS, DAILY_FOLDER_PICKER,
        DRIVE_SETUP, DRIVE_FOLDER_PICKER, DRIVE_CONFIRM, DRIVE_PROGRESS, DRIVE_RESULT
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val VAULT_REQUEST = 1002
        private const val CAMERA_REQUEST = 1003
        private const val DRIVE_SIGN_IN_REQUEST = 1004
        private const val FILE_PROVIDER_AUTHORITY = "com.markbook.android.fileprovider"
        private const val MAX_PREVIEW_SIDE = 2560
        private const val NOTE_PREVIEW_BYTES = 4096
        private const val AUTOSAVE_DELAY_MS = 600L
    }
}
