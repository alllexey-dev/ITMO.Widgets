package dev.alllexey.itmowidgets.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.ChipSpec
import dev.alllexey.itmowidgets.designsystem.kit.FilterChips
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.SearchField
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapUiState
import dev.alllexey.itmowidgets.feature.map.presentation.LevelChip
import dev.alllexey.itmowidgets.feature.map.presentation.MapScene
import dev.alllexey.itmowidgets.feature.map.presentation.Place
import dev.alllexey.itmowidgets.feature.map.presentation.RoomCard
import dev.alllexey.itmowidgets.feature.map.presentation.SearchResult
import dev.alllexey.itmowidgets.feature.map.presentation.SearchView
import dev.alllexey.itmowidgets.feature.map.presentation.TripView
import dev.alllexey.itmowidgets.shared.compose.strings.common_back
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_forward
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_directions
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_map
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.feature.map.Res
import dev.alllexey.itmowidgets.shared.feature.map.map_close
import dev.alllexey.itmowidgets.shared.feature.map.map_floors
import dev.alllexey.itmowidgets.shared.feature.map.map_no_plan_title
import dev.alllexey.itmowidgets.shared.feature.map.map_open_in_maps
import dev.alllexey.itmowidgets.shared.feature.map.map_route
import dev.alllexey.itmowidgets.shared.feature.map.map_route_next
import dev.alllexey.itmowidgets.shared.feature.map.map_route_previous
import dev.alllexey.itmowidgets.shared.feature.map.map_search_empty
import dev.alllexey.itmowidgets.shared.feature.map.map_search_hint
import dev.alllexey.itmowidgets.shared.feature.map.map_search_other_buildings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** What the map screen asks its route to do. */
data class CampusMapActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBuilding: (Int) -> Unit = {},
    val onLevel: (Int) -> Unit = {},
    val onRoomTap: (Long?) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onPlace: (Place) -> Unit = {},
    val onResult: (SearchResult) -> Unit = {},
    val onRoute: () -> Unit = {},
    val onStep: (Int) -> Unit = {},
    val onCloseTrip: () -> Unit = {},
    val onCloseRoom: () -> Unit = {},
    val onOpenMaps: (MapDestination) -> Unit = {},
)

object CampusMapTestTags {
    const val MAP = "map.view"
    const val ROOM = "map.room"
    const val TRIP = "map.trip"
    const val SEARCH = "map.search"
}

/**
 * The campus map (docs/features/map.md "Screens"), full screen: the plan under everything, the search, the building
 * chips and the back button on top, the floors on the edge, and below the room card, the route step or the results.
 * [map] draws the native plan; previews and screenshot tests pass a placeholder.
 */
@Composable
fun CampusMapScreen(
    state: CampusMapUiState,
    actions: CampusMapActions,
    modifier: Modifier = Modifier,
    map: @Composable (MapScene, Modifier) -> Unit,
) {
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.background)) {
        when (state) {
            CampusMapUiState.Loading -> ContentStateLoading(Modifier.fillMaxSize())
            is CampusMapUiState.Failed -> ContentState(
                title = stringResource(CoreRes.string.common_load_error_title),
                icon = painterResource(KitRes.drawable.ic_error),
                modifier = Modifier.fillMaxSize(),
                description = state.error.toUiText().asString(),
                action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
            )
            is CampusMapUiState.NoPlan -> ContentState(
                title = stringResource(Res.string.map_no_plan_title),
                icon = painterResource(KitRes.drawable.ic_map),
                modifier = Modifier.fillMaxSize(),
                description = state.building,
                action = state.destination?.let { ContentStateAction(stringResource(Res.string.map_open_in_maps), { actions.onOpenMaps(it) }) },
            )
            is CampusMapUiState.Plan -> PlanContent(state, actions, map)
        }
        if (state !is CampusMapUiState.Plan) {
            BackButton(actions.onBack, Modifier.statusBarsPadding().padding(ItmoTheme.spacing.related))
        }
    }
}

@Composable
private fun PlanContent(state: CampusMapUiState.Plan, actions: CampusMapActions, map: @Composable (MapScene, Modifier) -> Unit) {
    val spacing = ItmoTheme.spacing
    Box(Modifier.fillMaxSize()) {
        map(state.map, Modifier.fillMaxSize().testTag(CampusMapTestTags.MAP))
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = spacing.related),
            verticalArrangement = Arrangement.spacedBy(spacing.related),
        ) {
            Row(
                Modifier.padding(horizontal = spacing.related),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.content),
            ) {
                BackButton(actions.onBack)
                SearchField(
                    query = state.search.query,
                    onQueryChange = actions.onSearch,
                    placeholder = stringResource(Res.string.map_search_hint),
                    clearLabel = stringResource(Res.string.map_close),
                    modifier = Modifier.weight(1f).testTag(CampusMapTestTags.SEARCH),
                )
            }
            if (state.buildings.size > 1 && !state.search.active) {
                FilterChips(state.buildings.map { ChipSpec(it.label, it.selected) }, { actions.onBuilding(state.buildings[it].id) }, Modifier.padding(horizontal = spacing.related))
            }
            if (state.trip == null && state.search.query.isBlank()) {
                FilterChips(state.search.places.map { ChipSpec(it.label.asString(), it.selected) }, { actions.onPlace(state.search.places[it].place) }, Modifier.padding(horizontal = spacing.related))
            }
            // Under the chips, never over them; the results cover the edge, so the floors return with the plan.
            if (!state.search.active) {
                Floors(state.levels, actions.onLevel, Modifier.align(Alignment.End).padding(end = spacing.related))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(spacing.related),
            verticalArrangement = Arrangement.spacedBy(spacing.related),
        ) {
            when {
                state.search.active -> Results(state.search, actions.onResult)
                state.trip != null -> Trip(state.trip, actions)
                state.room != null -> Room(state.room, actions)
                state.notice != null -> ItmoCard { Text(state.notice.asString(), style = ItmoTheme.typography.bodyLarge) }
            }
            Text(
                state.map.attribution,
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = ItmoTheme.colorScheme.surfaceContainerHigh) {
        IconButton(onClick = onBack) {
            Icon(painterResource(KitRes.drawable.ic_arrow_back), stringResource(CoreRes.string.common_back))
        }
    }
}

@Composable
private fun Floors(levels: List<LevelChip>, onLevel: (Int) -> Unit, modifier: Modifier) {
    val colors = ItmoTheme.colorScheme
    val label = stringResource(Res.string.map_floors)
    Surface(modifier.semantics { contentDescription = label }, shape = CircleShape, color = colors.surfaceContainerHigh) {
        Column(Modifier.padding(ItmoTheme.spacing.compact), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            levels.reversed().forEach { level ->
                Box(
                    Modifier
                        .size(48.dp)
                        .background(if (level.selected) colors.primary else colors.surfaceContainerHigh, CircleShape)
                        .clickable(role = Role.Tab) { onLevel(level.level) }
                        .semantics { selected = level.selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        level.label.asString(),
                        color = if (level.selected) colors.onPrimary else colors.onSurface,
                        style = ItmoTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Room(room: RoomCard, actions: CampusMapActions) {
    val spacing = ItmoTheme.spacing
    ItmoCard(modifier = Modifier.testTag(CampusMapTestTags.ROOM)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(room.title.asString(), style = ItmoTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(room.subtitle.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                room.note?.let { Text(it.asString(), color = ItmoTheme.colorScheme.tertiary, style = ItmoTheme.typography.bodyMedium) }
            }
            IconButton(onClick = actions.onCloseRoom) { Icon(painterResource(KitRes.drawable.ic_close), stringResource(Res.string.map_close)) }
        }
        Row(Modifier.padding(top = spacing.content), horizontalArrangement = Arrangement.spacedBy(spacing.content)) {
            Button(onClick = actions.onRoute) {
                Icon(painterResource(KitRes.drawable.ic_directions), null, Modifier.size(18.dp))
                Text(stringResource(Res.string.map_route), Modifier.padding(start = ItmoTheme.spacing.content))
            }
            room.destination?.let { destination ->
                OutlinedButton(onClick = { actions.onOpenMaps(destination) }) { Text(stringResource(Res.string.map_open_in_maps)) }
            }
        }
    }
}

@Composable
private fun Trip(trip: TripView, actions: CampusMapActions) {
    val step = trip.step
    ItmoCard(modifier = Modifier.testTag(CampusMapTestTags.TRIP)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
                    Text(trip.from.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
                    Text(step.counter.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
                }
                Text(step.title.asString(), style = ItmoTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = actions.onCloseTrip) { Icon(painterResource(KitRes.drawable.ic_close), stringResource(Res.string.map_close)) }
        }
        Text(step.text.asString(), style = ItmoTheme.typography.bodyLarge)
        Row(Modifier.padding(top = ItmoTheme.spacing.content), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.onStep(trip.current - 1) }, enabled = trip.current > 0) {
                Icon(painterResource(KitRes.drawable.ic_arrow_back), stringResource(Res.string.map_route_previous))
            }
            IconButton(onClick = { actions.onStep(trip.current + 1) }, enabled = trip.current < trip.steps.lastIndex) {
                Icon(painterResource(KitRes.drawable.ic_arrow_forward), stringResource(Res.string.map_route_next))
            }
            Box(Modifier.weight(1f))
            step.destination?.let { destination ->
                Button(onClick = { actions.onOpenMaps(destination) }) {
                    Icon(painterResource(KitRes.drawable.ic_open_in_new), null, Modifier.size(18.dp))
                    Text(stringResource(Res.string.map_open_in_maps), Modifier.padding(start = ItmoTheme.spacing.content))
                }
            }
        }
    }
}

@Composable
private fun Results(search: SearchView, onResult: (SearchResult) -> Unit) {
    val colors = ItmoTheme.colorScheme
    Surface(shape = ItmoTheme.shapes.large, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        if (search.empty) {
            Text(stringResource(Res.string.map_search_empty), Modifier.padding(ItmoTheme.spacing.group), style = ItmoTheme.typography.bodyLarge)
            return@Surface
        }
        LazyColumn(Modifier.padding(vertical = ItmoTheme.spacing.content)) {
            items(search.results, key = { "here-${it.roomId}" }) { ResultRow(it, onResult) }
            if (search.elsewhere.isNotEmpty()) {
                item(key = "elsewhere") {
                    Text(
                        stringResource(Res.string.map_search_other_buildings),
                        Modifier.padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.content),
                        color = colors.primary,
                        style = ItmoTheme.typography.labelLarge,
                    )
                }
                items(search.elsewhere, key = { "there-${it.buildingId}-${it.roomId}" }) { ResultRow(it, onResult) }
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, onResult: (SearchResult) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onResult(result) }
            .padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.content),
    ) {
        Text(result.title.asString(), style = ItmoTheme.typography.bodyLarge)
        Text(result.subtitle.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
    }
}

/** The map's place in previews and screenshot tests, where no GL view renders. */
@Composable
fun MapPlaceholder(scene: MapScene, modifier: Modifier) {
    Box(modifier.background(ItmoTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(painterResource(KitRes.drawable.ic_map), null, Modifier.widthIn(max = 48.dp), tint = ItmoTheme.colorScheme.outline)
    }
}
