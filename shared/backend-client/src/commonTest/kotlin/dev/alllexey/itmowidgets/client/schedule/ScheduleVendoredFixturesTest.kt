package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.contract.RequestClaim
import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/**
 * Backend's vendored schedule fixtures (`claims/schedule.txt`): every answer decodes through [ScheduleApi] and
 * re-encodes to the fixture's `data`, and the sync body round-trips. Arguments do not matter, the fixture is the
 * answer.
 */
class ScheduleVendoredFixturesTest {

    private val responses = listOf(
        ResponseClaim("http/schedule/syncLessons.json", null) { schedule.syncLessons(SyntheticLessons.request) },
        ResponseClaim("http/schedule/userLessons.json", JacksonTimeLessons) {
            schedule.userLessons(ISU, SyntheticLessons.from, SyntheticLessons.to)
        },
        ResponseClaim("http/schedule/friendsOnLesson.json", ListSerializer(UserProfile.serializer())) {
            schedule.friendsOnLesson(ScheduleRouteCases.PAIR_ID, ScheduleRouteCases.occurrence)
        },
    )

    private val requests = listOf(
        RequestClaim("requests/LessonSyncRequest.json", LessonSyncRequest.serializer()),
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("schedule", responses, requests) }

    /**
     * Lessons re-encoded with Jackson's `HH:mm:ss` times. The client writes `08:20` (what the vendored request
     * fixture and released clients send) and Backend answers `08:20:00`; both read as the same [LessonDto], but
     * `assertJsonEquals` compares times exactly, so only the zero seconds are added back before the comparison.
     */
    private object JacksonTimeLessons : KSerializer<List<LessonDto>> {
        private val lessons = ListSerializer(LessonDto.serializer())

        override val descriptor = lessons.descriptor

        override fun deserialize(decoder: Decoder): List<LessonDto> = lessons.deserialize(decoder)

        override fun serialize(encoder: Encoder, value: List<LessonDto>) {
            val json = encoder as JsonEncoder
            val encoded = json.json.encodeToJsonElement(lessons, value).jsonArray.map { lesson ->
                JsonObject(
                    lesson.jsonObject.mapValues { (name, member) ->
                        if (name in TIMES) JsonPrimitive(withSeconds(member.jsonPrimitive.content)) else member
                    },
                )
            }
            json.encodeJsonElement(JsonArray(encoded))
        }

        private fun withSeconds(time: String) = if (time.length == "08:20".length) "$time:00" else time

        private val TIMES = setOf("start", "end")
    }

    private companion object {
        const val ISU = 100002
    }
}
