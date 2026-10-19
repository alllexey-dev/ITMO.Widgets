package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.feature.map.domain.graph.GraphNode
import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorRoute
import dev.alllexey.itmowidgets.feature.map.domain.graph.RoutePart
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The MapLibre style of both platforms (docs/features/map.md "MapLibre"): ITMO's own campus style ([ITMO_STYLE]) over
 * the local MBTiles file, with the `route` and `active` GeoJSON sources the campus app fills, the selection layers
 * of itmo-map-web, Noto Sans instead of the app's SF Pro glyphs, and every legacy filter turned into an expression.
 * Floors and the selected room switch by per-layer filters ([filters]); the icons are [SPRITES], added by each
 * platform at [SPRITE_PIXEL_RATIO].
 */
object CampusMapStyle {
    const val ROUTE_SOURCE = "route"
    const val SELECTION_SOURCE = "active"

    /** The campus app's icons (72 px PNGs drawn for a pixel ratio of 3), bundled as `map_sprites/<name>.png`. */
    val SPRITES: List<String> = listOf(
        "accelerator", "amenity.restroom.female", "amenity.restroom.male", "amenity.restroom.unisex",
        "amenity.restroom.wheelchair", "amenity.water", "auditory", "canteen", "cash", "cloakroom", "conferenceroom",
        "coworking", "default", "elevator", "entrance", "gym", "healthcare", "laboratory", "library", "lockerroom.female",
        "lockerroom.male", "meetingroom", "museum", "office", "office.study", "parking.bike", "pc", "printer", "science",
        "serviceroom", "stairs.down", "stairs", "stairs.up", "studoffice", "walkway", "yard",
    )
    const val SPRITE_PIXEL_RATIO: Int = 3

    /** The layers a tap looks at (itmo-map-web's); their features carry the plan room id in `id`. */
    val TAP_LAYERS: List<String> = listOf(
        "room", "room_restroom", "room_student_places", "room_services", "room_halls", "room_sport",
        "area_name_auditory", "area_name_public", "poi",
    )

    /** The style of [scene]'s building on its level with its selected room; [dark] follows the app's dark theme. */
    fun json(scene: MapScene, dark: Boolean = false): String {
        val template = Json.parseToJsonElement(ITMO_STYLE).jsonObject
        val filters = filters(scene.level, scene.selectedRoomId)
        val layers = JsonArray(layers().map { layer -> layer.styled(filters[layer.id]) })
        return JsonObject(
            template + mapOf(
                "glyphs" to JsonPrimitive(scene.glyphsUrl),
                "sources" to sources(scene),
                "layers" to if (dark) layers.darkened() as JsonArray else layers,
            ),
        ).toString()
    }

    /** [this] with every colour of the light template swapped for its [DARK_COLORS] twin. */
    private fun JsonElement.darkened(): JsonElement = when (this) {
        is JsonArray -> JsonArray(map { it.darkened() })
        is JsonObject -> JsonObject(mapValues { (_, value) -> value.darkened() })
        is JsonPrimitive -> if (isString) DARK_COLORS[content]?.let(::JsonPrimitive) ?: this else this
    }

    /** Layer id to its filter on [level] with [selectedRoomId], as MapLibre expression JSON. */
    fun filters(level: Int, selectedRoomId: Long?): Map<String, String> = expressions.mapValues { (_, filter) ->
        filter.bind(level.toString(), selectedRoomId?.toString() ?: NO_ROOM).toString()
    }

    /** The template's layers with the selection highlight under the walls. */
    private fun layers(): List<JsonObject> {
        val layers = Json.parseToJsonElement(ITMO_STYLE).jsonObject.getValue("layers").jsonArray.map { it.jsonObject }
        val walls = layers.indexOfFirst { it.id == WALLS_LAYER }
        return layers.take(walls) + selectionLayers() + layers.drop(walls)
    }

    /** Every filtered layer's filter as an expression, with the template's level and room placeholders. */
    private val expressions: Map<String, JsonElement> by lazy {
        layers().mapNotNull { layer -> layer["filter"]?.let { layer.id to expression(it) } }.toMap()
    }

    private val JsonObject.id: String get() = getValue("id").jsonPrimitive.content

    private fun JsonObject.styled(filter: String?): JsonObject {
        val layout = this["layout"]?.jsonObject?.let { layout ->
            if ("text-font" in layout) JsonObject(layout + ("text-font" to JsonArray(listOf(JsonPrimitive(FONT))))) else layout
        }
        return JsonObject(
            this + listOfNotNull(
                layout?.let { "layout" to it },
                filter?.let { "filter" to Json.parseToJsonElement(it) },
            ),
        )
    }

    private fun sources(scene: MapScene) = buildJsonObject {
        putJsonObject(PLAN_SOURCE) {
            put("type", "vector")
            put("url", "mbtiles://${scene.tilesPath}")
        }
        put(ROUTE_SOURCE, geoJsonSource(scene.route))
        put(SELECTION_SOURCE, geoJsonSource(scene.selection))
    }

    private fun geoJsonSource(data: String) = buildJsonObject {
        put("type", "geojson")
        put("data", Json.parseToJsonElement(data))
    }

    /** itmo-map-web's highlight of the selected room, under the walls. */
    private fun selectionLayers(): List<JsonObject> = listOf(
        selection("selected_fill", "fill", mapOf("fill-color" to JsonPrimitive(ACCENT), "fill-opacity" to JsonPrimitive(0.12))),
        selection("selected_line", "line", mapOf("line-color" to JsonPrimitive(ACCENT), "line-width" to JsonPrimitive(2))),
    )

    private fun selection(id: String, type: String, paint: Map<String, JsonElement>) = buildJsonObject {
        put("id", id)
        put("type", type)
        put("source", PLAN_SOURCE)
        put("source-layer", "area")
        put("filter", Json.parseToJsonElement("""["all",["==","level","0"],["==","id","-1"]]"""))
        put("paint", JsonObject(paint))
    }

    /**
     * A legacy filter (`all`, `==`, `!=`, `in`, `!in` over string properties, the only forms the template uses) as an
     * expression: the `setFilter` of both SDKs takes expressions only.
     */
    private fun expression(filter: JsonElement): JsonElement {
        val parts = filter.jsonArray
        val op = parts[0].jsonPrimitive.content
        if (op == "all") return JsonArray(listOf(JsonPrimitive("all")) + parts.drop(1).map(::expression))
        val get = JsonArray(listOf(JsonPrimitive("get"), parts[1]))
        return when (op) {
            "==", "!=" -> JsonArray(listOf(JsonPrimitive(op), get, parts[2]))
            "in", "!in" -> JsonArray(
                // `match` labels must be unique; the template lists `stairs` twice in one filter.
                listOf(JsonPrimitive("match"), get, JsonArray(parts.drop(2).distinct()), JsonPrimitive(op == "in"), JsonPrimitive(op != "in")),
            )
            else -> throw IllegalArgumentException("Unsupported legacy filter $op")
        }
    }

    /** [this] expression with every `level` and `id` comparison set to [level] and [room]. */
    private fun JsonElement.bind(level: String, room: String): JsonElement {
        if (this !is JsonArray) return this
        val op = (getOrNull(0) as? JsonPrimitive)?.content
        val key = (getOrNull(1) as? JsonArray)?.takeIf { it.size == 2 && it[0] == JsonPrimitive("get") }?.get(1)?.jsonPrimitive?.content
        if ((op == "==" || op == "!=") && size == 3) {
            when (key) {
                "level" -> return JsonArray(listOf(this[0], this[1], JsonPrimitive(level)))
                "id" -> return JsonArray(listOf(this[0], this[1], JsonPrimitive(room)))
            }
        }
        return JsonArray(map { it.bind(level, room) })
    }

    private const val PLAN_SOURCE = "openmaptiles"
    private const val WALLS_LAYER = "borders"
    private const val NO_ROOM = "-1"
    private const val ACCENT = "#1846C7"

    /**
     * The dark map: every colour of the light template (and [ACCENT]) to its dark twin. Rooms sit above the
     * footprint, corridors above rooms and walls above both, as in the light plan; the kinds keep their hue (toilets
     * purple, food and coworking orange, services blue, halls yellow, sport red) at a dark tone that the light labels
     * read on. The route and the selection take a lighter blue that stands out on the dark plan.
     */
    internal val DARK_COLORS: Map<String, String> = mapOf(
        "rgba(230, 233, 235, 1)" to "rgba(22, 24, 27, 1)",
        "rgba(210, 213, 215, 1)" to "rgba(32, 35, 39, 1)",
        "rgba(233, 247, 232, 1)" to "rgba(28, 44, 32, 1)",
        "rgba(255, 255, 255, 1)" to "rgba(58, 63, 70, 1)",
        "rgba(244, 244, 245, 1)" to "rgba(44, 48, 54, 1)",
        "rgba(246, 229, 255, 1)" to "rgba(72, 50, 94, 1)",
        "rgba(255, 240, 229, 1)" to "rgba(94, 62, 34, 1)",
        "rgba(229, 244, 255, 1)" to "rgba(32, 62, 90, 1)",
        "rgba(255, 248, 229, 1)" to "rgba(86, 74, 34, 1)",
        "rgba(255, 229, 230, 1)" to "rgba(92, 40, 46, 1)",
        "rgba(183, 196, 204, 1)" to "rgba(104, 116, 128, 1)",
        "rgba(96, 96, 96, 1)" to "rgba(214, 218, 224, 1)",
        "hsla(0, 0%, 100%, 0.75)" to "rgba(22, 24, 27, 0.8)",
        ACCENT to "#7C9CFF",
        "rgba(24, 70, 199, 0.25)" to "rgba(124, 156, 255, 0.3)",
        "#FFFFFF" to "#0E1A45",
    )

    /** The closest weight to the campus app's SF Pro Text Semibold among the mirror's open fonts (no SF Pro). */
    private const val FONT = "Noto Sans Bold"
}

/** The GeoJSON of the map's `route` and `active` sources, with the properties the campus app's style reads. */
internal object CampusMapGeoJson {
    val EMPTY: String = collection(emptyList())

    /**
     * [route] as the campus app draws a step (itmo-map-web `Route.geoJson(level, activeStep)`): a line per part,
     * `visible` and `active` on [level], `step` current for [current]; the start and end points.
     */
    fun route(route: IndoorRoute?, level: Int, current: RoutePart?): String {
        val start = route?.start ?: return EMPTY
        val end = route.end ?: return EMPTY
        fun onLevel(l: Int) = JsonPrimitive((l == level).toString())
        fun state(l: Int) = JsonPrimitive(if (l == level) "active" else "inactive")
        fun point(type: String, node: GraphNode) = feature(
            "Point",
            position(node.longitude, node.latitude),
            mapOf("type" to JsonPrimitive(type), "visible" to onLevel(node.level), "state" to state(node.level), "level" to JsonPrimitive(node.level.toString())),
        )
        val lines = route.parts.map { part ->
            // As the campus app: a part belongs to the floor it arrives at, a stair included.
            val shown = part.level
            feature(
                "LineString",
                JsonArray((listOf(part.edges.first().from) + part.edges.map { it.to }).map { position(it.longitude, it.latitude) }),
                mapOf(
                    "type" to JsonPrimitive("path"),
                    "level" to JsonPrimitive(part.level.toString()),
                    "visible" to onLevel(shown),
                    "state" to state(shown),
                    "step" to JsonPrimitive(if (part == current) "current" else "other"),
                ),
            )
        }
        return collection(listOf(point("start-point", start), point("end-point", end)) + lines)
    }

    /** The selected room as the campus app's `active` point: its kind's icon over its name, on its level. */
    fun selection(room: MapRoom?): String {
        room ?: return EMPTY
        val properties = buildMap {
            put("id", JsonPrimitive(room.id.toString()))
            put("level", JsonPrimitive(room.level.toString()))
            room.kind?.let { put("subclass", JsonPrimitive(it)) }
            room.name?.let { put("name", JsonPrimitive(it)) }
        }
        return collection(listOf(feature("Point", position(room.longitude, room.latitude), properties)))
    }

    private fun position(longitude: Double, latitude: Double) = buildJsonArray {
        add(longitude)
        add(latitude)
    }

    private fun feature(type: String, coordinates: JsonElement, properties: Map<String, JsonElement>) = buildJsonObject {
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
