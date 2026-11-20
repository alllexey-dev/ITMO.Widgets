package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsCourseScores
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsGateway
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsLoadResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsMatching
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsScore
import io.ktor.client.engine.HttpClientEngine
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A foreground-only session. Credentials and scores are never persisted or sent to Backend. */
class AndroidLmsGateway(
    private val demo: DemoMode,
    private val currentUser: CurrentUserProvider,
    private val dispatchers: AppDispatchers,
    engine: HttpClientEngine,
) : LmsGateway {
    private val client = LmsJournalClient(lmsHttpClient(engine), demo, dispatchers)

    override val loginUrl = LmsAuthorization.LOGIN_URL
    override fun authorizationState(url: String) = LmsAuthorization.stateFromAuthorization(url)
    override fun callbackCode(url: String, state: String) = LmsAuthorization.callbackCode(url, state)
    override fun isAllowedBrowserUrl(url: String) = LmsAuthorization.isAllowedBrowserUrl(url)

    override suspend fun canStart(): AppError? = when {
        demo.isActive() -> AppError.DemoUnavailable
        currentUser.getCurrentUser()?.isu == null -> AppError.Unauthorized
        else -> null
    }

    override suspend fun load(
        code: String,
        state: String,
        subject: String,
        year: String,
        semester: Int,
    ): LmsLoadResult {
        canStart()?.let { return LmsLoadResult.Failed(it) }
        val owner = currentUser.getCurrentUser()?.isu ?: return LmsLoadResult.Failed(AppError.Unauthorized)
        val token = when (val result = client.exchangeToken(code, state)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return LmsLoadResult.Failed(result.error)
        }
        if (tokenUsername(token) != owner.toString() || !isOwner(owner)) {
            return LmsLoadResult.Failed(AppError.Forbidden)
        }
        val programs = when (val result = client.programs(token)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return LmsLoadResult.Failed(result.error)
        }
        val enrolled = mutableListOf<Pair<LmsProgram, LmsCourse>>()
        for (program in programs) {
            if (!isOwner(owner)) return LmsLoadResult.Failed(AppError.Forbidden)
            val courses = when (val result = client.courses(token, program.id)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> return LmsLoadResult.Failed(result.error)
            }
            courses.forEach { course -> enrolled += program to course }
        }
        if (enrolled.isEmpty()) return LmsLoadResult.EmptyCourses
        val summaries = enrolled.map { (program, course) -> LmsCourseScores(course.name, program.name) }
        val matched = LmsMatching.course(summaries, subject, year, semester) ?: return LmsLoadResult.NoMatch
        val (program, course) = enrolled[summaries.indexOfFirst { it === matched }]
        if (!isOwner(owner)) return LmsLoadResult.Failed(AppError.Forbidden)
        return when (val result = client.modules(token, program.id, course.id)) {
            is AppResult.Success -> LmsLoadResult.Success(
                result.value.mapNotNull { score ->
                    val max = score.maxGrade?.takeIf { it > 0 } ?: return@mapNotNull null
                    LmsScore(score.name, score.earnedPoints, max, score.verificationStatus)
                },
            )
            is AppResult.Failure -> LmsLoadResult.CourseFailed(result.error)
        }
    }

    private suspend fun isOwner(owner: Int): Boolean = !demo.isActive() && currentUser.getCurrentUser()?.isu == owner

    private fun tokenUsername(token: String): String? = runCatching {
        val payload = token.split('.')[1]
        val claims = JSON.parseToJsonElement(PAYLOAD_BASE64.decode(payload).decodeToString()).jsonObject
        claims["preferred_username"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: claims["username"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val PAYLOAD_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}
