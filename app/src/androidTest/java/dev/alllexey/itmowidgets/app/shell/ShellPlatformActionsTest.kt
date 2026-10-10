package dev.alllexey.itmowidgets.app.shell

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.feature.settings.presentation.MaintenancePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.testing.ShellProbe
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The system pages the Compose shell's entries open through `LocalPlatformActions` (SH-FIX-PA), on the real
 * `MainActivity` in the demo session: the privacy policy row starts the browser on the site's page, and the locked
 * calendar dialog's «Открыть настройки» starts the app's system settings page. An [Instrumentation.ActivityMonitor]
 * answers both starts, so no browser or settings app opens; the permission request passes through. Only the Compose
 * shell is read; the legacy shell's Fragments open these pages themselves.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // WindowInspector reads the dialog's window
class ShellPlatformActionsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = TestUi.instrumentation
    private val started: MutableList<Intent> = Collections.synchronizedList(mutableListOf())
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
            if (intent.action in EXTERNAL_ACTIONS) {
                started += Intent(intent)
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            } else {
                null
            }
    }

    @Before
    fun startTheDemo() {
        TestSession.signOut()
        val dependencies = EntryPointAccessors.fromApplication(context, NotificationDebugEntryPoint::class.java)
        runBlocking { dependencies.session().startDemo() }
        instrumentation.addMonitor(monitor)
    }

    @After
    fun leaveTheDemo() {
        instrumentation.removeMonitor(monitor)
        CALENDAR_PERMISSIONS.forEach { shell("pm clear-permission-flags ${context.packageName} $it user-fixed") }
        TestSession.signOut()
    }

    @Test
    fun thePrivacyPolicyRowOpensTheSiteInTheBrowser() = onComposeShell {
        onActivity { it.navigator().open(AppRoutes.Settings(SettingsPage.MAINTENANCE.name)) }

        click(PRIVACY_POLICY)

        eventually {
            val intent = started.single()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            val page = BuildConfig.WIDGETS_BASE_URL + MaintenancePageProvider.PRIVACY_POLICY_PATH
            assertEquals(page, intent.dataString)
        }
    }

    @Test
    fun theLockedCalendarDialogOpensTheAppSettings() {
        assumeTrue(
            "calendar access is already granted",
            CALENDAR_PERMISSIONS.none { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
        )
        // Denied for good: the request answers without a dialog, and the shell explains with the locked dialog.
        CALENDAR_PERMISSIONS.forEach { shell("pm set-permission-flags ${context.packageName} $it user-fixed") }
        onComposeShell {
            onActivity { it.navigator().open(AppRoutes.Settings(SettingsPage.SCHEDULE.name)) }

            click(CALENDAR_SYNC)
            click(OPEN_SETTINGS)

            eventually {
                val intent = started.single()
                assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
                assertEquals(Uri.fromParts("package", context.packageName, null), intent.data)
            }
        }
    }

    /** Clicks the node whose text holds [text], in any of the app's windows, once it shows. */
    private fun click(text: String) {
        eventually {
            var clicked = false
            instrumentation.runOnMainSync {
                val node = WindowInspector.getGlobalWindowViews().flatMap(::clickableNodes).lastOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
                }
                clicked = node?.config?.get(SemanticsActions.OnClick)?.action?.invoke() == true
            }
            check(clicked) { "no clickable «$text»" }
        }
        TestUi.idle()
    }

    private fun onComposeShell(body: () -> Unit) {
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            var compose = false
            onActivity { compose = ShellHost.of(it) != null }
            assumeTrue("the Compose shell runs", compose)
            eventually { assertEquals(AppTab.HOME, ShellProbe.current().tab) }
            body()
        } finally {
            runCatching { onActivity { it.finish() } }
        }
    }

    /** The Compose shell's navigator; `ShellHost` keeps it private, so the test reads the field. */
    private fun MainActivity.navigator(): Nav3AppNavigator {
        val host = checkNotNull(ShellHost.of(this)) { "MainActivity runs the legacy shell" }
        val field = ShellHost::class.java.getDeclaredField("navigator").apply { isAccessible = true }
        return checkNotNull(field.get(host) as Nav3AppNavigator?) { "the Compose shell has not composed yet" }
    }

    private fun onActivity(block: (MainActivity) -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                block(activity)
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = 80, delayMillis = 100, message = "The shell did not settle", assertion = assertion)

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    /** The merged nodes with a click action, so a row's or a button's text is on the node itself. */
    private fun clickableNodes(view: View): List<SemanticsNode> = composeRoots(view).flatMap { root ->
        generateSequence(listOf(root.semanticsOwner.rootSemanticsNode)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten().filter { SemanticsActions.OnClick in it.config }.toList()
    }

    private fun composeRoots(view: View): List<ViewRootForTest> = when (view) {
        is ViewRootForTest -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { composeRoots(view.getChildAt(it)) }
        else -> emptyList()
    }

    private companion object {
        val EXTERNAL_ACTIONS = setOf(Intent.ACTION_VIEW, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val CALENDAR_PERMISSIONS = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        /** The maintenance page's row, the schedule page's switch and the locked dialog's confirm button. */
        const val PRIVACY_POLICY = "Политика конфиденциальности"
        const val CALENDAR_SYNC = "Синхронизация с календарём"
        const val OPEN_SETTINGS = "Открыть настройки"
    }
}
