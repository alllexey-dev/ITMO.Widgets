package dev.alllexey.itmowidgets.feature.connections.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.connections.PairLink
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.BundleItem
import dev.alllexey.itmowidgets.feature.connections.domain.BundleItmo
import dev.alllexey.itmowidgets.feature.connections.domain.BundleSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientItmoSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignIn
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInAttempt
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.Grant
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoGrants
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoSessionRef
import dev.alllexey.itmowidgets.feature.connections.domain.PairingApproval
import dev.alllexey.itmowidgets.feature.connections.domain.PairingBundle
import dev.alllexey.itmowidgets.feature.connections.domain.PairingBundleV2
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
 * «Подключить устройство»: a client's link (scanned, pasted or an App Link), its card, the screen lock, then for an
 * agent or MCP server the official ITMO.ID page in an empty browser store: the new session is sealed to the link's key
 * and approved with its `sid`; a renewal ends the old session after. The web is approved without a sign-in. The link
 * and the client's tokens stay in memory only, never in the saved state.
 *
 * A protocol-2 request (ADR 0040) lists grants: the card shows them as switches, required ones on and locked,
 * `itmo:sso` off every time and confirmed when turned on, an ITMO grant this version cannot mint off and locked. The
 * sign-in runs only when an ITMO grant is chosen, and the v2 bundle seals exactly the chosen ITMO items: with only
 * `itmo:sso` the refresh token is dropped unused (never logged out: that would end the session). A renewal shows a
 * card only for requested ITMO grants the connection does not hold yet.
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

    /** Protocol 2: the switch of every grant on the card; grants without one follow [included]. */
    private var choice: Map<String, Boolean> = emptyMap()

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

    /** A grant switch; turning `itmo:sso` on asks first ([confirmGrant]). Locked grants do not change. */
    fun onGrantChanged(grantId: String, checked: Boolean) {
        val current = _uiState.value as? PairingUiState.Confirm ?: return
        val opened = request ?: return
        if (current.busy || current.ask != null || grantId !in choice) return
        val grant = opened.grants.firstOrNull { it.id == grantId } ?: return
        if (grant.required || !grant.mintable) return
        if (grantId == ItmoGrants.SSO && checked) {
            _uiState.value = current.copy(
                ask = ConfirmAsk(
                    title = UiText.Res(AccountStrings.grant_sso_confirm_title),
                    text = UiText.Res(AccountStrings.grant_sso_confirm_text, listOf(opened.name)),
                    confirm = UiText.Res(AccountStrings.grant_sso_confirm_action),
                ),
            )
            return
        }
        choice = choice + (grantId to checked)
        _uiState.value = withGrants(current, opened)
    }

    /** «Дать доступ» in the `itmo:sso` confirmation. */
    fun confirmGrant() {
        val current = _uiState.value as? PairingUiState.Confirm ?: return
        val opened = request ?: return
        if (current.ask == null) return
        choice = choice + (ItmoGrants.SSO to true)
        _uiState.value = withGrants(current.copy(ask = null), opened)
    }

    /** «Отмена» in the `itmo:sso` confirmation: the switch stays off. */
    fun dismissGrant() {
        val current = _uiState.value as? PairingUiState.Confirm ?: return
        _uiState.value = current.copy(ask = null)
    }

    /** «Продолжить» or «Подключить», and a renewal at once: the screen lock, then the sign-in or the approval. */
    fun proceed(unlock: DeviceUnlock) {
        val current = _uiState.value as? PairingUiState.Confirm ?: return
        val opened = request ?: return
        if (!current.canProceed || current.ask != null) return
        _uiState.value = current.copy(busy = true)
        job = viewModelScope.launch {
            when (unlock.unlock()) {
                DeviceUnlockResult.UNLOCKED -> if (signsIn(opened)) beginSignIn() else approveWithoutSession(opened)
                DeviceUnlockResult.NOT_SET_UP -> _uiState.value = failure(UiText.Res(AccountStrings.pair_lock_required))
                DeviceUnlockResult.CANCELLED -> _uiState.value =
                    if (current.renewal) failure(UiText.Res(AccountStrings.pair_cancelled)) else current.copy(busy = false)
            }
        }
    }

    /**
     * Every main-frame navigation of the sign-in page; true for the callback, which the host must not load: it reads
     * `KEYCLOAK_IDENTITY` from the isolated store when [PairingUiState.SigningIn.readIdentity], before the store goes,
     * and passes the URL to [onSignInCallback].
     */
    fun isSignInCallback(url: String): Boolean {
        val signing = attempt ?: return false
        return _uiState.value is PairingUiState.SigningIn && signing.isCallback(url)
    }

    /**
     * The callback the host caught, with the isolated store's `KEYCLOAK_IDENTITY` (null when not asked or absent): the
     * code is exchanged here. The cookie is held in memory only until it is sealed.
     */
    fun onSignInCallback(url: String, identityCookie: String?) {
        val signing = attempt ?: return
        if (!isSignInCallback(url)) return
        attempt = null
        _uiState.value = PairingUiState.Sending
        val cookie = identityCookie.takeIf { signing.withIdentity }
        job = viewModelScope.launch { finish(signing.finish(url, cookie)) }
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
        choice = emptyMap()
        _uiState.value = PairingUiState.Input()
    }

    private fun open(parsed: PairLink) {
        job?.cancel()
        link = parsed
        _uiState.value = PairingUiState.Opening
        job = viewModelScope.launch {
            _uiState.value = when (val opened = pairing.open(parsed)) {
                is PairingResult.Failure -> pairingFailure(opened.error)
                is PairingResult.Success -> confirm(opened.value)
            }
        }
    }

    private fun confirm(opened: PairingRequest): PairingUiState.Confirm {
        request = opened
        if (opened.protocol >= 2) return confirmGrants(opened)
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
            caution = connect,
            scopes = chosen,
            note = if (web) UiText.Res(AccountStrings.pair_note_web) else {
                UiText.Res(AccountStrings.pair_note_client, listOf(opened.name, opened.name))
            },
            primary = UiText.Res(if (web) AccountStrings.pair_connect else AccountStrings.pair_continue),
            renewal = !connect && !web,
        )
    }

    /** A protocol-2 card: the client, the caution on a new connection and the grant switches. */
    private fun confirmGrants(opened: PairingRequest): PairingUiState.Confirm {
        chosen = emptyList()
        val web = opened.client == ClientType.WEB
        val connect = opened.kind == PairingKind.CONNECT
        val shown = shownGrants(opened)
        choice = shown.associate { it.id to it.initiallyOn }
        val confirm = PairingUiState.Confirm(
            icon = opened.client.icon(),
            name = UiText.Dynamic(opened.name),
            subtitle = when {
                web -> UiText.Res(AccountStrings.pair_web_subtitle)
                opened.client == ClientType.OTHER && opened.appName != null -> UiText.Dynamic(opened.appName)
                else -> clientSubtitle(opened.client, opened.version, opened.platform)
            },
            caution = connect,
            scopes = emptyList(),
            note = UiText.Dynamic(""),
            primary = UiText.Dynamic(""),
            renewal = !connect && !web && shown.isEmpty(),
        )
        return withGrants(confirm, opened)
    }

    /** [current] with the switches, the note, the button and [PairingUiState.Confirm.ready] of the present choice. */
    private fun withGrants(current: PairingUiState.Confirm, opened: PairingRequest): PairingUiState.Confirm {
        val shown = shownGrants(opened)
        val granted = granted(opened)
        val signs = granted.any(ItmoGrants::isItmo)
        val web = opened.client == ClientType.WEB
        val groups = listOf(
            AccountStrings.pair_group_widgets to shown.filterNot { it.itmo },
            AccountStrings.pair_group_itmo to shown.filter { it.itmo },
        ).filter { it.second.isNotEmpty() }.map { (title, grants) ->
            GrantGroup(UiText.Res(title), grants.map { it.toSwitch(choice[it.id] == true) })
        }
        val ready = when (opened.kind) {
            PairingKind.CONNECT -> opened.grants.none { !it.itmo } || granted.any { !ItmoGrants.isItmo(it) }
            PairingKind.RENEW -> granted.isNotEmpty()
        }
        return current.copy(
            grants = groups,
            note = when {
                signs -> UiText.Res(
                    if (ItmoGrants.SSO in granted) AccountStrings.pair_note_client_full else AccountStrings.pair_note_client,
                    listOf(opened.name, opened.name),
                )
                web -> UiText.Res(AccountStrings.pair_note_web)
                else -> UiText.Res(AccountStrings.pair_note_widgets, listOf(opened.name))
            },
            primary = UiText.Res(if (signs) AccountStrings.pair_continue else AccountStrings.pair_connect),
            ready = ready,
        )
    }

    /** The grants on the card: all of a new connection's; on a renewal only the ITMO grants not held yet. */
    private fun shownGrants(opened: PairingRequest): List<Grant> = when (opened.kind) {
        PairingKind.CONNECT -> opened.grants
        PairingKind.RENEW -> opened.grants.filter { it.itmo && it.held == false }
    }

    /**
     * What the approval sends, in request order: every required grant, the switches that are on and, on a renewal,
     * the ITMO grants the connection already holds; never an ITMO grant this version cannot mint.
     */
    private fun granted(opened: PairingRequest): List<String> = opened.grants.filter { grant ->
        grant.mintable && when {
            grant.id in choice -> choice[grant.id] == true
            grant.required -> true
            else -> opened.kind == PairingKind.RENEW && grant.itmo && grant.held == true
        }
    }.map { it.id }

    /** Protocol 1 signs in for every agent and MCP server; protocol 2 only when an ITMO grant is chosen. */
    private fun signsIn(opened: PairingRequest): Boolean =
        if (opened.protocol >= 2) granted(opened).any(ItmoGrants::isItmo) else opened.client != ClientType.WEB

    private val Grant.mintable: Boolean get() = !itmo || ItmoGrants.isKnown(id)

    private val Grant.initiallyOn: Boolean get() = mintable && (required || id != ItmoGrants.SSO)

    private fun Grant.toSwitch(checked: Boolean): GrantSwitch {
        val (title, description) = when (id) {
            ItmoGrants.REFRESH -> UiText.Res(AccountStrings.grant_refresh_title) to UiText.Res(AccountStrings.grant_refresh_text)
            ItmoGrants.SSO -> UiText.Res(AccountStrings.grant_sso_title) to UiText.Res(AccountStrings.grant_sso_text)
            else -> if (itmo) {
                // An ITMO grant this version does not know: its id means nothing to a student.
                UiText.Res(AccountStrings.grant_other_title) to null
            } else {
                UiText.Dynamic(title?.ifEmpty { null } ?: id) to description?.ifEmpty { null }?.let(UiText::Dynamic)
            }
        }
        return GrantSwitch(
            id = id,
            title = title,
            description = description,
            purpose = purpose?.ifEmpty { null }?.let { UiText.Res(AccountStrings.grant_purpose, listOf(it)) },
            warning = UiText.Res(AccountStrings.grant_sso_warning).takeIf { id == ItmoGrants.SSO },
            note = when {
                !mintable -> UiText.Res(AccountStrings.grant_unavailable)
                required -> UiText.Res(AccountStrings.grant_required)
                else -> null
            },
            checked = checked,
            enabled = mintable && !required,
        )
    }

    private suspend fun beginSignIn() {
        val opened = request ?: return
        val withIdentity = opened.protocol >= 2 && ItmoGrants.SSO in granted(opened)
        val started = signIn.begin(opened, withIdentity)
        attempt = started
        _uiState.value = PairingUiState.SigningIn(
            url = started.url,
            userAgent = started.userAgent,
            title = UiText.Res(AccountStrings.pair_sign_in_title, listOf(opened.name)),
            readIdentity = started.withIdentity,
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
            ClientSignInResult.IdentityMissing -> failure(
                UiText.Res(AccountStrings.pair_identity_missing),
                ResultAction.SIGN_IN_AGAIN,
            )
            is ClientSignInResult.Failed -> failure(result.error.toUiText(), ResultAction.SIGN_IN_AGAIN)
            is ClientSignInResult.Success -> approveClient(opened, parsed, result.session)
        }
    }

    private suspend fun approveClient(opened: PairingRequest, parsed: PairLink, session: ClientItmoSession): PairingUiState {
        val granted = if (opened.protocol >= 2) granted(opened) else null
        val sealed = try {
            if (granted == null) sealer.sealBundle(parsed, bundleV1(opened, session)) else {
                sealer.sealBundle(parsed, bundleV2(opened, session, granted))
            }
        } catch (error: Exception) {
            logins.end(listOf(session.sid))
            return failure(AppError.Unknown(error).toUiText())
        }
        val previous = opened.connectionId?.let { id -> devices.devices.value?.connections?.firstOrNull { it.id == id } }
        val approval = PairingApproval(
            sealedBundle = sealed,
            scopes = chosen.takeIf { granted == null && opened.kind == PairingKind.CONNECT }
                ?.filter { it.checked }?.mapNotNull { it.id },
            session = ItmoSessionRef(session.sid, session.expiresAt),
            granted = granted,
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

    private fun bundleV1(opened: PairingRequest, session: ClientItmoSession) = PairingBundle(
        challengeId = opened.challengeId,
        kind = opened.kind,
        isu = session.isu,
        issuedAt = time.now(),
        itmo = BundleSession(session.sid, session.expiresAt, session.refreshToken, session.refreshExpiresAt),
    )

    /** Exactly the granted ITMO items, in request order; a grant without its credential cannot be sealed. */
    private fun bundleV2(opened: PairingRequest, session: ClientItmoSession, granted: List<String>): PairingBundleV2 {
        val items = granted.filter(ItmoGrants::isItmo).map { grant ->
            when (grant) {
                ItmoGrants.REFRESH -> BundleItem(grant, session.refreshToken, session.refreshExpiresAt)
                ItmoGrants.SSO -> checkNotNull(session.identity) { "No identity to seal" }
                    .let { BundleItem(grant, it.value, it.expiresAt) }
                else -> error("Cannot seal $grant")
            }
        }
        return PairingBundleV2(
            challengeId = opened.challengeId,
            kind = opened.kind,
            clientId = checkNotNull(opened.clientId) { "No client id" },
            isu = session.isu,
            issuedAt = time.now(),
            itmo = BundleItmo(session.sid, session.expiresAt, items),
        )
    }

    /** The web, or a protocol-2 client without ITMO grants: no sign-in and no bundle. */
    private suspend fun approveWithoutSession(opened: PairingRequest) {
        _uiState.value = PairingUiState.Sending
        val approval = PairingApproval(
            sealedBundle = null,
            scopes = null,
            session = null,
            granted = if (opened.protocol >= 2) granted(opened) else null,
        )
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
        val text = when {
            session != null -> UiText.Res(AccountStrings.pair_done_text, listOf(dayMonth(session.expiresAt, time.timeZone)))
            opened.client == ClientType.WEB -> UiText.Res(AccountStrings.pair_done_web_text)
            else -> UiText.Res(AccountStrings.pair_done_widgets_text)
        }
        return PairingUiState.Result(true, title, text, UiText.Res(AccountStrings.pair_done_action), ResultAction.CLOSE)
    }

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
