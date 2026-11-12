package dev.alllexey.itmowidgets.feature.recordbook.domain.lms

import dev.alllexey.itmowidgets.core.result.AppError

data class LmsScore(val name: String, val earned: Double?, val max: Double, val verificationStatus: String? = null)
data class LmsCourseScores(val name: String, val program: String, val scores: List<LmsScore>? = null)

sealed interface LmsLoadResult {
    data class Success(val scores: List<LmsScore>) : LmsLoadResult
    data object EmptyCourses : LmsLoadResult
    data object NoMatch : LmsLoadResult
    data class CourseFailed(val error: AppError) : LmsLoadResult
    data class Failed(val error: AppError) : LmsLoadResult
}

/** One foreground login and read for a single subject. */
interface LmsGateway {
    val loginUrl: String
    fun authorizationState(url: String): String?
    fun callbackCode(url: String, state: String): String?
    fun isAllowedBrowserUrl(url: String): Boolean
    suspend fun canStart(): AppError?
    suspend fun load(code: String, state: String, subject: String, year: String, semester: Int): LmsLoadResult
}
