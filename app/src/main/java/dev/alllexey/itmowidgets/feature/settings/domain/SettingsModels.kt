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
    val sport: SportDisplaySettings = SportDisplaySettings(),
    val showSportAutoSign: Boolean = false
)

/** Stored as `hide*` to preserve the existing preference keys. */
data class SportDisplaySettings(
    val hideTeacherSelector: Boolean = true,
    val hideTimeSelector: Boolean = true
)

/** The owner's audience; the viewer's own choice never restricts access. */
enum class SharingVisibility { ALL, FRIENDS, NOBODY }

data class SharingSettings(
    val scheduleVisibility: SharingVisibility = SharingVisibility.FRIENDS,
    val sportVisibility: SharingVisibility = SharingVisibility.FRIENDS,
    val friendsVisibility: SharingVisibility = SharingVisibility.ALL
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

    suspend fun setScheduleVisibility(visibility: SharingVisibility): AppResult<Unit>

    suspend fun setFriendsVisibility(visibility: SharingVisibility): AppResult<Unit>

    suspend fun setSportVisibility(visibility: SharingVisibility): AppResult<Unit>

    suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean)

    suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean)

    suspend fun setCompactWidgetTeacherHidden(hidden: Boolean)

    suspend fun setFullWidgetTeacherHidden(hidden: Boolean)

    suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean)

    suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean)

    suspend fun setQrDynamicColorsEnabled(enabled: Boolean)

    suspend fun setQrSpoilerEnabled(enabled: Boolean)

    suspend fun setQrAnimationType(type: QrAnimationType)

    suspend fun setTeacherSelectorHidden(hidden: Boolean)

    suspend fun setTimeSelectorHidden(hidden: Boolean)
}
