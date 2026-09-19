package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class WidgetAppearanceRepositoryImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val widgetRefreshRequester: WidgetRefreshRequester
) : WidgetAppearanceRepository {

    override fun observeAppearance(): Flow<WidgetAppearance> = combine(
        settings.observeScheduleWidgetSettings(),
        settings.observeQrDynamicColorsEnabled(),
        settings.observeQrSpoilerEnabled(),
        settings.observeQrSpoilerAnimationType()
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
        write { settings.setCompactWidgetNextLessonEarlyEnabled(enabled) }

    override suspend fun setCompactTeacherHidden(hidden: Boolean) =
        write { settings.setCompactWidgetTeacherHidden(hidden) }

    override suspend fun setFullTeacherHidden(hidden: Boolean) =
        write { settings.setFullWidgetTeacherHidden(hidden) }

    override suspend fun setFullPastLessonsHidden(hidden: Boolean) =
        write { settings.setFullWidgetPastLessonsHidden(hidden) }

    override suspend fun setFullTomorrowEnabled(enabled: Boolean) =
        write { settings.setFullWidgetTomorrowEnabled(enabled) }

    override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) =
        write { settings.setQrDynamicColorsEnabled(enabled) }

    override suspend fun setQrSpoilerEnabled(enabled: Boolean) =
        write { settings.setQrSpoilerEnabled(enabled) }

    /** Installed widgets read the same preferences; a saved value is shown on them at once. */
    private suspend fun write(block: suspend () -> Unit) {
        block()
        widgetRefreshRequester.refreshAll()
    }
}
