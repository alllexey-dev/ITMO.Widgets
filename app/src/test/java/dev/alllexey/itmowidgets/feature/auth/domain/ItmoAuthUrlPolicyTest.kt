package dev.alllexey.itmowidgets.feature.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItmoAuthUrlPolicyTest {

    @Test
    fun `navigates to any https page including third-party sign-in providers`() {
        assertTrue(ItmoAuthUrlPolicy.isNavigable("https://my.itmo.ru/"))
        assertTrue(
            ItmoAuthUrlPolicy.isNavigable(
                "https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth"
            )
        )
        assertTrue(ItmoAuthUrlPolicy.isNavigable("https://oauth.vk.com/authorize?client_id=1"))
        assertTrue(ItmoAuthUrlPolicy.isNavigable("https://id.vk.com/auth"))
        assertFalse(ItmoAuthUrlPolicy.isNavigable("http://my.itmo.ru/"))
        assertFalse(ItmoAuthUrlPolicy.isNavigable("file:///tmp/login.html"))
        assertFalse(ItmoAuthUrlPolicy.isNavigable("vk://authorize"))
        assertFalse(ItmoAuthUrlPolicy.isNavigable("javascript:alert(1)"))
        assertFalse(ItmoAuthUrlPolicy.isNavigable("https:///no-host"))
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
