package dev.alllexey.itmowidgets.client.support

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.fail
import kotlin.time.Instant

/**
 * Semantic JSON equality: object key order is ignored, a `null` member equals an absent one (Jackson writes nulls,
 * the client omits them), arrays compare in order, and two strings that both parse as a wire date-time with an
 * offset are equal when they name the same instant (the client re-encodes `Instant` in UTC while fixtures carry
 * `+03:00` and Gson's dropped `:00` seconds). Every other value compares exactly, so `1` and `1.0`, or `"1"` and
 * `1`, differ.
 */
fun assertJsonEquals(expected: String, actual: String) {
    val difference = difference("$", parse(expected, "expected"), parse(actual, "actual"))
    if (difference != null) fail("JSON differs at $difference\nexpected: $expected\nactual:   $actual")
}

private fun parse(text: String, side: String): JsonElement =
    try {
        BackendJson.parseToJsonElement(text)
    } catch (error: SerializationException) {
        fail("The $side text is not JSON: ${error.message}")
    }

/** The path of the first difference with both values, or `null` when [expected] and [actual] are equal. */
private fun difference(path: String, expected: JsonElement, actual: JsonElement): String? = when {
    expected is JsonObject && actual is JsonObject -> objectDifference(path, expected, actual)
    expected is JsonArray && actual is JsonArray -> arrayDifference(path, expected, actual)
    expected is JsonPrimitive && actual is JsonPrimitive ->
        if (primitivesEqual(expected, actual)) null else "$path: $expected != $actual"
    else -> "$path: $expected != $actual"
}

private fun objectDifference(path: String, expected: JsonObject, actual: JsonObject): String? {
    val expectedKeys = expected.nonNullKeys()
    val actualKeys = actual.nonNullKeys()
    if (expectedKeys != actualKeys) return "$path: keys ${expectedKeys.sorted()} != ${actualKeys.sorted()}"
    return expectedKeys.firstNotNullOfOrNull { key ->
        difference("$path.$key", expected.getValue(key), actual.getValue(key))
    }
}

private fun JsonObject.nonNullKeys(): Set<String> = filterValues { it != JsonNull }.keys

private fun arrayDifference(path: String, expected: JsonArray, actual: JsonArray): String? {
    if (expected.size != actual.size) return "$path: ${expected.size} elements != ${actual.size}"
    return expected.indices.firstNotNullOfOrNull { index ->
        difference("$path[$index]", expected[index], actual[index])
    }
}

private fun primitivesEqual(expected: JsonPrimitive, actual: JsonPrimitive): Boolean {
    if (expected == actual) return true
    if (!expected.isString || !actual.isString) return false
    val expectedInstant = wireInstantOrNull(expected) ?: return false
    return expectedInstant == wireInstantOrNull(actual)
}

private fun wireInstantOrNull(value: JsonPrimitive): Instant? =
    try {
        BackendJson.decodeFromJsonElement(WireInstantSerializer, value)
    } catch (_: SerializationException) {
        null
    }
