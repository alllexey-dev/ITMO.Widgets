package dev.alllexey.itmowidgets.app.shell.entries

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.app.shell.ShellTags
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.OnboardingStatus
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellGate
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.appRouteSerializersModule
import dev.alllexey.itmowidgets.core.platform.AndroidPlatformCapabilities
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDevicesOverview
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FakeSportLessonTemplateController
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import dev.alllexey.itmowidgets.feature.auth.ui.AuthTestTags
import dev.alllexey.itmowidgets.feature.auth.ui.LoginActivity
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsViewModel
import dev.alllexey.itmowidgets.feature.debug.ui.DebugToolsTestTags
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.me.ui.MeTestTags
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import dev.alllexey.itmowidgets.feature.onboarding.ui.ONBOARDING_ROOT_TEST_TAG
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsLoginActivity
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.navigation.UpdateRoutes
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateArgs
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateTestTags
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import dev.alllexey.itmowidgets.feature.web.domain.MyItmoWebPolicy
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebTestTags
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiAccess
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiRepository
import dev.alllexey.itmowidgets.feature.wifi.presentation.WifiViewModel
import dev.alllexey.itmowidgets.feature.wifi.ui.WifiTestTags
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * The account keys in the Compose shell with the real [shellEntries] and Koin ViewModels on synthetic data: sign-in
 * and the first-run flow fill the window, the Me tab root opens its route-map routes, pairing and «Устройства» have
 * entries, My ITMO in the browser, the update offer and the debug tools render and close themselves, the Wi-Fi sheet
 * signs in to ITMO.ID for a result, the demo session refuses pairing, «Устройства» and My ITMO, the update key survives saving, and a release registry has no debug tools.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class AccountEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sessions = FakeSessionRepository(SessionState.SignedIn(user = null))
    private val updates = FakeAppUpdateRepository()
    private val wifi = NoIsuSession()
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.ME))
    private var surface by mutableStateOf<ShellSurface>(ShellSurface.Tabs(demoBanner = false))
    private lateinit var koin: Koin

    @Before
    fun startGraph() {
        koin = startKoin {
            modules(
                // First, so this test's own widget previews override the settings graph's.
                settingsTestModule(),
                module {
                    viewModel { AuthViewModel(sessions, TimeSource.Monotonic) }
                    viewModel { MeViewModel(sessions, EmptySocial, FakeCustomServicesRepository(enabled = true), FakeDevicesOverview()) }
                    viewModel { AppUpdateViewModel(updates, get<SavedStateHandle>()) }
                    viewModel {
                        OnboardingViewModel(
                            FakeOnboardingRepository(completed = false),
                            FakeCustomServicesRepository(),
                            sessions,
                            FakeCalendarSync(),
                            AndroidPlatformCapabilities,
                            get<SavedStateHandle>(),
                        )
                    }
                    viewModel {
                        DebugToolsViewModel(
                            FixedAcademicTime(),
                            NoTimeOverride,
                            NoScoreOverride,
                            FakeSportLessonTemplateController(),
                            sessions,
                            FakeSessionTokenStore(signedIn = false),
                            FakeCustomServicesRepository(),
                            FakeScheduleChangeTracking(),
                            NoBarsProbe,
                            FakeMarkTracking(),
                        )
                    }
                    viewModel { WifiViewModel(wifi) }
                    single<WidgetPreviewFactory> { NoPreviews }
                    single { ShareLinkFactory("https://widgets.alllexey.dev") }
                    single<UpdateAction> { NoUpdateAction }
                },
                socialTestModule(),
            )
        }.koin
    }

    @Test
    fun signInFillsTheWindowOpensItmoIdForAResultAndTheLogoEntersTheDemo() {
        surface = ShellSurface.Auth
        show()

        compose.onNodeWithTag(AuthTestTags.CONTENT).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.Auth)).assertDoesNotExist()

        compose.onNodeWithTag(AuthTestTags.ITMO_ID_LOGIN).performClick()
        compose.waitForIdle()
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(LoginActivity::class.java.name, started.intent.component?.className)

        repeat(DEMO_TAPS) { compose.onNodeWithTag(AuthTestTags.LOGO).performClick() }
        compose.waitForIdle()
        assertEquals(1, sessions.demoStarts)
        assertEquals("Демо-режим", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun theFirstRunFlowFillsTheWindow() {
        surface = ShellSurface.Onboarding
        show()

        compose.onNodeWithTag(ONBOARDING_ROOT_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.Onboarding)).assertDoesNotExist()
    }

    @Test
    fun theMeTabRootOpensItsRoutes() {
        show()
        compose.onNodeWithTag(MeTestTags.ROOT).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.ME))).assertDoesNotExist()

        assertOpens(MeTestTags.FRIENDS_ROW, overlays = listOf(AppRoutes.Friends))
        assertOpens(MeTestTags.WIDGETS_ROW, overlays = listOf(AppRoutes.Settings("WIDGETS")))
        assertOpens(MeTestTags.SETTINGS_ROW, overlays = listOf(AppRoutes.Settings()))
        assertOpens(MeTestTags.DEBUG_TOOLS_ROW, overlays = listOf(AppRoutes.DebugTools))
    }

    @Test
    fun pairingTheDevicesAndAClientHaveEntries() {
        val entries = shellEntries(debugTools = false)
        listOf(AppRoutes.Pairing(), AppRoutes.Devices, AppRoutes.Connection("conn-1")).forEach { route ->
            assertTrue("$route", entries.isRegistered(route))
        }
    }

    @Test
    fun myItmoInTheBrowserRendersAndClosesItself() {
        show()
        act { open(AppRoutes.MyItmoWeb) }

        compose.onNodeWithTag(MyItmoWebTestTags.RELOAD).assertIsDisplayed()
        compose.onNodeWithTag(MyItmoWebTestTags.CLOSE).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.overlays.isEmpty())
    }

    /**
     * The web history takes Back first; without history the entry's handler is off, so `NavDisplay`'s handler, the
     * one that follows the predictive gesture, closes the screen.
     */
    @Test
    fun backOnMyItmoGoesBackInTheWebHistoryThenLeavesItToTheOverlayGesture() {
        show()
        act { open(AppRoutes.MyItmoWeb) }
        val page = webView(compose.activity.window.decorView)
        compose.runOnIdle {
            // The shadow keeps no history of its own loads: the home page, then a second one.
            shadowOf(page).pushEntryToHistory(MyItmoWebPolicy.HOME_URL)
            shadowOf(page).pushEntryToHistory(SECOND_PAGE)
            assertTrue(page.canGoBack())
            shadowOf(page).webViewClient.doUpdateVisitedHistory(page, SECOND_PAGE, false)
        }
        compose.waitForIdle()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, shadowOf(page).goBackInvocations)
        assertEquals(listOf<AppRoute>(AppRoutes.MyItmoWeb), navigator.state.overlays)

        val screen = compose.onNodeWithTag(ShellTags.overlay(AppRoutes.MyItmoWeb.toString()))
        val full = screen.getBoundsInRoot()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.5f))
        }
        compose.waitForIdle()
        val held = screen.getBoundsInRoot()
        assertTrue("the predictive Back shrinks the screen: $held of $full", held.right - held.left < full.right - full.left)

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, shadowOf(page).goBackInvocations)
        assertTrue(navigator.state.overlays.isEmpty())
    }

    @Test
    fun theDemoSessionRefusesPairingTheDevicesAndMyItmo() {
        navigator.guard = { route -> ShellGate.check(route, SessionState.SignedIn(user = null, demo = true), PASSED) }
        navigator.onRefusedInDemo = { refuseInDemo(compose.activity) }
        surface = ShellSurface.Tabs(demoBanner = true)
        show()

        listOf(AppRoutes.Pairing(), AppRoutes.Devices, AppRoutes.Connection("conn-1"), AppRoutes.MyItmoWeb).forEach { route ->
            ShadowToast.reset()
            act { open(route) }
            assertEquals("$route", ShellBackStack(AppTab.ME), navigator.state)
            assertEquals("$route", compose.activity.getString(R.string.error_demo_unavailable), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun wifiWithoutAnItmoIdSessionSignsInForAResultAndReadsIsuAgain() {
        show()
        act { open(AppRoutes.Wifi) }
        compose.onNodeWithTag(WifiTestTags.SIGN_IN).performClick()
        compose.waitForIdle()

        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(BarsLoginActivity::class.java.name, started.intent.component?.className)
        assertTrue(started.intent.getBooleanExtra(BarsLoginActivity.EXTRA_ITMO_ID_TITLE, false))

        val reads = wifi.reads
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(started.intent, Activity.RESULT_OK, null) }
        compose.waitForIdle()
        assertEquals(reads + 1, wifi.reads)
    }

    @Test
    fun theUpdateOfferReadsItsKeyAndClosesItself() {
        show()
        act { open(UpdateRoutes.of(UPDATE)) }

        compose.onNodeWithTag(AppUpdateTestTags.TITLE).assertIsDisplayed()
        compose.onNode(hasTestTag(AppUpdateTestTags.VERSIONS) and hasText("2.3.0", substring = true)).assertExists()
        compose.onNodeWithTag(AppUpdateTestTags.CLOSE).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.overlays.isEmpty())
    }

    @Test
    fun theUpdateKeyRoundTripsThroughTheSavedShell() {
        val key: AppRoute = UpdateRoutes.AppUpdate(AppUpdateArgs.of(UPDATE))
        val json = Json { serializersModule = appRouteSerializersModule(UpdateRoutes.registration) }
        val serializer = PolymorphicSerializer(AppRoute::class)
        assertEquals(key, json.decodeFromString(serializer, json.encodeToString(serializer, key)))

        val saver = Nav3AppNavigator.saver(UpdateRoutes.registration)
        val shown = Nav3AppNavigator(ShellBackStack(AppTab.HOME, listOf(key)))
        val saved = with(saver) { SaverScope { true }.save(shown) }
        assertEquals(shown.state, saver.restore(checkNotNull(saved))?.state)
    }

    @Test
    fun theDebugToolsRenderInADebugRegistryAndAReleaseRegistryHasNone() {
        assertTrue(shellEntries(debugTools = true).isRegistered(AppRoutes.DebugTools))
        assertFalse(shellEntries(debugTools = false).isRegistered(AppRoutes.DebugTools))

        show()
        act { open(AppRoutes.DebugTools) }
        compose.onNodeWithTag(DebugToolsTestTags.BACK).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.overlays.isEmpty())
    }

    private fun show() {
        compose.setContent {
            // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
            KoinContext(koin) {
                ItmoTheme { ShellContent(navigator, shellEntries(debugTools = true), surface, onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(MeTestTags.ROOT).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    /** [tag] on the Me tab opens exactly [overlays] and [floating]; the Me tab is selected again afterwards. */
    private fun assertOpens(
        tag: String,
        overlays: List<AppRoute> = emptyList(),
        floating: List<AppRoute> = emptyList(),
    ) {
        tap(tag)
        assertEquals(tag, AppTab.ME, navigator.tab)
        assertEquals(tag, overlays, navigator.state.overlays)
        assertEquals(tag, floating, navigator.state.floating)
        act { select(AppTab.ME) }
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private fun webView(view: View): WebView = checkNotNull(findWebView(view)) { "no WebView on the screen" }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    /** What `ShellHost` shows for a refusal. */
    private fun refuseInDemo(context: Context) {
        Toast.makeText(context, R.string.error_demo_unavailable, Toast.LENGTH_SHORT).show()
    }

    private object EmptySocial : SocialRepository {
        override fun observeFriends(): Flow<LoadState<List<UserProfile>>> = flowOf(LoadState.Content(emptyList()))
        override fun observeRequests(): Flow<LoadState<FriendRequests>> =
            flowOf(LoadState.Content(FriendRequests.EMPTY))
        override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
        override val currentFriends: List<UserProfile>? = emptyList()
        override suspend fun refresh() = Unit
        override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> = error("not on the Me tab")
        override suspend fun profile(isu: Int): AppResult<UserProfile> = error("not on the Me tab")
        override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> = error("not on the Me tab")
        override suspend fun sendRequest(isu: Int): AppResult<UserProfile> = error("not on the Me tab")
        override suspend fun acceptRequest(isu: Int): AppResult<UserProfile> = error("not on the Me tab")
        override suspend fun rejectRequest(isu: Int): AppResult<UserProfile> = error("not on the Me tab")
        override suspend fun cancelRequest(isu: Int): AppResult<UserProfile> = error("not on the Me tab")
        override suspend fun removeFriend(isu: Int): AppResult<UserProfile> = error("not on the Me tab")
    }

    /** The widgets gallery gets empty views. */
    private object NoPreviews : WidgetPreviewFactory {
        override fun create(context: Context, scope: CoroutineScope, settings: WidgetPreviewSettings): WidgetPreview =
            object : WidgetPreview {
                override val view: View = View(context)
                override fun bind(settings: WidgetPreviewSettings) = Unit
            }
    }

    /** ISU wants the user every time: the phone has no ITMO.ID session. */
    private class NoIsuSession : WifiRepository {
        var reads = 0
        override suspend fun cached(): WifiCredentials? = null
        override suspend fun refresh(): AppResult<WifiAccess> {
            reads++
            return AppResult.Success(WifiAccess.SignInRequired)
        }
    }

    private object NoUpdateAction : UpdateAction {
        override fun start(activity: Activity, unsupported: Boolean, onFailed: () -> Unit) = Unit
    }

    private object NoTimeOverride : AcademicTimeOverrideController {
        override fun getOverrideDate(): LocalDate? = null
        override fun setOverrideDate(date: LocalDate?) = Unit
    }

    private object NoScoreOverride : SportScoreOverrideController {
        override fun getOverride(): SportScoreOverride? = null
        override fun setOverride(value: SportScoreOverride?) = Unit
    }

    private object NoBarsProbe : BarsSessionProbe {
        override fun start() = Unit
    }

    private class FakeAppUpdateRepository : AppUpdateRepository {
        override suspend fun loadUpdate(): AppUpdate? = null
        override suspend fun reminder() = AppUpdateReminder(AppVersionName("2.2.0"), Instant.fromEpochMilliseconds(0))
        override suspend fun markNotified() = Unit
        override suspend fun skip(version: AppVersionName) = Unit
    }

    private companion object {
        const val DEMO_TAPS = 5
        const val SECOND_PAGE = "https://my.itmo.ru/test/second"
        val PASSED = OnboardingStatus.PASSED
        val UPDATE = AppUpdate(AppVersionName("2.2.0"), AppVersionName("2.3.0"), note = "", unsupported = false)
    }
}
