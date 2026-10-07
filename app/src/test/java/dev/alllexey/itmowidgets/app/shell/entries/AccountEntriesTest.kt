package dev.alllexey.itmowidgets.app.shell.entries

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.debug.DebugRefreshTokenController
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
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSportLessonTemplateController
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
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
import dev.alllexey.itmowidgets.feature.onboarding.ui.OnboardingFragment
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.navigation.UpdateRoutes
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateArgs
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateViewModel
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateTestTags
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import dev.alllexey.itmowidgets.feature.web.ui.MyItmoWebTestTags
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import dev.alllexey.itmowidgets.feature.weblogin.ui.WebLoginSheetTestTags
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
 * and the first-run flow fill the window, the Me tab root opens its route-map routes, the web sign-in sheet, My ITMO
 * in the browser, the update offer and the debug tools render and close themselves, the demo session refuses the web
 * sign-in and My ITMO, the update key survives saving, and a release registry has no debug tools.
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
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.ME))
    private var surface by mutableStateOf<ShellSurface>(ShellSurface.Tabs(demoBanner = false))
    private lateinit var koin: Koin

    @Before
    fun startGraph() {
        koin = startKoin {
            modules(
                module {
                    viewModel { AuthViewModel(sessions, TimeSource.Monotonic) }
                    viewModel { MeViewModel(sessions, EmptySocial, FakeCustomServicesRepository(enabled = true)) }
                    viewModel { WebLoginViewModel(get<SavedStateHandle>(), OneBrowser, FixedAcademicTime()) }
                    viewModel { AppUpdateViewModel(updates, get<SavedStateHandle>()) }
                    viewModel {
                        OnboardingViewModel(
                            FakeOnboardingRepository(completed = false),
                            FakeCustomServicesRepository(),
                            NoAppearance,
                            FakeCustomSpoilerRepository(),
                            get<SavedStateHandle>(),
                        )
                    }
                    viewModel {
                        DebugToolsViewModel(
                            FixedAcademicTime(),
                            NoTimeOverride,
                            NoScoreOverride,
                            FakeSportLessonTemplateController(),
                            NoRefreshToken,
                            FakeCustomServicesRepository(),
                            FakeScheduleChangeTracking(),
                            NoBarsProbe,
                            FakeMarkTracking(),
                        )
                    }
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

        compose.onNodeWithTag(OnboardingFragment.ROOT_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.Onboarding)).assertDoesNotExist()
    }

    @Test
    fun theMeTabRootOpensItsRoutes() {
        show()
        compose.onNodeWithTag(MeTestTags.ROOT).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.ME))).assertDoesNotExist()

        assertOpens(MeTestTags.FRIENDS_ROW, overlays = listOf(AppRoutes.Friends))
        assertOpens(MeTestTags.FIND_PEOPLE_ROW, overlays = listOf(AppRoutes.UserSearch))
        assertOpens(MeTestTags.PRIVACY_ROW, overlays = listOf(AppRoutes.Settings("PRIVACY")))
        assertOpens(MeTestTags.SETTINGS_ROW, overlays = listOf(AppRoutes.Settings()))
        assertOpens(MeTestTags.DEBUG_TOOLS_ROW, overlays = listOf(AppRoutes.DebugTools))
        assertOpens(MeTestTags.WEB_LOGIN_ROW, floating = listOf(AppRoutes.WebLogin()))
    }

    @Test
    fun theWebSignInSheetApprovesACodeAndClosesItself() {
        show()
        act { open(AppRoutes.WebLogin()) }

        compose.onNodeWithTag(WebLoginSheetTestTags.SCAN).assertExists()
        compose.onNodeWithTag(WebLoginSheetTestTags.CODE).performTextInput(CODE)
        compose.onNodeWithTag(WebLoginSheetTestTags.CONTINUE).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(WebLoginSheetTestTags.APPROVE).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(WebLoginSheetTestTags.RESULT_ACTION).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.isEmpty())
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

    @Test
    fun theDemoSessionRefusesTheWebSignInAndMyItmo() {
        navigator.guard = { route -> ShellGate.check(route, SessionState.SignedIn(user = null, demo = true), PASSED) }
        navigator.onRefusedInDemo = { refuseInDemo(compose.activity) }
        surface = ShellSurface.Tabs(demoBanner = true)
        show()

        ShadowToast.reset()
        tap(MeTestTags.WEB_LOGIN_ROW)
        assertEquals(ShellBackStack(AppTab.ME), navigator.state)
        assertEquals(compose.activity.getString(R.string.error_demo_unavailable), ShadowToast.getTextOfLatestToast())

        ShadowToast.reset()
        act { open(AppRoutes.MyItmoWeb) }
        assertEquals(ShellBackStack(AppTab.ME), navigator.state)
        assertEquals(compose.activity.getString(R.string.error_demo_unavailable), ShadowToast.getTextOfLatestToast())
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

    /** Knows one browser waiting under [CODE] and approves it. */
    private object OneBrowser : WebLoginRepository {
        private val preview = WebLoginPreview(
            challengeId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            userAgent = null,
            createdAt = Instant.fromEpochMilliseconds(0),
            expiresAt = Instant.fromEpochMilliseconds(120_000),
        )

        override suspend fun preview(code: String): AppResult<WebLoginPreview> =
            if (code == CODE) AppResult.Success(preview) else error("unknown code $code")

        override suspend fun approve(challengeId: Uuid): AppResult<Unit> = AppResult.Success(Unit)
    }

    /** The stored appearance never answers, so the flow's preview cards stay empty. */
    private object NoAppearance : WidgetAppearanceRepository {
        override fun observeAppearance(): Flow<WidgetAppearance> = emptyFlow()
        override suspend fun setCompactNextLessonEarly(enabled: Boolean) = Unit
        override suspend fun setCompactTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullPastLessonsHidden(hidden: Boolean) = Unit
        override suspend fun setFullTomorrowEnabled(enabled: Boolean) = Unit
        override suspend fun setCompactTextSize(size: WidgetTextSize) = Unit
        override suspend fun setFullTextSize(size: WidgetTextSize) = Unit
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = Unit
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) = Unit
    }

    private object NoPreviews : WidgetPreviewFactory {
        override fun create(context: Context, scope: CoroutineScope, settings: WidgetPreviewSettings): WidgetPreview =
            error("the appearance never answers")
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

    private object NoRefreshToken : DebugRefreshTokenController {
        override fun hasRefreshToken(): Boolean = false
        override suspend fun replaceRefreshToken(refreshToken: String): AppResult<Unit> = AppResult.Success(Unit)
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
        const val CODE = "ABCD2345"
        val PASSED = OnboardingStatus.PASSED
        val UPDATE = AppUpdate(AppVersionName("2.2.0"), AppVersionName("2.3.0"), note = "", unsupported = false)
    }
}
