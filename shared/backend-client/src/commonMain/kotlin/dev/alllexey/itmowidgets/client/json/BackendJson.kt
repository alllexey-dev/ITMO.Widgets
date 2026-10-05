package dev.alllexey.itmowidgets.client.json

import kotlinx.serialization.json.Json

/**
 * The one Json of the client (SP-02 config A). Unknown keys are ignored and nulls are omitted on encode, but no
 * `coerceInputValues` and no `isLenient`: a `null` or unknown value must never turn into a declared default, so a
 * missing privacy or capability field fails decoding instead of widening access.
 */
internal val BackendJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
