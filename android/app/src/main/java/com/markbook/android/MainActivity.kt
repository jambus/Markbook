package com.markbook.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
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
    private var captureFile: File? = null
    private var captureUri: Uri? = null
    private var editorView: PhotoEditorView? = null
    private var savePending = false
    private var queuedExtra: String? = null
    private var queuedCompletion: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VaultRepository(this)
        buildMainScreen()
        if (repository.savedVaultUri() != null) loadDailyNote()
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) saveCurrentNote()
    }

    private fun buildMainScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 4)
        }
        toolbar.addView(button("Vault") { chooseVault() })
        toolbar.addView(button("保存") { saveCurrentNote() })
        toolbar.addView(button("拍照") { startCamera() })
        statusView = TextView(this).apply {
            text = "请选择一个 Obsidian Vault"
            setTextColor(Color.DKGRAY)
            setPadding(10, 4, 10, 8)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2))

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = attachmentClient()
            addJavascriptInterface(EditorBridge(), "Android")
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        webView.loadDataWithBaseURL(null, MarkdownCodec.toHtml("", repository), "text/html", "UTF-8", null)
    }

    private fun chooseVault() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, VAULT_REQUEST)
    }

    private fun loadDailyNote() {
        val note = repository.dailyNote()
        if (note == null) {
            statusView.text = "无法访问 Vault，请重新选择"
            return
        }
        currentNote = note
        val content = repository.readText(note) ?: "# ${note.name.removeSuffix(".md")}\n\n"
        statusView.text = "Vault 已连接 · ${note.name}"
        webView.loadDataWithBaseURL(null, MarkdownCodec.toHtml(content, repository), "text/html", "UTF-8", null)
    }

    private fun saveCurrentNote(extra: String? = null, onComplete: (Boolean) -> Unit = {}) {
        if (savePending) {
            if (extra != null) {
                queuedExtra = listOfNotNull(queuedExtra, extra).joinToString("\n")
                queuedCompletion = onComplete
            }
            return
        }
        if (currentNote == null) {
            onComplete(false)
            return
        }
        savePending = true
        webView.evaluateJavascript("window.markbook && window.markbook.serialize ? window.markbook.serialize() : ''") { value ->
            savePending = false
            val base = decodeJavascriptString(value)
            val content = if (extra == null) base else base.trimEnd() + "\n\n" + extra + "\n"
            val success = currentNote?.let { repository.saveText(it, content) } == true
            if (success) {
                repository.dailyNote()?.let { currentNote = it }
            }
            statusView.text = if (success) "已保存 · ${currentNote?.name}" else "保存失败，请检查 Vault 权限"
            if (success && extra != null) {
                webView.loadDataWithBaseURL(null, MarkdownCodec.toHtml(content, repository), "text/html", "UTF-8", null)
            }
            val nextExtra = queuedExtra
            val nextCompletion = queuedCompletion
            queuedExtra = null
            queuedCompletion = null
            if (nextExtra != null) {
                if (success) {
                    saveCurrentNote(nextExtra, nextCompletion ?: {})
                } else {
                    nextCompletion?.invoke(false)
                }
            } else {
                onComplete(success)
            }
        }
    }

    private fun startCamera() {
        if (currentNote == null) {
            toast("请先选择 Vault")
            return
        }
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
                loadDailyNote()
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
        while (longestSide / sampleSize > MAX_PREVIEW_SIDE) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            toast("需要相机权限才能拍照")
        }
    }

    private fun showPhotoEditor(bitmap: Bitmap) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(6, 8, 6, 8)
        }
        val view = PhotoEditorView(this, bitmap)
        editorView = view
        controls.addView(button("矩形") { view.mode = PhotoEditMode.RECTANGLE })
        controls.addView(button("四点校正") { view.mode = PhotoEditMode.PERSPECTIVE })
        controls.addView(button("取消") { restoreMainScreen(bitmap) })
        controls.addView(button("插入") { commitPhoto(bitmap, view) })
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun commitPhoto(bitmap: Bitmap, view: PhotoEditorView) {
        val input = captureFile?.let { FileInputStream(it) }
        if (input == null) {
            restoreMainScreen(bitmap)
            return
        }
        val corrected = view.outputJpeg()
        val attachments = input.use { repository.savePhotoPair(it, corrected) }
        if (attachments == null) {
            toast("照片保存失败，请检查 Vault 空间或权限")
            restoreMainScreen(bitmap)
            return
        }
        saveCurrentNote("![${attachments.corrected}](../attachments/${attachments.corrected})") { success ->
            if (success) {
                repository.confirmPhotoPair(attachments)
            } else {
                repository.rollbackPhotoPair(attachments)
            }
            restoreMainScreen(bitmap)
        }
    }

    private fun restoreMainScreen(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
        discardCaptureFile()
        editorView = null
        buildMainScreen()
        if (currentNote != null) loadDailyNote()
    }

    private fun attachmentClient(): WebViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): WebResourceResponse? {
            val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
            if (uri.scheme != "markbook" || uri.host != "attachment") {
                return super.shouldInterceptRequest(view, request)
            }
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
            handler.removeCallbacksAndMessages(AUTOSAVE_TOKEN)
            handler.postAtTime({ saveCurrentNote() }, AUTOSAVE_TOKEN, System.currentTimeMillis() + 600)
        }
    }

    private fun decodeJavascriptString(value: String): String {
        return try {
            org.json.JSONTokener(value).nextValue() as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
        isAllCaps = false
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun discardCaptureFile() {
        captureFile?.delete()
        captureFile = null
        captureUri = null
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val VAULT_REQUEST = 1002
        private const val CAMERA_REQUEST = 1003
        private const val FILE_PROVIDER_AUTHORITY = "com.markbook.android.fileprovider"
        private const val MAX_PREVIEW_SIDE = 4096
        private val AUTOSAVE_TOKEN = Any()
    }
}
