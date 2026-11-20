package com.tasbih.counter

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class TasbihCanvasView(context: Context) : View(context) {

    var count: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val totalBeads = 33
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#090D16") }
    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#131B2E") }
    private val ringStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#1E293B")
    }
    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val grayColor = Color.parseColor("#CBD5E1")
    private val activeColors = intArrayOf(
        Color.parseColor("#06B6D4"),
        Color.parseColor("#3B82F6"),
        Color.parseColor("#8B5CF6")
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) * 0.34f
        val centerRadius = baseRadius * 0.62f
        val beadRadius = baseRadius * 0.085f

        canvas.drawCircle(cx, cy, centerRadius, centerCirclePaint)
        canvas.drawCircle(cx, cy, centerRadius, ringStrokePaint)

        val coloredBeads = if (count == 0) 0 else {
            val mod = count % totalBeads
            if (mod == 0) totalBeads else mod
        }

        for (i in 0 until totalBeads) {
            val angle = Math.toRadians((i * (360.0 / totalBeads)) - 90.0)
            val bx = cx + (baseRadius * cos(angle)).toFloat()
            val by = cy + (baseRadius * sin(angle)).toFloat()

            beadPaint.color = if (i < coloredBeads) {
                activeColors[i % activeColors.size]
            } else {
                grayColor
            }
            canvas.drawCircle(bx, by, beadRadius, beadPaint)
        }

        textPaint.textSize = centerRadius * 0.55f
        val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(count.toString(), cx, textY, textPaint)
    }
}
