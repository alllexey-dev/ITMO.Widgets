package dev.alllexey.itmowidgets.feature.social.ui.reviews

import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.text.DateTexts
import kotlinx.datetime.format

/** The capitalised stand-alone month and the year, `Сентябрь 2026` (`LLLL yyyy` with a capital first letter). */
fun ReviewDate.Month.text(): String = month.format(DateTexts.MONTH_YEAR).replaceFirstChar(Char::uppercaseChar)
