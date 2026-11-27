package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookStoreJson
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsScoresRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable private data class StoredLms(val owner: Int, val entries: List<StoredLmsEntry>)
@Serializable private data class StoredLmsEntry(
    val programId: Long, val semester: Int, val studyYear: String, val entryId: Long,
    val disciplineId: Long?, val controlType: String?, val scores: List<StoredLmsScore>,
)
@Serializable private data class StoredLmsScore(val name: String, val earned: Double?, val max: Double, val status: String?)

/** One encrypted, account-bound store (`noBackupFilesDir/lms_scores`); the sign-out cleaner owns the same lock as reads and writes. */
class LmsScoresRepositoryImpl(
    private val store: SecureStore,
    private val currentUser: CurrentUserProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : LmsScoresRepository, SessionDataCleaner {
    private val lock = Mutex()
    private val revision = MutableStateFlow(0L)
    override fun observeChanges() = revision.map { Unit }

    override suspend fun load(args: RecordbookSubjectArgs): List<LmsScore> = lock.withLock {
        if (demo.isActive()) return@withLock emptyList()
        val owner = currentUser.getCurrentUser()?.isu ?: return@withLock emptyList()
        val entries = read(owner)?.entries.orEmpty()
        val saved = entries.firstOrNull { it.exactMatches(args) }
            ?: entries.singleOrNull { it.compatibleWith(args) }
            ?: return@withLock emptyList()
        if (currentUser.getCurrentUser()?.isu != owner || demo.isActive()) return@withLock emptyList()
        saved.scores.map { LmsScore(it.name, it.earned, it.max, it.status) }
    }

    override suspend fun save(args: RecordbookSubjectArgs, scores: List<LmsScore>) = lock.withLock {
        if (demo.isActive()) return@withLock
        val owner = currentUser.getCurrentUser()?.isu ?: return@withLock
        val controlType = args.controlType?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedArgs = args.copy(controlType = controlType)
        val old = read(owner)?.entries.orEmpty().filterNot { it.replaces(normalizedArgs) }
        val updated = if (scores.isEmpty()) old else old + StoredLmsEntry(
            normalizedArgs.programId,
            normalizedArgs.semester,
            normalizedArgs.studyYear,
            normalizedArgs.entryId,
            normalizedArgs.disciplineId,
            controlType,
            scores.map { StoredLmsScore(it.name, it.earned, it.max, it.verificationStatus) },
        )
        if (currentUser.getCurrentUser()?.isu != owner || demo.isActive()) return@withLock
        withContext(dispatchers.io) {
            if (updated.isEmpty()) store.delete(NAME)
            else store.write(NAME, RecordbookStoreJson.encodeToString(StoredLms(owner, updated)))
        }
        revision.value += 1
    }

    override suspend fun clearSessionData() = lock.withLock {
        withContext(dispatchers.io) { store.delete(NAME) }
        revision.value += 1
    }

    private suspend fun read(owner: Int): StoredLms? = withContext(dispatchers.io) {
        runCatching { store.read(NAME)?.let { RecordbookStoreJson.decodeFromString<StoredLms>(it) } }
            .getOrNull()?.takeIf { it.owner == owner }
    }

    private fun StoredLmsEntry.samePeriodAndEntry(args: RecordbookSubjectArgs): Boolean =
        programId == args.programId && semester == args.semester && studyYear == args.studyYear && entryId == args.entryId

    private fun StoredLmsEntry.normalizedControlType(): String? = controlType?.trim()?.takeIf { it.isNotEmpty() }

    private fun StoredLmsEntry.exactMatches(args: RecordbookSubjectArgs): Boolean =
        samePeriodAndEntry(args) &&
            disciplineId == args.disciplineId &&
            normalizedControlType() == args.controlType?.trim()?.takeIf { it.isNotEmpty() }

    private fun StoredLmsEntry.compatibleWith(args: RecordbookSubjectArgs): Boolean {
        if (!samePeriodAndEntry(args)) return false
        val argsDiscipline = args.disciplineId
        val argsControl = args.controlType?.trim()?.takeIf { it.isNotEmpty() }
        val entryControl = normalizedControlType()
        return (disciplineId == null || argsDiscipline == null || disciplineId == argsDiscipline) &&
            (entryControl == null || argsControl == null || entryControl == argsControl)
    }

    private fun StoredLmsEntry.replaces(args: RecordbookSubjectArgs): Boolean {
        if (exactMatches(args)) return true
        // When saving full subject coordinates, clean up any un-disambiguated legacy entry for the same period/entry.
        return samePeriodAndEntry(args) &&
            (args.disciplineId != null || args.controlType != null) &&
            disciplineId == null &&
            normalizedControlType() == null
    }

    private companion object { const val NAME = "lms_scores" }
}
