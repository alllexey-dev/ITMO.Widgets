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
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresActions
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresSheet
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

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
