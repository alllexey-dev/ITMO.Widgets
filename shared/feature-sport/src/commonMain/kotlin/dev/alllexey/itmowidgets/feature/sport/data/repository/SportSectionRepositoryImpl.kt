package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.sport.model.SportSectionOccurrenceRequest
import dev.alllexey.itmowidgets.client.sport.model.SportSectionRequest
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.sport.domain.model.MySportSection
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionSlot
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * Sections on Backend behind the custom-services opt-in. The demo checks first: it shows [DemoSport.sections] and
 * refuses changes, so demo data never reaches Backend. A mutation updates the list in place.
 */
class SportSectionRepositoryImpl(
    private val sportApi: SportApi,
    private val backend: BackendGate,
    private val demo: DemoMode,
    private val time: AcademicTimeProvider,
    private val dispatchers: AppDispatchers,
) : SportSectionRepository, SessionDataCleaner {

    private val sessionMutex = Mutex()
    private var sessionGeneration = 0L
    private val sections = MutableStateFlow<LoadState<List<MySportSection>>>(LoadState.Loading)

    override fun observeSections(): Flow<LoadState<List<MySportSection>>> = sections.asStateFlow()

    override suspend fun refreshSections() {
        val generation = sessionMutex.withLock { sessionGeneration }
        val state = when {
            demo.isActive() -> LoadState.Content(DemoSport.sections(time))
            !backend.mayCallBackend() -> LoadState.Disabled
            else -> when (val result = call { sportApi.mySportSections().map { it.toModel() } }) {
                is AppResult.Success -> LoadState.Content(result.value)
                is AppResult.Failure -> LoadState.Error(result.error)
            }
        }
        sessionMutex.withLock { if (generation == sessionGeneration) sections.value = state }
    }

    override suspend fun slots(lessonIds: List<Long>): AppResult<List<SportSectionSlot>> = when {
        lessonIds.isEmpty() -> AppResult.Success(emptyList())
        demo.isActive() -> AppResult.Success(DemoSport.sectionSlots(lessonIds, time))
        !backend.mayCallBackend() -> AppResult.Failure(AppError.CustomServicesDisabled)
        else -> call { sportApi.sportSectionSlots(lessonIds.take(MAX_SLOTS)).map { it.toModel() } }
    }

    override suspend fun create(lessonId: Long): AppResult<MySportSection> =
        mutate { sportApi.createSportSection(SportSectionRequest(lessonId)).toModel() }.also { result ->
            if (result is AppResult.Success) replace(result.value)
        }

    override suspend fun setSkipped(sectionId: Long, start: Instant, skipped: Boolean): AppResult<MySportSection> =
        mutate { sportApi.updateSportSectionOccurrence(sectionId, SportSectionOccurrenceRequest(start, skipped)).toModel() }
            .also { result -> if (result is AppResult.Success) replace(result.value) }

    override suspend fun stop(sectionId: Long): AppResult<Unit> =
        mutate { sportApi.stopSportSection(sectionId) }.also { result ->
            if (result is AppResult.Success) edit { list -> list.filter { it.id != sectionId } }
        }

    /** Another account never sees this one's sections: `Loading` until the next session loads its own. */
    override suspend fun clearSessionData() = sessionMutex.withLock {
        sessionGeneration++
        sections.value = LoadState.Loading
    }

    private fun replace(section: MySportSection) = edit { list -> list.filter { it.id != section.id } + section }

    private fun edit(change: (List<MySportSection>) -> List<MySportSection>) {
        val current = sections.value.valueOrNull() ?: return
        sections.value = LoadState.Content(change(current).sortedBy { it.dates.firstOrNull()?.start })
    }

    private suspend fun <T> mutate(block: suspend () -> T): AppResult<T> = when {
        demo.isActive() -> AppResult.Failure(AppError.DemoUnavailable)
        !backend.mayCallBackend() -> AppResult.Failure(AppError.CustomServicesDisabled)
        else -> call(block)
    }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(withContext(dispatchers.io) { block() })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(error.toSportAppError())
    }

    private companion object {
        /** Backend answers at most this many lessons per call. */
        const val MAX_SLOTS = 50
    }
}
