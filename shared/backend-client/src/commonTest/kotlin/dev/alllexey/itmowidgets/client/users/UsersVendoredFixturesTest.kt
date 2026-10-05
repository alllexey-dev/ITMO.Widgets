package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.contract.RequestClaim
import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test

/**
 * Backend's vendored users fixtures (`claims/users.txt`): every answer decodes through [UsersApi] and re-encodes to
 * the fixture's `data`, every request body round-trips. Arguments do not matter, the fixture is the answer.
 */
class UsersVendoredFixturesTest {

    private val responses = listOf(
        ResponseClaim("http/users/userProfile.json", UserProfile.serializer()) { users.userProfile(ISU) },
        ResponseClaim("http/users/userFriends.json", ListSerializer(UserProfile.serializer())) {
            users.userFriends(ISU)
        },
        ResponseClaim("http/users/lookupUsers.json", UserLookupResponse.serializer()) {
            users.lookupUsers(UserLookupRequest(listOf(ISU)))
        },
        ResponseClaim("http/users/myPrivacySettings.json", UserPrivacySettings.serializer()) {
            users.myPrivacySettings()
        },
        ResponseClaim("http/users/updateMyPrivacySettings.json", UserPrivacySettings.serializer()) {
            users.updateMyPrivacySettings(UsersRouteCases.settings)
        },
        ResponseClaim("http/users/updateIdTokenData.json", null) {
            users.updateIdTokenData(IdTokenRequest("synthetic.id.token"))
        },
        ResponseClaim("http/users/myUserData.json", UserData.serializer()) { users.myUserData() },
        ResponseClaim("http/users/webLoginPreview.json", WebLoginPreview.serializer()) {
            users.webLoginPreview("K7M2QX9P")
        },
        ResponseClaim("http/users/approveWebLogin.json", null) { users.approveWebLogin(UsersRouteCases.challengeId) },
    )

    private val requests = listOf(
        RequestClaim("requests/IdTokenRequest.json", IdTokenRequest.serializer()),
        RequestClaim("requests/UserLookupRequest.json", UserLookupRequest.serializer()),
        RequestClaim("requests/UserPrivacySettings.json", UserPrivacySettings.serializer()),
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("users", responses, requests) }

    private companion object {
        const val ISU = 100002
    }
}
