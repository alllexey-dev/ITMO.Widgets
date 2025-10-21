package dev.alllexey.itmowidgets.ui.widgets.data

import java.time.LocalDateTime

data class LessonWidgetsState(
    val singleLessonData: SingleLessonWidgetData,
    val lessonListWidgetData: LessonListWidgetData,
    val nextUpdateAt: LocalDateTime
)
