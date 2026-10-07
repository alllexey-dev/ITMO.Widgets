package dev.alllexey.itmowidgets.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities

/**
 * Whether the platform offers what a card of [kind] shows and opens. The new-marks card needs mark tracking and the
 * recordbook tab it opens (IO-09d2, IO-09d3); every other kind is on iOS.
 */
fun PlatformCapabilities.offers(kind: HomeCardKind): Boolean = when (kind) {
    HomeCardKind.MARKS -> marks && recordbook
    HomeCardKind.SCHEDULE,
    HomeCardKind.SCHEDULE_CHANGES,
    HomeCardKind.SPORT,
    HomeCardKind.FRIEND_REQUESTS,
    HomeCardKind.HINT_WIDGETS,
    HomeCardKind.HINT_NOTIFICATIONS,
    HomeCardKind.HINT_SERVICES -> true
}

/**
 * The renderers with only the kinds [capabilities] offers: `HomeScreen` leaves out a card no renderer claims, so a
 * card of a feature the platform does not offer yet never shows (App Review 2.1), while its source stays in the graph.
 */
fun List<HomeCardRenderer>.offeredBy(capabilities: PlatformCapabilities): List<HomeCardRenderer> =
    mapNotNull { renderer ->
        val kinds = renderer.kinds.filterTo(mutableSetOf(), capabilities::offers)
        when {
            kinds.isEmpty() -> null
            kinds == renderer.kinds -> renderer
            else -> RestrictedRenderer(renderer, kinds)
        }
    }

private class RestrictedRenderer(
    private val renderer: HomeCardRenderer,
    override val kinds: Set<HomeCardKind>,
) : HomeCardRenderer {

    @Composable
    override fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier) =
        renderer.Content(card, actions, modifier)
}
