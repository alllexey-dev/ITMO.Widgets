package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.feature.reviews.data.demo.DemoReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * One file of Backend's answers, each fresh for [TTL]. Calls are serialized, so screens asking for the same teachers
 * at once send one request.
 */
@Singleton
class TeacherLevelsRepositoryImpl @Inject constructor(
    private val backend: BackendGate,
    private val api: TeacherReviewsApi,
    private val store: TeacherLevelsFileStore,
    private val clock: Clock,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : TeacherLevelsRepository, SessionDataCleaner {
    private val lock = Mutex()
    private val generation = AtomicLong()

    override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = if (demo.isActive()) DemoReviews.levels(isus) else lock.withLock {
        withContext(dispatchers.io) {
            val started = generation.get()
            if (!backend.mayCallBackend()) {
                store.clear()
                return@withContext emptyMap()
            }
            val wanted = isus.filter { it in BACKEND_ISU }.toSet()
            val now = clock.now().toEpochMilliseconds()
            val fresh = readOrClear().filterValues { now - it.fetchedAt in 0 until TTL.inWholeMilliseconds }
            val missing = wanted - fresh.keys
            val fetched = try {
                fetch(missing, now)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            val entries = if (fetched == null) fresh else fresh + fetched
            if (fetched != null && missing.isNotEmpty()) {
                // A sign-out or a disabled opt-in during the request must not bring the answer back.
                if (started != generation.get() || !backend.mayCallBackend()) return@withContext emptyMap()
                store.write(entries)
            }
            wanted.mapNotNull { isu -> entries[isu]?.level?.let { isu to TeacherLevel.valueOf(it) } }.toMap()
        }
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        lock.withLock { withContext(dispatchers.io) { store.clear() } }
    }

    private fun readOrClear(): Map<Int, StoredLevel> = try {
        store.read()
    } catch (_: Exception) {
        store.clear()
        emptyMap()
    }

    private suspend fun fetch(isus: Set<Int>, now: Long): Map<Int, StoredLevel> {
        val levels = mutableMapOf<Int, TeacherLevel>()
        isus.sorted().chunked(BATCH).forEach { batch ->
            api.teacherSummaryLevels(batch).forEach { answer ->
                val level = answer.level.toModel()
                if (answer.teacherIsu in batch && level != null) levels[answer.teacherIsu] = level
            }
        }
        return isus.associateWith { isu -> StoredLevel(levels[isu]?.name, now) }
    }

    private companion object {
        val TTL: Duration = 1.days
        const val BATCH = 50

        /** Backend rejects a whole request with an ISU outside this range, and no summary exists for one. */
        val BACKEND_ISU = 100_000..9_999_999
    }
}
