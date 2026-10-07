package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.shared.core.common_partial_load_error
import dev.alllexey.itmowidgets.shared.core.common_retry
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LP-1d recorded the XML reference as
 * `SportSignScreen_content`. Each state therefore is a function called `SportSignScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

/** Today's catalog under the default filters: the section and building fields, the toggles, the week strip. */
private fun content(): SportSignUiState.Content = SportSignFiltersSamples.hiddenSelectors().copy(
    availableSports = SportSignFiltersSamples.sections,
    displayedLessons = listOf(SportLessonSamples.scarce, SportLessonSamples.open),
)

@Composable
private fun SignPreview(state: SportSignUiState) {
    SportSignScreen(state, SportLessonSamples.time, SportSignActions())
}

internal class SportSignScreenContentPreview {
    @Preview(name = "content")
    @Composable
    fun SportSignScreen() = ItmoPreview { SignPreview(content()) }
}

/** Nothing has answered and nothing runs yet: no header, placeholder cards. */
internal class SportSignScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun SportSignScreen() = ItmoPreview { SignPreview(SportSignUiState.Loading) }
}

/** The first load: the header and the week strip are real, the list waits for the catalog. */
internal class SportSignScreenInitialLoadingPreview {
    @Preview(name = "initial-loading")
    @Composable
    fun SportSignScreen() = ItmoPreview {
        SignPreview(SportSignFiltersSamples.hiddenSelectors().copy(initialLoading = true))
    }
}

/** A source failed behind content that stays: the snackbar with a retry over the list. */
internal class SportSignScreenPartialErrorPreview {
    @Preview(name = "partial-error")
    @Composable
    fun SportSignScreen() = ItmoPreview {
        Box {
            SignPreview(content().copy(hasPartialError = true))
            Snackbar(
                PreviewSnackbar(
                    stringResource(CoreRes.string.common_partial_load_error),
                    stringResource(CoreRes.string.common_retry),
                ),
                Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** No lesson on the day under the filters: the reset chip and the empty state. */
internal class SportSignScreenFilteredEmptyPreview {
    @Preview(name = "filtered-empty")
    @Composable
    fun SportSignScreen() = ItmoPreview {
        SignPreview(content().copy(displayedLessons = emptyList(), showOnlyFriends = true, hasActiveFilters = true))
    }
}

/** A snackbar on screen without a host: the harness stops the clock before a shown one would fade in. */
private class PreviewSnackbar(message: String, actionLabel: String) : SnackbarData {
    override val visuals: SnackbarVisuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel = actionLabel
        override val withDismissAction = false
        override val duration = SnackbarDuration.Long
    }

    override fun dismiss() = Unit

    override fun performAction() = Unit
}
