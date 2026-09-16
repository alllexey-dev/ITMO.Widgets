package dev.alllexey.itmowidgets.feature.web.domain

import org.junit.Assert.*
import org.junit.Test

class MyItmoWebPolicyTest {
    @Test fun `only official HTTPS origins are embedded`() {
        for (url in listOf("https://my.itmo.ru/", "https://my.itmo.ru:443/login?redirect=/",
            "https://id.itmo.ru/auth/realms/itmo", "HTTPS://MY.ITMO.RU/")) {
            assertTrue(url, MyItmoWebPolicy.isInternal(url))
            assertEquals(MyItmoWebPolicy.Navigation.INTERNAL, MyItmoWebPolicy.navigation(url, true, false))
        }
        for (url in listOf("http://my.itmo.ru/", "https://my.itmo.ru.evil.invalid/", "https://evilmy.itmo.ru/",
            "https://my.itmo.ru@evil.invalid/", "https://user@my.itmo.ru/", "https://my.itmo.ru:8443/",
            "https://my.itmo.ru./", "https://my%2eitmo.ru/", "//my.itmo.ru/", "file:///etc/passwd",
            "content://my.itmo.ru/", "javascript:alert(1)", "data:text/html,test", "intent://my.itmo.ru/",
            "https://my.itmo.ru\\@evil.invalid", "not a URL")) {
            assertFalse(url, MyItmoWebPolicy.isInternal(url))
            assertEquals(url, MyItmoWebPolicy.Navigation.BLOCKED, MyItmoWebPolicy.navigation(url, true, false))
        }
    }

    @Test fun `external links require an explicit main frame HTTPS gesture`() {
        val url = "https://example.invalid/"
        assertEquals(MyItmoWebPolicy.Navigation.EXTERNAL, MyItmoWebPolicy.navigation(url, true, true))
        assertEquals(MyItmoWebPolicy.Navigation.BLOCKED, MyItmoWebPolicy.navigation(url, false, true))
        assertEquals(MyItmoWebPolicy.Navigation.BLOCKED, MyItmoWebPolicy.navigation(url, true, false))
        for (blocked in listOf("http://example.invalid", "intent://example.invalid", "https://secret@example.invalid")) {
            assertEquals(MyItmoWebPolicy.Navigation.BLOCKED, MyItmoWebPolicy.navigation(blocked, true, true))
        }
    }
}
