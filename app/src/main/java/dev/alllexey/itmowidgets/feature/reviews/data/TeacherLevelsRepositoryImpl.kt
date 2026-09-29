package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.WallClock
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One file of Backend's answers, each fresh for [TTL]. Calls are serialized, so screens asking for the same teachers
 * at once send one request.
 */
@Singleton
class TeacherLevelsRepositoryImpl @Inject constructor(
    private val customServices: CustomServicesRepository,
    private val widgetsApi: ItmoWidgetsApi,
    private val store: TeacherLevelsFileStore,
    @param:WallClock private val clock: Clock,
) : TeacherLevelsRepository, SessionDataCleaner {
    private val lock = Mutex()
    private val generation = AtomicLong()

    override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> = lock.withLock {
        withContext(Dispatchers.IO) {
            val started = generation.get()
            if (!customServices.isEnabled()) {
                store.clear()
                return@withContext emptyMap()
            }
            val wanted = isus.filter { it in BACKEND_ISU }.toSet()
            val now = clock.millis()
            val fresh = readOrClear().filterValues { now - it.fetchedAt in 0 until TTL.toMillis() }
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
                if (started != generation.get() || !customServices.isEnabled()) return@withContext emptyMap()
                store.write(entries)
            }
            wanted.mapNotNull { isu -> entries[isu]?.level?.let { isu to TeacherLevel.valueOf(it) } }.toMap()
        }
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        lock.withLock { withContext(Dispatchers.IO) { store.clear() } }
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
            val answer = checkNotNull(widgetsApi.teacherSummaryLevels(batch).data) { "Backend returned no levels" }
            answer.forEach { if (it.teacherIsu in batch) levels[it.teacherIsu] = it.level.toModel() }
        }
        return isus.associateWith { isu -> StoredLevel(levels[isu]?.name, now) }
    }

    private companion object {
        val TTL: Duration = Duration.ofDays(1)
        const val BATCH = 50

        /** Backend rejects a whole request with an ISU outside this range, and no summary exists for one. */
        val BACKEND_ISU = 100_000..9_999_999
    }
}
