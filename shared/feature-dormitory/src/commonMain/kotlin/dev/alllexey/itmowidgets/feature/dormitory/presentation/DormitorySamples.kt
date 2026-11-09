package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.payments.AcademicYear
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.Payment
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.SafeHtml
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.dormitory.domain.AssignedDormitory
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryAnswer
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStage
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryStatus
import dev.alllexey.itmowidgets.feature.dormitory.domain.SettlementDocument
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Synthetic `Общежитие` screens for the Android previews and the iOS snapshot tests, one per Этап общежития and
 * state, built by the same presenter as the app.
 */
object DormitorySamples {
    private val now = Instant.parse("2026-11-08T09:00:00Z")

    /** The academic time standing at [now], 12:00 in Moscow. */
    private val time = object : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

        override fun today(): LocalDate = now.toLocalDateTime(timeZone).date

        override fun now(): Instant = now
    }

    private val dormitory = AssignedDormitory(
        name = "Общежитие №1",
        address = "Санкт-Петербург, Примерный пер., 1",
        instructions = SafeHtml.parse(
            "<p>Заселение — в будни с <b>10:00 до 17:00</b> у коменданта.</p>" +
                "<p>С собой:</p><ul><li>паспорт;</li><li>договор найма;</li><li>фото 3×4.</li></ul>",
        ),
    )

    private val documents = listOf(
        SettlementDocument("Флюорография", "Подтверждено", confirmed = true),
        SettlementDocument("Справка о прививках", "Отсутствует", confirmed = false),
    )

    private fun payment(rubles: Double, due: LocalDate, period: String, paid: Double = 0.0) =
        Payment(Money.ofRubles(rubles), Money.ofRubles(paid), due, period)

    private fun rent(id: String, year: Int, balance: Double, payments: List<Payment>, number: String = "0000.00.00.0001") = Contract(
        id = id,
        kind = ContractKind.RENT,
        number = number,
        start = LocalDate(year, 9, 1),
        end = LocalDate(year + 1, 6, 30),
        academicYear = AcademicYear(year),
        active = year == 2026,
        balance = Money.ofRubles(balance),
        payments = payments,
    )

    /** Paid for September and October, November due in 5 days and paid in part, December ahead. */
    private val onTime = rent(
        "rent-2026",
        2026,
        balance = 0.0,
        payments = listOf(
            payment(1500.0, LocalDate(2026, 9, 10), "Сентябрь 2026", paid = 1500.0),
            payment(1500.0, LocalDate(2026, 10, 10), "Октябрь 2026", paid = 1500.0),
            payment(1500.0, LocalDate(2026, 11, 13), "Ноябрь 2026", paid = 500.0),
            payment(1500.0, LocalDate(2026, 12, 10), "Декабрь 2026"),
        ),
    )

    /** October overdue, November due today: a Долг. */
    private val withDebt = rent(
        "rent-2026",
        2026,
        balance = -1500.0,
        payments = listOf(
            payment(1500.5, LocalDate(2026, 9, 10), "Сентябрь 2026", paid = 1500.5),
            payment(1500.0, LocalDate(2026, 10, 10), "Октябрь 2026"),
            payment(1500.0, LocalDate(2026, 11, 8), "Ноябрь 2026"),
            payment(12000.0, LocalDate(2026, 12, 10), "Декабрь 2026"),
        ),
    )

    private val lastYear = rent(
        "rent-2025",
        2025,
        balance = 0.0,
        number = "0000.00.00.0002",
        payments = listOf(
            payment(1400.0, LocalDate(2025, 9, 10), "Сентябрь 2025", paid = 1400.0),
            payment(1400.0, LocalDate(2026, 2, 10), "Февраль 2026", paid = 1400.0),
        ),
    )

    private fun shown(
        status: DormitoryStatus?,
        failure: AppError? = null,
        age: Int = 0,
        contracts: List<Contract> = emptyList(),
        pastYearsExpanded: Boolean = false,
    ) = DormitoryPresenter.present(
        DormitoryAnswer(status, now - age.hours, contracts), failure, refreshing = false, time = time,
        pastYearsExpanded = pastYearsExpanded,
    )

    val queue: DormitoryUiState = shown(DormitoryStatus(DormitoryStage.QUEUE, 42, null, emptyList()))

    /** Seen by the app at 09:00, three hours ago. */
    val placeOffered: DormitoryUiState = shown(
        DormitoryStatus(DormitoryStage.PLACE_OFFERED, null, dormitory, documents, placeOfferedSeenAt = now - 3.hours),
    )

    val dateChosen: DormitoryUiState = shown(
        DormitoryStatus(DormitoryStage.DATE_CHOSEN, null, dormitory, documents.map { it.copy(status = "Подтверждено", confirmed = true) }),
    )

    val settled: DormitoryUiState = shown(DormitoryStatus(DormitoryStage.SETTLED, null, dormitory, emptyList()), contracts = listOf(onTime))

    /** A resident with a Долг: October overdue, November due today. */
    val debt: DormitoryUiState = shown(DormitoryStatus(DormitoryStage.SETTLED, null, dormitory, emptyList()), contracts = listOf(withDebt))

    /** A former resident: only last year's Договор найма, unfolded under `Прошлые годы`. */
    val pastYears: DormitoryUiState = shown(null, contracts = listOf(lastYear), pastYearsExpanded = true)

    /** A Договор найма without a dormitory application. */
    val contractOnly: DormitoryUiState = shown(null, contracts = listOf(onTime))

    /** Offline: the stored answer from 2 hours ago. */
    val offline: DormitoryUiState = shown(DormitoryStatus(DormitoryStage.SETTLED, null, dormitory, emptyList()), AppError.Network, age = 2)

    val none: DormitoryUiState = DormitoryPresenter.present(DormitoryAnswer(null, now), null, refreshing = false, time = time)

    val failed: DormitoryUiState = DormitoryPresenter.present(null, AppError.Network, refreshing = false, time = time)

    val loading: DormitoryUiState = DormitoryUiState()
}
