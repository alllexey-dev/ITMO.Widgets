package dev.alllexey.itmowidgets.feature.sport.data.repository

import api.myitmo.MyItmoApi
import api.myitmo.utils.ApiException
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.core.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

class SportActionRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val myItmoApi: MyItmoApi,
    private val widgetsApi: ItmoWidgetsApi
) : SportActionRepository {

    override suspend fun areCommunityServicesEnabled(): Boolean {
        return settings.getCustomServicesEnabled()
    }

    override suspend fun signIn(lessonId: Long): AppResult<Unit> {
        return runAction {
            withContext(Dispatchers.IO) {
                val response = myItmoApi.signInLessons(listOf(lessonId)).execute()
                if (!response.isSuccessful) throw HttpException(response)
                val body = response.body()
                    ?: throw IllegalStateException("Empty sport sign-in response")
                if (body.errorCode != 0) {
                    throw ApiException(body.errorCode, body.errorMessage)
                }
                check(body.result != null) { "Missing sport action result" }
            }
        }
    }

    override suspend fun signOut(lessonId: Long): AppResult<Unit> {
        return runAction {
            withContext(Dispatchers.IO) {
                val response = myItmoApi.signOutLessons(listOf(lessonId)).execute()
                if (!response.isSuccessful) throw HttpException(response)
                val body = response.body()
                    ?: throw ApiException("Empty sport sign-out response", null)
                if (body.errorCode != 0) {
                    throw ApiException(body.errorCode, body.errorMessage)
                }
            }
        }
    }

    override suspend fun createFreeSignEntry(
        lessonId: Long,
        forceSign: Boolean
    ): AppResult<Unit> {
        return runAction {
            widgetsApi.createSportFreeSignEntry(
                SportFreeSignRequest(
                    lessonId = lessonId,
                    forceSign = forceSign
                )
            ).requireSuccess()
        }
    }

    override suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit> {
        return runAction {
            widgetsApi.cancelSportFreeSignEntry(entryId).requireSuccess()
        }
    }

    override suspend fun createAutoSignEntry(prototypeLessonId: Long): AppResult<Unit> {
        return runAction {
            widgetsApi.createSportAutoSignEntry(
                SportAutoSignRequest(prototypeLessonId = prototypeLessonId)
            ).requireSuccess()
        }
    }

    override suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit> {
        return runAction {
            widgetsApi.cancelSportAutoSignEntry(entryId).requireSuccess()
        }
    }

    private suspend fun runAction(action: suspend () -> Unit): AppResult<Unit> {
        return try {
            action()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private fun ApiResponse<*>.requireSuccess() {
        if (!success) {
            throw IllegalStateException(error?.message ?: "Backend rejected sport action")
        }
    }
}
