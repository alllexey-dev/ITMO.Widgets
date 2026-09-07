package dev.alllexey.itmowidgets.core.sport

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import org.junit.Assert.assertEquals
import org.junit.Test

class SportScoreSummaryTest {
    @Test fun `recordbook and my sport use identical calculation including bonus cap`() {
        listOf(0 to 0, 60 to 70, 120 to 20, 35 to 12).forEach { (attendance, bonus) ->
            val summary = SportScoreSummary(attendance, bonus)
            val mySport = SportScore(attendance, bonus, emptyList())
            assertEquals(mySport.total, summary.total)
            assertEquals(mySport.need, summary.remaining)
            assertEquals(mySport.otherCapped, summary.creditedBonus)
        }
        assertEquals(100, SportScoreSummary(60, 70).total)
        assertEquals(140, SportScoreSummary(120, 20).total)
        assertEquals(0, SportScoreSummary(120, 20).remaining)
    }
}
