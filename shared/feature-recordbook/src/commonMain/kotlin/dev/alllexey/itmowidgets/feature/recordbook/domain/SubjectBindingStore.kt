package dev.alllexey.itmowidgets.feature.recordbook.domain

/** Confirmed "recordbook discipline → schedule subject" links, local to this installation. */
interface SubjectBindingStore {
    suspend fun get(disciplineId: Long): Long?

    suspend fun put(disciplineId: Long, subjectId: Long)

    suspend fun remove(disciplineId: Long)
}
