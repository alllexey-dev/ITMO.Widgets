package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.result.ResultEffect
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.ScheduleChangesRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsActions
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsSheetRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsActions
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsContent
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsSheetState
import dev.alllexey.itmowidgets.feature.schedule.ui.details.lessonMapDestination
import dev.alllexey.itmowidgets.feature.schedule.ui.details.openLessonLink
import dev.alllexey.itmowidgets.feature.schedule.ui.details.openLessonMap
import dev.alllexey.itmowidgets.feature.schedule.ui.details.openPendingSportMap
import dev.alllexey.itmowidgets.feature.schedule.ui.details.viewModelArgs
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteActions
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteRequest
import dev.alllexey.itmowidgets.feature.schedule.ui.list.UserScheduleScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.parameter.parametersOf

/**
 * The schedule's keys: another user's schedule, the schedule changes, the lesson sheet and the pending sport sheet.
 * Both sheets close before a profile opens in their place.
 */
internal fun EntryRegistry.Builder.scheduleEntries() {
    entry<AppRoutes.UserSchedule>(
        args = { bundleOf(UserScreenArgs.ISU to it.isu, UserScreenArgs.NAME to it.name) },
    ) { key, navigator ->
        UserScheduleEntry(key, navigator)
    }
    entry<AppRoutes.ScheduleChanges> { key, navigator ->
        ScheduleChangesRoute(onBack = { navigator.close(key) }, viewModel = entryViewModel())
    }
    entry<AppRoutes.LessonDetails>(args = { it.args.viewModelArgs() }) { key, navigator ->
        LessonDetailsEntry(key.args, onClose = { navigator.close(key) }, navigator)
    }
    entry<AppRoutes.PendingSportDetails> { key, navigator ->
        PendingSportDetailsEntry(key.args, onClose = { navigator.close(key) }, navigator)
    }
}

/**
 * The schedule tab's root: the own `ScheduleRoute`. It alone takes [TabRequest.ScheduleToday], once,
 * and the friend picker's [FriendSelection]; the request stays with the back stack until this root hands it on, so it
 * survives recreation, and it is never handed out twice. A sport lesson or a pending sport row goes through the
 * [SportDetailsRedirect], which opens the sport tab's sheet when it finds the booking, the schedule's own otherwise.
 */
@Composable
internal fun ScheduleTabRoot(navigator: Nav3AppNavigator) {
    val timeProvider = koinGet<AcademicTimeProvider>()
    val requests = remember { Channel<ScheduleRouteRequest>(Channel.UNLIMITED) }
    val requestFlow = remember(requests) { requests.receiveAsFlow() }
    val sport = rememberSportDetailsRedirect()

    val pending = navigator.pendingRequest(AppTab.SCHEDULE)
    LaunchedEffect(pending) {
        if (pending is TabRequest.ScheduleToday) {
            requests.trySend(ScheduleRouteRequest.Today)
            navigator.consume(AppTab.SCHEDULE)
        }
    }
    ResultEffect<FriendSelection> { requests.trySend(ScheduleRouteRequest.SelectUser(it.toSelectedUser())) }

    val actions = remember(navigator, timeProvider, sport) {
        ScheduleRouteActions(
            onLessonClick = { lesson, date -> sport.openLesson(navigator, lesson.toDetailsArgs(date)) },
            onPendingClick = { sport.openPendingSport(navigator, it.toDetailsArgs(timeProvider.timeZone)) },
            onPickFriend = { shown ->
                navigator.open(AppRoutes.FriendSelector(shown?.isu ?: FriendSelectionContract.NO_USER_ISU))
            },
        )
    }
    ScheduleRoute(
        ownTab = true,
        actions = actions,
        requests = requestFlow,
        viewModel = entryViewModel(),
        timeProvider = timeProvider,
    )
}

/** As `ScheduleFragment` read the picker's result: no user for the own schedule, the picture URL as delivered. */
private fun FriendSelection.toSelectedUser(): SelectedUser? =
    if (useMySchedule) null else SelectedUser(isu = isu, name = name, avatar = pictureUrl)

/**
 * Another user's schedule: every lesson opens the lesson sheet, there are no pending sport rows and no
 * friend picker, and it never reads the schedule tab's request.
 */
@Composable
private fun UserScheduleEntry(key: AppRoutes.UserSchedule, navigator: Nav3AppNavigator) {
    val actions = remember(navigator) {
        ScheduleRouteActions(
            onLessonClick = { lesson, date -> navigator.open(AppRoutes.LessonDetails(lesson.toDetailsArgs(date))) },
        )
    }
    UserScheduleScreen(name = key.name, onBack = { navigator.close(key) }) {
        ScheduleRoute(
            ownTab = false,
            actions = actions,
            viewModel = entryViewModel(),
            timeProvider = koinGet(),
        )
    }
}

/** The lesson sheet: the map and the meeting link stay on this activity, a profile opens in its place. */
@Composable
private fun LessonDetailsEntry(lesson: LessonDetailsArgs, onClose: () -> Unit, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val view = LocalView.current
    val buildings = koinGet<BuildingDirectory>()
    val destination = remember(buildings, lesson, context) { buildings.lessonMapDestination(lesson, context) }
    val actions = remember(destination, context, view, onClose, navigator) {
        LessonDetailsActions(
            onMap = { destination?.let { context.openLessonMap(it, view) } },
            onLink = { url -> context.openLessonLink(url, view) },
            onProfile = navigator::openUserProfile,
            onClose = onClose,
        )
    }
    LessonDetailsSheetRoute(
        lesson = lesson,
        mapAvailable = destination != null,
        actions = actions,
        modifier = SheetSurface,
        viewModel = entryViewModel(),
    )
}

/**
 * The pending sport sheet: its open-sport button selects the sport tab, which closes the sheet, and a profile opens
 * in its place.
 */
@Composable
private fun PendingSportDetailsEntry(
    booking: PendingSportDetailsArgs,
    onClose: () -> Unit,
    navigator: Nav3AppNavigator,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val zone = koinGet<AcademicTimeProvider>().timeZone
    val actions = remember(booking, context, view, onClose, navigator) {
        PendingSportDetailsActions(
            onMap = { context.openPendingSportMap(booking, view) },
            onProfile = navigator::openUserProfile,
            onOpenSport = { navigator.select(AppTab.SPORT) },
            onClose = onClose,
        )
    }
    PendingSportDetailsContent(PendingSportDetailsSheetState(booking, zone), actions, SheetSurface)
}

/** Both sheets fill the shell's sheet on the lowest container, as `ItmoBottomSheetFragment` tints them. */
private val SheetSurface: Modifier
    @Composable get() = Modifier.fillMaxSize().background(ItmoTheme.colorScheme.surfaceContainerLowest)

@Composable
private inline fun <reified T : Any> koinGet(): T {
    val context = LocalContext.current
    return remember(context) { KoinStarter.ensureStarted(context).get<T>() }
}

/**
 * Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle` (its arguments, seeded by
 * the shell) as Koin's `koinViewModel()` passes it.
 */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
