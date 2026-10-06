package dev.alllexey.itmowidgets.feature.auth.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_invalid_credentials
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_network
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class InteractiveLoginViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val session = GatedLoginSession()

    @Test
    fun tokensCountOnlyWhenTheCallbackPagePostedThem() = runTest(main.dispatcher) {
        val viewModel = InteractiveLoginViewModel(session)

        listOf(
            "https://my.itmo.ru/",
            "https://my.itmo.ru/login/callback/extra",
            "http://my.itmo.ru/login/callback",
            "https://evil.example/login/callback",
            ""
        ).forEach { viewModel.onTokensPosted(it, TOKENS) }
        runCurrent()

        assertEquals(emptyList<String>(), session.logins)
        assertFalse(viewModel.uiState.value.completingLogin)

        viewModel.onTokensPosted(CALLBACK_URL, TOKENS)
        runCurrent()

        assertEquals(listOf(TOKENS), session.logins)
        assertTrue(viewModel.uiState.value.completingLogin)
    }

    @Test
    fun aCompletedLoginFinishesTheScreenOnce() = runTest(main.dispatcher) {
        val viewModel = InteractiveLoginViewModel(session)

        viewModel.onTokensPosted(CALLBACK_URL, TOKENS)
        runCurrent()
        session.answer(AppResult.Success(Unit))
        runCurrent()

        assertEquals(InteractiveLoginEvent.Completed, viewModel.events.first())
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun tokensPostedWhileALoginCompletesAreIgnored() = runTest(main.dispatcher) {
        val viewModel = InteractiveLoginViewModel(session)

        viewModel.onTokensPosted(CALLBACK_URL, TOKENS)
        runCurrent()
        viewModel.onTokensPosted(CALLBACK_URL, "{\"access_token\":\"second\"}")
        runCurrent()

        assertEquals(listOf(TOKENS), session.logins)
    }

    @Test
    fun aRejectedTokenResponseShowsTheErrorInsteadOfThePage() = runTest(main.dispatcher) {
        val viewModel = InteractiveLoginViewModel(session)
        val events = mutableListOf<InteractiveLoginEvent>()
        backgroundScope.launch { viewModel.events.toList(events) }
        viewModel.onPageFinished()

        // The repository answers Unauthorized to an oversized or unparsable response.
        viewModel.onTokensPosted(CALLBACK_URL, "x".repeat(100_000))
        runCurrent()
        session.answer(AppResult.Failure(AppError.Unauthorized))
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.completingLogin)
        assertEquals(UiText.Res(Res.string.auth_error_invalid_credentials), state.error)
        assertTrue(state.showsError)
        assertEquals(emptyList<InteractiveLoginEvent>(), events)
    }

    @Test
    fun aNetworkFailureWhileCompletingReadsAsANetworkError() = runTest(main.dispatcher) {
        val viewModel = InteractiveLoginViewModel(session)

        viewModel.onTokensPosted(CALLBACK_URL, TOKENS)
        runCurrent()
        session.answer(AppResult.Failure(AppError.Network))
        runCurrent()

        assertEquals(UiText.Res(Res.string.auth_error_network), viewModel.uiState.value.error)
    }

    @Test
    fun thePageLoadsShowsAndFailsUntilARetry() = runTest(main.dispatcher) {
        val viewModel = InteractiveLoginViewModel(session)
        assertEquals(LoginPage.Loading, viewModel.uiState.value.page)

        viewModel.onPageFinished()
        assertEquals(LoginPage.Shown, viewModel.uiState.value.page)
        assertFalse(viewModel.uiState.value.showsError)

        viewModel.onPageStarted()
        assertEquals(LoginPage.Loading, viewModel.uiState.value.page)

        viewModel.onMainFrameError()
        viewModel.onPageFinished()
        viewModel.onPageStarted()
        assertEquals(LoginPage.Failed, viewModel.uiState.value.page)
        assertTrue(viewModel.uiState.value.showsError)

        viewModel.retry()
        assertEquals(LoginPage.Loading, viewModel.uiState.value.page)
        assertFalse(viewModel.uiState.value.showsError)
    }

    @Test
    fun aRetryAfterAFailedLoginClearsTheErrorAndAcceptsTokensAgain() = runTest(main.dispatcher) {
        val viewModel = InteractiveLoginViewModel(session)
        viewModel.onTokensPosted(CALLBACK_URL, TOKENS)
        runCurrent()
        session.answer(AppResult.Failure(AppError.Unauthorized))
        runCurrent()

        viewModel.retry()
        assertNull(viewModel.uiState.value.error)
        assertEquals(LoginPage.Loading, viewModel.uiState.value.page)

        viewModel.onTokensPosted(CALLBACK_URL, TOKENS)
        runCurrent()
        assertEquals(listOf(TOKENS, TOKENS), session.logins)
        assertTrue(viewModel.uiState.value.completingLogin)
    }

    /** Records each token response and holds the answer until the test gives it. */
    private class GatedLoginSession : SessionRepository {
        private val mutableState = MutableStateFlow<SessionState>(SessionState.SignedOut)
        override val state: StateFlow<SessionState> = mutableState
        val logins = mutableListOf<String>()
        private var pending = CompletableDeferred<AppResult<Unit>>()

        fun answer(result: AppResult<Unit>) {
            pending.complete(result)
            pending = CompletableDeferred()
        }

        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> {
            logins += tokenResponseJson
            return pending.await()
        }

        override suspend fun initialize() = Unit

        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun startDemo() = Unit

        override suspend fun signOut() = Unit
    }

    private companion object {
        const val CALLBACK_URL = "https://my.itmo.ru/login/callback?code=redacted"
        const val TOKENS = "{\"access_token\":\"fake\"}"
    }
}
