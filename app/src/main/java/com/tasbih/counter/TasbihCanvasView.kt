package com.tasbih.counter

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class TasbihCanvasView(context: Context) : View(context) {

    companion object {
        const val BEADS_PER_CYCLE = 33
    }

    var count: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val activeBeads = listOf(
        intArrayOf(0xFF9DF7E6.toInt(), 0xFF1EB7A5.toInt(), 0xFF126D69.toInt()),
        intArrayOf(0xFFC0E2FF.toInt(), 0xFF498EFF.toInt(), 0xFF234A96.toInt()),
        intArrayOf(0xFFE3C9FF.toInt(), 0xFF9D72F4.toInt(), 0xFF553B99.toInt())
    )
    private val levelNames = listOf("التركوازي", "الأزرق", "البنفسجي")
    private lateinit var inactiveBeadShader: Shader
    private var activeBeadShaders = emptyList<Shader>()
    private var glowShaders = emptyList<Shader>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        val radius = min(w, h) * 0.35f
        bgPaint.shader = RadialGradient(
            w * 0.46f, h * 0.53f, radius * 2.6f,
            intArrayOf(0xFF28264F.toInt(), 0xFF141B30.toInt(), 0xFF090D16.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        centerCirclePaint.shader = RadialGradient(
            w / 2f, h * 0.54f, radius * 0.65f,
            intArrayOf(0xFF182033.toInt(), 0xFF111A2B.toInt(), 0xFF384D76.toInt()),
            floatArrayOf(0f, 0.74f, 1f), Shader.TileMode.CLAMP
        )
        val beadRadius = radius * 0.07f
        inactiveBeadShader = beadShader(
            beadRadius, intArrayOf(0xFFF0F6FC.toInt(), 0xFFAAB7C8.toInt(), 0xFF65768D.toInt())
        )
        activeBeadShaders = activeBeads.map { beadShader(beadRadius, it) }
        glowShaders = activeBeads.map { colors ->
            RadialGradient(
                0f, 0f, beadRadius * 1.65f,
                intArrayOf(colors[0], colors[1] and 0x00FFFFFF),
                floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP
            )
        }
    }

    private fun beadShader(radius: Float, colors: IntArray) = RadialGradient(
        -radius * 0.28f, -radius * 0.32f, radius * 1.35f,
        colors, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val cx = width / 2f
        val cy = height * 0.54f
        val baseRadius = min(width, height) * 0.35f
        val centerRadius = baseRadius * 0.65f
        val beadRadius = baseRadius * 0.07f

        canvas.drawCircle(cx, cy, centerRadius, centerCirclePaint)

        val beadIndex = (count - 1).coerceAtLeast(0)
        val level = beadIndex / BEADS_PER_CYCLE % activeBeads.size
        val coloredBeads = if (count == 0) 0 else beadIndex % BEADS_PER_CYCLE + 1
        glowPaint.shader = glowShaders[level]

        for (i in 0 until BEADS_PER_CYCLE) {
            val angle = Math.toRadians((i * (360.0 / BEADS_PER_CYCLE)) - 90.0)
            val bx = cx + (baseRadius * cos(angle)).toFloat()
            val by = cy + (baseRadius * sin(angle)).toFloat()

            beadPaint.shader = if (i < coloredBeads) {
                activeBeadShaders[level]
            } else {
                inactiveBeadShader
            }
            canvas.save()
            canvas.translate(bx, by)
            if (i < coloredBeads) canvas.drawCircle(0f, 0f, beadRadius * 1.65f, glowPaint)
            canvas.drawCircle(0f, 0f, beadRadius, beadPaint)
            canvas.restore()
        }

        labelPaint.color = 0xFF85C2C9.toInt()
        labelPaint.textSize = centerRadius * 0.13f
        canvas.drawText("العدد الحالي", cx, cy - centerRadius * 0.40f, labelPaint)

        textPaint.textSize = centerRadius * 0.46f
        val textY = cy - centerRadius * 0.12f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(count.toString(), cx, textY, textPaint)

        val label = "المستوى ${levelNames[level]}"
        labelPaint.color = activeBeads[level][0]
        val badgeHalfWidth = labelPaint.measureText(label) / 2f + centerRadius * 0.1f
        val badgeY = cy + centerRadius * 0.40f
        val badgeRadius = centerRadius * 0.12f
        badgePaint.color = activeBeads[level][2]
        canvas.drawRoundRect(
            cx - badgeHalfWidth, badgeY - badgeRadius, cx + badgeHalfWidth, badgeY + badgeRadius,
            badgeRadius, badgeRadius, badgePaint
        )
        canvas.drawText(label, cx, badgeY - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
    }
}
