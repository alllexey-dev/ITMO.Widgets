package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.navigation.SportDetailsOpener
import dev.alllexey.itmowidgets.feature.sport.navigation.SportRoutes
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.SportHostActions
import dev.alllexey.itmowidgets.feature.sport.ui.SportRoute
import dev.alllexey.itmowidgets.feature.sport.ui.SportSharedLesson
import dev.alllexey.itmowidgets.feature.sport.ui.common.openSportMap
import dev.alllexey.itmowidgets.feature.sport.ui.common.openSportMapOrSay
import dev.alllexey.itmowidgets.feature.sport.ui.common.shareSportLesson
import dev.alllexey.itmowidgets.feature.sport.ui.common.showTemplateLessonNotice
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsActions
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSheet
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSubmission
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSheetAction
import dev.alllexey.itmowidgets.feature.sport.ui.user.UserSportRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.koin.core.parameter.parametersOf

/**
 * The sport keys (route map rows 7, 18, S10 and the cancel question): another user's sport, the details sheet of a
 * lesson or a booking and the question before a cancellation the shell asks. The sheet's booking action goes where
 * its [SportRoutes.SportCommonDetails.replyTo] says: to the sport page that opened it on the result bus, or, for a
 * sheet the [SportDetailsRedirect] opened from the feed or the schedule, to the shell's own question.
 */
internal fun EntryRegistry.Builder.sportEntries() {
    entry<AppRoutes.UserSport>(
        args = { bundleOf(UserScreenArgs.ISU to it.isu, UserScreenArgs.NAME to it.name) },
    ) { key, navigator ->
        UserSportEntry(onBack = { navigator.close(key) })
    }
    entry<SportRoutes.SportCommonDetails> { key, navigator -> SportDetailsEntry(key, navigator) }
    entry<AppRoutes.CancelBookingConfirm> { key, navigator -> CancelBookingEntry(key, onClose = { navigator.close(key) }) }
}

/**
 * The sport tab's root (route map row 7): `SportRoute` with both page ViewModels in the root's own store, so `Запись`
 * keeps its week and filters across tab switches. A [TabRequest.SportLesson] (a shared link, a sport notification)
 * opens its lesson on `Запись` once; the pages' details sheets reply on their own result keys, as `SportFragment`
 * routed the sheet's Fragment result to the page that opened it.
 */
@Composable
internal fun SportTabRoot(navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val time = remember(context) { KoinStarter.ensureStarted(context).get<AcademicTimeProvider>() }
    val myViewModel: SportMyViewModel = entryViewModel()
    val signViewModel: SportSignViewModel = entryViewModel()
    val sharedLessons = remember { Channel<SportSharedLesson>(Channel.UNLIMITED) }
    val mySheetActions = remember { Channel<SportSheetAction>(Channel.UNLIMITED) }
    val signSheetActions = remember { Channel<SportSheetAction>(Channel.UNLIMITED) }

    val request = navigator.pendingRequest(AppTab.SPORT)
    LaunchedEffect(request) {
        val lesson = request as? TabRequest.SportLesson ?: return@LaunchedEffect
        sharedLessons.trySend(SportSharedLesson(lesson.lessonId, lesson.predicted))
        navigator.consume(AppTab.SPORT)
    }
    ResultEffect<SportSheetAction>(SportDetailsOpener.MY.resultKey) { mySheetActions.trySend(it) }
    ResultEffect<SportSheetAction>(SportDetailsOpener.SIGN.resultKey) { signSheetActions.trySend(it) }

    val host = remember(navigator, context) {
        SportHostActions(
            onOpenBooking = { booking -> navigator.open(SportRoutes.details(booking, SportDetailsOpener.MY)) },
            onOpenLesson = { lesson, busy -> navigator.open(SportRoutes.details(lesson, SportDetailsOpener.SIGN, busy)) },
            onOpenMap = { booking -> booking.extractBuildingAddress()?.let { context.openSportMap(it) } },
            onTemplateLesson = { context.showTemplateLessonNotice() },
        )
    }
    SportRoute(
        time = time,
        host = host,
        sharedLessons = remember(sharedLessons) { sharedLessons.receiveAsFlow() },
        mySheetActions = remember(mySheetActions) { mySheetActions.receiveAsFlow() },
        signSheetActions = remember(signSheetActions) { signSheetActions.receiveAsFlow() },
        myViewModel = myViewModel,
        signViewModel = signViewModel,
    )
}

/**
 * What `MainActivity.openLessonDetails` and `openPendingSportDetails` did for the feed and the schedule: a sport lesson
 * (type 11) or a pending sport row opens the sport tab's details sheet when [holder] finds its booking (the holder's
 * tie-break and wait), the schedule's own sheet otherwise; every other lesson opens its sheet at once. The cancel
 * those sheets offer is asked first ([AppRoutes.CancelBookingConfirm]) and runs only while the booking still offers
 * it. [scope] outlives the screen that asked, as the activity's did, so a slow lookup still opens its sheet.
 */
internal class SportDetailsRedirect(
    private val holder: SportBookingsHolder,
    private val time: AcademicTimeProvider,
    private val scope: CoroutineScope,
) {

    fun openLesson(navigator: Nav3AppNavigator, args: LessonDetailsArgs) {
        if (args.typeId != SPORT_TYPE_ID) {
            navigator.open(AppRoutes.LessonDetails(args))
            return
        }
        scope.launch {
            val booking = holder.findSportBookingAt(LocalDate.parse(args.date), LocalTime.parse(args.start), args.subjectName)
            navigator.open(booking?.let(::shellDetails) ?: AppRoutes.LessonDetails(args))
        }
    }

    fun openPendingSport(navigator: Nav3AppNavigator, args: PendingSportDetailsArgs) {
        scope.launch {
            val booking = holder.findSportBooking(args.lessonId)
            navigator.open(booking?.let(::shellDetails) ?: AppRoutes.PendingSportDetails(args))
        }
    }

    /** The action of a sheet this redirect opened: the question opens only while the booking offers [action]. */
    fun onAction(navigator: Nav3AppNavigator, lessonId: Long, action: String?) {
        scope.launch {
            holder.cancelCandidate(lessonId, action) ?: return@launch
            navigator.open(AppRoutes.CancelBookingConfirm(lessonId))
        }
    }

    /** `Отменить` in the question: cancels only while the booking still offers a cancellation now. */
    fun confirmCancel(lessonId: Long) {
        scope.launch {
            val booking = holder.findSportBooking(lessonId) ?: return@launch
            if (booking.toDetailsArgs().bookingAction(time.now()) != SportBookingAction.NONE) holder.cancel(booking)
        }
    }

    private fun shellDetails(item: SportCommon) = SportRoutes.details(item, SportDetailsOpener.SHELL)

    private companion object {
        /** The schedule's lesson type of a sport booking. */
        const val SPORT_TYPE_ID = 11
    }
}

/** The redirect of this activity: the holder from Koin, the activity's scope (the composition's without one). */
@Composable
internal fun rememberSportDetailsRedirect(): SportDetailsRedirect {
    val context = LocalContext.current
    val fallback = rememberCoroutineScope()
    val scope = (LocalActivity.current as? ComponentActivity)?.lifecycleScope ?: fallback
    return remember(context, scope) {
        val koin = KoinStarter.ensureStarted(context)
        SportDetailsRedirect(koin.get(), koin.get(), scope)
    }
}

/** Another user's sport (route map row 18); its ViewModel reads the ISU and the name from the entry's arguments. */
@Composable
private fun UserSportEntry(onBack: () -> Unit) {
    val context = LocalContext.current
    val time = remember(context) { KoinStarter.ensureStarted(context).get<AcademicTimeProvider>() }
    UserSportRoute(
        time = time,
        onBack = onBack,
        onOpenMap = { booking -> booking.extractBuildingAddress()?.let { context.openSportMap(it) } },
        viewModel = entryViewModel(),
    )
}

/**
 * The details sheet (route map S10), `SportCommonDetailsBottomSheet`'s body on its `surfaceContainerLowest`
 * background: whether its action was sent survives recreation, the action goes to the opener and closes the sheet,
 * a profile opens in the sheet's place, sharing and the map stay on this activity.
 */
@Composable
private fun SportDetailsEntry(key: SportRoutes.SportCommonDetails, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val koin = remember(context) { KoinStarter.ensureStarted(context) }
    val time = remember(koin) { koin.get<AcademicTimeProvider>() }
    val results = LocalResultEventBus.current
    val redirect = rememberSportDetailsRedirect()
    val submission = rememberSaveable(saver = SubmissionSaver) { SportDetailsSubmission() }
    val item = key.item
    val actions = remember(key, navigator, results, redirect) {
        SportDetailsActions(
            onAction = { action ->
                when (key.replyTo) {
                    SportDetailsOpener.SHELL -> redirect.onAction(navigator, item.lessonId, action.name)
                    else -> results.sendResult(key.replyTo.resultKey, SportSheetAction(item.lessonId, action.name))
                }
                navigator.close(key)
            },
            onShare = { target -> context.shareSportLesson(item, target, koin.get<ShareLinkFactory>(), time) },
            onMap = context::openSportMapOrSay,
            onProfile = navigator::openUserProfile,
            onClose = { navigator.close(key) },
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surfaceContainerLowest),
    ) {
        SportDetailsSheet(
            item = item,
            time = time,
            submission = submission,
            actions = actions,
            modifier = Modifier.fillMaxSize(),
            actionsEnabled = key.actionsEnabled,
            busy = key.busy,
        )
    }
}

/** `MainActivity.onSportSheetAction`'s question: `Назад` closes it, `Отменить` cancels and closes it. */
@Composable
private fun CancelBookingEntry(key: AppRoutes.CancelBookingConfirm, onClose: () -> Unit) {
    val redirect = rememberSportDetailsRedirect()
    ConfirmDialogSurface(
        title = null,
        text = stringResource(R.string.sport_cancel_booking_question),
        confirmLabel = stringResource(R.string.sport_cancel_booking_action),
        dismissLabel = stringResource(R.string.common_back),
        onConfirm = {
            redirect.confirmCancel(key.lessonId)
            onClose()
        },
        onDismiss = onClose,
    )
}

private val SubmissionSaver = Saver<SportDetailsSubmission, Boolean>(
    save = { it.submitted },
    restore = { SportDetailsSubmission(it) },
)

/**
 * Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle` (its arguments, seeded by
 * the shell) as Koin's `koinViewModel()` passes it.
 */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
