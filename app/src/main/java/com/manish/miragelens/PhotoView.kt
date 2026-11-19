package com.manish.miragelens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** A box from Mirage, in the 0..1000 coordinate scale it returns. */
data class OverlayBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: Int,
    val label: String
)

/**
 * Draws a photo scaled to fit, with Mirage boxes on top.
 * Boxes are mapped through the same scale/offset as the photo,
 * so they always line up with what they mark.
 */
class PhotoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var boxes: List<OverlayBox> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW == 0f || viewH == 0f) return

        val scale = minOf(viewW / bmp.width, viewH / bmp.height)
        val drawnW = bmp.width * scale
        val drawnH = bmp.height * scale
        val dx = (viewW - drawnW) / 2f
        val dy = (viewH - drawnH) / 2f

        canvas.drawBitmap(bmp, dx, dy, null)

        for (box in boxes) {
            val l = dx + box.left / 1000f * drawnW
            val t = dy + box.top / 1000f * drawnH
            val r = dx + box.right / 1000f * drawnW
            val b = dy + box.bottom / 1000f * drawnH
            boxPaint.color = box.color
            canvas.drawRect(l, t, r, b, boxPaint)
            if (box.label.isNotEmpty()) {
                val labelW = textPaint.measureText(box.label) + 12f
                fillPaint.color = box.color
                val labelTop = (t - 40f).coerceAtLeast(0f)
                canvas.drawRect(l, labelTop, l + labelW, labelTop + 40f, fillPaint)
                canvas.drawText(box.label, l + 6f, labelTop + 30f, textPaint)
            }
        }
    }
}
