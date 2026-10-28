package dev.alllexey.itmowidgets.feature.messages.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessagesEvent
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessagesViewModel

/**
 * The messages screen over its view model. Back closes an opened message first; links go to the system through the
 * host, a downloaded attachment to any app that views its type (`ACTION_VIEW` with a one-off read grant). [onSignIn]
 * is `Войти через ITMO.ID`; the shell calls [IsuMessagesViewModel.signedIn] after a completed one.
 */
@Composable
fun IsuMessagesRoute(
    viewModel: IsuMessagesViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val platform = LocalPlatformActions.current
    val snackbars = remember { SnackbarHostState() }
    BackHandler(enabled = state.opened != null, onBack = viewModel::close)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is IsuMessagesEvent.OpenFile -> {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(event.uri), event.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.onOpenFailed(file = true)
                    }
                }
                is IsuMessagesEvent.Snackbar -> snackbars.showSnackbar(event.text.resolve())
            }
        }
    }
    IsuMessagesScreen(
        state = state,
        actions = IsuMessagesActions(
            onBack = onBack,
            onFolder = viewModel::selectFolder,
            onRefresh = { viewModel.refresh() },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onSignIn = onSignIn,
            onMore = viewModel::loadMore,
            onOpen = viewModel::open,
            onClose = viewModel::close,
            onAttachment = viewModel::openAttachment,
            onLink = { url -> if (!platform.openLink(url)) viewModel.onOpenFailed(file = false) },
        ),
        modifier = modifier,
        snackbarHostState = snackbars,
    )
}
