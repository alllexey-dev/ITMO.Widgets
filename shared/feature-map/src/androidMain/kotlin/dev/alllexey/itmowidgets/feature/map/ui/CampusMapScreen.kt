package dev.alllexey.itmowidgets.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuGroup
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.SearchField
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.map.presentation.BuildingChip
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapUiState
import dev.alllexey.itmowidgets.feature.map.presentation.LevelChip
import dev.alllexey.itmowidgets.feature.map.presentation.MapScene
import dev.alllexey.itmowidgets.feature.map.presentation.Place
import dev.alllexey.itmowidgets.feature.map.presentation.RoomCard
import dev.alllexey.itmowidgets.feature.map.presentation.RouteEnd
import dev.alllexey.itmowidgets.feature.map.presentation.SearchResult
import dev.alllexey.itmowidgets.feature.map.presentation.SearchView
import dev.alllexey.itmowidgets.feature.map.presentation.TripStep
import dev.alllexey.itmowidgets.feature.map.presentation.TripView
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_back
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.map_buildings
import dev.alllexey.itmowidgets.shared.compose.strings.map_close
import dev.alllexey.itmowidgets.shared.compose.strings.map_fit_building
import dev.alllexey.itmowidgets.shared.compose.strings.map_floors
import dev.alllexey.itmowidgets.shared.compose.strings.map_loading
import dev.alllexey.itmowidgets.shared.compose.strings.map_no_plan_title
import dev.alllexey.itmowidgets.shared.compose.strings.map_open_in_maps
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_badge_from
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_badge_to
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_from_here
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_here
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_next
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_not_found
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_pick_from
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_pick_hint
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_pick_to
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_previous
import dev.alllexey.itmowidgets.shared.compose.strings.map_route_swap
import dev.alllexey.itmowidgets.shared.compose.strings.map_search_empty
import dev.alllexey.itmowidgets.shared.compose.strings.map_search_hint
import dev.alllexey.itmowidgets.shared.compose.strings.map_search_other_buildings
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_left
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_directions
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_expand_more
import dev.alllexey.itmowidgets.shared.designsystem.ic_fit_screen
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_map
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_swap_vert
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
    val onRouteFrom: () -> Unit = {},
    val onPick: (RouteEnd?) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onFitBuilding: () -> Unit = {},
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
 * The campus map (docs/features/map.md "Screen"), full screen: the plan under everything, the back button, the search,
 * the building pill and the place chips on top, the floors on the edge, and below a compact card: the room, the route
 * or the results. Everything over the plan is opaque with a shadow, so it reads on any floor in either theme.
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
                title = stringResource(Res.string.common_load_error_title),
                icon = painterResource(KitRes.drawable.ic_error),
                modifier = Modifier.fillMaxSize(),
                description = state.error.toUiText().asString(),
                action = ContentStateAction(stringResource(Res.string.common_retry), actions.onRetry),
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
    val trip = state.trip
    val picking = trip?.picking
    val searchFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    // Picking a route end starts in the search, as itmo-map-web's «Откуда»/«Куда»; a picked end or an opened result
    // ends the search and puts the keyboard away.
    LaunchedEffect(picking) { if (picking != null) searchFocus.requestFocus() else focus.clearFocus() }
    val onResult: (SearchResult) -> Unit = { result ->
        focus.clearFocus()
        actions.onResult(result)
    }
    Box(Modifier.fillMaxSize()) {
        map(state.map, Modifier.fillMaxSize().testTag(CampusMapTestTags.MAP))
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = spacing.related),
            verticalArrangement = Arrangement.spacedBy(spacing.compact),
        ) {
            Row(
                Modifier.padding(horizontal = spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.compact),
            ) {
                BackButton(actions.onBack)
                SearchField(
                    query = state.search.query,
                    onQueryChange = actions.onSearch,
                    placeholder = stringResource(
                        when (picking) {
                            RouteEnd.FROM -> Res.string.map_route_pick_from
                            RouteEnd.TO -> Res.string.map_route_pick_to
                            null -> Res.string.map_search_hint
                        },
                    ),
                    clearLabel = stringResource(Res.string.map_close),
                    modifier = Modifier.weight(1f).shadow(ChromeElevation, ItmoTheme.shapes.full).focusRequester(searchFocus)
                        .testTag(CampusMapTestTags.SEARCH),
                )
            }
            if (state.search.query.isBlank()) {
                Chips(state, showPlaces = trip == null || picking != null, actions)
            }
            // The results cover the edge, so the floors return with the plan; a route end can be tapped on any floor.
            if (!state.search.active || trip != null) {
                Floors(state.levels, actions.onLevel, Modifier.align(Alignment.End).padding(end = spacing.compact))
                FitButton(actions.onFitBuilding, Modifier.align(Alignment.End).padding(end = spacing.compact))
            }
        }
        if (state.loading) Loading(Modifier.align(Alignment.Center))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(spacing.compact),
            verticalArrangement = Arrangement.spacedBy(spacing.compact),
        ) {
            when {
                trip != null -> Trip(trip, state.search, actions.copy(onResult = onResult))
                state.search.active -> Sheet { Results(state.search, onResult) }
                state.room != null -> Room(state.room, actions)
                state.notice != null -> ItmoCard { Text(state.notice.asString(), style = ItmoTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = ItmoTheme.colorScheme.surfaceContainerHigh, shadowElevation = ChromeElevation) {
        IconButton(onClick = onBack) {
            Icon(painterResource(KitRes.drawable.ic_arrow_back), stringResource(Res.string.common_back))
        }
    }
}

/** The building pill and the places with their map icons, filled like itmo-map-web's so they read over the plan. */
@Composable
private fun Chips(state: CampusMapUiState.Plan, showPlaces: Boolean, actions: CampusMapActions) {
    val spacing = ItmoTheme.spacing
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = spacing.compact, vertical = spacing.related),
        horizontalArrangement = Arrangement.spacedBy(spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuildingPill(state.buildings, actions.onBuilding)
        if (showPlaces) {
            state.search.places.forEach { place ->
                MapChip(place.label.asString(), place.selected, onClick = { actions.onPlace(place.place) }) {
                    MapSprite(place.icon, ChipIconSize)
                }
            }
        }
    }
}

@Composable
private fun BuildingPill(buildings: List<BuildingChip>, onBuilding: (Int) -> Unit) {
    val current = buildings.firstOrNull { it.selected } ?: return
    var open by remember { mutableStateOf(false) }
    val name = stringResource(Res.string.map_buildings)
    Box {
        MapChip(
            current.label,
            selected = false,
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = "$name: ${current.label}" },
            trailing = { Icon(painterResource(KitRes.drawable.ic_expand_more), null, Modifier.size(18.dp)) },
        ) {
            Icon(painterResource(KitRes.drawable.ic_location_on), null, Modifier.size(ChipIconSize), tint = ItmoTheme.colorScheme.primary)
        }
        ItmoMenu(
            expanded = open,
            onDismissRequest = { open = false },
            groups = listOf(
                ItmoMenuGroup(buildings.map { building -> ItmoMenuItem(building.label, { onBuilding(building.id) }, selected = building.selected) }),
            ),
        )
    }
}

/** A chip over the plan: an opaque surface with a shadow, the accent's container when [selected]. */
@Composable
private fun MapChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    leading: @Composable () -> Unit,
) {
    val colors = ItmoTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = ItmoTheme.shapes.full,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        shadowElevation = ChromeElevation,
    ) {
        Row(
            Modifier.heightIn(min = ChipHeight)
                .padding(start = ItmoTheme.spacing.compact, end = if (trailing != null) ItmoTheme.spacing.compact else ItmoTheme.spacing.content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        ) {
            leading()
            Text(label, style = ItmoTheme.typography.labelLarge, maxLines = 1)
            trailing?.invoke()
        }
    }
}

/** itmo-map-web's «Показать корпус целиком» under the floors: the camera back to the whole building. */
@Composable
private fun FitButton(onFit: () -> Unit, modifier: Modifier) {
    Surface(modifier, shape = CircleShape, color = ItmoTheme.colorScheme.surfaceContainerHigh, shadowElevation = ChromeElevation) {
        IconButton(onClick = onFit) {
            Icon(painterResource(KitRes.drawable.ic_fit_screen), stringResource(Res.string.map_fit_building))
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Surface(modifier, shape = ItmoTheme.shapes.full, color = ItmoTheme.colorScheme.surfaceContainerHigh, shadowElevation = ChromeElevation) {
        Row(
            Modifier.padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.map_loading), style = ItmoTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Floors(levels: List<LevelChip>, onLevel: (Int) -> Unit, modifier: Modifier) {
    val colors = ItmoTheme.colorScheme
    val label = stringResource(Res.string.map_floors)
    // itmo-map-web's floor selector: a white column with a shadow, the floor in the accent, a dot on the route's floors.
    Surface(
        modifier.semantics { contentDescription = label },
        shape = ItmoTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
        shadowElevation = ChromeElevation,
    ) {
        Column(Modifier.padding(ItmoTheme.spacing.related)) {
            levels.reversed().forEach { level ->
                Box(
                    Modifier
                        .size(FloorSize)
                        .clip(CircleShape)
                        .clickable(role = Role.Tab) { onLevel(level.level) }
                        .semantics { selected = level.selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(40.dp).background(if (level.selected) colors.primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            level.label.asString(),
                            color = if (level.selected) colors.onPrimary else colors.onSurfaceVariant,
                            style = ItmoTheme.typography.titleSmall,
                            fontWeight = if (level.selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                    if (level.onRoute) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(ItmoTheme.spacing.related)
                                .size(10.dp)
                                .background(if (level.selected) colors.onPrimary else colors.primary, CircleShape)
                                .border(2.dp, if (level.selected) colors.primary else colors.surfaceContainerHigh, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/** The bottom sheet of the map: an opaque card with a shadow, compact so the plan keeps the screen. */
@Composable
private fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = ItmoTheme.shapes.extraLarge,
        color = ItmoTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = ChromeElevation,
    ) {
        Column(Modifier.padding(vertical = ItmoTheme.spacing.compact), content = content)
    }
}

/** The room: its icon, number and floor, «Сюда» and «Отсюда» (itmo-map-web's unit card). */
@Composable
private fun Room(room: RoomCard, actions: CampusMapActions) {
    val spacing = ItmoTheme.spacing
    Sheet(Modifier.testTag(CampusMapTestTags.ROOM)) {
        Row(Modifier.padding(start = spacing.group, end = spacing.related), verticalAlignment = Alignment.CenterVertically) {
            MapSprite(room.icon, 40.dp)
            Column(Modifier.weight(1f).padding(start = spacing.content)) {
                Text(room.title.asString(), style = ItmoTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(room.subtitle.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                room.note?.let { Text(it.asString(), color = ItmoTheme.colorScheme.tertiary, style = ItmoTheme.typography.bodyMedium) }
            }
            IconButton(onClick = actions.onCloseRoom) { Icon(painterResource(KitRes.drawable.ic_close), stringResource(Res.string.map_close)) }
        }
        Row(
            Modifier.padding(horizontal = spacing.group).padding(top = spacing.compact, bottom = spacing.related),
            horizontalArrangement = Arrangement.spacedBy(spacing.compact),
        ) {
            Button(onClick = actions.onRoute, modifier = Modifier.weight(1f)) {
                Icon(painterResource(KitRes.drawable.ic_directions), null, Modifier.size(18.dp))
                Text(stringResource(Res.string.map_route_here), Modifier.padding(start = spacing.compact))
            }
            TonalButton(onClick = actions.onRouteFrom, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.map_route_from_here))
            }
        }
    }
}

/**
 * The route: А and Б in one line with the swap (either end opens the search to pick it again), then the shown step
 * with its icon, the progress and the arrows; while picking an end, the search's results instead.
 */
@Composable
private fun Trip(trip: TripView, search: SearchView, actions: CampusMapActions) {
    val spacing = ItmoTheme.spacing
    Sheet(Modifier.testTag(CampusMapTestTags.TRIP)) {
        Row(
            Modifier.padding(start = spacing.compact, end = spacing.related),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.related),
        ) {
            Endpoint(RouteEnd.FROM, trip.from, trip.picking == RouteEnd.FROM, actions.onPick, Modifier.weight(1f))
            IconButton(onClick = actions.onSwap, enabled = trip.to != null) {
                Icon(painterResource(KitRes.drawable.ic_swap_vert), stringResource(Res.string.map_route_swap), Modifier.rotate(90f))
            }
            Endpoint(RouteEnd.TO, trip.to, trip.picking == RouteEnd.TO, actions.onPick, Modifier.weight(1f))
            IconButton(onClick = actions.onCloseTrip) { Icon(painterResource(KitRes.drawable.ic_close), stringResource(Res.string.map_close)) }
        }
        val step = trip.step
        when {
            trip.picking != null -> if (search.results.isEmpty() && search.elsewhere.isEmpty()) {
                Hint(stringResource(if (search.active && search.query.isNotBlank()) Res.string.map_search_empty else Res.string.map_route_pick_hint))
            } else {
                HorizontalDivider(Modifier.padding(top = spacing.related), color = ItmoTheme.colorScheme.outlineVariant)
                Results(search, actions.onResult, ResultsInTripHeight)
            }
            trip.notFound -> Hint(stringResource(Res.string.map_route_not_found))
            step != null -> Step(trip, step, actions)
            else -> Hint(stringResource(Res.string.map_route_pick_hint))
        }
    }
}

@Composable
private fun Endpoint(end: RouteEnd, text: UiText?, picking: Boolean, onPick: (RouteEnd) -> Unit, modifier: Modifier) {
    val colors = ItmoTheme.colorScheme
    val placeholder = stringResource(if (end == RouteEnd.FROM) Res.string.map_route_pick_from else Res.string.map_route_pick_to)
    Row(
        modifier
            .clip(ItmoTheme.shapes.full)
            .background(if (picking) colors.primaryContainer else colors.surfaceContainerHighest)
            .clickable(onClickLabel = placeholder) { onPick(end) }
            .heightIn(min = EndpointHeight)
            .padding(horizontal = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Box(Modifier.size(20.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (end == RouteEnd.FROM) Res.string.map_route_badge_from else Res.string.map_route_badge_to),
                color = colors.onPrimary,
                style = ItmoTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text?.asString() ?: placeholder,
            color = when {
                picking -> colors.onPrimaryContainer
                text == null -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            style = ItmoTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Step(trip: TripView, step: TripStep, actions: CampusMapActions) {
    val spacing = ItmoTheme.spacing
    Row(
        Modifier.padding(horizontal = spacing.group).padding(top = spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(spacing.related),
    ) {
        trip.steps.indices.forEach { index ->
            Box(
                Modifier.weight(1f).height(4.dp).background(
                    if (index <= trip.current) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            )
        }
    }
    Row(Modifier.padding(start = spacing.group, end = spacing.related, top = spacing.compact), verticalAlignment = Alignment.CenterVertically) {
        MapSprite(step.kind.icon, 36.dp)
        Column(Modifier.weight(1f).padding(start = spacing.content)) {
            Text(
                "${step.counter.asString()} · ${step.title.asString()}",
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelMedium,
                maxLines = 1,
            )
            Text(step.text.asString(), style = ItmoTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        step.destination?.let { destination ->
            IconButton(onClick = { actions.onOpenMaps(destination) }) {
                Icon(painterResource(KitRes.drawable.ic_open_in_new), stringResource(Res.string.map_open_in_maps), tint = ItmoTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = { actions.onStep(trip.current - 1) }, enabled = trip.current > 0) {
            Icon(painterResource(KitRes.drawable.ic_chevron_left), stringResource(Res.string.map_route_previous))
        }
        IconButton(onClick = { actions.onStep(trip.current + 1) }, enabled = trip.current < trip.steps.lastIndex) {
            Icon(painterResource(KitRes.drawable.ic_chevron_right), stringResource(Res.string.map_route_next))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.compact),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodyMedium,
    )
}

@Composable
private fun Results(search: SearchView, onResult: (SearchResult) -> Unit, maxHeight: Dp = ResultsHeight) {
    val colors = ItmoTheme.colorScheme
    if (search.empty) {
        Hint(stringResource(Res.string.map_search_empty))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        items(search.results, key = { "here-${it.roomId}" }) { ResultRow(it, onResult) }
        if (search.elsewhere.isNotEmpty()) {
            item(key = "elsewhere") {
                Text(
                    stringResource(Res.string.map_search_other_buildings),
                    Modifier.padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.compact),
                    color = colors.primary,
                    style = ItmoTheme.typography.labelLarge,
                )
            }
            items(search.elsewhere, key = { "there-${it.buildingId}-${it.roomId}" }) { ResultRow(it, onResult) }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, onResult: (SearchResult) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget + ItmoTheme.spacing.compact)
            .clickable { onResult(result) }
            .padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.related),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        MapSprite(result.icon, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(result.title.asString(), style = ItmoTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.subtitle.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The map's place in previews and screenshot tests, where no GL view renders. */
@Composable
fun MapPlaceholder(scene: MapScene, modifier: Modifier) {
    Box(modifier.background(ItmoTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(painterResource(KitRes.drawable.ic_map), null, Modifier.widthIn(max = 48.dp), tint = ItmoTheme.colorScheme.outline)
    }
}

private val ChromeElevation = 3.dp
private val ChipHeight = 36.dp
private val ChipIconSize = 24.dp
private val FloorSize = 48.dp
private val EndpointHeight = 40.dp
private val ResultsHeight = 280.dp
private val ResultsInTripHeight = 220.dp
