package dev.alllexey.itmowidgets.client.moderation

import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test

/**
 * Backend's vendored answer of the author's moderation notices (`claims/moderation.txt`): it decodes through
 * [ModerationNoticesApi] and re-encodes to the fixture's `data`.
 */
class ModerationNoticesVendoredFixturesTest {

    private val responses = listOf(
        ResponseClaim("http/moderation/moderationNotices.json", ListSerializer(ModerationNotice.serializer())) {
            moderationNotices.notices()
        },
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("moderation", responses, emptyList()) }
}
