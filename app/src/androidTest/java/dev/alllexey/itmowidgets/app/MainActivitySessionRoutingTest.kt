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
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.storage.AndroidKeystoreTokenCipher
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import java.io.File
import java.time.Clock
import java.util.Base64
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.`is`

@RunWith(AndroidJUnit4::class)
class MainActivitySessionRoutingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tokenFile = File(context.noBackupFilesDir, TOKEN_FILE_NAME)

    @After
    fun clearTokenFile() {
        tokenFile.delete()
    }

    @Test
    fun activeSessionOpensAuthenticatedGraphWithoutShowingAuthDestination() {
        seedActiveSession()

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

    private fun seedActiveSession() {
        val storage = MyItmoStorage(
            tokenFile = tokenFile,
            tokenCipher = AndroidKeystoreTokenCipher(),
            clock = Clock.systemUTC()
        )
        storage.replaceWithTokens(
            SessionTokens(
                accessToken = "test-access",
                accessExpiresInSeconds = TOKEN_LIFETIME_SECONDS,
                refreshToken = "test-refresh",
                refreshExpiresInSeconds = TOKEN_LIFETIME_SECONDS,
                idToken = testIdToken()
            )
        )
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

    private fun eventually(assertion: () -> Unit) {
        var lastFailure: Throwable? = null
        repeat(RETRY_COUNT) {
            try {
                assertion()
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                Thread.sleep(RETRY_DELAY_MILLIS)
            }
        }
        throw AssertionError("Condition was not met in time", lastFailure)
    }

    private companion object {
        const val TOKEN_FILE_NAME = "myitmo_tokens.enc"
        const val TOKEN_LIFETIME_SECONDS = 3_600L
        const val RETRY_COUNT = 20
        const val RETRY_DELAY_MILLIS = 100L
    }
}
