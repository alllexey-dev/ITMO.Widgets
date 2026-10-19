package dev.alllexey.itmowidgets.core.notification

import kotlinx.serialization.json.JsonElement

interface FcmPayloadHandler {
    val type: String

    /** The category of its notifications; the user's switch of it (`PushCategoryPreferences`) gates them. */
    val channel: String

    /** Handles one push and posts through [notifier] only, which drops everything while [channel] is off. */
    suspend fun handle(payload: JsonElement, notifier: AppNotifier)
}
