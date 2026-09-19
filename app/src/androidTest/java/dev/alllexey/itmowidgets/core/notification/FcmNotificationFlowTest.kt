package dev.alllexey.itmowidgets.core.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.app.AppOverlayHostFragment
import dev.alllexey.itmowidgets.app.OnboardingTestEntryPoint
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic local notifications and authentication; never sends Firebase messages or registers test users. */
@RunWith(AndroidJUnit4::class)
class FcmNotificationFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dependencies = EntryPointAccessors.fromApplication(context, NotificationDebugEntryPoint::class.java)
    private val onboarding = EntryPointAccessors
        .fromApplication(context, OnboardingTestEntryPoint::class.java)
        .onboarding()

    @Test
    fun notificationsGroupAndOpenTheirTargetsOnceAfterAuthenticationAndRecreation() {
        val originalServices = runBlocking { dependencies.settings().getCustomServicesEnabled() }
        runBlocking { dependencies.settings().setCustomServicesEnabled(false) }
        // Notification routing is what this test is about; the first-run flow would hold the window.
        runBlocking { onboarding.complete() }
        dependencies.tokens().clearTokens()
        val manager = context.getSystemService(NotificationManager::class.java)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_USER_PROFILE
                putExtra(UserScreenArgs.ISU, 100001)
            }
            instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            run {
                eventually { onActivity { assertEquals(R.id.auth, root(it).navController.currentDestination?.id) } }
                recreate()
                runBlocking {
                    withTimeout(30_000) { dependencies.session().completeItmoIdLogin(tokenResponse()) }
                }
                eventually { assertProfile(100001) }
                recreate()
                eventually { assertProfile(100001) }

                val notifier = dependencies.notifier()
                notifier.show(AppNotification(AppNotificationChannels.SPORT, 42,
                    UiText.Resource(R.string.notification_sport_success),
                    UiText.Resource(R.string.notification_sport_lesson, listOf("Секция плавания", "21 сент., 12:00")),
                    NotificationDestination.Sport))
                val names = listOf("Александра Константиновна Длиннофамильская", "Иван Иванов")
                names.forEachIndexed { index, name ->
                    notifier.show(AppNotification(AppNotificationChannels.FRIENDS, 100001 + index,
                        UiText.Resource(R.string.notification_channel_friends),
                        UiText.Resource(if (index == 0) R.string.notification_friend_request else R.string.notification_friend_accepted,
                            listOf(name)), NotificationDestination.UserProfile(100001 + index)))
                }
                eventually {
                    assertEquals(2, manager.activeNotifications.count { it.tag == AppNotificationChannels.FRIENDS })
                    assertEquals(1, manager.activeNotifications.count { it.tag == "friends-summary" })
                }
                shell("cmd statusbar expand-notifications")
                instrumentation.uiAutomation.waitForIdle(500, 5_000)
                if (InstrumentationRegistry.getArguments().getString("holdShade") == "true") {
                    // External adb screenshots capture SystemUI independently of instrumentation.
                    instrumentation.sendStatus(0, Bundle().apply { putString("fcm_visual", "shade_ready") })
                    Thread.sleep(12_000)
                }
                shell("cmd statusbar collapse")
                manager.activeNotifications.single { it.tag == AppNotificationChannels.FRIENDS && it.id == 100002 }
                    .notification.contentIntent.send()
                eventually { assertProfile(100002) }
                recreate()
                eventually { assertProfile(100002) }
                onActivity { it.onBackPressedDispatcher.onBackPressed() }
                eventually { onActivity {
                    assertNull(it.supportFragmentManager.findFragmentByTag("app-overlay"))
                    assertEquals(R.id.navigation_me, root(it).navController.currentDestination?.id)
                } }
                manager.activeNotifications.single { it.tag == AppNotificationChannels.SPORT }.notification.contentIntent.send()
                eventually { onActivity { assertEquals(R.id.navigation_sport, root(it).navController.currentDestination?.id) } }
                notifier.clear()
                assertTrue(manager.activeNotifications.isEmpty())
            }
        } finally {
            runCatching { onActivity { it.finish() } }
            dependencies.notifier().clear()
            dependencies.tokens().clearTokens()
            runBlocking {
                dependencies.settings().setCustomServicesEnabled(originalServices)
                onboarding.reset()
            }
        }
    }

    private fun assertProfile(isu: Int) {
        onActivity {
            assertEquals(R.id.navigation_me, root(it).navController.currentDestination?.id)
            val overlay = it.supportFragmentManager.findFragmentByTag("app-overlay") as? AppOverlayHostFragment
            assertNotNull(overlay)
            assertEquals(R.id.user_profile, overlay!!.navController.currentDestination?.id)
            assertEquals(isu, overlay.navController.currentBackStackEntry?.arguments?.getInt(UserScreenArgs.ISU))
        }
    }

    // ActivityScenario matches the original Intent and loses track when onNewIntent replaces it.
    // Observe the real resumed activity instead, including notification-initiated recreation.
    private fun onActivity(block: (MainActivity) -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                block(activity)
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
    }

    private fun recreate() {
        var previous: MainActivity? = null
        onActivity { previous = it; it.recreate() }
        eventually { onActivity { assertNotSame(previous, it) } }
    }

    private fun root(activity: MainActivity) = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private fun tokenResponse(): String {
        fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val idToken = encode("{\"alg\":\"none\"}") + "." + encode("{\"isu\":123456,\"name\":\"Synthetic test user\"}") + ".synthetic"
        return """{"access_token":"synthetic-access","expires_in":3600,"refresh_token":"synthetic-refresh","refresh_expires_in":3600,"id_token":"$idToken"}"""
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = 80, delayMillis = 100, message = "Notification route did not settle", assertion = assertion)
}
