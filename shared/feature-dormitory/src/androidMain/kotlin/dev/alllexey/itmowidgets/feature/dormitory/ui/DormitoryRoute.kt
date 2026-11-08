package dev.alllexey.itmowidgets.feature.dormitory.ui

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
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryViewModel
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryWeb
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import kotlinx.coroutines.launch

/**
 * The dormitory screen over its view model. `На карте` opens the system map app (a snackbar when none can), links in
 * the instructions open outside the app, [onOpenMyItmo] the dormitory section of my.itmo in the app, `Оплатить` the same
 * section in the system browser (a snackbar when none can).
 */
@Composable
fun DormitoryRoute(
    viewModel: DormitoryViewModel,
    onBack: () -> Unit,
    onOpenMyItmo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val platform = LocalPlatformActions.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mapFailed: () -> Unit = {
        scope.launch { snackbars.showSnackbar(UiText.Res(DormitoryStrings.dormitory_map_unavailable).resolve()) }
    }
    DormitoryScreen(
        state = state,
        actions = DormitoryActions(
            onBack = onBack,
            onRefresh = { viewModel.refresh() },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onOpenMyItmo = onOpenMyItmo,
            onOpenMap = { destination -> if (!platform.openMap(destination)) mapFailed() },
            onLink = { url -> platform.openLink(url) },
            onPay = {
                if (!platform.openLink(DormitoryWeb.PAY_URL)) {
                    scope.launch { snackbars.showSnackbar(UiText.Res(CoreStrings.contract_pay_unavailable).resolve()) }
                }
            },
            onTogglePastYears = viewModel::togglePastYears,
        ),
        modifier = modifier,
        snackbarHostState = snackbars,
    )
}
