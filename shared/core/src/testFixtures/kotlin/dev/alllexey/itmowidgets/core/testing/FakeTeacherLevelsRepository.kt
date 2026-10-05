package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import kotlinx.coroutines.CompletableDeferred

/** Answers from [levels]; each call's ISUs land in [calls]; a set [gate] holds answers until it completes. */
internal class FakeTeacherLevelsRepository : TeacherLevelsRepository {
    val levels: MutableMap<Int, TeacherLevel> = mutableMapOf()
    val calls = mutableListOf<Set<Int>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel> {
        calls += isus
        gate?.await()
        return levels.filterKeys { it in isus }
    }
}
