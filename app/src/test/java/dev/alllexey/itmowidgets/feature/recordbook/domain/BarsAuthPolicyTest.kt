package dev.alllexey.itmowidgets.feature.recordbook.domain

import org.junit.Assert.*
import org.junit.Test

class BarsAuthPolicyTest {
    @Test fun `accepts only the exact HTTPS callback and matching state`() {
        assertEquals("test-code", BarsAuthPolicy.authorizationCode("${BarsAuthPolicy.CALLBACK}?code=test-code&state=test-state&iss=https%3A%2F%2Fid.itmo.ru%2Fauth%2Frealms%2Fitmo", "test-state"))
        assertNull(BarsAuthPolicy.authorizationCode("${BarsAuthPolicy.CALLBACK}?code=test-code&state=other", "test-state"))
    }
    @Test fun `rejects duplicate parameters errors fragments and foreign issuer`() {
        listOf("code=a&code=b&state=s", "code=a&state=s&state=s", "error=denied&code=a&state=s", "code=a&state=s&iss=https://example.com", "code=a&state=s#code=b", "code=%ZZ&state=s")
            .forEach { assertNull(BarsAuthPolicy.authorizationCode("${BarsAuthPolicy.CALLBACK}?$it", "s")) }
    }
    @Test fun `rejects deceptive hosts credentials ports and paths`() {
        listOf("http://bars.itmo.ru/rest/login", "https://bars.itmo.ru.evil.test/rest/login", "https://user@bars.itmo.ru/rest/login", "https://bars.itmo.ru:8443/rest/login", "https://bars.itmo.ru/rest/login/", "https://bars.itmo.ru/rest/%6cogin")
            .forEach { assertFalse(it, BarsAuthPolicy.isCallback(it)) }
        assertFalse(BarsAuthPolicy.isAllowedPage("file:///tmp/login"))
        assertTrue(BarsAuthPolicy.isAllowedPage("https://id.itmo.ru/auth/realms/itmo/login-actions/authenticate"))
    }
    @Test fun `login URL binds the attempt and never supplies another redirect`() {
        val url = BarsAuthPolicy.loginUrl("state & one")
        assertTrue(url.startsWith(BarsAuthPolicy.ISSUER))
        assertTrue(url.contains("client_id=bars"))
        assertTrue(url.contains("state=state+%26+one"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fbars.itmo.ru%2Frest%2Flogin"))
    }
}
