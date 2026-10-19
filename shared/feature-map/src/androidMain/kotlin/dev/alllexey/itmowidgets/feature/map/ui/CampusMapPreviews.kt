package dev.alllexey.itmowidgets.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapSamples
import dev.alllexey.itmowidgets.feature.map.presentation.MapScene
import androidx.compose.ui.Modifier

// The map at the design lab's axes; the names are `<Screen>_<state>`. The GL view is the placeholder.

private const val SCREEN_HEIGHT = 800

private val placeholder: @Composable (MapScene, Modifier) -> Unit = { scene, modifier -> MapPlaceholder(scene, modifier) }

internal class CampusMapRoomPreview {
    @Preview(name = "room", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Room, CampusMapActions(), map = placeholder) }
}

internal class CampusMapApproximatePreview {
    @Preview(name = "approximate", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Approximate, CampusMapActions(), map = placeholder) }
}

internal class CampusMapRoutePreview {
    @Preview(name = "route", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Route, CampusMapActions(), map = placeholder) }
}

internal class CampusMapCityPreview {
    @Preview(name = "city", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.City, CampusMapActions(), map = placeholder) }
}

internal class CampusMapPickPreview {
    @Preview(name = "pick", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Pick, CampusMapActions(), map = placeholder) }
}

internal class CampusMapSwitchingPreview {
    @Preview(name = "switching", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Switching, CampusMapActions(), map = placeholder) }
}

internal class CampusMapSearchPreview {
    @Preview(name = "search", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Search, CampusMapActions(), map = placeholder) }
}

internal class CampusMapMissingPreview {
    @Preview(name = "missing", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Missing, CampusMapActions(), map = placeholder) }
}

internal class CampusMapNoPlanPreview {
    @Preview(name = "no-plan", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.NoPlan, CampusMapActions(), map = placeholder) }
}

internal class CampusMapErrorPreview {
    @Preview(name = "error", heightDp = SCREEN_HEIGHT)
    @Composable
    fun CampusMap() = ItmoPreview { CampusMapScreen(CampusMapSamples.Failed, CampusMapActions(), map = placeholder) }
}
