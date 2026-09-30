package dev.alllexey.itmowidgets.core.recordbook

/** The subject names a text shows and how many more it only counts. */
data class SubjectList(val shown: List<String>, val more: Int)

/** Shared by the marks notification (recordbook) and the home card (home), so both name the same subjects. */
object MarkSubjects {
    const val SHOWN = 3

    /** Keeps the order of [names]: the first [SHOWN] are named, the rest are a number. */
    fun split(names: List<String>): SubjectList = SubjectList(names.take(SHOWN), (names.size - SHOWN).coerceAtLeast(0))
}
