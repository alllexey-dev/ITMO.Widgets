package dev.alllexey.itmowidgets.core.reviews

/**
 * Tones of teachers' AI summaries for dots next to their names, gated by the ITMO.Widgets opt-in inside the
 * repository: without it the answer is empty and nothing reaches Backend. Answers are kept on the device for a day,
 * only missing or older ones go to Backend. A Backend failure is not a screen error: the fresh cached part is returned.
 */
interface TeacherLevelsRepository {
    /** Teachers without a shown summary of enough confidence are absent from the result. */
    suspend fun levels(isus: Set<Int>): Map<Int, TeacherLevel>
}
