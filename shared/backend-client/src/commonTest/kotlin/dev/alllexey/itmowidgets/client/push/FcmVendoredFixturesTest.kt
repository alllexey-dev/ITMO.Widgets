package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Backend's vendored FCM data maps (`claims/fcm.txt`). A fixture holds `data` parsed from its JSON string, so the
 * string the app receives is `data` printed back. Each one decodes through [FcmDecoder] and its typed payload
 * re-encodes to the fixture's `payload`, so a field the model lacks or misnames fails.
 */
class FcmVendoredFixturesTest {

    private val claims = listOf(
        FcmClaim("fcm/FRIENDSHIP_EVENT_PAYLOAD.json", FriendshipEventPayload.serializer(), FcmDecoder::friendshipEvent),
        FcmClaim(
            "fcm/SPORT_FREE_SIGN_LESSONS_PAYLOAD.json",
            SportFreeSignLessonsPayload.serializer(),
            FcmDecoder::sportFreeSignLessons,
        ),
        FcmClaim(
            "fcm/SPORT_AUTO_SIGN_LESSONS_PAYLOAD.json",
            SportAutoSignLessonsPayload.serializer(),
            FcmDecoder::sportAutoSignLessons,
        ),
    )

    @Test
    fun claimsFileListsEveryCheckedFixture() {
        assertEquals(VendoredContract.claims("fcm").sorted(), claims.map { it.path }.sorted())
    }

    @Test
    fun everyClaimDecodesAndRoundTrips() {
        for (claim in claims) {
            try {
                claim.assertDecodes()
            } catch (error: Throwable) {
                fail("${claim.path}: ${error.message}", error)
            }
        }
    }

    @Test
    fun gsonStyleSamplesDecodeToTheVendoredValues() {
        val friendship = FcmFixtures.data("fcm/FRIENDSHIP_EVENT_PAYLOAD.json")
        val autoSign = FcmFixtures.data("fcm/SPORT_AUTO_SIGN_LESSONS_PAYLOAD.json")

        for (sample in listOf(
            SyntheticFcm.GSON_FRIENDSHIP_EVENT,
            SyntheticFcm.jacksonDates(SyntheticFcm.GSON_FRIENDSHIP_EVENT),
        )) {
            assertEquals(
                FcmDecoder.friendshipEvent(FcmDecoder.envelope(friendship).payload),
                FcmDecoder.friendshipEvent(FcmDecoder.envelope(sample).payload),
            )
        }
        for (sample in listOf(
            SyntheticFcm.GSON_SPORT_AUTO_SIGN_LESSONS,
            SyntheticFcm.jacksonDates(SyntheticFcm.GSON_SPORT_AUTO_SIGN_LESSONS),
        )) {
            assertEquals(
                FcmDecoder.sportAutoSignLessons(FcmDecoder.envelope(autoSign).payload),
                FcmDecoder.sportAutoSignLessons(FcmDecoder.envelope(sample).payload),
            )
        }
    }
}

/** One claimed FCM fixture whose `type` is the file name. */
private class FcmClaim<T>(
    val path: String,
    private val serializer: KSerializer<T>,
    private val decode: (JsonElement) -> T,
) {
    fun assertDecodes() {
        val data = FcmFixtures.data(path)
        val envelope = FcmDecoder.envelope(data)

        assertEquals(path.removePrefix("fcm/").removeSuffix(".json"), envelope.type)
        val expected = BackendJson.parseToJsonElement(data).jsonObject["payload"] as JsonObject
        assertEquals(expected, envelope.payload)
        assertJsonEquals(expected.toString(), BackendJson.encodeToString(serializer, decode(envelope.payload)))
    }
}

/** The `data` strings of the vendored FCM fixtures. */
internal object FcmFixtures {
    fun data(path: String): String =
        BackendJson.parseToJsonElement(VendoredContract.read(path)).jsonObject["data"]?.toString()
            ?: fail("$path has no data")
}
