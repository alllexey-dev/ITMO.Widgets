package dev.alllexey.itmowidgets.feature.sport.data.debug

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import kotlinx.datetime.LocalDate

interface SportLessonTemplateProvider {
    fun getSchedule(): Map<LocalDate, List<SportLesson>>?
}
