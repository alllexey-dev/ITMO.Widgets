package dev.alllexey.itmowidgets.app.shell.entries

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.app.shell.BottomSheetSceneStrategy
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropActivity
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropContract
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.settings.presentation.MaintenancePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsTestTags
import dev.alllexey.itmowidgets.feature.settings.ui.diagnostics.DiagnosticsTestTags
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The settings keys in the Compose shell with Koin ViewModels over fakes: a page renders its rows under the real
 * [shellEntries] and opens the next page and the error journal as overlays, a site row opens the browser through the
 * shell's own platform actions, the journal and the `.ics` sheet render, and the spoiler picker and crop screen
 * answer the QR widget page after its state was saved and restored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class SettingsEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** The recordbook tab: its root reads [RecordbookTestGraph]'s synthetic recordbook. */
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.RECORDBOOK))
    private val spoilers = FakeCustomSpoilerRepository()
    private lateinit var koin: Koin

    @Before
    fun startGraph() {
        koin = startKoin { modules(settingsTestModule(spoilers), RecordbookTestGraph().module()) }.koin
    }

    @Test
    fun aPageRendersItsRowsAndOpensTheNextPageAndCloses() {
        show()
        act { open(AppRoutes.Settings()) }
        awaitRows()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.Settings())).assertDoesNotExist()

        clickRow(SettingRowId.navigation(SettingsPage.MAINTENANCE))
        assertEquals(
            listOf(AppRoutes.Settings(), AppRoutes.Settings(SettingsPage.MAINTENANCE.name)),
            navigator.state.overlays,
        )

        clickTop(SettingsTestTags.BACK)
        assertEquals(listOf(AppRoutes.Settings()), navigator.state.overlays)
    }

    @Test
    fun theMaintenancePageOpensTheJournalWhichRendersAndCloses() {
        show()
        act { open(AppRoutes.Settings(SettingsPage.MAINTENANCE.name)) }
        awaitRows()

        clickRow(SettingRowId.DIAGNOSTICS)
        assertEquals(AppRoutes.Diagnostics, navigator.state.overlays.last())
        compose.onNodeWithTag(DiagnosticsTestTags.EMPTY).assertIsDisplayed()

        clickTop(DiagnosticsTestTags.BACK)
        assertEquals(listOf(AppRoutes.Settings(SettingsPage.MAINTENANCE.name)), navigator.state.overlays)
    }

    @Test
    fun thePrivacyPolicyRowOpensTheSiteInTheBrowser() {
        show()
        act { open(AppRoutes.Settings(SettingsPage.MAINTENANCE.name)) }
        awaitRows()

        clickRow(SettingRowId.PRIVACY_POLICY)

        val started = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(BuildConfig.WIDGETS_BASE_URL + MaintenancePageProvider.PRIVACY_POLICY_PATH, started.dataString)
    }

    @Test
    fun theIcsSheetRendersItsRanges() {
        show()
        act { open(AppRoutes.IcsExport) }

        compose.onNodeWithTag(BottomSheetSceneStrategy.tag(AppRoutes.IcsExport.toString())).assertExists()
        compose.onNodeWithTag(IcsExportTestTags.RANGES).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.IcsExport)).assertDoesNotExist()
    }

    @Test
    fun theSpoilerPickAndCropReachTheQrPageAfterItsStateWasRestored() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Shell() }
        act { open(AppRoutes.Settings(SettingsPage.QR_WIDGET.name)) }
        awaitRows()

        clickRow(SettingRowId.QR_CUSTOM_IMAGE)
        val pick = shadowOf(compose.activity).nextStartedActivityForResult
        restoration.emulateSavedInstanceStateRestore()
        awaitRows()

        dispatch(pick.requestCode, Intent().setData(PICKED))
        val crop = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(SpoilerCropActivity::class.java.name, crop.intent.component?.className)
        dispatch(crop.requestCode, Intent().putExtra(SpoilerCropContract.RESULT_URI, CROPPED))

        compose.waitUntil(TIMEOUT_MS) { spoilers.saved.isNotEmpty() }
        assertEquals(listOf(CROPPED), spoilers.saved)
    }

    private fun show() {
        compose.setContent { Shell() }
        compose.waitForIdle()
    }

    @Composable
    private fun Shell() {
        // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
        KoinContext(koin) {
            ItmoTheme { ShellContent(navigator, shellEntries(), ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {}) }
        }
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    /** The top page shows its rows once its local settings arrive. */
    private fun awaitRows() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithTag(SettingsTestTags.SCROLL).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The row [id] of the top page: overlays below stay composed, and the pages share their tags. */
    private fun clickRow(id: SettingRowId) {
        val pages = compose.onAllNodesWithTag(SettingsTestTags.SCROLL)
        pages[pages.fetchSemanticsNodes().size - 1].performScrollToNode(hasTestTag(id.key))
        clickTop(id.key)
    }

    private fun clickTop(tag: String) {
        val nodes = compose.onAllNodesWithTag(tag)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        compose.waitForIdle()
    }

    private fun dispatch(requestCode: Int, data: Intent) {
        compose.runOnIdle {
            assertTrue(compose.activity.activityResultRegistry.dispatchResult(requestCode, Activity.RESULT_OK, data))
        }
        compose.waitForIdle()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        val PICKED: Uri = Uri.parse("content://media/picked/1")
        const val CROPPED = "file:///data/spoiler/cropped.png"
    }
}
