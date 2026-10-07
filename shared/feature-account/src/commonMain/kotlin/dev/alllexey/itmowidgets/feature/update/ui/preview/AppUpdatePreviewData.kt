package dev.alllexey.itmowidgets.feature.update.ui.preview

import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateUiState

/** Synthetic offers: the 2.1 -> 2.2 release of LA-1c's references, plus notes long enough to scroll. */
internal object AppUpdatePreviewData {
    const val NOTE = "Виджет расписания обновляется быстрее, зачётка помнит выбранный семестр."

    /** Release notes that push the actions below the fold at font 1.3 in 320 dp. */
    const val LONG_NOTE = "Виджет расписания обновляется быстрее и показывает замены занятий. Зачётка помнит " +
        "выбранный семестр и подсвечивает новые оценки. Запись на физкультуру показывает свободные места в " +
        "реальном времени, а уведомления о переносах приходят даже при выключенном экране. Исправлены редкие " +
        "сбои при входе и отображение длинных названий дисциплин."

    val Supported = offer(note = NOTE)
    val Unsupported = offer(unsupported = true)
    val LongNote = offer(note = LONG_NOTE)

    private fun offer(note: String = "", unsupported: Boolean = false) =
        AppUpdateUiState(installed = "2.1", latest = "2.2", note = note, unsupported = unsupported)
}
