package dev.alllexey.itmowidgets.feature.weblogin.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.Browser
import dev.alllexey.itmowidgets.feature.weblogin.domain.Platform
import dev.alllexey.itmowidgets.feature.weblogin.domain.WebLoginCode
import dev.alllexey.itmowidgets.feature.weblogin.domain.describeUserAgent
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_chrome
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_edge
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_firefox
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_on_platform
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_opera
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_safari
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_samsung
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_unknown
import dev.alllexey.itmowidgets.shared.feature.account.web_login_browser_yandex
import dev.alllexey.itmowidgets.shared.feature.account.web_login_code_invalid
import dev.alllexey.itmowidgets.shared.feature.account.web_login_not_found
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_android
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_chrome_os
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_ios
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_ipados
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_linux
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_macos
import dev.alllexey.itmowidgets.shared.feature.account.web_login_platform_windows
import dev.alllexey.itmowidgets.shared.feature.account.web_login_qr_unknown
import dev.alllexey.itmowidgets.shared.feature.account.web_login_requested_at
import dev.alllexey.itmowidgets.shared.feature.account.web_login_scanner_unavailable
import dev.alllexey.itmowidgets.shared.feature.account.web_login_services_disabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource

/** Approves a browser's sign-in to the web version: code from the QR or typed, a look at the browser, then «Войти». */
class WebLoginViewModel(
    private val handle: SavedStateHandle,
    private val repository: WebLoginRepository,
    private val time: AcademicTimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WebLoginUiState>(WebLoginUiState.Input(handle[KEY_CODE] ?: ""))
    val uiState: StateFlow<WebLoginUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun onCodeChanged(code: String) {
        if (_uiState.value !is WebLoginUiState.Input) return
        handle[KEY_CODE] = code
        _uiState.value = WebLoginUiState.Input(code)
    }

    /** A QR that is not a sign-in link leaves the typed code alone. */
    fun onScanned(raw: String) {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        val code = WebLoginCode.parse(raw)
        if (code == null) {
            _uiState.value = current.copy(error = UiText.Res(Res.string.web_login_qr_unknown))
            return
        }
        check(code)
    }

    /** The Play services scanner could not start, for example while its module is still downloading. */
    fun onScannerUnavailable() {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        _uiState.value = current.copy(error = UiText.Res(Res.string.web_login_scanner_unavailable))
    }

    fun submit() {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        val code = WebLoginCode.parse(current.code)
        if (code == null) {
            _uiState.value = current.copy(error = UiText.Res(Res.string.web_login_code_invalid))
            return
        }
        check(code)
    }

    fun approve() {
        val current = _uiState.value as? WebLoginUiState.Confirm ?: return
        if (current.approving) return
        _uiState.value = current.copy(approving = true)
        job = viewModelScope.launch {
            _uiState.value = when (val result = repository.approve(current.preview.challengeId)) {
                is AppResult.Success -> WebLoginUiState.Done.also { handle.remove<String>(KEY_CODE) }
                is AppResult.Failure -> failure(result.error, current.code)
            }
        }
    }

    /** Back from the browser card or an error to the code field, keeping what can be retried. */
    fun editCode() {
        val code = when (val current = _uiState.value) {
            // An approval in flight may already have signed the browser in.
            is WebLoginUiState.Confirm -> if (current.approving) return else current.code
            is WebLoginUiState.Error -> current.code
            else -> return
        }
        job?.cancel()
        onInput(code)
    }

    /** An error with a code checks it again; one without goes back to an empty field. */
    fun retry() {
        val current = _uiState.value as? WebLoginUiState.Error ?: return
        if (current.code.isEmpty()) onInput("") else check(current.code)
    }

    private fun onInput(code: String) {
        handle[KEY_CODE] = code
        _uiState.value = WebLoginUiState.Input(code)
    }

    private fun check(code: String) {
        handle[KEY_CODE] = code
        _uiState.value = WebLoginUiState.Checking(code)
        job = viewModelScope.launch {
            _uiState.value = when (val result = repository.preview(code)) {
                is AppResult.Success -> confirm(code, result.value)
                // A mistyped code is fixed in place rather than on a separate error screen.
                is AppResult.Failure -> if (result.error == AppError.NotFound) {
                    WebLoginUiState.Input(code, UiText.Res(Res.string.web_login_not_found))
                } else failure(result.error, code)
            }
        }
    }

    private fun confirm(code: String, preview: WebLoginPreview): WebLoginUiState.Confirm {
        // The academic zone like every time the app shows; a debug date override does not move a real request.
        val requestedAt = preview.createdAt.toLocalDateTime(time.timeZone).time.format(DateTexts.TIME)
        return WebLoginUiState.Confirm(code, preview, browserText(preview.userAgent),
            UiText.Res(Res.string.web_login_requested_at, listOf(requestedAt)))
    }

    /** A used or expired sign-in cannot be retried with the same code. */
    private fun failure(error: AppError, code: String): WebLoginUiState.Error = when (error) {
        AppError.NotFound -> WebLoginUiState.Error(UiText.Res(Res.string.web_login_not_found), "")
        AppError.CustomServicesDisabled -> WebLoginUiState.Error(UiText.Res(Res.string.web_login_services_disabled), "")
        else -> WebLoginUiState.Error(error.toUiText(), code)
    }

    private companion object {
        const val KEY_CODE = "web_login_code"
    }
}

/** «Chrome на macOS», «Chrome», «Браузер на Android» or just «Браузер». */
internal fun browserText(userAgent: String?): UiText {
    val description = describeUserAgent(userAgent)
    val browser = UiText.Res(description.browser?.nameRes() ?: Res.string.web_login_browser_unknown)
    val platform = description.platform ?: return browser
    return UiText.Res(Res.string.web_login_browser_on_platform, listOf(browser, UiText.Res(platform.nameRes())))
}

private fun Browser.nameRes(): StringResource = when (this) {
    Browser.YANDEX -> Res.string.web_login_browser_yandex
    Browser.EDGE -> Res.string.web_login_browser_edge
    Browser.OPERA -> Res.string.web_login_browser_opera
    Browser.SAMSUNG -> Res.string.web_login_browser_samsung
    Browser.FIREFOX -> Res.string.web_login_browser_firefox
    Browser.CHROME -> Res.string.web_login_browser_chrome
    Browser.SAFARI -> Res.string.web_login_browser_safari
}

private fun Platform.nameRes(): StringResource = when (this) {
    Platform.WINDOWS -> Res.string.web_login_platform_windows
    Platform.MACOS -> Res.string.web_login_platform_macos
    Platform.LINUX -> Res.string.web_login_platform_linux
    Platform.CHROME_OS -> Res.string.web_login_platform_chrome_os
    Platform.ANDROID -> Res.string.web_login_platform_android
    Platform.IOS -> Res.string.web_login_platform_ios
    Platform.IPADOS -> Res.string.web_login_platform_ipados
}
