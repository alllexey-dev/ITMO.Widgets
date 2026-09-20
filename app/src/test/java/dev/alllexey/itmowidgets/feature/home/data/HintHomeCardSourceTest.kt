package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.home.FakeHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.FakeHomeHintStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HintHomeCardSourceTest {
    private val status = FakeHomeHintStatus()
    private val store = FakeHomeHintStore()
    private val services = FakeServices()
    private val source = HintHomeCardSource(status, store, services)

    @Test
    fun `every reason is a hint in feed order`() = runTest {
        status.widgetPlaced = false
        status.notifications = false
        services.enabled.value = false

        val hints = source.observe().first().map { (it as HomeCard.Hint).hint }

        assertEquals(listOf(HomeHint.WIDGETS, HomeHint.NOTIFICATIONS, HomeHint.SERVICES), hints)
        assertEquals(hints.map { it.kind }, hints.map { it.kind }.sortedBy(HomeCardKind::ordinal))
    }

    @Test
    fun `nothing to nudge means no cards`() = runTest {
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `a closed hint stays closed and the opt-in removes its own`() = runTest {
        status.notifications = false
        services.enabled.value = false
        store.dismiss(HomeHint.NOTIFICATIONS)

        assertEquals(listOf(HomeCard.Hint(HomeHint.SERVICES)), source.observe().first())

        services.enabled.value = true
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `revalidation re-reads the device state`() = runTest {
        status.widgetPlaced = false
        assertEquals(1, source.observe().first().size)

        status.widgetPlaced = true
        assertEquals(AppResult.Success(Unit), source.refresh())
        assertTrue(source.observe().first().isEmpty())
    }

    private class FakeServices : CustomServicesRepository {
        val enabled = MutableStateFlow(true)
        override fun observeEnabled() = enabled
        override suspend fun isEnabled() = enabled.value
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }
}
