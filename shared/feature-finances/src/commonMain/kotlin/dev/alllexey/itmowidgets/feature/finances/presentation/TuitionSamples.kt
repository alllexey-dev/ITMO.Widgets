package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.payments.AcademicYear
import dev.alllexey.itmowidgets.core.payments.Contract
import dev.alllexey.itmowidgets.core.payments.ContractKind
import dev.alllexey.itmowidgets.core.payments.Money
import dev.alllexey.itmowidgets.core.payments.Payment
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Synthetic `Оплата обучения` screens for the Android previews and the iOS snapshot tests, built by the same presenter
 * as the app.
 */
object TuitionSamples {
    private val now = Instant.parse("2026-11-08T09:00:00Z")

    /** The academic time standing at [now], 12:00 in Moscow. */
    private val time = object : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

        override fun today(): LocalDate = now.toLocalDateTime(timeZone).date

        override fun now(): Instant = now
    }

    private fun payment(rubles: Double, due: LocalDate, period: String, paid: Double = 0.0) =
        Payment(Money.ofRubles(rubles), Money.ofRubles(paid), due, period)

    private fun tuition(id: String, year: Int, balance: Double, payments: List<Payment>, number: String) = Contract(
        id = id,
        kind = ContractKind.TUITION,
        number = number,
        start = LocalDate(year, 9, 1),
        end = LocalDate(year + 1, 8, 31),
        academicYear = AcademicYear(year),
        active = year == 2026,
        balance = Money.ofRubles(balance),
        payments = payments,
    )

    /** September paid, November due in 5 days and paid in part. */
    private val onTimeContract = tuition(
        "tuition-2026",
        2026,
        balance = 0.0,
        number = "T-0000-0001",
        payments = listOf(
            payment(150000.0, LocalDate(2026, 9, 15), "Сентябрь 2026", paid = 150000.0),
            payment(150000.0, LocalDate(2026, 11, 13), "Ноябрь 2026", paid = 50000.0),
        ),
    )

    /** The second payment overdue since 1 November: a Долг. */
    private val withDebt = tuition(
        "tuition-2026",
        2026,
        balance = -75000.5,
        number = "T-0000-0001",
        payments = listOf(
            payment(75000.5, LocalDate(2026, 9, 15), "Сентябрь 2026", paid = 75000.5),
            payment(75000.5, LocalDate(2026, 11, 1), "Ноябрь 2026"),
            payment(75000.0, LocalDate(2027, 2, 15), "Февраль 2027"),
        ),
    )

    private val lastYear = tuition(
        "tuition-2025",
        2025,
        balance = 0.0,
        number = "T-0000-0000",
        payments = listOf(
            payment(140000.0, LocalDate(2025, 9, 15), "Осенний семестр 2025", paid = 140000.0),
            payment(140000.0, LocalDate(2026, 2, 15), "Весенний семестр 2026", paid = 140000.0),
        ),
    )

    private fun shown(contracts: List<Contract>, failure: AppError? = null, age: Int = 0, pastYearsExpanded: Boolean = false) =
        TuitionPresenter.present(
            TuitionAnswer(contracts, now - age.hours), failure, refreshing = false, time = time, pastYearsExpanded = pastYearsExpanded,
        )

    val onTime: TuitionUiState = shown(listOf(onTimeContract))

    /** A Долг: November overdue. */
    val debt: TuitionUiState = shown(listOf(withDebt))

    /** This year's agreement and last year's, unfolded under `Прошлые годы`. */
    val pastYears: TuitionUiState = shown(listOf(onTimeContract, lastYear), pastYearsExpanded = true)

    /** Offline: the stored answer from 2 hours ago. */
    val offline: TuitionUiState = shown(listOf(withDebt), AppError.Network, age = 2)

    val none: TuitionUiState = shown(emptyList())

    val failed: TuitionUiState = TuitionPresenter.present(null, AppError.Network, refreshing = false, time = time)

    val loading: TuitionUiState = TuitionUiState()
}
