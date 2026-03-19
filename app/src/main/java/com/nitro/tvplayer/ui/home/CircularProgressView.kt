package com.nitro.tvplayer.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Draws a circular arc progress ring around content.
 * Used on dashboard cards to show content loading/download progress.
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap   = Paint.Cap.ROUND
        color       = Color.parseColor("#1A3A7A")  // dim track
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap   = Paint.Cap.ROUND
        color       = Color.parseColor("#2979FF")  // bright blue arc
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val oval = RectF()

    /** 0f – 1f   (0 = empty, 1 = full circle) */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** true  → spin animation (indeterminate loading)
     *  false → static arc (determinate progress) */
    var isSpinning: Boolean = false
        set(value) {
            field = value
            if (value) startSpinning() else stopSpinning()
        }

    private var spinAngle = -90f
    private val spinRunnable = object : Runnable {
        override fun run() {
            spinAngle = (spinAngle + 4f) % 360f
            invalidate()
            if (isSpinning) postDelayed(this, 16)
        }
    }

    private fun startSpinning() { removeCallbacks(spinRunnable); post(spinRunnable) }
    private fun stopSpinning()  { removeCallbacks(spinRunnable) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad  = trackPaint.strokeWidth
        val size = minOf(width, height).toFloat()
        oval.set(pad, pad, size - pad, size - pad)

        // Track (full circle)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        if (isSpinning) {
            // Spinning arc — 90 degree sweep chasing itself
            canvas.drawArc(oval, spinAngle, 90f, false, progressPaint)
        } else if (progress > 0f) {
            // Determinate arc — starts at top (-90°)
            val sweep = 360f * progress
            canvas.drawArc(oval, -90f, sweep, false, progressPaint)

            // Small white dot at the arc tip
            val rad    = (size / 2f) - pad
            val cx     = size / 2f
            val cy     = size / 2f
            val tipRad = Math.toRadians((-90f + sweep).toDouble())
            val tx     = (cx + rad * Math.cos(tipRad)).toFloat()
            val ty     = (cy + rad * Math.sin(tipRad)).toFloat()
            canvas.drawCircle(tx, ty, 5f, dotPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopSpinning()
    }
}
