package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class IdTokenCurrentUserProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers
    private val log = RecordingAppLog()

    @Test
    fun `reads identity claims from the id token`() = runTest {
        val provider = provider(
            token(
                """{"isu":123456,"name":"Иванов Иван","picture":"https://itmo/a.jpg"}"""
            )
        )

        assertEquals(
            CurrentUser(
                isu = 123456,
                name = "Иванов Иван",
                pictureUrl = "https://itmo/a.jpg"
            ),
            provider.getCurrentUser()
        )
    }

    @Test
    fun `ignores the other claims of a real ITMO_ID token`() = runTest {
        val provider = provider(
            token(
                """{"exp":1790000000,"iat":1789990000,"aud":["itmo"],"sub":"synthetic","isu":123456,""" +
                    """"name":"Иванов Иван","given_name":"Иван","groups":[{"name":"T100"}],"picture":null,""" +
                    """"email_verified":true}"""
            )
        )

        assertEquals(CurrentUser(isu = 123456, name = "Иванов Иван", pictureUrl = null), provider.getCurrentUser())
    }

    @Test
    fun `an id token payload that is not a claims object reads as no user`() = runTest {
        for (payload in listOf("[]", "\"text\"", """{"isu":{"id":1}}""")) {
            assertNull(payload, provider(token(payload)).getCurrentUser())
        }
    }

    @Test
    fun `trims claims and treats blank picture as absent`() = runTest {
        val provider = provider(
            token("""{"isu":1,"name":"  Иванов Иван  ","picture":"   "}""")
        )

        val user = provider.getCurrentUser()

        assertEquals("Иванов Иван", user?.name)
        assertNull(user?.pictureUrl)
    }

    @Test
    fun `survives a token without identity claims`() = runTest {
        val provider = provider(token("""{"aud":"itmo"}"""))

        assertNull(provider.getCurrentUser())
    }

    @Test
    fun `returns null for a malformed token`() = runTest {
        assertNull(provider("not-a-jwt").getCurrentUser())
        assertNull(provider("header.%%%.signature").getCurrentUser())
    }

    @Test
    fun `reads a padded payload as the JDK URL decoder did`() = runTest {
        val payload = Base64.getUrlEncoder().encodeToString("""{"isu":12}""".toByteArray(Charsets.UTF_8))

        assertEquals(12, provider("header.$payload.signature").getCurrentUser()?.isu)
    }

    @Test
    fun `never logs the token or its payload`() = runTest {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("not json".toByteArray(Charsets.UTF_8))

        assertNull(provider("header.$payload.signature").getCurrentUser())

        assertEquals(1, log.lines.size)
        assertFalse(log.lines.single().contains(payload))
        assertFalse(log.lines.single().contains("not json"))
    }

    @Test
    fun `returns null when no token is stored`() = runTest {
        assertNull(provider(null).getCurrentUser())
    }

    private fun provider(idToken: String?): IdTokenCurrentUserProvider {
        return IdTokenCurrentUserProvider(
            FakeSessionTokenStore(signedIn = false, idToken = idToken),
            dispatchers,
            log
        )
    }

    private fun token(payloadJson: String): String {
        val payload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        return "header.$payload.signature"
    }
}
