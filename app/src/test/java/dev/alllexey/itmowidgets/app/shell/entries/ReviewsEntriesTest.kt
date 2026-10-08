package dev.alllexey.itmowidgets.app.shell.entries

import android.app.Dialog
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.app.shell.BottomSheetSceneStrategy
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorSheetTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The reviews keys in the Compose shell with Koin ViewModels over fakes: the editor is a form sheet that survives
 * Back with a draft and asks before it is discarded, an untouched one closes on Back, and the report renders on the
 * kit's dialog and closes with its cancel button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class ReviewsEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** A tab whose root is not registered yet, so no tab root needs its own graph. */
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.RECORDBOOK))
    private lateinit var koin: Koin

    @Before
    fun startGraph() {
        val reviews = FakeTeacherReviewsRepository()
        val lessons = FakeTeacherLessonsGateway()
        koin = startKoin {
            modules(
                module {
                    viewModel { ReviewEditorViewModel(get<SavedStateHandle>(), reviews, lessons) }
                    viewModel { ReportReviewViewModel(get<SavedStateHandle>(), reviews) }
                },
            )
        }.koin
    }

    @Test
    fun theEditorWithADraftSurvivesBackAndAsksBeforeItIsDiscarded() {
        show()
        act { open(EDITOR) }
        sheet(EDITOR).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(EDITOR)).assertDoesNotExist()

        compose.onNodeWithTag(ReviewEditorSheetTestTags.TEXT).performTextInput("Объясняет понятно")
        compose.waitForIdle()
        pressBackOnTopWindow()

        assertEquals(listOf<AppRoute>(EDITOR), navigator.state.floating)
        sheet(EDITOR).assertExists()
        compose.onNodeWithText(DISCARD_TITLE).assertExists()

        compose.onNodeWithText(DISCARD).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.isEmpty())
    }

    @Test
    fun anUntouchedEditorClosesOnBack() {
        show()
        act { open(EDITOR) }
        sheet(EDITOR).assertExists()

        pressBackOnTopWindow()

        assertTrue(navigator.state.floating.isEmpty())
    }

    @Test
    fun theReportRendersOnTheKitsDialogAndCancelClosesIt() {
        val report = AppRoutes.ReportReview(TEACHER, REVIEW_ID)
        show()
        act { open(report) }

        compose.onNodeWithText(REPORT_TITLE).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(report)).assertDoesNotExist()

        compose.onNodeWithText(CANCEL).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.isEmpty())
    }

    private fun show() {
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

    private fun sheet(route: AppRoute) = compose.onNodeWithTag(BottomSheetSceneStrategy.tag(route.toString()))

    private fun shownDialogs(): List<Dialog> = ShadowDialog.getShownDialogs().filter { it.isShowing }

    /** Back on the window of the top sheet, where the system delivers it. */
    private fun pressBackOnTopWindow() {
        compose.runOnIdle { (shownDialogs().last() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private companion object {
        const val REVIEW_ID = "review-1"
        const val DISCARD_TITLE = "Не сохранять отзыв?"
        const val DISCARD = "Не сохранять"
        const val REPORT_TITLE = "Жалоба на отзыв"
        const val CANCEL = "Отмена"
        val TEACHER = TeacherReviewArgs(teacherIsu = 200001, teacherName = "Мария Иванова")
        val EDITOR = AppRoutes.ReviewEditor(TEACHER)
    }
}
