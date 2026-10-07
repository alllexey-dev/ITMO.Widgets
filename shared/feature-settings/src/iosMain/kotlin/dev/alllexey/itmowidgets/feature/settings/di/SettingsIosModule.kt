package dev.alllexey.itmowidgets.feature.settings.di

import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.storage.WidgetReloader
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import org.koin.dsl.module

/**
 * The iOS ports of [settingsDataModule], what the app's `SettingsBridge` gives Android, so the opt-in and the widget
 * appearance resolve for the first-run flow (IO-07b): WidgetKit reloads for a changed appearance, and no custom
 * spoiler image (iOS hides that row until a card adds the picker). The preference stores, the Backend gate and the
 * session ports come from `iosCoreModule` and `accountIosModule`. The settings screens add theirs here (IO-08a).
 */
val settingsIosModule = module {
    single<WidgetRefreshRequester> { WidgetKitRefreshRequester(get()) }
    single<CustomSpoilerRepository> { NoCustomSpoilerRepository }
}

/** Reloads the timelines of every widget kind of the app (docs/ios.md, Identifiers). */
internal class WidgetKitRefreshRequester(private val reloader: WidgetReloader) : WidgetRefreshRequester {

    override fun refreshAll() = WIDGET_KINDS.forEach(reloader::reload)

    private companion object {
        val WIDGET_KINDS = listOf(
            "dev.alllexey.itmowidgets.widget.qr",
            "dev.alllexey.itmowidgets.widget.single-lesson",
            "dev.alllexey.itmowidgets.widget.day-schedule",
        )
    }
}

/** The QR widget keeps the standard spoiler image on iOS; a save or reset changes nothing and reports so. */
internal object NoCustomSpoilerRepository : CustomSpoilerRepository {
    override suspend fun hasImage(): Boolean = false

    override suspend fun saveImage(sourceUri: String): Boolean = false

    override suspend fun resetImage(): Boolean = false
}
