package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `without the opt-in the flow has four steps and ends on services`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        val walked = mutableListOf(fixture.viewModel.state.value.step)
        repeat(3) { fixture.viewModel.next(); walked += fixture.viewModel.state.value.step }

        assertEquals(
            listOf(OnboardingStep.COMPACT_WIDGET, OnboardingStep.FULL_WIDGET, OnboardingStep.QR_WIDGET, OnboardingStep.SERVICES),
            walked
        )
        assertTrue(fixture.viewModel.state.value.isLastStep)
        assertFalse(fixture.onboarding.observeCompleted().first())

        fixture.viewModel.next()
        advanceUntilIdle()

        assertTrue(fixture.onboarding.observeCompleted().first())
        assertTrue(fixture.viewModel.state.value.finished)
    }

    @Test
    fun `the opt-in adds the notifications step and its loss returns to services`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()
            repeat(3) { fixture.viewModel.next() }
            assertEquals(4, fixture.viewModel.state.value.steps.size)

            fixture.viewModel.setServicesEnabled(true)
            advanceUntilIdle()
            assertEquals(5, fixture.viewModel.state.value.steps.size)
            assertFalse(fixture.viewModel.state.value.isLastStep)

            fixture.viewModel.next()
            assertEquals(OnboardingStep.NOTIFICATIONS, fixture.viewModel.state.value.step)
            assertTrue(fixture.viewModel.state.value.isLastStep)

            fixture.services.enabled.value = false
            advanceUntilIdle()

            assertEquals(OnboardingStep.SERVICES, fixture.viewModel.state.value.step)
            assertEquals(4, fixture.viewModel.state.value.steps.size)
        }

    @Test
    fun `back walks the steps in reverse and stops at the first one`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()
        repeat(2) { fixture.viewModel.next() }
        assertEquals(OnboardingStep.QR_WIDGET, fixture.viewModel.state.value.step)

        fixture.viewModel.back()
        assertEquals(OnboardingStep.FULL_WIDGET, fixture.viewModel.state.value.step)
        fixture.viewModel.back()
        fixture.viewModel.back()
        assertEquals(OnboardingStep.COMPACT_WIDGET, fixture.viewModel.state.value.step)
        assertEquals(0, fixture.viewModel.state.value.stepIndex)
    }

    @Test
    fun `the current step is restored from the saved state`() = runTest(mainDispatcherRule.dispatcher) {
        val handle = SavedStateHandle()
        createFixture(savedStateHandle = handle).viewModel.next()
        advanceUntilIdle()

        val restored = createFixture(savedStateHandle = handle).viewModel

        assertEquals(OnboardingStep.FULL_WIDGET, restored.state.value.step)
    }

    @Test
    fun `skipping completes the flow from any step`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.skip()
        advanceUntilIdle()

        assertTrue(fixture.onboarding.observeCompleted().first())
        assertTrue(fixture.viewModel.state.value.finished)
    }

    @Test
    fun `the opt-in is reported while it runs and once it settles`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.setServicesEnabled(true)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.state.value.servicesEnabled)
        assertFalse(fixture.viewModel.state.value.servicesBusy)

        fixture.viewModel.setServicesEnabled(false)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.state.value.servicesEnabled)
        assertEquals(listOf(true, false), fixture.services.requests)
    }

    @Test
    fun `a failed opt-in stays off and explains itself`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        fixture.services.failing = true
        advanceUntilIdle()

        fixture.viewModel.setServicesEnabled(true)
        advanceUntilIdle()

        assertFalse(fixture.viewModel.state.value.servicesEnabled)
        assertFalse(fixture.viewModel.state.value.servicesBusy)
        assertTrue(fixture.viewModel.events.first() is OnboardingEvent.ShowError)
    }

    @Test
    fun `every widget option writes through and the preview follows`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()
        val before = checkNotNull(fixture.viewModel.state.value.appearance)

        WidgetOption.entries.forEach { option ->
            fixture.viewModel.setOption(option, !option.isEnabled(before))
        }
        advanceUntilIdle()

        val after = checkNotNull(fixture.viewModel.state.value.appearance)
        WidgetOption.entries.forEach { option ->
            assertEquals(option.name, !option.isEnabled(before), option.isEnabled(after))
        }
    }

    @Test
    fun `text size writes through per schedule widget and the QR step has none`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.setTextSize(WidgetKind.SINGLE_LESSON, WidgetTextSize.EXTRA_LARGE)
        fixture.viewModel.setTextSize(WidgetKind.QR, WidgetTextSize.LARGE)
        advanceUntilIdle()

        val appearance = checkNotNull(fixture.viewModel.state.value.appearance)
        assertEquals(WidgetTextSize.EXTRA_LARGE, WidgetKind.SINGLE_LESSON.textSize(appearance))
        assertEquals(WidgetTextSize.NORMAL, WidgetKind.DAY_SCHEDULE.textSize(appearance))
        assertEquals(null, WidgetKind.QR.textSize(appearance))

        fixture.viewModel.setTextSize(WidgetKind.DAY_SCHEDULE, WidgetTextSize.LARGE)
        advanceUntilIdle()
        assertEquals(WidgetTextSize.LARGE, checkNotNull(fixture.viewModel.state.value.appearance).schedule.full.textSize)
    }

    @Test
    fun `the stored spoiler image is read once and a save marks it for the preview`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            assertEquals(null, fixture.viewModel.state.value.customSpoiler)
            advanceUntilIdle()
            assertEquals(false, fixture.viewModel.state.value.customSpoiler)
            assertEquals(0, fixture.viewModel.state.value.spoilerRevision)

            fixture.viewModel.saveSpoilerImage("content://test/image")
            fixture.viewModel.saveSpoilerImage("content://test/duplicate")
            fixture.viewModel.resetSpoilerImage()
            runCurrent()
            assertTrue(fixture.viewModel.state.value.spoilerBusy)
            assertEquals(listOf("content://test/image"), fixture.spoiler.saved)

            fixture.spoiler.result.complete(true)
            advanceUntilIdle()

            val state = fixture.viewModel.state.value
            assertEquals(true, state.customSpoiler)
            assertFalse(state.spoilerBusy)
            assertEquals(1, state.spoilerRevision)
            assertEquals(0, fixture.spoiler.resets)
        }

    @Test
    fun `a failed image keeps the previous one and explains itself`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(spoiler = FakeCustomSpoilerRepository(hasImage = true))
        advanceUntilIdle()

        fixture.viewModel.saveSpoilerImage("content://test/broken")
        fixture.spoiler.result.complete(false)
        advanceUntilIdle()

        val state = fixture.viewModel.state.value
        assertEquals(true, state.customSpoiler)
        assertFalse(state.spoilerBusy)
        assertEquals(0, state.spoilerRevision)
        assertEquals(OnboardingEvent.SpoilerImageFailed, fixture.viewModel.events.first())
    }

    @Test
    fun `a reset returns to the default image`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(spoiler = FakeCustomSpoilerRepository(hasImage = true))
        advanceUntilIdle()
        assertEquals(true, fixture.viewModel.state.value.customSpoiler)

        fixture.viewModel.resetSpoilerImage()
        fixture.spoiler.result.complete(true)
        advanceUntilIdle()

        assertEquals(false, fixture.viewModel.state.value.customSpoiler)
        assertEquals(1, fixture.spoiler.resets)
        assertEquals(1, fixture.viewModel.state.value.spoilerRevision)
    }

    @Test
    fun `each widget step offers its own options`() {
        assertEquals(listOf(WidgetOption.COMPACT_NEXT_LESSON_EARLY, WidgetOption.COMPACT_HIDE_TEACHER), WidgetKind.SINGLE_LESSON.options)
        assertEquals(
            listOf(WidgetOption.FULL_HIDE_TEACHER, WidgetOption.FULL_HIDE_PAST_LESSONS, WidgetOption.FULL_SHOW_TOMORROW),
            WidgetKind.DAY_SCHEDULE.options
        )
        assertEquals(listOf(WidgetOption.QR_DYNAMIC_COLORS, WidgetOption.QR_SPOILER), WidgetKind.QR.options)
    }

    @Test
    fun `a pinned widget is asked for once and then marked`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        advanceUntilIdle()

        fixture.viewModel.pinWidget(WidgetKind.QR)
        assertEquals(OnboardingEvent.RequestPinWidget(WidgetKind.QR), fixture.viewModel.events.first())

        fixture.viewModel.onWidgetPinned(WidgetKind.QR)

        assertEquals(setOf(WidgetKind.QR), fixture.viewModel.state.value.pinnedWidgets)
    }

    @Test
    fun `a denied permission leads to the system settings on the second tap`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            fixture.viewModel.requestNotifications()
            assertEquals(OnboardingEvent.RequestNotificationPermission, fixture.viewModel.events.first())
            assertTrue(fixture.viewModel.state.value.notificationsAsked)

            fixture.viewModel.onNotificationPermission(granted = false)
            fixture.viewModel.requestNotifications()

            assertEquals(OnboardingEvent.OpenNotificationSettings, fixture.viewModel.events.first())
        }

    @Test
    fun `a granted permission opens the system page where channels live`() =
        runTest(mainDispatcherRule.dispatcher) {
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

    /** Writes wait for [result], so a test sees the busy frame before the answer. */
    private class FakeCustomSpoilerRepository(private val hasImage: Boolean = false) : CustomSpoilerRepository {
        val result = CompletableDeferred<Boolean>()
        val saved = mutableListOf<String>()
        var resets = 0

        override suspend fun hasImage(): Boolean = hasImage

        override suspend fun saveImage(sourceUri: String): Boolean {
            saved += sourceUri
            return result.await()
        }

        override suspend fun resetImage(): Boolean {
            resets++
            return result.await()
        }
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        private val completed = MutableStateFlow(false)

        override fun observeCompleted(): Flow<Boolean> = completed

        override suspend fun complete() {
            completed.value = true
        }

        override suspend fun reset() {
            completed.value = false
        }
    }

    private class FakeCustomServicesRepository : CustomServicesRepository {
        val enabled = MutableStateFlow(false)
        val requests = mutableListOf<Boolean>()
        var failing = false

        override fun observeEnabled(): Flow<Boolean> = enabled

        override suspend fun isEnabled(): Boolean = enabled.value

        override suspend fun setEnabled(enabled: Boolean) {
            requests += enabled
            if (failing) error("Opt-in is unavailable in this fixture")
            this.enabled.value = enabled
        }
    }

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
