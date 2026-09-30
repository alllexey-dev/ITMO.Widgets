package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.recordbook.MarkSubjects

/** "Физика, Математический анализ и ещё 2": the subjects a notification or the home card names, without marks. */
fun markSubjectList(context: Context, names: List<String>): String {
    val list = MarkSubjects.split(names)
    val shown = list.shown.joinToString(", ")
    return if (list.more > 0) context.getString(R.string.marks_subjects_more, shown, list.more) else shown
}
