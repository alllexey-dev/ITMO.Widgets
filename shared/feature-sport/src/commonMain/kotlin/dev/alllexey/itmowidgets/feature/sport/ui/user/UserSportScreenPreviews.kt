package dev.alllexey.itmowidgets.feature.sport.ui.user

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportUiState
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingSamples

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LP-1d recorded the XML references as
 * `UserSportScreen_<state>`. Each state therefore is a function called `UserSportScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun UserSportPreview(state: UserSportUiState, name: String) = ItmoPreview {
    UserSportScreen(state, name, SportBookingSamples.time, UserSportActions())
}

/** Another user's two volleyball bookings, read-only, as the reference showed Ivan's. */
internal class UserSportScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun UserSportScreen() = UserSportPreview(
        UserSportUiState.Content(listOf(SportBookingSamples.signed, SportBookingSamples.nextWeek), refreshing = false),
        name = "Иван Петров",
    )
}

/** Neither confirmed lessons nor queues. */
internal class UserSportScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun UserSportScreen() = UserSportPreview(
        UserSportUiState.Content(emptyList(), refreshing = false),
        name = "Дмитрий Смирнов",
    )
}

/** The user's privacy settings hide their sport: the lock, nothing to retry. */
internal class UserSportScreenLockPreview {
    @Preview(name = "lock")
    @Composable
    fun UserSportScreen() = UserSportPreview(UserSportUiState.Error(AppError.Forbidden), name = "Иван Петров")
}
