package com.example.codbenchmarker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#00FF41") // Tactical Green
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        textSize = 42f
        typeface = Typeface.MONOSPACE
        style = Paint.Style.FILL
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }

    private var boxes: List<RectF> = emptyList()

    fun setResults(newBoxes: List<RectF>) {
        boxes = newBoxes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // COORDINATE SCALING: 640 AI Pixels -> Physical Screen Pixels
        val scaleX = width.toFloat() / 640f
        val scaleY = height.toFloat() / 640f

        for (box in boxes) {
            val mappedBox = RectF(
                box.left * scaleX,
                box.top * scaleY,
                box.right * scaleX,
                box.bottom * scaleY
            )

            canvas.drawRect(mappedBox, boxPaint)
            canvas.drawText("CAM_TARGET", mappedBox.left, mappedBox.top - 15f, textPaint)
        }
    }
}