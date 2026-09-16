package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.UserPrivacySettings
import dev.alllexey.itmowidgets.core.model.SharingVisibility as ApiSharingVisibility
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
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
        val scheduleWidget = settings.observeScheduleWidgetSettings()
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
            sport,
            settings.observeScheduleSportAutoSignEnabled()
        ) { customServices, schedule, qr, sportSettings, showSportAutoSign ->
            LocalSettings(
                customServicesEnabled = customServices,
                scheduleWidget = schedule,
                qrWidget = qr,
                sport = sportSettings,
                showSportAutoSign = showSportAutoSign
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

    override suspend fun setScheduleVisibility(visibility: SharingVisibility): AppResult<Unit> {
        return updateSharingSettings { current ->
            current.copy(scheduleVisibility = visibility)
        }
    }

    override suspend fun setFriendsVisibility(visibility: SharingVisibility): AppResult<Unit> =
        updateSharingSettings { current -> current.copy(friendsVisibility = visibility) }

    override suspend fun setSportVisibility(visibility: SharingVisibility): AppResult<Unit> {
        return updateSharingSettings { current ->
            current.copy(sportVisibility = visibility)
        }
    }

    override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
        settings.setScheduleSportAutoSignEnabled(enabled)
    }

    override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) {
        settings.setCompactWidgetNextLessonEarlyEnabled(enabled)
    }

    override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) {
        settings.setCompactWidgetTeacherHidden(hidden)
    }

    override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) {
        settings.setFullWidgetTeacherHidden(hidden)
    }

    override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) {
        settings.setFullWidgetPastLessonsHidden(hidden)
    }

    override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) {
        settings.setFullWidgetTomorrowEnabled(enabled)
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
                widgetsApi.updateMyPrivacySettings(requested.toDto())
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
            widgetsApi.myPrivacySettings().requireData().toDomain()
        }
    }

    private fun UserPrivacySettings.toDomain(): SharingSettings {
        return SharingSettings(
            scheduleVisibility = SharingVisibility.valueOf(scheduleVisibility.name),
            sportVisibility = SharingVisibility.valueOf(sportVisibility.name),
            friendsVisibility = SharingVisibility.valueOf(friendsVisibility.name)
        )
    }

    private fun SharingSettings.toDto(): UserPrivacySettings {
        return UserPrivacySettings(
            scheduleVisibility = ApiSharingVisibility.valueOf(scheduleVisibility.name),
            sportVisibility = ApiSharingVisibility.valueOf(sportVisibility.name),
            friendsVisibility = ApiSharingVisibility.valueOf(friendsVisibility.name)
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
