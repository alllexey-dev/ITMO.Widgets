package dev.alllexey.itmowidgets.feature.settings.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import kotlinx.coroutines.flow.Flow

data class LocalSettings(
    val customServicesEnabled: Boolean = false,
    val scheduleWidget: ScheduleWidgetSettings = ScheduleWidgetSettings(),
    val qrWidget: QrWidgetSettings = QrWidgetSettings(),
    val sport: SportDisplaySettings = SportDisplaySettings()
)

/** Stored as `hide*` to preserve the existing preference keys. */
data class SportDisplaySettings(
    val hideTeacherSelector: Boolean = true,
    val hideTimeSelector: Boolean = true
)

data class SharingSettings(
    val scheduleSharing: Boolean,
    val sportSharing: Boolean
)

sealed interface SharingSettingsState {
    data object Disabled : SharingSettingsState
    data object Loading : SharingSettingsState
    data class Content(
        val settings: SharingSettings,
        val updating: Boolean = false
    ) : SharingSettingsState
    data object Error : SharingSettingsState
}

interface SettingsRepository {

    fun observeLocalSettings(): Flow<LocalSettings>

    fun observeSharingSettings(): Flow<SharingSettingsState>

    suspend fun refreshSharingSettings()

    fun disableSharingSettings()

    suspend fun setScheduleSharing(enabled: Boolean): AppResult<Unit>

    suspend fun setSportSharing(enabled: Boolean): AppResult<Unit>

    suspend fun setNextLessonEarlyEnabled(enabled: Boolean)

    suspend fun setWidgetTeacherHidden(hidden: Boolean)

    suspend fun setPastLessonsHidden(hidden: Boolean)

    suspend fun setTomorrowScheduleEnabled(enabled: Boolean)

    suspend fun setQrDynamicColorsEnabled(enabled: Boolean)

    suspend fun setQrSpoilerEnabled(enabled: Boolean)

    suspend fun setQrAnimationType(type: QrAnimationType)

    suspend fun setTeacherSelectorHidden(hidden: Boolean)

    suspend fun setTimeSelectorHidden(hidden: Boolean)
}
