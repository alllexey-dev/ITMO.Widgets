package dev.alllexey.itmowidgets.feature.update.presentation

/**
 * @property note Release notes from the backend; empty when there are none.
 * @property unsupported The installed build is below the minimum the backend
 * serves, so the offer cannot be skipped — there is no working version to skip to.
 */
data class AppUpdateUiState(
    val installed: String,
    val latest: String,
    val note: String,
    val unsupported: Boolean
)

sealed interface AppUpdateEvent {
    /** The skipped release is stored; the screen may close. */
    data object Skipped : AppUpdateEvent
}
