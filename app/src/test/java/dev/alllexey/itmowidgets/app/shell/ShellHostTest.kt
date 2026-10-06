package dev.alllexey.itmowidgets.app.shell

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryShortcuts
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGateViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsLoginActivity
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * `ShellHost` on a real activity with a Koin test graph: every entry-intent row of `docs/architecture.md` section
 * Navigation, the Recents flag, the pending route across recreation, the gate and the demo refusals, the update check.
 * Tab roots are fakes with two buttons that open the demo-refused keys; every other key shows its placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ShellHostTestApplication::class)
class ShellHostTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @get:Rule
    val compose = createEmptyComposeRule()

    private val sessions = FakeSessionRepository()
    private val onboarding = FakeOnboardingRepository()
    private val updates = FakeAppUpdateRepository()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun startGraph() {
        ShellHostTestActivity.updateRoute = null
        startKoin {
            modules(
                module {
                    single<SessionRepository> { sessions }
                    single<OnboardingRepository> { onboarding }
                    single<InstallStateWatcher> { NoInstallState }
                    factory { PendingAppUpdate(updates, Clock.System) }
                    viewModelOf(::OnboardingGateViewModel)
                },
            )
        }
    }

    @Test
    fun everyEntryIntentRunsItsRouteOnceTheTabsShow() {
        val subject = RecordbookSubjectArgs(entryId = 7, programId = 3, semester = 2, studyYear = "2025/2026")
        val rows = listOf(
            Row(entry(AppEntryIntents.ACTION_OPEN_QR_PASS), ShellBackStack(AppTab.HOME, listOf(AppRoutes.QrPass)), EntryShortcuts.QR_PASS),
            Row(
                entry(AppEntryIntents.ACTION_OPEN_TODAY),
                ShellBackStack(AppTab.SCHEDULE, requests = mapOf(AppTab.SCHEDULE to TabRequest.ScheduleToday)),
                EntryShortcuts.TODAY,
            ),
            Row(entry(AppEntryIntents.ACTION_OPEN_SCHEDULE), ShellBackStack(AppTab.SCHEDULE)),
            Row(entry(AppEntryIntents.ACTION_OPEN_SPORT), ShellBackStack(AppTab.SPORT)),
            Row(
                entry(AppEntryIntents.ACTION_OPEN_USER_PROFILE).putExtra(UserScreenArgs.ISU, ISU),
                ShellBackStack(AppTab.ME, listOf(AppRoutes.UserProfile(ISU))),
            ),
            Row(entry(AppEntryIntents.ACTION_OPEN_USER_PROFILE).putExtra(UserScreenArgs.ISU, 0), ShellBackStack()),
            Row(
                entry(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES),
                ShellBackStack(AppTab.SCHEDULE, listOf(AppRoutes.ScheduleChanges)),
            ),
            Row(entry(AppEntryIntents.ACTION_OPEN_RECORDBOOK), ShellBackStack(AppTab.RECORDBOOK)),
            Row(
                entry(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT).putExtras(subject.toBundle()),
                ShellBackStack(AppTab.RECORDBOOK, listOf(AppRoutes.RecordbookSubject(subject))),
            ),
            Row(
                entry(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT).putExtras(subject.copy(semester = 0).toBundle()),
                ShellBackStack(AppTab.RECORDBOOK),
            ),
            Row(
                entry(AppEntryIntents.ACTION_OPEN_BARS_LOGIN),
                ShellBackStack(AppTab.RECORDBOOK),
                activity = BarsLoginActivity::class.java.name,
            ),
            Row(link("/u/$ISU"), ShellBackStack(AppTab.ME, listOf(AppRoutes.UserProfile(ISU)))),
            Row(
                link("/sport/12"),
                ShellBackStack(AppTab.SPORT, requests = mapOf(AppTab.SPORT to TabRequest.SportLesson(12))),
            ),
            Row(
                link("/sport/p/12"),
                ShellBackStack(AppTab.SPORT, requests = mapOf(AppTab.SPORT to TabRequest.SportLesson(12, predicted = true))),
            ),
            Row(link("/sport/abc"), ShellBackStack(AppTab.HOME, floating = listOf(AppRoutes.LinkUnavailable))),
            Row(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/u/$ISU"), context, ShellHostTestActivity::class.java),
                ShellBackStack(),
            ),
            Row(entry("dev.alllexey.itmowidgets.action.UNKNOWN"), ShellBackStack()),
        )
        signedIn()

        rows.forEach { row ->
            val reportedBefore = reportedShortcuts().size
            launch(row.intent).use { scenario ->
                val case = "${row.intent.action} ${row.intent.dataString} ${row.intent.extras?.keySet()}"
                assertEquals(case, ShellSurface.Tabs(demoBanner = false), snapshot(scenario).surface)
                assertEquals(case, row.expected, snapshot(scenario).backStack)
                scenario.onActivity { activity ->
                    assertEquals(case, listOfNotNull(row.shortcut), reportedShortcuts().drop(reportedBefore))
                    assertEquals(case, row.activity, shadowOf(activity).nextStartedActivity?.component?.className)
                }
            }
        }
    }

    @Test
    fun theMalformedLinkAlertSaysItAndClosesOnItsButton() {
        signedIn()
        launch(link("/sport/abc")).use { scenario ->
            compose.onNodeWithText(context.getString(R.string.app_link_unavailable_title)).assertExists()
            compose.onNodeWithText(context.getString(R.string.app_link_unavailable_text)).assertExists()

            compose.onNodeWithText(context.getString(R.string.common_got_it)).performClick()
            compose.waitForIdle()

            assertEquals(ShellBackStack(AppTab.HOME), snapshot(scenario).backStack)
        }
    }

    @Test
    fun aRouteArrivingWhileOpenReplacesWhatWasShown() {
        signedIn()
        launch(entry(AppEntryIntents.ACTION_OPEN_SPORT)).use { scenario ->
            scenario.onActivity { it.deliver(entry(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES)) }
            compose.waitForIdle()
            assertEquals(ShellBackStack(AppTab.SCHEDULE, listOf(AppRoutes.ScheduleChanges)), snapshot(scenario).backStack)

            scenario.onActivity { it.deliver(entry(AppEntryIntents.ACTION_OPEN_QR_PASS)) }
            compose.waitForIdle()
            assertEquals(ShellBackStack(AppTab.HOME, listOf(AppRoutes.QrPass)), snapshot(scenario).backStack)
        }
    }

    @Test
    fun aReplayFromRecentsRunsNothing() {
        signedIn()
        val replay = entry(AppEntryIntents.ACTION_OPEN_QR_PASS).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        launch(replay).use { scenario ->
            assertEquals(ShellBackStack(), snapshot(scenario).backStack)
            assertEquals(emptyList<String>(), reportedShortcuts())
        }
    }

    @Test
    fun aPendingRouteSurvivesRecreationAndRunsOnceAfterSignInAndTheFirstRunFlow() {
        sessions.state.value = SessionState.SignedOut
        launch(entry(AppEntryIntents.ACTION_OPEN_QR_PASS)).use { scenario ->
            assertEquals(ShellSurface.Auth, snapshot(scenario).surface)
            scenario.recreate()
            compose.waitForIdle()

            sessions.state.value = SessionState.SignedIn(user = null)
            compose.waitForIdle()
            assertEquals(ShellSurface.Progress, snapshot(scenario).surface)
            onboarding.completed.value = false
            compose.waitForIdle()
            assertEquals(ShellSurface.Onboarding, snapshot(scenario).surface)
            scenario.recreate()
            compose.waitForIdle()
            assertEquals(ShellBackStack(), snapshot(scenario).backStack)

            onboarding.completed.value = true
            compose.waitForIdle()
            assertEquals(ShellBackStack(AppTab.HOME, listOf(AppRoutes.QrPass)), snapshot(scenario).backStack)

            // The route ran: neither the saved shell nor the saved queue runs it again.
            scenario.recreate()
            compose.waitForIdle()
            assertEquals(ShellBackStack(AppTab.HOME, listOf(AppRoutes.QrPass)), snapshot(scenario).backStack)
        }
    }

    @Test
    fun theGateFollowsTheSessionAndTheFirstRunFlag() {
        sessions.state.value = SessionState.Initializing
        launch(entry(Intent.ACTION_MAIN)).use { scenario ->
            assertEquals(ShellSurface.Progress, snapshot(scenario).surface)

            sessions.state.value = SessionState.SignedOut
            compose.waitForIdle()
            assertEquals(ShellSurface.Auth, snapshot(scenario).surface)

            sessions.state.value = SessionState.SignedIn(user = null)
            compose.waitForIdle()
            assertEquals("the flag is not read yet", ShellSurface.Progress, snapshot(scenario).surface)

            onboarding.completed.value = false
            compose.waitForIdle()
            assertEquals(ShellSurface.Onboarding, snapshot(scenario).surface)

            onboarding.completed.value = true
            compose.waitForIdle()
            assertEquals(ShellSurface.Tabs(demoBanner = false), snapshot(scenario).surface)

            compose.onNodeWithText(OPEN_WEB).performClick()
            compose.waitForIdle()
            assertEquals(listOf<AppRoute>(AppRoutes.MyItmoWeb), snapshot(scenario).backStack.overlays)

            sessions.state.value = SessionState.ReauthenticationRequired
            compose.waitForIdle()
            assertEquals(ShellSurface.Auth, snapshot(scenario).surface)
            assertEquals("signing out closes everything", ShellBackStack(), snapshot(scenario).backStack)

            sessions.state.value = SessionState.SignedIn(user = null, demo = true)
            onboarding.completed.value = false
            compose.waitForIdle()
            assertEquals("the demo skips the first-run flow", ShellSurface.Tabs(demoBanner = true), snapshot(scenario).surface)
        }
    }

    @Test
    fun theDemoSessionRefusesMyItmoWebAndTheWebSignIn() {
        sessions.state.value = SessionState.SignedIn(user = null, demo = true)
        launch(entry(Intent.ACTION_MAIN)).use { scenario ->
            listOf(OPEN_WEB, OPEN_WEB_LOGIN).forEach { button ->
                ShadowToast.reset()
                compose.onNodeWithText(button).performClick()
                compose.waitForIdle()

                assertEquals(button, ShellBackStack(), snapshot(scenario).backStack)
                assertEquals(button, context.getString(R.string.error_demo_unavailable), ShadowToast.getTextOfLatestToast())
            }

            compose.onNodeWithText(context.getString(R.string.demo_banner_sign_in)).performClick()
            compose.waitForIdle()
            assertEquals(ShellSurface.Auth, snapshot(scenario).surface)
        }
    }

    @Test
    fun theUpdateIsCheckedOncePerProcessOutsideTheDemoAndOfferedWhileResumed() {
        ShellHostTestActivity.updateRoute = { AppRoutes.Diagnostics }
        updates.update = AppUpdate(AppVersionName("2.2"), AppVersionName("2.3"), note = "", unsupported = false)
        sessions.state.value = SessionState.SignedIn(user = null, demo = true)
        launch(entry(Intent.ACTION_MAIN)).use { scenario ->
            assertEquals("never in the demo", 0, updates.checks)

            sessions.state.value = SessionState.SignedOut
            onboarding.completed.value = true
            compose.waitForIdle()
            scenario.moveToState(Lifecycle.State.STARTED)
            sessions.state.value = SessionState.SignedIn(user = null)
            compose.waitForIdle()
            assertEquals(1, updates.checks)
            assertEquals("not while paused", emptyList<AppRoute>(), snapshot(scenario).backStack.overlays)

            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()
            assertEquals(listOf<AppRoute>(AppRoutes.Diagnostics), snapshot(scenario).backStack.overlays)

            scenario.recreate()
            compose.waitForIdle()
            assertEquals(1, updates.checks)
            assertEquals(listOf<AppRoute>(AppRoutes.Diagnostics), snapshot(scenario).backStack.overlays)
        }
    }

    @Test
    fun theHostIsReadableOnlyWhileItsActivityLives() {
        signedIn()
        lateinit var activity: ShellHostTestActivity
        launch(entry(Intent.ACTION_MAIN)).use { scenario -> scenario.onActivity { activity = it } }
        assertNull(ShellHost.of(activity))
    }

    private fun signedIn() {
        sessions.state.value = SessionState.SignedIn(user = null)
        onboarding.completed.value = true
    }

    private fun launch(intent: Intent): ActivityScenario<ShellHostTestActivity> =
        ActivityScenario.launch<ShellHostTestActivity>(intent).also { compose.waitForIdle() }

    private fun snapshot(scenario: ActivityScenario<ShellHostTestActivity>): ShellSnapshot {
        compose.waitForIdle()
        var snapshot: ShellSnapshot? = null
        scenario.onActivity { snapshot = checkNotNull(ShellHost.of(it)).snapshot }
        return checkNotNull(snapshot)
    }

    private fun entry(action: String) = Intent(context, ShellHostTestActivity::class.java).setAction(action)

    private fun link(path: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://widgets.alllexey.dev$path"), context, ShellHostTestActivity::class.java)

    /** Every shortcut reported in this test; the shortcut service is the application's, shared by every launch. */
    private fun reportedShortcuts(): List<String> =
        shadowOf(context.getSystemService(ShortcutManager::class.java)).reportedShortcutsUsed

    private data class Row(
        val intent: Intent,
        val expected: ShellBackStack,
        val shortcut: String? = null,
        val activity: String? = null,
    )

    private companion object {
        const val ISU = 100001
        const val OPEN_WEB = "open:web"
        const val OPEN_WEB_LOGIN = "open:web_login"
    }
}

/** The activity `ShellHost` runs in for [ShellHostTest]: what `MainActivity` will do in Navigation 3 mode. */
class ShellHostTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShellHost(this, registry = registry, updateRoute = updateRoute)
    }

    /** What the system calls for an intent that reaches the running activity. */
    fun deliver(intent: Intent) = onNewIntent(intent)

    companion object {
        var updateRoute: ((AppUpdate) -> AppRoute)? = null

        /** Fake tab roots with buttons for the demo-refused keys, the shell host's own alert, placeholders elsewhere. */
        val registry = entryRegistry {
            shellHostEntries()
            entry<AppRoutes.TabRoot> { key, navigator ->
                Column(Modifier.fillMaxSize().testTag("root:${key.tab}")) {
                    Button(onClick = { navigator.open(AppRoutes.MyItmoWeb) }) { Text("open:web") }
                    Button(onClick = { navigator.open(AppRoutes.WebLogin()) }) { Text("open:web_login") }
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Registers [ShellHostTestActivity], which an application's unit tests do not get from a merged manifest. */
class ShellHostTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        shadowOf(packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = ShellHostTestActivity::class.java.name
                packageName = this@ShellHostTestApplication.packageName
            },
        )
    }
}

private class FakeSessionRepository : SessionRepository {
    override val state = MutableStateFlow<SessionState>(SessionState.Initializing)

    override suspend fun initialize() = Unit

    override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> = error("not in the shell")

    override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> = error("not in the shell")

    override suspend fun startDemo() {
        state.value = SessionState.SignedIn(user = null, demo = true)
    }

    override suspend fun signOut() {
        state.value = SessionState.SignedOut
    }
}

/** The first-run flag; null until it is read, which the gate shows as progress. */
private class FakeOnboardingRepository : OnboardingRepository {
    val completed = MutableStateFlow<Boolean?>(null)

    override fun observeCompleted(): Flow<Boolean> = completed.filterNotNull()

    override suspend fun complete() {
        completed.value = true
    }

    override suspend fun reset() {
        completed.value = false
    }
}

private class FakeAppUpdateRepository : AppUpdateRepository {
    var update: AppUpdate? = null
    var checks = 0

    override suspend fun loadUpdate(): AppUpdate? {
        checks++
        return update
    }

    override suspend fun reminder() = AppUpdateReminder(AppVersionName("2.2"), Instant.fromEpochMilliseconds(0))

    override suspend fun markNotified() = Unit

    override suspend fun skip(version: AppVersionName) = Unit
}

private object NoInstallState : InstallStateWatcher {
    override fun start(onDownloaded: () -> Unit) = Unit

    override fun stop() = Unit

    override fun completeUpdate() = Unit
}
