package com.vangnang.youtubelive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot

/** Local touch controls only: this view is never passed to the encoder. */
class ScoreEditorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var snapshot: () -> EditableOverlay = {
        EditableOverlay(BoardPlacement(32f, 32f, 0.6f), BoardBounds(0f, 0f, 400f, 200f), "Lớp phủ")
    }
    var defaultPlacement: () -> BoardPlacement = { BoardPlacement(32f, 32f, 0.6f) }
    var onBegin: () -> Unit = {}
    var onChange: (BoardPlacement) -> Unit = {}
    var onFinish: () -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val resetRect = RectF()
    private val density = resources.displayMetrics.density
    private var mode = 0 // 1 move, 2 corner resize, 3 pinch
    private var pointerId = -1
    private var startX = 0f
    private var startY = 0f
    private var initial = BoardPlacement(32f, 32f, 0.6f)
    private var started = false
    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (!started) return false
            mode = 3
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val overlay = snapshot()
            onChange(overlay.placement.resize(detector.scaleFactor, overlay.bounds))
            invalidate()
            return true
        }
    })
    init {
        isClickable = true
        contentDescription = "Chỉnh lớp phủ: kéo để di chuyển, kéo góc dưới phải hoặc chụm hai ngón để đổi cỡ."
    }
    private fun frame(): RectF {
        val overlay = snapshot(); val p = overlay.placement; val b = overlay.bounds
        return RectF(p.x * width / 1280f, p.y * height / 720f,
            (p.x + b.width * p.scale) * width / 1280f, (p.y + b.height * p.scale) * height / 720f)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = frame()
        paint.color = Color.YELLOW; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
        canvas.drawRect(r, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(r.right, r.bottom, 13 * density, paint)
        paint.color = Color.BLACK; paint.textSize = 17 * density; paint.textAlign = Paint.Align.CENTER
        canvas.drawText("↔", r.right, r.bottom + 6 * density, paint)
        val overlay = snapshot()
        val label = "${overlay.title} • ${(overlay.placement.scale * 100).toInt()}% • Kéo góc vàng đổi cỡ"
        paint.textSize = 12 * density; paint.textAlign = Paint.Align.LEFT
        val labelY = if (r.bottom + 35 * density < height - 58 * density) r.bottom + 33 * density else (r.top - 8 * density).coerceAtLeast(20 * density)
        paint.color = 0xDD071D32.toInt()
        canvas.drawRoundRect(r.left, labelY - 17 * density, (r.left + paint.measureText(label) + 12 * density).coerceAtMost(width.toFloat()), labelY + 5 * density, 4 * density, 4 * density, paint)
        paint.color = Color.WHITE
        canvas.drawText(label, r.left + 5 * density, labelY, paint)
        paint.color = 0xE6071D32.toInt()
        canvas.drawRect(0f, height - 48 * density, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.WHITE; paint.textSize = 12 * density
        canvas.drawText("Kéo ${overlay.title.lowercase()} • Chụm 2 ngón đổi cỡ", 12 * density, height - 18 * density, paint)
        resetRect.set(width - 124 * density, height - 45 * density, width - 6 * density, height - 3 * density)
        paint.color = 0xFF007A8E.toInt()
        canvas.drawRoundRect(resetRect, 5 * density, 5 * density, paint)
        paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER
        canvas.drawText("ĐẶT LẠI", resetRect.centerX(), resetRect.centerY() + 5 * density, paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (resetRect.contains(event.x, event.y)) {
                onBegin(); onChange(defaultPlacement()); onFinish(); invalidate(); performClick()
                return true
            }
            val r = frame()
            val corner = hypot(event.x - r.right, event.y - r.bottom) <= 32 * density
            val hit = RectF(r).apply { inset(-15 * density, -15 * density) }.contains(event.x, event.y)
            if (!corner && !hit) return false
            onBegin(); started = true; mode = if (corner) 2 else 1
            initial = snapshot().placement; pointerId = event.getPointerId(0)
            startX = event.x * 1280f / width; startY = event.y * 720f / height
            parent.requestDisallowInterceptTouchEvent(true)
        }
        pinch.onTouchEvent(event)
        if (!started) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (!pinch.isInProgress && mode != 3) {
                val i = event.findPointerIndex(pointerId)
                if (i >= 0) {
                    val dx = event.getX(i) * 1280f / width - startX
                    val dy = event.getY(i) * 720f / height - startY
                    val b = snapshot().bounds
                    val next = if (mode == 1) initial.move(dx, dy, b) else initial.resizeByDrag(dx, dy, b)
                    onChange(next); invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Rebase on the remaining finger, preventing a jump after a pinch.
                val remaining = if (event.actionIndex == 0) 1 else 0
                if (remaining < event.pointerCount) {
                    pointerId = event.getPointerId(remaining)
                    startX = event.getX(remaining) * 1280f / width; startY = event.getY(remaining) * 720f / height
                    initial = snapshot().placement; mode = 1
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                started = false; mode = 0; pointerId = -1
                parent.requestDisallowInterceptTouchEvent(false)
                onFinish(); performClick()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
