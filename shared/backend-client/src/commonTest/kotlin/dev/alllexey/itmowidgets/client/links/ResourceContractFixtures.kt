package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Synthetic links, audiences and restrictions shared by the links tests (1.x `ResourceContractFixtures`). */
object ResourceContractFixtures {
    const val SUBJECT_ID = 42L
    const val PERIOD = "2026-1"
    const val FLOW_ID = 7103L

    val id: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000042")
    val otherId: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000043")
    val now: Instant = Instant.parse("2026-09-22T09:00:00Z")

    val link = SubjectLink(
        id = id,
        subjectId = SUBJECT_ID,
        subjectName = "Предмет",
        periodKey = PERIOD,
        category = LinkCategory.SCORES,
        url = "https://example.invalid/scores#gid=1",
        title = "Баллы",
        visibility = LinkVisibility.FLOW,
        flowId = FLOW_ID,
        audienceLabel = "ФИЗ ПИИКТ 3.2.1",
        status = SubjectLinkStatus.PUBLISHED,
        reviewNote = null,
        score = 2,
        myVote = 1,
        isMine = false,
        reportedByMe = false,
        author = SyntheticUsers.identity,
        updatedAt = now,
    )
    val ownLink = link.copy(
        id = otherId,
        category = LinkCategory.CHAT,
        url = "https://example.invalid/chat",
        title = null,
        visibility = LinkVisibility.ALL,
        flowId = null,
        audienceLabel = null,
        status = SubjectLinkStatus.REJECTED,
        reviewNote = "Не по предмету",
        score = 0,
        myVote = 0,
        isMine = true,
        author = null,
    )
    val previousLink = link.copy(
        periodKey = "2025-1",
        category = LinkCategory.MATERIALS,
        visibility = LinkVisibility.ALL,
        flowId = null,
        audienceLabel = null,
        myVote = -1,
        reportedByMe = true,
    )
    val audience = LinkAudience(flowId = FLOW_ID, label = "ФИЗ ПИИКТ 3.2.1", typeId = 2, depth = 3)
    val links = SubjectLinksResponse(
        mine = listOf(ownLink),
        shared = listOf(link),
        previous = listOf(previousLink),
        pinnedId = id,
        audiences = listOf(
            LinkAudience(flowId = 7101, label = "ФИЗ ПИИКТ 3", typeId = 1, depth = 1),
            LinkAudience(flowId = 7102, label = "ФИЗ ПИИКТ 3.2", typeId = 3, depth = 2),
            audience,
        ),
        premoderation = true,
    )
    val save = SaveSubjectLinkRequest(
        subjectId = SUBJECT_ID,
        subjectName = "Предмет",
        periodKey = PERIOD,
        category = LinkCategory.QUEUE,
        url = "https://example.invalid/queue",
        title = null,
        visibility = LinkVisibility.FLOW,
        flowId = FLOW_ID,
    )
    val privateSave = save.copy(visibility = LinkVisibility.PRIVATE, flowId = null)
    val restriction = UserRestriction(
        id = id,
        capability = RestrictionCapability.VOTE,
        reason = "Правила",
        startsAt = now,
        expiresAt = null,
    )

    /** [value] as a JSON object, to edit before decoding it again. */
    fun <T> jsonOf(serializer: KSerializer<T>, value: T): JsonObject =
        BackendJson.encodeToJsonElement(serializer, value).jsonObject

    /** [json] with [field] set to [wire] (a JSON text) or removed when [wire] is `null`. */
    fun JsonObject.with(field: String, wire: String?): JsonObject = JsonObject(
        toMutableMap().apply {
            if (wire == null) remove(field) else put(field, BackendJson.parseToJsonElement(wire))
        },
    )

    /** Decodes [json] from its text, the way the client decodes an answer. */
    fun <T> decode(serializer: KSerializer<T>, json: JsonElement): T =
        BackendJson.decodeFromString(serializer, json.toString())

    /** Backend's success envelope around [data], with the `error: null` Jackson writes. */
    fun envelope(data: String): String = """{"success":true,"data":$data,"error":null}"""
}
