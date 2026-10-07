package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportMyActions
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportMyScreen
import dev.alllexey.itmowidgets.feature.sport.ui.my.cancelCandidate
import dev.alllexey.itmowidgets.feature.sport.ui.my.showSportMyEvent
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSheetAction
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSignRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** A shared lesson link for the sport tab: a real lesson, or the prediction made from [lessonId]'s lesson. */
data class SportSharedLesson(val lessonId: Long, val predicted: Boolean = false)

/**
 * What only the platform host does for the sport tab: the details sheet of a booking or of a lesson (with whether a
 * request for it is in flight), the map of a booking's building, and the message for a debug template lesson.
 */
@Immutable
class SportHostActions(
    val onOpenBooking: (SportBooking) -> Unit = {},
    val onOpenLesson: (lesson: SportLesson, busy: Boolean) -> Unit = { _, _ -> },
    val onOpenMap: (SportBooking) -> Unit = {},
    val onTemplateLesson: () -> Unit = {},
)

/**
 * The sport tab with its two ViewModels, obtained from the host's `ViewModelStoreOwner`: a Fragment's, a Nav3 entry's
 * or the SwiftUI host's, so the pages keep their week, filters and data for as long as the host keeps its store.
 *
 * Each page runs on a lifecycle of its own that stays `STARTED` while the other page is in front, as the View pager
 * kept its neighbour: a page shows its messages and dialogs only while it is the current one, and the page behind
 * keeps them for later. A link from [sharedLessons] selects `Запись` at once and opens the lesson there, even when the
 * filters hide it. [mySheetActions] and [signSheetActions] carry the details sheet's actions back to the page that
 * opened the sheet; each page checks them against what it shows now.
 */
@Composable
fun SportRoute(
    time: AcademicTimeProvider,
    host: SportHostActions,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberSportPagerState(),
    sharedLessons: Flow<SportSharedLesson> = emptyFlow(),
    mySheetActions: Flow<SportSheetAction> = emptyFlow(),
    signSheetActions: Flow<SportSheetAction> = emptyFlow(),
    myViewModel: SportMyViewModel = koinViewModel(),
    signViewModel: SportSignViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, signViewModel, sharedLessons) {
        sharedLessons.collect { link ->
            pagerState.scrollToPage(SportPage.SIGN.ordinal)
            signViewModel.openSharedLesson(link.lessonId, link.predicted)
        }
    }
    SportScreen(pagerState, modifier) { page ->
        PageLifecycle(active = pagerState.currentPage == page.ordinal) {
            when (page) {
                SportPage.MY -> SportMyPage(
                    viewModel = myViewModel,
                    time = time,
                    host = host,
                    sheetActions = mySheetActions,
                    onOpenSign = { scope.launch { pagerState.animateScrollToPage(SportPage.SIGN.ordinal) } },
                )
                SportPage.SIGN -> SportSignRoute(
                    time = time,
                    onOpenLesson = host.onOpenLesson,
                    onTemplateLesson = host.onTemplateLesson,
                    viewModel = signViewModel,
                    sheetActions = signSheetActions,
                )
            }
        }
    }
}

/**
 * `Мой спорт` on its ViewModel: loads once (a page that comes back with content starts no request), reports a failed
 * cancellation only while the page is resumed, and asks for the confirmation of a cancellation the details sheet
 * offered, while the booking still offers it.
 */
@Composable
private fun SportMyPage(
    viewModel: SportMyViewModel,
    time: AcademicTimeProvider,
    host: SportHostActions,
    sheetActions: Flow<SportSheetAction>,
    onOpenSign: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var pendingCancel by remember { mutableStateOf<SportBooking?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentHost by rememberUpdatedState(host)
    val currentOnOpenSign by rememberUpdatedState(onOpenSign)

    LaunchedEffect(viewModel) { viewModel.ensureDataLoaded() }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { snackbars.showSportMyEvent(it) }
        }
    }
    LaunchedEffect(viewModel, sheetActions) {
        sheetActions.collect { request ->
            viewModel.uiState.value.cancelCandidate(request.lessonId, request.action, time.now())
                ?.let { pendingCancel = it }
        }
    }

    val actions = remember(viewModel) {
        SportMyActions(
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onOpenSign = { currentOnOpenSign() },
            onOpenDetails = { currentHost.onOpenBooking(it) },
            onOpenMap = { currentHost.onOpenMap(it) },
            onRequestCancel = { pendingCancel = it },
            onConfirmCancel = { booking ->
                pendingCancel = null
                viewModel.cancelBooking(booking)
            },
            onDismissCancel = { pendingCancel = null },
        )
    }
    SportMyScreen(
        state = state,
        time = time,
        actions = actions,
        pendingCancel = pendingCancel,
        snackbarHostState = snackbars,
    )
}

/**
 * Gives [content] a lifecycle that follows the host's but stops at `STARTED` while the page is not [active], the
 * maximum lifecycle a View pager gave the page it was not showing.
 */
@Composable
private fun PageLifecycle(active: Boolean, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current.lifecycle
    val owner = remember(parent) { PageLifecycleOwner(parent) }
    DisposableEffect(owner, active) {
        owner.active = active
        onDispose {}
    }
    DisposableEffect(owner) {
        owner.attach()
        onDispose { owner.detach() }
    }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

private class PageLifecycleOwner(private val parent: Lifecycle) : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    var active: Boolean = false
        set(value) {
            field = value
            sync()
        }

    private val observer = LifecycleEventObserver { _, _ -> sync() }

    fun attach() = parent.addObserver(observer)

    fun detach() {
        parent.removeObserver(observer)
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }

    private fun sync() {
        val host = parent.currentState
        val target = if (!active && host == Lifecycle.State.RESUMED) Lifecycle.State.STARTED else host
        // A registry that never got past INITIALIZED cannot move to DESTROYED; it has nothing to tear down either.
        if (target == Lifecycle.State.DESTROYED && registry.currentState == Lifecycle.State.INITIALIZED) return
        registry.currentState = target
    }
}
