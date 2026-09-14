package com.vangnang.youtubelive

import android.graphics.*

class ScorePainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val normal = Typeface.create("sans-serif", Typeface.BOLD)
    private val digits = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val body = Typeface.create("sans-serif", Typeface.NORMAL)
    private fun ink(fill: Ink, top: Float, bottom: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = if (fill.colors.size > 1) LinearGradient(0f, top, 0f, bottom, fill.colors.toIntArray(), null, Shader.TileMode.CLAMP) else null
        paint.color = fill.colors.first()
    }
    private fun stroke(color: Int?) {
        paint.shader = null; paint.color = color ?: Color.TRANSPARENT
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f
    }
    internal fun render(c: Canvas, marks: List<Mark>) {
        for (mark in marks) when (mark) {
            is Mark.Panel -> {
                ink(mark.ink, mark.y, mark.y + mark.h)
                c.drawRoundRect(mark.x, mark.y, mark.x + mark.w, mark.y + mark.h, mark.radius, mark.radius, paint)
                if (mark.stroke != null) { stroke(mark.stroke); c.drawRoundRect(mark.x, mark.y, mark.x + mark.w, mark.y + mark.h, mark.radius, mark.radius, paint) }
            }
            is Mark.Shape -> {
                val path = Path().apply { moveTo(mark.points[0].first, mark.points[0].second); mark.points.drop(1).forEach { lineTo(it.first, it.second) }; close() }
                ink(mark.ink, mark.points.minOf { it.second }, mark.points.maxOf { it.second })
                c.drawPath(path, paint)
                if (mark.stroke != null) { stroke(mark.stroke); c.drawPath(path, paint) }
            }
            is Mark.Dot -> {
                paint.shader = null; paint.style = Paint.Style.FILL; paint.color = mark.color
                c.drawCircle(mark.x, mark.y, mark.radius, paint)
            }
            is Mark.Caption -> {
                paint.shader = null; paint.style = Paint.Style.FILL; paint.color = mark.color
                paint.typeface = if (mark.number) digits else normal
                paint.textAlign = if (mark.center) Paint.Align.CENTER else Paint.Align.LEFT
                val fitted = fitBroadcastText(mark.value, mark.size, mark.minimum, mark.maxWidth) { text, size -> paint.textSize = size; paint.measureText(text) }
                paint.textSize = fitted.size
                c.drawText(fitted.text, mark.x, mark.baseline, paint)
            }
        }
    }
    fun board(c: Canvas, s: ScoreState, now: Long) {
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (!s.visible) return
        val marks = BroadcastDesign.board(s, now)
        // Keep the intermission scrim full-frame; transform only the actual board.
        if (s.intermission) render(c, marks.take(1))
        val bounds = s.boardBounds()
        val placement = s.placement()
        c.save()
        c.translate(placement.x, placement.y)
        c.scale(placement.scale, placement.scale)
        c.translate(-bounds.left, -bounds.top)
        render(c, if (s.intermission) marks.drop(1) else marks)
        c.restore()
    }
    fun ticker(c: Canvas, s: ScoreState, elapsed: Long) {
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (!s.tickerVisible || s.tickerText.isBlank()) return
        val bounds = tickerBounds()
        val placement = s.tickerPlacementValue()
        c.save()
        c.translate(placement.x, placement.y)
        c.scale(placement.scale, placement.scale)
        c.translate(-bounds.left, -bounds.top)
        render(c, BroadcastDesign.tickerFrame(s.tickerLabel, s.tickerColors))
        c.clipRect(158f, 6f, 1265f, 51f)
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = s.tickerColors.text ?: Color.WHITE
        paint.typeface = body; paint.textAlign = Paint.Align.LEFT; paint.textSize = 23f
        val x = 159f + tickerX(elapsed, s.tickerSpeed, paint.measureText(s.tickerText), 1105f)
        c.drawText(s.tickerText, x, 36f, paint)
        c.restore()
    }
}
