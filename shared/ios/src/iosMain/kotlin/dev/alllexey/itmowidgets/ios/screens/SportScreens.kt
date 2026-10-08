package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.ui.SportHostActions
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportRoute
import dev.alllexey.itmowidgets.feature.sport.ui.SportSharedLesson
import dev.alllexey.itmowidgets.feature.sport.ui.SportShares
import dev.alllexey.itmowidgets.feature.sport.ui.UserSportIosRoute
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsActions
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSheet
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSubmission
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSheetAction
import dev.alllexey.itmowidgets.ios.di.IosKoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * What the Swift sport root keeps for as long as its screen lives, as Android's `SportFragment` keeps its channels:
 * the shared lessons a link or a notification asks the sign page to show, and the details sheet's actions for the
 * page that opened the sheet. One flow per channel, so recomposition never restarts the route's collectors.
 */
class SportTabState {
    private val sharedLessons = Channel<SportSharedLesson>(Channel.UNLIMITED)
    private val mySheetActions = Channel<SportSheetAction>(Channel.UNLIMITED)
    private val signSheetActions = Channel<SportSheetAction>(Channel.UNLIMITED)

    internal val sharedLessonFlow: Flow<SportSharedLesson> = sharedLessons.receiveAsFlow()
    internal val mySheetActionFlow: Flow<SportSheetAction> = mySheetActions.receiveAsFlow()
    internal val signSheetActionFlow: Flow<SportSheetAction> = signSheetActions.receiveAsFlow()

    /** `TabRequest.SportLesson`: the sign page comes to the front and opens the lesson, or its prediction. */
    fun openSharedLesson(lessonId: Long, predicted: Boolean) {
        sharedLessons.trySend(SportSharedLesson(lessonId, predicted))
    }

    /** The sheet of [request] sent [action]; the page that opened it checks it against what it shows now. */
    internal fun deliver(request: SportDetailsRequest, action: SportBookingAction) {
        val result = SportSheetAction(request.item.lessonId, action.name)
        when (request.page) {
            SportPage.MY -> mySheetActions.trySend(result)
            SportPage.SIGN -> signSheetActions.trySend(result)
        }
    }
}

/**
 * One opening of the details sheet the Swift host presents: the snapshot of the booking or lesson, the page it came
 * from, whether the page had a request in flight for it, and the sheet's one-shot submission for the sheet's life.
 * Swift only hands it back to [sportDetailsViewController].
 */
class SportDetailsRequest internal constructor(
    item: SportCommon,
    internal val page: SportPage,
    internal val busy: Boolean,
) {
    internal val item: SportCommonDetailsArgs = item.toDetailsArgs()
    internal val submission = SportDetailsSubmission()
}

/**
 * The sport tab's root (`AppRoutes.TabRoot(SPORT)`), LP-6's route as Android's `SportFragment` hosts it: `Мой спорт`
 * and `Запись` with their ViewModels in this controller's store. [tab] is the Swift host's: it carries a shared
 * lesson to the sign page and the details sheet's actions back to the page that opened the sheet. A booking or a lesson
 * asks the host for its details sheet through [openDetails] (a SwiftUI sheet, not a Compose one); a booking's
 * building opens in Apple Maps. iOS has no debug template lessons. The tab draws no top bar of its own, so the
 * content keeps clear of the status bar and the tab bar.
 */
fun sportViewController(tab: SportTabState, openDetails: (SportDetailsRequest) -> Unit): UIViewController {
    val koin = IosKoin.koin()
    val time = koin.get<AcademicTimeProvider>()
    val actions = koin.get<PlatformActions>()
    val host = SportHostActions(
        onOpenBooking = { booking -> openDetails(SportDetailsRequest(booking, SportPage.MY, busy = false)) },
        onOpenLesson = { lesson, busy -> openDetails(SportDetailsRequest(lesson, SportPage.SIGN, busy)) },
        onOpenMap = { booking -> actions.openBuilding(booking) },
    )
    return screenController {
        Box(
            Modifier
                .fillMaxSize()
                .background(ItmoTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            SportRoute(
                time = time,
                host = host,
                sharedLessons = tab.sharedLessonFlow,
                mySheetActions = tab.mySheetActionFlow,
                signSheetActions = tab.signSheetActionFlow,
            )
        }
    }
}

/**
 * The content of the sport details sheet for [request], as Android's `SportCommonDetailsBottomSheet` draws it; the
 * Swift host presents it in a SwiftUI sheet, which owns the drag indicator and the detents. The booking action goes
 * to [tab] for the page that opened the sheet, which then closes ([onClose]); the share action sends the lesson's
 * link ([SportShares]), the map opens Apple Maps, a teacher or a friend closes the sheet and opens the profile through
 * [open]. The content paints its own grouped background and keeps clear of the home indicator, where the
 * transparent controller shows the sheet's presentation background; its top is the sheet's edge.
 */
fun sportDetailsViewController(
    tab: SportTabState,
    request: SportDetailsRequest,
    open: (AppRoute) -> Unit,
    onClose: () -> Unit,
): UIViewController {
    val koin = IosKoin.koin()
    val time = koin.get<AcademicTimeProvider>()
    val shares = koin.get<SportShares>()
    val actions = koin.get<PlatformActions>()
    return screenController(opaque = false) {
        val scope = rememberCoroutineScope()
        val sheetActions = remember(scope) {
            SportDetailsActions(
                onAction = { action ->
                    tab.deliver(request, action)
                    onClose()
                },
                onShare = { target -> scope.launch { shares.lesson(request.item, target) } },
                onMap = { address -> actions.openMap(MapDestination(label = address, address = address)) },
                onProfile = { isu ->
                    onClose()
                    open(AppRoutes.UserProfile(isu))
                },
                onClose = onClose,
            )
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(SheetInsets)) {
            SportDetailsSheet(
                item = request.item,
                time = time,
                submission = request.submission,
                actions = sheetActions,
                modifier = Modifier.fillMaxSize(),
                busy = request.busy,
            )
        }
    }
}

/**
 * Another user's sport (`AppRoutes.UserSport`), as `UserSportFragment` hosts it: read-only cards whose building opens
 * in Apple Maps. [name] is the profile's display name for the title.
 */
fun userSportViewController(isu: Int, name: String, onBack: () -> Unit): UIViewController {
    val koin = IosKoin.koin()
    val time = koin.get<AcademicTimeProvider>()
    val actions = koin.get<PlatformActions>()
    return screenController {
        Box(
            Modifier
                .fillMaxSize()
                .background(ItmoTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            UserSportIosRoute(isu, name, time, onBack = onBack, onOpenMap = { actions.openBuilding(it) })
        }
    }
}

/** A sheet's own top is under the sheet's edge, so only the sides and the home indicator are padded. */
private val SheetInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

/** A booking's building from its room, as Android's `geo:` query: nothing opens when the room names none. */
private fun PlatformActions.openBuilding(booking: SportBooking) {
    val address = booking.extractBuildingAddress() ?: return
    openMap(MapDestination(label = address, address = address))
}
