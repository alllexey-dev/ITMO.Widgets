package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.schedule_building_birzha
import dev.alllexey.itmowidgets.shared.core.schedule_building_chaika
import dev.alllexey.itmowidgets.shared.core.schedule_building_griva
import dev.alllexey.itmowidgets.shared.core.schedule_building_kronva
import dev.alllexey.itmowidgets.shared.core.schedule_building_lomo
import dev.alllexey.itmowidgets.shared.core.schedule_building_pesochka
import dev.alllexey.itmowidgets.shared.core.schedule_building_vyazma
import dev.alllexey.itmowidgets.shared.core.schedule_room_assembly_hall
import org.jetbrains.compose.resources.StringResource

/** Short campus names for the buildings MyITMO spells out in full; unknown names stay as they are. */
fun buildingShortTitle(raw: String, maxLength: Int? = null): UiText {
    val normalizedName = raw.lowercase()
    val knownTitle = BUILDING_TITLES.entries.firstOrNull { (part, _) -> part in normalizedName }?.value
    return knownTitle?.let { UiText.Res(it) } ?: UiText.Dynamic(maxLength?.let(raw::take) ?: raw)
}

/** "1506" or "1506/1" out of MyITMO's room text, "Акт. зал" for the assembly hall; anything else as it is. */
fun roomShortTitle(raw: String): UiText {
    val normalizedName = raw.lowercase()
    if ("актовый" in normalizedName) return UiText.Res(Res.string.schedule_room_assembly_hall)
    return UiText.Dynamic(ROOM_WITH_PART_REGEX.find(normalizedName)?.value ?: ROOM_REGEX.find(normalizedName)?.value ?: raw)
}

private val BUILDING_TITLES: Map<String, StringResource> = mapOf(
    "кронв" to Res.string.schedule_building_kronva,
    "ломо" to Res.string.schedule_building_lomo,
    "гривц" to Res.string.schedule_building_griva,
    "бирж" to Res.string.schedule_building_birzha,
    "песоч" to Res.string.schedule_building_pesochka,
    "чайк" to Res.string.schedule_building_chaika,
    "вязем" to Res.string.schedule_building_vyazma
)
private val ROOM_WITH_PART_REGEX = Regex("[0-9]{4}/[0-9]")
private val ROOM_REGEX = Regex("[0-9]{4}")
