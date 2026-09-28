package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.ApplicationScope
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class TeacherReviewsRepositoryImpl @Inject constructor(
    private val customServices: CustomServicesRepository,
    private val widgetsApi: ItmoWidgetsApi,
    @param:ApplicationScope private val scope: CoroutineScope,
) : TeacherReviewsRepository, SessionDataCleaner {
    private val cache = ConcurrentHashMap<Int, TeacherReviews>()
    private val cacheLock = Any()
    private var cacheGeneration = 0L
    @Volatile private var enabled: Boolean? = null

    init {
        scope.launch {
            customServices.observeEnabled().collect { on ->
                synchronized(cacheLock) { updateEnabled(on) }
            }
        }
    }

    override fun cachedReviews(isu: Int): TeacherReviews? = synchronized(cacheLock) {
        if (enabled == true) cache[isu] else null
    }

    override suspend fun reviews(isu: Int): AppResult<TeacherReviews> {
        val generation = beginRequest() ?: return AppResult.Failure(AppError.CustomServicesDisabled)
        val result = loadReviews(isu, generation)
        return synchronized(cacheLock) {
            if (!isCurrent(generation)) AppResult.Failure(AppError.CustomServicesDisabled)
            else result.also { if (it is AppResult.Success) cache[isu] = it.value }
        }
    }

    override suspend fun clearSessionData() {
        synchronized(cacheLock) { clearCache() }
    }

    private suspend fun beginRequest(): Long? {
        val (generation, wasEnabled) = synchronized(cacheLock) { cacheGeneration to enabled }
        val on = customServices.isEnabled()
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

    private suspend fun loadReviews(isu: Int, generation: Long): AppResult<TeacherReviews> = try {
        withContext(Dispatchers.IO) {
            if (!isCurrent(generation)) return@withContext AppResult.Failure(AppError.CustomServicesDisabled)
            val reviews = checkNotNull(widgetsApi.teacherReviews(isu).data) { "Backend returned no reviews" }
            AppResult.Success(reviews.toModel())
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(error.toAppError())
    }
}
