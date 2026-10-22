package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Cross-page state of [SettingsViewModel]: loading, page arguments and row dispatch. «Сервер ITMO.Widgets» and
 * «Уведомления» have view models of their own (`ConnectionViewModelTest`, `NotificationSettingsViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun localReadinessWaitsForPersistedValues() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SECTIONS, localInitiallyAvailable = false)
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.loaded)
            fixture.repository.publishLocalSettings()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.loaded)
            assertTrue(fixture.viewModel.uiState.value.sections.isNotEmpty())
            advanceUntilIdle()
        }

    @Test
    fun eachPageRestoresItsArgumentAndNoneRequestsBackendSettings() =
        runTest(main.dispatcher) {
            BuiltPages.forEach { page ->
                val fixture = createFixture(
                    page = page,
                    local = LocalSettings(customServicesEnabled = true)
                )
                advanceUntilIdle()
                assertEquals(page, fixture.viewModel.uiState.value.page)
                assertTrue(fixture.viewModel.uiState.value.sections.isNotEmpty())
                assertEquals(0, fixture.repository.refreshSharingCount)
            }
        }

    @Test
    fun missingOrObsoletePageArgumentSafelyOpensTheCatalogue() {
        assertEquals(SettingsPage.ROOT, SettingsPage.fromArgument(null))
        assertEquals(SettingsPage.ROOT, SettingsPage.fromArgument("obsolete"))
        assertEquals(SettingsPage.WIDGETS, SettingsPage.fromArgument("WIDGETS"))
        // The per-widget pages the gallery replaced, which old links still name.
        listOf("QR_WIDGET", "COMPACT_SCHEDULE_WIDGET", "FULL_SCHEDULE_WIDGET", "SCHEDULE_WIDGETS").forEach {
            assertEquals(SettingsPage.WIDGETS, SettingsPage.fromArgument(it))
        }
        // The mark checks moved to «Уведомления»; an old link to the recordbook page opens it.
        assertEquals(SettingsPage.NOTIFICATIONS, SettingsPage.fromArgument("RECORDBOOK"))
        assertEquals(SettingsPage.SERVICES, SettingsPage.fromArgument("SERVICES"))
        // The pages merged away keep opening where their settings went.
        assertEquals(SettingsPage.SERVICES, SettingsPage.fromArgument("PRIVACY"))
        listOf("HOME", "SCHEDULE", "SPORT").forEach { assertEquals(SettingsPage.SECTIONS, SettingsPage.fromArgument(it)) }
        assertEquals(SettingsPage.ABOUT, SettingsPage.fromArgument("MAINTENANCE"))
    }

    @Test
    fun doesNotPublishDefaultsBeforeStoredLocalSettingsArrive() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.WIDGETS,
                local = LocalSettings(
                    customServicesEnabled = true,
                    scheduleWidget = ScheduleWidgetSettings(
                        compact = CompactScheduleWidgetSettings(hideTeacher = true),
                        full = FullScheduleWidgetSettings(hideTeacher = true, hidePastLessons = true, showTomorrowWhenTodayIsOver = true)
                    ),
                    sport = SportDisplaySettings(
                        hideTeacherSelector = false,
                        hideTimeSelector = false
                    )
                ),
                localInitiallyAvailable = false
            )

            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())

            fixture.repository.publishLocalSettings()
            advanceUntilIdle()

            assertFalse(fixture.viewModel.toggle(SettingRowId.FULL_WIDGET_SHOW_TEACHER).checked)
            assertTrue(fixture.viewModel.toggle(SettingRowId.FULL_WIDGET_HIDE_PAST).checked)
            assertTrue(fixture.viewModel.toggle(SettingRowId.FULL_WIDGET_SHOW_TOMORROW).checked)
        }

    @Test
    fun preservesEveryExistingControlAcrossSettingsPagesWithoutForbiddenControls() =
        runTest(main.dispatcher) {
            val fixture = createFixture()

            advanceUntilIdle()

            val details = BuiltPages.filter { it != SettingsPage.ROOT }.map { page -> createFixture(page = page).viewModel }
            advanceUntilIdle()
            val items = (listOf(fixture.viewModel) + details)
                .flatMap { it.allItems() }
                .filterNot { it is SettingItem.Navigation }
            assertTrue(details.all { it.uiState.value.sections.all { section -> section.items.isNotEmpty() } })
            assertEquals(
                setOf(
                    SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                    SettingRowId.COMPACT_WIDGET_TEXT_SIZE,
                    SettingRowId.FULL_WIDGET_SHOW_TEACHER,
                    SettingRowId.FULL_WIDGET_HIDE_PAST,
                    SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                    SettingRowId.FULL_WIDGET_TEXT_SIZE,
                    SettingRowId.QR_DYNAMIC_COLORS,
                    SettingRowId.QR_SPOILER,
                    SettingRowId.QR_ANIMATION,
                    SettingRowId.QR_CUSTOM_IMAGE,
                    SettingRowId.QR_RESET_IMAGE,
                    SettingRowId.SPORT_TEACHER_FILTER,
                    SettingRowId.SPORT_TIME_FILTER,
                    SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
                    SettingRowId.CALENDAR_SYNC,
                    SettingRowId.ICS_EXPORT,
                    SettingRowId.REFRESH_WIDGETS,
                    SettingRowId.RESTART_ONBOARDING,
                    SettingRowId.DIAGNOSTICS,
                    SettingRowId.VERSION,
                    SettingRowId.PRIVACY_POLICY,
                    SettingRowId.HOME_PASS_VISIBLE,
                    SettingRowId.WIDGETS_FOLLOW_THEME,
                    SettingRowId.LICENSE_PLANS,
                    SettingRowId.LICENSE_MAPLIBRE,
                    SettingRowId.LICENSE_NOTO,
                    SettingRowId.BARS
                ),
                items.map(SettingItem::id).toSet()
            )

            val keys = items.map { it.id.key }
            assertTrue(keys.none { "smart" in it || "style" in it || "map" in it })
            assertTrue(keys.none { "name" in it || "group" in it || "isu" in it })
        }

    @Test
    fun actionsEmitNavigationEventsAndRefreshConfirmation() =
        runTest(main.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            fixture.viewModel.onAction(SettingRowId.QR_CUSTOM_IMAGE)
            fixture.viewModel.onAction(SettingRowId.QR_RESET_IMAGE)
            fixture.viewModel.onAction(SettingRowId.REFRESH_WIDGETS)

            assertEquals(
                listOf(
                    SettingsEvent.ChooseCustomSpoiler,
                    SettingsEvent.ResetCustomSpoiler,
                    SettingsEvent.WidgetsRefreshStarted
                ),
                fixture.viewModel.events.take(3).toList()
            )
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    private companion object {
        /** The pages a [SettingsViewModel] builds; the others are screens of their own. */
        val BuiltPages = SettingsPage.entries - setOf(SettingsPage.SERVICES, SettingsPage.NOTIFICATIONS, SettingsPage.APPEARANCE)
    }
}
