package dev.alllexey.itmowidgets.feature.sport.ui.my

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportScoreRulesTest {

    @Test
    fun theTotalIsAttendancePlusAtMostFortyBonusPoints() {
        assertEquals(SportScoreCounts(48, 20, 68), score(48, 20).counts())
        assertEquals(SportScoreCounts(90, 40, 130), score(90, 52).counts())
        assertEquals(SportScoreCounts(60, 40, 100), score(60, 70).counts())
    }

    @Test
    fun upToTheGoalTheRingShowsThePointsThemselves() {
        assertEquals(SportScoreRingShares(48f, 20f), score(48, 20).ringShares())
        assertEquals(SportScoreRingShares(60f, 40f), score(60, 70).ringShares())
    }

    @Test
    fun aboveTheGoalTheRingShowsSharesOfTheTotal() {
        val shares = score(90, 52).ringShares()!!

        assertEquals(90f * 100f / 130f, shares.attendance, 0.001f)
        assertEquals(40f * 100f / 130f, shares.bonus, 0.001f)
        assertEquals(100f, shares.attendance + shares.bonus, 0.001f)
    }

    @Test
    fun withoutPointsTheRingShowsOnlyItsTrack() {
        assertNull(score(0, 0).ringShares())
    }

    @Test
    fun countersStepTowardsTheTargetWithoutOvershooting() {
        val target = SportScoreCounts(90, 40, 130)

        assertEquals(SportScoreCounts.Zero, SportScoreCounts.Zero.towards(target, 0f))
        assertEquals(SportScoreCounts(45, 20, 65), SportScoreCounts.Zero.towards(target, 0.5f))
        assertEquals(target, SportScoreCounts.Zero.towards(target, 1f))
        // A refresh that lowers the score counts down; frames truncate like the View counters did.
        assertEquals(SportScoreCounts(85, 35, 120), target.towards(SportScoreCounts(80, 30, 110), 0.5f))
    }

    @Test
    fun theRawBonusShowsBesideTheCreditedOneOnlyOnceSettled() {
        assertFalse(score(90, 52).showsBonusOverLimit(settled = false))
        assertTrue(score(90, 52).showsBonusOverLimit(settled = true))
        assertFalse(score(90, 40).showsBonusOverLimit(settled = true))
    }

    @Test
    fun theStatusPassesOnceNothingIsMissing() {
        assertTrue(score(100, 0).passed)
        assertTrue(score(70, 30).passed)
        assertFalse(score(70, 29).passed)
        assertEquals(1, score(70, 29).need)
    }

    private fun score(attendances: Int, bonus: Int) = SportScore(attendances, bonus, emptyList())
}
