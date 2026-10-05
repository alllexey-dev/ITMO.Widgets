package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The home cards page built and handled by [HomePageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomePageProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `home page lists one switch per card and hides a card without touching widgets`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.HOME, local = LocalSettings(hiddenHomeCards = setOf(HomeCardKind.SPORT)))
            advanceUntilIdle()

            val section = fixture.viewModel.uiState.value.sections.single()
            assertEquals(UiText.Resource(R.string.settings_group_home), fixture.viewModel.uiState.value.page.title)
            assertEquals(null, section.footer)
            assertEquals(
                listOf(
                    SettingRowId.HOME_CARD_SCHEDULE, SettingRowId.HOME_CARD_SCHEDULE_CHANGES,
                    SettingRowId.HOME_CARD_MARKS, SettingRowId.HOME_CARD_SPORT,
                    SettingRowId.HOME_CARD_FRIENDS
                ),
                section.items.map { it.id }
            )
            assertTrue(fixture.viewModel.toggle(SettingRowId.HOME_CARD_SCHEDULE).checked)
            assertFalse(fixture.viewModel.toggle(SettingRowId.HOME_CARD_SPORT).checked)

            fixture.viewModel.onToggleChanged(SettingRowId.HOME_CARD_SCHEDULE, false)
            advanceUntilIdle()
            assertEquals(listOf(HomeCardKind.SCHEDULE to false), fixture.repository.homeCardRequests)
            assertFalse(fixture.viewModel.toggle(SettingRowId.HOME_CARD_SCHEDULE).checked)
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `home page offers the schedule changes card second and hides it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.HOME)
            advanceUntilIdle()

            val row = fixture.viewModel.uiState.value.sections.single().items[1] as SettingItem.Toggle
            assertEquals(SettingRowId.HOME_CARD_SCHEDULE_CHANGES, row.id)
            assertEquals(UiText.Resource(R.string.settings_home_card_schedule_changes_title), row.title)
            assertTrue(row.checked)

            fixture.viewModel.onToggleChanged(SettingRowId.HOME_CARD_SCHEDULE_CHANGES, false)
            advanceUntilIdle()
            assertEquals(listOf(HomeCardKind.SCHEDULE_CHANGES to false), fixture.repository.homeCardRequests)
            assertFalse(fixture.viewModel.toggle(SettingRowId.HOME_CARD_SCHEDULE_CHANGES).checked)
            assertTrue(fixture.tracking.setCalls.isEmpty())
        }

    @Test
    fun `home page offers the marks card third and hides it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.HOME)
            advanceUntilIdle()

            val row = fixture.viewModel.uiState.value.sections.single().items[2] as SettingItem.Toggle
            assertEquals(SettingRowId.HOME_CARD_MARKS, row.id)
            assertEquals(UiText.Resource(R.string.settings_home_card_marks_title), row.title)
            assertTrue(row.checked)

            fixture.viewModel.onToggleChanged(SettingRowId.HOME_CARD_MARKS, false)
            advanceUntilIdle()
            assertEquals(listOf(HomeCardKind.MARKS to false), fixture.repository.homeCardRequests)
            assertFalse(fixture.viewModel.toggle(SettingRowId.HOME_CARD_MARKS).checked)
            assertTrue(fixture.markTracking.myItmoCalls.isEmpty())
        }
}
