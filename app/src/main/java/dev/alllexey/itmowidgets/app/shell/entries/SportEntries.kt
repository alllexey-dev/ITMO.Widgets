package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.sport.navigation.SportDetailsOpener
import dev.alllexey.itmowidgets.feature.sport.navigation.SportRoutes
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.core.parameter.parametersOf

/**
 * The sport keys: another user's sport and the details sheet of a lesson or a booking. The sheet's booking action goes
 * on the result bus to the sport page that opened it ([SportRoutes.SportCommonDetails.replyTo]).
 */
internal fun EntryRegistry.Builder.sportEntries() {
    entry<AppRoutes.UserSport>(
        args = { bundleOf(UserScreenArgs.ISU to it.isu, UserScreenArgs.NAME to it.name) },
    ) { key, navigator ->
        UserSportEntry(onBack = { navigator.close(key) })
    }
    entry<SportRoutes.SportCommonDetails> { key, navigator -> SportDetailsEntry(key, navigator) }
}

/**
 * The sport tab's root: `SportRoute` with both page ViewModels in the root's own store, so `Запись`
 * keeps its week and filters across tab switches. A [TabRequest.SportLesson] (a shared link, a sport notification)
 * opens its lesson on `Запись` once; the pages' details sheets reply on their own result keys, so a sheet's result
 * reaches the page that opened it.
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

/** Another user's sport; its ViewModel reads the ISU and the name from the entry's arguments. */
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
 * The details sheet, its body on a `surfaceContainerLowest`
 * background: whether its action was sent survives recreation, the action goes to the opener and closes the sheet,
 * a profile opens in the sheet's place, sharing and the map stay on this activity.
 */
@Composable
private fun SportDetailsEntry(key: SportRoutes.SportCommonDetails, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val koin = remember(context) { KoinStarter.ensureStarted(context) }
    val time = remember(koin) { koin.get<AcademicTimeProvider>() }
    val results = LocalResultEventBus.current
    val submission = rememberSaveable(saver = SubmissionSaver) { SportDetailsSubmission() }
    val item = key.item
    val actions = remember(key, navigator, results) {
        SportDetailsActions(
            onAction = { action ->
                results.sendResult(key.replyTo.resultKey, SportSheetAction(item.lessonId, action.name))
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
