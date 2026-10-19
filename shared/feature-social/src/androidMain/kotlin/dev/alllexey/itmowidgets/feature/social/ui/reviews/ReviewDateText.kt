package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.runtime.Composable
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.teacher_review_before_year
import org.jetbrains.compose.resources.stringResource

/** When a review was written: `Сентябрь 2026` or `До 2023`. */
@Composable
fun ReviewDate.text(): String = when (this) {
    is ReviewDate.Month -> text()
    is ReviewDate.BeforeYear -> stringResource(Res.string.teacher_review_before_year, year)
}
