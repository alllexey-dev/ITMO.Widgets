package dev.alllexey.itmowidgets.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A synthetic session (ISU 123456) and the first-run flag in the application's real graph. */
object TestSession {
    private const val TOKEN_FILE_NAME = "myitmo_tokens.enc"
    private const val TOKEN_LIFETIME_SECONDS = 3_600L
    private const val SIGN_IN_TIMEOUT_MILLIS = 30_000L

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val onboarding get() = TestGraph.get<OnboardingRepository>()

    /**
     * Signs in through the application's own repository.
     *
     * Writing the token file directly would be invisible to a `SessionRepository`
     * that another test already initialised: `initialize()` reads the store once
     * and returns early afterwards.
     */
    fun seedActiveSession() {
        runBlocking {
            withTimeout(SIGN_IN_TIMEOUT_MILLIS) {
                TestGraph.get<SessionRepository>().completeItmoIdLogin(tokenResponse())
            }
        }
    }

    fun signOut() {
        runBlocking { TestGraph.get<SessionRepository>().signOut() }
        TestGraph.get<SessionTokenStore>().clearTokens()
        File(context.noBackupFilesDir, TOKEN_FILE_NAME).delete()
    }

    fun completeOnboarding() {
        runBlocking { onboarding.complete() }
    }

    fun resetOnboarding() {
        runBlocking { onboarding.reset() }
    }

    private fun tokenResponse(): String {
        return """{"access_token":"test-access","expires_in":$TOKEN_LIFETIME_SECONDS,""" +
            """"refresh_token":"test-refresh","refresh_expires_in":$TOKEN_LIFETIME_SECONDS,""" +
            """"id_token":"${testIdToken()}"}"""
    }

    private fun testIdToken(): String {
        val header = encodeBase64Url("{\"alg\":\"none\"}")
        val payload = encodeBase64Url("{\"isu\":123456,\"name\":\"Тестовый пользователь\"}")
        return "$header.$payload.signature"
    }

    private fun encodeBase64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}
