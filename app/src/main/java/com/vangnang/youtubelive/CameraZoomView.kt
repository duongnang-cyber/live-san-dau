package com.vangnang.youtubelive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.Locale

/** Operator-only touch surface. Never encoded; scoreboard editing has priority above it. */
class CameraZoomView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var canZoom: () -> Boolean = { false }
    var onZoom: (Float) -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var zoom = 1f
    private var labelUntil = 0L
    private val hideLabel = Runnable { invalidate() }
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector) = canZoom()
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (canZoom()) onZoom(detector.scaleFactor)
            return true
        }
    })
    init { isClickable = true; contentDescription = "Chụm hoặc tách hai ngón để zoom camera" }
    fun showZoom(value: Float) {
        zoom = value; labelUntil = SystemClock.elapsedRealtime() + 1200
        removeCallbacks(hideLabel); postDelayed(hideLabel, 1250); invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        if (SystemClock.elapsedRealtime() >= labelUntil) return
        val density = resources.displayMetrics.density
        val x = width / 2f; val y = height - 106 * density
        paint.color = 0xD9071D32.toInt()
        canvas.drawRoundRect(x - 44 * density, y - 22 * density, x + 44 * density, y + 14 * density, 18 * density, 18 * density, paint)
        paint.color = -1; paint.textSize = 16 * density; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(String.format(Locale.ROOT, "%.1f×", zoom), x, y + 2 * density, paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!canZoom()) { if (event.actionMasked == MotionEvent.ACTION_CANCEL) detector.onTouchEvent(event); return false }
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { removeCallbacks(hideLabel); super.onDetachedFromWindow() }
}
