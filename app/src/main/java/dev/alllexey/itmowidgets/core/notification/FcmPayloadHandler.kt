package dev.alllexey.itmowidgets.core.notification

import com.google.gson.JsonElement

interface FcmPayloadHandler {
    val type: String
    suspend fun handle(payload: JsonElement)
}
