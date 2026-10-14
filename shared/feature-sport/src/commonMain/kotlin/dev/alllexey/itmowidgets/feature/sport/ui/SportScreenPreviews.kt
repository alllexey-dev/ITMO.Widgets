package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.feature.sport.ui.preview.SportSamples

/*
 * The Спорт tab at the design lab's axes; the harness names a baseline `<function>_<@Preview name>`, so each state is a
 * function named after its screen in a holder class of its own. Sheets are separate windows: their contents have
 * previews of their own.
 */

@Composable
private fun MyPreview(state: SportMyUiState) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.background)) {
        SportScreen(SportSamples.TITLE, SportTab.MY, {}, state, SportSamples.sign("none", "normal"), SportMyActions(), SportSignActions())
    }
}

@Composable
private fun SignPreview(state: SportSignUiState) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.background)) {
        SportScreen(SportSamples.TITLE, SportTab.SIGN, {}, SportSamples.my("active"), state.copy(filters = null), SportMyActions(), SportSignActions())
    }
}

internal class SportScreenMyActivePreview {
    @Preview(name = "my-active", heightDp = 1900)
    @Composable
    fun SportScreen() = MyPreview(SportSamples.my("active"))
}

internal class SportScreenMyNonePreview {
    @Preview(name = "my-none")
    @Composable
    fun SportScreen() = MyPreview(SportSamples.my("none"))
}

internal class SportScreenMyLoadingPreview {
    @Preview(name = "my-loading")
    @Composable
    fun SportScreen() = MyPreview(SportSamples.myLoading)
}

internal class SportScreenMyErrorPreview {
    @Preview(name = "my-error")
    @Composable
    fun SportScreen() = MyPreview(SportSamples.myFailed)
}

internal class SportScreenSignPreview {
    @Preview(name = "sign")
    @Composable
    fun SportScreen() = SignPreview(SportSamples.sign("none", "normal"))
}

internal class SportScreenSignFilteredPreview {
    @Preview(name = "sign-filtered")
    @Composable
    fun SportScreen() = SignPreview(SportSamples.sign("active", "normal"))
}

internal class SportScreenSignEmptyPreview {
    @Preview(name = "sign-empty")
    @Composable
    fun SportScreen() = SignPreview(SportSamples.sign("none", "empty"))
}

internal class SportScreenSignFilteredEmptyPreview {
    @Preview(name = "sign-filtered-empty")
    @Composable
    fun SportScreen() = SignPreview(SportSamples.sign("active", "empty"))
}

internal class SportScreenSignErrorPreview {
    @Preview(name = "sign-error")
    @Composable
    fun SportScreen() = SignPreview(SportSamples.signFailed)
}

internal class SportFiltersSheetContentPreview {
    @Preview(name = "content")
    @Composable
    fun SportFiltersSheetContent() = ItmoPreview {
        SportFiltersSheetContent(checkNotNull(SportSamples.sign("sheet", "normal").filters), SportFiltersActions())
    }
}

internal class SportJoinSheetContentFullPreview {
    @Preview(name = "full")
    @Composable
    fun SportJoinSheetContent() = ItmoPreview { SportJoinSheetContent(SportSamples.join("full"), SportPromptActions()) }
}

internal class SportJoinSheetContentFuturePreview {
    @Preview(name = "future")
    @Composable
    fun SportJoinSheetContent() = ItmoPreview { SportJoinSheetContent(SportSamples.join("future"), SportPromptActions()) }
}

internal class SportLessonSheetContentCanPreview {
    @Preview(name = "can", heightDp = 1200)
    @Composable
    fun SportLessonSheetContent() = ItmoPreview { SportLessonSheetContent(SportSamples.lesson("can"), SportLessonSheetActions()) }
}

internal class SportLessonSheetContentQueuePreview {
    @Preview(name = "queue", heightDp = 1200)
    @Composable
    fun SportLessonSheetContent() = ItmoPreview { SportLessonSheetContent(SportSamples.lesson("queue"), SportLessonSheetActions()) }
}

internal class SportLessonSheetContentClashPreview {
    @Preview(name = "clash", heightDp = 1200)
    @Composable
    fun SportLessonSheetContent() = ItmoPreview { SportLessonSheetContent(SportSamples.lesson("clash"), SportLessonSheetActions()) }
}

internal class SportLessonSheetContentBookedPreview {
    @Preview(name = "booked", heightDp = 1200)
    @Composable
    fun SportLessonSheetContent() = ItmoPreview {
        SportLessonSheetContent(SportSamples.lesson("booked"), SportLessonSheetActions(), section = SportSamples.sectionToggle("on"))
    }
}

internal class SportLessonSheetContentSectionFullPreview {
    @Preview(name = "section-full", heightDp = 1200)
    @Composable
    fun SportLessonSheetContent() = ItmoPreview {
        SportLessonSheetContent(SportSamples.lesson("booked"), SportLessonSheetActions(), section = SportSamples.sectionToggle("full"))
    }
}
