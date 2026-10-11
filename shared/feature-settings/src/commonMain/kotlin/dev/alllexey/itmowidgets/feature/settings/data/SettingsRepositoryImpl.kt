package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.users.UserPrivacySettings
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.client.users.SharingVisibility as ApiSharingVisibility
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.core.storage.safeEnumOf
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val servicesOptIn: ServicesOptInPreferences,
    private val scheduleChecks: ScheduleCheckPreferences,
    private val widgetSettings: WidgetSettingsPreferences,
    private val qrSettings: QrSettingsPreferences,
    private val sportSignSelectors: SportSignSelectorPreferences,
    private val markSources: MarkSourcePreferences,
    private val homeLayout: HomeLayoutPreferences,
    private val deviceHints: DeviceHintPreferences,
    private val appearance: AppearancePreferences,
    private val backend: BackendGate,
    private val users: UsersApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : SettingsRepository {

    private val sharingState =
        MutableStateFlow<SharingSettingsState>(SharingSettingsState.Disabled)
    private val sharingMutex = Mutex()

    override fun observeLocalSettings(): Flow<LocalSettings> {
        val scheduleWidget = widgetSettings.observeScheduleWidgetSettings()
        val qrWidget = combine(
            qrSettings.observeQrDynamicColorsEnabled(),
            qrSettings.observeQrSpoilerEnabled(),
            qrSettings.observeQrSpoilerAnimationType()
        ) { dynamicColors, spoilerEnabled, animationType ->
            QrWidgetSettings(
                dynamicColors = dynamicColors,
                spoilerEnabled = spoilerEnabled,
                animationType = animationType
            )
        }
        val sport = combine(
            sportSignSelectors.observeSportSignHideTeacherSelectorEnabled(),
            sportSignSelectors.observeSportSignHideTimeSelectorEnabled()
        ) { hideTeacher, hideTime ->
            SportDisplaySettings(
                hideTeacherSelector = hideTeacher,
                hideTimeSelector = hideTime
            )
        }

        val device = combine(
            homeLayout.observeHiddenHomeCards(),
            deviceHints.observeBackgroundWorkHintShown(),
            deviceHints.observeQrTileAdded(),
            appearance.observeTheme(),
            appearance.observeWidgetsFollowTheme()
        ) { hiddenHomeCards, backgroundWorkHintShown, qrTileAdded, theme, widgetsFollowTheme ->
            DeviceLocalSettings(
                hiddenHomeCards = hiddenHomeCards.mapNotNull { safeEnumOf<HomeCardKind>(it) }.toSet(),
                backgroundWorkHintShown = backgroundWorkHintShown,
                qrTileAdded = qrTileAdded,
                theme = theme,
                widgetsFollowTheme = widgetsFollowTheme
            )
        }
        // The typed combine takes at most five flows, so the three mark switches travel together.
        val marks = combine(
            markSources.observeMyItmoMarksEnabled(),
            markSources.observeBarsMarksEnabled(),
            markSources.observeSheetMarksEnabled(),
            ::MarkLocalSettings
        )
        val app = combine(
            scheduleChecks.observeScheduleSportAutoSignEnabled(),
            scheduleChecks.observeScheduleChangesEnabled(),
            marks,
            device
        ) { showSportAutoSign, scheduleChangesEnabled, markSettings, deviceSettings ->
            AppLocalSettings(
                showSportAutoSign = showSportAutoSign,
                scheduleChangesEnabled = scheduleChangesEnabled,
                marks = markSettings,
                device = deviceSettings
            )
        }

        return combine(
            servicesOptIn.observeCustomServicesEnabled(),
            scheduleWidget,
            qrWidget,
            sport,
            app
        ) { customServices, schedule, qr, sportSettings, appSettings ->
            LocalSettings(
                customServicesEnabled = customServices,
                scheduleWidget = schedule,
                qrWidget = qr,
                sport = sportSettings,
                showSportAutoSign = appSettings.showSportAutoSign,
                scheduleChangesEnabled = appSettings.scheduleChangesEnabled,
                myItmoMarksEnabled = appSettings.marks.myItmo,
                barsMarksEnabled = appSettings.marks.bars,
                sheetMarksEnabled = appSettings.marks.sheets,
                hiddenHomeCards = appSettings.device.hiddenHomeCards,
                backgroundWorkHintShown = appSettings.device.backgroundWorkHintShown,
                qrTileAdded = appSettings.device.qrTileAdded,
                theme = appSettings.device.theme,
                widgetsFollowTheme = appSettings.device.widgetsFollowTheme
            )
        }
    }

    override fun observeSharingSettings(): Flow<SharingSettingsState> =
        sharingState.asStateFlow()

    override suspend fun refreshSharingSettings() {
        sharingMutex.withLock {
            if (demo.isActive()) {
                sharingState.value = SharingSettingsState.Content(SharingSettings())
                return
            }
            if (!backend.mayCallBackend()) {
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
        scheduleChecks.setScheduleSportAutoSignEnabled(enabled)
    }

    override suspend fun setHomeCardVisible(kind: HomeCardKind, visible: Boolean) {
        homeLayout.setHomeCardHidden(kind.name, hidden = !visible)
    }

    override suspend fun setBackgroundWorkHintShown() {
        deviceHints.setBackgroundWorkHintShown()
    }

    override suspend fun setQrTileAdded(added: Boolean) {
        deviceHints.setQrTileAdded(added)
    }

    override suspend fun updateTheme(change: (ThemeSpec) -> ThemeSpec) {
        appearance.updateTheme(change)
    }

    override suspend fun setWidgetsFollowTheme(enabled: Boolean) {
        appearance.setWidgetsFollowTheme(enabled)
    }

    override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) {
        widgetSettings.setCompactWidgetNextLessonEarlyEnabled(enabled)
    }

    override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) {
        widgetSettings.setCompactWidgetTeacherHidden(hidden)
    }

    override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) {
        widgetSettings.setFullWidgetTeacherHidden(hidden)
    }

    override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) {
        widgetSettings.setFullWidgetPastLessonsHidden(hidden)
    }

    override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) {
        widgetSettings.setFullWidgetTomorrowEnabled(enabled)
    }

    override suspend fun setCompactWidgetTextSize(size: WidgetTextSize) {
        widgetSettings.setCompactWidgetTextSize(size)
    }

    override suspend fun setFullWidgetTextSize(size: WidgetTextSize) {
        widgetSettings.setFullWidgetTextSize(size)
    }

    override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) {
        qrSettings.setQrDynamicColorsEnabled(enabled)
    }

    override suspend fun setQrSpoilerEnabled(enabled: Boolean) {
        qrSettings.setQrSpoilerEnabled(enabled)
    }

    override suspend fun setQrAnimationType(type: QrAnimationType) {
        qrSettings.setQrSpoilerAnimationType(type)
    }

    override suspend fun setTeacherSelectorHidden(hidden: Boolean) {
        sportSignSelectors.setSportSignHideTeacherSelectorEnabled(hidden)
    }

    override suspend fun setTimeSelectorHidden(hidden: Boolean) {
        sportSignSelectors.setSportSignHideTimeSelectorEnabled(hidden)
    }

    private suspend fun updateSharingSettings(
        transform: (SharingSettings) -> SharingSettings
    ): AppResult<Unit> = sharingMutex.withLock {
        if (demo.isActive()) return@withLock AppResult.Failure(AppError.DemoUnavailable)
        if (!backend.mayCallBackend()) {
            sharingState.value = SharingSettingsState.Disabled
            return@withLock AppResult.Failure(AppError.CustomServicesDisabled)
        }

        val current = (sharingState.value as? SharingSettingsState.Content)?.settings
            ?: return@withLock AppResult.Failure(AppError.Unknown())
        val requested = transform(current)
        sharingState.value = SharingSettingsState.Content(requested, updating = true)

        try {
            val saved = withContext(dispatchers.io) {
                users.updateMyPrivacySettings(requested.toDto())
            }.toDomain()
            sharingState.value = SharingSettingsState.Content(saved)
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            sharingState.value = SharingSettingsState.Content(current)
            throw cancellation
        } catch (error: Exception) {
            sharingState.value = SharingSettingsState.Content(current)
            AppResult.Failure(toAppError(error))
        }
    }

    /**
     * The app's released mapping for a Core 2.0 call: a failure before any answer is [AppError.Network], then the
     * first [BackendException] (or a [MyItmoException] of the token refresh) in the cause chain maps in common code,
     * anything else is a retriable unknown.
     */
    private fun toAppError(error: Exception): AppError {
        if (error.isCausedByNetworkFailure()) return AppError.Network
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            when (current) {
                is BackendException -> return current.asAppError()
                is MyItmoException -> return current.asAppError()
            }
            current = current.cause
        }
        return AppError.Unknown(error)
    }

    private suspend fun fetchSharingSettings(): SharingSettings {
        return withContext(dispatchers.io) {
            users.myPrivacySettings().toDomain()
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
}

private data class AppLocalSettings(
    val showSportAutoSign: Boolean,
    val scheduleChangesEnabled: Boolean,
    val marks: MarkLocalSettings,
    val device: DeviceLocalSettings
)

private data class MarkLocalSettings(
    val myItmo: Boolean,
    val bars: Boolean?,
    val sheets: Boolean
)

private data class DeviceLocalSettings(
    val hiddenHomeCards: Set<HomeCardKind>,
    val backgroundWorkHintShown: Boolean,
    val qrTileAdded: Boolean,
    val theme: ThemeSpec,
    val widgetsFollowTheme: Boolean
)
