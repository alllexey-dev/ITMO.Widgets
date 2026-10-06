package dev.alllexey.itmowidgets.di.bridge

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeHomeCardSource
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.home.di.homeModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeBridgeTest {

    private val marks = FakeHomeCardSource(HomeCard.Marks(listOf("Тестовый предмет")))
    private val hints = FakeHomeCardSource(HomeCard.Hint(HomeHint.WIDGETS))
    private val sport = FakeHomeCardSource(HomeCard.Sport(null, emptyList()))

    @Test
    fun `an empty set still emits an empty feed`() = runTest {
        assertEquals(emptyList<HomeCard>(), CompositeHomeCardSource(emptyList()).observe().first())
    }

    @Test
    fun `the composite concatenates the cards of its parts`() = runTest {
        val composite = CompositeHomeCardSource(listOf(marks, hints))

        assertEquals(marks.cards.value + hints.cards.value, composite.observe().first())
    }

    @Test
    fun `a partial failure asks every part and returns the first failure`() = runTest {
        hints.refreshResult = AppResult.Failure(AppError.Network)
        sport.refreshResult = AppResult.Failure(AppError.Unauthorized)

        val result = CompositeHomeCardSource(listOf(marks, hints, sport)).refresh()

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(listOf(1, 1, 1), listOf(marks.refreshes, hints.refreshes, sport.refreshes))
    }

    @Test
    fun `a refresh without failures succeeds`() = runTest {
        assertEquals(AppResult.Success(Unit), CompositeHomeCardSource(listOf(marks, hints)).refresh())
    }

    @Test
    fun `dismiss and revalidate reach every part`() = runTest {
        val composite = CompositeHomeCardSource(listOf(marks, hints, sport))

        composite.dismiss(HomeCardKind.MARKS)
        composite.revalidate()

        assertEquals(
            List(3) { listOf(HomeCardKind.MARKS) },
            listOf(marks.dismissed, hints.dismissed, sport.dismissed)
        )
        assertEquals(listOf(1, 1, 1), listOf(marks.revalidations, hints.revalidations, sport.revalidations))
    }

    /** The hint cards read the services opt-in, which `settingsDataModule` constructs since KM-11e. */
    @Test
    fun `the home module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(authDataModule, settingsDataModule, homeModule))
    }
}
