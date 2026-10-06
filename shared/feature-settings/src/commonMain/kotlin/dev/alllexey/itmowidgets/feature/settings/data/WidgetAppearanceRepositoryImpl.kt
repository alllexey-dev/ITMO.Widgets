package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class WidgetAppearanceRepositoryImpl(
    private val widgetSettings: WidgetSettingsPreferences,
    private val qrSettings: QrSettingsPreferences,
    private val widgetRefreshRequester: WidgetRefreshRequester
) : WidgetAppearanceRepository {

    override fun observeAppearance(): Flow<WidgetAppearance> = combine(
        widgetSettings.observeScheduleWidgetSettings(),
        qrSettings.observeQrDynamicColorsEnabled(),
        qrSettings.observeQrSpoilerEnabled(),
        qrSettings.observeQrSpoilerAnimationType()
    ) { schedule, dynamicColors, spoilerEnabled, animationType ->
        WidgetAppearance(
            schedule = schedule,
            qr = QrWidgetSettings(
                dynamicColors = dynamicColors,
                spoilerEnabled = spoilerEnabled,
                animationType = animationType
            )
        )
    }

    override suspend fun setCompactNextLessonEarly(enabled: Boolean) =
        write { widgetSettings.setCompactWidgetNextLessonEarlyEnabled(enabled) }

    override suspend fun setCompactTeacherHidden(hidden: Boolean) =
        write { widgetSettings.setCompactWidgetTeacherHidden(hidden) }

    override suspend fun setFullTeacherHidden(hidden: Boolean) =
        write { widgetSettings.setFullWidgetTeacherHidden(hidden) }

    override suspend fun setFullPastLessonsHidden(hidden: Boolean) =
        write { widgetSettings.setFullWidgetPastLessonsHidden(hidden) }

    override suspend fun setFullTomorrowEnabled(enabled: Boolean) =
        write { widgetSettings.setFullWidgetTomorrowEnabled(enabled) }

    override suspend fun setCompactTextSize(size: WidgetTextSize) =
        write { widgetSettings.setCompactWidgetTextSize(size) }

    override suspend fun setFullTextSize(size: WidgetTextSize) =
        write { widgetSettings.setFullWidgetTextSize(size) }

    override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) =
        write { qrSettings.setQrDynamicColorsEnabled(enabled) }

    override suspend fun setQrSpoilerEnabled(enabled: Boolean) =
        write { qrSettings.setQrSpoilerEnabled(enabled) }

    /** Installed widgets read the same preferences; a saved value is shown on them at once. */
    private suspend fun write(block: suspend () -> Unit) {
        block()
        widgetRefreshRequester.refreshAll()
    }
}
