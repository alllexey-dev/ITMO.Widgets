package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.platform.IosPlatformCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeCardCapabilitiesTest {

    private val marks = FakeRenderer(setOf(HomeCardKind.MARKS))
    private val hints = FakeRenderer(setOf(HomeCardKind.HINT_WIDGETS, HomeCardKind.HINT_NOTIFICATIONS))
    private val mixed = FakeRenderer(setOf(HomeCardKind.MARKS, HomeCardKind.SPORT))

    @Test
    fun withoutMarkTrackingThePlatformDropsTheNewMarksCard() {
        val offered = listOf(marks, hints, mixed).offeredBy(IosPlatformCapabilities.copy(marks = false))

        assertEquals(2, offered.size)
        assertSame<HomeCardRenderer>(hints, offered[0], "a renderer of offered kinds stays itself")
        assertEquals(setOf(HomeCardKind.SPORT), offered[1].kinds)
        assertTrue(offered.none { HomeCardKind.MARKS in it.kinds })
    }

    @Test
    fun marksNeedTheRecordbookTabTheCardOpens() {
        val withoutRecordbook = IosPlatformCapabilities.copy(marks = true, recordbook = false)
        val withRecordbook = withoutRecordbook.copy(recordbook = true)

        assertTrue(listOf(marks).offeredBy(withoutRecordbook).isEmpty())
        assertEquals(listOf<HomeCardRenderer>(marks), listOf(marks).offeredBy(withRecordbook))
    }

    @Test
    fun everyKindIsOnIosSinceMarkTrackingShipped() {
        HomeCardKind.entries.forEach { kind ->
            assertTrue(IosPlatformCapabilities.offers(kind), kind.name)
        }
        val renderers = listOf<HomeCardRenderer>(marks, hints, mixed)
        assertEquals(renderers, renderers.offeredBy(IosPlatformCapabilities))
    }

    private class FakeRenderer(override val kinds: Set<HomeCardKind>) : HomeCardRenderer {
        @Composable
        override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) = Unit
    }
}
