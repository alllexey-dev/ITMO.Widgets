package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.javaToday
import dev.alllexey.itmowidgets.feature.reviews.data.demo.DemoReviews
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.resources.ModerationReportRequest
import dev.alllexey.itmowidgets.core.model.resources.ResourceVoteRequest
import dev.alllexey.itmowidgets.core.model.reviews.SaveTeacherReviewRequest
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewsResponse
import dev.alllexey.itmowidgets.core.network.appResultOf
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewLimits
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Singleton
class TeacherReviewsRepositoryImpl @Inject constructor(
    private val backend: BackendGate,
    private val widgetsApi: ItmoWidgetsApi,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : TeacherReviewsRepository, SessionDataCleaner {
    private val cache = ConcurrentHashMap<Int, TeacherReviews>()
    private val updates = MutableSharedFlow<TeacherReviews>(extraBufferCapacity = 8)
    private val cacheLock = Any()
    private var cacheGeneration = 0L
    @Volatile private var enabled: Boolean? = null

    init {
        scope.launch {
            backend.observeConnected().collect { on ->
                synchronized(cacheLock) { updateEnabled(on) }
            }
        }
    }

    override fun cachedReviews(isu: Int): TeacherReviews? = synchronized(cacheLock) {
        if (enabled == true) cache[isu] else null
    }

    override suspend fun reviews(isu: Int): AppResult<TeacherReviews> {
        if (demo.isActive()) {
            val reviews = DemoReviews.reviews(isu, time.javaToday())
            synchronized(cacheLock) { cache[isu] = reviews }
            return AppResult.Success(reviews)
        }
        val generation = beginRequest() ?: return AppResult.Failure(AppError.CustomServicesDisabled)
        val result = call(generation) { widgetsApi.teacherReviews(isu) }
        return synchronized(cacheLock) {
            if (!isCurrent(generation)) AppResult.Failure(AppError.CustomServicesDisabled)
            else result.also { if (it is AppResult.Success) cache[isu] = it.value }
        }
    }

    override fun observeUpdates(): Flow<TeacherReviews> = updates.asSharedFlow()

    override suspend fun save(isu: Int, draft: TeacherReviewDraft): AppResult<TeacherReviews> {
        val request = draft.toRequest() ?: return invalidInput()
        return mutate { widgetsApi.saveMyTeacherReview(isu, request) }
    }

    override suspend fun delete(isu: Int): AppResult<TeacherReviews> = mutate { widgetsApi.deleteMyTeacherReview(isu) }

    override suspend fun vote(isu: Int, reviewId: String, value: Int): AppResult<TeacherReviews> {
        val id = reviewId.toWireId()
        if (id == null || value !in -1..1) return invalidInput()
        return mutate { widgetsApi.voteTeacherReview(id, ResourceVoteRequest(value)) }
    }

    override suspend fun report(
        isu: Int,
        reviewId: String,
        reason: ReviewReportReason,
        comment: String?,
    ): AppResult<TeacherReviews> {
        val id = reviewId.toWireId() ?: return invalidInput()
        val note = comment?.trim()?.takeIf(String::isNotEmpty)
        if (note != null && TeacherReviewLimits.length(note) > TeacherReviewLimits.MAX_COMMENT) return invalidInput()
        return mutate { widgetsApi.reportTeacherReview(id, ModerationReportRequest(reason.toWire(), note)) }
    }

    override suspend fun clearSessionData() {
        synchronized(cacheLock) { clearCache() }
    }

    private suspend fun beginRequest(): Long? {
        val (generation, wasEnabled) = synchronized(cacheLock) { cacheGeneration to enabled }
        val on = backend.mayCallBackend()
        return synchronized(cacheLock) {
            // An older opt-in read must neither revive cleared data nor clear a newer connection.
            if (generation != cacheGeneration || (enabled != wasEnabled && on != enabled)) {
                return@synchronized null
            }
            updateEnabled(on)
            if (on) cacheGeneration else null
        }
    }

    private fun updateEnabled(on: Boolean) {
        enabled = on
        if (!on) clearCache()
    }

    private fun clearCache() {
        cacheGeneration += 1
        cache.clear()
    }

    private fun isCurrent(generation: Long): Boolean = synchronized(cacheLock) {
        enabled == true && generation == cacheGeneration
    }

    /** The answer lands in the cache and in [updates] only while the generation it was sent in is current. */
    private suspend fun mutate(block: suspend () -> ApiResponse<TeacherReviewsResponse>): AppResult<TeacherReviews> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        val generation = beginRequest() ?: return AppResult.Failure(AppError.CustomServicesDisabled)
        val result = call(generation, block)
        return synchronized(cacheLock) {
            if (!isCurrent(generation)) return@synchronized AppResult.Failure(AppError.CustomServicesDisabled)
            if (result is AppResult.Success) {
                cache[result.value.isu] = result.value
                updates.tryEmit(result.value)
            }
            result
        }
    }

    private suspend fun call(
        generation: Long,
        block: suspend () -> ApiResponse<TeacherReviewsResponse>,
    ): AppResult<TeacherReviews> = withContext(dispatchers.io) {
        if (!isCurrent(generation)) return@withContext AppResult.Failure(AppError.CustomServicesDisabled)
        appResultOf {
            val reviews = checkNotNull(block().data) { "Backend returned no reviews" }
            reviews.toModel()
        }
    }

    private fun TeacherReviewDraft.toRequest(): SaveTeacherReviewRequest? {
        val cleanText = text.replace("\r\n", "\n").trim()
        val cleanSubject = subject?.trim()?.takeIf(String::isNotEmpty)
        val textLength = TeacherReviewLimits.length(cleanText)
        if (textLength !in TeacherReviewLimits.MIN_TEXT..TeacherReviewLimits.MAX_TEXT) return null
        if (cleanSubject != null && TeacherReviewLimits.length(cleanSubject) > TeacherReviewLimits.MAX_SUBJECT) return null
        return SaveTeacherReviewRequest(
            subjectTitle = cleanSubject,
            text = cleanText,
            anonymous = anonymous,
            flowIds = flowIds.filter { it > 0 }.sorted().take(TeacherReviewLimits.MAX_FLOWS),
        )
    }

    private fun String.toWireId() = Uuid.parseOrNull(this)?.toJavaUuid()

    private fun invalidInput(): AppResult<TeacherReviews> = AppResult.Failure(AppError.Unknown())
}
