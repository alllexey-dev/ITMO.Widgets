package dev.alllexey.itmowidgets.core.debug

import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import java.time.LocalDate

interface SportLessonTemplateProvider {
    fun getSchedule(): Map<LocalDate, List<SportLesson>>?
}

interface SportLessonTemplateController {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
