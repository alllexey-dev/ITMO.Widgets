package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomTextsTest {
    @Test
    fun aBareNumberSaysWhatTheRoomIs() {
        assertEquals(
            UiText.Joined(listOf(UiText.Res(MapStrings.map_kind_education), floor(4)), " · "),
            RoomTexts.subtitle(room(number = "1404", name = "1404", kind = "education")),
        )
    }

    @Test
    fun aNameOfItsOwnIsNotRepeatedByTheKind() {
        assertEquals(
            UiText.Joined(listOf(UiText.Dynamic("Столовая"), floor(1)), " · "),
            RoomTexts.subtitle(room(number = "1110", name = "Столовая", kind = "canteen", level = 0)),
        )
    }

    private fun floor(number: Int) = UiText.Res(MapStrings.map_floor, listOf(number))

    private fun room(number: String?, name: String?, kind: String, level: Int = 3) =
        MapRoom(1, 13, level, "room", kind, name, number, null, 30.0, 59.0)
}
