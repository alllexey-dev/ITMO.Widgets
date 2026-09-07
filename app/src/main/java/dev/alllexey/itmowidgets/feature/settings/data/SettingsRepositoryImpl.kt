package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.UserSettings
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val widgetsApi: ItmoWidgetsApi
) : SettingsRepository {

    private val sharingState =
        MutableStateFlow<SharingSettingsState>(SharingSettingsState.Disabled)
    private val sharingMutex = Mutex()

    override fun observeLocalSettings(): Flow<LocalSettings> {
        val scheduleWidget = combine(
            settings.observeWidgetForwardSchedulingEnabled(),
            settings.observeWidgetHideTeacherEnabled(),
            settings.observeWidgetHidePreviousLessonsEnabled(),
            settings.observeWidgetFutureScheduleEnabled()
        ) { showNextEarly, hideTeacher, hidePast, showTomorrow ->
            ScheduleWidgetSettings(
                showNextLessonEarly = showNextEarly,
                hideTeacher = hideTeacher,
                hidePastLessons = hidePast,
                showTomorrowWhenTodayIsOver = showTomorrow
            )
        }
        val qrWidget = combine(
            settings.observeQrDynamicColorsEnabled(),
            settings.observeQrSpoilerEnabled(),
            settings.observeQrSpoilerAnimationType()
        ) { dynamicColors, spoilerEnabled, animationType ->
            QrWidgetSettings(
                dynamicColors = dynamicColors,
                spoilerEnabled = spoilerEnabled,
                animationType = animationType
            )
        }
        val sport = combine(
            settings.observeSportSignHideTeacherSelectorEnabled(),
            settings.observeSportSignHideTimeSelectorEnabled()
        ) { hideTeacher, hideTime ->
            SportDisplaySettings(
                hideTeacherSelector = hideTeacher,
                hideTimeSelector = hideTime
            )
        }

        return combine(
            settings.observeCustomServicesEnabled(),
            scheduleWidget,
            qrWidget,
            sport
        ) { customServices, schedule, qr, sportSettings ->
            LocalSettings(
                customServicesEnabled = customServices,
                scheduleWidget = schedule,
                qrWidget = qr,
                sport = sportSettings
            )
        }
    }

    override fun observeSharingSettings(): Flow<SharingSettingsState> =
        sharingState.asStateFlow()

    override suspend fun refreshSharingSettings() {
        sharingMutex.withLock {
            if (!settings.getCustomServicesEnabled()) {
                sharingState.value = SharingSettingsState.Disabled
                return
            }

            sharingState.value = SharingSettingsState.Loading
            sharingState.value = try {
                SharingSettingsState.Content(fetchSharingSettings())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                SharingSettingsState.Error
            }
        }
    }

    override fun disableSharingSettings() {
        sharingState.value = SharingSettingsState.Disabled
    }

    override suspend fun setScheduleSharing(enabled: Boolean): AppResult<Unit> {
        return updateSharingSettings { current ->
            current.copy(scheduleSharing = enabled)
        }
    }

    override suspend fun setSportSharing(enabled: Boolean): AppResult<Unit> {
        return updateSharingSettings { current ->
            current.copy(sportSharing = enabled)
        }
    }

    override suspend fun setNextLessonEarlyEnabled(enabled: Boolean) {
        settings.setWidgetForwardSchedulingEnabled(enabled)
    }

    override suspend fun setWidgetTeacherHidden(hidden: Boolean) {
        settings.setWidgetHideTeacherEnabled(hidden)
    }

    override suspend fun setPastLessonsHidden(hidden: Boolean) {
        settings.setWidgetHidePreviousLessonsEnabled(hidden)
    }

    override suspend fun setTomorrowScheduleEnabled(enabled: Boolean) {
        settings.setWidgetFutureScheduleEnabled(enabled)
    }

    override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) {
        settings.setQrDynamicColorsEnabled(enabled)
    }

    override suspend fun setQrSpoilerEnabled(enabled: Boolean) {
        settings.setQrSpoilerEnabled(enabled)
    }

    override suspend fun setQrAnimationType(type: QrAnimationType) {
        settings.setQrSpoilerAnimationType(type)
    }

    override suspend fun setTeacherSelectorHidden(hidden: Boolean) {
        settings.setSportSignHideTeacherSelectorEnabled(hidden)
    }

    override suspend fun setTimeSelectorHidden(hidden: Boolean) {
        settings.setSportSignHideTimeSelectorEnabled(hidden)
    }

    private suspend fun updateSharingSettings(
        transform: (SharingSettings) -> SharingSettings
    ): AppResult<Unit> = sharingMutex.withLock {
        if (!settings.getCustomServicesEnabled()) {
            sharingState.value = SharingSettingsState.Disabled
            return@withLock AppResult.Failure(AppError.CustomServicesDisabled)
        }

        val current = (sharingState.value as? SharingSettingsState.Content)?.settings
            ?: return@withLock AppResult.Failure(AppError.Unknown())
        val requested = transform(current)
        sharingState.value = SharingSettingsState.Content(requested, updating = true)

        try {
            val response = withContext(Dispatchers.IO) {
                widgetsApi.updateMySettings(requested.toDto())
            }
            val saved = response.requireData().toDomain()
            sharingState.value = SharingSettingsState.Content(saved)
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            sharingState.value = SharingSettingsState.Content(current)
            throw cancellation
        } catch (error: Exception) {
            sharingState.value = SharingSettingsState.Content(current)
            AppResult.Failure(error.toAppError())
        }
    }

    private suspend fun fetchSharingSettings(): SharingSettings {
        return withContext(Dispatchers.IO) {
            widgetsApi.mySettings().requireData().toDomain()
        }
    }

    private fun UserSettings.toDomain(): SharingSettings {
        return SharingSettings(
            scheduleSharing = scheduleSharing,
            sportSharing = sportSharing
        )
    }

    private fun SharingSettings.toDto(): UserSettings {
        return UserSettings(
            scheduleSharing = scheduleSharing,
            sportSharing = sportSharing
        )
    }

    private fun <T> ApiResponse<T>.requireData(): T {
        val responseData = data
        if (!success || responseData == null) {
            throw IllegalStateException(error?.message ?: "Backend returned empty settings")
        }
        return responseData
    }
}
