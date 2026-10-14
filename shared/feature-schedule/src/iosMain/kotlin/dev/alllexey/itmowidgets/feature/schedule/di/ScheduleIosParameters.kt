package dev.alllexey.itmowidgets.feature.schedule.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.ScheduleMonthViewModel
import kotlinx.datetime.LocalDate

// The Koin parameters of the schedule's SwiftUI screens: a SavedStateHandle with what Android's shell seeds from the
// route, since a SwiftUI store has no saved-state registry.

/** The feed: the tab's own schedule for [userIsu] 0, else that user's on a pushed screen. */
fun scheduleFeedParameters(userIsu: Int, name: String): List<Any> = listOf(
    SavedStateHandle(if (userIsu > 0) mapOf(UserScreenArgs.ISU to userIsu, UserScreenArgs.NAME to name) else emptyMap()),
)

/** The lesson sheet of [route], `AppRoutes.LessonDetails` or `AppRoutes.PendingSportDetails`. */
fun lessonSheetParameters(route: AppRoute): List<Any> = listOf(SavedStateHandle(LessonSheetArgs.of(route)))

fun scheduleMonthParameters(route: AppRoutes.ScheduleMonth): List<Any> = listOf(
    SavedStateHandle(mapOf(ScheduleMonthViewModel.ARG_DATE to route.date, ScheduleMonthViewModel.ARG_USER_ISU to route.userIsu)),
)

fun friendPickerParameters(route: AppRoutes.FriendSelector): List<Any> = listOf(
    SavedStateHandle(mapOf(FriendSelectionContract.ARG_SELECTED_ISU to route.selectedIsu)),
)

/** The month sheet's `Открыть` in the feed; [isoDate] is `2026-09-17`, since Swift has no `LocalDate`. */
fun showScheduleDate(viewModel: ScheduleFeedViewModel, isoDate: String) = viewModel.showDate(LocalDate.parse(isoDate))
