package dev.alllexey.itmowidgets.feature.schedule.ui

import android.content.Context
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room

fun Building.shortTitle(context: Context, maxLength: Int? = null): String {
    val normalizedName = raw.lowercase()
    val knownTitle = BUILDING_TITLES.entries.firstOrNull { (part, _) ->
        part in normalizedName
    }?.value?.let(context::getString)
    return knownTitle ?: maxLength?.let(raw::take) ?: raw
}

fun Room.shortTitle(context: Context): String {
    val normalizedName = raw.lowercase()
    if ("актовый" in normalizedName) {
        return context.getString(R.string.schedule_room_assembly_hall)
    }
    return ROOM_WITH_PART_REGEX.find(normalizedName)?.value
        ?: ROOM_REGEX.find(normalizedName)?.value
        ?: raw
}

private val BUILDING_TITLES: Map<String, Int> = mapOf(
    "кронв" to R.string.schedule_building_kronva,
    "ломо" to R.string.schedule_building_lomo,
    "гривц" to R.string.schedule_building_griva,
    "бирж" to R.string.schedule_building_birzha,
    "песоч" to R.string.schedule_building_pesochka,
    "чайк" to R.string.schedule_building_chaika,
    "вязем" to R.string.schedule_building_vyazma
)
private val ROOM_WITH_PART_REGEX = Regex("[0-9]{4}/[0-9]")
private val ROOM_REGEX = Regex("[0-9]{4}")
