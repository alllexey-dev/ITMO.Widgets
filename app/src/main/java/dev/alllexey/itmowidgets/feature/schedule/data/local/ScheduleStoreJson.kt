package dev.alllexey.itmowidgets.feature.schedule.data.local

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * The JSON of the schedule's files, which 2.2 wrote with Gson (recipe `kotlinx-file-store`). Gson wrote every non-null
 * field and omitted nulls, and its reader left absent fields null: absent keys read as null or the default, nulls stay
 * absent, and defaults such as `format` are always written, so each side reads the other's files.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val ScheduleStoreJson = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}
