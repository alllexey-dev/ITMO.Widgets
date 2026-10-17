package dev.alllexey.itmowidgets.feature.recordbook.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs

/** The Koin parameters of `SheetScoresViewModel` for the SwiftUI sheet: [args] in a handle, as Android's entry seeds it. */
object SheetScoresIosParameters {
    fun viewModel(args: SheetScoresArgs): List<Any> = listOf(SavedStateHandle(mapOf(*args.handleEntries())))
}

/** Android's `SheetScoresArgs.toBundle()`. */
internal fun SheetScoresArgs.handleEntries(): Array<Pair<String, Any>> = arrayOf(
    SheetScoresArgs.SUBJECT_ID to subjectId,
    SheetScoresArgs.SUBJECT_NAME to subjectName,
    SheetScoresArgs.PERIOD_KEY to periodKey,
    SheetScoresArgs.URL to url,
    SheetScoresArgs.STEP to step.name,
)
