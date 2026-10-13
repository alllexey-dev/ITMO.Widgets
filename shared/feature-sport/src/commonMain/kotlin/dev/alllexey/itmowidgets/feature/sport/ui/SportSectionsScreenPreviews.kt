package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsUiState
import dev.alllexey.itmowidgets.feature.sport.ui.preview.SportSamples

// `Мои секции` at the design lab's axes (the repeat on, off, the limit spent) and as the app shows it before Backend
// keeps the rules; `Откуда баллы` at both progress axes.

@Composable
private fun SectionsPreview(state: SportSectionsUiState) = ItmoPreview { SportSectionsScreen(state.copy(picker = null), SportSectionsActions()) }

internal class SportSectionsScreenOnPreview {
    @Preview(name = "repeat-on", heightDp = 1100)
    @Composable
    fun SportSectionsScreen() = SectionsPreview(SportSamples.sections("on"))
}

internal class SportSectionsScreenOffPreview {
    @Preview(name = "repeat-off", heightDp = 1100)
    @Composable
    fun SportSectionsScreen() = SectionsPreview(SportSamples.sections("off"))
}

internal class SportSectionsScreenLimitPreview {
    @Preview(name = "limit", heightDp = 1100)
    @Composable
    fun SportSectionsScreen() = SectionsPreview(SportSamples.sections("limit"))
}

internal class SportSectionsScreenPlainPreview {
    @Preview(name = "plain")
    @Composable
    fun SportSectionsScreen() = SectionsPreview(SportSamples.sectionsWithoutRepeat)
}

internal class SportSectionsScreenEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun SportSectionsScreen() = SectionsPreview(SportSamples.sectionsEmpty)
}

internal class SportSectionsPickerSheetContentPreview {
    @Preview(name = "content")
    @Composable
    fun SportSectionsPickerSheetContent() = ItmoPreview {
        SportSectionsPickerSheetContent(checkNotNull(SportSamples.sectionsPicker.picker), SportSectionsActions())
    }
}

internal class SportPointsScreenShortPreview {
    @Preview(name = "short", heightDp = 1100)
    @Composable
    fun SportPointsScreen() = ItmoPreview { SportPointsScreen(SportSamples.points("short"), SportPointsActions()) }
}

internal class SportPointsScreenDonePreview {
    @Preview(name = "done", heightDp = 1100)
    @Composable
    fun SportPointsScreen() = ItmoPreview { SportPointsScreen(SportSamples.points("done"), SportPointsActions()) }
}
