package dev.alllexey.itmowidgets.feature.schedule.presentation.lesson

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import kotlinx.serialization.json.Json

/** The arguments of [LessonSheetViewModel] for a route of the lesson sheet: one JSON string under its key. */
object LessonSheetArgs {
    private val json = Json { ignoreUnknownKeys = true }

    /** The saved-state entries of [route], `AppRoutes.LessonDetails` or `AppRoutes.PendingSportDetails`. */
    fun of(route: AppRoute): Map<String, String> = when (route) {
        is AppRoutes.LessonDetails ->
            mapOf(LessonSheetViewModel.ARG_LESSON to json.encodeToString(LessonDetailsArgs.serializer(), route.args))
        is AppRoutes.PendingSportDetails ->
            mapOf(LessonSheetViewModel.ARG_SPORT to json.encodeToString(PendingSportDetailsArgs.serializer(), route.args))
        else -> error("Not a lesson sheet: $route")
    }

    internal fun lesson(text: String): LessonDetailsArgs = json.decodeFromString(LessonDetailsArgs.serializer(), text)

    internal fun sport(text: String): PendingSportDetailsArgs = json.decodeFromString(PendingSportDetailsArgs.serializer(), text)
}
