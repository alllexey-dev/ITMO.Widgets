package dev.alllexey.itmowidgets.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSummaryMappingTest {

    @Test
    fun `maps each viewer capability independently without widening access`() {
        for (schedule in listOf(false, true)) {
            for (sport in listOf(false, true)) {
                val source = user(UserCapabilities(schedule, sport))

                assertEquals(
                    UserSharing(sport = sport, schedule = schedule),
                    source.toUserSummary().sharing
                )
            }
        }
    }

    @Test
    fun `normalizes identity metadata without changing permissions`() {
        val mapped = user(UserCapabilities(false, true)).toUserSummary()

        assertEquals(123456, mapped.isu)
        assertEquals("Тестовый пользователь", mapped.name)
        assertEquals("https://example.invalid/avatar", mapped.pictureUrl)
        assertEquals(listOf(UserGroup("M3100", 1, "ФТМИ")), mapped.groups)
        assertEquals(UserSharing(sport = true, schedule = false), mapped.sharing)
    }

    @Test
    fun `absent and blank pictures remain absent and empty groups stay empty`() {
        for (picture in listOf(null, "", "  \t\n")) {
            val source = user(UserCapabilities(false, false)).copy(
                pictureUrl = picture,
                groups = emptyList()
            )

            val mapped = source.toUserSummary()

            assertNull(mapped.pictureUrl)
            assertEquals(emptyList<UserGroup>(), mapped.groups)
            assertEquals(UserSharing(sport = false, schedule = false), mapped.sharing)
        }
    }

    @Test
    fun `primary group is the highest course then the first by name`() {
        val summary = user(UserCapabilities(false, false)).toUserSummary().copy(
            groups = listOf(UserGroup("P3119", 1, "ФПИ"), UserGroup("Z3244", 2, "ФизФ"), UserGroup("P3219", 2, "ФПИ"))
        )

        assertEquals(UserGroup("P3219", 2, "ФПИ"), summary.primaryGroup())
        assertNull(summary.copy(groups = emptyList()).primaryGroup())
    }

    private fun user(capabilities: UserCapabilities) = UserData(
        isu = 123456,
        name = "  Тестовый пользователь  ",
        pictureUrl = "  https://example.invalid/avatar  ",
        groups = listOf(GroupData(name = " M3100 ", course = 1, facultyShortName = " ФТМИ ")),
        capabilities = capabilities
    )
}
