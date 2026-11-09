package dev.alllexey.itmowidgets.feature.finances.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionViewModel
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionWeb
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.coroutines.launch

/**
 * The tuition screen over its view model; `Оплатить` opens my.itmo's tuition page in the system browser (a snackbar
 * when none can).
 */
@Composable
fun TuitionRoute(viewModel: TuitionViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val platform = LocalPlatformActions.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    TuitionScreen(
        state = state,
        actions = TuitionActions(
            onBack = onBack,
            onRefresh = { viewModel.refresh() },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onPay = {
                if (!platform.openLink(TuitionWeb.PAY_URL)) {
                    scope.launch { snackbars.showSnackbar(UiText.Res(CoreStrings.contract_pay_unavailable).resolve()) }
                }
            },
            onTogglePastYears = viewModel::togglePastYears,
        ),
        modifier = modifier,
        snackbarHostState = snackbars,
    )
}
