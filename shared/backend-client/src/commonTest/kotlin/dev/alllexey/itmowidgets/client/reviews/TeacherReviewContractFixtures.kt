package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Synthetic reviews of the 1.x `TeacherReviewContractFixtures` without the moderation types, plus JSON helpers. */
object TeacherReviewContractFixtures {
    const val TEACHER = 100001
    const val PROVIDER_URL = "https://example.invalid/reviews/#/teacher/100001"
    const val TEXT = "Синтетический отзыв о преподавателе для проверки контракта"

    val namedId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000071")
    val anonymousId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000072")
    val copyId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000073")
    val ownId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000074")

    val author = SyntheticUsers.identity

    val named = TeacherReview(
        id = namedId,
        kind = TeacherReviewKind.COMMUNITY,
        subjectTitle = "Математика",
        writtenOn = LocalDate(2026, 9, 23),
        writtenBeforeYear = null,
        text = TEXT,
        score = 3,
        myVote = 1,
        verified = true,
        reportedByMe = false,
        author = author,
        sourceTitle = null,
        sourceLink = null,
    )
    val anonymous = named.copy(
        id = anonymousId,
        subjectTitle = null,
        score = 0,
        myVote = 0,
        verified = false,
        reportedByMe = true,
        author = null,
    )
    val copy = TeacherReview(
        id = copyId,
        kind = TeacherReviewKind.REVIEWS,
        subjectTitle = "Физика",
        writtenOn = null,
        writtenBeforeYear = 2024,
        text = TEXT,
        score = -1,
        myVote = -1,
        verified = false,
        reportedByMe = false,
        author = null,
        sourceTitle = "Тестовый источник",
        sourceLink = "https://example.invalid/review",
    )
    val mine = OwnTeacherReview(
        id = ownId,
        subjectTitle = "Математика",
        text = TEXT,
        anonymous = true,
        status = TeacherReviewStatus.REJECTED,
        reviewNote = "Не о преподавателе",
        score = 0,
        verified = false,
        writtenOn = LocalDate(2026, 9, 28),
    )
    val response = TeacherReviewsResponse(
        teacherIsu = TEACHER,
        providerUrl = PROVIDER_URL,
        reviews = listOf(named, anonymous, copy),
        mine = mine,
        canWrite = true,
        canVote = true,
        canReport = true,
        knownTeacher = true,
        summary = null,
    )
    val summary = TeacherSummary(
        reviewCount = 5,
        description = "Студенты пишут о понятных лекциях и строгой защите лабораторных.",
        pros = listOf("Понятно объясняет"),
        cons = listOf("Строгая защита"),
        tags = listOf("MANY_LABS", "NEW_TAG"),
        scales = listOf(
            TeacherSummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Лекции понятные"),
            TeacherSummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Спокойно отвечает на вопросы"),
            TeacherSummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.NOT_ENOUGH_DATA, null),
            TeacherSummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.HIGH, "Строго принимает лабораторные"),
            TeacherSummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.LOW, "Заданий немного"),
        ),
        level = SummaryLevel.POSITIVE,
        confidence = SummaryConfidence.MEDIUM,
        generatedAt = Instant.parse("2026-09-29T09:00:00Z"),
    )

    /** Backend's `TeacherSummary` as `GET /api/teachers/{isu}/reviews` sends it; `NEW_TAG` is unknown to the apps. */
    const val SUMMARY_JSON = """{"reviewCount":5,
        "description":"Студенты пишут о понятных лекциях и строгой защите лабораторных.",
        "pros":["Понятно объясняет"],"cons":["Строгая защита"],"tags":["MANY_LABS","NEW_TAG"],
        "scales":[{"kind":"EXPLAINS","value":"HIGH","reason":"Лекции понятные"},
            {"kind":"ATTITUDE","value":"MEDIUM","reason":"Спокойно отвечает на вопросы"},
            {"kind":"FAIRNESS","value":"NOT_ENOUGH_DATA","reason":null},
            {"kind":"STRICTNESS","value":"HIGH","reason":"Строго принимает лабораторные"},
            {"kind":"WORKLOAD","value":"LOW","reason":"Заданий немного"}],
        "level":"POSITIVE","confidence":"MEDIUM","generatedAt":"2026-09-29T09:00:00Z"}"""

    val save = SaveTeacherReviewRequest(
        subjectTitle = "Математика",
        text = TEXT,
        anonymous = false,
        flowIds = listOf(93724, 93725),
    )

    /** [value] as a JSON object, to edit before decoding it again. */
    fun <T> jsonOf(serializer: KSerializer<T>, value: T): JsonObject =
        BackendJson.encodeToJsonElement(serializer, value).jsonObject

    /** [this] with [field] set to [wire] (a JSON text) or removed when [wire] is `null`. */
    fun JsonObject.with(field: String, wire: String?): JsonObject = JsonObject(
        toMutableMap().apply {
            if (wire == null) remove(field) else put(field, BackendJson.parseToJsonElement(wire))
        },
    )

    /** Decodes [json] from its text, the way the client decodes an answer. */
    fun <T> decode(serializer: KSerializer<T>, json: JsonElement): T =
        BackendJson.decodeFromString(serializer, json.toString())

    fun responseJson(value: TeacherReviewsResponse = response): String =
        BackendJson.encodeToString(TeacherReviewsResponse.serializer(), value)

    /** Backend's success envelope around [data], with the `error: null` Jackson writes. */
    fun envelope(data: String): String = """{"success":true,"data":$data,"error":null}"""

    /** A [MockBackend] that answers every request with [envelope] of [data]. */
    fun answering(data: String): MockBackend = MockBackend { ok(envelope(data)) }
}
