package dev.alllexey.itmowidgets.feature.social.data

/**
 * Synthetic Backend answers for users and friends, shaped like CO-09a's vendored `http/users/` and
 * `http/friends/` answers at Backend commit ad8fa2dc3681dd57b6e8b797ace2f97322742555 (`BACKEND_COMMIT` of
 * `shared/backend-client/src/commonTest/resources/contract/`). Kept as constants, not JVM resources, so they move to
 * `commonTest` unchanged. No value here is a real person or ISU number.
 */
object SocialRemoteFixtures {

    const val ME_ISU = 100001
    const val FRIEND_ISU = 100002
    const val SECOND_FRIEND_ISU = 100003
    const val INCOMING_ISU = 100004
    const val OUTGOING_ISU = 100005

    /** `myUserData.json`, with padding the mapping trims. */
    const val MY_USER_DATA = """{
  "success": true,
  "data": {
    "isu": 100001,
    "name": "  Студент Тестовый  ",
    "pictureUrl": "  https://example.org/avatars/100001.jpg ",
    "groups": [{ "name": " К3240 ", "course": 2, "facultyShortName": " ФИТИП " }],
    "capabilities": { "canViewSchedule": true, "canViewSport": true, "canViewFriends": true }
  },
  "error": null
}"""

    /** `friends.json`: two friends, the second without a picture or groups. */
    const val FRIENDS = """{
  "success": true,
  "data": [
    {
      "user": {
        "isu": 100002,
        "name": "Друг Первый",
        "pictureUrl": "https://example.org/avatars/100002.jpg",
        "groups": [{ "name": "К3240", "course": 2, "facultyShortName": "ФИТИП" }],
        "capabilities": { "canViewSchedule": true, "canViewSport": true, "canViewFriends": true }
      },
      "relationship": "FRIENDS"
    },
    {
      "user": {
        "isu": 100003,
        "name": "Друг Второй",
        "pictureUrl": null,
        "groups": [],
        "capabilities": { "canViewSchedule": true, "canViewSport": false, "canViewFriends": true }
      },
      "relationship": "FRIENDS"
    }
  ],
  "error": null
}"""

    /** `incomingFriendRequests.json`. */
    const val INCOMING = """{
  "success": true,
  "data": [
    {
      "user": {
        "isu": 100004,
        "name": "Однокурсник Третий",
        "pictureUrl": "",
        "groups": [{ "name": "К3240", "course": 2, "facultyShortName": "ФИТИП" }],
        "capabilities": { "canViewSchedule": false, "canViewSport": false, "canViewFriends": false }
      },
      "relationship": "INCOMING"
    }
  ],
  "error": null
}"""

    /** `outgoingFriendRequests.json`. */
    const val OUTGOING = """{
  "success": true,
  "data": [
    {
      "user": {
        "isu": 100005,
        "name": "Однокурсник Четвёртый",
        "pictureUrl": "https://example.org/avatars/100005.jpg",
        "groups": [{ "name": "К3240", "course": 2, "facultyShortName": "ФИТИП" }],
        "capabilities": { "canViewSchedule": true, "canViewSport": false, "canViewFriends": true }
      },
      "relationship": "OUTGOING"
    }
  ],
  "error": null
}"""

    /** `userFriends.json`: another user's friends, each relative to the viewer. */
    const val USER_FRIENDS = """{
  "success": true,
  "data": [
    {
      "user": {
        "isu": 100004,
        "name": "Однокурсник Третий",
        "pictureUrl": null,
        "groups": [],
        "capabilities": { "canViewSchedule": false, "canViewSport": false, "canViewFriends": true }
      },
      "relationship": "INCOMING"
    },
    {
      "user": {
        "isu": 100003,
        "name": "Друг Второй",
        "pictureUrl": null,
        "groups": [],
        "capabilities": { "canViewSchedule": true, "canViewSport": false, "canViewFriends": true }
      },
      "relationship": "NONE"
    }
  ],
  "error": null
}"""

    /** A relationship this client does not know: Core 2.0 fails the answer instead of reading it as `NONE`. */
    val UNKNOWN_RELATIONSHIP: String = profile(FRIEND_ISU, "MUTED")

    /** `userProfile.json` and every friendship action's answer: one user as the viewer now sees them. */
    fun profile(isu: Int, relationship: String): String = envelope(profileData(isu, relationship))

    /** `lookupUsers.json`: the registered users among the asked ISUs. */
    fun lookup(isus: List<Int>): String =
        envelope("""{ "users": [${isus.joinToString(",") { profileData(it, "NONE") }}] }""")

    /** Backend's error envelope with a synthetic message. */
    fun error(code: String): String =
        """{ "success": false, "data": null, "error": { "message": "synthetic message", "code": "$code" } }"""

    private fun envelope(data: String) = """{ "success": true, "data": $data, "error": null }"""

    private fun profileData(isu: Int, relationship: String) = """{
  "user": {
    "isu": $isu,
    "name": "Пользователь $isu",
    "pictureUrl": "https://example.org/avatars/$isu.jpg",
    "groups": [{ "name": "К3240", "course": 2, "facultyShortName": "ФИТИП" }],
    "capabilities": { "canViewSchedule": true, "canViewSport": false, "canViewFriends": true }
  },
  "relationship": "$relationship"
}"""
}
