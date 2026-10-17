package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.data.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.feature.sport.data.unreachable
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Sections never reach Backend in the demo or without the opt-in; the demo shows its own section. */
class SportSectionRepositoryGateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val time = FixedAcademicTime()
    private val sportApi = unreachable<SportApi>()

    private fun repository(demo: Boolean, optedIn: Boolean): SportSectionRepositoryImpl {
        val mode = FakeDemoMode(active = demo)
        return SportSectionRepositoryImpl(sportApi, FakeBackendGate(optedIn, mode), mode, time, mainDispatcherRule.appDispatchers)
    }

    @Test
    fun `the demo shows its section and refuses changes without a request`() = runTest {
        val sections = repository(demo = true, optedIn = true)

        sections.refreshSections()

        assertEquals(LoadState.Content(DemoSport.sections(time)), sections.observeSections().first())
        val refused = AppResult.Failure(AppError.DemoUnavailable)
        assertEquals(refused, sections.create(1))
        assertEquals(refused, sections.setSkipped(1, Instant.DISTANT_FUTURE, skipped = true))
        assertEquals(refused, sections.stop(1))
    }

    @Test
    fun `without the opt-in sections are disabled and nothing is requested`() = runTest {
        val sections = repository(demo = false, optedIn = false)

        sections.refreshSections()

        assertEquals(LoadState.Disabled, sections.observeSections().first())
        val disabled = AppResult.Failure(AppError.CustomServicesDisabled)
        assertEquals(disabled, sections.slots(listOf(1)))
        assertEquals(disabled, sections.create(1))
        assertEquals(disabled, sections.stop(1))
    }
}
