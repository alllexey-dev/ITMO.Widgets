package dev.alllexey.itmowidgets.feature.settings.reference

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.IcsExportBottomSheet
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * XML references of the «Выгрузить в .ics» sheet in its five states, under the names of LT-4c's `IcsExportSheet`
 * previews. The sheet is added into the host's container, so it draws its content without the dialog window; every
 * state but the choice starts from a saved week range, as after recreation, on Friday 2 October 2026.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class IcsExportReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-settings")

    @Test
    fun choose() = sheet("choose", FixedExport(AppResult.Success(null))) { SavedStateHandle() }

    @Test
    fun preparing() = sheet("preparing", FixedExport(null)) { weekChosen() }

    @Test
    fun ready() = sheet(
        "ready",
        FixedExport(AppResult.Success(null))
    ) {
        weekChosen(
            "ics_file_uri" to "content://dev.alllexey.itmowidgets.files/ics/schedule.ics",
            "ics_file_name" to "schedule.ics",
            "ics_file_lessons" to 23
        )
    }

    @Test
    fun empty() = sheet("empty", FixedExport(AppResult.Success(null))) { weekChosen() }

    @Test
    fun failed() = sheet("failed", FixedExport(AppResult.Failure(AppError.Network))) { weekChosen() }

    /** One view model per appearance over a fresh [saved] state. */
    private fun sheet(state: String, export: ScheduleIcsExport, saved: () -> SavedStateHandle) {
        lateinit var viewModel: IcsExportViewModel
        references.fragment(
            "IcsExportSheet_$state",
            ready = { viewModel.uiState.value != IcsExportUiState.Preparing || state == "preparing" }
        ) {
            IcsExportBottomSheet().withViewModel {
                IcsExportViewModel(export, TODAY, saved()).also { viewModel = it }
            }
        }
    }

    /** The `SavedStateHandle` keys the view model restores: the week range and, given, the written file. */
    private fun weekChosen(vararg file: Pair<String, Any>) = SavedStateHandle(mapOf("ics_range" to "week", *file))

    /** Answers every export with [result]; null holds it, so the sheet stays preparing. */
    private class FixedExport(private val result: AppResult<IcsFile?>?) : ScheduleIcsExport {
        override suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?> = result ?: awaitCancellation()
    }

    private companion object {
        val TODAY = FixedAcademicTime(LocalDateTime(LocalDate(2026, 10, 2), LocalTime(9, 0)))
    }
}
