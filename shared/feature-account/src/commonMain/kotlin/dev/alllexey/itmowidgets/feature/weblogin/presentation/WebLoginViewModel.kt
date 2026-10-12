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
import dev.alllexey.itmowidgets.core.weblogin.PairLink
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.Browser
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingError
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingHandoff
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingHandoffResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.Platform
import dev.alllexey.itmowidgets.feature.weblogin.domain.WebLoginCode
import dev.alllexey.itmowidgets.feature.weblogin.domain.describeUserAgent
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * «Подключить устройство»: a browser's sign-in to the web version (a code typed or from its QR) or a device's pairing
 * link (scanned, pasted or from an App Link), a look at what asked, then the approval. A device that receives the
 * ITMO session asks for the screen lock, takes the phone's previous session and gets it sealed to the link's key; its
 * key and secret come only from the link, so a typed code of such a device is refused. The link is kept in memory and
 * never written to the saved state.
 */
class WebLoginViewModel(
    private val handle: SavedStateHandle,
    private val repository: WebLoginRepository,
    private val pairing: PairingRepository,
    private val handoff: PairingHandoff,
    private val time: AcademicTimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WebLoginUiState>(WebLoginUiState.Input(handle[KEY_CODE] ?: ""))
    val uiState: StateFlow<WebLoginUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    /** What the shown card approves. */
    private var opened: Opened? = null

    fun onCodeChanged(code: String) {
        if (_uiState.value !is WebLoginUiState.Input) return
        handle[KEY_CODE] = code
        _uiState.value = WebLoginUiState.Input(code)
    }

    /** A QR that is neither a sign-in nor a pairing link leaves the typed code alone. */
    fun onScanned(raw: String) {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        PairLink.parse(raw)?.let { return openLink(it) }
        val code = WebLoginCode.parse(raw)
        if (code == null) {
            _uiState.value = current.copy(error = UiText.Res(AccountStrings.web_login_qr_unknown))
            return
        }
        check(code)
    }

    /** The Play services scanner could not start, for example while its module is still downloading. */
    fun onScannerUnavailable() {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        _uiState.value = current.copy(error = UiText.Res(AccountStrings.web_login_scanner_unavailable))
    }

    fun submit() {
        val current = _uiState.value as? WebLoginUiState.Input ?: return
        PairLink.parse(current.code)?.let { return openLink(it) }
        val code = WebLoginCode.parse(current.code)
        if (code == null) {
            _uiState.value = current.copy(error = UiText.Res(AccountStrings.web_login_code_invalid))
            return
        }
        check(code)
    }

    /** A new agent's permission switch; at least one must stay on to connect. */
    fun onScopeChanged(scope: AgentScope, checked: Boolean) {
        val current = _uiState.value as? WebLoginUiState.Confirm ?: return
        if (!current.scopesEditable || current.approving) return
        _uiState.value = current.copy(
            scopes = current.scopes.map { if (it.scope == scope) it.copy(checked = checked) else it },
        )
    }

    /** «Войти» or «Подключить»; [unlock] is the screen lock, asked only for a device that takes the session. */
    fun approve(unlock: DeviceUnlock) {
        val current = _uiState.value as? WebLoginUiState.Confirm ?: return
        if (!current.canApprove) return
        when (val target = opened ?: return) {
            is Opened.Web -> approveWeb(current, target.challengeId)
            is Opened.Device -> pair(current, target, unlock)
        }
    }

    /** Back from the card or an error to the code field, keeping what can be retried; a device hears «no». */
    fun editCode() {
        val code = when (val current = _uiState.value) {
            // An approval in flight may already have signed the browser in.
            is WebLoginUiState.Confirm -> if (current.approving) return else current.code
            is WebLoginUiState.Error -> current.code.takeIf { PairLink.parse(it) == null }.orEmpty()
            else -> return
        }
        job?.cancel()
        (opened as? Opened.Device)?.let { device ->
            if (_uiState.value is WebLoginUiState.Confirm) {
                viewModelScope.launch { pairing.reject(device.request.challengeId) }
            }
        }
        opened = null
        onInput(code)
    }

    /** An error with a code or link checks it again; one without goes back to an empty field. */
    fun retry() {
        val current = _uiState.value as? WebLoginUiState.Error ?: return
        when {
            current.code.isEmpty() -> onInput("")
            else -> PairLink.parse(current.code)?.let(::openLink) ?: check(current.code)
        }
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
                is AppResult.Success -> {
                    val preview = result.value
                    opened = Opened.Web(preview.challengeId)
                    confirm(code, PairingKind.WEB_SESSION, preview.createdAt, userAgent = preview.userAgent)
                }
                // A mistyped code is fixed in place rather than on a separate error screen.
                is AppResult.Failure -> if (result.error == AppError.NotFound) {
                    WebLoginUiState.Input(code, UiText.Res(typedPairingError(code)))
                } else failure(result.error, code)
            }
        }
    }

    /** Old routes do not see a device's code: a typed one of a device that takes the session is named as such. */
    private suspend fun typedPairingError(code: String) = when (val result = pairing.open(code, link = null)) {
        is PairingResult.Failure -> if (result.error == PairingError.KeyRequired) {
            AccountStrings.pair_key_required
        } else AccountStrings.web_login_not_found
        is PairingResult.Success -> AccountStrings.web_login_not_found
    }

    private fun openLink(link: PairLink) {
        job?.cancel()
        handle[KEY_CODE] = ""
        _uiState.value = WebLoginUiState.Checking(link.code)
        job = viewModelScope.launch {
            _uiState.value = when (val result = pairing.open(link.code, link)) {
                is PairingResult.Success -> {
                    val request = result.value
                    opened = Opened.Device(request, link)
                    confirm(
                        code = "",
                        kind = request.kind,
                        createdAt = request.createdAt,
                        name = request.name,
                        version = request.version,
                        scopes = request.scopes,
                        verification = verificationCode(request.challengeId, link),
                    )
                }
                is PairingResult.Failure -> pairingFailure(result.error, link.toUrl())
            }
        }
    }

    private fun approveWeb(current: WebLoginUiState.Confirm, challengeId: Uuid) {
        _uiState.value = current.copy(approving = true)
        job = viewModelScope.launch {
            _uiState.value = when (val result = repository.approve(challengeId)) {
                is AppResult.Success -> done(UiText.Res(AccountStrings.web_login_done))
                is AppResult.Failure -> failure(result.error, current.code)
            }
        }
    }

    private fun pair(current: WebLoginUiState.Confirm, device: Opened.Device, unlock: DeviceUnlock) {
        val retry = device.link.toUrl()
        val scopes = current.scopes.filter { it.checked }.map { it.scope }.toSet()
        _uiState.value = WebLoginUiState.Unlocking
        job = viewModelScope.launch {
            val sealed = handoff.seal(unlock, device.link, device.request.challengeId, device.request.kind) {
                _uiState.value = WebLoginUiState.Rotating
            }
            _uiState.value = when (sealed) {
                is PairingHandoffResult.Locked -> if (sealed.unlock == DeviceUnlockResult.NOT_SET_UP) {
                    WebLoginUiState.Error(UiText.Res(AccountStrings.pair_lock_required), retry)
                } else current
                is PairingHandoffResult.Failed -> if (sealed.error == AppError.Unauthorized) {
                    WebLoginUiState.Error(UiText.Res(AccountStrings.pair_sign_in_again), "")
                } else WebLoginUiState.Error(sealed.error.toUiText(), retry)
                is PairingHandoffResult.Sealed -> {
                    _uiState.value = WebLoginUiState.Sending
                    when (val result = pairing.approve(device.request, sealed.sealedBundle, scopes)) {
                        is PairingResult.Success -> done(UiText.Res(AccountStrings.pair_done))
                        is PairingResult.Failure -> pairingFailure(result.error, retry)
                    }
                }
            }
        }
    }

    private fun done(text: UiText): WebLoginUiState {
        handle.remove<String>(KEY_CODE)
        opened = null
        return WebLoginUiState.Done(text)
    }

    private fun confirm(
        code: String,
        kind: PairingKind,
        createdAt: Instant,
        name: String? = null,
        version: String? = null,
        userAgent: String? = null,
        scopes: Set<AgentScope> = emptySet(),
        verification: String? = null,
    ): WebLoginUiState.Confirm {
        val web = kind == PairingKind.WEB_SESSION
        // The academic zone like every time the app shows; a debug date override does not move a real request.
        val requestedAt = createdAt.toLocalDateTime(time.timeZone).time.format(DateTexts.TIME)
        return WebLoginUiState.Confirm(
            code = code,
            kind = kind,
            title = UiText.Res(
                when (kind) {
                    PairingKind.AGENT -> AccountStrings.pair_title_agent
                    PairingKind.AGENT_SESSION -> AccountStrings.pair_title_agent_session
                    else -> AccountStrings.pair_title_web
                },
            ),
            name = name?.let { UiText.Res(AccountStrings.pair_device_name, listOf(it)) },
            client = if (web) {
                browserText(userAgent)
            } else {
                version?.let { UiText.Res(AccountStrings.pair_version, listOf(it)) }
            },
            requestedAt = UiText.Res(AccountStrings.web_login_requested_at, listOf(requestedAt)),
            access = UiText.Res(if (web) AccountStrings.pair_access_web else AccountStrings.pair_access_itmo),
            scopes = when (kind) {
                PairingKind.AGENT -> AgentScope.entries.map { scopeChoice(it) }
                else -> AgentScope.entries.filter { it in scopes }.map { scopeChoice(it) }
            },
            scopesEditable = kind == PairingKind.AGENT,
            sharedSession = !web,
            verification = verification?.let { UiText.Res(AccountStrings.pair_verification, listOf(it)) },
            warning = UiText.Res(if (web) AccountStrings.web_login_own_only else AccountStrings.pair_own_only),
            approve = UiText.Res(if (web) AccountStrings.web_login_approve else AccountStrings.pair_approve),
        )
    }

    /** A used or expired sign-in cannot be retried with the same code. */
    private fun failure(error: AppError, code: String): WebLoginUiState.Error = when (error) {
        AppError.NotFound -> WebLoginUiState.Error(UiText.Res(AccountStrings.web_login_not_found), "")
        AppError.CustomServicesDisabled -> WebLoginUiState.Error(UiText.Res(AccountStrings.web_login_services_disabled), "")
        else -> WebLoginUiState.Error(error.toUiText(), code)
    }

    /** Only a failure Backend may answer differently next time keeps the link for a retry. */
    private fun pairingFailure(error: PairingError, link: String): WebLoginUiState.Error = when (error) {
        is PairingError.Failed -> if (error.error == AppError.Forbidden) {
            WebLoginUiState.Error(UiText.Res(AccountStrings.pair_agent_unavailable), "")
        } else failure(error.error, link)
        PairingError.LinkMismatch -> WebLoginUiState.Error(UiText.Res(AccountStrings.pair_link_mismatch), "")
        PairingError.KeyRequired -> WebLoginUiState.Error(UiText.Res(AccountStrings.pair_key_required), "")
        PairingError.Unsupported -> WebLoginUiState.Error(UiText.Res(AccountStrings.pair_unsupported), "")
        PairingError.TooManyDevices -> WebLoginUiState.Error(UiText.Res(AccountStrings.pair_too_many), "")
    }

    private sealed interface Opened {
        class Web(val challengeId: Uuid) : Opened

        class Device(val request: PairingRequest, val link: PairLink) : Opened
    }

    private companion object {
        const val KEY_CODE = "web_login_code"
    }
}

/** The six digits both screens show, as «482 913». */
internal fun verificationCode(challengeId: Uuid, link: PairLink): String =
    PairingCrypto.verificationCode(challengeId, link.publicKey, link.secret).chunked(3).joinToString(" ")

internal fun scopeChoice(scope: AgentScope) = ScopeChoice(
    scope = scope,
    title = UiText.Res(
        when (scope) {
            AgentScope.SPORT -> AccountStrings.pair_scope_sport
            AgentScope.FRIENDS_READ -> AccountStrings.pair_scope_friends
            AgentScope.SCHEDULE_WRITE -> AccountStrings.pair_scope_schedule
        },
    ),
    checked = true,
)

/** «Chrome на macOS», «Chrome», «Браузер на Android» or just «Браузер». */
internal fun browserText(userAgent: String?): UiText {
    val description = describeUserAgent(userAgent)
    val browser = description.browser?.text() ?: UiText.Res(AccountStrings.web_login_browser_unknown)
    val platform = description.platform ?: return browser
    return UiText.Res(AccountStrings.web_login_browser_on_platform, listOf(browser, platform.text()))
}

private fun Browser.text(): UiText = UiText.Res(
    when (this) {
        Browser.YANDEX -> AccountStrings.web_login_browser_yandex
        Browser.EDGE -> AccountStrings.web_login_browser_edge
        Browser.OPERA -> AccountStrings.web_login_browser_opera
        Browser.SAMSUNG -> AccountStrings.web_login_browser_samsung
        Browser.FIREFOX -> AccountStrings.web_login_browser_firefox
        Browser.CHROME -> AccountStrings.web_login_browser_chrome
        Browser.SAFARI -> AccountStrings.web_login_browser_safari
    }
)

private fun Platform.text(): UiText = UiText.Res(
    when (this) {
        Platform.WINDOWS -> AccountStrings.web_login_platform_windows
        Platform.MACOS -> AccountStrings.web_login_platform_macos
        Platform.LINUX -> AccountStrings.web_login_platform_linux
        Platform.CHROME_OS -> AccountStrings.web_login_platform_chrome_os
        Platform.ANDROID -> AccountStrings.web_login_platform_android
        Platform.IOS -> AccountStrings.web_login_platform_ios
        Platform.IPADOS -> AccountStrings.web_login_platform_ipados
    }
)
