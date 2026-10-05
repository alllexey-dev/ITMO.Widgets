package dev.alllexey.itmowidgets.core.notification

import kotlinx.serialization.json.JsonElement

interface FcmPayloadHandler {
    val type: String
    suspend fun handle(payload: JsonElement)
}
