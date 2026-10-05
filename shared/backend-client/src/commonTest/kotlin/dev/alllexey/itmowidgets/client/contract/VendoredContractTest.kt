package dev.alllexey.itmowidgets.client.contract

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.Fixtures
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.TEST_RESOURCES_DIR
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import dev.alllexey.itmowidgets.client.support.ok
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Backend's contract vendored by `scripts/sync-backend-contract.sh` into `src/commonTest/resources/contract/`
 * (generated, never edited by hand), and the claims of each area in `src/commonTest/resources/claims/<area>.txt`.
 * Paths are relative to `contract/`, for example `http/users/userProfile.json`.
 */
object VendoredContract {
    private val resources: Path = TEST_RESOURCES_DIR.toPath()
    private val contract: Path = resources / "contract"
    private val claims: Path = resources / "claims"

    /** The directories whose files an area claims: route answers, request bodies and FCM data maps. */
    val FIXTURE_DIRECTORIES = listOf("http/", "requests/", "fcm/")

    fun read(path: String): String = Fixtures.read("contract/$path")

    /** Every file of the vendored contract, `contract/`-relative and sorted. */
    fun files(): List<String> = FileSystem.SYSTEM.listRecursively(contract)
        .filter { FileSystem.SYSTEM.metadata(it).isRegularFile }
        .map { it.relativeTo(contract).segments.joinToString("/") }
        .sorted()
        .toList()

    /** The fixtures an area can claim: every file under [FIXTURE_DIRECTORIES]. */
    fun fixtures(): List<String> = files().filter { path -> FIXTURE_DIRECTORIES.any { path.startsWith(it) } }

    /** The areas with a claims file, sorted. */
    fun areas(): List<String> = FileSystem.SYSTEM.list(claims)
        .map { it.name }
        .filter { it.endsWith(".txt") }
        .map { it.removeSuffix(".txt") }
        .sorted()

    /** The paths `claims/<area>.txt` lists, one per line; blank lines and `#` comments are skipped. */
    fun claims(area: String): List<String> = Fixtures.read("claims/$area.txt").lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
}

/**
 * A claimed route answer: [call] runs against a backend that answers the fixture. A value route re-encodes what
 * it decoded with [serializer] and compares it with the fixture's `data`, so a field the model lacks or misnames
 * fails instead of being skipped by `ignoreUnknownKeys`; a `Unit` route ([serializer] `null`) only has to succeed.
 */
class ResponseClaim<T>(
    val path: String,
    private val serializer: KSerializer<T>?,
    private val call: suspend BackendClient.() -> T,
) {
    suspend fun assertDecodes() {
        val body = VendoredContract.read(path)
        val value = MockBackend { ok(body) }.client.call()
        if (serializer != null) {
            val data = BackendJson.parseToJsonElement(body).jsonObject["data"] ?: fail("$path has no data")
            assertJsonEquals(data.toString(), BackendJson.encodeToString(serializer, value))
        }
    }
}

/** A claimed request body: it decodes to [serializer]'s type and encodes back to the same JSON. */
class RequestClaim<T>(val path: String, private val serializer: KSerializer<T>) {
    fun assertRoundTrips() {
        val fixture = VendoredContract.read(path)
        val value = BackendJson.decodeFromString(serializer, fixture)
        assertJsonEquals(fixture, BackendJson.encodeToString(serializer, value))
    }
}

/** Asserts that [responses] and [requests] are exactly what `claims/<area>.txt` lists, then checks each one. */
suspend fun assertAreaClaims(area: String, responses: List<ResponseClaim<*>>, requests: List<RequestClaim<*>>) {
    val checked = responses.map { it.path } + requests.map { it.path }
    assertEquals(checked.size, checked.toSet().size, "$area checks a fixture twice")
    assertEquals(VendoredContract.claims(area).sorted(), checked.sorted(), "claims/$area.txt and its checks differ")
    for (claim in responses) withPath(claim.path) { claim.assertDecodes() }
    for (claim in requests) withPath(claim.path) { claim.assertRoundTrips() }
}

private inline fun withPath(path: String, check: () -> Unit) {
    try {
        check()
    } catch (error: Throwable) {
        fail("$path: ${error.message}", error)
    }
}

/** The vendored contract's shape and the claims over it; each area's own test decodes what it claims. */
class VendoredContractTest {

    @Test
    fun backendCommitIsAFullSha() {
        val lines = VendoredContract.read("BACKEND_COMMIT").lines()

        assertTrue(Regex("[0-9a-f]{40}").matches(lines[0]), "BACKEND_COMMIT starts with ${lines[0]}")
        assertTrue(lines[1].startsWith("version "), "BACKEND_COMMIT line 2 is ${lines[1]}")
        assertTrue(lines[2].startsWith("date "), "BACKEND_COMMIT line 3 is ${lines[2]}")
    }

    @Test
    fun everyJsonParses() {
        val json = VendoredContract.files().filter { it.endsWith(".json") }

        assertTrue("index.json" in json && json.any { it.startsWith("http/") }, "no contract was vendored")
        for (path in json) {
            try {
                BackendJson.parseToJsonElement(VendoredContract.read(path))
            } catch (error: SerializationException) {
                fail("$path is not JSON: ${error.message}", error)
            }
        }
    }

    @Test
    fun everyClaimedPathExistsOnce() {
        val fixtures = VendoredContract.fixtures().toSet()
        val claimed = VendoredContract.areas().flatMap { area -> VendoredContract.claims(area).map { area to it } }

        assertTrue(VendoredContract.areas().isNotEmpty(), "no claims file")
        for ((area, path) in claimed) assertTrue(path in fixtures, "claims/$area.txt lists $path, no such fixture")
        val twice = claimed.groupBy({ it.second }, { it.first }).filterValues { it.size > 1 }
        assertEquals(emptyMap(), twice, "fixtures claimed more than once")
    }

    @Test
    fun unclaimedFixturesArePending() {
        val claimed = VendoredContract.areas().flatMap { VendoredContract.claims(it) }.toSet()

        val pending = VendoredContract.fixtures().filterNot { it in claimed }

        // CO-09b turns this report into a failure once every route is mirrored or listed as not mirrored.
        println("Vendored contract: ${claimed.size} claimed, ${pending.size} pending")
        pending.forEach { println("pending $it") }
    }
}
