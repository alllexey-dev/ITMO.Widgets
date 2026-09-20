package dev.alllexey.itmowidgets.feature.schedule.ui

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.lessonTypeNameRes
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson

@ColorRes
fun Lesson.TypeId.colorRes(): Int = lessonTypeColorRes(raw)

@StringRes
fun Lesson.TypeId.nameRes(): Int = lessonTypeNameRes(raw)
