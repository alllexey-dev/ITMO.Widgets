package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.text.markSubjectList as markSubjectListText

/** Android adapter of `core.text.markSubjectList`; goes with its last caller. */
fun markSubjectList(context: Context, names: List<String>): String = markSubjectListText(names).resolve(context)
