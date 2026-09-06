package dev.alllexey.itmowidgets.feature.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItmoAuthUrlPolicyTest {

    @Test
    fun `allows only official https login hosts`() {
        assertTrue(ItmoAuthUrlPolicy.isAllowed("https://my.itmo.ru/"))
        assertTrue(
            ItmoAuthUrlPolicy.isAllowed(
                "https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth"
            )
        )
        assertFalse(ItmoAuthUrlPolicy.isAllowed("http://my.itmo.ru/"))
        assertFalse(ItmoAuthUrlPolicy.isAllowed("https://my.itmo.ru.example.com/"))
        assertFalse(ItmoAuthUrlPolicy.isAllowed("file:///tmp/login.html"))
    }

    @Test
    fun `accepts tokens only on the official callback`() {
        assertTrue(
            ItmoAuthUrlPolicy.isTokenCallback(
                "https://my.itmo.ru/login/callback?state=test"
            )
        )
        assertFalse(ItmoAuthUrlPolicy.isTokenCallback("https://my.itmo.ru/"))
        assertFalse(
            ItmoAuthUrlPolicy.isTokenCallback(
                "https://my.itmo.ru/login/callback/extra"
            )
        )
        assertFalse(
            ItmoAuthUrlPolicy.isTokenCallback(
                "https://id.itmo.ru/login/callback"
            )
        )
    }
}
