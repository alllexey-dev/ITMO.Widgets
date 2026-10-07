package dev.alllexey.itmowidgets.feature.sport.ui.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Another user's sport with its Koin ViewModel, which reads the user's ISU and name from the destination's arguments.
 * Android hosts it in `UserSportFragment` (overlay `user_sport`); the iOS shell hosts the same route.
 */
@Composable
fun UserSportRoute(
    time: AcademicTimeProvider,
    onBack: () -> Unit,
    onOpenMap: (SportBooking) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserSportViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenMap by rememberUpdatedState(onOpenMap)
    val actions = remember(viewModel) {
        UserSportActions(
            onBack = { currentOnBack() },
            onRefresh = { viewModel.refresh(RefreshMode.Pull) },
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onOpenMap = { currentOnOpenMap(it) },
        )
    }
    UserSportScreen(state, viewModel.name, time, actions, modifier)
}
