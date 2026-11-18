package com.vangnang.youtubelive
import java.io.File
import org.junit.Test
/** Exports the shared scene for visual review without Android or desktop graphics dependencies. */
class JerseyPreviewTest {
 @Test fun renderKitStyles() {
  fun color(v: Int) = "#%06x".format(v and 0xFFFFFF)
  fun xml(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
  val svg = StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"640\"><rect width=\"1200\" height=\"640\" fill=\"#173D2B\"/>")
  for ((row, style) in BoardStyle.entries.withIndex()) for ((col, mode) in listOf(JerseyBar.VERTICAL, JerseyBar.HORIZONTAL).withIndex()) {
   val s = ScoreState(visible=true, teamA="BẮC NINH", teamB="HÀ NỘI", scoreA=2, scoreB=1, footballStyle=style, jerseyBar=mode)
   svg.append("<g transform=\"translate(${col*600} ${row*160}) scale(0.74)\">")
   for (m in BroadcastDesign.board(s,0)) when(m) {
    is Mark.Panel -> svg.append("<rect x=\"${m.x}\" y=\"${m.y}\" width=\"${m.w}\" height=\"${m.h}\" rx=\"${m.radius}\" fill=\"${color(m.ink.colors.first())}\" stroke=\"${m.stroke?.let { color(it) } ?: "none"}\"/>")
    is Mark.Shape -> svg.append("<polygon points=\"${m.points.joinToString(" ") { "${it.first},${it.second}" }}\" fill=\"${color(m.ink.colors.first())}\"/>")
    is Mark.Dot -> svg.append("<circle cx=\"${m.x}\" cy=\"${m.y}\" r=\"${m.radius}\" fill=\"${color(m.color)}\"/>")
    is Mark.Caption -> svg.append("<text x=\"${m.x}\" y=\"${m.baseline}\" font-family=\"DejaVu Sans\" font-weight=\"bold\" font-size=\"${m.size}\" text-anchor=\"${if(m.center) "middle" else "start"}\" fill=\"${color(m.color)}\">${xml(m.value)}</text>")
   }
   svg.append("</g>")
  }
  svg.append("</svg>");File("build/review").mkdirs();File("build/review/jersey-styles.svg").writeText(svg.toString())
 }
}
