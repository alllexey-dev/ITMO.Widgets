package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsAuthorizationTest {
    @Test fun `accepts only LMS authorization state and matching callback`() {
        val request = "https://sso.tuxedo.se.ifmo.ru/realms/Newdex/protocol/openid-connect/auth" +
            "?client_id=newdex-backend&redirect_uri=https%3A%2F%2Flms.tuxedo.se.ifmo.ru%2Fsso%2Ftoken" +
            "&response_type=code&state=synthetic-state"
        assertEquals("synthetic-state", LmsAuthorization.stateFromAuthorization(request))
        assertEquals("synthetic-code", LmsAuthorization.callbackCode(
            "https://lms.tuxedo.se.ifmo.ru/sso/token?code=synthetic-code&state=synthetic-state", "synthetic-state"
        ))
        assertNull(LmsAuthorization.callbackCode("https://lms.tuxedo.se.ifmo.ru/sso/token?code=x&state=wrong", "synthetic-state"))
        assertNull(LmsAuthorization.callbackCode("https://lms.tuxedo.se.ifmo.ru.example.org/sso/token?code=x&state=synthetic-state", "synthetic-state"))
        assertNull(LmsAuthorization.callbackCode("https://lms.tuxedo.se.ifmo.ru/sso/token?code=x&state=synthetic-state&state=other", "synthetic-state"))
        assertNull(LmsAuthorization.callbackCode("https://user@lms.tuxedo.se.ifmo.ru/sso/token?code=x&state=synthetic-state", "synthetic-state"))
        assertNull(LmsAuthorization.stateFromAuthorization(request.replace("sso.tuxedo.se.ifmo.ru", "example.org")))
        assertNull(LmsAuthorization.stateFromAuthorization(request.replace("https://", "https://user:pass@")))
    }

    @Test fun `allowed browser urls enforce host, https, no custom port and no userinfo`() {
        assertTrue(LmsAuthorization.isAllowedBrowserUrl("https://id.itmo.ru/auth"))
        assertTrue(LmsAuthorization.isAllowedBrowserUrl("https://sso.tuxedo.se.ifmo.ru/realms/Newdex"))
        assertTrue(LmsAuthorization.isAllowedBrowserUrl("https://lms.tuxedo.se.ifmo.ru/sso/token"))
        assertFalse(LmsAuthorization.isAllowedBrowserUrl("https://user@lms.tuxedo.se.ifmo.ru/sso/token"))
        assertFalse(LmsAuthorization.isAllowedBrowserUrl("http://lms.tuxedo.se.ifmo.ru/sso/token"))
        assertFalse(LmsAuthorization.isAllowedBrowserUrl("https://lms.tuxedo.se.ifmo.ru:8443/sso/token"))
        assertFalse(LmsAuthorization.isAllowedBrowserUrl("https://lms.tuxedo.se.ifmo.ru.evil.org/sso/token"))
    }
}
