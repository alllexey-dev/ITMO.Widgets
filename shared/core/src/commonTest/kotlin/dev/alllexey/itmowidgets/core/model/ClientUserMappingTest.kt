package dev.alllexey.itmowidgets.core.model

import dev.alllexey.itmowidgets.client.common.GroupData
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientUserMappingTest {

    @Test
    fun mapsEachViewerCapabilityIndependentlyWithoutWideningAccess() {
        for (schedule in listOf(false, true)) {
            for (sport in listOf(false, true)) {
                for (friends in listOf(false, true)) {
                    val mapped = user(UserCapabilities(schedule, sport, friends)).toUserSummary()

                    assertEquals(UserSharing(sport = sport, schedule = schedule, friends = friends), mapped.sharing)
                }
            }
        }
    }

    @Test
    fun trimsIdentityMetadataWithoutChangingPermissions() {
        val mapped = user(UserCapabilities(canViewSchedule = false, canViewSport = true, canViewFriends = false))
            .toUserSummary()

        assertEquals(123456, mapped.isu)
        assertEquals("Тестовый пользователь", mapped.name)
        assertEquals("https://example.invalid/avatar", mapped.pictureUrl)
        assertEquals(listOf(UserGroup("M3100", 1, "ФТМИ")), mapped.groups)
        assertEquals(UserSharing(sport = true, schedule = false, friends = false), mapped.sharing)
    }

    @Test
    fun absentAndBlankPicturesStayAbsentAndEmptyGroupsStayEmpty() {
        for (picture in listOf(null, "", "  \t\n")) {
            val source = user(UserCapabilities(false, false, false)).copy(pictureUrl = picture, groups = emptyList())

            val mapped = source.toUserSummary()

            assertNull(mapped.pictureUrl)
            assertEquals(emptyList(), mapped.groups)
        }
    }

    private fun user(capabilities: UserCapabilities) = UserData(
        isu = 123456,
        name = "  Тестовый пользователь  ",
        pictureUrl = "  https://example.invalid/avatar  ",
        groups = listOf(GroupData(name = " M3100 ", course = 1, facultyShortName = " ФТМИ ")),
        capabilities = capabilities,
    )
}
