package dev.alllexey.itmowidgets.core.location

import java.net.URLEncoder

/** A place to show in any map app through a generic `geo:` URI; no provider is preferred. */
data class MapDestination(
    val label: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    /** Coordinates when known, so the pin is exact; otherwise the address for the map app to geocode. */
    fun geoUri(): String {
        if (latitude != null && longitude != null) {
            return "geo:$latitude,$longitude?q=$latitude,$longitude(${encode(label)})"
        }
        return "geo:0,0?q=${encode(address)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
