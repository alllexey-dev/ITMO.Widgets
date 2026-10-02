package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `friends privacy starts all and edits only its own audience`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings()))
        advanceUntilIdle()
        assertEquals("ALL", fixture.viewModel.choice(SettingsViewModel.KEY_FRIENDS_SHARING).selectedOptionKey)
        fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_FRIENDS_SHARING, "NOBODY")
        advanceUntilIdle()
        assertEquals(listOf(SharingVisibility.NOBODY), fixture.repository.friendsSharingRequests)
        assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
        assertTrue(fixture.repository.sportSharingRequests.isEmpty())
        assertEquals("NOBODY", fixture.viewModel.choice(SettingsViewModel.KEY_FRIENDS_SHARING).selectedOptionKey)
    }

    @Test
    fun `widget pages expose only their own controls and independent teacher values`() = runTest(mainDispatcherRule.dispatcher) {
        val local = LocalSettings(scheduleWidget = ScheduleWidgetSettings(
            compact = CompactScheduleWidgetSettings(hideTeacher = true),
            full = FullScheduleWidgetSettings(hideTeacher = false)
        ))
        val compact = createFixture(page = SettingsPage.COMPACT_SCHEDULE_WIDGET, local = local)
        val full = createFixture(page = SettingsPage.FULL_SCHEDULE_WIDGET, local = local)
        advanceUntilIdle()
        assertEquals(
            setOf(
                SettingsViewModel.KEY_COMPACT_WIDGET_NEXT_LESSON_EARLY,
                SettingsViewModel.KEY_COMPACT_WIDGET_HIDE_TEACHER,
                SettingsViewModel.KEY_COMPACT_WIDGET_TEXT_SIZE
            ),
            compact.viewModel.allItems().map { it.key }.toSet()
        )
        assertEquals(
            setOf(
                SettingsViewModel.KEY_FULL_WIDGET_HIDE_TEACHER,
                SettingsViewModel.KEY_FULL_WIDGET_HIDE_PAST,
                SettingsViewModel.KEY_FULL_WIDGET_SHOW_TOMORROW,
                SettingsViewModel.KEY_FULL_WIDGET_TEXT_SIZE
            ),
            full.viewModel.allItems().map { it.key }.toSet()
        )
        assertTrue(compact.viewModel.toggle(SettingsViewModel.KEY_COMPACT_WIDGET_HIDE_TEACHER).checked)
        assertFalse(full.viewModel.toggle(SettingsViewModel.KEY_FULL_WIDGET_HIDE_TEACHER).checked)
        full.viewModel.onToggleChanged(SettingsViewModel.KEY_FULL_WIDGET_HIDE_TEACHER, true)
        advanceUntilIdle()
        assertEquals(listOf(true), full.repository.fullWidgetTeacherHiddenRequests)
        assertTrue(full.repository.widgetTeacherHiddenRequests.isEmpty())
        assertEquals(1, full.widgetRefresher.refreshCount)
        assertEquals(SettingsPage.COMPACT_SCHEDULE_WIDGET, SettingsPage.fromArgument("SCHEDULE_WIDGETS"))
    }

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
                scheduleWidget = ScheduleWidgetSettings(compact = CompactScheduleWidgetSettings(hideTeacher = true), full = FullScheduleWidgetSettings(hideTeacher = true))
            )
            SettingsPage.entries.forEach { page ->
                val fixture = createFixture(page = page, local = local)
                advanceUntilIdle()
                assertEquals(
                    when (page) {
                        SettingsPage.QR_WIDGET -> WidgetPreviewSettings.Qr(local.qrWidget)
                        SettingsPage.COMPACT_SCHEDULE_WIDGET -> WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.COMPACT)
                        SettingsPage.FULL_SCHEDULE_WIDGET -> WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.FULL)
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
            assertEquals(11, fixture.viewModel.allItems().size)
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
                listOf(SettingsPage.HOME, SettingsPage.SCHEDULE, SettingsPage.RECORDBOOK, SettingsPage.SPORT, SettingsPage.MAINTENANCE),
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
            assertTrue(fixture.viewModel.sections.value.isEmpty())

            fixture.repository.publishLocalSettings()
            advanceUntilIdle()

            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_FULL_WIDGET_HIDE_TEACHER).checked)
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_FULL_WIDGET_HIDE_PAST).checked)
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_FULL_WIDGET_SHOW_TOMORROW).checked)
        }

    @Test
    fun `does not expose default privacy choices while backend settings are loading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Loading
            )
            advanceUntilIdle()

            assertTrue(fixture.viewModel.sections.value.isEmpty())

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.FRIENDS)
            )
            advanceUntilIdle()

            val schedule = fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING)
            val sport = fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING)
            assertEquals(SharingVisibility.FRIENDS.name, schedule.selectedOptionKey)
            assertEquals(SharingVisibility.FRIENDS.name, sport.selectedOptionKey)
            assertTrue(schedule.enabled)
            assertTrue(sport.enabled)
        }

    @Test
    fun `preserves every existing control across settings pages without forbidden controls`() =
        runTest(mainDispatcherRule.dispatcher) {
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
            assertTrue(details.all { it.sections.value.all { section -> section.items.isNotEmpty() } })
            assertEquals(
                setOf(
                    SettingsViewModel.KEY_CUSTOM_SERVICES,
                    SettingsViewModel.KEY_NOTIFICATIONS,
                    SettingsViewModel.KEY_SCHEDULE_SHARING,
                    SettingsViewModel.KEY_SPORT_SHARING,
                    SettingsViewModel.KEY_FRIENDS_SHARING,
                    SettingsViewModel.KEY_COMPACT_WIDGET_NEXT_LESSON_EARLY,
                    SettingsViewModel.KEY_COMPACT_WIDGET_HIDE_TEACHER,
                    SettingsViewModel.KEY_COMPACT_WIDGET_TEXT_SIZE,
                    SettingsViewModel.KEY_FULL_WIDGET_HIDE_TEACHER,
                    SettingsViewModel.KEY_FULL_WIDGET_HIDE_PAST,
                    SettingsViewModel.KEY_FULL_WIDGET_SHOW_TOMORROW,
                    SettingsViewModel.KEY_FULL_WIDGET_TEXT_SIZE,
                    SettingsViewModel.KEY_QR_DYNAMIC_COLORS,
                    SettingsViewModel.KEY_QR_SPOILER,
                    SettingsViewModel.KEY_QR_ANIMATION,
                    SettingsViewModel.KEY_QR_CUSTOM_IMAGE,
                    SettingsViewModel.KEY_QR_RESET_IMAGE,
                    SettingsViewModel.KEY_SPORT_TEACHER_FILTER,
                    SettingsViewModel.KEY_SPORT_TIME_FILTER,
                    SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN,
                    SettingsViewModel.KEY_SCHEDULE_CHANGES,
                    SettingsViewModel.KEY_HOME_CARD_SCHEDULE,
                    SettingsViewModel.KEY_HOME_CARD_SCHEDULE_CHANGES,
                    SettingsViewModel.KEY_HOME_CARD_MARKS,
                    SettingsViewModel.KEY_HOME_CARD_SPORT,
                    SettingsViewModel.KEY_HOME_CARD_FRIENDS,
                    SettingsViewModel.KEY_MYITMO_MARKS,
                    SettingsViewModel.KEY_BARS_MARKS,
                    SettingsViewModel.KEY_SHEET_MARKS,
                    SettingsViewModel.KEY_REFRESH_WIDGETS,
                    SettingsViewModel.KEY_RESTART_ONBOARDING,
                    SettingsViewModel.KEY_DIAGNOSTICS,
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
                SettingsViewModel.KEY_COMPACT_WIDGET_NEXT_LESSON_EARLY,
                false
            )
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_COMPACT_WIDGET_HIDE_TEACHER, true)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_FULL_WIDGET_HIDE_PAST, true)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_FULL_WIDGET_SHOW_TOMORROW, true)
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
    fun `custom services toggle refreshes widgets after applying the data gate`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SERVICES)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_CUSTOM_SERVICES, true)
            advanceUntilIdle()

            assertEquals(listOf(true), fixture.customServicesRepository.requests)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
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

            val schedule = fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING)
            val sport = fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING)
            assertFalse(schedule.enabled)
            assertEquals(null, schedule.selectedOptionKey)
            assertFalse(sport.enabled)
            assertEquals(null, sport.selectedOptionKey)
            assertEquals(UiText.Resource(R.string.settings_privacy_unknown), schedule.value)
            assertEquals(UiText.Resource(R.string.settings_privacy_unknown), sport.value)
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
                    SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.NOBODY)
                )
            )
            advanceUntilIdle()

            assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
            assertTrue(
                fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled
            )
            assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).selectedOptionKey)
            assertTrue(fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).enabled)
            assertEquals(1, fixture.repository.refreshSharingCount)

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(scheduleVisibility = SharingVisibility.FRIENDS, sportVisibility = SharingVisibility.NOBODY),
                updating = true
            )
            advanceUntilIdle()

            assertFalse(
                fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled
            )
            assertFalse(fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).enabled)
        }

    @Test
    fun `sharing update failure emits the repository error`() =
        runTest(mainDispatcherRule.dispatcher) {
            for (key in listOf(SettingsViewModel.KEY_SCHEDULE_SHARING, SettingsViewModel.KEY_SPORT_SHARING)) {
                val fixture = createFixture(
                    page = SettingsPage.PRIVACY,
                    local = LocalSettings(customServicesEnabled = true),
                    sharing = SharingSettingsState.Content(SharingSettings())
                )
                fixture.repository.scheduleSharingResult = AppResult.Failure(AppError.Network)
                fixture.repository.sportSharingResult = AppResult.Failure(AppError.Network)
                advanceUntilIdle()

                fixture.viewModel.onChoiceChanged(key, SharingVisibility.NOBODY.name)
                advanceUntilIdle()

                assertEquals(if (key == SettingsViewModel.KEY_SCHEDULE_SHARING) listOf(SharingVisibility.NOBODY) else emptyList<SharingVisibility>(), fixture.repository.scheduleSharingRequests)
                assertEquals(if (key == SettingsViewModel.KEY_SPORT_SHARING) listOf(SharingVisibility.NOBODY) else emptyList<SharingVisibility>(), fixture.repository.sportSharingRequests)
                assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
                assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).selectedOptionKey)
                assertTrue(fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled)
                assertTrue(fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).enabled)
                assertEquals(0, fixture.widgetRefresher.refreshCount)
                assertEquals(
                    SettingsEvent.ShowError(AppError.Network),
                    fixture.viewModel.events.first()
                )
            }
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
                fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled
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
    fun `text size choice writes only its own widget format and refreshes widgets`() =
        runTest(mainDispatcherRule.dispatcher) {
            val local = LocalSettings(
                scheduleWidget = ScheduleWidgetSettings(
                    compact = CompactScheduleWidgetSettings(textSize = WidgetTextSize.LARGE)
                )
            )
            val compact = createFixture(page = SettingsPage.COMPACT_SCHEDULE_WIDGET, local = local)
            val full = createFixture(page = SettingsPage.FULL_SCHEDULE_WIDGET, local = local)
            advanceUntilIdle()

            val choice = compact.viewModel.choice(SettingsViewModel.KEY_COMPACT_WIDGET_TEXT_SIZE)
            assertEquals(WidgetTextSize.LARGE.name, choice.selectedOptionKey)
            assertEquals(WidgetTextSize.entries.map { it.name }, choice.options.map { it.key })
            assertEquals(
                WidgetTextSize.NORMAL.name,
                full.viewModel.choice(SettingsViewModel.KEY_FULL_WIDGET_TEXT_SIZE).selectedOptionKey
            )

            compact.viewModel.onChoiceChanged(SettingsViewModel.KEY_COMPACT_WIDGET_TEXT_SIZE, "huge")
            advanceUntilIdle()
            assertTrue(compact.repository.compactTextSizeRequests.isEmpty())
            assertEquals(0, compact.widgetRefresher.refreshCount)

            full.viewModel.onChoiceChanged(SettingsViewModel.KEY_FULL_WIDGET_TEXT_SIZE, WidgetTextSize.EXTRA_LARGE.name)
            advanceUntilIdle()

            assertEquals(listOf(WidgetTextSize.EXTRA_LARGE), full.repository.fullTextSizeRequests)
            assertTrue(full.repository.compactTextSizeRequests.isEmpty())
            assertEquals(
                WidgetTextSize.EXTRA_LARGE.name,
                full.viewModel.choice(SettingsViewModel.KEY_FULL_WIDGET_TEXT_SIZE).selectedOptionKey
            )
            assertEquals(1, full.widgetRefresher.refreshCount)
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
    fun `maintenance offers a replay that resets the flag and leaves the overlay`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.MAINTENANCE)
            advanceUntilIdle()
            assertEquals(
                UiText.Resource(R.string.settings_restart_onboarding_title),
                fixture.viewModel.action(SettingsViewModel.KEY_RESTART_ONBOARDING).title
            )

            fixture.viewModel.onAction(SettingsViewModel.KEY_RESTART_ONBOARDING)
            advanceUntilIdle()

            assertEquals(1, fixture.onboardingRepository.resetCount)
            assertFalse(fixture.onboardingRepository.completed.value)
            assertEquals(SettingsEvent.CloseOverlays, fixture.viewModel.events.first())
        }

    @Test
    fun `the replay action stays out of every other page`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(page = SettingsPage.ROOT)
        advanceUntilIdle()

        assertTrue(
            fixture.viewModel.allItems().none { it.key == SettingsViewModel.KEY_RESTART_ONBOARDING }
        )
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
    fun `home page lists one switch per card and hides a card without touching widgets`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.HOME, local = LocalSettings(hiddenHomeCards = setOf(HomeCardKind.SPORT)))
            advanceUntilIdle()

            val section = fixture.viewModel.sections.value.single()
            assertEquals(UiText.Resource(R.string.settings_group_home), fixture.viewModel.page.title)
            assertEquals(null, section.footer)
            assertEquals(
                listOf(
                    SettingsViewModel.KEY_HOME_CARD_SCHEDULE, SettingsViewModel.KEY_HOME_CARD_SCHEDULE_CHANGES,
                    SettingsViewModel.KEY_HOME_CARD_MARKS, SettingsViewModel.KEY_HOME_CARD_SPORT,
                    SettingsViewModel.KEY_HOME_CARD_FRIENDS
                ),
                section.items.map { it.key }
            )
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_HOME_CARD_SCHEDULE).checked)
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_HOME_CARD_SPORT).checked)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_HOME_CARD_SCHEDULE, false)
            advanceUntilIdle()
            assertEquals(listOf(HomeCardKind.SCHEDULE to false), fixture.repository.homeCardRequests)
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_HOME_CARD_SCHEDULE).checked)
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `schedule auto sign display remains local and refreshes widgets for explicit toggles with services disabled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            advanceUntilIdle()

            val section = fixture.viewModel.sections.value.single { section ->
                section.items.any { it.key == SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN }
            }
            val toggle = fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN)
            assertEquals(listOf(toggle), section.items)
            assertEquals(UiText.Resource(R.string.settings_group_schedule), fixture.viewModel.page.title)
            assertEquals(UiText.Resource(R.string.settings_schedule_sport_auto_sign_title), toggle.title)
            assertEquals(UiText.Resource(R.string.settings_schedule_sport_auto_sign_description), toggle.description)
            assertEquals(UiText.Resource(R.string.settings_schedule_footer), section.footer)
            assertTrue(toggle.enabled)
            assertTrue(toggle.stateKnown)
            assertFalse(toggle.checked)
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN, true)
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertTrue(fixture.repository.local.value.showSportAutoSign)
            assertEquals(1, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN, false)
            advanceUntilIdle()
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertEquals(listOf(true, false), fixture.repository.scheduleSportAutoSignRequests)
            assertFalse(fixture.repository.local.value.customServicesEnabled)
            assertTrue(fixture.customServicesRepository.requests.isEmpty())
            assertEquals(0, fixture.repository.refreshSharingCount)
            assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            assertEquals(2, fixture.widgetRefresher.refreshCount)
            assertEquals(null, fixture.viewModel.previewSettings.value)
        }

    @Test
    fun `schedule page puts the changes switch in its own section above auto sign`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, local = LocalSettings(scheduleChangesEnabled = false))
            advanceUntilIdle()

            val (changes, autoSign) = fixture.viewModel.sections.value
            val toggle = fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_CHANGES)
            assertEquals(listOf(toggle), changes.items)
            assertEquals(null, changes.title)
            assertEquals(null, changes.footer)
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_title), toggle.title)
            assertFalse(toggle.checked)
            assertEquals(listOf(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN), autoSign.items.map { it.key })
            assertEquals(UiText.Resource(R.string.settings_schedule_footer), autoSign.footer)

            fixture.tracking.enabled.value = true
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_CHANGES).checked)
        }

    @Test
    fun `schedule changes switch goes through tracking and asks for notifications only when they are off`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_CHANGES, false)
            advanceUntilIdle()
            assertEquals(listOf(false), fixture.tracking.setCalls)
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_CHANGES).checked)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(listOf(false, true), fixture.tracking.setCalls)

            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(listOf(false, true, true), fixture.tracking.setCalls)
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission), fixture.viewModel.events.take(1).toList())
            assertEquals(0, fixture.widgetRefresher.refreshCount)
            assertTrue(fixture.repository.homeCardRequests.isEmpty())
        }

    @Test
    fun `schedule changes description says when notifications are off`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            advanceUntilIdle()
            val description = { fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_CHANGES).description }
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_description), description())

            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_notifications_off), description())

            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_description), description())
        }

    @Test
    fun `home page offers the schedule changes card second and hides it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.HOME)
            advanceUntilIdle()

            val row = fixture.viewModel.sections.value.single().items[1] as SettingItem.Toggle
            assertEquals(SettingsViewModel.KEY_HOME_CARD_SCHEDULE_CHANGES, row.key)
            assertEquals(UiText.Resource(R.string.settings_home_card_schedule_changes_title), row.title)
            assertTrue(row.checked)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_HOME_CARD_SCHEDULE_CHANGES, false)
            advanceUntilIdle()
            assertEquals(listOf(HomeCardKind.SCHEDULE_CHANGES to false), fixture.repository.homeCardRequests)
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_HOME_CARD_SCHEDULE_CHANGES).checked)
            assertTrue(fixture.tracking.setCalls.isEmpty())
        }

    @Test
    fun `root lists the recordbook between schedule and sport`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture()
            advanceUntilIdle()

            val pages = fixture.viewModel.allItems().filterIsInstance<SettingItem.Navigation>().map { it.page }
            val index = pages.indexOf(SettingsPage.RECORDBOOK)
            assertEquals(SettingsPage.SCHEDULE, pages[index - 1])
            assertEquals(SettingsPage.SPORT, pages[index + 1])
            assertEquals(UiText.Resource(R.string.title_recordbook), SettingsPage.RECORDBOOK.title)
        }

    @Test
    fun `recordbook page shows the BARS switch only once BARS has answered`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, local = LocalSettings(myItmoMarksEnabled = false))
            advanceUntilIdle()

            val section = fixture.viewModel.sections.value.single()
            assertEquals(null, section.title)
            assertEquals(listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_SHEET_MARKS), section.items.map { it.key })
            val myItmo = fixture.viewModel.toggle(SettingsViewModel.KEY_MYITMO_MARKS)
            assertEquals(UiText.Resource(R.string.settings_marks_myitmo_title), myItmo.title)
            assertFalse(myItmo.checked)

            for (bars in listOf(false, true)) {
                fixture.repository.barsMarks.value = bars
                advanceUntilIdle()
                assertEquals(
                    listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_BARS_MARKS, SettingsViewModel.KEY_SHEET_MARKS),
                    fixture.viewModel.sections.value.single().items.map { it.key }
                )
                val toggle = fixture.viewModel.toggle(SettingsViewModel.KEY_BARS_MARKS)
                assertEquals(UiText.Resource(R.string.settings_marks_bars_title), toggle.title)
                assertEquals(bars, toggle.checked)
            }
        }

    @Test
    fun `recordbook page puts the sheets switch after My ITMO and BARS`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, local = LocalSettings(sheetMarksEnabled = false))
            advanceUntilIdle()
            val keys = { fixture.viewModel.sections.value.single().items.map { it.key } }

            assertEquals(listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_SHEET_MARKS), keys())
            val sheets = fixture.viewModel.toggle(SettingsViewModel.KEY_SHEET_MARKS)
            assertEquals(UiText.Resource(R.string.settings_marks_sheets_title), sheets.title)
            assertFalse(sheets.checked)

            fixture.repository.barsMarks.value = true
            fixture.repository.sheetMarks.value = true
            advanceUntilIdle()
            assertEquals(
                listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_BARS_MARKS, SettingsViewModel.KEY_SHEET_MARKS),
                keys()
            )
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SHEET_MARKS).checked)
        }

    @Test
    fun `the sheets switch goes through tracking and asks for notifications, then offers the hint`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            val events = recordEvents(fixture)
            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SHEET_MARKS, false)
            advanceUntilIdle()
            assertEquals(listOf(false), fixture.markTracking.sheetsCalls)
            assertEquals(emptyList<SettingsEvent>(), events)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SHEET_MARKS, true)
            advanceUntilIdle()
            assertEquals(listOf(false, true), fixture.markTracking.sheetsCalls)
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission, SettingsEvent.ShowBackgroundWorkHint), events)
            assertTrue(fixture.markTracking.myItmoCalls.isEmpty())
            assertTrue(fixture.markTracking.barsCalls.isEmpty())
        }

    @Test
    fun `the background work row follows the sheets switch alone`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.RECORDBOOK,
                local = LocalSettings(myItmoMarksEnabled = false, barsMarksEnabled = false),
                backgroundWork = FakeBackgroundWorkAccess(unrestricted = false)
            )
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            val keys = { fixture.viewModel.sections.value.single().items.map { it.key } }
            assertEquals(SettingsViewModel.KEY_BACKGROUND_WORK, keys().last())

            fixture.repository.sheetMarks.value = false
            advanceUntilIdle()
            assertFalse(SettingsViewModel.KEY_BACKGROUND_WORK in keys())
        }

    @Test
    fun `recordbook footer says when notifications are off`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK)
            advanceUntilIdle()
            val footer = { fixture.viewModel.sections.value.single().footer }
            assertEquals(UiText.Resource(R.string.settings_marks_footer), footer())

            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()
            assertEquals(UiText.Resource(R.string.settings_marks_notifications_off), footer())

            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()
            assertEquals(UiText.Resource(R.string.settings_marks_footer), footer())
        }

    @Test
    fun `mark switches go through tracking and turning one on asks for notifications only when they are off`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, local = LocalSettings(barsMarksEnabled = false))
            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_MYITMO_MARKS, false)
            advanceUntilIdle()
            assertEquals(listOf(false), fixture.markTracking.myItmoCalls)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_BARS_MARKS, true)
            advanceUntilIdle()
            assertEquals(listOf(true), fixture.markTracking.barsCalls)
            // Only the switch turned on asked; turning My ITMO off did not.
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission), fixture.viewModel.events.take(1).toList())
            assertTrue(fixture.tracking.setCalls.isEmpty())
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `schedule page shows the background work row after the changes switch only while restricted and on`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()
            val keys = { fixture.viewModel.sections.value.first().items.map { it.key } }
            // Not asked yet: the row stays out rather than flash in.
            assertEquals(listOf(SettingsViewModel.KEY_SCHEDULE_CHANGES), keys())

            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            assertEquals(listOf(SettingsViewModel.KEY_SCHEDULE_CHANGES, SettingsViewModel.KEY_BACKGROUND_WORK), keys())
            val row = fixture.viewModel.action(SettingsViewModel.KEY_BACKGROUND_WORK)
            assertEquals(UiText.Resource(R.string.settings_background_work_title), row.title)
            assertEquals(UiText.Resource(R.string.background_work_hint), row.description)
            assertEquals(R.drawable.ic_open_in_new, row.trailingIconRes)
            assertTrue(row.enabled)

            fixture.tracking.enabled.value = false
            advanceUntilIdle()
            assertEquals(listOf(SettingsViewModel.KEY_SCHEDULE_CHANGES), keys())

            fixture.tracking.enabled.value = true
            fixture.backgroundWork.unrestricted = true
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            assertEquals(listOf(SettingsViewModel.KEY_SCHEDULE_CHANGES), keys())
        }

    @Test
    fun `recordbook page shows the background work row while any mark check is on`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            val keys = { fixture.viewModel.sections.value.single().items.map { it.key } }
            assertEquals(
                listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_SHEET_MARKS, SettingsViewModel.KEY_BACKGROUND_WORK),
                keys()
            )

            fixture.repository.myItmoMarks.value = false
            fixture.repository.sheetMarks.value = false
            advanceUntilIdle()
            assertEquals(listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_SHEET_MARKS), keys())

            fixture.repository.barsMarks.value = false
            advanceUntilIdle()
            assertEquals(
                listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_BARS_MARKS, SettingsViewModel.KEY_SHEET_MARKS),
                keys()
            )

            fixture.repository.barsMarks.value = true
            advanceUntilIdle()
            assertEquals(
                listOf(
                    SettingsViewModel.KEY_MYITMO_MARKS,
                    SettingsViewModel.KEY_BARS_MARKS,
                    SettingsViewModel.KEY_SHEET_MARKS,
                    SettingsViewModel.KEY_BACKGROUND_WORK
                ),
                keys()
            )
        }

    @Test
    fun `the background work row leaves when the system saves the choice after the screen returns`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()
            val keys = { fixture.viewModel.sections.value.single().items.map { it.key } }

            fixture.viewModel.onBackgroundWorkChanged()
            runCurrent()
            assertEquals(listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_SHEET_MARKS, SettingsViewModel.KEY_BACKGROUND_WORK), keys())

            fixture.backgroundWork.unrestricted = true
            advanceTimeBy(999)
            runCurrent()
            assertEquals(listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_SHEET_MARKS, SettingsViewModel.KEY_BACKGROUND_WORK), keys())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(SettingsViewModel.KEY_MYITMO_MARKS, SettingsViewModel.KEY_SHEET_MARKS), keys())
        }

    @Test
    fun `the background work row opens the system page`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()

            fixture.viewModel.onAction(SettingsViewModel.KEY_BACKGROUND_WORK)

            assertEquals(SettingsEvent.OpenBackgroundWorkSettings, fixture.viewModel.events.first())
        }

    @Test
    fun `turning a background check on offers the hint once while restricted`() =
        runTest(mainDispatcherRule.dispatcher) {
            val switches = listOf(
                SettingsPage.SCHEDULE to SettingsViewModel.KEY_SCHEDULE_CHANGES,
                SettingsPage.RECORDBOOK to SettingsViewModel.KEY_MYITMO_MARKS,
                SettingsPage.RECORDBOOK to SettingsViewModel.KEY_BARS_MARKS
            )
            for ((page, key) in switches) {
                val fixture = createFixture(
                    page = page,
                    local = LocalSettings(barsMarksEnabled = false),
                    backgroundWork = FakeBackgroundWorkAccess(unrestricted = false)
                )
                val events = recordEvents(fixture)
                fixture.viewModel.onNotificationPermissionChanged(granted = true)
                fixture.viewModel.onBackgroundWorkChanged()
                advanceUntilIdle()

                fixture.viewModel.onToggleChanged(key, false)
                advanceUntilIdle()
                assertEquals(key, emptyList<SettingsEvent>(), events)

                fixture.viewModel.onToggleChanged(key, true)
                advanceUntilIdle()
                assertEquals(key, listOf(SettingsEvent.ShowBackgroundWorkHint), events)
                assertEquals(key, 1, fixture.repository.hintShownCalls)

                fixture.viewModel.onToggleChanged(key, true)
                advanceUntilIdle()
                assertEquals(key, listOf(SettingsEvent.ShowBackgroundWorkHint), events)
                assertEquals(key, 1, fixture.repository.hintShownCalls)
            }
        }

    @Test
    fun `an unrestricted app gets no hint and missing notifications are asked for first`() =
        runTest(mainDispatcherRule.dispatcher) {
            val unrestricted = createFixture(page = SettingsPage.SCHEDULE)
            val quiet = recordEvents(unrestricted)
            unrestricted.viewModel.onNotificationPermissionChanged(granted = true)
            unrestricted.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            unrestricted.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(emptyList<SettingsEvent>(), quiet)
            assertEquals(0, unrestricted.repository.hintShownCalls)

            val restricted = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            val events = recordEvents(restricted)
            restricted.viewModel.onNotificationPermissionChanged(granted = false)
            restricted.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            restricted.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission, SettingsEvent.ShowBackgroundWorkHint), events)
        }

    @Test
    fun `the QR page offers the tile only where it can be requested and until it is added`() =
        runTest(mainDispatcherRule.dispatcher) {
            val unsupported = createFixture(page = SettingsPage.QR_WIDGET)
            advanceUntilIdle()
            assertTrue(unsupported.viewModel.allItems().none { it.key == SettingsViewModel.KEY_QR_TILE })

            val fixture = createFixture(page = SettingsPage.QR_WIDGET, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
            advanceUntilIdle()
            val first = fixture.viewModel.sections.value.first()
            assertNull(first.title)
            assertEquals(
                listOf(
                    SettingItem.Action(
                        key = SettingsViewModel.KEY_QR_TILE,
                        title = UiText.Resource(R.string.settings_qr_tile_title),
                        description = UiText.Resource(R.string.settings_qr_tile_description)
                    )
                ),
                first.items
            )

            fixture.repository.qrTileAdded.value = true
            advanceUntilIdle()
            assertTrue(fixture.viewModel.allItems().none { it.key == SettingsViewModel.KEY_QR_TILE })
        }

    @Test
    fun `tapping the tile row asks the system to add the tile`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.QR_WIDGET, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
            val events = recordEvents(fixture)
            advanceUntilIdle()

            fixture.viewModel.onAction(SettingsViewModel.KEY_QR_TILE)
            advanceUntilIdle()

            assertEquals(listOf(SettingsEvent.RequestQrTile), events)
        }

    @Test
    fun `the system answer remembers an added tile and explains only what the user should know`() =
        runTest(mainDispatcherRule.dispatcher) {
            fun answer(result: QrTileAddResult): Pair<List<Boolean>, List<SettingsEvent>> {
                val fixture = createFixture(page = SettingsPage.QR_WIDGET, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
                val events = recordEvents(fixture)
                advanceUntilIdle()
                fixture.viewModel.onQrTileResult(result)
                advanceUntilIdle()
                return fixture.repository.qrTileAddedRequests.toList() to events.toList()
            }

            assertEquals(listOf(true) to emptyList<SettingsEvent>(), answer(QrTileAddResult.ADDED))
            assertEquals(
                listOf(true) to listOf(SettingsEvent.ShowMessage(UiText.Resource(R.string.settings_qr_tile_already_added))),
                answer(QrTileAddResult.ALREADY_ADDED)
            )
            assertEquals(emptyList<Boolean>() to emptyList<SettingsEvent>(), answer(QrTileAddResult.NOT_ADDED))
            assertEquals(emptyList<Boolean>() to emptyList<SettingsEvent>(), answer(QrTileAddResult.IN_PROGRESS))
            assertEquals(
                emptyList<Boolean>() to listOf(SettingsEvent.ShowMessage(UiText.Resource(R.string.settings_qr_tile_failed))),
                answer(QrTileAddResult.FAILED)
            )
        }

    @Test
    fun `home page offers the marks card third and hides it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.HOME)
            advanceUntilIdle()

            val row = fixture.viewModel.sections.value.single().items[2] as SettingItem.Toggle
            assertEquals(SettingsViewModel.KEY_HOME_CARD_MARKS, row.key)
            assertEquals(UiText.Resource(R.string.settings_home_card_marks_title), row.title)
            assertTrue(row.checked)

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_HOME_CARD_MARKS, false)
            advanceUntilIdle()
            assertEquals(listOf(HomeCardKind.MARKS to false), fixture.repository.homeCardRequests)
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_HOME_CARD_MARKS).checked)
            assertTrue(fixture.markTracking.myItmoCalls.isEmpty())
        }

    @Test
    fun `schedule auto sign display refreshes widgets only after persistence completes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            val persisted = CompletableDeferred<Unit>()
            fixture.repository.scheduleSportAutoSignWrite = { persisted.await() }
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN, true)
            runCurrent()

            assertEquals(listOf(true), fixture.repository.scheduleSportAutoSignRequests)
            assertFalse(fixture.repository.local.value.showSportAutoSign)
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            persisted.complete(Unit)
            advanceUntilIdle()

            assertTrue(fixture.repository.local.value.showSportAutoSign)
            assertTrue(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `schedule auto sign persistence failure does not refresh widgets or enable services`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            val failure = IllegalStateException("Preference write failed")
            fixture.repository.scheduleSportAutoSignWrite = { throw failure }
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN, true)
            advanceUntilIdle()

            assertEquals(listOf(true), fixture.repository.scheduleSportAutoSignRequests)
            assertFalse(fixture.repository.local.value.showSportAutoSign)
            assertFalse(fixture.viewModel.toggle(SettingsViewModel.KEY_SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertEquals(0, fixture.widgetRefresher.refreshCount)
            assertFalse(fixture.repository.local.value.customServicesEnabled)
            assertTrue(fixture.customServicesRepository.requests.isEmpty())
            assertEquals(SettingsEvent.ShowError(AppError.Unknown(failure)), fixture.viewModel.events.first())
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
            sharing = SharingSettingsState.Content(SharingSettings(SharingVisibility.FRIENDS, SharingVisibility.NOBODY))
        )
        runCurrent()
        assertEquals(1, fixture.repository.refreshSharingCount)
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
        assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).selectedOptionKey)
    }

    @Test
    fun `slow privacy responses do not incur an extra delay`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings())
        )
        fixture.repository.refreshDelayMs = 800
        runCurrent()
        advanceTimeBy(799)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
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
        fixture.repository.sharing.value = SharingSettingsState.Content(SharingSettings(SharingVisibility.NOBODY, SharingVisibility.FRIENDS))
        runCurrent()
        advanceTimeBy(299)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).selectedOptionKey)
    }

    @Test
    fun `disabling services bypasses the privacy loading delay immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.PRIVACY,
            local = LocalSettings(customServicesEnabled = true),
            sharing = SharingSettingsState.Content(SharingSettings())
        )
        runCurrent()
        advanceTimeBy(50)
        fixture.repository.local.value = LocalSettings(customServicesEnabled = false)
        runCurrent()
        assertTrue(fixture.viewModel.sections.value.isNotEmpty())
        assertEquals(null, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
        assertEquals(SharingSettingsState.Disabled, fixture.repository.sharing.value)
        advanceUntilIdle()
        assertEquals(null, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
    }

    @Test
    fun `privacy delay starts when stored settings arrive not when view model is created`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertTrue(fixture.viewModel.sections.value.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
    }

    @Test
    fun `privacy offers all friends nobody in order with server defaults and current values`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            advanceUntilIdle()
            val choices = fixture.viewModel.allItems().filterIsInstance<SettingItem.Choice>()
            assertEquals(listOf(SettingsViewModel.KEY_SCHEDULE_SHARING, SettingsViewModel.KEY_SPORT_SHARING, SettingsViewModel.KEY_FRIENDS_SHARING), choices.map { it.key })
            assertEquals(listOf(UiText.Resource(R.string.settings_schedule_sharing_title), UiText.Resource(R.string.settings_sport_sharing_title), UiText.Resource(R.string.settings_friends_sharing_title)), choices.map { it.title })
            choices.forEach { choice ->
                assertEquals(listOf("ALL", "FRIENDS", "NOBODY"), choice.options.map { it.key })
                assertEquals(listOf(R.string.settings_privacy_all, R.string.settings_privacy_friends, R.string.settings_privacy_nobody).map(UiText::Resource), choice.options.map { it.label })
                val isFriends = choice.key == SettingsViewModel.KEY_FRIENDS_SHARING
                assertEquals((if (isFriends) SharingVisibility.ALL else SharingVisibility.FRIENDS).name, choice.selectedOptionKey)
                assertEquals(UiText.Resource(if (isFriends) R.string.settings_privacy_all else R.string.settings_privacy_friends), choice.value)
                assertTrue(choice.enabled)
            }
            assertEquals(UiText.Resource(R.string.settings_privacy_footer), fixture.viewModel.sections.value.single().footer)

            fixture.repository.sharing.value = SharingSettingsState.Content(
                SharingSettings(SharingVisibility.ALL, SharingVisibility.NOBODY)
            )
            advanceUntilIdle()
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
            assertEquals(UiText.Resource(R.string.settings_privacy_all), fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).value)
            assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).selectedOptionKey)
            assertEquals(UiText.Resource(R.string.settings_privacy_nobody), fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).value)
        }

    @Test
    fun `privacy choices independently send typed commands without changing other visibility`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            advanceUntilIdle()
            fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_SCHEDULE_SHARING, SharingVisibility.ALL.name)
            advanceUntilIdle()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
            assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).selectedOptionKey)

            fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_SPORT_SHARING, SharingVisibility.NOBODY.name)
            advanceUntilIdle()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertEquals(listOf(SharingVisibility.NOBODY), fixture.repository.sportSharingRequests)
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
            assertEquals(SharingVisibility.NOBODY.name, fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).selectedOptionKey)
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun `privacy rejects unknown keys and options unchanged values and obsolete toggles`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            advanceUntilIdle()
            fixture.viewModel.onChoiceChanged("unknown", SharingVisibility.ALL.name)
            for (key in listOf(SettingsViewModel.KEY_SCHEDULE_SHARING, SettingsViewModel.KEY_SPORT_SHARING)) {
                fixture.viewModel.onChoiceChanged(key, "unknown")
                fixture.viewModel.onChoiceChanged(key, "all")
                fixture.viewModel.onChoiceChanged(key, SharingVisibility.FRIENDS.name)
                fixture.viewModel.onToggleChanged(key, false)
            }
            advanceUntilIdle()
            assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
        }

    @Test
    fun `privacy rejects actions during loading unknown error disabled and updating states`() =
        runTest(mainDispatcherRule.dispatcher) {
            val states = listOf(
                SharingSettingsState.Loading,
                SharingSettingsState.Error,
                SharingSettingsState.Disabled,
                SharingSettingsState.Content(SharingSettings(), updating = true)
            )
            states.forEach { sharing ->
                val fixture = createFixture(
                    page = SettingsPage.PRIVACY,
                    local = LocalSettings(customServicesEnabled = sharing != SharingSettingsState.Disabled),
                    sharing = sharing
                )
                advanceUntilIdle()
                for (key in listOf(SettingsViewModel.KEY_SCHEDULE_SHARING, SettingsViewModel.KEY_SPORT_SHARING)) {
                    fixture.viewModel.onChoiceChanged(key, SharingVisibility.ALL.name)
                }
                advanceUntilIdle()
                assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
                assertTrue(fixture.repository.sportSharingRequests.isEmpty())
                if (sharing == SharingSettingsState.Loading) {
                    assertTrue(fixture.viewModel.sections.value.isEmpty())
                } else if (sharing !is SharingSettingsState.Content) {
                    fixture.viewModel.allItems().filterIsInstance<SettingItem.Choice>().forEach { choice ->
                        assertFalse(choice.enabled)
                        assertEquals(null, choice.selectedOptionKey)
                        assertEquals(UiText.Resource(R.string.settings_privacy_unknown), choice.value)
                    }
                }
            }
        }

    @Test
    fun `privacy ignores choices on other pages and while fresh settings are masked`() =
        runTest(mainDispatcherRule.dispatcher) {
            for (page in listOf(SettingsPage.ROOT, SettingsPage.PRIVACY)) {
                val fixture = createFixture(
                    page = page,
                    local = LocalSettings(customServicesEnabled = true),
                    sharing = SharingSettingsState.Content(SharingSettings())
                )
                runCurrent()
                fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_SCHEDULE_SHARING, SharingVisibility.ALL.name)
                fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_SPORT_SHARING, SharingVisibility.ALL.name)
                advanceUntilIdle()
                assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
                assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            }
        }

    @Test
    fun `pending privacy command locks both rows and rejects stale dialog callbacks until saved`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.PRIVACY,
                local = LocalSettings(customServicesEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings())
            )
            val saved = CompletableDeferred<Unit>()
            fixture.repository.sharingWrite = { saved.await() }
            advanceUntilIdle()
            fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_SCHEDULE_SHARING, SharingVisibility.ALL.name)
            // No collector has run yet: even a second callback in the same frame is rejected.
            fixture.viewModel.onChoiceChanged(SettingsViewModel.KEY_SPORT_SHARING, SharingVisibility.NOBODY.name)
            runCurrent()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            for (key in listOf(SettingsViewModel.KEY_SCHEDULE_SHARING, SettingsViewModel.KEY_SPORT_SHARING)) {
                assertFalse(fixture.viewModel.choice(key).enabled)
                assertEquals(SharingVisibility.FRIENDS.name, fixture.viewModel.choice(key).selectedOptionKey)
                fixture.viewModel.onChoiceChanged(key, SharingVisibility.NOBODY.name)
            }
            runCurrent()
            assertEquals(listOf(SharingVisibility.ALL), fixture.repository.scheduleSharingRequests)
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())

            saved.complete(Unit)
            advanceUntilIdle()
            assertTrue(fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).enabled)
            assertTrue(fixture.viewModel.choice(SettingsViewModel.KEY_SPORT_SHARING).enabled)
            assertEquals(SharingVisibility.ALL.name, fixture.viewModel.choice(SettingsViewModel.KEY_SCHEDULE_SHARING).selectedOptionKey)
        }

    private fun createFixture(
        local: LocalSettings = LocalSettings(),
        sharing: SharingSettingsState = SharingSettingsState.Disabled,
        localInitiallyAvailable: Boolean = true,
        page: SettingsPage = SettingsPage.ROOT,
        backgroundWork: FakeBackgroundWorkAccess = FakeBackgroundWorkAccess(),
        tileAccess: FakeQuickSettingsTileAccess = FakeQuickSettingsTileAccess()
    ): Fixture {
        val tracking = FakeScheduleChangeTracking(enabled = local.scheduleChangesEnabled)
        val markTracking = FakeMarkTracking()
        val repository = FakeSettingsRepository(local, sharing, localInitiallyAvailable, tracking.enabled)
        val customServicesRepository = FakeCustomServicesRepository { enabled ->
            repository.local.value = repository.local.value.copy(
                customServicesEnabled = enabled
            )
        }
        val refresher = FakeWidgetRefreshRequester()
        val onboarding = FakeOnboardingRepository()
        val viewModel = SettingsViewModel(
            repository = repository,
            customServicesRepository = customServicesRepository,
            onboardingRepository = onboarding,
            widgetRefreshRequester = refresher,
            appVersion = AppVersion("2.1-test"),
            tracking = tracking,
            markTracking = markTracking,
            backgroundWork = backgroundWork,
            tileAccess = tileAccess,
            diagnostics = RecordingDiagnostics(),
            savedStateHandle = SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name))
        )
        return Fixture(viewModel, repository, customServicesRepository, refresher, onboarding, tracking, markTracking, backgroundWork, tileAccess)
    }

    /** Collects every event from now on; the channel has one receiver, so a test uses either this or `events.first()`. */
    private fun TestScope.recordEvents(fixture: Fixture): List<SettingsEvent> {
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.viewModel.events.toList(events) }
        return events
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
        val widgetRefresher: FakeWidgetRefreshRequester,
        val onboardingRepository: FakeOnboardingRepository,
        val tracking: FakeScheduleChangeTracking,
        val markTracking: FakeMarkTracking,
        val backgroundWork: FakeBackgroundWorkAccess,
        val tileAccess: FakeQuickSettingsTileAccess
    )

    /** Unrestricted by default, so the background work row stays out of the other cases. */
    private class FakeBackgroundWorkAccess(var unrestricted: Boolean = true) : BackgroundWorkAccess {
        override fun isUnrestricted(): Boolean = unrestricted
    }

    /** Below Android 13 by default, so the tile row stays out of the other cases. */
    private class FakeQuickSettingsTileAccess(var canRequest: Boolean = false) : QuickSettingsTileAccess {
        override fun canRequestAdd(): Boolean = canRequest
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        val completed = MutableStateFlow(true)
        var resetCount = 0

        override fun observeCompleted(): Flow<Boolean> = completed

        override suspend fun complete() {
            completed.value = true
        }

        override suspend fun reset() {
            resetCount += 1
            completed.value = false
        }
    }

    private class FakeSettingsRepository(
        initialLocal: LocalSettings,
        initialSharing: SharingSettingsState,
        localInitiallyAvailable: Boolean,
        /** The switch lives behind [dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking]; reads follow it. */
        private val scheduleChangesEnabled: MutableStateFlow<Boolean>
    ) : SettingsRepository {

        val local = MutableStateFlow(initialLocal)
        /** The mark switches live behind [dev.alllexey.itmowidgets.core.recordbook.MarkTracking]; tests move them here. */
        val myItmoMarks = MutableStateFlow(initialLocal.myItmoMarksEnabled)
        val barsMarks = MutableStateFlow(initialLocal.barsMarksEnabled)
        val sheetMarks = MutableStateFlow(initialLocal.sheetMarksEnabled)
        val backgroundWorkHintShown = MutableStateFlow(initialLocal.backgroundWorkHintShown)
        var hintShownCalls = 0
        val qrTileAdded = MutableStateFlow(initialLocal.qrTileAdded)
        val qrTileAddedRequests = mutableListOf<Boolean>()
        private val localAvailable = MutableStateFlow(localInitiallyAvailable)
        val sharing = MutableStateFlow(initialSharing)
        var refreshSharingCount = 0
        var disableSharingCount = 0
        var scheduleSharingResult: AppResult<Unit> = AppResult.Success(Unit)
        var sportSharingResult: AppResult<Unit> = AppResult.Success(Unit)
        var sharingWrite: suspend () -> Unit = {}
        val scheduleSharingRequests = mutableListOf<SharingVisibility>()
        val sportSharingRequests = mutableListOf<SharingVisibility>()
        val friendsSharingRequests = mutableListOf<SharingVisibility>()
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
        var scheduleSportAutoSignWrite: suspend () -> Unit = {}

        override fun observeLocalSettings(): Flow<LocalSettings> =
            combine(
                localAvailable,
                combine(local, backgroundWorkHintShown, sheetMarks, qrTileAdded) { settings, hintShown, sheets, tileAdded ->
                    settings.copy(backgroundWorkHintShown = hintShown, sheetMarksEnabled = sheets, qrTileAdded = tileAdded)
                },
                scheduleChangesEnabled,
                myItmoMarks,
                barsMarks
            ) { available, settings, scheduleChanges, myItmo, bars ->
                settings.copy(scheduleChangesEnabled = scheduleChanges, myItmoMarksEnabled = myItmo, barsMarksEnabled = bars)
                    .takeIf { available }
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

        override suspend fun setScheduleVisibility(visibility: SharingVisibility): AppResult<Unit> {
            scheduleSharingRequests += visibility
            sharingWrite()
            if (scheduleSharingResult is AppResult.Success) {
                val content = sharing.value as SharingSettingsState.Content
                sharing.value = content.copy(settings = content.settings.copy(scheduleVisibility = visibility))
            }
            return scheduleSharingResult
        }

        override suspend fun setFriendsVisibility(visibility: SharingVisibility): AppResult<Unit> {
            friendsSharingRequests += visibility
            sharingWrite()
            if (sportSharingResult is AppResult.Success) {
                val content = sharing.value as SharingSettingsState.Content
                sharing.value = content.copy(settings = content.settings.copy(friendsVisibility = visibility))
            }
            return sportSharingResult
        }

        override suspend fun setSportVisibility(visibility: SharingVisibility): AppResult<Unit> {
            sportSharingRequests += visibility
            sharingWrite()
            if (sportSharingResult is AppResult.Success) {
                val content = sharing.value as SharingSettingsState.Content
                sharing.value = content.copy(settings = content.settings.copy(sportVisibility = visibility))
            }
            return sportSharingResult
        }

        override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) {
            nextLessonEarlyRequests += enabled
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(compact = local.value.scheduleWidget.compact.copy(showNextLessonEarly = enabled))
            )
        }

        override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) {
            widgetTeacherHiddenRequests += hidden
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(compact = local.value.scheduleWidget.compact.copy(hideTeacher = hidden))
            )
        }

        val fullWidgetTeacherHiddenRequests = mutableListOf<Boolean>()

        override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) {
            fullWidgetTeacherHiddenRequests += hidden
            local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(
                full = local.value.scheduleWidget.full.copy(hideTeacher = hidden)
            ))
        }

        override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) {
            pastLessonsHiddenRequests += hidden
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(hidePastLessons = hidden))
            )
        }

        override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) {
            tomorrowScheduleRequests += enabled
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(
                    showTomorrowWhenTodayIsOver = enabled
                ))
            )
        }

        val compactTextSizeRequests = mutableListOf<WidgetTextSize>()
        val fullTextSizeRequests = mutableListOf<WidgetTextSize>()

        override suspend fun setCompactWidgetTextSize(size: WidgetTextSize) {
            compactTextSizeRequests += size
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(
                    compact = local.value.scheduleWidget.compact.copy(textSize = size)
                )
            )
        }

        override suspend fun setFullWidgetTextSize(size: WidgetTextSize) {
            fullTextSizeRequests += size
            local.value = local.value.copy(
                scheduleWidget = local.value.scheduleWidget.copy(
                    full = local.value.scheduleWidget.full.copy(textSize = size)
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
            scheduleSportAutoSignWrite()
            local.value = local.value.copy(showSportAutoSign = enabled)
        }

        val homeCardRequests = mutableListOf<Pair<HomeCardKind, Boolean>>()

        override suspend fun setHomeCardVisible(kind: HomeCardKind, visible: Boolean) {
            homeCardRequests += kind to visible
            local.value = local.value.copy(hiddenHomeCards = if (visible) local.value.hiddenHomeCards - kind else local.value.hiddenHomeCards + kind)
        }

        override suspend fun setBackgroundWorkHintShown() {
            hintShownCalls += 1
            backgroundWorkHintShown.value = true
        }

        override suspend fun setQrTileAdded(added: Boolean) {
            qrTileAddedRequests += added
            qrTileAdded.value = added
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
