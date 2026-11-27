package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.feature.connections.domain.KeycloakIdentityCookie
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** The unverified JWT payload of ITMO.ID tokens and `KEYCLOAK_IDENTITY`, and the cookie in a `Cookie` header. */
class IdTokenClaimsTest {

    private fun jwt(payload: String) =
        "eyJhbGciOiJIUzI1NiJ9.${Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())}.c2ln"

    @Test
    fun readsSidIsuAndExp() {
        val token = jwt("""{"sid":"sid-1","isu":100001,"exp":1800000000}""")

        assertEquals("sid-1", IdTokenClaims.sid(token))
        assertEquals("sid-1", IdTokenClaims.sessionId(token))
        assertEquals(100001, IdTokenClaims.isu(token))
        assertEquals(Instant.fromEpochSeconds(1_800_000_000), IdTokenClaims.expiresAt(token))
    }

    @Test
    fun anOlderTokenNamesItsSessionInSessionState() {
        assertEquals("state-1", IdTokenClaims.sessionId(jwt("""{"session_state":"state-1"}""")))
    }

    @Test
    fun malformedTokensAndClaimsAreNull() {
        for (token in listOf(null, "", "no-dots", "a.%%%.c", jwt("[1,2]"), jwt("""{"sid":5,"exp":"soon"}"""), jwt("""{"sid":" "}"""))) {
            assertNull(IdTokenClaims.sessionId(token), token)
            assertNull(IdTokenClaims.expiresAt(token), token)
        }
    }

    @Test
    fun theCookieIsFoundByItsExactName() {
        val header = "AUTH_SESSION_ID=a; KEYCLOAK_IDENTITY_LEGACY=legacy; KEYCLOAK_IDENTITY=eyJ.payload.sig; KC_RESTART=x"

        assertEquals("eyJ.payload.sig", KeycloakIdentityCookie.fromCookieHeader(header))
        assertNull(KeycloakIdentityCookie.fromCookieHeader("KEYCLOAK_IDENTITY_LEGACY=legacy"))
        assertNull(KeycloakIdentityCookie.fromCookieHeader("KEYCLOAK_IDENTITY="))
        assertNull(KeycloakIdentityCookie.fromCookieHeader(null))
    }
}
