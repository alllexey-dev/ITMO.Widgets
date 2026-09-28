package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun ReviewDate.text(context: Context): String = when (this) {
    is ReviewDate.Month -> month.format(MONTH).replaceFirstChar { it.uppercase(RUSSIAN) }
    is ReviewDate.BeforeYear -> context.getString(R.string.teacher_review_before_year, year)
}

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", RUSSIAN)
