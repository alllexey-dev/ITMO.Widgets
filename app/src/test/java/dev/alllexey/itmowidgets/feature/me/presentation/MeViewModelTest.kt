package dev.alllexey.itmowidgets.feature.me.presentation

import dev.alllexey.itmowidgets.core.debug.DebugRefreshTokenController
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.me.domain.CustomServicesRepository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val timeController = FakeTimeOverrideController()
    private val scoreController = FakeScoreOverrideController()
    private val lessonController = FakeLessonTemplateController()
    private val refreshTokenController = FakeRefreshTokenController()
    private val customServicesRepository = FakeCustomServicesRepository()

    private fun createViewModel(): MeViewModel = MeViewModel(
        timeProvider = FixedTimeProvider,
        timeOverrideController = timeController,
        sportScoreOverrideController = scoreController,
        sportLessonTemplateController = lessonController,
        refreshTokenController = refreshTokenController,
        customServicesRepository = customServicesRepository
    )

    @Test
    fun `publishes debug values through state`() {
        val viewModel = createViewModel()
        val date = LocalDate.of(2026, 2, 1)

        viewModel.setDateOverride(date)
        viewModel.setScoreOverride(attendances = 120, bonus = 10)
        viewModel.setLessonTemplatesEnabled(true)

        assertEquals(
            MeUiState.Content(
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
            (viewModel.uiState.value as MeUiState.Content).customServicesEnabled
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
            (viewModel.uiState.value as MeUiState.Content).customServicesEnabled
        )
    }

    @Test
    fun `emits recreation event after a change`() =
        runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val event = async { viewModel.events.first() }

        viewModel.clearScoreOverride()

        assertEquals(MeEvent.RecreateActivity, event.await())
    }

    @Test
    fun `replaces refresh token without exposing it in state`() =
        runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val event = async { viewModel.events.first() }

        viewModel.replaceRefreshToken("  secret-refresh-token  ")
        advanceUntilIdle()

        assertEquals("secret-refresh-token", refreshTokenController.lastToken)
        assertEquals(MeEvent.RefreshTokenUpdated, event.await())
        assertEquals(
            true,
            (viewModel.uiState.value as MeUiState.Content).refreshTokenConfigured
        )
    }

    private object FixedTimeProvider : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 24)

        override fun now(): OffsetDateTime = today()
            .atStartOfDay()
            .atZone(zoneId)
            .toOffsetDateTime()
    }

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

    private class FakeLessonTemplateController : SportLessonTemplateController {
        private var enabled = false

        override fun isEnabled(): Boolean = enabled

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class FakeCustomServicesRepository : CustomServicesRepository {
        val enabled = MutableStateFlow(false)

        override fun observeEnabled(): Flow<Boolean> = enabled

        override suspend fun setEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }
    }

    private class FakeRefreshTokenController : DebugRefreshTokenController {
        var lastToken: String? = null

        override fun hasRefreshToken(): Boolean = lastToken != null

        override suspend fun replaceRefreshToken(
            refreshToken: String
        ): AppResult<Unit> {
            lastToken = refreshToken.trim()
            return AppResult.Success(Unit)
        }
    }
}
