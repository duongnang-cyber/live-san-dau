package com.vangnang.youtubelive
import org.junit.Assert.*
import org.junit.Test
class JerseyColorsTest {
 @Test fun colorsFollowTeamsAndSurviveReset() {
  val s = ScoreState(teamA = "A", teamB = "B", jerseyA = JerseyColor.GREEN, jerseyB = JerseyColor.YELLOW)
  assertEquals(s.jerseyA, s.swapTeams().jerseyB)
  assertEquals(s, s.swapTeams().swapTeams())
  assertEquals(s.jerseyA, s.resetMatch().jerseyA)
 }
 @Test fun hiddenOrOtherSportsDoNotShowKitBars() {
  assertTrue(jerseyMarks(ScoreState()).isEmpty())
  assertTrue(jerseyMarks(ScoreState(visible = true, jerseyBar = JerseyBar.OFF)).isEmpty())
  for (sport in listOf(Sport.VOLLEYBALL, Sport.PICKLEBALL)) assertTrue(jerseyMarks(ScoreState(visible = true, sport = sport)).isEmpty())
 }
 @Test fun barsRetainColorsAndAvoidNamesAcrossEveryStyle() {
  for (style in BoardStyle.entries) for (mode in listOf(JerseyBar.VERTICAL, JerseyBar.HORIZONTAL)) for (big in listOf(false, true)) {
   val s = ScoreState(visible = true, intermission = big, footballStyle = style, jerseyBar = mode, boardColors = OverlayColors(background = 0xFF444444.toInt()))
   val scene = BroadcastDesign.board(s, 0)
   val bars = scene.takeLast(2).filterIsInstance<Mark.Panel>()
   assertEquals(2, bars.size)
   assertEquals(s.jerseyA.argb, bars[0].ink.colors.single()); assertEquals(s.jerseyB.argb, bars[1].ink.colors.single())
   val names = scene.filterIsInstance<Mark.Caption>().filter { it.value in listOf(s.teamA, s.teamB) }
   assertEquals(2, names.size)
   for (i in 0..1) {
    val bar = bars[i]; val name = names[i]
    if (mode == JerseyBar.VERTICAL) assertTrue(bar.x + bar.w < name.x - name.maxWidth / 2)
    else assertTrue(bar.y > name.baseline)
   }
  }
 }

}
