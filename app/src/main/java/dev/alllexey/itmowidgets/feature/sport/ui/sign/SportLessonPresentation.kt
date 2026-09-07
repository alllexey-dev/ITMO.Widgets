package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.annotation.StringRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind

@StringRes
fun SportLessonKind.titleRes(): Int {
    return when (this) {
        SportLessonKind.TRAINING_SECTION -> R.string.sport_lesson_training_section
        SportLessonKind.INTERMEDIATE_SECTION -> R.string.sport_lesson_intermediate_section
        SportLessonKind.TEAM_SECTION -> R.string.sport_lesson_team_section
        SportLessonKind.OPEN -> R.string.sport_lesson_open
        SportLessonKind.FREE_ATTENDANCE -> R.string.sport_lesson_free_attendance
        SportLessonKind.DEBT -> R.string.sport_lesson_debt
        SportLessonKind.STANDARDS -> R.string.sport_lesson_standards
        SportLessonKind.EXTERNAL -> R.string.sport_lesson_external
        SportLessonKind.ADDITIONAL -> R.string.sport_lesson_additional
        SportLessonKind.UNKNOWN -> R.string.sport_lesson_unknown
    }
}

@StringRes
fun SportLessonKind.compactTitleRes(): Int = when (this) {
    SportLessonKind.FREE_ATTENDANCE -> R.string.sport_kind_free_short
    SportLessonKind.TRAINING_SECTION -> R.string.sport_kind_training_short
    SportLessonKind.INTERMEDIATE_SECTION -> R.string.sport_kind_intermediate_short
    SportLessonKind.TEAM_SECTION -> R.string.sport_kind_team_short
    else -> titleRes()
}
