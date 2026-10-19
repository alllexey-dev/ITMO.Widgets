package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dev.alllexey.itmowidgets.app.shell.ActivityRoutes
import dev.alllexey.itmowidgets.app.shell.BottomSheetSceneStrategy
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.app.shell.entries.RecordbookTestGraph.Companion.CURRENT_SEMESTER
import dev.alllexey.itmowidgets.app.shell.entries.RecordbookTestGraph.Companion.EARLIER_SEMESTER
import dev.alllexey.itmowidgets.app.shell.entries.RecordbookTestGraph.Companion.PROGRAM_ID
import dev.alllexey.itmowidgets.app.shell.entries.RecordbookTestGraph.Companion.STUDY_YEAR
import dev.alllexey.itmowidgets.app.shell.entries.RecordbookTestGraph.Companion.entryId
import dev.alllexey.itmowidgets.core.navigation.ActivityRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookTestTags
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresSheetTestTags
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.SubjectTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The recordbook keys in the Compose shell with the real [shellEntries] and Koin ViewModels on synthetic data: the
 * tab root opens a subject's page, a period picked in the header's menu loads once, invalid subject arguments keep
 * the root, the scores sheet renders and closes itself when done, and the BARS sign-in starts `BarsLoginActivity`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class RecordbookEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val recordbook = RecordbookTestGraph()
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.RECORDBOOK))

    @Test
    fun theTabRootRendersTheRecordbookAndASubjectOpensItsPage() {
        show()
        compose.onNodeWithTag(RecordbookTestTags.LIST).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.RECORDBOOK))).assertDoesNotExist()

        compose.onNodeWithTag(RecordbookTestTags.row(entryId(CURRENT_SEMESTER))).performClick()
        compose.waitForIdle()

        assertEquals(listOf<AppRoute>(AppRoutes.RecordbookSubject(SUBJECT)), navigator.state.overlays)
        compose.onNodeWithTag(SubjectTestTags.TABS).assertExists()
        // The title and its subtitle are one paragraph in the pushed header.
        compose.onNodeWithText("Тестовый предмет $CURRENT_SEMESTER", substring = true).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.RecordbookSubject(SUBJECT))).assertDoesNotExist()
    }

    @Test
    fun aSwipeTurnsTheSubjectPageToTheNextTab() {
        show()
        compose.onNodeWithTag(RecordbookTestTags.row(entryId(CURRENT_SEMESTER))).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(SubjectTestTags.list(SubjectTab.SCORES)).assertIsDisplayed()

        compose.onNodeWithTag(SubjectTestTags.list(SubjectTab.SCORES)).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithTag(SubjectTestTags.list(SubjectTab.LESSONS)).assertIsDisplayed()
        compose.onNodeWithTag(SubjectTestTags.list(SubjectTab.SCORES)).assertIsNotDisplayed()
    }

    @Test
    fun invalidSubjectArgumentsKeepTheRoot() {
        show()
        act { open(AppRoutes.RecordbookSubject(SUBJECT.copy(studyYear = "2026"))) }

        assertEquals(emptyList<AppRoute>(), navigator.state.overlays)
        compose.onNodeWithTag(RecordbookTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun thePeriodPickedInTheHeaderMenuLoadsOnce() {
        show()
        compose.onNodeWithTag(RecordbookTestTags.PERIOD).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<AppRoute>(), navigator.state.floating)

        compose.onNodeWithText("$EARLIER_SEMESTER семестр").performClick()
        compose.waitForIdle()

        assertEquals(1, recordbook.subjectRequests.count { it == EARLIER_SEMESTER })
        compose.onNodeWithTag(RecordbookTestTags.row(entryId(EARLIER_SEMESTER))).assertIsDisplayed()
    }

    @Test
    fun theScoresSheetRendersItsReading() {
        show()
        val scores = AppRoutes.SheetScores(SHEET.copy(step = SheetScoresArgs.Step.CONNECT))
        act { open(scores) }

        sheet(scores).assertExists()
        compose.onNodeWithTag(SheetScoresSheetTestTags.RETRY).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(scores)).assertDoesNotExist()
    }

    @Test
    fun theScoresSheetClosesItselfWhenDone() {
        show()
        act { open(AppRoutes.SheetScores(SHEET)) }

        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
    }

    @Test
    fun theBarsRouteStartsBarsLoginActivity() {
        assertEquals(
            BARS_LOGIN_ACTIVITY,
            ActivityRoutes.intent(compose.activity, ActivityRoute.BARS_LOGIN).component?.className,
        )
    }

    @Test
    fun anEndedBarsSessionSignsInToBarsForAResult() {
        recordbook.barsEnabled = true
        recordbook.bars = AppResult.Failure(AppError.Unauthorized)
        show()

        awaitText(BARS_LOGIN)
        compose.onNodeWithText(BARS_LOGIN).performClick()
        compose.waitForIdle()

        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(BARS_LOGIN_ACTIVITY, started.intent.component?.className)
    }

    private fun show() {
        val koin = startKoin { modules(recordbook.module()) }.koin
        compose.setContent {
            // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
            KoinContext(koin) {
                ItmoTheme {
                    ShellContent(navigator, shellEntries(), ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    /**
     * Waits for a snackbar's [text]: the snackbar takes its message and action from compose-resources' suspend
     * `getString`, which loads a string the first time on `Dispatchers.Default`, so `waitForIdle` can return before it.
     */
    private fun awaitText(text: String) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun sheet(route: AppRoute) = compose.onNodeWithTag(BottomSheetSceneStrategy.tag(route.toString()))

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val BARS_LOGIN = "Войти"
        const val BARS_LOGIN_ACTIVITY = "dev.alllexey.itmowidgets.feature.recordbook.ui.BarsLoginActivity"
        val SUBJECT = RecordbookSubjectArgs(
            entryId = entryId(CURRENT_SEMESTER),
            programId = PROGRAM_ID,
            semester = CURRENT_SEMESTER,
            studyYear = STUDY_YEAR,
        )
        val SHEET = SheetScoresArgs(
            subjectId = 5,
            subjectName = "Тестовый предмет",
            periodKey = "2026/2027-1",
            url = "https://docs.google.com/spreadsheets/d/test/edit",
            step = SheetScoresArgs.Step.TOTAL,
        )
    }
}
