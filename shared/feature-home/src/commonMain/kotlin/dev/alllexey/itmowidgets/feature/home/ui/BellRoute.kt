package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.home.presentation.BellEvent
import dev.alllexey.itmowidgets.feature.home.presentation.BellViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The bell's sheet with its Koin ViewModel, one per opening. A row's home opens through [onOpen] (the host closes the
 * sheet first); a failed answer to a request goes to [onAnswerFailed].
 */
@Composable
fun BellRoute(
    onOpen: (EntryRoute) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    onAnswerFailed: (AppError) -> Unit = {},
    viewModel: BellViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BellEvent.Open -> onOpen(event.route)
                is BellEvent.AnswerFailed -> onAnswerFailed(event.error)
            }
        }
    }
    val actions = remember(viewModel, onSettings, onClose) {
        BellActions(
            onOpen = viewModel::open,
            onAnswer = viewModel::answer,
            onSettings = onSettings,
            onClose = onClose,
        )
    }
    BellSheetContent(state, actions)
}
