package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.text.DateTexts
import java.util.Locale
import kotlinx.datetime.format

fun ReviewDate.text(context: Context): String = when (this) {
    is ReviewDate.Month -> month.format(DateTexts.MONTH_YEAR).replaceFirstChar { it.uppercase(RUSSIAN) }
    is ReviewDate.BeforeYear -> context.getString(R.string.teacher_review_before_year, year)
}

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
