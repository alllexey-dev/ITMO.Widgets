package dev.alllexey.itmowidgets.feature.connections.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.connections.PairLink
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.BundleSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientItmoSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignIn
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInAttempt
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoSessionRef
import dev.alllexey.itmowidgets.feature.connections.domain.PairingApproval
import dev.alllexey.itmowidgets.feature.connections.domain.PairingBundle
import dev.alllexey.itmowidgets.feature.connections.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.connections.domain.PairingError
import dev.alllexey.itmowidgets.feature.connections.domain.PairingKind
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.connections.domain.PairingResult
import dev.alllexey.itmowidgets.feature.connections.domain.PairingSealer
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * «Подключить устройство»: a client's link (scanned, pasted or an App Link), its card with the code to compare, the
 * screen lock, then for an agent or MCP server the official ITMO.ID page in an empty browser store: the new session
 * is sealed to the link's key and approved with its `sid`; a renewal ends the old session after. The web is approved
 * without a sign-in. The link and the client's tokens stay in memory only, never in the saved state.
 */
class PairingViewModel(
    private val handle: SavedStateHandle,
    private val pairing: PairingRepository,
    private val signIn: ClientSignIn,
    private val sealer: PairingSealer,
    private val devices: DevicesRepository,
    private val logins: ItmoLoginsRepository,
    private val time: AcademicTimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Input(handle[KEY_TEXT] ?: ""))
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    private var link: PairLink? = null
    private var request: PairingRequest? = null
    private var chosen: List<ScopeSwitch> = emptyList()
    private var attempt: ClientSignInAttempt? = null

    fun onTextChanged(text: String) {
        if (_uiState.value !is PairingUiState.Input) return
        handle[KEY_TEXT] = text
        _uiState.value = PairingUiState.Input(text)
    }

    fun submit() {
        val current = _uiState.value as? PairingUiState.Input ?: return
        onScanned(current.text)
    }

    /** A QR or text that is no pairing link leaves the field with a hint. */
    fun onScanned(raw: String) {
        val current = _uiState.value as? PairingUiState.Input ?: return
        val parsed = PairLink.parse(raw)
        if (parsed == null) {
            _uiState.value = current.copy(error = UiText.Res(AccountStrings.pair_link_unknown))
            return
        }
        handle[KEY_TEXT] = ""
        open(parsed)
    }

    /** The scanner could not start, for example while its module is still downloading. */
    fun onScannerUnavailable() {
        val current = _uiState.value as? PairingUiState.Input ?: return
        _uiState.value = current.copy(error = UiText.Res(AccountStrings.pair_scanner_unavailable))
    }

    fun onScopeChanged(scopeId: String, checked: Boolean) {
        val current = _uiState.value as? PairingUiState.Confirm ?: return
        if (current.busy) return
        chosen = current.scopes.map { if (it.id == scopeId) it.copy(checked = checked) else it }
        _uiState.value = current.copy(scopes = chosen)
    }

    /** «Продолжить» or «Подключить», and a renewal at once: the screen lock, then the sign-in or the approval. */
    fun proceed(unlock: DeviceUnlock) {
        val current = _uiState.value as? PairingUiState.Confirm ?: return
        val opened = request ?: return
        if (!current.canProceed) return
        _uiState.value = current.copy(busy = true)
        job = viewModelScope.launch {
            when (unlock.unlock()) {
                DeviceUnlockResult.UNLOCKED -> if (opened.client == ClientType.WEB) approveWeb(opened) else beginSignIn()
                DeviceUnlockResult.NOT_SET_UP -> _uiState.value = failure(UiText.Res(AccountStrings.pair_lock_required))
                DeviceUnlockResult.CANCELLED -> _uiState.value =
                    if (current.renewal) failure(UiText.Res(AccountStrings.pair_cancelled)) else current.copy(busy = false)
            }
        }
    }

    /**
     * Every main-frame navigation of the sign-in page; true when it was the callback, which the host must not load:
     * the code is exchanged here.
     */
    fun onSignInNavigation(url: String): Boolean {
        val signing = attempt ?: return false
        if (_uiState.value !is PairingUiState.SigningIn || !signing.isCallback(url)) return false
        attempt = null
        _uiState.value = PairingUiState.Sending
        job = viewModelScope.launch { finish(signing.finish(url)) }
        return true
    }

    /** The sign-in page was closed: nothing was sent, the challenge runs out. */
    fun onSignInCancelled() {
        if (_uiState.value !is PairingUiState.SigningIn) return
        attempt = null
        _uiState.value = failure(UiText.Res(AccountStrings.pair_cancelled), ResultAction.SIGN_IN_AGAIN)
    }

    /** Android without WebView profiles: no fallback touches the app's own store (plan 5.4). */
    fun onSignInUnsupported() {
        attempt = null
        _uiState.value = failure(UiText.Res(AccountStrings.pair_webview_outdated))
    }

    /** The result's button other than closing. */
    fun onResultAction() {
        val current = _uiState.value as? PairingUiState.Result ?: return
        when (current.next) {
            ResultAction.SIGN_IN_AGAIN -> if (request != null && link != null) {
                job = viewModelScope.launch { beginSignIn() }
            }
            ResultAction.SCAN_AGAIN -> reset()
            ResultAction.CLOSE -> Unit
        }
    }

    private fun reset() {
        job?.cancel()
        link = null
        request = null
        attempt = null
        _uiState.value = PairingUiState.Input()
    }

    private fun open(parsed: PairLink) {
        job?.cancel()
        link = parsed
        _uiState.value = PairingUiState.Opening
        job = viewModelScope.launch {
            _uiState.value = when (val opened = pairing.open(parsed)) {
                is PairingResult.Failure -> pairingFailure(opened.error)
                is PairingResult.Success -> confirm(opened.value, parsed)
            }
        }
    }

    private fun confirm(opened: PairingRequest, parsed: PairLink): PairingUiState.Confirm {
        request = opened
        val web = opened.client == ClientType.WEB
        val connect = opened.kind == PairingKind.CONNECT
        chosen = if (connect && !web) {
            opened.scopes.map { ScopeSwitch(it.id, UiText.Dynamic(it.title), UiText.Dynamic(it.description), true) }
        } else {
            emptyList()
        }
        return PairingUiState.Confirm(
            icon = opened.client.icon(),
            name = UiText.Dynamic(opened.name),
            subtitle = if (web) UiText.Res(AccountStrings.pair_web_subtitle) else {
                clientSubtitle(opened.client, opened.version, opened.platform)
            },
            code = code(opened, parsed),
            scopes = chosen,
            note = if (web) UiText.Res(AccountStrings.pair_note_web) else {
                UiText.Res(AccountStrings.pair_note_client, listOf(opened.name, opened.name))
            },
            primary = UiText.Res(if (web) AccountStrings.pair_connect else AccountStrings.pair_continue),
            renewal = !connect && !web,
        )
    }

    private suspend fun beginSignIn() {
        val opened = request ?: return
        val parsed = link ?: return
        val started = signIn.begin(opened)
        attempt = started
        _uiState.value = PairingUiState.SigningIn(
            url = started.url,
            userAgent = started.userAgent,
            title = UiText.Res(AccountStrings.pair_sign_in_title, listOf(opened.name)),
            code = code(opened, parsed).takeIf { opened.kind == PairingKind.RENEW },
        )
    }

    private suspend fun finish(result: ClientSignInResult) {
        val opened = request ?: return
        val parsed = link ?: return
        _uiState.value = when (result) {
            ClientSignInResult.OtherAccount -> failure(
                UiText.Res(AccountStrings.pair_other_account_text),
                ResultAction.SIGN_IN_AGAIN,
                UiText.Res(AccountStrings.pair_other_account_title),
            )
            is ClientSignInResult.Failed -> failure(result.error.toUiText(), ResultAction.SIGN_IN_AGAIN)
            is ClientSignInResult.Success -> approveClient(opened, parsed, result.session)
        }
    }

    private suspend fun approveClient(opened: PairingRequest, parsed: PairLink, session: ClientItmoSession): PairingUiState {
        val bundle = PairingBundle(
            challengeId = opened.challengeId,
            kind = opened.kind,
            isu = session.isu,
            issuedAt = time.now(),
            itmo = BundleSession(session.sid, session.expiresAt, session.refreshToken, session.refreshExpiresAt),
        )
        val sealed = try {
            sealer.sealBundle(parsed, bundle)
        } catch (error: Exception) {
            return failure(AppError.Unknown(error).toUiText())
        }
        val previous = opened.connectionId?.let { id -> devices.devices.value?.connections?.firstOrNull { it.id == id } }
        val approval = PairingApproval(
            sealedBundle = sealed,
            scopes = chosen.takeIf { opened.kind == PairingKind.CONNECT }?.filter { it.checked }?.mapNotNull { it.id },
            session = ItmoSessionRef(session.sid, session.expiresAt),
        )
        return when (val approved = pairing.approve(opened, approval)) {
            is PairingResult.Failure -> {
                // The new session reached nobody; end it so it does not linger on id.itmo.ru.
                logins.end(listOf(session.sid))
                pairingFailure(approved.error)
            }
            is PairingResult.Success -> {
                previous?.itmo?.sid?.takeIf { it != session.sid }?.let { logins.end(listOf(it)) }
                devices.refresh()
                done(opened, session)
            }
        }
    }

    private suspend fun approveWeb(opened: PairingRequest) {
        _uiState.value = PairingUiState.Sending
        val approval = PairingApproval(sealedBundle = null, scopes = null, session = null)
        _uiState.value = when (val approved = pairing.approve(opened, approval)) {
            is PairingResult.Failure -> pairingFailure(approved.error)
            is PairingResult.Success -> {
                devices.refresh()
                done(opened, null)
            }
        }
    }

    private fun done(opened: PairingRequest, session: ClientItmoSession?): PairingUiState.Result {
        link = null
        val renew = opened.kind == PairingKind.RENEW
        val title = UiText.Res(
            when {
                session == null && renew -> AccountStrings.pair_done_web_renew
                renew -> AccountStrings.pair_done_renew
                else -> AccountStrings.pair_done
            },
            listOf(opened.name),
        )
        val text = session?.let {
            UiText.Res(AccountStrings.pair_done_text, listOf(dayMonth(it.expiresAt, time.timeZone)))
        } ?: UiText.Res(AccountStrings.pair_done_web_text)
        return PairingUiState.Result(true, title, text, UiText.Res(AccountStrings.pair_done_action), ResultAction.CLOSE)
    }

    private fun code(opened: PairingRequest, parsed: PairLink): String =
        PairingCrypto.verificationCode(opened.challengeId, parsed.publicKey, parsed.secret).chunked(3).joinToString(" ")

    private fun pairingFailure(error: PairingError): PairingUiState.Result = when (error) {
        PairingError.LinkMismatch -> failure(UiText.Res(AccountStrings.pair_link_mismatch), ResultAction.SCAN_AGAIN)
        PairingError.Unsupported -> failure(UiText.Res(AccountStrings.pair_unsupported))
        PairingError.TooManyDevices -> failure(UiText.Res(AccountStrings.pair_too_many))
        is PairingError.Failed -> when (error.error) {
            AppError.NotFound -> failure(
                UiText.Res(AccountStrings.pair_expired_text),
                ResultAction.SCAN_AGAIN,
                UiText.Res(AccountStrings.pair_expired_title),
            )
            AppError.Forbidden -> failure(UiText.Res(AccountStrings.pair_client_unavailable))
            AppError.CustomServicesDisabled -> failure(UiText.Res(AccountStrings.pair_services_disabled))
            else -> failure(error.error.toUiText(), ResultAction.SCAN_AGAIN)
        }
    }

    private fun failure(
        text: UiText,
        next: ResultAction = ResultAction.CLOSE,
        title: UiText = UiText.Res(AccountStrings.pair_failed_title),
    ) = PairingUiState.Result(
        success = false,
        title = title,
        text = text,
        action = UiText.Res(
            when (next) {
                ResultAction.CLOSE -> AccountStrings.pair_close
                ResultAction.SIGN_IN_AGAIN -> AccountStrings.pair_sign_in_again
                ResultAction.SCAN_AGAIN -> AccountStrings.pair_scan_again
            },
        ),
        next = next,
    )

    private companion object {
        const val KEY_TEXT = "pairing_text"
    }
}
