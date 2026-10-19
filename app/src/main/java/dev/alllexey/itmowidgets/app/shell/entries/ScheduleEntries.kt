package dev.alllexey.itmowidgets.app.shell.entries

import android.view.View
import androidx.compose.foundation.layout.Box
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
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.ScheduleMonthViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.feed.ScheduleFeedHost
import dev.alllexey.itmowidgets.feature.schedule.ui.feed.ScheduleFeedRequest
import dev.alllexey.itmowidgets.feature.schedule.ui.feed.ScheduleFeedRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.friends.FriendPickerActions
import dev.alllexey.itmowidgets.feature.schedule.ui.friends.FriendPickerRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.lesson.LessonSheetActions
import dev.alllexey.itmowidgets.feature.schedule.ui.lesson.LessonSheetRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.month.ScheduleMonthRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate
import org.koin.core.parameter.parametersOf

/** The friend picker's answer to the schedule tab: [user]'s schedule, or the own one for null. */
internal data class FriendPick(val user: SelectedUser?)

/** The month sheet's `Открыть` for the schedule tab. */
internal data class DatePick(val date: LocalDate)

/**
 * The schedule's keys: another user's schedule, the lesson sheet (a timetable lesson or an own sport queue), the
 * friend picker and the month sheet. The picker and the month sheet answer the schedule tab on the result bus and
 * close; every sheet closes before a screen opens in its place.
 */
internal fun EntryRegistry.Builder.scheduleEntries() {
    entry<AppRoutes.UserSchedule>(
        args = { bundleOf(UserScreenArgs.ISU to it.isu, UserScreenArgs.NAME to it.name) },
    ) { key, navigator ->
        UserScheduleEntry(onBack = { navigator.close(key) }, navigator)
    }
    entry<AppRoutes.LessonDetails>(args = { it.sheetArgs() }) { key, navigator ->
        LessonSheetEntry(onClose = { navigator.close(key) }, navigator)
    }
    entry<AppRoutes.PendingSportDetails>(args = { it.sheetArgs() }) { key, navigator ->
        LessonSheetEntry(onClose = { navigator.close(key) }, navigator)
    }
    entry<AppRoutes.FriendSelector>(
        args = { bundleOf(FriendSelectionContract.ARG_SELECTED_ISU to it.selectedIsu) },
    ) { key, navigator ->
        FriendPickerEntry(onClose = { navigator.close(key) }, navigator)
    }
    entry<AppRoutes.ScheduleMonth>(
        args = { bundleOf(ScheduleMonthViewModel.ARG_DATE to it.date, ScheduleMonthViewModel.ARG_USER_ISU to it.userIsu) },
    ) { key, navigator ->
        MonthEntry(onClose = { navigator.close(key) })
    }
}

private fun AppRoute.sheetArgs() = bundleOf(*LessonSheetArgs.of(this).toList().toTypedArray())

/**
 * The schedule tab's root: the feed of the own schedule (or a friend's). It alone takes [TabRequest.ScheduleToday],
 * once, the picker's [FriendPick] and the month sheet's [DatePick]; the request stays with the back stack until this
 * root hands it on, so it survives recreation, and it is never handed out twice.
 */
@Composable
internal fun ScheduleTabRoot(navigator: Nav3AppNavigator) {
    val requests = remember { Channel<ScheduleFeedRequest>(Channel.UNLIMITED) }
    val requestFlow = remember(requests) { requests.receiveAsFlow() }

    val pending = navigator.pendingRequest(AppTab.SCHEDULE)
    LaunchedEffect(pending) {
        if (pending is TabRequest.ScheduleToday) {
            requests.trySend(ScheduleFeedRequest.Today)
            navigator.consume(AppTab.SCHEDULE)
        }
    }
    ResultEffect<FriendPick> { requests.trySend(ScheduleFeedRequest.SelectUser(it.user)) }
    ResultEffect<DatePick> { requests.trySend(ScheduleFeedRequest.ShowDate(it.date)) }

    val sport = rememberSportDetailsRedirect()
    val host = remember(navigator, sport) {
        ScheduleFeedHost(
            onMonth = { date, userIsu ->
                navigator.open(AppRoutes.ScheduleMonth(date.toString(), userIsu ?: FriendSelectionContract.NO_USER_ISU))
            },
            onPickFriend = { shown -> navigator.open(AppRoutes.FriendSelector(shown ?: FriendSelectionContract.NO_USER_ISU)) },
            onLesson = { sport.open(navigator, it) },
        )
    }
    ScheduleFeedRoute(host, viewModel = entryViewModel(), requests = requestFlow)
}

/** Another user's schedule from their profile: the same feed on a pushed screen, without the picker. */
@Composable
private fun UserScheduleEntry(onBack: () -> Unit, navigator: Nav3AppNavigator) {
    val sport = rememberSportDetailsRedirect()
    val host = remember(navigator, sport, onBack) { ScheduleFeedHost(onLesson = { sport.open(navigator, it) }, onBack = onBack) }
    ScheduleFeedRoute(host, viewModel = entryViewModel())
}

/**
 * A lesson of the feed opens its sheet: a sport lesson or an own queue the sport tab's sheet when the redirect finds
 * the booking, the lesson sheet otherwise.
 */
private fun SportDetailsRedirect.open(navigator: Nav3AppNavigator, target: LessonTarget) {
    when (target) {
        is LessonTarget.Lesson -> openLesson(navigator, target.args)
        is LessonTarget.PendingSport -> openPendingSport(navigator, target.args)
    }
}

/**
 * The lesson sheet: the meeting link stays on this activity (a snackbar when nothing takes it); the subject, its links,
 * a person and Спорт open in the sheet's place.
 */
@Composable
private fun LessonSheetEntry(onClose: () -> Unit, navigator: Nav3AppNavigator) {
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    val actions = remember(view, platform, onClose, navigator) {
        LessonSheetActions(
            onVideo = { url -> if (!platform.openLink(url)) view.say(R.string.link_open_failed) },
            onRoute = { route ->
                onClose()
                navigator.open(route)
            },
            onProfile = { isu ->
                onClose()
                navigator.openUserProfile(isu)
            },
            onSport = {
                onClose()
                navigator.select(AppTab.SPORT)
            },
            onClose = onClose,
        )
    }
    SheetBody { LessonSheetRoute(entryViewModel(), actions) }
}

/** The friend picker: the choice goes to the schedule tab before the sheet closes. */
@Composable
private fun FriendPickerEntry(onClose: () -> Unit, navigator: Nav3AppNavigator) {
    val results = LocalResultEventBus.current
    val actions = remember(results, onClose, navigator) {
        FriendPickerActions(
            onPick = { user ->
                results.sendResult(FriendPick(user))
                onClose()
            },
            onConnect = {
                onClose()
                navigator.open(AppRoutes.Settings(SERVICES_PAGE))
            },
            onClose = onClose,
        )
    }
    SheetBody { FriendPickerRoute(entryViewModel(), actions) }
}

/** The month sheet: `Открыть` goes to the schedule tab before the sheet closes. */
@Composable
private fun MonthEntry(onClose: () -> Unit) {
    val results = LocalResultEventBus.current
    SheetBody {
        ScheduleMonthRoute(
            viewModel = entryViewModel(),
            onOpen = { date ->
                results.sendResult(DatePick(date))
                onClose()
            },
            onClose = onClose,
        )
    }
}

/**
 * The sheets fill the shell's sheet, whose own container shows through down to the screen's edge, and keep its whole
 * height, so rows that load late (the friends on a lesson) never resize it.
 */
@Composable
private fun SheetBody(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}

private fun View.say(text: Int) = Snackbar.make(this, text, Snackbar.LENGTH_SHORT).show()

/** The settings page of the connection to ITMO.Widgets (`SettingsPage.SERVICES`). */
private const val SERVICES_PAGE = "SERVICES"

/**
 * Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle` (its arguments, seeded by
 * the shell) as Koin's `koinViewModel()` passes it.
 */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
