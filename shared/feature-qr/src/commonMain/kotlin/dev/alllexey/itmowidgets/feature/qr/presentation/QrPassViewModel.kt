package dev.alllexey.itmowidgets.feature.qr.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.shared.feature.qr.QrStrings
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * The pass sheet, the one home of the QR pass: the Сегодня button, the widgets, the tile and the shortcuts open it.
 * It shows the last code at once, whatever its age, and asks My ITMO for a new one when the cached code is older than
 * [QrCodeSnapshot.FRESH_MILLIS] or the user taps `Обновить`. Without network the last code stays with its time.
 * Requests run only while the sheet is shown ([start], [stop]); the wall [clock] dates the codes in the phone's
 * [zone], never the academic time, which a debug override can move.
 */
class QrPassViewModel(
    private val repository: QrCodeRepository,
    private val appearance: QrAppearancePreferences,
    sessions: SessionRepository,
    social: SocialRepository,
    private val clock: Clock,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val state = MutableStateFlow(QrPassUiState())
    val uiState: StateFlow<QrPassUiState> = state.asStateFlow()
    private val queue = EventQueue<QrPassEvent>()
    val events: Flow<QrPassEvent> = queue.events
    private var request: Job? = null
    private var visible = false

    init {
        combine(sessions.state, social.observeCurrentUser()) { session, backendUser ->
            val user = (session as? SessionState.SignedIn)?.user
            holderOf(user?.name, user?.isu ?: backendUser?.isu, backendUser?.primaryGroup()?.name)
        }.onEach { holder -> state.update { it.copy(holder = holder) } }.launchIn(viewModelScope)
    }

    fun start() {
        visible = true
        refresh(RefreshMode.Silent)
    }

    fun stop() {
        visible = false
        request?.cancel()
        request = null
    }

    /** [RefreshMode.Force] always asks My ITMO; any other mode only when the cached code is not fresh. */
    fun refresh(mode: RefreshMode) {
        if (!visible || request?.isActive == true) return
        val force = mode == RefreshMode.Force
        request = viewModelScope.launch {
            val dynamicColors = appearance.useDynamicColors()
            val last = repository.lastQr()?.takeIf { it.hex.isNotBlank() }
            val asks = force || last == null || last.expiresAtMillis <= nowMillis()
            state.update {
                it.copy(
                    code = last?.let { code -> QrPassCode(code.hex, dynamicColors) },
                    status = when {
                        last == null -> QrPassStatus.Loading
                        asks -> QrPassStatus.Refreshing
                        else -> QrPassStatus.Ready(timeOf(last))
                    },
                )
            }
            if (!asks) return@launch
            val result = repository.refreshQrHex(force = force)
            val code = repository.lastQr()?.takeIf { it.hex.isNotBlank() }
            val error = (result as? AppResult.Failure)?.error
            state.update {
                it.copy(
                    code = code?.let { snapshot -> QrPassCode(snapshot.hex, dynamicColors) },
                    status = when {
                        code == null && error != null -> QrPassStatus.Failed(error)
                        code == null -> QrPassStatus.Empty
                        error == AppError.Network -> QrPassStatus.Offline(timeOf(code))
                        else -> QrPassStatus.Ready(timeOf(code))
                    },
                )
            }
            if (code != null && error != null && error != AppError.Network) queue.send(QrPassEvent.RefreshFailed(error))
        }
    }

    private fun holderOf(name: String?, isu: Int?, group: String?): QrPassHolder? {
        val details = when {
            isu != null && group != null -> UiText.Res(QrStrings.qr_pass_group_isu, listOf(group, isu))
            isu != null -> UiText.Res(QrStrings.qr_pass_isu, listOf(isu))
            group != null -> UiText.Dynamic(group)
            else -> null
        }
        return if (name == null && details == null) null else QrPassHolder(name, details)
    }

    /** `11:44` for a code from today, `1 сент., 11:44` for an older one. */
    private fun timeOf(code: QrCodeSnapshot): String {
        val saved = Instant.fromEpochMilliseconds(code.savedAtMillis).toLocalDateTime(zone)
        val today = clock.now().toLocalDateTime(zone).date
        return if (saved.date == today) {
            saved.time.format(DateTexts.TIME)
        } else {
            saved.format(DateTexts.DAY_SHORT_MONTH_TIME)
        }
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()
}
