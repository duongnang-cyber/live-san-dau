package com.vangnang.youtubelive

import java.util.Locale

/** Shared vector scene: used by Android Canvas and the desktop visual test renderer. */
data class Ink(val colors: List<Int>) { constructor(color: Int) : this(listOf(color)) }
sealed class Mark {
    data class Panel(val x: Float, val y: Float, val w: Float, val h: Float, val ink: Ink, val radius: Float = 0f, val stroke: Int? = null) : Mark()
    data class Shape(val points: List<Pair<Float, Float>>, val ink: Ink, val stroke: Int? = null) : Mark()
    data class Caption(val value: String, val x: Float, val baseline: Float, val size: Float, val maxWidth: Float,
        val color: Int, val center: Boolean = false, val minimum: Float = 14f, val number: Boolean = false) : Mark()
    data class Dot(val x: Float, val y: Float, val radius: Float, val color: Int) : Mark()
}
data class FitText(val text: String, val size: Float)

fun fitBroadcastText(text: String, size: Float, minimum: Float, width: Float, measure: (String, Float) -> Float): FitText {
    if (text.isEmpty()) return FitText(text, size)
    val measured = measure(text, size)
    if (measured <= width) return FitText(text, size)
    val fitted = (size * width / measured).coerceAtLeast(minimum.coerceAtMost(size))
    if (measure(text, fitted) <= width) return FitText(text, fitted)
    var short = text
    while (short.isNotEmpty() && measure("$short…", fitted) > width) {
        val step = if (short.length >= 2 && Character.isSurrogatePair(short[short.lastIndex - 1], short.last())) 2 else 1
        short = short.dropLast(step)
    }
    return FitText("$short…", fitted)
}

object BroadcastDesign {
    val navy = 0xFF071D32.toInt()
    val deep = 0xFF031322.toInt()
    val teal = 0xFF008EA8.toInt()
    val cyan = 0xFF68E4ED.toInt()
    val ice = 0xFFDDFBFF.toInt()
    val white = 0xFFFFFFFF.toInt()
    val gold = 0xFFFFD46B.toInt()
    val serveGreen = 0xFF39E58C.toInt()
    private fun upper(text: String) = text.uppercase(Locale.ROOT)
    private class Scene {
        val marks = mutableListOf<Mark>()
        fun panel(x: Int, y: Int, w: Int, h: Int, ink: Ink, radius: Int = 0, stroke: Int? = null) { marks += Mark.Panel(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), ink, radius.toFloat(), stroke) }
        fun shape(ink: Ink, vararg xy: Int, stroke: Int? = null) { marks += Mark.Shape(xy.toList().chunked(2).map { it[0].toFloat() to it[1].toFloat() }, ink, stroke) }
        fun text(value: String, x: Int, y: Int, size: Int, width: Int, color: Int = white, center: Boolean = false, minimum: Int = 14, number: Boolean = false) {
            marks += Mark.Caption(value, x.toFloat(), y.toFloat(), size.toFloat(), width.toFloat(), color, center, minimum.toFloat(), number)
        }
        fun dot(x: Int, y: Int, r: Int, color: Int) { marks += Mark.Dot(x.toFloat(), y.toFloat(), r.toFloat(), color) }
    }
    fun board(s: ScoreState, now: Long): List<Mark> {
        if (!s.visible) return emptyList()
        val scene = Scene()
        when {
            s.intermission -> intermission(scene, s, now)
            s.sport == Sport.FOOTBALL -> footballPro(scene, s, now)
            s.sport == Sport.PICKLEBALL -> pickleballPro(scene, s, now)
            else -> courtPro(scene, s, now)
        }
        return scene.marks
    }
    private fun footballPro(d: Scene, s: ScoreState, now: Long) = with(d) {
        val style = s.boardStyle()
        val black = 0xFF15191E.toInt()
        val light = 0xFFF3F6F8.toInt()
        val accent = if (style == BoardStyle.CHAMPION) gold else cyan
        val teamInk = if (style == BoardStyle.MINIMAL) navy else white
        when (style) {
            BoardStyle.CLASSIC -> {
                panel(40, 46, 740, 70, Ink(0xFF28243D.toInt()), 18, 0xFFC5B7F5.toInt())
                panel(322, 50, 176, 62, Ink(0xFF5F487E.toInt()), 16)
                panel(40, 122, 740, 28, Ink(0xFF28243D.toInt()), 10)
                text("${upper(s.period)}  ${s.clock(now)}", 150, 142, 16, 198, ice, true, 12, true)
                text(upper(s.event), 270, 142, 13, 494, ice, minimum = 11)
            }
            BoardStyle.ARENA -> {
                // Compact TV layout: strong score hierarchy without covering the camera.
                panel(44, 45, 740, 114, Ink(0x66000000), 11)
                panel(40, 40, 740, 82, Ink(listOf(0xF5071D32.toInt(), 0xF5031322.toInt())), 10)
                panel(40, 40, 5, 82, Ink(0xFF20C4D6.toInt()), 3)
                panel(775, 40, 5, 82, Ink(0xFFFFC857.toInt()), 3)

                // Separate score cells make the result readable on a small phone preview.
                panel(326, 46, 68, 70, Ink(listOf(0xFF16B5C4.toInt(), 0xFF087C96.toInt())), 7)
                panel(426, 46, 68, 70, Ink(listOf(0xFF16B5C4.toInt(), 0xFF087C96.toInt())), 7)
                panel(397, 46, 26, 70, Ink(0xFF061827.toInt()), 4)

                // Quiet information rail: LIVE, time, period and optional event.
                panel(40, 125, 740, 30, Ink(0xF20A1825.toInt()), 7)
                panel(50, 130, 62, 20, Ink(0xFFE32636.toInt()), 10)
                text("LIVE", 81, 145, 12, 52, white, true, 10)
                text(s.clock(now), 158, 146, 17, 78, white, true, 13, true)
                panel(205, 132, 2, 16, Ink(0xFF365064.toInt()), 1)
                text(upper(s.period), 222, 145, 13, 105, cyan, minimum = 11)
                text(upper(s.event), 332, 145, 12, 430, ice, minimum = 10)
            }
            BoardStyle.MINIMAL -> {
                text(upper(s.period), 52, 40, 14, 240, white, minimum = 11)
                panel(40, 52, 740, 60, Ink(light), 2)
                panel(322, 52, 176, 60, Ink(navy))
                panel(40, 112, 280, 4, Ink(teal)); panel(500, 112, 280, 4, Ink(0xFFE65065.toInt()))
                panel(40, 120, 110, 30, Ink(navy), 2)
                text(s.clock(now), 95, 142, 20, 98, white, true, 15, true)
                text(upper(s.event), 164, 140, 13, 602, white, minimum = 11)
            }
            else -> {
                shape(Ink(black), 40, 60, 54, 46, 766, 46, 780, 60, 780, 116, 40, 116)
                shape(Ink(gold), 328, 46, 510, 46, 490, 116, 308, 116)
                panel(54, 112, 238, 3, Ink(gold)); panel(528, 112, 238, 3, Ink(gold))
                panel(40, 120, 740, 30, Ink(black))
                text(upper(s.event), 56, 141, 13, 480, gold, minimum = 11)
                text("${upper(s.period)}  ${s.clock(now)}", 660, 141, 16, 211, white, true, 12, true)
            }
        }
        // Team — score — score — team: both numbers are inside and close together.
        val teamAX = if (style == BoardStyle.ARENA) 181 else 180
        val teamBX = if (style == BoardStyle.ARENA) 639 else 640
        val teamWidth = if (style == BoardStyle.ARENA) 252 else 248
        text(upper(s.teamA), teamAX, 91, 26, teamWidth, teamInk, true, 16)
        text(upper(s.teamB), teamBX, 91, 26, teamWidth, teamInk, true, 16)
        val scoreInk = if (style == BoardStyle.CHAMPION) black else white
        val scoreAX = if (style == BoardStyle.ARENA) 360 else 373
        val scoreBX = if (style == BoardStyle.ARENA) 460 else 447
        val scoreY = if (style == BoardStyle.ARENA) 99 else 98
        text("${s.scoreA}", scoreAX, scoreY, 43, 60, scoreInk, true, 26, true)
        text("${s.scoreB}", scoreBX, scoreY, 43, 60, scoreInk, true, 26, true)
        text(if (style == BoardStyle.ARENA) ":" else "–", 410, 94, 24, 16, scoreInk, true, 14)
        if (style != BoardStyle.MINIMAL && style != BoardStyle.ARENA) {
            dot(51, 34, 3, accent)
            text("FOOTBALL", 63, 39, 12, 170, accent, minimum = 11)
        }
    }

    /** Compact broadcast layout dedicated to Pickleball. */
    private fun pickleballPro(d: Scene, s: ScoreState, now: Long) = with(d) {
        val style = s.boardStyle()
        val light = style == BoardStyle.MINIMAL
        val champion = style == BoardStyle.CHAMPION
        val header = when (style) {
            BoardStyle.ARENA -> Ink(listOf(0xFFE8471B.toInt(), 0xFFD80B7A.toInt()))
            BoardStyle.CLASSIC -> Ink(listOf(0xFF5F487E.toInt(), 0xFFD80B7A.toInt()))
            BoardStyle.MINIMAL -> Ink(listOf(0xFF007A8E.toInt(), 0xFF16B5C4.toInt()))
            else -> Ink(listOf(0xFFFFB000.toInt(), 0xFFE8471B.toInt()))
        }
        val body = if (light) 0xFFF4F7F9.toInt() else if (champion) 0xFF11161D.toInt() else 0xF5071D32.toInt()
        val alternate = if (light) 0xFFE7EEF2.toInt() else if (champion) 0xFF20262D.toInt() else 0xFF102D42.toInt()
        val setCell = if (light) 0xFFD8E3E9.toInt() else 0xFF1A3C50.toInt()
        val scoreCell = if (champion) 0xFFFFB000.toInt() else if (light) navy else 0xFF073B55.toInt()
        val nameInk = if (light) navy else white
        val scoreInk = if (champion) 0xFF15191E.toInt() else white
        val labelInk = if (light) 0xFF486271.toInt() else 0xFFAED3E4.toInt()

        // Shadow and a colored event header keep the graphic readable on any camera image.
        panel(45, 39, 520, 182, Ink(0x66000000), 8)
        panel(40, 34, 520, 34, header, 6)
        text("PICKLEBALL", 54, 56, 15, 128, white, minimum = 11)
        text(upper(s.event), 188, 55, 11, 244, white, minimum = 9)
        text(upper(s.period), 505, 56, 13, 94, white, true, 10)

        panel(40, 68, 520, 20, Ink(if (light) 0xFFE0E8EC.toInt() else deep))
        text("ĐỘI / VĐV", 78, 82, 10, 292, labelInk, minimum = 9)
        text("SET", 420, 82, 10, 52, labelInk, true, 9)
        text("ĐIỂM", 505, 82, 10, 88, labelInk, true, 9)

        for (row in 0..1) {
            val y = 88 + row * 50
            val teamRail = if (row == 0) 0xFF20C4D6.toInt() else 0xFFFFC857.toInt()
            panel(40, y, 350, 48, Ink(if (row == 0) body else alternate))
            panel(390, y, 60, 48, Ink(setCell))
            panel(450, y, 110, 48, Ink(scoreCell))
            panel(40, y, 5, 48, Ink(teamRail), 2)
            text(upper(if (row == 0) s.teamA else s.teamB), 78, y + 31, 20, 292, nameInk, minimum = 14)
            text("${if (row == 0) s.setsA else s.setsB}", 420, y + 33, 24, 52, if (light) navy else white, true, 17, true)
            text("${if (row == 0) s.scoreA else s.scoreB}", 505, y + 36, 34, 88, scoreInk, true, 23, true)
            if (s.serving == row + 1) {
                // One green bar = server 1; two green bars = server 2.
                panel(55, y + 7, 5, 34, Ink(serveGreen), 2)
                if (s.serverNumber == 2) panel(63, y + 7, 5, 34, Ink(serveGreen), 2)
            }
        }

        panel(40, 188, 520, 28, Ink(if (light) navy else deep), 0)
        val serveLabel = if (s.serving != 0) {
            "GIAO BÓNG ${if (s.serving == 1) "A" else "B"} · TAY ${s.serverNumber}"
        } else "CHƯA CHỌN GIAO BÓNG"
        text(serveLabel, 54, 207, 12, 344, if (s.serving == 0) labelInk else serveGreen, minimum = 10)
        text(s.clock(now), 512, 208, 15, 80, white, true, 11, true)
        panel(40, 216, 520, 2, Ink(if (champion) gold else cyan))
    }

    private fun courtPro(d: Scene, s: ScoreState, now: Long) = with(d) {
        val style = s.boardStyle()
        val light = style == BoardStyle.MINIMAL
        val champion = style == BoardStyle.CHAMPION
        val pickle = s.sport == Sport.PICKLEBALL
        val bg = if (champion) 0xFF15191E.toInt() else if (style == BoardStyle.CLASSIC) 0xFF28243D.toInt() else navy
        val accent = if (champion) gold else if (style == BoardStyle.CLASSIC) 0xFFC5B7F5.toInt() else cyan
        val nameColor = if (light) navy else white
        val pointsX = if (champion) 82 else 429
        val setsX = if (champion) 435 else 356
        val nameX = if (champion) {
            if (pickle) 160 else 148
        } else {
            if (pickle) 78 else 68
        }
        val nameWidth = if (pickle) 226 else if (champion) 238 else 240
        panel(40, 34, 432, 204, Ink(bg), if (style == BoardStyle.CLASSIC) 18 else if (style == BoardStyle.ARENA) 9 else 2, if (style == BoardStyle.CLASSIC) accent else null)
        if (champion) {
            shape(Ink(gold), 40, 34, 448, 34, 472, 58, 40, 58)
            text(if (pickle) "PICKLEBALL" else "BÓNG CHUYỀN", 54, 52, 15, 380, bg, minimum = 12)
        } else {
            panel(40, 34, 432, 31, Ink(if (light) 0xFFE7F0F4.toInt() else teal), if (light) 2 else 6)
            text(if (pickle) "PICKLEBALL" else "BÓNG CHUYỀN", 54, 56, 17, 252, if (light) navy else white, minimum = 13)
            text(upper(s.period), 411, 55, 13, 100, if (light) navy else white, true, 10)
        }
        text("SET", setsX, 82, 11, 46, accent, true, 10)
        text("ĐIỂM", pointsX, 82, 11, 69, accent, true, 10)
        text(if (champion) upper(s.period) else "ĐỘI / VĐV", nameX, 82, 11, nameWidth, accent, minimum = 10)
        for (row in 0..1) {
            val y = 90 + row * 57
            val nameBg = if (light) (if (row == 0) 0xFFFFFFFF.toInt() else 0xFFE9F0F4.toInt()) else bg
            if (champion) {
                panel(40, y, 84, 56, Ink(gold))
                panel(128, y, 271, 56, Ink(bg))
                panel(402, y, 70, 56, Ink(0xFF30353B.toInt()))
            } else {
                panel(40, y, 287, 56, Ink(nameBg))
                panel(328, y, 58, 56, Ink(if (light) 0xFFD9E4EC.toInt() else 0xFF153D53.toInt()))
                panel(388, y, 84, 56, Ink(if (light) navy else teal))
                panel(40, y, 4, 56, Ink(if (row == 0) teal else gold))
            }
            text(upper(if (row == 0) s.teamA else s.teamB), nameX, y + 35, 22, nameWidth, nameColor, minimum = 15)
            text("${if (row == 0) s.setsA else s.setsB}", setsX, y + 37, 27, 50, if (light) navy else white, true, 20, true)
            text("${if (row == 0) s.scoreA else s.scoreB}", pointsX, y + 42, 42, 72, if (champion) bg else white, true, 26, true)
            if (s.serving == row + 1) {
                if (pickle) {
                    // Pickleball: one green bar = server 1, two green bars = server 2.
                    val barX = if (champion) 138 else 52
                    panel(barX, y + 8, 5, 40, Ink(serveGreen), 2)
                    if (s.serverNumber == 2) panel(barX + 8, y + 8, 5, 40, Ink(serveGreen), 2)
                } else {
                    dot(nameX - 13, y + 27, 5, if (light) teal else gold)
                }
            }
        }
        text(if (pickle && s.serving != 0) "GIAO BÓNG ${if (s.serving == 1) "A" else "B"} · TAY ${s.serverNumber}" else if (s.serving != 0) "GIAO BÓNG ${if (s.serving == 1) "A" else "B"}" else "${upper(s.period)}", 53, 225, 12, 297, accent, minimum = 10)
        text(s.clock(now), 427, 226, 16, 74, white, true, 12, true)
        panel(40, 239, 432, 2, Ink(accent))
        if (s.event.isNotBlank()) {
            panel(40, 246, 432, 19, Ink(bg))
            text(upper(s.event), 52, 260, 11, 407, white, minimum = 10)
        }
    }
    // Original flat scoreboard intermission: no reference-image facets or broadcaster marks.
    private fun intermission(d: Scene, s: ScoreState, now: Long) = with(d) {
        panel(0, 0, 1280, 720, Ink(0x440C1420))
        panel(160, 178, 960, 316, Ink(0xF5101B28.toInt()), 24)
        panel(184, 202, 6, 266, Ink(0xFF9DDCCD.toInt()), 3)
        text(upper(s.breakTitle), 640, 236, 32, 846, center = true, minimum = 20)
        text("${upper(s.sport.title)}  •  ${upper(s.period)}", 640, 271, 16, 850, cyan, center = true)
        panel(210, 296, 272, 102, Ink(0xFF1D3041.toInt()), 12)
        panel(798, 296, 272, 102, Ink(0xFF1D3041.toInt()), 12)
        text(upper(s.teamA), 346, 357, 29, 244, center = true, minimum = 18)
        text(upper(s.teamB), 934, 357, 29, 244, center = true, minimum = 18)
        panel(500, 296, 280, 102, Ink(0xFF006B5F.toInt()), 18)
        text("${s.scoreA} : ${s.scoreB}", 640, 369, 70, 256, gold, true, 40, true)
        val summary = if (s.sport == Sport.FOOTBALL) "THỜI GIAN THI ĐẤU  ${s.clock(now)}"
            else "SET THẮNG   ${s.setsA} — ${s.setsB}    •    ${s.clock(now)}"
        text(summary, 640, 433, 19, 850, ice, true, 16)
        text(upper(s.event), 640, 473, 17, 850, ice, true, 13)
    }
    fun replayBumper(): List<Mark> = Scene().apply {
        panel(0, 0, 1280, 720, Ink(0xFF101B28.toInt()))
        panel(80, 80, 1120, 560, Ink(0xFF162B38.toInt()), 32, 0xFF9DDCCD.toInt())
        panel(104, 104, 6, 512, Ink(0xFF9DDCCD.toInt()), 3)
        panel(1170, 104, 6, 512, Ink(gold), 3)
        panel(180, 152, 410, 3, Ink(0xFF46666C.toInt()))
        panel(690, 152, 410, 3, Ink(0xFF46666C.toInt()))
        dot(640, 154, 8, gold)
        text("KHOẢNH KHẮC TRẬN ĐẤU", 640, 234, 24, 900, 0xFF9DDCCD.toInt(), true)
        text("PHÁT LẠI", 640, 386, 110, 900, white, true, 80)
        text("INSTANT REPLAY", 640, 450, 32, 900, gold, true)
        panel(524, 502, 232, 4, Ink(0xFF9DDCCD.toInt()), 2)
        text("LIVE SÂN ĐẤU", 640, 578, 22, 900, ice, true)
    }.marks
    fun tickerFrame(label: String): List<Mark> = Scene().apply {
        panel(0, 4, 1280, 48, Ink(0xF20A1825.toInt()))
        panel(0, 4, 1280, 2, Ink(0xFF20C4D6.toInt()))
        panel(10, 11, 132, 34, Ink(0xFFE32636.toInt()), 8)
        dot(25, 28, 4, white)
        text(upper(label), 82, 34, 15, 105, white, true, 11)
    }.marks
}
