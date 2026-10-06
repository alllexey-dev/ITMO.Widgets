package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.BarsCodeSupplier
import dev.alllexey.itmoapi.bars.BarsConfiguration
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import dev.alllexey.itmoapi.bars.BarsClient as LibraryBarsClient

/** Synthetic BARS session headers; never a real credential. */
internal const val OLD_HEADER = "Bearer synthetic-old-credential"
internal const val FRESH_HEADER = "Bearer synthetic-fresh-credential"

/**
 * In-memory `bars_tokens.enc` behind the real [BarsTokenStore]: a [SecureStore] that keeps the one session name sealed
 * by a reversible stand-in for the Keystore cipher, so [value] is what would be on disk.
 */
internal class MemoryBarsTokens : SecureStore {
    var value: String? = null

    override fun read(name: String): String? =
        value?.let { String(Base64.getDecoder().decode(it)) }.also { require(name == SESSION_NAME) }

    override fun write(name: String, value: String) {
        require(name == SESSION_NAME)
        this.value = Base64.getEncoder().encodeToString(value.toByteArray())
    }

    override fun delete(name: String) {
        require(name == SESSION_NAME)
        value = null
    }

    val store = BarsTokenStore(this)

    private companion object {
        const val SESSION_NAME = "bars_tokens.enc"
    }
}

/** Counts successful BARS answers reported by the client. */
internal class CountingBarsSessionListener : BarsSessionListener {
    var answers = 0

    override suspend fun onBarsAnswered() {
        answers++
    }
}

/**
 * Stateful BARS stand-in on a MockEngine: identity, selected period, one issued session, one rejected header and own
 * journals of flow 7. [offline] paths fail before any answer, as with no network.
 */
internal class BarsTestServer {
    var login = "123"
    var year = "2026/2027"
    var term = 1
    var rejected: String? = null
    var ignoreWrites = false
    var logins = 0
    var plans = listOf(1L, 2L)
    val courseProjects = mutableSetOf<Long>()
    val offline = mutableSetOf<String>()
    val settings = CopyOnWriteArrayList<String>()
    val authorizations = CopyOnWriteArrayList<String?>()
    val requests = CopyOnWriteArrayList<String>()
    val requestCount get() = requests.size

    val engine = MockEngine { request -> answer(request) }

    /** The library client as `BarsClient` builds it over the platform's engine, here this server. */
    fun library(storage: OwnerBoundBarsStorage, renewal: BarsCodeSupplier) =
        LibraryBarsClient(engine, BarsConfiguration(), storage, renewal)

    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.removePrefix("/backend/rest/").trimEnd('/')
        if (offline.any { path.startsWith(it) }) throw IOException("Synthetic offline")
        requests += path
        val authorization = request.headers[HttpHeaders.Authorization]
        authorizations += authorization
        if (path == "login") {
            logins++
            return respond("", HttpStatusCode.OK, headersOf(HttpHeaders.Authorization, FRESH_HEADER))
        }
        if (authorization == null || authorization == rejected) return respond("", HttpStatusCode.Unauthorized)
        val journal = Regex("""^marks/(\d+)/flow/7/student$""").find(path)?.groupValues?.get(1)?.toLong()
        return when {
            path == "users/current_user" -> respondJson(
                """{"id":1,"login":"$login","selected_year":"$year","selected_term":$term,"user_roles":[],"personal_config":[]}"""
            )
            path == "config/personal" -> {
                val body = request.bodyText()
                val name = Regex("\"name\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                val value = Regex("\"value\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                settings += "$name=$value"
                if (!ignoreWrites) { if (name == "current_year") year = value else term = value.toInt() }
                respondJson(body)
            }
            path == "journal/disciplines" -> respondJson(
                """[{"id":90,"name":"Тестовый предмет","checkpoint_plan_ids":${plans.joinToString(",", "[", "]")}}]"""
            )
            path == "journal/groups-and-flows" -> respondJson(
                """[{"type":"flow","name":"Поток","identifier":"7","checkpoint_plan_ids":${plans.joinToString(",", "[", "]")}}]"""
            )
            journal != null -> respondJson(journal(journal, courseProject = journal in courseProjects))
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private fun journal(planId: Long, courseProject: Boolean): String {
        val checkpoint = planId * 10
        return """{"students":[{"student_login":"123","marks":{"regular":[{"id":1,"checkpoint_id":$checkpoint,
            "checkpoint_plan_id":$planId,"mark":7.5,"is_absent":false}],"total":7.5,"active_approvals":[]}}],
            "headers":{"plan":{"id":$planId,"year":"2026/2027","discipline":{"id":${90 + planId},"name":"Тестовый предмет $planId"},
            "regular_checkpoints":[{"id":$checkpoint,"name":"Работа","type":"Тест","min_grade":1.0,"max_grade":10.0,"key":true,
            "sub_checkpoints":[]}],"has_course_project":$courseProject},"type":"flow","identifier":"7"}}"""
    }
}
