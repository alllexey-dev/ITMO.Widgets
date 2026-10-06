package dev.alllexey.itmowidgets.feature.settings.reference

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import dev.alllexey.itmowidgets.feature.settings.presentation.HomePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.MaintenancePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.RecordbookPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.RootPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SchedulePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.ServicesPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPages
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SportPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.WidgetsPageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The settings ViewModel of [page] over read-only fakes, as the presentation tests' fixture builds it: no background
 * restriction, no quick-settings tile, version `2.1-test`. That fixture lives in `:shared:feature-settings`'s common
 * tests, which `:app` cannot read.
 */
internal fun referenceSettingsViewModel(
    local: LocalSettings,
    sharing: SharingSettingsState,
    page: SettingsPage
): SettingsViewModel {
    val repository = ReadOnlySettingsRepository(local, sharing)
    val refresher = object : WidgetRefreshRequester {
        override fun refreshAll() = Unit
    }
    val pages = SettingsPages(
        root = RootPageProvider(),
        services = ServicesPageProvider(repository, FakeCustomServicesRepository(), refresher),
        widgets = WidgetsPageProvider(repository, NoQuickSettingsTile),
        home = HomePageProvider(repository),
        schedule = SchedulePageProvider(
            repository,
            FakeScheduleChangeTracking(enabled = local.scheduleChangesEnabled),
            FakeCalendarSync()
        ),
        recordbook = RecordbookPageProvider(FakeMarkTracking()),
        sport = SportPageProvider(repository),
        maintenance = MaintenancePageProvider(
            refresher,
            FakeOnboardingRepository(completed = true),
            AppVersion("2.1-test"),
            RecordingDiagnostics()
        )
    )
    return SettingsViewModel(
        pages = pages,
        repository = repository,
        widgetRefreshRequester = refresher,
        backgroundWork = UnrestrictedBackgroundWork,
        savedStateHandle = SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name))
    )
}

private object UnrestrictedBackgroundWork : BackgroundWorkAccess {
    override fun isUnrestricted(): Boolean = true
}

private object NoQuickSettingsTile : QuickSettingsTileAccess {
    override fun canRequestAdd(): Boolean = false
}

/** Stored values that never change: a reference draws one state, so every write is dropped. */
private class ReadOnlySettingsRepository(
    private val local: LocalSettings,
    sharing: SharingSettingsState
) : SettingsRepository {
    private val sharing = MutableStateFlow(sharing)

    override fun observeLocalSettings(): Flow<LocalSettings> = flowOf(local)
    override fun observeSharingSettings(): Flow<SharingSettingsState> = sharing
    override suspend fun refreshSharingSettings() = Unit
    override fun disableSharingSettings() {
        sharing.value = SharingSettingsState.Disabled
    }
    override suspend fun setScheduleVisibility(visibility: SharingVisibility): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setFriendsVisibility(visibility: SharingVisibility): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setSportVisibility(visibility: SharingVisibility): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) = Unit
    override suspend fun setHomeCardVisible(kind: HomeCardKind, visible: Boolean) = Unit
    override suspend fun setBackgroundWorkHintShown() = Unit
    override suspend fun setQrTileAdded(added: Boolean) = Unit
    override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) = Unit
    override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) = Unit
    override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) = Unit
    override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) = Unit
    override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) = Unit
    override suspend fun setCompactWidgetTextSize(size: WidgetTextSize) = Unit
    override suspend fun setFullWidgetTextSize(size: WidgetTextSize) = Unit
    override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = Unit
    override suspend fun setQrSpoilerEnabled(enabled: Boolean) = Unit
    override suspend fun setQrAnimationType(type: QrAnimationType) = Unit
    override suspend fun setTeacherSelectorHidden(hidden: Boolean) = Unit
    override suspend fun setTimeSelectorHidden(hidden: Boolean) = Unit
}
