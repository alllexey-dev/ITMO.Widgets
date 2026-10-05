package dev.alllexey.itmowidgets.client.contract

import dev.alllexey.itmowidgets.client.support.RecordedRequest
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The client against Backend's OpenAPI snapshot (`contract/openapi.json`) at the recorded commit: every route case
 * is an operation and every `/api` operation is mirrored or in [NotMirrored]; every model in [WireModels] matches
 * its component schema, except the [PendingBackendFields]. The seeded-drift tests prove that each check bites.
 */
class ContractConformanceTest {

    @Test
    fun routesMatchTheSnapshot() = runSuspend {
        val problems = ContractConformance.routeProblems(spec, recorded(), NotMirrored.all)

        assertNoProblems(problems)
    }

    @Test
    fun modelsMatchTheSnapshot() {
        assertNoProblems(modelProblems(spec))
    }

    @Test
    fun operationsAreUnderApi() {
        val outside = spec.operations.filterNot { it.path.startsWith("/api/") }

        assertTrue(outside.isEmpty(), "operations outside /api: $outside")
    }

    @Test
    fun seededRenamedFieldFails() {
        val drifted = spec.edited("components", "schemas", "UnregisterDeviceRequest") { schema ->
            val properties = schema.jsonObject.getValue("properties").jsonObject
            JsonObject(
                schema.jsonObject + mapOf(
                    "properties" to JsonObject(properties.mapKeys { (key) -> key.replace("fcmToken", "pushToken") }),
                    "required" to JsonArray(listOf(JsonPrimitive("pushToken"))),
                ),
            )
        }

        val problems = modelProblems(drifted)

        assertHas(problems, "UnregisterDeviceRequest.fcmToken is not in Backend's schema")
        assertHas(problems, "UnregisterDeviceRequest lacks Backend's property pushToken")
    }

    @Test
    fun seededExtraEnumValueFails() {
        val drifted = spec.edited("components", "schemas", "SubjectLink", "properties", "visibility", "enum") {
            JsonArray(it.jsonArray + JsonPrimitive("BANNED"))
        }

        assertHas(modelProblems(drifted), "SubjectLink.visibility: LinkVisibility has")
    }

    @Test
    fun seededDiscriminatorValueFails() {
        val drifted = spec.edited("components", "schemas", "SportQueueEntry", "discriminator", "mapping") { mapping ->
            JsonObject(mapping.jsonObject.mapKeys { (key) -> if (key == "free") "FREE" else key })
        }

        assertHas(modelProblems(drifted), "SportQueueEntry maps type values")
    }

    @Test
    fun seededOptionalPrivacyFieldFails() {
        val drifted = spec.edited("components", "schemas", "UserCapabilities", "required") { required ->
            JsonArray(required.jsonArray - JsonPrimitive("canViewFriends"))
        }

        assertHas(modelProblems(drifted), "UserCapabilities.canViewFriends is non-null without a default")
    }

    @Test
    fun seededNullableFieldFails() {
        val drifted = spec.edited("components", "schemas", "SubjectLink", "properties", "url") {
            buildJsonObject { put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null")))) }
        }

        assertHas(modelProblems(drifted), "SubjectLink.url may be null on Backend, not in the client")
    }

    @Test
    fun seededNestedTypeFails() {
        val drifted = spec.edited("components", "schemas", "FriendSportBooking", "properties", "entry") {
            buildJsonObject { put("\$ref", "#/components/schemas/SportQueue") }
        }

        assertHas(modelProblems(drifted), "FriendSportBooking.entry is a SportQueueEntry, Backend's is SportQueue")
    }

    @Test
    fun seededShippedPendingFieldFails() {
        val drifted = spec.edited("components", "schemas", "RegisterDeviceRequest", "properties") { properties ->
            JsonObject(properties.jsonObject + ("platform" to buildJsonObject { put("type", "string") }))
        }

        assertHas(modelProblems(drifted), "RegisterDeviceRequest.platform is in Backend's schema now")
    }

    @Test
    fun seededUnknownRouteFails() = runSuspend {
        val unknown = "probe" to RecordedRequest(HttpMethod.Get, "/api/users/me/avatar", emptyList(), null)

        val problems = ContractConformance.routeProblems(spec, recorded() + unknown, NotMirrored.all)

        assertHas(problems, "probe: GET /api/users/me/avatar is no Backend operation")
    }

    @Test
    fun seededUnlistedOperationFails() = runSuspend {
        val drifted = spec.edited("paths") { paths ->
            val operation = buildJsonObject { putJsonObject("get") { put("operationId", "user_avatar") } }
            JsonObject(paths.jsonObject + ("/api/users/me/avatar" to operation))
        }

        val problems = ContractConformance.routeProblems(drifted, recorded(), NotMirrored.all)

        assertHas(problems, "GET /api/users/me/avatar is neither mirrored nor in NotMirrored")
    }

    @Test
    fun seededQueryDriftFails() = runSuspend {
        val drifted = spec.edited("paths", "/api/app/version-info", "get") { operation ->
            JsonObject(operation.jsonObject - "parameters")
        }

        val problems = ContractConformance.routeProblems(drifted, recorded(), NotMirrored.all)

        assertHas(problems, "versionInfo: GET /api/app/version-info takes query [], the client sends [platform]")
    }

    @Test
    fun seededStaleNotMirroredEntryFails() = runSuspend {
        val stale = NotMirroredRoute(HttpMethod.Get, "/api/users/me/avatar", "probe", emptyList())

        val problems = ContractConformance.routeProblems(spec, recorded(), NotMirrored.all + stale)

        assertHas(problems, "NotMirrored GET /api/users/me/avatar matches no operation")
    }

    @Test
    fun seededUnclaimedFixtureFails() {
        val files = listOf("http/users/extra.json", "http/users/userProfile.json")
        val claims = listOf("claims/users.txt" to "http/users/userProfile.json")

        val problems = ContractConformance.claimProblems(files, claims)

        assertHas(problems, "http/users/extra.json is not claimed")
    }

    @Test
    fun seededDoubleClaimAndMissingFileFail() {
        val files = listOf("http/moderation/decide.json")
        val claims = listOf("NotMirrored" to "http/moderation/", "claims/links.txt" to "http/moderation/decide.json") +
            ("claims/users.txt" to "http/users/gone.json")

        val problems = ContractConformance.claimProblems(files, claims)

        assertHas(problems, "http/moderation/decide.json is claimed by [NotMirrored, claims/links.txt]")
        assertHas(problems, "claims/users.txt claims http/users/gone.json, no such file")
    }

    private suspend fun recorded(): List<Pair<String, RecordedRequest>> =
        AllRouteCases.all.map { it.name to it.record() }

    private fun modelProblems(snapshot: OpenApi) =
        ContractConformance.modelProblems(snapshot, WireModels.all, WireModels.enums, PendingBackendFields.all)

    private fun OpenApi.edited(vararg path: String, transform: (JsonElement) -> JsonElement): OpenApi =
        OpenApi(root.edited(path.toList(), transform).jsonObject)

    private fun assertNoProblems(problems: List<String>) =
        assertTrue(problems.isEmpty(), "${problems.size} problem(s):\n" + problems.joinToString(separator = "\n"))

    private fun assertHas(problems: List<String>, expected: String) =
        assertTrue(problems.any { it.startsWith(expected) }, "no \"$expected\" among:\n" + problems.joinToString("\n"))

    private companion object {
        val spec: OpenApi = OpenApi.parse(VendoredContract.read("openapi.json"))
    }
}
