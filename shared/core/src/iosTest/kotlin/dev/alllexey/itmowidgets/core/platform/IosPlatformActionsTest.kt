package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.location.MapDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class IosPlatformActionsTest {

    @Test
    fun pinsTheCoordinatesInAppleMapsWhenKnown() {
        val destination = MapDestination("Main building", "Kronverksky 49", 59.957, 30.308)

        assertEquals(
            "https://maps.apple.com/?ll=59.957,30.308&q=Main%20building",
            IosPlatformActions.appleMapsUrl(destination)
        )
    }

    @Test
    fun searchesTheAddressWithoutCoordinates() {
        assertEquals(
            "https://maps.apple.com/?q=Lomonosova%209",
            IosPlatformActions.appleMapsUrl(MapDestination("Building", "Lomonosova 9"))
        )
    }
}
