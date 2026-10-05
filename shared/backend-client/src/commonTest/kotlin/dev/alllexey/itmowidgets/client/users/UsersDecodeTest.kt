package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.profile
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.profileJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** What every [UsersApi] call returns for a synthetic Backend answer. */
class UsersDecodeTest {

    @Test
    fun userProfile() = runSuspend {
        val result = answering(profileJson("OUTGOING")).client.users.userProfile(SyntheticUsers.ISU)

        assertEquals(profile(RelationshipState.OUTGOING), result)
    }

    @Test
    fun userFriends() = runSuspend {
        val result = answering("[${profileJson("NONE")},${profileJson("FRIENDS")}]").client.users
            .userFriends(SyntheticUsers.OTHER_ISU)

        assertEquals(listOf(profile(RelationshipState.NONE), profile(RelationshipState.FRIENDS)), result)
    }

    @Test
    fun lookupUsers() = runSuspend {
        val result = answering("""{"users":[${profileJson("INCOMING")}]}""").client.users
            .lookupUsers(UserLookupRequest(listOf(SyntheticUsers.ISU)))

        assertEquals(UserLookupResponse(listOf(profile(RelationshipState.INCOMING))), result)
    }

    @Test
    fun myPrivacySettings() = runSuspend {
        val body = """{"scheduleVisibility":"FRIENDS","sportVisibility":"ALL","friendsVisibility":"NOBODY"}"""

        val result = answering(body).client.users.myPrivacySettings()

        assertEquals(
            UserPrivacySettings(SharingVisibility.FRIENDS, SharingVisibility.ALL, SharingVisibility.NOBODY),
            result,
        )
    }

    @Test
    fun updateMyPrivacySettings() = runSuspend {
        val body = """{"scheduleVisibility":"NOBODY","sportVisibility":"NOBODY","friendsVisibility":"ALL"}"""

        val result = answering(body).client.users.updateMyPrivacySettings(UsersRouteCases.settings)

        assertEquals(
            UserPrivacySettings(SharingVisibility.NOBODY, SharingVisibility.NOBODY, SharingVisibility.ALL),
            result,
        )
    }

    @Test
    fun updateIdTokenDataIgnoresTheConfirmationText() = runSuspend {
        answering("\"Successfully updated\"").client.users.updateIdTokenData(IdTokenRequest("synthetic.id.token"))
    }

    @Test
    fun myUserData() = runSuspend {
        val everything = UserCapabilities(canViewSchedule = true, canViewSport = true, canViewFriends = true)
        val body =
            SyntheticUsers.userJson(SyntheticUsers.capabilitiesJson(schedule = true, sport = true, friends = true))

        val result = answering(body).client.users.myUserData()

        assertEquals(SyntheticUsers.identity.copy(capabilities = everything), result)
    }

    @Test
    fun webLoginPreview() = runSuspend {
        val body = """{"challengeId":"00000000-0000-0000-0000-000000000077","userAgent":"Synthetic browser",
            "createdAt":"2026-09-24T10:00:00Z","expiresAt":"2026-09-24T10:02:00Z"}"""

        val result = answering(body).client.users.webLoginPreview("K7M2QX9P")

        assertEquals(
            WebLoginPreview(
                challengeId = UsersRouteCases.challengeId,
                userAgent = "Synthetic browser",
                createdAt = Instant.parse("2026-09-24T10:00:00Z"),
                expiresAt = Instant.parse("2026-09-24T10:02:00Z"),
            ),
            result,
        )
    }

    @Test
    fun approveWebLoginNeedsNoData() = runSuspend {
        // Backend answers ApiResponse<Unit>, which Jackson writes as `{}`; an absent `data` is fine as well.
        for (body in listOf(SyntheticUsers.envelope("{}"), """{"success":true}""")) {
            val backend = MockBackend { ok(body) }

            backend.client.users.approveWebLogin(UsersRouteCases.challengeId)

            assertEquals(1, backend.requests.size)
        }
    }
}
