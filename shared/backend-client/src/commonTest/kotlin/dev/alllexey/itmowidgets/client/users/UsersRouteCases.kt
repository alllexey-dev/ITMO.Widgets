package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.support.RouteCase
import kotlin.uuid.Uuid

/** One [RouteCase] per public function of [UsersApi], with synthetic arguments. */
object UsersRouteCases {
    val challengeId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000077")
    val settings = UserPrivacySettings(SharingVisibility.ALL, SharingVisibility.NOBODY, SharingVisibility.FRIENDS)

    val userProfile = RouteCase("userProfile") { users.userProfile(SyntheticUsers.ISU) }
    val userFriends = RouteCase("userFriends") { users.userFriends(SyntheticUsers.OTHER_ISU) }
    val lookupUsers = RouteCase("lookupUsers") {
        users.lookupUsers(UserLookupRequest(listOf(SyntheticUsers.ISU, 999999, SyntheticUsers.ISU)))
    }
    val myPrivacySettings = RouteCase("myPrivacySettings") { users.myPrivacySettings() }
    val updateMyPrivacySettings = RouteCase("updateMyPrivacySettings") { users.updateMyPrivacySettings(settings) }
    val updateIdTokenData = RouteCase("updateIdTokenData") {
        users.updateIdTokenData(IdTokenRequest("synthetic.id.token"))
    }
    val myUserData = RouteCase("myUserData") { users.myUserData() }
    val webLoginPreview = RouteCase("webLoginPreview") { users.webLoginPreview("K7M2QX9P") }
    val approveWebLogin = RouteCase("approveWebLogin") { users.approveWebLogin(challengeId) }

    val all: List<RouteCase> = listOf(
        userProfile,
        userFriends,
        lookupUsers,
        myPrivacySettings,
        updateMyPrivacySettings,
        updateIdTokenData,
        myUserData,
        webLoginPreview,
        approveWebLogin,
    )
}
