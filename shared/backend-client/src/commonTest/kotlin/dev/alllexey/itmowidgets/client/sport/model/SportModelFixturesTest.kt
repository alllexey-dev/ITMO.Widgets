package dev.alllexey.itmowidgets.client.sport.model

import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.http.ApiEnvelope
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.fail

/**
 * Backend's vendored sport answers that carry a model (`claims/sport.txt`): each decodes and re-encodes to the
 * fixture's `data`, `"type"` included. The routes land with CO-04b, so these claims decode the envelope directly;
 * CO-04b moves them onto `SportApi` calls and claims the `Unit` answers and the request bodies.
 */
class SportModelFixturesTest {

    private val entries = ListSerializer(SportQueueEntry.serializer())
    private val queues = ListSerializer(SportQueue.serializer())

    private val responses = listOf(
        claim("http/sport/friendsSportBookings.json", FriendsSportBookingsResponse.serializer()),
        claim("http/sport/userSportBookings.json", UserSportBookingsResponse.serializer()),
        claim("http/sport-free-sign/createSportFreeSignEntry.json", SportQueueEntry.serializer()),
        claim("http/sport-free-sign/currentSportFreeSignQueues.json", queues),
        claim("http/sport-free-sign/mySportFreeSignEntries.json", entries),
        claim("http/sport-auto-sign/createSportAutoSignEntry.json", SportQueueEntry.serializer()),
        claim("http/sport-auto-sign/currentSportAutoSignQueues.json", queues),
        claim("http/sport-auto-sign/mySportAutoSignEntries.json", entries),
        claim("http/sport-auto-sign/sportAutoSignLimits.json", SportAutoSignLimits.serializer()),
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("sport", responses, emptyList()) }

    private fun <T> claim(path: String, serializer: KSerializer<T>) =
        ResponseClaim(path, serializer) { SportFixtures.data(path, serializer) }
}

/** Decoded `data` of the vendored sport answers. */
internal object SportFixtures {
    fun <T> data(path: String, serializer: KSerializer<T>): T =
        BackendJson.decodeFromString(ApiEnvelope.serializer(serializer), VendoredContract.read(path)).data
            ?: fail("$path has no data")
}
