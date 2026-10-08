package dev.alllexey.itmowidgets.feature.schedule.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsActions
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsSheetRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsActions
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsContent
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsSheetState
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteActions
import dev.alllexey.itmowidgets.feature.schedule.ui.list.UserScheduleScreen
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Another user's schedule as the iOS shell hosts it, as `UserScheduleFragment` does on Android: [UserScheduleScreen]
 * around [ScheduleRoute] of the user [isu] named [name] (blank: no name in the title), whose ViewModel reads the user
 * from the arguments Android's destination gives it. Every lesson goes to [actions]; there are no pending sport rows
 * and no friend picker.
 */
@Composable
fun UserScheduleIosRoute(isu: Int, name: String, actions: ScheduleRouteActions, onBack: () -> Unit) {
    val viewModel = hostedViewModel<ScheduleViewModel>(ScheduleViewModel.ARG_USER_ISU to isu)
    UserScheduleScreen(name = name.takeIf { it.isNotBlank() }, onBack = onBack) {
        ScheduleRoute(ownTab = false, actions = actions, viewModel = viewModel)
    }
}

/**
 * What a sheet of the schedule asks of the iOS shell beyond the route: [onProfile] after closing the sheet, [onClose],
 * and the messages Android shows as a snackbar when no app takes the map ([onMapUnavailable],
 * `schedule_map_unavailable`) or the meeting link ([onLinkFailed], `link_open_failed`).
 */
class ScheduleSheetExits(
    val onProfile: (isu: Int) -> Unit,
    val onClose: () -> Unit,
    val onMapUnavailable: () -> Unit,
    val onLinkFailed: () -> Unit,
    val onOpenSport: () -> Unit = {},
)

/**
 * The lesson sheet as the iOS shell hosts it, as `LessonDetailsBottomSheet` does on Android: [LessonDetailsSheetRoute]
 * with its ViewModel reading the occurrence and the teacher from the arguments, the place in Apple Maps through
 * `PlatformActions` (the known building's pin, else the building text under its short title), the meeting link in
 * Safari. The friends on the lesson stay behind the Backend gate inside `LessonFriendsRepositoryImpl`.
 */
@Composable
fun LessonDetailsIosRoute(lesson: LessonDetailsArgs, exits: ScheduleSheetExits) {
    val buildings = koinInject<BuildingDirectory>()
    val platform = LocalPlatformActions.current
    val scope = rememberCoroutineScope()
    val arguments = buildList {
        add(LessonDetailsViewModel.ARG_PAIR_ID to lesson.pairId)
        add(LessonDetailsViewModel.ARG_DATE to lesson.date)
        UserScreenArgs.profileIsu(lesson.teacherIsu)?.let { add(LessonDetailsViewModel.ARG_TEACHER_ISU to it) }
    }
    val viewModel = hostedViewModel<LessonDetailsViewModel>(*arguments.toTypedArray())
    val actions = remember(lesson, exits, platform, scope) {
        LessonDetailsActions(
            onMap = {
                scope.launch {
                    val destination = buildings.mapDestination(lesson)
                    if (destination == null || !platform.openMap(destination)) exits.onMapUnavailable()
                }
            },
            onLink = { url -> if (!platform.openLink(url)) exits.onLinkFailed() },
            onProfile = exits.onProfile,
            onClose = exits.onClose,
        )
    }
    LessonDetailsSheetRoute(
        lesson = lesson,
        mapAvailable = remember(lesson) { buildings.knows(lesson) || lesson.building != null },
        actions = actions,
        modifier = Modifier.fillMaxSize(),
        viewModel = viewModel,
    )
}

/**
 * The schedule's sheet of a queued or predicted sport booking as the iOS shell hosts it, as
 * `PendingSportDetailsBottomSheet` does on Android: the room in Apple Maps, the teacher's profile and the sport tab
 * (where the queue is managed) through [exits].
 */
@Composable
fun PendingSportDetailsIosRoute(booking: PendingSportDetailsArgs, exits: ScheduleSheetExits) {
    val timeProvider = koinInject<AcademicTimeProvider>()
    val platform = LocalPlatformActions.current
    val actions = remember(booking, exits, platform) {
        PendingSportDetailsActions(
            onMap = {
                val destination = MapDestination(label = booking.sectionName, address = booking.roomName)
                if (!platform.openMap(destination)) exits.onMapUnavailable()
            },
            onProfile = exits.onProfile,
            onOpenSport = exits.onOpenSport,
            onClose = exits.onClose,
        )
    }
    PendingSportDetailsContent(
        PendingSportDetailsSheetState(booking, timeProvider.timeZone),
        actions,
        Modifier.fillMaxSize(),
    )
}

private fun BuildingDirectory.knows(lesson: LessonDetailsArgs): Boolean =
    find(lesson.buildingId, lesson.mainBuildingId, lesson.building) != null

/** Android's `LessonDetailsBottomSheet.mapDestination`. */
private suspend fun BuildingDirectory.mapDestination(lesson: LessonDetailsArgs): MapDestination? {
    find(lesson.buildingId, lesson.mainBuildingId, lesson.building)?.let { return it.toMapDestination() }
    val building = lesson.building ?: return null
    return MapDestination(label = buildingShortTitle(building).resolve(), address = building)
}

/**
 * Koin's definition of [VM] in the hosting controller's store with [arguments] as its `SavedStateHandle`, as Android
 * seeds a destination's handle from its arguments. `koinViewModel()` would hand the definition the store's own empty
 * handle instead, since a Compose controller on iOS has no destination arguments.
 */
@Composable
private inline fun <reified VM : ViewModel> hostedViewModel(vararg arguments: Pair<String, Any>): VM {
    val koin = getKoin()
    val handle = remember { SavedStateHandle(mapOf(*arguments)) }
    return viewModel { koin.get<VM> { parametersOf(handle) } }
}
