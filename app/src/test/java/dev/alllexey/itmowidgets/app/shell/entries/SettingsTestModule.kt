package dev.alllexey.itmowidgets.app.shell.entries

import android.content.Context
import android.view.View
import android.widget.Space
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.schedule.ScheduleIcsExport
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreviewFactory
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.HomePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.MaintenancePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.RecordbookPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.RootPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SchedulePageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.ServicesPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPages
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SportPageProvider
import dev.alllexey.itmowidgets.feature.settings.presentation.WidgetsPageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The ViewModels of the settings keys over fakes, for every shell test whose screens open them: local settings in
 * memory with the custom services off, no privacy, an empty journal, no lessons to export and blank widget previews.
 * [spoilers] keeps the custom spoiler image.
 */
internal fun settingsTestModule(spoilers: CustomSpoilerRepository = FakeCustomSpoilerRepository()): Module = module {
    val repository = MemorySettings()
    val refresher = object : WidgetRefreshRequester {
        override fun refreshAll() = Unit
    }
    val pages = SettingsPages(
        root = RootPageProvider(repository),
        services = ServicesPageProvider(repository, FakeCustomServicesRepository(), refresher),
        widgets = WidgetsPageProvider(repository, NoQuickSettingsTile),
        home = HomePageProvider(repository),
        schedule = SchedulePageProvider(repository, FakeScheduleChangeTracking(), FakeCalendarSync()),
        recordbook = RecordbookPageProvider(FakeMarkTracking()),
        sport = SportPageProvider(repository),
        maintenance = MaintenancePageProvider(
            refresher, FakeOnboardingRepository(completed = true), AppVersion("test"), RecordingDiagnostics(),
        ),
    )
    viewModel { SettingsViewModel(pages, repository, refresher, UnrestrictedWork, get<SavedStateHandle>()) }
    viewModel { CustomSpoilerViewModel(spoilers) }
    viewModel { DiagnosticsViewModel(RecordingDiagnostics(), FixedAcademicTime()) }
    viewModel { IcsExportViewModel(NoLessons, FixedAcademicTime(), get<SavedStateHandle>()) }
    single<WidgetPreviewFactory> { BlankPreviews }
}

private object NoQuickSettingsTile : QuickSettingsTileAccess {
    override fun canRequestAdd() = false
}

private object UnrestrictedWork : BackgroundWorkAccess {
    override fun isUnrestricted() = true
}

private object NoLessons : ScheduleIcsExport {
    override suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?> = AppResult.Success(null)
}

/** An empty [Space] for every preview: a plain View would take the whole height from the rows. */
private object BlankPreviews : WidgetPreviewFactory {
    override fun create(context: Context, scope: CoroutineScope, settings: WidgetPreviewSettings): WidgetPreview =
        object : WidgetPreview {
            override val view: View = Space(context)
            override fun bind(settings: WidgetPreviewSettings) = Unit
        }
}

/** Local settings in memory; the privacy page is never opened here. */
private class MemorySettings : SettingsRepository {
    private val local = MutableStateFlow(LocalSettings())
    override fun observeLocalSettings(): Flow<LocalSettings> = local
    override fun observeSharingSettings(): Flow<SharingSettingsState> =
        MutableStateFlow(SharingSettingsState.Disabled)
    override suspend fun refreshSharingSettings() = Unit
    override fun disableSharingSettings() = Unit
    override suspend fun setScheduleVisibility(visibility: SharingVisibility): AppResult<Unit> =
        AppResult.Success(Unit)
    override suspend fun setFriendsVisibility(visibility: SharingVisibility): AppResult<Unit> =
        AppResult.Success(Unit)
    override suspend fun setSportVisibility(visibility: SharingVisibility): AppResult<Unit> =
        AppResult.Success(Unit)
    override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) = Unit
    override suspend fun setHomeCardVisible(kind: HomeCardKind, visible: Boolean) = Unit
    override suspend fun setBackgroundWorkHintShown() = Unit
    override suspend fun setQrTileAdded(added: Boolean) = Unit
    override suspend fun updateTheme(change: (ThemeSpec) -> ThemeSpec) {
        local.value = local.value.copy(theme = change(local.value.theme))
    }
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
