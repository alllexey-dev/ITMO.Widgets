package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.text.detailLines
import dev.alllexey.itmowidgets.core.text.headline
import dev.alllexey.itmowidgets.core.text.listLines
import dev.alllexey.itmowidgets.core.text.listSummary
import dev.alllexey.itmowidgets.core.text.summary

// Android adapters of `core.text.ScheduleChangeTexts` for notifiers, widgets and Views; each goes with its last caller.

fun ScheduleChange.summary(context: Context): String = summary().resolve(context)

fun ScheduleChange.listSummary(context: Context): String = listSummary().resolve(context)

fun ScheduleChange.listLines(context: Context): List<String> = listLines().map { it.resolve(context) }

fun ScheduleChange.headline(context: Context): String = headline().resolve(context)

fun ScheduleChange.detailLines(context: Context): List<String> = detailLines().map { it.resolve(context) }
