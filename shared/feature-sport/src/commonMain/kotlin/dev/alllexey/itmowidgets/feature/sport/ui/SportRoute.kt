package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.points.SportPointsViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsViewModel
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignEvent
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** A shared lesson link for the sport tab: a real lesson, or the prediction made from [lessonId]'s lesson. */
data class SportSharedLesson(val lessonId: Long, val predicted: Boolean = false)

/** A booking action the lesson sheet asked for: the lesson and the [SportBookingAction] name it offered. */
data class SportSheetAction(val lessonId: Long, val action: String?) {
    /** The action, null for a name this build does not know. */
    val bookingAction: SportBookingAction? get() = SportBookingAction.entries.firstOrNull { it.name == action }
}

/**
 * What only the platform host does for the sport screens: the lesson sheet of [SportCommonDetailsArgs] (with the part
 * that opened it, to send the sheet's action back, and whether a request for it is in flight), the pushed «Мои
 * секции» and «Откуда баллы».
 */
data class SportHostActions(
    val onOpenLesson: (details: SportCommonDetailsArgs, from: SportTab, busy: Boolean) -> Unit = { _, _, _ -> },
    val onOpenSections: () -> Unit = {},
    val onOpenPoints: () -> Unit = {},
)

/**
 * The Спорт tab on its two ViewModels. «Моё» loads once; a link from [sharedLessons] selects «Запись» and opens the
 * lesson's sheet there. [mySheetActions] and [signSheetActions] carry the lesson sheet's action back to the part that
 * opened it, which checks it against what the lesson offers now. Messages show only while the tab is resumed.
 */
@Composable
fun SportRoute(
    title: String,
    myViewModel: SportMyViewModel,
    signViewModel: SportSignViewModel,
    host: SportHostActions,
    modifier: Modifier = Modifier,
    sharedLessons: Flow<SportSharedLesson> = emptyFlow(),
    mySheetActions: Flow<SportSheetAction> = emptyFlow(),
    signSheetActions: Flow<SportSheetAction> = emptyFlow(),
) {
    val my by myViewModel.uiState.collectAsStateWithLifecycle()
    val sign by signViewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(SportTab.MY) }
    val snackbars = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentHost by rememberUpdatedState(host)

    LaunchedEffect(myViewModel) { myViewModel.ensureDataLoaded() }
    LaunchedEffect(signViewModel, sharedLessons) {
        sharedLessons.collect { link ->
            tab = SportTab.SIGN
            signViewModel.openSharedLesson(link.lessonId, link.predicted)
        }
    }
    LaunchedEffect(myViewModel, mySheetActions) {
        mySheetActions.collect { request -> request.bookingAction?.let { myViewModel.onSheetAction(request.lessonId, it) } }
    }
    LaunchedEffect(signViewModel, signSheetActions) {
        signSheetActions.collect { request -> request.bookingAction?.let { signViewModel.onSheetAction(request.lessonId, it) } }
    }
    LaunchedEffect(lifecycle, myViewModel, signViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch { myViewModel.events.collect { snackbars.showSnackbar(it.resolve(), duration = SnackbarDuration.Long) } }
            signViewModel.events.collect { event ->
                when (event) {
                    is SportSignEvent.Notice -> launch { snackbars.showSnackbar(event.text.resolve(), duration = SnackbarDuration.Long) }
                    is SportSignEvent.OpenLesson -> signViewModel.details(event.lessonId)?.let {
                        currentHost.onOpenLesson(it, SportTab.SIGN, signViewModel.isBusy(event.lessonId))
                    }
                }
            }
        }
    }

    val myActions = remember(myViewModel) {
        SportMyActions(
            onRefresh = { myViewModel.refresh(RefreshMode.Pull) },
            onRetry = { myViewModel.refresh(RefreshMode.Force) },
            onOpenPoints = { currentHost.onOpenPoints() },
            onOpenSections = { currentHost.onOpenSections() },
            onOpenBooking = { id -> myViewModel.details(id)?.let { currentHost.onOpenLesson(it, SportTab.MY, false) } },
            onFindLesson = { tab = SportTab.SIGN },
            onConfirmCancel = myViewModel::confirmCancel,
            onDismissCancel = myViewModel::dismissCancel,
        )
    }
    val signActions = remember(signViewModel) {
        SportSignActions(
            onRefresh = { signViewModel.refresh(RefreshMode.Pull) },
            onRetry = { signViewModel.refresh(RefreshMode.Force) },
            onQuery = signViewModel::setQuery,
            onChip = signViewModel::toggle,
            onDay = signViewModel::selectDate,
            onWeek = signViewModel::showWeek,
            onOpenFilters = signViewModel::openFilters,
            onEmptyAction = signViewModel::onEmptyAction,
            onResetFilters = signViewModel::resetFilters,
            onOpenLesson = { id ->
                signViewModel.details(id)?.let { currentHost.onOpenLesson(it, SportTab.SIGN, signViewModel.isBusy(id)) }
            },
            onRowAction = signViewModel::act,
            filters = SportFiltersActions(
                onClose = signViewModel::closeFilters,
                onSection = signViewModel::toggleSection,
                onBuilding = signViewModel::selectBuilding,
                onTeacher = signViewModel::selectTeacher,
                onDayPart = signViewModel::selectDayPart,
                onOnlyFree = signViewModel::setOnlyFree,
                onOnlyPinned = signViewModel::setOnlyPinned,
                onOnlyFriends = signViewModel::setOnlyFriends,
                onReset = signViewModel::resetFilters,
            ),
            prompt = SportPromptActions(onConfirm = signViewModel::confirmPrompt, onDismiss = signViewModel::dismissPrompt),
        )
    }
    SportScreen(title, tab, { tab = it }, my, sign, myActions, signActions, modifier, snackbars)
}

/** «Мои секции» on its ViewModel; a lesson's sheet opens through [onOpenLesson], its action comes back in [sheetActions]. */
@Composable
fun SportSectionsRoute(
    viewModel: SportSectionsViewModel,
    onBack: () -> Unit,
    onOpenLesson: (details: SportCommonDetailsArgs, busy: Boolean) -> Unit,
    onOpenBookings: () -> Unit,
    modifier: Modifier = Modifier,
    sheetActions: Flow<SportSheetAction> = emptyFlow(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, sheetActions) {
        sheetActions.collect { request -> request.bookingAction?.let { viewModel.onSheetAction(request.lessonId, it) } }
    }
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.events.collect { launch { snackbars.showSnackbar(it.resolve(), duration = SnackbarDuration.Long) } }
        }
    }
    val open by rememberUpdatedState(onOpenLesson)
    val actions = remember(viewModel, onBack, onOpenBookings) {
        SportSectionsActions(
            onBack = onBack,
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onOpenPicker = viewModel::openPicker,
            onClosePicker = viewModel::closePicker,
            onTogglePinned = viewModel::togglePinned,
            onToggleRepeat = viewModel::toggleRepeat,
            onOpenLesson = { id -> viewModel.details(id)?.let { open(it, viewModel.isBusy(id)) } },
            onRowAction = viewModel::act,
            onOpenBookings = onOpenBookings,
            prompt = SportPromptActions(onConfirm = viewModel::confirmPrompt, onDismiss = viewModel::dismissPrompt),
        )
    }
    SportSectionsScreen(state, actions, modifier, snackbars)
}

/** «Откуда баллы» on its ViewModel. */
@Composable
fun SportPointsRoute(viewModel: SportPointsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack) {
        SportPointsActions(
            onBack = onBack,
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onFilter = viewModel::select,
        )
    }
    SportPointsScreen(state, actions, modifier)
}
