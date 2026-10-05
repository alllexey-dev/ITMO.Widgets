package dev.alllexey.itmowidgets.core.settings

import kotlinx.coroutines.flow.Flow

/**
 * Widget appearance for screens outside settings.
 *
 * Local preferences only: nothing here reaches Backend. Writing a value also
 * refreshes the pinned widgets, so a caller never has to know about them.
 */
interface WidgetAppearanceRepository {

    fun observeAppearance(): Flow<WidgetAppearance>

    suspend fun setCompactNextLessonEarly(enabled: Boolean)

    suspend fun setCompactTeacherHidden(hidden: Boolean)

    suspend fun setFullTeacherHidden(hidden: Boolean)

    suspend fun setFullPastLessonsHidden(hidden: Boolean)

    suspend fun setFullTomorrowEnabled(enabled: Boolean)

    suspend fun setCompactTextSize(size: WidgetTextSize)

    suspend fun setFullTextSize(size: WidgetTextSize)

    suspend fun setQrDynamicColorsEnabled(enabled: Boolean)

    suspend fun setQrSpoilerEnabled(enabled: Boolean)
}
