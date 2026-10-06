package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SportActionRepositoryImpl @Inject constructor(
    private val backend: BackendGate,
    private val myItmo: MyItmoClient,
    private val sportApi: SportApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SportActionRepository {

    /** The demo session shows the queues, so their buttons are there; pressing them is refused. */
    override suspend fun areCommunityServicesEnabled(): Boolean {
        return backend.isConnected()
    }

    /**
     * MyITMO's refusal stays a `MyItmoException.Api` with its message inside [AppError.Unknown]: the push handler
     * tells a full lesson from another rule by it (`SportSignOutcome`).
     */
    override suspend fun signIn(lessonId: Long): AppResult<Unit> {
        return runAction {
            withContext(dispatchers.io) { myItmo.sport.signInLessons(listOf(lessonId)).requireResult() }
        }
    }

    /** Only the error envelope fails a withdrawal; its result list is not needed. */
    override suspend fun signOut(lessonId: Long): AppResult<Unit> {
        return runAction {
            withContext(dispatchers.io) { myItmo.sport.signOutLessons(listOf(lessonId)) }
        }
    }

    override suspend fun createFreeSignEntry(
        lessonId: Long,
        forceSign: Boolean
    ): AppResult<Unit> {
        return runBackendAction {
            sportApi.createSportFreeSignEntry(SportFreeSignRequest(lessonId = lessonId, forceSign = forceSign))
        }
    }

    override suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit> {
        return runBackendAction {
            sportApi.cancelSportFreeSignEntry(entryId)
        }
    }

    override suspend fun createAutoSignEntry(prototypeLessonId: Long): AppResult<Unit> {
        return runBackendAction {
            sportApi.createSportAutoSignEntry(SportAutoSignRequest(prototypeLessonId = prototypeLessonId))
        }
    }

    override suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit> {
        return runBackendAction {
            sportApi.cancelSportAutoSignEntry(entryId)
        }
    }

    /** The queues live on Backend: outside the demo, which refuses them itself, the opt-in must allow the call. */
    private suspend fun runBackendAction(request: suspend () -> Any): AppResult<Unit> {
        if (!demo.isActive() && !backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return runAction { withContext(dispatchers.io) { request() } }
    }

    private suspend fun runAction(action: suspend () -> Any): AppResult<Unit> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        return try {
            action()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }
}
