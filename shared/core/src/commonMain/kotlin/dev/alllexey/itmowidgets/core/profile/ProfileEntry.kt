package dev.alllexey.itmowidgets.core.profile

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.flow.Flow

/**
 * A row a feature adds to Профиль without Профиль depending on it (`Общежитие`): [id] is stable and orders the rows,
 * a tap opens [route].
 */
data class ProfileEntry(
    val id: String,
    val icon: AppIcon,
    val title: UiText,
    val subtitle: UiText?,
    val route: AppRoute,
)

/**
 * A feature's row in Профиль: null while the student has nothing there, so the row is hidden. Sources are an open set
 * (`getAll()`), each under its own qualifier.
 */
fun interface ProfileEntrySource {
    fun observe(): Flow<ProfileEntry?>
}
