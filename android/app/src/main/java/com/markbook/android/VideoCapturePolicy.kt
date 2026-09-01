package com.markbook.android

import java.security.MessageDigest
import java.util.Locale

/** Pure limits and metadata decisions for system-camera video capture. */
object VideoCapturePolicy {
    const val MAX_DURATION_MS = 180_000L
    const val MAX_SIZE_BYTES = 200L * 1024L * 1024L

    enum class Container { MP4, THREE_GP, UNKNOWN }

    data class Metadata(
        val sizeBytes: Long,
        val durationMs: Long,
        val mimeType: String?,
        val container: Container,
        val hasVideoTrack: Boolean
    )

    sealed class Validation {
        data class Accepted(val extension: String) : Validation()
        data class Rejected(val message: String) : Validation()
    }

    fun validate(metadata: Metadata): Validation = when {
        metadata.sizeBytes <= 0L -> Validation.Rejected("视频文件为空")
        metadata.sizeBytes > MAX_SIZE_BYTES -> Validation.Rejected("视频超过 200 MiB 限制")
        metadata.durationMs <= 0L -> Validation.Rejected("无法读取视频时长")
        metadata.durationMs > MAX_DURATION_MS -> Validation.Rejected("视频超过 180 秒限制")
        !metadata.hasVideoTrack -> Validation.Rejected("文件不包含视频轨")
        extensionFor(metadata.container) == null ->
            Validation.Rejected("仅支持 MP4 或 3GP 视频")
        metadata.mimeType != null && !metadata.mimeType.startsWith("video/", ignoreCase = true) ->
            Validation.Rejected("返回内容不是视频 MIME 类型")
        else -> Validation.Accepted(extensionFor(metadata.container)!!)
    }

    fun extensionFor(container: Container): String? = when (container) {
        Container.MP4 -> "mp4"
        Container.THREE_GP -> "3gp"
        Container.UNKNOWN -> null
    }

    /** Identifies an ISO base-media `ftyp` brand from bytes, never from an output filename. */
    fun sniffContainer(header: ByteArray): Container {
        if (header.size < FTYPE_MIN_BYTES || brandAt(header, 4) != "ftyp") return Container.UNKNOWN
        val boxSize = unsignedIntAt(header, 0)
        if (boxSize < FTYPE_MIN_BYTES || boxSize > Int.MAX_VALUE || boxSize % 4L != 0L) return Container.UNKNOWN
        // The supplied prefix can end inside a valid ftyp box; never inspect bytes past its declared end.
        val brandEnd = minOf(header.size, boxSize.toInt())
        val brands = buildList {
            add(brandAt(header, 8))
            var index = 16
            while (index + 4 <= brandEnd) {
                add(brandAt(header, index))
                index += 4
            }
        }
        val normalized = brands.map { it.lowercase(Locale.ROOT) }
        return when {
            normalized.any { it.startsWith("3gp") } -> Container.THREE_GP
            normalized.any { it in MP4_BRANDS } -> Container.MP4
            else -> Container.UNKNOWN
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        return "%02d:%02d:%02d".format(Locale.ROOT, totalSeconds / 3600L, (totalSeconds / 60L) % 60L, totalSeconds % 60L)
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    fun videoLink(relativePath: String, durationMs: Long): String =
        "[视频 ${formatDuration(durationMs)}](<$relativePath>)"

    /** A photo and a video reserve the same capture ID, regardless of their extension. */
    fun captureIdCollides(existingNames: Collection<String>, id: String): Boolean = existingNames.any { name ->
        name.startsWith("$id-o.") || name.startsWith("$id-c.") || name.startsWith("$id-v.")
    }

    private val MP4_BRANDS = setOf(
        "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
        "mp41", "mp42", "avc1", "av01", "m4v ", "f4v ", "msnv", "dash", "cmfc", "cmfs", "cmaf"
    )
    private const val FTYPE_MIN_BYTES = 16

    private fun brandAt(bytes: ByteArray, offset: Int): String = String(bytes, offset, 4, Charsets.ISO_8859_1)

    private fun unsignedIntAt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
}

/** The persisted external-activity pointer. Markdown content is deliberately never stored here. */
data class PendingVideoCaptureSession(
    val noteUri: String,
    val noteRelativePath: String,
    val contentSha256: String,
    val caretOffset: Int,
    val scrollY: Int,
    val cachePath: String,
    val stage: String,
    /** Vault-root attachment path; used only to complete cleanup after a saved note link. */
    val attachmentVaultPath: String? = null
)
