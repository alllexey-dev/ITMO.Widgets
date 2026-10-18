package dev.alllexey.itmowidgets.feature.map.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CampusMapStyleTest {
    private val scene = MapScene(
        buildingId = 13, tilesPath = "/data/files/abc.mbtiles", glyphsUrl = "https://map.example/glyphs/{fontstack}/{range}.pbf",
        attribution = "План: Университет ИТМО", bounds = listOf(30.0, 59.0, 30.1, 59.1), bearing = 160.0, level = 2,
        route = CampusMapGeoJson.EMPTY, selection = CampusMapGeoJson.EMPTY, focus = null,
    )

    @Test
    fun theStyleReadsTheLocalTilesAndKeepsTheGlyphTemplate() {
        val style = Json.parseToJsonElement(CampusMapStyle.json(scene, dark = false)).jsonObject

        assertEquals("mbtiles:///data/files/abc.mbtiles", style["sources"]!!.jsonObject["plan"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals(scene.glyphsUrl, style["glyphs"]!!.jsonPrimitive.content)
        val ids = style["layers"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertTrue(ids.containsAll(CampusMapStyle.TAP_LAYERS + CampusMapStyle.filters(0).keys))
    }

    @Test
    fun everyFloorLayerIsFilteredToTheLevelAsAString() {
        val style = Json.parseToJsonElement(CampusMapStyle.json(scene, dark = true)).jsonObject
        val filters = style["layers"]!!.jsonArray.associate { it.jsonObject["id"]!!.jsonPrimitive.content to it.jsonObject["filter"] }

        CampusMapStyle.filters(2).forEach { (id, filter) ->
            assertEquals(Json.parseToJsonElement(filter), filters[id], id)
            assertTrue("\"2\"" in filter, filter)
        }
        assertEquals("""["all",["==",["get","level"],"0"],["==",["get","class"],"room"]]""", CampusMapStyle.filters(0).getValue("rooms"))
    }
}
