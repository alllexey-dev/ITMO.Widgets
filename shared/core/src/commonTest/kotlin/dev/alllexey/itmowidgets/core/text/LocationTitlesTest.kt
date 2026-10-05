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
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationTitlesTest {

    @Test
    fun knownBuildingsTakeTheirCampusName() {
        mapOf(
            "Кронверкский проспект, 49" to Res.string.schedule_building_kronva,
            "ул. Ломоносова, 9" to Res.string.schedule_building_lomo,
            "Гривцова пер., 14" to Res.string.schedule_building_griva,
            "Биржевая линия, 14" to Res.string.schedule_building_birzha,
            "ул. Песочная, 14" to Res.string.schedule_building_pesochka,
            "ул. Чайковского, 11" to Res.string.schedule_building_chaika,
            "Вяземский пер., 5-7" to Res.string.schedule_building_vyazma
        ).forEach { (raw, title) -> assertEquals(UiText.Res(title), buildingShortTitle(raw, maxLength = 3)) }
    }

    @Test
    fun anUnknownBuildingStaysAsItIsOrCut() {
        assertEquals(UiText.Dynamic("Невский проспект, 1"), buildingShortTitle("Невский проспект, 1"))
        assertEquals(UiText.Dynamic("Невский пр"), buildingShortTitle("Невский проспект, 1", maxLength = 10))
    }

    @Test
    fun aRoomKeepsItsNumber() {
        assertEquals(UiText.Dynamic("1506"), roomShortTitle("Ауд. 1506"))
        assertEquals(UiText.Dynamic("2304/1"), roomShortTitle("2304/1 (лаб.)"))
        assertEquals(UiText.Res(Res.string.schedule_room_assembly_hall), roomShortTitle("Актовый зал"))
        assertEquals(UiText.Dynamic("Спортзал"), roomShortTitle("Спортзал"))
    }
}
