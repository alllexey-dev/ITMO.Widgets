package dev.alllexey.itmowidgets.app

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import java.io.File
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not

@RunWith(AndroidJUnit4::class)
class MainActivitySessionRoutingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tokenFile = File(context.noBackupFilesDir, TOKEN_FILE_NAME)
    private val dependencies =
        EntryPointAccessors.fromApplication(context, NotificationDebugEntryPoint::class.java)
    private val onboarding = EntryPointAccessors
        .fromApplication(context, OnboardingTestEntryPoint::class.java)
        .onboarding()

    @After
    fun clearSessionAndFirstRunFlag() {
        runBlocking {
            dependencies.session().signOut()
            onboarding.reset()
        }
        dependencies.tokens().clearTokens()
        tokenFile.delete()
    }

    @Test
    fun activeSessionOpensAuthenticatedGraphWithoutShowingAuthDestination() {
        seedActiveSession()
        runBlocking { onboarding.complete() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var decorView: View
            scenario.onActivity { activity -> decorView = activity.window.decorView }

            eventually {
                onView(withId(R.id.bottom_nav_view))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }

            onView(withText(R.string.auth_title))
                .inRoot(withDecorView(`is`(decorView)))
                .check(doesNotExist())
        }
    }

    @Test
    fun firstRunOpensTheFlowInsteadOfTheBottomTabs() {
        seedActiveSession()
        runBlocking { onboarding.reset() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var decorView: View
            scenario.onActivity { activity -> decorView = activity.window.decorView }

            eventually {
                onView(withId(R.id.onboarding_root))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }

            // The flow owns the window until it is passed.
            onView(withId(R.id.bottom_nav_view))
                .inRoot(withDecorView(`is`(decorView)))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun aReplayTakesTheWindowBackFromTheTabs() {
        seedActiveSession()
        runBlocking { onboarding.complete() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var decorView: View
            scenario.onActivity { activity -> decorView = activity.window.decorView }
            eventually {
                onView(withId(R.id.bottom_nav_view))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }

            // What `Повторить первоначальную настройку` does: only the flag changes.
            runBlocking { onboarding.reset() }

            eventually {
                onView(withId(R.id.onboarding_root))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }
            onView(withId(R.id.bottom_nav_view))
                .inRoot(withDecorView(`is`(decorView)))
                .check(matches(not(isDisplayed())))
        }
    }

    /**
     * Signs in through the application's own repository.
     *
     * Writing the token file directly would be invisible to a `SessionRepository`
     * that another test already initialised: `initialize()` reads the store once
     * and returns early afterwards.
     */
    private fun seedActiveSession() {
        runBlocking {
            withTimeout(SIGN_IN_TIMEOUT_MILLIS) {
                dependencies.session().completeItmoIdLogin(tokenResponse())
            }
        }
    }

    private fun tokenResponse(): String {
        return """{"access_token":"test-access","expires_in":$TOKEN_LIFETIME_SECONDS,""" +
            """"refresh_token":"test-refresh","refresh_expires_in":$TOKEN_LIFETIME_SECONDS,""" +
            """"id_token":"${testIdToken()}"}"""
    }

    private fun testIdToken(): String {
        val header = encodeBase64Url("{\"alg\":\"none\"}")
        val payload = encodeBase64Url(
            "{\"isu\":123456,\"name\":\"Тестовый пользователь\"}"
        )
        return "$header.$payload.signature"
    }

    private fun encodeBase64Url(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = RETRY_COUNT, delayMillis = RETRY_DELAY_MILLIS, assertion = assertion)

    private companion object {
        const val TOKEN_FILE_NAME = "myitmo_tokens.enc"
        const val TOKEN_LIFETIME_SECONDS = 3_600L
        const val SIGN_IN_TIMEOUT_MILLIS = 30_000L
        const val RETRY_COUNT = 20
        const val RETRY_DELAY_MILLIS = 100L
    }
}
