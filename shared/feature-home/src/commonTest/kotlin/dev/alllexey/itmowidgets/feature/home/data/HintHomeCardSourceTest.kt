package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.feature.home.FakeHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.FakeHomeHintStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class HintHomeCardSourceTest {
    private val status = FakeHomeHintStatus()
    private val store = FakeHomeHintStore()
    private val services = FakeCustomServicesRepository(enabled = true)
    private val source = HintHomeCardSource(status, store, services)

    @Test
    fun everyReasonIsAHintInFeedOrder() = runTest {
        status.widgetPlaced = false
        status.notifications = false
        services.enabled.value = false

        val hints = source.observe().first().map { (it as HomeCard.Hint).hint }

        assertEquals(listOf(HomeHint.WIDGETS, HomeHint.NOTIFICATIONS, HomeHint.SERVICES), hints)
        assertEquals(hints.map { it.kind }, hints.map { it.kind }.sortedBy(HomeCardKind::ordinal))
    }

    @Test
    fun nothingToNudgeMeansNoCards() = runTest {
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun aClosedHintStaysClosedAndTheOptInRemovesItsOwn() = runTest {
        status.notifications = false
        services.enabled.value = false
        store.dismiss(HomeHint.NOTIFICATIONS)

        assertEquals(listOf(HomeCard.Hint(HomeHint.SERVICES)), source.observe().first())

        services.enabled.value = true
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun revalidationReReadsTheDeviceState() = runTest {
        status.widgetPlaced = false
        assertEquals(1, source.observe().first().size)

        status.widgetPlaced = true
        assertEquals(AppResult.Success(Unit), source.refresh())
        assertTrue(source.observe().first().isEmpty())
    }
}
