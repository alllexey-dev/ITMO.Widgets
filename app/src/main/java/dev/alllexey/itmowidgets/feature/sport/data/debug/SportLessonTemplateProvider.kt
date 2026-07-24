package dev.alllexey.itmowidgets.feature.sport.data.debug

import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import java.time.LocalDate

interface SportLessonTemplateProvider {
    fun getSchedule(): Map<LocalDate, List<SportLesson>>?
}
