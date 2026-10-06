package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/**
 * The shell's navigation state and its reducer (v2.2 parity, `docs/architecture.md` section Navigation). Immutable:
 * every command returns the next state, which the host keeps in saved state, so the tab, the overlays, the sheets and
 * the tab requests survive recreation and process death.
 *
 * Layers, bottom to top: the selected [tab]'s own stack ([tabStack], only its root), the [overlays] (full-window
 * screens above the bar, which keeps its size and is hidden from accessibility while [coversBar]) and the [floating]
 * sheets and dialogs. [requests] wait for their tab's root.
 */
@Serializable
data class ShellBackStack(
    val tab: AppTab = AppTab.START,
    val overlays: List<AppRoute> = emptyList(),
    val floating: List<AppRoute> = emptyList(),
    val requests: Map<AppTab, TabRequest> = emptyMap(),
) {
    /** What the display shows, bottom to top. */
    val entries: List<AppRoute> get() = tabStack(tab) + overlays + floating

    val coversBar: Boolean get() = overlays.isNotEmpty()

    fun tabStack(tab: AppTab): List<AppRoute> = listOf(AppRoutes.TabRoot(tab))

    /** Selecting or reselecting a tab closes every overlay, sheet and dialog; the tab's own state stays. */
    fun select(tab: AppTab): ShellBackStack = copy(tab = tab, overlays = emptyList(), floating = emptyList())

    /**
     * A screen goes on the overlay stack and first closes the sheets and dialogs it was opened from. A sheet or a
     * dialog goes on top unless one of its class or of its [AppRoute.exclusiveGroup] is already shown. A tab root
     * selects its tab; a gate key is the [ShellGate]'s and opens nothing.
     */
    fun open(route: AppRoute): ShellBackStack = when (route.kind) {
        RouteKind.GATE -> this
        RouteKind.TAB_ROOT -> (route as? AppRoutes.TabRoot)?.let { select(it.tab) } ?: this
        RouteKind.SCREEN -> copy(overlays = overlays + route, floating = emptyList())
        RouteKind.SHEET, RouteKind.DIALOG ->
            if (floating.any(route::excludes)) this else copy(floating = floating + route)
    }

    /** Back: the top sheet or dialog, then the top overlay, then another tab goes to the start; null leaves the app. */
    fun back(): ShellBackStack? = when {
        floating.isNotEmpty() -> copy(floating = floating.dropLast(1))
        overlays.isNotEmpty() -> copy(overlays = overlays.dropLast(1))
        tab != AppTab.START -> select(AppTab.START)
        else -> null
    }

    /** Closes [route] where it is, for a sheet, dialog or screen that closes itself; unknown keys change nothing. */
    fun close(route: AppRoute): ShellBackStack = when {
        route in floating -> copy(floating = floating.withoutLast(route))
        route in overlays -> copy(overlays = overlays.withoutLast(route), floating = emptyList())
        else -> this
    }

    /** Closes the whole contextual stack at once, for a screen that hands the window over. */
    fun dismissOverlays(): ShellBackStack = copy(overlays = emptyList(), floating = emptyList())

    /** Runs an entry route whose tab is selectable: tab, overlay, request, alert; the host starts its activity. */
    fun apply(route: EntryRoute): ShellBackStack {
        var next = select(route.tab)
        route.overlay?.let { next = next.open(it) }
        route.request?.let { next = next.request(it) }
        route.alert?.let { next = next.open(it) }
        return next
    }

    /** Keeps [request] for its tab's root; a newer request for the same tab replaces the older one. */
    fun request(request: TabRequest): ShellBackStack = copy(requests = requests + (request.tab to request))

    /** The request waiting for [tab]'s root, read before [consume]. */
    fun pendingRequest(tab: AppTab): TabRequest? = requests[tab]

    /** The root of [tab] handled its request; it is never handed out again. */
    fun consume(tab: AppTab): ShellBackStack = copy(requests = requests - tab)
}

private fun AppRoute.excludes(shown: AppRoute): Boolean =
    shown::class == this::class || (exclusiveGroup != null && shown.exclusiveGroup == exclusiveGroup)

private fun List<AppRoute>.withoutLast(route: AppRoute): List<AppRoute> {
    val index = lastIndexOf(route)
    return filterIndexed { i, _ -> i != index }
}
