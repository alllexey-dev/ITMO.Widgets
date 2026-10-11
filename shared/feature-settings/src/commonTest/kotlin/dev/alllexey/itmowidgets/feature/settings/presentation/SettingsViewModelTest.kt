package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Cross-page state of [SettingsViewModel]: loading, page arguments, row dispatch, background work and privacy timing. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun localReadinessWaitsForPersistedValuesButNeverWaitsForPrivacyRefresh() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.PRIVACY, local = LocalSettings(customServicesEnabled = true), localInitiallyAvailable = false)
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.loaded)
            fixture.repository.publishLocalSettings()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.loaded)
            assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
            advanceUntilIdle()
        }

    @Test
    fun eachDetailRestoresItsArgumentAndOnlyPrivacyRequestsBackendSettings() =
        runTest(main.dispatcher) {
            SettingsPage.entries.forEach { page ->
                val fixture = createFixture(
                    page = page,
                    local = LocalSettings(customServicesEnabled = true)
                )
                advanceUntilIdle()
                assertEquals(page, fixture.viewModel.uiState.value.page)
                assertTrue(fixture.viewModel.uiState.value.sections.isNotEmpty())
                assertEquals(
                    if (page == SettingsPage.PRIVACY) 1 else 0,
                    fixture.repository.refreshSharingCount
                )
            }
        }

    @Test
    fun missingOrObsoletePageArgumentSafelyOpensTheCatalogue() {
        assertEquals(SettingsPage.ROOT, SettingsPage.fromArgument(null))
        assertEquals(SettingsPage.ROOT, SettingsPage.fromArgument("obsolete"))
        assertEquals(SettingsPage.QR_WIDGET, SettingsPage.fromArgument("QR_WIDGET"))
    }

    @Test
    fun doesNotPublishDefaultsBeforeStoredLocalSettingsArrive() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.FULL_SCHEDULE_WIDGET,
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

            assertTrue(fixture.viewModel.toggle(SettingRowId.FULL_WIDGET_HIDE_TEACHER).checked)
            assertTrue(fixture.viewModel.toggle(SettingRowId.FULL_WIDGET_HIDE_PAST).checked)
            assertTrue(fixture.viewModel.toggle(SettingRowId.FULL_WIDGET_SHOW_TOMORROW).checked)
        }

    @Test
    fun preservesEveryExistingControlAcrossSettingsPagesWithoutForbiddenControls() =
        runTest(main.dispatcher) {
            val fixture = createFixture()

            advanceUntilIdle()

            // A decided BARS switch, so the recordbook page shows both of its rows.
            val details = SettingsPage.entries.filter { it != SettingsPage.ROOT }.map { page ->
                createFixture(page = page, local = LocalSettings(barsMarksEnabled = false)).viewModel
            }
            advanceUntilIdle()
            val items = (listOf(fixture.viewModel) + details)
                .flatMap { it.allItems() }
                .filterNot { it is SettingItem.Navigation }
            assertTrue(details.all { it.uiState.value.sections.all { section -> section.items.isNotEmpty() } })
            assertEquals(
                setOf(
                    SettingRowId.CUSTOM_SERVICES,
                    SettingRowId.NOTIFICATIONS,
                    SettingRowId.SCHEDULE_SHARING,
                    SettingRowId.SPORT_SHARING,
                    SettingRowId.FRIENDS_SHARING,
                    SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                    SettingRowId.COMPACT_WIDGET_HIDE_TEACHER,
                    SettingRowId.COMPACT_WIDGET_TEXT_SIZE,
                    SettingRowId.FULL_WIDGET_HIDE_TEACHER,
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
                    SettingRowId.SCHEDULE_CHANGES,
                    SettingRowId.HOME_CARD_SCHEDULE,
                    SettingRowId.HOME_CARD_SCHEDULE_CHANGES,
                    SettingRowId.HOME_CARD_MARKS,
                    SettingRowId.HOME_CARD_SPORT,
                    SettingRowId.HOME_CARD_FRIENDS,
                    SettingRowId.MYITMO_MARKS,
                    SettingRowId.BARS_MARKS,
                    SettingRowId.SHEET_MARKS,
                    SettingRowId.CALENDAR_SYNC,
                    SettingRowId.ICS_EXPORT,
                    SettingRowId.REFRESH_WIDGETS,
                    SettingRowId.RESTART_ONBOARDING,
                    SettingRowId.DIAGNOSTICS,
                    SettingRowId.VERSION,
                    SettingRowId.DELETE_ACCOUNT,
                    SettingRowId.PRIVACY_POLICY,
                    SettingRowId.ACCENT_COLOR,
                    SettingRowId.THEME_PALETTE,
                    SettingRowId.THEME_CONTRAST,
                    SettingRowId.DARK_BLACK,
                    SettingRowId.WIDGETS_FOLLOW_THEME
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

            fixture.viewModel.onAction(SettingRowId.NOTIFICATIONS)
            fixture.viewModel.onAction(SettingRowId.QR_CUSTOM_IMAGE)
            fixture.viewModel.onAction(SettingRowId.QR_RESET_IMAGE)
            fixture.viewModel.onAction(SettingRowId.REFRESH_WIDGETS)

            assertEquals(
                listOf(
                    SettingsEvent.OpenNotificationSettings,
                    SettingsEvent.ChooseCustomSpoiler,
                    SettingsEvent.ResetCustomSpoiler,
                    SettingsEvent.WidgetsRefreshStarted
                ),
                fixture.viewModel.events.take(4).toList()
            )
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun theBackgroundWorkRowLeavesWhenTheSystemSavesTheChoiceAfterTheScreenReturns() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()
            val keys = { fixture.viewModel.uiState.value.sections.single().items.map { it.id } }

            fixture.viewModel.onBackgroundWorkChanged()
            runCurrent()
            assertEquals(listOf(SettingRowId.MYITMO_MARKS, SettingRowId.SHEET_MARKS, SettingRowId.BACKGROUND_WORK), keys())

            fixture.backgroundWork.unrestricted = true
            advanceTimeBy(999)
            runCurrent()
            assertEquals(listOf(SettingRowId.MYITMO_MARKS, SettingRowId.SHEET_MARKS, SettingRowId.BACKGROUND_WORK), keys())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(SettingRowId.MYITMO_MARKS, SettingRowId.SHEET_MARKS), keys())
        }

    @Test
    fun turningABackgroundCheckOnOffersTheHintOnceWhileRestricted() =
        runTest(main.dispatcher) {
            val switches = listOf(
                SettingsPage.SCHEDULE to SettingRowId.SCHEDULE_CHANGES,
                SettingsPage.RECORDBOOK to SettingRowId.MYITMO_MARKS,
                SettingsPage.RECORDBOOK to SettingRowId.BARS_MARKS
            )
            for ((page, id) in switches) {
                val fixture = createFixture(
                    page = page,
                    local = LocalSettings(barsMarksEnabled = false),
                    backgroundWork = FakeBackgroundWorkAccess(unrestricted = false)
                )
                val events = recordEvents(fixture)
                fixture.viewModel.onNotificationPermissionChanged(granted = true)
                fixture.viewModel.onBackgroundWorkChanged()
                advanceUntilIdle()

                fixture.viewModel.onToggleChanged(id, false)
                advanceUntilIdle()
                assertEquals(emptyList<SettingsEvent>(), events, id.key)

                fixture.viewModel.onToggleChanged(id, true)
                advanceUntilIdle()
                assertEquals(listOf(SettingsEvent.ShowBackgroundWorkHint), events, id.key)
                assertEquals(1, fixture.repository.hintShownCalls, id.key)

                fixture.viewModel.onToggleChanged(id, true)
                advanceUntilIdle()
                assertEquals(listOf(SettingsEvent.ShowBackgroundWorkHint), events, id.key)
                assertEquals(1, fixture.repository.hintShownCalls, id.key)
            }
        }

    @Test
    fun anUnrestrictedAppGetsNoHintAndMissingNotificationsAreAskedForFirst() =
        runTest(main.dispatcher) {
            val unrestricted = createFixture(page = SettingsPage.SCHEDULE)
            val quiet = recordEvents(unrestricted)
            unrestricted.viewModel.onNotificationPermissionChanged(granted = true)
            unrestricted.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            unrestricted.viewModel.onToggleChanged(SettingRowId.SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(emptyList<SettingsEvent>(), quiet)
            assertEquals(0, unrestricted.repository.hintShownCalls)

            val restricted = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            val events = recordEvents(restricted)
            restricted.viewModel.onNotificationPermissionChanged(granted = false)
            restricted.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            restricted.viewModel.onToggleChanged(SettingRowId.SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission, SettingsEvent.ShowBackgroundWorkHint), events)
        }

    @Test
    fun fastPrivacyResponsesStayInTheBoundedLoadingStateFor300Ms() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings(SharingVisibility.FRIENDS, SharingVisibility.NOBODY))
        )
        runCurrent()
        assertEquals(1, fixture.repository.refreshSharingCount)
        assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
        assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingRowId.SPORT_SHARING).selectedOptionKey)
    }

    @Test
    fun slowPrivacyResponsesDoNotIncurAnExtraDelay() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings())
        )
        fixture.repository.refreshDelayMs = 800
        runCurrent()
        advanceTimeBy(799)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
    }

    @Test
    fun fastPrivacyErrorsAndRetriesUseTheSameMinimumLoadingDuration() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Error
        )
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
        advanceTimeBy(300)
        runCurrent()
        assertTrue(fixture.viewModel.action(SettingRowId.RETRY_PRIVACY).enabled)
        fixture.viewModel.onAction(SettingRowId.RETRY_PRIVACY)
        runCurrent()
        fixture.repository.sharing.value = SharingSettingsState.Content(SharingSettings(SharingVisibility.NOBODY, SharingVisibility.FRIENDS))
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SPORT_SHARING).selectedOptionKey)
    }

    @Test
    fun disablingServicesBypassesThePrivacyLoadingDelayImmediately() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings())
        )
        runCurrent()
        advanceTimeBy(50)
        fixture.repository.local.value = LocalSettings(customServicesEnabled = false)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.sections.isNotEmpty())
        assertEquals(null, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
        assertEquals(SharingSettingsState.Disabled, fixture.repository.sharing.value)
        advanceUntilIdle()
        assertEquals(null, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
    }

    @Test
    fun privacyDelayStartsWhenStoredSettingsArriveNotWhenViewModelIsCreated() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings()),
            localInitiallyAvailable = false
        )
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0, fixture.repository.refreshSharingCount)
        fixture.repository.publishLocalSettings()
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingRowId.SCHEDULE_SHARING).selectedOptionKey)
    }
}
