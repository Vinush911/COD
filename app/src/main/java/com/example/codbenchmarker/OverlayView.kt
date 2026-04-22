package com.example.codbenchmarker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var results: List<Detection> = emptyList()

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 50f
        style = Paint.Style.FILL
    }

    fun setResults(newResults: List<Detection>) {
        results = newResults
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (det in results) {
            val rect = RectF(
                det.box.left * width,
                det.box.top * height,
                det.box.right * width,
                det.box.bottom * height
            )

            canvas.drawRect(rect, paint)
            canvas.drawText(det.label, rect.left, rect.top - 10, textPaint)
        }
    }
}