package com.markbook.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCapturePolicyTest {
    @Test
    fun acceptsMp4VideoAtMvpLimits() {
        val result = VideoCapturePolicy.validate(
            VideoCapturePolicy.Metadata(1024, 179_000, "video/mp4", VideoCapturePolicy.Container.MP4, true)
        )
        assertEquals(VideoCapturePolicy.Validation.Accepted("mp4"), result)
    }

    @Test
    fun rejectsTooLongTooLargeAndAudioOnlyMetadata() {
        assertTrue(VideoCapturePolicy.validate(
            VideoCapturePolicy.Metadata(1, 180_001, "video/mp4", VideoCapturePolicy.Container.MP4, true)
        ) is VideoCapturePolicy.Validation.Rejected)
        assertTrue(VideoCapturePolicy.validate(
            VideoCapturePolicy.Metadata(VideoCapturePolicy.MAX_SIZE_BYTES + 1, 1_000, "video/mp4", VideoCapturePolicy.Container.MP4, true)
        ) is VideoCapturePolicy.Validation.Rejected)
        assertTrue(VideoCapturePolicy.validate(
            VideoCapturePolicy.Metadata(1, 1_000, "audio/mp4", VideoCapturePolicy.Container.MP4, false)
        ) is VideoCapturePolicy.Validation.Rejected)
    }

    @Test
    fun linkEscapesRelativePathWithAngleBrackets() {
        assertEquals(
            "[视频 00:01:05](<../assets/会议 记录/120000-ab12-v.mp4>)",
            VideoCapturePolicy.videoLink("../assets/会议 记录/120000-ab12-v.mp4", 65_000)
        )
    }

    @Test
    fun photoAndVideoNamesReserveTheSameCaptureId() {
        assertTrue(VideoCapturePolicy.captureIdCollides(listOf("120000-ab12-o.jpg"), "120000-ab12"))
        assertTrue(VideoCapturePolicy.captureIdCollides(listOf("120000-ab12-v.3gp"), "120000-ab12"))
    }

    @Test
    fun mp4MimeAndFileNameCannotBlessAnUnknownContainer() {
        assertTrue(VideoCapturePolicy.validate(
            VideoCapturePolicy.Metadata(1024, 1_000, "video/mp4", VideoCapturePolicy.Container.UNKNOWN, true)
        ) is VideoCapturePolicy.Validation.Rejected)
    }

    @Test
    fun sniffsOnlyKnownMp4And3gpFtypBrands() {
        assertEquals(VideoCapturePolicy.Container.MP4, VideoCapturePolicy.sniffContainer(ftyp("isom")))
        assertEquals(VideoCapturePolicy.Container.THREE_GP, VideoCapturePolicy.sniffContainer(ftyp("3gp4")))
        assertEquals(VideoCapturePolicy.Container.UNKNOWN, VideoCapturePolicy.sniffContainer(ftyp("fake")))
    }

    @Test
    fun ftypParserRespectsDeclaredBoxLengthAndRejectsMalformedLengths() {
        val nextBoxBrand = ftyp("fake", boxSize = 16, totalBytes = 24).also { bytes ->
            "isom".toByteArray().copyInto(bytes, destinationOffset = 16)
        }
        assertEquals(VideoCapturePolicy.Container.UNKNOWN, VideoCapturePolicy.sniffContainer(nextBoxBrand))
        assertEquals(
            VideoCapturePolicy.Container.UNKNOWN,
            VideoCapturePolicy.sniffContainer(ftyp("isom", boxSize = 12))
        )
    }

    private fun ftyp(brand: String, boxSize: Int = 16, totalBytes: Int = 20): ByteArray = ByteArray(totalBytes).also { bytes ->
        bytes[0] = (boxSize ushr 24).toByte()
        bytes[1] = (boxSize ushr 16).toByte()
        bytes[2] = (boxSize ushr 8).toByte()
        bytes[3] = boxSize.toByte()
        "ftyp".toByteArray().copyInto(bytes, destinationOffset = 4)
        brand.toByteArray().copyInto(bytes, destinationOffset = 8)
    }
}
