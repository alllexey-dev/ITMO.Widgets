package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_selected
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_already_added
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_failed
import dev.alllexey.itmowidgets.shared.feature.settings.settings_qr_tile_title
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** Widget pages built and handled by [WidgetsPageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetsPageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun widgetPagesExposeOnlyTheirOwnControlsAndIndependentTeacherValues() = runTest(main.dispatcher) {
        val local = LocalSettings(scheduleWidget = ScheduleWidgetSettings(
            compact = CompactScheduleWidgetSettings(hideTeacher = true),
            full = FullScheduleWidgetSettings(hideTeacher = false)
        ))
        val compact = createFixture(page = SettingsPage.COMPACT_SCHEDULE_WIDGET, local = local)
        val full = createFixture(page = SettingsPage.FULL_SCHEDULE_WIDGET, local = local)
        advanceUntilIdle()
        assertEquals(
            setOf(
                SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                SettingRowId.COMPACT_WIDGET_HIDE_TEACHER,
                SettingRowId.COMPACT_WIDGET_TEXT_SIZE
            ),
            compact.viewModel.allItems().map { it.id }.toSet()
        )
        assertEquals(
            setOf(
                SettingRowId.FULL_WIDGET_HIDE_TEACHER,
                SettingRowId.FULL_WIDGET_HIDE_PAST,
                SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                SettingRowId.FULL_WIDGET_TEXT_SIZE
            ),
            full.viewModel.allItems().map { it.id }.toSet()
        )
        assertTrue(compact.viewModel.toggle(SettingRowId.COMPACT_WIDGET_HIDE_TEACHER).checked)
        assertFalse(full.viewModel.toggle(SettingRowId.FULL_WIDGET_HIDE_TEACHER).checked)
        full.viewModel.onToggleChanged(SettingRowId.FULL_WIDGET_HIDE_TEACHER, true)
        advanceUntilIdle()
        assertEquals(listOf(true), full.repository.fullWidgetTeacherHiddenRequests)
        assertTrue(full.repository.widgetTeacherHiddenRequests.isEmpty())
        assertEquals(1, full.widgetRefresher.refreshCount)
        assertEquals(SettingsPage.COMPACT_SCHEDULE_WIDGET, SettingsPage.fromArgument("SCHEDULE_WIDGETS"))
    }

    @Test
    fun previewExistsOnlyOnWidgetSettingsPagesAndUsesStoredValues() =
        runTest(main.dispatcher) {
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
                    fixture.viewModel.uiState.value.previewSettings
                )
            }
        }

    @Test
    fun previewWaitsForStorageAndFollowsPersistedToggleAndAnimationChanges() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.QR_WIDGET, localInitiallyAvailable = false)
            advanceUntilIdle()
            assertEquals(null, fixture.viewModel.uiState.value.previewSettings)
            fixture.repository.publishLocalSettings()
            advanceUntilIdle()
            fixture.viewModel.onToggleChanged(SettingRowId.QR_DYNAMIC_COLORS, false)
            fixture.viewModel.onChoiceChanged(SettingRowId.QR_ANIMATION, QrAnimationType.FADE.name)
            advanceUntilIdle()
            assertEquals(
                WidgetPreviewSettings.Qr(QrWidgetSettings(dynamicColors = false, animationType = QrAnimationType.FADE)),
                fixture.viewModel.uiState.value.previewSettings
            )
        }

    @Test
    fun disabledSpoilerKeepsItsDependentControlsVisibleButUnavailable() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.QR_WIDGET,
                local = LocalSettings(qrWidget = QrWidgetSettings(spoilerEnabled = false))
            )
            fixture.viewModel.onCustomSpoilerChanged(configured = true)
            advanceUntilIdle()

            assertEquals(5, fixture.viewModel.allItems().size)
            assertFalse(fixture.viewModel.choice(SettingRowId.QR_ANIMATION).enabled)
            assertFalse(fixture.viewModel.action(SettingRowId.QR_CUSTOM_IMAGE).enabled)
            assertFalse(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)
        }

    @Test
    fun textSizeChoiceWritesOnlyItsOwnWidgetFormatAndRefreshesWidgets() =
        runTest(main.dispatcher) {
            val local = LocalSettings(
                scheduleWidget = ScheduleWidgetSettings(
                    compact = CompactScheduleWidgetSettings(textSize = WidgetTextSize.LARGE)
                )
            )
            val compact = createFixture(page = SettingsPage.COMPACT_SCHEDULE_WIDGET, local = local)
            val full = createFixture(page = SettingsPage.FULL_SCHEDULE_WIDGET, local = local)
            advanceUntilIdle()

            val choice = compact.viewModel.choice(SettingRowId.COMPACT_WIDGET_TEXT_SIZE)
            assertEquals(WidgetTextSize.LARGE.name, choice.selectedOptionKey)
            assertEquals(WidgetTextSize.entries.map { it.name }, choice.options.map { it.key })
            assertEquals(
                WidgetTextSize.NORMAL.name,
                full.viewModel.choice(SettingRowId.FULL_WIDGET_TEXT_SIZE).selectedOptionKey
            )

            compact.viewModel.onChoiceChanged(SettingRowId.COMPACT_WIDGET_TEXT_SIZE, "huge")
            advanceUntilIdle()
            assertTrue(compact.repository.compactTextSizeRequests.isEmpty())
            assertEquals(0, compact.widgetRefresher.refreshCount)

            full.viewModel.onChoiceChanged(SettingRowId.FULL_WIDGET_TEXT_SIZE, WidgetTextSize.EXTRA_LARGE.name)
            advanceUntilIdle()

            assertEquals(listOf(WidgetTextSize.EXTRA_LARGE), full.repository.fullTextSizeRequests)
            assertTrue(full.repository.compactTextSizeRequests.isEmpty())
            assertEquals(
                WidgetTextSize.EXTRA_LARGE.name,
                full.viewModel.choice(SettingRowId.FULL_WIDGET_TEXT_SIZE).selectedOptionKey
            )
            assertEquals(1, full.widgetRefresher.refreshCount)
        }

    @Test
    fun qrAnimationChoicePersistsKnownOptionAndRefreshesWidgets() =
        runTest(main.dispatcher) {
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

            val choice = fixture.viewModel.choice(SettingRowId.QR_ANIMATION)
            assertEquals(QrAnimationType.FADE.name, choice.selectedOptionKey)
            assertEquals(QrAnimationType.entries.map { it.name }, choice.options.map { it.key })

            fixture.viewModel.onChoiceChanged(SettingRowId.QR_ANIMATION, "unknown")
            advanceUntilIdle()
            assertTrue(fixture.repository.qrAnimationRequests.isEmpty())
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onChoiceChanged(
                SettingRowId.QR_ANIMATION,
                QrAnimationType.NONE.name
            )
            advanceUntilIdle()

            assertEquals(listOf(QrAnimationType.NONE), fixture.repository.qrAnimationRequests)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun customSpoilerStateControlsActionsAndOptionalWidgetRefresh() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.QR_WIDGET)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)

            fixture.viewModel.onCustomSpoilerChanged(configured = true)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)
            assertEquals(
                UiText.Res(CoreRes.string.settings_qr_custom_image_selected),
                fixture.viewModel.action(SettingRowId.QR_CUSTOM_IMAGE).value
            )
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onCustomSpoilerChanged(configured = false, refreshWidgets = true)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun imageActionsAreDisabledWhileSavingWithoutDisablingAnimation() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.QR_WIDGET)
        advanceUntilIdle()
        fixture.viewModel.onCustomSpoilerChanged(configured = true, busy = true)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.action(SettingRowId.QR_CUSTOM_IMAGE).enabled)
        assertFalse(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)
        assertTrue(fixture.viewModel.choice(SettingRowId.QR_ANIMATION).enabled)
        fixture.viewModel.onCustomSpoilerChanged(configured = true, busy = false)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.action(SettingRowId.QR_CUSTOM_IMAGE).enabled)
        assertTrue(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)
    }

    @Test
    fun theQRPageOffersTheTileOnlyWhereItCanBeRequestedAndUntilItIsAdded() =
        runTest(main.dispatcher) {
            val unsupported = createFixture(page = SettingsPage.QR_WIDGET)
            advanceUntilIdle()
            assertTrue(unsupported.viewModel.allItems().none { it.id == SettingRowId.QR_TILE })

            val fixture = createFixture(page = SettingsPage.QR_WIDGET, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
            advanceUntilIdle()
            val first = fixture.viewModel.uiState.value.sections.first()
            assertNull(first.title)
            assertEquals(
                listOf(
                    SettingItem.Action(
                        id = SettingRowId.QR_TILE,
                        title = UiText.Res(Res.string.settings_qr_tile_title),
                        description = UiText.Res(Res.string.settings_qr_tile_description)
                    )
                ),
                first.items
            )

            fixture.repository.qrTileAdded.value = true
            advanceUntilIdle()
            assertTrue(fixture.viewModel.allItems().none { it.id == SettingRowId.QR_TILE })
        }

    @Test
    fun tappingTheTileRowAsksTheSystemToAddTheTile() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.QR_WIDGET, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
            val events = recordEvents(fixture)
            advanceUntilIdle()

            fixture.viewModel.onAction(SettingRowId.QR_TILE)
            advanceUntilIdle()

            assertEquals(listOf(SettingsEvent.RequestQrTile), events)
        }

    @Test
    fun theSystemAnswerRemembersAnAddedTileAndExplainsOnlyWhatTheUserShouldKnow() =
        runTest(main.dispatcher) {
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
                listOf(true) to listOf(SettingsEvent.ShowMessage(UiText.Res(Res.string.settings_qr_tile_already_added))),
                answer(QrTileAddResult.ALREADY_ADDED)
            )
            assertEquals(emptyList<Boolean>() to emptyList<SettingsEvent>(), answer(QrTileAddResult.NOT_ADDED))
            assertEquals(emptyList<Boolean>() to emptyList<SettingsEvent>(), answer(QrTileAddResult.IN_PROGRESS))
            assertEquals(
                emptyList<Boolean>() to listOf(SettingsEvent.ShowMessage(UiText.Res(Res.string.settings_qr_tile_failed))),
                answer(QrTileAddResult.FAILED)
            )
        }
}
