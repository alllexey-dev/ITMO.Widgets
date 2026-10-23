package dev.alllexey.itmowidgets.feature.connections.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.connections.SignInLabel
import dev.alllexey.itmowidgets.core.connections.ThisPhone
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.AppInstall
import dev.alllexey.itmowidgets.feature.connections.domain.Connection
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogin
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.shared.feature.account.AccountPlurals
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Устройства»: Backend's installs and clients joined by `sid` with the account's ITMO.ID sessions that the phone's
 * own cookie lists. The Backend part comes from the repository's last answer at once and refreshes with the ITMO.ID
 * list; either failing keeps the other. Revoked clients whose session is still open are ended here and reported
 * (plan 6.2). Nothing from ITMO.ID goes to Backend.
 */
class DevicesViewModel(
    private val devices: DevicesRepository,
    private val logins: ItmoLoginsRepository,
    private val gate: BackendGate,
    private val label: SignInLabel,
    private val phone: ThisPhone,
    private val time: AcademicTimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private var backend: Devices? = devices.devices.value
    private var backendError: AppError? = null
    private var itmo: AppResult<ItmoLogins>? = null
    private var pending: Pending? = null

    init {
        gate.observeConnected().onEach { connected ->
            _uiState.update { it.copy(backend = connected) }
            render()
        }.launchIn(viewModelScope)
        label.observeBrowserLike().onEach { browserLike -> _uiState.update { it.copy(browserLike = browserLike) } }
            .launchIn(viewModelScope)
        // A client connected, renewed or revoked elsewhere shows up at once, then both sources settle. This screen's own
        // loads assign the answer before the flow delivers it, so they never come back here.
        devices.devices.filterNotNull().onEach {
            if (it === backend) return@onEach
            backend = it
            render()
            settle()
        }.launchIn(viewModelScope)
        viewModelScope.launch { load() }
    }

    /** Both sources again; pull-to-refresh and the error's retry. */
    fun refresh() {
        if (_uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }
        viewModelScope.launch { load() }
    }

    private suspend fun load() = coroutineScope {
        val listed = async { logins.list() }
        val answered = if (gate.mayCallBackend()) devices.refresh() else null
        when (answered) {
            is AppResult.Success -> { backend = answered.value; backendError = null }
            is AppResult.Failure -> backendError = answered.error
            null -> Unit
        }
        itmo = listed.await()
        _uiState.update { it.copy(refreshing = false) }
        render()
        reconcile()
    }

    /** A row of an install or another session opens its facts; clients are the host's to open. */
    fun open(key: String) {
        val sheet = when {
            key.startsWith(INSTALL) -> installSheet(key)
            key.startsWith(SESSION) -> sessionSheet(key)
            else -> null
        }
        _uiState.update { it.copy(sheet = sheet) }
    }

    fun closeSheet() = _uiState.update { it.copy(sheet = null) }

    /** The sheet's button asks first. */
    fun askSheetAction() {
        val sheet = _uiState.value.sheet ?: return
        if (sheet.action == null) return
        when {
            sheet.key.startsWith(INSTALL) -> {
                val install = installOf(sheet.key) ?: return
                pending = Pending.SignOut(install)
                ask(
                    UiText.Res(AccountStrings.devices_sign_out_title, listOf(installTitle(install))),
                    UiText.Res(AccountStrings.devices_sign_out_text),
                    UiText.Res(AccountStrings.devices_sign_out_confirm),
                )
            }
            sheet.key.startsWith(SESSION) -> {
                val session = otherSessions().firstOrNull { SESSION + it.sid == sheet.key } ?: return
                pending = Pending.End(listOf(session.sid))
                ask(
                    UiText.Res(AccountStrings.devices_end_title),
                    UiText.Res(AccountStrings.devices_end_text),
                    UiText.Res(AccountStrings.devices_end_confirm),
                )
            }
        }
    }

    fun askEndAllOthers() {
        val sids = otherSessions().map { it.sid }
        if (sids.isEmpty()) return
        pending = Pending.End(sids)
        ask(
            UiText.Plural(AccountPlurals.devices_end_all_title, sids.size, listOf(sids.size)),
            UiText.Res(AccountStrings.devices_end_all_text),
            UiText.Res(AccountStrings.devices_end_confirm),
        )
    }

    fun dismissConfirm() {
        if (_uiState.value.busy) return
        pending = null
        _uiState.update { it.copy(confirm = null) }
    }

    fun confirm() {
        val action = pending ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = when (action) {
                is Pending.End -> logins.end(action.sids)
                is Pending.SignOut -> signOut(action.install)
            }
            pending = null
            val ok = result is AppResult.Success
            _uiState.update {
                it.copy(
                    busy = false,
                    confirm = null,
                    sheet = if (ok) null else it.sheet,
                    message = if (ok) UiText.Res(AccountStrings.devices_ended) else result.failureText(),
                )
            }
            render()
            if (ok) settle()
        }
    }

    /** ITMO.ID and Backend show a change a moment late: both sources now and once more after [SETTLE_DELAY]. */
    private suspend fun settle() {
        load()
        delay(SETTLE_DELAY)
        load()
    }

    fun setBrowserLike(browserLike: Boolean) {
        viewModelScope.launch { label.setBrowserLike(browserLike) }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** The install's ITMO session first (that app signs out at its next refresh), then its push on Backend. */
    private suspend fun signOut(install: AppInstall): AppResult<Unit> {
        install.sid?.let { sid ->
            val ended = logins.end(listOf(sid))
            if (ended is AppResult.Failure && ended.error != AppError.Unauthorized) return ended
        }
        return devices.signOutInstall(install.instanceId)
    }

    /** Ends the open sessions of revoked clients and tells Backend; a session ITMO.ID no longer lists is ended too. */
    private suspend fun reconcile() {
        val listed = (itmo as? AppResult.Success)?.value as? ItmoLogins.Listed ?: return
        val open = listed.sessions.map { it.sid }.toSet()
        backend?.connections.orEmpty().filter { it.revokedAt != null && it.itmo != null }.forEach { connection ->
            val sid = connection.itmo?.sid ?: return@forEach
            val ended = if (sid in open) logins.end(listOf(sid)) is AppResult.Success else true
            if (ended) devices.itmoEnded(connection.id)
        }
    }

    private fun render() {
        val now = time.now()
        val zone = time.timeZone
        val data = backend
        val listed = (itmo as? AppResult.Success)?.value as? ItmoLogins.Listed
        val sessions = listed?.sessions.orEmpty().associateBy { it.sid }
        val ownSid = listed?.ownSid
        val backendOn = _uiState.value.backend
        val installs = if (backendOn) data?.installs.orEmpty() else emptyList()
        val current = installs.firstOrNull { it.current }
        val phoneSession = (current?.sid ?: ownSid)?.let(sessions::get)
        _uiState.update { state ->
            state.copy(
                loading = backendOn && data == null && backendError == null,
                error = if (backendOn && data == null) backendError?.toUiText() else null,
                thisPhone = DeviceRow(
                    key = INSTALL + (current?.instanceId?.toString() ?: THIS_PHONE),
                    icon = DeviceIcon.PHONE,
                    title = UiText.Dynamic(phone.name),
                    subtitle = lines(
                        current?.let(::installLine)
                            ?: UiText.Res(AccountStrings.devices_install_app, listOf(phone.appVersion)),
                        phoneSession?.let { UiText.Res(AccountStrings.devices_sign_in_until_line, listOf(dayMonth(it.expires, zone))) },
                    ),
                ),
                installs = installs.filter { !it.current && it.isLive(sessions, listed != null) }.map { install ->
                    val ended = listed != null && install.sid != null && install.sid !in sessions
                    DeviceRow(
                        key = INSTALL + install.instanceId,
                        icon = DeviceIcon.PHONE,
                        title = installTitle(install),
                        subtitle = lines(
                            installLine(install),
                            if (ended) {
                                UiText.Res(AccountStrings.devices_sign_in_ended)
                            } else {
                                UiText.Res(AccountStrings.devices_activity_line, listOf(activityText(install.lastSeenAt, now, zone)))
                            },
                        ),
                    )
                },
                clients = if (backendOn) data?.connections.orEmpty().filter { it.revokedAt == null }.map { it.row(now) } else emptyList(),
                others = when (val result = itmo) {
                    null -> OtherLogins.Loading
                    is AppResult.Failure -> OtherLogins.Error(result.error.toUiText())
                    is AppResult.Success -> when (result.value) {
                        ItmoLogins.NoCookie -> OtherLogins.NoCookie
                        is ItmoLogins.Listed -> OtherLogins.Content(otherSessions().map { it.row(now) })
                    }
                },
            )
        }
    }

    private fun Connection.row(now: kotlin.time.Instant): DeviceRow {
        val activity = lastUsedAt?.let { activityText(it, now, time.timeZone) }
        return DeviceRow(
            key = CLIENT + id,
            icon = client.icon(),
            title = UiText.Dynamic(name),
            subtitle = activity?.let {
                UiText.Res(AccountStrings.devices_client_line, listOf(client.typeName(), it))
            } ?: client.typeName(),
            pill = signInPill(now, time.timeZone),
            connectionId = id.toString(),
        )
    }

    private fun ItmoLogin.row(now: kotlin.time.Instant) = DeviceRow(
        key = SESSION + sid,
        icon = if (mobile) DeviceIcon.PHONE else DeviceIcon.COMPUTER,
        title = UiText.Dynamic(label),
        subtitle = UiText.Res(
            AccountStrings.devices_session_line,
            listOf(dayMonth(started, time.timeZone), activityText(lastAccess, now, time.timeZone)),
        ),
    )

    /**
     * An install worth a row: one with a device registration, or one whose ITMO session ITMO.ID still lists. The
     * install id is new after every sign-in, so each earlier sign-in of a phone leaves a nameless row behind.
     */
    private fun AppInstall.isLive(sessions: Map<String, ItmoLogin>, listed: Boolean): Boolean =
        deviceName != null || (listed && sid != null && sid in sessions)

    /** ITMO.ID sessions that match no install and no client of ours, live or revoked, nor this phone. */
    private fun otherSessions(): List<ItmoLogin> {
        val listed = (itmo as? AppResult.Success)?.value as? ItmoLogins.Listed ?: return emptyList()
        val data = backend
        val ours = buildSet {
            listed.ownSid?.let(::add)
            data?.installs?.mapNotNullTo(this) { it.sid }
            data?.connections?.mapNotNullTo(this) { it.itmo?.sid }
        }
        return listed.sessions.filter { it.sid !in ours }
    }

    private fun installOf(key: String): AppInstall? =
        backend?.installs?.firstOrNull { INSTALL + it.instanceId == key }

    private fun installSheet(key: String): FactsSheet? {
        val zone = time.timeZone
        val listed = (itmo as? AppResult.Success)?.value as? ItmoLogins.Listed
        val install = installOf(key)
        val current = install?.current ?: (key == INSTALL + THIS_PHONE)
        val sid = install?.sid ?: listed?.ownSid?.takeIf { current }
        val session = sid?.let { s -> listed?.sessions?.firstOrNull { it.sid == s } }
        val facts = buildList {
            session?.let {
                add(Fact(UiText.Res(AccountStrings.devices_fact_sign_in), dayMonth(it.started, zone)))
                add(Fact(UiText.Res(AccountStrings.devices_fact_until), dayMonth(it.expires, zone)))
            }
            if (install != null && !current) {
                add(Fact(UiText.Res(AccountStrings.devices_fact_activity), activityText(install.lastSeenAt, time.now(), zone)))
                add(
                    Fact(
                        UiText.Res(AccountStrings.devices_fact_notifications),
                        UiText.Res(if (install.pushEnabled) AccountStrings.devices_notifications_on else AccountStrings.devices_notifications_off),
                    ),
                )
            }
            session?.ipAddress?.let { add(Fact(UiText.Res(AccountStrings.devices_fact_ip), UiText.Dynamic(it))) }
        }
        return FactsSheet(
            key = key,
            title = if (current) UiText.Dynamic(phone.name) else install?.let(::installTitle) ?: UiText.Dynamic(phone.name),
            subtitle = install?.let(::installLine),
            facts = facts,
            action = if (current) null else UiText.Res(AccountStrings.devices_sign_out_action),
            note = if (current) UiText.Res(AccountStrings.devices_this_phone_note) else null,
        )
    }

    private fun sessionSheet(key: String): FactsSheet? {
        val session = otherSessions().firstOrNull { SESSION + it.sid == key } ?: return null
        val zone = time.timeZone
        return FactsSheet(
            key = key,
            title = UiText.Dynamic(session.label),
            subtitle = UiText.Res(AccountStrings.devices_session_subtitle),
            facts = listOfNotNull(
                Fact(UiText.Res(AccountStrings.devices_fact_started), dayMonth(session.started, zone)),
                Fact(UiText.Res(AccountStrings.devices_fact_until), dayMonth(session.expires, zone)),
                Fact(UiText.Res(AccountStrings.devices_fact_activity), activityText(session.lastAccess, time.now(), zone)),
                session.ipAddress?.let { Fact(UiText.Res(AccountStrings.devices_fact_ip), UiText.Dynamic(it)) },
            ),
            action = UiText.Res(AccountStrings.devices_end_action),
            note = null,
        )
    }

    private fun ask(title: UiText, text: UiText, confirm: UiText) =
        _uiState.update { it.copy(confirm = ConfirmAsk(title, text, confirm)) }

    private fun AppResult<*>.failureText(): UiText? = when (this) {
        is AppResult.Success -> null
        is AppResult.Failure -> if (error == AppError.Unauthorized) UiText.Res(AccountStrings.devices_no_cookie) else error.toUiText()
    }

    private sealed interface Pending {
        class End(val sids: List<String>) : Pending

        class SignOut(val install: AppInstall) : Pending
    }

    private companion object {
        const val INSTALL = "install:"
        const val CLIENT = "client:"
        const val SESSION = "session:"
        const val THIS_PHONE = "this"
        val SETTLE_DELAY = 3.seconds

        fun lines(first: UiText, second: UiText?): UiText =
            second?.let { UiText.Joined(listOf(first, it), "\n") } ?: first
    }
}
