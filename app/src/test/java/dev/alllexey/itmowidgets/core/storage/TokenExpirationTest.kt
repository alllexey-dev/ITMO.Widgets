package dev.alllexey.itmowidgets.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenExpirationTest {

    @Test
    fun `converts lifetime seconds to absolute milliseconds`() {
        assertEquals(
            1_500_000L,
            calculateTokenExpiration(
                nowMillis = 1_000_000L,
                lifetimeSeconds = 500L
            )
        )
    }

    @Test
    fun `saturates expiration when arithmetic overflows`() {
        assertEquals(
            Long.MAX_VALUE,
            calculateTokenExpiration(
                nowMillis = Long.MAX_VALUE,
                lifetimeSeconds = Long.MAX_VALUE
            )
        )
    }
}
