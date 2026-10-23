package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/** The method, path, query and body of every [UsersApi] call ([UsersRouteCases]). */
class UsersRequestTest {

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(9, UsersRouteCases.all.size)
        assertEquals(UsersRouteCases.all.size, UsersRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun userProfile() = runSuspend {
        UsersRouteCases.userProfile.assertRequest(HttpMethod.Get, "/api/users/123456")
    }

    @Test
    fun userFriends() = runSuspend {
        UsersRouteCases.userFriends.assertRequest(HttpMethod.Get, "/api/users/456789/friends")
    }

    @Test
    fun lookupUsers() = runSuspend {
        UsersRouteCases.lookupUsers.assertRequest(
            HttpMethod.Post,
            "/api/users/lookup",
            body = """{"isus":[123456,999999,123456]}""",
        )
    }

    @Test
    fun myPrivacySettings() = runSuspend {
        UsersRouteCases.myPrivacySettings.assertRequest(HttpMethod.Get, "/api/users/me/privacy")
    }

    @Test
    fun updateMyPrivacySettings() = runSuspend {
        UsersRouteCases.updateMyPrivacySettings.assertRequest(
            HttpMethod.Put,
            "/api/users/me/privacy",
            body = """{"scheduleVisibility":"ALL","sportVisibility":"NOBODY","friendsVisibility":"FRIENDS"}""",
        )
    }

    @Test
    fun updateIdTokenData() = runSuspend {
        UsersRouteCases.updateIdTokenData.assertRequest(
            HttpMethod.Put,
            "/api/users/me/id-token",
            body = """{"idToken":"synthetic.id.token"}""",
        )
    }

    @Test
    fun myUserData() = runSuspend {
        UsersRouteCases.myUserData.assertRequest(HttpMethod.Get, "/api/users/me/data")
    }

    @Test
    fun updateConsent() = runSuspend {
        UsersRouteCases.updateConsent.assertRequest(HttpMethod.Put, "/api/users/me/consent", body = """{"version":1}""")
    }

    @Test
    fun deleteMe() = runSuspend {
        UsersRouteCases.deleteMe.assertRequest(HttpMethod.Delete, "/api/users/me")
    }

    @Test
    fun privacyPutBodyHasExactlyTheThreeAudienceKeys() = runSuspend {
        val body = UsersRouteCases.updateMyPrivacySettings.record().body.orEmpty()

        val keys = Regex(""""(\w+)":""").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(listOf("scheduleVisibility", "sportVisibility", "friendsVisibility"), keys)
    }

    @Test
    fun idTokenRequestDoesNotPrintTheToken() {
        assertEquals("IdTokenRequest(idToken=<redacted>)", IdTokenRequest("synthetic.id.token").toString())
    }
}
