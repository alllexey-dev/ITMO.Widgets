package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.reviews.SaveTeacherReviewRequest
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsResponse
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewLimits
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.reviews.data.demo.DemoReviews
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Every change of the cache happens under [lock] and replaces the whole [cache] snapshot, so [cachedReviews], which
 * is not suspending (a ViewModel's restored state reads it), sees one consistent state without the lock. [scope]
 * outlives the screens: it follows the services opt-in for the process's lifetime.
 */
@OptIn(ExperimentalAtomicApi::class)
class TeacherReviewsRepositoryImpl(
    private val backend: BackendGate,
    private val api: TeacherReviewsApi,
    private val scope: CoroutineScope,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : TeacherReviewsRepository, SessionDataCleaner {
    private val cache = AtomicReference(CacheState())
    private val updates = MutableSharedFlow<TeacherReviews>(extraBufferCapacity = 8)
    private val lock = Mutex()

    init {
        scope.launch {
            backend.observeConnected().collect { on ->
                lock.withLock { updateEnabled(on) }
            }
        }
    }

    override fun cachedReviews(isu: Int): TeacherReviews? = cache.load().let { state ->
        if (state.enabled == true) state.entries[isu] else null
    }

    override suspend fun reviews(isu: Int): AppResult<TeacherReviews> {
        if (demo.isActive()) {
            val reviews = DemoReviews.reviews(isu, time.today())
            lock.withLock { put(reviews, isu) }
            return AppResult.Success(reviews)
        }
        val generation = beginRequest() ?: return AppResult.Failure(AppError.CustomServicesDisabled)
        val result = call(generation) { api.teacherReviews(isu) }
        return lock.withLock {
            if (!isCurrent(generation)) AppResult.Failure(AppError.CustomServicesDisabled)
            else result.also { if (it is AppResult.Success) put(it.value, isu) }
        }
    }

    override fun observeUpdates(): Flow<TeacherReviews> = updates.asSharedFlow()

    override suspend fun save(isu: Int, draft: TeacherReviewDraft): AppResult<TeacherReviews> {
        val request = draft.toRequest() ?: return invalidInput()
        return mutate { api.saveMyTeacherReview(isu, request) }
    }

    override suspend fun delete(isu: Int): AppResult<TeacherReviews> = mutate { api.deleteMyTeacherReview(isu) }

    override suspend fun vote(isu: Int, reviewId: String, value: Int): AppResult<TeacherReviews> {
        val id = reviewId.toWireId()
        if (id == null || value !in -1..1) return invalidInput()
        return mutate { api.voteTeacherReview(id, ResourceVoteRequest(value)) }
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
        return mutate { api.reportTeacherReview(id, ModerationReportRequest(reason.toWire(), note)) }
    }

    override suspend fun clearSessionData() {
        lock.withLock { clearCache() }
    }

    private suspend fun beginRequest(): Long? {
        val started = cache.load()
        val on = backend.mayCallBackend()
        return lock.withLock {
            val current = cache.load()
            // An older opt-in read must neither revive cleared data nor clear a newer connection.
            val cleared = started.generation != current.generation
            if (cleared || (current.enabled != started.enabled && on != current.enabled)) return@withLock null
            updateEnabled(on)
            if (on) cache.load().generation else null
        }
    }

    /** Under [lock]. */
    private fun updateEnabled(on: Boolean) {
        cache.store(cache.load().copy(enabled = on))
        if (!on) clearCache()
    }

    /** Under [lock]. */
    private fun clearCache() {
        val current = cache.load()
        cache.store(current.copy(generation = current.generation + 1, entries = emptyMap()))
    }

    /** Under [lock]; [isu] is the teacher the answer belongs to. */
    private fun put(reviews: TeacherReviews, isu: Int = reviews.isu) {
        val current = cache.load()
        cache.store(current.copy(entries = current.entries + (isu to reviews)))
    }

    /** Reads the snapshot: the lock is not reentrant and the callers may hold it. */
    private fun isCurrent(generation: Long): Boolean = cache.load().let { state ->
        state.enabled == true && generation == state.generation
    }

    /** The answer lands in the cache and in [updates] only while the generation it was sent in is current. */
    private suspend fun mutate(block: suspend () -> TeacherReviewsResponse): AppResult<TeacherReviews> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        val generation = beginRequest() ?: return AppResult.Failure(AppError.CustomServicesDisabled)
        val result = call(generation, block)
        return lock.withLock {
            if (!isCurrent(generation)) return@withLock AppResult.Failure(AppError.CustomServicesDisabled)
            if (result is AppResult.Success) {
                put(result.value)
                updates.tryEmit(result.value)
            }
            result
        }
    }

    private suspend fun call(
        generation: Long,
        block: suspend () -> TeacherReviewsResponse,
    ): AppResult<TeacherReviews> = withContext(dispatchers.io) {
        if (!isCurrent(generation)) return@withContext AppResult.Failure(AppError.CustomServicesDisabled)
        appResultOf(Throwable::toAppError) { block().toModel() }
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

    private fun String.toWireId() = Uuid.parseOrNull(this)

    private fun invalidInput(): AppResult<TeacherReviews> = AppResult.Failure(AppError.Unknown())

    /** [enabled] is null until the first opt-in read; [generation] grows on every clear. */
    private data class CacheState(
        val enabled: Boolean? = null,
        val generation: Long = 0,
        val entries: Map<Int, TeacherReviews> = emptyMap(),
    )
}
