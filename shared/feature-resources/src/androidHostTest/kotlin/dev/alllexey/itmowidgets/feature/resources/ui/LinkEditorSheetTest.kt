package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.robolectric.annotation.Config

/**
 * The editor cases of the deleted `SubjectLinksVisualTest` on the Compose sheet, through the real view model where
 * the case saves: nested flows named over their kind of classes, the category guessed from the site until one is
 * picked, only the audiences still offered, the missing connection, the narrow screen at a large font. The clipboard
 * link a new sheet starts with is the host's (`LinkEditorPasteTest` in `:app`); the looks live in the
 * `LinkEditorSheetContent_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class LinkEditorSheetTest {

    private val repository = FakeSubjectLinksRepository().apply {
        state.value = SubjectLinksState.Content(SubjectLinksSamples.snapshot)
    }
    private val events = mutableListOf<String>()

    @Test
    fun editorListsEveryNestedFlowWithItsKindOfClasses() = runComposeUiTest {
        setContent { Route(model(linkId = "own-scores")) }

        onNodeWithText("Изменить ссылку").assert(isHeading())
        assertEquals(
            listOf("Все\nПосле проверки", "ФИЗ ПИИКТ 3\nЛекция", "ФИЗ ПИИКТ 3.2\nПрактика", "ФИЗ ПИИКТ 3.2.1\nЛабораторная",
                "Только я"),
            audienceRows(),
        )
        audience("ФИЗ ПИИКТ 3.2").assertIsSelected()
        onNodeWithTag(LinkEditorSheetTestTags.CONNECTION_HINT).assertDoesNotExist()
        assertTouchTargets()
        assertNoTextOverflow()

        audience("ФИЗ ПИИКТ 3.2.1").performClick()
        audience("ФИЗ ПИИКТ 3.2.1").assertIsSelected()
        audience("ФИЗ ПИИКТ 3.2").assertIsNotSelected()
        onNodeWithTag(LinkEditorSheetTestTags.SAVE).performClick()
        waitForIdle()

        val saved = checkNotNull(repository.lastSave)
        assertEquals("own-scores", saved.id)
        assertEquals(LinkVisibility.FLOW, saved.visibility)
        assertEquals(7103L, saved.flowId)
        assertEquals(listOf("done"), events)
    }

    @Test
    fun editorGuessesTheCategoryAndOffersOnlyAvailableAudiences() = runComposeUiTest {
        val lecture = SubjectLinksSamples.snapshot.audiences.first()
        show(SubjectLinksSamples.snapshot.copy(audiences = listOf(lecture)))
        setContent { Route(model()) }

        onNodeWithText("Новая ссылка").assert(isHeading())
        url().performTextReplacement("https://docs.google.com/spreadsheets/d/synthetic")
        chip(LinkCategory.SCORES).assertIsSelected()
        onNodeWithTag(LinkEditorSheetTestTags.TITLE).assert(hasText("Таблица баллов"))
        assertEquals(listOf("Все\nПосле проверки", "ФИЗ ПИИКТ 3\nЛекция", "Только я"), audienceRows())
        audience("Только я").assertIsSelected()
        audience("ФИЗ ПИИКТ 3").performClick()
        audience("ФИЗ ПИИКТ 3").assertIsSelected()
        assertTouchTargets()

        // The site keeps suggesting until a chip is picked by hand.
        url().performTextReplacement("https://youtu.be/synthetic")
        chip(LinkCategory.RECORDINGS).assertIsSelected()
        chip(LinkCategory.EXAM).performClick()
        url().performTextReplacement("https://github.com/synthetic")
        chip(LinkCategory.EXAM).assertIsSelected()
        onNodeWithTag(LinkEditorSheetTestTags.TITLE).assert(hasText("К экзамену"))

        // The chosen flow is gone from the schedule, so the choice falls back to only me.
        show(SubjectLinksSamples.snapshot.copy(audiences = emptyList(), premoderation = false))
        waitForIdle()
        assertEquals(listOf("Все", "Только я"), audienceRows())
        audience("Только я").assertIsSelected()
        audience("Все").performClick()
        onNodeWithTag(LinkEditorSheetTestTags.SAVE).performClick()
        waitForIdle()

        val saved = checkNotNull(repository.lastSave)
        assertEquals("https://github.com/synthetic", saved.url)
        assertEquals(LinkCategory.EXAM, saved.category)
        assertEquals(LinkVisibility.ALL, saved.visibility)
        assertEquals(listOf("done"), events)
    }

    @Test
    fun editorWithoutTheConnectionKeepsTheLinkPrivate() = runComposeUiTest {
        show(SubjectLinksSamples.snapshot.copy(shared = emptyList(), previous = emptyList(), audiences = emptyList(),
            servicesEnabled = false))
        setContent { Route(model()) }

        assertEquals(listOf("Только я"), audienceRows())
        audience("Только я").assertIsSelected()
        onNodeWithTag(LinkEditorSheetTestTags.CONNECTION_HINT)
            .assert(hasText("Поделиться можно с подключением к ITMO.Widgets"))
        onNodeWithTag(LinkEditorSheetTestTags.SAVE).assertIsNotEnabled()

        url().performTextReplacement(LinkEditorSamples.CHAT_URL)
        chip(LinkCategory.CHAT).assertIsSelected()
        onNodeWithTag(LinkEditorSheetTestTags.SAVE).assertIsEnabled().performClick()
        waitForIdle()

        val saved = checkNotNull(repository.lastSave)
        assertEquals(LinkEditorSamples.CHAT_URL, saved.url)
        assertEquals(LinkVisibility.PRIVATE, saved.visibility)
        assertEquals(null, saved.flowId)
    }

    @Test
    @Config(qualifiers = "w320dp-h4000dp")
    fun editorFitsAtLargeFontOnANarrowScreen() = runComposeUiTest {
        val long = LinkEditorSamples.edit.copy(title = SubjectLinksSamples.LONG_TITLE)
        setContent { Sheet(long, width = NARROW_WIDTH, fontScale = NARROW_FONT_SCALE) }

        onNodeWithText("Изменить ссылку").assertExists()
        assertEquals(5, audienceRows().size)
        assertNoTextOverflow()
        assertTouchTargets()
    }

    @Test
    fun theFieldsReachTheHostAndTheirErrorsSayWhy() = runComposeUiTest {
        var state by mutableStateOf(LinkEditorSamples.new)
        setContent { Sheet(state) }

        onNodeWithContentDescription("Очистить ссылку").performClick()
        onNodeWithTag(LinkEditorSheetTestTags.TITLE).performTextReplacement("Первая строка\nвторая")
        chip(LinkCategory.MATERIALS).performClick()
        audience("ФИЗ ПИИКТ 3.2").performClick()
        onNodeWithTag(LinkEditorSheetTestTags.SAVE).performClick()
        assertEquals(listOf("url:", "title:Первая строка вторая", "category:MATERIALS", "audience:7102", "save"), events)

        // An error takes the place of the clear icon and says what is wrong.
        state = LinkEditorSamples.urlError
        waitForIdle()
        onNodeWithText("Нужна ссылка https://").assertExists()
        onNodeWithContentDescription("Очистить ссылку").assertDoesNotExist()

        state = LinkEditorSamples.longTitle
        waitForIdle()
        onNodeWithText("Не больше 120 символов").assertExists()
    }

    @Test
    fun saveWaitsForAnAddressAndACategoryAndIgnoresTapsWhileSaving() = runComposeUiTest {
        var state by mutableStateOf(LinkEditorUiState(url = "https://example.org"))
        setContent { Sheet(state) }

        onNodeWithTag(LinkEditorSheetTestTags.SAVE).assertIsNotEnabled()
        onNodeWithText("Название").assertExists()

        state = LinkEditorSamples.saving
        waitForIdle()
        onNodeWithTag(LinkEditorSheetTestTags.SAVE).assertIsEnabled().performClick()
        assertTrue(events.isEmpty())

        state = state.copy(saving = false)
        waitForIdle()
        onNodeWithTag(LinkEditorSheetTestTags.SAVE).performClick()
        assertEquals(listOf("save"), events)
    }

    private fun ComposeUiTest.url(): SemanticsNodeInteraction = onNodeWithTag(LinkEditorSheetTestTags.URL)

    /** A chip of the row, scrolled into view first: the row composes only the chips it shows. */
    private fun ComposeUiTest.chip(category: LinkCategory): SemanticsNodeInteraction {
        onNodeWithTag(LinkEditorSheetTestTags.CATEGORIES)
            .performScrollToNode(hasTestTag(LinkEditorSheetTestTags.category(category)))
        return onNodeWithTag(LinkEditorSheetTestTags.category(category))
    }

    /** The «Кто видит» row whose first line is [title]. */
    private fun ComposeUiTest.audience(title: String): SemanticsNodeInteraction =
        onNode(isAudienceRow() and hasText(title))

    /** Each row's lines joined by a line break, top to bottom. */
    private fun ComposeUiTest.audienceRows(): List<String> = onAllNodes(isAudienceRow()).fetchSemanticsNodes()
        .sortedBy { it.boundsInRoot.top }
        .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("\n") { it.text } }

    private fun isAudienceRow() = hasAnyAncestor(hasTestTag(LinkEditorSheetTestTags.AUDIENCES)) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun show(snapshot: SubjectLinksSnapshot) {
        repository.state.value = SubjectLinksState.Content(snapshot)
    }

    private fun model(linkId: String? = null) = LinkEditorViewModel(
        SavedStateHandle(
            buildMap {
                put(SubjectLinksArgs.SUBJECT_ID, SubjectLinksSamples.scope.subjectId)
                put(SubjectLinksArgs.SUBJECT_NAME, SubjectLinksSamples.scope.subjectName)
                put(SubjectLinksArgs.PERIOD_KEY, SubjectLinksSamples.scope.periodKey)
                if (linkId != null) put(SubjectLinksArgs.LINK_ID, linkId)
            }
        ),
        repository,
    )

    @Composable
    private fun Route(model: LinkEditorViewModel) = Frame(WIDE_WIDTH, 1f) {
        LinkEditorSheetRoute(model, onDone = { events += "done" }, onFailure = { events += "failure:$it" })
    }

    @Composable
    private fun Sheet(state: LinkEditorUiState, width: Dp = WIDE_WIDTH, fontScale: Float = 1f) = Frame(width, fontScale) {
        LinkEditorSheetContent(
            state,
            LinkEditorActions(
                onUrlChanged = { events += "url:$it" },
                onCategorySelected = { events += "category:${it.name}" },
                onTitleChanged = { events += "title:$it" },
                onAudienceSelected = { events += "audience:${it.flowId}" },
                onSave = { events += "save" },
            ),
        )
    }

    @Composable
    private fun Frame(width: Dp, fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Box(Modifier.size(width, SHEET_HEIGHT)) { content() }
            }
        }
    }

    private companion object {
        val WIDE_WIDTH = 411.dp
        val NARROW_WIDTH = 320.dp
        const val NARROW_FONT_SCALE = 1.3f
        val SHEET_HEIGHT = 2000.dp
    }
}
