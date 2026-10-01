package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.OwnIdentity
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetChange
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetReading
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRows
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoreRules
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * The own totals from public Google Sheets, kept on the device only. The state has one lock and the network runs
 * outside it; a cleared session (generation) or a replaced connection drops a reading that was already on its way.
 */
@Singleton
class SheetScoresRepositoryImpl @Inject constructor(
    private val client: PublicSheetClient,
    private val store: SheetScoresFileStore,
    private val currentUser: CurrentUserProvider,
    @param:WallClock private val clock: Clock,
) : SheetScoresRepository, SessionDataCleaner {

    private val lock = Mutex()
    private val generation = AtomicLong()
    /** The file's connections, read once; null until the first reader. */
    private val state = MutableStateFlow<List<SheetScore>?>(null)

    override fun observe(): Flow<List<SheetScore>> = flow {
        lock.withLock { loaded() }
        emitAll(state.filterNotNull())
    }

    override suspend fun refresh(scope: ResourceScope) {
        val started = generation.get()
        val score = lock.withLock { loaded().firstOrNull { it.scope.key == scope.key } } ?: return
        val (reading, _) = read(score)
        store(started, score, reading, notify = false)
    }

    override suspend fun inspect(url: String): SheetInspection {
        val sheet = GoogleSheetUrl.parse(url) ?: return SheetInspection.Failed(SheetStatus.CLOSED)
        val listed = when (val fetch = client.tabs(sheet.spreadsheetId)) {
            is SheetFetch.Loaded -> fetch.value
            SheetFetch.Closed, SheetFetch.Missing -> return SheetInspection.Failed(SheetStatus.CLOSED)
            SheetFetch.TooLarge -> return SheetInspection.Failed(SheetStatus.TOO_LARGE)
            is SheetFetch.Failed -> return SheetInspection.Failed(SheetStatus.NETWORK)
        }
        val tabs = listed.ifEmpty { listOf(SheetTab(sheet.gid ?: 0, "")) }
            .sortedBy { it.gid != sheet.gid }
            .take(MAX_TABS)
        val permits = Semaphore(PARALLEL_TABS)
        val fetched = coroutineScope {
            tabs.map { tab -> async { tab to permits.withPermit { client.grid(sheet.spreadsheetId, tab.gid) } } }.awaitAll()
        }
        if (fetched.any { it.second == SheetFetch.Closed }) return SheetInspection.Failed(SheetStatus.CLOSED)
        if (fetched.any { it.second is SheetFetch.Failed }) return SheetInspection.Failed(SheetStatus.NETWORK)
        val grids = fetched.mapNotNull { (tab, fetch) -> (fetch as? SheetFetch.Loaded)?.let { SheetTabGrid(tab, it.value) } }
        if (grids.isEmpty()) return SheetInspection.Failed(SheetStatus.TOO_LARGE)
        val workbook = SheetWorkbook(sheet, grids)
        val user = currentUser.getCurrentUser()
        return SheetInspection.Ready(workbook, SheetRows.find(workbook, OwnIdentity(user?.isu, user?.name)))
    }

    override suspend fun connect(scope: ResourceScope, url: String, row: SheetRowMatch, total: SheetCell): AppResult<Unit> {
        require(row.tab == total.tab)
        return lock.withLock {
            val now = clock.instant()
            val score = connection(scope, url, row, total, connectedAt = now)
            write(loaded().filterNot { it.scope.key == scope.key } + score)
        }
    }

    override suspend fun changeTotal(scope: ResourceScope, row: SheetRowMatch, total: SheetCell): AppResult<Unit> {
        require(row.tab == total.tab)
        return lock.withLock {
            val current = loaded()
            val previous = current.firstOrNull { it.scope.key == scope.key }
                ?: return@withLock AppResult.Failure(AppError.NotFound)
            val score = connection(previous.scope, previous.url, row, total, connectedAt = previous.connectedAt)
            write(current.map { if (it.scope.key == scope.key) score else it })
        }
    }

    override suspend fun disconnect(scope: ResourceScope) {
        lock.withLock { persistOrKeep(loaded().filterNot { it.scope.key == scope.key }) }
    }

    override suspend fun check(half: StudyHalf): SheetCheck {
        val started = generation.get()
        val scores = lock.withLock { loaded().filter { it.scope.periodKey == half.periodKey } }
        val changes = mutableListOf<SheetChange>()
        val errors = mutableListOf<AppError>()
        for (score in scores) {
            val (reading, error) = read(score)
            if (error != null) errors += error
            val change = store(started, score, reading, notify = true) ?: continue
            changes += change
        }
        return SheetCheck(changes, errors)
    }

    override suspend fun untrack() {
        lock.withLock { persistOrKeep(loaded().map { it.copy(tracked = false) }) }
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        lock.withLock {
            withContext(Dispatchers.IO) { store.clear() }
            state.value = emptyList()
        }
    }

    private fun connection(scope: ResourceScope, url: String, row: SheetRowMatch, total: SheetCell, connectedAt: Instant): SheetScore {
        val value = total.value.trim().ifEmpty { null }
        return SheetScore(
            scope = scope,
            url = url,
            tabGid = row.tab.gid,
            tabName = row.tab.name,
            rowKey = row.key,
            keyColumn = row.keyColumn,
            keyKind = row.kind,
            column = SheetColumnRef(total.headerPath, total.column),
            value = value,
            baseline = value,
            tracked = true,
            status = SheetStatus.OK,
            updatedAt = clock.instant(),
            connectedAt = connectedAt,
        )
    }

    /** One download of the connected tab; the error is set only for a failed download. */
    private suspend fun read(score: SheetScore): Pair<SheetReading, AppError?> {
        val sheet = GoogleSheetUrl.parse(score.url) ?: return SheetReading(null, SheetStatus.CLOSED) to null
        return when (val fetch = client.grid(sheet.spreadsheetId, score.tabGid)) {
            is SheetFetch.Loaded -> SheetScoreRules.read(fetch.value, score) to null
            SheetFetch.Closed -> SheetReading(null, SheetStatus.CLOSED) to null
            SheetFetch.Missing -> SheetReading(null, SheetStatus.COLUMN_NOT_FOUND) to null
            SheetFetch.TooLarge -> SheetReading(null, SheetStatus.TOO_LARGE) to null
            is SheetFetch.Failed -> SheetReading(null, SheetStatus.NETWORK) to fetch.error
        }
    }

    /**
     * Writes [reading] of [read] when the session and the connection are still those it was taken for; returns the
     * change it makes, if any.
     */
    private suspend fun store(started: Long, read: SheetScore, reading: SheetReading, notify: Boolean): SheetChange? =
        lock.withLock {
            if (generation.get() != started) return@withLock null
            val current = loaded()
            val score = current.firstOrNull { it.scope.key == read.scope.key }
            if (score == null || !score.sameTarget(read)) return@withLock null
            val update = SheetScoreRules.apply(score, reading, clock.instant(), notify)
            persistOrKeep(current.map { if (it === score) update.score else it })
            update.change?.let { SheetChange(score.scope, it) }
        }

    private fun SheetScore.sameTarget(other: SheetScore): Boolean =
        connectedAt == other.connectedAt && url == other.url && tabGid == other.tabGid && rowKey == other.rowKey &&
            keyKind == other.keyKind && column == other.column

    /** Writes [next] and publishes it; a failed write leaves the state as it was. Must hold [lock]. */
    private suspend fun write(next: List<SheetScore>): AppResult<Unit> = try {
        persist(next)
        AppResult.Success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(AppError.Unknown())
    }

    /** Must hold [lock]. */
    private suspend fun persistOrKeep(next: List<SheetScore>) {
        try {
            persist(next)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The file is behind until the next write; this process keeps the state in memory.
            state.value = next
        }
    }

    /** Must hold [lock]. */
    private suspend fun persist(next: List<SheetScore>) {
        val owner = currentUser.getCurrentUser()?.isu
        withContext(Dispatchers.IO) { store.write(StoredSheetScores(owner = owner, connections = next.map { it.toStored() })) }
        state.value = next
    }

    /** A corrupt file or another account's file is removed and the state starts empty. Must hold [lock]. */
    private suspend fun loaded(): List<SheetScore> = state.value ?: run {
        val owner = currentUser.getCurrentUser()?.isu
        withContext(Dispatchers.IO) {
            val stored = try {
                store.read()
            } catch (_: Exception) {
                store.clear()
                null
            }
            when {
                stored == null -> emptyList()
                stored.owner != null && stored.owner != owner -> {
                    store.clear()
                    emptyList()
                }
                else -> stored.connections.map { it.toModel() }
            }
        }
    }.also { state.value = it }

    private companion object {
        const val MAX_TABS = 50
        const val PARALLEL_TABS = 4
    }
}
