package dev.alllexey.itmowidgets.client.contract

import dev.alllexey.itmowidgets.client.json.BackendJson
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The parts of Backend's OpenAPI snapshot (`contract/openapi.json`, springdoc with BK-13's customiser) that the
 * conformance test reads: operations with their query parameters, and component schemas with their properties,
 * `required` lists, enums and sealed `oneOf` shapes.
 */
class OpenApi(val root: JsonObject) {

    val operations: List<Operation> = root.getValue("paths").jsonObject.flatMap { (path, item) ->
        item.jsonObject.filterKeys { it in HTTP_METHODS }.map { (method, operation) ->
            Operation(HttpMethod.parse(method.uppercase()), path, operation.jsonObject)
        }
    }

    private val schemas: JsonObject = root.getValue("components").jsonObject.getValue("schemas").jsonObject

    fun schema(name: String): Schema? = schemas[name]?.let { Schema(name, it.jsonObject) }

    companion object {
        private val HTTP_METHODS = setOf("get", "put", "post", "delete", "patch", "head", "options")

        fun parse(text: String): OpenApi = OpenApi(BackendJson.parseToJsonElement(text).jsonObject)
    }
}

/** One operation; [path] is the template, for example `/api/users/{isu}/friends`. */
class Operation(val method: HttpMethod, val path: String, json: JsonObject) {
    val id: String? = json["operationId"]?.jsonPrimitive?.contentOrNull

    private val parameters: List<JsonObject> = json["parameters"]?.jsonArray?.map { it.jsonObject }.orEmpty()

    val queryNames: Set<String> = parameters.filter { it.string("in") == "query" }.map { it.string("name")!! }.toSet()

    val requiredQueryNames: Set<String> =
        parameters.filter { it.string("in") == "query" && it["required"]?.jsonPrimitive?.contentOrNull == "true" }
            .map { it.string("name")!! }
            .toSet()

    private val segments = path.trim('/').split('/')

    /** Literal segments count; the most specific template wins when two match (`me/roles` over `{isu}/friends`). */
    val literalSegments: Int = segments.count { !it.isTemplate() }

    private val regex = Regex(
        segments.joinToString(separator = "/", prefix = "/") { if (it.isTemplate()) "[^/]+" else Regex.escape(it) },
    )

    /** Whether a request with [method] to the percent-encoded [encodedPath] is this operation. */
    fun matches(method: HttpMethod, encodedPath: String): Boolean = method == this.method && regex.matches(encodedPath)

    override fun toString(): String = "${method.value} $path"

    private fun String.isTemplate() = startsWith("{") && endsWith("}")
}

/** One component schema: an object with properties, or a sealed `oneOf` with a discriminator. */
class Schema(val name: String, private val json: JsonObject) {
    val properties: Map<String, Property> =
        json["properties"]?.jsonObject?.mapValues { (key, value) -> Property(key, value.jsonObject) }.orEmpty()

    val required: Set<String> = json["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()

    /** The `oneOf` schema names, empty for an object schema. */
    val oneOf: List<String> = json["oneOf"]?.jsonArray?.mapNotNull { it.jsonObject.ref() }.orEmpty()

    val discriminator: String? = json["discriminator"]?.jsonObject?.string("propertyName")

    /** Discriminator value to schema name. */
    val mapping: Map<String, String> = json["discriminator"]?.jsonObject?.get("mapping")?.jsonObject
        ?.mapValues { (_, value) -> value.jsonPrimitive.content.substringAfterLast('/') }
        .orEmpty()
}

/** One property of a [Schema]. */
class Property(val name: String, private val json: JsonObject) {

    /** `type` lists `null`, or `oneOf` has a `{"type":"null"}` branch. */
    val isNullable: Boolean = when (val type = json["type"]) {
        is JsonArray -> type.any { it.jsonPrimitive.content == "null" }
        else -> json["oneOf"]?.jsonArray?.any { it.jsonObject.string("type") == "null" } == true
    }

    /** The schema this property, its list items or its non-null `oneOf` branch refers to. */
    val ref: String? = json.ref()
        ?: json["items"]?.jsonObject?.ref()
        ?: json["oneOf"]?.jsonArray?.firstNotNullOfOrNull { it.jsonObject.ref() }

    /** The enum values of this property or of its list items, without `null`; `null` when it is no enum. */
    val enum: Set<String>? = (json["enum"] ?: json["items"]?.jsonObject?.get("enum"))?.jsonArray
        ?.filterNot { it is JsonNull }
        ?.map { it.jsonPrimitive.content }
        ?.toSet()
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.ref(): String? = string("\$ref")?.substringAfterLast('/')

/** A copy of this tree with [transform] applied to the element at [path] (object keys only); for seeded drifts. */
fun JsonElement.edited(path: List<String>, transform: (JsonElement) -> JsonElement): JsonElement {
    if (path.isEmpty()) return transform(this)
    val node = jsonObject
    val key = path.first()
    return JsonObject(node + (key to node.getValue(key).edited(path.drop(1), transform)))
}
