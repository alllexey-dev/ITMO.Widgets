package dev.alllexey.itmowidgets.feature.weblogin.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_chrome
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_on_platform
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_unknown
import dev.alllexey.itmowidgets.shared.feature.account.web_login_code_invalid
import dev.alllexey.itmowidgets.shared.feature.account.web_login_not_found
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_macos
import dev.alllexey.itmowidgets.shared.feature.account.web_login_qr_unknown
import dev.alllexey.itmowidgets.shared.feature.account.web_login_requested_at
import dev.alllexey.itmowidgets.shared.feature.account.web_login_scanner_unavailable
import dev.alllexey.itmowidgets.shared.feature.account.web_login_services_disabled
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class WebLoginViewModelTest {
    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val repository = FakeWebLoginRepository()
    private val challenge = Uuid.parse("00000000-0000-0000-0000-000000000042")
    private val preview = WebLoginPreview(
        challengeId = challenge,
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        createdAt = Instant.parse("2026-09-24T09:04:30Z"),
        expiresAt = Instant.parse("2026-09-24T09:06:30Z"),
    )

    @Test fun aScannedLinkIsCheckedThenApproved() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Success(preview)
        val vm = model()

        vm.onScanned("https://dev.widgets.alllexey.dev/app/login?code=abcd2345")
        assertEquals(WebLoginUiState.Checking("ABCD2345"), vm.uiState.value)
        runCurrent()

        val confirm = vm.uiState.value as WebLoginUiState.Confirm
        assertEquals(challenge, confirm.preview.challengeId)
        assertEquals(UiText.Res(Res.string.web_login_browser_on_platform, listOf(
            UiText.Res(Res.string.web_login_browser_chrome), UiText.Res(Res.string.web_login_platform_macos))), confirm.browser)
        // Moscow time, as the rest of the app shows it.
        assertEquals(UiText.Res(Res.string.web_login_requested_at, listOf("12:04")), confirm.requestedAt)

        vm.approve()
        assertTrue((vm.uiState.value as WebLoginUiState.Confirm).approving)
        vm.approve()
        runCurrent()

        assertEquals(WebLoginUiState.Done, vm.uiState.value)
        assertEquals(listOf("preview:ABCD2345", "approve:$challenge"), repository.calls)
    }

    @Test fun aTypedCodeIsNormalizedAndAMalformedOneNeverReachesBackend() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Success(preview)
        val vm = model()

        vm.onCodeChanged("abcd-234")
        vm.submit()
        assertEquals(WebLoginUiState.Input("abcd-234", UiText.Res(Res.string.web_login_code_invalid)), vm.uiState.value)

        vm.onCodeChanged("abcd-2345")
        assertEquals(WebLoginUiState.Input("abcd-2345"), vm.uiState.value)
        vm.submit()
        runCurrent()

        assertTrue(vm.uiState.value is WebLoginUiState.Confirm)
        assertEquals(listOf("preview:ABCD2345"), repository.calls)
    }

    @Test fun aQRThatIsNotASignInLinkKeepsTheTypedCode() = runTest(main.dispatcher) {
        val vm = model()
        vm.onCodeChanged("ABCD")

        vm.onScanned("https://t.me/itmowidgets")

        assertEquals(WebLoginUiState.Input("ABCD", UiText.Res(Res.string.web_login_qr_unknown)), vm.uiState.value)
        assertTrue(repository.calls.isEmpty())
    }

    @Test fun anUnavailableScannerLeavesManualEntry() = runTest(main.dispatcher) {
        val vm = model()
        vm.onCodeChanged("ABCD")

        vm.onScannerUnavailable()

        assertEquals(WebLoginUiState.Input("ABCD", UiText.Res(Res.string.web_login_scanner_unavailable)), vm.uiState.value)
    }

    @Test fun anUnknownCodeStaysInTheFieldToBeFixed() = runTest(main.dispatcher) {
        val vm = model()
        vm.onCodeChanged("ABCD2345")

        vm.submit()
        runCurrent()

        assertEquals(WebLoginUiState.Input("ABCD2345", UiText.Res(Res.string.web_login_not_found)), vm.uiState.value)
    }

    @Test fun aSignInThatExpiredBeforeApprovalCannotBeRetried() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Success(preview)
        repository.approval = AppResult.Failure(AppError.NotFound)
        val vm = model()
        vm.onScanned("ABCD2345")
        runCurrent()

        vm.approve()
        runCurrent()
        assertEquals(WebLoginUiState.Error(UiText.Res(Res.string.web_login_not_found), ""), vm.uiState.value)

        vm.retry()
        assertEquals(WebLoginUiState.Input(""), vm.uiState.value)
    }

    @Test fun withoutTheConnectionTheSheetSaysWhatIsMissing() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Failure(AppError.CustomServicesDisabled)
        val vm = model()

        vm.onScanned("ABCD2345")
        runCurrent()

        assertEquals(WebLoginUiState.Error(UiText.Res(Res.string.web_login_services_disabled), ""), vm.uiState.value)
    }

    @Test fun aNetworkErrorChecksTheSameCodeAgainOnRetry() = runTest(main.dispatcher) {
        repository.previews["ABCD2345"] = AppResult.Failure(AppError.Network)
        val vm = model()
        vm.onScanned("ABCD2345")
        runCurrent()
        assertEquals(WebLoginUiState.Error(AppError.Network.toUiText(), "ABCD2345"), vm.uiState.value)

        repository.previews["ABCD2345"] = AppResult.Success(preview.copy(userAgent = null))
        vm.retry()
        runCurrent()

        val confirm = vm.uiState.value as WebLoginUiState.Confirm
        assertEquals(UiText.Res(Res.string.web_login_browser_unknown), confirm.browser)
        assertEquals(listOf("preview:ABCD2345", "preview:ABCD2345"), repository.calls)
    }

    @Test fun goingBackFromTheBrowserCardKeepsTheCodeButNotDuringApproval() = runTest(main.dispatcher) {
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

    @Test fun theTypedCodeSurvivesProcessDeath() = runTest(main.dispatcher) {
        val handle = SavedStateHandle()
        model(handle).onCodeChanged("ABCD23")

        assertEquals(WebLoginUiState.Input("ABCD23"), model(handle).uiState.value)
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) =
        WebLoginViewModel(handle, repository, FixedAcademicTime())

    private class FakeWebLoginRepository : WebLoginRepository {
        val previews = mutableMapOf<String, AppResult<WebLoginPreview>>()
        var approval: AppResult<Unit> = AppResult.Success(Unit)
        var pendingApproval: CompletableDeferred<AppResult<Unit>>? = null
        val calls = mutableListOf<String>()

        override suspend fun preview(code: String): AppResult<WebLoginPreview> {
            calls += "preview:$code"
            return previews[code] ?: AppResult.Failure(AppError.NotFound)
        }

        override suspend fun approve(challengeId: Uuid): AppResult<Unit> {
            calls += "approve:$challengeId"
            return pendingApproval?.await() ?: approval
        }
    }
}
