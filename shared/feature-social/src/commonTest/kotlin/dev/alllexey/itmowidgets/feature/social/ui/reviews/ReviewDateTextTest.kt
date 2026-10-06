package dev.alllexey.itmowidgets.feature.social.ui.reviews

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewDateTextTest {

    @Test
    fun aMonthIsTheCapitalisedStandaloneMonthAndTheYear() {
        val expected = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
        ).map { "$it 2026" }

        val texts = Month.entries.map { ReviewDate.Month(YearMonth(2026, it)).text() }

        assertEquals(expected, texts)
    }

    @Test
    fun anOldReviewSaysBeforeItsYear() = runComposeUiTest {
        setContent { Text(ReviewDate.BeforeYear(2023).text()) }

        onNodeWithText("До 2023").assertExists()
    }
}
