package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.url.StrictUri
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsGateway
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsLoadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordinates the foreground LMS sign-in and single-subject score fetch without persisting credentials. */
class LmsLoginViewModel(
    private val gateway: LmsGateway,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val state = MutableStateFlow<LmsLoginUiState>(LmsLoginUiState.Checking)
    val uiState: StateFlow<LmsLoginUiState> = state.asStateFlow()

    private val eventQueue = EventQueue<LmsLoginEvent>()
    val events: Flow<LmsLoginEvent> = eventQueue.events

    val loginUrl: String get() = gateway.loginUrl

    private var attempt = 0
    private var oauthState: String?
        get() = savedState[KEY_OAUTH_STATE]
        set(value) { savedState[KEY_OAUTH_STATE] = value }

    private val subject: String get() = savedState.get<String>(EXTRA_SUBJECT).orEmpty()
    private val studyYear: String get() = savedState.get<String>(EXTRA_YEAR).orEmpty()
    private val semester: Int get() = savedState.get<Int>(EXTRA_SEMESTER) ?: 0

    init {
        start()
    }

    /** Checks session eligibility before opening or retrying the LMS browser flow. */
    fun start() {
        if (state.value == LmsLoginUiState.Loading) return
        state.value = LmsLoginUiState.Checking
        viewModelScope.launch {
            when (gateway.canStart()) {
                null -> {
                    oauthState = null
                    state.value = LmsLoginUiState.Web(++attempt)
                }
                AppError.DemoUnavailable -> state.value = LmsLoginUiState.Error(LmsLoginError.DEMO)
                else -> state.value = LmsLoginUiState.Error(LmsLoginError.WRONG_ACCOUNT, clearCookiesOnRetry = true)
            }
        }
    }

    fun isAllowedBrowserUrl(url: String): Boolean = gateway.isAllowedBrowserUrl(url)

    /** Handles a main-frame URL navigation inside the LMS WebView; returns true when intercepted. */
    fun onNavigation(url: String): Boolean {
        if (!gateway.isAllowedBrowserUrl(url)) {
            if (state.value != LmsLoginUiState.Loading) {
                state.value = LmsLoginUiState.Error(LmsLoginError.NETWORK_OR_UNKNOWN)
            }
            return true
        }
        gateway.authorizationState(url)?.let { oauthState = it }
        val uri = StrictUri.parse(url)
        if (uri?.host == CALLBACK_HOST && uri.rawPath == CALLBACK_PATH) {
            val expected = oauthState
            val code = expected?.let { gateway.callbackCode(url, it) }
            if (expected == null || code == null) {
                if (state.value != LmsLoginUiState.Loading) {
                    state.value = LmsLoginUiState.Error(LmsLoginError.NETWORK_OR_UNKNOWN)
                }
            } else {
                complete(code, expected)
            }
            return true
        }
        return false
    }

    fun onWebError() {
        if (state.value != LmsLoginUiState.Loading) {
            state.value = LmsLoginUiState.Error(LmsLoginError.NETWORK_OR_UNKNOWN)
        }
    }

    private fun complete(code: String, expectedState: String) {
        if (state.value == LmsLoginUiState.Loading) return
        state.value = LmsLoginUiState.Loading
        viewModelScope.launch {
            when (val result = gateway.load(code, expectedState, subject, studyYear, semester)) {
                is LmsLoadResult.Success -> eventQueue.send(LmsLoginEvent.Completed(result.scores))
                LmsLoadResult.EmptyCourses -> state.value = LmsLoginUiState.Error(LmsLoginError.EMPTY_COURSES)
                LmsLoadResult.NoMatch -> state.value = LmsLoginUiState.Error(LmsLoginError.NO_MATCH)
                is LmsLoadResult.CourseFailed -> state.value = errorState(result.error, LmsLoginError.COURSE_LOAD_FAILED)
                is LmsLoadResult.Failed -> state.value = errorState(result.error, LmsLoginError.NETWORK_OR_UNKNOWN)
            }
        }
    }

    private fun errorState(error: AppError, fallback: LmsLoginError): LmsLoginUiState.Error =
        if (error == AppError.Forbidden || error == AppError.Unauthorized) {
            LmsLoginUiState.Error(LmsLoginError.WRONG_ACCOUNT, clearCookiesOnRetry = true)
        } else {
            LmsLoginUiState.Error(if (error == AppError.DemoUnavailable) LmsLoginError.DEMO else fallback)
        }

    companion object {
        const val EXTRA_SUBJECT = "lms_subject"
        const val EXTRA_YEAR = "lms_year"
        const val EXTRA_SEMESTER = "lms_semester"
        private const val KEY_OAUTH_STATE = "lms_oauth_state"
        private const val CALLBACK_HOST = "lms.tuxedo.se.ifmo.ru"
        private const val CALLBACK_PATH = "/sso/token"
    }
}
