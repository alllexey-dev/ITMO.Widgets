package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.common.GroupData
import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok

/** Synthetic users, profiles and envelopes shared by the users and friends tests; no real person or ISU. */
object SyntheticUsers {
    const val ISU = 123456
    const val OTHER_ISU = 456789

    const val CAPABILITIES = """{"canViewSchedule":false,"canViewSport":true,"canViewFriends":false}"""

    val capabilities = UserCapabilities(canViewSchedule = false, canViewSport = true, canViewFriends = false)

    /** The identity [userJson] decodes to with [CAPABILITIES]. */
    val identity = UserData(
        isu = ISU,
        name = "Synthetic user",
        pictureUrl = null,
        groups = emptyList(),
        capabilities = capabilities,
    )

    val group = GroupData(name = "M3100", course = 1, facultyShortName = "SYN")

    fun userJson(capabilities: String = CAPABILITIES): String =
        """{"isu":$ISU,"name":"Synthetic user","pictureUrl":null,"groups":[],"capabilities":$capabilities}"""

    fun profileJson(relationship: String, capabilities: String = CAPABILITIES): String =
        """{"user":${userJson(capabilities)},"relationship":"$relationship"}"""

    fun profile(relationship: RelationshipState, capabilities: UserCapabilities = this.capabilities) =
        UserProfile(identity.copy(capabilities = capabilities), relationship)

    fun capabilitiesJson(schedule: Boolean, sport: Boolean, friends: Boolean): String =
        """{"canViewSchedule":$schedule,"canViewSport":$sport,"canViewFriends":$friends}"""

    /** Backend's success envelope around [data], with the `error: null` Jackson writes. */
    fun envelope(data: String): String = """{"success":true,"data":$data,"error":null}"""

    /** A [MockBackend] that answers every request with [envelope] of [data]. */
    fun answering(data: String): MockBackend = MockBackend { ok(envelope(data)) }
}
