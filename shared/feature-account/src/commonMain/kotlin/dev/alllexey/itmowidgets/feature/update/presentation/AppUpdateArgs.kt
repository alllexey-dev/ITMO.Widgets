package dev.alllexey.itmowidgets.feature.update.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import kotlinx.serialization.Serializable

/**
 * The update screen's arguments: the result of the check, so the screen never repeats the request. The Fragment host
 * passes them under the keys below; a Navigation 3 entry keys the overlay with this class itself.
 */
@Serializable
data class AppUpdateArgs(
    val installed: String,
    val latest: String,
    /** Release notes from the backend; empty when there are none. */
    val note: String,
    val unsupported: Boolean
) {
    companion object {
        const val KEY_INSTALLED_VERSION = "installed_version"
        const val KEY_LATEST_VERSION = "latest_version"
        const val KEY_NOTE = "note"
        const val KEY_UNSUPPORTED = "unsupported"

        fun of(update: AppUpdate) = AppUpdateArgs(
            installed = update.installed.raw,
            latest = update.latest.raw,
            note = update.note,
            unsupported = update.unsupported
        )

        /** Both versions are required; a missing note is empty and a missing flag is a supported build. */
        fun from(handle: SavedStateHandle) = AppUpdateArgs(
            installed = checkNotNull(handle.get<String>(KEY_INSTALLED_VERSION)),
            latest = checkNotNull(handle.get<String>(KEY_LATEST_VERSION)),
            note = handle.get<String>(KEY_NOTE).orEmpty(),
            unsupported = handle.get<Boolean>(KEY_UNSUPPORTED) == true
        )
    }
}
