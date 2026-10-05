package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ReportReasonSerializer
import dev.alllexey.itmowidgets.client.json.BackendJson
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Port of the review report reason case of Core 1.x `reviews/TeacherReviewModerationContractTest`. The other cases
 * (`TEACHER_REVIEW` case targets, `moderationCases`) and the `ModerationReport` part of this one belong to the
 * moderation API, which is not mirrored (CO-02).
 */
class TeacherReviewModerationContractTest {

    @Test
    fun reviewReportReasonsDecodeAndUnknownReasonsFail() {
        for (reason in listOf(ReportReason.OFFENSIVE, ReportReason.WRONG_TEACHER)) {
            assertEquals(reason, BackendJson.decodeFromString(ReportReasonSerializer, "\"${reason.name}\""))
            val request = ModerationReportRequest(reason, "Комментарий")
            val text = BackendJson.encodeToString(ModerationReportRequest.serializer(), request)
            assertEquals(request, BackendJson.decodeFromString(ModerationReportRequest.serializer(), text))
        }
        assertFailsWith<SerializationException> { BackendJson.decodeFromString(ReportReasonSerializer, "\"RUDE\"") }
        assertFailsWith<SerializationException> {
            BackendJson.decodeFromString(ModerationReportRequest.serializer(), """{"reason":"RUDE"}""")
        }
    }
}
