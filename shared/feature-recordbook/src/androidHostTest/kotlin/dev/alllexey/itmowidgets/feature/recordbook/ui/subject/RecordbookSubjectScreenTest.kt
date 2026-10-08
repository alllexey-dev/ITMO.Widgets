package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookErrorSnackbars
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.preview.RecordbookSubjectPreviewSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The subject page on its Compose list (LR-4b): the header in every state, the error retry, `Все пары` opening in
 * place, the snackbars of a failed vote and of an ended BARS session, and the sheet-link picker. The looks live in the
 * `RecordbookSubjectScreen_*` goldens, the sections' actions in `SubjectHubSectionsTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class RecordbookSubjectScreenTest {

    private var retries = 0
    private var lessonExpansions = 0
    private val connected = mutableListOf<SheetLinkOption>()

    @Test
    fun theHeaderNamesTheSubjectItsAssessmentAndSemesterOverTheRows() = runComposeUiTest {
        setContent { Screen(RecordbookSubjectPreviewSamples.session()) }

        onNodeWithTag(RecordbookSubjectTestTags.TITLE).assertExists()
        onNodeWithText(RecordbookPreviewSamples.ALGORITHMS).assertExists()
        onNodeWithText("Экзамен, 2 семестр").assertExists()
        onNodeWithText("Контрольные точки").assertExists()
        onNodeWithTag(RecordbookSubjectTestTags.LOADING).assertDoesNotExist()
        assertTouchTargets()
    }

    @Test
    fun theFirstLoadKeepsTheHeaderOverListPlaceholders() = runComposeUiTest {
        setContent { Screen(RecordbookSubjectUiState.Loading) }

        onNodeWithTag(RecordbookSubjectTestTags.LOADING).assertExists()
        onNodeWithText("2 семестр").assertExists()
        onNodeWithTag(RecordbookSubjectTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun aFailedLoadOffersARetryInTheListsPlace() = runComposeUiTest {
        setContent { Screen(RecordbookSubjectUiState.Error(AppError.Network)) }

        onNodeWithText("Не удалось загрузить").assertExists()
        onNodeWithTag(RecordbookSubjectTestTags.STATE_ACTION).performClick()

        assertEquals(1, retries)
        onNodeWithTag(RecordbookSubjectTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun allLessonsOpenInPlaceOnThePage() = runComposeUiTest {
        var state by mutableStateOf(RecordbookSubjectPreviewSamples.math(sheet = null))
        setContent {
            // Tall enough for the whole page, so every row is composed.
            Screen(state, Modifier.requiredHeight(PAGE_HEIGHT.dp)) {
                lessonExpansions++
                state = state.copy(hub = state.hub.copy(lessonsExpanded = true))
            }
        }
        onAllNodes(hasText(LESSON_START, substring = true), useUnmergedTree = true).assertCountEquals(2)

        onNodeWithText("Все пары, 4").performClick()

        assertEquals(1, lessonExpansions)
        onAllNodesWithText("Все пары", substring = true).assertCountEquals(0)
        onAllNodes(hasText(LESSON_START, substring = true), useUnmergedTree = true).assertCountEquals(4)
        onNodeWithTag(RecordbookSubjectTestTags.LIST).assertExists()
    }

    @Test
    fun aFailedVoteSaysWhyInASnackbar() = runComposeUiTest {
        val events = MutableSharedFlow<RecordbookSubjectEvent>(extraBufferCapacity = 1)
        setContent {
            ItmoTheme {
                val snackbars = remember { SnackbarHostState() }
                RecordbookSubjectEventSnackbars(events, snackbars)
                RecordbookSubjectScreen(
                    RecordbookSubjectPreviewSamples.sheet(), SEMESTER, RecordbookSubjectActions(),
                    snackbarHostState = snackbars,
                )
            }
        }
        waitForIdle()

        events.tryEmit(RecordbookSubjectEvent.VoteFailed(AppError.Network))

        waitUntil(timeoutMillis = RESOURCE_TIMEOUT_MS) { onAllNodesWithText("Нет связи. Проверьте интернет.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun anEndedBarsSessionOffersTheSignInFromItsSnackbar() = runComposeUiTest {
        var logins = 0
        val state = RecordbookSubjectPreviewSamples.bars().copy(barsError = AppError.Unauthorized)
        setContent {
            ItmoTheme {
                val snackbars = remember { SnackbarHostState() }
                RecordbookErrorSnackbars(state.refreshError, state.barsError, snackbars, onRetry = { retries++ }) {
                    logins++
                }
                RecordbookSubjectScreen(state, SEMESTER, RecordbookSubjectActions(), snackbarHostState = snackbars)
            }
        }

        waitUntil(timeoutMillis = RESOURCE_TIMEOUT_MS) { onAllNodesWithText("Войти в БАРС").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Сессия БАРС закончилась, нужно войти снова.").assertExists()
        onNodeWithText("Войти в БАРС").performClick()

        assertEquals(1, logins)
        assertEquals(0, retries)
    }

    @Test
    fun severalSheetLinksAskWhichOneAndReturnThePickedLink() = runComposeUiTest {
        setContent { Screen(RecordbookSubjectPreviewSamples.offer()) }

        onNodeWithTag(SubjectSheetTotalTestTags.HINT).performClick()
        onNodeWithText("Какая таблица?").assertExists()
        onNodeWithText("${RecordbookSubjectPreviewSamples.OWN_SHEET_TITLE}, моя").assertExists()
        onNodeWithText(RecordbookSubjectPreviewSamples.SHARED_SHEET_TITLE).performClick()

        assertEquals(listOf(RecordbookSubjectPreviewSamples.SHARED_SHEET_TITLE), connected.map { it.title })
        onAllNodesWithText("Какая таблица?").assertCountEquals(0)
    }

    @Test
    fun theOnlySheetLinkConnectsWithoutAsking() = runComposeUiTest {
        val only = SheetLinkOption(RecordbookSubjectPreviewSamples.SHEET_URL, null, mine = false)
        setContent { Screen(RecordbookSubjectPreviewSamples.math(SubjectSheetState.Hint(listOf(only)))) }

        onNodeWithTag(SubjectSheetTotalTestTags.HINT).performClick()

        assertEquals(listOf(only), connected)
        onAllNodesWithText("Какая таблица?").assertCountEquals(0)
    }

    @Test
    fun everyRowOfThePageHasItsOwnKey() {
        val repeated = RecordbookControl(21, "Текущий контроль", 40.0, 30.0, 60.0, true, null, null)
        val states = listOf(
            RecordbookSubjectPreviewSamples.sheet(),
            RecordbookSubjectPreviewSamples.bars(),
            RecordbookSubjectPreviewSamples.sport(),
            RecordbookSubjectPreviewSamples.binding(),
            RecordbookSubjectPreviewSamples.session().let { it.copy(controls = it.controls + repeated) },
        )

        states.forEach { state ->
            val keys = subjectHubKeys(subjectHubItems(state))
            assertEquals(keys.size, keys.toSet().size, keys.toString())
        }
        val session = subjectHubKeys(subjectHubItems(states.last()))
        assertTrue("control:21:Текущий контроль:2" in session, session.toString())
    }

    @Composable
    private fun Screen(
        state: RecordbookSubjectUiState,
        modifier: Modifier = Modifier,
        onShowAllLessons: () -> Unit = {},
    ) = ItmoTheme {
        RecordbookSubjectScreen(
            state,
            SEMESTER,
            RecordbookSubjectActions(
                onRetry = { retries++ },
                onShowAllLessons = onShowAllLessons,
                onConnectSheet = { connected += it },
            ),
            modifier,
        )
    }

    private companion object {
        const val SEMESTER = 2
        const val PAGE_HEIGHT = 4000
        const val LESSON_START = "10:00"
        const val RESOURCE_TIMEOUT_MS = 5_000L
    }
}
