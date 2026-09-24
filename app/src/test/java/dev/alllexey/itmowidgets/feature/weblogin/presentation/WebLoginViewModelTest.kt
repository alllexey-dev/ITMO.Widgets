package dev.alllexey.itmowidgets.feature.weblogin.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebLoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeWebLoginRepository()
    private val challenge = UUID.fromString("00000000-0000-0000-0000-000000000042")
    private val preview = WebLoginPreview(
        challengeId = challenge,
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        createdAt = OffsetDateTime.parse("2026-09-24T09:04:30Z"),
        expiresAt = OffsetDateTime.parse("2026-09-24T09:06:30Z"),
    )

    @Test fun `a scanned link is checked then approved`() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Success(preview)
        val vm = model()

        vm.onScanned("https://dev.widgets.alllexey.dev/app/login?code=abcd2345")
        assertEquals(WebLoginUiState.Checking("ABCD2345"), vm.uiState.value)
        runCurrent()

        val confirm = vm.uiState.value as WebLoginUiState.Confirm
        assertEquals(challenge, confirm.preview.challengeId)
        assertEquals(UiText.Resource(R.string.web_login_browser_on_platform, listOf(
            UiText.Resource(R.string.web_login_browser_chrome), UiText.Resource(R.string.web_login_platform_macos))), confirm.browser)
        // Moscow time, as the rest of the app shows it.
        assertEquals(UiText.Resource(R.string.web_login_requested_at, listOf("12:04")), confirm.requestedAt)

        vm.approve()
        assertTrue((vm.uiState.value as WebLoginUiState.Confirm).approving)
        vm.approve()
        runCurrent()

        assertEquals(WebLoginUiState.Done, vm.uiState.value)
        assertEquals(listOf("preview:ABCD2345", "approve:$challenge"), repository.calls)
    }

    @Test fun `a typed code is normalized and a malformed one never reaches Backend`() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Success(preview)
        val vm = model()

        vm.onCodeChanged("abcd-234")
        vm.submit()
        assertEquals(WebLoginUiState.Input("abcd-234", UiText.Resource(R.string.web_login_code_invalid)), vm.uiState.value)

        vm.onCodeChanged("abcd-2345")
        assertEquals(WebLoginUiState.Input("abcd-2345"), vm.uiState.value)
        vm.submit()
        runCurrent()

        assertTrue(vm.uiState.value is WebLoginUiState.Confirm)
        assertEquals(listOf("preview:ABCD2345"), repository.calls)
    }

    @Test fun `a QR that is not a sign-in link keeps the typed code`() = runTest(main.dispatcher) {
        val vm = model()
        vm.onCodeChanged("ABCD")

        vm.onScanned("https://t.me/itmowidgets")

        assertEquals(WebLoginUiState.Input("ABCD", UiText.Resource(R.string.web_login_qr_unknown)), vm.uiState.value)
        assertTrue(repository.calls.isEmpty())
    }

    @Test fun `an unavailable scanner leaves manual entry`() = runTest(main.dispatcher) {
        val vm = model()
        vm.onCodeChanged("ABCD")

        vm.onScannerUnavailable()

        assertEquals(WebLoginUiState.Input("ABCD", UiText.Resource(R.string.web_login_scanner_unavailable)), vm.uiState.value)
    }

    @Test fun `an unknown code stays in the field to be fixed`() = runTest(main.dispatcher) {
        val vm = model()
        vm.onCodeChanged("ABCD2345")

        vm.submit()
        runCurrent()

        assertEquals(WebLoginUiState.Input("ABCD2345", UiText.Resource(R.string.web_login_not_found)), vm.uiState.value)
    }

    @Test fun `a sign-in that expired before approval cannot be retried`() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Success(preview)
        repository.approval = AppResult.Failure(AppError.NotFound)
        val vm = model()
        vm.onScanned("ABCD2345")
        runCurrent()

        vm.approve()
        runCurrent()
        assertEquals(WebLoginUiState.Error(UiText.Resource(R.string.web_login_not_found), ""), vm.uiState.value)

        vm.retry()
        assertEquals(WebLoginUiState.Input(""), vm.uiState.value)
    }

    @Test fun `without the connection the sheet says what is missing`() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Failure(AppError.CustomServicesDisabled)
        val vm = model()

        vm.onScanned("ABCD2345")
        runCurrent()

        assertEquals(WebLoginUiState.Error(UiText.Resource(R.string.web_login_services_disabled), ""), vm.uiState.value)
    }

    @Test fun `a network error checks the same code again on retry`() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Failure(AppError.Network)
        val vm = model()
        vm.onScanned("ABCD2345")
        runCurrent()
        assertEquals(WebLoginUiState.Error(UiText.Resource(R.string.common_error_network), "ABCD2345"), vm.uiState.value)

        repository.previews["ABCD2345"] = AppResult.Success(preview.copy(userAgent = null))
        vm.retry()
        runCurrent()

        val confirm = vm.uiState.value as WebLoginUiState.Confirm
        assertEquals(UiText.Resource(R.string.web_login_browser_unknown), confirm.browser)
        assertEquals(listOf("preview:ABCD2345", "preview:ABCD2345"), repository.calls)
    }

    @Test fun `going back from the browser card keeps the code but not during approval`() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Success(preview)
        val pending = CompletableDeferred<AppResult<Unit>>()
        repository.pendingApproval = pending
        val vm = model()
        vm.onScanned("ABCD2345")
        runCurrent()

        vm.editCode()
        assertEquals(WebLoginUiState.Input("ABCD2345"), vm.uiState.value)

        vm.submit()
        runCurrent()
        vm.approve()
        runCurrent()
        vm.editCode()
        assertTrue((vm.uiState.value as WebLoginUiState.Confirm).approving)

        pending.complete(AppResult.Success(Unit))
        runCurrent()
        assertEquals(WebLoginUiState.Done, vm.uiState.value)
    }

    @Test fun `the typed code survives process death`() = runTest(main.dispatcher) {
        val handle = SavedStateHandle()
        model(handle).onCodeChanged("ABCD23")

        assertEquals(WebLoginUiState.Input("ABCD23"), model(handle).uiState.value)
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) =
        WebLoginViewModel(handle, repository, Clock.fixed(Instant.parse("2026-09-24T09:05:00Z"), ZoneId.of("Europe/Moscow")))

    private class FakeWebLoginRepository : WebLoginRepository {
        val previews = mutableMapOf<String, AppResult<WebLoginPreview>>()
        var approval: AppResult<Unit> = AppResult.Success(Unit)
        var pendingApproval: CompletableDeferred<AppResult<Unit>>? = null
        val calls = mutableListOf<String>()

        override suspend fun preview(code: String): AppResult<WebLoginPreview> {
            calls += "preview:$code"
            return previews[code] ?: AppResult.Failure(AppError.NotFound)
        }

        override suspend fun approve(challengeId: UUID): AppResult<Unit> {
            calls += "approve:$challengeId"
            return pendingApproval?.await() ?: approval
        }
    }
}
