package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.ChoiceOption
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.ui.preview.SettingsPreviewData
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_all
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_friends
import dev.alllexey.itmowidgets.shared.feature.settings.settings_privacy_nobody
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `SettingsScreen` against the rules of the View renderer it replaced (the 13 cases of `SettingsRendererTest`): the
 * whole row is the target, unknown and disabled rows cannot change, restoring state emits no user action, rows keep
 * their identity across updates, the scroll position survives recreation, and long text fits at 320 dp and font 1.3.
 * Its dialogs keep the View dialogs' copy and buttons, survive recreation and never move a switch off its state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val toggles = mutableListOf<Pair<SettingRowId, Boolean>>()
    private val choices = mutableListOf<Pair<SettingRowId, String>>()
    private val pages = mutableListOf<SettingsPage>()
    private val commands = mutableListOf<SettingRowId>()
    private val actions = SettingsActions(
        onToggle = { id, checked -> toggles += id to checked },
        onChoice = { id, key -> choices += id to key },
        onNavigate = { pages += it },
        onAction = { commands += it },
    )

    private var state by mutableStateOf(SettingsPreviewData.Root)
    private var dark by mutableStateOf(false)
    private var colors: ColorSource by mutableStateOf(ColorSource.Platform)

    @Test
    fun `title value and footer changes keep the rows and a choice opens the latest item`() {
        val original = choice()
        val section = SettingSection(text("Раздел"), listOf(toggle(), original), text("Подсказка"))
        show(page(section))
        val toggleNode = nodeId(SettingRowId.SCHEDULE_CHANGES)
        val choiceNode = nodeId(SettingRowId.QR_ANIMATION)
        val updated = original.copy(value = text("Круг"), selectedOptionKey = "circle")

        update(page(section.copy(title = text("Новый раздел"), items = listOf(toggle(true), updated), footer = null)))
        compose.onNodeWithTag(SettingsTestTags.FOOTER).assertDoesNotExist()
        update(
            page(
                section.copy(
                    title = text("Новый раздел"),
                    items = listOf(toggle(true), updated),
                    footer = text("Новая подсказка"),
                ),
            ),
        )

        assertEquals(toggleNode, nodeId(SettingRowId.SCHEDULE_CHANGES))
        assertEquals(choiceNode, nodeId(SettingRowId.QR_ANIMATION))
        compose.onNodeWithTag(SettingRowId.SCHEDULE_CHANGES.key).assertIsOn()
        compose.onNodeWithText("Новый раздел").assertExists()
        compose.onNodeWithText("Новая подсказка").assertExists()
        compose.onNodeWithTag(SettingRowId.QR_ANIMATION.key).assert(hasText("Круг"))
        compose.onNodeWithTag(SettingRowId.QR_ANIMATION.key).performClick()
        // The dialog shows the latest row: its options with the current one marked.
        compose.onNode(dialogOption("Круг")).assertIsSelected()
        compose.onNode(dialogOption("Плавное исчезновение")).assertIsNotSelected().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf(SettingRowId.QR_ANIMATION to "fade"), choices)
        assertTrue(toggles.isEmpty())
    }

    @Test
    fun `unknown and disabled toggle rows cannot change state`() {
        show(page(SettingSection(null, listOf(toggle().copy(stateKnown = false)))))
        for (item in listOf(toggle().copy(stateKnown = false), toggle().copy(enabled = false))) {
            update(page(SettingSection(null, listOf(item))))
            val row = compose.onNodeWithTag(SettingRowId.SCHEDULE_CHANGES.key)
            row.assertIsNotEnabled()
            row.performClick()
        }
        compose.onNodeWithTag(SettingRowId.SCHEDULE_CHANGES.key).assertIsOff()

        assertTrue(toggles.isEmpty())
    }

    @Test
    fun `state restoration emits no user action and the whole row toggles once`() {
        val section = SettingSection(null, listOf(toggle(true)))
        show(page(section))
        update(page(section.copy()))
        compose.onNodeWithTag(SettingRowId.SCHEDULE_CHANGES.key).assertIsOn()
        assertTrue(toggles.isEmpty())

        compose.onNodeWithTag(SettingRowId.SCHEDULE_CHANGES.key).assertHasClickAction().performClick()
        assertEquals(listOf(SettingRowId.SCHEDULE_CHANGES to false), toggles)

        update(page(section.copy()))
        // The switch shows the state, which the tap alone did not change.
        compose.onNodeWithTag(SettingRowId.SCHEDULE_CHANGES.key).assertIsOn()
        assertEquals(1, toggles.size)
    }

    @Test
    fun `navigation uses the updated page and disabled rows ignore actions`() {
        val navigation = SettingItem.Navigation(SettingRowId.PAGE_SERVICES, text("Открыть"), page = SettingsPage.SERVICES)
        show(page(SettingSection(null, listOf(navigation))))
        val row = nodeId(SettingRowId.PAGE_SERVICES)
        update(page(SettingSection(null, listOf(navigation.copy(page = SettingsPage.PRIVACY)))))
        assertEquals(row, nodeId(SettingRowId.PAGE_SERVICES))
        compose.onNodeWithTag(SettingRowId.PAGE_SERVICES.key).performClick()
        assertEquals(listOf(SettingsPage.PRIVACY), pages)

        update(
            page(
                SettingSection(
                    null,
                    listOf(
                        navigation.copy(enabled = false),
                        choice().copy(enabled = false),
                        SettingItem.Action(SettingRowId.REFRESH_WIDGETS, text("Действие"), enabled = false),
                    ),
                ),
            ),
        )
        for (id in listOf(SettingRowId.PAGE_SERVICES, SettingRowId.QR_ANIMATION, SettingRowId.REFRESH_WIDGETS)) {
            compose.onNodeWithTag(id.key).assertIsNotEnabled().performClick()
        }
        assertEquals(1, pages.size)
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(choices.isEmpty())
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `saved state cannot overwrite the switch values the state holds`() {
        val restoration = StateRestorationTester(compose)
        val first = toggle(true)
        val second = toggle().copy(id = SettingRowId.SPORT_TEACHER_FILTER, title = text("Спорт"))
        state = page(SettingSection(null, listOf(first, second)))
        restoration.setContent { Themed { SettingsScreen(state, actions, widgetPreview = {}) } }

        state = page(SettingSection(null, listOf(first.copy(checked = false), second.copy(checked = true))))
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag(SettingRowId.SCHEDULE_CHANGES.key).assertIsOff()
        compose.onNodeWithTag(SettingRowId.SPORT_TEACHER_FILTER.key).assertIsOn()
        assertTrue(toggles.isEmpty())
    }

    @Test
    fun `a scrolled position survives recreation when the rows arrive after it`() {
        val rows = (1..30).map { index ->
            toggle(index % 2 == 0).copy(id = SettingRowId.entries[index - 1], title = text("Настройка $index"))
        }
        val full = page(SettingSection(null, rows))
        val restoration = StateRestorationTester(compose)
        lateinit var scroll: ScrollState
        state = full
        restoration.setContent {
            scroll = rememberScrollState()
            Themed { SettingsScreen(state, actions, widgetPreview = {}, scrollState = scroll) }
        }
        compose.runOnIdle { scroll.dispatchRawDelta(SCROLLED_PX) }
        val previous = compose.runOnIdle { scroll.value }
        assertTrue(previous > 0)

        state = full.copy(sections = emptyList(), loaded = false)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(SettingsTestTags.SCROLL).assertDoesNotExist()
        update(full)

        assertEquals(previous, compose.runOnIdle { scroll.value })
        compose.onNodeWithTag(SettingRowId.entries[0].key).assertIsOff()
        compose.onNodeWithTag(SettingRowId.entries[1].key).assertIsOn()
        assertTrue(toggles.isEmpty())
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp")
    fun `a long title and value fit a narrow screen at the large font`() {
        show(
            page(
                SettingSection(
                    null,
                    listOf(
                        choice().copy(title = text(LONG_TITLE), value = text(LONG_VALUE)),
                        toggle().copy(title = text(LONG_TOGGLE)),
                    ),
                ),
            ),
            fontScale = LARGE_FONT,
        )

        compose.assertNoTextOverflow()
        compose.assertTouchTargets()
        assertTitleAboveValue(LONG_TITLE, LONG_VALUE)
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp")
    fun `the version row shows the app version and fits a narrow screen in light and dark`() {
        show(SettingsPreviewData.Maintenance, fontScale = LARGE_FONT)
        for (scheme in listOf(false, true)) {
            dark = scheme
            colors = ColorSource.Seed(GREEN_SEED)
            compose.waitForIdle()
            compose.onNodeWithTag(SettingRowId.VERSION.key).assert(hasText(SettingsPreviewData.VERSION))
            compose.assertNoTextOverflow()
        }
    }

    @Test
    fun `root and detail pages keep their targets in light dark and a dynamic palette`() {
        show(SettingsPreviewData.Root)
        val schemes = listOf(false to ColorSource.Platform, true to ColorSource.Platform, false to ColorSource.Seed(GREEN_SEED))
        val details = listOf(
            SettingsPreviewData.Root,
            SettingsPreviewData.Privacy,
            SettingsPreviewData.QrWidget,
            SettingsPreviewData.Schedule,
        )
        for ((night, source) in schemes) for (detail in details) {
            dark = night
            colors = source
            update(detail)
            compose.onNodeWithTag(SettingsTestTags.SCROLL).assertExists()
            compose.assertTouchTargets()
            compose.assertNoTextOverflow()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp")
    fun `the schedule page fits a narrow screen with custom services off`() {
        show(SettingsPreviewData.Schedule, fontScale = LARGE_FONT)

        compose.onNodeWithTag(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN.key).assertIsEnabled().assertHasClickAction()
        compose.assertNoTextOverflow()
        compose.assertTouchTargets()
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp")
    fun `privacy loading error and disabled states show no switch and no made-up audience`() {
        show(SettingsPreviewData.PrivacyLoading, fontScale = LARGE_FONT)
        compose.onNodeWithTag(SettingsTestTags.PROGRESS).assertExists()
        compose.onNodeWithTag(SettingsTestTags.SCROLL).assertDoesNotExist()

        for (privacy in listOf(SettingsPreviewData.PrivacyError, SettingsPreviewData.PrivacyDisabled)) {
            update(privacy)
            compose.onNodeWithTag(SettingsTestTags.PROGRESS).assertDoesNotExist()
            for (id in SHARING_ROWS) {
                compose.onNodeWithTag(id.key).assertIsNotEnabled().assert(hasText(UNKNOWN))
            }
            compose.onAllNodes(IsSwitch).assertCountEquals(0)
            compose.assertNoTextOverflow()
            compose.assertTouchTargets()
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp")
    fun `privacy audiences fit a narrow screen in light dark and a dynamic palette`() {
        val audiences = privacyWith(schedule = "Все", sport = "Никто")
        show(audiences, fontScale = LARGE_FONT)
        for ((night, source) in listOf(false to ColorSource.Platform, true to ColorSource.Seed(GREEN_SEED))) {
            dark = night
            colors = source
            compose.waitForIdle()
            for ((id, value) in listOf(
                SettingRowId.SCHEDULE_SHARING to "Все",
                SettingRowId.SPORT_SHARING to "Никто",
                SettingRowId.FRIENDS_SHARING to "Все",
            )) {
                compose.onNodeWithTag(id.key).assertIsEnabled().assertHasClickAction().assert(hasText(value))
            }
            compose.onAllNodes(IsSwitch).assertCountEquals(0)
            compose.assertTouchTargets()
            compose.assertNoTextOverflow()
            assertTitleAboveValue("Кто видит моё расписание", "Все")
        }
    }

    @Test
    fun `privacy rows use the current audience and keep their identity when an update locks them`() {
        show(SettingsPreviewData.Privacy)
        val schedule = nodeId(SettingRowId.SCHEDULE_SHARING)
        val sport = nodeId(SettingRowId.SPORT_SHARING)
        compose.onNodeWithTag(SettingRowId.SCHEDULE_SHARING.key).assert(hasText("Друзья"))
        compose.onNodeWithTag(SettingRowId.SPORT_SHARING.key).assert(hasText("Друзья"))

        val options = SettingsPreviewData.Privacy.sections.flatMap { it.items }
            .filterIsInstance<SettingItem.Choice>().first { it.id == SettingRowId.SCHEDULE_SHARING }.options
        assertEquals(listOf("ALL", "FRIENDS", "NOBODY"), options.map(ChoiceOption::key))
        assertEquals(
            listOf(Res.string.settings_privacy_all, Res.string.settings_privacy_friends, Res.string.settings_privacy_nobody)
                .map { UiText.Res(it) },
            options.map(ChoiceOption::label),
        )
        compose.onNodeWithTag(SettingRowId.SCHEDULE_SHARING.key).performClick()
        compose.onNode(dialogOption("Друзья")).assertIsSelected()
        compose.onNode(dialogOption("Все")).assertIsNotSelected()
        compose.onNode(dialogOption("Никто")).assertIsNotSelected().performClick()
        assertEquals(listOf(SettingRowId.SCHEDULE_SHARING to "NOBODY"), choices)

        update(
            SettingsPreviewData.Privacy.copy(
                sections = SettingsPreviewData.Privacy.sections.map { section ->
                    section.copy(items = section.items.map { if (it is SettingItem.Choice) it.copy(enabled = false) else it })
                },
            ),
        )
        assertEquals(schedule, nodeId(SettingRowId.SCHEDULE_SHARING))
        assertEquals(sport, nodeId(SettingRowId.SPORT_SHARING))
        compose.onNodeWithTag(SettingRowId.SCHEDULE_SHARING.key).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(SettingRowId.SPORT_SHARING.key).assertIsNotEnabled().performClick()
        assertEquals(1, choices.size)
        compose.onNodeWithTag(SettingRowId.SCHEDULE_SHARING.key).assert(hasText("Друзья"))
    }

    @Test
    fun `turning the custom services on asks first and a cancel leaves the switch at its state`() {
        val services = SettingItem.Toggle(SettingRowId.CUSTOM_SERVICES, text("Подключение"), checked = false)
        show(page(SettingSection(null, listOf(services))))

        compose.onNodeWithTag(SettingRowId.CUSTOM_SERVICES.key).performClick()
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText(CONSENT_TITLE).assertExists()
        compose.onNode(dialogButton(CANCEL)).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag(SettingRowId.CUSTOM_SERVICES.key).assertIsOff()
        assertTrue(toggles.isEmpty())

        compose.onNodeWithTag(SettingRowId.CUSTOM_SERVICES.key).performClick()
        compose.onNode(dialogButton(CONSENT_ENABLE)).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(listOf(SettingRowId.CUSTOM_SERVICES to true), toggles)
        // Until the state says otherwise, a refused or failed opt-in still shows the switch off.
        compose.onNodeWithTag(SettingRowId.CUSTOM_SERVICES.key).assertIsOff()

        update(page(SettingSection(null, listOf(services.copy(checked = true)))))
        compose.onNodeWithTag(SettingRowId.CUSTOM_SERVICES.key).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(SettingRowId.CUSTOM_SERVICES to false, toggles.last())
    }

    @Test
    fun `the host dialogs hand their buttons to the host once and dismiss does nothing`() {
        val calls = mutableListOf<String>()
        val hostActions = actions.copy(
            onAllowBackgroundWork = { calls += "background" },
            onAllowCalendarAccess = { calls += "calendar" },
            onOpenAppSettings = { calls += "app-settings" },
        )
        val dialogs = SettingsDialogState()
        state = SettingsPreviewData.Schedule
        compose.setContent { Themed { SettingsScreen(state, hostActions, widgetPreview = {}, dialogs = dialogs) } }

        for ((dialog, button) in listOf(
            SettingsDialog.BackgroundWorkHint to "Разрешить",
            SettingsDialog.CalendarAccess(locked = false) to "Разрешить",
            SettingsDialog.CalendarAccess(locked = true) to "Открыть настройки",
        )) {
            compose.runOnIdle { dialogs.show(dialog) }
            compose.onNode(dialogButton(LATER)).performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle { dialogs.show(dialog) }
            compose.onNode(dialogButton(button)).performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
        }

        assertEquals(listOf("background", "calendar", "app-settings"), calls)
        assertTrue(toggles.isEmpty())
    }

    @Test
    fun `an open choice survives recreation and shows the current value`() {
        val restoration = StateRestorationTester(compose)
        state = SettingsPreviewData.Privacy
        restoration.setContent { Themed { SettingsScreen(state, actions, widgetPreview = {}) } }
        compose.onNodeWithTag(SettingRowId.SPORT_SHARING.key).performClick()
        compose.onNode(dialogOption("Друзья")).assertIsSelected()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNode(dialogOption("Друзья")).assertIsSelected()
        compose.onNode(dialogButton(CANCEL)).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertTrue(choices.isEmpty())
    }

    @Test
    fun `the widget preview is composed once above the rows and does not move when they change`() {
        var created = 0
        var released = 0
        state = SettingsPreviewData.QrWidget
        compose.setContent {
            Themed {
                SettingsScreen(state, actions, widgetPreview = { settings ->
                    DisposableEffect(Unit) {
                        created++
                        onDispose { released++ }
                    }
                    Box(Modifier.height(PREVIEW_HEIGHT).testTag(PREVIEW + (settings as WidgetPreviewSettings.Qr).appearance))
                })
            }
        }
        val qr = QrWidgetSettings()
        val bounds = compose.onNodeWithTag(PREVIEW + qr).getBoundsInRoot()
        val rowsTop = compose.onNodeWithTag(SettingsTestTags.SCROLL).getBoundsInRoot().top

        val changed = qr.copy(dynamicColors = false)
        update(
            SettingsPreviewData.QrWidget.copy(
                sections = SettingsPreviewData.QrWidget.sections.drop(1),
                previewSettings = WidgetPreviewSettings.Qr(changed),
            ),
        )

        assertEquals(bounds, compose.onNodeWithTag(PREVIEW + changed).getBoundsInRoot())
        assertEquals(rowsTop, compose.onNodeWithTag(SettingsTestTags.SCROLL).getBoundsInRoot().top)
        assertTrue(bounds.bottom <= rowsTop)
        assertEquals(1, created)
        assertEquals(0, released)

        update(SettingsPreviewData.Root)
        compose.onNodeWithTag(SettingsTestTags.WIDGET_PREVIEW).assertDoesNotExist()
        assertEquals(1, released)
    }

    private fun show(initial: SettingsUiState, fontScale: Float = 1f) {
        state = initial
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Themed { SettingsScreen(state, actions, widgetPreview = {}) }
            }
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) =
        ItmoTheme(dark = dark, colorSource = colors, content = content)

    private fun update(next: SettingsUiState) {
        state = next
        compose.waitForIdle()
    }

    /** A radio row of the open dialog. */
    private fun dialogOption(label: String) =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and hasAnyAncestor(isDialog())

    private fun dialogButton(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun nodeId(id: SettingRowId): Int = compose.onNodeWithTag(id.key).fetchSemanticsNode().id

    /** The value sits below its title in the same row, never over it; the first row with [value] is [title]'s. */
    private fun assertTitleAboveValue(title: String, value: String) {
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).getBoundsInRoot()
        val valueBounds = compose.onAllNodes(hasText(value), useUnmergedTree = true)[0].getBoundsInRoot()
        assertFalse("$title and $value overlap", valueBounds.top < titleBounds.bottom)
    }

    private fun page(vararg sections: SettingSection) =
        SettingsUiState(SettingsPage.QR_WIDGET, sections.toList(), loaded = true)

    private fun privacyWith(schedule: String, sport: String): SettingsUiState {
        val values = mapOf(SettingRowId.SCHEDULE_SHARING to schedule, SettingRowId.SPORT_SHARING to sport)
        return SettingsPreviewData.Privacy.copy(
            sections = SettingsPreviewData.Privacy.sections.map { section ->
                section.copy(
                    items = section.items.map { item ->
                        val value = values[item.id]
                        if (item is SettingItem.Choice && value != null) item.copy(value = text(value)) else item
                    },
                )
            },
        )
    }

    private fun toggle(checked: Boolean = false) =
        SettingItem.Toggle(SettingRowId.SCHEDULE_CHANGES, text("Расписание"), checked = checked)

    private fun choice() = SettingItem.Choice(
        SettingRowId.QR_ANIMATION,
        text("Анимация"),
        text("Плавное исчезновение"),
        listOf(ChoiceOption("fade", text("Плавное исчезновение")), ChoiceOption("circle", text("Круг"))),
        "fade",
    )

    private fun text(value: String) = UiText.Dynamic(value)

    private companion object {
        const val LONG_TITLE = "Анимация скрытия и раскрытия изображения QR-кода"
        const val LONG_VALUE = "Плавное исчезновение пользовательского изображения"
        const val LONG_TOGGLE = "Показывать расписание следующего дня после окончания сегодняшних занятий"
        const val UNKNOWN = "Не загрузилось"
        const val CANCEL = "Отмена"
        const val LATER = "Не сейчас"
        const val CONSENT_TITLE = "Подключиться к ITMO.Widgets?"
        const val CONSENT_ENABLE = "Подключиться"
        const val LARGE_FONT = 1.3f
        const val SCROLLED_PX = 420f
        const val PREVIEW = "preview:"
        const val GREEN_SEED = 0xFF087F5B.toInt()
        val PREVIEW_HEIGHT = 120.dp
        val SHARING_ROWS = listOf(SettingRowId.SCHEDULE_SHARING, SettingRowId.SPORT_SHARING, SettingRowId.FRIENDS_SHARING)
        val IsSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    }
}
