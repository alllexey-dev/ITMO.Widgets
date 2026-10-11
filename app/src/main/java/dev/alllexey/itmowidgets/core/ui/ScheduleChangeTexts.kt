package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.text.headline

// Android adapter of `core.text.ScheduleChangeTexts` for the change notifier; it goes with its last caller.

fun ScheduleChange.headline(context: Context): String = headline().resolve(context)
