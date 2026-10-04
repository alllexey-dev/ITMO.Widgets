package dev.alllexey.itmowidgets.core.session

import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class IdTokenCurrentUserProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

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
    fun `returns null when no token is stored`() = runTest {
        assertNull(provider(null).getCurrentUser())
    }

    private fun provider(idToken: String?): IdTokenCurrentUserProvider {
        return IdTokenCurrentUserProvider(FakeSessionTokenStore(signedIn = false, idToken = idToken), Gson(), dispatchers)
    }

    private fun token(payloadJson: String): String {
        val payload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        return "header.$payload.signature"
    }
}
