package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_before_year
import kotlinx.datetime.format
import org.jetbrains.compose.resources.stringResource

/** When a review was written: `Сентябрь 2026` or `До 2023`. */
@Composable
fun ReviewDate.text(): String = when (this) {
    is ReviewDate.Month -> text()
    is ReviewDate.BeforeYear -> stringResource(Res.string.teacher_review_before_year, year)
}

/** The capitalised stand-alone month and the year, `Сентябрь 2026` (`LLLL yyyy` with a capital first letter). */
fun ReviewDate.Month.text(): String = month.format(DateTexts.MONTH_YEAR).replaceFirstChar(Char::uppercaseChar)
