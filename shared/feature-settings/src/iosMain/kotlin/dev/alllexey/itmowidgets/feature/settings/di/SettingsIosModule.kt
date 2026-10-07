package dev.alllexey.itmowidgets.feature.settings.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.platform.AppBundleVersion
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.feature.settings.data.IosBackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.data.IosWidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.data.StoredScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.settings.data.UnavailableCalendarSync
import dev.alllexey.itmowidgets.feature.settings.data.UnavailableMarkTracking
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import org.koin.dsl.module

/**
 * The iOS side of [settingsDataModule] and [settingsModule], what `:app`'s `SettingsBridge` gives Android: widget
 * reloads, Background App Refresh as the background work access, no quick settings tile, no custom spoiler image (iOS
 * hides that row until a card adds the picker), the bundle's version. The first-run flow (IO-07b) resolves the opt-in
 * and the widget appearance through it too. The core contracts come from `iosCoreModule`, the onboarding flag from
 * `onboardingDataModule`.
 *
 * Until their IO cards bind the real ones (`IosPendingChecks.kt`): the schedule change switch without its check
 * (IO-09b, IO-14), and mark tracking and the calendar sync, whose rows `PlatformCapabilities` hides (IO-09d3,
 * IO-15b). Each of those cards deletes its line here.
 */
val settingsIosModule = module {
    single<WidgetRefreshRequester> { IosWidgetRefreshRequester(get()) }
    single<BackgroundWorkAccess> { IosBackgroundWorkAccess() }
    single<QuickSettingsTileAccess> { NoQuickSettingsTile }
    single<CustomSpoilerRepository> { NoCustomSpoilerRepository }
    single { AppVersion(AppBundleVersion.fromMainBundle()) }

    single<ScheduleChangeTracking> { StoredScheduleChangeTracking(get()) }
    single<MarkTracking> { UnavailableMarkTracking }
    single<CalendarSync> { UnavailableCalendarSync }
}

/**
 * The Koin parameters of the `SettingsViewModel` of [page], a `SettingsPage` name (`AppRoutes.Settings.page`): Swift
 * passes them to `ScreenViewModelStore.resolve(type:parameters:)`. The ViewModel reads its page from a
 * `SavedStateHandle`, as on Android; a SwiftUI store has no saved-state registry, so the handle is built here and
 * restores nothing.
 */
fun settingsPageParameters(page: String): List<Any> =
    listOf(SavedStateHandle(mapOf(SettingsPage.ARGUMENT to SettingsPage.fromArgument(page).name)))

/** iOS has no quick settings. */
private object NoQuickSettingsTile : QuickSettingsTileAccess {
    override fun canRequestAdd(): Boolean = false
}

/** The QR widget keeps the standard spoiler image on iOS; a save or reset changes nothing and reports so. */
internal object NoCustomSpoilerRepository : CustomSpoilerRepository {
    override suspend fun hasImage(): Boolean = false

    override suspend fun saveImage(sourceUri: String): Boolean = false

    override suspend fun resetImage(): Boolean = false
}
