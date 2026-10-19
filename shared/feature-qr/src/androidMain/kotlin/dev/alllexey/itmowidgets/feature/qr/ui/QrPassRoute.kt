package dev.alllexey.itmowidgets.feature.qr.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassEvent
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * The pass sheet with its Koin ViewModel: requests run while the sheet is started, and a failed refresh that keeps the
 * code shows a snackbar with a retry. Android hosts it in the shell's `QrPass` sheet; iOS draws the same view model in
 * SwiftUI (`QrPassSheet`).
 */
@Composable
fun QrPassRoute(onClose: () -> Unit, viewModel: QrPassViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is QrPassEvent.RefreshFailed -> {
                        val result = snackbars.showSnackbar(
                            message = event.error.toUiText().resolve(),
                            actionLabel = getString(Res.string.common_retry),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.refresh(RefreshMode.Force)
                    }
                }
            }
        }
    }

    QrPassSheetContent(
        state = state,
        onRefresh = { viewModel.refresh(RefreshMode.Force) },
        onClose = onClose,
        snackbarHostState = snackbars,
    )
}
