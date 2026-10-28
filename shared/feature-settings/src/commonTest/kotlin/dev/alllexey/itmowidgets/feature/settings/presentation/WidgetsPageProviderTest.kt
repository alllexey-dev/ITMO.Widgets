package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
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

/** The widgets gallery built and handled by [WidgetsPageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetsPageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun eachFormatsSheetHasOnlyItsOwnControlsAndTheTeacherIsShownAsTheInverseOfHidden() = runTest(main.dispatcher) {
        val local = LocalSettings(scheduleWidget = ScheduleWidgetSettings(full = FullScheduleWidgetSettings(hideTeacher = false)))
        val fixture = createFixture(page = SettingsPage.WIDGETS, local = local)
        advanceUntilIdle()

        fun ids(format: WidgetFormat) = fixture.viewModel.card(format).settings.flatMap { it.items }.map { it.id }
        assertEquals(listOf(SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY, SettingRowId.COMPACT_WIDGET_TEXT_SIZE), ids(WidgetFormat.NEXT))
        assertEquals(
            listOf(
                SettingRowId.FULL_WIDGET_HIDE_PAST,
                SettingRowId.FULL_WIDGET_SHOW_TEACHER,
                SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                SettingRowId.FULL_WIDGET_TEXT_SIZE
            ),
            ids(WidgetFormat.DAY)
        )
        assertEquals(ids(WidgetFormat.DAY), ids(WidgetFormat.LIST))
        assertEquals(ids(WidgetFormat.DAY), ids(WidgetFormat.CARDS))
        assertEquals(emptyList(), ids(WidgetFormat.SPORT))
        assertEquals(listOf(SettingRowId.WIDGETS_FOLLOW_THEME), fixture.viewModel.uiState.value.sections.flatMap { it.items }.map { it.id })
        assertTrue(fixture.viewModel.toggle(SettingRowId.FULL_WIDGET_SHOW_TEACHER).checked)

        fixture.viewModel.onToggleChanged(SettingRowId.FULL_WIDGET_SHOW_TEACHER, false)
        advanceUntilIdle()
        assertEquals(listOf(true), fixture.repository.fullWidgetTeacherHiddenRequests)
        assertEquals(1, fixture.widgetRefresher.refreshCount)
    }

    @Test
    fun theCardsPreviewTheirFormatWithTheStoredValuesAndOnlyTheGalleryHasThem() =
        runTest(main.dispatcher) {
            val local = LocalSettings(
                qrWidget = QrWidgetSettings(dynamicColors = false, animationType = QrAnimationType.NONE),
                scheduleWidget = ScheduleWidgetSettings(full = FullScheduleWidgetSettings(hideTeacher = true)),
                // The widgets' own colours: the previews carry no theme.
                widgetsFollowTheme = false
            )
            (SettingsPage.entries - setOf(SettingsPage.SERVICES, SettingsPage.NOTIFICATIONS, SettingsPage.APPEARANCE)).forEach { page ->
                val fixture = createFixture(page = page, local = local)
                advanceUntilIdle()
                assertEquals(page == SettingsPage.WIDGETS, fixture.viewModel.uiState.value.gallery != null, "$page")
            }
            val fixture = createFixture(page = SettingsPage.WIDGETS, local = local)
            advanceUntilIdle()
            assertEquals(
                listOf(
                    WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.COMPACT),
                    WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.FULL),
                    WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.CARDS),
                    WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.LIST),
                    WidgetPreviewSettings.Sport(),
                    WidgetPreviewSettings.Qr(local.qrWidget),
                ),
                checkNotNull(fixture.viewModel.uiState.value.gallery).cards.map { it.preview }
            )
        }

    @Test
    fun previewsCarryTheAccentOnlyWhileTheWidgetsFollowTheTheme() =
        runTest(main.dispatcher) {
            val themed = LocalSettings(theme = ThemeSpec(accent = AccentColor.TEAL), widgetsFollowTheme = true)
            val on = createFixture(page = SettingsPage.WIDGETS, local = themed)
            val off = createFixture(page = SettingsPage.WIDGETS, local = themed.copy(widgetsFollowTheme = false))
            advanceUntilIdle()
            assertTrue(checkNotNull(on.viewModel.uiState.value.gallery).cards.all { it.preview.theme == ThemeSpec(accent = AccentColor.TEAL) })
            assertTrue(checkNotNull(off.viewModel.uiState.value.gallery).cards.all { it.preview.theme == null })
        }

    @Test
    fun placedWidgetsTurnAddIntoOnScreenAndCountInTheSubtitle() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.WIDGETS)
            advanceUntilIdle()
            assertNull(checkNotNull(fixture.viewModel.uiState.value.gallery).subtitle)
            assertTrue(checkNotNull(fixture.viewModel.uiState.value.gallery).cards.all { it.action == WidgetCardAction.ADD })

            fixture.viewModel.onPlacedWidgets(WidgetFormat.DAY, 1)
            fixture.viewModel.onPlacedWidgets(WidgetFormat.PASS, 1)
            advanceUntilIdle()

            val gallery = checkNotNull(fixture.viewModel.uiState.value.gallery)
            assertEquals(UiText.Res(SettingsStrings.settings_widgets_placed, listOf(2)), gallery.subtitle)
            assertEquals(
                listOf(WidgetCardAction.ADD, WidgetCardAction.ON_SCREEN, WidgetCardAction.ADD, WidgetCardAction.ADD, WidgetCardAction.ADD, WidgetCardAction.ON_SCREEN),
                gallery.cards.map { it.action }
            )
        }

    @Test
    fun theWidgetsOfTheOverhaulAreBetaAndTheClassicListAndThePassAreNot() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.WIDGETS)
            advanceUntilIdle()
            val cards = checkNotNull(fixture.viewModel.uiState.value.gallery).cards
            assertEquals(listOf(WidgetFormat.NEXT, WidgetFormat.DAY, WidgetFormat.CARDS, WidgetFormat.SPORT), cards.filter { it.beta }.map { it.format })
            assertEquals(listOf(WidgetFormat.LIST, WidgetFormat.PASS), cards.filterNot { it.beta }.map { it.format })
        }

    @Test
    fun theGalleryWaitsForStorageAndFollowsPersistedToggleAndAnimationChanges() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.WIDGETS,
                local = LocalSettings(widgetsFollowTheme = false),
                localInitiallyAvailable = false
            )
            advanceUntilIdle()
            assertEquals(null, fixture.viewModel.uiState.value.gallery)
            fixture.repository.publishLocalSettings()
            advanceUntilIdle()
            fixture.viewModel.onToggleChanged(SettingRowId.QR_DYNAMIC_COLORS, false)
            fixture.viewModel.onChoiceChanged(SettingRowId.QR_ANIMATION, QrAnimationType.FADE.name)
            advanceUntilIdle()
            assertEquals(
                WidgetPreviewSettings.Qr(QrWidgetSettings(dynamicColors = false, animationType = QrAnimationType.FADE)),
                fixture.viewModel.card(WidgetFormat.PASS).preview
            )
        }

    @Test
    fun disabledSpoilerKeepsItsDependentControlsVisibleButUnavailable() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.WIDGETS,
                local = LocalSettings(qrWidget = QrWidgetSettings(spoilerEnabled = false))
            )
            fixture.viewModel.onCustomSpoilerChanged(configured = true)
            advanceUntilIdle()

            assertEquals(5, fixture.viewModel.card(WidgetFormat.PASS).settings.flatMap { it.items }.size)
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
            val compact = createFixture(page = SettingsPage.WIDGETS, local = local)
            val full = createFixture(page = SettingsPage.WIDGETS, local = local)
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
                page = SettingsPage.WIDGETS,
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
            val fixture = createFixture(page = SettingsPage.WIDGETS)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)

            fixture.viewModel.onCustomSpoilerChanged(configured = true)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.action(SettingRowId.QR_RESET_IMAGE).enabled)
            assertEquals(
                UiText.Res(CoreStrings.settings_qr_custom_image_selected),
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
        val fixture = createFixture(page = SettingsPage.WIDGETS)
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
            val unsupported = createFixture(page = SettingsPage.WIDGETS)
            advanceUntilIdle()
            assertTrue(unsupported.viewModel.allItems().none { it.id == SettingRowId.QR_TILE })

            val fixture = createFixture(page = SettingsPage.WIDGETS, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
            advanceUntilIdle()
            val first = fixture.viewModel.card(WidgetFormat.PASS).settings.last()
            assertNull(first.title)
            assertEquals(
                listOf(
                    SettingItem.Action(
                        id = SettingRowId.QR_TILE,
                        title = UiText.Res(SettingsStrings.settings_qr_tile_title),
                        description = UiText.Res(SettingsStrings.settings_qr_tile_description)
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
            val fixture = createFixture(page = SettingsPage.WIDGETS, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
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
                val fixture = createFixture(page = SettingsPage.WIDGETS, tileAccess = FakeQuickSettingsTileAccess(canRequest = true))
                val events = recordEvents(fixture)
                advanceUntilIdle()
                fixture.viewModel.onQrTileResult(result)
                advanceUntilIdle()
                return fixture.repository.qrTileAddedRequests.toList() to events.toList()
            }

            assertEquals(listOf(true) to emptyList<SettingsEvent>(), answer(QrTileAddResult.ADDED))
            assertEquals(
                listOf(true) to listOf(SettingsEvent.ShowMessage(UiText.Res(SettingsStrings.settings_qr_tile_already_added))),
                answer(QrTileAddResult.ALREADY_ADDED)
            )
            assertEquals(emptyList<Boolean>() to emptyList<SettingsEvent>(), answer(QrTileAddResult.NOT_ADDED))
            assertEquals(emptyList<Boolean>() to emptyList<SettingsEvent>(), answer(QrTileAddResult.IN_PROGRESS))
            assertEquals(
                emptyList<Boolean>() to listOf(SettingsEvent.ShowMessage(UiText.Res(SettingsStrings.settings_qr_tile_failed))),
                answer(QrTileAddResult.FAILED)
            )
        }
}
