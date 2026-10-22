package dev.alllexey.itmowidgets.feature.debug.presentation

import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeSportLessonTemplateController
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugToolsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val timeController = FakeTimeOverrideController()
    private val scoreController = FakeScoreOverrideController()
    private val lessonController = FakeSportLessonTemplateController()
    private val tokenStore = FakeSessionTokenStore(signedIn = false)
    private val sessions = RecordingSessions(tokenStore)
    private val customServicesRepository = FakeCustomServicesRepository()
    private val scheduleChangeTracking = FakeScheduleChangeTracking()
    private val barsSessionProbe = FakeBarsSessionProbe()
    private val markTracking = FakeMarkTracking()

    private fun createViewModel(): DebugToolsViewModel = DebugToolsViewModel(
        timeProvider = FixedTimeProvider,
        timeOverrideController = timeController,
        sportScoreOverrideController = scoreController,
        sportLessonTemplateController = lessonController,
        sessions = sessions,
        tokenStore = tokenStore,
        customServicesRepository = customServicesRepository,
        scheduleChangeTracking = scheduleChangeTracking,
        barsSessionProbe = barsSessionProbe,
        markTracking = markTracking
    )

    @Test
    fun `checking schedule changes asks for one background check`() {
        val viewModel = createViewModel()

        viewModel.checkScheduleChanges()

        assertEquals(1, scheduleChangeTracking.checkNowCalls)
    }

    @Test
    fun `checking marks asks for one background check`() {
        val viewModel = createViewModel()

        viewModel.checkMarks()

        assertEquals(1, markTracking.checkNowCalls)
    }

    @Test
    fun `probing the BARS session starts one probe`() {
        val viewModel = createViewModel()

        viewModel.probeBarsSession()

        assertEquals(1, barsSessionProbe.starts)
    }

    @Test
    fun `publishes debug values through state`() {
        val viewModel = createViewModel()
        val date = LocalDate(2026, 2, 1)

        viewModel.setDateOverride(date)
        viewModel.setScoreOverride(attendances = 120, bonus = 10)
        viewModel.setLessonTemplatesEnabled(true)

        assertEquals(
            DebugToolsUiState.Content(
                effectiveDate = FixedTimeProvider.today(),
                dateOverride = date,
                scoreOverride = SportScoreOverride(120, 10),
                lessonTemplatesEnabled = true,
                refreshTokenConfigured = false,
                refreshTokenUpdateInProgress = false,
                customServicesEnabled = false
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun `enables custom services through the repository`() =
        runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.setCustomServicesEnabled(true)
        advanceUntilIdle()

        assertEquals(true, customServicesRepository.enabled.value)
        assertEquals(
            true,
            (viewModel.uiState.value as DebugToolsUiState.Content).customServicesEnabled
        )
    }

    @Test
    fun `keeps custom services state across debug changes`() =
        runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.setCustomServicesEnabled(true)
        advanceUntilIdle()

        viewModel.setLessonTemplatesEnabled(true)

        assertEquals(
            true,
            (viewModel.uiState.value as DebugToolsUiState.Content).customServicesEnabled
        )
    }

    @Test
    fun `emits recreation event after a change`() =
        runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val event = async { viewModel.events.first() }

        viewModel.clearScoreOverride()

        assertEquals(DebugToolsEvent.RecreateActivity, event.await())
    }

    @Test
    fun `a recreation requested while nobody listens reaches the next collector`() =
        runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.setLessonTemplatesEnabled(true)
        advanceUntilIdle()

        assertEquals(DebugToolsEvent.RecreateActivity, viewModel.events.first())
    }

    @Test
    fun `signs in with the pasted refresh token through the session without exposing it in state`() =
        runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val event = async { viewModel.events.first() }

        viewModel.replaceRefreshToken("  secret-refresh-token  ")
        advanceUntilIdle()

        assertEquals("  secret-refresh-token  ", sessions.lastToken)
        assertEquals(DebugToolsEvent.RefreshTokenUpdated, event.await())
        assertEquals(
            true,
            (viewModel.uiState.value as DebugToolsUiState.Content).refreshTokenConfigured
        )
    }

    private object FixedTimeProvider : AcademicTimeProvider by FixedAcademicTime(java.time.LocalDate.of(2026, 7, 24))

    private class FakeTimeOverrideController : AcademicTimeOverrideController {
        private var value: LocalDate? = null

        override fun getOverrideDate(): LocalDate? = value

        override fun setOverrideDate(date: LocalDate?) {
            value = date
        }
    }

    private class FakeScoreOverrideController : SportScoreOverrideController {
        private var value: SportScoreOverride? = null

        override fun getOverride(): SportScoreOverride? = value

        override fun setOverride(value: SportScoreOverride?) {
            this.value = value
        }
    }

    private class FakeBarsSessionProbe : BarsSessionProbe {
        var starts = 0

        override fun start() {
            starts++
        }
    }

    /** A session whose refresh-token sign-in records the token and stores it as the repository would. */
    private class RecordingSessions(
        private val tokenStore: FakeSessionTokenStore
    ) : SessionRepository by FakeSessionRepository(SessionState.SignedOut) {
        var lastToken: String? = null

        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> {
            lastToken = refreshToken
            tokenStore.replaceWithRefreshToken(refreshToken.trim())
            return AppResult.Success(Unit)
        }
    }
}
