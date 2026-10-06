package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The sport page built and handled by [SportPageProvider], through the ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class SportPageProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `maps local toggles to storage semantics and refreshes only widgets`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.SPORT,
                local = LocalSettings(
                    sport = SportDisplaySettings(
                        hideTeacherSelector = true,
                        hideTimeSelector = false
                    )
                )
            )
            advanceUntilIdle()

            assertFalse(
                fixture.viewModel.toggle(SettingRowId.SPORT_TEACHER_FILTER).checked
            )
            assertTrue(
                fixture.viewModel.toggle(SettingRowId.SPORT_TIME_FILTER).checked
            )

            fixture.viewModel.onToggleChanged(
                SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                false
            )
            fixture.viewModel.onToggleChanged(SettingRowId.COMPACT_WIDGET_HIDE_TEACHER, true)
            fixture.viewModel.onToggleChanged(SettingRowId.FULL_WIDGET_HIDE_PAST, true)
            fixture.viewModel.onToggleChanged(SettingRowId.FULL_WIDGET_SHOW_TOMORROW, true)
            fixture.viewModel.onToggleChanged(SettingRowId.QR_DYNAMIC_COLORS, false)
            fixture.viewModel.onToggleChanged(SettingRowId.QR_SPOILER, false)
            fixture.viewModel.onToggleChanged(SettingRowId.SPORT_TEACHER_FILTER, true)
            fixture.viewModel.onToggleChanged(SettingRowId.SPORT_TIME_FILTER, false)
            advanceUntilIdle()

            assertEquals(listOf(false), fixture.repository.nextLessonEarlyRequests)
            assertEquals(listOf(true), fixture.repository.widgetTeacherHiddenRequests)
            assertEquals(listOf(true), fixture.repository.pastLessonsHiddenRequests)
            assertEquals(listOf(true), fixture.repository.tomorrowScheduleRequests)
            assertEquals(listOf(false), fixture.repository.qrDynamicColorsRequests)
            assertEquals(listOf(false), fixture.repository.qrSpoilerRequests)
            assertEquals(listOf(false), fixture.repository.teacherSelectorHiddenRequests)
            assertEquals(listOf(true), fixture.repository.timeSelectorHiddenRequests)
            assertEquals(6, fixture.widgetRefresher.refreshCount)
        }
}
