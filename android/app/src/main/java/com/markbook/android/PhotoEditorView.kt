package com.markbook.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PhotoEditMode { RECTANGLE, PERSPECTIVE }

class PhotoEditorView(context: android.content.Context, private val bitmap: Bitmap) : View(context) {
    var mode: PhotoEditMode = PhotoEditMode.RECTANGLE
        set(value) {
            field = value
            invalidate()
        }

    private val handles = arrayOf(
        PointF(0f, 0f),
        PointF(bitmap.width.toFloat(), 0f),
        PointF(bitmap.width.toFloat(), bitmap.height.toFloat()),
        PointF(0f, bitmap.height.toFloat())
    )
    private val imageRect = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(21, 101, 192) }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private var activeHandle = -1

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val left = (width - bitmap.width * scale) / 2f
        val top = (height - bitmap.height * scale) / 2f
        imageRect.set(left, top, left + bitmap.width * scale, top + bitmap.height * scale)
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint)

        val path = Path()
        path.moveTo(viewX(handles[0].x), viewY(handles[0].y))
        for (i in 1..3) path.lineTo(viewX(handles[i].x), viewY(handles[i].y))
        path.close()
        canvas.drawPath(path, linePaint)
        handles.forEach { point ->
            canvas.drawCircle(viewX(point.x), viewY(point.y), 18f, handlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = nearestHandle(event.x, event.y)
                return activeHandle >= 0
            }
            MotionEvent.ACTION_MOVE -> if (activeHandle >= 0) {
                updateHandle(activeHandle, imageX(event.x), imageY(event.y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = -1
                return true
            }
        }
        return true
    }

    fun outputJpeg(quality: Int = 92): ByteArray {
        val output = if (mode == PhotoEditMode.RECTANGLE) cropBitmap() else perspectiveBitmap()
        return java.io.ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            if (output !== bitmap) output.recycle()
            stream.toByteArray()
        }
    }

    private fun cropBitmap(): Bitmap {
        val left = handles.minOf { it.x }.roundToInt().coerceIn(0, bitmap.width - 1)
        val top = handles.minOf { it.y }.roundToInt().coerceIn(0, bitmap.height - 1)
        val right = handles.maxOf { it.x }.roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = handles.maxOf { it.y }.roundToInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun perspectiveBitmap(): Bitmap {
        val topWidth = distance(handles[0], handles[1])
        val bottomWidth = distance(handles[3], handles[2])
        val leftHeight = distance(handles[0], handles[3])
        val rightHeight = distance(handles[1], handles[2])
        val outWidth = max(1, ((topWidth + bottomWidth) / 2f).roundToInt())
        val outHeight = max(1, ((leftHeight + rightHeight) / 2f).roundToInt())
        val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val matrix = Matrix()
        val source = floatArrayOf(
            handles[0].x, handles[0].y,
            handles[1].x, handles[1].y,
            handles[2].x, handles[2].y,
            handles[3].x, handles[3].y
        )
        val destination = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat()
        )
        matrix.setPolyToPoly(source, 0, destination, 0, 4)
        Canvas(result).drawBitmap(bitmap, matrix, imagePaint)
        return result
    }

    private fun updateHandle(index: Int, x: Float, y: Float) {
        val clampedX = x.coerceIn(0f, bitmap.width.toFloat())
        val clampedY = y.coerceIn(0f, bitmap.height.toFloat())
        if (mode == PhotoEditMode.PERSPECTIVE) {
            handles[index].set(clampedX, clampedY)
            return
        }
        handles[index].set(clampedX, clampedY)
        when (index) {
            0 -> { handles[1].y = clampedY; handles[3].x = clampedX }
            1 -> { handles[0].y = clampedY; handles[2].x = clampedX }
            2 -> { handles[1].x = clampedX; handles[3].y = clampedY }
            3 -> { handles[0].x = clampedX; handles[2].y = clampedY }
        }
    }

    private fun nearestHandle(x: Float, y: Float): Int {
        var nearest = -1
        var distance = Float.MAX_VALUE
        handles.forEachIndexed { index, point ->
            val candidate = hypot(viewX(point.x) - x, viewY(point.y) - y)
            if (candidate < distance && candidate <= 60f) {
                distance = candidate
                nearest = index
            }
        }
        return nearest
    }

    private fun viewX(imageX: Float): Float = imageRect.left + imageX / bitmap.width * imageRect.width()
    private fun viewY(imageY: Float): Float = imageRect.top + imageY / bitmap.height * imageRect.height()
    private fun imageX(viewX: Float): Float = ((viewX - imageRect.left) / imageRect.width() * bitmap.width)
    private fun imageY(viewY: Float): Float = ((viewY - imageRect.top) / imageRect.height() * bitmap.height)
    private fun distance(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)
}
