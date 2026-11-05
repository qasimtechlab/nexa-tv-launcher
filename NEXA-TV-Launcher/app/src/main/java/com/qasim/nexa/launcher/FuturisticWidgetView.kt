package com.qasim.nexa.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight, offline animated artwork for the NEXA home screen.
 * It uses Canvas primitives only, so no large image or animation assets are required.
 */
internal class FuturisticWidgetView(
    context: Context,
    private val style: String
) : View(context) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(100, 224, 255)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.4f
    }
    private val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8861DFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3.2f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotation = 0f

    fun advanceFrame() {
        rotation = (rotation + 1.35f) % 360f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) * 0.34f).coerceAtLeast(1f)
        if (style == "reactor") {
            drawReactor(canvas, cx, cy, radius)
        } else {
            drawGlobe(canvas, cx, cy, radius)
        }
    }

    private fun drawGlobe(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        fillPaint.shader = RadialGradient(
            cx - radius * 0.32f,
            cy - radius * 0.38f,
            radius * 1.55f,
            intArrayOf(0xFF8BF3FF.toInt(), 0xFF167AB5.toInt(), 0xFF071C47.toInt()),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null

        softPaint.style = Paint.Style.STROKE
        canvas.drawCircle(cx, cy, radius + resources.displayMetrics.density * 4f, softPaint)
        linePaint.style = Paint.Style.STROKE

        val save = canvas.save()
        canvas.rotate(rotation, cx, cy)
        for (index in -2..2) {
            val angle = index * 31f + rotation * 0.18f
            val widthFactor = abs(cos(Math.toRadians(angle.toDouble())).toFloat()).coerceAtLeast(0.08f)
            val ovalWidth = radius * widthFactor
            linePaint.alpha = if (index == 0) 220 else 125
            canvas.drawOval(
                RectF(cx - ovalWidth, cy - radius * 0.96f, cx + ovalWidth, cy + radius * 0.96f),
                linePaint
            )
        }
        linePaint.alpha = 125
        for (factor in listOf(-0.52f, 0f, 0.52f)) {
            val y = cy + radius * factor
            val latitudeHeight = radius * (0.16f + (1f - abs(factor)) * 0.05f)
            canvas.drawOval(
                RectF(cx - radius * 0.93f, y - latitudeHeight, cx + radius * 0.93f, y + latitudeHeight),
                linePaint
            )
        }
        canvas.restoreToCount(save)

        val orbitSave = canvas.save()
        canvas.rotate(-24f, cx, cy)
        val orbit = RectF(cx - radius * 1.42f, cy - radius * 0.38f, cx + radius * 1.42f, cy + radius * 0.38f)
        softPaint.alpha = 150
        canvas.drawOval(orbit, softPaint)
        val dotAngle = Math.toRadians((rotation * 1.8f).toDouble())
        val dotX = cx + radius * 1.42f * cos(dotAngle).toFloat()
        val dotY = cy + radius * 0.38f * sin(dotAngle).toFloat()
        fillPaint.color = Color.WHITE
        canvas.drawCircle(dotX, dotY, radius * 0.085f, fillPaint)
        canvas.restoreToCount(orbitSave)
    }

    private fun drawReactor(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        fillPaint.shader = RadialGradient(
            cx,
            cy,
            radius * 1.45f,
            intArrayOf(0xFF46E9FF.toInt(), 0xFF145B96.toInt(), 0xFF06152D.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 0.72f, fillPaint)
        fillPaint.shader = null
        fillPaint.style = Paint.Style.STROKE
        linePaint.style = Paint.Style.STROKE

        for (ring in 0..3) {
            val ringRadius = radius * (0.72f + ring * 0.18f)
            val oval = RectF(cx - ringRadius, cy - ringRadius * 0.72f, cx + ringRadius, cy + ringRadius * 0.72f)
            linePaint.alpha = 100 + ring * 24
            linePaint.strokeWidth = resources.displayMetrics.density * (1f + ring * 0.35f)
            canvas.save()
            canvas.rotate(rotation * (if (ring % 2 == 0) 1f else -0.72f) + ring * 28f, cx, cy)
            canvas.drawArc(oval, ring * 38f, 235f, false, linePaint)
            canvas.drawArc(oval, ring * 38f + 250f, 55f, false, linePaint)
            canvas.restore()
        }

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.WHITE
        for (index in 0 until 8) {
            val angle = Math.toRadians((rotation * 1.5f + index * 45f).toDouble())
            val orbitRadius = radius * 1.08f
            canvas.drawCircle(
                cx + cos(angle).toFloat() * orbitRadius,
                cy + sin(angle).toFloat() * orbitRadius * 0.72f,
                radius * 0.035f,
                fillPaint
            )
        }
        fillPaint.style = Paint.Style.FILL
    }
}
