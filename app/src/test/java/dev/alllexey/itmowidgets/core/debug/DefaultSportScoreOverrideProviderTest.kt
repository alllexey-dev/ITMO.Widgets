package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.domain.model.sport.SportScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DefaultSportScoreOverrideProviderTest {

    private val store = FakeSportScoreOverrideStore()
    private val provider = DefaultSportScoreOverrideProvider(store)

    @Test
    fun `apply returns original score when override is absent`() {
        val score = sportScore(attendances = 70, bonus = 15)

        val result = provider.apply(score)

        assertSame(score, result)
    }

    @Test
    fun `apply replaces attendance and bonus points`() {
        val score = sportScore(attendances = 70, bonus = 15)
        provider.setOverride(SportScoreOverride(attendances = 100, bonus = 20))

        val result = provider.apply(score)

        assertEquals(100, result.attendances)
        assertEquals(20, result.other)
        assertEquals(score.attendancesData, result.attendancesData)
    }

    @Test
    fun `controller clears override`() {
        provider.setOverride(SportScoreOverride(attendances = 100, bonus = 20))

        provider.setOverride(null)

        assertEquals(null, provider.getOverride())
    }

    private fun sportScore(attendances: Int, bonus: Int) = SportScore(
        attendances = attendances,
        other = bonus,
        attendancesData = emptyList()
    )

    private class FakeSportScoreOverrideStore : SportScoreOverrideStore {
        private var value: SportScoreOverride? = null

        override fun getOverride(): SportScoreOverride? = value

        override fun setOverride(value: SportScoreOverride?) {
            this.value = value
        }
    }
}
