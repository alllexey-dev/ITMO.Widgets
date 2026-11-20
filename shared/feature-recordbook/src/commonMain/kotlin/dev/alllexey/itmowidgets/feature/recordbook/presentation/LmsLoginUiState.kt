package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsScore

/** UI state of the foreground LMS check activity. */
sealed interface LmsLoginUiState {
    data object Checking : LmsLoginUiState
    data class Web(val attempt: Int) : LmsLoginUiState
    data object Loading : LmsLoginUiState
    data class Error(val reason: LmsLoginError, val clearCookiesOnRetry: Boolean = false) : LmsLoginUiState
}

enum class LmsLoginError {
    NETWORK_OR_UNKNOWN,
    WRONG_ACCOUNT,
    DEMO,
    EMPTY_COURSES,
    NO_MATCH,
    COURSE_LOAD_FAILED,
}

sealed interface LmsLoginEvent {
    data class Completed(val scores: List<LmsScore>) : LmsLoginEvent
}
