package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresActions
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresSheet
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectExits
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectRoute
import kotlinx.coroutines.flow.Flow
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What the iOS shell hands a hosted recordbook page beside its route, as Android's results do. */
sealed interface RecordbookIosRequest {
    /** The period picker's answer to the tab root. */
    data class SelectPeriod(val programId: Long, val semester: Int) : RecordbookIosRequest

    /** A completed BARS sign-in: the page loads again, as after `BarsLoginActivity`'s OK. */
    data object BarsSignedIn : RecordbookIosRequest
}

/**
 * The recordbook tab's root as the iOS shell hosts it, as `RecordbookTabRoot` does on Android: [RecordbookRoute] with
 * the tab's ViewModel in the controller's store, and [requests] for the picked period and a completed BARS sign-in.
 */
@Composable
fun RecordbookIosRoute(
    requests: Flow<RecordbookIosRequest>,
    onOpenPeriods: (List<RecordbookProgram>, RecordbookSelection) -> Unit,
    onOpenSubject: (RecordbookSelection, RecordbookSubject) -> Unit,
    onBarsLogin: () -> Unit,
) {
    val viewModel: RecordbookViewModel = koinViewModel()
    LaunchedEffect(viewModel, requests) {
        requests.collect { request ->
            when (request) {
                is RecordbookIosRequest.SelectPeriod -> viewModel.selectPeriod(request.programId, request.semester)
                RecordbookIosRequest.BarsSignedIn -> viewModel.refresh(RefreshMode.Force)
            }
        }
    }
    RecordbookRoute(onOpenPeriods, onOpenSubject, onBarsLogin, viewModel)
}

/**
 * The subject page as the iOS shell hosts it, as `RecordbookSubjectEntry` does on Android: [RecordbookSubjectRoute]
 * with its ViewModel reading [args] from a `SavedStateHandle`, a completed BARS sign-in from [requests] (it loads the
 * page again), and the links only when [linksEnabled] (`PlatformCapabilities.reviews`).
 */
@Composable
fun RecordbookSubjectIosRoute(
    args: RecordbookSubjectArgs,
    requests: Flow<RecordbookIosRequest>,
    exits: RecordbookSubjectExits,
    linksEnabled: Boolean,
) {
    val viewModel = hostedViewModel<RecordbookSubjectViewModel>(*args.handleEntries())
    LaunchedEffect(viewModel, requests) {
        requests.collect { request ->
            if (request == RecordbookIosRequest.BarsSignedIn) viewModel.refresh(RefreshMode.Force)
        }
    }
    RecordbookSubjectRoute(args.semester, exits, viewModel, linksEnabled)
}

/**
 * `Мои баллы` as the iOS shell hosts it, as `SheetScoresEntry` does on Android: the sheet closes itself through
 * [onClose] once the ViewModel is done, and a failed save goes to [onSaveFailed] as its text (Android's snackbar).
 */
@Composable
fun SheetScoresIosRoute(args: SheetScoresArgs, onClose: () -> Unit, onSaveFailed: (String) -> Unit) {
    val viewModel = hostedViewModel<SheetScoresViewModel>(*args.handleEntries())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnSaveFailed by rememberUpdatedState(onSaveFailed)
    LaunchedEffect(state) {
        if (state == SheetScoresUiState.Done) currentOnClose()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SheetScoresEvent.SaveFailed -> currentOnSaveFailed(event.text.resolve())
            }
        }
    }
    val actions = remember(viewModel) {
        SheetScoresActions(
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onPickRow = viewModel::pickRow,
            onPickTab = viewModel::pickTab,
            onPickTotal = viewModel::pickTotal,
        )
    }
    SheetScoresSheet(viewModel.scope.subjectName, state, actions, Modifier.fillMaxWidth())
}

/** Android's `RecordbookSubjectArgs.toBundle()`: the BARS journal only when it is there. */
internal fun RecordbookSubjectArgs.handleEntries(): Array<Pair<String, Any>> = listOfNotNull(
    RecordbookSubjectArgs.ENTRY_ID to entryId,
    RecordbookSubjectArgs.PROGRAM_ID to programId,
    RecordbookSubjectArgs.SEMESTER to semester,
    RecordbookSubjectArgs.STUDY_YEAR_KEY to studyYear,
    barsPlan?.let { RecordbookSubjectArgs.BARS_PLAN to it },
    barsType?.let { RecordbookSubjectArgs.BARS_TYPE to it },
    barsIdentifier?.let { RecordbookSubjectArgs.BARS_IDENTIFIER to it },
).toTypedArray()

/** Android's `SheetScoresArgs.toBundle()`. */
internal fun SheetScoresArgs.handleEntries(): Array<Pair<String, Any>> = arrayOf(
    SheetScoresArgs.SUBJECT_ID to subjectId,
    SheetScoresArgs.SUBJECT_NAME to subjectName,
    SheetScoresArgs.PERIOD_KEY to periodKey,
    SheetScoresArgs.URL to url,
    SheetScoresArgs.STEP to step.name,
)

/**
 * Koin's definition of [VM] in the hosting controller's store with [arguments] as its `SavedStateHandle`, as Android's
 * shell seeds an entry's handle from its key. `koinViewModel()` would hand the definition the store's own empty
 * handle instead, since a Compose controller on iOS has no destination arguments.
 */
@Composable
private inline fun <reified VM : ViewModel> hostedViewModel(vararg arguments: Pair<String, Any>): VM {
    val koin = getKoin()
    val handle = remember { SavedStateHandle(mapOf(*arguments)) }
    return viewModel { koin.get<VM> { parametersOf(handle) } }
}
