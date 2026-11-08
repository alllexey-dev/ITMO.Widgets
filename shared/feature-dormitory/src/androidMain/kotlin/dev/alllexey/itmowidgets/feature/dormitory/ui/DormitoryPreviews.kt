package dev.alllexey.itmowidgets.feature.dormitory.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitorySamples

// The dormitory screen at each Этап общежития and state; the names are `<Screen>_<state>`.

internal class DormitoryQueuePreview {
    @Preview(name = "queue", heightDp = 800)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.queue, DormitoryActions()) }
}

internal class DormitoryPlaceOfferedPreview {
    @Preview(name = "place-offered", heightDp = 1100)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.placeOffered, DormitoryActions()) }
}

internal class DormitoryDateChosenPreview {
    @Preview(name = "date-chosen", heightDp = 1100)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.dateChosen, DormitoryActions()) }
}

internal class DormitorySettledPreview {
    @Preview(name = "settled", heightDp = 1300)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.settled, DormitoryActions()) }
}

internal class DormitoryDebtPreview {
    @Preview(name = "debt", heightDp = 1100)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.debt, DormitoryActions()) }
}

internal class DormitoryPastYearsPreview {
    @Preview(name = "past-years", heightDp = 1500)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.pastYears, DormitoryActions()) }
}

internal class DormitoryContractOnlyPreview {
    @Preview(name = "contract-only", heightDp = 900)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.contractOnly, DormitoryActions()) }
}

internal class DormitoryOfflinePreview {
    @Preview(name = "offline", heightDp = 900)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.offline, DormitoryActions()) }
}

internal class DormitoryNonePreview {
    @Preview(name = "none", heightDp = 800)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.none, DormitoryActions()) }
}

internal class DormitoryFailedPreview {
    @Preview(name = "failed", heightDp = 800)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.failed, DormitoryActions()) }
}

internal class DormitoryLoadingPreview {
    @Preview(name = "loading", heightDp = 800)
    @Composable
    fun Dormitory() = ItmoPreview { DormitoryScreen(DormitorySamples.loading, DormitoryActions()) }
}
