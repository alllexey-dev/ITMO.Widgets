package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `local readiness waits for persisted values but never waits for privacy refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.PRIVACY, local = LocalSettings(customServicesEnabled = true), localInitiallyAvailable = false)
            runCurrent()
            assertFalse(fixture.viewModel.localSettingsLoaded.value)
            fixture.repository.publishLocalSettings()
            runCurrent()
            assertTrue(fixture.viewModel.localSettingsLoaded.value)
            assertTrue(fixture.viewModel.sections.value.isEmpty())
            advanceUntilIdle()
        }

    @Test
    fun `preview exists only on widget settings pages and uses stored values`() =
        runTest(mainDispatcherRule.dispatcher) {
            val local = LocalSettings(
                qrWidget = QrWidgetSettings(dynamicColors = false, animationType = QrAnimationType.NONE),
                scheduleWidget = ScheduleWidgetSettings(hideTeacher = true)
            )
            SettingsPage.entries.forEach { page ->
                val fixture = createFixture(page = page, local = local)
                advanceUntilIdle()
                assertEquals(
                    when (page) {
                        SettingsPage.QR_WIDGET -> WidgetPreviewSettings.Qr(local.qrWidget)
                        SettingsPage.SCHEDULE_WIDGETS -> WidgetPreviewSettings.Schedule(local.scheduleWidget)
                        else -> null
                    },
                    fixture.viewModel.previewSettings.value
                )
            }
        }

    @Test
    fun `preview waits for storage and follows persisted toggle and animation changes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.QR_WIDGET, localInitiallyAvailable = false)
            advanceUntilIdle()
            assertEquals(null, fixture.viewModel.previewSettings.value)
            fixture.repository.publishLocalSettings()
            advanceUntilIdle()
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_QR_DYNAMIC_COLORS, false)
            fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_QR_ANIMATION, QrAnimationType.FADE.name)
            advanceUntilIdle()
            assertEquals(
                WidgetPreviewSettings.Qr(QrWidgetSettings(dynamicColors = false, animationType = QrAnimationType.FADE)),
                fixture.viewModel.previewSettings.value
            )
        }

    @Test
    fun `root is a compact catalogue with no switches or arbitrary option summaries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(local = LocalSettings(customServicesEnabled = true))
            advanceUntilIdle()

            assertEquals(SettingsPage.ROOT, fixture.viewModel.page)
            assertEquals(3, fixture.viewModel.sections.value.size)
            assertEquals(8, fixture.viewModel.allItems().size)
            assertTrue(fixture.viewModel.allItems().none { it is SettingItem.Toggle })
            val navigation = fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>()
            assertEquals(
                SettingsPage.entries.filter { it != SettingsPage.ROOT },
                navigation.map { it.page }
            )
            assertTrue(navigation.all { it.description == null })
            assertTrue(navigation.filter { it.page != SettingsPage.SERVICES }.all { it.value == null })
            assertEquals(UiText.Resource(R.string.settings_services_enabled), navigation.first().value)
            val applicationSection = fixture.viewModel.sections.value.single {
                it.title == UiText.Resource(R.string.me_group_app)
            }
            assertEquals(
                listOf(SettingsPage.SCHEDULE, SettingsPage.SPORT, SettingsPage.MAINTENANCE),
                applicationSection.items.filterIsInstance<SettingItem.Navigation>().map { it.page }
            )
            assertEquals(0, fixture.repository.refreshSharingCount)
        }

    @Test
    fun `each detail restores its argument and only privacy requests backend settings`() =
        runTest(mainDispatcherRule.dispatcher) {
            SettingsPage.entries.forEach { page ->
                val fixture = createFixture(
                    page = page,
                    local = LocalSettings(customServicesEnabled = true)
                )
                advanceUntilIdle()
                assertEquals(page, fixture.viewModel.page)
                assertTrue(fixture.viewModel.sections.value.isNotEmpty())
                assertEquals(
                    if (page == SettingsPage.PRIVACY) 1 else 0,
                    fixture.repository.refreshSharingCount
                )
            }
        }

    @Test
    fun `missing or obsolete page argument safely opens the catalogue`() {
        assertEquals(SettingsPage.ROOT, SettingsPage.fromArgument(null))
        assertEquals(SettingsPage.ROOT, SettingsPage.fromArgument("obsolete"))
        assertEquals(SettingsPage.QR_WIDGET, SettingsPage.fromArgument("QR_WIDGET"))
    }

    @Test
    fun `disabled spoiler keeps its dependent controls visible but unavailable`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.QR_WIDGET,
                local = LocalSettings(qrWidget = QrWidgetSettings(spoilerEnabled = false))
            )
            fixture.viewModel.onCustomSpoilerChanged(configured = true)
            advanceUntilIdle()

            assertEquals(5, fixture.viewModel.allItems().size)
            assertFalse(fixture.viewModel.choice(SettingsViewModel.KEY_QR_ANIMATION).enabled)
            assertFalse(fixture.viewModel.action(SettingsViewModel.KEY_QR_CUSTOM_IMAGE).enabled)
            assertFalse(fixture.viewModel.action(SettingsViewModel.KEY_QR_RESET_IMAGE).enabled)
        }

    @Test
    fun `does not publish defaults before stored local settings arrive`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.SCHEDULE_WIDGETS,
                local = LocalSettings(
                    customServicesEnabled = true,
                    scheduleWidget = ScheduleWidgetSettings(
                        showNextLessonEarly = true,
                        hideTeacher = true,
                        hidePastLessons = true,
                        showTomorrowWhenTodayIsOver = true
                    ),
                    sport = SportDisplaySettings(
                        hideTeacherSelector = false,
                        hideTimeSelector = false
                    )
                ),
                localInitiallyAvailable = false
            )

            advanceUntilIdle()
            assertTrue(fixture.viewModel.sections.value.isEmpty())

            fixture.repository.publishLocalSettings()
            advanceUntilIdle()

            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_WIDGET_HIDE_TEACHER).checked)
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_WIDGET_HIDE_PAST).checked)
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_WIDGET_SHOW_TOMORROW).checked)
        }

    @Test
    fun `does not expose false privacy values while backend settings are loading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Loading
            )
            advanceUntilIdle()

            assertTrue(fixture.viewModel.sections.value.isEmpty())

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(scheduleSharing = true, sportSharing = true)
            )
            advanceUntilIdle()

            val schedule = fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING)
            val sport = fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_SHARING)
            assertTrue(schedule.stateKnown)
            assertTrue(schedule.checked)
            assertTrue(sport.stateKnown)
            assertTrue(sport.checked)
        }

    @Test
    fun `preserves every existing control across settings pages without forbidden controls`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()

            advanceUntilIdle()

            val details = SettingsPage.entries.filter { it != SettingsPage.ROOT }.map { page ->
                createFixture(page = page).viewModel
            }
            advanceUntilIdle()
            val items = (listOf(fixture.viewModel) + details)
                .flatMap { it.allItems() }
                .filterNot { it is SettingItem.Navigation }
            assertTrue(details.all { it.sections.value.all { section -> section.items.isNotEmpty() } })
            assertEquals(
                setOf(
                    SettingsViewModel.KEY_CUSTOM_SERVICES,
                    SettingsViewModel.KEY_NOTIFICATIONS,
                    SettingsViewModel.KEY_SCHEDULE_SHARING,
                    SettingsViewModel.KEY_SPORT_SHARING,
                    SettingsViewModel.KEY_WIDGET_NEXT_LESSON_EARLY,
                    SettingsViewModel.KEY_WIDGET_HIDE_TEACHER,
                    SettingsViewModel.KEY_WIDGET_HIDE_PAST,
                    SettingsViewModel.KEY_WIDGET_SHOW_TOMORROW,
                    SettingsViewModel.KEY_QR_DYNAMIC_COLORS,
                    SettingsViewModel.KEY_QR_SPOILER,
                    SettingsViewModel.KEY_QR_ANIMATION,
                    SettingsViewModel.KEY_QR_CUSTOM_IMAGE,
                    SettingsViewModel.KEY_QR_RESET_IMAGE,
                    SettingsViewModel.KEY_SPORT_TEACHER_FILTER,
                    SettingsViewModel.KEY_SPORT_TIME_FILTER,
                    SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN,
                    SettingsViewModel.KEY_REFRESH_WIDGETS,
                    SettingsViewModel.KEY_VERSION
                ),
                items.map(SettingItem::key).toSet()
            )

            val keys = items.map(SettingItem::key)
            assertTrue(keys.none { "smart" in it || "style" in it || "map" in it })
            assertTrue(keys.none { "name" in it || "group" in it || "isu" in it })
        }

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
                fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_TEACHER_FILTER).checked
            )
            assertTrue(
                fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_TIME_FILTER).checked
            )

            fixture.viewModel.onToggleChanged(
                SettingsViewModel.KEY_WIDGET_NEXT_LESSON_EARLY,
                false
            )
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_WIDGET_HIDE_TEACHER, true)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_WIDGET_HIDE_PAST, true)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_WIDGET_SHOW_TOMORROW, true)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_QR_DYNAMIC_COLORS, false)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_QR_SPOILER, false)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SPORT_TEACHER_FILTER, true)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SPORT_TIME_FILTER, false)
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

    @Test
    fun `custom services toggle is delegated without refreshing widgets`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SERVICES)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_CUSTOM_SERVICES, true)
            advanceUntilIdle()

            assertEquals(listOf(true), fixture.customServicesRepository.requests)
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `sharing controls are disabled when custom services are disabled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = false),
                sharing = SharingSettingsState.Disabled
            )

            advanceUntilIdle()

            val schedule = fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING)
            val sport = fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_SHARING)
            assertFalse(schedule.enabled)
            assertFalse(schedule.checked)
            assertFalse(sport.enabled)
            assertFalse(sport.checked)
            assertEquals(
                UiText.Resource(R.string.settings_privacy_services_required),
                fixture.viewModel.sections.value.single().footer
            )
            assertEquals(1, fixture.repository.disableSharingCount)
            assertEquals(0, fixture.repository.refreshSharingCount)
        }

    @Test
    fun `sharing content renders independently and locks while updating`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(
                    SharingSettings(scheduleSharing = true, sportSharing = false)
                )
            )
            advanceUntilIdle()

            assertTrue(
                fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).checked
            )
            assertTrue(
                fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled
            )
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_SHARING).checked)
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_SHARING).enabled)
            assertEquals(1, fixture.repository.refreshSharingCount)

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(scheduleSharing = true, sportSharing = false),
                updating = true
            )
            advanceUntilIdle()

            assertFalse(
                fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled
            )
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_SHARING).enabled)
        }

    @Test
    fun `sharing update failure emits the repository error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(
                    SharingSettings(scheduleSharing = true, sportSharing = true)
                )
            )
            fixture.repository.scheduleSharingResult = AppResult.Failure(AppError.Network)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_SHARING, false)
            advanceUntilIdle()

            assertEquals(listOf(false), fixture.repository.scheduleSharingRequests)
            assertEquals(
                SettingsEvent.ShowError(AppError.Network),
                fixture.viewModel.events.first()
            )
        }

    @Test
    fun `privacy error exposes retry action`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Error
            )
            advanceUntilIdle()

            assertFalse(
                fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled
            )
            assertEquals(
                UiText.Resource(R.string.settings_privacy_load_error),
                fixture.viewModel.sections.value.single().footer
            )
            fixture.viewModel.action(SettingsViewModel.KEY_RETRY_PRIVACY)

            fixture.viewModel.onAction(SettingsViewModel.KEY_RETRY_PRIVACY)
            advanceUntilIdle()

            assertEquals(2, fixture.repository.refreshSharingCount)
        }

    @Test
    fun `qr animation choice persists known option and refreshes widgets`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.QR_WIDGET,
                local = LocalSettings(
                    qrWidget = QrWidgetSettings(
                        spoilerEnabled = true,
                        animationType = QrAnimationType.FADE
                    )
                )
            )
            advanceUntilIdle()

            val choice = fixture.viewModel.choice(SettingsViewModel.KEY_QR_ANIMATION)
            assertEquals(QrAnimationType.FADE.name, choice.selectedOptionKey)
            assertEquals(QrAnimationType.entries.map { it.name }, choice.options.map { it.key })

            fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_QR_ANIMATION, "unknown")
            advanceUntilIdle()
            assertTrue(fixture.repository.qrAnimationRequests.isEmpty())
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onChoiceChanged(
                SettingsViewModel.KEY_QR_ANIMATION,
                QrAnimationType.NONE.name
            )
            advanceUntilIdle()

            assertEquals(listOf(QrAnimationType.NONE), fixture.repository.qrAnimationRequests)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `custom spoiler state controls actions and optional widget refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.QR_WIDGET)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.action(SettingsViewModel.KEY_QR_RESET_IMAGE).enabled)

            fixture.viewModel.onCustomSpoilerChanged(configured = true)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.action(SettingsViewModel.KEY_QR_RESET_IMAGE).enabled)
            assertEquals(
                UiText.Resource(R.string.settings_qr_custom_image_selected),
                fixture.viewModel.action(SettingsViewModel.KEY_QR_CUSTOM_IMAGE).value
            )
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onCustomSpoilerChanged(configured = false, refreshWidgets = true)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.action(SettingsViewModel.KEY_QR_RESET_IMAGE).enabled)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `actions emit navigation events and refresh confirmation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            fixture.viewModel.onAction(SettingsViewModel.KEY_NOTIFICATIONS)
            fixture.viewModel.onAction(SettingsViewModel.KEY_QR_CUSTOM_IMAGE)
            fixture.viewModel.onAction(SettingsViewModel.KEY_QR_RESET_IMAGE)
            fixture.viewModel.onAction(SettingsViewModel.KEY_REFRESH_WIDGETS)

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
    fun `exposes notification status and application version`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            assertEquals(
                UiText.Resource(R.string.settings_notifications_checking),
                fixture.viewModel.action(SettingsViewModel.KEY_NOTIFICATIONS).value
            )
            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()

            assertEquals(
                UiText.Resource(R.string.settings_notifications_allowed),
                fixture.viewModel.action(SettingsViewModel.KEY_NOTIFICATIONS).value
            )
            val maintenance = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()
            val version = maintenance.viewModel.allItems()
                .filterIsInstance<SettingItem.Info>()
                .single { it.key == SettingsViewModel.KEY_VERSION }
            assertEquals(UiText.Dynamic("2.1-test"), version.value)
        }

    @Test
    fun `image actions are disabled while saving without disabling animation`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(page = SettingsPage.QR_WIDGET)
        advanceUntilIdle()
        fixture.viewModel.onCustomSpoilerChanged(configured = true, busy = true)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.action(SettingsViewModel.KEY_QR_CUSTOM_IMAGE).enabled)
        assertFalse(fixture.viewModel.action(SettingsViewModel.KEY_QR_RESET_IMAGE).enabled)
        assertTrue(fixture.viewModel.choice(SettingsViewModel.KEY_QR_ANIMATION).enabled)
        fixture.viewModel.onCustomSpoilerChanged(configured = true, busy = false)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.action(SettingsViewModel.KEY_QR_CUSTOM_IMAGE).enabled)
        assertTrue(fixture.viewModel.action(SettingsViewModel.KEY_QR_RESET_IMAGE).enabled)
    }

    @Test
    fun `schedule auto sign display is local and toggleable with custom services disabled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            advanceUntilIdle()

            val section = fixture.viewModel.sections.value.single()
            val toggle = fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN)
            assertEquals(listOf(toggle), section.items)
            assertEquals(UiText.Resource(R.string.settings_group_schedule), fixture.viewModel.page.title)
            assertEquals(UiText.Resource(R.string.settings_schedule_sport_auto_sign_title), toggle.title)
            assertEquals(UiText.Resource(R.string.settings_schedule_sport_auto_sign_description), toggle.description)
            assertEquals(UiText.Resource(R.string.settings_schedule_footer), section.footer)
            assertTrue(toggle.enabled)
            assertTrue(toggle.stateKnown)
            assertFalse(toggle.checked)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN, true)
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertTrue(fixture.repository.local.value.showSportAutoSign)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN, false)
            advanceUntilIdle()
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertEquals(listOf(true, false), fixture.repository.scheduleSportAutoSignRequests)
            assertFalse(fixture.repository.local.value.customServicesEnabled)
            assertTrue(fixture.customServicesRepository.requests.isEmpty())
            assertEquals(0, fixture.repository.refreshSharingCount)
            assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            assertEquals(0, fixture.widgetRefresher.refreshCount)
            assertEquals(null, fixture.viewModel.previewSettings.value)
        }

    @Test
    fun `schedule auto sign display waits for and follows persisted values`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.SCHEDULE,
                local = LocalSettings(showSportAutoSign = true),
                localInitiallyAvailable = false
            )
            advanceUntilIdle()
            assertTrue(fixture.viewModel.sections.value.isEmpty())

            fixture.repository.publishLocalSettings()
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)

            fixture.repository.local.value = fixture.repository.local.value.copy(showSportAutoSign = false)
            advanceUntilIdle()
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertTrue(fixture.repository.scheduleSportAutoSignRequests.isEmpty())
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `fast privacy responses stay in the bounded loading state for 300 ms`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings(true, false))
        )
        runCurrent()
        assertEquals(1, fixture.repository.refreshSharingCount)
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).checked)
        assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_SHARING).checked)
    }

    @Test
    fun `slow privacy responses do not incur an extra delay`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings(true, true))
        )
        fixture.repository.refreshDelayMs = 800
        runCurrent()
        advanceTimeBy(799)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).stateKnown)
    }

    @Test
    fun `fast privacy errors and retries use the same minimum loading duration`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Error
        )
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(300)
        runCurrent()
        assertTrue(fixture.viewModel.action(SettingsViewModel.KEY_RETRY_PRIVACY).enabled)
        fixture.viewModel.onAction(SettingsViewModel.KEY_RETRY_PRIVACY)
        runCurrent()
        fixture.repository.sharing.value = SharingSettingsState.Content(SharingSettings(false, true))
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SPORT_SHARING).checked)
    }

    @Test
    fun `disabling services bypasses the privacy loading delay immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings(true, true))
        )
        runCurrent()
        advanceTimeBy(50)
        fixture.repository.local.value = LocalSettings(customServicesEnabled = false)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isNotEmpty())
        assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).stateKnown)
        assertEquals(SharingSettingsState.Disabled, fixture.repository.sharing.value)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).stateKnown)
    }

    @Test
    fun `privacy delay starts when stored settings arrive not when view model is created`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings(true, true)),
            localInitiallyAvailable = false
        )
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0, fixture.repository.refreshSharingCount)
        fixture.repository.publishLocalSettings()
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SHARING).stateKnown)
    }

    private fun createFixture(
        local: LocalSettings = LocalSettings(),
        sharing: SharingSettingsState = SharingSettingsState.Disabled,
        localInitiallyAvailable: Boolean = true,
        page: SettingsPage = SettingsPage.ROOT
    ): Fixture {
        val repository = FakeSettingsRepository(local, sharing, localInitiallyAvailable)
        val customServicesRepository = FakeCustomServicesRepository { enabled ->
            repository.local.value = repository.local.value.copy(
                customServicesEnabled = enabled
            )
        }
        val refresher = FakeWidgetRefreshRequester()
        val viewModel = SettingsViewModel(
            repository = repository,
            customServicesRepository = customServicesRepository,
            widgetRefreshRequester = refresher,
            appVersion = AppVersion("2.1-test"),
            savedStateHandle = SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name))
        )
        return Fixture(viewModel, repository, customServicesRepository, refresher)
    }

    private fun SettingsViewModel.allItems(): List<SettingItem> =
        sections.value.flatMap(SettingSection::items)

    private fun SettingsViewModel.toggle(key: String): SettingItem.Toggle =
        allItems().filterIsInstance<SettingItem.Toggle>().single { it.key == key }

    private fun SettingsViewModel.choice(key: String): SettingItem.Choice =
        allItems().filterIsInstance<SettingItem.Choice>().single { it.key == key }

    private fun SettingsViewModel.action(key: String): SettingItem.Action =
        allItems().filterIsInstance<SettingItem.Action>().single { it.key == key }

    private data class Fixture(
        val viewModel: SettingsViewModel,
        val repository: FakeSettingsRepository,
        val customServicesRepository: FakeCustomServicesRepository,
        val widgetRefresher: FakeWidgetRefreshRequester
    )

    private class FakeSettingsRepository(
        initialLocal: LocalSettings,
        initialSharing: SharingSettingsState,
        localInitiallyAvailable: Boolean
    ) : SettingsRepository {

        val local = MutableStateFlow(initialLocal)
        private val localAvailable = MutableStateFlow(localInitiallyAvailable)
        val sharing = MutableStateFlow(initialSharing)
        var refreshSharingCount = 0
        var disableSharingCount = 0
        var scheduleSharingResult: AppResult<Unit> = AppResult.Success(Unit)
        var sportSharingResult: AppResult<Unit> = AppResult.Success(Unit)
        val scheduleSharingRequests = mutableListOf<Boolean>()
        val sportSharingRequests = mutableListOf<Boolean>()
        val nextLessonEarlyRequests = mutableListOf<Boolean>()
        val widgetTeacherHiddenRequests = mutableListOf<Boolean>()
        val pastLessonsHiddenRequests = mutableListOf<Boolean>()
        val tomorrowScheduleRequests = mutableListOf<Boolean>()
        val qrDynamicColorsRequests = mutableListOf<Boolean>()
        val qrSpoilerRequests = mutableListOf<Boolean>()
        val qrAnimationRequests = mutableListOf<QrAnimationType>()
        val teacherSelectorHiddenRequests = mutableListOf<Boolean>()
        val timeSelectorHiddenRequests = mutableListOf<Boolean>()
        val scheduleSportAutoSignRequests = mutableListOf<Boolean>()

        override fun observeLocalSettings(): Flow<LocalSettings> =
            combine(localAvailable, local) { available, settings ->
                settings.takeIf { available }
            }.filterNotNull()

        fun publishLocalSettings() {
            localAvailable.value = true
        }

        override fun observeSharingSettings(): Flow<SharingSettingsState> = sharing

        var refreshDelayMs = 0L

        override suspend fun refreshSharingSettings() {
            refreshSharingCount += 1
            delay(refreshDelayMs)
        }

        override fun disableSharingSettings() {
            disableSharingCount += 1
            sharing.value = SharingSettingsState.Disabled
        }

        override suspend fun setScheduleSharing(enabled: Boolean): AppResult<Unit> {
            scheduleSharingRequests += enabled
            return scheduleSharingResult
        }

        override suspend fun setSportSharing(enabled: Boolean): AppResult<Unit> {
            sportSharingRequests += enabled
            return sportSharingResult
        }

        override suspend fun setNextLessonEarlyEnabled(enabled: Boolean) {
            nextLessonEarlyRequests += enabled
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(showNextLessonEarly = enabled)
            )
        }

        override suspend fun setWidgetTeacherHidden(hidden: Boolean) {
            widgetTeacherHiddenRequests += hidden
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(hideTeacher = hidden)
            )
        }

        override suspend fun setPastLessonsHidden(hidden: Boolean) {
            pastLessonsHiddenRequests += hidden
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(hidePastLessons = hidden)
            )
        }

        override suspend fun setTomorrowScheduleEnabled(enabled: Boolean) {
            tomorrowScheduleRequests += enabled
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(
                    showTomorrowWhenTodayIsOver = enabled
                )
            )
        }

        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) {
            qrDynamicColorsRequests += enabled
            local.value = local.value.copy(
                qrWidget = local.value.qrWidget.copy(dynamicColors = enabled)
            )
        }

        override suspend fun setQrSpoilerEnabled(enabled: Boolean) {
            qrSpoilerRequests += enabled
            local.value = local.value.copy(
                qrWidget = local.value.qrWidget.copy(spoilerEnabled = enabled)
            )
        }

        override suspend fun setQrAnimationType(type: QrAnimationType) {
            qrAnimationRequests += type
            local.value = local.value.copy(
                qrWidget = local.value.qrWidget.copy(animationType = type)
            )
        }

        override suspend fun setTeacherSelectorHidden(hidden: Boolean) {
            teacherSelectorHiddenRequests += hidden
            local.value = local.value.copy(
                sport = local.value.sport.copy(hideTeacherSelector = hidden)
            )
        }

        override suspend fun setTimeSelectorHidden(hidden: Boolean) {
            timeSelectorHiddenRequests += hidden
            local.value = local.value.copy(
                sport = local.value.sport.copy(hideTimeSelector = hidden)
            )
        }

        override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
            scheduleSportAutoSignRequests += enabled
            local.value = local.value.copy(showSportAutoSign = enabled)
        }
    }

    private class FakeCustomServicesRepository(
        private val onSet: (Boolean) -> Unit
    ) : CustomServicesRepository {

        private val enabled = MutableStateFlow(false)
        val requests = mutableListOf<Boolean>()

        override fun observeEnabled(): Flow<Boolean> = enabled

        override suspend fun isEnabled(): Boolean = enabled.value

        override suspend fun setEnabled(enabled: Boolean) {
            requests += enabled
            this.enabled.value = enabled
            onSet(enabled)
        }
    }

    private class FakeWidgetRefreshRequester : WidgetRefreshRequester {
        var refreshCount = 0

        override fun refreshAll() {
            refreshCount += 1
        }
    }
}
