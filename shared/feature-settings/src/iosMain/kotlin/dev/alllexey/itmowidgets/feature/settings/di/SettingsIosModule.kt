package dev.alllexey.itmowidgets.feature.settings.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.platform.AppBundleVersion
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.feature.settings.data.IosBackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.data.IosWidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import kotlinx.datetime.LocalDate
import org.koin.dsl.module

/**
 * The iOS side of [settingsDataModule] and [settingsModule], what `:app`'s `SettingsBridge` gives Android: widget
 * reloads, Background App Refresh as the background work access, no quick settings tile, no custom spoiler image (iOS
 * hides that row until a card adds the picker), the bundle's version. The first-run flow (IO-07b) resolves the opt-in
 * and the widget appearance through it too. The core contracts come from `iosCoreModule`, the onboarding flag from
 * `onboardingDataModule`.
 *
 * The schedule change switch and the calendar sync are the schedule data graph's (`scheduleDataModule`, loaded since
 * IO-09b); the phone's calendar and the `.ics` export behind the calendar rows are `calendarIosModule`'s (IO-15b). Mark
 * tracking is `recordbookModule`'s (IO-09d1), scheduled on the app refresh task since IO-09d3, which shows its page.
 */
val settingsIosModule = module {
    single<WidgetRefreshRequester> { IosWidgetRefreshRequester(get()) }
    single<BackgroundWorkAccess> { IosBackgroundWorkAccess() }
    single<QuickSettingsTileAccess> { NoQuickSettingsTile }
    single<CustomSpoilerRepository> { NoCustomSpoilerRepository }
    single { AppVersion(AppBundleVersion.fromMainBundle()) }
}

/**
 * The Koin parameters of the `SettingsViewModel` of [page], a `SettingsPage` name (`AppRoutes.Settings.page`): Swift
 * passes them to `ScreenViewModelStore.resolve(type:parameters:)`. The ViewModel reads its page from a
 * `SavedStateHandle`, as on Android; a SwiftUI store has no saved-state registry, so the handle is built here and
 * restores nothing.
 */
fun settingsPageParameters(page: String): List<Any> =
    listOf(SavedStateHandle(mapOf(SettingsPage.ARGUMENT to SettingsPage.fromArgument(page).name)))

/**
 * The Koin parameters of the `.ics` sheet's `IcsExportViewModel` (IO-15b): a fresh `SavedStateHandle`, since a SwiftUI
 * store has no saved-state registry; the sheet starts at the range choice.
 */
fun icsExportParameters(): List<Any> = listOf(SavedStateHandle())

/**
 * «Свои даты» of the `.ics` sheet from SwiftUI's date pickers: [start] and [end] are ISO days (`2026-10-05`), since
 * Swift has no `LocalDate`. The days are the ones the user picked, so no time zone is involved.
 */
fun pickIcsExportDates(viewModel: IcsExportViewModel, start: String, end: String) =
    viewModel.onDates(LocalDate.parse(start), LocalDate.parse(end))

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
