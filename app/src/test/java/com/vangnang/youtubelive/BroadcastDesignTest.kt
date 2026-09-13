package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BroadcastDesignTest {
    private val demo = ScoreState(visible = true, teamA = "CẨM LÝ", teamB = "BẮC NINH", event = "GIẢI THỂ THAO HỌC ĐƯỜNG", scoreA = 2, scoreB = 1, elapsedMs = 2_729_000,
        footballStyle = BoardStyle.CLASSIC, volleyballStyle = BoardStyle.CLASSIC, pickleballStyle = BoardStyle.CLASSIC)
    @Test fun disabledBoardHasNoGraphics() { assertTrue(BroadcastDesign.board(demo.copy(visible = false), 0).isEmpty()) }
    @Test fun everySportHasDistinctLayout() {
        val football = BroadcastDesign.board(demo, 0)
        val volley = BroadcastDesign.board(demo.copy(sport = Sport.VOLLEYBALL), 0)
        val pickle = BroadcastDesign.board(demo.copy(sport = Sport.PICKLEBALL), 0)
        assertNotEquals(football, volley); assertNotEquals(volley, pickle)
    }
    @Test fun originalTeamNamesAreNotMutated() {
        val s = demo.copy(teamA = "Đội bóng của thầy Nắng")
        BroadcastDesign.board(s, 0)
        assertEquals("Đội bóng của thầy Nắng", s.teamA)
    }
    @Test fun courtHasSeparateSetAndPointLabels() {
        for (sport in listOf(Sport.VOLLEYBALL, Sport.PICKLEBALL)) {
            val labels = BroadcastDesign.board(demo.copy(sport = sport), 0).filterIsInstance<Mark.Caption>().map { it.value }
            assertTrue(labels.contains("SET")); assertTrue(labels.contains("ĐIỂM"))
        }
    }
    @Test fun pickleballServerNumberIsVisible() {
        val marks = BroadcastDesign.board(demo.copy(sport = Sport.PICKLEBALL, serving = 1, serverNumber = 2), 0)
        assertTrue(marks.filterIsInstance<Mark.Caption>().any { it.value.contains("TAY 2") })
    }
    @Test fun noServerBadgeIfUnset() {
        val marks = BroadcastDesign.board(demo.copy(sport = Sport.PICKLEBALL, serving = 0), 0)
        assertFalse(marks.filterIsInstance<Mark.Caption>().any { it.value.contains("TAY ") })
    }
    @Test fun pickleballServerOneHasOneGreenBarAndServerTwoHasTwo() {
        for (style in BoardStyle.entries) for (server in 1..2) {
            val marks = BroadcastDesign.board(demo.copy(sport = Sport.PICKLEBALL, serving = 1, serverNumber = server).withBoardStyle(style), 0)
            val bars = marks.filterIsInstance<Mark.Panel>().filter { it.ink.colors == listOf(BroadcastDesign.serveGreen) && it.w == 5f && it.h == 40f }
            assertEquals(server, bars.size)
            assertTrue(bars.all { it.y == 98f })
        }
    }
    @Test fun pickleballBarsFollowServingSideAndHideWhenUnset() {
        val teamB = BroadcastDesign.board(demo.copy(sport = Sport.PICKLEBALL, serving = 2, serverNumber = 2), 0)
            .filterIsInstance<Mark.Panel>().filter { it.ink.colors == listOf(BroadcastDesign.serveGreen) }
        assertEquals(2, teamB.size); assertTrue(teamB.all { it.y == 155f })
        val hidden = BroadcastDesign.board(demo.copy(sport = Sport.PICKLEBALL, serving = 0, serverNumber = 2), 0)
            .filterIsInstance<Mark.Panel>().filter { it.ink.colors == listOf(BroadcastDesign.serveGreen) }
        assertTrue(hidden.isEmpty())
    }
    @Test fun customBreakTitleIsRendered() {
        val marks = BroadcastDesign.board(demo.copy(intermission = true, breakTitle = "Kết thúc trận"), 0)
        assertTrue(marks.filterIsInstance<Mark.Caption>().any { it.value == "KẾT THÚC TRẬN" })
    }
    @Test fun labelsDoNotPretendToBeVtv() {
        for (sport in Sport.entries) {
            val labels = BroadcastDesign.board(demo.copy(sport = sport), 0).filterIsInstance<Mark.Caption>()
            assertFalse(labels.any { it.value.contains("VTV") })
        }
    }
    @Test fun allGeometryFitsVideo() {
        for (sport in Sport.entries) for (pause in listOf(false, true)) {
            BroadcastDesign.board(demo.copy(sport = sport, intermission = pause), 0).forEach { m -> when (m) {
                is Mark.Panel -> { assertTrue(m.x >= 0 && m.y >= 0); assertTrue(m.x + m.w <= 1280 && m.y + m.h <= 720) }
                is Mark.Shape -> m.points.forEach { assertTrue(it.first in 0f..1280f && it.second in 0f..720f) }
                is Mark.Dot -> assertTrue(m.x - m.radius >= 0 && m.x + m.radius <= 1280 && m.y - m.radius >= 0 && m.y + m.radius <= 720)
                is Mark.Caption -> { val left = if (m.center) m.x - m.maxWidth / 2 else m.x; assertTrue(left >= 0 && left + m.maxWidth <= 1280); assertTrue(m.baseline in 0f..720f) }
            } }
        }
    }
    @Test fun longLabelsHaveLegibleMinimumAndEllipsis() {
        val fitted = fitBroadcastText("TÊN ĐỘI RẤT DÀI KHÔNG ĐƯỢC TRÀN SANG Ô ĐIỂM", 27f, 17f, 224f) { text, size -> text.length * size * 0.6f }
        assertEquals(17f, fitted.size, 0.01f); assertTrue(fitted.text.endsWith("…")); assertTrue(fitted.text.length * fitted.size * 0.6f <= 224f)
    }
    @Test fun fittingShortTextIsUnchanged() { assertEquals(FitText("ĐỘI A", 27f), fitBroadcastText("ĐỘI A", 27f, 17f, 224f) { t, sz -> t.length * sz / 2 }) }
    @Test fun ellipsisDoesNotSplitEmoji() { val fit = fitBroadcastText("🏆🏆🏆🏆", 20f, 20f, 45f) { t, sz -> t.codePointCount(0, t.length) * sz }; assertEquals("🏆…", fit.text) }


    /** Exact vector positions/colors exported from the app scene. Desktop fonts may differ. */
    @Test fun renderDesignContactSheet() {
        val output = File("build/reports/score-preview").apply { mkdirs() }
        val football = demo.copy(period = "HIỆP 2")
        val volley = demo.copy(sport = Sport.VOLLEYBALL, period = "SET 3", scoreA = 24, scoreB = 22, setsA = 1, setsB = 1, serving = 1, elapsedMs = 846000)
        val pickle = demo.copy(sport = Sport.PICKLEBALL, teamA = "MINH / HÙNG", teamB = "NAM / PHONG", period = "SET 2", scoreA = 9, scoreB = 8, setsA = 1, setsB = 0, serving = 2, serverNumber = 2, elapsedMs = 728000)
        val pause = football.copy(intermission = true)
        val doc = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1280\" height=\"1090\" viewBox=\"0 0 1280 1090\">")
            append("<rect width=\"1280\" height=\"1090\" fill=\"#09131f\"/>")
            append(label("LIVE SÂN ĐẤU / CONTOUR", 52, 45, 25))
            append(label("Thiết kế 1.11 • vector riêng • không phải ảnh chụp điện thoại", 52, 75, 17, "#9ab4c9"))
            append(label("01   BÓNG ĐÁ", 52, 116, 15, "#68e4ed"))
            append("<rect x=\"34\" y=\"130\" width=\"1212\" height=\"178\" rx=\"18\" fill=\"#153a36\"/>")
            append("<g transform=\"translate(185 137) scale(1.04)\">")
            append(svg(BroadcastDesign.board(football, 0), "fb")); append("</g>")
            append(label("02   BÓNG CHUYỀN", 52, 348, 15, "#68e4ed"))
            append(label("03   PICKLEBALL", 686, 348, 15, "#68e4ed"))
            append("<rect x=\"34\" y=\"362\" width=\"588\" height=\"283\" rx=\"18\" fill=\"#21384a\"/>")
            append("<rect x=\"658\" y=\"362\" width=\"588\" height=\"283\" rx=\"18\" fill=\"#1a3d48\"/>")
            append("<g transform=\"translate(72 363) scale(1.04)\">")
            append(svg(BroadcastDesign.board(volley, 0), "vb")); append("</g>")
            append("<g transform=\"translate(696 363) scale(1.04)\">")
            append(svg(BroadcastDesign.board(pickle, 0), "pb")); append("</g>")
            append(label("04   BẢNG GIỮA HIỆP / GIỮA SET", 52, 686, 15, "#68e4ed"))
            append("<g transform=\"translate(64 552) scale(0.9)\">")
            append(svg(BroadcastDesign.board(pause, 0).drop(1), "half")); append("</g>")
            append("<g transform=\"translate(35 1020) scale(0.945)\">")
            append(svg(BroadcastDesign.tickerFrame("GIỚI THIỆU"), "ticker"))
            append(label("Chào mừng quý vị đến với giải thể thao học đường • Đồng hành cùng các đội tuyển", 220, 36, 23))
            append("</g></svg>")
        }
        File(output, "Broadcast_1.11_preview.svg").writeText(doc)
        for ((name, state) in listOf("football" to football, "volleyball" to volley, "pickleball" to pickle, "intermission" to pause)) {
            File(output, "$name.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1280\" height=\"720\">" + svg(BroadcastDesign.board(state, 0), name) + "</svg>")
        }
        assertTrue(File(output, "Broadcast_1.11_preview.svg").length() > 5000)
    }
    @Test fun renderNineNewTemplates() {
        val output = File("build/reports/score-preview").apply { mkdirs() }
        val doc = buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1440\" height=\"1120\">")
            append("<rect width=\"1440\" height=\"1120\" fill=\"#09131f\"/>")
            append(label("LIVE SÂN ĐẤU 1.11 / BẢNG TỈ SỐ", 40, 50, 28))
            append(label("9 mẫu mới • Điểm bóng đá ở giữa • Mẫu lưu riêng cho từng môn", 40, 82, 18, "#9ab4c9"))
            for ((row, sport) in Sport.entries.withIndex()) for ((col, style) in BoardStyle.entries.drop(1).withIndex()) {
                val x = 28 + col * 476; val y = 110 + row * 330
                append("<rect x=\"$x\" y=\"$y\" width=\"456\" height=\"308\" rx=\"12\" fill=\"#173348\"/>")
                append(label("${sport.title} / ${style.title.substringBefore(" •")}", x + 16, y + 30, 18, "#68e4ed"))
                val state = demo.copy(sport = sport, period = if (sport == Sport.FOOTBALL) "HIỆP 2" else "SET 3",
                    scoreA = if (sport == Sport.FOOTBALL) 2 else if (sport == Sport.VOLLEYBALL) 24 else 9,
                    scoreB = if (sport == Sport.FOOTBALL) 1 else if (sport == Sport.VOLLEYBALL) 22 else 8,
                    setsA = 1, serving = 1, serverNumber = 2).withBoardStyle(style)
                val scale = if (sport == Sport.FOOTBALL) 0.57 else 0.98
                append("<g transform=\"translate(${x - 20 * scale + 10} ${y + if (sport == Sport.FOOTBALL) 106 else 40}) scale($scale)\">")
                append(svg(BroadcastDesign.board(state, 0), "${row}_$col")); append("</g>")
            }
            append("</svg>")
        }
        File(output, "Broadcast_1.11_templates.svg").writeText(doc)
        assertTrue(doc.contains("PHÁT LẠI").not())
    }
    @Test fun renderOriginalReplayBumper() {
        val output = File("build/reports/score-preview").apply { mkdirs() }
        val marks = BroadcastDesign.replayBumper()
        File(output, "Replay_1.11.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1280\" height=\"720\">" + svg(marks, "replay") + "</svg>")
        assertTrue(marks.filterIsInstance<Mark.Caption>().any { it.value == "LIVE SÂN ĐẤU" })
        assertFalse(marks.any { it is Mark.Shape })
    }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun rgb(color: Int) = "#%06x".format(color and 0xffffff)
    private fun alpha(color: Int) = (color ushr 24) / 255.0
    private fun label(text: String, x: Int, y: Int, size: Int, color: String = "#ffffff") =
        "<text x=\"$x\" y=\"$y\" font-family=\"DejaVu Sans\" font-size=\"$size\" font-weight=\"bold\" fill=\"$color\">${escape(text)}</text>"
    private fun svg(marks: List<Mark>, prefix: String): String = buildString {
        for ((index, m) in marks.withIndex()) {
            fun fill(ink: Ink): String {
                if (ink.colors.size == 1) return "fill=\"${rgb(ink.colors[0])}\" fill-opacity=\"${alpha(ink.colors[0])}\""
                val id = "$prefix-$index"
                append("<defs><linearGradient id=\"$id\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">")
                ink.colors.forEachIndexed { i, c -> append("<stop offset=\"${i.toFloat() / (ink.colors.size - 1)}\" stop-color=\"${rgb(c)}\" stop-opacity=\"${alpha(c)}\"/>") }
                append("</linearGradient></defs>")
                return "fill=\"url(#$id)\""
            }
            fun stroke(color: Int?) = color?.let { "stroke=\"${rgb(it)}\" stroke-opacity=\"${alpha(it)}\" stroke-width=\"1.4\"" } ?: ""
            when (m) {
                is Mark.Panel -> {
                    val f = fill(m.ink)
                    append("<rect x=\"${m.x}\" y=\"${m.y}\" width=\"${m.w}\" height=\"${m.h}\" rx=\"${m.radius}\" $f ${stroke(m.stroke)}/>")
                }
                is Mark.Shape -> {
                    val f = fill(m.ink)
                    append("<polygon points=\"${m.points.joinToString(" ") { "${it.first},${it.second}" }}\" $f ${stroke(m.stroke)}/>")
                }
                is Mark.Dot -> append("<circle cx=\"${m.x}\" cy=\"${m.y}\" r=\"${m.radius}\" fill=\"${rgb(m.color)}\" fill-opacity=\"${alpha(m.color)}\"/>")
                is Mark.Caption -> {
                    val fit = fitBroadcastText(m.value, m.size, m.minimum, m.maxWidth) { text, size -> text.codePointCount(0, text.length) * size * 0.62f }
                    append("<text x=\"${m.x}\" y=\"${m.baseline}\" font-family=\"DejaVu Sans\" font-size=\"${fit.size}\" font-weight=\"bold\" text-anchor=\"${if (m.center) "middle" else "start"}\" fill=\"${rgb(m.color)}\">${escape(fit.text)}</text>")
                }
            }
        }
    }
}
