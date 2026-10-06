package dev.alllexey.itmowidgets.feature.weblogin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebLoginCodeTest {

    @Test fun aTypedCodeIsUppercasedWithoutSpacesAndDashes() {
        assertEquals("ABCD2345", WebLoginCode.parse("ABCD2345"))
        assertEquals("ABCD2345", WebLoginCode.parse(" abcd-2345 "))
        assertEquals("ABCD2345", WebLoginCode.parse("AB CD 23 45"))
    }

    @Test fun aCodeOfAnotherLengthOrWithLookAlikeCharactersIsRejected() {
        assertNull(WebLoginCode.parse(""))
        assertNull(WebLoginCode.parse("ABCD234"))
        assertNull(WebLoginCode.parse("ABCD23456"))
        assertNull(WebLoginCode.parse("ABCD2340"))
        assertNull(WebLoginCode.parse("ABCDO234"))
        assertNull(WebLoginCode.parse("ABCD1234"))
        assertNull(WebLoginCode.parse("ABCDI234"))
        assertNull(WebLoginCode.parse("ABCDL234"))
        assertNull(WebLoginCode.parse("ABCD_234"))
        assertNull(WebLoginCode.parse("АВСD2345"))
    }

    @Test fun aSignInLinkOnAnyHttpsHostGivesItsCode() {
        assertEquals("ABCD2345", WebLoginCode.parse("https://widgets.alllexey.dev/app/login?code=ABCD2345"))
        assertEquals("ABCD2345", WebLoginCode.parse("https://dev.widgets.alllexey.dev/app/login?code=abcd2345"))
        assertEquals("ABCD2345", WebLoginCode.parse("HTTPS://example.org/app/login/?from=qr&code=ABCD-2345"))
        assertEquals("ABCD2345", WebLoginCode.parse("https://example.org:8443/app/login?code=ABCD%202345"))
    }

    @Test fun otherLinksAndGarbageAreNotCodes() {
        assertNull(WebLoginCode.parse("http://widgets.alllexey.dev/app/login?code=ABCD2345"))
        assertNull(WebLoginCode.parse("https://widgets.alllexey.dev/app/admin?code=ABCD2345"))
        assertNull(WebLoginCode.parse("https://widgets.alllexey.dev/app/login"))
        assertNull(WebLoginCode.parse("https://widgets.alllexey.dev/app/login?code=ABCD234"))
        assertNull(WebLoginCode.parse("https://widgets.alllexey.dev/app/login?code=ABCD2345&code=EFGH6789"))
        assertNull(WebLoginCode.parse("https://user@widgets.alllexey.dev/app/login?code=ABCD2345"))
        assertNull(WebLoginCode.parse("https:///app/login?code=ABCD2345"))
        assertNull(WebLoginCode.parse("itmowidgets://login?code=ABCD2345"))
        assertNull(WebLoginCode.parse("https://t.me/itmowidgets"))
        assertNull(WebLoginCode.parse("WIFI:S:itmo;T:WPA;P:secret;;"))
    }
}
