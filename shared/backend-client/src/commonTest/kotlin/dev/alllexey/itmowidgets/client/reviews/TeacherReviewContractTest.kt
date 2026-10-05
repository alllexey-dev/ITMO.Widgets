package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.decode
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.jsonOf
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.with
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Port of Core 1.x `reviews/TeacherReviewContractTest`, one test per 1.x test with the same name. Not ported here:
 * the moderation types (`TeacherReviewRevision`, `ModeratedTeacherReview`, `ReviewRevisionStatus`,
 * `ReviewVerification`) inside the tests, since the moderation API is not mirrored (CO-02), and `lesson sync still
 * serializes and decodes its date boundaries`, which belongs to the schedule area (handed to CO-07).
 *
 * Where the 2.0 policy differs, the test asserts the new behaviour and says so: 07 Q4 (b) accepts quoted numbers and
 * booleans and keeps the last of duplicate keys; 13 Q7 (b) makes every review and summary enum a display enum, so an
 * unknown name decodes as `UNKNOWN` instead of failing the answer.
 *
 * A malformed answer fails with a [SerializationException] or, for a model `init` invariant, an
 * [IllegalArgumentException]; both are [IllegalArgumentException]s, which the client turns into a contract failure.
 */
class TeacherReviewContractTest {

    private val fixtures = TeacherReviewContractFixtures

    private fun <T> assertRoundTrips(serializer: KSerializer<T>, value: T) = assertEquals(
        value,
        BackendJson.decodeFromString(serializer, BackendJson.encodeToString(serializer, value)),
        serializer.descriptor.serialName,
    )

    private fun <T> assertDecodeFails(serializer: KSerializer<T>, json: JsonObject, message: String) {
        assertFailsWith<IllegalArgumentException>(message) { decode(serializer, json) }
    }

    private val responseJson get() = jsonOf(TeacherReviewsResponse.serializer(), fixtures.response)
    private val namedJson get() = jsonOf(TeacherReview.serializer(), fixtures.named)
    private val copyJson get() = jsonOf(TeacherReview.serializer(), fixtures.copy)
    private val mineJson get() = jsonOf(OwnTeacherReview.serializer(), fixtures.mine)
    private val saveJson get() = jsonOf(SaveTeacherReviewRequest.serializer(), fixtures.save)

    private fun decodeResponse(text: String) = BackendJson.decodeFromString(TeacherReviewsResponse.serializer(), text)

    private fun summaryJson(): JsonObject = BackendJson.parseToJsonElement(fixtures.SUMMARY_JSON).jsonObject

    private fun JsonObject.withScale(index: Int, field: String, wire: String): JsonObject {
        val scales = getValue("scales").jsonArray.toMutableList()
        scales[index] = scales[index].jsonObject.with(field, wire)
        return JsonObject(toMutableMap().apply { put("scales", JsonArray(scales)) })
    }

    private fun JsonObject.withScales(change: MutableList<JsonElement>.() -> Unit): JsonObject {
        val scales = getValue("scales").jsonArray.toMutableList().apply(change)
        return JsonObject(toMutableMap().apply { put("scales", JsonArray(scales)) })
    }

    @Test
    fun aBackendShapedResponseWithNamedAnonymousCopiedAndOwnReviewsDecodes() {
        val wire = """{"teacherIsu":100001,"providerUrl":"${fixtures.PROVIDER_URL}","reviews":[
            {"id":"${fixtures.namedId}","kind":"COMMUNITY","subjectTitle":"Математика","writtenOn":"2026-09-23",
             "writtenBeforeYear":null,"text":"${fixtures.TEXT}","score":3,"myVote":1,"verified":true,
             "reportedByMe":false,
             "author":${SyntheticUsers.userJson()},"sourceTitle":null,"sourceLink":null},
            {"id":"${fixtures.anonymousId}","kind":"COMMUNITY","subjectTitle":null,"writtenOn":"2026-09-23",
             "writtenBeforeYear":null,"text":"${fixtures.TEXT}","score":0,"myVote":0,"verified":false,
             "reportedByMe":true,
             "author":null,"sourceTitle":null,"sourceLink":null},
            {"id":"${fixtures.copyId}","kind":"REVIEWS","subjectTitle":"Физика","writtenOn":null,
             "writtenBeforeYear":2024,
             "text":"${fixtures.TEXT}","score":-1,"myVote":-1,"verified":false,"reportedByMe":false,"author":null,
             "sourceTitle":"Тестовый источник","sourceLink":"https://example.invalid/review"}],
            "mine":{"id":"${fixtures.ownId}","subjectTitle":"Математика","text":"${fixtures.TEXT}","anonymous":true,
             "status":"REJECTED","reviewNote":"Не о преподавателе","score":0,"verified":false,"writtenOn":"2026-09-28"},
            "canWrite":true,"canVote":true,"canReport":true,"knownTeacher":true,"summary":null}"""

        assertEquals(fixtures.response, BackendJson.decodeFromString(TeacherReviewsResponse.serializer(), wire))
        val empty = """{"teacherIsu":100001,"providerUrl":"${fixtures.PROVIDER_URL}","reviews":[],"mine":null,
            "canWrite":false,"canVote":false,"canReport":false,"knownTeacher":false}"""
        assertEquals(
            fixtures.response.copy(
                reviews = emptyList(),
                mine = null,
                canWrite = false,
                canVote = false,
                canReport = false,
                knownTeacher = false,
            ),
            BackendJson.decodeFromString(TeacherReviewsResponse.serializer(), empty),
        )
    }

    @Test
    fun everyReviewModelRoundTrips() {
        for (value in listOf(
            fixtures.response,
            fixtures.response.copy(reviews = emptyList(), mine = null),
            fixtures.response.copy(summary = fixtures.summary),
        )) {
            assertRoundTrips(TeacherReviewsResponse.serializer(), value)
        }
        for (value in listOf(
            fixtures.named,
            fixtures.anonymous,
            fixtures.copy,
            fixtures.copy.copy(writtenBeforeYear = null, writtenOn = LocalDate(2025, 1, 25)),
            fixtures.copy.copy(subjectTitle = null, writtenBeforeYear = null, sourceTitle = null, sourceLink = null),
        )) {
            assertRoundTrips(TeacherReview.serializer(), value)
        }
        assertRoundTrips(OwnTeacherReview.serializer(), fixtures.mine)
        assertRoundTrips(
            OwnTeacherReview.serializer(),
            fixtures.mine.copy(
                subjectTitle = null,
                status = TeacherReviewStatus.PUBLISHED,
                reviewNote = null,
                verified = true,
            ),
        )
        assertRoundTrips(SaveTeacherReviewRequest.serializer(), fixtures.save)
        assertRoundTrips(SaveTeacherReviewRequest.serializer(), SaveTeacherReviewRequest(text = fixtures.TEXT))
        assertRoundTrips(TeacherSummary.serializer(), fixtures.summary)
        assertRoundTrips(
            TeacherSummary.serializer(),
            fixtures.summary.copy(pros = emptyList(), cons = emptyList(), tags = emptyList()),
        )
        assertRoundTrips(TeacherSummaryLevel.serializer(), TeacherSummaryLevel(123456, SummaryLevel.VERY_NEGATIVE))
    }

    @Test
    fun reviewModelsUseTheExactWireKeys() {
        // The declared wire names, nullable ones included; the client omits nulls on encode (`explicitNulls = false`).
        assertEquals(
            listOf(
                "teacherIsu", "providerUrl", "reviews", "mine", "canWrite", "canVote", "canReport", "knownTeacher",
                "summary",
            ),
            wireKeys(TeacherReviewsResponse.serializer()),
        )
        assertEquals(
            listOf(
                "reviewCount", "description", "pros", "cons", "tags", "scales", "level", "confidence", "generatedAt",
            ),
            wireKeys(TeacherSummary.serializer()),
        )
        assertEquals(listOf("kind", "value", "reason"), wireKeys(TeacherSummaryScale.serializer()))
        assertEquals(listOf("teacherIsu", "level"), wireKeys(TeacherSummaryLevel.serializer()))
        assertEquals(REVIEW_KEYS, wireKeys(TeacherReview.serializer()))
        assertEquals(
            listOf("id", "subjectTitle", "text", "anonymous", "status", "reviewNote", "score", "verified", "writtenOn"),
            wireKeys(OwnTeacherReview.serializer()),
        )
        assertEquals(
            setOf(
                "id", "kind", "subjectTitle", "writtenOn", "text", "score", "myVote", "verified", "reportedByMe",
                "author",
            ),
            jsonOf(TeacherReview.serializer(), fixtures.named).keys,
        )
        assertEquals(
            setOf(
                "id", "kind", "subjectTitle", "writtenBeforeYear", "text", "score", "myVote", "verified",
                "reportedByMe", "sourceTitle", "sourceLink",
            ),
            jsonOf(TeacherReview.serializer(), fixtures.copy).keys,
        )
        assertEquals(
            BackendJson.parseToJsonElement(
                """{"subjectTitle":"Математика","text":"${fixtures.TEXT}","anonymous":false,"flowIds":[93724,93725]}""",
            ),
            jsonOf(SaveTeacherReviewRequest.serializer(), fixtures.save),
        )
    }

    @Test
    fun aResponseWithoutASummaryOrWithANullSummaryHasNone() {
        val base = responseJson.with("summary", null)
        assertNull(decode(TeacherReviewsResponse.serializer(), base).summary)
        assertNull(decode(TeacherReviewsResponse.serializer(), base.with("summary", "null")).summary)
    }

    @Test
    fun aBackendShapedSummaryDecodesWithUnknownTagsKeptAsStrings() {
        val wire = responseJson.with("summary", fixtures.SUMMARY_JSON)

        val decoded = decode(TeacherReviewsResponse.serializer(), wire)

        assertEquals(fixtures.response.copy(summary = fixtures.summary), decoded)
        assertEquals(listOf("MANY_LABS", "NEW_TAG"), decoded.summary?.tags)
        assertNull(decoded.summary?.scales?.single { it.value == SummaryScaleValue.NOT_ENOUGH_DATA }?.reason)
    }

    @Test
    fun summariesThatBreakTheContractAreRejected() {
        val invalid = mapOf<String, JsonObject.() -> JsonObject>(
            "reviewCount 2" to { with("reviewCount", "2") },
            "four scales" to { withScales { removeAt(4) } },
            "six scales" to { withScales { add(this[0]) } },
            "repeated kind" to { withScale(4, "kind", "\"EXPLAINS\"") },
            "scale kind as a number" to { withScale(4, "kind", "4") },
            "scale as a string" to { withScales { set(4, JsonPrimitive("WORKLOAD")) } },
            "reason without data" to { withScale(2, "reason", "\"Мало отзывов\"") },
            "reason as a number" to { withScale(0, "reason", "1") },
            "numeric tag" to { with("tags", "[1]") },
            "null tag" to { with("tags", "[null]") },
            "numeric pro" to { with("pros", "[1]") },
            "object con" to { with("cons", "[{}]") },
            "local generatedAt" to { with("generatedAt", "\"2026-09-29T09:00:00\"") },
            "date generatedAt" to { with("generatedAt", "\"2026-09-29\"") },
            "numeric generatedAt" to { with("generatedAt", "1790000000") },
        )
        for ((name, change) in invalid) {
            val summary = summaryJson().change()
            assertDecodeFails(TeacherSummary.serializer(), summary, name)
            val response = responseJson.with("summary", summary.toString())
            assertDecodeFails(TeacherReviewsResponse.serializer(), response, "response: $name")
        }
        // 2.0 policy (13 Q7 (b)): the summary enums are display enums, so an unknown kind, value, level or confidence
        // decodes as UNKNOWN instead of failing the answer; 1.x rejected them.
        val unknown = summaryJson()
            .withScale(4, "kind", "\"HUMOUR\"")
            .withScale(3, "value", "\"EXTREME\"")
            .with("level", "\"NEUTRAL\"")
            .with("confidence", "\"CERTAIN\"")
        val decoded = decode(TeacherSummary.serializer(), unknown)
        assertEquals(SummaryScaleKind.UNKNOWN, decoded.scales[4].kind)
        assertEquals(SummaryScaleValue.UNKNOWN, decoded.scales[3].value)
        assertEquals(SummaryLevel.UNKNOWN, decoded.level)
        assertEquals(SummaryConfidence.UNKNOWN, decoded.confidence)
        for (wire in listOf("\"summary\"", "[]", "42", "true")) {
            assertDecodeFails(TeacherReviewsResponse.serializer(), responseJson.with("summary", wire), "summary: $wire")
        }
    }

    @Test
    fun summaryLevelsAreStrict() {
        val serializer = TeacherSummaryLevel.serializer()
        assertEquals(
            TeacherSummaryLevel(123456, SummaryLevel.VERY_POSITIVE),
            BackendJson.decodeFromString(serializer, """{"teacherIsu":123456,"level":"VERY_POSITIVE"}"""),
        )
        for (wire in listOf(
            """{"teacherIsu":123456,"level":null}""",
            """{"teacherIsu":123456}""",
            """{"level":"MIXED"}""",
            """{"teacherIsu":0,"level":"MIXED"}""",
            """{"teacherIsu":123456,"level":4}""",
        )) {
            assertFailsWith<IllegalArgumentException>(wire) { BackendJson.decodeFromString(serializer, wire) }
        }
        // 2.0 policy: an unknown tone is UNKNOWN (13 Q7 (b)); a quoted ISU is accepted and the last of duplicate keys
        // wins (07 Q4 (b)). 1.x rejected all three.
        assertEquals(
            TeacherSummaryLevel(123456, SummaryLevel.UNKNOWN),
            BackendJson.decodeFromString(serializer, """{"teacherIsu":123456,"level":"NEUTRAL"}"""),
        )
        assertEquals(
            TeacherSummaryLevel(123456, SummaryLevel.MIXED),
            BackendJson.decodeFromString(serializer, """{"teacherIsu":"123456","level":"MIXED"}"""),
        )
        assertEquals(
            TeacherSummaryLevel(123456, SummaryLevel.MIXED),
            BackendJson.decodeFromString(serializer, """{"teacherIsu":123456,"level":"POSITIVE","level":"MIXED"}"""),
        )
    }

    @Test
    fun aDefaultSaveRequestIsAnonymousWithoutFlows() {
        assertEquals(
            BackendJson.parseToJsonElement("""{"text":"${fixtures.TEXT}","anonymous":true,"flowIds":[]}"""),
            jsonOf(SaveTeacherReviewRequest.serializer(), SaveTeacherReviewRequest(text = fixtures.TEXT)),
        )
    }

    @Test
    fun optionalReviewFieldsDecodeFromBothExplicitNullAndAbsence() {
        assertOptional(
            TeacherReview.serializer(),
            fixtures.named,
            listOf("subjectTitle", "writtenOn", "author"),
            fixtures.named.copy(subjectTitle = null, writtenOn = null, author = null),
        )
        assertOptional(
            TeacherReview.serializer(),
            fixtures.copy,
            listOf("subjectTitle", "writtenBeforeYear", "sourceTitle", "sourceLink"),
            fixtures.copy.copy(subjectTitle = null, writtenBeforeYear = null, sourceTitle = null, sourceLink = null),
        )
        assertOptional(
            OwnTeacherReview.serializer(),
            fixtures.mine,
            listOf("subjectTitle", "reviewNote"),
            fixtures.mine.copy(subjectTitle = null, reviewNote = null),
        )
        assertOptional(
            TeacherReviewsResponse.serializer(),
            fixtures.response.copy(summary = fixtures.summary),
            listOf("mine", "summary"),
            fixtures.response.copy(mine = null),
        )
        assertOptional(
            SaveTeacherReviewRequest.serializer(),
            fixtures.save,
            listOf("subjectTitle"),
            fixtures.save.copy(subjectTitle = null),
        )
        assertOptional(
            TeacherSummaryScale.serializer(),
            fixtures.summary.scales[2],
            listOf("reason"),
            fixtures.summary.scales[2],
        )
    }

    @Test
    fun requiredFieldsNeverBecomeJvmDefaults() {
        assertRequired(
            TeacherReviewsResponse.serializer(),
            fixtures.response,
            "teacherIsu", "providerUrl", "reviews", "canWrite", "canVote", "canReport", "knownTeacher",
        )
        assertRequired(
            TeacherReview.serializer(),
            fixtures.named,
            "id", "kind", "text", "score", "myVote", "verified", "reportedByMe",
        )
        assertRequired(
            OwnTeacherReview.serializer(),
            fixtures.mine,
            "id", "text", "anonymous", "status", "score", "verified", "writtenOn",
        )
        assertRequired(
            TeacherSummary.serializer(),
            fixtures.summary,
            "reviewCount", "description", "pros", "cons", "tags", "scales", "level", "confidence", "generatedAt",
        )
        assertRequired(TeacherSummaryScale.serializer(), fixtures.summary.scales[0], "kind", "value")
        assertRequired(
            TeacherSummaryLevel.serializer(),
            TeacherSummaryLevel(123456, SummaryLevel.MIXED),
            "teacherIsu", "level",
        )
        // SaveTeacherReviewRequest: `text` is required; `anonymous` and `flowIds` have Backend's own defaults (true,
        // empty), which the client always encodes, so a body without them decodes to exactly those defaults.
        val save = jsonOf(SaveTeacherReviewRequest.serializer(), fixtures.save)
        assertDecodeFails(SaveTeacherReviewRequest.serializer(), save.with("text", null), "text missing")
        assertDecodeFails(SaveTeacherReviewRequest.serializer(), save.with("text", "null"), "text null")
        assertDecodeFails(SaveTeacherReviewRequest.serializer(), save.with("anonymous", "null"), "anonymous null")
        assertDecodeFails(SaveTeacherReviewRequest.serializer(), save.with("flowIds", "null"), "flowIds null")
        assertEquals(
            SaveTeacherReviewRequest(subjectTitle = "Математика", text = fixtures.TEXT),
            decode(SaveTeacherReviewRequest.serializer(), save.with("anonymous", null).with("flowIds", null)),
        )
    }

    @Test
    fun requiredValuesRejectIncompatibleJsonShapes() {
        // 2.0 policy (07 Q4 (b)): a quoted number or boolean is accepted (`"100001"`, `"3"`, `"true"`, `"false"`,
        // `["93724"]`); 1.x rejected them, so those cases moved to the acceptance list below.
        assertMalformed(
            TeacherReviewsResponse.serializer(),
            fixtures.response,
            "teacherIsu" to "0", "teacherIsu" to "-1", "teacherIsu" to "2147483648", "providerUrl" to "42",
            "reviews" to "{}", "reviews" to "[null]", "reviews" to "[42]", "knownTeacher" to "1", "mine" to "\"mine\"",
        )
        assertMalformed(
            TeacherReview.serializer(),
            fixtures.named,
            "id" to "\"not-a-uuid\"", "myVote" to "2", "myVote" to "-2", "score" to "1.5", "verified" to "1",
            "text" to "42", "author" to "\"author\"",
        )
        assertMalformed(
            OwnTeacherReview.serializer(),
            fixtures.mine,
            "id" to "\"not-a-uuid\"", "anonymous" to "1", "writtenOn" to "\"28.09.2026\"",
            "writtenOn" to "\"2026-09-28T10:00:00Z\"", "score" to "true",
        )
        assertMalformed(
            SaveTeacherReviewRequest.serializer(),
            fixtures.save,
            "anonymous" to "1", "flowIds" to "[null]", "flowIds" to "[1.5]", "flowIds" to "[9223372036854775808]",
            "text" to "42",
        )

        val accepted = listOf(
            decode(TeacherReviewsResponse.serializer(), responseJson.with("teacherIsu", "\"100001\"")).teacherIsu to
                fixtures.TEACHER,
            decode(TeacherReviewsResponse.serializer(), responseJson.with("canWrite", "\"true\"")).canWrite to true,
            decode(TeacherReview.serializer(), namedJson.with("score", "\"3\"")).score to 3,
            decode(TeacherReview.serializer(), namedJson.with("reportedByMe", "\"false\"")).reportedByMe to false,
            decode(
                SaveTeacherReviewRequest.serializer(),
                jsonOf(SaveTeacherReviewRequest.serializer(), fixtures.save).with("flowIds", "[\"93724\"]"),
            ).flowIds to listOf(93724L),
        )
        for ((actual, expected) in accepted) assertEquals(expected, actual)
    }

    @Test
    fun optionalReviewStringsDoNotCoerceOtherJsonShapes() {
        val optional = listOf(
            Triple(TeacherReview.serializer(), namedJson, listOf("subjectTitle", "writtenOn")),
            Triple(TeacherReview.serializer(), copyJson, listOf("sourceTitle", "sourceLink")),
            Triple(OwnTeacherReview.serializer(), mineJson, listOf("subjectTitle", "reviewNote")),
            Triple(SaveTeacherReviewRequest.serializer(), saveJson, listOf("subjectTitle")),
        )
        for ((serializer, json, names) in optional) for (name in names) for (wire in listOf("42", "true", "{}", "[]")) {
            assertFailsWith<IllegalArgumentException>("${serializer.descriptor.serialName}.$name: $wire") {
                decode(serializer, json.with(name, wire))
            }
        }
    }

    @Test
    fun reviewKindsCarryOnlyTheirOwnFieldsAndAtMostOneDate() {
        val named = jsonOf(TeacherReview.serializer(), fixtures.named)
        val anonymous = jsonOf(TeacherReview.serializer(), fixtures.anonymous)
        val copy = jsonOf(TeacherReview.serializer(), fixtures.copy)
        val invalid = listOf(
            copy.with("author", SyntheticUsers.userJson()),
            copy.with("verified", "true"),
            copy.with("reportedByMe", "true"),
            named.with("sourceLink", "\"https://example.invalid/review\""),
            named.with("sourceTitle", "\"Тестовый источник\""),
            anonymous.with("writtenOn", null).with("writtenBeforeYear", "2024"),
            named.with("writtenBeforeYear", "2024"),
            copy.with("writtenOn", "\"2025-01-25\""),
        )
        for (json in invalid) assertDecodeFails(TeacherReview.serializer(), json, json.toString())
        // 2.0: the app cannot build such a review either.
        assertFailsWith<IllegalArgumentException> { fixtures.copy.copy(author = fixtures.author) }
        assertFailsWith<IllegalArgumentException> { fixtures.named.copy(writtenBeforeYear = 2024) }
        // 2.0 policy (07 Q4 (b)): a quoted year is accepted; 1.x rejected it.
        val quotedYear = decode(TeacherReview.serializer(), copy.with("writtenBeforeYear", "\"2024\""))
        assertEquals(2024, quotedYear.writtenBeforeYear)
    }

    @Test
    fun reviewEnumsAreStrict() {
        // 2.0 policy (13 Q7 (b)): every review and summary enum is a display enum. An unknown name decodes as
        // UNKNOWN instead of failing the answer (1.x rejected it); null, numbers and other shapes still fail, and
        // UNKNOWN is never encoded.
        assertDisplayEnum(TeacherReviewKindSerializer, TeacherReviewKind.entries, TeacherReviewKind.UNKNOWN)
        assertDisplayEnum(TeacherReviewStatusSerializer, TeacherReviewStatus.entries, TeacherReviewStatus.UNKNOWN)
        assertDisplayEnum(SummaryLevelSerializer, SummaryLevel.entries, SummaryLevel.UNKNOWN)
        assertDisplayEnum(SummaryConfidenceSerializer, SummaryConfidence.entries, SummaryConfidence.UNKNOWN)
        assertDisplayEnum(SummaryScaleKindSerializer, SummaryScaleKind.entries, SummaryScaleKind.UNKNOWN)
        assertDisplayEnum(SummaryScaleValueSerializer, SummaryScaleValue.entries, SummaryScaleValue.UNKNOWN)

        val external = decode(TeacherReview.serializer(), namedJson.with("kind", "\"EXTERNAL\""))
        assertEquals(TeacherReviewKind.UNKNOWN, external.kind)
        val deleted = decode(OwnTeacherReview.serializer(), mineJson.with("status", "\"DELETED\""))
        assertEquals(TeacherReviewStatus.UNKNOWN, deleted.status)
        assertDecodeFails(TeacherReview.serializer(), namedJson.with("kind", "null"), "kind null")
        assertDecodeFails(OwnTeacherReview.serializer(), mineJson.with("status", "1"), "status 1")
    }

    @Test
    fun duplicateKeysAreRejectedInTheResponseAndNestedModels() {
        // 2.0 policy (07 Q4 (b)): duplicate keys are not rejected, the last value wins; 1.x rejected them.
        val responseText = fixtures.responseJson()
        val duplicateResponse = responseText.dropLast(1) + ",\"canWrite\":false}"
        assertEquals(false, decodeResponse(duplicateResponse).canWrite)

        val anonymousText = BackendJson.encodeToString(TeacherReview.serializer(), fixtures.anonymous)
        val duplicateReview = anonymousText.dropLast(1) + ",\"text\":\"Другой отзыв\"}"
        assertEquals("Другой отзыв", BackendJson.decodeFromString(TeacherReview.serializer(), duplicateReview).text)

        val base = responseJson.with("reviews", null).with("mine", null).toString().dropLast(1)
        val nested = decodeResponse("$base,\"reviews\":[$duplicateReview]}")
        assertEquals("Другой отзыв", nested.reviews.single().text)
        val mineText = BackendJson.encodeToString(OwnTeacherReview.serializer(), fixtures.mine)
        val duplicateMine = mineText.dropLast(1) + ",\"anonymous\":false}"
        val withMine = decodeResponse("$base,\"reviews\":[],\"mine\":$duplicateMine}")
        assertEquals(false, withMine.mine?.anonymous)
    }

    @Test
    fun reviewModelsRejectNonObjectJsonRoots() {
        val serializers = listOf(
            TeacherReviewsResponse.serializer(),
            TeacherReview.serializer(),
            OwnTeacherReview.serializer(),
            SaveTeacherReviewRequest.serializer(),
            TeacherSummary.serializer(),
            TeacherSummaryScale.serializer(),
            TeacherSummaryLevel.serializer(),
        )
        for (serializer in serializers) for (wire in listOf("[]", "\"review\"", "42", "true")) {
            assertFailsWith<SerializationException>("${serializer.descriptor.serialName}: $wire") {
                BackendJson.decodeFromString(serializer, wire)
            }
        }
    }

    private fun wireKeys(serializer: KSerializer<*>): List<String> = serializer.descriptor.elementNames.toList()

    private fun <T> assertOptional(serializer: KSerializer<T>, value: T, names: List<String>, expected: T) {
        val json = jsonOf(serializer, value)
        val name = serializer.descriptor.serialName
        val nulls = names.fold(json) { edited, field -> edited.with(field, "null") }
        assertEquals(expected, decode(serializer, nulls), "$name nulls")
        val absent = names.fold(json) { edited, field -> edited.with(field, null) }
        assertEquals(expected, decode(serializer, absent), "$name absent")
    }

    private fun <T> assertRequired(serializer: KSerializer<T>, value: T, vararg names: String) {
        val json = jsonOf(serializer, value)
        for (field in names) {
            assertDecodeFails(serializer, json.with(field, null), "${serializer.descriptor.serialName}.$field missing")
            assertDecodeFails(serializer, json.with(field, "null"), "${serializer.descriptor.serialName}.$field null")
        }
    }

    private fun <T> assertMalformed(serializer: KSerializer<T>, value: T, vararg fields: Pair<String, String>) {
        val json = jsonOf(serializer, value)
        for ((field, wire) in fields) {
            assertDecodeFails(serializer, json.with(field, wire), "${serializer.descriptor.serialName}.$field: $wire")
        }
    }

    private fun <E> assertDisplayEnum(serializer: KSerializer<E>, entries: List<E>, unknown: E) {
        val name = serializer.descriptor.serialName
        for (value in entries - unknown) {
            val text = BackendJson.encodeToString(serializer, value)
            assertEquals("\"$value\"", text)
            assertEquals(value, BackendJson.decodeFromString(serializer, text))
        }
        for (wire in listOf("\"UNKNOWN\"", "\"FUTURE\"")) {
            assertEquals(unknown, BackendJson.decodeFromString(serializer, wire), "$name: $wire")
        }
        for (wire in listOf("null", "0", "true", "{}", "[]")) {
            assertFailsWith<SerializationException>("$name: $wire") { BackendJson.decodeFromString(serializer, wire) }
        }
        assertFailsWith<SerializationException> { BackendJson.encodeToString(serializer, unknown) }
    }

    private companion object {
        val REVIEW_KEYS = listOf(
            "id", "kind", "subjectTitle", "writtenOn", "writtenBeforeYear", "text", "score", "myVote", "verified",
            "reportedByMe", "author", "sourceTitle", "sourceLink",
        )
    }
}
