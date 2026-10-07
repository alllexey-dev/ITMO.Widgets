package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookUiState
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples.ALGORITHMS_ID
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples.DESIGN_ID
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples.HISTORY_ID
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples.MATH_ID
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples.PE_ID
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cases of the deleted list half of `RecordbookVisualTest` and of `RecordbookBarsVisualTest` on the Compose list:
 * the rows of the middle of the semester and of the session, the BARS chip, the period button, the states with their
 * retry, the snackbars with both actions and the scroll on a period switch. The looks live in the
 * `RecordbookScreen_*` goldens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class RecordbookScreenTest {

    private val periods = mutableListOf<Pair<List<RecordbookProgram>, RecordbookSelection>>()
    private val bars = mutableListOf<Boolean>()
    private val opened = mutableListOf<Pair<RecordbookSelection, RecordbookSubject>>()
    private var refreshes = 0
    private var retries = 0
    private var logins = 0

    private var locale: Locale? = null

    /** Compose plurals follow the process locale, which the app keeps Russian; the JVM's is not. */
    @BeforeTest
    fun russian() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ru"))
    }

    @AfterTest
    fun restoreLocale() {
        locale?.let(Locale::setDefault)
    }

    @Test
    fun theMiddleOfTheSemesterPutsReasonsFirstWithDotsBarsAndTheSheetTotal() = runComposeUiTest {
        setContent { Screen(RecordbookPreviewSamples.content()) }

        onNodeWithText("Требуют внимания").assertExists()
        onNodeWithText("Дисциплины").assertExists()
        onNodeWithTag(RecordbookTestTags.SUMMARY).assertDoesNotExist()
        description(MATH_ID).let {
            assertTrue(it.startsWith("Новое. ${RecordbookPreviewSamples.MATH}. Контрольная работа 1 ниже минимума"), it)
            assertTrue(it.endsWith("74 из 100 баллов"), it)
        }
        assertEquals(
            "${RecordbookPreviewSamples.PE}. Спорт: ещё 32 балла. 68 из 100 баллов",
            description(PE_ID),
        )
        assertEquals(
            "${RecordbookPreviewSamples.ALGORITHMS}. Экзамен · нет в БАРС. 58,5 из 100 баллов",
            description(ALGORITHMS_ID),
        )
        assertTrue(description(DESIGN_ID).startsWith("Новое. "))
        onNodeWithTag(RecordbookTestTags.LIST).performScrollToNode(hasTestTag(RecordbookTestTags.subject(HISTORY_ID)))
        assertEquals(
            "${RecordbookPreviewSamples.HISTORY}. Экзамен · нет в БАРС. Из таблицы: 41,5",
            description(HISTORY_ID),
        )
        onNodeWithTag(RecordbookTestTags.SHEET_MARK, useUnmergedTree = true).assertExists()
        assertTrue(
            onAllNodesWithTag(RecordbookTestTags.SCORE, useUnmergedTree = true).fetchSemanticsNodes()
                .any { it.config[SemanticsProperties.Text].single().text == "41,5" },
        )
        assertTouchTargets()
    }

    @Test
    fun theSessionShowsThePassCountAndBadgesWithoutBars() = runComposeUiTest {
        setContent { Screen(RecordbookPreviewSamples.session()) }

        onNodeWithText("Сдано 4 из 6", useUnmergedTree = true).assertExists()
        assertEquals("${RecordbookPreviewSamples.MATH}. Пересдача. 2FX", description(MATH_ID))
        onNodeWithTag(RecordbookTestTags.LIST).performScrollToNode(hasTestTag(RecordbookTestTags.subject(HISTORY_ID)))
        assertEquals("${RecordbookPreviewSamples.HISTORY}. Неявка. Неявка", description(HISTORY_ID))
        val badges = onAllNodesWithTag(RecordbookTestTags.GRADE, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.Text].single().text }
        assertTrue(badges.containsAll(listOf("2FX", "—")), "$badges")
        onNodeWithTag(RecordbookTestTags.LIST).performScrollToNode(hasTestTag(RecordbookTestTags.subject(DESIGN_ID)))
        val all = onAllNodesWithTag(RecordbookTestTags.GRADE, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.Text].single().text }
        assertTrue(all.containsAll(listOf("4C", "5A", "Зачёт")), "$all")
        onNodeWithTag(RecordbookTestTags.PROGRESS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theBarsChipReportsOnlyTheUsersToggle() = runComposeUiTest {
        var state: RecordbookUiState by mutableStateOf(RecordbookPreviewSamples.content())
        setContent { Screen(state) }

        onNodeWithTag(RecordbookTestTags.BARS).assertIsSelected().performClick()
        state = RecordbookPreviewSamples.content().copy(barsEnabled = false)
        waitForIdle()
        onNodeWithTag(RecordbookTestTags.BARS).performClick()
        // A state that changes the chip on its own reports nothing.
        state = RecordbookPreviewSamples.content()
        waitForIdle()

        assertEquals(listOf(false, true), bars)
    }

    @Test
    fun thePeriodButtonOpensThePickerOnlyOnceThePeriodsAreKnown() = runComposeUiTest {
        var state: RecordbookUiState by mutableStateOf(RecordbookUiState.Loading())
        setContent { Screen(state) }

        onNodeWithTag(RecordbookTestTags.PERIOD).assertIsNotEnabled()
        onNodeWithText("Учебный период", useUnmergedTree = true).assertExists()
        onNodeWithTag(RecordbookTestTags.LOADING).assertExists()

        state = RecordbookPreviewSamples.loading()
        waitForIdle()
        onNodeWithText("1 курс · 2 семестр", useUnmergedTree = true).assertExists()
        onNodeWithTag(RecordbookTestTags.YEAR).assertTextEquals("2025/2026")
        onNodeWithTag(RecordbookTestTags.PERIOD).assertIsEnabled().performClick()

        assertEquals(listOf(listOf(RecordbookPreviewSamples.program) to RecordbookPreviewSamples.selection), periods)
    }

    @Test
    fun aRowOpensItsSubjectWithTheSelection() = runComposeUiTest {
        setContent { Screen(RecordbookPreviewSamples.content()) }

        onNodeWithTag(RecordbookTestTags.subject(ALGORITHMS_ID)).performClick()

        assertEquals(RecordbookPreviewSamples.selection, opened.single().first)
        assertEquals(ALGORITHMS_ID, opened.single().second.entryId)
    }

    @Test
    fun theErrorAndEmptyStatesRetryInTheListsPlace() = runComposeUiTest {
        var state: RecordbookUiState by mutableStateOf(RecordbookPreviewSamples.error())
        setContent { Screen(state) }

        onNodeWithText("Не удалось загрузить", useUnmergedTree = true).assertExists()
        onNodeWithText("Нет связи. Проверьте интернет.", useUnmergedTree = true).assertExists()
        onNodeWithTag(RecordbookTestTags.STATE_ACTION).performClick()
        assertTouchTargets()

        state = RecordbookPreviewSamples.empty()
        waitForIdle()
        onNodeWithText("Дисциплин нет", useUnmergedTree = true).assertExists()
        onNodeWithTag(RecordbookTestTags.LIST).assertDoesNotExist()
        onNodeWithTag(RecordbookTestTags.STATE_ACTION).performClick()

        state = RecordbookUiState.Empty()
        waitForIdle()
        onNodeWithTag(RecordbookTestTags.PERIOD).assertIsNotEnabled()
        onNodeWithTag(RecordbookTestTags.STATE_ACTION).performClick()

        assertEquals(3, retries)
    }

    @Test
    fun aFailedRefreshOffersARetryInASnackbar() = runComposeUiTest {
        setContent { WithSnackbars(RecordbookPreviewSamples.content().copy(refreshError = AppError.Network)) }

        clickSnackbarAction("Не удалось обновить, показываем прежние данные. Нет связи. Проверьте интернет.", "Повторить")

        assertEquals(1, retries)
        assertEquals(0, logins)
    }

    @Test
    fun anEndedBarsSessionOffersTheSignIn() = runComposeUiTest {
        setContent { WithSnackbars(RecordbookPreviewSamples.content().copy(barsError = AppError.Unauthorized)) }

        clickSnackbarAction("Сессия БАРС закончилась, нужно войти снова.", "Войти в БАРС")

        assertEquals(1, logins)
        assertEquals(0, retries)
    }

    @Test
    fun anotherBarsFailureOffersARetry() = runComposeUiTest {
        setContent { WithSnackbars(RecordbookPreviewSamples.content().copy(barsError = AppError.Network)) }

        clickSnackbarAction("БАРС недоступен, показываем My ITMO. Нет связи. Проверьте интернет.", "Повторить")

        assertEquals(1, retries)
        assertEquals(0, logins)
    }

    @Test
    fun anotherPeriodStartsAtTheTopAndTheSameOneKeepsThePosition() = runComposeUiTest {
        val list = LazyListState()
        var state: RecordbookUiState by mutableStateOf(RecordbookPreviewSamples.content())
        setContent { Screen(state, list) }

        onNodeWithTag(RecordbookTestTags.LIST).performScrollToNode(hasTestTag(RecordbookTestTags.subject(HISTORY_ID)))
        val scrolled = list.firstVisibleItemIndex
        assertTrue(scrolled > 0)

        // A refresh of the same period keeps the place.
        state = RecordbookPreviewSamples.content().copy(refreshing = true)
        waitForIdle()
        assertEquals(scrolled, list.firstVisibleItemIndex)

        val autumn = RecordbookSelection(RecordbookPreviewSamples.program, RecordbookPreviewSamples.program.periods[1])
        state = RecordbookPreviewSamples.content().copy(selection = autumn)
        waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0, list.firstVisibleItemScrollOffset)
    }

    @Test
    fun longNamesWrapAndNothingElseIsCutAtTheNarrowLargeFont() = runComposeUiTest {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT)) {
                ItmoTheme {
                    Box(Modifier.size(320.dp, 891.dp)) {
                        Content(RecordbookPreviewSamples.content())
                    }
                }
            }
        }

        assertTouchTargets()
        // Subject names may end in an ellipsis on their second line, as the View rows did; nothing else may.
        val names = RecordbookPreviewSamples.content().subjects.map { it.name }.toSet()
        assertNoTextOverflow(allowed = SemanticsMatcher("a subject name") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } in names
        })
    }

    private fun ComposeUiTest.description(entryId: Long): String =
        onNodeWithTag(RecordbookTestTags.subject(entryId)).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()

    private fun ComposeUiTest.clickSnackbarAction(message: String, action: String) {
        // getString loads on the resources' own scope, which Compose idling does not track.
        waitUntil("the snackbar is shown", RESOURCE_LOAD_TIMEOUT_MS) {
            onAllNodes(hasText(message), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(action).performClick()
        waitForIdle()
    }

    @Composable
    private fun Screen(state: RecordbookUiState, list: LazyListState? = null) = ItmoTheme { Content(state, list) }

    @Composable
    private fun Content(state: RecordbookUiState, list: LazyListState? = null) {
        RecordbookScreen(
            state = state,
            onOpenPeriods = { programs, selection -> periods += programs to selection },
            onBarsChange = { bars += it },
            onRefresh = { refreshes++ },
            onRetry = { retries++ },
            onOpenSubject = { selection, subject -> opened += selection to subject },
            listState = list ?: remember { LazyListState() },
        )
    }

    @Composable
    private fun WithSnackbars(state: RecordbookUiState) = ItmoTheme {
        val snackbars = remember { SnackbarHostState() }
        RecordbookErrorSnackbars(state, snackbars, onRetry = { retries++ }, onBarsLogin = { logins++ })
        RecordbookScreen(
            state = state,
            onOpenPeriods = { _, _ -> },
            onBarsChange = {},
            onRefresh = {},
            onRetry = {},
            onOpenSubject = { _, _ -> },
            snackbarHostState = snackbars,
        )
    }

    private companion object {
        const val RESOURCE_LOAD_TIMEOUT_MS = 5_000L
        const val LARGE_FONT = 1.3f
    }
}
