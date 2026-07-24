package dev.alllexey.itmowidgets.core.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSportScoreOverrideProviderTest {

    private val store = FakeSportScoreOverrideStore()
    private val provider = DefaultSportScoreOverrideProvider(store)

    @Test
    fun `provider returns null when override is absent`() {
        assertEquals(null, provider.getOverride())
    }

    @Test
    fun `provider returns stored override`() {
        val override = SportScoreOverride(attendances = 100, bonus = 20)

        provider.setOverride(override)

        assertEquals(override, provider.getOverride())
    }

    @Test
    fun `controller clears override`() {
        provider.setOverride(SportScoreOverride(attendances = 100, bonus = 20))

        provider.setOverride(null)

        assertEquals(null, provider.getOverride())
    }

    private class FakeSportScoreOverrideStore : SportScoreOverrideStore {
        private var value: SportScoreOverride? = null

        override fun getOverride(): SportScoreOverride? = value

        override fun setOverride(value: SportScoreOverride?) {
            this.value = value
        }
    }
}
