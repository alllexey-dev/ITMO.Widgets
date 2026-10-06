package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The schedule changes history with its Koin ViewModel. The screen counts as visible while its lifecycle is started,
 * so opening it marks every change read and a screen stopped under another one marks nothing. Android hosts it in
 * `ScheduleChangesFragment`, iOS in IO-09b.
 */
@Composable
fun ScheduleChangesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleChangesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.setVisible(true)
        onStopOrDispose { viewModel.setVisible(false) }
    }

    ScheduleChangesScreen(state = state, onBack = onBack, modifier = modifier)
}
