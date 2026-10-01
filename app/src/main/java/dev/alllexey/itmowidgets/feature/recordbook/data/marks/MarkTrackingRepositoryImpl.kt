package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkRead
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsMarkSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.Compared
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkCheckResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDiff
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEvent
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNewsRules
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MyItmoMarkSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MyItmoSubjectMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.ReadStamp
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.SheetsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.studyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.toMyItmoMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Compares the own marks of the current half-year in My ITMO and BARS with the last snapshots on the device and turns
 * changed totals of the connected sheets into unread subjects. Nothing leaves the device.
 *
 * Checks run one at a time; the state has its own lock, so reading marks and advancing from a list never wait for the
 * network. A cleared session (generation), a reset source (epoch) or a newer write of the source ([StoredMarks]'s
 * `fetchedAt`) make an answer that was already on its way drop instead of being written.
 */
@Singleton
class MarkTrackingRepositoryImpl @Inject constructor(
    private val recordbook: RecordbookRepository,
    private val bars: BarsMarkSource,
    private val store: MarksFileStore,
    private val time: AcademicTimeProvider,
    @param:WallClock private val clock: Clock,
    private val notifier: AppNotifier,
    private val currentUser: CurrentUserProvider,
    private val sheets: SheetScoresRepository,
) : MarkTrackingRepository, SessionDataCleaner {

    private val checks = Mutex()
    private val lock = Mutex()
    private val generation = AtomicLong()
    private val epochs = MarkSource.entries.associateWith { AtomicLong() }
    /** The file's state, read once; null until the first reader. */
    private val state = MutableStateFlow<StoredMarks?>(null)

    override fun observeNews(): Flow<List<MarkNews>> = flow {
        lock.withLock { loaded() }
        emitAll(state.filterNotNull().map { stored -> MarkNewsRules.pruned(stored.news.map { it.toModel() }, clock.instant()) })
    }

    override suspend fun checkMyItmo(): AppResult<MarkCheckResult> = checks.withLock {
        val started = generation.get()
        val epoch = epochs.getValue(MarkSource.MY_ITMO).get()
        val fetchedAt = clock.millis()
        val half = StudyHalf.of(time.today())
        val current = when (val answer = requestMyItmo(half)) {
            is AppResult.Success -> answer.value
            is AppResult.Failure -> return@withLock answer
        }
        lock.withLock {
            if (generation.get() != started) return@withLock AppResult.Failure(AppError.Unauthorized)
            val stored = loaded()
            if (epochs.getValue(MarkSource.MY_ITMO).get() != epoch || stored.myItmo.isNewerThan(fetchedAt)) {
                return@withLock AppResult.Success(MarkCheckResult.Stale)
            }
            val compared = MarkDiff.myItmo(stored.myItmo?.toModel(), current)
            val events = MarkNewsRules.withoutEchoes(compared.events, compared.snapshot, stored.bars?.toModel())
            val next = stored.copy(
                owner = currentUser.getCurrentUser()?.isu,
                myItmo = compared.snapshot.toStored(fetchedAt),
                news = merged(stored, events, notify = true)
            )
            try {
                persist(next)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return@withLock AppResult.Failure(AppError.Unknown(error))
            }
            AppResult.Success(result(compared, events))
        }
    }

    override suspend fun checkBars(): BarsCheck = checks.withLock {
        val started = generation.get()
        val epoch = epochs.getValue(MarkSource.BARS).get()
        val fetchedAt = clock.millis()
        val half = StudyHalf.of(time.today())
        val plans = when (val read = bars.read(half)) {
            is BarsMarkRead.Journals -> read.plans
            BarsMarkRead.NoSession -> return@withLock BarsCheck.NoSession
            BarsMarkRead.SessionEnded -> return@withLock BarsCheck.SessionEnded
            is BarsMarkRead.Failure -> return@withLock BarsCheck.Failed(read.error)
        }
        lock.withLock {
            if (generation.get() != started) return@withLock BarsCheck.Failed(AppError.Unauthorized)
            val stored = loaded()
            if (epochs.getValue(MarkSource.BARS).get() != epoch || stored.bars.isNewerThan(fetchedAt)) {
                return@withLock BarsCheck.Done(MarkCheckResult.Stale)
            }
            val compared = MarkDiff.bars(stored.bars?.toModel(), BarsMarkSnapshot(half, plans))
            val next = stored.copy(
                owner = currentUser.getCurrentUser()?.isu,
                bars = compared.snapshot.toStored(fetchedAt),
                news = merged(stored, compared.events, notify = true)
            )
            try {
                persist(next)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return@withLock BarsCheck.Failed(AppError.Unknown(error))
            }
            BarsCheck.Done(result(compared, compared.events))
        }
    }

    override suspend fun checkSheets(): SheetsCheck = checks.withLock {
        val started = generation.get()
        val epoch = epochs.getValue(MarkSource.SHEETS).get()
        val half = StudyHalf.of(time.today())
        val check = sheets.check(half)
        lock.withLock {
            if (generation.get() != started) return@withLock SheetsCheck(MarkCheckResult.Stale, listOf(AppError.Unauthorized))
            if (epochs.getValue(MarkSource.SHEETS).get() != epoch) return@withLock SheetsCheck(MarkCheckResult.Stale, check.errors)
            val events = check.changes.map { MarkEvent(MarkSource.SHEETS, half, it.scope.subjectName, it.kind) }
            val found = MarkCheckResult.Compared(events.mapTo(mutableSetOf()) { it.half to it.nameKey }.size)
            if (events.isEmpty()) return@withLock SheetsCheck(found, check.errors)
            val stored = loaded()
            val next = stored.copy(owner = currentUser.getCurrentUser()?.isu, news = merged(stored, events, notify = true))
            try {
                persist(next)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                return@withLock SheetsCheck(found, check.errors + AppError.Unknown(error))
            }
            SheetsCheck(found, check.errors)
        }
    }

    override fun readStarted(): ReadStamp = ReadStamp(clock.millis())

    override suspend fun recordMyItmoSeen(
        stamp: ReadStamp,
        half: StudyHalf,
        programId: Long,
        semester: Int,
        subjects: List<RecordbookSubject>
    ) {
        advance { stored ->
            val previous = stored.myItmo?.takeIf { it.half == half.key && !it.isNewerThan(stamp.millis) } ?: return@advance null
            val current = MyItmoMarkSnapshot(half, subjects.map { it.toMyItmoMark(programId, semester) })
            // Other programs and semesters are not in this answer; the comparison carries them unchanged.
            val compared = MarkDiff.myItmo(previous.toModel(), current)
            val events = MarkNewsRules.withoutEchoes(compared.events, compared.snapshot, stored.bars?.toModel())
            stored.copy(myItmo = compared.snapshot.toStored(stamp.millis), news = merged(stored, events, notify = false))
        }
    }

    override suspend fun recordBarsSeen(stamp: ReadStamp, half: StudyHalf, plans: List<BarsPlanMarks>) {
        advance { stored ->
            val previous = stored.bars?.takeIf { it.half == half.key && !it.isNewerThan(stamp.millis) } ?: return@advance null
            val compared = MarkDiff.bars(previous.toModel(), BarsMarkSnapshot(half, plans))
            stored.copy(bars = compared.snapshot.toStored(stamp.millis), news = merged(stored, compared.events, notify = false))
        }
    }

    override suspend fun target(news: MarkNews, withBars: Boolean): MarkSubjectTarget? = lock.withLock {
        val stored = loaded()
        MarkNewsRules.target(news, stored.myItmo?.toModel(), stored.bars?.toModel(), withBars)
    }

    override suspend fun markNotified(ids: Set<String>) {
        if (ids.isEmpty()) return
        update { stored -> stored.copy(news = stored.news.map { if (it.id in ids) it.copy(notified = true) else it }) }
    }

    override suspend fun markRead(half: StudyHalf, nameKey: String) {
        val id = MarkNews.idOf(half, nameKey)
        var readLast = false
        update { stored ->
            val remaining = stored.news.filterNot { it.id == id }
            readLast = remaining.size < stored.news.size && remaining.isEmpty()
            stored.copy(news = remaining)
        }
        if (readLast) notifier.cancel(AppNotificationChannels.MARKS, MarkDigests.DIGEST_ID)
    }

    override suspend fun markAllRead() {
        update { stored -> stored.copy(news = emptyList()) }
        notifier.cancel(AppNotificationChannels.MARKS, MarkDigests.DIGEST_ID)
    }

    override suspend fun resetSource(source: MarkSource) {
        epochs.getValue(source).incrementAndGet()
        when (source) {
            MarkSource.MY_ITMO -> update { stored -> stored.copy(myItmo = null) }
            MarkSource.BARS -> update { stored -> stored.copy(bars = null) }
            // The totals are what the subject pages show; only the next background read of each becomes a baseline.
            MarkSource.SHEETS -> sheets.untrack()
        }
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        lock.withLock {
            withContext(Dispatchers.IO) { store.clear() }
            state.value = StoredMarks()
        }
    }

    /** Every program's periods of [half]; the first failure fails the whole answer. */
    private suspend fun requestMyItmo(half: StudyHalf): AppResult<MyItmoMarkSnapshot> {
        val programs = when (val answer = recordbook.getPrograms()) {
            is AppResult.Success -> answer.value
            is AppResult.Failure -> return answer
        }
        val subjects = mutableListOf<MyItmoSubjectMark>()
        programs.forEach { program ->
            program.periods.filter { it.studyHalf() == half }.forEach { period ->
                when (val answer = recordbook.getSubjects(program.id, period.semester)) {
                    is AppResult.Success -> subjects += answer.value.map { it.toMyItmoMark(program.id, period.semester) }
                    is AppResult.Failure -> return answer
                }
            }
        }
        return AppResult.Success(MyItmoMarkSnapshot(half, subjects))
    }

    private fun merged(stored: StoredMarks, events: List<MarkEvent>, notify: Boolean): List<StoredMarkNews> =
        MarkNewsRules.merge(stored.news.map { it.toModel() }, events, clock.instant(), notify).map { it.toStored() }

    private fun result(compared: Compared<*>, events: List<MarkEvent>): MarkCheckResult =
        if (compared.baseline) MarkCheckResult.Baseline
        else MarkCheckResult.Compared(events.mapTo(mutableSetOf()) { it.half to it.nameKey }.size)

    /** Writes what [transform] makes of the state; null leaves it as it is. A failed write keeps it in memory. */
    private suspend fun advance(transform: suspend (StoredMarks) -> StoredMarks?) {
        lock.withLock {
            val next = transform(loaded()) ?: return@withLock
            persistOrKeep(next)
        }
    }

    private suspend fun update(transform: (StoredMarks) -> StoredMarks) {
        lock.withLock { persistOrKeep(transform(loaded())) }
    }

    /** Must hold [lock]. */
    private suspend fun persistOrKeep(next: StoredMarks) {
        try {
            persist(next)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The file is behind until the next write; this process keeps the state in memory.
            state.value = pruned(next)
        }
    }

    /** Writes [next] without expired and surplus unread subjects, then publishes it. Must hold [lock]. */
    private suspend fun persist(next: StoredMarks) {
        val kept = pruned(next)
        withContext(Dispatchers.IO) { store.write(kept) }
        state.value = kept
    }

    private fun pruned(next: StoredMarks): StoredMarks =
        next.copy(news = MarkNewsRules.pruned(next.news.map { it.toModel() }, clock.instant()).map { it.toStored() })

    /** A corrupt file or another account's file is removed and the state starts empty. Must hold [lock]. */
    private suspend fun loaded(): StoredMarks = state.value ?: run {
        val owner = currentUser.getCurrentUser()?.isu
        withContext(Dispatchers.IO) {
            val stored = try {
                store.read()
            } catch (_: Exception) {
                store.clear()
                null
            }
            if (stored?.owner != null && stored.owner != owner) {
                store.clear()
                StoredMarks()
            } else {
                stored ?: StoredMarks()
            }
        }
    }.also { state.value = it }

    private fun StoredMyItmoSnapshot?.isNewerThan(millis: Long): Boolean = this != null && fetchedAt > millis

    private fun StoredBarsSnapshot?.isNewerThan(millis: Long): Boolean = this != null && fetchedAt > millis
}
