package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignCommand
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignEvent
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.shared.core.common_partial_load_error
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_unavailable
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/** Everything the `Запись` screen asks of its route: the filters, the week strip, the lessons and the list states. */
@Immutable
data class SportSignActions(
    val filters: SportSignFilterActions = SportSignFilterActions(),
    val onPreviousWeek: () -> Unit = {},
    val onNextWeek: () -> Unit = {},
    val onSelectDate: (LocalDate) -> Unit = {},
    val lessons: SportLessonActions = SportLessonActions(),
    /** A pull: the indicator shows while it runs. */
    val onRefresh: () -> Unit = {},
    /** The error state's button. */
    val onRetry: () -> Unit = {},
    /** A confirmed auto-sign dialog with its force-sign switch. */
    val onExecute: (command: SportSignCommand, forceSign: Boolean) -> Unit = { _, _ -> },
)

object SportSignScreenTestTags {
    const val SCREEN = "sport_sign_screen"
}

/**
 * The `Запись` page of the sport tab: one list with the filters, the week strip and the day's lessons (or their
 * placeholder, empty or error state), under pull-to-refresh, with the screen's dialogs and snackbars on top.
 *
 * The header needs a [SportSignUiState.Content]; before the first one and behind an error the screen keeps the last
 * header it showed, as the View header did. Only a refresh the user asked for shows the indicator.
 */
@Composable
fun SportSignScreen(
    state: SportSignUiState,
    time: AcademicTimeProvider,
    actions: SportSignActions,
    modifier: Modifier = Modifier,
    dialogState: SportSignDialogState = rememberSportSignDialogState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val header = rememberHeader(state)
    Box(
        modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface)
            .testTag(SportSignScreenTestTags.SCREEN),
    ) {
        AppRefreshBox(
            refreshing = (state as? SportSignUiState.Content)?.refreshing == true,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            SportLessonList(
                state = state.lessonListState(),
                time = time,
                actions = actions.lessons,
                onRetry = actions.onRetry,
                modifier = Modifier.fillMaxSize(),
                listState = listState,
            ) {
                if (header != null) {
                    item(key = FILTERS_KEY, contentType = FILTERS_KEY) {
                        // The outlined fields' label notch needs room above the first one, as `TextInputLayout` kept.
                        SportSignFilters(header, actions.filters, Modifier.padding(top = ItmoTheme.spacing.related))
                    }
                    item(key = WEEK_KEY, contentType = WEEK_KEY) {
                        SportWeekStrip(header, actions.onPreviousWeek, actions.onNextWeek, actions.onSelectDate)
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    SportSignDialogs(dialogState, actions.onExecute)
}

/** The header of the last content: loading and error states keep it, as the View header kept its last bind. */
@Composable
private fun rememberHeader(state: SportSignUiState): SportSignUiState.Content? {
    val kept = remember { HeaderHolder() }
    if (state is SportSignUiState.Content) kept.content = state
    return kept.content
}

private class HeaderHolder {
    var content: SportSignUiState.Content? = null
}

/**
 * The `Запись` page with its ViewModel. Events are handled only while the page is resumed, so the page behind the
 * `Мой спорт` tab keeps them for later: success and error messages and a lesson that is no longer bookable go to a
 * snackbar (a newer one replaces it), dialogs open, and a shared lesson opens through [onOpenLesson] with whether a
 * request for it is in flight. A partial failure shows a snackbar with a retry once per resume.
 *
 * [sheetActions] are the actions the lesson details sheet asked for; each one is checked again here
 * ([SportSheetAction.outcome]). A debug template lesson (negative id) never reaches the ViewModel: the action calls
 * [onTemplateLesson] instead.
 */
@Composable
fun SportSignRoute(
    time: AcademicTimeProvider,
    onOpenLesson: (lesson: SportLesson, busy: Boolean) -> Unit,
    onTemplateLesson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SportSignViewModel = koinViewModel(),
    sheetActions: Flow<SportSheetAction> = emptyFlow(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogs = rememberSportSignDialogState()
    val snackbars = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val openLesson by rememberUpdatedState(onOpenLesson)
    val templateLesson by rememberUpdatedState(onTemplateLesson)
    val runAction: (SportLesson, SportBookingAction) -> Unit = { lesson, action ->
        viewModel.runBookingAction(lesson, action, templateLesson)
    }

    LaunchedEffect(viewModel, lifecycle, sheetActions) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            coroutineScope {
                val feedback = SportSignFeedback(this, snackbars) { viewModel.refresh(RefreshMode.Force) }
                launch { feedback.followPartialErrors(viewModel.uiState) }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SportSignEvent.ShowToast -> feedback.show { showSportSignMessage(event) }
                            is SportSignEvent.ShowError -> feedback.show {
                                showSnackbar(getString(event.error.textResource()), duration = SnackbarDuration.Long)
                            }
                            is SportSignEvent.OpenLessonDetails ->
                                openLesson(event.lesson, viewModel.uiState.value.isBusy(event.lesson.lessonId))
                            else -> dialogs.show(event)
                        }
                    }
                }
                launch {
                    sheetActions.collect { request ->
                        when (val outcome = request.outcome(viewModel.uiState.value, viewModel::linkedLesson, time.now())) {
                            SportSheetOutcome.Ignored -> Unit
                            SportSheetOutcome.Stale -> feedback.show {
                                showSnackbar(getString(Res.string.sport_lesson_unavailable), duration = SnackbarDuration.Long)
                            }
                            is SportSheetOutcome.Run -> runAction(outcome.lesson, outcome.action)
                        }
                    }
                }
            }
        }
    }

    val actions = remember(viewModel) {
        SportSignActions(
            filters = SportSignFilterActions(
                onSelectSports = viewModel::selectSports,
                onSelectBuilding = viewModel::selectBuilding,
                onSelectTeacher = viewModel::selectTeacher,
                onSelectTime = viewModel::selectTime,
                onShowOnlyAvailable = viewModel::showOnlyAvailable,
                onShowAutoSign = viewModel::showAutoSign,
                onShowOnlyFriends = viewModel::showOnlyFriends,
                onReset = viewModel::resetFilters,
            ),
            onPreviousWeek = viewModel::prevWeek,
            onNextWeek = viewModel::nextWeek,
            onSelectDate = viewModel::selectDate,
            lessons = SportLessonActions(
                onOpen = { lesson -> openLesson(lesson, viewModel.uiState.value.isBusy(lesson.lessonId)) },
                onAction = runAction,
            ),
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onExecute = viewModel::executeAutoSignCommand,
        )
    }
    SportSignScreen(state, time, actions, modifier, dialogs, snackbars)
}

/** A booking action the lesson details sheet asked for: the lesson and the [SportBookingAction] name it offered. */
data class SportSheetAction(val lessonId: Long, val action: String?)

/** What a [SportSheetAction] does once it reaches the page. */
sealed interface SportSheetOutcome {

    /** The lesson is gone or has a request in flight: nothing happens. */
    data object Ignored : SportSheetOutcome

    /** The offer changed while the sheet was open (the lesson started, the free queue closed): `Недоступно`. */
    data object Stale : SportSheetOutcome

    data class Run(val lesson: SportLesson, val action: SportBookingAction) : SportSheetOutcome
}

/**
 * Checks this request against the page: the lesson is taken from the shown lessons or, for a lesson a link opened
 * that the filters hide, from [linkedLesson]; a busy lesson ignores it, and the action runs only when the lesson
 * still offers it at [now].
 */
fun SportSheetAction.outcome(
    state: SportSignUiState,
    linkedLesson: (Long) -> SportLesson?,
    now: Instant,
): SportSheetOutcome {
    val content = state as? SportSignUiState.Content
    val lesson = content?.displayedLessons?.firstOrNull { it.lessonId == lessonId }
        ?: linkedLesson(lessonId)
        ?: return SportSheetOutcome.Ignored
    if (state.isBusy(lessonId)) return SportSheetOutcome.Ignored
    val offered = lesson.bookingConditions().evaluate(now).action
    if (offered.name != action) return SportSheetOutcome.Stale
    return if (offered == SportBookingAction.NONE) SportSheetOutcome.Ignored else SportSheetOutcome.Run(lesson, offered)
}

/**
 * Sends [action] on [lesson] to the ViewModel. A debug template lesson (negative id, `SportLessonTemplateProvider`)
 * is local only: [onTemplateLesson] runs instead and nothing reaches the network.
 */
fun SportSignViewModel.runBookingAction(lesson: SportLesson, action: SportBookingAction, onTemplateLesson: () -> Unit) {
    if (action == SportBookingAction.NONE) return
    if (lesson.lessonId < 0) {
        onTemplateLesson()
        return
    }
    when (action) {
        SportBookingAction.SIGN -> signUpForLesson(lesson)
        SportBookingAction.CANCEL -> unSignForLesson(lesson)
        SportBookingAction.AUTO, SportBookingAction.CANCEL_AUTO -> handleAutoSignClick(lesson)
        SportBookingAction.NONE -> Unit
    }
}

private fun SportSignUiState.isBusy(lessonId: Long): Boolean =
    (this as? SportSignUiState.Content)?.busyLessonIds?.contains(lessonId) == true

/** One snackbar at a time: a newer message replaces the one on screen, and leaving the page dismisses it. */
private class SportSignFeedback(
    private val scope: CoroutineScope,
    private val host: SnackbarHostState,
    private val onRetry: () -> Unit,
) {
    private var job: Job? = null

    fun show(message: suspend SnackbarHostState.() -> Unit) {
        job?.cancel()
        job = scope.launch { host.message() }
    }

    fun dismiss() {
        job?.cancel()
        job = null
    }

    /** A partial failure shows its snackbar once until it clears; clearing dismisses whatever is shown. */
    suspend fun followPartialErrors(states: Flow<SportSignUiState>) {
        var shown = false
        states.map { (it as? SportSignUiState.Content)?.hasPartialError == true }
            .distinctUntilChanged()
            .collect { partial ->
                if (partial == shown) return@collect
                shown = partial
                dismiss()
                if (partial) {
                    show {
                        val result = showSnackbar(
                            message = getString(CoreRes.string.common_partial_load_error),
                            actionLabel = getString(CoreRes.string.common_retry),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) onRetry()
                    }
                }
            }
    }
}

private const val FILTERS_KEY = "sport_sign_filters"
private const val WEEK_KEY = "sport_sign_week"
