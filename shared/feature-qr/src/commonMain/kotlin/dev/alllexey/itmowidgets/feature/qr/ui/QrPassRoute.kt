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
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeEvent
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.common_retry
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * The QR pass screen with its Koin ViewModel: requests run while the screen is started, and a failed refresh of a
 * still valid pass shows a snackbar with a retry. Android hosts it in `QrCodeFragment`, iOS in IO-21.
 */
@Composable
fun QrPassRoute(onBack: () -> Unit, viewModel: QrCodeViewModel = koinViewModel()) {
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
                    is QrCodeEvent.RefreshFailed -> {
                        val result = snackbars.showSnackbar(
                            message = getString(event.error.textResource()),
                            actionLabel = getString(Res.string.common_retry),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.refresh(RefreshMode.Force)
                    }
                }
            }
        }
    }

    QrPassScreen(
        state = state,
        onRefresh = { viewModel.refresh(RefreshMode.Force) },
        onBack = onBack,
        snackbarHostState = snackbars,
    )
}
