package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorRoute
import dev.alllexey.itmowidgets.feature.map.domain.graph.RoutePart
import dev.alllexey.itmowidgets.feature.map.domain.graph.RoutePartType
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The one MapLibre style of both platforms (docs/features/map.md "MapLibre"): ITMO's vector plan from the local
 * MBTiles file, drawn in the app's colours, plus the `route` and `selection` GeoJSON sources. Floors switch by
 * per-layer filters ([filters]); the platform view applies them and replaces the GeoJSON on every scene.
 */
object CampusMapStyle {
    const val ROUTE_SOURCE = "route"
    const val SELECTION_SOURCE = "selection"

    /** The layers a tap looks at; their features carry the plan room id in `id`. */
    val TAP_LAYERS: List<String> = listOf("labels", "poi", "rooms")

    /** The style of [scene]'s building in light or [dark] colours, filtered to its level. */
    fun json(scene: MapScene, dark: Boolean): String {
        val palette = if (dark) MapPalette.DARK else MapPalette.LIGHT
        return buildJsonObject {
            put("version", 8)
            put("glyphs", scene.glyphsUrl)
            putJsonObject("sources") {
                putJsonObject(PLAN_SOURCE) {
                    put("type", "vector")
                    put("url", "mbtiles://${scene.tilesPath}")
                    put("attribution", scene.attribution)
                }
                put(ROUTE_SOURCE, geoJsonSource(scene.route))
                put(SELECTION_SOURCE, geoJsonSource(scene.selection))
            }
            putJsonArray("layers") { layers(palette, scene.level).forEach(::add) }
        }.toString()
    }

    /** Layer id to its filter on [level], as MapLibre expression JSON. */
    fun filters(level: Int): Map<String, String> = LEVEL_LAYERS.associate { (id, extra) -> id to filter(level, extra).toString() }

    private fun geoJsonSource(data: String) = buildJsonObject {
        put("type", "geojson")
        put("data", kotlinx.serialization.json.Json.parseToJsonElement(data))
    }

    /** Per-level layers and the extra condition each adds to `level == N`. */
    private val LEVEL_LAYERS: List<Pair<String, JsonElement?>> = listOf(
        "floor" to null,
        "areas" to expr("!=", get("class"), JsonPrimitive("room")),
        "rooms" to expr("==", get("class"), JsonPrimitive("room")),
        "outlines" to null,
        "walls" to null,
        "poi" to null,
        "labels" to null,
    )

    private fun filter(level: Int, extra: JsonElement?): JsonElement {
        val onLevel = expr("==", get("level"), JsonPrimitive(level.toString()))
        return if (extra == null) onLevel else expr("all", onLevel, extra)
    }

    private fun layers(p: MapPalette, level: Int): List<JsonObject> {
        val filters = LEVEL_LAYERS.toMap().mapValues { (_, extra) -> filter(level, extra) }
        return listOf(
            layer("background", "background", paint = mapOf("background-color" to JsonPrimitive(p.background))),
            layer("footprint", "fill", "footprint", paint = mapOf("fill-color" to JsonPrimitive(p.footprint))),
            layer("floor", "fill", "level", filters["floor"], mapOf("fill-color" to JsonPrimitive(p.floor))),
            layer("areas", "fill", "area", filters["areas"], mapOf("fill-color" to JsonPrimitive(p.corridor))),
            layer(
                "rooms", "fill", "area", filters["rooms"],
                mapOf(
                    "fill-color" to expr(
                        "match", get("subclass"),
                        JsonPrimitive("education"), JsonPrimitive(p.classroom),
                        JsonPrimitive(p.room),
                    ),
                ),
            ),
            layer("outlines", "line", "area", filters["outlines"], mapOf("line-color" to JsonPrimitive(p.outline), "line-width" to JsonPrimitive(0.6))),
            layer("walls", "line", "walls", filters["walls"], mapOf("line-color" to JsonPrimitive(p.wall), "line-width" to zoomRamp(18, 1.0, 22, 4.0))),
            routeLayer("route-other", p.routeMuted, current = false),
            routeLayer("route", p.route, current = true),
            buildJsonObject {
                put("id", "route-ends")
                put("type", "circle")
                put("source", ROUTE_SOURCE)
                put("filter", expr("==", JsonArray(listOf(JsonPrimitive("geometry-type"))), JsonPrimitive("Point")))
                putJsonObject("paint") {
                    put("circle-radius", 7)
                    put("circle-color", expr("match", get("role"), JsonPrimitive("start"), JsonPrimitive(p.routeStart), JsonPrimitive(p.route)))
                    put("circle-stroke-width", 2)
                    put("circle-stroke-color", p.halo)
                }
            },
            layer(
                "poi", "circle", "poi", filters["poi"],
                mapOf("circle-radius" to JsonPrimitive(4), "circle-color" to JsonPrimitive(p.poi)),
            ),
            buildJsonObject {
                put("id", "selection")
                put("type", "circle")
                put("source", SELECTION_SOURCE)
                putJsonObject("paint") {
                    put("circle-radius", 10)
                    put("circle-color", p.selection)
                    put("circle-stroke-width", 3)
                    put("circle-stroke-color", p.halo)
                }
            },
            buildJsonObject {
                put("id", "labels")
                put("type", "symbol")
                put("source", PLAN_SOURCE)
                put("source-layer", "area_name")
                put("minzoom", 17.5)
                put("filter", filters.getValue("labels"))
                putJsonObject("layout") {
                    put("text-field", expr("coalesce", get("ref"), get("name")))
                    putJsonArray("text-font") { add(FONT) }
                    put("text-size", zoomRamp(18, 10.0, 21, 14.0))
                    put("text-optional", true)
                }
                putJsonObject("paint") {
                    put("text-color", p.label)
                    put("text-halo-color", p.halo)
                    put("text-halo-width", 1.2)
                }
            },
        )
    }

    private fun layer(id: String, type: String, sourceLayer: String? = null, filter: JsonElement? = null, paint: Map<String, JsonElement>) =
        buildJsonObject {
            put("id", id)
            put("type", type)
            if (sourceLayer != null) {
                put("source", PLAN_SOURCE)
                put("source-layer", sourceLayer)
            }
            if (filter != null) put("filter", filter)
            put("paint", JsonObject(paint))
        }

    private fun routeLayer(id: String, color: String, current: Boolean) = buildJsonObject {
        put("id", id)
        put("type", "line")
        put("source", ROUTE_SOURCE)
        put("filter", expr("all", expr("==", JsonArray(listOf(JsonPrimitive("geometry-type"))), JsonPrimitive("LineString")), expr("==", get("current"), JsonPrimitive(current))))
        putJsonObject("layout") {
            put("line-cap", "round")
            put("line-join", "round")
        }
        putJsonObject("paint") {
            put("line-color", color)
            put("line-width", zoomRamp(18, if (current) 4.0 else 3.0, 22, if (current) 10.0 else 6.0))
        }
    }

    private fun zoomRamp(z1: Int, v1: Double, z2: Int, v2: Double) =
        expr("interpolate", JsonArray(listOf(JsonPrimitive("linear"))), JsonArray(listOf(JsonPrimitive("zoom"))), JsonPrimitive(z1), JsonPrimitive(v1), JsonPrimitive(z2), JsonPrimitive(v2))

    private fun get(name: String) = JsonArray(listOf(JsonPrimitive("get"), JsonPrimitive(name)))

    private fun expr(op: String, vararg args: JsonElement) = JsonArray(listOf(JsonPrimitive(op)) + args)

    private const val PLAN_SOURCE = "plan"
    private const val FONT = "Noto Sans Regular"
}

/** The plan's colours. */
internal data class MapPalette(
    val background: String,
    val footprint: String,
    val floor: String,
    val corridor: String,
    val room: String,
    val classroom: String,
    val outline: String,
    val wall: String,
    val label: String,
    val halo: String,
    val poi: String,
    val route: String,
    val routeMuted: String,
    val routeStart: String,
    val selection: String,
) {
    companion object {
        // The scheme roles of shared/designsystem/tokens/itmo-tokens.json: surfaces for the floor, primary for the way.
        val LIGHT = MapPalette(
            background = "#E2E2E9", footprint = "#D9D9E0", floor = "#F9F9FF", corridor = "#F9F9FF",
            room = "#EDEDF4", classroom = "#E0E2EC", outline = "#C4C6D0", wall = "#74777F", label = "#1A1C20",
            halo = "#FFFFFF", poi = "#705574", route = "#425E91", routeMuted = "#ABC7FF", routeStart = "#1A1C20",
            selection = "#425E91",
        )
        val DARK = MapPalette(
            background = "#0C0E13", footprint = "#191C20", floor = "#111318", corridor = "#111318",
            room = "#1E2025", classroom = "#282A2F", outline = "#44474E", wall = "#8E9099", label = "#E2E2E9",
            halo = "#111318", poi = "#DDBCE0", route = "#ABC7FF", routeMuted = "#425E91", routeStart = "#E2E2E9",
            selection = "#ABC7FF",
        )
    }
}

/** The GeoJSON of the map's `route` and `selection` sources. */
internal object CampusMapGeoJson {
    val EMPTY: String = collection(emptyList())

    /** [route]'s parts on [level]: the [current] part bold, the rest muted; the ends where they are on [level]. */
    fun route(route: IndoorRoute?, level: Int, current: RoutePart?): String {
        if (route == null || route.edges.isEmpty()) return EMPTY
        val lines = route.parts.filter { it.onLevel(level) }.map { part ->
            feature("LineString", coordinates(part), mapOf("current" to JsonPrimitive(part == current)))
        }
        val ends = listOfNotNull(
            route.start?.takeIf { it.level == level }?.let { point(it.longitude, it.latitude, "start") },
            route.end?.takeIf { it.level == level }?.let { point(it.longitude, it.latitude, "end") },
        )
        return collection(lines + ends)
    }

    fun selection(room: MapRoom?, level: Int): String =
        if (room == null || room.level != level) EMPTY else collection(listOf(point(room.longitude, room.latitude, "room")))

    private fun RoutePart.onLevel(level: Int): Boolean = this.level == level ||
        (type == RoutePartType.STAIRS_UP || type == RoutePartType.STAIRS_DOWN) && edges.first().from.level == level

    private fun coordinates(part: RoutePart) = buildJsonArray {
        (listOf(part.edges.first().from) + part.edges.map { it.to }).forEach { node ->
            addJsonArray {
                add(node.longitude)
                add(node.latitude)
            }
        }
    }

    private fun point(longitude: Double, latitude: Double, role: String) =
        feature("Point", buildJsonArray { add(longitude); add(latitude) }, mapOf("role" to JsonPrimitive(role)))

    private fun feature(type: String, coordinates: JsonArray, properties: Map<String, JsonElement>) = buildJsonObject {
        put("type", "Feature")
        putJsonObject("geometry") {
            put("type", type)
            put("coordinates", coordinates)
        }
        put("properties", JsonObject(properties))
    }

    private fun collection(features: List<JsonObject>) = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(features))
    }.toString()
}
