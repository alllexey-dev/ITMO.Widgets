package dev.alllexey.itmowidgets.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class MapDestinationTest {
    @Test
    fun `known coordinates pin the exact point with the label`() {
        val destination = MapDestination("Кронверкский пр., 49", "Кронверкский проспект, 49", 59.9567751, 30.308711)

        assertEquals(
            "geo:59.9567751,30.308711?q=59.9567751,30.308711(%D0%9A%D1%80%D0%BE%D0%BD%D0%B2%D0%B5%D1%80%D0%BA%D1%81%D0%BA%D0%B8%D0%B9%20%D0%BF%D1%80.%2C%2049)",
            destination.geoUri()
        )
    }

    @Test
    fun `without coordinates the map app geocodes the address`() {
        val destination = MapDestination("Где-то", "Some Street 5, Санкт-Петербург")

        assertEquals("geo:0,0?q=Some%20Street%205%2C%20%D0%A1%D0%B0%D0%BD%D0%BA%D1%82-%D0%9F%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3", destination.geoUri())
    }

    @Test
    fun `a known building becomes a destination with its label and coordinates`() {
        val building = KnownBuilding("x", listOf(1), listOf("x"), "Label", "Address", 1.5, 2.5)

        assertEquals(MapDestination("Label", "Address", 1.5, 2.5), building.toMapDestination())
    }
}
