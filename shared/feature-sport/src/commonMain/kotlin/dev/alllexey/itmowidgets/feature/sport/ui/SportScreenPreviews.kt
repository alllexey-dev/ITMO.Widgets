package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyUiState
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingSamples
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportMyActions
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportMyScreen
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonSamples
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSignActions
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSignFiltersSamples
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSignScreen

/*
 * The harness names a baseline `<function>_<@Preview name>`. Each page therefore is a function called `SportScreen`
 * in a holder class of its own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun SportPreview(initialPage: SportPage) = ItmoPreview {
    SportScreen(rememberSportPagerState(initialPage)) { page ->
        when (page) {
            SportPage.MY -> SportMyScreen(
                SportMyUiState.Content(
                    SportAttempts(total = 3, used = 1, free = 2, canSignIn = true),
                    SportBookingSamples.score,
                    SportBookingSamples.content,
                ),
                SportBookingSamples.time,
                SportMyActions(),
                animateScore = false,
            )
            SportPage.SIGN -> SportSignScreen(
                SportSignFiltersSamples.hiddenSelectors().copy(
                    availableSports = SportSignFiltersSamples.sections,
                    displayedLessons = listOf(SportLessonSamples.scarce, SportLessonSamples.open),
                ),
                SportLessonSamples.time,
                SportSignActions(),
            )
        }
    }
}

/** The tab as it opens: `Мой спорт` in front with the score card over the bookings. */
internal class SportScreenMyPreview {
    @Preview(name = "my")
    @Composable
    fun SportScreen() = SportPreview(SportPage.MY)
}

/** `Запись` in front, as a shared link opens it: the filters, the week strip and the day's lessons. */
internal class SportScreenSignPreview {
    @Preview(name = "sign")
    @Composable
    fun SportScreen() = SportPreview(SportPage.SIGN)
}
