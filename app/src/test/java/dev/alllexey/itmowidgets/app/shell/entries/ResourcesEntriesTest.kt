package dev.alllexey.itmowidgets.app.shell.entries

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.linkScope
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.feature.resources.ui.LinkAction
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsSheetTestTags
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorSheetTestTags
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksSheetTestTags
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.util.ReflectionHelpers

/**
 * The resources keys in the Compose shell with Koin ViewModels over a fake repository: the links sheet opens the
 * editor and a link's actions above itself, the actions sheet closes itself before the editor or the report opens,
 * the report is the kit's own dialog over an undimmed scene window, and a new link's editor takes the clipboard link
 * once its window has focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class ResourcesEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** A tab whose root is not registered yet, so no tab root needs its own graph. */
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.RECORDBOOK))
    private val links = FakeSubjectLinksRepository().apply {
        state.value = SubjectLinksState.Content(
            linksSnapshot(
                mine = listOf(subjectLink(OWN)),
                shared = listOf(subjectLink(OTHER, visibility = LinkVisibility.ALL, isMine = false)),
            ),
        )
    }
    private lateinit var koin: Koin

    @Before
    fun startGraph() {
        koin = startKoin {
            modules(
                module {
                    viewModel { SubjectLinksViewModel(get<SavedStateHandle>(), links) }
                    viewModel { LinkEditorViewModel(get<SavedStateHandle>(), links) }
                },
            )
        }.koin
    }

    @Test
    fun theLinksSheetOpensTheEditorAboveItself() {
        show()
        act { open(AppRoutes.SubjectLinks(ARGS)) }
        sheet(AppRoutes.SubjectLinks(ARGS)).assertExists()
        compose.onNodeWithTag(SubjectLinksSheetTestTags.LIST).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.SubjectLinks(ARGS))).assertDoesNotExist()

        compose.onNodeWithTag(SubjectLinksSheetTestTags.ADD).performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppRoutes.SubjectLinks(ARGS), AppRoutes.LinkEditor(ARGS)), navigator.state.floating)
        compose.onNodeWithTag(LinkEditorSheetTestTags.URL).assertExists()
    }

    @Test
    fun theActionsSheetClosesItselfBeforeTheEditorOpens() {
        show()
        act {
            open(AppRoutes.SubjectLinks(ARGS))
            open(AppRoutes.LinkActions(ARGS, OWN))
        }

        compose.onNodeWithTag(LinkActionsSheetTestTags.row(LinkAction.EDIT)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppRoutes.SubjectLinks(ARGS), AppRoutes.LinkEditor(ARGS, OWN)), navigator.state.floating)
        sheet(AppRoutes.LinkActions(ARGS, OWN)).assertDoesNotExist()
    }

    @Test
    fun theReportReplacesTheActionsSheetOnAnUndimmedSceneWindowAndCancelClosesIt() {
        show()
        act { open(AppRoutes.LinkActions(ARGS, OTHER)) }

        compose.onNodeWithTag(LinkActionsSheetTestTags.row(LinkAction.REPORT)).performClick()
        compose.waitForIdle()

        assertEquals(listOf<AppRoute>(AppRoutes.ReportLink(ARGS, OTHER)), navigator.state.floating)
        compose.onNodeWithText(REPORT_TITLE).assertExists()
        assertEquals("one dimmed window", 1, shownDialogs().count { it.dims() })

        compose.onNodeWithText(CANCEL).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.isEmpty())
    }

    @Test
    fun aNewLinksEditorTakesTheClipboardLinkOnceItsWindowHasFocus() {
        compose.activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("link", CLIP))
        show()
        act { open(AppRoutes.LinkEditor(ARGS)) }

        // Robolectric keeps the focus on the activity when a dialog shows; the sheet's window gets it by hand.
        compose.runOnIdle {
            compose.activity.window.decorView.windowFocus(false)
            checkNotNull(shownDialogs().last().window).decorView.windowFocus(true)
        }
        compose.waitForIdle()

        compose.onNodeWithTag(LinkEditorSheetTestTags.URL).assert(hasText(CLIP))
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

    private fun Dialog.dims(): Boolean =
        checkNotNull(window).attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0

    private fun View.windowFocus(focused: Boolean) =
        Shadow.extract<ShadowViewRootImpl>(ReflectionHelpers.callInstanceMethod<Any>(this, "getViewRootImpl"))
            .callWindowFocusChanged(focused)

    private companion object {
        const val OWN = "own"
        const val OTHER = "other"
        const val CLIP = "https://example.org/new"
        const val REPORT_TITLE = "Жалоба на ссылку"
        const val CANCEL = "Отмена"
        val ARGS = SubjectLinksArgs(linkScope.subjectId, linkScope.subjectName, linkScope.periodKey)
    }
}
