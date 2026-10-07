package dev.alllexey.itmowidgets.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthEvent
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.demo_entered
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The sign-in screen with its Koin ViewModel. [onSignInWithItmoId] opens the host's ITMO.ID page after the last error
 * is cleared; [onDemoStarted] gets `demo_entered` («Демо-режим») to confirm the hidden demo entry the way the
 * platform does (Android: a haptic and a toast). The demo session replaces this screen right after.
 */
@Composable
fun AuthRoute(
    onSignInWithItmoId: () -> Unit,
    onDemoStarted: (message: String) -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val demoEntered = stringResource(Res.string.demo_entered)
    val currentOnDemoStarted by rememberUpdatedState(onDemoStarted)
    val currentDemoEntered by rememberUpdatedState(demoEntered)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    AuthEvent.DemoStarted -> currentOnDemoStarted(currentDemoEntered)
                }
            }
        }
    }
    AuthScreen(
        state = state,
        onLogoTap = viewModel::onLogoTap,
        onSignInWithItmoId = {
            viewModel.clearError()
            onSignInWithItmoId()
        },
        onSignInWithToken = viewModel::signInWithRefreshToken,
    )
}
