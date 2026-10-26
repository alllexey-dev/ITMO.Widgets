package dev.alllexey.itmowidgets.feature.map.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapUiState
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapViewModel

/** The campus map over its view model; «Открыть в Картах» (a building without a plan, «По городу») goes to the system map app through the host. */
@Composable
fun CampusMapRoute(viewModel: CampusMapViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val platform = LocalPlatformActions.current
    // Back closes the map's layers first (the room, the place's list, ...); with none left the shell pops the map.
    BackHandler(enabled = (state as? CampusMapUiState.Plan)?.canGoBack == true, onBack = viewModel::back)
    CampusMapScreen(
        state = state,
        actions = CampusMapActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onBuilding = viewModel::selectBuilding,
            onLevel = viewModel::selectLevel,
            onRoomTap = viewModel::selectRoom,
            onSearch = viewModel::search,
            onPlace = viewModel::selectPlace,
            onResult = viewModel::openResult,
            onRoute = viewModel::route,
            onRouteFrom = viewModel::routeFrom,
            onPick = viewModel::pick,
            onSwap = viewModel::swap,
            onFitBuilding = viewModel::fitBuilding,
            onStep = viewModel::step,
            onCloseTrip = viewModel::closeTrip,
            onCloseRoom = viewModel::closeRoom,
            onOpenMaps = { platform.openMap(it) },
        ),
        modifier = modifier,
        map = { scene, mapModifier -> IndoorMapView(scene, viewModel::selectRoom, mapModifier) },
    )
}
