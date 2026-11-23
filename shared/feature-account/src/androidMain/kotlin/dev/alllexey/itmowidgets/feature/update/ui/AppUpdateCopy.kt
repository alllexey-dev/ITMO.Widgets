package dev.alllexey.itmowidgets.feature.update.ui

import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.app_update_action
import dev.alllexey.itmowidgets.shared.compose.strings.app_update_description
import dev.alllexey.itmowidgets.shared.compose.strings.app_update_open_failed
import dev.alllexey.itmowidgets.shared.compose.strings.app_update_play_action
import dev.alllexey.itmowidgets.shared.compose.strings.app_update_play_description
import dev.alllexey.itmowidgets.shared.compose.strings.app_update_play_open_failed
import org.jetbrains.compose.resources.StringResource

/**
 * How the offer reads for the place the build is updated from; the app binds one per distribution variant.
 *
 * @property description why the screen is open, for a build Backend still serves.
 * @property openFailed the snackbar when the update button could open nothing.
 * @property showsNote whether Backend's release note follows the description.
 */
enum class AppUpdateCopy(
    internal val description: StringResource,
    internal val action: StringResource,
    internal val openFailed: StringResource,
    internal val showsNote: Boolean,
) {
    /** A download from the release page: the `github` variant. */
    Download(
        description = Res.string.app_update_description,
        action = Res.string.app_update_action,
        openFailed = Res.string.app_update_open_failed,
        showsNote = true,
    ),

    /**
     * An update through Google Play: the `play` variant. Backend's note is one text for both Android variants and
     * may describe a release Play does not serve or name another way to install it, so it stays out.
     */
    GooglePlay(
        description = Res.string.app_update_play_description,
        action = Res.string.app_update_play_action,
        openFailed = Res.string.app_update_play_open_failed,
        showsNote = false,
    ),
}
