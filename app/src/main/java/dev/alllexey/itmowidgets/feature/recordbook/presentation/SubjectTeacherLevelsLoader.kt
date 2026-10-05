package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import javax.inject.Inject

/** Review tones of the subject's teachers; the repository keeps them for a day. */
class SubjectTeacherLevelsLoader @Inject constructor(private val repository: TeacherLevelsRepository) {
    /** The ISUs of [teachers] that open a profile: only they can have a tone. */
    fun isusOf(teachers: List<SubjectTeacher>): Set<Int> = teachers.mapNotNull { UserScreenArgs.profileIsu(it.isu) }.toSet()

    /** Tones by ISU; a teacher without one is absent. */
    suspend fun levels(isus: Set<Int>): Map<Long, TeacherLevel> =
        repository.levels(isus).mapKeys { (isu, _) -> isu.toLong() }
}
