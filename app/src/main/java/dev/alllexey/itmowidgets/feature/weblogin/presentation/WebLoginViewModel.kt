package dev.alllexey.itmowidgets.feature.weblogin.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicClock
import dev.alllexey.itmowidgets.core.ui.toUiText
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.Browser
import dev.alllexey.itmowidgets.feature.weblogin.domain.Platform
import dev.alllexey.itmowidgets.feature.weblogin.domain.WebLoginCode
import dev.alllexey.itmowidgets.feature.weblogin.domain.describeUserAgent
import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WebLoginUiState {
    /** Typing or scanning; [error] says why the last code was not taken. */
    data class Input(val code: String = "", val error: UiText? = null) : WebLoginUiState {
        val canSubmit: Boolean get() = code.isNotBlank()
    }

    data class Checking(val code: String) : WebLoginUiState

    /** The browser behind the code, waiting for «Войти». */
    data class Confirm(
        val code: String,
        val preview: WebLoginPreview,
        val browser: UiText,
        val requestedAt: UiText,
        val approving: Boolean = false,
    ) : WebLoginUiState

    data object Done : WebLoginUiState

    /** [code] is what a retry checks again; empty when the code itself is gone. */
    data class Error(val text: UiText, val code: String) : WebLoginUiState
}

/** Approves a browser's sign-in to the web version: code from the QR or typed, a look at the browser, then «Войти». */
@HiltViewModel
class WebLoginViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val repository: WebLoginRepository,
    @param:AcademicClock private val clock: Clock,
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
            _uiState.value = current.copy(error = UiText.Resource(R.string.web_login_qr_unknown))
            return
        }
        check(code)
    }

    /** The Play services scanner could not start, for example while its module is still downloading. */
    fun onScannerUnavailable() {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        _uiState.value = current.copy(error = UiText.Resource(R.string.web_login_scanner_unavailable))
    }

    fun submit() {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        val code = WebLoginCode.parse(current.code)
        if (code == null) {
            _uiState.value = current.copy(error = UiText.Resource(R.string.web_login_code_invalid))
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
                    WebLoginUiState.Input(code, UiText.Resource(R.string.web_login_not_found))
                } else failure(result.error, code)
            }
        }
    }

    private fun confirm(code: String, preview: WebLoginPreview): WebLoginUiState.Confirm {
        val time = preview.createdAt.atZoneSameInstant(clock.zone).format(TIME)
        return WebLoginUiState.Confirm(code, preview, browserText(preview.userAgent),
            UiText.Resource(R.string.web_login_requested_at, listOf(time)))
    }

    /** A used or expired sign-in cannot be retried with the same code. */
    private fun failure(error: AppError, code: String): WebLoginUiState.Error = when (error) {
        AppError.NotFound -> WebLoginUiState.Error(UiText.Resource(R.string.web_login_not_found), "")
        AppError.CustomServicesDisabled -> WebLoginUiState.Error(UiText.Resource(R.string.web_login_services_disabled), "")
        else -> WebLoginUiState.Error(error.toUiText(), code)
    }

    private companion object {
        const val KEY_CODE = "web_login_code"
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** «Chrome на macOS», «Chrome», «Браузер на Android» or just «Браузер». */
internal fun browserText(userAgent: String?): UiText {
    val description = describeUserAgent(userAgent)
    val browser = UiText.Resource(description.browser?.nameRes() ?: R.string.web_login_browser_unknown)
    val platform = description.platform ?: return browser
    return UiText.Resource(R.string.web_login_browser_on_platform, listOf(browser, UiText.Resource(platform.nameRes())))
}

private fun Browser.nameRes(): Int = when (this) {
    Browser.YANDEX -> R.string.web_login_browser_yandex
    Browser.EDGE -> R.string.web_login_browser_edge
    Browser.OPERA -> R.string.web_login_browser_opera
    Browser.SAMSUNG -> R.string.web_login_browser_samsung
    Browser.FIREFOX -> R.string.web_login_browser_firefox
    Browser.CHROME -> R.string.web_login_browser_chrome
    Browser.SAFARI -> R.string.web_login_browser_safari
}

private fun Platform.nameRes(): Int = when (this) {
    Platform.WINDOWS -> R.string.web_login_platform_windows
    Platform.MACOS -> R.string.web_login_platform_macos
    Platform.LINUX -> R.string.web_login_platform_linux
    Platform.CHROME_OS -> R.string.web_login_platform_chrome_os
    Platform.ANDROID -> R.string.web_login_platform_android
    Platform.IOS -> R.string.web_login_platform_ios
    Platform.IPADOS -> R.string.web_login_platform_ipados
}
