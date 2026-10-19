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
        bounds = listOf(30.0, 59.0, 30.1, 59.1), bearing = 160.0, level = 2,
        route = CampusMapGeoJson.EMPTY, selection = CampusMapGeoJson.EMPTY, selectedRoomId = 71, focus = null,
    )
    private val style = Json.parseToJsonElement(CampusMapStyle.json(scene)).jsonObject
    private val layers = style.getValue("layers").jsonArray.map { it.jsonObject }

    @Test
    fun theCampusAppStyleReadsTheLocalTilesWithTheMirrorsFont() {
        val plan = style.getValue("sources").jsonObject.getValue("openmaptiles").jsonObject
        assertEquals("mbtiles:///data/files/abc.mbtiles", plan.getValue("url").jsonPrimitive.content)
        assertEquals(scene.glyphsUrl, style.getValue("glyphs").jsonPrimitive.content)
        val fonts = layers.mapNotNull { it["layout"]?.jsonObject?.get("text-font") }.flatMap { it.jsonArray }.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("Noto Sans Bold"), fonts)
        val ids = layers.map { it.getValue("id").jsonPrimitive.content }
        assertTrue(ids.containsAll(CampusMapStyle.TAP_LAYERS + listOf("selected_fill", "selected_line", "route_line_current", "area_name_active")))
        assertTrue(ids.indexOf("selected_fill") < ids.indexOf("borders"))
    }

    @Test
    fun legacyFiltersBecomeExpressionsOnTheLevelAndTheSelectedRoom() {
        val filters = CampusMapStyle.filters(level = 2, selectedRoomId = 71)

        assertEquals("""["all",["==",["get","level"],"2"],["==",["get","id"],"71"]]""", filters.getValue("selected_fill"))
        assertEquals("""["all",["==",["get","class"],"footway"],["==",["get","level"],"2"]]""", filters.getValue("outside_footway"))
        val auditory = Json.parseToJsonElement(filters.getValue("area_name_auditory")).jsonArray
        assertEquals("match", auditory[1].jsonArray[0].jsonPrimitive.content)
        // MapLibre rejects a match with repeated labels, and the template repeats `stairs`.
        val labels = auditory[1].jsonArray[2].jsonArray.map { it.jsonPrimitive.content }
        assertEquals(labels.distinct(), labels)
        assertEquals("""["!=",["get","id"],"71"]""", auditory[3].toString())
        layers.forEach { layer ->
            val id = layer.getValue("id").jsonPrimitive.content
            filters[id]?.let { assertEquals(Json.parseToJsonElement(it), layer["filter"], id) }
        }
        assertEquals("""["all",["==",["get","level"],"0"],["==",["get","id"],"-1"]]""", CampusMapStyle.filters(0, null).getValue("selected_line"))
    }

    @Test
    fun foodIsApartFromStudyPlacesAndTheRouteHasACasing() {
        val ids = layers.map { it.getValue("id").jsonPrimitive.content }
        assertTrue(ids.indexOf("route_casing") < ids.indexOf("route_line_dashed"))
        assertTrue(ids.indexOf("route_casing") < ids.indexOf("route_line_current"))
        val study = layers.single { it.getValue("id").jsonPrimitive.content == "room_student_places" }
        val fill = study.getValue("paint").jsonObject.getValue("fill-color").jsonArray
        assertEquals("""["match",["get","subclass"],["canteen","food"],"rgba(255, 240, 229, 1)","rgba(214, 241, 236, 1)"]""", fill.toString())
    }

    @Test
    fun theDarkStyleKeepsNoLightColourAndChangesNothingElse() {
        val dark = Json.parseToJsonElement(CampusMapStyle.json(scene, dark = true)).jsonObject
        val text = dark.toString()

        CampusMapStyle.DARK_COLORS.keys.forEach { light -> assertTrue("\"$light\"" !in text, light) }
        val darkLayers = dark.getValue("layers").jsonArray.map { it.jsonObject }
        assertEquals(layers.map { it["id"] to it["filter"] }, darkLayers.map { it["id"] to it["filter"] })
        val background = darkLayers.first().getValue("paint").jsonObject.getValue("background-color").jsonPrimitive.content
        assertEquals("rgba(22, 24, 27, 1)", background)
    }

    @Test
    fun noRouteAndNoSelectionAreEmptyCollections() {
        assertEquals(0, Json.parseToJsonElement(CampusMapGeoJson.selection(null)).jsonObject.getValue("features").jsonArray.size)
        assertEquals(CampusMapGeoJson.EMPTY, CampusMapGeoJson.route(null, 0, null))
    }
}
