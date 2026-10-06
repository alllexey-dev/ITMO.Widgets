package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class OnboardingViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun withoutTheOptInTheFlowHasFourStepsAndEndsOnServices() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        val walked = mutableListOf(fixture.viewModel.uiState.value.step)
        repeat(3) { fixture.viewModel.next(); walked += fixture.viewModel.uiState.value.step }

        assertEquals(
            listOf(OnboardingStep.COMPACT_WIDGET, OnboardingStep.FULL_WIDGET, OnboardingStep.QR_WIDGET, OnboardingStep.SERVICES),
            walked
        )
        assertTrue(fixture.viewModel.uiState.value.isLastStep)
        assertFalse(fixture.onboarding.observeCompleted().first())

        fixture.viewModel.next()
        advanceUntilIdle()

        assertTrue(fixture.onboarding.observeCompleted().first())
        assertTrue(fixture.viewModel.uiState.value.finished)
    }

    @Test
    fun theOptInAddsTheNotificationsStepAndItsLossReturnsToServices() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()
            repeat(3) { fixture.viewModel.next() }
            assertEquals(4, fixture.viewModel.uiState.value.steps.size)

            fixture.viewModel.setServicesEnabled(true)
            advanceUntilIdle()
            assertEquals(5, fixture.viewModel.uiState.value.steps.size)
            assertFalse(fixture.viewModel.uiState.value.isLastStep)

            fixture.viewModel.next()
            assertEquals(OnboardingStep.NOTIFICATIONS, fixture.viewModel.uiState.value.step)
            assertTrue(fixture.viewModel.uiState.value.isLastStep)

            fixture.services.enabled.value = false
            advanceUntilIdle()

            assertEquals(OnboardingStep.SERVICES, fixture.viewModel.uiState.value.step)
            assertEquals(4, fixture.viewModel.uiState.value.steps.size)
        }

    @Test
    fun backWalksTheStepsInReverseAndStopsAtTheFirstOne() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()
        repeat(2) { fixture.viewModel.next() }
        assertEquals(OnboardingStep.QR_WIDGET, fixture.viewModel.uiState.value.step)

        fixture.viewModel.back()
        assertEquals(OnboardingStep.FULL_WIDGET, fixture.viewModel.uiState.value.step)
        fixture.viewModel.back()
        fixture.viewModel.back()
        assertEquals(OnboardingStep.COMPACT_WIDGET, fixture.viewModel.uiState.value.step)
        assertEquals(0, fixture.viewModel.uiState.value.stepIndex)
    }

    @Test
    fun theCurrentStepIsRestoredFromTheSavedState() = runTest(main.dispatcher) {
        val handle = SavedStateHandle()
        createFixture(savedStateHandle = handle).viewModel.next()
        advanceUntilIdle()

        val restored = createFixture(savedStateHandle = handle).viewModel

        assertEquals(OnboardingStep.FULL_WIDGET, restored.uiState.value.step)
    }

    @Test
    fun skippingCompletesTheFlowFromAnyStep() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.skip()
        advanceUntilIdle()

        assertTrue(fixture.onboarding.observeCompleted().first())
        assertTrue(fixture.viewModel.uiState.value.finished)
    }

    @Test
    fun theOptInIsReportedWhileItRunsAndOnceItSettles() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.setServicesEnabled(true)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.servicesEnabled)
        assertFalse(fixture.viewModel.uiState.value.servicesBusy)

        fixture.viewModel.setServicesEnabled(false)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.servicesEnabled)
        assertEquals(listOf(true, false), fixture.services.requests)
    }

    @Test
    fun aFailedOptInStaysOffAndExplainsItself() = runTest(main.dispatcher) {
        val fixture = createFixture()
        fixture.services.failing = true
        advanceUntilIdle()

        fixture.viewModel.setServicesEnabled(true)
        advanceUntilIdle()

        assertFalse(fixture.viewModel.uiState.value.servicesEnabled)
        assertFalse(fixture.viewModel.uiState.value.servicesBusy)
        assertTrue(fixture.viewModel.events.first() is OnboardingEvent.ShowError)
    }

    @Test
    fun everyWidgetOptionWritesThroughAndThePreviewFollows() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()
        val before = checkNotNull(fixture.viewModel.uiState.value.appearance)

        WidgetOption.entries.forEach { option ->
            fixture.viewModel.setOption(option, !option.isEnabled(before))
        }
        advanceUntilIdle()

        val after = checkNotNull(fixture.viewModel.uiState.value.appearance)
        WidgetOption.entries.forEach { option ->
            assertEquals(!option.isEnabled(before), option.isEnabled(after), option.name)
        }
    }

    @Test
    fun textSizeWritesThroughPerScheduleWidgetAndTheQRStepHasNone() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.setTextSize(WidgetKind.SINGLE_LESSON, WidgetTextSize.EXTRA_LARGE)
        fixture.viewModel.setTextSize(WidgetKind.QR, WidgetTextSize.LARGE)
        advanceUntilIdle()

        val appearance = checkNotNull(fixture.viewModel.uiState.value.appearance)
        assertEquals(WidgetTextSize.EXTRA_LARGE, WidgetKind.SINGLE_LESSON.textSize(appearance))
        assertEquals(WidgetTextSize.NORMAL, WidgetKind.DAY_SCHEDULE.textSize(appearance))
        assertEquals(null, WidgetKind.QR.textSize(appearance))

        fixture.viewModel.setTextSize(WidgetKind.DAY_SCHEDULE, WidgetTextSize.LARGE)
        advanceUntilIdle()
        assertEquals(WidgetTextSize.LARGE, checkNotNull(fixture.viewModel.uiState.value.appearance).schedule.full.textSize)
    }

    @Test
    fun theStoredSpoilerImageIsReadOnceAndASaveMarksItForThePreview() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            assertEquals(null, fixture.viewModel.uiState.value.customSpoiler)
            advanceUntilIdle()
            assertEquals(false, fixture.viewModel.uiState.value.customSpoiler)
            assertEquals(0, fixture.viewModel.uiState.value.spoilerRevision)

            fixture.viewModel.saveSpoilerImage("content://test/image")
            fixture.viewModel.saveSpoilerImage("content://test/duplicate")
            fixture.viewModel.resetSpoilerImage()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.spoilerBusy)
            assertEquals(listOf("content://test/image"), fixture.spoiler.saved)

            fixture.spoiler.result.complete(true)
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value
            assertEquals(true, state.customSpoiler)
            assertFalse(state.spoilerBusy)
            assertEquals(1, state.spoilerRevision)
            assertEquals(0, fixture.spoiler.resets)
        }

    @Test
    fun aFailedImageKeepsThePreviousOneAndExplainsItself() = runTest(main.dispatcher) {
        val fixture = createFixture(spoiler = FakeCustomSpoilerRepository(hasImage = true))
        advanceUntilIdle()

        fixture.viewModel.saveSpoilerImage("content://test/broken")
        fixture.spoiler.result.complete(false)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(true, state.customSpoiler)
        assertFalse(state.spoilerBusy)
        assertEquals(0, state.spoilerRevision)
        assertEquals(OnboardingEvent.SpoilerImageFailed, fixture.viewModel.events.first())
    }

    @Test
    fun aResetReturnsToTheDefaultImage() = runTest(main.dispatcher) {
        val fixture = createFixture(spoiler = FakeCustomSpoilerRepository(hasImage = true))
        advanceUntilIdle()
        assertEquals(true, fixture.viewModel.uiState.value.customSpoiler)

        fixture.viewModel.resetSpoilerImage()
        fixture.spoiler.result.complete(true)
        advanceUntilIdle()

        assertEquals(false, fixture.viewModel.uiState.value.customSpoiler)
        assertEquals(1, fixture.spoiler.resets)
        assertEquals(1, fixture.viewModel.uiState.value.spoilerRevision)
    }

    @Test
    fun eachWidgetStepOffersItsOwnOptions() {
        assertEquals(listOf(WidgetOption.COMPACT_NEXT_LESSON_EARLY, WidgetOption.COMPACT_HIDE_TEACHER), WidgetKind.SINGLE_LESSON.options)
        assertEquals(
            listOf(WidgetOption.FULL_HIDE_TEACHER, WidgetOption.FULL_HIDE_PAST_LESSONS, WidgetOption.FULL_SHOW_TOMORROW),
            WidgetKind.DAY_SCHEDULE.options
        )
        assertEquals(listOf(WidgetOption.QR_DYNAMIC_COLORS, WidgetOption.QR_SPOILER), WidgetKind.QR.options)
    }

    @Test
    fun aPinnedWidgetIsAskedForOnceAndThenMarked() = runTest(main.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.pinWidget(WidgetKind.QR)
        assertEquals(OnboardingEvent.RequestPinWidget(WidgetKind.QR), fixture.viewModel.events.first())

        fixture.viewModel.onWidgetPinned(WidgetKind.QR)

        assertEquals(setOf(WidgetKind.QR), fixture.viewModel.uiState.value.pinnedWidgets)
    }

    @Test
    fun aDeniedPermissionLeadsToTheSystemSettingsOnTheSecondTap() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            fixture.viewModel.requestNotifications()
            assertEquals(OnboardingEvent.RequestNotificationPermission, fixture.viewModel.events.first())
            assertTrue(fixture.viewModel.uiState.value.notificationsAsked)

            fixture.viewModel.onNotificationPermission(granted = false)
            fixture.viewModel.requestNotifications()

            assertEquals(OnboardingEvent.OpenNotificationSettings, fixture.viewModel.events.first())
        }

    @Test
    fun aGrantedPermissionOpensTheSystemPageWhereChannelsLive() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            fixture.viewModel.onNotificationPermission(granted = true)
            fixture.viewModel.requestNotifications()

            assertEquals(OnboardingEvent.OpenNotificationSettings, fixture.viewModel.events.first())
        }

    private fun createFixture(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        spoiler: FakeCustomSpoilerRepository = FakeCustomSpoilerRepository()
    ): Fixture {
        val onboarding = FakeOnboardingRepository()
        val services = FakeCustomServicesRepository()
        val appearance = FakeWidgetAppearanceRepository()
        return Fixture(
            onboarding = onboarding,
            services = services,
            appearance = appearance,
            spoiler = spoiler,
            viewModel = OnboardingViewModel(
                onboardingRepository = onboarding,
                customServicesRepository = services,
                widgetAppearanceRepository = appearance,
                customSpoilerRepository = spoiler,
                savedStateHandle = savedStateHandle
            )
        )
    }

    private data class Fixture(
        val onboarding: FakeOnboardingRepository,
        val services: FakeCustomServicesRepository,
        val appearance: FakeWidgetAppearanceRepository,
        val spoiler: FakeCustomSpoilerRepository,
        val viewModel: OnboardingViewModel
    )

    private class FakeWidgetAppearanceRepository : WidgetAppearanceRepository {
        val state = MutableStateFlow(WidgetAppearance())

        override fun observeAppearance(): Flow<WidgetAppearance> = state

        override suspend fun setCompactNextLessonEarly(enabled: Boolean) =
            schedule { copy(compact = compact.copy(showNextLessonEarly = enabled)) }

        override suspend fun setCompactTeacherHidden(hidden: Boolean) =
            schedule { copy(compact = compact.copy(hideTeacher = hidden)) }

        override suspend fun setFullTeacherHidden(hidden: Boolean) =
            schedule { copy(full = full.copy(hideTeacher = hidden)) }

        override suspend fun setFullPastLessonsHidden(hidden: Boolean) =
            schedule { copy(full = full.copy(hidePastLessons = hidden)) }

        override suspend fun setFullTomorrowEnabled(enabled: Boolean) =
            schedule { copy(full = full.copy(showTomorrowWhenTodayIsOver = enabled)) }

        override suspend fun setCompactTextSize(size: WidgetTextSize) =
            schedule { copy(compact = compact.copy(textSize = size)) }

        override suspend fun setFullTextSize(size: WidgetTextSize) =
            schedule { copy(full = full.copy(textSize = size)) }

        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = qr { copy(dynamicColors = enabled) }

        override suspend fun setQrSpoilerEnabled(enabled: Boolean) = qr { copy(spoilerEnabled = enabled) }

        private fun schedule(block: ScheduleWidgetSettings.() -> ScheduleWidgetSettings) {
            state.value = state.value.copy(schedule = state.value.schedule.block())
        }

        private fun qr(block: QrWidgetSettings.() -> QrWidgetSettings) {
            state.value = state.value.copy(qr = state.value.qr.block())
        }
    }
}
