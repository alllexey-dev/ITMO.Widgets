package dev.alllexey.itmowidgets.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserScreenArgsTest {
    @Test
    fun `missing and nonpositive person identifiers cannot open a profile`() {
        listOf(null, 0L, -5L).forEach { assertNull(UserScreenArgs.profileIsu(it)) }
    }

    @Test
    fun `both supported identifier boundaries are preserved`() {
        assertEquals(1, UserScreenArgs.profileIsu(1L))
        assertEquals(Int.MAX_VALUE, UserScreenArgs.profileIsu(Int.MAX_VALUE.toLong()))
    }

    @Test
    fun `identifiers above the integer range are rejected without overflow`() {
        assertNull(UserScreenArgs.profileIsu(Int.MAX_VALUE + 1L))
    }
}
