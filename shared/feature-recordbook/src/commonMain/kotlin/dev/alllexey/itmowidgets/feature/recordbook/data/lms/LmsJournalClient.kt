package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** LMS tokens go only to its API; redirects and cookies are disabled. The caller must verify the account first. */
fun lmsHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 60_000
    }
}

/** Read-only journal transport. Its token is kept only by the caller and never included in an error. */
class LmsJournalClient(
    private val client: HttpClient,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) {
    suspend fun exchangeToken(code: String, state: String): AppResult<String> = execute {
        val response = client.post("https://api.tuxedo.se.ifmo.ru/api/v1/sso/token") {
            contentType(ContentType.Application.Json)
            setBody(LmsJournal.tokenExchangeRequest(code, state, LmsAuthorization.REDIRECT_URI))
        }
        if (!response.status.isSuccess()) throw LmsHttpFailure(response.status.value)
        LmsJournal.accessToken(response.bodyAsText())
    }

    suspend fun programs(accessToken: String): AppResult<List<LmsProgram>> = read(accessToken) {
        val all = mutableListOf<LmsProgram>()
        val ids = mutableSetOf<Long>()
        var pageNumber = 0
        var total: Int
        do {
            val page = LmsJournal.programs(request(accessToken, "programs/by-student", mapOf(
                "pageSize" to PAGE_SIZE.toString(), "pageNumber" to pageNumber.toString(),
            )))
            total = page.total
            if (page.items.isEmpty() && all.size < total || page.items.any { !ids.add(it.id) }) throw LmsInvalidResponse()
            all += page.items
            if (all.size > total || ++pageNumber > MAX_PAGES) throw LmsInvalidResponse()
        } while (all.size < total)
        all.toList()
    }

    suspend fun courses(accessToken: String, programId: Long): AppResult<List<LmsCourse>> = read(accessToken) {
        require(programId > 0)
        LmsJournal.courses(request(accessToken, "programs/$programId/courses/by-student"))
    }

    suspend fun modules(accessToken: String, programId: Long, courseId: Long): AppResult<List<LmsModuleScore>> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        if (programId <= 0 || courseId <= 0 || !validToken(accessToken)) {
            return AppResult.Failure(AppError.Unauthorized)
        }
        return execute {
            val overall = get("student-journal/overall", accessToken, programId, courseId)
            val gradeId = LmsJournal.courseGradeId(overall)
            val all = mutableListOf<LmsModuleScore>()
            val ids = mutableSetOf<Long>()
            var pageNumber = 0
            var total: Int
            do {
                val page = LmsJournal.modules(get("student-journal/modules", accessToken, programId, courseId, gradeId, pageNumber))
                total = page.total
                if (page.items.isEmpty() && all.size < total) throw LmsInvalidResponse()
                if (page.items.any { !ids.add(it.id) }) throw LmsInvalidResponse()
                all += page.items
                if (all.size > total || ++pageNumber > MAX_PAGES) throw LmsInvalidResponse()
            } while (all.size < total)
            all.toList()
        }
    }

    private suspend fun <T> read(accessToken: String, block: suspend () -> T): AppResult<T> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        if (!validToken(accessToken)) return AppResult.Failure(AppError.Unauthorized)
        return execute(block)
    }

    private suspend fun <T> execute(block: suspend () -> T): AppResult<T> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        return try {
            AppResult.Success(withContext(dispatchers.io) { block() })
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: LmsHttpFailure) {
            AppResult.Failure(when (failure.status) {
                401 -> AppError.Unauthorized
                403 -> AppError.Forbidden
                404 -> AppError.NotFound
                else -> AppError.Unknown()
            })
        } catch (failure: Exception) {
            AppResult.Failure(if (failure.isCausedByNetworkFailure()) AppError.Network else AppError.Unknown())
        }
    }

    private fun validToken(token: String): Boolean = token.isNotEmpty() && token.all { it > ' ' && it < '\u007f' }

    private suspend fun request(token: String, path: String, query: Map<String, String> = emptyMap()): String {
        val response = client.get("https://api.tuxedo.se.ifmo.ru/api/v1/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            url { query.forEach { (key, value) -> parameters.append(key, value) } }
        }
        if (!response.status.isSuccess()) throw LmsHttpFailure(response.status.value)
        return response.bodyAsText()
    }

    private suspend fun get(
        path: String,
        token: String,
        programId: Long,
        courseId: Long,
        gradeId: Long? = null,
        pageNumber: Int = 0,
    ): String {
        val response = client.get("https://api.tuxedo.se.ifmo.ru/api/v1/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            url {
                parameters.append("programId", programId.toString())
                parameters.append("courseId", courseId.toString())
                if (gradeId != null) {
                    parameters.append("courseGradeId", gradeId.toString())
                    parameters.append("pageSize", PAGE_SIZE.toString())
                    parameters.append("pageNumber", pageNumber.toString())
                }
            }
        }
        if (!response.status.isSuccess()) throw LmsHttpFailure(response.status.value)
        return response.bodyAsText()
    }

    private class LmsHttpFailure(val status: Int) : Exception()
    private class LmsInvalidResponse : Exception()

    private companion object {
        const val PAGE_SIZE = 10
        const val MAX_PAGES = 1_000
    }
}
