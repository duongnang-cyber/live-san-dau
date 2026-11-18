package com.vangnang.youtubelive

enum class JerseyBar(val title: String) { OFF("Ẩn vạch màu"), VERTICAL("Vạch dọc cạnh tên đội"), HORIZONTAL("Vạch ngang dưới tên đội") }
enum class JerseyColor(val title: String, val argb: Int) {
 RED("Đỏ", 0xFFE32636.toInt()), BLUE("Xanh dương", 0xFF2374FF.toInt()),
 WHITE("Trắng", 0xFFFFFFFF.toInt()), BLACK("Đen", 0xFF090D12.toInt()),
 YELLOW("Vàng", 0xFFFFD600.toInt()), GREEN("Xanh lá", 0xFF22C55E.toInt()),
 ORANGE("Cam", 0xFFFF861C.toInt()), PURPLE("Tím", 0xFF9955DD.toInt()),
 PINK("Hồng", 0xFFFF70BA.toInt()), CYAN("Xanh ngọc", 0xFF20C4D6.toInt())
}
fun jerseyMarks(s: ScoreState): List<Mark> {
 if (!s.visible || s.sport != Sport.FOOTBALL || s.jerseyBar == JerseyBar.OFF) return emptyList()
 val vertical = s.jerseyBar == JerseyBar.VERTICAL
 return listOf(s.jerseyA, s.jerseyB).mapIndexed { index, color ->
  val x = if (s.intermission) { if (vertical) 222f + index * 588f else 306f + index * 588f }
          else { if (vertical) 60f + index * 458f else 140f + index * 460f }
  val y = if (s.intermission) { if (vertical) 325f else 380f } else { if (vertical) 65f else 103f }
  Mark.Panel(x, y, if (vertical) 8f else 80f, if (vertical) 34f else 5f, Ink(color.argb), 2f, 0xFF87929B.toInt())
 }
}
