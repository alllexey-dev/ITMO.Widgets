package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.recordbook.MarkSubjects
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.marks_subjects_more

/** "Физика, Математический анализ и ещё 2": the subjects a notification or the home card names, without marks. */
fun markSubjectList(names: List<String>): UiText {
    val list = MarkSubjects.split(names)
    val shown = list.shown.joinToString(", ")
    return if (list.more > 0) UiText.Res(Res.string.marks_subjects_more, listOf(shown, list.more)) else UiText.Dynamic(shown)
}
