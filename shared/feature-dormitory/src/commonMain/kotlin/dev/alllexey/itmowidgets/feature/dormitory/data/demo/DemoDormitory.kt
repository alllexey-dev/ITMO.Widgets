package dev.alllexey.itmowidgets.feature.dormitory.data.demo

import dev.alllexey.itmowidgets.core.payments.AcademicYear
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.Payment
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.SafeHtml
import dev.alllexey.itmowidgets.feature.dormitory.domain.AssignedDormitory
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

/**
 * The demo session's dormitory: a settled resident of a fictional dormitory with one Договор найма and one upcoming
 * Платёж, answered at [now] on [today].
 */
internal object DemoDormitory {

    fun answer(now: Instant, today: LocalDate) = DormitoryAnswer(
        status = DormitoryStatus(
            stage = DormitoryStage.SETTLED,
            queuePlace = null,
            dormitory = AssignedDormitory(
                name = "Общежитие №1",
                address = "Санкт-Петербург, Примерный пер., 1",
                instructions = SafeHtml.parse(
                    "<p>Заселение — в будни с 10:00 до 17:00 у коменданта.</p>" +
                        "<p>С собой:</p><ul><li>паспорт;</li><li>договор найма;</li><li>фото 3×4.</li></ul>",
                ),
            ),
            documents = emptyList(),
        ),
        fetchedAt = now,
        contracts = listOf(contract(today)),
    )

    private fun contract(today: LocalDate): Contract {
        val year = AcademicYear.of(today)
        val due = today.plus(DUE_IN_DAYS, DateTimeUnit.DAY)
        return Contract(
            id = "demo-rent",
            kind = ContractKind.RENT,
            number = "0000.00.00.0001",
            start = LocalDate(year.startYear, Month.SEPTEMBER, 1),
            end = LocalDate(year.endYear, Month.JUNE, 30),
            academicYear = year,
            active = true,
            balance = Money.ZERO,
            payments = listOf(Payment(Money.ofRubles(RENT), Money.ZERO, due, DateTexts.MONTH_YEAR.format(YearMonth(due.year, due.month)).replaceFirstChar { it.uppercase() })),
        )
    }

    private const val DUE_IN_DAYS = 12
    private const val RENT = 1500.0
}
