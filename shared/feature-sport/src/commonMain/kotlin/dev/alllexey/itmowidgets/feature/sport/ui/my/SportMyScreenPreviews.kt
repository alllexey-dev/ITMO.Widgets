package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyUiState

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LP-1d recorded the XML reference as
 * `SportMyScreen_content`. Each state therefore is a function called `SportMyScreen` in a holder class of its own;
 * the scanner instantiates each holder by reflection.
 */

@Composable
private fun SportMyPreview(state: SportMyUiState) = ItmoPreview {
    SportMyScreen(state, SportBookingSamples.time, SportMyActions(), animateScore = false)
}

private fun content(bookings: List<SportBooking>) =
    SportMyUiState.Content(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true), SportBookingSamples.score, bookings)

/** The reference's page: the score card over tomorrow's queue entry and two volleyball bookings. */
internal class SportMyScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun SportMyScreen() = SportMyPreview(content(SportBookingSamples.content))
}

/** No bookings: the expanded card above the way to `Запись`. */
internal class SportMyScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun SportMyScreen() = SportMyPreview(content(emptyList()))
}

/** The first load. */
internal class SportMyScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun SportMyScreen() = SportMyPreview(SportMyUiState.Loading)
}

/** Nothing loaded: the error and a retry. */
internal class SportMyScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun SportMyScreen() = SportMyPreview(SportMyUiState.Error(AppError.Network))
}
